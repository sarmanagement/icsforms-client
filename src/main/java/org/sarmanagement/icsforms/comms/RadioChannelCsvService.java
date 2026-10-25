/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.comms;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.sarmanagement.icsforms.model.Ics205Form;
import org.sarmanagement.icsforms.model.RadioChannel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Lossless versioned native plan CSV. Each data-field cell is a JSON value, preserving nulls,
 * strings, booleans and arbitrary programming metadata without numeric coercion.
 * A form record is always emitted, including for plans without channels.
 */
public class RadioChannelCsvService {
	private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule())
			.enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
	private static final List<String> FIELDS = List.of("channelId", "zoneGroup", "channelNumber", "function",
			"channelName", "assignment", "rxFrequency", "rxBandwidth", "rxToneNac", "txFrequency",
			"txBandwidth", "txToneNac", "mode", "digitalProtocol", "system", "remarks", "txPolicy",
			"includeOnForm", "programmingMetadata");
	private static final List<String> HEADERS;

	static {
		List<String> headers = new ArrayList<>(List.of("schemaVersion", "recordType", "formMetadata"));
		headers.addAll(FIELDS);
		HEADERS = List.copyOf(headers);
	}

	/**
	 * Reads a complete native plan.
	 * @param path UTF-8 source.
	 * @return imported plan.
	 * @throws IOException when reading fails.
	 */
	public Ics205Form read(Path path) throws IOException {
		return importCsv(Files.readString(path, StandardCharsets.UTF_8));
	}

	/**
	 * Writes a complete native plan.
	 * @param path UTF-8 destination.
	 * @param form plan to serialize.
	 * @throws IOException when writing fails.
	 */
	public void write(Path path, Ics205Form form) throws IOException {
		writeCsv(path, exportCsv(form));
	}

	/**
	 * Publishes a complete native or CHIRP CSV without truncating an existing backup.
	 * Atomic-move failures are not retried with a non-atomic replacement.
	 *
	 * @param path destination file.
	 * @param csv complete UTF-8 CSV text.
	 * @throws IOException when staging or atomic publication fails.
	 */
	public static void writeCsv(Path path, String csv) throws IOException {
		if (path == null || csv == null) {
			throw new IllegalArgumentException("CSV destination and text are required.");
		}
		Path destination = path.toAbsolutePath();
		Path temporary = Files.createTempFile(destination.getParent(), ".ics205-csv-", ".tmp");
		try {
			Files.writeString(temporary, csv, StandardCharsets.UTF_8);
			Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} finally {
			Files.deleteIfExists(temporary);
		}
	}

	/**
	 * Serializes all form and channel data, independently of print selection.
	 * @param form source plan.
	 * @return native CSV.
	 */
	public String exportCsv(Ics205Form form) {
		if (form == null) {
			throw new IllegalArgumentException("Plan is required.");
		}
		UUID.fromString(form.getFormId());
		ObjectNode metadata = MAPPER.valueToTree(form);
		metadata.remove("channels");
		StringBuilder csv = new StringBuilder();
		CsvTable.append(csv, HEADERS);
		List<String> formRow = new ArrayList<>(List.of("1", "form", metadata.toString()));
		FIELDS.forEach(field -> formRow.add(""));
		CsvTable.append(csv, formRow);
		Set<String> ids = new HashSet<>();
		for (RadioChannel channel : form.getChannels()) {
			if (channel == null || channel.getChannelId() == null || !ids.add(channel.getChannelId())) {
				throw new IllegalArgumentException("Channels require unique explicit IDs.");
			}
			UUID.fromString(channel.getChannelId());
			JsonNode node = MAPPER.valueToTree(channel);
			List<String> row = new ArrayList<>(List.of("1", "channel", ""));
			for (String field : FIELDS) {
				row.add(node.path(field).toString());
			}
			CsvTable.append(csv, row);
		}
		return csv.toString();
	}

	/**
	 * Parses and validates an entire plan before returning it.
	 * @param csv native CSV text.
	 * @return imported plan.
	 */
	public Ics205Form importCsv(String csv) {
		List<List<String>> parsed = CsvTable.parse(csv);
		List<Map<String, String>> records = CsvTable.records(parsed, HEADERS);
		List<Integer> recordNumbers = new ArrayList<>();
		for (int i = 1; i < parsed.size(); i++) {
			if (parsed.get(i).size() != 1 || !parsed.get(i).get(0).isBlank()) {
				recordNumbers.add(i + 1);
			}
		}
		Ics205Form form = null;
		List<RadioChannel> channels = new ArrayList<>();
		Set<String> ids = new HashSet<>();
		int recordIndex = 0;
		int recordNumber = 1;
		String column = "recordType";
		try {
			for (Map<String, String> row : records) {
				recordNumber = recordNumbers.get(recordIndex++);
				column = "headers";
				if (row.size() != HEADERS.size()) {
					throw new IllegalArgumentException("Unknown native CSV columns; cannot import losslessly.");
				}
				column = "schemaVersion";
				if (!"1".equals(row.get("schemaVersion"))) {
					throw new IllegalArgumentException("Unsupported native CSV schema version.");
				}
				column = "recordType";
				if ("form".equals(row.get("recordType"))) {
					column = "formMetadata";
					if (form != null) {
						throw new IllegalArgumentException("Duplicate form metadata record.");
					}
					JsonNode metadata = MAPPER.readTree(row.get("formMetadata"));
					if (metadata == null || !metadata.isObject() || metadata.has("channels")) {
						throw new IllegalArgumentException("Invalid form metadata.");
					}
					Set<String> formFields = Set.of("formId", "specialInstructions", "preparedByName",
							"preparedBySignature", "datePrepared", "preparedDateTime", "iapPage");
					if (metadata.size() != formFields.size()) {
						throw new IllegalArgumentException("Native CSV requires complete form metadata.");
					}
					metadata.fieldNames().forEachRemaining(field -> {
						if (!formFields.contains(field)) {
							throw new IllegalArgumentException("Unknown form metadata field: " + field);
						}
						if (!Set.of("datePrepared", "preparedDateTime").contains(field)
								&& !metadata.path(field).isTextual()) {
							throw new IllegalArgumentException("Form text values must be strings: " + field);
						}
					});
					UUID.fromString(metadata.path("formId").asText());
					for (String field : FIELDS) {
						column = field;
						if (!row.get(field).isEmpty()) {
							throw new IllegalArgumentException("Form records cannot contain channel values.");
						}
					}
					column = "formMetadata";
					form = MAPPER.treeToValue(metadata, Ics205Form.class);
				} else if ("channel".equals(row.get("recordType"))) {
					column = "formMetadata";
					if (!row.get("formMetadata").isEmpty()) {
						throw new IllegalArgumentException("Channel records cannot contain form metadata.");
					}
					ObjectNode node = MAPPER.createObjectNode();
					for (String field : FIELDS) {
						column = field;
						JsonNode value = MAPPER.readTree(row.get(field));
						if (value == null) {
							throw new IllegalArgumentException("Missing channel value: " + field);
						}
						if ("includeOnForm".equals(field) ? !value.isBoolean()
								: "programmingMetadata".equals(field) ? !value.isObject() : !value.isTextual()) {
							throw new IllegalArgumentException("Incorrect native CSV value type: " + field);
						}
						if ("programmingMetadata".equals(field)) {
							value.elements().forEachRemaining(cell -> {
								if (!cell.isTextual() && !cell.isNull()) {
									throw new IllegalArgumentException("Programming metadata values must be strings or null.");
								}
							});
						}
						node.set(field, value);
					}
					if (!node.path("channelId").isTextual()) {
						throw new IllegalArgumentException("Channel ID is required.");
					}
					column = "channelId";
					UUID.fromString(node.path("channelId").asText());
					column = "txPolicy";
					if (node.path("txPolicy").asText().isBlank()) {
						throw new IllegalArgumentException("Explicit TX policy is required, including UNKNOWN.");
					}
					column = "channelId";
					RadioChannel channel = MAPPER.treeToValue(node, RadioChannel.class);
					if (channel.getChannelId() == null || !ids.add(channel.getChannelId())) {
						throw new IllegalArgumentException("Duplicate or missing channel ID.");
					}
					channels.add(channel);
				} else {
					throw new IllegalArgumentException("Unknown native CSV record type.");
				}
			}
		} catch (JsonProcessingException e) {
			throw new IllegalArgumentException("CSV record " + recordNumber + ", column " + column
					+ ": invalid JSON value: " + e.getOriginalMessage(), e);
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("CSV record " + recordNumber + ", column " + column
					+ ": " + e.getMessage(), e);
		}
		if (form == null) {
			throw new IllegalArgumentException("Native CSV requires one form metadata record.");
		}
		form.setChannels(channels);
		return form;
	}
}
