/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Verifies content-bound objective approval and explicit planning stage persistence. */
class ObjectiveApprovalTest {
	@Test
	void approvalTracksObjectiveWordingAndOrderButNotAssessment() {
		Ics202Form form = new Ics202Form();
		IncidentObjective objective = new IncidentObjective("Protect responders", 0);
		form.setStructuredObjectives(List.of(objective));

		form.recordObjectiveApproval("Incident Commander", "IC", LocalDateTime.parse("2026-10-25T04:15"));
		assertEquals(ObjectiveApprovalStatus.APPROVED_CURRENT, form.getObjectiveApprovalStatus());

		objective.setAssessment(ObjectiveAssessment.ACHIEVED);
		assertEquals(ObjectiveApprovalStatus.APPROVED_CURRENT, form.getObjectiveApprovalStatus());

		objective.setText("Protect responders and public");
		assertEquals(ObjectiveApprovalStatus.CONTENT_CHANGED, form.getObjectiveApprovalStatus());
	}

	@Test
	void approvalRequiresSubstantiveObjectivesAndApprover() {
		Ics202Form form = new Ics202Form();

		assertThrows(IllegalArgumentException.class,
				() -> form.recordObjectiveApproval("IC", "", LocalDateTime.now()));

		form.setStructuredObjectives(List.of(new IncidentObjective("Search", 0)));
		assertThrows(IllegalArgumentException.class,
				() -> form.recordObjectiveApproval("", "", LocalDateTime.now()));
	}

	@Test
	void legacyApprovalIsNotPresentedAsCurrent() {
		Ics202Form form = new Ics202Form();
		form.setStructuredObjectives(List.of(new IncidentObjective("Search", 0)));
		form.setApprovedByIncidentCommanderName("Legacy IC");
		form.setApprovedDateTime(LocalDateTime.parse("2025-01-01T12:00"));

		assertEquals(ObjectiveApprovalStatus.LEGACY_UNVERIFIED, form.getObjectiveApprovalStatus());
	}

	@Test
	void approvalAndPlanningStageRoundTrip() throws Exception {
		AppData data = new AppData();
		data.setIapPlanningStage(IapPlanningStage.PLANNING_MEETING);
		data.getForm202().setStructuredObjectives(List.of(new IncidentObjective("Search", 0)));
		data.getForm202().recordObjectiveApproval("IC", "Signed", LocalDateTime.parse("2026-10-25T04:15"));
		ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

		AppData restored = mapper.readValue(mapper.writeValueAsBytes(data), AppData.class);

		assertEquals(IapPlanningStage.PLANNING_MEETING, restored.getIapPlanningStage());
		assertEquals(ObjectiveApprovalStatus.APPROVED_CURRENT,
				restored.getForm202().getObjectiveApprovalStatus());
	}

	@Test
	void periodAdvanceArchivesStageAndResetsCurrentCycle() {
		AppData data = new AppData();
		data.setIapPlanningStage(IapPlanningStage.OPERATIONS_BRIEFING);

		data.advanceToNewOperationalPeriod();

		assertEquals(IapPlanningStage.OPERATIONS_BRIEFING,
				data.getOperationalPeriodHistory().get(0).getIapPlanningStage());
		assertEquals(IapPlanningStage.NOT_RECORDED, data.getIapPlanningStage());
	}
}
