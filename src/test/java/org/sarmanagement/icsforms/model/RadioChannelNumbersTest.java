/*
 * Copyright (C) 2026 SAR Management
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or any later version.
 * This program is distributed WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See <https://www.gnu.org/licenses/>.
 */

package org.sarmanagement.icsforms.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.validation.IncidentValidator;

class RadioChannelNumbersTest {
	private static RadioChannel channel(String zone, String number) {
		RadioChannel channel = new RadioChannel();
		channel.setZoneGroup(zone);
		channel.setChannelNumber(number);
		return channel;
	}

	@Test
	void sameNumberConflictsOnlyWithinTheSameZoneAndNeverWithItself() {
		RadioChannel mine = channel("A", "3");
		RadioChannel theirs = channel("a", " 3 ");
		RadioChannel otherZone = channel("B", "3");
		List<RadioChannel> all = List.of(mine, theirs, otherZone);

		assertEquals(theirs, RadioChannelNumbers.findConflict(all, mine, "A", "3"));
		assertNull(RadioChannelNumbers.findConflict(List.of(mine, otherZone), mine, "A", "3"));
		assertNull(RadioChannelNumbers.findConflict(all, mine, "A", ""));
	}

	@Test
	void suggestionKeepsThePatternAndSkipsUsedNumbers() {
		RadioChannel mine = channel("", "CH3");
		List<RadioChannel> all = List.of(mine, channel("", "CH3"), channel("", "CH4"));

		assertEquals("CH5", RadioChannelNumbers.nextFree(all, mine, "", "CH3"));
		assertEquals("Cmd-2", RadioChannelNumbers.nextFree(List.of(channel("", "Cmd")), null, "", "Cmd"));
	}

	@Test
	void repeatedNumbersAreListedAndValidated() {
		AppData data = new AppData();
		data.getForm205().getChannels().add(channel("A", "7"));
		data.getForm205().getChannels().add(channel("A", "7"));

		assertEquals(List.of("A / 7"), RadioChannelNumbers.repeated(data.getForm205().getChannels()));
		assertTrue(new IncidentValidator().validate(data).stream()
				.anyMatch(message -> message.field().equals("ics205.channels")));
	}
}
