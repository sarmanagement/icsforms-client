/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.file.Path;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;

/**
 * Focused tests for persisted identity serialization and record-level comparison.
 */
class RecordIdentityTest {
	private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

	/**
	 * Reads legacy identity properties and writes the canonical identity property.
	 *
	 * @throws Exception when Jackson conversion fails
	 */
	@ParameterizedTest
	@MethodSource("legacyIdentityCases")
	void legacyIdentityAliasReadsAndCanonicalIdWrites(Class<? extends Identified> type, String legacyName,
			String additionalProperties)
			throws Exception {
		String id = UUID.randomUUID().toString();
		Identified loaded = mapper.readValue(
				"{\"" + legacyName + "\":\"" + id + "\"" + additionalProperties + "}", type);
		JsonNode written = mapper.valueToTree(loaded);

		assertEquals(id, loaded.getId());
		assertEquals(id, written.path("id").asText());
		assertFalse(written.has(legacyName));
	}

	/**
	 * Provides every legacy serialized identity property still supported by the model.
	 *
	 * @return legacy identity aliases and their record types
	 */
	static Stream<Arguments> legacyIdentityCases() {
		return Stream.of(
				Arguments.of(ActivityLogEntry.class, "entryId", ""),
				Arguments.of(Ics204Form.class, "formId", ""),
				Arguments.of(Ics214Form.class, "logId", ""),
				Arguments.of(SarTaskAssignment.class, "taskId", ""),
				Arguments.of(RadioChannel.class, "channelId", ""),
				Arguments.of(IncidentObjective.class, "objectiveId", ""),
				Arguments.of(Ics215Form.class, "worksheetId", ""),
				Arguments.of(AppData.OperationalPeriodRecord.class, "recordId", ""),
				Arguments.of(Ics202ReportedResult.class, "resultId", ""),
				Arguments.of(ResourceAssignment.class, "assignmentId", ""),
				Arguments.of(TCard.class, "resourceId", ""),
				Arguments.of(IncidentResource.class, "resourceId", ",\"resourceType\":\"GENERIC\""));
	}

	/**
	 * Reads reference properties without confusing them for the owning record identity.
	 *
	 * @throws Exception when Jackson conversion fails
	 */
	@Test
	void referencePropertiesAreNotTreatedAsIdentityAliases() throws Exception {
		String referenceId = UUID.randomUUID().toString();
		SarTaskAssignment task = mapper.readValue(
				"{\"taskId\":\"task-identity\",\"assignmentId\":\"" + referenceId + "\"}",
				SarTaskAssignment.class);
		Ics202ReportedResult result = mapper.readValue(
				"{\"resultId\":\"result-identity\",\"objectiveId\":\"" + referenceId + "\"}",
				Ics202ReportedResult.class);
		SafetyHazardReport hazard = mapper.readValue(
				"{\"id\":\"hazard-identity\",\"taskId\":\"" + referenceId + "\"}", SafetyHazardReport.class);
		SarTaskResource resource = mapper.readValue(
				"{\"id\":\"resource-row\",\"resourceId\":\"" + referenceId + "\"}", SarTaskResource.class);
		Ics214Form linkedFormLog = mapper.readValue(
				"{\"logId\":\"log-identity\",\"linkedIcs204FormId\":\"linked-form\"}", Ics214Form.class);
		Ics214Form linkedTaskLog = mapper.readValue(
				"{\"logId\":\"log-identity\",\"linkedSarTaskAssignmentId\":\"linked-task\"}", Ics214Form.class);
		Ics214Form linkedResourceLog = mapper.readValue(
				"{\"logId\":\"log-identity\",\"linkedResourceId\":\"linked-resource\"}", Ics214Form.class);

		assertEquals(referenceId, task.getAssignmentId());
		assertEquals("task-identity", task.getId());
		assertEquals(referenceId, result.getObjectiveId());
		assertEquals("result-identity", result.getId());
		assertEquals(referenceId, hazard.getTaskId());
		assertEquals("hazard-identity", hazard.getId());
		assertEquals(referenceId, resource.getResourceId());
		assertEquals("resource-row", resource.getId());
		assertEquals("linked-form", linkedFormLog.getLinkedIcs204FormId());
		assertEquals("log-identity", linkedFormLog.getId());
		assertEquals("linked-task", linkedTaskLog.getLinkedSarTaskAssignmentId());
		assertEquals("log-identity", linkedTaskLog.getId());
		assertEquals("linked-resource", linkedResourceLog.getLinkedResourceId());
		assertEquals("log-identity", linkedResourceLog.getId());
	}

	/**
	 * Keeps identity absent on reads until load-time migration assigns it.
	 */
	@Test
	void identityGetterDoesNotGenerateId() {
		ActivityLogEntry entry = new ActivityLogEntry();
		entry.setId(null);

		assertEquals(null, entry.getId());
		entry.setId(UUID.randomUUID().toString());
		assertNotNull(entry.getId());
	}

	/**
	 * Keeps task and T-card identity getters pure until migration assigns identities.
	 */
	@Test
	void taskAndTCardIdentityGettersArePureAndStable() throws Exception {
		TCard card = new TCard();
		card.setId(null);
		SarTaskAssignment task = new SarTaskAssignment();
		task.setId(null);

		assertNull(card.getResourceId());
		assertNull(card.getResourceId());
		assertNull(card.getId());
		assertNull(task.getTaskId());
		assertNull(task.getTaskId());
		assertNull(task.getId());

		AppData legacy = mapper.readValue("""
				{"schemaVersion":17,"tcards":[{}],"sarTaskAssignments":[{}]}
				""", AppData.class);
		assertNull(legacy.getTCards().get(0).getResourceId());
		assertNull(legacy.getTCards().get(0).getResourceId());
		assertNull(legacy.getSarTaskAssignments().get(0).getTaskId());
		assertNull(legacy.getSarTaskAssignments().get(0).getTaskId());
		IdentityMigration.migrate(legacy);
		TCard migratedCard = legacy.getTCards().get(0);
		SarTaskAssignment migratedTask = legacy.getSarTaskAssignments().get(0);
		String cardId = migratedCard.getId();
		String taskId = migratedTask.getId();

		assertNotNull(cardId);
		assertNotNull(taskId);
		assertEquals(cardId, migratedCard.getResourceId());
		assertEquals(cardId, migratedCard.getResourceId());
		assertEquals(taskId, migratedTask.getTaskId());
		assertEquals(taskId, migratedTask.getTaskId());
		assertNotNull(new TCard().getId());
		assertNotNull(new SarTaskAssignment().getId());
	}

	/**
	 * Ensures every persisted record in the example document has a unique nonblank identity.
	 *
	 * @throws Exception when the example document cannot be read
	 */
	@Test
	void exampleDocumentHasStableIdentityForEveryRecord() throws Exception {
		AppData data = mapper.readValue(Path.of("docs/examples/maple-ridge-dementia-op1.json").toFile(),
				AppData.class);
		IdentityMigration.migrate(data);

		RecordIdentityAssertions.collect(data);
	}

	/**
	 * Detects a net field edit and ignores a child-list reorder.
	 */
	@Test
	void recordDiffUsesNetFieldValuesAndIgnoresChildReordering() {
		AppData base = new AppData();
		base.normalizeIdentity();
		AppData edited = mapper.convertValue(base, AppData.class);
		edited.normalizeIdentity();
		edited.getIncidentContext().setIncidentName("Changed");
		Ics214Form first = new Ics214Form();
		Ics214Form second = new Ics214Form();
		edited.getActivityLogs().add(first);
		edited.getActivityLogs().add(second);
		AppData reordered = mapper.convertValue(edited, AppData.class);
		reordered.getActivityLogs().clear();
		reordered.getActivityLogs().add(second);
		reordered.getActivityLogs().add(first);

		RecordDiff diff = RecordDiff.between(base, edited);
		RecordDiff reorderedDiff = RecordDiff.between(base, reordered);
		RecordIndex.RecordKey contextKey = new RecordIndex.RecordKey(
				IncidentContext.class.getName(), base.getIncidentContext().getId());

		assertEquals(java.util.Set.of("incidentName"), diff.changed().get(contextKey));
		assertEquals(diff.created(), reorderedDiff.created());
		assertEquals(diff.changed(), reorderedDiff.changed());
		assertTrue(RecordIndex.of(edited).records().containsKey(contextKey));
	}

	/**
	 * Indexes child records separately and groups net creates, removals and strike-outs.
	 */
	@Test
	void recordDiffIndexesNestedRecordsAndGroupsAtomicBatches() {
		AppData base = new AppData();
		Ics214Form form = new Ics214Form();
		ActivityLogEntry unchanged = new ActivityLogEntry();
		unchanged.setNotableActivity("unchanged");
		ActivityLogEntry removed = new ActivityLogEntry();
		removed.setNotableActivity("removed");
		form.getActivityLog().addAll(java.util.List.of(unchanged, removed));
		base.getActivityLogs().add(form);
		base.normalizeIdentity();

		AppData edited = mapper.convertValue(base, AppData.class);
		Ics214Form editedForm = edited.getActivityLogs().get(0);
		ActivityLogEntry editedUnchanged = editedForm.getActivityLog().get(0);
		editedUnchanged.setNotableActivity("temporarily changed");
		editedUnchanged.setNotableActivity("unchanged");
		ActivityLogEntry added = new ActivityLogEntry();
		added.setNotableActivity("added");
		added.setStruckOut(true);
		editedForm.getActivityLog().remove(1);
		editedForm.getActivityLog().add(added);

		RecordIndex index = RecordIndex.of(edited);
		RecordIndex.RecordKey formKey = new RecordIndex.RecordKey(Ics214Form.class.getName(), editedForm.getId());
		RecordIndex.RecordKey addedKey = new RecordIndex.RecordKey(ActivityLogEntry.class.getName(), added.getId());
		RecordIndex.RecordKey removedKey = new RecordIndex.RecordKey(ActivityLogEntry.class.getName(),
				removed.getId());
		JsonNode formSnapshot = index.record(formKey);
		JsonNode children = formSnapshot.path("activityLog");
		RecordDiff diff = RecordDiff.between(base, edited,
				key -> key.type().equals(ActivityLogEntry.class.getName()) ? "events" : "forms");

		assertEquals(formKey, index.parentOf(addedKey));
		assertTrue(children.toString().contains(added.getId()));
		assertTrue(diff.created().contains(addedKey));
		assertTrue(diff.removed().contains(removedKey));
		assertTrue(diff.struckOut().contains(addedKey));
		assertFalse(diff.changed().containsKey(new RecordIndex.RecordKey(ActivityLogEntry.class.getName(),
				unchanged.getId())));
		assertTrue(diff.batches().get("events").created().contains(addedKey));
		assertTrue(diff.batches().get("events").removed().contains(removedKey));
		assertTrue(diff.batches().get("forms").changed().containsKey(formKey));
	}

}
