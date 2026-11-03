/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.medical.AddressGeocoder;
import org.sarmanagement.icsforms.medical.DrivingRouteProvider;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.MedicalPlan;
import org.sarmanagement.icsforms.model.PlanReviewArea;
import org.sarmanagement.icsforms.model.ResourceAssignment;
import org.sarmanagement.icsforms.model.SarTaskAssignment;
import org.sarmanagement.icsforms.model.TCard;
import org.sarmanagement.icsforms.model.VetFacility;
import org.sarmanagement.icsforms.persistence.LocalRepository;
import org.sarmanagement.icsforms.pdf.PdfExportService;
import org.sarmanagement.icsforms.validation.IncidentValidator;

import javax.swing.SwingUtilities;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JTable;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** Verifies strict medical-reference separation and side-effect-free lookup. */
class MedicalReferencePanelTest {
	@TempDir
	Path directory;

	@Test
	void humanAndCanineReferencesNeverSubstituteContent() throws Exception {
		AppData data = new AppData();
		data.getMedicalPlan().setCall911Procedure("HUMAN ONLY");
		MedicalPlan.Hospital hospital = new MedicalPlan.Hospital();
		hospital.setName("Human Hospital");
		data.getMedicalPlan().setHospitals(List.of(hospital));
		data.getCanineMedicalPlan().setEmergencyProcedures("CANINE ONLY");
		VetFacility vet = new VetFacility();
		vet.setName("Veterinary Hospital");
		data.getCanineMedicalPlan().setSelectedFacilities(List.of(vet));
		AppController controller = controller(data);
		MedicalReferencePanel[] panels = new MedicalReferencePanel[2];

		SwingUtilities.invokeAndWait(() -> {
			panels[0] = new MedicalReferencePanel(controller, false, destination -> {
			});
			panels[1] = new MedicalReferencePanel(controller, true, destination -> {
			});
		});

		assertTrue(panels[0].displayedInstructions().contains("HUMAN ONLY"));
		assertFalse(panels[0].displayedInstructions().contains("CANINE ONLY"));
		assertTrue(panels[1].displayedInstructions().contains("CANINE ONLY"));
		assertFalse(panels[1].displayedInstructions().contains("HUMAN ONLY"));
		assertEquals(1, panels[0].displayedFacilityCount());
		assertEquals(1, panels[1].displayedFacilityCount());
		SwingUtilities.invokeAndWait(() -> {
			panels[0].dispose();
			panels[1].dispose();
		});
	}

	@Test
	void canineWorkspaceAndReferenceCanRunConcurrentlyFromSharedDirectory() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		AppData data = new AppData();
		data.getMedicalPlan().setEmergencyProcedures("HUMAN ONLY");
		data.getCanineMedicalPlan().setEmergencyProcedures("CANINE ONLY");
		VetFacility accepted = new VetFacility();
		accepted.setName("Accepted canine hospital");
		data.getCanineMedicalPlan().setSelectedFacilities(List.of(accepted));
		VetFacility candidate = new VetFacility();
		candidate.setName("Directory canine hospital");
		data.getVetDirectory().setFacilities(new java.util.ArrayList<>(List.of(candidate)));
		AppController controller = controller(data);
		AtomicInteger providerCalls = new AtomicInteger();
		AddressGeocoder geocoder = address -> {
			providerCalls.incrementAndGet();
			return List.of();
		};
		DrivingRouteProvider routeProvider = (source, destinations) -> {
			providerCalls.incrementAndGet();
			return List.of();
		};
		CanineMedicalPanel caninePanel = new CanineMedicalPanel(controller, directory, geocoder, routeProvider);
		MedicalWorkspacePanel workspace = new MedicalWorkspacePanel(new Ics206Panel(controller), caninePanel);
		MedicalReferencePanel reference = new MedicalReferencePanel(controller, true, destination -> {
		});
		JFrame workspaceWindow = new JFrame("Medical workspace");
		JFrame referenceWindow = new JFrame("Canine reference");

		try {
			SwingUtilities.invokeAndWait(() -> {
				workspace.openCaninePlan();
				workspaceWindow.setContentPane(workspace);
				workspaceWindow.setSize(900, 600);
				referenceWindow.setContentPane(reference);
				referenceWindow.setSize(900, 600);
				workspaceWindow.setVisible(true);
				referenceWindow.setVisible(true);
				assertTrue(workspaceWindow.isVisible());
				assertTrue(referenceWindow.isVisible());
				assertNotSame(workspace, reference);
				assertEquals(2, reference.displayedFacilityCount());
				assertTrue(reference.displayedInstructions().contains("CANINE ONLY"));
				assertFalse(reference.displayedInstructions().contains("HUMAN ONLY"));
				assertTrue(reference.displayedFacilityDetail().contains("Accepted canine hospital"));
				assertEquals(1, CanineMedicalFacilityTools.directoryFacilities(data).size());
				assertEquals(candidate,
						CanineMedicalFacilityTools.findDirectoryFacility(data, candidate.getId()));
				assertTrue(CanineMedicalFacilityTools.selectedFacility(data, candidate.getId()).detail()
						.contains("Directory canine hospital"));
				findComponent(reference, JList.class).setSelectedIndex(1);
				assertTrue(reference.displayedFacilityDetail().contains("Directory canine hospital"));
				assertEquals(1, tableWithColumnCount(workspace, 18).getRowCount());
				VetFacility additional = new VetFacility();
				additional.setName("New shared directory hospital");
				data.getVetDirectory().getFacilities().add(additional);
				controller.markDirty();
			});
			SwingUtilities.invokeAndWait(() -> {
				caninePanel.refreshFromModel();
				assertEquals(2, tableWithColumnCount(workspace, 18).getRowCount());
				assertEquals(3, reference.displayedFacilityCount());
				assertEquals("HUMAN ONLY", data.getMedicalPlan().getEmergencyProcedures());
				assertEquals(0, providerCalls.get(), "Opening or refreshing views must not request routing services");
			});
		} finally {
			SwingUtilities.invokeAndWait(() -> {
				caninePanel.dispose();
				reference.dispose();
				workspaceWindow.dispose();
				referenceWindow.dispose();
				controller.close();
			});
		}
	}

	@Test
	void openingAndRefreshingReferencesHasNoIncidentEffects() throws Exception {
		AppData data = new AppData();
		data.getCanineMedicalPlan().setVeterinaryPersonnelResourceIds(List.of("missing-link"));
		AppController controller = controller(data);
		int logCount = data.getActivityLogs().size();
		int taskCount = data.getSarTaskAssignments().size();
		MedicalReferencePanel[] panel = new MedicalReferencePanel[1];

		SwingUtilities.invokeAndWait(() -> {
			panel[0] = new MedicalReferencePanel(controller, true, destination -> {
			});
			panel[0].refreshFromModel();
		});

		assertEquals(logCount, data.getActivityLogs().size());
		assertEquals(taskCount, data.getSarTaskAssignments().size());
		assertTrue(panel[0].displayedPersonnel().contains("missing resource"));
		SwingUtilities.invokeAndWait(panel[0]::dispose);
	}

	@Test
	void linkedVeterinaryCardsShowRecordedStatusAndLocation() throws Exception {
		AppData data = new AppData();
		TCard card = new TCard();
		card.setPersonName("Dr. Vet");
		card.setStatus("Assigned");
		card.setLocation("Team 4");
		data.getTCards().add(card);
		data.getCanineMedicalPlan().setVeterinaryPersonnelResourceIds(List.of(card.getResourceId()));
		MedicalReferencePanel[] panel = new MedicalReferencePanel[1];

		SwingUtilities.invokeAndWait(() -> panel[0] = new MedicalReferencePanel(controller(data), true,
				destination -> {
				}));

		assertTrue(panel[0].displayedPersonnel().contains("Dr. Vet"));
		assertTrue(panel[0].displayedPersonnel().contains("recorded status Assigned"));
		assertTrue(panel[0].displayedPersonnel().contains("recorded location Team 4"));
		SwingUtilities.invokeAndWait(panel[0]::dispose);
	}

	@Test
	void explicitMedicalEventCreatesOneCategorizedIcpEntry() {
		AppData data = new AppData();
		AppController controller = controller(data);

		controller.recordMedicalEvent(true, "Canine transported for evaluation", "");

		assertEquals(1, data.getActivityLogs().size());
		assertEquals(1, data.getActivityLogs().get(0).getActivityLog().size());
		assertEquals(org.sarmanagement.icsforms.model.ActivityEventType.ID_CANINE_MEDICAL_EVENT,
				data.getActivityLogs().get(0).getActivityLog().get(0).getEventTypeId());
	}

	@Test
	void approvalIndicatorTracksCurrentContentAndPdfLoadsAutomatically() throws Exception {
		AppData data = new AppData();
		data.getMedicalPlan().setEmergencyProcedures("Recorded procedures");
		AppController controller = controller(data);
		MedicalReferencePanel[] panel = new MedicalReferencePanel[1];
		SwingUtilities.invokeAndWait(() -> {
			panel[0] = new MedicalReferencePanel(controller, false, destination -> {
			});
			assertTrue(panel[0].completionText().contains("Incomplete"));
			assertFalse(hasRefreshButton(panel[0]));
			assertTrue(pdfPanel(panel[0]).previewStateText().contains("PDF"));
			data.recordPlanReview(PlanReviewArea.HUMAN_MEDICAL, "Safety Officer", null);
			panel[0].refreshFromModel();
			assertTrue(panel[0].completionText().contains("Content recorded / reviewed-approved"));
			data.getMedicalPlan().setEmergencyProcedures("Changed procedures");
			panel[0].refreshFromModel();
			assertTrue(panel[0].completionText().contains("Changed since review"));
			assertTrue(panel[0].completionText().contains("Incomplete"));
			panel[0].dispose();
		});
	}

	@Test
	void emptyReviewedPlanRemainsIncomplete() throws Exception {
		AppData data = new AppData();
		AppController controller = controller(data);
		data.recordPlanReview(PlanReviewArea.HUMAN_MEDICAL, "Safety Officer", null);
		SwingUtilities.invokeAndWait(() -> {
			MedicalReferencePanel panel = new MedicalReferencePanel(controller, false, destination -> {
			});
			try {
				assertTrue(panel.completionText().contains("Incomplete"));
				assertTrue(panel.completionText().contains("Reviewed-approved"));
			} finally {
				panel.dispose();
			}
		});
	}

	@Test
	void changedReferenceAutomaticallyReplacesPdfWithoutRefreshAction() throws Exception {
		AppData data = new AppData();
		AppController controller = controller(data);
		MedicalReferencePanel[] panel = new MedicalReferencePanel[1];
		SwingUtilities.invokeAndWait(() -> panel[0] = new MedicalReferencePanel(controller, false, destination -> {
		}));
		try {
			waitForPdf(panel[0]);
			SwingUtilities.invokeAndWait(() -> {
				data.getMedicalPlan().setEmergencyProcedures("Updated current reference content");
				controller.markDirty();
			});
			waitForPdf(panel[0]);
			SwingUtilities.invokeAndWait(() -> assertTrue(
					panel[0].displayedInstructions().contains("Updated current reference content")));
		} finally {
			SwingUtilities.invokeAndWait(panel[0]::dispose);
		}
	}

	@Test
	void eventSupportsTaskAndLocationContextWithoutChangingTask() throws Exception {
		AppData data = new AppData();
		for (int index = 0; index < 2; index++) {
			ResourceAssignment assignment = new ResourceAssignment();
			assignment.setAssignmentId("medical-context-task-" + index);
			assignment.setAssignmentTeamNumber("Team 7");
			assignment.setAssignment(index == 0 ? "Search north sector" : "Search south sector");
			assignment.setResourceIdentifier("Resource Alpha");
			data.getForm204().getResourcesAssigned().add(assignment);
		}
		AppController controller = controller(data);
		controller.syncSarTasks();
		SarTaskAssignment task = data.getSarTaskAssignments().stream()
				.filter(candidate -> "medical-context-task-0".equals(candidate.getAssignmentId())).findFirst().orElseThrow();
		SarTaskAssignment otherTask = data.getSarTaskAssignments().stream()
				.filter(candidate -> "medical-context-task-1".equals(candidate.getAssignmentId())).findFirst().orElseThrow();
		SwingUtilities.invokeAndWait(() -> {
			MedicalReferencePanel panel = new MedicalReferencePanel(controller, false, destination -> {
			});
			panel.recordMedicalEvent("Transport requested", task.getAssignmentId(), " Trailhead ");
			assertTrue(data.getSarTaskAssignments().stream()
					.anyMatch(candidate -> otherTask.getAssignmentId().equals(candidate.getAssignmentId())));
			panel.recordMedicalEvent("Transport requested", otherTask.getAssignmentId(), " Trailhead ");
			panel.dispose();
		});
		var entry = data.getActivityLogs().get(0).getActivityLog().get(0);
		assertEquals("Transport requested\nTask: " + task.getTaskId() + " / Team 7 — Search north sector"
				+ "\nLocation: Trailhead", entry.getNotableActivity());
		assertFalse(entry.getResourceIdentifier().isBlank());
		var otherEntry = data.getActivityLogs().get(0).getActivityLog().get(1);
		assertEquals(entry.getResourceIdentifier(), otherEntry.getResourceIdentifier());
		assertEquals("Transport requested\nTask: " + otherTask.getTaskId() + " / Team 7 — Search south sector"
				+ "\nLocation: Trailhead", otherEntry.getNotableActivity());
		assertEquals("Search north sector", task.getAssignment());
		assertEquals("Search south sector", otherTask.getAssignment());
		assertEquals(2, data.getActivityLogs().get(0).getActivityLog().size());
	}

	/**
	 * Finds a PDF surface among nested reference components.
	 *
	 * @param container reference component tree.
	 * @return PDF surface, or null when absent.
	 */
	private AsyncFormPreviewPanel pdfPanel(Container container) {
		for (Component component : container.getComponents()) {
			if (component instanceof AsyncFormPreviewPanel panel) {
				return panel;
			}
			if (component instanceof Container child) {
				AsyncFormPreviewPanel panel = pdfPanel(child);
				if (panel != null) {
					return panel;
				}
			}
		}
		return null;
	}

	/**
	 * Waits until the latest reference PDF has finished rendering on the EDT.
	 *
	 * @param panel reference panel.
	 * @throws Exception when the wait is interrupted or EDT synchronization fails.
	 */
	private void waitForPdf(MedicalReferencePanel panel) throws Exception {
		AtomicBoolean ready = new AtomicBoolean();
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
		while (!ready.get() && System.nanoTime() < deadline) {
			SwingUtilities.invokeAndWait(() -> ready.set(pdfPanel(panel).previewStateText().contains("Current PDF")
					&& pdfPanel(panel).displayedPageCount() > 0));
			if (!ready.get()) {
				Thread.sleep(25);
			}
		}
		assertTrue(ready.get());
	}

	/**
	 * Checks that obsolete manual-refresh actions are not exposed.
	 *
	 * @param container reference component tree.
	 * @return true when a refresh button is present.
	 */
	private boolean hasRefreshButton(Container container) {
		for (Component component : container.getComponents()) {
			if (component instanceof JButton button && button.getText().contains("Refresh")) {
				return true;
			}

			if (component instanceof Container child && hasRefreshButton(child)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Finds the canine directory table by its stable column count.
	 *
	 * @param root workspace component tree.
	 * @param columns expected column count.
	 * @return matching table.
	 */
	private JTable tableWithColumnCount(Container root, int columns) {
		for (Component component : root.getComponents()) {
			if (component instanceof JTable table && table.getColumnCount() == columns) {
				return table;
			}
			if (component instanceof Container child) {
				JTable table = tableWithColumnCount(child, columns);
				if (table != null) {
					return table;
				}
			}
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	private <T extends Component> T findComponent(Container root, Class<T> type) {
		for (Component component : root.getComponents()) {
			if (type.isInstance(component)) {
				return (T) component;
			}
			if (component instanceof Container child) {
				T found = findComponent(child, type);
				if (found != null) {
					return found;
				}
			}
		}
		return null;
	}

	/**
	 * Creates an isolated incident controller.
	 *
	 * @param data test incident.
	 * @return configured controller.
	 */
	private AppController controller(AppData data) {
		return new AppController(data, new LocalRepository(directory.resolve("incident.json")), new PdfExportService(),
				new IncidentValidator());
	}
}
