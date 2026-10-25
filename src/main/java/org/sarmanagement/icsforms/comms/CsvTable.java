/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.comms;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

/** Strict RFC 4180 text codec shared by communications interchange formats. */
final class CsvTable {
	/** Prevents instantiation. */
	private CsvTable() {
	}

	/**
	 * Parses fields without changing embedded newlines or precision.
	 * @param text CSV text, optionally beginning with a UTF-8 BOM.
	 * @return parsed records.
	 */
	static List<List<String>> parse(String text) {
		if (text == null) {
			throw new IllegalArgumentException("CSV text is required.");
		}
		String csv = text.startsWith("\uFEFF") ? text.substring(1) : text;
		List<List<String>> rows = new ArrayList<>();
		List<String> row = new ArrayList<>();
		StringBuilder field = new StringBuilder();
		boolean quoted = false;
		boolean closed = false;
		for (int i = 0; i < csv.length(); i++) {
			char c = csv.charAt(i);
			if (quoted) {
				if (c == '"' && i + 1 < csv.length() && csv.charAt(i + 1) == '"') {
					field.append('"');
					i++;
				} else if (c == '"') {
					quoted = false;
					closed = true;
				} else {
					field.append(c);
				}
			} else if (c == ',' || c == '\r' || c == '\n') {
				row.add(field.toString());
				field.setLength(0);
				closed = false;
				if (c != ',') {
					rows.add(row);
					row = new ArrayList<>();
					if (c == '\r' && i + 1 < csv.length() && csv.charAt(i + 1) == '\n') {
						i++;
					}
				}
			} else if (c == '"' && field.length() == 0 && !closed) {
				quoted = true;
			} else {
				if (closed || c == '"') {
					throw new IllegalArgumentException("Invalid CSV quoting at record " + (rows.size() + 1)
							+ ", column " + (row.size() + 1));
				}
				field.append(c);
			}
		}
		if (quoted) {
			throw new IllegalArgumentException("Unclosed quoted CSV field at record " + (rows.size() + 1)
					+ ", column " + (row.size() + 1));
		}
		if (closed || field.length() > 0 || !row.isEmpty()) {
			row.add(field.toString());
			rows.add(row);
		}
		return rows;
	}

	/**
	 * Maps unique nonblank headers to cells and checks record widths.
	 * @param csv CSV document.
	 * @param required required header names.
	 * @return records keyed by header.
	 */
	static List<Map<String, String>> records(String csv, List<String> required) {
		return records(parse(csv), required);
	}

	/**
	 * Maps already parsed records after a format-specific preamble is handled.
	 * @param rows parsed records, beginning with the header.
	 * @param required required header names.
	 * @return records keyed by header.
	 */
	static List<Map<String, String>> records(List<List<String>> rows, List<String> required) {
		return records(rows, required, required);
	}

	/**
	 * Canonicalizes known header names case-insensitively, preserving unknown names.
	 * @param rows parsed records, beginning with the header.
	 * @param required required header names.
	 * @param known canonical required and optional header names.
	 * @return records keyed by canonical header.
	 */
	static List<Map<String, String>> records(List<List<String>> rows, List<String> required, List<String> known) {
		if (rows.isEmpty()) {
			throw new IllegalArgumentException("CSV header is required.");
		}
		Map<String, String> canonical = new LinkedHashMap<>();
		known.forEach(header -> canonical.put(header.toLowerCase(Locale.ROOT), header));
		List<String> headers = rows.get(0).stream().map(String::trim)
				.map(header -> canonical.getOrDefault(header.toLowerCase(Locale.ROOT), header)).toList();
		if (headers.stream().anyMatch(String::isEmpty)
				|| headers.stream().map(header -> header.toLowerCase(Locale.ROOT)).distinct().count() != headers.size()
				|| !headers.containsAll(required)) {
			throw new IllegalArgumentException("Missing, blank, or duplicate CSV headers; required: " + required);
		}
		List<Map<String, String>> result = new ArrayList<>();
		for (int i = 1; i < rows.size(); i++) {
			List<String> row = rows.get(i);
			if (row.size() == 1 && row.get(0).isBlank()) {
				continue;
			}
			if (row.size() != headers.size()) {
				throw new IllegalArgumentException("CSV record " + (i + 1) + " has " + row.size()
						+ " cells; expected " + headers.size() + "; first mismatched column "
						+ (Math.min(row.size(), headers.size()) + 1));
			}
			Map<String, String> record = new LinkedHashMap<>();
			for (int j = 0; j < headers.size(); j++) {
				record.put(headers.get(j), row.get(j));
			}
			result.add(record);
		}
		return result;
	}

	/**
	 * Writes one record using CRLF record separators.
	 * @param output destination builder.
	 * @param cells cell values.
	 */
	static void append(StringBuilder output, List<String> cells) {
		for (int i = 0; i < cells.size(); i++) {
			if (i > 0) {
				output.append(',');
			}
			String value = cells.get(i);
			if (value.indexOf(',') >= 0 || value.indexOf('"') >= 0
					|| value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
				output.append('"').append(value.replace("\"", "\"\"")).append('"');
			} else {
				output.append(value);
			}
		}
		output.append("\r\n");
	}
}
