/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.model.AppData;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** File-specific behavior of the multi-incident store. */
class FileIncidentStoreTest extends AbstractIncidentStoreContractTest {
	@Override
	protected IncidentStore createStore(Path directory) {
		return new ControllableIncidentStore(directory);
	}

	@Override
	protected boolean lock(IncidentStore store, IncidentId id) {
		return ((ControllableIncidentStore) store).lock(id);
	}

	@Override
	protected void unlock(IncidentStore store, IncidentId id) {
		((ControllableIncidentStore) store).unlock(id);
	}

	@Override
	protected void failNextSave(IncidentStore store) {
		((ControllableIncidentStore) store).failNext(ControllableIncidentStore.Operation.SAVE,
				new IllegalStateException("Scheduled save failure"));
	}

	@Override
	protected void failNextDelete(IncidentStore store) {
		((ControllableIncidentStore) store).failNext(ControllableIncidentStore.Operation.DELETE,
				new IllegalStateException("Scheduled delete failure"));
	}

	/** Backups and temporary writes are isolated per incident. */
	@Test
	void backupsArePerIncidentAndCorruptionRecovers() throws Exception {
		try (FileIncidentStore store = new FileIncidentStore(tempDir)) {
			IncidentId first = store.create(new AppData());
			IncidentId second = store.create(new AppData());
			AppData one = store.load(first);
			AppData two = store.load(second);
			one.getIncidentContext().setIncidentName("One");
			two.getIncidentContext().setIncidentName("Two");
			store.save(first, one);
			store.save(second, two);
			Path firstFile = tempDir.resolve("incidents").resolve(first.value() + ".json");
			Path secondFile = tempDir.resolve("incidents").resolve(second.value() + ".json");
			assertTrue(Files.exists(firstFile.resolveSibling(firstFile.getFileName() + ".bak")));
			assertTrue(Files.exists(secondFile.resolveSibling(secondFile.getFileName() + ".bak")));
			assertFalse(Files.exists(firstFile.resolveSibling(firstFile.getFileName() + ".tmp")));
			Files.writeString(firstFile, "{invalid");
			assertEquals(first, IncidentId.of(store.load(first).getIncidentId()));
			assertEquals("Two", store.load(second).getIncidentContext().getIncidentName());
		}
	}

	/** Single-workspace migration leaves its input untouched and runs only once. */
	@Test
	void migrationIsOnceOnly() throws Exception {
		Files.writeString(tempDir.resolve("incident.json"), "{\"schemaVersion\":5}");
		try (FileIncidentStore store = new FileIncidentStore(tempDir)) {
			assertEquals(1, store.list().size());
			assertEquals(AppData.CURRENT_SCHEMA_VERSION, store.load(store.list().get(0).incidentId()).getSchemaVersion());
			assertEquals(0, store.list().get(0).revision());
			assertTrue(Files.exists(tempDir.resolve("incident.json.migrated")));
			assertEquals("{\"schemaVersion\":5}", Files.readString(tempDir.resolve("incident.json")));
		}
		try (FileIncidentStore reopened = new FileIncidentStore(tempDir)) {
			assertEquals(1, reopened.list().size());
		}
	}

	/** Missing v5 identity is assigned on load and retained when saved. */
	@Test
	void legacyVersionInIncidentDirectoryUpgradesOnLoad() throws Exception {
		IncidentId id = IncidentId.newId();
		Path directory = tempDir.resolve("incidents");
		Files.createDirectories(directory);
		Files.writeString(directory.resolve(id.value() + ".json"),
				"{\"schemaVersion\":5,\"revision\":0,\"futureField\":true}");
		try (FileIncidentStore store = new FileIncidentStore(tempDir)) {
			AppData upgraded = store.load(id);
			assertEquals(id.value(), upgraded.getIncidentId());
			assertEquals(AppData.CURRENT_SCHEMA_VERSION, upgraded.getSchemaVersion());
			assertEquals(0, upgraded.getRevision());
			store.save(id, upgraded);
			assertEquals(1, store.load(id).getRevision());
		}
	}

	@Test
	void newerSchemaIsRejectedWithoutChangingTheIncidentDocument() throws Exception {
		IncidentId id = IncidentId.newId();
		Path directory = tempDir.resolve("incidents");
		Files.createDirectories(directory);
		Path document = directory.resolve(id.value() + ".json");
		String original = "{\"schemaVersion\":" + (AppData.CURRENT_SCHEMA_VERSION + 1) + ",\"incidentId\":\""
				+ id.value()
				+ "\",\"futurePlan\":{\"mustKeep\":true}}";
		Files.writeString(document, original);

		try (FileIncidentStore store = new FileIncidentStore(tempDir)) {
			assertThrows(IllegalStateException.class, () -> store.load(id));
			assertEquals(original, Files.readString(document));
		}
	}

	@Test
	void malformedAndStringFutureSchemasAreRejectedWithoutChangingTheDocument() throws Exception {
		IncidentId id = IncidentId.newId();
		Path directory = tempDir.resolve("incidents");
		Files.createDirectories(directory);
		Path document = directory.resolve(id.value() + ".json");
		try (FileIncidentStore store = new FileIncidentStore(tempDir)) {
			for (String version : List.of("\"" + (AppData.CURRENT_SCHEMA_VERSION + 1) + "\"", "\"future\"", "null",
					AppData.CURRENT_SCHEMA_VERSION + ".5")) {
				String original = "{\"schemaVersion\":" + version + ",\"incidentId\":\"" + id.value()
						+ "\",\"incidentContext\":{\"incidentName\":\"Retained\"}}";
				Files.writeString(document, original);
				assertThrows(IllegalStateException.class, () -> store.load(id), version);
				assertEquals(original, Files.readString(document));
			}
		}
	}

		@Test
		void storedSaveRetainsUnknownRootAndNestedProperties() throws Exception {
			IncidentId id = IncidentId.newId();
			Path incidents = tempDir.resolve("incidents");
			Files.createDirectories(incidents);
			Path document = incidents.resolve(id.value() + ".json");
			Files.writeString(document, """
					{"schemaVersion":5,"revision":0,"incidentId":"%s",
					"futureRoot":{"preserve":"root value"},
					"form202":{"commandEmphasis":"Existing","futureNested":{"preserve":"nested value"}},
					"openActionItems":[{"item":"Coordinate staging","futureRecord":"record value"}]}
					""".formatted(id.value()));

			try (FileIncidentStore store = new FileIncidentStore(tempDir)) {
				AppData data = store.load(id);
				data.getIncidentContext().setIncidentName("Updated incident");
				store.save(id, data);
			}

			JsonNode saved = new ObjectMapper().readTree(document.toFile());
			assertEquals("root value", saved.path("futureRoot").path("preserve").asText());
			assertEquals("nested value", saved.path("form202").path("futureNested").path("preserve").asText());
			assertEquals("record value", saved.path("openActionItems").get(0).path("futureRecord").asText());
			assertEquals("Updated incident", saved.path("incidentContext").path("incidentName").asText());
		}

		@Test
		void migratedIdentityBackupSupportsRepeatableRecovery() throws Exception {
			IncidentId id = IncidentId.newId();
			Path incidents = tempDir.resolve("incidents");
			Files.createDirectories(incidents);
			Path document = incidents.resolve(id.value() + ".json");
			Path backup = document.resolveSibling(document.getFileName() + ".bak");
			String legacy = "{\"schemaVersion\":5,\"revision\":0,\"form201\":{\"currentActions\":[{\"actions\":\"Search\"}]}}";
			Files.writeString(document, legacy);
			Files.writeString(backup, legacy);

			String actionId;
			try (FileIncidentStore store = new FileIncidentStore(tempDir)) {
				actionId = store.load(id).getForm201().getCurrentActions().get(0).getId();
				assertEquals(actionId, new ObjectMapper().readTree(backup.toFile())
						.path("form201").path("currentActions").get(0).path("id").asText());
			}

			for (int attempt = 0; attempt < 2; attempt++) {
				Files.writeString(document, "{corrupt");
				try (FileIncidentStore store = new FileIncidentStore(tempDir)) {
					assertEquals(actionId, store.load(id).getForm201().getCurrentActions().get(0).getId());
				}
			}
		}

	@Test
	void legacyIdentityMigrationIsDurableAcrossLoadsAndRestarts() throws Exception {
		IncidentId id = IncidentId.newId();
		Path incidents = tempDir.resolve("incidents");
		Files.createDirectories(incidents);
		Path document = incidents.resolve(id.value() + ".json");
		Files.writeString(document, """
				{"schemaVersion":5,"revision":0,"futureLegacy":{"retain":true},
				"form201":{"currentActions":[{"actions":"Brief resources"}],
				"resources":[{"resource":"Search team"}]},
				"organizationalChart":{"assignmentList":{"branches":[{"name":"Operations",
				"divisions":[{"title":"Division"}]}]},"additionalPositions":[{"title":"Planning support"}]},
				"form215":{"resourceHeadings":[{"category":"Teams","id":null}],
				"workRows":[{"assignment":"Search","id":null}]},
				"form204":{"resourcesAssigned":[{"assignmentId":"assigned-record",
				"resourceIdentifier":"Legacy Unit"}],"communications":[{"primaryContact":"Command"}]},
				"form208":{"hazardBlocks":[{"hazard":"Low visibility"}]},
				"additionalForms204":[{"communications":[{"primaryContact":"Field"}]}],
				"sarTaskAssignments":[{"resourcesAssigned":[{"name":"Search dog"}],
				"communications":[{"primaryContact":"Task channel"}],
				"qualitativePodFactors":[{"name":"Weather"}]}],
				"medicalPlan":{"aidStations":[{"name":"Base"}],
				"transportServices":[{"service":"Ground transport"}],"hospitals":[{"name":"Hospital"}]},
				"canineMedicalPlan":{"transportServiceEntries":[{"service":"Canine transport"}]},
				"vetDirectory":{"sources":[{"name":"Legacy directory"}]},
				"operationalPeriodHistory":[{"forms204":[{}],"sarTaskAssignments":[{}]}],
				"openActionItems":[{"item":"Coordinate staging"}],
				"archivedSafetyHazardReports":[{"taskId":"legacy-task-ref","taskLabel":"Team 1","hazards":"Low visibility"}],
				"commonSafetyHazards":[{"incidentArea":"Ridge","hazardsRisks":"Loose rock"}],
				"clueLogEntries":[{"description":"Track the trail","followUpAssignments":[{"assignee":"Responder"}]}],
				"activityLogs":[{"activityLog":[{"resourceIdentifier":"Legacy Unit",
				"notableActivity":"Search started",
				"eventTypeChangeHistory":[{"previousEventTypeId":"FREE_TEXT","newEventTypeId":"CLUE_REPORTED",
				"changedAt":"2026-01-01T00:00:00"}]}]}]}
				""");

		List<String> identities;
		try (FileIncidentStore store = new FileIncidentStore(tempDir)) {
			AppData migrated = store.load(id);
			identities = identities(migrated);
			assertEquals("assigned-record", migrated.getActivityLogs().get(0).getActivityLog().get(0).getAssignmentId());
			assertEquals("Legacy Unit",
					migrated.getActivityLogs().get(0).getActivityLog().get(0).getResourceIdentifier());
			assertEquals(identities, identities(store.load(id)));
			assertEquals(0, store.load(id).getRevision());
		}
		assertTrue(Files.readString(document).contains("\"retain\" : true"));

		try (FileIncidentStore reopened = new FileIncidentStore(tempDir)) {
			AppData restored = reopened.load(id);
			assertEquals(identities, identities(restored));
		}
	}

	/** A second store cannot acquire an incident's advisory lock. */
	@Test
	void anotherStoreDetectsLock() {
		try (FileIncidentStore first = new FileIncidentStore(tempDir);
				FileIncidentStore second = new FileIncidentStore(tempDir)) {
			IncidentId id = first.create(new AppData());
			assertTrue(first.lock(id));
			assertTrue(second.isLockedElsewhere(id));
			assertFalse(second.lock(id));
			assertThrows(StoreConflictException.class, () -> second.save(id, second.load(id)));
			assertNotEquals("", first.load(id).getOriginNodeId());
			first.unlock(id);
			assertFalse(second.isLockedElsewhere(id));
		}
		try (FileIncidentStore reopened = new FileIncidentStore(tempDir)) {
			assertFalse(reopened.isLockedElsewhere(reopened.list().get(0).incidentId()));
		}
	}

	@Test
	void lockIoFailureIncludesPathAndCause() throws Exception {
		try (FileIncidentStore store = new FileIncidentStore(tempDir)) {
			IncidentId id = store.create(new AppData());
			Path lockPath = tempDir.resolve("incidents").resolve(id.value() + ".lock");
			Files.createDirectory(lockPath);

			IllegalStateException failure = assertThrows(IllegalStateException.class, () -> store.lock(id));

			assertTrue(failure.getMessage().contains(lockPath.toAbsolutePath().toString()));
			assertTrue(failure.getMessage().contains(failure.getCause().getMessage()));
		}
	}

	/** Store restarts preserve the installation's origin node identity. */
	@Test
	void nodeIdentityPersistsAcrossStoreInstances() {
		String node;
		try (FileIncidentStore first = new FileIncidentStore(tempDir)) {
			node = first.load(first.create(new AppData())).getOriginNodeId();
		}
		try (FileIncidentStore second = new FileIncidentStore(tempDir)) {
			IncidentId next = second.create(new AppData());
			assertEquals(node, second.load(next).getOriginNodeId());
		}
	}

	/**
	 * Captures representative root and nested record identities after legacy migration.
	 *
	 * @param data migrated incident
	 * @return ordered identities for persistence comparisons
	 */
	private static List<String> identities(AppData data) {
		return List.of(data.getIncidentId(), data.getForm201().getCurrentActions().get(0).getId(),
				data.getForm201().getResources().get(0).getId(),
				data.getOrganizationalChart().getAssignmentList().getBranches().get(0).getId(),
				data.getOrganizationalChart().getAssignmentList().getBranches().get(0).getDivisions().get(0).getId(),
				data.getForm215().getResourceHeadings().get(0).id,
				data.getForm215().getWorkRows().get(0).id,
				data.getOrganizationalChart().getAdditionalPositions().get(0).getId(),
				data.getForm204().getCommunications().get(0).getId(),
				data.getForm208().getHazardBlocks().get(0).getId(),
				data.getAdditionalForms204().get(0).getCommunications().get(0).getId(),
				data.getSarTaskAssignments().get(0).getResourcesAssigned().get(0).getId(),
				data.getSarTaskAssignments().get(0).getCommunications().get(0).getId(),
				data.getSarTaskAssignments().get(0).getQualitativePodFactors().get(0).getId(),
				data.getMedicalPlan().getAidStations().get(0).getId(),
				data.getMedicalPlan().getTransportServices().get(0).getId(),
				data.getMedicalPlan().getHospitals().get(0).getId(),
				data.getCanineMedicalPlan().getTransportServiceEntries().get(0).getId(),
				data.getVetDirectory().getSources().get(0).getId(),
				data.getOperationalPeriodHistory().get(0).getRecordId(),
				data.getOperationalPeriodHistory().get(0).getForms204().get(0).getFormId(),
				data.getOperationalPeriodHistory().get(0).getSarTaskAssignments().get(0).getTaskId(),
				data.getOpenActionItems().get(0).getId(),
				data.getArchivedSafetyHazardReports().get(0).getId(),
				data.getCommonSafetyHazards().get(0).getEntryId(),
				data.getClueLogEntries().get(0).getFollowUpAssignments().get(0).getId(),
				data.getActivityLogs().get(0).getLogId(),
				data.getActivityLogs().get(0).getActivityLog().get(0).getEntryId(),
				data.getActivityLogs().get(0).getActivityLog().get(0).getAssignmentId(),
				data.getActivityLogs().get(0).getActivityLog().get(0).getEventTypeChangeHistory().get(0).changeId());
	}
}
