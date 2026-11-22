/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.host;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.ClueLogEntry;
import org.sarmanagement.icsforms.model.Provenance;
import org.sarmanagement.icsforms.persistence.StoredIncident;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Covers administrator-approved device renewal and revoked-device replacement. */
class ReauthorizationTest {
	@TempDir
	Path directory;

	@Test
	void expiredRenewalKeepsIdentityAndKeyUntilApproved() {
		try (HostFixture fixture = new HostFixture(directory)) {
			fixture.start();
			DeviceCredential expired = fixture.pair("Renewed");
			fixture.clock.advance(Duration.ofHours(73));
			HostClient enrollment = enrollmentClient(fixture);
			HostClient.ReauthorizationTicket ticket = enrollment.requestRenewal(expired, "Renewed", outbox());

			assertEquals("waiting_for_approval", enrollment.pollReauthorization(ticket).state());
			assertEquals("expired", enrollment.credentialState(expired));
			JsonNode queued = fixture.session.reauthorizationRequests().get(0);
			assertEquals("queued operation", queued.path("outbox").get(0).path("description").asText());

			fixture.session.approveReauthorization(ticket.requestId());
			HostClient.ReauthorizationResult approved = enrollment.pollReauthorization(ticket);

			assertEquals("approved", approved.state());
			assertEquals(expired.deviceId(), approved.credential().deviceId());
			assertEquals(expired.keyPair().getPrivate().getEncoded().length,
					approved.credential().keyPair().getPrivate().getEncoded().length);
			assertTrue(java.util.Arrays.equals(expired.keyPair().getPrivate().getEncoded(),
					approved.credential().keyPair().getPrivate().getEncoded()));
			assertEquals("active", enrollment.credentialState(approved.credential()));
			assertFalse(fixture.session.storage().registry().approvalAudit().isEmpty());
		}
	}

	@Test
	void revokedRepairNeedsApprovalAndKeepsBothDeviceIdentities(@TempDir Path backupDirectory) {
		try (HostFixture fixture = new HostFixture(directory)) {
			StoredIncident snapshot = fixture.store.loadSnapshot(fixture.incident);
			AppData data = snapshot.data();
			ClueLogEntry clue = new ClueLogEntry();
			clue.setDescription("Queued clue");
			clue.setProvenance(Provenance.local("Operator", "Field device", Instant.parse("2026-01-01T00:00:00Z")));
			data.getClueLogEntries().add(clue);
			fixture.store.save(fixture.incident, data, snapshot.revision());
			fixture.start();
			DeviceCredential revoked = fixture.pair("Original");
			fixture.session.revoke(revoked.deviceId());
			HostInvitation invitation = HostInvitation.parse(fixture.session.issueInvitation());
			HostClient enrollment = enrollmentClient(fixture);
			HostClient.ReauthorizationTicket ticket = HostClient.requestRepair(invitation, revoked.deviceId(),
					"Replacement", outbox());

			assertEquals("waiting_for_approval", enrollment.pollReauthorization(ticket).state());
			assertEquals("revoked", enrollment.credentialState(revoked));
			assertEquals("queued operation",
					fixture.session.reauthorizationRequests().get(0).path("outbox").get(0).path("description").asText());
			fixture.session.approveReauthorization(ticket.requestId());
			DeviceCredential replacement = enrollment.pollReauthorization(ticket).credential();

			assertNotEquals(revoked.deviceId(), replacement.deviceId());
			assertEquals("revoked", enrollment.credentialState(revoked));
			assertEquals(revoked.deviceId(), fixture.session.storage().registry().devices().stream()
					.filter(device -> device.deviceId().equals(replacement.deviceId())).findFirst()
					.orElseThrow().originalDeviceId());
			assertTrue(fixture.session.reauthorizationRequests().isEmpty());

			HostStatus status = fixture.session.status();
			HostClient replacementClient = HostClient.forDevice("127.0.0.1", status.port(), status.enrollmentPort(),
					replacement);
			JsonNode read = replacementClient.readIncident(fixture.incident.value());
			JsonNode clueRecord = recordsOfType(read, ClueLogEntry.class.getName()).get(0);
			ObjectNode content = ((ObjectNode) clueRecord.path("content")).deepCopy();
			content.put("description", "Accepted after approval");
			ObjectNode provenance = (ObjectNode) content.path("provenance");
			provenance.put("deviceId", "forged-device");
			provenance.put("originalDeviceId", "forged-source");
			provenance.put("acceptedAt", "2000-01-01T00:00:00Z");
			ObjectNode duplicateConfirmation = content.putObject("duplicateConfirmation");
			duplicateConfirmation.put("duplicateOfId", "reviewed-clue");
			duplicateConfirmation.put("confirmedAt", "2026-01-01T00:00:00Z");
			ObjectNode nestedProvenance = duplicateConfirmation.putObject("provenance");
			nestedProvenance.put("operatorName", "Reviewer");
			nestedProvenance.put("enteredAt", "2026-01-01T00:00:00Z");
			nestedProvenance.put("deviceId", "forged-nested-device");
			nestedProvenance.put("originalDeviceId", "forged-nested-source");
			nestedProvenance.put("acceptedAt", "2000-01-01T00:00:00Z");
			ObjectNode submission = HostHttp.MAPPER.createObjectNode();
			submission.set("key", clueRecord.path("key"));
			submission.put("baseVersion", clueRecord.path("version").asLong());
			submission.put("operation", "UPDATE");
			submission.set("content", content);
			ObjectNode batch = HostHttp.MAPPER.createObjectNode();
			batch.put("operationId", "approved-repair-operation");
			batch.putArray("records").add(submission);
			assertTrue(replacementClient.submit(fixture.incident.value(), batch.toString()).path("applied").asBoolean());
			JsonNode accepted = recordsOfType(replacementClient.readIncident(fixture.incident.value()),
					ClueLogEntry.class.getName()).get(0).path("content").path("provenance");
			assertEquals(replacement.deviceId(), accepted.path("deviceId").asText());
			assertEquals(revoked.deviceId(), accepted.path("originalDeviceId").asText());
			assertEquals(fixture.clock.instant().toString(), accepted.path("acceptedAt").asText());
			JsonNode nestedAccepted = recordsOfType(replacementClient.readIncident(fixture.incident.value()),
					ClueLogEntry.class.getName()).get(0).path("content").path("duplicateConfirmation")
					.path("provenance");
			assertEquals(replacement.deviceId(), nestedAccepted.path("deviceId").asText());
			assertEquals(revoked.deviceId(), nestedAccepted.path("originalDeviceId").asText());
			assertEquals(fixture.clock.instant().toString(), nestedAccepted.path("acceptedAt").asText());

			Path backup = backupDirectory.resolve("host-backup.zip");
			HostBackup.create(fixture.store, fixture.incident, fixture.session.storage(), backup);
			fixture.session.revoke(replacement.deviceId());
			fixture.session.stop();
			HostBackup.restore(fixture.store, fixture.incident, fixture.session.storage(), backup);
			fixture.session = new HostSession(fixture.store, fixture.incident, fixture.config(), null);
			fixture.start();
			assertEquals(HostConnectionState.CREDENTIAL_REVOKED,
					fixture.client(revoked).diagnose(revoked));
			assertEquals(HostConnectionState.CONNECTED,
					fixture.client(replacement).diagnose(replacement));
			assertEquals(revoked.deviceId(), fixture.session.storage().registry().devices().stream()
					.filter(device -> device.deviceId().equals(replacement.deviceId())).findFirst()
					.orElseThrow().originalDeviceId());
		}
	}

	@Test
	void renewalRequestsAreRateLimitedAndPendingQueueIsDurable(@TempDir Path backupDirectory) {
		try (HostFixture fixture = new HostFixture(directory)) {
			fixture.start();
			DeviceCredential expired = fixture.pair("Renewed");
			fixture.clock.advance(Duration.ofHours(73));
			HostClient enrollment = enrollmentClient(fixture);
			HostClient.ReauthorizationTicket ticket = enrollment.requestRenewal(expired, "Renewed", outbox());
			assertThrows(HostClientException.class, () -> enrollment.requestRenewal(expired, "Renewed", outbox()));
			assertThrows(HostClientException.class, () -> enrollment.requestRenewal(expired, "Renewed", outbox()));
			HostClientException limited = assertThrows(HostClientException.class,
					() -> enrollment.requestRenewal(expired, "Renewed", outbox()));
			assertEquals(HostClientException.Kind.RATE_LIMITED, limited.kind());
			assertEquals(1, fixture.session.reauthorizationRequests().size());

			fixture.session.denyReauthorization(ticket.requestId());
			assertEquals("denied", enrollment.pollReauthorization(ticket).state());
			assertEquals(1, fixture.session.storage().registry().approvalAudit().size());
		}
	}

	/**
	 * Creates a pinned unauthenticated enrollment client for a running fixture.
	 *
	 * @param fixture running host
	 * @return enrollment client
	 */
	private static HostClient enrollmentClient(HostFixture fixture) {
		HostStatus status = fixture.session.status();
		return HostClient.anonymous("127.0.0.1", status.port(), status.enrollmentPort(), status.hostFingerprint());
	}

	/**
	 * Builds a small outbox with recognizable content for the host administrator.
	 *
	 * @return pending work array
	 */
	private static ArrayNode outbox() {
		ObjectNode operation = HostHttp.MAPPER.createObjectNode();
		operation.put("description", "queued operation");
		return HostHttp.MAPPER.createArrayNode().add(operation);
	}

	/**
	 * Selects record snapshots by type name.
	 *
	 * @param document host read response
	 * @param type record type
	 * @return matching records
	 */
	private static List<JsonNode> recordsOfType(JsonNode document, String type) {
		java.util.ArrayList<JsonNode> matching = new java.util.ArrayList<>();
		for (JsonNode record : document.path("records")) {
			if (type.equals(record.path("key").path("type").asText())) {
				matching.add(record);
			}
		}
		return matching;
	}
}
