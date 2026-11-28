/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.nio.file.Files;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.List;

import javax.swing.SwingUtilities;
import javax.swing.JOptionPane;
import javax.swing.Timer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.ClueLogEntry;
import org.sarmanagement.icsforms.pdf.PdfExportService;
import org.sarmanagement.icsforms.persistence.FileIncidentStore;
import org.sarmanagement.icsforms.persistence.RemoteIncidentGateway;
import org.sarmanagement.icsforms.persistence.RemoteIncidentStore;
import org.sarmanagement.icsforms.validation.IncidentValidator;

/** Displayed integration coverage for reopening a durable disconnected incident. */
class MainFrameOfflineIntegrationTest {
	@TempDir
	Path directory;

	@Test
	void opensCachedDraftInNewWindowWithoutContactingHost() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		Path cache = directory.resolve(".icsforms").resolve("remote-cache");
		try (HostedIncidentFixture host = new HostedIncidentFixture(directory.resolve("host"), "Shared");
				FileIncidentStore local = new FileIncidentStore(directory.resolve("local"))) {
			RemoteIncidentStore paired = host.pair();
			var gateway = paired.detachGateway();
			paired.close();
			String fingerprint = gateway.authorityFingerprint().orElseThrow();
			try (RemoteIncidentStore durable = new RemoteIncidentStore(gateway, host.incident(), cache)) {
				var snapshot = durable.loadSnapshot(host.incident());
				snapshot.data().getIncidentContext().setIncidentName("Offline draft");
				durable.save(host.incident(), snapshot.data(), snapshot.revision());
			}
			host.session().stop();
			AppData localData = new AppData();
			var localId = local.create(localData);
			AtomicReference<MainFrame> owner = new AtomicReference<>();
			SwingUtilities.invokeAndWait(() -> owner.set(new MainFrame(local.load(localId), local, localId,
					new PdfExportService(), new IncidentValidator(), directory)));
			try {
				Method open = MainFrame.class.getDeclaredMethod("openOffline",
						RemoteIncidentStore.CachedIncident.class);
				open.setAccessible(true);
				SwingUtilities.invokeAndWait(() -> {
					try {
						open.invoke(owner.get(), new RemoteIncidentStore.CachedIncident(fingerprint, host.incident()));
					} catch (ReflectiveOperationException failure) {
						throw new AssertionError(failure);
					}
				});
				long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
				AtomicReference<MainFrame> opened = new AtomicReference<>();
				while (opened.get() == null && System.nanoTime() < deadline) {
					SwingUtilities.invokeAndWait(() -> {
						for (Window window : Window.getWindows()) {
							if (window instanceof MainFrame frame && frame != owner.get() && frame.isDisplayable()) {
								opened.set(frame);
							}
						}
					});
					Thread.sleep(20);
				}
				assertTrue(opened.get() != null, "cached window did not open");
				SwingUtilities.invokeAndWait(() -> {
					AppController controller = controller(opened.get());
					assertEquals("Offline draft", controller.getData().getIncidentContext().getIncidentName());
					assertTrue(opened.get().connectionStatusText().contains("Offline cached incident"));
				});
				deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
				AtomicReference<String> status = new AtomicReference<>("");
				while (!status.get().contains("pending 1") && System.nanoTime() < deadline) {
					SwingUtilities.invokeAndWait(() -> status.set(opened.get().connectionStatusText()));
					Thread.sleep(20);
				}
				assertTrue(status.get().contains("pending 1"), status.get());
				SwingUtilities.invokeAndWait(() -> {
					controller(opened.get()).close();
					controller(opened.get()).closeStore();
					opened.get().dispose();
				});
			} finally {
				SwingUtilities.invokeAndWait(() -> {
					controller(owner.get()).close();
					owner.get().dispose();
				});
			}
		}
	}

	@Test
	void contactReplaysQueuedWorkEvenWithoutAnExternalCursorChange() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		try (HostedIncidentFixture host = new HostedIncidentFixture(directory.resolve("host"), "Shared")) {
			RemoteIncidentStore paired = host.pair();
			var gateway = paired.detachGateway();
			paired.close();
			RemoteIncidentStore remote = new RemoteIncidentStore(gateway, host.incident(),
					directory.resolve("cache"));
			var initial = remote.loadSnapshot(host.incident());
			initial.data().getIncidentContext().setIncidentName("Queued edit");
			remote.save(host.incident(), initial.data(), initial.revision());
			var local = remote.loadSnapshot(host.incident());
			AtomicReference<MainFrame> frame = new AtomicReference<>();
			SwingUtilities.invokeAndWait(() -> {
				frame.set(new MainFrame(local.data(), remote, host.incident(), new PdfExportService(),
						new IncidentValidator(), directory));
				frame.get().attachMonitor(remote, "Connected");
			});
			try {
				long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
				AtomicReference<String> status = new AtomicReference<>("");
				while (!status.get().contains("accepted 1") && System.nanoTime() < deadline) {
					SwingUtilities.invokeAndWait(() -> status.set(frame.get().connectionStatusText()));
					Thread.sleep(20);
				}
				assertTrue(status.get().contains("accepted 1"), status.get());
				assertTrue(status.get().contains("pending 0"), status.get());
				assertEquals("Queued edit", host.backing().load(host.incident())
						.getIncidentContext().getIncidentName());
			} finally {
				SwingUtilities.invokeAndWait(() -> {
					controller(frame.get()).close();
					controller(frame.get()).closeStore();
					frame.get().dispose();
				});
			}
		}
	}

	@Test
	void aBlockedReplayDoesNotBlockStatusOrEventDispatch() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		CountDownLatch submitting = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		try (HostedIncidentFixture host = new HostedIncidentFixture(directory.resolve("host"), "Shared")) {
			RemoteIncidentStore paired = host.pair();
			RemoteIncidentGateway delegate = paired.detachGateway();
			paired.close();
			RemoteIncidentGateway gateway = (RemoteIncidentGateway) Proxy.newProxyInstance(
					RemoteIncidentGateway.class.getClassLoader(), new Class<?>[] {RemoteIncidentGateway.class},
					(proxy, method, args) -> {
						if ("submit".equals(method.getName())) {
							submitting.countDown();
							if (!release.await(20, TimeUnit.SECONDS)) {
								throw new IllegalStateException("Replay did not resume");
							}
						}
						try {
							return method.invoke(delegate, args);
						} catch (InvocationTargetException failure) {
							throw failure.getCause();
						}
					});
			RemoteIncidentStore remote = new RemoteIncidentStore(gateway, host.incident(),
					directory.resolve("cache"));
			var initial = remote.loadSnapshot(host.incident());
			initial.data().getIncidentContext().setIncidentName("Queued edit");
			remote.save(host.incident(), initial.data(), initial.revision());
			var local = remote.loadSnapshot(host.incident());
			AtomicReference<MainFrame> frame = new AtomicReference<>();
			SwingUtilities.invokeAndWait(() -> {
				frame.set(new MainFrame(local.data(), remote, host.incident(), new PdfExportService(),
						new IncidentValidator(), directory));
				frame.get().attachMonitor(remote, "Connected");
			});
			try {
				assertTrue(submitting.await(20, TimeUnit.SECONDS));
				assertTimeoutPreemptively(Duration.ofSeconds(3), () -> SwingUtilities.invokeAndWait(
						() -> assertTrue(frame.get().connectionStatusText().contains("Connected"))));
			} finally {
				release.countDown();
				SwingUtilities.invokeAndWait(() -> {
					controller(frame.get()).close();
					controller(frame.get()).closeStore();
					frame.get().dispose();
				});
			}
		}
	}

	@Test
	void reparingCachedIncidentReplaysItsDurableDraft() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		Path cache = directory.resolve("cache");
		try (HostedIncidentFixture host = new HostedIncidentFixture(directory.resolve("host"), "Shared")) {
			RemoteIncidentStore paired = host.pair();
			RemoteIncidentGateway gateway = paired.detachGateway();
			paired.close();
			String fingerprint = gateway.authorityFingerprint().orElseThrow();
			try (RemoteIncidentStore durable = new RemoteIncidentStore(gateway, host.incident(), cache)) {
				var initial = durable.loadSnapshot(host.incident());
				initial.data().getIncidentContext().setIncidentName("Repaired draft");
				durable.save(host.incident(), initial.data(), initial.revision());
			}
			RemoteIncidentStore offline = RemoteIncidentStore.openOffline(cache, fingerprint, host.incident());
			var snapshot = offline.loadSnapshot(host.incident());
			AtomicReference<MainFrame> frame = new AtomicReference<>();
			SwingUtilities.invokeAndWait(() -> frame.set(new MainFrame(snapshot.data(), offline, host.incident(),
					new PdfExportService(), new IncidentValidator(), directory)));
			try {
				String invitation = host.session().issueInvitation();
				Method reconnect = MainFrame.class.getDeclaredMethod("reconnect", RemoteIncidentStore.class,
						String.class, String.class);
				reconnect.setAccessible(true);
				SwingUtilities.invokeAndWait(() -> {
					try {
						reconnect.invoke(frame.get(), offline, invitation, "repaired device");
					} catch (ReflectiveOperationException failure) {
						throw new AssertionError(failure);
					}
				});
				long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
				AtomicReference<String> status = new AtomicReference<>("");
				while (!status.get().contains("accepted 1") && System.nanoTime() < deadline) {
					SwingUtilities.invokeAndWait(() -> status.set(frame.get().connectionStatusText()));
					Thread.sleep(20);
				}
				assertTrue(status.get().contains("accepted 1"), status.get());
				assertEquals("Repaired draft", host.backing().load(host.incident())
						.getIncidentContext().getIncidentName());
			} finally {
				SwingUtilities.invokeAndWait(() -> {
					controller(frame.get()).close();
					controller(frame.get()).closeStore();
					frame.get().dispose();
				});
			}
		}
	}

	@Test
	void reparingRejectsDifferentIncidentOrPinnedAuthorityWithoutLosingDraft() throws Exception {
		Path cache = directory.resolve("cache");
		try (HostedIncidentFixture original = new HostedIncidentFixture(directory.resolve("original"), "Original");
				HostedIncidentFixture other = new HostedIncidentFixture(directory.resolve("other"), "Other")) {
			RemoteIncidentStore paired = original.pair();
			RemoteIncidentGateway gateway = paired.detachGateway();
			paired.close();
			String fingerprint = gateway.authorityFingerprint().orElseThrow();
			try (RemoteIncidentStore durable = new RemoteIncidentStore(gateway, original.incident(), cache)) {
				var snapshot = durable.loadSnapshot(original.incident());
				snapshot.data().getIncidentContext().setIncidentName("Retained draft");
				durable.save(original.incident(), snapshot.data(), snapshot.revision());
			}
			try (RemoteIncidentStore offline = RemoteIncidentStore.openOffline(cache, fingerprint,
					original.incident())) {
				Method attach = MainFrame.class.getDeclaredMethod("attachPairedGateway", RemoteIncidentStore.class,
						RemoteIncidentStore.class);
				attach.setAccessible(true);
				assertThrows(InvocationTargetException.class, () -> attach.invoke(null, offline, other.pair()));
				RemoteIncidentStore otherPair = other.pair();
				RemoteIncidentGateway otherGateway = otherPair.detachGateway();
				otherPair.close();
				RemoteIncidentStore wrongAuthority = new RemoteIncidentStore(otherGateway, original.incident());
				assertThrows(InvocationTargetException.class, () -> attach.invoke(null, offline, wrongAuthority));
				assertNull(offline.gateway());
				assertEquals(1, offline.pendingOperations().size());
				assertEquals("Retained draft", offline.loadSnapshot(original.incident()).data()
						.getIncidentContext().getIncidentName());
			}
		}
	}

	@Test
	void resolvingConflictRefreshesControllerBeforeReplay() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		try (HostedIncidentFixture host = new HostedIncidentFixture(directory.resolve("host"), "Shared")) {
			RemoteIncidentStore paired = host.pair();
			RemoteIncidentGateway gateway = paired.detachGateway();
			paired.close();
			RemoteIncidentStore remote = new RemoteIncidentStore(gateway, host.incident(),
					directory.resolve("cache"));
			var initial = remote.loadSnapshot(host.incident());
			initial.data().getIncidentContext().setIncidentName("Mine");
			remote.save(host.incident(), initial.data(), initial.revision());
			try (RemoteIncidentStore other = host.pair()) {
				var latest = other.loadSnapshot(host.incident());
				latest.data().getIncidentContext().setIncidentName("Theirs");
				other.save(host.incident(), latest.data(), latest.revision());
			}
			remote.replayPending();
			var conflict = remote.pendingOperations().get(0);
			assertEquals(RemoteIncidentStore.PendingStatus.CONFLICT, conflict.status());
			var entry = new OutboxReviewPanel.Entry(conflict.operationId(), conflict.before(), conflict.draft(), conflict.batch(),
					OutboxReviewPanel.Status.CONFLICT, conflict.conflicts(), conflict.rejection());
			List<OutboxReviewPanel.Decision> decisions = conflict.conflicts().stream()
					.map(item -> new OutboxReviewPanel.Decision(item, OutboxReviewPanel.Choice.MINE)).toList();
			var snapshot = remote.loadSnapshot(host.incident());
			AtomicReference<MainFrame> frame = new AtomicReference<>();
			SwingUtilities.invokeAndWait(() -> frame.set(new MainFrame(snapshot.data(), remote, host.incident(),
					new PdfExportService(), new IncidentValidator(), directory)));
			try {
				Method resolve = MainFrame.class.getDeclaredMethod("resolveOutbox", RemoteIncidentStore.class,
						OutboxReviewPanel.Entry.class, List.class);
				resolve.setAccessible(true);
				SwingUtilities.invokeAndWait(() -> {
					try {
						resolve.invoke(frame.get(), remote, entry, decisions);
					} catch (ReflectiveOperationException failure) {
						throw new AssertionError(failure);
					}
				});
				long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
				AtomicReference<String> status = new AtomicReference<>("");
				while (!status.get().contains("accepted 1") && System.nanoTime() < deadline) {
					SwingUtilities.invokeAndWait(() -> status.set(frame.get().connectionStatusText()));
					Thread.sleep(20);
				}
				assertTrue(status.get().contains("accepted 1"), status.get());
				assertEquals("Mine", host.backing().load(host.incident()).getIncidentContext().getIncidentName());
				SwingUtilities.invokeAndWait(() -> assertEquals("Mine",
						controller(frame.get()).getData().getIncidentContext().getIncidentName()));
			} finally {
				SwingUtilities.invokeAndWait(() -> {
					controller(frame.get()).close();
					controller(frame.get()).closeStore();
					frame.get().dispose();
				});
			}
		}
	}

	@Test
	void confirmedRetryAndDiscardAreExplicitAndRefreshTheController() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		try (HostedIncidentFixture host = new HostedIncidentFixture(directory.resolve("host"), "Shared")) {
			RemoteIncidentStore paired = host.pair();
			RemoteIncidentGateway gateway = paired.detachGateway();
			paired.close();
			String fingerprint = gateway.authorityFingerprint().orElseThrow();
			RemoteIncidentStore remote = new RemoteIncidentStore(gateway, host.incident(), directory.resolve("cache"));
			var snapshot = remote.loadSnapshot(host.incident());
			for (int index = 0; index < 101; index++) {
				ClueLogEntry clue = new ClueLogEntry();
				clue.setDescription("clue " + index);
				snapshot.data().getClueLogEntries().add(clue);
			}
			remote.save(host.incident(), snapshot.data(), snapshot.revision());
			remote.replayPending();
			var rejected = remote.pendingOperations().get(0);
			assertEquals(RemoteIncidentStore.PendingStatus.REJECTED, rejected.status());
			remote.detachGateway().close();
			var local = remote.loadSnapshot(host.incident());
			AtomicReference<MainFrame> frame = new AtomicReference<>();
			SwingUtilities.invokeAndWait(() -> frame.set(new MainFrame(local.data(), remote, host.incident(),
					new PdfExportService(), new IncidentValidator(), directory)));
			try {
				var entry = new OutboxReviewPanel.Entry(rejected.operationId(), rejected.before(), rejected.draft(),
						rejected.batch(), OutboxReviewPanel.Status.REJECTED, rejected.conflicts(), rejected.rejection());
				recover(frame.get(), remote, entry, false, JOptionPane.NO_OPTION);
				assertEquals(RemoteIncidentStore.PendingStatus.REJECTED, remote.pendingOperations().get(0).status());
				recover(frame.get(), remote, entry, false, JOptionPane.YES_OPTION);
				await(() -> remote.pendingOperations().get(0).status() == RemoteIncidentStore.PendingStatus.PENDING);
				recover(frame.get(), remote, entry, false, JOptionPane.YES_OPTION);
				awaitDialog("Could not retry the rejected edit");
				assertEquals(RemoteIncidentStore.PendingStatus.PENDING, remote.pendingOperations().get(0).status());
				var stale = new OutboxReviewPanel.Entry("stale", rejected.before(), rejected.draft(), null,
						OutboxReviewPanel.Status.PENDING, List.of(), null);
				recover(frame.get(), remote, stale, true, JOptionPane.YES_OPTION);
				awaitDialog("Could not discard the pending edit");
				assertEquals(1, remote.pendingOperations().size());
				Path authority = directory.resolve("cache").resolve(fingerprint);
				Path moved = directory.resolve("cache").resolve("moved-authority");
				Files.move(authority, moved);
				try {
					Files.createSymbolicLink(authority, moved);
					recover(frame.get(), remote, entry, true, JOptionPane.YES_OPTION);
					awaitDialog("Could not discard the pending edit");
					assertEquals(1, remote.pendingOperations().size());
					assertEquals(101, remote.loadSnapshot(host.incident()).data().getClueLogEntries().size());
				} finally {
					Files.deleteIfExists(authority);
					Files.move(moved, authority);
				}
				recover(frame.get(), remote, entry, true, JOptionPane.NO_OPTION);
				assertEquals(1, remote.pendingOperations().size());
				recover(frame.get(), remote, entry, true, JOptionPane.YES_OPTION);
				await(() -> remote.pendingOperations().isEmpty());
				try (var archives = Files.list(authority)) {
					assertTrue(archives.anyMatch(path -> path.getFileName().toString()
							.contains(".rejected-" + rejected.operationId() + "-")));
				}
				await(() -> {
					AtomicReference<Boolean> refreshed = new AtomicReference<>(false);
					SwingUtilities.invokeAndWait(() -> refreshed.set(
							controller(frame.get()).getData().getClueLogEntries().isEmpty()));
					return refreshed.get();
				});
			} finally {
				SwingUtilities.invokeAndWait(() -> {
					controller(frame.get()).close();
					controller(frame.get()).closeStore();
					frame.get().dispose();
				});
			}
		}
	}

	@Test
	void splitRequiresConfirmationAndReportsBackendFailure() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		try (HostedIncidentFixture host = new HostedIncidentFixture(directory.resolve("host"), "Shared")) {
			RemoteIncidentStore paired = host.pair();
			RemoteIncidentGateway gateway = paired.detachGateway();
			paired.close();
			RemoteIncidentStore remote = new RemoteIncidentStore(gateway, host.incident(), directory.resolve("cache"));
			var snapshot = remote.loadSnapshot(host.incident());
			for (int index = 0; index < 101; index++) {
				ClueLogEntry clue = new ClueLogEntry();
				clue.setDescription("clue " + index);
				snapshot.data().getClueLogEntries().add(clue);
			}
			remote.save(host.incident(), snapshot.data(), snapshot.revision());
			remote.replayPending();
			var rejected = remote.pendingOperations().get(0);
			remote.detachGateway().close();
			var local = remote.loadSnapshot(host.incident());
			AtomicReference<MainFrame> frame = new AtomicReference<>();
			SwingUtilities.invokeAndWait(() -> frame.set(new MainFrame(local.data(), remote, host.incident(),
					new PdfExportService(), new IncidentValidator(), directory)));
			try {
				var entry = new OutboxReviewPanel.Entry(rejected.operationId(), rejected.before(), rejected.draft(),
						rejected.batch(), OutboxReviewPanel.Status.REJECTED, rejected.conflicts(), rejected.rejection());
				split(frame.get(), remote, entry, JOptionPane.NO_OPTION);
				assertEquals(rejected.operationId(), remote.pendingOperations().get(0).operationId());
				split(frame.get(), remote, entry, JOptionPane.YES_OPTION);
				await(() -> remote.pendingOperations().size() > 1);
				assertTrue(remote.pendingOperations().stream().allMatch(item -> !item.operationId().equals(entry.operationId())));
				split(frame.get(), remote, entry, JOptionPane.YES_OPTION);
				awaitDialog("Could not split the rejected edit");
				assertTrue(remote.pendingOperations().size() > 1);
			} finally {
				SwingUtilities.invokeAndWait(() -> {
					controller(frame.get()).close();
					controller(frame.get()).closeStore();
					frame.get().dispose();
				});
			}
		}
	}

	/**
	 * Invokes a split action and answers its confirmation dialog.
	 *
	 * @param frame owning frame
	 * @param remote active store
	 * @param entry reviewed entry
	 * @param response confirmation response
	 * @throws Exception if invocation fails
	 */
	private static void split(MainFrame frame, RemoteIncidentStore remote, OutboxReviewPanel.Entry entry,
			int response) throws Exception {
		Method method = MainFrame.class.getDeclaredMethod("splitOutboxOperation", RemoteIncidentStore.class,
				OutboxReviewPanel.Entry.class, OutboxReviewPanel.class);
		method.setAccessible(true);
		SwingUtilities.invokeAndWait(() -> {
			Timer timer = answerDialog("Split rejected edit", response);
			try {
				method.invoke(frame, remote, entry, emptyPanel());
			} catch (ReflectiveOperationException failure) {
				throw new AssertionError(failure);
			} finally {
				timer.stop();
			}
		});
	}

	/**
	 * Waits for and dismisses a recovery error.
	 *
	 * @param title expected dialog title
	 * @throws Exception if the dialog cannot be inspected
	 */
	private static void awaitDialog(String title) throws Exception {
		AtomicReference<Boolean> shown = new AtomicReference<>(false);
		await(() -> {
			SwingUtilities.invokeAndWait(() -> {
				for (Window window : Window.getWindows()) {
					if (window instanceof javax.swing.JDialog dialog && dialog.isShowing()
							&& dialog.getTitle().equals(title)) {
						shown.set(true);
						dialog.dispose();
					}
				}
			});
			return shown.get();
		});
	}

	/**
	 * Invokes a recovery action and answers its confirmation dialog.
	 *
	 * @param frame owning frame
	 * @param remote active store
	 * @param entry reviewed entry
	 * @param discard whether to discard
	 * @param response confirmation response
	 * @throws Exception if invocation fails
	 */
	private static void recover(MainFrame frame, RemoteIncidentStore remote, OutboxReviewPanel.Entry entry,
			boolean discard, int response) throws Exception {
		Method method = MainFrame.class.getDeclaredMethod("recoverOutboxOperation", RemoteIncidentStore.class,
				OutboxReviewPanel.Entry.class, OutboxReviewPanel.class, boolean.class);
		method.setAccessible(true);
		SwingUtilities.invokeAndWait(() -> {
			Timer timer = answerDialog(discard ? "Discard pending edit" : "Retry rejected edit", response);
			try {
				method.invoke(frame, remote, entry, emptyPanel(), discard);
			} catch (ReflectiveOperationException failure) {
				throw new AssertionError(failure);
			} finally {
				timer.stop();
			}
		});
	}

	/**
	 * Answers the next matching confirmation on the event thread.
	 *
	 * @param title confirmation title
	 * @param response response to select
	 * @return running timer
	 */
	private static Timer answerDialog(String title, int response) {
		Timer timer = new Timer(20, event -> {
			for (Window window : Window.getWindows()) {
				if (window instanceof javax.swing.JDialog dialog && dialog.isShowing()
						&& dialog.getTitle().equals(title)) {
					((Timer) event.getSource()).stop();
					((JOptionPane) dialog.getContentPane().getComponent(0)).setValue(response);
				}
			}
		});
		timer.start();
		return timer;
	}

	/**
	 * Creates an unattached panel for invoking frame recovery methods.
	 *
	 * @return panel with inert callbacks
	 */
	private static OutboxReviewPanel emptyPanel() {
		return new OutboxReviewPanel(new OutboxReviewPanel.Actions() {
			/** {@inheritDoc} */
			@Override
			public void exportDraft(OutboxReviewPanel.Entry value) {
			}

			/** {@inheritDoc} */
			@Override
			public void resolve(OutboxReviewPanel.Entry value, List<OutboxReviewPanel.Decision> decisions) {
			}

			/** {@inheritDoc} */
			@Override
			public void refreshConflict(OutboxReviewPanel.Entry value) {
			}

			/** {@inheritDoc} */
			@Override
			public void confirm(OutboxReviewPanel.Entry value) {
			}
		});
	}

	/**
	 * Waits for asynchronous Swing work to complete.
	 *
	 * @param condition completion condition
	 * @throws Exception if the condition cannot be evaluated
	 */
	private static void await(CheckedCondition condition) throws Exception {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
		while (!condition.test() && System.nanoTime() < deadline) {
			Thread.sleep(20);
		}
		assertTrue(condition.test(), "Recovery did not complete");
	}

	/** Condition evaluated while waiting for an asynchronous recovery. */
	@FunctionalInterface
	private interface CheckedCondition {
		/**
		 * Evaluates the condition.
		 *
		 * @return whether the condition is satisfied
		 * @throws Exception if evaluation fails
		 */
		boolean test() throws Exception;
	}

	/**
	 * Retrieves the controller owned by a test window.
	 *
	 * @param frame window to inspect
	 * @return its controller
	 */
	private static AppController controller(MainFrame frame) {
		try {
			Field field = MainFrame.class.getDeclaredField("controller");
			field.setAccessible(true);
			return (AppController) field.get(frame);
		} catch (ReflectiveOperationException failure) {
			throw new AssertionError(failure);
		}
	}
}
