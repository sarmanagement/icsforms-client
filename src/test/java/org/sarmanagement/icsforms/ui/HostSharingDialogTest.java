/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.host.CredentialState;
import org.sarmanagement.icsforms.host.DeviceCredential;
import org.sarmanagement.icsforms.host.HostClient;
import org.sarmanagement.icsforms.host.HostInvitation;
import org.sarmanagement.icsforms.host.HostState;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.persistence.FileIncidentStore;
import org.sarmanagement.icsforms.persistence.IncidentId;
import org.sarmanagement.icsforms.persistence.RemoteIncidentStore;

import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/** Exercises the host-sharing window: start/stop, invitation, devices, failure, backup and restore guards. */
class HostSharingDialogTest {
	@TempDir
	Path directory;

	/** Records prompts and answers them from a script. */
	private static final class ScriptedPrompts implements HostSharingDialog.Prompts {
		boolean answer = true;
		Path file;
		final List<String> confirmations = new ArrayList<>();
		final List<String> messages = new ArrayList<>();
		final List<Boolean> errors = new ArrayList<>();

		/** {@inheritDoc} */
		@Override
		public boolean confirm(Component parent, String title, String message) {
			confirmations.add(message);
			return answer;
		}

		/** {@inheritDoc} */
		@Override
		public void show(Component parent, String title, String message, boolean error) {
			messages.add(message);
			errors.add(error);
		}

		/** {@inheritDoc} */
		@Override
		public Path chooseFile(Component parent, String title, boolean save) {
			return file;
		}
	}

	/** Holds the objects every test needs. */
	private static final class Harness implements AutoCloseable {
		final FileIncidentStore store;
		final IncidentId incident;
		final JFrame owner = new JFrame();
		final ScriptedPrompts prompts = new ScriptedPrompts();
		final HostSharingDialog dialog;
		final AtomicReference<Instant> periodClose = new AtomicReference<>();
		final AtomicReference<Boolean> restored = new AtomicReference<>(false);
		final AtomicReference<RemoteIncidentStore> remote = new AtomicReference<>();
		final AtomicReference<Boolean> transitioning = new AtomicReference<>(false);
		final AtomicReference<Runnable> flush = new AtomicReference<>(() -> { });
		final AtomicReference<Runnable> switchLocal = new AtomicReference<>(() -> { });

		/**
		 * Creates the harness on the event thread.
		 *
		 * @param directory store directory
		 * @throws Exception when creation fails
		 */
		Harness(Path directory) throws Exception {
			store = new FileIncidentStore(directory);
			incident = store.create(new AppData());
			AtomicReference<HostSharingDialog> created = new AtomicReference<>();
			SwingUtilities.invokeAndWait(() -> {
				HostSharingDialog value = new HostSharingDialog(owner, store, incident, () -> flush.get().run(),
						periodClose::get, () -> restored.set(true));
				value.setPrompts(prompts);
				value.setStopGrace(java.time.Duration.ofMillis(50));
				value.configure("127.0.0.1", 0, 0);
				value.setCacheRoot(directory.resolve("remote-cache"));
				value.setStoreSwitch(loopback -> remote.set((RemoteIncidentStore) loopback),
						() -> switchLocal.get().run());
				value.setTransitionGuard(transitioning::set);
				created.set(value);
			});
			dialog = created.get();
		}

		/**
		 * Runs an action on the event thread.
		 *
		 * @param action action to run
		 * @throws Exception when it fails
		 */
		void edt(Runnable action) throws Exception {
			SwingUtilities.invokeAndWait(action);
		}

		/**
		 * Starts sharing and waits until the background start has completed on the event thread.
		 *
		 * @throws Exception when the wait is interrupted or times out
		 */
		void start() throws Exception {
			edt(dialog::startSharing);
			long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(120);
			boolean[] starting = {true};
			while (starting[0]) {
				edt(() -> starting[0] = dialog.isStarting());
				if (starting[0]) {
					assertTrue(System.nanoTime() < deadline, "host start did not complete");
					Thread.sleep(25);
				}
			}
		}

		/**
		 * Waits for an event-thread condition without blocking its asynchronous workers.
		 *
		 * @param condition condition evaluated on the event thread
		 * @throws Exception when dispatch fails or the wait times out
		 */
		void await(BooleanSupplier condition) throws Exception {
			long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(120);
			boolean[] done = {false};
			while (!done[0]) {
				edt(() -> done[0] = condition.getAsBoolean());
				if (!done[0]) {
					assertTrue(System.nanoTime() < deadline, "condition did not complete");
					Thread.sleep(25);
				}
			}
		}

		/**
		 * Issues an invitation and waits for the background request.
		 *
		 * @throws Exception when dispatch fails or the request times out
		 */
		void invite() throws Exception {
			edt(dialog::issueInvitation);
			await(() -> !dialog.invitationText().isBlank());
		}

		/** {@inheritDoc} */
		@Override
		public void close() throws Exception {
			if (dialog.session() != null) {
				dialog.session().stop();
				RemoteIncidentStore opened = remote.get();
				if (opened != null) {
					opened.close();
				}
			}
			SwingUtilities.invokeAndWait(() -> {
				dialog.dispose();
				owner.dispose();
			});
			store.close();
		}
	}

	@Test
	void fitsScreenAndKeepsContentsScrollable() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		try (Harness harness = new Harness(directory)) {
			harness.edt(() -> {
				GraphicsConfiguration screen = harness.dialog.getGraphicsConfiguration();
				Rectangle bounds = screen.getBounds();
				Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(screen);
				assertTrue(harness.dialog.getWidth() <= bounds.width - insets.left - insets.right);
				assertTrue(harness.dialog.getHeight() <= bounds.height - insets.top - insets.bottom);
				JScrollPane scroll = (JScrollPane) harness.dialog.getContentPane().getComponent(0);
				harness.dialog.setSize(new Dimension(400, 250));
				harness.dialog.validate();
				assertTrue(scroll.getVerticalScrollBar().getMaximum()
						> scroll.getVerticalScrollBar().getVisibleAmount());
			});
		}
	}

	@Test
	void periodCloseIsDerivedFromOperationalPeriodEnd() {
		AppData data = new AppData();
		assertNull(HostSharingDialog.periodCloseOf(data));
		data.getIncidentContext().setOperationalPeriodEnd(LocalDateTime.of(2026, 11, 21, 18, 0));
		assertEquals(LocalDateTime.of(2026, 11, 21, 18, 0).toInstant(ZoneOffset.UTC),
				HostSharingDialog.periodCloseOf(data));
		assertNull(HostSharingDialog.periodCloseOf(null));
	}

	@Test
	void closedOperationalPeriodIsExplainedBeforeHostingAndOpenOrUndefinedIsNot() {
		AppData data = new AppData();
		java.time.Instant now = java.time.Instant.parse("2026-11-22T12:00:00Z");
		assertNull(HostSharingDialog.periodClosedMessage(data, now));
		data.getIncidentContext().setOperationalPeriodEnd(LocalDateTime.of(2026, 11, 22, 18, 0));
		assertNull(HostSharingDialog.periodClosedMessage(data, now));
		String message = HostSharingDialog.periodClosedMessage(data, java.time.Instant.parse("2026-11-22T18:00:00Z"));
		org.junit.jupiter.api.Assertions.assertTrue(message.contains("2026-11-22 18:00 UTC"), message);
		assertNull(HostSharingDialog.periodClosedMessage(null, now));
	}

	@Test
	void startShowsInvitationCopiesItAndStopEndsSharing() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		try (Harness harness = new Harness(directory)) {
			harness.start();
			assertTrue(harness.dialog.isSharing());
			assertEquals(HostState.RUNNING, harness.dialog.session().state());
			harness.invite();
			String text = harness.dialog.invitationText();
			assertNotNull(HostInvitation.parse(text));
			String clipboard = Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor)
					.toString();
			assertEquals(text, clipboard);

			DeviceCredential device = HostClient.pair(HostInvitation.parse(text), "Tablet");
			assertNotNull(device);
			HostClient.forDevice("127.0.0.1", harness.dialog.session().status().port(),
					harness.dialog.session().status().enrollmentPort(), device).readIncident(harness.incident.value());
			harness.edt(harness.dialog::refresh);
			harness.await(() -> harness.dialog.deviceTable().getRowCount() == 1);
			assertEquals(1, harness.dialog.deviceTable().getRowCount());
			assertEquals("Tablet", harness.dialog.deviceTable().getValueAt(0, 0));

			harness.prompts.answer = false;
			CountDownLatch declined = new CountDownLatch(1);
			harness.edt(() -> {
				harness.dialog.stopSharing();
				declined.countDown();
			});
			assertTrue(declined.await(5, TimeUnit.SECONDS));
			assertTrue(harness.dialog.isSharing());

			harness.prompts.answer = true;
			CountDownLatch stopped = new CountDownLatch(1);
			harness.edt(() -> harness.dialog.stopAsync(stopped::countDown));
			assertTrue(stopped.await(15, TimeUnit.SECONDS), () -> "session=" + harness.dialog.session()
					+ ", transition=" + harness.transitioning.get() + ", messages=" + harness.prompts.messages);
			assertFalse(harness.dialog.isSharing());
			assertEquals("Not sharing", harness.dialog.statusText());
		}
	}

	@Test
	void stopWithConnectedDevicesWarnsFirst() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		try (Harness harness = new Harness(directory)) {
			harness.start();
			harness.invite();
			DeviceCredential device = HostClient.pair(HostInvitation.parse(harness.dialog.invitationText()), "Tablet");
			var status = harness.dialog.session().status();
			HostClient client = HostClient.forDevice("127.0.0.1", status.port(), status.enrollmentPort(), device);
			client.readIncident(harness.incident.value());
			harness.prompts.answer = false;
			harness.edt(harness.dialog::stopSharing);
			assertEquals(1, harness.prompts.confirmations.size());
			assertTrue(harness.prompts.confirmations.get(0).contains("1 device(s) are connected"));
			assertTrue(harness.dialog.isSharing());
		}
	}

	@Test
	void revokeMarksSelectedDeviceRevoked() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		try (Harness harness = new Harness(directory)) {
			harness.start();
			harness.invite();
			HostClient.pair(HostInvitation.parse(harness.dialog.invitationText()), "Tablet");
			harness.edt(harness.dialog::refresh);
			harness.await(() -> harness.dialog.deviceTable().getRowCount() == 1);
			harness.edt(() -> harness.dialog.deviceTable().setRowSelectionInterval(0, 0));
			harness.edt(harness.dialog::revokeSelected);
			harness.await(() -> harness.dialog.deviceTable().getRowCount() == 1
					&& CredentialState.REVOKED.toString().equals(harness.dialog.deviceTable().getValueAt(0, 1).toString()));
			assertEquals(CredentialState.REVOKED, harness.dialog.session().devices().get(0).state());
			assertEquals(CredentialState.REVOKED.toString(), harness.dialog.deviceTable().getValueAt(0, 1).toString());
		}
	}

	@Test
	void failedStartIsReportedAndLeavesIncidentUsable() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		try (Harness harness = new Harness(directory); ServerSocket busy = new ServerSocket(0)) {
			harness.edt(() -> harness.dialog.configure("127.0.0.1", busy.getLocalPort(), 0));
			harness.start();
			assertFalse(harness.dialog.isSharing());
			assertNull(harness.dialog.session());
			assertEquals(1, harness.prompts.messages.size());
			assertTrue(harness.prompts.errors.get(0));
			assertTrue(harness.prompts.messages.get(0).contains("could not be started"));
			assertEquals("Not sharing", harness.dialog.statusText());
			AppData data = harness.store.load(harness.incident);
			harness.store.save(harness.incident, data);
		}
	}

	@Test
	void periodCloseSourceCapsCredentialsIssuedWhileSharing() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		try (Harness harness = new Harness(directory)) {
			Instant close = Instant.now().plusSeconds(3600);
			harness.periodClose.set(close);
			harness.start();
			harness.invite();
			DeviceCredential first = HostClient.pair(HostInvitation.parse(harness.dialog.invitationText()), "A");
			assertFalse(first.expiresAt().isAfter(close));
			Instant moved = Instant.now().plusSeconds(1800);
			harness.periodClose.set(moved);
			harness.edt(harness.dialog::refresh);
			String firstInvitation = harness.dialog.invitationText();
			harness.edt(harness.dialog::issueInvitation);
			harness.await(() -> !firstInvitation.equals(harness.dialog.invitationText()));
			DeviceCredential second = HostClient.pair(HostInvitation.parse(harness.dialog.invitationText()), "B");
			assertFalse(second.expiresAt().isAfter(moved));
		}
	}

	@Test
	void pendingLoopbackWorkBlocksStopUntilReplayedAndJournalIsReleased() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		try (Harness harness = new Harness(directory)) {
			harness.start();
			RemoteIncidentStore remote = harness.remote.get();
			var snapshot = remote.loadSnapshot(harness.incident);
			var gateway = remote.detachGateway();
			snapshot.data().getIncidentContext().setIncidentName("Offline host draft");
			remote.save(harness.incident, snapshot.data(), snapshot.revision());
			assertEquals(1, remote.pendingOperations().size());

			CountDownLatch stopped = new CountDownLatch(1);
			harness.edt(() -> harness.dialog.stopAsync(stopped::countDown));
			harness.await(() -> !harness.transitioning.get() && !harness.prompts.messages.isEmpty());
			assertTrue(harness.dialog.isSharing());
			assertEquals(1, stopped.getCount());
			assertTrue(harness.prompts.messages.get(0).contains("unsynchronized"));
			assertEquals(1, remote.pendingOperations().size());

			remote.attachGateway(gateway);
			remote.replayPending();
			assertTrue(remote.pendingOperations().isEmpty());
			harness.edt(() -> harness.dialog.stopAsync(stopped::countDown));
			assertTrue(stopped.await(30, TimeUnit.SECONDS));
			assertFalse(harness.dialog.isSharing());
			try (RemoteIncidentStore reopened = RemoteIncidentStore.openOffline(directory.resolve("remote-cache"),
					gateway.authorityFingerprint().orElseThrow(), harness.incident)) {
				assertTrue(reopened.pendingOperations().isEmpty());
				assertEquals("Offline host draft", reopened.loadSnapshot(harness.incident).data()
						.getIncidentContext().getIncidentName());
			}
		}
	}

	@Test
	void stopKeepsHostingWhenFlushOrLocalSwitchFails() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		try (Harness harness = new Harness(directory)) {
			harness.start();
			harness.flush.set(() -> {
				assertTrue(SwingUtilities.isEventDispatchThread());
				assertTrue(harness.transitioning.get());
				throw new IllegalStateException("flush failed");
			});
			CountDownLatch stopped = new CountDownLatch(1);
			harness.edt(() -> harness.dialog.stopAsync(stopped::countDown));
			assertTrue(harness.dialog.isSharing());
			assertFalse(harness.transitioning.get());
			assertTrue(harness.prompts.messages.get(0).contains("flush failed"));

			harness.flush.set(() -> { });
			harness.switchLocal.set(() -> {
				assertTrue(SwingUtilities.isEventDispatchThread());
				assertTrue(harness.transitioning.get());
				assertEquals(HostState.STOPPING, harness.dialog.session().state());
				throw new IllegalStateException("switch failed");
			});
			harness.edt(() -> harness.dialog.stopAsync(stopped::countDown));
			harness.await(() -> !harness.transitioning.get() && harness.prompts.messages.size() == 2);
			assertTrue(harness.dialog.isSharing());
			assertEquals(HostState.RUNNING, harness.dialog.session().state());
			assertTrue(harness.prompts.messages.get(1).contains("switch failed"));
			assertEquals(1, stopped.getCount());
		}
	}

	@Test
	void backupWarnsWritesFileAndRestoreIsRefusedWhileSharing(@TempDir Path elsewhere) throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		try (Harness harness = new Harness(directory)) {
			Path backup = elsewhere.resolve("host-backup.zip");
			assertNull(harness.dialog.backupTo(backup));
			assertTrue(java.nio.file.Files.size(backup) > 0);
			if (System.getProperty("os.name").toLowerCase().contains("win")) {
				return;
			}
			assertEquals(java.util.Set.of(java.nio.file.attribute.PosixFilePermission.OWNER_READ,
					java.nio.file.attribute.PosixFilePermission.OWNER_WRITE),
					java.nio.file.Files.getPosixFilePermissions(backup));
			assertTrue(harness.dialog.backupTo(backup).contains("already exists"));
			assertTrue(harness.dialog.backupTo(directory.resolve("inside.zip")).contains("outside"));

			harness.start();
			assertEquals(HostSharingDialog.RESTORE_WHILE_SHARING, harness.dialog.restoreFrom(backup));
			assertFalse(harness.restored.get());
			assertTrue(harness.dialog.isSharing());
		}
	}

	@Test
	void restoreWhenStoppedReloadsAndReportsCorruptBackups(@TempDir Path elsewhere) throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		try (Harness harness = new Harness(directory)) {
			Path backup = elsewhere.resolve("host-backup.zip");
			assertNull(harness.dialog.backupTo(backup));
			assertNull(harness.dialog.restoreFrom(backup));
			assertTrue(harness.restored.get());

			Path bogus = elsewhere.resolve("bogus.zip");
			java.nio.file.Files.writeString(bogus, "not a zip");
			String problem = harness.dialog.restoreFrom(bogus);
			assertNotNull(problem);
			assertFalse(problem.isBlank());
		}
	}
}
