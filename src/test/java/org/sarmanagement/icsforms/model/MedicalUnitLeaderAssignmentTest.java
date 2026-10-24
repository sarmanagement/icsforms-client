/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies migration and preservation of the shared Medical Unit Leader assignment. */
class MedicalUnitLeaderAssignmentTest {
	@Test
	void legacyCopiesUseDocumentedPrecedenceAndConflictsRemainAvailable() {
		AppData data = new AppData();
		OrganizationalChart chart = data.getOrganizationalChart();
		chart.setMedicalUnitLeaderName("Chart Leader");
		chart.setMedicalUnitLeaderRadio("Command 2");
		chart.setMedicalUnitLeaderPhone("555-0101");
		chart.getAssignmentList().setPosition(Ics203Form.Position.MEDICAL_UNIT_LEADER, "ICS 203 Leader");
		data.getMedicalPlan().setPreparedByMedicalUnitLeader("Human Plan Leader");
		data.getCanineMedicalPlan().setPreparedByMedicalUnitLeader("Canine Plan Leader");

		MedicalUnitLeaderAssignment assignment = data.getMedicalUnitLeaderAssignment();

		assertEquals("Chart Leader", assignment.getName());
		assertEquals("Command 2", assignment.getRadioChannel());
		assertEquals("555-0101", assignment.getPhoneNumber());
		assertTrue(assignment.getPreservedLegacyConflicts().containsAll(List.of(
				"organizationalChart.assignmentList.medicalUnitLeader: ICS 203 Leader",
				"medicalPlan.preparedByMedicalUnitLeader: Human Plan Leader",
				"canineMedicalPlan.preparedByMedicalUnitLeader: Canine Plan Leader")));
		assertEquals("Human Plan Leader", data.getMedicalPlan().getPreparedByMedicalUnitLeader());
		assertEquals("Canine Plan Leader", data.getCanineMedicalPlan().getPreparedByMedicalUnitLeader());
	}

	@Test
	void supplementalIcs203PrecedesLegacyMedicalPlanNamesAndDesignatedServiceIsIndependent() {
		AppData data = new AppData();
		data.getOrganizationalChart().getAssignmentList()
				.setPosition(Ics203Form.Position.MEDICAL_UNIT_LEADER, "ICS 203 Leader");
		data.getMedicalPlan().setPreparedByMedicalUnitLeader("Human Plan Leader");
		data.getCanineMedicalPlan().setPreparedByMedicalUnitLeader("Canine Plan Leader");
		data.getMedicalPlan().setDesignatedMedicalUnitName("Medical Service Contact");

		assertEquals("ICS 203 Leader", data.getMedicalUnitLeaderAssignment().getName());

		AppData noRoleAssignment = new AppData();
		noRoleAssignment.getMedicalPlan().setDesignatedMedicalUnitName("Medical Service Contact");
		assertEquals("", noRoleAssignment.getMedicalUnitLeaderAssignment().getName());
	}
}
