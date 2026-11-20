/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Covers net-value record diffs and deterministic batch grouping. */
class RecordDiffTest {
	private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

	@Test
	void noChangeProducesAnEmptyDiff() {
		AppData base = new AppData();

		RecordDiff diff = RecordDiff.between(base, copy(base));

		assertTrue(diff.created().isEmpty());
		assertTrue(diff.changed().isEmpty());
		assertTrue(diff.removed().isEmpty());
		assertTrue(diff.struckOut().isEmpty());
		assertTrue(diff.batches().isEmpty());
	}

	@Test
	void createReportsOneNewRecord() {
		AppData base = new AppData();
		AppData edited = copy(base);
		OpenActionItem created = new OpenActionItem();
		edited.getOpenActionItems().add(created);

		RecordDiff diff = RecordDiff.between(base, edited);

		assertEquals(Set.of(key(created)), diff.created());
	}

	@Test
	void changeOneFieldReportsItsExactName() {
		AppData base = new AppData();
		AppData edited = copy(base);
		edited.getIncidentContext().setIncidentName("Changed");
		RecordIndex.RecordKey key = key(base.getIncidentContext());

		assertEquals(Set.of("incidentName"), RecordDiff.between(base, edited).changed().get(key));
	}

	@Test
	void changeTwoFieldsReportsBothNames() {
		AppData base = new AppData();
		AppData edited = copy(base);
		edited.getIncidentContext().setIncidentName("Changed");
		edited.getIncidentContext().setCurrentUser("Responder");
		RecordIndex.RecordKey key = key(base.getIncidentContext());

		assertEquals(Set.of("incidentName", "currentUser"), RecordDiff.between(base, edited).changed().get(key));
	}

	@Test
	void equalValueChangeDoesNotAppearInNetDiff() {
		AppData base = new AppData();
		AppData edited = copy(base);
		edited.getIncidentContext().setIncidentName("Temporary");
		edited.getIncidentContext().setIncidentName(base.getIncidentContext().getIncidentName());

		assertTrue(RecordDiff.between(base, edited).changed().isEmpty());
	}

	@Test
	void deleteAppearsInRemoved() {
		AppData base = new AppData();
		OpenActionItem removed = new OpenActionItem();
		base.getOpenActionItems().add(removed);
		AppData edited = copy(base);
		edited.getOpenActionItems().clear();

		assertEquals(Set.of(key(removed)), RecordDiff.between(base, edited).removed());
	}

	@Test
	void strikeOutIsReportedWithoutRemoval() {
		AppData base = withActivityEntry();
		AppData edited = copy(base);
		ActivityLogEntry entry = edited.getActivityLogs().get(0).getActivityLog().get(0);
		entry.setStruckOut(true);

		RecordDiff diff = RecordDiff.between(base, edited);

		assertEquals(Set.of(key(base.getActivityLogs().get(0).getActivityLog().get(0))), diff.struckOut());
		assertTrue(diff.removed().isEmpty());
	}

	@Test
	void reorderingChildRecordsDoesNotChangeTheDiff() {
		AppData base = withActivityEntry();
		ActivityLogEntry second = new ActivityLogEntry();
		base.getActivityLogs().get(0).getActivityLog().add(second);
		AppData edited = copy(base);
		List<ActivityLogEntry> entries = edited.getActivityLogs().get(0).getActivityLog();
		java.util.Collections.reverse(entries);

		RecordDiff diff = RecordDiff.between(base, edited);

		assertTrue(diff.created().isEmpty());
		assertTrue(diff.changed().isEmpty());
		assertTrue(diff.removed().isEmpty());
	}

	@Test
	void nestedChildAdditionChangesOnlyParentChildIdsAndCreatesChild() {
		AppData base = withActivityEntry();
		AppData edited = copy(base);
		ActivityLogEntry added = new ActivityLogEntry();
		added.setNotableActivity("Added event");
		edited.getActivityLogs().get(0).getActivityLog().add(added);
		RecordIndex.RecordKey formKey = key(edited.getActivityLogs().get(0));
		RecordIndex.RecordKey childKey = key(added);
		RecordIndex baseIndex = RecordIndex.of(base);
		RecordIndex editedIndex = RecordIndex.of(edited);

		RecordDiff diff = RecordDiff.between(base, edited);

		assertEquals(Set.of(childKey), diff.created());
		assertEquals(Set.of("activityLog"), diff.changed().get(formKey));
		assertTrue(baseIndex.record(formKey).path("activityLog").toString()
				.contains(base.getActivityLogs().get(0).getActivityLog().get(0).getId()));
		assertFalse(editedIndex.record(formKey).path("activityLog").toString().contains("Added event"));
	}

	@Test
	void nestedChildRemovalChangesOnlyParentChildIdsAndRemovesChild() {
		AppData base = withActivityEntry();
		AppData edited = copy(base);
		ActivityLogEntry removed = edited.getActivityLogs().get(0).getActivityLog().remove(0);
		RecordIndex.RecordKey formKey = key(edited.getActivityLogs().get(0));

		RecordDiff diff = RecordDiff.between(base, edited);

		assertEquals(Set.of(key(removed)), diff.removed());
		assertEquals(Set.of("activityLog"), diff.changed().get(formKey));
		assertFalse(RecordIndex.of(edited).records().containsKey(key(removed)));
	}

	@Test
	void defaultBatchIsAtomicAndCustomGroupingSplitsOperations() {
		AppData base = new AppData();
		AppData edited = copy(base);
		edited.getIncidentContext().setIncidentName("Changed");
		OpenActionItem added = new OpenActionItem();
		edited.getOpenActionItems().add(added);
		RecordDiff defaultDiff = RecordDiff.between(base, edited);
		RecordDiff grouped = RecordDiff.between(base, edited, key -> key.type().equals(
				IncidentContext.class.getName()) ? "context" : "actions");

		assertEquals(Set.of("default"), defaultDiff.batches().keySet());
		assertEquals(Set.of("actions", "context"), grouped.batches().keySet());
		assertEquals(Set.of(key(added)), grouped.batches().get("actions").created());
		assertEquals(Set.of(key(base.getIncidentContext())), grouped.batches().get("context").changed().keySet());
	}

	@Test
	void repeatedAndInsertionOrderVariantsProduceDeterministicResults() {
		AppData first = withActivityEntry();
		Ics214Form second = new Ics214Form();
		org.sarmanagement.icsforms.TestIdentitySupport.assign(second, "00000000-0000-4000-8000-000000000099");
		first.getActivityLogs().add(second);
		AppData base = copy(first);
		AppData edited = copy(first);
		edited.getActivityLogs().get(0).getActivityLog().get(0).setNotableActivity("Changed");
		AppData reordered = copy(edited);
		java.util.Collections.reverse(reordered.getActivityLogs());

		RecordDiff one = RecordDiff.between(base, edited);
		RecordDiff repeated = RecordDiff.between(base, edited);
		RecordDiff insertionVariant = RecordDiff.between(base, reordered);

		assertEquals(List.copyOf(one.created()), List.copyOf(repeated.created()));
		assertEquals(List.copyOf(one.changed().keySet()), List.copyOf(repeated.changed().keySet()));
		assertEquals(one.changed(), repeated.changed());
		assertEquals(one.changed(), insertionVariant.changed());
		assertEquals(List.copyOf(RecordIndex.of(edited).records().keySet()),
				List.copyOf(RecordIndex.of(reordered).records().keySet()));
	}

	/**
	 * Creates a document with one activity form and entry.
	 *
	 * @return prepared incident document
	 */
	private AppData withActivityEntry() {
		AppData data = new AppData();
		Ics214Form form = new Ics214Form();
		form.getActivityLog().add(new ActivityLogEntry());
		data.getActivityLogs().add(form);
		return data;
	}

	/**
	 * Creates a detached Jackson copy for comparison tests.
	 *
	 * @param source document to copy
	 * @return detached document copy
	 */
	private AppData copy(AppData source) {
		return mapper.convertValue(source, AppData.class);
	}

	/**
	 * Builds the record key used by the index and diff.
	 *
	 * @param value identified model record
	 * @return corresponding record key
	 */
	private static RecordIndex.RecordKey key(Identified value) {
		return new RecordIndex.RecordKey(value.getClass().getName(), value.getId());
	}
}
