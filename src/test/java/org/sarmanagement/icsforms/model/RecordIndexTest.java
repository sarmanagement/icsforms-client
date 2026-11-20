/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Covers flattened record snapshots and immediate parent relationships. */
class RecordIndexTest {
	@Test
	void indexContainsEveryIdentifiedRecordFromTheReflectionWalk() throws Exception {
		AppData data = new AppData();
		Ics214Form form = new Ics214Form();
		form.getActivityLog().add(new ActivityLogEntry());
		data.getActivityLogs().add(form);
		data.getOpenActionItems().add(new OpenActionItem());

		RecordIndex index = RecordIndex.of(data);
		Map<Class<?>, Set<String>> walked = RecordIdentityAssertions.collect(data);
		int expectedCount = walked.values().stream().mapToInt(Set::size).sum();

		assertEquals(expectedCount, index.records().size());
	}

	@Test
	void parentOfDistinguishesTopLevelAndNestedRecords() {
		AppData data = new AppData();
		Ics214Form form = new Ics214Form();
		ActivityLogEntry entry = new ActivityLogEntry();
		form.getActivityLog().add(entry);
		data.getActivityLogs().add(form);
		OpenActionItem action = new OpenActionItem();
		data.getOpenActionItems().add(action);
		RecordIndex index = RecordIndex.of(data);
		RecordIndex.RecordKey formKey = key(form);

		assertNull(index.parentOf(formKey));
		assertEquals(formKey, index.parentOf(key(entry)));
		assertNull(index.parentOf(key(action)));
	}

	@Test
	void parentSnapshotContainsChildIdsWithoutChildContent() {
		AppData data = new AppData();
		Ics214Form form = new Ics214Form();
		ActivityLogEntry entry = new ActivityLogEntry();
		entry.setNotableActivity("Private child narrative");
		form.getActivityLog().add(entry);
		data.getActivityLogs().add(form);
		var snapshot = RecordIndex.of(data).record(key(form));

		assertEquals(entry.getId(), snapshot.path("activityLog").get(0).asText());
		assertTrue(!snapshot.toString().contains("Private child narrative"));
	}

	/**
	 * Builds an index key for one identified record.
	 *
	 * @param value identified model record
	 * @return corresponding record key
	 */
	private static RecordIndex.RecordKey key(Identified value) {
		return new RecordIndex.RecordKey(value.getClass().getName(), value.getId());
	}
}
