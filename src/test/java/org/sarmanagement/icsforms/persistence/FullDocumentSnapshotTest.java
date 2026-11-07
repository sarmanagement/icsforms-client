/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.persistence;

import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.TCard;
import org.sarmanagement.icsforms.model.SarTaskAssignment;
import org.sarmanagement.icsforms.model.ClueLogEntry;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Checks detached ownership, legacy fields and large-incident capture cost. */
class FullDocumentSnapshotTest {
	@Test
	void copyPreservesLegacyContentWithoutInvokingLazySourceGetters() throws Exception {
		AppData live = new AppData();
		TCard card = new TCard();
		card.setPersonName("Legacy resource");
		live.getTCards().add(card);
		var resourceId = TCard.class.getDeclaredField("resourceId");
		resourceId.setAccessible(true);
		assertNull(resourceId.get(card));
		AppData detached = FullDocumentSnapshot.detach(live);
		assertNull(resourceId.get(card), "Capturing must not initialize legacy source identities");
		assertNotSame(live.getTCards(), detached.getTCards());
		assertNotSame(card, detached.getTCards().get(0));
		detached.getTCards().get(0).setPersonName("Detached edit");
		assertEquals("Legacy resource", card.getPersonName());
		assertEquals(live.getSchemaVersion(), detached.getSchemaVersion());
	}

	@Test
	void largeFixtureCapturesAllRecordsAndReportsMedianOwnerThreadCost() throws Exception {
		AppData live = new AppData();
		for (int index = 0; index < 2000; index++) {
			TCard card = new TCard();
			card.setPersonName("Resource " + index);
			live.getTCards().add(card);
		}
		for (int index = 0; index < 500; index++) {
			live.getSarTaskAssignments().add(new SarTaskAssignment());
			live.getClueLogEntries().add(new ClueLogEntry());
		}
		for (int index = 0; index < 10; index++) {
			AppData.OperationalPeriodRecord period = new AppData.OperationalPeriodRecord();
			period.setSarTaskAssignments(List.of(new SarTaskAssignment()));
			live.getOperationalPeriodHistory().add(period);
		}
		long[] samples = new long[7];
		AtomicReference<AppData> captured = new AtomicReference<>();
		SwingUtilities.invokeAndWait(() -> {
			FullDocumentSnapshot.detach(live);
			for (int sample = 0; sample < samples.length; sample++) {
				long started = System.nanoTime();
				captured.set(FullDocumentSnapshot.detach(live));
				samples[sample] = System.nanoTime() - started;
			}
		});
		AppData detached = captured.get();
		Arrays.sort(samples);
		System.out.printf("Detached large incident (2000 resources, 500 tasks, 500 clues, 10 periods): median %.2f ms%n",
				samples[samples.length / 2] / 1_000_000.0);
		assertEquals(2000, detached.getTCards().size());
		assertEquals(500, detached.getSarTaskAssignments().size());
		assertEquals(500, detached.getClueLogEntries().size());
		assertEquals(10, detached.getOperationalPeriodHistory().size());
		assertNotSame(live.getOperationalPeriodHistory().get(0), detached.getOperationalPeriodHistory().get(0));
	}
}
