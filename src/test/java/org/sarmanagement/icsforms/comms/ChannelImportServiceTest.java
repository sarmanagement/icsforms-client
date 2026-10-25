/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.comms;

import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.model.Ics205Form;
import org.sarmanagement.icsforms.model.RadioChannel;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Tests transaction isolation, selection and explicit identity-only operations. */
class ChannelImportServiceTest {
	private final ChannelImportService service = new ChannelImportService();

	@Test
	void appendsSelectedRowsReassignsCollisionsAndNeverMutatesSources() {
		Ics205Form target = plan("Same name");
		Ics205Form imported = target.deepCopy();
		imported.getChannels().get(0).setProgrammingMetadata(Map.of("raw", "023"));
		imported.getChannels().add(new RadioChannel());
		Ics205Form result = service.merge(target, imported, List.of(0), ChannelImportService.Mode.APPEND);
		assertEquals(2, result.getChannels().size());
		assertNotEquals(result.getChannels().get(0).getChannelId(), result.getChannels().get(1).getChannelId());
		assertEquals(target.getChannels().get(0).getChannelId(), imported.getChannels().get(0).getChannelId());
		result.getChannels().get(1).getProgrammingMetadata().put("raw", "changed");
		assertEquals("023", imported.getChannels().get(0).getProgrammingMetadata().get("raw"));
		assertEquals(1, target.getChannels().size());
	}

	@Test
	void replacesOnlyChannelInventoryAndKeepsPlanMetadata() {
		Ics205Form target = plan("Existing");
		target.setSpecialInstructions("Local instructions");
		Ics205Form result = service.merge(target, plan("Incoming"), List.of(0), ChannelImportService.Mode.REPLACE);
		assertEquals("Incoming", result.getChannels().get(0).getChannelName());
		assertEquals("Local instructions", result.getSpecialInstructions());
		assertEquals(target.getFormId(), result.getFormId());
		assertEquals(0, service.merge(target, plan("Unused"), List.of(), ChannelImportService.Mode.REPLACE).getChannels().size());
	}

	@Test
	void updatesOnlyExplicitIdsAndRejectsUnknownMatchingNamesAtomically() {
		Ics205Form target = plan("Same");
		Ics205Form imported = target.deepCopy();
		imported.getChannels().get(0).setRxFrequency("155.123456");
		Ics205Form result = service.merge(target, imported, List.of(0), ChannelImportService.Mode.UPDATE_BY_ID);
		assertEquals("155.123456", result.getChannels().get(0).getRxFrequency());
		assertEquals("", target.getChannels().get(0).getRxFrequency());
		imported.getChannels().add(plan("Same").getChannels().get(0));
		assertThrows(IllegalArgumentException.class,
				() -> service.merge(target, imported, List.of(0, 1), ChannelImportService.Mode.UPDATE_BY_ID));
		assertEquals("", target.getChannels().get(0).getRxFrequency());
	}

	@Test
	void rejectsInvalidSelectionsAndAmbiguousExplicitIds() {
		Ics205Form target = plan("Existing");
		Ics205Form imported = plan("Incoming");
		for (List<Integer> selection : List.of(List.of(-1), List.of(1), List.of(0, 0))) {
			assertThrows(IllegalArgumentException.class,
					() -> service.merge(target, imported, selection, ChannelImportService.Mode.APPEND));
		}
		imported.getChannels().add(imported.getChannels().get(0).deepCopy());
		assertThrows(IllegalArgumentException.class,
				() -> service.merge(target, imported, List.of(0), ChannelImportService.Mode.APPEND));
	}

	/**
	 * Creates a one-channel source plan.
	 * @param name channel name.
	 * @return test plan.
	 */
	private static Ics205Form plan(String name) {
		Ics205Form form = new Ics205Form();
		RadioChannel channel = new RadioChannel();
		channel.setChannelName(name);
		form.setChannels(List.of(channel));
		return form;
	}
}
