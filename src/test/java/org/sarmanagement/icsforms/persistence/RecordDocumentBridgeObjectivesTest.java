/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.Ics202Form;
import org.sarmanagement.icsforms.model.IncidentObjective;
import org.sarmanagement.icsforms.model.RecordIndex.RecordKey;
import org.sarmanagement.icsforms.persistence.RecordVersionStore.RecordHistory;
import org.sarmanagement.icsforms.persistence.RecordVersionStore.RecordVersion;

/** Covers projection of ICS 202 objectives from record histories. */
class RecordDocumentBridgeObjectivesTest {
	private static final ObjectMapper MAPPER = new ObjectMapper();

	@Test
	void legacyObjectiveTextDoesNotInventObjectivesWithRandomIdentities() throws Exception {
		AppData data = new AppData();
		data.setForm202(null);
		RecordKey formKey = new RecordKey(Ics202Form.class.getName(), "form-1");
		RecordKey objectiveKey = new RecordKey(IncidentObjective.class.getName(), "objective-1");
		JsonNode form = MAPPER.readTree("{\"objectives\":[\"Ma\"],\"structuredObjectives\":[\"objective-1\"]}");
		JsonNode objective = MAPPER.readTree("{\"text\":\"Ma\",\"displayOrder\":0}");
		List<RecordHistory> histories = List.of(
				new RecordHistory(formKey, null, "form202",
						List.of(new RecordVersion(1, 1, Instant.now(), form))),
				new RecordHistory(objectiveKey, formKey, "structuredObjectives",
						List.of(new RecordVersion(1, 2, Instant.now(), objective))));
		new RecordDocumentBridge().apply(data, histories, true);
		List<IncidentObjective> objectives = data.getForm202().getStructuredObjectives();
		assertEquals(1, objectives.size());
		assertEquals("objective-1", objectives.get(0).getObjectiveId());
		assertEquals("Ma", objectives.get(0).getText());
	}
}
