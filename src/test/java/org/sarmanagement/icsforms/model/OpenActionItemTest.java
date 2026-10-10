/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Regression tests for optional tracker persistence and clue responsibility. */
class OpenActionItemTest {
	@Test
	void incidentLevelActionsAndPreferencesSurviveNewOperationalPeriod() {
		AppData data = new AppData();
		OpenActionItem action = new OpenActionItem();
		action.setItem("Unfinished IC/UC work");
		action.setPoc("Incident Commander");
		data.setOpenActionItems(List.of(action));
		data.setOpenActionTrackerEnabled(true);
		data.setIcs233Portrait(true);
		data.setClueLogIcs233Enabled(true);
		data.setClueLogEntries(List.of(new ClueLogEntry()));
		data.advanceToNewOperationalPeriod();
		assertSame(action, data.getOpenActionItems().get(0));
		assertEquals("Unfinished IC/UC work", data.getOpenActionItems().get(0).getItem());
		assertTrue(data.isOpenActionTrackerEnabled());
		assertTrue(data.isIcs233Portrait());
		assertTrue(data.isClueLogIcs233Enabled());
		assertTrue(data.getClueLogEntries().isEmpty());
		assertEquals(1, data.getOperationalPeriodHistory().size());
	}

	@Test
	void completedStatusIsCaseInsensitiveAndTrimmed() {
		OpenActionItem item = new OpenActionItem();
		item.setStatus("  CoMpLeTeD  ");
		assertTrue(item.isCompleted());
		item.setStatus("In progress");
		assertFalse(item.isCompleted());
		item.setStatus(null);
		assertFalse(item.isCompleted());
		item.setActualDate(LocalDateTime.parse("2026-10-10T12:00"));
		assertTrue(item.isCompleted());
	}

	@Test
	void briefingPersistenceIsIndependentOfJsonPropertyOrder() throws Exception {
		ObjectMapper mapper = new ObjectMapper();
		OpenActionItem briefingFirst = mapper.readValue("{\"briefedToPoc\":true,\"poc\":\"Ops\"}", OpenActionItem.class);
		OpenActionItem pocFirst = mapper.readValue("{\"poc\":\"Ops\",\"briefedToPoc\":true}", OpenActionItem.class);
		assertTrue(briefingFirst.isBriefedToPoc());
		assertTrue(pocFirst.isBriefedToPoc());
		assertEquals("Ops", briefingFirst.getPoc());
		assertEquals("Ops", pocFirst.getPoc());
		briefingFirst.setPoc(null);
		assertTrue(briefingFirst.isBriefedToPoc());
		assertEquals("", briefingFirst.getPoc());
	}

	@Test
	void legacyDocumentsKeepDefaultsAndNullInputsAreSafe() throws Exception {
		ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
		AppData data = mapper.readValue("{\"clueLogEntries\":[{\"description\":\"old clue\"}]}", AppData.class);
		assertFalse(data.isOpenActionTrackerEnabled());
		assertFalse(data.isIcs233Portrait());
		assertFalse(data.isClueLogIcs233Enabled());
		assertTrue(data.getOpenActionItems().isEmpty());
		assertTrue(data.getClueLogEntries().get(0).getFollowUpAssignments().isEmpty());
		data.setOpenActionItems(null);
		assertTrue(data.getOpenActionItems().isEmpty());
		OpenActionItem item = mapper.readValue(
				"{\"item\":null,\"poc\":null,\"status\":null,\"futureField\":true}", OpenActionItem.class);
		assertEquals("", item.getItem());
		assertEquals("", item.getPoc());
		assertEquals("", item.getStatus());
		assertFalse(item.isCompleted());
		data.setOpenActionItems(Arrays.asList(null, item));
		assertEquals(List.of(item), data.getOpenActionItems());
	}

	@Test
	void trackerAndClueRoundTripWithUtcDatesAndDerivedPropertiesIgnored() throws Exception {
		ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
		LocalDateTime time = LocalDateTime.parse("2026-10-10T12:34");
		OpenActionItem item = new OpenActionItem();
		item.setNumber(1);
		item.setItem("Call agency");
		item.setPoc("Operations");
		item.setBriefedToPoc(true);
		item.setStartDate(time);
		item.setTargetDate(time.plusDays(1));
		item.setActualDate(time.plusHours(2));
		item.setStatus("Done");
		assertTrue(item.isCompleted());
		ClueLogEntry clue = new ClueLogEntry();
		clue.setFollowUpAssignments(List.of(new PocAssignment("First", time), new PocAssignment("Second", time.plusHours(1))));
		clue.setFollowUpBriefed(true);
		clue.setFollowUpTargetDate(time.plusDays(1));
		clue.setFollowUpCompletedDate(time.plusHours(2));
		clue.setFollowUpStatus("Checked");
		AppData data = new AppData();
		data.setOpenActionItems(List.of(item));
		data.setClueLogEntries(List.of(clue));
		data.setOpenActionTrackerEnabled(true);
		data.setIcs233Portrait(true);
		data.setClueLogIcs233Enabled(true);
		String json = mapper.writeValueAsString(data);
		assertFalse(json.contains("\"completed\":"));
		assertFalse(json.contains("\"followUpAssignedTo\":"));
		AppData restored = mapper.readValue(json, AppData.class);
		assertEquals(time, restored.getOpenActionItems().get(0).getStartDate());
		assertTrue(restored.getOpenActionItems().get(0).isCompleted());
		assertEquals("Second", restored.getClueLogEntries().get(0).getFollowUpAssignedTo());
		assertEquals(time.plusDays(1), restored.getClueLogEntries().get(0).getFollowUpTargetDate());
		assertEquals(time.plusHours(2), restored.getClueLogEntries().get(0).getFollowUpCompletedDate());
		assertTrue(restored.getClueLogEntries().get(0).isFollowUpBriefed());
		assertEquals("Checked", restored.getClueLogEntries().get(0).getFollowUpStatus());
		assertTrue(restored.isIcs233Portrait());
		assertTrue(restored.isClueLogIcs233Enabled());
		restored.setOpenActionTrackerEnabled(false);
		restored.setIncidentMode(IncidentMode.GENERIC);
		assertEquals(1, restored.getOpenActionItems().size());
		assertEquals(1, restored.getClueLogEntries().size());
		item.setActualDate(null);
		assertFalse(item.isCompleted());
	}

	@Test
	void historyUsesLastRecordedAssignmentIncludingUnassignment() {
		ClueLogEntry clue = new ClueLogEntry();
		clue.setFollowUpAssignments(Arrays.asList(null, new PocAssignment("First", null), new PocAssignment(null, null)));
		assertEquals(2, clue.getFollowUpAssignments().size());
		assertEquals("", clue.getFollowUpAssignedTo());
		clue.setFollowUpStatus(null);
		assertEquals("", clue.getFollowUpStatus());
		clue.setFollowUpAssignments(null);
		assertEquals("", clue.getFollowUpAssignedTo());
	}
}
