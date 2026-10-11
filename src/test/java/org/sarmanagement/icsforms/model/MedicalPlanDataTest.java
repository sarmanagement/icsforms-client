/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers medical-plan defaults, persistence, provenance, eligibility, and rollover.
 */
class MedicalPlanDataTest {
	private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

	@Test
	void medicalPlansDefaultInactiveAndPreserveBothModesAndNullableAnswers() throws Exception {
		AppData data = new AppData();
		assertFalse(data.getMedicalPlan().isActive());
		assertTrue(data.getMedicalPlan().isSimpleMode());
		assertEquals("Call 911", data.getMedicalPlan().getCall911Procedure());
		assertFalse(data.getCanineMedicalPlan().isActive());

		MedicalPlan plan = data.getMedicalPlan();
		plan.setEmergencyProcedures("Call dispatch, then notify Safety.");
		MedicalPlan.Hospital hospital = new MedicalPlan.Hospital();
		hospital.setName("Fictional Valley Hospital");
		hospital.setHelipad(null);
		plan.getHospitals().add(hospital);
		plan.setSimpleMode(false);
		plan.setSimpleMode(true);

		AppData reloaded = mapper.readValue(mapper.writeValueAsBytes(data), AppData.class);
		assertTrue(reloaded.getMedicalPlan().isSimpleMode());
		assertEquals("Call dispatch, then notify Safety.", reloaded.getMedicalPlan().getEmergencyProcedures());
		assertEquals("Fictional Valley Hospital", reloaded.getMedicalPlan().getHospitals().get(0).getName());
		assertNull(reloaded.getMedicalPlan().getHospitals().get(0).getHelipad());
		assertEquals("Not reviewed", reloaded.getMedicalPlan().getReviewStatus());
	}

	@Test
	void stagingEditsInvalidateCoordinatesAndRejectInvalidLocations() {
		StagingLocation staging = new StagingLocation();
		staging.setAddress("10 Fictional Road");
		staging.setResolution("10 Fictional Road", 42.1, -71.2, "manual", "operator-confirmed", null);
		long resolvedGeneration = staging.getGeneration();
		assertTrue(staging.hasValidCoordinates());

		staging.setAddress("11 Fictional Road");
		assertFalse(staging.hasValidCoordinates());
		assertTrue(staging.getGeneration() > resolvedGeneration);
		assertThrows(IllegalArgumentException.class,
				() -> staging.setResolution("", 91, 0, "manual", "", null));
	}

	@Test
	void directoryAndSelectedPlanSnapshotsRemainIndependent() {
		AppData data = new AppData();
		VetFacility facility = new VetFacility();
		facility.setName("Fictional Regional Vet");
		data.getVetDirectory().getFacilities().add(facility);
		VetFacility snapshot = new VetFacility();
		snapshot.setId(facility.getId());
		snapshot.setName(facility.getName());
		data.getCanineMedicalPlan().getSelectedFacilities().add(snapshot);

		data.getCanineMedicalPlan().getSelectedFacilities().clear();

		assertEquals(1, data.getVetDirectory().getFacilities().size());
		assertTrue(data.getCanineMedicalPlan().getSelectedFacilities().isEmpty());
	}

	@Test
	void canineWorkspaceVisibilityMatchesSarOrCanineCardRegardlessOfStatus() {
		AppData data = new AppData();
		assertTrue(data.isCanineMedicalWorkspaceEligible());
		data.setIncidentMode(IncidentMode.GENERIC);
		assertFalse(data.isCanineMedicalWorkspaceEligible());

		TCard canine = new TCard();
		canine.setCardType(TCardType.EQUIPMENT);
		canine.setCanineResource(true);
		canine.setStatus("Out of Service");
		data.getTCards().add(canine);
		assertTrue(data.isCanineMedicalWorkspaceEligible());

		canine.setCanineResource(false);
		canine.setHandlerName("Handler");
		assertTrue(data.isCanineMedicalWorkspaceEligible());

		data.getTCards().clear();
		assertFalse(data.isCanineMedicalWorkspaceEligible());
	}

	@Test
	void rolloverArchivesMedicalSnapshotsAndKeepsReusableDirectoryAndBase() {
		AppData data = new AppData();
		data.getMedicalPlan().setActive(true);
		data.getMedicalPlan().setCall911Procedure("Use local emergency dispatch");
		data.getCanineMedicalPlan().setActive(true);
		data.getCanineMedicalPlan().setPoisonControlContact("Local contact on file");
		data.getVetDirectory().getFacilities().add(new VetFacility());
		data.getStagingLocation().setAddress("Incident staging address");

		data.advanceToNewOperationalPeriod();

		assertEquals(1, data.getOperationalPeriodHistory().size());
		assertTrue(data.getOperationalPeriodHistory().get(0).getMedicalPlan().isActive());
		assertEquals("Use local emergency dispatch",
				data.getOperationalPeriodHistory().get(0).getMedicalPlan().getCall911Procedure());
		assertTrue(data.getOperationalPeriodHistory().get(0).getCanineMedicalPlan().isActive());
		assertFalse(data.getMedicalPlan().isActive());
		assertFalse(data.getCanineMedicalPlan().isActive());
		assertEquals(1, data.getVetDirectory().getFacilities().size());
		assertEquals("Incident staging address", data.getStagingLocation().getAddress());
	}
}
