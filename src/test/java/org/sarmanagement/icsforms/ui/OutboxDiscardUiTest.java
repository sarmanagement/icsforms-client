/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JDialog;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.pdf.PdfExportService;
import org.sarmanagement.icsforms.persistence.RemoteIncidentStore;
import org.sarmanagement.icsforms.persistence.RemoteIncidentGateway;
import org.sarmanagement.icsforms.persistence.RemoteAccessException;
import org.sarmanagement.icsforms.validation.IncidentValidator;

/** Covers the displayed discard recheck when later work has been saved. */
class OutboxDiscardUiTest {
	@TempDir
	Path directory;

	@Test
	void unchangedSaveAllowsActualSoleEditDiscardAfterConfirmation() throws Exception {
		discardSoleEdit(false);
	}

	@Test
	void revokedCredentialStillRebindsDiscardedEditToLocalRevision() throws Exception {
		discardSoleEdit(true);
	}

	/**
	 * Drives the actual discard UI with either an available or revoked host credential.
	 *
	 * @param revoked whether subsequent host reads reject the credential
	 * @throws Exception when setup or interaction fails
	 */
	private void discardSoleEdit(boolean revoked) throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		try (HostedIncidentFixture host = new HostedIncidentFixture(directory.resolve("host"), "Shared")) {
			RemoteIncidentStore paired = host.pair();
			RemoteIncidentGateway gateway = paired.detachGateway();
			paired.close();
			var denied = new java.util.concurrent.atomic.AtomicBoolean();
			RemoteIncidentGateway transport = (RemoteIncidentGateway) java.lang.reflect.Proxy.newProxyInstance(
					RemoteIncidentGateway.class.getClassLoader(), new Class<?>[] {RemoteIncidentGateway.class},
					(proxy, method, args) -> {
						if (denied.get() && "read".equals(method.getName())) {
							throw new RemoteAccessException(RemoteAccessException.Reason.CREDENTIAL_REVOKED,
									"Credential revoked", null);
						}
						try {
							return method.invoke(gateway, args);
						} catch (java.lang.reflect.InvocationTargetException failure) {
							throw failure.getCause();
						}
					});
			RemoteIncidentStore remote = new RemoteIncidentStore(transport, host.incident(), directory.resolve("cache"));
			var initial = remote.loadSnapshot(host.incident());
			String originalName = initial.data().getIncidentContext().getIncidentName();
			initial.data().getIncidentContext().setIncidentName("to archive");
			remote.save(host.incident(), initial.data(), initial.revision());
			var local = remote.loadSnapshot(host.incident());
			denied.set(revoked);
			if (revoked) {
				assertThrows(RemoteAccessException.class, () -> transport.read(host.incident()));
			}
			AtomicReference<MainFrame> frame = new AtomicReference<>();
			CompletableFuture<Void> confirmed = new CompletableFuture<>();
			SwingUtilities.invokeAndWait(() -> {
				frame.set(new MainFrame(local.data(), remote, host.incident(), new PdfExportService(),
						new IncidentValidator(), directory));
				frame.get().setVisible(true);
				java.util.concurrent.atomic.AtomicBoolean clicked = new java.util.concurrent.atomic.AtomicBoolean();
				Timer timer = new Timer(25, null);
				timer.addActionListener(event -> {
					for (java.awt.Window window : java.awt.Window.getWindows()) {
						if (window instanceof JDialog dialog && dialog.isShowing()) {
							JOptionPane pane = optionPane(dialog);
							if (pane == null) {
								continue;
							}
							if (dialog.getTitle().equals("Outbox review")) {
								JButton button = button(dialog, "Discard pending edit");
								if (button != null && button.isEnabled() && clicked.compareAndSet(false, true)) {
									SwingUtilities.invokeLater(button::doClick);
								}
							} else if (dialog.getTitle().equals("Discard pending edit")) {
								pane.setValue(JOptionPane.YES_OPTION);
								confirmed.complete(null);
								timer.stop();
							} else {
								confirmed.completeExceptionally(new AssertionError("Unexpected dialog: " + pane.getMessage()));
								pane.setValue(JOptionPane.OK_OPTION);
								timer.stop();
							}
						}
					}
				});
				timer.start();
				try {
					Method show = MainFrame.class.getDeclaredMethod("showOutbox");
					show.setAccessible(true);
					show.invoke(frame.get());
				} catch (ReflectiveOperationException failure) {
					timer.stop();
					confirmed.completeExceptionally(failure);
				}
			});
			try {
				confirmed.get(20, TimeUnit.SECONDS);
				long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
				while (!remote.pendingOperations().isEmpty() && System.nanoTime() < deadline) {
					Thread.sleep(20);
				}
				assertTrue(remote.pendingOperations().isEmpty());
				var cached = remote.loadCachedSnapshot(host.incident());
				AtomicReference<Boolean> rebound = new AtomicReference<>(false);
				while (!rebound.get() && System.nanoTime() < deadline) {
					SwingUtilities.invokeAndWait(() -> {
						AppController controller = (AppController) Ics205PanelTest.field(frame.get(), "controller");
						rebound.set(frame.get().isEnabled()
								&& originalName.equals(controller.getData().getIncidentContext().getIncidentName())
								&& cached.revision().equals(Ics205PanelTest.field(controller, "storedRevision")));
					});
					Thread.sleep(20);
				}
				assertTrue(rebound.get(), "Editor must bind to the local post-discard revision");
				try (var files = java.nio.file.Files.walk(directory.resolve("cache"))) {
					assertTrue(files.anyMatch(path -> path.getFileName().toString().contains(".rejected-")));
				}
			} finally {
				SwingUtilities.invokeAndWait(() -> frame.get().dispose());
				remote.close();
			}
		}
	}

	@Test
	void savedLaterWorkPreventsDiscardAndDisplaysReviewMessage() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		try (HostedIncidentFixture host = new HostedIncidentFixture(directory.resolve("host"), "Shared")) {
			RemoteIncidentStore paired = host.pair();
			var gateway = paired.detachGateway();
			paired.close();
			RemoteIncidentStore remote = new RemoteIncidentStore(gateway, host.incident(), directory.resolve("cache"));
			var initial = remote.loadSnapshot(host.incident());
			initial.data().getIncidentContext().setIncidentName("first edit");
			remote.save(host.incident(), initial.data(), initial.revision());
			var local = remote.loadSnapshot(host.incident());
			AtomicReference<MainFrame> frame = new AtomicReference<>();
			CompletableFuture<String> message = new CompletableFuture<>();
			SwingUtilities.invokeAndWait(() -> {
				frame.set(new MainFrame(local.data(), remote, host.incident(), new PdfExportService(),
						new IncidentValidator(), directory));
				frame.get().setVisible(true);
				var contextPanel = (IncidentContextPanel) Ics205PanelTest.field(frame.get(), "incidentContextPanel");
				((javax.swing.JTextField) Ics205PanelTest.field(contextPanel, "incidentNameField")).setText("later edit");
				java.util.concurrent.atomic.AtomicBoolean clicked = new java.util.concurrent.atomic.AtomicBoolean();
				Timer timer = new Timer(25, null);
				timer.addActionListener(event -> {
					for (java.awt.Window window : java.awt.Window.getWindows()) {
						if (window instanceof JDialog dialog && dialog.isShowing()) {
							JOptionPane pane = optionPane(dialog);
							if (pane != null) {
								if (dialog.getTitle().equals("Outbox review")) {
									JButton action = button(dialog, "Discard pending edit");
									if (action != null && action.isEnabled() && clicked.compareAndSet(false, true)) {
										SwingUtilities.invokeLater(action::doClick);
									}
								} else {
									message.complete(String.valueOf(pane.getMessage()));
									pane.setValue(JOptionPane.OK_OPTION);
									timer.stop();
								}
							}
						}
					}
				});
				timer.start();
				try {
					Method show = MainFrame.class.getDeclaredMethod("showOutbox");
					show.setAccessible(true);
					show.invoke(frame.get());
				} catch (ReflectiveOperationException failure) {
					timer.stop();
					message.completeExceptionally(failure);
				}
			});
			try {
				assertTrue(message.get(20, TimeUnit.SECONDS).contains("Nothing was discarded"));
				assertEquals(2, remote.pendingOperations().size());
				assertEquals("later edit", remote.loadSnapshot(host.incident()).data().getIncidentContext().getIncidentName());
			} finally {
				SwingUtilities.invokeAndWait(() -> {
					frame.get().dispose();
				});
				remote.close();
			}
		}
	}

	/**
	 * Finds the option pane in a displayed dialog.
	 *
	 * @param component component to inspect
	 * @return option pane, or null when absent
	 */
	private static JOptionPane optionPane(Component component) {
		if (component instanceof JOptionPane pane) {
			return pane;
		}
		if (component instanceof Container container) {
			for (Component child : container.getComponents()) {
				JOptionPane pane = optionPane(child);
				if (pane != null) {
					return pane;
				}
			}
		}
		return null;
	}

	/**
	 * Finds an action button by its label.
	 *
	 * @param component component to inspect
	 * @param label requested button text
	 * @return matching button, or null
	 */
	private static JButton button(Component component, String label) {
		if (component instanceof JButton candidate && label.equals(candidate.getText())) {
			return candidate;
		}
		if (component instanceof Container container) {
			for (Component child : container.getComponents()) {
				JButton found = button(child, label);
				if (found != null) {
					return found;
				}
			}
		}
		return null;
	}
}
