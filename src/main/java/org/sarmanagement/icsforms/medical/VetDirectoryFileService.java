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
import java.util.Set;
import java.util.UUID;

/**
 * Reads and explicitly exports reusable regional veterinary data as JSON or CSV.
 */
public class VetDirectoryFileService {
	/** Version of the portable regional JSON document. */
	public static final int JSON_SCHEMA_VERSION = 1;
	private static final List<String> CSV_HEADERS = List.of("id", "name", "town", "address", "contactNumbers",
			"available24Hours", "availabilityNotes", "capabilities", "verifiedAt", "latitude", "longitude");
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
				facility.setSourceName(file.getFileName().toString());
				MergeDecision decision = merge(merged, facility);
				if (decision == MergeDecision.DUPLICATE) {
					duplicates++;
				} else if (decision == MergeDecision.CONFLICT) {
					conflicts.add(file.getFileName() + ": conflicting ID retained with a new ID for "
							+ facility.getName());
				}
			}
		}
		return new ImportResult(merged, duplicates, conflicts, errors);
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
		List<String> header = rows.get(0).stream().map(VetDirectoryFileService::normalizeHeader).toList();
		List<String> expectedHeaders = CSV_HEADERS.stream().map(VetDirectoryFileService::normalizeHeader).toList();
		if (!header.equals(expectedHeaders)) {
			throw new IllegalArgumentException("CSV header must be: " + String.join(",", CSV_HEADERS));
		}
		List<VetFacility> facilities = new ArrayList<>();
		for (int rowIndex = 1; rowIndex < rows.size(); rowIndex++) {
			List<String> row = rows.get(rowIndex);
			if (row.size() != CSV_HEADERS.size()) {
				errors.add(file.getFileName() + ": row " + (rowIndex + 1) + " has " + row.size()
						+ " fields; expected " + CSV_HEADERS.size());
				continue;
			}
			try {
				facilities.add(csvFacility(row));
			} catch (IllegalArgumentException exception) {
				errors.add(file.getFileName() + ": row " + (rowIndex + 1) + ": " + safeMessage(exception));
			}
		}
		return facilities;
	}

	/**
	 * Converts one parsed CSV row into a validated regional entry.
	 *
	 * @param row CSV cells in documented order.
	 * @return parsed facility.
	 */
	private VetFacility csvFacility(List<String> row) {
		VetFacility facility = new VetFacility();
		facility.setId(row.get(0));
		facility.setName(row.get(1));
		facility.setTown(row.get(2));
		facility.setAddress(row.get(3));
		facility.setContactNumbers(parseContacts(row.get(4)));
		facility.setAvailable24Hours(parseAvailability(row.get(5)));
		facility.setAvailabilityNotes(row.get(6));
		facility.setCapabilities(row.get(7));
		if (!row.get(8).isBlank()) {
			try {
				facility.setVerifiedAt(Instant.parse(row.get(8)));
			} catch (RuntimeException exception) {
				throw new IllegalArgumentException("verifiedAt must be ISO-8601", exception);
			}
		}
		Double latitude = optionalNumber(row.get(9), "latitude");
		Double longitude = optionalNumber(row.get(10), "longitude");
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
						facility.getAddress(), String.join(";", facility.getContactNumbers()),
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
				&& normalize(first.getAddress()).equals(normalize(second.getAddress()))
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
		return !normalize(first.getAddress()).isBlank()
				&& normalize(first.getAddress()).equals(normalize(second.getAddress()))
				&& !firstContacts.isEmpty() && firstContacts.equals(secondContacts);
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
		copy.setAddress(source.getAddress());
		copy.setContactNumbers(new ArrayList<>(source.getContactNumbers()));
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
		return header == null ? "" : header.trim().replace("\uFEFF", "").toLowerCase(Locale.ROOT);
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
	 */
	public record ImportResult(VetDirectory directory, int duplicateCount, List<String> conflicts, List<String> errors) {
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
