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
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** File-specific behavior of the multi-incident store. */
class FileIncidentStoreTest extends AbstractIncidentStoreContractTest {
	/** {@inheritDoc} */
	@Override
	protected IncidentStore createStore(Path directory) {
		return new ControllableIncidentStore(directory);
	}

	/** {@inheritDoc} */
	@Override
	protected boolean lock(IncidentStore store, IncidentId id) {
		return ((ControllableIncidentStore) store).lock(id);
	}

	/** {@inheritDoc} */
	@Override
	protected void unlock(IncidentStore store, IncidentId id) {
		((ControllableIncidentStore) store).unlock(id);
	}

	/** {@inheritDoc} */
	@Override
	protected void failNextSave(IncidentStore store) {
		((ControllableIncidentStore) store).failNext(ControllableIncidentStore.Operation.SAVE,
				new IllegalStateException("Scheduled save failure"));
	}

	/** {@inheritDoc} */
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
			assertEquals(new RevisionToken("0"), store.list().get(0).revision());
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
	void mergerFailureRestoresCallerMetadataAndCanRetrySameInstance() throws Exception {
		try (FileIncidentStore store = new FileIncidentStore(tempDir)) {
			IncidentId id = store.create(new AppData());
			AppData data = store.load(id);
			var actions = data.getForm201().getCurrentActions();
			org.sarmanagement.icsforms.model.Ics201Form.ActionEntry first =
					new org.sarmanagement.icsforms.model.Ics201Form.ActionEntry();
			first.setActions("First action");
			actions.add(first);
			org.sarmanagement.icsforms.model.Ics201Form.ActionEntry second =
					new org.sarmanagement.icsforms.model.Ics201Form.ActionEntry();
			second.setActions("Second action");
			actions.add(second);
			store.save(id, data);

			AppData retryData = store.load(id);
			actions = retryData.getForm201().getCurrentActions();
			actions.get(1).setId(actions.get(0).getId());
			long originalRevision = retryData.getRevision();
			Instant originalUpdatedAt = retryData.getUpdatedAt();
			Path document = tempDir.resolve("incidents").resolve(id.value() + ".json");
			Path backup = document.resolveSibling(document.getFileName() + ".bak");
			String originalFile = Files.readString(document);
			String originalBackup = Files.readString(backup);
			IllegalStateException failure = assertThrows(IllegalStateException.class, () -> store.save(id, retryData));

			assertTrue(failure.getMessage().contains("duplicate id"));
			assertEquals(originalRevision, retryData.getRevision());
			assertEquals(originalUpdatedAt, retryData.getUpdatedAt());
			assertEquals(originalFile, Files.readString(document));
			assertTrue(Files.exists(backup));
			assertEquals(originalBackup, Files.readString(backup));
			assertFalse(Files.exists(document.resolveSibling(document.getFileName() + ".tmp")));

			actions.get(1).setId(UUID.randomUUID().toString());
			store.save(id, retryData);
			assertEquals(originalRevision + 1, retryData.getRevision());
			assertEquals(originalRevision + 1, store.load(id).getRevision());
			assertNotEquals(actions.get(0).getId(), actions.get(1).getId());
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
			assertThrows(StoreValidationException.class, () -> store.load(id));
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
				assertThrows(StoreValidationException.class, () -> store.load(id), version);
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
		void unknownPropertiesFollowStableIdsWhenRecordsAreDeletedInsertedAndReordered() throws Exception {
			IncidentId id = IncidentId.newId();
			Path incidents = tempDir.resolve("incidents");
			Files.createDirectories(incidents);
			Path document = incidents.resolve(id.value() + ".json");
			String actionA = "00000000-0000-4000-8000-000000000001";
			String actionB = "00000000-0000-4000-8000-000000000002";
			String branchA = "00000000-0000-4000-8000-000000000003";
			String branchB = "00000000-0000-4000-8000-000000000004";
			String divisionA = "00000000-0000-4000-8000-000000000005";
			String divisionB = "00000000-0000-4000-8000-000000000006";
			Files.writeString(document, """
					{"schemaVersion":5,"revision":0,"incidentId":"%s",
					"form201":{"currentActions":[
						{"id":"%s","actions":"Action A","futureAction":"A"},
						{"id":"%s","actions":"Action B","futureAction":"B"}]},
					"organizationalChart":{"assignmentList":{"branches":[
						{"id":"%s","name":"Branch A","futureBranch":"A"},
						{"id":"%s","name":"Branch B","futureBranch":"B","divisions":[
							{"id":"%s","title":"Division A","futureDivision":"A"},
							{"id":"%s","title":"Division B","futureDivision":"B"}]}]}}}
					""".formatted(id.value(), actionA, actionB, branchA, branchB, divisionA, divisionB));

			try (FileIncidentStore store = new FileIncidentStore(tempDir)) {
				AppData data = store.load(id);
				var actions = data.getForm201().getCurrentActions();
				actions.removeIf(action -> actionA.equals(action.getId()));
				org.sarmanagement.icsforms.model.Ics201Form.ActionEntry newAction =
						new org.sarmanagement.icsforms.model.Ics201Form.ActionEntry();
				newAction.setId("00000000-0000-4000-8000-000000000007");
				newAction.setActions("Action C");
				actions.add(newAction);
				java.util.Collections.reverse(actions);

				var branches = data.getOrganizationalChart().getAssignmentList().getBranches();
				branches.removeIf(branch -> branchA.equals(branch.getId()));
				var divisions = branches.get(0).getDivisions();
				java.util.Collections.reverse(divisions);
				org.sarmanagement.icsforms.model.OrgChartEntry newDivision =
						new org.sarmanagement.icsforms.model.OrgChartEntry();
				newDivision.setId("00000000-0000-4000-8000-000000000008");
				newDivision.setTitle("Division C");
				divisions.add(newDivision);
				store.save(id, data);
			}

			JsonNode saved = new ObjectMapper().readTree(document.toFile());
			JsonNode savedActions = saved.path("form201").path("currentActions");
			assertEquals(2, savedActions.size());
			assertEquals("00000000-0000-4000-8000-000000000007", savedActions.get(0).path("id").asText());
			assertFalse(savedActions.get(0).has("futureAction"));
			assertEquals("B", savedActions.get(1).path("futureAction").asText());
			JsonNode savedBranches = saved.path("organizationalChart").path("assignmentList").path("branches");
			assertEquals(1, savedBranches.size());
			assertEquals(branchB, savedBranches.get(0).path("id").asText());
			assertEquals("B", savedBranches.get(0).path("futureBranch").asText());
			JsonNode savedDivisions = savedBranches.get(0).path("divisions");
			assertEquals(3, savedDivisions.size());
			assertEquals(divisionB, savedDivisions.get(0).path("id").asText());
			assertEquals("B", savedDivisions.get(0).path("futureDivision").asText());
			assertEquals(divisionA, savedDivisions.get(1).path("id").asText());
			assertEquals("A", savedDivisions.get(1).path("futureDivision").asText());
			assertFalse(savedDivisions.get(2).has("futureDivision"));
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
	void migrationRejectsAmbiguousLegacyRecordArraysWithUnknownProperties() throws Exception {
		IncidentId id = IncidentId.newId();
		Path incidents = tempDir.resolve("incidents");
		Files.createDirectories(incidents);
		Path document = incidents.resolve(id.value() + ".json");
		String original = "{\"schemaVersion\":5,\"revision\":0,\"openActionItems\":["
				+ "{\"item\":\"Same item\",\"future\":\"unknown A\"},"
				+ "{\"item\":\"Same item\",\"future\":\"unknown B\"}]}";
		Files.writeString(document, original);

		try (FileIncidentStore store = new FileIncidentStore(tempDir)) {
			IllegalStateException failure = assertThrows(IllegalStateException.class, () -> store.load(id));
			assertTrue(failure.getMessage().contains("without stable IDs"));
			assertEquals(original, Files.readString(document));
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

	@Test
	void historicalFixturesMigrateThroughStoredLoadsAndKeepUnknownData() throws Exception {
		Path incidents = tempDir.resolve("incidents");
		Files.createDirectories(incidents);
		List<String> fixtureNames = List.of("schema-5-legacy-aliases.json", "schema-5-missing-identities.json");
		for (String fixtureName : fixtureNames) {
			String fixtureResource = "/incidents/historical/" + fixtureName;
			Path source;
			try (var fixture = getClass().getResourceAsStream(fixtureResource)) {
				assertTrue(fixture != null, fixtureResource);
				JsonNode original = new ObjectMapper().readTree(fixture);
				IncidentId id = IncidentId.of(original.path("incidentId").asText());
				source = incidents.resolve(id.value() + ".json");
				try (var reopenedFixture = getClass().getResourceAsStream(fixtureResource)) {
					assertTrue(reopenedFixture != null, fixtureResource);
					Files.copy(reopenedFixture, source);
				}
			}

			List<String> migratedIds;
			try (FileIncidentStore store = new FileIncidentStore(tempDir)) {
				AppData migrated = store.load(IncidentId.of(
						new ObjectMapper().readTree(source.toFile()).path("incidentId").asText()));
				migratedIds = migrationFixtureIds(migrated);
				assertTrue(migratedIds.size() >= 2);
				migratedIds.forEach(identity -> assertEquals(identity, UUID.fromString(identity).toString()));
				if (fixtureName.equals("schema-5-legacy-aliases.json")) {
					assertEquals("555-0100", migrated.getOrganizationalChart().getSafetyOfficerPhone());
					assertEquals("Carry water", migrated.getSarTaskAssignments().get(0).getSpecialEquipment());
					assertEquals("FIXED_WING", migrated.getTCards().get(0).getCardType().name());
				} else {
					assertEquals("Locate the subject",
							migrated.getForm201().getCurrentActions().get(0).getActions());
					assertNull(migrated.getActivityLogs().get(0).getActivityLog().get(0).getProvenance());
					assertEquals("Legacy team label",
							migrated.getActivityLogs().get(0).getActivityLog().get(0).getResourceIdentifier());
					JsonNode saved = new ObjectMapper().readTree(source.toFile());
					assertEquals("historical fixture", saved.path("futureRoot").path("source").asText());
					assertEquals("historical fixture", saved.path("form201").path("currentActions").get(0)
							.path("futureAction").path("source").asText());
					assertEquals("historical fixture", saved.path("activityLogs").get(0).path("activityLog").get(0)
							.path("futureActivity").path("source").asText());
				}
				assertEquals(migratedIds, migrationFixtureIds(store.load(
						IncidentId.of(migrated.getIncidentId()))));
			}

			try (FileIncidentStore restarted = new FileIncidentStore(tempDir)) {
				assertEquals(migratedIds, migrationFixtureIds(restarted.load(
						IncidentId.of(new ObjectMapper().readTree(source.toFile()).path("incidentId").asText()))));
			}
		}
	}

	@Test
	void provenanceFixtureRoundTripsAndLocalStoresDiscardHostAcceptanceTimes() throws Exception {
		Path incidents = tempDir.resolve("incidents");
		Files.createDirectories(incidents);
		Path source = incidents.resolve("0a10d9f1-64b3-4f66-9b67-a0faaf8f64e3.json");
		try (var fixture = getClass().getResourceAsStream("/incidents/historical/schema-17-provenance.json")) {
			assertTrue(fixture != null);
			Files.copy(fixture, source);
		}

		try (FileIncidentStore store = new FileIncidentStore(tempDir)) {
			IncidentId id = IncidentId.of("0a10d9f1-64b3-4f66-9b67-a0faaf8f64e3");
			AppData loaded = store.load(id);
			assertEquals(AppData.CURRENT_SCHEMA_VERSION, loaded.getSchemaVersion());
			assertEquals("Fictional Operator",
					loaded.getActivityLogs().get(0).getActivityLog().get(0).getProvenance().operatorName());
			assertNull(loaded.getActivityLogs().get(0).getActivityLog().get(0).getProvenance().acceptedAt());
			assertNull(loaded.getClueLogEntries().get(0).getProvenance());
			assertEquals(1, loaded.getSarTaskAssignments().get(0).getLifecycleStatusChanges().size());
			assertEquals(2, loaded.getTCards().get(0).getCheckInOutEvents().size());
			assertNull(loaded.getTCards().get(0).getCheckInOutEvents().get(0).provenance().acceptedAt());

			JsonNode saved = new ObjectMapper().readTree(source.toFile());
			assertTrue(saved.path("activityLogs").get(0).path("activityLog").get(0).path("provenance")
					.get("acceptedAt") == null);
			assertTrue(saved.path("sarTaskAssignments").get(0).path("lifecycleStatusChanges").get(0)
					.path("provenance").get("acceptedAt") == null);
			assertEquals(loaded.getActivityLogs().get(0).getActivityLog().get(0).getProvenance(),
					store.load(id).getActivityLogs().get(0).getActivityLog().get(0).getProvenance());
		}

		Path legacyLocalFile = tempDir.resolve("legacy-local.json");
		try (var fixture = getClass().getResourceAsStream("/incidents/historical/schema-17-provenance.json")) {
			assertTrue(fixture != null);
			Files.copy(fixture, legacyLocalFile);
		}
		AppData localData = new LocalRepository(legacyLocalFile).loadOrDefault();
		assertNull(localData.getActivityLogs().get(0).getActivityLog().get(0).getProvenance().acceptedAt());
		assertTrue(new ObjectMapper().readTree(legacyLocalFile.toFile()).path("activityLogs").get(0).path("activityLog")
				.get(0).path("provenance").get("acceptedAt") == null);
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

	/**
	 * Collects IDs present in representative historical fixtures for repeatability checks.
	 *
	 * @param data migrated fixture incident
	 * @return all identities exercised by the fixture
	 */
	private static List<String> migrationFixtureIds(AppData data) {
		List<String> identities = new java.util.ArrayList<>();
		identities.add(data.getIncidentId());
		data.getForm201().getCurrentActions().forEach(record -> identities.add(record.getId()));
		data.getSarTaskAssignments().forEach(record -> identities.add(record.getTaskId()));
		data.getTCards().forEach(record -> identities.add(record.getResourceId()));
		data.getOpenActionItems().forEach(record -> identities.add(record.getId()));
		data.getActivityLogs().forEach(log -> {
			identities.add(log.getLogId());
			log.getActivityLog().forEach(record -> identities.add(record.getEntryId()));
		});
		return List.copyOf(identities);
	}
}
