/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.IapPlanningStage;
import org.sarmanagement.icsforms.model.IncidentObjective;
import org.sarmanagement.icsforms.model.MedicalPlan;
import org.sarmanagement.icsforms.model.PlanReviewArea;
import org.sarmanagement.icsforms.model.PlanReviewSupport;
import org.sarmanagement.icsforms.model.RadioChannel;
import org.sarmanagement.icsforms.model.ResourceAssignment;
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
import javax.swing.SwingUtilities;
import javax.swing.JTextField;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
		assertTrue(badge(panel, "Assignment coverage").getText().contains("1/3 assignments linked"));
		SwingUtilities.invokeAndWait(panel::dispose);
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
			int assessmentWidth = table.getColumnModel().getColumn(1).getWidth();
			int linksWidth = table.getColumnModel().getColumn(2).getWidth();
			assertEquals(1220, window[0].getWidth());
			assertTrue(textWidth >= 400, "Default objective text width: " + textWidth);
			assertTrue(textWidth > assessmentWidth * 2, "Text/assessment widths: " + textWidth + "/" + assessmentWidth);
			assertTrue(textWidth > linksWidth * 2, "Text/links widths: " + textWidth + "/" + linksWidth);
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
		assertTrue(textWidths[0] > table.getColumnModel().getColumn(1).getMaxWidth() * 3);
		assertTrue(textWidths[1] > textWidths[0]);
		assertTrue(table.getColumnModel().getColumn(1).getWidth() <= 150);
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
