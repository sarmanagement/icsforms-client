/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.safety;

import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.model.SafetyAnalysisEntry;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests CSV library round trips and malformed input handling.
 */
class SafetyHazardCsvServiceTest {
	@Test
	void parsesQuotedMultilineHazardsAndMitigations() {
		SafetyHazardCsvService service = new SafetyHazardCsvService();
		String csv = "\uFEFFhazards_risks,mitigations,incident_area,equipment_ppe\r\n"
				+ "\"Terrain, Dense Brush, Trip Hazards\",\"Use caution,\r\nslow down\",Division A,\"Compass\r\nWater\"";

		SafetyAnalysisEntry entry = service.importCsv(csv).get(0);

		assertEquals("Terrain, Dense Brush, Trip Hazards", entry.getHazardsRisks());
		assertEquals("Use caution,\r\nslow down", entry.getMitigations());
		assertEquals("Division A", entry.getIncidentArea());
		assertEquals("Compass\r\nWater", entry.getEquipmentPpe());
	}

	@Test
	void exportsAndReimportsQuotesAndNewlines() {
		SafetyHazardCsvService service = new SafetyHazardCsvService();
		SafetyAnalysisEntry entry = new SafetyAnalysisEntry();
		entry.setHazardsRisks("Water, rocks");
		entry.setMitigations("Line one\nSay \"watch out\"");
		entry.setIncidentArea("All");
		List<SafetyAnalysisEntry> imported = service.importCsv(service.exportCsv(List.of(entry)));

		assertEquals(entry.getHazardsRisks(), imported.get(0).getHazardsRisks());
		assertEquals(entry.getMitigations(), imported.get(0).getMitigations());
	}

	@Test
	void rejectsMissingRequiredHeadersAndUnclosedQuotes() {
		SafetyHazardCsvService service = new SafetyHazardCsvService();

		assertThrows(IllegalArgumentException.class, () -> service.importCsv("hazard,mitigation\nx,y"));
		assertThrows(IllegalArgumentException.class,
				() -> service.importCsv("hazards_risks,mitigations\n\"unfinished,x"));
	}
}
