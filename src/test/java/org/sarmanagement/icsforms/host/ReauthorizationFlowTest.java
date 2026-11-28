/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.host;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.model.ClueLogEntry;
import org.sarmanagement.icsforms.model.Provenance;
import org.sarmanagement.icsforms.persistence.RemoteIncidentStore;
import org.sarmanagement.icsforms.persistence.RemoteAccessException;
import org.sarmanagement.icsforms.persistence.StoreValidationException;
import org.sarmanagement.icsforms.persistence.StoredIncident;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/** Runs the real client re-authorization flow against a real host with a real durable outbox. */
class ReauthorizationFlowTest {
	@TempDir
	Path directory;

	@Test
	void revokedDeviceReplaysItsRealOutboxOnlyAfterApprovalAndOnlyThatWorkNamesTheOldIdentity() throws Exception {
		try (HostFixture fixture = new HostFixture(directory.resolve("host"))) {
			fixture.start();
			DeviceCredential revoked = fixture.pair("Field phone");
			HostGateway gateway = new HostGateway(fixture.client(revoked), revoked, "test-host");
			try (RemoteIncidentStore remote = new RemoteIncidentStore(gateway, fixture.incident,
					directory.resolve("cache"))) {
				StoredIncident draft = remote.loadSnapshot(fixture.incident);
				fixture.session.revoke(revoked.deviceId());
				ClueLogEntry first = clue("Blue glove");
				draft.data().getClueLogEntries().add(first);
				try {
					remote.save(fixture.incident, draft.data(), draft.revision());
				} catch (RuntimeException expected) {
					// The revoked credential is refused; the edit must stay queued.
				}
				List<String> queued = remote.pendingOperations().stream()
						.map(RemoteIncidentStore.PendingOperation::operationId).toList();
				assertEquals(1, queued.size());

				AtomicBoolean cancelled = new AtomicBoolean();
				String invitation = fixture.session.issueInvitation();
				CompletableFuture<ReauthorizationFlow.Outcome> flow = CompletableFuture.supplyAsync(() -> {
					try {
						return ReauthorizationFlow.run(remote, gateway, invitation, "Field phone 2",
								cancelled::get, Duration.ofMillis(50), null);
					} catch (InterruptedException interrupted) {
						throw new IllegalStateException(interrupted);
					}
				});
				JsonNode request = awaitRequest(fixture);
				assertEquals(queued.get(0), request.path("outbox").get(0).path("operationId").asText());
				assertEquals("Blue glove", request.path("outbox").get(0).path("records").get(0).path("content")
						.path("description").asText());
				assertFalse(flow.isDone());
				assertEquals(1, remote.pendingOperations().size());
				assertTrue(fixture.store.loadSnapshot(fixture.incident).data().getClueLogEntries().isEmpty());

				fixture.session.approveReauthorization(request.path("requestId").asText());
				assertEquals(ReauthorizationFlow.Outcome.APPROVED, flow.get(20, TimeUnit.SECONDS));
				assertTrue(remote.pendingOperations().isEmpty());

				ClueLogEntry replayed = fixture.store.loadSnapshot(fixture.incident).data().getClueLogEntries().get(0);
				assertNotEquals(revoked.deviceId(), replayed.getProvenance().deviceId());
				assertEquals(revoked.deviceId(), replayed.getProvenance().originalDeviceId());

				StoredIncident later = remote.loadSnapshot(fixture.incident);
				ClueLogEntry second = clue("Red scarf");
				later.data().getClueLogEntries().add(second);
				remote.save(fixture.incident, later.data(), later.revision());
				remote.replayPending();
				ClueLogEntry fresh = fixture.store.loadSnapshot(fixture.incident).data().getClueLogEntries().stream()
						.filter(entry -> entry.getEntryId().equals(second.getEntryId())).findFirst().orElseThrow();
				assertNotEquals(revoked.deviceId(), fresh.getProvenance().deviceId());
				assertNull(fresh.getProvenance().originalDeviceId());
			}
		}
	}

	@Test
	void deniedAndCancelledRequestsLeaveTheOutboxUntouched() throws Exception {
		try (HostFixture fixture = new HostFixture(directory.resolve("host"))) {
			fixture.start();
			DeviceCredential expired = fixture.pair("Tablet");
			HostGateway gateway = new HostGateway(fixture.client(expired), expired, "test-host");
			try (RemoteIncidentStore remote = new RemoteIncidentStore(gateway, fixture.incident,
					directory.resolve("cache"))) {
				StoredIncident draft = remote.loadSnapshot(fixture.incident);
				fixture.clock.advance(Duration.ofHours(73));
				draft.data().getClueLogEntries().add(clue("Boot print"));
				try {
					remote.save(fixture.incident, draft.data(), draft.revision());
				} catch (RuntimeException expectedExpiry) {
					// Expired credential; the edit stays queued.
				}

				assertEquals(1, remote.pendingOperations().size());

				AtomicBoolean cancelled = new AtomicBoolean();
				Supplier<ReauthorizationFlow.Outcome> waiting = () -> {
					try {
						return ReauthorizationFlow.run(remote, gateway, null, "Tablet", cancelled::get,
								Duration.ofMillis(50), null);
					} catch (InterruptedException interrupted) {
						throw new IllegalStateException(interrupted);
					}
				};
				CompletableFuture<ReauthorizationFlow.Outcome> cancelledFlow = CompletableFuture.supplyAsync(waiting);
				awaitRequest(fixture);
				cancelled.set(true);
				assertEquals(ReauthorizationFlow.Outcome.CANCELLED, cancelledFlow.get(20, TimeUnit.SECONDS));
				assertEquals(1, fixture.session.reauthorizationRequests().size());
				assertEquals(1, remote.pendingOperations().size());

				fixture.session.denyReauthorization(
						fixture.session.reauthorizationRequests().get(0).path("requestId").asText());
				cancelled.set(false);
				CompletableFuture<ReauthorizationFlow.Outcome> deniedFlow = CompletableFuture.supplyAsync(waiting);
				JsonNode second = awaitRequest(fixture);
				fixture.session.denyReauthorization(second.path("requestId").asText());
				assertEquals(ReauthorizationFlow.Outcome.DENIED, deniedFlow.get(20, TimeUnit.SECONDS));
				assertEquals(1, remote.pendingOperations().size());
				assertTrue(fixture.store.loadSnapshot(fixture.incident).data().getClueLogEntries().isEmpty());
			}
		}
	}


	@Test
	void postRequestEditsStayBlockedAcrossRestartAndCanBeExported() throws Exception {
		try (HostFixture fixture = new HostFixture(directory.resolve("host"))) {
			fixture.start();
			DeviceCredential revoked = fixture.pair("Field phone");
			HostGateway gateway = new HostGateway(fixture.client(revoked), revoked, "test-host");
			Path cache = directory.resolve("cache");
			try (RemoteIncidentStore remote = new RemoteIncidentStore(gateway, fixture.incident, cache)) {
				StoredIncident initial = remote.loadSnapshot(fixture.incident);
				initial.data().getClueLogEntries().add(clue("Reviewed"));
				remote.save(fixture.incident, initial.data(), initial.revision());
				fixture.session.revoke(revoked.deviceId());
				assertThrows(RemoteAccessException.class, remote::replayPending);
				String invitation = fixture.session.issueInvitation();
				CompletableFuture<ReauthorizationFlow.Outcome> flow = CompletableFuture.supplyAsync(() -> {
					try {
						return ReauthorizationFlow.run(remote, gateway, invitation, "Replacement", () -> false,
								Duration.ofMillis(50), null);
					} catch (InterruptedException interrupted) {
						throw new IllegalStateException(interrupted);
					}
				});
				JsonNode request = awaitRequest(fixture);
				StoredIncident later = remote.loadSnapshot(fixture.incident);
				later.data().getClueLogEntries().add(clue("Not reviewed"));
				remote.save(fixture.incident, later.data(), later.revision());
				fixture.session.approveReauthorization(request.path("requestId").asText());
				assertEquals(ReauthorizationFlow.Outcome.APPROVED, flow.get(20, TimeUnit.SECONDS));
				assertEquals(1, fixture.store.loadSnapshot(fixture.incident).data().getClueLogEntries().size());
				assertTrue(remote.hasUnreviewedPending());
				assertEquals(1, remote.pendingOperations().size());
				Path export = directory.resolve("recovery.json");
				remote.exportOutbox(export);
				assertTrue(java.nio.file.Files.readString(export).contains("Not reviewed"));
				StoreValidationException replace = assertThrows(StoreValidationException.class,
						() -> remote.replaceRejected(remote.pendingOperations().get(0).operationId()));
				assertTrue(replace.getMessage().contains("held work"));
				StoreValidationException split = assertThrows(StoreValidationException.class,
						() -> remote.splitRejected(remote.pendingOperations().get(0).operationId()));
				assertTrue(split.getMessage().contains("not reviewed"));
				StoredIncident further = remote.loadSnapshot(fixture.incident);
				further.data().getClueLogEntries().add(clue("Also held"));
				remote.save(fixture.incident, further.data(), further.revision());
			}
			try (RemoteIncidentStore reopened = RemoteIncidentStore.openOffline(List.of(cache),
					gateway.authorityFingerprint().orElseThrow(), fixture.incident)) {
				assertTrue(reopened.hasUnreviewedPending());
				assertEquals(2, reopened.pendingOperations().size());
				reopened.exportOutbox(directory.resolve("reopened-recovery.json"));
				assertTrue(java.nio.file.Files.readString(directory.resolve("reopened-recovery.json"))
						.contains("Also held"));
			}
		}
	}

	@Test
	void connectedOnlyStoreCanAttachAnApprovedReplacement() throws Exception {
		try (HostFixture fixture = new HostFixture(directory.resolve("host"))) {
			fixture.start();
			DeviceCredential revoked = fixture.pair("Phone");
			HostGateway gateway = new HostGateway(fixture.client(revoked), revoked, "test-host");
			try (RemoteIncidentStore remote = new RemoteIncidentStore(gateway, fixture.incident)) {
				fixture.session.revoke(revoked.deviceId());
				String invitation = fixture.session.issueInvitation();
				CompletableFuture<ReauthorizationFlow.Outcome> flow = CompletableFuture.supplyAsync(() -> {
					try {
						return ReauthorizationFlow.run(remote, gateway, invitation, "Replacement", () -> false,
								Duration.ofMillis(50), null);
					} catch (InterruptedException interrupted) {
						throw new IllegalStateException(interrupted);
					}
				});
				JsonNode request = awaitRequest(fixture);
				fixture.session.approveReauthorization(request.path("requestId").asText());
				assertEquals(ReauthorizationFlow.Outcome.APPROVED, flow.get(20, TimeUnit.SECONDS));
				assertEquals(fixture.incident.value(), remote.loadSnapshot(fixture.incident).data().getIncidentId());
			}
		}
	}

	/**
	 * Waits for the host to hold exactly one pending request.
	 *
	 * @param fixture running host
	 * @return the pending request
	 * @throws InterruptedException when interrupted while waiting
	 */
	private static JsonNode awaitRequest(HostFixture fixture) throws InterruptedException {
		for (int attempt = 0; attempt < 200; attempt++) {
			List<JsonNode> pending = fixture.session.reauthorizationRequests();
			if (!pending.isEmpty()) {
				return pending.get(0);
			}
			Thread.sleep(50);
		}
		throw new AssertionError("no re-authorization request arrived");
	}

	/**
	 * Creates a clue with fixed text.
	 *
	 * @param description clue description
	 * @return new clue
	 */
	private static ClueLogEntry clue(String description) {
		ClueLogEntry clue = new ClueLogEntry();
		clue.setLocation("North ridge");
		clue.setDescription(description);
		clue.setProvenance(Provenance.local("Operator", "Field device", Instant.parse("2026-01-01T00:00:00Z")));
		return clue;
	}
}
