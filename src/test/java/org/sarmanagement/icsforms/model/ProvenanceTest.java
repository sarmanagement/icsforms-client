/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Covers provenance serialization and compatibility with records created before attribution existed. */
class ProvenanceTest {
	private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule())
			.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

	@Test
	void provenanceRoundTripsAcrossAppendStyleRecords() throws Exception {
		Instant enteredAt = Instant.parse("2026-11-07T10:05:30Z");
		Instant acceptedAt = Instant.parse("2026-11-07T10:06:00Z");
		Provenance provenance = new Provenance("Fictional Operator", "fictional-device-17", enteredAt, acceptedAt);

		AppData data = new AppData();
		ActivityLogEntry activity = new ActivityLogEntry();
		activity.setProvenance(provenance);
		Ics214Form log = new Ics214Form();
		log.setActivityLog(List.of(activity));
		data.setActivityLogs(List.of(log));
		ClueLogEntry clue = new ClueLogEntry();
		clue.setProvenance(provenance);
		data.setClueLogEntries(List.of(clue));
		SarTaskAssignment task = new SarTaskAssignment();
		task.recordLifecycleStatusChange("planned", "assigned - on task", enteredAt, provenance);
		data.setSarTaskAssignments(List.of(task));
		TCard card = new TCard();
		card.setCheckInDateTime(LocalDateTime.of(2026, 11, 7, 10, 0));
		card.recordCheckIn(enteredAt, provenance);
		card.recordCheckOut(acceptedAt, provenance);
		data.setTCards(List.of(card));

		String json = mapper.writeValueAsString(data);
		AppData restored = mapper.readValue(json, AppData.class);

		assertEquals(provenance,
				restored.getActivityLogs().get(0).getActivityLog().get(0).getProvenance());
		assertEquals(provenance, restored.getClueLogEntries().get(0).getProvenance());
		assertEquals(provenance,
				restored.getSarTaskAssignments().get(0).getLifecycleStatusChanges().get(0).provenance());
		assertEquals(provenance, restored.getTCards().get(0).getCheckInOutEvents().get(0).provenance());
		assertEquals(TCard.CheckInOutAction.CHECK_OUT,
				restored.getTCards().get(0).getCheckInOutEvents().get(1).action());
		assertEquals(acceptedAt,
				restored.getActivityLogs().get(0).getActivityLog().get(0).getProvenance().acceptedAt());
	}

	@Test
	void legacyTargetRecordsLoadWithoutProvenanceOrEventHistory() throws Exception {
		ActivityLogEntry activity = mapper.readValue("{\"timestamp\":\"2026-11-07T10:05:00\"}",
				ActivityLogEntry.class);
		ClueLogEntry clue = mapper.readValue("{\"description\":\"Legacy clue\"}", ClueLogEntry.class);
		SarTaskAssignment task = mapper.readValue("{\"taskLifecycleStatus\":\"planned\"}", SarTaskAssignment.class);
		TCard card = mapper.readValue("{\"checkInDateTime\":\"2026-11-07T10:00:00\"}", TCard.class);

		assertNull(activity.getProvenance());
		assertNull(clue.getProvenance());
		assertTrue(task.getLifecycleStatusChanges().isEmpty());
		assertTrue(card.getCheckInOutEvents().isEmpty());
		assertFalse(mapper.valueToTree(activity).has("provenance"));
		assertFalse(mapper.valueToTree(clue).has("provenance"));
		assertFalse(mapper.valueToTree(task).has("lifecycleStatusChanges"));
		assertFalse(mapper.valueToTree(card).has("checkInOutEvents"));
		assertEquals(LocalDateTime.of(2026, 11, 7, 10, 0), card.getCheckInDateTime());
	}

	@Test
	void legacyCheckInOutEventsLoadWithoutOccurrenceIds() throws Exception {
		TCard legacyCard = mapper.readValue("""
				{"checkInOutEvents":[{"action":"CHECK_IN","eventAt":"2026-11-07T10:05:00Z"}]}
				""", TCard.class);
		TCard newCard = new TCard();
		newCard.recordCheckIn(Instant.parse("2026-11-07T10:05:00Z"), null);

		assertNull(legacyCard.getCheckInOutEvents().get(0).eventId());
		assertNull(legacyCard.getCheckInOutEvents().get(0).provenance());
		assertNotNull(newCard.getCheckInOutEvents().get(0).eventId());
	}

	@Test
	void emptyOperatorContextHasNoIdentity() {
		assertTrue(OperatorContext.empty().isEmpty());
		assertEquals("", new OperatorContext(null, " ").operatorName());
		assertNull(new OperatorContext("", " ").stationId());
	}
}
