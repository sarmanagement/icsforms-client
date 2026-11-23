/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.model.ActivityLogEntry;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.IncidentContext;
import org.sarmanagement.icsforms.model.Ics214Form;
import org.sarmanagement.icsforms.model.OpenActionItem;
import org.sarmanagement.icsforms.model.OrgChartEntry;
import org.sarmanagement.icsforms.model.RecordIndex;
import org.sarmanagement.icsforms.model.VetFacility;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Covers record-level merge, idempotency, and durable history behavior. */
class IncidentServiceTest {
	@TempDir
	Path directory;

	@Test
	void readsIncidentWhoseVetFacilityHasNoRoutedDuration() {
		AppData data = new AppData();
		VetFacility facility = new VetFacility();
		facility.setName("Unrouted clinic");
		facility.setDistance(40233.6, Double.NaN, "straight-line", "test", java.time.Instant.EPOCH, 0L);
		data.getVetDirectory().getFacilities().add(facility);
		try (FileIncidentStore store = new FileIncidentStore(directory)) {
			IncidentId incident = store.create(data);
			IncidentService service = service(store);
			service.read(incident);
			StoredIncident stored = store.loadSnapshot(incident);
			store.save(incident, stored.data(), stored.revision());
			IncidentService.ReadResult again = service.read(incident);
			assertFalse(again.records().isEmpty());
		}
	}

	@Test
	void rereadsIncidentWhoseOrgChartListsAgencyRepresentatives() {
		AppData data = new AppData();
		OrgChartEntry representative = new OrgChartEntry();
		representative.setSection(OrgChartEntry.Section.COMMAND_STAFF);
		representative.setTitle("Agency Representative");
		representative.setName("Casey Lee");
		data.getOrganizationalChart().getAssignmentList().getAgencyRepresentatives().add(representative);
		try (FileIncidentStore store = new FileIncidentStore(directory)) {
			IncidentId incident = store.create(data);
			IncidentService service = service(store);
			service.read(incident);
			IncidentService.ReadResult again = service.read(incident);
			assertTrue(again.records().stream()
					.anyMatch(record -> record.key().type().equals(OrgChartEntry.class.getName())));
			AppData projected = store.loadSnapshot(incident).data();
			List<OrgChartEntry> kept = projected.getOrganizationalChart().getAssignmentList()
					.getAgencyRepresentatives();
			assertEquals(1, kept.size());
			assertEquals("Casey Lee", kept.get(0).getName());
			assertEquals(representative.getId(), kept.get(0).getId());
		}
	}

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
	void acceptsOnlyMineChangedAndPreservesOnlyTheirsChanged() {
		try (FileIncidentStore store = new FileIncidentStore(directory)) {
			IncidentId incident = store.create(new AppData());
			IncidentService service = service(store);
			IncidentService.ReadResult initial = service.read(incident);
			RecordIndex.RecordKey key = contextKey(initial);

			IncidentService.SubmissionResult mine = service.submit(batch(incident, "mine-only",
					update(initial, "incidentName", "Mine")));
			assertTrue(mine.applied());
			assertEquals("Mine", record(service.read(incident), key).content().path("incidentName").asText());

			service.submit(batch(incident, "theirs-change", update(initial, "currentUser", "Host")));
			long currentVersion = record(service.read(incident), key).version();
			IncidentService.SubmissionResult theirsOnly = service.submit(batch(incident, "theirs-only",
					update(initial, "currentUser", "")));

			assertTrue(theirsOnly.applied());
			assertEquals(currentVersion, record(service.read(incident), key).version());
			assertEquals("Host", record(service.read(incident), key).content().path("currentUser").asText());
		}
	}

	@Test
	void acceptsSubmissionWhoseBaseIsCurrent() {
		try (FileIncidentStore store = new FileIncidentStore(directory)) {
			IncidentId incident = store.create(new AppData());
			IncidentService service = service(store);
			IncidentService.ReadResult current = service.read(incident);
			IncidentService.RecordSubmission update = update(current, "incidentName", "Current base");

			IncidentService.SubmissionResult result = service.submit(batch(incident, "current-base", update));

			assertTrue(result.applied());
			assertEquals("Current base",
					record(service.read(incident), contextKey(current)).content().path("incidentName").asText());
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
	void replayedReceiptRetriesFailedDocumentProjection() {
		ProjectionFailingStore store = new ProjectionFailingStore(directory);
		try (store) {
			IncidentId incident = store.create(new AppData());
			IncidentService service = service(store);
			IncidentService.ReadResult initial = service.read(incident);
			IncidentService.RecordSubmission update = update(initial, "incidentName", "Projected on retry");
			IncidentService.RecordBatch batch = batch(incident, "projection-retry", update);

			store.failNextSave = true;
			assertThrows(StorePersistenceException.class, () -> service.submit(batch));
			assertEquals("", store.loadSnapshot(incident).data().getIncidentContext().getIncidentName());

			store.failNextSave = true;
			IncidentService restarted = service(store);
			IncidentService.SubmissionResult replay = restarted.submit(batch);
			assertTrue(replay.applied());
			assertEquals("Projected on retry",
					record(restarted.read(incident), contextKey(initial)).content().path("incidentName").asText());
			assertEquals("Projected on retry",
					store.loadSnapshot(incident).data().getIncidentContext().getIncidentName());
			assertEquals(replay, restarted.submit(batch));
			assertEquals(2, record(restarted.read(incident), contextKey(initial)).version());
		}
	}

	@Test
	void replaysSuccessfulReceiptAfterLostResponseWithoutNewVersion() {
		try (FileIncidentStore store = new FileIncidentStore(directory)) {
			IncidentId incident = store.create(new AppData());
			IncidentService service = service(store);
			IncidentService.ReadResult initial = service.read(incident);
			IncidentService.RecordBatch batch = batch(incident, "lost-response",
					update(initial, "incidentName", "Committed once"));

			IncidentService.SubmissionResult first = service.submit(batch);
			IncidentService.SubmissionResult replay = service.submit(batch);

			assertEquals(first, replay);
			assertEquals(1, service.changesSince(incident, initial.cursor()).changes().size());
			assertEquals(first.cursor(), replay.cursor());
		}
	}

	@Test
	void concurrentClientsSerializeSameAndDifferentRecordEdits() throws Exception {
		AppData data = new AppData();
		OpenActionItem action = new OpenActionItem();
		data.getOpenActionItems().add(action);
		try (FileIncidentStore firstStore = new FileIncidentStore(directory);
				FileIncidentStore secondStore = new FileIncidentStore(directory)) {
			IncidentId incident = firstStore.create(data);
			IncidentService firstClient = service(firstStore);
			IncidentService secondClient = service(secondStore);
			IncidentService.ReadResult initial = firstClient.read(incident);
			RecordIndex.RecordKey context = contextKey(initial);
			RecordIndex.RecordKey actionKey = RecordIndex.of(data).records().keySet().stream()
					.filter(key -> key.type().equals(OpenActionItem.class.getName())).findFirst().orElseThrow();
			ExecutorService executor = Executors.newFixedThreadPool(2);
			try {
				IncidentService.SubmissionResult[] sameRecord = runConcurrentSubmissions(executor,
						firstClient, batch(incident, "client-one-same", update(initial, "incidentName", "One")),
						secondClient, batch(incident, "client-two-same", update(initial, "incidentName", "Two")));
				assertNotEquals(sameRecord[0].applied(), sameRecord[1].applied());
				assertEquals(initial.cursor() + 1, firstClient.read(incident).cursor());

				IncidentService.ReadResult next = firstClient.read(incident);
				IncidentService.SubmissionResult[] differentRecords = runConcurrentSubmissions(executor,
						firstClient, batch(incident, "client-one-different", update(next, "currentUser", "User")),
						secondClient, batch(incident, "client-two-different",
								updateRecord(next, actionKey, "poc", "Resource")));
				assertTrue(differentRecords[0].applied());
				assertTrue(differentRecords[1].applied());

				IncidentService.ChangeResult changes = firstClient.changesSince(incident, next.cursor());
				assertEquals(2, changes.changes().size());
				assertTrue(changes.changes().get(0).cursor() < changes.changes().get(1).cursor());
				assertEquals(next.cursor() + 2, changes.cursor());
				assertTrue(record(firstClient.read(incident), context).version() >= 2);
			} finally {
				executor.shutdownNow();
			}
		}
	}

	@Test
	void standaloneSaveAndHostSubmitSerializeAcrossInstances() throws Exception {
		try (FileIncidentStore hostStore = new FileIncidentStore(directory);
				FileIncidentStore standaloneStore = new FileIncidentStore(directory)) {
			IncidentId incident = hostStore.create(new AppData());
			BlockingRecordVersionStore versions = new BlockingRecordVersionStore(
					new FileRecordVersionStore(directory.resolve("incidents")));
			IncidentService host = new IncidentService(hostStore, versions);
			IncidentService.ReadResult initial = host.read(incident);
			AppData standaloneEdit = standaloneStore.loadSnapshot(incident).data();
			standaloneEdit.getIncidentContext().setIncidentName("Standalone edit");
			IncidentService.RecordBatch hostBatch = batch(incident, "host-concurrent-save",
					update(initial, "currentUser", "Host edit"));
			versions.blockNextSave();
			ExecutorService executor = Executors.newFixedThreadPool(2);
			try {
				Future<IncidentService.SubmissionResult> hostResult = executor.submit(() -> host.submit(hostBatch));
				assertTrue(versions.saveEntered.await(5, TimeUnit.SECONDS));
				CountDownLatch standaloneStarted = new CountDownLatch(1);
				Future<Boolean> standaloneResult = executor.submit(() -> {
					standaloneStarted.countDown();
					try {
						standaloneStore.save(incident, standaloneEdit, standaloneStore.revisionToken(standaloneEdit));
						return true;
					} catch (StoreConflictException expected) {
						return false;
					}
				});
				assertTrue(standaloneStarted.await(5, TimeUnit.SECONDS));
				Thread.sleep(50);
				assertFalse(standaloneResult.isDone());
				versions.continueSave.countDown();

				assertTrue(hostResult.get(5, TimeUnit.SECONDS).applied());
				assertFalse(standaloneResult.get(5, TimeUnit.SECONDS));
				IncidentService.ReadResult afterHost = host.read(incident);
				assertEquals("Host edit", record(afterHost, contextKey(initial)).content()
						.path("currentUser").asText());

				AppData retry = standaloneStore.loadSnapshot(incident).data();
				retry.getIncidentContext().setIncidentName("Standalone edit");
				standaloneStore.save(incident, retry, standaloneStore.revisionToken(retry));
				IncidentService.VersionedRecord context = record(host.read(incident), contextKey(initial));
				assertEquals("Host edit", context.content().path("currentUser").asText());
				assertEquals("Standalone edit", context.content().path("incidentName").asText());
			} finally {
				versions.continueSave.countDown();
				executor.shutdownNow();
			}
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
	void failedVersionWriteLeavesNoEffectOrReceipt() {
		try (FileIncidentStore store = new FileIncidentStore(directory)) {
			IncidentId incident = store.create(new AppData());
			FileRecordVersionStore delegate = new FileRecordVersionStore(directory.resolve("incidents"));
			FailingRecordVersionStore versions = new FailingRecordVersionStore(delegate);
			IncidentService service = new IncidentService(store, versions);
			IncidentService.ReadResult initial = service.read(incident);
			IncidentService.RecordBatch batch = batch(incident, "fail-before-commit",
					update(initial, "incidentName", "Must not commit"));
			versions.failNextSave = true;

			assertThrows(StorePersistenceException.class, () -> service.submit(batch));

			RecordVersionStore.IncidentState durable = delegate.load(incident);
			assertEquals(initial.cursor(), durable.cursor());
			assertTrue(durable.receipts().stream().noneMatch(receipt -> receipt.operationId().equals(
					batch.operationId())));
			assertEquals("", store.loadSnapshot(incident).data().getIncidentContext().getIncidentName());
		}
	}

	@Test
	void rejectsUnknownRecordAndUnknownBaseVersion() {
		try (FileIncidentStore store = new FileIncidentStore(directory)) {
			IncidentId incident = store.create(new AppData());
			IncidentService service = service(store);
			IncidentService.ReadResult initial = service.read(incident);
			RecordIndex.RecordKey known = contextKey(initial);
			RecordIndex.RecordKey unknownKey = new RecordIndex.RecordKey(IncidentContext.class.getName(), "unknown");
			IncidentService.RecordSubmission unknown = new IncidentService.RecordSubmission(unknownKey, null, null, 1,
					IncidentService.Operation.UPDATE, content(unknownKey, "incidentName", "Unknown"));
			IncidentService.RecordSubmission stale = new IncidentService.RecordSubmission(known, null, null, 999,
					IncidentService.Operation.UPDATE, content(known, "incidentName", "Stale base"));

			assertThrows(StoreValidationException.class, () -> service.submit(batch(incident, "unknown-record",
					unknown)));
			assertThrows(StoreValidationException.class, () -> service.submit(batch(incident, "unknown-base",
					stale)));
		}
	}

	@Test
	void recoversVersionStoreFromCorruptAndMissingPrimaryFiles() throws IOException {
		try (FileIncidentStore store = new FileIncidentStore(directory)) {
			IncidentId incident = store.create(new AppData());
			IncidentService service = service(store);
			IncidentService.ReadResult initial = service.read(incident);
			service.submit(batch(incident, "create-backup", update(initial, "incidentName", "Backed up state")));
			Path state = directory.resolve("incidents").resolve(incident.value() + ".records.json");
			Path backup = state.resolveSibling(state.getFileName() + ".bak");
			long latestCursor = new FileRecordVersionStore(directory.resolve("incidents")).load(incident).cursor();

			Files.writeString(state, "{corrupt");
			IncidentService afterCorrupt = service(store);
			IncidentService.ReadResult corruptRecovery = afterCorrupt.read(incident);
			assertEquals(latestCursor, corruptRecovery.cursor());
			assertEquals("Backed up state", record(corruptRecovery, contextKey(initial)).content()
					.path("incidentName").asText());

			Files.delete(state);
			IncidentService afterMissing = service(store);
			IncidentService.ReadResult missingRecovery = afterMissing.read(incident);
			assertEquals(latestCursor, missingRecovery.cursor());
			assertEquals("Backed up state",
					record(missingRecovery, contextKey(initial)).content().path("incidentName").asText());
			assertTrue(Files.exists(backup));
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
	void standaloneCreateUpdateAndStrikeOutKeepVersionHistoryConsistent() {
		try (FileIncidentStore store = new FileIncidentStore(directory)) {
			AppData data = new AppData();
			Ics214Form form = new Ics214Form();
			ActivityLogEntry entry = new ActivityLogEntry();
			form.getActivityLog().add(entry);
			data.getActivityLogs().add(form);
			IncidentId incident = store.create(data);
			IncidentService service = service(store);
			IncidentService.ReadResult initial = service.read(incident);
			RecordIndex initialIndex = RecordIndex.of(data);
			RecordIndex.RecordKey entryKey = initialIndex.records().keySet().stream()
					.filter(key -> key.type().equals(ActivityLogEntry.class.getName())).findFirst().orElseThrow();
			entry.setNotableActivity("Standalone edit");
			OpenActionItem action = new OpenActionItem();
			action.setItem("Standalone create");
			data.getOpenActionItems().add(action);
			store.save(incident, data, store.revisionToken(data));

			RecordIndex.RecordKey actionKey = RecordIndex.of(data).records().keySet().stream()
					.filter(key -> key.type().equals(OpenActionItem.class.getName())).findFirst().orElseThrow();
			IncidentService.ReadResult afterUpdate = service.read(incident);
			assertEquals(2, record(afterUpdate, entryKey).version());
			assertEquals(1, record(afterUpdate, actionKey).version());

			entry.setStruckOut(true);
			store.save(incident, data, store.revisionToken(data));

			IncidentService.ReadResult afterStrikeOut = service.read(incident);
			assertEquals(3, record(afterStrikeOut, entryKey).version());
			assertTrue(record(afterStrikeOut, entryKey).content().path("struckOut").asBoolean());
			List<RecordVersionStore.RecordChange> changes = service.changesSince(incident, initial.cursor()).changes();
			assertEquals(3, changes.size());
			assertTrue(changes.get(0).cursor() < changes.get(1).cursor());
			assertTrue(changes.get(1).cursor() < changes.get(2).cursor());
		}
	}

	@Test
	void failedStandaloneHistorySynchronizationRollsDocumentBack() throws IOException {
		try (FileIncidentStore store = new FileIncidentStore(directory)) {
			IncidentId incident = store.create(new AppData());
			AppData before = store.loadSnapshot(incident).data();
			before.getIncidentContext().setIncidentName("Should roll back");
			Path history = directory.resolve("incidents").resolve(incident.value() + ".records.json");
			Files.delete(history);
			Files.createDirectory(history);

			assertThrows(StorePersistenceException.class,
					() -> store.save(incident, before, store.revisionToken(before)));

			assertEquals("", store.loadSnapshot(incident).data().getIncidentContext().getIncidentName());
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

	/**
	 * Builds an update for any record in the supplied snapshot.
	 *
	 * @param initial current record snapshot
	 * @param key target record identity
	 * @param field field to replace
	 * @param value replacement value
	 * @return update submission
	 */
	private IncidentService.RecordSubmission updateRecord(IncidentService.ReadResult initial,
			RecordIndex.RecordKey key, String field, String value) {
		ObjectNode content = (ObjectNode) record(initial, key).content();
		content.put(field, value);
		return new IncidentService.RecordSubmission(key, null, null, record(initial, key).version(),
				IncidentService.Operation.UPDATE, content);
	}

	/**
	 * Creates minimal record content for request-validation tests.
	 *
	 * @param key record identity
	 * @param field field to include
	 * @param value field value
	 * @return record JSON
	 */
	private ObjectNode content(RecordIndex.RecordKey key, String field, String value) {
		ObjectNode content = JsonNodeFactory.instance.objectNode();
		content.put("id", key.id());
		content.put(field, value);
		return content;
	}

	/**
	 * Starts two clients together and waits for both atomic submissions.
	 *
	 * @param executor concurrent task executor
	 * @param first first service
	 * @param firstBatch first client's batch
	 * @param second second service
	 * @param secondBatch second client's batch
	 * @return results in client order
	 * @throws Exception when a task or synchronization fails
	 */
	private IncidentService.SubmissionResult[] runConcurrentSubmissions(ExecutorService executor,
			IncidentService first, IncidentService.RecordBatch firstBatch, IncidentService second,
			IncidentService.RecordBatch secondBatch) throws Exception {
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		Future<IncidentService.SubmissionResult> firstResult = executor.submit(
				() -> submitAfterBarrier(first, firstBatch, ready, start));
		Future<IncidentService.SubmissionResult> secondResult = executor.submit(
				() -> submitAfterBarrier(second, secondBatch, ready, start));
		assertTrue(ready.await(5, TimeUnit.SECONDS));
		start.countDown();
		return new IncidentService.SubmissionResult[] {
				firstResult.get(5, TimeUnit.SECONDS), secondResult.get(5, TimeUnit.SECONDS)
		};
	}

	/**
	 * Waits for the concurrent test's shared start signal before submitting.
	 *
	 * @param service service for this client
	 * @param batch client batch
	 * @param ready waiting-client barrier
	 * @param start shared submit signal
	 * @return service submission result
	 * @throws InterruptedException if interrupted while waiting
	 */
	private IncidentService.SubmissionResult submitAfterBarrier(IncidentService service,
			IncidentService.RecordBatch batch, CountDownLatch ready, CountDownLatch start) throws InterruptedException {
		ready.countDown();
		start.await();
		return service.submit(batch);
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

	/** File store that can fail one document projection write. */
	private static final class ProjectionFailingStore extends FileIncidentStore {
		private boolean failNextSave;

		/**
		 * Creates a projection test store at the supplied directory.
		 *
		 * @param directory store root
		 */
		private ProjectionFailingStore(Path directory) {
			super(directory);
		}

		/**
		 * Fails the next save when failure injection is enabled.
		 *
		 * @param id incident identity
		 * @param data updated document
		 * @param expected expected revision
		 * @return the new revision
		 */
		@Override
		public RevisionToken save(IncidentId id, AppData data, RevisionToken expected) {
			if (failNextSave) {
				failNextSave = false;
				throw new StorePersistenceException("Injected projection failure", null);
			}
			return super.save(id, data, expected);
		}

	}

	/** Record history store that fails before one state replacement. */
	private static final class FailingRecordVersionStore implements RecordVersionStore {
		private final RecordVersionStore delegate;
		private boolean failNextSave;

		/**
		 * Creates a version store decorator.
		 *
		 * @param delegate durable state store
		 */
		private FailingRecordVersionStore(RecordVersionStore delegate) {
			this.delegate = delegate;
		}

		/** {@inheritDoc} */
		@Override
		public IncidentState load(IncidentId incident) {
			return delegate.load(incident);
		}

		/** {@inheritDoc} */
		@Override
		public void save(IncidentId incident, IncidentState state) {
			if (failNextSave) {
				failNextSave = false;
				throw new StorePersistenceException("Injected version commit failure", null);
			}
			delegate.save(incident, state);
		}
	}

	/** Version store decorator that pauses before one durable replacement. */
	private static final class BlockingRecordVersionStore implements RecordVersionStore {
		private final RecordVersionStore delegate;
		private final CountDownLatch saveEntered = new CountDownLatch(1);
		private final CountDownLatch continueSave = new CountDownLatch(1);
		private volatile boolean blockNextSave;

		/**
		 * Creates a version store decorator.
		 *
		 * @param delegate durable state store
		 */
		private BlockingRecordVersionStore(RecordVersionStore delegate) {
			this.delegate = delegate;
		}

		/**
		 * Pauses the next state replacement until released by the test.
		 */
		private void blockNextSave() {
			blockNextSave = true;
		}

		/** {@inheritDoc} */
		@Override
		public IncidentState load(IncidentId incident) {
			return delegate.load(incident);
		}

		/** {@inheritDoc} */
		@Override
		public void save(IncidentId incident, IncidentState state) {
			if (blockNextSave) {
				blockNextSave = false;
				saveEntered.countDown();
				try {
					if (!continueSave.await(5, TimeUnit.SECONDS)) {
						throw new StorePersistenceException("Timed out waiting to resume version save", null);
					}
				} catch (InterruptedException exception) {
					Thread.currentThread().interrupt();
					throw new StorePersistenceException("Interrupted while waiting to resume version save",
							exception);
				}
			}
			delegate.save(incident, state);
		}
	}
}
