/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests ICS 215 arithmetic, persistence, and explicit period promotion. */
class Ics215FormTest {
	private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

	@Test
	void needDistinguishesUnknownZeroAndSurplus() {
		Ics215Form.CountPair required = new Ics215Form.CountPair(2, 0);
		Ics215Form.CountPair have = new Ics215Form.CountPair(3, 0);

		Ics215Form.CountPair need = required.need(have);
		Ics215Form.CountPair unknown = new Ics215Form.CountPair().need(have);

		assertEquals(-1, need.singleResources);
		assertEquals(0, need.teams);
		assertNull(unknown.singleResources);
		assertEquals(0, Ics215Form.CountPair.needToOrder(required, have).singleResources);
		assertEquals(0, Ics215Form.CountPair.needToOrder(required, have).teams);
	}

	@Test
	void totalsUseConfirmedIncidentPoolInsteadOfRowAllocationSums() {
		Ics215Form form = new Ics215Form();
		Ics215Form.ResourceHeading heading = new Ics215Form.ResourceHeading();
		heading.acceptedOnHand = new Ics215Form.CountPair(7, 1);
		form.getResourceHeadings().add(heading);
		Ics215Form.WorkRow row = new Ics215Form.WorkRow();
		Ics215Form.ResourceAllocation allocation = new Ics215Form.ResourceAllocation();
		allocation.required = new Ics215Form.CountPair(5, 2);
		allocation.acceptedHave = new Ics215Form.CountPair(2, 1);
		row.resourceAllocations.put(heading.id, allocation);
		form.getWorkRows().add(row);

		assertEquals(5, form.totalRequired(heading.id).singleResources);
		assertEquals(7, form.totalOnHand(heading.id).singleResources);
		assertEquals(0, form.totalNeedToOrder(heading.id).singleResources);
		assertEquals(1, form.totalNeedToOrder(heading.id).teams);
		assertEquals(3, form.need(row.id, heading.id).singleResources);
	}

	@Test
	void validationRejectsNegativeCountsAndOverflowIsExplicit() {
		Ics215Form form = new Ics215Form();
		Ics215Form.ResourceHeading heading = new Ics215Form.ResourceHeading();
		form.getResourceHeadings().add(heading);
		Ics215Form.WorkRow row = new Ics215Form.WorkRow();
		Ics215Form.ResourceAllocation allocation = new Ics215Form.ResourceAllocation();
		allocation.required = new Ics215Form.CountPair(-1, 0);
		row.resourceAllocations.put(heading.id, allocation);
		form.getWorkRows().add(row);

		assertFalse(form.validate().isEmpty());
		assertThrows(IllegalArgumentException.class, form::validateOrThrow);
		assertThrows(ArithmeticException.class, () -> Ics215Form.CountPair.sum(List.of(
				new Ics215Form.CountPair(Integer.MAX_VALUE, 0),
				new Ics215Form.CountPair(1, 0))));
	}

	@Test
	void jsonRoundTripKeepsIndependentWorksheetContent() throws Exception {
		AppData source = new AppData();
		source.getForm215().setBranch("Current");
		source.getNextPeriodForm215().setBranch("Planned");
		source.getNextPeriodForm215().setTargetPeriodStart(LocalDateTime.of(2026, 11, 1, 6, 0));
		source.setIcs215ExportNextPeriod(true);

		byte[] json = mapper.writeValueAsBytes(source);
		AppData restored = mapper.readValue(json, AppData.class);

		assertEquals("Current", restored.getForm215().getBranch());
		assertEquals("Planned", restored.getNextPeriodForm215().getBranch());
		assertEquals(LocalDateTime.of(2026, 11, 1, 6, 0),
				restored.getNextPeriodForm215().getTargetPeriodStart());
		assertFalse(restored.isIcs215Enabled());
		assertFalse(restored.isIcs215ExportNextPeriod());
		assertFalse(new String(json, java.nio.charset.StandardCharsets.UTF_8).contains("ics215ExportNextPeriod"));
		assertEquals("", mapper.readValue("{\"schemaVersion\":15}", AppData.class)
				.getNextPeriodForm215().getBranch());
	}

	@Test
	void promotionArchivesOutgoingWorksheetsAndAppliesOnlyPlannedDates() {
		AppData data = new AppData();
		LocalDateTime currentStart = LocalDateTime.of(2026, 10, 31, 6, 0);
		LocalDateTime targetStart = LocalDateTime.of(2026, 10, 31, 18, 0);
		data.getIncidentContext().setOperationalPeriodStart(currentStart);
		data.getForm215().setBranch("Active period");
		data.getNextPeriodForm215().setBranch("Planned period");
		data.getNextPeriodForm215().setTargetPeriodStart(targetStart);
		data.getNextPeriodForm215().setTargetPeriodEnd(targetStart.plusHours(12));
		Ics215Form.ResourceHeading heading = new Ics215Form.ResourceHeading();
		heading.id = "heading";
		heading.linkedResourceIds.add("card-1");
		heading.acceptedAt = targetStart.minusHours(1);
		heading.acceptedOnHand = new Ics215Form.CountPair(1, 0);
		data.getNextPeriodForm215().getResourceHeadings().add(heading);

		data.advanceToNewOperationalPeriod(false, true);

		assertEquals(currentStart, data.getOperationalPeriodHistory().get(0).getPeriodStart());
		assertEquals("Active period", data.getOperationalPeriodHistory().get(0).getForm215().getBranch());
		assertEquals("Planned period", data.getForm215().getBranch());
		assertEquals(targetStart, data.getIncidentContext().getOperationalPeriodStart());
		assertEquals(1, data.getForm215().getResourceHeadings().get(0).acceptedOnHand.singleResources);
		assertTrue(data.getForm215().getResourceHeadings().get(0).linksRequireReview);
		assertTrue(data.getNextPeriodForm215().getWorkRows().isEmpty());
	}

	@Test
	void invalidPromotionLeavesIncidentPeriodAndHistoryUnchanged() {
		AppData data = new AppData();
		LocalDateTime currentStart = LocalDateTime.of(2026, 10, 31, 6, 0);
		data.getIncidentContext().setOperationalPeriodStart(currentStart);
		data.getNextPeriodForm215().setTargetPeriodStart(currentStart.plusHours(12));
		data.getNextPeriodForm215().setTargetPeriodEnd(currentStart);

		assertThrows(IllegalArgumentException.class, () -> data.advanceToNewOperationalPeriod(false, true));

		assertEquals(currentStart, data.getIncidentContext().getOperationalPeriodStart());
		assertTrue(data.getOperationalPeriodHistory().isEmpty());
		assertEquals(IapPhase.PRE_OP, data.getIapPhase());
	}

	@Test
	void planReuseClearsDatesEvidenceAndSignaturesUnlessRequiredIsExplicitlyKept() {
		AppData source = new AppData();
		Ics215Form planned = source.getNextPeriodForm215();
		planned.setTargetPeriodStart(LocalDateTime.of(2026, 11, 1, 6, 0));
		planned.setPreparerName("Prepared");
		planned.setPreparerSignature("signature");
		Ics215Form.ResourceHeading heading = new Ics215Form.ResourceHeading();
		heading.category = "Ground";
		heading.countingUnit = "resource";
		planned.getResourceHeadings().add(heading);
		Ics215Form.WorkRow row = new Ics215Form.WorkRow();
		row.assignment = "Search sector";
		row.specialInstructions = "Keep this instruction";
		Ics215Form.ResourceAllocation allocation = new Ics215Form.ResourceAllocation();
		allocation.required = new Ics215Form.CountPair(4, 1);
		allocation.acceptedHave = new Ics215Form.CountPair(2, 0);
		allocation.linkedResourceIds.add("card-1");
		allocation.acceptedAt = LocalDateTime.now();
		row.resourceAllocations.put(heading.id, allocation);
		planned.getWorkRows().add(row);
		AppData.OperationalPeriodRecord snapshot = PlanReuseService.snapshot(source);
		AppData target = new AppData();

		assertTrue(PlanReuseService.hasIcs215Plan(snapshot));
		PlanReuseService.apply(target, snapshot, java.util.Set.of(), false, true, false);

		Ics215Form reused = target.getNextPeriodForm215();
		assertNull(reused.getTargetPeriodStart());
		assertEquals("", reused.getPreparerName());
		assertEquals("", reused.getPreparerSignature());
		assertEquals("Keep this instruction", reused.getWorkRows().get(0).specialInstructions);
		assertNull(reused.getWorkRows().get(0).resourceAllocations.values().iterator().next().acceptedHave);
		assertTrue(reused.getWorkRows().get(0).resourceAllocations.values().iterator().next()
				.linkedResourceIds.isEmpty());
		assertNull(reused.getWorkRows().get(0).resourceAllocations.values().iterator().next()
				.required.singleResources);
		assertFalse(reused.getWorksheetId().equals(planned.getWorksheetId()));

		PlanReuseService.apply(target, snapshot, java.util.Set.of(), false, true, true);
		assertEquals(4, target.getNextPeriodForm215().getWorkRows().get(0)
				.resourceAllocations.values().iterator().next().required.singleResources);
	}
}
