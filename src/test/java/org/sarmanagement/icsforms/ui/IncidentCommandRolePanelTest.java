/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.IapPlanningStage;
import org.sarmanagement.icsforms.model.IncidentObjective;
import org.sarmanagement.icsforms.model.ObjectiveAssessment;
import org.sarmanagement.icsforms.model.MedicalPlan;
import org.sarmanagement.icsforms.model.PlanReviewArea;
import org.sarmanagement.icsforms.model.PlanReviewSupport;
import org.sarmanagement.icsforms.model.RadioChannel;
import org.sarmanagement.icsforms.model.ResourceAssignment;
import org.sarmanagement.icsforms.model.SarTaskAssignment;
import org.sarmanagement.icsforms.model.SafetyAnalysisEntry;
import org.sarmanagement.icsforms.model.SafetyMessageBlock;
import org.sarmanagement.icsforms.model.TCard;
import org.sarmanagement.icsforms.model.TCardType;
import org.sarmanagement.icsforms.persistence.LocalRepository;
import org.sarmanagement.icsforms.pdf.PdfExportService;
import org.sarmanagement.icsforms.validation.IncidentValidator;

import javax.swing.JComboBox;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JFrame;
import javax.swing.JDialog;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JSplitPane;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.JTextField;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import java.awt.Container;
import java.awt.BorderLayout;
import java.awt.GraphicsEnvironment;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** Verifies Incident Command facts use explicit persisted state only. */
class IncidentCommandRolePanelTest {
	@TempDir
	Path directory;

	@Test
	void resourceOverviewDoesNotInferAvailabilityFromPresence() throws Exception {
		AppData data = new AppData();
		TCard unspecified = new TCard();
		unspecified.setPersonName("Unspecified Responder");
		TCard available = new TCard();
		available.setPersonName("Available Responder");
		available.setStatus("Available");
		data.getTCards().addAll(List.of(unspecified, available));
		IncidentCommandRolePanel[] panel = new IncidentCommandRolePanel[1];

		SwingUtilities.invokeAndWait(() -> panel[0] = new IncidentCommandRolePanel(controller(data), ignored -> {
		}));

		assertEquals(1, panel[0].displayedResourceCount("Unspecified"));
		assertEquals(1, panel[0].displayedResourceCount("Available"));
		SwingUtilities.invokeAndWait(panel[0]::dispose);
	}

	@Test
	void navigationActionsOccupyHeaderRightSide() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			IncidentCommandRolePanel panel = new IncidentCommandRolePanel(controller(new AppData()), ignored -> {
			});
			try {
				java.lang.reflect.Method method = IncidentCommandRolePanel.class.getDeclaredMethod("buildHeader");
				method.setAccessible(true);
				JPanel header = (JPanel) method.invoke(panel);
				BorderLayout layout = (BorderLayout) header.getLayout();

				assertTrue(layout.getLayoutComponent(BorderLayout.EAST) instanceof JPanel);
				assertTrue(layout.getLayoutComponent(BorderLayout.CENTER) instanceof JLabel);
				assertNull(layout.getLayoutComponent(BorderLayout.SOUTH));
			} catch (ReflectiveOperationException exception) {
				throw new IllegalStateException(exception);
			} finally {
				panel.dispose();
			}
		});
	}

	@Test
	void liveRefreshShowsExplicitPlanningStageWithoutDerivingItFromContent() throws Exception {
		AppData data = new AppData();
		data.getForm202().setStructuredObjectives(List.of(new IncidentObjective("Search", 0)));
		AppController controller = controller(data);
		IncidentCommandRolePanel[] panel = new IncidentCommandRolePanel[1];
		SwingUtilities.invokeAndWait(() -> panel[0] = new IncidentCommandRolePanel(controller, ignored -> {
		}));

		assertEquals(IapPlanningStage.NOT_RECORDED, planningStage(panel[0]).getSelectedItem());
		controller.setIapPlanningStage(IapPlanningStage.PLANNING_MEETING);
		SwingUtilities.invokeAndWait(() -> {
		});

		assertEquals(IapPlanningStage.PLANNING_MEETING, planningStage(panel[0]).getSelectedItem());
		SwingUtilities.invokeAndWait(panel[0]::dispose);
	}

	@Test
	void incidentCommandActionsAreGroupedWithPlanningAndContextAboveResources() throws Exception {
		IncidentCommandRolePanel panel = panel(controller(new AppData()));
		try {
			JComboBox<?> stage = planningStage(panel);
			JButton addObjective = button(panel, "Add objective");
			JButton editObjective = button(panel, "Edit objective");
			JButton linkedWork = button(panel, "Linked work");
			JPanel stageRow = (JPanel) stage.getParent();
			assertTrue(stageRow.getComponents().length < 4);
			assertTrue(java.util.Arrays.stream(stageRow.getComponents())
					.filter(JLabel.class::isInstance).map(JLabel.class::cast)
					.anyMatch(label -> "Planning stage".equals(label.getText())));
			assertSame(addObjective.getParent(), editObjective.getParent());
			assertSame(addObjective.getParent(), linkedWork.getParent());
			JPanel objectiveActions = (JPanel) addObjective.getParent();
			JPanel objectiveTablePanel = (JPanel) objectiveActions.getParent();
			assertSame(objectiveActions,
					((BorderLayout) objectiveTablePanel.getLayout()).getLayoutComponent(BorderLayout.NORTH));
			assertFalse(addObjective.getText().endsWith("…"));
			assertFalse(editObjective.getText().endsWith("…"));
			assertFalse(linkedWork.getText().endsWith("…"));
			assertFalse(editObjective.isEnabled());
			assertFalse(linkedWork.isEnabled());
			JPanel approval = (JPanel) ((BorderLayout) ((JPanel) stageRow.getParent()).getLayout())
					.getLayoutComponent(BorderLayout.SOUTH);
			assertTrue(approval.getLayout() instanceof java.awt.GridBagLayout);
			assertEquals(2, java.util.Arrays.stream(approval.getComponents())
					.mapToInt(component -> ((java.awt.GridBagLayout) approval.getLayout())
							.getConstraints(component).gridy).max().orElse(-1) + 1);

			JSplitPane body = findComponent(panel, JSplitPane.class);
			JSplitPane secondColumn = (JSplitPane) body.getRightComponent();
			assertEquals(JSplitPane.VERTICAL_SPLIT, secondColumn.getOrientation());
			assertEquals(0.72, secondColumn.getResizeWeight());
			JPanel context = (JPanel) secondColumn.getTopComponent();
			JPanel resourceOverview = (JPanel) secondColumn.getBottomComponent();
			assertNull(context.getBorder());
			assertTrue(context.isAncestorOf(button(panel, "Edit command emphasis and situational awareness")));
			assertTrue(resourceOverview.isAncestorOf((JTable) field(panel, "resources")));
			JScrollPane resourceScroll = findComponent(resourceOverview, JScrollPane.class);
			JTable resources = (JTable) field(panel, "resources");
			assertEquals(resources.getTableHeader().getPreferredSize().height + 10 * resources.getRowHeight(),
					resourceScroll.getPreferredSize().height);
		} finally {
			SwingUtilities.invokeAndWait(panel::dispose);
		}
	}

	@Test
	void objectivesHighlightMissingAssignmentsAndSelectionEnablesObjectiveActions() throws Exception {
		AppData data = new AppData();
		IncidentObjective noAssignment = new IncidentObjective("Search sector A", 0);
		IncidentObjective withAssignment = new IncidentObjective("Search sector B", 1);
		data.getForm202().setStructuredObjectives(List.of(noAssignment, withAssignment));
		ResourceAssignment linked = new ResourceAssignment();
		linked.setObjectiveIds(List.of(withAssignment.getObjectiveId()));
		data.getForm204().getResourcesAssigned().add(linked);
		data.getForm202().setCommandEmphasis("Prioritize the north ridge search.");
		data.getForm202().setSituationalAwareness("Weather remains clear; visibility is good.");
		IncidentCommandRolePanel panel = panel(controller(data));
		try {
			JTable objectives = (JTable) field(panel, "objectives");
			JTextArea emphasis = (JTextArea) field(panel, "commandEmphasis");
			JTextArea awareness = (JTextArea) field(panel, "situationalAwareness");
			assertTrue(objectives.getValueAt(0, 1).toString().contains("<b>None</b>"));
			assertFalse(objectives.getValueAt(1, 1).toString().contains("<b>None</b>"));
			JLabel objectiveCell = (JLabel) objectives.getCellRenderer(0, 0)
					.getTableCellRendererComponent(objectives, objectives.getValueAt(0, 0), false, false, 0, 0);
			assertEquals(SwingConstants.TOP, objectiveCell.getVerticalAlignment());
			assertFalse(emphasis.isEditable());
			assertFalse(awareness.isEditable());
			assertEquals("Prioritize the north ridge search.", emphasis.getText());
			assertEquals("Weather remains clear; visibility is good.", awareness.getText());

			SwingUtilities.invokeAndWait(() -> objectives.setRowSelectionInterval(0, 0));
			assertEquals(noAssignment.getObjectiveId(),
					data.getForm202().getStructuredObjectives().get(objectives.getSelectedRow()).getObjectiveId());
			assertTrue(button(panel, "Edit objective").isEnabled());
			assertTrue(button(panel, "Linked work").isEnabled());
		} finally {
			SwingUtilities.invokeAndWait(panel::dispose);
		}
	}

	@Test
	void incidentCommandEditsObjectivesAndAddsThemWithoutChangingLinkedIdentity() throws Exception {
		AppData data = new AppData();
		IncidentObjective objective = new IncidentObjective("Search the north ridge", 0);
		data.getForm202().setStructuredObjectives(List.of(objective));
		ResourceAssignment assignment = new ResourceAssignment();
		assignment.setObjectiveIds(List.of(objective.getObjectiveId()));
		data.getForm204().getResourcesAssigned().add(assignment);
		AppController controller = controller(data);
		IncidentCommandRolePanel panel = panel(controller);
		assertTrue(java.util.Arrays.stream(((JTable) field(panel, "objectives")).getComponentPopupMenu().getComponents())
				.filter(JMenuItem.class::isInstance).map(JMenuItem.class::cast)
				.anyMatch(item -> "Edit objective".equals(item.getText())));
		AppController.IncidentBinding binding = controller.captureIncidentBinding();
		SwingUtilities.invokeAndWait(() -> assertTrue(panel.saveObjective(binding, objective.getObjectiveId(),
				"Search north ridge sector A", ObjectiveAssessment.ONGOING, "Team report pending.")));

		assertEquals(objective.getObjectiveId(), data.getForm202().getStructuredObjectives().get(0).getObjectiveId());
		assertEquals("Search north ridge sector A", objective.getText());
		assertEquals(ObjectiveAssessment.ONGOING, objective.getAssessment());
		assertEquals("Team report pending.", objective.getAssessmentNote());
		assertEquals(List.of(objective.getObjectiveId()), assignment.getObjectiveIds());

		SwingUtilities.invokeAndWait(() -> assertTrue(panel.saveObjective(controller.captureIncidentBinding(), "",
				"Establish command post", ObjectiveAssessment.NOT_ASSESSED, "")));
		assertEquals(2, data.getForm202().getStructuredObjectives().size());
		assertEquals("Establish command post", data.getForm202().getStructuredObjectives().get(1).getText());
		assertNull(data.getForm202().getStructuredObjectives().get(1).getAssessedAt());
		SwingUtilities.invokeAndWait(panel::dispose);
	}

	@Test
	void incidentCommandEditsIcs202CommandEmphasisAndSituationalAwareness() throws Exception {
		AppData data = new AppData();
		AppController controller = controller(data);
		IncidentCommandRolePanel panel = panel(controller);
		SwingUtilities.invokeAndWait(() -> assertTrue(panel.saveIcs202Context(controller.captureIncidentBinding(),
				"Protect responder safety", "Weather worsening after 1600.")));

		assertEquals("Protect responder safety", data.getForm202().getCommandEmphasis());
		assertEquals("Weather worsening after 1600.", data.getForm202().getSituationalAwareness());
		SwingUtilities.invokeAndWait(panel::dispose);
	}

	@Test
	void incidentCommandLinksExistingAndCreatesNormalPrelinkedAssignments() throws Exception {
		AppData data = new AppData();
		IncidentObjective objective = new IncidentObjective("Search sector A", 0);
		IncidentObjective otherObjective = new IncidentObjective("Secure command post", 1);
		data.getForm202().setStructuredObjectives(List.of(objective, otherObjective));
		ResourceAssignment existing = new ResourceAssignment();
		existing.setObjectiveIds(List.of(otherObjective.getObjectiveId()));
		data.getForm204().getResourcesAssigned().add(existing);
		AppController controller = controller(data);
		IncidentCommandRolePanel.AssignmentActions actions = new IncidentCommandRolePanel.AssignmentActions() {
			@Override
			public void openIcs204(String assignmentId) {
			}

			@Override
			public void openSarTask(String assignmentId) {
			}

			@Override
			public void addLogEntry(String assignmentId) {
			}

			@Override
			public void changeStatus(String assignmentId) {
			}

			@Override
			public void debrief(String assignmentId) {
			}

			@Override
			public ResourceAssignment createSupportingAssignment(String objectiveId) {
				ResourceAssignment created = new ResourceAssignment();
				created.setAssignmentTeamNumber("T-21");
				created.setResourceIdentifier("Search team");
				created.setAssignment("Search sector A");
				data.getForm204().getResourcesAssigned().add(created);
				return created;
			}
		};
		IncidentCommandRolePanel panel = new IncidentCommandRolePanel(controller, ignored -> {
		}, actions);
		SwingUtilities.invokeAndWait(() -> assertTrue(panel.linkAssignmentsToObjective(
				controller.captureIncidentBinding(), objective.getObjectiveId(),
				List.of(existing.getAssignmentId()))));
		assertEquals(List.of(otherObjective.getObjectiveId(), objective.getObjectiveId()), existing.getObjectiveIds());

		ResourceAssignment[] createdHolder = new ResourceAssignment[1];
		SwingUtilities.invokeAndWait(
				() -> createdHolder[0] = panel.createSupportingAssignment(objective.getObjectiveId()));
		ResourceAssignment created = createdHolder[0];
		assertTrue(created.getObjectiveIds().contains(objective.getObjectiveId()));
		assertEquals("T-21", created.getAssignmentTeamNumber());
		assertEquals(2, controller.getData().getAllAssignmentResources().size());
		assertEquals(2, controller.getData().getAssignmentsForObjective(objective.getObjectiveId()).size());
		assertTrue(controller.getData().getAssignmentsForObjective(objective.getObjectiveId()).contains(existing));
		SwingUtilities.invokeAndWait(panel::dispose);
	}

	@Test
	void incidentCommandRecordsAndDisplaysReportedResultsWithTheirSources() throws Exception {
		AppData data = new AppData();
		IncidentObjective objective = new IncidentObjective("Search sector A", 0);
		data.getForm202().setStructuredObjectives(List.of(objective));
		AppController controller = controller(data);
		IncidentCommandRolePanel panel = panel(controller);
		SwingUtilities.invokeAndWait(() -> assertTrue(panel.recordReportedResult(
				controller.captureIncidentBinding(), objective.getObjectiveId(), "Sector A searched; no find.",
				"Team T-12 radio report")));

		assertEquals(1, data.getForm202().getReportedResults().size());
		assertEquals(objective.getObjectiveId(), data.getForm202().getReportedResults().get(0).getObjectiveId());
		assertEquals(ObjectiveAssessment.NOT_ASSESSED, objective.getAssessment());
		javax.swing.table.DefaultTableModel displayed = panel.resultsModelFor(objective.getObjectiveId());
		assertEquals("Sector A searched; no find.", displayed.getValueAt(0, 0));
		assertEquals("Team T-12 radio report", displayed.getValueAt(0, 1));
		SwingUtilities.invokeAndWait(panel::dispose);
	}

	@Test
	void reportedResultsAndSourcesRoundTripWithTheIcs202Form() throws Exception {
		AppData data = new AppData();
		IncidentObjective objective = new IncidentObjective("Search sector A", 0);
		data.getForm202().setStructuredObjectives(List.of(objective));
		AppController controller = controller(data);
		IncidentCommandRolePanel panel = panel(controller);
		SwingUtilities.invokeAndWait(() -> assertTrue(panel.recordReportedResult(
				controller.captureIncidentBinding(), objective.getObjectiveId(), "Sector searched.",
				"Team radio report")));

		ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
		var saved = mapper.readValue(mapper.writeValueAsBytes(data.getForm202()),
				org.sarmanagement.icsforms.model.Ics202Form.class);
		assertEquals(1, saved.getReportedResults().size());
		assertEquals("Sector searched.", saved.getReportedResults().get(0).getResult());
		assertEquals("Team radio report", saved.getReportedResults().get(0).getSource());
		assertEquals(objective.getObjectiveId(), saved.getReportedResults().get(0).getObjectiveId());
		SwingUtilities.invokeAndWait(panel::dispose);
	}

	@Test
	void unrelatedLiveChangePreservesApprovalDraft() throws Exception {
		AppController controller = controller(new AppData());
		IncidentCommandRolePanel[] panel = new IncidentCommandRolePanel[1];
		SwingUtilities.invokeAndWait(() -> {
			panel[0] = new IncidentCommandRolePanel(controller, ignored -> {
			});
			try {
				textField(panel[0], "approver").setText("");
				textField(panel[0], "signature").setText("Draft signature");
			} catch (ReflectiveOperationException exception) {
				throw new IllegalStateException(exception);
			}
		});

		controller.getData().getIncidentContext().setIncidentName("Unrelated update");
		controller.markDirty();
		SwingUtilities.invokeAndWait(() -> {
		});

		assertEquals("", textField(panel[0], "approver").getText());
		assertEquals("Draft signature", textField(panel[0], "signature").getText());
		SwingUtilities.invokeAndWait(panel[0]::dispose);
	}

	@Test
	void resourceTypeBreakdownDeduplicatesStableIdentitiesAndExcludesHeaders() throws Exception {
		AppData data = new AppData();
		TCard person = card("person", TCardType.PERSONNEL, "Available");
		TCard duplicate = card("person", TCardType.EQUIPMENT, "Assigned");
		TCard sameName = card("second-person", TCardType.PERSONNEL, "Assigned");
		person.setPersonName("Same name");
		sameName.setPersonName("Same name");
		data.getTCards().addAll(List.of(person, duplicate, sameName,
				card("equipment", TCardType.EQUIPMENT, " "),
				card("header", TCardType.HEADER, "Available")));
		AppController controller = controller(data);
		IncidentCommandRolePanel panel = panel(controller);
		JTable table = (JTable) field(panel, "resources");
		JComboBox<?> grouping = (JComboBox<?>) field(panel, "resourceGrouping");
		assertEquals(1, panel.displayedResourceCount("Available"));
		assertEquals(1, panel.displayedResourceCount("Assigned"));
		assertEquals(1, panel.displayedResourceCount("Unspecified"));
		assertEquals(3, table.getValueAt(0, table.getColumnCount() - 1));

		SwingUtilities.invokeAndWait(() -> grouping.setSelectedIndex(1));
		assertEquals(2, table.getRowCount());
		assertEquals(TCardType.PERSONNEL.getLabel(), table.getValueAt(0, 0));
		assertEquals(2, table.getValueAt(0, table.getColumnCount() - 1));
		assertEquals(TCardType.EQUIPMENT.getLabel(), table.getValueAt(1, 0));
		assertEquals(1, table.getValueAt(1, table.getColumnCount() - 1));
		assertEquals(1, panel.displayedResourceCount("Available"));
		assertEquals(1, panel.displayedResourceCount("Assigned"));

		person.setStatus("Out of service");
		controller.markDirty();
		SwingUtilities.invokeAndWait(() -> {
		});
		assertEquals(1, grouping.getSelectedIndex());
		assertEquals(0, panel.displayedResourceCount("Available"));
		assertEquals(1, panel.displayedResourceCount("Out of service"));
		assertEquals(1, panel.displayedResourceCount("Unspecified"));
		SwingUtilities.invokeAndWait(panel::dispose);
	}

	@Test
	void emptyDefaultsAndPlaceholderRowsDoNotCountAsRecordedPlanContent() throws Exception {
		AppData data = new AppData();
		data.getForm215a().getEntries().add(new SafetyAnalysisEntry());
		data.getForm215a().getEntries().add(null);
		data.getForm208().getHazardBlocks().add(new SafetyMessageBlock());
		data.getForm208().getHazardBlocks().add(null);
		data.getMedicalPlan().getAidStations().add(new MedicalPlan.AidStation());
		data.getMedicalPlan().getAidStations().add(null);
		data.getMedicalPlan().getHospitals().add(new MedicalPlan.Hospital());
		data.getMedicalPlan().getHospitals().add(null);
		data.getMedicalPlan().getTransportServices().add(new MedicalPlan.TransportService());
		data.getMedicalPlan().getTransportServices().add(null);
		data.getCanineMedicalPlan().getTransportServiceEntries().add(new MedicalPlan.TransportService());
		data.getCanineMedicalPlan().getTransportServiceEntries().add(null);
		data.getCanineMedicalPlan().getSelectedFacilities().add(null);
		data.getForm205().getChannels().add(new RadioChannel());
		data.getForm205().setPreparedByName("Preparer only");
		data.getForm205().setIapPage("12");
		IncidentCommandRolePanel panel = panel(controller(data));
		for (String area : List.of("Safety", "Human medical", "Canine medical", "Communications")) {
			String badge = badge(panel, area).getText();
			assertTrue(badge.contains("No content recorded"), badge);
			assertTrue(badge.contains("Not yet reviewed"), badge);
			assertFalse(badge.contains("%"), badge);
		}
		assertTrue(badge(panel, "Objectives").getText().contains("0 recorded"));
		assertTrue(badge(panel, "Objectives").getText().contains("Not approved"));
		SwingUtilities.invokeAndWait(panel::dispose);
	}

	@Test
	void badgesCountEnteredContentAndIndependentlyShowCurrentAndStaleReview() throws Exception {
		AppData data = new AppData();
		SafetyAnalysisEntry hazard = new SafetyAnalysisEntry();
		hazard.setHazardsRisks("Steep terrain");
		hazard.setMitigations("Use ropes");
		data.getForm215a().getEntries().add(hazard);
		data.getForm208().setGeneralRequirements("Wear helmets");
		data.getMedicalPlan().setEmergencyProcedures("Contact incident medical");
		data.getCanineMedicalPlan().setPoisonControlContact("Animal poison control");
		RadioChannel channel = new RadioChannel();
		channel.setChannelName("Command");
		channel.setRxFrequency("155.000");
		data.getForm205().getChannels().add(channel);
		data.getForm205().setSpecialInstructions("Monitor command");
		for (PlanReviewArea area : PlanReviewArea.values()) {
			data.getReviewAssertion(area).setReviewedBy("Reviewer");
			data.getReviewAssertion(area).setReviewedAt(LocalDateTime.of(2026, 1, 1, 12, 0));
			data.getReviewAssertion(area).setReviewedContentDigest(PlanReviewSupport.contentDigest(data, area));
		}
		AppController controller = controller(data);
		IncidentCommandRolePanel panel = panel(controller);
		assertTrue(badge(panel, "Safety").getText().contains("2 content items recorded"));
		assertTrue(badge(panel, "Human medical").getText().contains("1 content items recorded"));
		assertTrue(badge(panel, "Canine medical").getText().contains("1 content items recorded"));
		assertTrue(badge(panel, "Communications").getText().contains("2 content items recorded"));
		assertTrue(badge(panel, "Safety").getText().contains("Reviewed-approved — current"));

		hazard.setHazardsRisks("Changed terrain");
		controller.markDirty();
		SwingUtilities.invokeAndWait(() -> {
		});
		assertTrue(badge(panel, "Safety").getText().contains("Stale — changed since review"));
		assertTrue(badge(panel, "Human medical").getText().contains("Reviewed-approved — current"));
		assertTrue(badge(panel, "Communications").getText().contains("Reviewed-approved — current"));
		SwingUtilities.invokeAndWait(panel::dispose);
	}

	@Test
	void structuredMedicalRowsAndExplicitCommunicationsSettingsCountWithoutDefaultInflation() throws Exception {
		AppData data = new AppData();
		MedicalPlan.AidStation aid = new MedicalPlan.AidStation();
		aid.setParamedicsOnSite(false);
		MedicalPlan.TransportService transport = new MedicalPlan.TransportService();
		transport.setService("Ambulance");
		MedicalPlan.Hospital hospital = new MedicalPlan.Hospital();
		hospital.setName("Regional hospital");
		data.getMedicalPlan().getAidStations().add(aid);
		data.getMedicalPlan().getTransportServices().add(transport);
		data.getMedicalPlan().getHospitals().add(hospital);
		data.getMedicalPlan().setCall911Procedure("Contact incident dispatch before calling 911");
		data.getCanineMedicalPlan().getTransportServiceEntries().add(transport);
		data.getCanineMedicalPlan().getVeterinaryPersonnelResourceIds().addAll(List.of("vet-1", "vet-1", " "));
		RadioChannel receiveOnly = new RadioChannel();
		receiveOnly.setTxPolicy(RadioChannel.TX_POLICY_RECEIVE_ONLY);
		RadioChannel programmed = new RadioChannel();
		programmed.getProgrammingMetadata().put("Imported setting", "Recorded value");
		data.getForm205().getChannels().addAll(List.of(receiveOnly, programmed, new RadioChannel()));
		IncidentCommandRolePanel panel = panel(controller(data));
		assertTrue(badge(panel, "Human medical").getText().contains("4 content items recorded"));
		assertTrue(badge(panel, "Canine medical").getText().contains("2 content items recorded"));
		assertTrue(badge(panel, "Communications").getText().contains("2 content items recorded"));
		SwingUtilities.invokeAndWait(panel::dispose);
	}

	@Test
	void assignmentCoverageRequiresLinksToExistingRecordedObjectives() throws Exception {
		AppData data = new AppData();
		IncidentObjective objective = new IncidentObjective("Search north", 0);
		data.getForm202().setStructuredObjectives(List.of(objective));
		ResourceAssignment linked = new ResourceAssignment();
		linked.setObjectiveIds(List.of(objective.getObjectiveId()));
		ResourceAssignment staleLink = new ResourceAssignment();
		staleLink.setObjectiveIds(List.of("deleted-objective"));
		data.getForm204().getResourcesAssigned().addAll(List.of(linked, staleLink, new ResourceAssignment()));
		IncidentCommandRolePanel panel = panel(controller(data));
		assertTrue(badge(panel, "Objectives").getText().contains("1 recorded"));
		assertTrue(badge(panel, "Assignment coverage").getAccessibleContext().getAccessibleName()
				.contains("1/3 assignments linked"));
		JTable table = (JTable) field(panel, "objectives");
		assertEquals("Linked assignments", table.getColumnName(1));
		assertEquals("Assessment", table.getColumnName(2));
		assertTrue(table.getValueAt(0, 1).toString().contains("1 linked"));
		SwingUtilities.invokeAndWait(panel::dispose);
	}

	@Test
	void linkedAssignmentDialogShowsObjectiveAndRoutesAssignmentAction() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		AppData data = new AppData();
		IncidentObjective objective = new IncidentObjective("Search the north ridge", 0);
		data.getForm202().setStructuredObjectives(List.of(objective));
		ResourceAssignment assignment = new ResourceAssignment();
		assignment.setAssignmentTeamNumber("T-12");
		assignment.setResourceIdentifier("North team");
		assignment.setAssignment("Search sector A");
		assignment.setObjectiveIds(List.of(objective.getObjectiveId()));
		data.getForm204().getResourcesAssigned().add(assignment);
		SarTaskAssignment task = new SarTaskAssignment();
		task.setAssignmentId(assignment.getAssignmentId());
		task.setTaskLifecycleStatus("assigned - on task");
		data.getSarTaskAssignments().add(task);
		AtomicReference<String> openedAssignment = new AtomicReference<>();
		AtomicReference<String> invokedAction = new AtomicReference<>();
		IncidentCommandRolePanel.AssignmentActions actions = new IncidentCommandRolePanel.AssignmentActions() {
			/** {@inheritDoc} */
			@Override
			public void openIcs204(String assignmentId) {
				openedAssignment.set(assignmentId);
				invokedAction.set("ics204");
			}

			/** {@inheritDoc} */
			@Override
			public void openSarTask(String assignmentId) {
				invokedAction.set("sar");
			}

			/** {@inheritDoc} */
			@Override
			public void addLogEntry(String assignmentId) {
				invokedAction.set("log");
			}

			/** {@inheritDoc} */
			@Override
			public void changeStatus(String assignmentId) {
				invokedAction.set("status");
			}

			/** {@inheritDoc} */
			@Override
			public void debrief(String assignmentId) {
				invokedAction.set("debrief");
			}
		};
		IncidentCommandRolePanel panel = new IncidentCommandRolePanel(controller(data), ignored -> {
		}, actions);
		JTable objectives = (JTable) field(panel, "objectives");
		objectives.setRowSelectionInterval(0, 0);
		AtomicReference<Throwable> dialogFailure = new AtomicReference<>();
		Thread dialogThread = new Thread(() -> {
			try {
				SwingUtilities.invokeAndWait(() -> {
					try {
						var method = IncidentCommandRolePanel.class.getDeclaredMethod("showSelectedObjectiveAssignments");
						method.setAccessible(true);
						method.invoke(panel);
					} catch (ReflectiveOperationException exception) {
						throw new IllegalStateException(exception);
					}
				});
			} catch (Throwable exception) {
				dialogFailure.set(exception);
			}
		});
		dialogThread.start();
		JDialog dialog = null;
		for (int attempt = 0; attempt < 200 && dialog == null; attempt++) {
			for (java.awt.Window window : java.awt.Window.getWindows()) {
				if (window instanceof JDialog candidate && candidate.isShowing()
						&& candidate.getTitle().startsWith("Linked assignments for objective ")) {
					dialog = candidate;
					break;
				}
			}
			if (dialog == null) {
				Thread.sleep(10);
			}
		}
		assertTrue(dialog != null, "Linked-assignment dialog did not open");
		JDialog shownDialog = dialog;
		dialogThread.join(2000);
		assertFalse(dialogThread.isAlive(), "Modeless linked-assignment dialog blocked its caller");
		assertNull(dialogFailure.get());
		SwingUtilities.invokeAndWait(() -> {
			assertEquals("Linked assignments for objective 1", shownDialog.getTitle());
			assertFalse(shownDialog.getTitle().contains(objective.getObjectiveId()));
			JTextArea wording = findComponent(shownDialog, JTextArea.class);
			assertEquals(objective.getText(), wording.getText());
			JTable tasks = findComponent(shownDialog, JTable.class);
			assertEquals(List.of("Task Number", "Task Name", "Status", "Assignment"),
					java.util.stream.IntStream.range(0, tasks.getColumnCount()).mapToObj(tasks::getColumnName)
							.toList());
			assertEquals("T-12", tasks.getValueAt(0, 0));
			assertEquals("North team", tasks.getValueAt(0, 1));
			assertEquals("Search sector A", tasks.getValueAt(0, 3));
			button(shownDialog, "Open ICS 204 details").doClick();
			assertTrue(shownDialog.isShowing());
			assertEquals("ics204", invokedAction.get());

			JPopupMenu popup = tasks.getComponentPopupMenu();
			assertTrue(popup != null);
			JMenuItem logEntry = java.util.Arrays.stream(popup.getComponents()).filter(JMenuItem.class::isInstance)
					.map(JMenuItem.class::cast).filter(item -> "Make a log entry".equals(item.getText()))
					.findFirst().orElseThrow();
			logEntry.doClick();
			assertTrue(shownDialog.isShowing());
			assertEquals("log", invokedAction.get());
			button(shownDialog, "Change status").doClick();
			assertTrue(shownDialog.isShowing());
			assertEquals("status", invokedAction.get());

			task.setTaskLifecycleStatus("returned");
			tasks.clearSelection();
			tasks.setRowSelectionInterval(0, 0);
			assertFalse(button(shownDialog, "Change status").isEnabled());
			assertTrue(button(shownDialog, "Debrief").isEnabled());
			assertFalse(menuItem(tasks.getComponentPopupMenu(), "Change status").isEnabled());
			assertTrue(menuItem(tasks.getComponentPopupMenu(), "Debrief").isEnabled());
			button(shownDialog, "Debrief").doClick();
			assertTrue(shownDialog.isShowing());
			assertEquals("debrief", invokedAction.get());

			for (var listener : tasks.getMouseListeners()) {
				listener.mouseClicked(new java.awt.event.MouseEvent(tasks, java.awt.event.MouseEvent.MOUSE_CLICKED,
						System.currentTimeMillis(), 0, 10, tasks.getRowHeight() / 2, 2, false,
						java.awt.event.MouseEvent.BUTTON1));
			}
			assertEquals(assignment.getAssignmentId(), openedAssignment.get());
			assertTrue(shownDialog.isShowing());
			button(shownDialog, "Close").doClick();
		});
		assertEquals(assignment.getAssignmentId(), openedAssignment.get());
		SwingUtilities.invokeAndWait(panel::dispose);
	}

	/**
	 * Finds a context menu item by its displayed label.
	 *
	 * @param popup menu to inspect
	 * @param label requested item label
	 * @return matching menu item
	 */
	private static JMenuItem menuItem(JPopupMenu popup, String label) {
		return java.util.Arrays.stream(popup.getComponents()).filter(JMenuItem.class::isInstance)
				.map(JMenuItem.class::cast).filter(item -> label.equals(item.getText())).findFirst().orElseThrow();
	}

	@Test
	void objectiveBadgeShowsLegacyApprovalAndLiveCurrentThenStaleApproval() throws Exception {
		AppData data = new AppData();
		IncidentObjective objective = new IncidentObjective("Search north", 0);
		data.getForm202().setStructuredObjectives(List.of(objective));
		data.getForm202().setApprovedByIncidentCommanderName("Legacy commander");
		data.getForm202().setApprovedBySignature("Legacy signature");
		AppController controller = controller(data);
		IncidentCommandRolePanel panel = panel(controller);
		assertTrue(badge(panel, "Objectives").getText().contains("Legacy approval — content not verified"));

		SwingUtilities.invokeAndWait(() -> {
			try {
				textField(panel, "approver").setText("Commander");
				textField(panel, "signature").setText("Signature");
			} catch (ReflectiveOperationException exception) {
				throw new IllegalStateException(exception);
			}
			button(panel, "Approve current objectives").doClick();
		});
		assertTrue(badge(panel, "Objectives").getText().contains("Approved — current"));
		assertEquals("Commander", data.getForm202().getApprovedByIncidentCommanderName());
		assertEquals("Signature", data.getForm202().getApprovedBySignature());
		objective.setText("Search south");
		controller.markDirty();
		SwingUtilities.invokeAndWait(() -> {
		});
		assertTrue(badge(panel, "Objectives").getText().contains("Approval stale"));
		SwingUtilities.invokeAndWait(panel::dispose);
	}

	@Test
	void preparerNamesAndSignaturesDoNotImplyApprovalOrReview() throws Exception {
		AppData data = new AppData();
		data.getForm202().setPreparedByName("Preparer");
		data.getForm202().setPreparedBySignature("Preparer signature");
		data.getForm208().setPreparedByName("Safety preparer");
		data.getForm208().setPreparedBySignature("Safety signature");
		data.getMedicalPlan().setPreparedByMedicalUnitLeader("Medical preparer");
		data.getMedicalPlan().setPreparerSignature("Medical signature");
		data.getCanineMedicalPlan().setPreparedByMedicalUnitLeader("Canine preparer");
		data.getCanineMedicalPlan().setPreparerSignature("Canine signature");
		data.getForm205().setPreparedByName("Comms preparer");
		data.getForm205().setPreparedBySignature("Comms signature");
		IncidentCommandRolePanel panel = panel(controller(data));
		assertTrue(badge(panel, "Objectives").getText().contains("Not approved"));
		for (String area : List.of("Safety", "Human medical", "Canine medical", "Communications")) {
			assertTrue(badge(panel, area).getText().contains("Not yet reviewed"));
			assertTrue(badge(panel, area).getText().contains("No content recorded"));
		}
		SwingUtilities.invokeAndWait(panel::dispose);
	}

	@Test
	void defaultRoleWindowGivesObjectiveTextMostOfTheTableWidth() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		JFrame[] window = new JFrame[1];
		try {
			SwingUtilities.invokeAndWait(() -> window[0] = RolePanelWindows.openIncidentCommand(null,
					controller(new AppData()), ignored -> {
					}));
			SwingUtilities.invokeAndWait(() -> {
			});
			JTable table = (JTable) field((IncidentCommandRolePanel) window[0].getContentPane(), "objectives");
			int textWidth = table.getColumnModel().getColumn(0).getWidth();
			int linksWidth = table.getColumnModel().getColumn(1).getWidth();
			int assessmentWidth = table.getColumnModel().getColumn(2).getWidth();
			assertEquals(1220, window[0].getWidth());
			assertTrue(textWidth >= 400, "Default objective text width: " + textWidth);
			assertTrue(textWidth > assessmentWidth * 2, "Text/assessment widths: " + textWidth + "/" + assessmentWidth);
			assertTrue(textWidth > linksWidth, "Text/links widths: " + textWidth + "/" + linksWidth);
		} finally {
			if (window[0] != null) {
				SwingUtilities.invokeAndWait(window[0]::dispose);
			}
		}
	}

	@Test
	void objectiveTextGetsSpareWidthWhileAssessmentAndLinksStayCompact() throws Exception {
		IncidentCommandRolePanel panel = panel(controller(new AppData()));
		JTable table = (JTable) field(panel, "objectives");
		int[] textWidths = new int[2];
		SwingUtilities.invokeAndWait(() -> {
			table.setSize(800, 200);
			table.doLayout();
			textWidths[0] = table.getColumnModel().getColumn(0).getWidth();
			table.setSize(1200, 200);
			table.doLayout();
			textWidths[1] = table.getColumnModel().getColumn(0).getWidth();
		});
		assertTrue(textWidths[1] > textWidths[0]);
		assertTrue(table.getColumnModel().getColumn(1).getWidth() >= 160);
		assertTrue(table.getColumnModel().getColumn(2).getWidth() <= 140);
		SwingUtilities.invokeAndWait(panel::dispose);
	}

	@Test
	void fullEditorNavigationRemainsAvailable() throws Exception {
		List<WorkspaceDestination> destinations = new ArrayList<>();
		IncidentCommandRolePanel[] panel = new IncidentCommandRolePanel[1];
		SwingUtilities.invokeAndWait(() -> {
			panel[0] = new IncidentCommandRolePanel(controller(new AppData()), destinations::add);
			JPanel header = (JPanel) panel[0].getComponent(0);
			JPanel actions = (JPanel) header.getComponent(1);
			((JButton) actions.getComponent(0)).doClick();
			((JButton) actions.getComponent(1)).doClick();
			panel[0].dispose();
		});
		assertEquals(List.of(WorkspaceDestination.of(WorkspaceId.ICS_202), WorkspaceDestination.of(WorkspaceId.TCARDS)),
				destinations);
	}

	@Test
	void disposalStopsLiveBadgeRefresh() throws Exception {
		AppData data = new AppData();
		AppController controller = controller(data);
		IncidentCommandRolePanel panel = panel(controller);
		String original = badge(panel, "Communications").getText();
		SwingUtilities.invokeAndWait(panel::dispose);
		data.getForm205().setSpecialInstructions("Recorded after disposal");
		controller.markDirty();
		SwingUtilities.invokeAndWait(() -> {
		});
		assertEquals(original, badge(panel, "Communications").getText());
	}

	/**
	 * Creates a resource with an explicit stable identity.
	 *
	 * @param id stable resource identity.
	 * @param type recorded resource type.
	 * @param status recorded status.
	 * @return resource fixture.
	 */
	private TCard card(String id, TCardType type, String status) {
		TCard card = new TCard();
		card.setResourceId(id);
		card.setCardType(type);
		card.setStatus(status);
		return card;
	}

	/**
	 * Finds an actual displayed button by its text.
	 *
	 * @param parent parent container.
	 * @param text button text.
	 * @return matching button, or null when absent.
	 */
	private JButton button(Container parent, String text) {
		for (java.awt.Component component : parent.getComponents()) {
			if (component instanceof JButton candidate && text.equals(candidate.getText())) {
				return candidate;
			}
			if (component instanceof Container container) {
				JButton nested = button(container, text);
				if (nested != null) {
					return nested;
				}
			}
		}
		return null;
	}

	/**
	 * Finds the first descendant of the requested Swing component type.
	 *
	 * @param parent container to inspect.
	 * @param type component type to find.
	 * @param <T> component type.
	 * @return matching component.
	 */
	private <T extends java.awt.Component> T findComponent(Container parent, Class<T> type) {
		for (java.awt.Component component : parent.getComponents()) {
			if (type.isInstance(component)) {
				return type.cast(component);
			}
			if (component instanceof Container container) {
				T nested = findComponent(container, type);
				if (nested != null) {
					return nested;
				}
			}
		}
		return null;
	}

	/**
	 * Constructs the panel on the event dispatch thread.
	 *
	 * @param controller incident controller.
	 * @return constructed panel.
	 * @throws Exception if event dispatch fails.
	 */
	private IncidentCommandRolePanel panel(AppController controller) throws Exception {
		IncidentCommandRolePanel[] panel = new IncidentCommandRolePanel[1];
		SwingUtilities.invokeAndWait(() -> panel[0] = new IncidentCommandRolePanel(controller, ignored -> {
		}));
		return panel[0];
	}

	/**
	 * Finds a badge by its accessible heading.
	 *
	 * @param panel inspected panel.
	 * @param title badge heading.
	 * @return matching badge.
	 * @throws Exception if reflection fails.
	 */
	private JLabel badge(IncidentCommandRolePanel panel, String title) throws Exception {
		JPanel badges = (JPanel) field(panel, "planProgress");
		for (java.awt.Component component : badges.getComponents()) {
			JLabel badge = (JLabel) component;
			if (badge.getAccessibleContext().getAccessibleName().startsWith(title + ":")) {
				return badge;
			}
		}
		throw new AssertionError("Missing badge: " + title);
	}

	/**
	 * Reads a named panel field for regression assertions.
	 *
	 * @param panel inspected panel.
	 * @param name field name.
	 * @return field value.
	 * @throws ReflectiveOperationException if field access fails.
	 */
	private Object field(IncidentCommandRolePanel panel, String name) throws ReflectiveOperationException {
		Field field = IncidentCommandRolePanel.class.getDeclaredField(name);
		field.setAccessible(true);
		return field.get(panel);
	}

	/**
	 * Creates a controller with test-local persistence.
	 *
	 * @param data incident fixture.
	 * @return controller.
	 */
	private AppController controller(AppData data) {
		return new AppController(data, new LocalRepository(directory.resolve("incident.json")), new PdfExportService(),
				new IncidentValidator());
	}

	/**
	 * Reads the explicit planning-stage selector.
	 *
	 * @param panel inspected panel.
	 * @return planning-stage selector.
	 * @throws Exception if reflection fails.
	 */
	@SuppressWarnings("unchecked")
	private JComboBox<IapPlanningStage> planningStage(IncidentCommandRolePanel panel) throws Exception {
		Field field = IncidentCommandRolePanel.class.getDeclaredField("planningStage");
		field.setAccessible(true);
		return (JComboBox<IapPlanningStage>) field.get(panel);
	}

	/**
	 * Reads one approval draft field.
	 *
	 * @param panel inspected panel.
	 * @param name field name.
	 * @return draft field.
	 * @throws ReflectiveOperationException if reflection fails.
	 */
	private JTextField textField(IncidentCommandRolePanel panel, String name) throws ReflectiveOperationException {
		Field field = IncidentCommandRolePanel.class.getDeclaredField(name);
		field.setAccessible(true);
		return (JTextField) field.get(panel);
	}
}
