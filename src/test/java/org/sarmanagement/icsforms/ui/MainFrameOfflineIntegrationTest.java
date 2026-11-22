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
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.List;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.model.AppData;
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
