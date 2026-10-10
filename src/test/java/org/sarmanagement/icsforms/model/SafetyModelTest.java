/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests safety-form defaults and backward-compatible JSON persistence.
 */
class SafetyModelTest {
	private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule())
			.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

	@Test
	void oldIncidentJsonDefaultsNewSafetyFormsAndLibrary() throws Exception {
		AppData data = mapper.readValue("{\"incidentContext\":{\"incidentName\":\"Old incident\"}}", AppData.class);

		assertEquals("Old incident", data.getIncidentContext().getIncidentName());
		assertTrue(data.getForm215a().getEntries().isEmpty());
		assertTrue(data.getForm208().getHazardBlocks().isEmpty());
		assertTrue(data.getCommonSafetyHazards().isEmpty());
		assertNull(data.getForm208().getSiteSafetyPlanRequired());
	}

	@Test
	void safetyFormsRoundTripIsoDateMessageModeAndCommonItems() throws Exception {
		AppData data = new AppData();
		data.getForm215a().setPreparedDateTime(LocalDateTime.of(2026, 10, 10, 7, 30));
		data.getForm208().setAuthorRole(Ics208Form.AuthorRole.HAZARDOUS_MATERIALS_GROUP_SUPERVISOR);
		data.getForm208().setMessageType(Ics208Form.MessageType.HAZARDOUS_MATERIALS_SITE_SAFETY_PLAN);
		SafetyAnalysisEntry common = new SafetyAnalysisEntry();
		common.setHazardsRisks("Chemical exposure");
		data.getCommonSafetyHazards().add(common);

		AppData loaded = mapper.readValue(mapper.writeValueAsBytes(data), AppData.class);

		assertEquals(LocalDateTime.of(2026, 10, 10, 7, 30), loaded.getForm215a().getPreparedDateTime());
		assertSame(Ics208Form.AuthorRole.HAZARDOUS_MATERIALS_GROUP_SUPERVISOR,
				loaded.getForm208().getAuthorRole());
		assertEquals(Ics208Form.MessageType.HAZARDOUS_MATERIALS_SITE_SAFETY_PLAN,
				loaded.getForm208().getMessageType());
		assertEquals("Chemical exposure", loaded.getCommonSafetyHazards().get(0).getHazardsRisks());
	}

	@Test
	void nullCollectionsNormalizeToEmptyLists() {
		Ics215aForm analysis = new Ics215aForm();
		analysis.setEntries(null);
		Ics208Form message = new Ics208Form();
		message.setHazardBlocks(null);

		assertEquals(List.of(), analysis.getEntries());
		assertEquals(List.of(), message.getHazardBlocks());
	}
}
