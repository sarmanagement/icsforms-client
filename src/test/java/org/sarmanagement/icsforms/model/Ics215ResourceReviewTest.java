/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests explicit, unique and non-mutating T-card assistance for ICS 215. */
class Ics215ResourceReviewTest {
	@Test
	void inspectDoesNotCreateLegacyIdsAndFlagsAmbiguousAndCrewCards() {
		AppData data = new AppData();
		TCard legacy = card(null, TCardType.PERSONNEL, 0);
		TCard first = card("duplicate", TCardType.PERSONNEL, 0);
		TCard second = card("duplicate", TCardType.PERSONNEL, 0);
		TCard crew = card("crew", TCardType.CREW, 6);
		data.getTCards().addAll(List.of(legacy, first, second, crew));

		List<Ics215ResourceReview.Candidate> candidates = Ics215ResourceReview.inspect(data);

		assertNull(legacy.peekResourceId());
		assertTrue(candidates.get(0).missingIdentity());
		assertTrue(candidates.get(1).ambiguousIdentity());
		assertTrue(candidates.get(2).ambiguousIdentity());
		assertTrue(candidates.get(3).crewMemberOverlapNeedsReview());
	}

	@Test
	void acceptedSnapshotsCountExplicitUnitsAndKeepManualCountsSeparate() {
		AppData data = new AppData();
		TCard card = card("unit-1", TCardType.CREW, 0);
		data.getTCards().add(card);
		Ics215Form form = new Ics215Form();
		Ics215Form.ResourceHeading heading = new Ics215Form.ResourceHeading();
		form.getResourceHeadings().add(heading);
		Ics215Form.WorkRow row = new Ics215Form.WorkRow();
		form.getWorkRows().add(row);
		Ics215Form.ResourceLink team = link("unit-1", Ics215Form.ResourceUnit.STRIKE_TEAM_OR_TASK_FORCE);
		LocalDateTime acceptedAt = LocalDateTime.of(2026, 10, 31, 12, 0);

		Ics215ResourceReview.acceptOnHand(data, form, heading.id, List.of(team),
				new Ics215Form.CountPair(1, 0), acceptedAt);
		Ics215Form.CountPair accepted = Ics215ResourceReview.acceptRowHave(data, form, row.id, heading.id,
				List.of(team), new Ics215Form.CountPair(0, 0), acceptedAt);

		assertEquals(1, form.totalOnHand(heading.id).singleResources);
		assertEquals(1, form.totalOnHand(heading.id).teams);
		assertEquals(0, accepted.singleResources);
		assertEquals(1, accepted.teams);
		assertEquals(List.of("unit-1"), form.allocation(row.id, heading.id).linkedResourceIds);
		assertEquals(acceptedAt, form.allocation(row.id, heading.id).acceptedAt);
		assertFalse(form.allocation(row.id, heading.id).linksRequireReview);
	}

	@Test
	void duplicateRowAllocationsAndCrossHeadingPoolDoubleCountsAreRejected() {
		AppData data = new AppData();
		data.getTCards().add(card("resource", TCardType.PERSONNEL, 0));
		Ics215Form form = new Ics215Form();
		Ics215Form.ResourceHeading firstHeading = new Ics215Form.ResourceHeading();
		Ics215Form.ResourceHeading secondHeading = new Ics215Form.ResourceHeading();
		form.getResourceHeadings().addAll(List.of(firstHeading, secondHeading));
		Ics215Form.WorkRow firstRow = new Ics215Form.WorkRow();
		Ics215Form.WorkRow secondRow = new Ics215Form.WorkRow();
		form.getWorkRows().addAll(List.of(firstRow, secondRow));
		Ics215Form.ResourceLink candidate = link("resource", Ics215Form.ResourceUnit.SINGLE_RESOURCE);
		Ics215Form.CountPair noManual = new Ics215Form.CountPair(0, 0);

		Ics215ResourceReview.acceptRowHave(data, form, firstRow.id, firstHeading.id, List.of(candidate),
				noManual, LocalDateTime.now());
		assertThrows(IllegalArgumentException.class, () -> Ics215ResourceReview.acceptRowHave(data, form,
				secondRow.id, firstHeading.id, List.of(candidate), noManual, LocalDateTime.now()));
		Ics215ResourceReview.acceptOnHand(data, form, firstHeading.id, List.of(candidate), noManual,
				LocalDateTime.now());
		assertThrows(IllegalArgumentException.class, () -> Ics215ResourceReview.acceptOnHand(data, form,
				secondHeading.id, List.of(candidate), noManual, LocalDateTime.now()));
	}

	@Test
	void reviewReportsStaleCardsWithoutChangingAcceptedSnapshots() {
		AppData data = new AppData();
		TCard card = card("resource", TCardType.PERSONNEL, 0);
		card.setStatus("Available");
		card.setLocation("ICP");
		data.getTCards().add(card);
		Ics215Form form = new Ics215Form();
		Ics215Form.ResourceHeading heading = new Ics215Form.ResourceHeading();
		form.getResourceHeadings().add(heading);
		Ics215Form.WorkRow row = new Ics215Form.WorkRow();
		form.getWorkRows().add(row);
		Ics215ResourceReview.acceptRowHave(data, form, row.id, heading.id,
				List.of(link("resource", Ics215Form.ResourceUnit.SINGLE_RESOURCE)),
				new Ics215Form.CountPair(0, 0), LocalDateTime.now());
		Ics215Form.CountPair accepted = form.allocation(row.id, heading.id).acceptedHave;
		card.setStatus("Assigned");
		card.setLocation("Division A");

		Ics215ResourceReview.RefreshResult review = Ics215ResourceReview.reviewRowHave(
				data, form, row.id, heading.id);

		assertEquals(accepted, form.allocation(row.id, heading.id).acceptedHave);
		assertTrue(review.discrepancies().stream().anyMatch(value -> value.contains("changed")));
		assertTrue(review.discrepancies().stream().anyMatch(value -> value.contains("dated snapshots")));
	}

	@Test
	void headerCardsAndCrewOverlapCannotBeCountedAsLinkedResources() {
		AppData data = new AppData();
		TCard header = card("header", TCardType.HEADER, 0);
		TCard crew = card("crew", TCardType.CREW, 5);
		data.getTCards().addAll(List.of(header, crew));
		Ics215Form form = new Ics215Form();
		Ics215Form.ResourceHeading heading = new Ics215Form.ResourceHeading();
		form.getResourceHeadings().add(heading);
		Ics215Form.WorkRow row = new Ics215Form.WorkRow();
		form.getWorkRows().add(row);

		assertThrows(IllegalArgumentException.class, () -> Ics215ResourceReview.acceptOnHand(data, form,
				heading.id, List.of(link("header", Ics215Form.ResourceUnit.SINGLE_RESOURCE)),
				new Ics215Form.CountPair(0, 0), LocalDateTime.now()));
		assertThrows(IllegalArgumentException.class, () -> Ics215ResourceReview.acceptRowHave(data, form,
				row.id, heading.id, List.of(link("crew", Ics215Form.ResourceUnit.STRIKE_TEAM_OR_TASK_FORCE)),
				new Ics215Form.CountPair(0, 0), LocalDateTime.now()));
		assertFalse(form.validate().isEmpty() == false);
	}

	/**
	 * Creates a T-card with a deliberate stable identity.
	 *
	 * @param id stable ID, or null to leave a legacy card unidentified.
	 * @param type card discriminator.
	 * @param persons number of represented persons.
	 * @return configured card.
	 */
	private TCard card(String id, TCardType type, int persons) {
		TCard card = new TCard();
		card.setCardType(type);
		if (id != null) {
			card.setResourceId(id);
		}
		card.setResourceIdentifier(id == null ? "Legacy card" : id);
		card.setNumberOfPersons(persons);
		return card;
	}

	/**
	 * Creates an explicitly classified resource identity.
	 *
	 * @param id stable card ID.
	 * @param unit operator-selected unit.
	 * @return link candidate.
	 */
	private Ics215Form.ResourceLink link(String id, Ics215Form.ResourceUnit unit) {
		Ics215Form.ResourceLink link = new Ics215Form.ResourceLink();
		link.resourceId = id;
		link.unit = unit;
		return link;
	}
}
