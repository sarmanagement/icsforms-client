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
import org.sarmanagement.icsforms.model.ResourceAssignment;
import org.sarmanagement.icsforms.model.SarTaskAssignment;
import org.sarmanagement.icsforms.persistence.FileIncidentStore;
import org.sarmanagement.icsforms.persistence.IncidentId;
import org.sarmanagement.icsforms.persistence.ImportValidationException;
import org.sarmanagement.icsforms.persistence.StoreConflictException;
import org.sarmanagement.icsforms.pdf.PdfExportService;
import org.sarmanagement.icsforms.validation.IncidentValidator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
			AppData external = store.load(first);
			external.getIncidentContext().setIncidentName("External");
			store.save(first, external);
			controller.getData().getIncidentContext().setIncidentName("Local");
			controller.markDirty();

			assertThrows(StoreConflictException.class, controller::save);
			assertTrue(controller.isDirty());
			assertEquals(first, controller.getActiveIncidentId());
			assertEquals("Local", controller.getData().getIncidentContext().getIncidentName());
			assertEquals("External", store.load(first).getIncidentContext().getIncidentName());
			assertFalse(store.list().isEmpty());

			IncidentId preserved = controller.duplicateIncident();
			assertNotEquals(first, preserved);
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

	/**
	 * Creates a controller for a previously persisted incident.
	 *
	 * @param store file store.
	 * @param id existing incident identifier.
	 * @param data loaded incident data.
	 * @return initialized controller.
	 */
	private AppController controller(FileIncidentStore store, IncidentId id, AppData data) {
		return SwingTestLifecycleExtension.track(
				new AppController(data, store, id, new PdfExportService(), new IncidentValidator()));
	}
}
