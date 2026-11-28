/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.awt.GraphicsEnvironment;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.host.HostGateway;
import org.sarmanagement.icsforms.model.ActivityLogEntry;
import org.sarmanagement.icsforms.model.ClueLogEntry;
import org.sarmanagement.icsforms.model.Ics214Form;
import org.sarmanagement.icsforms.model.Provenance;
import org.sarmanagement.icsforms.model.TCard;
import org.sarmanagement.icsforms.pdf.PdfExportService;
import org.sarmanagement.icsforms.persistence.IncidentId;
import org.sarmanagement.icsforms.persistence.RemoteIncidentGateway;
import org.sarmanagement.icsforms.persistence.RemoteIncidentStore;
import org.sarmanagement.icsforms.persistence.StoreUnavailableException;
import org.sarmanagement.icsforms.validation.IncidentValidator;

/** Exercises durable offline work and duplicate review across killed client JVMs and a real host. */
class ChildJvmOfflineAcceptanceTest {
	@TempDir
	Path directory;

	@Test
	void killedClientsRecoverDependentWorkLostResponseAndReviewDuplicates() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		exercise(true);
	}

	@Test
	void killedHeadlessClientsRecoverDependentWorkAndLostResponse() throws Exception {
		exercise(false);
	}

	/**
	 * Runs the restart scenario with displayed controllers or headless stores.
	 *
	 * @param displayed whether to exercise Swing and duplicate review
	 * @throws Exception if a phase fails
	 */
	private void exercise(boolean displayed) throws Exception {
		try (HostedIncidentFixture host = new HostedIncidentFixture(directory.resolve("host"), "Shared")) {
			try (RemoteIncidentStore seed = host.pair()) {
				var snapshot = seed.loadSnapshot(host.incident());
				Ics214Form log = new Ics214Form();
				ActivityLogEntry activity = new ActivityLogEntry();
				activity.setNotableActivity("Original");
				log.getActivityLog().add(activity);
				snapshot.data().getActivityLogs().add(log);
				TCard card = new TCard();
				card.setPersonName("Original responder");
				snapshot.data().getTCards().add(card);
				seed.save(host.incident(), snapshot.data(), snapshot.revision());
			}
			if (displayed) {
				RemoteIncidentStore loopback = host.session().loopbackStore();
				var initialData = loopback.loadSnapshot(host.incident()).data();
				AtomicReference<MainFrame> initial = new AtomicReference<>();
				SwingUtilities.invokeAndWait(() -> initial.set(new MainFrame(initialData, loopback, host.incident(),
						new PdfExportService(), new IncidentValidator(), directory)));
				try {
					controller(initial.get()).save();
				} finally {
					close(initial.get());
				}
			}

			Path cacheA = directory.resolve("cache-a");
			Path cacheB = directory.resolve("cache-b");
			String prefix = displayed ? "" : "headless-";
			String[] a = runAndKill(host, "create-a", cacheA, "-", prefix + "create-a").split("\\|");
			String[] b = runAndKill(host, "create-b", cacheB, "-", prefix + "create-b").split("\\|");
			assertEquals(a[1], b[1]);
			assertFalse(a[2].equals(b[2]));
			assertTrue(host.backing().load(host.incident()).getClueLogEntries().isEmpty());

			String[] lost = runAndKill(host, "lose", cacheA, a[1], prefix + "lose").split("\\|");
			assertEquals(a[3], lost[3]);
			assertEquals(1, host.backing().load(host.incident()).getClueLogEntries().size(),
					"the first batch reached the host before its response was lost");
			runAndKill(host, "replay-b", cacheB, b[1], prefix + "replay");
			runAndKill(host, "replay-a", cacheA, a[1], prefix + "replay", a[3]);
			runAndKill(host, "receipt-a", cacheA, a[1], prefix + "check", "2");
			runAndKill(host, "receipt-b", cacheB, b[1], prefix + "check", "1");
			var data = host.backing().load(host.incident());
			assertEquals(2, data.getClueLogEntries().size());
			assertEquals("Activity from A", data.getActivityLogs().get(0).getActivityLog().get(0).getNotableActivity());
			assertEquals("Planning from A", data.getForm202().getCommandEmphasis());
			assertTrue(data.getTCards().stream().anyMatch(card -> "Responder from B".equals(card.getPersonName())));
			assertEquals("dependent edit", data.getClueLogEntries().stream()
					.filter(clue -> a[2].equals(clue.getEntryId())).findFirst().orElseThrow().getFollowUp());
			assertTrue(data.getClueLogEntries().stream().noneMatch(ClueLogEntry::isStruckOut));

			if (!displayed) {
				return;
			}
			runAndKill(host, "review", cacheA, a[1], "review", a[2], b[2]);
			var reviewed = host.backing().load(host.incident()).getClueLogEntries();
			assertEquals(2, reviewed.size());
			ClueLogEntry retained = reviewed.stream().filter(clue -> a[2].equals(clue.getEntryId()))
					.findFirst().orElseThrow();
			ClueLogEntry duplicate = reviewed.stream().filter(clue -> b[2].equals(clue.getEntryId()))
					.findFirst().orElseThrow();
			assertFalse(retained.isStruckOut());
			assertTrue(duplicate.isStruckOut());
			assertNotNull(duplicate.getDuplicateConfirmation());
			assertEquals(retained.getEntryId(), duplicate.getDuplicateConfirmation().duplicateOfId());
			assertEquals("Ridge", duplicate.getLocation());
			assertEquals("Blue pack", duplicate.getDescription());
			assertEquals("Operator B", duplicate.getProvenance().operatorName());
			runAndKill(host, "verify-review", cacheB, b[1], "verify-review", a[2], b[2]);
		}
	}

	/**
	 * Starts a client, waits for its durable checkpoint, then kills it without closing its store.
	 *
	 * @param host running host
	 * @param name unique output file name
	 * @param cache client journal directory
	 * @param fingerprint cached authority or a placeholder for initial pairing
	 * @param mode child action
	 * @param ids optional clue identities
	 * @return checkpoint line
	 * @throws Exception if the child fails or does not reach the checkpoint
	 */
	private String runAndKill(HostedIncidentFixture host, String name, Path cache, String fingerprint, String mode,
			String... ids) throws Exception {
		Path output = directory.resolve(name + ".log");
		var command = new java.util.ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
				"-Djava.io.tmpdir=" + directory, "-Djava.awt.headless=" + mode.startsWith("headless-"),
				"-Dicsforms.testMode=true", "-Dicsforms.discovery=none",
				"-cp", System.getProperty("java.class.path"), Client.class.getName(), mode, cache.toString(),
				host.incident().value(), fingerprint, host.session().issueInvitation(), directory.toString()));
		command.addAll(List.of(ids));
		Process child = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(output.toFile()).start();
		try {
			long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
			while (System.nanoTime() < deadline) {
				String text = Files.readString(output);
				for (String line : text.split("\\R")) {
					if (line.startsWith("READY|")) {
						return line;
					}
					if (line.startsWith("Exception in thread \"main\"")) {
						throw new AssertionError("child failed: " + text);
					}
				}
				assertTrue(child.isAlive(), () -> "child exited before checkpoint: " + text);
				Thread.sleep(50);
			}
			throw new AssertionError("child timed out: " + Files.readString(output));
		} finally {
			child.destroyForcibly();
			assertTrue(child.waitFor(30, TimeUnit.SECONDS), "child did not terminate");
		}
	}

	/**
	 * Returns the controller of a displayed frame.
	 *
	 * @param frame displayed frame
	 * @return controller
	 */
	private static AppController controller(MainFrame frame) {
		return (AppController) Ics205PanelTest.field(frame, "controller");
	}

	/**
	 * Closes an initialization frame.
	 *
	 * @param frame displayed frame
	 * @throws Exception if the event thread fails
	 */
	private static void close(MainFrame frame) throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			controller(frame).close();
			controller(frame).closeStore();
			frame.dispose();
		});
	}

	/** Executed in a separate JVM and deliberately left open for abrupt termination. */
	public static final class Client {
		/**
		 * Runs one client phase and signals after its journal or receipt is durable.
		 *
		 * @param args phase, cache, incident, fingerprint, invitation, UI directory and optional clue IDs
		 * @throws Exception if the phase fails
		 */
		public static void main(String[] args) throws Exception {
			boolean headless = args[0].startsWith("headless-");
			String mode = headless ? args[0].substring("headless-".length()) : args[0];
			Path cache = Path.of(args[1]);
			IncidentId incident = new IncidentId(args[2]);
			RemoteIncidentStore paired = HostGateway.pair(args[4], "acceptance-" + mode);
			RemoteIncidentGateway gateway = paired.detachGateway();
			paired.close();
			String fingerprint = gateway.authorityFingerprint().orElseThrow();
			RemoteIncidentStore store;
			if (mode.startsWith("create")) {
				store = new RemoteIncidentStore(gateway, incident, cache);
				store.loadSnapshot(incident);
				store.detachGateway().close();
			} else {
				assertEquals(args[3], fingerprint);
				store = RemoteIncidentStore.openOffline(cache, fingerprint, incident);
				if ("lose".equals(mode)) {
					gateway = loseResponse(gateway);
				}
				store.attachGateway(gateway);
			}
			String id = "-";
			String operationId = "-";
			if (mode.startsWith("create") && headless) {
				var snapshot = store.loadSnapshot(incident);
				ClueLogEntry clue = editData(snapshot.data(), mode);
				store.save(incident, snapshot.data(), snapshot.revision());
				if ("create-a".equals(mode)) {
					snapshot = store.loadSnapshot(incident);
					snapshot.data().getClueLogEntries().get(0).setFollowUp("dependent edit");
					store.save(incident, snapshot.data(), snapshot.revision());
				}
				assertEquals("create-a".equals(mode) ? 2 : 1, store.pendingOperations().size());
				id = clue.getEntryId();
				operationId = store.pendingOperations().get(0).operationId();
			} else if (mode.startsWith("create")) {
				AtomicReference<MainFrame> frame = new AtomicReference<>();
				SwingUtilities.invokeAndWait(() -> {
					frame.set(new MainFrame(store.loadSnapshot(incident).data(), store, incident,
							new PdfExportService(), new IncidentValidator(), Path.of(args[5])));
					frame.get().setVisible(true);
				});
				AtomicReference<String> clueId = new AtomicReference<>();
				SwingUtilities.invokeAndWait(() -> {
					ClueLogEntry clue = editData(controller(frame.get()).getData(), mode);
					clueId.set(clue.getEntryId());
					controller(frame.get()).markDirty();
				});
				controller(frame.get()).save();
				if ("create-a".equals(mode)) {
					SwingUtilities.invokeAndWait(() -> {
						controller(frame.get()).getData().getClueLogEntries().get(0).setFollowUp("dependent edit");
						controller(frame.get()).markDirty();
					});
					controller(frame.get()).save();
				}
				assertEquals("create-a".equals(mode) ? 2 : 1, store.pendingOperations().size());
				id = clueId.get();
				operationId = store.pendingOperations().get(0).operationId();
			} else if ("lose".equals(mode)) {
				assertEquals(2, store.pendingOperations().size());
				var draft = store.loadSnapshot(incident).data();
				assertEquals("Activity from A", draft.getActivityLogs().get(0).getActivityLog().get(0)
						.getNotableActivity());
				assertEquals("Planning from A", draft.getForm202().getCommandEmphasis());
				assertEquals("dependent edit", draft.getClueLogEntries().get(0).getFollowUp());
				org.junit.jupiter.api.Assertions.assertThrows(StoreUnavailableException.class, store::replayPending);
				assertEquals(2, store.pendingOperations().size());
				operationId = store.pendingOperations().get(0).operationId();
			} else if ("replay".equals(mode)) {
				assertFalse(store.pendingOperations().isEmpty());
				if (args.length > 6) {
					assertEquals(args[6], store.pendingOperations().get(0).operationId());
				}
				assertEquals("Blue pack", store.loadSnapshot(incident).data().getClueLogEntries().get(0)
						.getDescription());
				store.replayPending();
				assertTrue(store.pendingOperations().isEmpty());
				store.replayPending();
			} else if ("check".equals(mode)) {
				assertTrue(store.pendingOperations().isEmpty());
				assertEquals(Long.parseLong(args[6]), store.acceptedReceiptCount());
			} else if ("review".equals(mode) || "verify-review".equals(mode)) {
				assertTrue(store.pendingOperations().isEmpty());
				var snapshot = store.loadSnapshot(incident);
				AtomicReference<MainFrame> frame = new AtomicReference<>();
				SwingUtilities.invokeAndWait(() -> {
					frame.set(new MainFrame(snapshot.data(), store, incident, new PdfExportService(),
							new IncidentValidator(), Path.of(args[5])));
					frame.get().setVisible(true);
					ClueLogPanel panel = (ClueLogPanel) Ics205PanelTest.field(frame.get(), "clueLogPanel");
					var clues = controller(frame.get()).getData().getClueLogEntries();
					if ("review".equals(mode)) {
						assertEquals(1, ClueDuplicateReview.candidates(clues).size());
						assertTrue(panel.confirmDuplicate(args[7], args[6]));
					} else {
						assertTrue(ClueDuplicateReview.candidates(clues).isEmpty());
						ClueLogEntry duplicate = clues.stream().filter(clue -> args[7].equals(clue.getEntryId()))
								.findFirst().orElseThrow();
						assertTrue(duplicate.isStruckOut());
						assertEquals(args[6], duplicate.getDuplicateConfirmation().duplicateOfId());
					}
				});
				if ("review".equals(mode)) {
					controller(frame.get()).save();
					store.replayPending();
				}
				assertTrue(store.pendingOperations().isEmpty());
			}
			System.out.println("READY|" + fingerprint + "|" + id + "|" + operationId);
			System.out.flush();
			Thread.sleep(TimeUnit.MINUTES.toMillis(5));
		}

		/**
		 * Creates one independent clue and edits the client's other workflows.
		 *
		 * @param data local draft
		 * @param mode client identity
		 * @return the newly created clue
		 */
		private static ClueLogEntry editData(org.sarmanagement.icsforms.model.AppData data, String mode) {
			ClueLogEntry clue = new ClueLogEntry();
			clue.setLocation("Ridge");
			clue.setDescription("Blue pack");
			clue.setProvenance(Provenance.local("create-a".equals(mode) ? "Operator A" : "Operator B",
					"Station " + mode, java.time.Instant.parse("2026-01-01T00:00:00Z")));
			data.getClueLogEntries().add(clue);
			if ("create-a".equals(mode)) {
				data.getActivityLogs().get(0).getActivityLog().get(0).setNotableActivity("Activity from A");
				data.getForm202().setCommandEmphasis("Planning from A");
			} else {
				data.getTCards().stream().filter(card -> "Original responder".equals(card.getPersonName()))
						.findFirst().orElseThrow().setPersonName("Responder from B");
			}
			return clue;
		}

		/**
		 * Forwards a real accepted submission and drops exactly its first response.
		 *
		 * @param gateway real HTTPS gateway
		 * @return gateway simulating a lost reply
		 */
		private static RemoteIncidentGateway loseResponse(RemoteIncidentGateway gateway) {
			var lost = new java.util.concurrent.atomic.AtomicBoolean();
			return (RemoteIncidentGateway) Proxy.newProxyInstance(RemoteIncidentGateway.class.getClassLoader(),
					new Class<?>[] {RemoteIncidentGateway.class}, (proxy, method, args) -> {
						try {
							Object result = method.invoke(gateway, args);
							if ("submit".equals(method.getName()) && lost.compareAndSet(false, true)) {
								throw new StoreUnavailableException("Simulated lost response");
							}
							return result;
						} catch (InvocationTargetException failure) {
							throw failure.getCause();
						}
					});
		}
	}
}
