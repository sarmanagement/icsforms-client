/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies stable objectives, authoritative assignment links, and period history. */
class IncidentObjectiveTest {
	@Test
	void migratesLegacyStringsWithoutGuessingLinks() throws Exception {
		String json = """
				{"objectives":["Protect responders","Search sector"],"commandEmphasis":""}
				""";

		Ics202Form form = new ObjectMapper().readValue(json, Ics202Form.class);

		assertEquals(List.of("Protect responders", "Search sector"), form.getObjectives());
		assertEquals(2, form.getStructuredObjectives().size());
		assertTrue(form.getStructuredObjectives().stream()
				.allMatch(objective -> !objective.getObjectiveId().isBlank()));
	}

	@Test
	void reorderAndTextEditPreserveIdentityAndLinks() {
		AppData data = new AppData();
		IncidentObjective first = new IncidentObjective("First", 0);
		IncidentObjective second = new IncidentObjective("Second", 1);
		data.getForm202().setStructuredObjectives(new ArrayList<>(List.of(first, second)));
		ResourceAssignment assignment = new ResourceAssignment();
		assignment.setObjectiveIds(List.of(first.getObjectiveId(), second.getObjectiveId()));
		data.getForm204().getResourcesAssigned().add(assignment);

		first.setText("First revised");
		data.getForm202().setStructuredObjectives(new ArrayList<>(List.of(second, first)));

		assertEquals(first.getObjectiveId(), data.getForm202().getStructuredObjectives().get(1).getObjectiveId());
		assertEquals(List.of(first.getObjectiveId(), second.getObjectiveId()), assignment.getObjectiveIds());
		assertEquals(List.of("Second", "First revised"), data.getForm202().getObjectives());
	}

	@Test
	void linkedRemovalRequiresExplicitResolution() {
		AppData data = new AppData();
		IncidentObjective objective = new IncidentObjective("Search", 0);
		data.getForm202().setStructuredObjectives(new ArrayList<>(List.of(objective)));
		ResourceAssignment assignment = new ResourceAssignment();
		assignment.setObjectiveIds(List.of(objective.getObjectiveId()));
		data.getForm204().getResourcesAssigned().add(assignment);

		assertThrows(IllegalStateException.class,
				() -> data.removeObjective(objective.getObjectiveId(), "", false));

		data.removeObjective(objective.getObjectiveId(), "", true);
		assertTrue(data.getForm202().getStructuredObjectives().isEmpty());
		assertTrue(assignment.getObjectiveIds().isEmpty());
	}

	@Test
	void assessmentRoundTripsAndDoesNotFollowTaskStatus() throws Exception {
		AppData data = new AppData();
		IncidentObjective objective = new IncidentObjective("Search", 0);
		objective.setAssessment(ObjectiveAssessment.ONGOING);
		objective.setAssessmentNote("Crews active");
		objective.setAssessedBy("Planning");
		objective.setAssessedAt(LocalDateTime.parse("2026-10-25T03:30"));
		data.getForm202().setStructuredObjectives(List.of(objective));
		SarTaskAssignment task = new SarTaskAssignment();
		task.setTaskLifecycleStatus("returned");
		data.getSarTaskAssignments().add(task);

		ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
		AppData restored = mapper.readValue(mapper.writeValueAsBytes(data), AppData.class);
		IncidentObjective restoredObjective = restored.getForm202().getStructuredObjectives().get(0);

		assertEquals(ObjectiveAssessment.ONGOING, restoredObjective.getAssessment());
		assertEquals("Crews active", restoredObjective.getAssessmentNote());
	}

	@Test
	void periodAdvanceArchivesIndependentObjectiveAssignmentGraph() {
		AppData data = new AppData();
		IncidentObjective objective = new IncidentObjective("Search", 0);
		data.getForm202().setStructuredObjectives(List.of(objective));
		ResourceAssignment assignment = new ResourceAssignment();
		assignment.setAssignment("Sector A");
		assignment.setObjectiveIds(List.of(objective.getObjectiveId()));
		data.getForm204().getResourcesAssigned().add(assignment);

		data.advanceToNewOperationalPeriod();
		AppData.OperationalPeriodRecord history = data.getOperationalPeriodHistory().get(0);
		IncidentObjective archived = history.getForm202().getStructuredObjectives().get(0);
		ResourceAssignment archivedAssignment = history.getForms204().get(0).getResourcesAssigned().get(0);

		assertNotSame(objective, archived);
		assertEquals(objective.getObjectiveId(), archived.getObjectiveId());
		assertEquals(List.of(objective.getObjectiveId()), archivedAssignment.getObjectiveIds());
		assertTrue(data.getForm202().getStructuredObjectives().isEmpty());
		assertTrue(data.getForm204().getResourcesAssigned().isEmpty());
	}
}
