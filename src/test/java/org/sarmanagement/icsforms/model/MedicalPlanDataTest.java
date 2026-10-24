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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
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
		CanineMedicalPlan caninePlan = data.getCanineMedicalPlan();
		caninePlan.setAidStationInformation("Fictional base | paramedics unknown");
		caninePlan.setTransportServices("Fictional canine-capable ground service");
		IncidentLocation base = new IncidentLocation();
		base.setType("Base");
		base.setName("Fictional Incident Base");
		base.setAddress("10 Fictional Base Road");
		data.getIncidentLocations().add(base);
		data.setCanineRoutingBaseLocationId(base.getId());
		VetFacility selectedVet = new VetFacility();
		selectedVet.setName("Fictional 24-hour Vet");
		selectedVet.setAvailable24Hours(true);
		caninePlan.getSelectedFacilities().add(selectedVet);

		AppData reloaded = mapper.readValue(mapper.writeValueAsBytes(data), AppData.class);
		assertTrue(reloaded.getMedicalPlan().isSimpleMode());
		assertEquals("Call dispatch, then notify Safety.", reloaded.getMedicalPlan().getEmergencyProcedures());
		assertEquals("Fictional Valley Hospital", reloaded.getMedicalPlan().getHospitals().get(0).getName());
		assertNull(reloaded.getMedicalPlan().getHospitals().get(0).getHelipad());
		assertEquals("Not reviewed", reloaded.getMedicalPlan().getReviewStatus());
		assertEquals("Fictional base | paramedics unknown",
				reloaded.getCanineMedicalPlan().getAidStationInformation());
		assertEquals("Fictional canine-capable ground service",
				reloaded.getCanineMedicalPlan().getTransportServices());
		assertEquals("Fictional 24-hour Vet",
				reloaded.getCanineMedicalPlan().getSelectedFacilities().get(0).getName());
		assertEquals(base.getId(), reloaded.getIncidentLocations().get(0).getId());
		assertEquals("Fictional Incident Base", reloaded.getIncidentLocations().get(0).getName());
		assertEquals("10 Fictional Base Road", reloaded.getIncidentLocations().get(0).getAddress());
		assertEquals(base.getId(), reloaded.getCanineRoutingBaseLocationId());
	}

	@Test
	void olderIncidentDocumentsDefaultToAnEmptyLocationListAndManualRoutingBase() throws Exception {
		AppData migrated = mapper.readValue("{\"schemaVersion\":9}", AppData.class);

		assertEquals(9, migrated.getSchemaVersion());
		assertTrue(migrated.getIncidentLocations().isEmpty());
		assertEquals("", migrated.getCanineRoutingBaseLocationId());
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
	void rolloverCarriesForwardIndependentMedicalPlansAndKeepsReusableDirectoryAndBase() {
		AppData data = new AppData();
		MedicalPlan medicalPlan = data.getMedicalPlan();
		medicalPlan.setActive(true);
		medicalPlan.setSimpleMode(false);
		medicalPlan.setCall911Procedure("Use local emergency dispatch");
		medicalPlan.setEmergencyProcedures("Notify command after dispatch.");
		medicalPlan.setPreparedByMedicalUnitLeader("Medical Unit Leader");
		medicalPlan.setPreparerSignature("Prepared signature");
		medicalPlan.setPreparedAt(LocalDateTime.of(2026, 10, 24, 12, 0));
		medicalPlan.setReviewedBySafetyOfficer("Prior Safety Officer");
		medicalPlan.setSafetyOfficerSignature("Prior review signature");
		medicalPlan.setReviewStatus("Reviewed");
		MedicalPlan.AidStation aidStation = new MedicalPlan.AidStation();
		aidStation.setName("Incident aid station");
		aidStation.setLocation("Incident base");
		medicalPlan.getAidStations().add(aidStation);
		MedicalPlan.TransportService transport = new MedicalPlan.TransportService();
		transport.setService("Incident ambulance");
		transport.setAirOrGround("Ground");
		medicalPlan.getTransportServices().add(transport);
		MedicalPlan.Hospital hospital = new MedicalPlan.Hospital();
		hospital.setName("Incident hospital");
		hospital.setTraumaCenter(true);
		medicalPlan.getHospitals().add(hospital);

		CanineMedicalPlan caninePlan = data.getCanineMedicalPlan();
		caninePlan.setActive(true);
		caninePlan.setPoisonControlContact("Local contact on file");
		caninePlan.setAidStationInformation("Canine aid station");
		caninePlan.setTransportServices("Canine ground transport");
		MedicalPlan.TransportService canineAmbulance = new MedicalPlan.TransportService();
		canineAmbulance.setService("Ambulance");
		canineAmbulance.setLevelOfService("Canine-capable");
		caninePlan.getTransportServiceEntries().add(canineAmbulance);
		caninePlan.setEmergencyProcedures("Provide canine first aid.");
		caninePlan.setAviationAssetsUsed(true);
		caninePlan.setPreparedByMedicalUnitLeader("Medical Unit Leader");
		caninePlan.setPreparerSignature("Canine prepared signature");
		caninePlan.setPreparedAt(LocalDateTime.of(2026, 10, 24, 12, 30));
		caninePlan.setReviewedBySafetyOfficer("Prior Canine Safety Officer");
		caninePlan.setSafetyOfficerSignature("Prior canine review signature");
		caninePlan.setReviewStatus("Reviewed");
		VetFacility selectedFacility = new VetFacility();
		selectedFacility.setName("Selected canine hospital");
		selectedFacility.setContactNumbers(new ArrayList<>(List.of("555-0100")));
		selectedFacility.setCapabilities("Emergency care");
		caninePlan.getSelectedFacilities().add(selectedFacility);
		data.getVetDirectory().getFacilities().add(new VetFacility());
		data.getStagingLocation().setAddress("Incident staging address");
		IncidentLocation base = new IncidentLocation();
		base.setType("Base");
		base.setName("Incident Base");
		base.setAddress("Incident base address");
		data.getIncidentLocations().add(base);
		data.setCanineRoutingBaseLocationId(base.getId());

		data.advanceToNewOperationalPeriod();

		assertEquals(1, data.getOperationalPeriodHistory().size());
		assertTrue(data.getOperationalPeriodHistory().get(0).getMedicalPlan().isActive());
		assertEquals("Use local emergency dispatch",
				data.getOperationalPeriodHistory().get(0).getMedicalPlan().getCall911Procedure());
		assertEquals("Notify command after dispatch.", data.getMedicalPlan().getEmergencyProcedures());
		assertTrue(data.getMedicalPlan().isActive());
		assertFalse(data.getMedicalPlan().isSimpleMode());
		assertEquals("Medical Unit Leader", data.getMedicalPlan().getPreparedByMedicalUnitLeader());
		assertEquals("Prepared signature", data.getMedicalPlan().getPreparerSignature());
		assertEquals(LocalDateTime.of(2026, 10, 24, 12, 0), data.getMedicalPlan().getPreparedAt());
		assertEquals("Not reviewed", data.getMedicalPlan().getReviewStatus());
		assertEquals("", data.getMedicalPlan().getReviewedBySafetyOfficer());
		assertEquals("", data.getMedicalPlan().getSafetyOfficerSignature());
		assertEquals("Incident aid station", data.getMedicalPlan().getAidStations().get(0).getName());
		assertEquals("Incident ambulance", data.getMedicalPlan().getTransportServices().get(0).getService());
		assertEquals("Incident hospital", data.getMedicalPlan().getHospitals().get(0).getName());
		assertTrue(data.getOperationalPeriodHistory().get(0).getCanineMedicalPlan().isActive());
		assertTrue(data.getCanineMedicalPlan().isActive());
		assertEquals("Canine aid station", data.getCanineMedicalPlan().getAidStationInformation());
		assertEquals("Canine ground transport", data.getCanineMedicalPlan().getTransportServices());
		assertEquals("Ambulance",
				data.getCanineMedicalPlan().getTransportServiceEntries().get(0).getService());
		assertEquals("Ambulance", data.getOperationalPeriodHistory().get(0).getCanineMedicalPlan()
				.getTransportServiceEntries().get(0).getService());
		assertFalse(data.getOperationalPeriodHistory().get(0).getCanineMedicalPlan()
				.getTransportServiceEntries().get(0) == data.getCanineMedicalPlan().getTransportServiceEntries().get(0));
		assertTrue(data.getCanineMedicalPlan().isAviationAssetsUsed());
		assertEquals("Medical Unit Leader", data.getCanineMedicalPlan().getPreparedByMedicalUnitLeader());
		assertEquals("Canine prepared signature", data.getCanineMedicalPlan().getPreparerSignature());
		assertEquals(LocalDateTime.of(2026, 10, 24, 12, 30), data.getCanineMedicalPlan().getPreparedAt());
		assertEquals("Not reviewed", data.getCanineMedicalPlan().getReviewStatus());
		assertEquals("", data.getCanineMedicalPlan().getReviewedBySafetyOfficer());
		assertEquals("", data.getCanineMedicalPlan().getSafetyOfficerSignature());
		assertEquals("Selected canine hospital",
				data.getCanineMedicalPlan().getSelectedFacilities().get(0).getName());
		assertEquals("555-0100",
				data.getCanineMedicalPlan().getSelectedFacilities().get(0).getContactNumbers().get(0));
		assertEquals("Emergency care",
				data.getCanineMedicalPlan().getSelectedFacilities().get(0).getCapabilities());
		assertEquals("Prior Safety Officer",
				data.getOperationalPeriodHistory().get(0).getMedicalPlan().getReviewedBySafetyOfficer());
		assertEquals("Prior review signature",
				data.getOperationalPeriodHistory().get(0).getMedicalPlan().getSafetyOfficerSignature());
		assertEquals("Reviewed", data.getOperationalPeriodHistory().get(0).getMedicalPlan().getReviewStatus());
		assertEquals("Prior Canine Safety Officer",
				data.getOperationalPeriodHistory().get(0).getCanineMedicalPlan().getReviewedBySafetyOfficer());
		assertEquals("Prior canine review signature",
				data.getOperationalPeriodHistory().get(0).getCanineMedicalPlan().getSafetyOfficerSignature());
		assertEquals("Reviewed",
				data.getOperationalPeriodHistory().get(0).getCanineMedicalPlan().getReviewStatus());
		assertNotSame(data.getOperationalPeriodHistory().get(0).getMedicalPlan(), data.getMedicalPlan());
		assertNotSame(data.getOperationalPeriodHistory().get(0).getMedicalPlan().getAidStations().get(0),
				data.getMedicalPlan().getAidStations().get(0));
		assertNotSame(data.getOperationalPeriodHistory().get(0).getCanineMedicalPlan()
				.getSelectedFacilities().get(0), data.getCanineMedicalPlan().getSelectedFacilities().get(0));
		assertNotSame(data.getOperationalPeriodHistory().get(0).getCanineMedicalPlan()
				.getSelectedFacilities().get(0).getContactNumbers(),
				data.getCanineMedicalPlan().getSelectedFacilities().get(0).getContactNumbers());
		data.getMedicalPlan().getAidStations().get(0).setName("Next period edit");
		data.getCanineMedicalPlan().getSelectedFacilities().get(0).setName("Next period hospital");
		assertEquals("Incident aid station",
				data.getOperationalPeriodHistory().get(0).getMedicalPlan().getAidStations().get(0).getName());
		assertEquals("Selected canine hospital", data.getOperationalPeriodHistory().get(0).getCanineMedicalPlan()
				.getSelectedFacilities().get(0).getName());
		assertEquals(1, data.getVetDirectory().getFacilities().size());
		assertEquals("Incident staging address", data.getStagingLocation().getAddress());
		assertEquals(1, data.getIncidentLocations().size());
		assertEquals(base.getId(), data.getCanineRoutingBaseLocationId());
	}
}
