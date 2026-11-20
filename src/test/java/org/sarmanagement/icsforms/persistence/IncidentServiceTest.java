/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.IncidentContext;
import org.sarmanagement.icsforms.model.OpenActionItem;
import org.sarmanagement.icsforms.model.RecordIndex;

import java.nio.file.Path;
import java.util.List;

/** Covers record-level merge, idempotency, and durable history behavior. */
class IncidentServiceTest {
	@TempDir
	Path directory;

	@Test
	void mergesDisjointFieldsAndReplaysReceiptAfterRestart() {
		IncidentId incident;
		IncidentService service;
		IncidentService.ReadResult initial;
		try (FileIncidentStore store = new FileIncidentStore(directory)) {
			incident = store.create(new AppData());
			service = service(store);
			initial = service.read(incident);
			IncidentService.RecordSubmission first = update(initial, "incidentName", "Mine");
			IncidentService.SubmissionResult firstResult = service.submit(batch(incident, "operation-one", first));

			IncidentService.RecordSubmission second = update(initial, "currentUser", "Theirs");
			IncidentService.SubmissionResult secondResult = service.submit(batch(incident, "operation-two", second));

			assertTrue(firstResult.applied());
			assertTrue(secondResult.applied());
			RecordIndex.RecordKey contextKey = contextKey(initial);
			IncidentService.VersionedRecord merged = record(service.read(incident), contextKey);
			assertEquals("Mine", merged.content().path("incidentName").asText());
			assertEquals("Theirs", merged.content().path("currentUser").asText());
			AppData projected = store.loadSnapshot(incident).data();
			assertEquals("Mine", projected.getIncidentContext().getIncidentName());
			assertEquals("Theirs", projected.getIncidentContext().getCurrentUser());

			try (FileIncidentStore restartedStore = new FileIncidentStore(directory)) {
				IncidentService restarted = service(restartedStore);
				assertEquals(secondResult,
						restarted.submit(batch(incident, "operation-two", second)));
				IncidentService.ChangeResult replayChanges = restarted.changesSince(incident, initial.cursor());
				assertEquals(secondResult.cursor(), replayChanges.cursor(), replayChanges.changes().toString());
			}
		}
	}

	@Test
	void createsRecordInTheExistingDocumentGraph() {
		try (FileIncidentStore store = new FileIncidentStore(directory)) {
			IncidentId incident = store.create(new AppData());
			IncidentService service = service(store);
			AppData submittedDocument = new AppData();
			OpenActionItem submittedRecord = new OpenActionItem();
			submittedRecord.setItem("Check access");
			submittedDocument.getOpenActionItems().add(submittedRecord);
			RecordIndex submittedIndex = RecordIndex.of(submittedDocument);
			RecordIndex.RecordKey key = submittedIndex.records().keySet().stream()
					.filter(recordKey -> recordKey.type().equals(OpenActionItem.class.getName()))
					.findFirst().orElseThrow();
			JsonNode content = submittedIndex.record(key);
			IncidentService.RecordSubmission create = new IncidentService.RecordSubmission(key, null,
					submittedIndex.parentFieldOf(key), 0, IncidentService.Operation.CREATE, content);

			IncidentService.SubmissionResult result = service.submit(batch(incident, "create-action", create));

			assertTrue(result.applied());
			assertEquals("Check access", store.loadSnapshot(incident).data().getOpenActionItems().get(0).getItem());
			assertEquals(1, record(service.read(incident), key).version());
		}
	}

	@Test
	void reportsFieldConflictAndReplaysOriginalConflict() {
		try (FileIncidentStore store = new FileIncidentStore(directory)) {
			IncidentId incident = store.create(new AppData());
			IncidentService service = service(store);
			IncidentService.ReadResult initial = service.read(incident);
			IncidentService.RecordSubmission host = update(initial, "incidentName", "Host");
			service.submit(batch(incident, "host-write", host));

			IncidentService.RecordSubmission client = update(initial, "incidentName", "Client");
			IncidentService.RecordBatch batch = batch(incident, "conflict-operation", client);
			IncidentService.SubmissionResult result = service.submit(batch);

			assertFalse(result.applied());
			assertEquals(List.of("incidentName"), result.conflicts().stream()
					.map(IncidentService.FieldConflict::field).toList());
			assertEquals(result, service.submit(batch));
			assertEquals("Host", record(service.read(incident), contextKey(initial)).content()
					.path("incidentName").asText());
			try (FileIncidentStore restartedStore = new FileIncidentStore(directory)) {
				assertEquals(result, service(restartedStore).submit(batch));
			}
		}
	}

	@Test
	void acceptsSameValueAndTreatsHostRevertAsNetUnchanged() {
		try (FileIncidentStore store = new FileIncidentStore(directory)) {
			IncidentId incident = store.create(new AppData());
			IncidentService service = service(store);
			IncidentService.ReadResult initial = service.read(incident);
			IncidentService.RecordSubmission hostChange = update(initial, "incidentName", "Temporary");
			IncidentService.SubmissionResult changed = service.submit(batch(incident, "host-change", hostChange));
			assertTrue(changed.applied());
			IncidentService.ReadResult intermediate = service.read(incident);
			IncidentService.RecordSubmission hostRevert = update(intermediate, "incidentName", "");
			service.submit(batch(incident, "host-revert", hostRevert));

			IncidentService.RecordSubmission client = update(initial, "incidentName", "Client value");
			IncidentService.SubmissionResult result = service.submit(batch(incident, "reverted-host", client));
			assertTrue(result.applied());

			IncidentService.ReadResult current = service.read(incident);
			IncidentService.RecordSubmission sameValue = update(initial, "incidentName", "Client value");
			IncidentService.SubmissionResult sameResult = service.submit(batch(incident, "same-value", sameValue));
			assertTrue(sameResult.applied());
			assertEquals(current.cursor(), sameResult.cursor());
			assertEquals("Client value",
					record(service.read(incident), contextKey(initial)).content().path("incidentName").asText());
		}
	}

	@Test
	void failedBatchDoesNotApplyEarlierRecordChanges() {
		try (FileIncidentStore store = new FileIncidentStore(directory)) {
			IncidentId incident = store.create(new AppData());
			IncidentService service = service(store);
			IncidentService.ReadResult initial = service.read(incident);
			IncidentService.RecordSubmission valid = update(initial, "incidentName", "Must roll back");
			ObjectNode unknownContent = (ObjectNode) valid.content();
			unknownContent.put("id", "unknown");
			IncidentService.RecordSubmission unknown = new IncidentService.RecordSubmission(
					new RecordIndex.RecordKey(IncidentContext.class.getName(), "unknown"), null, null, 1,
					IncidentService.Operation.UPDATE, unknownContent);

			assertThrows(StoreValidationException.class,
					() -> service.submit(batch(incident, "invalid-batch", valid, unknown)));
			assertEquals("", record(service.read(incident), contextKey(initial)).content()
					.path("incidentName").asText());
		}
	}

	@Test
	void rejectsImmutableIdentityChangesAndCreateOfExistingRecord() {
		try (FileIncidentStore store = new FileIncidentStore(directory)) {
			IncidentId incident = store.create(new AppData());
			IncidentService service = service(store);
			IncidentService.ReadResult initial = service.read(incident);
			RecordIndex.RecordKey contextKey = contextKey(initial);
			ObjectNode changedId = (ObjectNode) record(initial, contextKey).content();
			changedId.put("id", "replacement");
			IncidentService.RecordSubmission identityViolation = new IncidentService.RecordSubmission(contextKey,
					null, null, 1, IncidentService.Operation.UPDATE, changedId);
			assertThrows(StoreValidationException.class,
					() -> service.submit(batch(incident, "immutable-id", identityViolation)));

			JsonNode existing = record(initial, contextKey).content();
			IncidentService.RecordSubmission duplicate = new IncidentService.RecordSubmission(contextKey, null,
					"incidentContext", 0, IncidentService.Operation.CREATE, existing);
			IncidentService.SubmissionResult result = service.submit(batch(incident, "duplicate-create", duplicate));
			assertFalse(result.applied());
			assertEquals("$record", result.conflicts().get(0).field());
		}
	}

	@Test
	void synchronizingStandaloneSaveAppendsRecordVersion() {
		try (FileIncidentStore store = new FileIncidentStore(directory)) {
			AppData data = new AppData();
			IncidentId incident = store.create(data);
			IncidentService service = service(store);
			IncidentService.ReadResult initial = service.read(incident);
			data.getIncidentContext().setIncidentName("Standalone edit");
			store.save(incident, data, store.revisionToken(data));

			RecordIndex.RecordKey contextKey = contextKey(initial);
			IncidentService.VersionedRecord context = record(service.read(incident), contextKey);
			assertEquals(2, context.version());
			assertEquals("Standalone edit", context.content().path("incidentName").asText());
			assertTrue(service.changesSince(incident, initial.cursor()).changes().stream()
					.anyMatch(change -> change.key().equals(contextKey)));
		}
	}

	private IncidentService service(FileIncidentStore store) {
		return new IncidentService(store, new FileRecordVersionStore(directory.resolve("incidents")));
	}

	private IncidentService.RecordSubmission update(IncidentService.ReadResult initial, String field, String value) {
		RecordIndex.RecordKey contextKey = contextKey(initial);
		IncidentService.VersionedRecord current = record(initial, contextKey);
		ObjectNode content = (ObjectNode) current.content();
		content.put(field, value);
		return new IncidentService.RecordSubmission(contextKey, null, null, current.version(),
				IncidentService.Operation.UPDATE, content);
	}

	private IncidentService.RecordBatch batch(IncidentId incident, String operationId,
			IncidentService.RecordSubmission... submissions) {
		return new IncidentService.RecordBatch(incident, operationId, List.of(submissions));
	}

	private RecordIndex.RecordKey contextKey(IncidentService.ReadResult read) {
		return read.records().stream().map(IncidentService.VersionedRecord::key)
				.filter(key -> key.type().equals(IncidentContext.class.getName()))
				.findFirst().orElseThrow();
	}

	private IncidentService.VersionedRecord record(IncidentService.ReadResult read, RecordIndex.RecordKey key) {
		return read.records().stream().filter(record -> record.key().equals(key)).findFirst().orElseThrow();
	}
}
