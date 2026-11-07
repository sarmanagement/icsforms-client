/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.IapPhase;
import org.sarmanagement.icsforms.model.IncidentMode;
import org.sarmanagement.icsforms.model.Ics202Form;
import org.sarmanagement.icsforms.model.ResourceAssignment;
import org.sarmanagement.icsforms.model.SarTaskAssignment;
import org.sarmanagement.icsforms.persistence.ControllableIncidentStore;
import org.sarmanagement.icsforms.persistence.FileIncidentStore;
import org.sarmanagement.icsforms.persistence.IncidentId;
import org.sarmanagement.icsforms.persistence.ImportValidationException;
import org.sarmanagement.icsforms.persistence.IncidentStore;
import org.sarmanagement.icsforms.persistence.IncidentNotFoundException;
import org.sarmanagement.icsforms.persistence.RemoteIncidentStore;
import org.sarmanagement.icsforms.persistence.RevisionToken;
import org.sarmanagement.icsforms.persistence.StoreConflictException;
import org.sarmanagement.icsforms.persistence.StorePersistenceException;
import org.sarmanagement.icsforms.persistence.StoreUnavailableException;
import org.sarmanagement.icsforms.persistence.StoredIncident;
import org.sarmanagement.icsforms.pdf.PdfExportService;
import org.sarmanagement.icsforms.validation.IncidentValidator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies store-backed controller transitions without opening Swing dialogs. */
class AppControllerStoreTest {
	@TempDir
	Path directory;

	@Test
	void startupPreferencesAndDisabledTrackerSurviveStoreAndNewPeriod() {
		IncidentId id;
		String clueId;
		try (FileIncidentStore store = new FileIncidentStore(directory)) {
			AppData initial = new AppData();
			AppController controller = controller(store, store.create(initial), initial);
			controller.newDocument(IapPhase.INITIAL_RESPONSE, IncidentMode.GENERIC, data -> {
				data.setOpenActionTrackerEnabled(true);
				data.setIcs233Portrait(true);
				data.setClueLogIcs233Enabled(true);
			});
			AppData created = controller.getData();
			id = controller.getActiveIncidentId();
			AppData persisted = store.load(id);
			assertTrue(persisted.isOpenActionTrackerEnabled());
			assertTrue(persisted.isIcs233Portrait());
			assertTrue(persisted.isClueLogIcs233Enabled());
			assertEquals(IncidentMode.GENERIC, persisted.getIncidentMode());
			assertEquals(IapPhase.INITIAL_RESPONSE, persisted.getIapPhase());

			var action = new org.sarmanagement.icsforms.model.OpenActionItem();
			action.setItem("Keep searching");
			created.setOpenActionItems(java.util.List.of(action));
			var clue = new org.sarmanagement.icsforms.model.ClueLogEntry();
			clue.setFollowUpAssignments(java.util.List.of(
					new org.sarmanagement.icsforms.model.PocAssignment("First",
							java.time.LocalDateTime.parse("2026-01-01T12:00")),
					new org.sarmanagement.icsforms.model.PocAssignment("Second",
							java.time.LocalDateTime.parse("2026-01-02T12:00"))));
			clue.setFollowUpBriefed(true);
			clue.setFollowUpStatus("In progress");
			created.setClueLogEntries(java.util.List.of(clue));
			created.setOpenActionTrackerEnabled(false);
			long revision = created.getRevision();
			controller.save();
			assertEquals(revision + 1, created.getRevision());
			clueId = clue.getEntryId();
			controller.open(id);
			assertFalse(controller.getData().isOpenActionTrackerEnabled());
			var loadedClue = controller.getData().getClueLogEntries().get(0);
			assertEquals(clueId, loadedClue.getEntryId());
			assertEquals(2, loadedClue.getFollowUpAssignments().size());
			assertEquals("First", loadedClue.getFollowUpAssignments().get(0).getAssignee());
			assertEquals("Second", loadedClue.getFollowUpAssignedTo());
			assertTrue(loadedClue.isFollowUpBriefed());
			assertEquals("In progress", loadedClue.getFollowUpStatus());
			controller.newOperationalPeriod();
			controller.save();
			assertEquals(revision + 2, controller.getData().getRevision());
		}
		try (FileIncidentStore store = new FileIncidentStore(directory)) {
			AppData restored = store.load(id);
			assertEquals(id.value(), restored.getIncidentId());
			assertEquals(IncidentMode.GENERIC, restored.getIncidentMode());
			assertFalse(restored.isOpenActionTrackerEnabled());
			assertTrue(restored.isIcs233Portrait());
			assertTrue(restored.isClueLogIcs233Enabled());
			assertEquals(IapPhase.DURING_OP, restored.getIapPhase());
			assertEquals(1, restored.getOperationalPeriodHistory().size());
			assertEquals("Keep searching", restored.getOpenActionItems().get(0).getItem());
			assertTrue(restored.getClueLogEntries().isEmpty());
		}
	}

	@Test
	void populatedFormsSurviveLegacySchemaOpenSaveAndReopen() throws Exception {
		IncidentId id;
		String assignmentId = "assignment-1";
		try (FileIncidentStore store = new FileIncidentStore(directory)) {
			AppData initial = new AppData();
			initial.getForm202().setObjectives(List.of("Protect responders"));
			ResourceAssignment assignment = new ResourceAssignment();
			assignment.setAssignmentId(assignmentId);
			assignment.setAssignmentTeamNumber("Team 1");
			assignment.setAssignment("Search Sector A");
			initial.getForm204().getResourcesAssigned().add(assignment);
			SarTaskAssignment task = new SarTaskAssignment();
			task.setTaskId("task-1");
			task.setAssignmentId(assignmentId);
			task.setHazardsObserved("Steep terrain");
			initial.setSarTaskAssignments(List.of(task));
			id = store.create(initial);

			Path incidentFile = directory.resolve("incidents").resolve(id.value() + ".json");
			int legacySchemaVersion = AppData.CURRENT_SCHEMA_VERSION - 1;
			ObjectMapper mapper = new ObjectMapper();
			ObjectNode json = (ObjectNode) mapper.readTree(Files.readString(incidentFile));
			json.put("schemaVersion", legacySchemaVersion);
			json.remove("archivedSafetyHazardReports");
			Files.writeString(incidentFile, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(json));

			AppData loaded = store.load(id);
			assertEquals(AppData.CURRENT_SCHEMA_VERSION, loaded.getSchemaVersion());
			AppController controller = controller(store, id, loaded);
			controller.save();
		}

		try (FileIncidentStore reopened = new FileIncidentStore(directory)) {
			AppData restored = reopened.load(id);
			assertEquals(AppData.CURRENT_SCHEMA_VERSION, restored.getSchemaVersion());
			assertEquals(List.of("Protect responders"), restored.getForm202().getObjectives());
			assertEquals(1, restored.getForm204().getResourcesAssigned().size());
			assertEquals(assignmentId, restored.getForm204().getResourcesAssigned().get(0).getAssignmentId());
			assertEquals("Team 1", restored.getForm204().getResourcesAssigned().get(0).getAssignmentTeamNumber());
			assertEquals(1, restored.getSarTaskAssignments().size());
			assertEquals("task-1", restored.getSarTaskAssignments().get(0).getTaskId());
			assertEquals("Steep terrain", restored.getSarTaskAssignments().get(0).getHazardsObserved());
		}
	}

	@Test
	void createsAndDuplicatesIndependentIncidents() {
		try (FileIncidentStore store = new FileIncidentStore(directory)) {
			AppData original = new AppData();
			IncidentId first = store.create(original);
			AppController controller = controller(store, first, original);
			controller.getData().getIncidentContext().setIncidentName("Search Alpha");
			assertTrue(controller.getStoreDisplayLabel().contains(first.value()));

			IncidentId duplicate = controller.duplicateIncident();

			assertNotEquals(first, duplicate);
			assertEquals(duplicate, controller.getActiveIncidentId());
			assertNotEquals("Search Alpha", store.load(first).getIncidentContext().getIncidentName());
			assertEquals("Search Alpha", store.load(duplicate).getIncidentContext().getIncidentName());

			controller.newDocument(IapPhase.INITIAL_RESPONSE, IncidentMode.GENERIC);
			assertNotEquals(duplicate, controller.getActiveIncidentId());
			assertEquals(IapPhase.INITIAL_RESPONSE, store.load(controller.getActiveIncidentId()).getIapPhase());
			assertEquals(IncidentMode.GENERIC, store.load(controller.getActiveIncidentId()).getIncidentMode());
			assertFalse(controller.getData().isOpenActionTrackerEnabled());
			assertFalse(store.load(controller.getActiveIncidentId()).isOpenActionTrackerEnabled());
			assertTrue(controller.getStoreDisplayLabel().contains(controller.getActiveIncidentId().value()));
		}
	}

	@Test
	void exportsAndImportsWithoutRebindingTheActiveIncident() {
		try (FileIncidentStore store = new FileIncidentStore(directory)) {
			AppData data = new AppData();
			data.getIncidentContext().setIncidentName("Fictional export test");
			IncidentId first = store.create(data);
			AppController controller = controller(store, first, data);
			Path exported = directory.resolve("export.json");

			controller.exportIncident(exported, AppController.LinkSource.NONE);
			assertEquals(first, controller.getActiveIncidentId());
			assertTrue(controller.importIncident(exported));
			assertNotEquals(first, controller.getActiveIncidentId());
			assertEquals(2, store.list().size());
		}
	}

	@Test
	void failedImportLeavesActiveIncidentAndUnsavedContentIntact() throws Exception {
		try (FileIncidentStore store = new FileIncidentStore(directory)) {
			AppData initial = new AppData();
			IncidentId id = store.create(initial);
			AppController controller = controller(store, id, initial);
			initial.getIncidentContext().setIncidentName("Unsaved fictional incident");
			Path invalid = directory.resolve("wrong-shape.json");
			for (String json : List.of("{\"incident\":{\"name\":\"Wrong shape\"},\"resources\":[]}",
					"{\"schemaVersion\":15,\"incidentContext\":{\"incidentName\":\"Example\"},"
							+ "\"sarTaskAssignments\":[null]}")) {
				Files.writeString(invalid, json);
				assertThrows(ImportValidationException.class, () -> controller.importIncident(invalid));
				assertEquals(id, controller.getActiveIncidentId());
				assertSame(initial, controller.getData());
				assertEquals("Unsaved fictional incident", controller.getData().getIncidentContext().getIncidentName());
				assertEquals(1, store.list().size());
			}
		}
	}

	@Test
	void legacyControllerAlsoValidatesExternalFilesBeforeReplacingData() throws Exception {
		AppData initial = new AppData();
		initial.getIncidentContext().setIncidentName("Fictional legacy controller incident");
		AppController controller = SwingTestLifecycleExtension.track(new AppController(initial,
				new org.sarmanagement.icsforms.persistence.LocalRepository(directory.resolve("local.json")),
				new PdfExportService(), new IncidentValidator()));
		Path source = directory.resolve("external.json");
		Files.writeString(source, "{\"incident\":{\"name\":\"Wrong shape\"}}");

		assertThrows(ImportValidationException.class, () -> controller.open(source));
		assertSame(initial, controller.getData());

		Files.writeString(source,
				"{\"schemaVersion\":15,\"incidentContext\":{\"incidentName\":\"Fictional external incident\"}}");
		controller.open(source);
		assertEquals("Fictional external incident", controller.getData().getIncidentContext().getIncidentName());
		assertTrue(controller.getLastImportProblems().isEmpty());

		Files.writeString(source, "{\"form201\":{\"situationSummary\":\"Fictional legacy import\"}}");
		assertTrue(controller.importIncident(source));
		assertEquals(List.of("$.schemaVersion", "$.incidentContext.incidentName"),
				controller.getLastImportProblems().stream().map(ImportValidationException.Problem::path).toList());
		assertFalse(controller.importIncident(null));
		assertTrue(controller.getLastImportProblems().isEmpty());
	}

	@Test
	void duplicatePreservesUnsavedEditsAfterRevisionConflict() {
		try (FileIncidentStore store = new FileIncidentStore(directory)) {
			AppData data = new AppData();
			IncidentId first = store.create(data);
			AppController controller = controller(store, first, data);
			RevisionToken expected = store.revisionToken(data);
			AppData external = store.load(first);
			external.getIncidentContext().setIncidentName("External");
			store.save(first, external);
			RevisionToken current = store.revisionToken(external);
			controller.getData().getIncidentContext().setIncidentName("Local");
			controller.markDirty();

			StoreConflictException conflict = assertThrows(StoreConflictException.class, controller::save);
			assertEquals(expected, conflict.expected());
			assertEquals(current, conflict.current());
			assertTrue(controller.isDirty());
			assertEquals(first, controller.getActiveIncidentId());
			assertEquals("Local", controller.getData().getIncidentContext().getIncidentName());
			assertEquals("External", store.load(first).getIncidentContext().getIncidentName());
			assertFalse(store.list().isEmpty());

			IncidentId preserved = controller.duplicateIncident();
			assertNotEquals(first, preserved);
			assertFalse(controller.isDirty());
			assertEquals("Local", store.load(preserved).getIncidentContext().getIncidentName());
			assertEquals("External", store.load(first).getIncidentContext().getIncidentName());
		}
	}

	@Test
	void declinesToEditIncidentLockedByAnotherStore() {
		try (FileIncidentStore firstStore = new FileIncidentStore(directory);
				FileIncidentStore secondStore = new FileIncidentStore(directory)) {
			AppData data = new AppData();
			IncidentId id = firstStore.create(data);
			AppController firstController = controller(firstStore, id, data);
			assertEquals(id, firstController.getActiveIncidentId());

			assertThrows(StoreConflictException.class, () -> controller(secondStore, id, secondStore.load(id)));
		}
	}

	@Test
	void releasesPreviousLockOnlyAfterSuccessfulSwitch() {
		try (FileIncidentStore firstStore = new FileIncidentStore(directory);
				FileIncidentStore secondStore = new FileIncidentStore(directory)) {
			AppData data = new AppData();
			IncidentId original = firstStore.create(data);
			AppController controller = controller(firstStore, original, data);
			IncidentId duplicate = controller.duplicateIncident();
			assertTrue(secondStore.lock(original));

			assertThrows(StoreConflictException.class, () -> controller.open(original));
			assertEquals(duplicate, controller.getActiveIncidentId());
			assertTrue(secondStore.isLockedElsewhere(duplicate));

			controller.newDocument(IapPhase.PRE_OP, IncidentMode.SAR);
			assertTrue(secondStore.lock(duplicate));
			IncidentId fresh = controller.getActiveIncidentId();
			secondStore.unlock(original);
			controller.open(original);
			assertTrue(secondStore.lock(fresh));
		}
	}

	@Test
	void closePreservesDirtyEditsAndReleasesTheActiveEditLock() {
		try (FileIncidentStore store = new FileIncidentStore(directory);
				FileIncidentStore otherStore = new FileIncidentStore(directory)) {
			AppData data = new AppData();
			IncidentId id = store.create(data);
			AppController controller = controller(store, id, data);
			assertFalse(controller.hasUnsavedChanges());

			data.getIncidentContext().setIncidentName("Unsaved close test");
			controller.markDirty();
			controller.close();
			controller.close();

			assertTrue(controller.hasUnsavedChanges());
			assertEquals("", store.load(id).getIncidentContext().getIncidentName());
			assertTrue(otherStore.lock(id));
			otherStore.unlock(id);
		}
	}

	@Test
	void failedSavePreservesPersistedDataAndUnsavedEditorInput() {
		AppData data = new AppData();
		IncidentStoreTestFixtures.Fixture fixture = IncidentStoreTestFixtures.create(data);
		AppController controller = fixture.controller();
		data.getIncidentContext().setIncidentName("Unsaved editor input");
		controller.markDirty();
		fixture.store().failNext(ControllableIncidentStore.Operation.SAVE,
				new IllegalStateException("Scheduled save failure"));

		assertThrows(IllegalStateException.class, controller::save);

		assertEquals("Unsaved editor input", controller.getData().getIncidentContext().getIncidentName());
		assertTrue(controller.isDirty());
		assertEquals("", fixture.store().load(fixture.incidentId()).getIncidentContext().getIncidentName());
	}

	@Test
	void locklessRemoteStoreEditsAndSavesUsingServerTokensInsteadOfLegacyRevisions() {
		try (RemoteIncidentStore store = new RemoteIncidentStore()) {
			AppData data = new AppData();
			IncidentId id = store.create(data);
			RevisionToken created = store.revisionToken(data);
			AppController controller = controller(store, id, data);
			assertTrue(store.editLocks().isEmpty());
			assertTrue(controller.getStoreDisplayLabel().contains(id.value()));
			data.setRevision(987654321L);
			data.getIncidentContext().setIncidentName("Remote edit");
			controller.markDirty();

			controller.save();

			assertFalse(controller.isDirty());
			assertSame(data, controller.getData());
			StoredIncident saved = store.loadSnapshot(id);
			assertNotSame(data, saved.data());
			assertEquals("Remote edit", saved.data().getIncidentContext().getIncidentName());
			assertNotEquals(created, saved.revision());
			assertEquals(saved.revision(), store.revisionToken(data));
			data.getIncidentContext().setIncidentName("Second remote edit");
			controller.markDirty();
			controller.save();
			assertNotEquals(saved.revision(), store.revisionToken(data));
			assertEquals("Second remote edit", store.loadSnapshot(id).data().getIncidentContext().getIncidentName());
			assertFalse(controller.isDirty());
		}
	}

	@Test
	void remoteConflictPreservesEditsAndReloadRecoversTheCurrentServerToken() {
		try (RemoteIncidentStore store = new RemoteIncidentStore()) {
			AppData data = new AppData();
			IncidentId id = store.create(data);
			RevisionToken expected = store.revisionToken(data);
			AppController controller = controller(store, id, data);
			StoredIncident external = store.loadSnapshot(id);
			external.data().getIncidentContext().setIncidentName("Server edit");
			RevisionToken current = store.save(id, external.data(), external.revision());
			data.getIncidentContext().setIncidentName("Unsaved local edit");
			controller.markDirty();

			StoreConflictException conflict = assertThrows(StoreConflictException.class, controller::save);

			assertEquals(expected, conflict.expected());
			assertEquals(current, conflict.current());
			assertSame(data, controller.getData());
			assertEquals(id, controller.getActiveIncidentId());
			assertEquals("Unsaved local edit", data.getIncidentContext().getIncidentName());
			assertTrue(controller.isDirty());
			assertEquals(expected, store.revisionToken(data));
			assertEquals("Server edit", store.loadSnapshot(id).data().getIncidentContext().getIncidentName());

			controller.open(id);

			assertNotSame(data, controller.getData());
			assertFalse(controller.isDirty());
			assertEquals(current, store.revisionToken(controller.getData()));
			assertEquals("Server edit", controller.getData().getIncidentContext().getIncidentName());
			controller.getData().getIncidentContext().setIncidentName("Recovered edit");
			controller.markDirty();
			controller.save();
			assertFalse(controller.isDirty());
			assertEquals("Recovered edit", store.loadSnapshot(id).data().getIncidentContext().getIncidentName());
		}
	}

	@Test
	void remoteConflictCanBeRecoveredByDuplicatingUnsavedEdits() {
		try (RemoteIncidentStore store = new RemoteIncidentStore()) {
			AppData data = new AppData();
			IncidentId original = store.create(data);
			AppController controller = controller(store, original, data);
			StoredIncident external = store.loadSnapshot(original);
			external.data().getIncidentContext().setIncidentName("Server original");
			store.save(original, external.data(), external.revision());
			data.getIncidentContext().setIncidentName("Local recovery copy");
			controller.markDirty();
			assertThrows(StoreConflictException.class, controller::save);

			IncidentId duplicate = controller.duplicateIncident();

			assertNotEquals(original, duplicate);
			assertEquals(duplicate, controller.getActiveIncidentId());
			assertFalse(controller.isDirty());
			assertEquals("Local recovery copy", store.loadSnapshot(duplicate).data().getIncidentContext().getIncidentName());
			assertEquals("Server original", store.loadSnapshot(original).data().getIncidentContext().getIncidentName());
			controller.getData().getIncidentContext().setIncidentName("Saved recovery copy");
			controller.markDirty();
			controller.save();
			assertEquals("Saved recovery copy", store.loadSnapshot(duplicate).data().getIncidentContext().getIncidentName());
			assertFalse(controller.isDirty());
		}
	}

	@Test
	void remoteDeletionChecksTheTokenObservedByTheController() {
		try (RemoteIncidentStore store = new RemoteIncidentStore()) {
			AppData data = new AppData();
			IncidentId id = store.create(data);
			RevisionToken expected = store.revisionToken(data);
			AppController controller = controller(store, id, data);
			StoredIncident external = store.loadSnapshot(id);
			external.data().getIncidentContext().setIncidentName("New server revision");
			RevisionToken current = store.save(id, external.data(), external.revision());
			data.getIncidentContext().setIncidentName("Unsaved deletion conflict edit");
			controller.markDirty();

			StoreConflictException conflict = assertThrows(StoreConflictException.class,
					() -> store.delete(id, store.revisionToken(controller.getData())));

			assertEquals(expected, conflict.expected());
			assertEquals(current, conflict.current());
			assertSame(data, controller.getData());
			assertTrue(controller.isDirty());
			assertEquals("Unsaved deletion conflict edit", data.getIncidentContext().getIncidentName());
			assertEquals("New server revision", store.loadSnapshot(id).data().getIncidentContext().getIncidentName());
			controller.open(id);
			store.delete(id, store.revisionToken(controller.getData()));
			assertThrows(IncidentNotFoundException.class, () -> store.loadSnapshot(id));
			assertTrue(store.list().isEmpty());
			controller.close();
		}
	}

	@Test
	void remoteLoadAndCreationFailuresRetainTheActiveDocumentAndDirtyState() {
		for (RuntimeException failure : List.of(new StoreUnavailableException("Remote service unavailable"),
				new StorePersistenceException("Remote storage failed", null))) {
			try (RemoteIncidentStore store = new RemoteIncidentStore()) {
				AppData data = new AppData();
				IncidentId active = store.create(data);
				RevisionToken expected = store.revisionToken(data);
				IncidentId candidate = store.create(new AppData());
				AppController controller = controller(store, active, data);
				data.getIncidentContext().setIncidentName("Retained remote editor input");
				controller.markDirty();
				store.failNext(ControllableIncidentStore.Operation.LOAD, failure);

				assertSame(failure, assertThrows(failure.getClass(), () -> controller.open(candidate)));
				assertSame(data, controller.getData());
				assertEquals(active, controller.getActiveIncidentId());
				assertTrue(controller.isDirty());
				assertEquals(expected, store.revisionToken(data));
				store.failNext(ControllableIncidentStore.Operation.LOAD, failure);

				assertSame(failure, assertThrows(failure.getClass(), () -> controller.open(active)));
				assertSame(data, controller.getData());
				assertEquals(active, controller.getActiveIncidentId());
				assertTrue(controller.isDirty());
				store.failNext(ControllableIncidentStore.Operation.CREATE, failure);

				assertSame(failure, assertThrows(failure.getClass(), () -> controller.newDocument(IapPhase.PRE_OP,
						IncidentMode.SAR)));
				assertSame(data, controller.getData());
				assertEquals(active, controller.getActiveIncidentId());
				assertEquals("Retained remote editor input", data.getIncidentContext().getIncidentName());
				assertTrue(controller.isDirty());
				assertEquals(expected, store.revisionToken(data));
				assertEquals(2, store.list().size());
				controller.save();
				assertFalse(controller.isDirty());
				assertEquals("Retained remote editor input",
						store.loadSnapshot(active).data().getIncidentContext().getIncidentName());
			}
		}
	}

	@Test
	void remoteSaveFailuresRemainDistinctAndKeepEditsAvailableForRetry() throws ReflectiveOperationException {
		for (RuntimeException failure : List.of(new StoreUnavailableException("Remote service unavailable"),
				new StorePersistenceException("Remote storage failed", null))) {
			try (RemoteIncidentStore store = new RemoteIncidentStore()) {
				AppData data = new AppData();
				IncidentId id = store.create(data);
				RevisionToken expected = store.revisionToken(data);
				AppController controller = controller(store, id, data);
				data.getIncidentContext().setIncidentName("Retryable remote edit");
				controller.markDirty();
				assertTrue(autosaveTimerIsRunning(controller));
				store.failNext(ControllableIncidentStore.Operation.SAVE, failure);

				assertSame(failure, assertThrows(failure.getClass(), controller::save));

				assertSame(data, controller.getData());
				assertEquals(id, controller.getActiveIncidentId());
				assertTrue(controller.isDirty());
				assertFalse(autosaveTimerIsRunning(controller));
				assertEquals("Retryable remote edit", data.getIncidentContext().getIncidentName());
				assertEquals(expected, store.revisionToken(data));
				assertEquals(expected, store.loadSnapshot(id).revision());
				assertEquals("", store.loadSnapshot(id).data().getIncidentContext().getIncidentName());
				controller.save();
				assertFalse(controller.isDirty());
				assertEquals("Retryable remote edit", store.loadSnapshot(id).data().getIncidentContext().getIncidentName());
			}
		}
	}

	@Test
	void closingALocklessRemoteControllerIsIdempotentAndDoesNotSaveDirtyEdits() {
		try (RemoteIncidentStore store = new RemoteIncidentStore()) {
			AppData data = new AppData();
			IncidentId id = store.create(data);
			AppController controller = controller(store, id, data);
			assertTrue(store.editLocks().isEmpty());
			data.getIncidentContext().setIncidentName("Unsaved remote close edit");
			controller.markDirty();

			controller.close();
			controller.close();

			assertSame(data, controller.getData());
			assertTrue(controller.hasUnsavedChanges());
			assertEquals("Unsaved remote close edit", data.getIncidentContext().getIncidentName());
			assertEquals("", store.loadSnapshot(id).data().getIncidentContext().getIncidentName());
		}
	}

	@Test
	void failedCandidateLoadReleasesOnlyItsNewLockAndKeepsTheDirtyActiveDocument() {
		try (ScheduledFailureFileStore store = new ScheduledFailureFileStore(directory);
				FileIncidentStore other = new FileIncidentStore(directory)) {
			AppData data = new AppData();
			IncidentId active = store.create(data);
			IncidentId candidate = store.create(new AppData());
			AppController controller = controller(store, active, data);
			data.getIncidentContext().setIncidentName("Retained local edits");
			controller.markDirty();
			StorePersistenceException failure = new StorePersistenceException("Cannot load candidate", null);
			store.loadFailure = failure;

			assertSame(failure, assertThrows(StorePersistenceException.class, () -> controller.open(candidate)));

			assertSame(data, controller.getData());
			assertEquals(active, controller.getActiveIncidentId());
			assertTrue(controller.isDirty());
			assertEquals("Retained local edits", data.getIncidentContext().getIncidentName());
			assertTrue(other.isLockedElsewhere(active));
			assertEquals(List.of(candidate), store.releasedLocks);
			assertTrue(other.lock(candidate));
		}
	}

	@Test
	void failedCandidateLoadDoesNotReleaseAPreexistingCallerLock() {
		try (ScheduledFailureFileStore store = new ScheduledFailureFileStore(directory);
				FileIncidentStore other = new FileIncidentStore(directory)) {
			AppData data = new AppData();
			IncidentId active = store.create(data);
			IncidentId candidate = store.create(new AppData());
			AppController controller = controller(store, active, data);
			controller.markDirty();
			assertTrue(store.lock(candidate));
			store.loadFailure = new StorePersistenceException("Cannot load already held candidate", null);

			assertThrows(StorePersistenceException.class, () -> controller.open(candidate));

			assertSame(data, controller.getData());
			assertEquals(active, controller.getActiveIncidentId());
			assertTrue(controller.isDirty());
			assertTrue(other.isLockedElsewhere(active));
			assertTrue(other.isLockedElsewhere(candidate));
			assertTrue(store.releasedLocks.isEmpty());
		}
	}

	@Test
	void failedSameIncidentReloadKeepsItsExistingEditLockAndDirtyDocument() {
		try (ScheduledFailureFileStore store = new ScheduledFailureFileStore(directory);
				FileIncidentStore other = new FileIncidentStore(directory)) {
			AppData data = new AppData();
			IncidentId active = store.create(data);
			AppController controller = controller(store, active, data);
			data.getIncidentContext().setIncidentName("Unsaved reload edits");
			controller.markDirty();
			store.loadFailure = new StorePersistenceException("Cannot reload active incident", null);

			assertThrows(StorePersistenceException.class, () -> controller.open(active));

			assertSame(data, controller.getData());
			assertEquals(active, controller.getActiveIncidentId());
			assertTrue(controller.isDirty());
			assertEquals("Unsaved reload edits", data.getIncidentContext().getIncidentName());
			assertTrue(other.isLockedElsewhere(active));
			assertTrue(store.releasedLocks.isEmpty());
		}
	}

	@Test
	void failedCandidateCleanupIsSuppressedOnTheOriginalLoadFailure() {
		try (ScheduledFailureFileStore store = new ScheduledFailureFileStore(directory);
				FileIncidentStore other = new FileIncidentStore(directory)) {
			AppData data = new AppData();
			IncidentId active = store.create(data);
			IncidentId candidate = store.create(new AppData());
			AppController controller = controller(store, active, data);
			controller.markDirty();
			StorePersistenceException failure = new StorePersistenceException("Original load failure", null);
			StorePersistenceException cleanupFailure = new StorePersistenceException("Candidate cleanup failure", null);
			store.loadFailure = failure;
			store.unlockFailure = cleanupFailure;

			assertSame(failure, assertThrows(StorePersistenceException.class, () -> controller.open(candidate)));

			assertEquals(List.of(cleanupFailure), List.of(failure.getSuppressed()));
			assertSame(data, controller.getData());
			assertEquals(active, controller.getActiveIncidentId());
			assertTrue(controller.isDirty());
			assertTrue(other.isLockedElsewhere(active));
			assertEquals(List.of(candidate), store.releasedLocks);
			assertTrue(other.lock(candidate));
		}
	}

	@Test
	void failedCandidatePreparationRetainsActiveIdentitySourceAndDirtyEdits() throws ReflectiveOperationException {
		try (ScheduledFailureFileStore store = new ScheduledFailureFileStore(directory);
				FileIncidentStore other = new FileIncidentStore(directory)) {
			AppData activeData = new AppData();
			IncidentId active = store.create(activeData);
			AppController controller = controller(store, active, activeData);
			controller.markDirty(AppController.LinkSource.ICS202);
			activeData.getOrganizationalChart().setIncidentCommanders(List.of("Unsaved chart commander"));
			activeData.getForm202().setApprovedByIncidentCommanderName("Unsaved form commander");
			AppData candidateData = new AppData();
			IncidentId candidate = store.create(candidateData);
			IllegalStateException failure = new IllegalStateException("Cannot prepare candidate form");
			AppData malformed = new RejectingCandidateData(failure);
			malformed.setIncidentId(candidate.value());
			store.nextSnapshot = new StoredIncident(malformed, store.revisionToken(candidateData));

			assertSame(failure, assertThrows(IllegalStateException.class, () -> controller.open(candidate)));

			assertSame(activeData, controller.getData());
			assertEquals(active, controller.getActiveIncidentId());
			assertEquals(AppController.LinkSource.ICS202, activeLinkSource(controller));
			assertTrue(controller.isDirty());
			assertEquals(List.of("Unsaved chart commander"), activeData.getOrganizationalChart().getIncidentCommanders());
			assertEquals("Unsaved form commander", activeData.getForm202().getApprovedByIncidentCommanderName());
			assertTrue(other.isLockedElsewhere(active));
			assertEquals(List.of(candidate), store.releasedLocks);
			assertTrue(other.lock(candidate));
		}
	}

	@Test
	void successfulOpenAndCreationPrepareOnlyTheCandidateDocument() {
		for (boolean create : List.of(false, true)) {
			try (FileIncidentStore store = new FileIncidentStore(directory.resolve(create ? "create" : "open"))) {
				AppData activeData = new AppData();
				IncidentId active = store.create(activeData);
				AppController controller = controller(store, active, activeData);
				controller.markDirty(AppController.LinkSource.ICS202);
				activeData.getOrganizationalChart().setIncidentCommanders(List.of("Unsaved chart commander"));
				activeData.getForm202().setApprovedByIncidentCommanderName("Unsaved form commander");
				AppData candidate = new AppData();
				candidate.getForm202().setApprovedByIncidentCommanderName("Candidate commander");

				if (create) {
					controller.createNewIncident(candidate);
				} else {
					controller.open(store.create(candidate));
				}

				assertNotEquals(active, controller.getActiveIncidentId());
				assertNotSame(activeData, controller.getData());
				assertFalse(controller.isDirty());
				assertEquals(List.of("Unsaved chart commander"),
						activeData.getOrganizationalChart().getIncidentCommanders());
				assertEquals("Unsaved form commander", activeData.getForm202().getApprovedByIncidentCommanderName());
				assertEquals(List.of("Candidate commander"), controller.getData().getOrganizationalChart().getIncidentCommanders());
				assertEquals("Candidate commander", controller.getData().getForm202().getApprovedByIncidentCommanderName());
			}
		}
	}

	@Test
	void remoteSaveFailureMarksPreviouslyCleanEditorDirty() {
		try (RemoteIncidentStore store = new RemoteIncidentStore()) {
			AppData data = new AppData();
			IncidentId id = store.create(data);
			AppController controller = controller(store, id, data);
			data.getIncidentContext().setIncidentName("Editor input not yet marked dirty");
			assertFalse(controller.isDirty());
			StorePersistenceException failure = new StorePersistenceException("Cannot commit editor input", null);
			store.failNext(ControllableIncidentStore.Operation.SAVE, failure);

			assertSame(failure, assertThrows(StorePersistenceException.class, controller::save));

			assertTrue(controller.isDirty());
			assertSame(data, controller.getData());
			assertEquals(id, controller.getActiveIncidentId());
			assertEquals("Editor input not yet marked dirty", data.getIncidentContext().getIncidentName());
			assertEquals("", store.loadSnapshot(id).data().getIncidentContext().getIncidentName());
		}
	}

	/**
	 * Reads the authoritative linked-field source without changing controller state.
	 *
	 * @param controller active editor controller.
	 * @return authoritative source retained by the controller.
	 * @throws ReflectiveOperationException when the source field cannot be inspected.
	 */
	private AppController.LinkSource activeLinkSource(AppController controller) throws ReflectiveOperationException {
		java.lang.reflect.Field field = AppController.class.getDeclaredField("activeLinkSource");
		field.setAccessible(true);
		return (AppController.LinkSource) field.get(controller);
	}

	/**
	 * Inspects whether an automatic save is still scheduled.
	 *
	 * @param controller active editor controller.
	 * @return whether its autosave timer is running.
	 * @throws ReflectiveOperationException when the timer field cannot be inspected.
	 */
	private boolean autosaveTimerIsRunning(AppController controller) throws ReflectiveOperationException {
		java.lang.reflect.Field field = AppController.class.getDeclaredField("autosaveTimer");
		field.setAccessible(true);
		return ((javax.swing.Timer) field.get(controller)).isRunning();
	}

	/**
	 * Creates a controller for a previously persisted incident.
	 *
	 * @param store incident store.
	 * @param id existing incident identifier.
	 * @param data loaded incident data.
	 * @return initialized controller.
	 */
	private AppController controller(IncidentStore store, IncidentId id, AppData data) {
		return SwingTestLifecycleExtension.track(
				new AppController(data, store, id, new PdfExportService(), new IncidentValidator()));
	}

	/** Injects load and cleanup failures while retaining real file-lock behavior. */
	private static final class ScheduledFailureFileStore extends FileIncidentStore {
		private final List<IncidentId> releasedLocks = new ArrayList<>();
		private RuntimeException loadFailure;
		private RuntimeException unlockFailure;
		private StoredIncident nextSnapshot;

		/**
		 * Opens a file store for deterministic switch failures.
		 *
		 * @param root isolated test store directory.
		 */
		private ScheduledFailureFileStore(Path root) {
			super(root);
		}

		/** {@inheritDoc} */
		@Override
		public synchronized StoredIncident loadSnapshot(IncidentId id) {
			RuntimeException failure = loadFailure;
			loadFailure = null;
			if (failure != null) {
				throw failure;
			}
			StoredIncident snapshot = nextSnapshot;
			nextSnapshot = null;
			if (snapshot != null) {
				return snapshot;
			}
			return super.loadSnapshot(id);
		}

		/** {@inheritDoc} */
		@Override
		public synchronized void unlock(IncidentId id) {
			releasedLocks.add(id);
			super.unlock(id);
			RuntimeException failure = unlockFailure;
			unlockFailure = null;
			if (failure != null) {
				throw failure;
			}
		}
	}

	/** Represents a loaded candidate that cannot be prepared for editing. */
	private static final class RejectingCandidateData extends AppData {
		private final RuntimeException failure;

		/**
		 * Creates a candidate with a deterministic form accessor failure.
		 *
		 * @param failure original preparation exception.
		 */
		private RejectingCandidateData(RuntimeException failure) {
			this.failure = failure;
		}

		/** {@inheritDoc} */
		@Override
		public Ics202Form getForm202() {
			throw failure;
		}
	}
}
