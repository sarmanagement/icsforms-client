/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.host;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.model.ClueLogEntry;
import org.sarmanagement.icsforms.model.Provenance;
import org.sarmanagement.icsforms.persistence.IncidentId;
import org.sarmanagement.icsforms.persistence.IncidentService;
import org.sarmanagement.icsforms.persistence.ReauthorizationOutbox;
import org.sarmanagement.icsforms.persistence.RemoteIncidentGateway;
import org.sarmanagement.icsforms.persistence.RemoteIncidentStore;
import org.sarmanagement.icsforms.persistence.StoreUnavailableException;
import org.sarmanagement.icsforms.persistence.StoredIncident;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * One automated multi-instance run of the host-mode validation exercise: one host, two remote clients with real
 * durable outboxes, and every failure the release checklist names, in sequence, against the same incident.
 */
class HostReleaseExerciseTest {
	@TempDir
	Path directory;

	@Test
	void twoClientsSurviveInterruptionRestartRevocationRestoreAndExpiry() throws Exception {
		Path hostDirectory = directory.resolve("host");
		Path backup = directory.resolve("backup.zip");
		HostFixture host = new HostFixture(hostDirectory);
		HostFixture restarted = null;
		try {
			host.start();
			IncidentId incident = host.incident;
			DeviceCredential tabletA = host.pair("Tablet A");
			DeviceCredential tabletB = host.pair("Tablet B");

			// Invalid credentials: a spent invitation cannot pair a second device.
			String invitation = host.session.issueInvitation();
			HostClient.pair(HostInvitation.parse(invitation), "First use");
			assertThrows(RuntimeException.class, () -> HostClient.pair(HostInvitation.parse(invitation), "Reuse"));

			LosingGateway lossy = new LosingGateway(new HostGateway(host.client(tabletA), tabletA, "host"));
			try (RemoteIncidentStore clientA = new RemoteIncidentStore(lossy, incident, directory.resolve("cacheA"));
					RemoteIncidentStore clientB = new RemoteIncidentStore(
							new HostGateway(host.client(tabletB), tabletB, "host"), incident,
							directory.resolve("cacheB"))) {

				// Concurrent saves from two clients both land.
				ExecutorService pool = Executors.newFixedThreadPool(2);
				try {
					Future<?> fromA = pool.submit(() -> enter(clientA, incident, "concurrent A"));
					Future<?> fromB = pool.submit(() -> enter(clientB, incident, "concurrent B"));
					fromA.get(30, TimeUnit.SECONDS);
					fromB.get(30, TimeUnit.SECONDS);
				} finally {
					pool.shutdownNow();
				}
				assertEquals(1, count(host, "concurrent A"));
				assertEquals(1, count(host, "concurrent B"));

				// An interrupted response is replayed idempotently.
				lossy.lose = true;
				enter(clientA, incident, "lost reply", false);
				assertThrows(StoreUnavailableException.class, clientA::replayPending);
				clientA.replayPending();
				assertEquals(1, count(host, "lost reply"));
				assertTrue(clientA.pendingOperations().isEmpty());

				// B is revoked and keeps working offline, then the host is backed up and stopped.
				StoredIncident openOnB = clientB.loadSnapshot(incident);
				host.session.revoke(tabletB.deviceId());
				enterFrom(clientB, incident, openOnB, "revoked work", true);
				assertEquals(1, clientB.pendingOperations().size());
				assertEquals(0, count(host, "revoked work"));
				HostBackup.create(host.store, incident, host.session.storage(), backup);
				host.session.stop();
				enter(clientA, incident, "while host down", true);
				assertEquals(1, clientA.pendingOperations().size());

				// Restore and restart: A is still admitted, B's revocation is still in force.
				HostBackup.restore(host.store, incident, host.session.storage(), backup);
				host.close();
				restarted = new HostFixture(hostDirectory, incident);
				restarted.start();
				assertEquals(HostConnectionState.CONNECTED, restarted.client(tabletA).diagnose(tabletA));
				assertEquals(HostConnectionState.CREDENTIAL_REVOKED, restarted.client(tabletB).diagnose(tabletB));

				clientA.attachGateway(new HostGateway(restarted.client(tabletA), tabletA, "host"));
				clientA.replayPending();
				assertEquals(1, count(restarted, "while host down"));

				// B re-pairs under a new identity; only the queued work names the old one.
				HostGateway oldB = new HostGateway(restarted.client(tabletB), tabletB, "host");
				assertEquals(ReauthorizationFlow.Outcome.APPROVED,
						reauthorize(restarted, clientB, oldB, "Tablet B (replacement)"));
				assertTrue(clientB.pendingOperations().isEmpty());
				ClueLogEntry recovered = find(restarted, "revoked work");
				assertNotEquals(tabletB.deviceId(), recovered.getProvenance().deviceId());
				assertEquals(tabletB.deviceId(), recovered.getProvenance().originalDeviceId());
				enter(clientB, incident, "after repair", true);
				ClueLogEntry later = find(restarted, "after repair");
				assertNull(later.getProvenance().originalDeviceId());

				// A's credential expires and is renewed with the same identity.
				StoredIncident openOnA = clientA.loadSnapshot(incident);
				restarted.clock.advance(Duration.ofHours(73));
				enterFrom(clientA, incident, openOnA, "expired work", true);
				assertEquals(1, clientA.pendingOperations().size());
				assertEquals(0, count(restarted, "expired work"));
				// The renewed certificate is dated by the simulated host clock, so TLS (which uses real time) cannot
				// carry the replay; the exchange itself, the identity and the retained outbox are what is checked.
				HostGateway expiredA = new HostGateway(restarted.client(tabletA), tabletA, "host");
				assertEquals("expired", expiredA.credentialState());
				HostClient.ReauthorizationTicket ticket = expiredA.requestRenewal("Tablet A",
						ReauthorizationOutbox.describe(clientA.pendingOperations()));
				JsonNode renewal = restarted.session.reauthorizationRequests().get(0);
				assertEquals(clientA.pendingOperations().get(0).operationId(),
						renewal.path("outbox").get(0).path("operationId").asText());
				restarted.session.approveReauthorization(renewal.path("requestId").asText());
				HostClient.ReauthorizationResult renewed = expiredA.pollReauthorization(ticket);
				assertEquals("approved", renewed.state());
				assertEquals(tabletA.deviceId(), renewed.credential().deviceId());
				assertTrue(renewed.credential().expiresAt().isAfter(tabletA.expiresAt()));
				assertEquals(1, clientA.pendingOperations().size());
				assertEquals(0, count(restarted, "expired work"));

				for (String description : List.of("concurrent A", "concurrent B", "lost reply", "while host down",
						"revoked work", "after repair")) {
					assertEquals(1, count(restarted, description), description);
				}
			}
		} finally {
			if (restarted != null) {
				restarted.close();
			}
			host.close();
		}
	}

	/**
	 * Enters a clue and replays, tolerating a host that refuses or is unreachable.
	 *
	 * @param client client store
	 * @param incident incident identity
	 * @param description clue description
	 */
	private static void enter(RemoteIncidentStore client, IncidentId incident, String description) {
		enter(client, incident, description, true);
	}

	/**
	 * Enters a clue, optionally replays, and tolerates a host that refuses or is unreachable.
	 *
	 * @param client client store
	 * @param incident incident identity
	 * @param description clue description
	 * @param replay whether to attempt replay after queueing
	 */
	private static void enter(RemoteIncidentStore client, IncidentId incident, String description, boolean replay) {
		StoredIncident snapshot = null;
		try {
			snapshot = client.loadSnapshot(incident);
		} catch (RuntimeException unreachable) {
			return;
		}
		enterFrom(client, incident, snapshot, description, replay);
	}

	/**
	 * Enters a clue into a snapshot taken earlier, as an operator would who loaded the incident before the host
	 * became unavailable, and tolerates a host that refuses or is unreachable.
	 *
	 * @param client client store
	 * @param incident incident identity
	 * @param snapshot snapshot the operator is editing
	 * @param description clue description
	 * @param replay whether to attempt replay after queueing
	 */
	private static void enterFrom(RemoteIncidentStore client, IncidentId incident, StoredIncident snapshot,
			String description, boolean replay) {
		try {
			ClueLogEntry clue = new ClueLogEntry();
			clue.setLocation("Staging");
			clue.setDescription(description);
			clue.setProvenance(Provenance.local("Operator", "Field device", Instant.parse("2026-01-01T00:00:00Z")));
			snapshot.data().getClueLogEntries().add(clue);
			client.save(incident, snapshot.data(), snapshot.revision());
			if (replay) {
				client.replayPending();
			}
		} catch (RuntimeException expected) {
			// The edit stays in the durable outbox; the caller asserts on host state.
		}
	}

	/**
	 * Runs the client flow and approves the request as the administrator.
	 *
	 * @param host running host
	 * @param client client store with queued work
	 * @param gateway current gateway for that client
	 * @param name replacement device name
	 * @return the flow outcome
	 * @throws Exception when the flow fails or times out
	 */
	private static ReauthorizationFlow.Outcome reauthorize(HostFixture host, RemoteIncidentStore client,
			HostGateway gateway, String name) throws Exception {
		String invitation = host.session.issueInvitation();
		CompletableFuture<ReauthorizationFlow.Outcome> flow = CompletableFuture.supplyAsync(() -> {
			try {
				return ReauthorizationFlow.run(client, gateway, invitation, name, () -> false, Duration.ofMillis(50),
						null);
			} catch (InterruptedException interrupted) {
				throw new IllegalStateException(interrupted);
			}
		});
		for (int attempt = 0; attempt < 200 && host.session.reauthorizationRequests().isEmpty(); attempt++) {
			Thread.sleep(50);
		}
		JsonNode request = host.session.reauthorizationRequests().get(0);
		host.session.approveReauthorization(request.path("requestId").asText());
		return flow.get(30, TimeUnit.SECONDS);
	}

	/**
	 * Counts accepted clues with a description.
	 *
	 * @param host running host
	 * @param description clue description
	 * @return number of matching accepted clues
	 */
	private static long count(HostFixture host, String description) {
		return host.store.loadSnapshot(host.incident).data().getClueLogEntries().stream()
				.filter(entry -> description.equals(entry.getDescription())).count();
	}

	/**
	 * Finds one accepted clue.
	 *
	 * @param host running host
	 * @param description clue description
	 * @return the clue
	 */
	private static ClueLogEntry find(HostFixture host, String description) {
		return host.store.loadSnapshot(host.incident).data().getClueLogEntries().stream()
				.filter(entry -> description.equals(entry.getDescription())).findFirst().orElseThrow();
	}

	/** Gateway that applies a batch on the host, then loses the response once when asked. */
	private static final class LosingGateway implements RemoteIncidentGateway {
		private final RemoteIncidentGateway delegate;
		private boolean lose;

		/**
		 * Wraps a gateway.
		 *
		 * @param delegate real host gateway
		 */
		LosingGateway(RemoteIncidentGateway delegate) {
			this.delegate = delegate;
		}

		/** {@inheritDoc} */
		@Override
		public Optional<String> authorityFingerprint() {
			return delegate.authorityFingerprint();
		}

		/** {@inheritDoc} */
		@Override
		public String location() {
			return delegate.location();
		}

		/** {@inheritDoc} */
		@Override
		public IncidentService.ReadResult read(IncidentId id) {
			return delegate.read(id);
		}

		/** {@inheritDoc} */
		@Override
		public IncidentService.ChangeResult changesSince(IncidentId id, long cursor) {
			return delegate.changesSince(id, cursor);
		}

		/** {@inheritDoc} */
		@Override
		public IncidentService.SubmissionResult submit(IncidentService.RecordBatch batch) {
			IncidentService.SubmissionResult result = delegate.submit(batch);
			if (lose) {
				lose = false;
				throw new StoreUnavailableException("Simulated lost response");
			}
			return result;
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
