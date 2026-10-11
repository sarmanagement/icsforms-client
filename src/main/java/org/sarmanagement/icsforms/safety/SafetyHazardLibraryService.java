/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.safety;

import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.SafetyAnalysisEntry;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages copying incident analysis rows to and from the reusable safety library.
 */
public class SafetyHazardLibraryService {
	private final SafetyHazardCsvService csvService;

	/**
	 * Creates a library service using the standard CSV format.
	 */
	public SafetyHazardLibraryService() {
		this(new SafetyHazardCsvService());
	}

	/**
	 * Creates a library service with the supplied CSV codec.
	 *
	 * @param csvService CSV reader and writer.
	 */
	SafetyHazardLibraryService(SafetyHazardCsvService csvService) {
		this.csvService = csvService;
	}

	/**
	 * Copies current ICS 215A analysis rows into the reusable library and writes
	 * the resulting library to a CSV file.
	 *
	 * @param data incident data containing the current analysis.
	 * @param path destination CSV file.
	 * @throws IOException when the library cannot be written.
	 */
	public void saveAnalysisAsCommonLibrary(AppData data, Path path) throws IOException {
		List<SafetyAnalysisEntry> library = copyEntries(data.getForm215a().getEntries());
		csvService.write(path, library);
		data.setCommonSafetyHazards(library);
	}

	/**
	 * Reads a CSV file and stores its rows as this incident's reusable library.
	 *
	 * @param data incident data to update.
	 * @param path source CSV file.
	 * @return imported library entries.
	 * @throws IOException when the library cannot be read.
	 */
	public List<SafetyAnalysisEntry> loadCommonLibrary(AppData data, Path path) throws IOException {
		List<SafetyAnalysisEntry> library = storeCommonLibrary(data, csvService.read(path));
		return library;
	}

	/**
	 * Stores independent copies of imported rows as the incident's common library.
	 *
	 * @param data incident data to update.
	 * @param entries imported reusable rows.
	 * @return copied library entries.
	 */
	public List<SafetyAnalysisEntry> storeCommonLibrary(AppData data, List<SafetyAnalysisEntry> entries) {
		List<SafetyAnalysisEntry> library = copyEntries(entries);
		data.setCommonSafetyHazards(library);
		return library;
	}

	/**
	 * Creates independent copies of hazard rows.
	 *
	 * @param entries source rows.
	 * @return copied rows.
	 */
	private static List<SafetyAnalysisEntry> copyEntries(List<SafetyAnalysisEntry> entries) {
		List<SafetyAnalysisEntry> copies = new ArrayList<>();
		if (entries == null) {
			return copies;
		}
		for (SafetyAnalysisEntry entry : entries) {
			if (entry == null) {
				continue;
			}
			SafetyAnalysisEntry copy = new SafetyAnalysisEntry();
			copy.setIncidentArea(entry.getIncidentArea());
			copy.setHazardsRisks(entry.getHazardsRisks());
			copy.setMitigations(entry.getMitigations());
			copy.setEquipmentPpe(entry.getEquipmentPpe());
			copies.add(copy);
		}
		return copies;
	}
}
