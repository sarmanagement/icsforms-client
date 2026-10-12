// SPDX-License-Identifier: GPL-3.0-or-later
package org.sarmanagement.icsforms.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies backward-compatible persisted assignment context and stable activity identity. */
class ActivityLogEntryTest {
	private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

	@Test
	void assignmentContextAndExistingEntryIdSurviveJsonRoundTrip() throws Exception {
		ActivityLogEntry entry = new ActivityLogEntry();
		entry.setEntryId("activity-existing-id");
		entry.setAssignmentId("assignment-stable-id");
		entry.setResourceIdentifier("Robin");
		entry.setTimestamp(LocalDateTime.of(2026, 1, 1, 8, 30));
		entry.setNotableActivity("Task observation");
		String json = mapper.writeValueAsString(entry);
		assertEquals("assignment-stable-id", mapper.readTree(json).get("assignmentId").asText());
		assertEquals("activity-existing-id", mapper.readTree(json).get("entryId").asText());
		ActivityLogEntry restored = mapper.readValue(json, ActivityLogEntry.class);
		assertEquals(entry.getEntryId(), restored.getEntryId());
		assertEquals(entry.getAssignmentId(), restored.getAssignmentId());
		assertEquals(entry.getResourceIdentifier(), restored.getResourceIdentifier());
		assertEquals(entry.getTimestamp(), restored.getTimestamp());
		assertEquals(entry.getNotableActivity(), restored.getNotableActivity());
	}

	@Test
	void legacyJsonWithoutAssignmentContextKeepsExistingEntryId() throws Exception {
		ActivityLogEntry restored = mapper.readValue(
				"{\"entryId\":\"legacy-id\",\"resourceIdentifier\":\"Robin\",\"eventTypeId\":\"FREE_TEXT\"}",
				ActivityLogEntry.class);
		assertEquals("legacy-id", restored.getEntryId());
		assertEquals("", restored.getAssignmentId());
		assertEquals("Robin", restored.getResourceIdentifier());
	}

	@Test
	void nullAssignmentContextNormalizesToEmptyWithoutChangingEntryId() throws Exception {
		ActivityLogEntry restored = mapper.readValue(
				"{\"entryId\":\"preserved-id\",\"assignmentId\":null}", ActivityLogEntry.class);
		assertEquals("", restored.getAssignmentId());
		restored.setAssignmentId("assignment-new");
		assertEquals("preserved-id", restored.getEntryId());
		restored.setAssignmentId(null);
		assertEquals("", restored.getAssignmentId());
		assertEquals("preserved-id", restored.getEntryId());
	}

	@Test
	void legacyJsonWithoutAnyIdentityStillGeneratesStableActivityId() throws Exception {
		ActivityLogEntry restored = mapper.readValue("{}", ActivityLogEntry.class);
		assertEquals("", restored.getAssignmentId());
		String generated = restored.getEntryId();
		assertTrue(!generated.isBlank());
		assertEquals(generated, restored.getEntryId());
	}
}
