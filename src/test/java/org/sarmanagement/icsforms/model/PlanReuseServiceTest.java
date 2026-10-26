/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Verifies selective copying, archive compatibility and explicit review boundaries. */
class PlanReuseServiceTest {
	@Test
	void emptyAndLegacyPeriodsDoNotOfferNonexistentPlans() {
		assertTrue(PlanReuseService.availablePlans(null).isEmpty());
		assertTrue(PlanReuseService.availablePlans(new AppData.OperationalPeriodRecord()).isEmpty());
		assertTrue(PlanReuseService.availablePlans(PlanReuseService.snapshot(new AppData())).isEmpty());
		AppData source = new AppData();
		source.getMedicalPlan().setPreparedByMedicalUnitLeader("Former leader");
		source.getMedicalPlan().setReviewStatus("Approved");
		source.getForm208().getSiteSafetyPlan().setPreparedBySignature("Signed");
		source.getForm208().getSiteSafetyPlan().setPreparedByName("Site preparer");
		source.getForm215a().setIncidentNumber("Source incident number");
		source.getForm215a().setSafetyOfficerName("Linked safety officer");
		source.getForm215a().setOperationsSectionChiefName("Linked operations chief");
		source.getForm215a().setSafetyOfficerSignature("Safety signature");
		source.getForm215a().setOperationsSectionChiefSignature("Operations signature");
		source.getForm205().setPreparedByName("Former COML");
		assertTrue(PlanReuseService.availablePlans(PlanReuseService.snapshot(source)).isEmpty());
		source.getForm215a().getEntries().add(new SafetyAnalysisEntry());
		source.getForm208().getHazardBlocks().add(new SafetyMessageBlock());
		source.getForm205().getChannels().add(new RadioChannel());
		source.getMedicalPlan().getAidStations().add(new MedicalPlan.AidStation());
		source.getMedicalPlan().getTransportServices().add(new MedicalPlan.TransportService());
		source.getMedicalPlan().getHospitals().add(new MedicalPlan.Hospital());
		assertTrue(PlanReuseService.availablePlans(PlanReuseService.snapshot(source)).isEmpty());
	}

	@Test
	void safetyArchivesPersistAndAreIndependentOfLaterPeriodEdits() throws Exception {
		AppData source = new AppData();
		source.getForm215a().getEntries().add(new SafetyAnalysisEntry());
		source.getForm208().setFreeText("Cold weather precautions");
		source.advanceToNewOperationalPeriod();
		var archived = source.getOperationalPeriodHistory().get(0);
		assertEquals(Set.of(PlanReviewArea.SAFETY), PlanReuseService.availablePlans(archived));
		source.getForm208().setFreeText("New period");
		source.getForm215a().getEntries().clear();
		assertEquals("Cold weather precautions", archived.getForm208().getFreeText());
		assertEquals(1, archived.getForm215a().getEntries().size());
		ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
		AppData restored = mapper.readValue(mapper.writeValueAsBytes(source), AppData.class);
		assertEquals("Cold weather precautions",
				restored.getOperationalPeriodHistory().get(0).getForm208().getFreeText());
		assertEquals(1, restored.getOperationalPeriodHistory().get(0).getForm215a().getEntries().size());
	}

	@Test
	void selectedCopyPreservesCurrentContextOtherPlansAndSourceApprovals() {
		AppData source = populatedSource();
		source.recordPlanReview(PlanReviewArea.HUMAN_MEDICAL, "Source reviewer", LocalDateTime.now());
		AppData target = new AppData();
		target.setIncidentId("current-incident");
		LocalDateTime period = LocalDateTime.of(2026, 10, 25, 10, 0);
		target.getIncidentContext().setOperationalPeriodStart(period);
		target.getIncidentContext().setIncidentName("Current incident");
		target.getMedicalUnitLeaderAssignment().setName("Current leader");
		target.getForm205().setSpecialInstructions("Keep current communications");
		target.getCanineMedicalPlan().setEmergencyProcedures("Keep current canine plan");
		var canine = target.getCanineMedicalPlan();
		PlanReuseService.apply(target, PlanReuseService.snapshot(source), Set.of(PlanReviewArea.HUMAN_MEDICAL), true);
		assertEquals("current-incident", target.getIncidentId());
		assertEquals(period, target.getIncidentContext().getOperationalPeriodStart());
		assertEquals("Current incident", target.getIncidentContext().getIncidentName());
		assertEquals("Current leader", target.getMedicalUnitLeaderAssignment().getName());
		assertEquals("Keep current communications", target.getForm205().getSpecialInstructions());
		assertSame(canine, target.getCanineMedicalPlan());
		assertEquals("Source procedures", target.getMedicalPlan().getEmergencyProcedures());
		assertEquals("Not reviewed", target.getMedicalPlan().getReviewStatus());
		assertEquals("", target.getMedicalPlan().getSafetyOfficerSignature());
		assertEquals("", target.getMedicalPlan().getPreparerSignature());
		assertNull(target.getMedicalPlan().getPreparedAt());
		assertEquals(PlanReviewStatus.NOT_YET_REVIEWED, target.getPlanReviewStatus(PlanReviewArea.HUMAN_MEDICAL));
		assertEquals(PlanReviewStatus.REVIEWED_APPROVED, source.getPlanReviewStatus(PlanReviewArea.HUMAN_MEDICAL));
		target.getMedicalPlan().getHospitals().get(0).setName("Edited copy");
		assertEquals("Source hospital", source.getMedicalPlan().getHospitals().get(0).getName());
	}

	@Test
	void allPlansClearApprovalsRadioChecksAndForeignResourceLinks() {
		AppData source = populatedSource();
		source.getForm215a().setSafetyOfficerSignature("Approved");
		source.getForm215a().setReviewedDateTime(LocalDateTime.now());
		source.getForm208().setPreparedBySignature("Signed");
		source.getForm208().getSiteSafetyPlan().setPreparedBySignature("Site signed");
		SafetyMessageBlock block = new SafetyMessageBlock();
		block.setHazard("Cold");
		source.getForm208().getHazardBlocks().add(block);
		source.getCanineMedicalPlan().setVeterinaryPersonnelResourceIds(List.of("source-resource"));
		VetFacility vet = new VetFacility();
		vet.setName("Source vet");
		vet.setDistance(1200, 120, "Road", "Source base", Instant.now(), 1);
		source.getCanineMedicalPlan().setSelectedFacilities(List.of(vet));
		AppData target = new AppData();
		for (PlanReviewArea area : PlanReviewArea.values()) {
			target.recordPlanReview(area, "Old reviewer", LocalDateTime.now());
		}
		PlanReuseService.apply(target, PlanReuseService.snapshot(source), EnumSet.allOf(PlanReviewArea.class), true);
		for (PlanReviewArea area : PlanReviewArea.values()) {
			assertEquals(PlanReviewStatus.NOT_YET_REVIEWED, target.getPlanReviewStatus(area));
		}
		assertEquals("", target.getForm215a().getSafetyOfficerSignature());
		assertNull(target.getForm215a().getReviewedDateTime());
		assertEquals("", target.getForm208().getPreparedBySignature());
		assertEquals("", target.getForm208().getSiteSafetyPlan().getPreparedBySignature());
		assertTrue(target.getForm208().getHazardBlocks().get(0).isNeedsReview());
		assertEquals("", target.getForm205().getPreparedBySignature());
		assertNull(target.getForm205().getPreparedDateTime());
		assertEquals(RadioCheckStatus.NOT_RECORDED,
				target.getForm205().getChannels().get(0).getRadioCheckStatus());
		assertTrue(target.getCanineMedicalPlan().getVeterinaryPersonnelResourceIds().isEmpty());
		assertNull(target.getCanineMedicalPlan().getSelectedFacilities().get(0).getCalculatedAt());
		assertEquals("Not reviewed", target.getCanineMedicalPlan().getReviewStatus());
		assertEquals(1200, source.getCanineMedicalPlan().getSelectedFacilities().get(0).getDistanceMeters());
		target.getForm208().getHazardBlocks().get(0).setHazard("Edited");
		assertEquals("Cold", source.getForm208().getHazardBlocks().get(0).getHazard());
		target.getForm205().getChannels().get(0).setChannelName("Edited");
		assertEquals("Source channel", source.getForm205().getChannels().get(0).getChannelName());
	}

	@Test
	void invalidSelectionDoesNotPartiallyReplacePlans() {
		AppData source = new AppData();
		source.getMedicalPlan().setEmergencyProcedures("Available");
		AppData target = new AppData();
		var original = target.getMedicalPlan();
		assertThrows(IllegalArgumentException.class, () -> PlanReuseService.apply(target,
				PlanReuseService.snapshot(source), Set.of(PlanReviewArea.HUMAN_MEDICAL, PlanReviewArea.SAFETY), false));
		assertSame(original, target.getMedicalPlan());
		assertThrows(IllegalArgumentException.class, () -> PlanReuseService.apply(target,
				PlanReuseService.snapshot(source), Set.of(), false));
	}

	@Test
	void crossIncidentHospitalImportsDiscardOriginSpecificTravelEstimatesOnly() {
		AppData source = new AppData();
		MedicalPlan.Hospital hospital = new MedicalPlan.Hospital();
		hospital.setName("Regional hospital");
		hospital.setAddress("Hospital address");
		hospital.setContactNumbers("555-0100");
		hospital.setLatitude(42.1);
		hospital.setLongitude(-71.2);
		hospital.setAirTravelTime("15 minutes");
		hospital.setGroundTravelTime("40 minutes");
		hospital.setAirTravelProvenance("Source incident helibase");
		hospital.setGroundTravelProvenance("Source incident ICP");
		source.getMedicalPlan().getHospitals().add(hospital);
		AppData target = new AppData();
		PlanReuseService.apply(target, PlanReuseService.snapshot(source), Set.of(PlanReviewArea.HUMAN_MEDICAL), true);
		MedicalPlan.Hospital imported = target.getMedicalPlan().getHospitals().get(0);
		assertEquals("", imported.getAirTravelTime());
		assertEquals("", imported.getGroundTravelTime());
		assertEquals("", imported.getAirTravelProvenance());
		assertEquals("", imported.getGroundTravelProvenance());
		assertEquals("Regional hospital", imported.getName());
		assertEquals("Hospital address", imported.getAddress());
		assertEquals("555-0100", imported.getContactNumbers());
		assertEquals(42.1, imported.getLatitude());
		assertEquals(-71.2, imported.getLongitude());
		assertEquals("15 minutes", hospital.getAirTravelTime());
		PlanReuseService.apply(target, PlanReuseService.snapshot(source), Set.of(PlanReviewArea.HUMAN_MEDICAL), false);
		assertEquals("15 minutes", target.getMedicalPlan().getHospitals().get(0).getAirTravelTime());
		assertEquals("Source incident ICP", target.getMedicalPlan().getHospitals().get(0).getGroundTravelProvenance());
	}

	/**
	 * Creates nonempty source plans containing preparation and review metadata.
	 *
	 * @return source incident for independent-copy assertions.
	 */
	private static AppData populatedSource() {
		AppData source = new AppData();
		source.getForm205().setSpecialInstructions("Source communications");
		source.getForm205().setPreparedBySignature("Source signature");
		source.getForm205().setPreparedDateTime(LocalDateTime.now());
		RadioChannel channel = new RadioChannel();
		channel.setChannelName("Source channel");
		channel.setRadioCheckStatus(RadioCheckStatus.SUCCESSFUL);
		source.getForm205().getChannels().add(channel);
		source.getForm208().setFreeText("Source safety");
		source.getMedicalPlan().setEmergencyProcedures("Source procedures");
		source.getMedicalPlan().setPreparerSignature("Source preparer");
		source.getMedicalPlan().setSafetyOfficerSignature("Source reviewer");
		source.getMedicalPlan().setReviewStatus("Approved");
		source.getMedicalPlan().setPreparedAt(LocalDateTime.now());
		MedicalPlan.Hospital hospital = new MedicalPlan.Hospital();
		hospital.setName("Source hospital");
		source.getMedicalPlan().getHospitals().add(hospital);
		source.getCanineMedicalPlan().setEmergencyProcedures("Source canine procedures");
		source.getCanineMedicalPlan().setReviewStatus("Approved");
		return source;
	}
}
