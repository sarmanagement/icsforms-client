/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies application-wide event type settings and the task quick-log defaults. */
class ActivityEventTypeCatalogTest {
	@Test
	void customTypesPersistAcrossIncidentMergesAndQuickFlagsDefaultSafely() {
		List<ActivityEventType> previous = ActivityEventTypeCatalog.loadCustomTypes();
		ActivityEventType custom = new ActivityEventType("CANINE_CHECK", "Canine check", false);
		custom.setShowInQuickMenu(true);

		try {
			ActivityEventTypeCatalog.saveCustomTypes(List.of(custom));
			List<ActivityEventType> loaded = ActivityEventTypeCatalog.loadCustomTypes();
			assertEquals("Canine check", loaded.get(0).getLabel());
			assertTrue(loaded.get(0).isShowInQuickMenu());
			assertTrue(ActivityEventTypeCatalog.forIncident(List.of()).stream()
					.anyMatch(type -> "CANINE_CHECK".equals(type.getId()) && type.isShowInQuickMenu()));
			assertTrue(ActivityEventType.defaultTypes().stream()
					.filter(type -> ActivityEventType.ID_RESOURCE_DEPARTED_STAGING.equals(type.getId()))
					.findFirst().orElseThrow().isShowInQuickMenu());
			assertFalse(ActivityEventType.defaultTypes().stream()
					.filter(type -> ActivityEventType.ID_FREE_TEXT.equals(type.getId()))
					.findFirst().orElseThrow().isShowInQuickMenu());
		} finally {
			ActivityEventTypeCatalog.saveCustomTypes(previous);
		}
	}

	@Test
	void customTypeIdentifiersAndLabelsAreValidated() {
		assertThrows(IllegalArgumentException.class,
				() -> ActivityEventTypeCatalog.saveCustomTypes(List.of(new ActivityEventType("TYPE1", "Label", false))));
		assertThrows(IllegalArgumentException.class,
				() -> ActivityEventTypeCatalog.saveCustomTypes(List.of(new ActivityEventType("TYPE", "one two three four five six", false))));
		assertThrows(IllegalArgumentException.class, () -> ActivityEventTypeCatalog.saveCustomTypes(List.of(
				new ActivityEventType("TYPE", "First", false), new ActivityEventType("TYPE", "Second", false))));
	}
}
