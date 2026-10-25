/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.MedicalPlan;
import org.sarmanagement.icsforms.model.TCard;
import org.sarmanagement.icsforms.model.VetFacility;
import org.sarmanagement.icsforms.persistence.LocalRepository;
import org.sarmanagement.icsforms.pdf.PdfExportService;
import org.sarmanagement.icsforms.validation.IncidentValidator;

import javax.swing.SwingUtilities;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

	private AppController controller(AppData data) {
		return new AppController(data, new LocalRepository(directory.resolve("incident.json")), new PdfExportService(),
				new IncidentValidator());
	}
}
