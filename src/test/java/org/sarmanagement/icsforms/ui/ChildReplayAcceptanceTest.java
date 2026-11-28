/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.host.HostGateway;
import org.sarmanagement.icsforms.model.ClueLogEntry;
import org.sarmanagement.icsforms.model.ActivityLogEntry;
import org.sarmanagement.icsforms.model.Ics214Form;
import org.sarmanagement.icsforms.model.TCard;
import org.sarmanagement.icsforms.persistence.IncidentId;
import org.sarmanagement.icsforms.persistence.IncidentService;
import org.sarmanagement.icsforms.persistence.RemoteIncidentGateway;
import org.sarmanagement.icsforms.persistence.RemoteIncidentStore;
import org.sarmanagement.icsforms.persistence.StoreUnavailableException;

/** Exercises a durable outbox across forcibly terminated client JVMs and a real host. */
class ChildReplayAcceptanceTest {
	@TempDir
	Path directory;

	@Test
	void killedClientRestartsReplaysOrderedDependentEditsAndRestartsAgain() throws Exception {
		try (HostedIncidentFixture host = new HostedIncidentFixture(directory.resolve("host"), "Restart")) {
			Path cache = directory.resolve("cache");
			String queued = runChild(host, cache, "queue", "", true);
			String[] identities = queued.split(" ");
			assertEquals(4, identities.length);
			assertFalse(identities[0].equals(identities[1]));
			assertEquals(0, host.backing().load(host.incident()).getClueLogEntries().size());

			assertEquals(queued, runChild(host, cache, "lost", queued, true));
			var acceptedBeforeRetry = host.backing().load(host.incident());
			assertEquals("Created offline", acceptedBeforeRetry.getClueLogEntries().get(0).getDescription());
			assertEquals("Offline activity", acceptedBeforeRetry.getActivityLogs().get(0)
					.getActivityLog().get(0).getNotableActivity());
			assertEquals("Offline responder", acceptedBeforeRetry.getTCards().get(0).getPersonName());
			assertEquals("Offline planning", acceptedBeforeRetry.getForm202().getCommandEmphasis());
			String replayed = runChild(host, cache, "replay", queued, true);
			assertEquals(queued, replayed);
			var clues = host.backing().load(host.incident()).getClueLogEntries();
			assertEquals(1, clues.size());
			assertEquals(identities[2], clues.get(0).getEntryId());
			assertEquals("Edited after restart", clues.get(0).getDescription());

			assertEquals(queued, runChild(host, cache, "verify", queued, false));
			assertEquals(1, host.backing().load(host.incident()).getClueLogEntries().size());
		}
	}

	/**
	 * Starts a client, waits for its durable checkpoint, and optionally kills it without cleanup.
	 * @param host running host
	 * @param cache shared client journal directory
	 * @param phase child action
	 * @param expected identities from the initial checkpoint
	 * @param kill whether to terminate the process at the checkpoint
	 * @return checkpoint identities
	 * @throws Exception if the process fails or times out
	 */
	private static String runChild(HostedIncidentFixture host, Path cache, String phase, String expected,
			boolean kill) throws Exception {
		Process child = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
				"-Djava.awt.headless=true", "-Djava.io.tmpdir=" + System.getProperty("java.io.tmpdir"),
				"-cp", System.getProperty("java.class.path"),
				ClientProcess.class.getName(), phase, cache.toString(), host.incident().value(), expected)
				.redirectErrorStream(true).start();
		CompletableFuture<String> checkpoint = CompletableFuture.supplyAsync(() -> {
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(child.getInputStream(),
					StandardCharsets.UTF_8))) {
				List<String> output = new ArrayList<>();
				String line;
				while ((line = reader.readLine()) != null) {
					output.add(line);
					if (line.startsWith("CHECKPOINT ")) {
						return line.substring("CHECKPOINT ".length());
					}
				}
				throw new IllegalStateException("Child exited before checkpoint: " + output);
			} catch (java.io.IOException failure) {
				throw new IllegalStateException(failure);
			}
		});
		try {
			try (var input = new OutputStreamWriter(child.getOutputStream(), StandardCharsets.UTF_8)) {
				input.write(host.session().issueInvitation() + "\n");
			}
			String result = checkpoint.get(60, TimeUnit.SECONDS);
			if (kill) {
				assertTrue(child.isAlive());
				child.destroyForcibly();
			}
			assertTrue(child.waitFor(30, TimeUnit.SECONDS));
			if (!kill) {
				assertEquals(0, child.exitValue());
			}
			return result;
		} finally {
			if (child.isAlive()) {
				child.destroyForcibly();
				child.waitFor(30, TimeUnit.SECONDS);
			}
		}
	}

	/** Entry point run in a separate JVM; checkpoints deliberately leave the journal open. */
	public static final class ClientProcess {
		/** Prevents instantiation. */
		private ClientProcess() {
		}

		/**
		 * Pairs with the host and performs one restart phase.
		 * @param args phase, cache, incident and expected identities
		 * @throws Exception on a failed checkpoint
		 */
		public static void main(String[] args) throws Exception {
			String invitation = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)).readLine();
			IncidentId incident = new IncidentId(args[2]);
			RemoteIncidentStore paired = HostGateway.pair(invitation, "Restart test");
			RemoteIncidentGateway gateway = paired.detachGateway();
			paired.close();
			String fingerprint = gateway.authorityFingerprint().orElseThrow();
			RemoteIncidentStore store = args[0].equals("queue")
					? new RemoteIncidentStore(gateway, incident, Path.of(args[1]))
					: RemoteIncidentStore.openOffline(Path.of(args[1]), fingerprint, incident);
			try {
				String identities;
				if (args[0].equals("queue")) {
					var snapshot = store.loadSnapshot(incident);
					store.detachGateway().close();
					ClueLogEntry clue = new ClueLogEntry();
					clue.setDescription("Created offline");
					snapshot.data().getClueLogEntries().add(clue);
					Ics214Form log = new Ics214Form();
					ActivityLogEntry entry = new ActivityLogEntry();
					entry.setNotableActivity("Offline activity");
					log.getActivityLog().add(entry);
					snapshot.data().getActivityLogs().add(log);
					TCard card = new TCard();
					card.setPersonName("Offline responder");
					snapshot.data().getTCards().add(card);
					snapshot.data().getForm202().setCommandEmphasis("Offline planning");
					store.save(incident, snapshot.data(), snapshot.revision());
					var edited = store.loadSnapshot(incident);
					edited.data().getClueLogEntries().get(0).setDescription("Edited after restart");
					store.save(incident, edited.data(), edited.revision());
					var pending = store.pendingOperations();
					if (pending.size() != 2) {
						throw new IllegalStateException("Expected two queued operations: " + pending);
					}
					identities = pending.get(0).operationId() + " " + pending.get(1).operationId() + " "
							+ clue.getEntryId() + " " + fingerprint;
				} else {
					identities = args[3];
					String[] expected = identities.split(" ");
					if (args[0].equals("replay") || args[0].equals("lost")) {
						var pending = store.pendingOperations();
						if (pending.size() != 2 || !pending.get(0).operationId().equals(expected[0])
								|| !pending.get(1).operationId().equals(expected[1])) {
							throw new IllegalStateException("Queue identity or order changed: " + pending);
						}
						store.attachGateway(args[0].equals("lost") ? new LostResponseGateway(gateway) : gateway);
						if (args[0].equals("lost")) {
							try {
								store.replayPending();
								throw new IllegalStateException("Lost response was not observed");
							} catch (StoreUnavailableException expectedFailure) {
								if (store.pendingOperations().size() != 2) {
									throw new IllegalStateException("Ambiguous operation was removed", expectedFailure);
								}
							}
						} else {
							store.replayPending();
							store.replayPending();
						}
					} else {
						gateway.close();
					}
					if (!args[0].equals("lost") && (!store.pendingOperations().isEmpty()
							|| store.acceptedReceiptCount() != 2
							|| !store.loadSnapshot(incident).data().getClueLogEntries().get(0).getEntryId()
									.equals(expected[2]))) {
						throw new IllegalStateException("Replay state was not durable");
					}
				}
				System.out.println("CHECKPOINT " + identities);
				System.out.flush();
				if (!args[0].equals("verify")) {
					Thread.sleep(TimeUnit.MINUTES.toMillis(2));
				}
			} finally {
				store.close();
			}
		}

		/** Drops a response only after the actual host has accepted the submitted batch. */
		private static final class LostResponseGateway implements RemoteIncidentGateway {
			private final RemoteIncidentGateway delegate;

			/**
			 * Wraps an authenticated host gateway.
			 * @param delegate real host transport
			 */
			private LostResponseGateway(RemoteIncidentGateway delegate) {
				this.delegate = delegate;
			}

			/** {@inheritDoc} */
			@Override
			public java.util.Optional<String> authorityFingerprint() {
				return delegate.authorityFingerprint();
			}

			/** {@inheritDoc} */
			@Override
			public String location() {
				return delegate.location();
			}

			/** {@inheritDoc} */
			@Override
			public IncidentService.ReadResult read(IncidentId incident) {
				return delegate.read(incident);
			}

			/** {@inheritDoc} */
			@Override
			public IncidentService.ChangeResult changesSince(IncidentId incident, long after) {
				return delegate.changesSince(incident, after);
			}

			/** {@inheritDoc} */
			@Override
			public IncidentService.SubmissionResult submit(IncidentService.RecordBatch batch) {
				IncidentService.SubmissionResult result = delegate.submit(batch);
				if (!result.applied()) {
					throw new IllegalStateException("Host did not accept the batch");
				}
				throw new StoreUnavailableException("Simulated lost host response");
			}

			/** {@inheritDoc} */
			@Override
			public HostProbe probe() {
				return delegate.probe();
			}

			/** {@inheritDoc} */
			@Override
			public void close() {
				delegate.close();
			}
		}
	}
}
