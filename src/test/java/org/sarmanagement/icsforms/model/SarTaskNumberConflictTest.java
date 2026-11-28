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

import java.util.List;
import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.validation.IncidentValidator;

class SarTaskNumberConflictTest {
	private static SarTaskAssignment task(String id, String number) {
		SarTaskAssignment task = new SarTaskAssignment();
		task.setAssignmentId(id);
		task.setAssignmentTeamNumber(number);
		return task;
	}

	@Test
	void sameNumberOnAnotherDeviceIsFoundAndNeverConflictsWithItself() {
		SarTaskAssignment mine = task("a", "Team 7");
		SarTaskAssignment theirs = task("b", " team 7 ");
		List<SarTaskAssignment> all = List.of(mine, theirs);

		assertEquals(theirs, SarTaskSupport.findNumberConflict(all, mine, "Team 7"));
		assertNull(SarTaskSupport.findNumberConflict(all, mine, "Team 8"));
		assertNull(SarTaskSupport.findNumberConflict(List.of(mine), task("a", "x"), "Team 7"));
	}

	@Test
	void suggestionSkipsNumbersAlreadyInUse() {
		SarTaskAssignment mine = task("a", "7");
		List<SarTaskAssignment> all = List.of(mine, task("b", "7"), task("c", "8"));

		assertEquals("9", SarTaskSupport.nextFreeNumber(all, mine, "7"));
		assertEquals("Alpha-2", SarTaskSupport.nextFreeNumber(List.of(task("d", "Alpha")), null, "Alpha"));
	}

	@Test
	void validatorFlagsTheLaterOfTwoTasksSharingANumber() {
		AppData data = new AppData();
		data.getSarTaskAssignments().add(task("a", "7"));
		data.getSarTaskAssignments().add(task("b", "7"));

		boolean flagged = new IncidentValidator().validate(data).stream()
				.anyMatch(m -> m.field().equals("sarTaskAssignments[1].assignmentTeamNumber")
						&& m.message().contains("more than one"));
		assertEquals(true, flagged);
	}
}
