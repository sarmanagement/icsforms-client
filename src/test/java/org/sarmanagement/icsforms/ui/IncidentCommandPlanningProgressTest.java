/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.IncidentObjective;
import org.sarmanagement.icsforms.model.PlanReviewArea;
import org.sarmanagement.icsforms.model.PlanReviewSupport;
import org.sarmanagement.icsforms.model.ResourceAssignment;
import org.sarmanagement.icsforms.model.SarTaskAssignment;
import org.sarmanagement.icsforms.model.TCard;
import org.sarmanagement.icsforms.model.TCardType;
import org.sarmanagement.icsforms.persistence.LocalRepository;
import org.sarmanagement.icsforms.pdf.PdfExportService;
import org.sarmanagement.icsforms.validation.IncidentValidator;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies planning colors, independent checkmarks, and live recorded facts. */
class IncidentCommandPlanningProgressTest {
	@TempDir
	Path directory;

	@Test
	void populatedRequiredPlansNeedCurrentExplicitApprovalsNotMetadata() throws Exception {
		AppData data = populated();
		data.getForm202().setApprovedByIncidentCommanderName("Legacy commander");
		data.getForm202().setApprovedBySignature("Legacy signature");
		AppController controller = controller(data);
		IncidentCommandRolePanel panel = panel(controller);
		try {
			assertState(panel, "YELLOW");
			assertTrue(badge(panel, "Objectives").contains("✓ 1 recorded"));
			assertTrue(badge(panel, "Objectives").contains("○ Legacy approval"));
			assertTrue(badge(panel, "Safety").contains("✓ 1 content items recorded"));
			assertTrue(badge(panel, "Safety").contains("○ Not yet reviewed"));
			assertTrue(badge(panel, "Canine medical").contains("optional"));
			approve(data);
			refresh(controller);
			assertState(panel, "GREEN");
			assertTrue(badge(panel, "Objectives").contains("✓ Approved — current"));
			assertTrue(badge(panel, "Safety").contains("✓ Reviewed-approved — current"));
			assertTrue(overall(panel).getText().contains("not plan sufficiency or operational readiness"));
			assertTrue(overall(panel).getToolTipText().contains("not a validated complete plan"));
			assertTrue(overall(panel).getToolTipText().contains("green: required content present with current approvals"));
		} finally {
			SwingUtilities.invokeAndWait(panel::dispose);
		}
	}

	@Test
	void anyMissingRequiredPlanOverridesOtherApprovals() throws Exception {
		for (String missing : List.of("Objectives", "Assignment coverage", "Safety", "Human medical", "Communications")) {
			AppData data = populated();
			approve(data);
			switch (missing) {
				case "Objectives" -> data.getForm202().setStructuredObjectives(List.of());
				case "Assignment coverage" -> data.getForm204().getResourcesAssigned().clear();
				case "Safety" -> data.getForm208().setGeneralRequirements("");
				case "Human medical" -> data.getMedicalPlan().setEmergencyProcedures("");
				case "Communications" -> data.getForm205().setSpecialInstructions("");
				default -> throw new AssertionError(missing);
			}
			IncidentCommandRolePanel panel = panel(controller(data));
			try {
				assertState(panel, "RED");
				assertTrue(badge(panel, missing).contains("○"));
			} finally {
				SwingUtilities.invokeAndWait(panel::dispose);
			}
		}
	}

	@Test
	void optionalAnimalPlanDoesNotGateUntilAnimalResourcesAreRecorded() throws Exception {
		AppData data = populated();
		AppController controller = controller(data);
		approve(data);
		IncidentCommandRolePanel panel = panel(controller);
		try {
			assertState(panel, "GREEN");
			TCard canine = new TCard();
			canine.setCardType(TCardType.EQUIPMENT);
			canine.setCanineResource(true);
			data.getTCards().add(canine);
			refresh(controller);
			assertState(panel, "RED");
			assertTrue(badge(panel, "Canine medical").contains("required"));
			data.getCanineMedicalPlan().setEmergencyProcedures("Contact veterinarian");
			refresh(controller);
			assertState(panel, "YELLOW");
			review(data, PlanReviewArea.CANINE_MEDICAL);
			refresh(controller);
			assertState(panel, "GREEN");
			data.getCanineMedicalPlan().setEmergencyProcedures("Changed veterinary procedure");
			refresh(controller);
			assertState(panel, "YELLOW");
			assertTrue(badge(panel, "Canine medical").contains("○ Stale"));
			data.getTCards().clear();
			refresh(controller);
			assertState(panel, "GREEN");
		} finally {
			SwingUtilities.invokeAndWait(panel::dispose);
		}
	}

	@Test
	void currentApprovalBecomesYellowAfterObjectiveOrPlanContentChanges() throws Exception {
		AppData data = populated();
		AppController controller = controller(data);
		approve(data);
		IncidentCommandRolePanel panel = panel(controller);
		try {
			assertState(panel, "GREEN");
			data.getForm202().getStructuredObjectives().get(0).setText("Changed objective");
			refresh(controller);
			assertState(panel, "YELLOW");
			assertTrue(badge(panel, "Objectives").contains("○ Approval stale"));
			approve(data);
			data.getForm205().setSpecialInstructions("Changed command frequency");
			refresh(controller);
			assertState(panel, "YELLOW");
			assertTrue(badge(panel, "Communications").contains("○ Stale"));
		} finally {
			SwingUtilities.invokeAndWait(panel::dispose);
		}
	}

	@Test
	void partialAssignmentCoverageIsNotCheckedAndDoesNotCertifyPlanApproval() throws Exception {
		AppData data = populated();
		data.getForm204().getResourcesAssigned().clear();
		ResourceAssignment linked = new ResourceAssignment();
		linked.setResourceIdentifier("Ground Team 1");
		linked.setObjectiveIds(List.of(data.getForm202().getStructuredObjectives().get(0).getObjectiveId()));
		data.getForm204().getResourcesAssigned().addAll(List.of(linked, new ResourceAssignment()));
		AppController controller = controller(data);
		IncidentCommandRolePanel panel = panel(controller);
		try {
			assertTrue(badge(panel, "Assignment coverage").contains("○ 1/2"));
			assertState(panel, "YELLOW");
			data.getForm204().getResourcesAssigned().remove(1);
			refresh(controller);
			assertTrue(badge(panel, "Assignment coverage").contains("✓ 1/1"));
			assertState(panel, "YELLOW");
		} finally {
			SwingUtilities.invokeAndWait(panel::dispose);
		}
	}

	@Test
	void assignmentLinksAndPlaceholderRowsDoNotSatisfyRequiredAssignmentContent() throws Exception {
		AppData data = populated();
		ResourceAssignment assignment = data.getForm204().getResourcesAssigned().get(0);
		assignment.setResourceIdentifier("");
		assignment.setObjectiveIds(List.of(data.getForm202().getStructuredObjectives().get(0).getObjectiveId()));
		AppController controller = controller(data);
		approve(data);
		IncidentCommandRolePanel panel = panel(controller);
		try {
			assertState(panel, "RED");
			assertTrue(badge(panel, "Assignment coverage").contains("○ 0 populated assignment rows"));
			assertTrue(badge(panel, "Assignment coverage").contains("✓ 1/1"));
			assignment.setAssignment("Search north sector");
			refresh(controller);
			assertState(panel, "GREEN");
			assertTrue(badge(panel, "Assignment coverage").contains("✓ 1 populated assignment rows"));
			assertTrue(overall(panel).getToolTipText().contains("No separate assignment approval is inferred"));
		} finally {
			SwingUtilities.invokeAndWait(panel::dispose);
		}
	}

	@Test
	void resourceBreakdownAndObjectiveLifecycleAreLiveAndHtmlEscaped() throws Exception {
		AppData data = populated();
		IncidentObjective objective = data.getForm202().getStructuredObjectives().get(0);
		objective.setText("Search <north> & east");
		ResourceAssignment assignment = new ResourceAssignment();
		assignment.setObjectiveIds(List.of(objective.getObjectiveId()));
		data.getForm204().getResourcesAssigned().add(assignment);
		SarTaskAssignment task = new SarTaskAssignment();
		task.setAssignmentId(assignment.getAssignmentId());
		task.setTaskLifecycleStatus("assigned - on task");
		data.getSarTaskAssignments().add(task);
		TCard canine = new TCard();
		canine.setCardType(TCardType.EQUIPMENT);
		canine.setCanineResource(true);
		canine.setStatus("Available");
		TCard equipment = new TCard();
		equipment.setCardType(TCardType.EQUIPMENT);
		equipment.setStatus("Available");
		data.getTCards().addAll(List.of(canine, equipment));
		AppController controller = controller(data);
		IncidentCommandRolePanel panel = panel(controller);
		try {
			JTable objectives = (JTable) field(panel, "objectives");
			assertTrue(objectives.getValueAt(0, 0).toString().contains("&lt;north&gt; &amp; east"));
			assertTrue(objectives.getValueAt(0, 0).toString().contains("assigned - on task: 1"));
			JComboBox<?> grouping = (JComboBox<?>) field(panel, "resourceGrouping");
			SwingUtilities.invokeAndWait(() -> grouping.setSelectedIndex(1));
			JTable resources = (JTable) field(panel, "resources");
			assertEquals(2, resources.getRowCount());
			assertEquals("219-7 Equipment — Canine", resources.getValueAt(0, 0));
			assertEquals("219-7 Equipment", resources.getValueAt(1, 0));
			assertEquals(2, panel.displayedResourceCount("Available"));
			task.setTaskLifecycleStatus("returned");
			refresh(controller);
			assertTrue(objectives.getValueAt(0, 0).toString().contains("returned: 1"));
			assertFalse(objectives.getValueAt(0, 0).toString().contains("assigned - on task"));
		} finally {
			SwingUtilities.invokeAndWait(panel::dispose);
		}
	}

	/**
	 * Builds required populated plans without approvals or animal resources.
	 *
	 * @return incident fixture.
	 */
	private AppData populated() {
		AppData data = new AppData();
		data.getForm202().setStructuredObjectives(List.of(new IncidentObjective("Search north", 0)));
		data.getForm208().setGeneralRequirements("Wear helmets");
		data.getMedicalPlan().setEmergencyProcedures("Call incident medical");
		data.getForm205().setSpecialInstructions("Monitor command");
		ResourceAssignment assignment = new ResourceAssignment();
		assignment.setResourceIdentifier("Ground Team 1");
		data.getForm204().getResourcesAssigned().add(assignment);
		return data;
	}

	/**
	 * Approves current objectives and required plans using content-bound assertions.
	 *
	 * @param data incident fixture.
	 */
	private void approve(AppData data) {
		data.getForm202().recordObjectiveApproval("Commander", "Signature", LocalDateTime.of(2026, 1, 1, 12, 0));
		for (PlanReviewArea area : List.of(PlanReviewArea.SAFETY, PlanReviewArea.HUMAN_MEDICAL,
				PlanReviewArea.COMMUNICATIONS)) {
			review(data, area);
		}
	}

	/**
	 * Records a review of the exact current content.
	 *
	 * @param data incident fixture.
	 * @param area reviewed area.
	 */
	private void review(AppData data, PlanReviewArea area) {
		data.getReviewAssertion(area).setReviewedBy("Reviewer");
		data.getReviewAssertion(area).setReviewedAt(LocalDateTime.of(2026, 1, 1, 12, 0));
		data.getReviewAssertion(area).setReviewedContentDigest(PlanReviewSupport.contentDigest(data, area));
	}

	/**
	 * Creates a test-local incident controller.
	 *
	 * @param data incident fixture.
	 * @return controller.
	 */
	private AppController controller(AppData data) {
		return new AppController(data, new LocalRepository(directory.resolve("incident.json")), new PdfExportService(),
				new IncidentValidator());
	}

	/**
	 * Creates the Swing panel on the event dispatch thread.
	 *
	 * @param controller incident controller.
	 * @return panel.
	 * @throws Exception if Swing dispatch fails.
	 */
	private IncidentCommandRolePanel panel(AppController controller) throws Exception {
		IncidentCommandRolePanel[] result = new IncidentCommandRolePanel[1];
		SwingUtilities.invokeAndWait(() -> result[0] = new IncidentCommandRolePanel(controller, ignored -> {
		}));
		return result[0];
	}

	/**
	 * Delivers a live incident change and drains the Swing event queue.
	 *
	 * @param controller incident controller.
	 * @throws Exception if Swing dispatch fails.
	 */
	private void refresh(AppController controller) throws Exception {
		controller.markDirty();
		SwingUtilities.invokeAndWait(() -> {
		});
	}

	/**
	 * Asserts a textual color state for accessibility as well as visual display.
	 *
	 * @param panel command panel.
	 * @param state expected textual color.
	 * @throws Exception if reflection fails.
	 */
	private void assertState(IncidentCommandRolePanel panel, String state) throws Exception {
		assertTrue(overall(panel).getAccessibleContext().getAccessibleName()
				.startsWith("Overall planning status: " + state), overall(panel).getText());
	}

	/**
	 * Reads the overall planning status label.
	 *
	 * @param panel command panel.
	 * @return overall status label.
	 * @throws Exception if reflection fails.
	 */
	private JLabel overall(IncidentCommandRolePanel panel) throws Exception {
		return (JLabel) field(panel, "overallProgress");
	}

	/**
	 * Reads a named badge's displayed text.
	 *
	 * @param panel command panel.
	 * @param title badge title.
	 * @return displayed badge text.
	 * @throws Exception if reflection fails.
	 */
	private String badge(IncidentCommandRolePanel panel, String title) throws Exception {
		JPanel badges = (JPanel) field(panel, "planProgress");
		for (java.awt.Component component : badges.getComponents()) {
			JLabel badge = (JLabel) component;
			if (badge.getAccessibleContext().getAccessibleName().startsWith(title + ":")) {
				return badge.getText();
			}
		}
		throw new AssertionError(title);
	}

	/**
	 * Reads a panel field for observable UI assertions.
	 *
	 * @param panel command panel.
	 * @param name field name.
	 * @return field value.
	 * @throws ReflectiveOperationException if reflection fails.
	 */
	private Object field(IncidentCommandRolePanel panel, String name) throws ReflectiveOperationException {
		Field field = IncidentCommandRolePanel.class.getDeclaredField(name);
		field.setAccessible(true);
		return field.get(panel);
	}
}
