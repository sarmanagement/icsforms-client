/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

/** Verifies explicit review boundaries, persistence, and fresh-period behavior. */
class PlanReviewTest {
	@Test
	void substantiveChangeStalesReviewButGeneratedPageDoesNot() {
		AppData data = new AppData();
		data.getForm205().setSpecialInstructions("Use command channel");
		data.recordPlanReview(PlanReviewArea.COMMUNICATIONS, "COML", LocalDateTime.parse("2026-10-25T03:30"));
		assertEquals(PlanReviewStatus.REVIEWED_APPROVED,
				data.getPlanReviewStatus(PlanReviewArea.COMMUNICATIONS));

		data.getForm205().setIapPage("27");
		assertEquals(PlanReviewStatus.REVIEWED_APPROVED,
				data.getPlanReviewStatus(PlanReviewArea.COMMUNICATIONS));

		data.getForm205().setSpecialInstructions("Use command and tactical channels");
		assertEquals(PlanReviewStatus.CHANGED_SINCE_REVIEW,
				data.getPlanReviewStatus(PlanReviewArea.COMMUNICATIONS));
		assertEquals("COML", data.getCommunicationsReview().getReviewedBy());
	}

	@Test
	void sharedMedicalRoleChangeStalesBothMedicalReviews() {
		AppData data = new AppData();
		data.getMedicalPlan().setCall911Procedure("Call 911");
		data.getCanineMedicalPlan().setEmergencyProcedures("Call veterinary hospital");
		data.recordPlanReview(PlanReviewArea.HUMAN_MEDICAL, "Reviewer", null);
		data.recordPlanReview(PlanReviewArea.CANINE_MEDICAL, "Reviewer", null);

		data.getMedicalUnitLeaderAssignment().setName("New Medical Unit Leader");

		assertEquals(PlanReviewStatus.CHANGED_SINCE_REVIEW,
				data.getPlanReviewStatus(PlanReviewArea.HUMAN_MEDICAL));
		assertEquals(PlanReviewStatus.CHANGED_SINCE_REVIEW,
				data.getPlanReviewStatus(PlanReviewArea.CANINE_MEDICAL));
	}

	@Test
	void assertionsRoundTripAndLegacyFieldsDoNotCreateAssertion() throws Exception {
		AppData data = new AppData();
		data.getMedicalPlan().setReviewedBySafetyOfficer("Legacy reviewer");
		data.recordPlanReview(PlanReviewArea.HUMAN_MEDICAL, "Current reviewer", null);
		ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

		AppData restored = mapper.readValue(mapper.writeValueAsBytes(data), AppData.class);
		AppData legacy = mapper.readValue("{\"medicalPlan\":{\"reviewedApprovedBy\":\"Legacy reviewer\"}}",
				AppData.class);

		assertEquals("Current reviewer", restored.getHumanMedicalReview().getReviewedBy());
		assertEquals(PlanReviewStatus.REVIEWED_APPROVED,
				restored.getPlanReviewStatus(PlanReviewArea.HUMAN_MEDICAL));
		assertEquals(PlanReviewStatus.NOT_YET_REVIEWED,
				legacy.getPlanReviewStatus(PlanReviewArea.HUMAN_MEDICAL));
	}

	@Test
	void rolloverArchivesIndependentAssertionsAndRequiresFreshReview() {
		AppData data = new AppData();
		data.getMedicalPlan().setCall911Procedure("Call 911");
		data.recordPlanReview(PlanReviewArea.HUMAN_MEDICAL, "MUL", null);
		PlanReviewAssertion current = data.getHumanMedicalReview();

		data.advanceToNewOperationalPeriod();

		PlanReviewAssertion archived = data.getOperationalPeriodHistory().get(0).getHumanMedicalReview();
		assertNotSame(current, archived);
		assertEquals("MUL", archived.getReviewedBy());
		assertEquals(PlanReviewStatus.NOT_YET_REVIEWED,
				data.getPlanReviewStatus(PlanReviewArea.HUMAN_MEDICAL));
	}
}
