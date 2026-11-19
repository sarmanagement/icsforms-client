/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Covers copy identity and collection isolation for veterinary facilities. */
class VetFacilityTest {
	@Test
	void copyRetainsDirectoryIdentityAndClonesContactNumbers() {
		VetFacility source = new VetFacility();
		source.setContactNumbers(new ArrayList<>(List.of("555-0101")));

		VetFacility copy = source.copy();

		assertEquals(source.getId(), copy.getId());
		assertNotSame(source.getContactNumbers(), copy.getContactNumbers());
		copy.getContactNumbers().add("555-0102");
		assertEquals(List.of("555-0101"), source.getContactNumbers());
	}
}
