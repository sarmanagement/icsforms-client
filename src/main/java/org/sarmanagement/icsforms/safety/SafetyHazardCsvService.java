/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.safety;

import org.sarmanagement.icsforms.model.SafetyAnalysisEntry;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Imports and exports reusable ICS 215A hazards and mitigation CSV libraries.
 */
public class SafetyHazardCsvService {
	private static final List<String> COLUMNS = List.of("hazards_risks", "mitigations", "incident_area",
			"equipment_ppe");

	/**
	 * Reads a UTF-8 CSV library, accepting an optional byte-order mark.
	 *
	 * @param path source CSV path.
	 * @return imported hazard rows.
	 * @throws IOException when the file cannot be read.
	 */
	public List<SafetyAnalysisEntry> read(Path path) throws IOException {
		return importCsv(Files.readString(path, StandardCharsets.UTF_8));
	}

	/**
	 * Parses the required hazards/mitigations header and optional library columns.
	 *
	 * @param csv CSV document.
	 * @return imported hazard rows.
	 * @throws IllegalArgumentException when CSV syntax or required headers are invalid.
	 */
	public List<SafetyAnalysisEntry> importCsv(String csv) {
		if (csv == null || csv.isBlank()) {
			return new ArrayList<>();
		}
		String content = csv.startsWith("\uFEFF") ? csv.substring(1) : csv;
		List<List<String>> rows = parse(content);
		if (rows.isEmpty()) {
			return new ArrayList<>();
		}
		Map<String, Integer> indexes = new HashMap<>();
		for (int i = 0; i < rows.get(0).size(); i++) {
			indexes.put(rows.get(0).get(i).trim().toLowerCase(Locale.ROOT), i);
		}
		if (!indexes.containsKey("hazards_risks") || !indexes.containsKey("mitigations")) {
			throw new IllegalArgumentException("CSV must include hazards_risks and mitigations headers.");
		}
		List<SafetyAnalysisEntry> entries = new ArrayList<>();
		for (int rowIndex = 1; rowIndex < rows.size(); rowIndex++) {
			List<String> row = rows.get(rowIndex);
			if (row.stream().allMatch(String::isBlank)) {
				continue;
			}
			if (row.size() > rows.get(0).size()) {
				throw new IllegalArgumentException("CSV row " + (rowIndex + 1) + " has more values than headers.");
			}
			SafetyAnalysisEntry entry = new SafetyAnalysisEntry();
			entry.setHazardsRisks(value(row, indexes, "hazards_risks"));
			entry.setMitigations(value(row, indexes, "mitigations"));
			entry.setIncidentArea(value(row, indexes, "incident_area"));
			entry.setEquipmentPpe(value(row, indexes, "equipment_ppe"));
			entries.add(entry);
		}
		return entries;
	}

	/**
	 * Serializes reusable rows as RFC 4180-style CSV with LF line endings.
	 *
	 * @param entries rows to export.
	 * @return UTF-8-compatible CSV text.
	 */
	public String exportCsv(List<SafetyAnalysisEntry> entries) {
		StringBuilder csv = new StringBuilder(String.join(",", COLUMNS)).append('\n');
		if (entries != null) {
			for (SafetyAnalysisEntry entry : entries) {
				if (entry == null) {
					continue;
				}
				csv.append(escape(entry.getHazardsRisks())).append(',')
						.append(escape(entry.getMitigations())).append(',')
						.append(escape(entry.getIncidentArea())).append(',')
						.append(escape(entry.getEquipmentPpe())).append('\n');
			}
		}
		return csv.toString();
	}

	/**
	 * Writes rows as UTF-8 CSV.
	 *
	 * @param path destination path.
	 * @param entries rows to export.
	 * @throws IOException when the file cannot be written.
	 */
	public void write(Path path, List<SafetyAnalysisEntry> entries) throws IOException {
		Files.writeString(path, exportCsv(entries), StandardCharsets.UTF_8);
	}

	/**
	 * Returns a cell value when its header exists and the row contains that cell.
	 *
	 * @param row parsed row.
	 * @param indexes header indexes.
	 * @param header column name.
	 * @return cell text, or blank.
	 */
	private static String value(List<String> row, Map<String, Integer> indexes, String header) {
		Integer index = indexes.get(header);
		return index == null || index >= row.size() ? "" : row.get(index);
	}

	/**
	 * Quotes a cell only when CSV syntax requires it.
	 *
	 * @param value cell content.
	 * @return escaped CSV cell.
	 */
	private static String escape(String value) {
		String safe = value == null ? "" : value;
		if (safe.indexOf(',') < 0 && safe.indexOf('"') < 0 && safe.indexOf('\n') < 0 && safe.indexOf('\r') < 0) {
			return safe;
		}
		return "\"" + safe.replace("\"", "\"\"") + "\"";
	}

	/**
	 * Parses CSV fields with quoted commas, embedded newlines, and escaped quotes.
	 *
	 * @param csv input text.
	 * @return parsed rows.
	 * @throws IllegalArgumentException when quotes are unclosed or misplaced.
	 */
	private static List<List<String>> parse(String csv) {
		List<List<String>> rows = new ArrayList<>();
		List<String> row = new ArrayList<>();
		StringBuilder field = new StringBuilder();
		boolean quoted = false;
		boolean closedQuote = false;
		for (int i = 0; i < csv.length(); i++) {
			char c = csv.charAt(i);
			if (quoted) {
				if (c == '"' && i + 1 < csv.length() && csv.charAt(i + 1) == '"') {
					field.append('"');
					i++;
				} else if (c == '"') {
					quoted = false;
					closedQuote = true;
				} else {
					field.append(c);
				}
			} else if (c == ',' || c == '\n' || c == '\r') {
				if (c == ',' ) {
					row.add(field.toString());
					field.setLength(0);
					closedQuote = false;
				} else {
					row.add(field.toString());
					rows.add(row);
					row = new ArrayList<>();
					field.setLength(0);
					closedQuote = false;
					if (c == '\r' && i + 1 < csv.length() && csv.charAt(i + 1) == '\n') {
						i++;
					}
				}
			} else if (c == '"' && field.length() == 0 && !closedQuote) {
				quoted = true;
			} else {
				if (closedQuote) {
					throw new IllegalArgumentException("Unexpected character after a quoted CSV field.");
				}
				field.append(c);
			}
		}
		if (quoted) {
			throw new IllegalArgumentException("CSV contains an unclosed quoted field.");
		}
		if (field.length() > 0 || !row.isEmpty() || closedQuote) {
			row.add(field.toString());
			rows.add(row);
		}
		return rows;
	}
}
