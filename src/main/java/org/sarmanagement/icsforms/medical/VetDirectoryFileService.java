/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.medical;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.sarmanagement.icsforms.model.StagingLocation;
import org.sarmanagement.icsforms.model.VetDirectory;
import org.sarmanagement.icsforms.model.VetFacility;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Reads and explicitly exports reusable regional veterinary data as JSON or CSV.
 */
public class VetDirectoryFileService {
	/** Version of the portable regional JSON document. */
	public static final int JSON_SCHEMA_VERSION = 1;
	private static final List<String> CSV_HEADERS = List.of("id", "name", "town", "state", "postalCode", "address",
			"contactNumbers", "fax", "available24Hours", "availabilityNotes", "capabilities", "verifiedAt",
			"latitude", "longitude");
	private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule())
			.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

	/**
	 * Creates a regional file service.
	 */
	public VetDirectoryFileService() {
	}

	/**
	 * Imports one or more regional files into a copy of an existing directory.
	 *
	 * <p>
	 * Parse failures are reported per file/row. Existing records are never removed;
	 * conflicting stable IDs are retained with a new ID and reported.
	 * </p>
	 *
	 * @param files selected local files.
	 * @param existing incident-owned directory.
	 * @return merged directory and a report of duplicates, conflicts, and errors.
	 */
	public ImportResult importFiles(List<Path> files, VetDirectory existing) {
		VetDirectory merged = copyDirectory(existing);
		List<String> errors = new ArrayList<>();
		List<String> conflicts = new ArrayList<>();
		List<ConflictRecord> conflictRecords = new ArrayList<>();
		int duplicates = 0;
		for (Path file : files == null ? List.<Path>of() : files) {
			if (file == null) {
				continue;
			}
			List<VetFacility> parsed;
			String format = format(file);
			try {
				parsed = "json".equals(format) ? readJson(file) : readCsv(file, errors);
			} catch (IOException | IllegalArgumentException exception) {
				errors.add(file.getFileName() + ": " + safeMessage(exception));
				continue;
			}
			VetDirectory.Source source = new VetDirectory.Source();
			source.setName(file.getFileName().toString());
			source.setFormat(format.toUpperCase(Locale.ROOT));
			source.setImportedAt(Instant.now());
			merged.getSources().add(source);
			for (VetFacility facility : parsed) {
				String existingId = merged.getFacilities().stream()
						.filter(candidate -> candidate.getId().equals(facility.getId()))
						.map(VetFacility::getId).findFirst().orElse("");
				facility.setSourceName(file.getFileName().toString());
				MergeDecision decision = merge(merged, facility);
				if (decision == MergeDecision.DUPLICATE) {
					duplicates++;
				} else if (decision == MergeDecision.CONFLICT) {
					conflicts.add(file.getFileName() + ": conflicting ID retained with a new ID for "
							+ facility.getName());
					conflictRecords.add(new ConflictRecord(existingId, facility.getId(), facility.getName(),
							file.getFileName().toString()));
				}
			}
		}
		return new ImportResult(merged, duplicates, conflicts, errors, conflictRecords);
	}

	/**
	 * Applies explicit stable-ID conflict choices to an imported directory copy.
	 *
	 * @param result merge preview.
	 * @param choices resolutions keyed by imported record ID; omitted choices keep both.
	 * @return resolved directory copy.
	 */
	public VetDirectory resolveConflicts(ImportResult result, java.util.Map<String, ConflictResolution> choices) {
		VetDirectory resolved = copyDirectory(result.directory());
		for (ConflictRecord conflict : result.conflictRecords()) {
			ConflictResolution choice = choices == null ? null : choices.get(conflict.importedId());
			if (choice == ConflictResolution.REPLACE_EXISTING) {
				VetFacility imported = findById(resolved, conflict.importedId());
				int existingIndex = indexById(resolved, conflict.existingId());
				int importedIndex = indexById(resolved, conflict.importedId());
				if (imported != null && existingIndex >= 0 && importedIndex >= 0) {
					imported.setId(conflict.existingId());
					resolved.getFacilities().set(existingIndex, imported);
					if (importedIndex != existingIndex) {
						resolved.getFacilities().remove(importedIndex);
					}
				}
			} else if (choice == ConflictResolution.SKIP_IMPORTED) {
				removeById(resolved, conflict.importedId());
			}
		}
		return resolved;
	}

	/**
	 * Writes a selected reusable directory to a JSON or CSV file.
	 *
	 * @param target destination path; null represents a cancelled chooser.
	 * @param directory data to save.
	 * @param overwrite whether an existing destination may be replaced.
	 * @return true when written, false when cancelled.
	 * @throws IOException when export fails or overwrite was not confirmed.
	 */
	public boolean save(Path target, VetDirectory directory, boolean overwrite) throws IOException {
		if (target == null) {
			return false;
		}
		Path absolute = target.toAbsolutePath();
		if (Files.exists(absolute) && !overwrite) {
			throw new java.nio.file.FileAlreadyExistsException(absolute.toString());
		}
		Path parent = absolute.getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		String content = "json".equals(format(absolute)) ? jsonContent(directory) : csvContent(directory);
		Path temporary = Files.createTempFile(parent, absolute.getFileName().toString(), ".tmp");
		try {
			Files.writeString(temporary, content, java.nio.charset.StandardCharsets.UTF_8);
			move(temporary, absolute, overwrite);
			return true;
		} finally {
			Files.deleteIfExists(temporary);
		}
	}

	/**
	 * Returns a documented CSV header row.
	 *
	 * @return immutable CSV field order.
	 */
	public List<String> csvHeaders() {
		return CSV_HEADERS;
	}

	/**
	 * Reads supported JSON facilities without using their source path afterward.
	 *
	 * @param file input JSON path.
	 * @return facilities in source order.
	 * @throws IOException when JSON cannot be read.
	 */
	private List<VetFacility> readJson(Path file) throws IOException {
		JsonNode root = MAPPER.readTree(file.toFile());
		JsonNode version = root == null ? null : root.get("schemaVersion");
		if (version == null || !version.canConvertToInt() || version.intValue() != JSON_SCHEMA_VERSION) {
			throw new IllegalArgumentException("Unsupported regional JSON schema version");
		}
		DirectoryFile parsed = MAPPER.treeToValue(root, DirectoryFile.class);
		return parsed.facilities() == null ? new ArrayList<>() : new ArrayList<>(parsed.facilities());
	}

	/**
	 * Parses the documented UTF-8 CSV schema and reports malformed data rows.
	 *
	 * @param file input CSV path.
	 * @param errors target error report.
	 * @return all valid rows.
	 * @throws IOException when the file cannot be read or quoting is malformed.
	 */
	private List<VetFacility> readCsv(Path file, List<String> errors) throws IOException {
		String content = Files.readString(file, java.nio.charset.StandardCharsets.UTF_8);
		List<List<String>> rows = parseCsv(content);
		if (rows.isEmpty()) {
			return new ArrayList<>();
		}
		List<CsvField> fields = csvFields(rows.get(0));
		List<VetFacility> facilities = new ArrayList<>();
		for (int rowIndex = 1; rowIndex < rows.size(); rowIndex++) {
			List<String> row = rows.get(rowIndex);
			if (row.size() != fields.size()) {
				errors.add(file.getFileName() + ": row " + (rowIndex + 1) + " has " + row.size()
						+ " fields; expected " + fields.size());
				continue;
			}
			try {
				facilities.add(csvFacility(row, fields));
			} catch (IllegalArgumentException exception) {
				errors.add(file.getFileName() + ": row " + (rowIndex + 1) + ": " + safeMessage(exception));
			}
		}
		return facilities;
	}

	/**
	 * Converts one parsed CSV row into a validated regional entry.
	 *
	 * @param row CSV cells.
	 * @param fields field mapping for the imported header.
	 * @return parsed facility.
	 */
	private VetFacility csvFacility(List<String> row, List<CsvField> fields) {
		java.util.EnumMap<CsvField, String> values = new java.util.EnumMap<>(CsvField.class);
		for (int index = 0; index < fields.size(); index++) {
			CsvField field = fields.get(index);
			if (field != null) {
				String value = row.get(index).trim();
				if (field == CsvField.CAPABILITIES && values.containsKey(field) && !value.isBlank()) {
					String existing = values.get(field);
					values.put(field, existing.isBlank() ? value : existing + "; " + value);
				} else if (!values.containsKey(field) || values.get(field).isBlank()) {
					values.put(field, value);
				}
			}
		}
		VetFacility facility = new VetFacility();
		facility.setId(values.get(CsvField.ID));
		facility.setName(values.get(CsvField.NAME));
		facility.setTown(values.get(CsvField.TOWN));
		facility.setState(values.get(CsvField.STATE));
		facility.setPostalCode(values.get(CsvField.POSTAL_CODE));
		facility.setAddress(values.get(CsvField.ADDRESS));
		facility.setContactNumbers(parseContacts(value(values.get(CsvField.CONTACT_NUMBERS))));
		facility.setFax(value(values.get(CsvField.FAX)));
		facility.setAvailable24Hours(parseAvailability(value(values.get(CsvField.AVAILABLE_24_HOURS))));
		facility.setAvailabilityNotes(value(values.get(CsvField.AVAILABILITY_NOTES)));
		facility.setCapabilities(value(values.get(CsvField.CAPABILITIES)));
		String verifiedAt = value(values.get(CsvField.VERIFIED_AT));
		if (!verifiedAt.isBlank()) {
			try {
				facility.setVerifiedAt(Instant.parse(verifiedAt));
			} catch (RuntimeException exception) {
				throw new IllegalArgumentException("verifiedAt must be ISO-8601", exception);
			}
		}
		Double latitude = optionalNumber(value(values.get(CsvField.LATITUDE)), "latitude");
		Double longitude = optionalNumber(value(values.get(CsvField.LONGITUDE)), "longitude");
		if (latitude != null && longitude != null) {
			StagingLocation.validateCoordinates(latitude, longitude);
			facility.setCoordinates(latitude, longitude, "imported", "CSV coordinates");
		} else if (latitude != null || longitude != null) {
			throw new IllegalArgumentException("latitude and longitude must both be present");
		}
		return facility;
	}

	/**
	 * Writes a JSON document with an explicit format version.
	 *
	 * @param directory regional directory.
	 * @return pretty JSON document.
	 * @throws IOException when serialization fails.
	 */
	private String jsonContent(VetDirectory directory) throws IOException {
		DirectoryFile file = new DirectoryFile(JSON_SCHEMA_VERSION,
				directory == null ? List.of() : directory.getFacilities());
		return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(file);
	}

	/**
	 * Writes all facilities using the versioned header/field order.
	 *
	 * @param directory regional directory.
	 * @return UTF-8 CSV text.
	 */
	private String csvContent(VetDirectory directory) {
		StringBuilder csv = new StringBuilder();
		appendCsvRow(csv, CSV_HEADERS);
		if (directory != null) {
			for (VetFacility facility : directory.getFacilities()) {
				appendCsvRow(csv, List.of(facility.getId(), facility.getName(), facility.getTown(),
						facility.getState(), facility.getPostalCode(), facility.getAddress(),
						String.join(";", facility.getContactNumbers()), facility.getFax(),
						availabilityText(facility.getAvailable24Hours()), facility.getAvailabilityNotes(),
						facility.getCapabilities(), facility.getVerifiedAt() == null ? "" : facility.getVerifiedAt().toString(),
						facility.getLatitude() == null ? "" : facility.getLatitude().toString(),
						facility.getLongitude() == null ? "" : facility.getLongitude().toString()));
			}
		}
		return csv.toString();
	}

	/**
	 * Appends a quoted CSV record, including embedded newlines and Unicode.
	 *
	 * @param output output buffer.
	 * @param values record values.
	 */
	private void appendCsvRow(StringBuilder output, List<String> values) {
		for (int index = 0; index < values.size(); index++) {
			if (index > 0) {
				output.append(',');
			}
			String value = values.get(index) == null ? "" : values.get(index);
			output.append('"').append(value.replace("\"", "\"\"")).append('"');
		}
		output.append("\r\n");
	}

	/**
	 * Parses standard quoted CSV including escaped quotes and multiline fields.
	 *
	 * @param content complete UTF-8 file content.
	 * @return parsed records.
	 * @throws IOException when a quoted field is not terminated.
	 */
	private static List<List<String>> parseCsv(String content) throws IOException {
		List<List<String>> rows = new ArrayList<>();
		List<String> row = new ArrayList<>();
		StringBuilder cell = new StringBuilder();
		boolean quoted = false;
		for (int index = 0; index < content.length(); index++) {
			char character = content.charAt(index);
			if (quoted) {
				if (character == '"' && index + 1 < content.length() && content.charAt(index + 1) == '"') {
					cell.append('"');
					index++;
				} else if (character == '"') {
					quoted = false;
				} else {
					cell.append(character);
				}
			} else if (character == '"' && cell.isEmpty()) {
				quoted = true;
			} else if (character == ',') {
				row.add(cell.toString());
				cell.setLength(0);
			} else if (character == '\n' || character == '\r') {
				if (character == '\r' && index + 1 < content.length() && content.charAt(index + 1) == '\n') {
					index++;
				}
				row.add(cell.toString());
				cell.setLength(0);
				if (!(row.size() == 1 && row.get(0).isEmpty())) {
					rows.add(row);
				}
				row = new ArrayList<>();
			} else {
				cell.append(character);
			}
		}
		if (quoted) {
			throw new IOException("CSV contains an unterminated quoted field");
		}
		if (!row.isEmpty() || !cell.isEmpty()) {
			row.add(cell.toString());
			rows.add(row);
		}
		return rows;
	}

	/**
	 * Adds a source facility unless it is an exact ID or cautious address/contact duplicate.
	 *
	 * @param directory destination directory.
	 * @param incoming source facility.
	 * @return merge classification.
	 */
	private MergeDecision merge(VetDirectory directory, VetFacility incoming) {
		for (VetFacility existing : directory.getFacilities()) {
			if (existing.getId().equals(incoming.getId())) {
				if (sameFacility(existing, incoming)) {
					return MergeDecision.DUPLICATE;
				}
				incoming.setId(UUID.randomUUID().toString());
				directory.getFacilities().add(incoming);
				return MergeDecision.CONFLICT;
			}
			if (sameAddressAndContact(existing, incoming)) {
				return MergeDecision.DUPLICATE;
			}
		}
		directory.getFacilities().add(incoming);
		return MergeDecision.ADDED;
	}

	/**
	 * Compares records sharing a stable ID.
	 *
	 * @param first existing record.
	 * @param second imported record.
	 * @return true when their principal identity/details match.
	 */
	private boolean sameFacility(VetFacility first, VetFacility second) {
		return normalize(first.getName()).equals(normalize(second.getName()))
				&& normalize(fullAddress(first)).equals(normalize(fullAddress(second)))
				&& new HashSet<>(normalizeContacts(first.getContactNumbers()))
						.equals(new HashSet<>(normalizeContacts(second.getContactNumbers())));
	}

	/**
	 * Cautiously deduplicates only exact normalized address/contact combinations.
	 *
	 * @param first existing record.
	 * @param second imported record.
	 * @return true when both address and nonempty contact sets match.
	 */
	private boolean sameAddressAndContact(VetFacility first, VetFacility second) {
		List<String> firstContacts = normalizeContacts(first.getContactNumbers());
		List<String> secondContacts = normalizeContacts(second.getContactNumbers());
		return !normalize(fullAddress(first)).isBlank()
				&& normalize(fullAddress(first)).equals(normalize(fullAddress(second)))
				&& !firstContacts.isEmpty() && firstContacts.equals(secondContacts);
	}

	/**
	 * Combines separate address fields for geocoding and cautious duplicate matching.
	 *
	 * @param facility veterinary facility.
	 * @return street, city, state, and postal code joined as one query string.
	 */
	private static String fullAddress(VetFacility facility) {
		return java.util.stream.Stream.of(facility.getAddress(), facility.getTown(), facility.getState(),
				facility.getPostalCode()).filter(value -> value != null && !value.isBlank())
				.collect(java.util.stream.Collectors.joining(", "));
	}

	/**
	 * Normalizes a contact list for cautious matching.
	 *
	 * @param contacts contact values.
	 * @return normalized sorted contacts.
	 */
	private static List<String> normalizeContacts(List<String> contacts) {
		return contacts.stream().map(VetDirectoryFileService::normalize).filter(value -> !value.isBlank()).sorted()
				.toList();
	}

	/**
	 * Normalizes whitespace and case for deduplication.
	 *
	 * @param value source text.
	 * @return normalized text.
	 */
	private static String normalize(String value) {
		return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
	}

	/**
	 * Makes an independent copy before merging.
	 *
	 * @param source source directory.
	 * @return copied directory.
	 */
	private VetDirectory copyDirectory(VetDirectory source) {
		VetDirectory copy = new VetDirectory();
		if (source != null) {
			for (VetFacility facility : source.getFacilities()) {
				copy.getFacilities().add(copyFacility(facility));
			}
			for (VetDirectory.Source item : source.getSources()) {
				VetDirectory.Source sourceCopy = new VetDirectory.Source();
				sourceCopy.setName(item.getName());
				sourceCopy.setFormat(item.getFormat());
				sourceCopy.setImportedAt(item.getImportedAt());
				copy.getSources().add(sourceCopy);
			}
		}
		return copy;
	}

	/**
	 * Copies all facility details, coordinates, and provenance.
	 *
	 * @param source source facility.
	 * @return independent facility copy.
	 */
	private VetFacility copyFacility(VetFacility source) {
		VetFacility copy = new VetFacility();
		copy.setId(source.getId());
		copy.setName(source.getName());
		copy.setTown(source.getTown());
		copy.setState(source.getState());
		copy.setPostalCode(source.getPostalCode());
		copy.setAddress(source.getAddress());
		copy.setContactNumbers(new ArrayList<>(source.getContactNumbers()));
		copy.setFax(source.getFax());
		copy.setAvailable24Hours(source.getAvailable24Hours());
		copy.setAvailabilityNotes(source.getAvailabilityNotes());
		copy.setCapabilities(source.getCapabilities());
		copy.setVerifiedAt(source.getVerifiedAt());
		if (source.getLatitude() != null && source.getLongitude() != null) {
			copy.setCoordinates(source.getLatitude(), source.getLongitude(), source.getGeocodingProvider(),
					source.getGeocodingProvenance());
		}
		copy.setDistance(source.getDistanceMeters(), source.getDurationSeconds(), source.getDistanceMethod(),
				source.getDistanceProvenance(), source.getCalculatedAt(), source.getStagingGeneration());
		copy.setSourceName(source.getSourceName());
		return copy;
	}

	/**
	 * Finds a facility by its stable identifier.
	 *
	 * @param directory regional directory.
	 * @param id identifier to find.
	 * @return matching record or null.
	 */
	private VetFacility findById(VetDirectory directory, String id) {
		return directory.getFacilities().stream().filter(facility -> facility.getId().equals(id))
				.findFirst().orElse(null);
	}

	/**
	 * Finds the first record index for a stable identifier.
	 *
	 * @param directory regional directory.
	 * @param id identifier to find.
	 * @return first matching index or -1.
	 */
	private int indexById(VetDirectory directory, String id) {
		for (int index = 0; index < directory.getFacilities().size(); index++) {
			if (directory.getFacilities().get(index).getId().equals(id)) {
				return index;
			}
		}
		return -1;
	}

	/**
	 * Removes a facility by stable identifier.
	 *
	 * @param directory regional directory.
	 * @param id identifier to remove.
	 */
	private void removeById(VetDirectory directory, String id) {
		directory.getFacilities().removeIf(facility -> facility.getId().equals(id));
	}

	/**
	 * Moves a completed temporary export into place.
	 *
	 * @param source temporary file.
	 * @param target destination path.
	 * @param overwrite whether existing content may be replaced.
	 * @throws IOException when move fails.
	 */
	private void move(Path source, Path target, boolean overwrite) throws IOException {
		try {
			if (overwrite) {
				Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			} else {
				Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
			}
		} catch (AtomicMoveNotSupportedException exception) {
			if (overwrite) {
				Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
			} else {
				Files.move(source, target);
			}
		}
	}

	/**
	 * Chooses CSV only for a .csv extension; JSON is the default.
	 *
	 * @param path file path.
	 * @return lower-case format.
	 */
	private static String format(Path path) {
		String filename = path.getFileName().toString().toLowerCase(Locale.ROOT);
		return filename.endsWith(".csv") ? "csv" : "json";
	}

	/**
	 * Normalizes a column heading.
	 *
	 * @param header source header.
	 * @return normalized header.
	 */
	private static String normalizeHeader(String header) {
		return header == null ? "" : header.replace("\uFEFF", "").toLowerCase(Locale.ROOT)
				.replaceAll("[^a-z0-9]", "");
	}

	/**
	 * Maps case- and spacing-insensitive supported CSV column names to facility fields.
	 *
	 * @param headers source CSV header row.
	 * @return ordered field mapping.
	 */
	private static List<CsvField> csvFields(List<String> headers) {
		List<CsvField> fields = new ArrayList<>();
		boolean hasName = false;
		boolean hasAddress = false;
		for (String header : headers) {
			String normalized = normalizeHeader(header);
			CsvField field = switch (normalized) {
				case "id", "facilityid" -> CsvField.ID;
				case "name", "hospitalname", "facilityname" -> CsvField.NAME;
				case "town", "city", "municipality" -> CsvField.TOWN;
				case "state", "province" -> CsvField.STATE;
				case "zip", "zipcode", "postalcode", "postal" -> CsvField.POSTAL_CODE;
				case "address", "streetaddress" -> CsvField.ADDRESS;
				case "contactnumbers", "contactnumber", "phone", "phonenumber", "telephone" ->
					CsvField.CONTACT_NUMBERS;
				case "fax", "faxnumber" -> CsvField.FAX;
				case "available24hours", "24hours", "open24hours" -> CsvField.AVAILABLE_24_HOURS;
				case "availabilitynotes" -> CsvField.AVAILABILITY_NOTES;
				case "capabilities", "notes" -> CsvField.CAPABILITIES;
				case "verifiedat" -> CsvField.VERIFIED_AT;
				case "latitude", "lat" -> CsvField.LATITUDE;
				case "longitude", "lon", "lng" -> CsvField.LONGITUDE;
				default -> null;
			};
			fields.add(field);
			hasName |= field == CsvField.NAME;
			hasAddress |= field == CsvField.ADDRESS;
		}
		if (!hasName || !hasAddress) {
			throw new IllegalArgumentException("CSV must include Hospital Name/Name and Address columns");
		}
		return fields;
	}

	/**
	 * Replaces a missing CSV field value with empty text.
	 *
	 * @param value source cell.
	 * @return non-null cell text.
	 */
	private static String value(String value) {
		return value == null ? "" : value;
	}

	/**
	 * Supported imported CSV facility columns.
	 */
	private enum CsvField {
		ID, NAME, TOWN, STATE, POSTAL_CODE, ADDRESS, CONTACT_NUMBERS, FAX, AVAILABLE_24_HOURS,
		AVAILABILITY_NOTES, CAPABILITIES, VERIFIED_AT, LATITUDE, LONGITUDE
	}

	/**
	 * Parses multiple contacts separated by semicolons.
	 *
	 * @param contacts source field.
	 * @return individual nonblank contacts.
	 */
	private static List<String> parseContacts(String contacts) {
		return java.util.Arrays.stream(contacts.split(";")).map(String::trim).filter(value -> !value.isBlank()).toList();
	}

	/**
	 * Parses an explicit 24-hour availability value without promoting unknown to yes.
	 *
	 * @param value source status.
	 * @return true, false, or null.
	 */
	private static Boolean parseAvailability(String value) {
		if (value.isBlank() || "unknown".equalsIgnoreCase(value)) {
			return null;
		}
		if ("yes".equalsIgnoreCase(value) || "true".equalsIgnoreCase(value)) {
			return true;
		}
		if ("no".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
			return false;
		}
		throw new IllegalArgumentException("available24Hours must be Yes, No, or Unknown");
	}

	/**
	 * Converts a nullable availability state for CSV output.
	 *
	 * @param value availability state.
	 * @return Yes, No, or Unknown.
	 */
	private static String availabilityText(Boolean value) {
		return value == null ? "Unknown" : value ? "Yes" : "No";
	}

	/**
	 * Parses one optional numeric CSV cell.
	 *
	 * @param value source number.
	 * @param field field name for diagnostics.
	 * @return parsed value or null.
	 */
	private static Double optionalNumber(String value, String field) {
		if (value.isBlank()) {
			return null;
		}
		try {
			double number = Double.parseDouble(value);
			if (!Double.isFinite(number)) {
				throw new NumberFormatException();
			}
			return number;
		} catch (NumberFormatException exception) {
			throw new IllegalArgumentException(field + " must be a finite number", exception);
		}
	}

	/**
	 * Returns a stable message without exposing full input paths.
	 *
	 * @param exception source failure.
	 * @return safe diagnostic.
	 */
	private static String safeMessage(Exception exception) {
		String message = exception.getMessage();
		return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
	}

	/**
	 * One regional directory import report.
	 *
	 * @param directory merged incident-owned records.
	 * @param duplicateCount skipped duplicates.
	 * @param conflicts conflicting IDs retained as new records.
	 * @param errors partial parse/import errors.
	 * @param conflictRecords identifiers needed for explicit conflict resolution.
	 */
	public record ImportResult(VetDirectory directory, int duplicateCount, List<String> conflicts, List<String> errors,
			List<ConflictRecord> conflictRecords) {
	}

	/**
	 * One stable-ID collision between an existing record and an imported record.
	 *
	 * @param existingId retained record identifier.
	 * @param importedId imported record identifier in the preview.
	 * @param importedName imported record display name.
	 * @param sourceName source filename.
	 */
	public record ConflictRecord(String existingId, String importedId, String importedName, String sourceName) {
	}

	/**
	 * Operator decision for a conflicting stable-ID record.
	 */
	public enum ConflictResolution {
		KEEP_BOTH,
		REPLACE_EXISTING,
		SKIP_IMPORTED
	}

	/**
	 * Merge classification for one facility.
	 */
	private enum MergeDecision {
		ADDED,
		DUPLICATE,
		CONFLICT
	}

	/**
	 * Versioned portable JSON format.
	 *
	 * @param schemaVersion regional file format version.
	 * @param facilities regional facility records.
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	private record DirectoryFile(int schemaVersion, List<VetFacility> facilities) {
	}
}
