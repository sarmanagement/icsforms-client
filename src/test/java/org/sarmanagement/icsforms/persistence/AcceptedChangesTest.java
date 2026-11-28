/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.Ics214Form;
import org.sarmanagement.icsforms.model.IncidentMode;
import org.sarmanagement.icsforms.model.TCard;

/** Covers receipt identity and individual activity-log attribution. */
class AcceptedChangesTest {
	@Test
	void retainsLatestReceiptPerPropertyAndIndividualLog() {
		AppData base = new AppData();
		Ics214Form firstLog = new Ics214Form();
		Ics214Form secondLog = new Ics214Form();
		base.getActivityLogs().add(firstLog);
		base.getActivityLogs().add(secondLog);
		AppData first = FullDocumentSnapshot.detach(base);
		first.getForm202().setCommandEmphasis("First");
		first.getActivityLogs().get(0).setName("Field team");
		AppData second = FullDocumentSnapshot.detach(first);
		second.getForm202().setCommandEmphasis("Second");
		second.getActivityLogs().get(1).setName("Command");
		Instant time = Instant.parse("2026-01-01T00:00:00Z");
		AcceptedChanges changes = AcceptedChanges.empty().withReceipt(operation("a", base, first), time)
				.withReceipt(operation("b", first, second), time.plusSeconds(1));
		assertEquals("b", changes.properties().get("form202").operationId());
		assertEquals("a", changes.logs().get(firstLog.getId()).operationId());
		assertEquals("b", changes.logs().get(secondLog.getId()).operationId());
		assertFalse(changes.properties().containsKey("incidentContext"));
	}

	@Test
	void separatesArchivedWorkspaceFields() {
		AppData base = new AppData();
		AppData.OperationalPeriodRecord period = new AppData.OperationalPeriodRecord();
		period.setForm202(new org.sarmanagement.icsforms.model.Ics202Form());
		base.getOperationalPeriodHistory().add(period);
		AppData edited = FullDocumentSnapshot.detach(base);
		edited.getOperationalPeriodHistory().get(0).getForm202().setCommandEmphasis("Safety");
		var changes = AcceptedChanges.empty().withReceipt(operation("archived", base, edited), Instant.now());
		assertEquals("archived", changes.history().get("form202").operationId());
		assertFalse(changes.history().containsKey("form205"));
	}

	@Test
	void derivedEligibilityIsNotAttributedAsAnIncidentEdit() {
		AppData base = new AppData();
		base.setIncidentMode(IncidentMode.GENERIC);
		AppData edited = FullDocumentSnapshot.detach(base);
		TCard card = new TCard();
		card.setCanineResource(true);
		edited.getTCards().add(card);
		assertFalse(base.isCanineMedicalWorkspaceEligible());
		assertTrue(edited.isCanineMedicalWorkspaceEligible());
		var changes = AcceptedChanges.empty().withReceipt(operation("card", base, edited), Instant.now());
		assertFalse(changes.properties().containsKey("canineMedicalWorkspaceEligible"));
		assertTrue(changes.properties().containsKey("tcards") || changes.properties().containsKey("tCards"));
	}

	/**
	 * Constructs an operation for acceptance attribution.
	 *
	 * @param id operation identity
	 * @param before prior document
	 * @param after edited document
	 * @return detached operation
	 */
	private static RemoteIncidentStore.PendingOperation operation(String id, AppData before, AppData after) {
		return new RemoteIncidentStore.PendingOperation(id, before, after, null,
				RemoteIncidentStore.PendingStatus.PENDING, List.of(), null);
	}
}
