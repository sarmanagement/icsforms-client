/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Covers backward-compatible clue strike-out serialization. */
class ClueLogEntrySerializationTest {
	private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

	@Test
	void omittedAndExplicitFalseStrikeOutLoadAndSerializeAsLegacy() throws Exception {
		for (String json : new String[] {"{}", "{\"struckOut\":false}"}) {
			ClueLogEntry clue = mapper.readValue(json, ClueLogEntry.class);
			assertFalse(clue.isStruckOut());
			assertFalse(mapper.valueToTree(clue).has("struckOut"));
		}
	}

	@Test
	void confirmedDuplicateRetainsStrikeOutAndConfirmationOnRoundTrip() throws Exception {
		ClueLogEntry clue = new ClueLogEntry();
		Instant confirmedAt = Instant.parse("2026-11-07T10:05:30Z");
		clue.confirmDuplicate("other-clue", confirmedAt, null);

		var json = mapper.valueToTree(clue);
		assertTrue(json.path("struckOut").asBoolean());
		ClueLogEntry restored = mapper.treeToValue(json, ClueLogEntry.class);
		assertTrue(restored.isStruckOut());
		assertEquals("other-clue", restored.getDuplicateConfirmation().duplicateOfId());
		assertEquals(confirmedAt, restored.getDuplicateConfirmation().confirmedAt());
		restored.setStruckOut(false);
		assertFalse(mapper.valueToTree(restored).has("struckOut"));
	}
}
