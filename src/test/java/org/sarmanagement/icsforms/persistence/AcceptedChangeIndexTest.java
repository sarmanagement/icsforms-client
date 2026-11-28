/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.Ics214Form;

/** Covers receipt attribution for root fields, activity logs, and archived periods. */
class AcceptedChangeIndexTest {
	@Test
	void indexesChangesByStableLogIdentityAndArchivedField() {
		AppData before = new AppData();
		Ics214Form first = new Ics214Form();
		Ics214Form second = new Ics214Form();
		before.getActivityLogs().add(first);
		before.getActivityLogs().add(second);
		AppData.OperationalPeriodRecord period = new AppData.OperationalPeriodRecord();
		before.getOperationalPeriodHistory().add(period);
		AppData after = FullDocumentSnapshot.detach(before);
		after.getActivityLogs().get(1).setName("Field team");
		after.getOperationalPeriodHistory().get(0).setPeriodStart(java.time.LocalDateTime.of(2026, 1, 2, 3, 4));
		var receipt = new RemoteIncidentStore.AcceptedReceipt("operation", 12, Instant.parse("2026-01-02T03:04:05Z"));
		var changes = AcceptedChangeIndex.update(Map.of(), before, after, receipt);
		assertEquals(receipt, changes.get("log/" + second.getId()));
		assertFalse(changes.containsKey("log/" + first.getId()));
		assertEquals(receipt, changes.get("history/periodStart"));
		assertFalse(changes.containsKey("history/form202"));
		assertEquals(changes, AcceptedChangeIndex.update(changes, after, after, receipt));
	}

	@Test
	void indexesAddedAndRemovedPeriodsAndLogs() {
		AppData before = new AppData();
		AppData after = FullDocumentSnapshot.detach(before);
		Ics214Form log = new Ics214Form();
		after.getActivityLogs().add(log);
		after.getOperationalPeriodHistory().add(new AppData.OperationalPeriodRecord());
		var receipt = new RemoteIncidentStore.AcceptedReceipt("operation", 1, Instant.now());
		var added = AcceptedChangeIndex.update(Map.of(), before, after, receipt);
		assertTrue(added.containsKey("log/" + log.getId()));
		assertTrue(added.containsKey("history/periodStart"));
		var removed = AcceptedChangeIndex.update(Map.of(), after, before, receipt);
		assertTrue(removed.containsKey("log/" + log.getId()));
		assertTrue(removed.containsKey("history/periodStart"));
	}
}
