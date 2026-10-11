/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.safety;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.SafetyAnalysisEntry;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests adding analysis hazards to and restoring them from the common library.
 */
class SafetyHazardLibraryServiceTest {
	@TempDir
	Path tempDirectory;

	@Test
	void savesCurrentAnalysisAsLibraryCsvAndLoadsRowsIntoAnotherIncident() throws Exception {
		AppData incident = new AppData();
		SafetyAnalysisEntry first = entry("Division A", "Loose rocks", "Avoid the slope.", "Helmet");
		SafetyAnalysisEntry second = entry("All", "Cold exposure", "Use insulating layers.", "Dry clothing");
		incident.getForm215a().setEntries(List.of(first, second));
		SafetyHazardLibraryService service = new SafetyHazardLibraryService();
		Path libraryFile = tempDirectory.resolve("common-hazards.csv");

		service.saveAnalysisAsCommonLibrary(incident, libraryFile);

		assertEquals(2, incident.getCommonSafetyHazards().size());
		assertNotSame(first, incident.getCommonSafetyHazards().get(0));
		assertEquals("Loose rocks", incident.getCommonSafetyHazards().get(0).getHazardsRisks());
		assertEquals("Division A", incident.getCommonSafetyHazards().get(0).getIncidentArea());
		assertEquals("Avoid the slope.", incident.getCommonSafetyHazards().get(0).getMitigations());
		assertEquals("Helmet", incident.getCommonSafetyHazards().get(0).getEquipmentPpe());
		assertEquals(2, new SafetyHazardCsvService().read(libraryFile).size());
		String csv = Files.readString(libraryFile);
		assertTrue(csv.contains("Loose rocks"));
		assertTrue(csv.contains("Avoid the slope."));
		assertTrue(csv.contains("Cold exposure"));

		AppData nextIncident = new AppData();
		List<SafetyAnalysisEntry> loaded = service.loadCommonLibrary(nextIncident, libraryFile);

		assertEquals(2, loaded.size());
		assertEquals(2, nextIncident.getCommonSafetyHazards().size());
		assertEquals("Cold exposure", nextIncident.getCommonSafetyHazards().get(1).getHazardsRisks());
		assertEquals("Use insulating layers.", nextIncident.getCommonSafetyHazards().get(1).getMitigations());
		assertEquals("Dry clothing", nextIncident.getCommonSafetyHazards().get(1).getEquipmentPpe());
	}

	/**
	 * Creates one populated hazard row for library round-trip coverage.
	 *
	 * @param area incident area.
	 * @param hazard hazard and risk description.
	 * @param mitigation mitigation description.
	 * @param equipment required equipment.
	 * @return populated analysis entry.
	 */
	private static SafetyAnalysisEntry entry(String area, String hazard, String mitigation, String equipment) {
		SafetyAnalysisEntry entry = new SafetyAnalysisEntry();
		entry.setIncidentArea(area);
		entry.setHazardsRisks(hazard);
		entry.setMitigations(mitigation);
		entry.setEquipmentPpe(equipment);
		return entry;
	}
}
