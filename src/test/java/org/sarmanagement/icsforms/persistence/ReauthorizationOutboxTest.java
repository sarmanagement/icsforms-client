/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.ClueLogEntry;
import org.sarmanagement.icsforms.model.RecordIndex;

import java.util.ArrayList;
import java.util.List;

/** Covers bounded outbox descriptions sent to the host administrator. */
class ReauthorizationOutboxTest {
	@Test
	void largeOutboxDegradesDetailButAlwaysKeepsEveryOperationIdentifier() {
		List<RemoteIncidentStore.PendingOperation> operations = new ArrayList<>();
		for (int index = 0; index < 30; index++) {
			operations.add(operation("op-" + index, "Clue " + index + " " + "x".repeat(2_000)));
		}
		ArrayNode full = ReauthorizationOutbox.describe(operations);
		assertTrue(full.get(0).path("records").get(0).has("content"));

		ArrayNode identities = ReauthorizationOutbox.describe(operations, 30_000);
		assertFalse(identities.get(0).path("records").get(0).has("content"));
		assertEquals("CREATE", identities.get(0).path("records").get(0).path("operation").asText());

		ArrayNode identifiers = ReauthorizationOutbox.describe(operations, 1_000);
		assertFalse(identifiers.get(0).has("records"));
		assertEquals(30, identifiers.size());
		for (int index = 0; index < 30; index++) {
			JsonNode item = identifiers.get(index);
			assertEquals("op-" + index, item.path("operationId").asText());
			assertEquals(1, item.path("recordCount").asInt());
		}
	}

	/**
	 * Builds an edit that creates one clue and has no prepared batch yet.
	 *
	 * @param id operation identifier
	 * @param description clue text
	 * @return pending operation
	 */
	private static RemoteIncidentStore.PendingOperation operation(String id, String description) {
		AppData before = new AppData();
		AppData draft = FullDocumentSnapshot.detach(before);
		ClueLogEntry clue = new ClueLogEntry();
		clue.setDescription(description);
		draft.getClueLogEntries().add(clue);
		assertEquals(1, RecordIndex.of(draft).records().keySet().stream()
				.filter(key -> key.type().equals(ClueLogEntry.class.getName())).count());
		return new RemoteIncidentStore.PendingOperation(id, before, draft, null,
				RemoteIncidentStore.PendingStatus.PENDING, List.of(), null);
	}
}
