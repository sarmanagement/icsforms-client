/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.persistence;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.persistence.ImportValidationException.Problem;
import org.sarmanagement.icsforms.persistence.ImportValidationException.Severity;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/** Separates strict external import validation from tolerant on-disk compatibility. */
final class IncidentDocumentReader {
	private static final Logger LOGGER = Logger.getLogger(IncidentDocumentReader.class.getName());
	private static final int MAX_PROBLEMS = 100;
	private static final ObjectMapper TOLERANT = JsonMapper.builder().addModule(new JavaTimeModule())
			.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
			.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
	private static final ObjectMapper STRICT = JsonMapper.builder().addModule(new JavaTimeModule())
			.enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
			.disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
			.disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
			.enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
			.enable(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
			.enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
	private static final Set<String> CONTENT_PROPERTIES = Set.of("incidentContext", "organizationalChart",
			"medicalUnitLeaderAssignment", "form201", "form202", "form215a", "form208", "form207", "form204",
			"form205", "additionalForms204", "activityLogs", "sarTaskAssignments", "archivedSafetyHazardReports",
			"commonSafetyHazards", "clueLogEntries", "openActionItems", "tcards", "medicalPlan", "canineMedicalPlan",
			"vetDirectory", "stagingLocation", "incidentLocations", "operationalPeriodHistory", "safetyReview",
			"humanMedicalReview", "canineMedicalReview", "communicationsReview");
	private static final Set<String> CHILD_IDENTITIES = Set.of("formId", "resourceId", "objectiveId", "assignmentId",
			"taskId", "channelId", "entryId", "id");
	private final List<Problem> problems = new ArrayList<>();
	private final Map<JavaType, JsonNode> defaultValues = new HashMap<>();
	private Throwable cause;

	/** Creates an isolated diagnostic collector for one document. */
	IncidentDocumentReader() {
	}

	/**
	 * Reads and validates an external import without modifying its source.
	 *
	 * @param path external document
	 * @return validated document
	 * @throws ImportValidationException when the document is invalid
	 */
	AppData readImport(Path path) {
		String schema = "unknown";
		log(path, schema, "attempt");
		try {
			JsonNode tree = STRICT.readTree(path.toFile());
			if (tree == null || !tree.isObject()) {
				add(Severity.ERROR, "$", "Expected an incident JSON object.", "Export an incident as a JSON object.");
			} else {
				JsonNode version = tree.get("schemaVersion");
				if (version == null) {
					add(Severity.WARNING, "$.schemaVersion", "Schema version is missing.",
							"Re-export with this client to include the schema version.");
				} else if (!version.isIntegralNumber() || !version.canConvertToInt() || version.intValue() < 1) {
					add(Severity.ERROR, "$.schemaVersion", "Schema version must be a positive integer.",
							"Use a supported integer schema version.");
				} else {
					schema = Integer.toString(version.intValue());
					if (version.intValue() > AppData.CURRENT_SCHEMA_VERSION) {
						add(Severity.ERROR, "$.schemaVersion", "File schema " + version.intValue()
								+ " is newer than supported schema " + AppData.CURRENT_SCHEMA_VERSION
								+ "; update icsforms-client", "Update icsforms-client before importing this file.");
					}
				}
				inspect(tree, STRICT.constructType(AppData.class), "$", true);
				JsonNode name = tree.path("incidentContext").path("incidentName");
				if (!name.isTextual() || name.asText().isBlank()) {
					add(Severity.WARNING, "$.incidentContext.incidentName", "Incident name is missing.",
							"Set an incident name after importing.");
				}
				if (!hasErrors()) {
					AppData content = TOLERANT.treeToValue(tree, AppData.class);
					if (!hasContent(TOLERANT.valueToTree(content), TOLERANT.valueToTree(new AppData()),
							TOLERANT.constructType(AppData.class), true)) {
						add(Severity.ERROR, "$", "File does not appear to be an icsforms incident export",
								"Provide incident context, form, task, T-card, or log content.");
					}
				}
			}
			if (!hasErrors()) {
				AppData data = STRICT.treeToValue(tree, AppData.class);
				log(path, schema, "validated");
				return data;
			}
		} catch (IOException e) {
			cause = cause == null ? e : cause;
			String location = "";
			if (e instanceof JsonProcessingException parsing && parsing.getLocation() != null
					&& parsing.getLocation().getLineNr() > 0) {
				location = " at line " + parsing.getLocation().getLineNr()
						+ ", column " + parsing.getLocation().getColumnNr();
			}
			if (e instanceof JsonMappingException mapping) {
				String fix = "Check this property's JSON type and structure.";
				if (mapping instanceof MismatchedInputException mismatch && mismatch.getTargetType() != null
						&& mismatch.getTargetType().isEnum()) {
					fix = enumValues(mismatch.getTargetType());
				}
				add(Severity.ERROR, mappingPath(mapping), "Invalid incident value" + location + ".", fix);
			} else {
				add(Severity.ERROR, "$", "Cannot parse or read incident JSON" + location + ".",
						"Check JSON syntax, file access, and property types.");
			}
		}
		log(path, schema, "rejected");
		throw new ImportValidationException(path, problems, cause);
	}

	/**
	 * Reads stored data tolerantly, reporting ignored properties without rejecting legacy content.
	 *
	 * @param path stored document
	 * @return loaded incident
	 * @throws IOException when the stored document cannot be read
	 */
	AppData readStored(Path path) throws IOException {
		JsonNode tree = TOLERANT.readTree(path.toFile());
		if (tree == null || !tree.isObject()) {
			throw new IOException("Stored incident must be a JSON object");
		}
		JsonNode version = tree.get("schemaVersion");
		if (version != null && version.canConvertToInt() && version.intValue() > AppData.CURRENT_SCHEMA_VERSION) {
			throw new IllegalStateException("Incident schema is newer than supported schema "
					+ AppData.CURRENT_SCHEMA_VERSION + ": " + path);
		}
		inspect(tree, TOLERANT.constructType(AppData.class), "$", false);
		if (!problems.isEmpty()) {
			log(path, version != null && version.isIntegralNumber() ? version.asText() : "unknown", "stored-warning");
		}
		return TOLERANT.treeToValue(tree, AppData.class);
	}

	/**
	 * Returns the immutable diagnostics from this read.
	 *
	 * @return collected problems
	 */
	List<Problem> problems() {
		return List.copyOf(problems);
	}

	/**
	 * Reports the storage result separately from successful validation.
	 *
	 * @param path source document
	 * @param schema persisted document schema
	 * @param outcome storage outcome
	 */
	void logOutcome(Path path, int schema, String outcome) {
		log(path, Integer.toString(schema), outcome);
	}

	/**
	 * Walks declared Jackson properties, validating independent values to aggregate errors.
	 *
	 * @param node JSON value
	 * @param type declared property type
	 * @param path JSON path
	 * @param strict whether unknown fields and binding errors reject the import
	 */
	private void inspect(JsonNode node, JavaType type, String path, boolean strict) {
		if (problems.size() >= MAX_PROBLEMS) {
			return;
		}
		if (type.isCollectionLikeType() && node.isArray()) {
			for (int i = 0; i < node.size() && problems.size() < MAX_PROBLEMS; i++) {
				String elementPath = path + "[" + i + "]";
				JavaType elementType = type.getContentType();
				if (strict && node.get(i).isNull()
						&& elementType.getRawClass().getPackageName().startsWith("org.sarmanagement")) {
					if (cause == null) {
						cause = MismatchedInputException.from(node.traverse(STRICT), elementType,
								"Null incident records are not permitted.");
					}
					add(Severity.ERROR, elementPath, "Null incident records are not permitted.",
							"Remove the null entry or replace it with a record object.");
				} else {
					inspect(node.get(i), elementType, elementPath, strict);
				}
			}
		} else if (type.isMapLikeType() && node.isObject()) {
			node.fields().forEachRemaining(entry ->
					inspect(entry.getValue(), type.getContentType(), path + "." + entry.getKey(), strict));
		} else if (node.isObject() && type.getRawClass().getPackageName().startsWith("org.sarmanagement")) {
			Map<String, JavaType> properties = new HashMap<>();
			for (BeanPropertyDefinition property : TOLERANT.getDeserializationConfig().introspect(type).findProperties()) {
				properties.put(property.getName(), property.couldDeserialize() ? property.getPrimaryType() : null);
				JsonAlias aliases = property.getPrimaryMember() == null ? null
						: property.getPrimaryMember().getAnnotation(JsonAlias.class);
				if (aliases != null) {
					for (String alias : aliases.value()) {
						properties.put(alias, property.getPrimaryType());
					}
				}
			}
			node.fields().forEachRemaining(entry -> {
				String childPath = path + "." + entry.getKey();
				JavaType childType = properties.get(entry.getKey());
				if (!properties.containsKey(entry.getKey())) {
					if (strict && cause == null) {
						cause = UnrecognizedPropertyException.from(node.traverse(STRICT), type.getRawClass(),
								entry.getKey(), new ArrayList<>(properties.keySet()));
					}
					add(strict ? Severity.ERROR : Severity.WARNING, childPath, "Unknown property.",
							strict ? "Remove or correct the property name." : "This client ignores this property.");
				} else if (childType != null) {
					inspect(entry.getValue(), childType, childPath, strict);
				}
			});
		} else if (strict) {
			try {
				if (type.hasRawClass(String.class) && !node.isTextual() && !node.isNull()) {
					add(Severity.ERROR, path, "Expected a text value.", "Use a JSON string.");
					return;
				}
				STRICT.readerFor(type).readValue(node.traverse(STRICT));
			} catch (IOException e) {
				cause = cause == null ? e : cause;
				String fix = "Use a value of type " + type.getRawClass().getSimpleName() + ".";
				if (type.isEnumType()) {
					fix = enumValues(type.getRawClass());
				} else if (type.getRawClass().getPackageName().equals("java.time")) {
					fix = "Use an ISO-8601 " + type.getRawClass().getSimpleName() + " value.";
				}
				add(Severity.ERROR, path, "Invalid value for " + type.getRawClass().getSimpleName() + ".", fix);
			}
		}
	}

	/**
	 * Translates Jackson's binding references into the same JSON paths as tree validation.
	 *
	 * @param failure original mapping failure
	 * @return JSON property/index path rooted at the document
	 */
	private String mappingPath(JsonMappingException failure) {
		StringBuilder path = new StringBuilder("$");
		for (JsonMappingException.Reference reference : failure.getPath()) {
			if (reference.getFieldName() != null) {
				path.append('.').append(reference.getFieldName());
			} else if (reference.getIndex() >= 0) {
				path.append('[').append(reference.getIndex()).append(']');
			}
		}
		return path.toString();
	}

	/**
	 * Describes valid enum JSON literals without substituting operator-facing labels.
	 *
	 * @param type enum class
	 * @return corrective action listing valid literal names
	 */
	private String enumValues(Class<?> type) {
		return "Valid values: " + java.util.Arrays.stream(type.getEnumConstants())
				.map(value -> ((Enum<?>) value).name()).toList() + ".";
	}

	/**
	 * Detects non-default substantive content while excluding document identity and preferences.
	 *
	 * @param node source subtree
	 * @param defaults corresponding default subtree
	 * @param type declared subtree type
	 * @param root whether metadata exclusions apply
	 * @return true if substantive content exists
	 */
	private boolean hasContent(JsonNode node, JsonNode defaults, JavaType type, boolean root) {
		if (node == null || node.isNull()) {
			return false;
		}
		if (node.isObject() && type != null && type.isMapLikeType()) {
			var entries = node.fields();
			while (entries.hasNext()) {
				var entry = entries.next();
				if (hasContent(entry.getValue(), defaults == null ? null : defaults.get(entry.getKey()),
						type.getContentType(), false)) {
					return true;
				}
			}
			return false;
		}
		if (node.isObject()) {
			Map<String, JavaType> properties = new HashMap<>();
			if (type != null) {
				for (BeanPropertyDefinition property : TOLERANT.getDeserializationConfig().introspect(type).findProperties()) {
					if (property.couldDeserialize()) {
						properties.put(property.getName(), property.getPrimaryType());
						JsonAlias aliases = property.getPrimaryMember() == null ? null
								: property.getPrimaryMember().getAnnotation(JsonAlias.class);
						if (aliases != null) {
							for (String alias : aliases.value()) {
								properties.put(alias, property.getPrimaryType());
							}
						}
					}
				}
			}
			var fields = node.fields();
			while (fields.hasNext()) {
				var field = fields.next();
				if (properties.containsKey(field.getKey()) && (!root || CONTENT_PROPERTIES.contains(field.getKey()))
						&& !CHILD_IDENTITIES.contains(field.getKey())
						&& hasContent(field.getValue(), defaults == null ? null : defaults.get(field.getKey()),
								properties.get(field.getKey()), false)) {
					return true;
				}
			}
			return false;
		}
		if (node.isArray()) {
			if (node.equals(defaults)) {
				return false;
			}
			JavaType elementType = type == null ? null : type.getContentType();
			JsonNode elementDefaults = defaultValue(elementType);
			for (JsonNode element : node) {
				if (hasContent(element, elementDefaults, elementType, false)) {
					return true;
				}
			}
			return false;
		}
		if (node.isTextual() && node.asText().isBlank()) {
			return false;
		}
		if (node.isNumber() && defaults != null && defaults.isNumber()) {
			return node.decimalValue().compareTo(defaults.decimalValue()) != 0;
		}
		return !node.equals(defaults);
	}

	/**
	 * Gets default values for a record type so empty collection records do not count as content.
	 *
	 * @param type collection element type
	 * @return default element tree, or null for scalars and unsupported default construction
	 */
	private JsonNode defaultValue(JavaType type) {
		if (type == null || type.isEnumType()
				|| !type.getRawClass().getPackageName().startsWith("org.sarmanagement")) {
			return null;
		}
		if (!defaultValues.containsKey(type)) {
			try {
				defaultValues.put(type, TOLERANT.valueToTree(TOLERANT.readerFor(type).readValue("{}")));
			} catch (IOException e) {
				defaultValues.put(type, null);
			}
		}
		return defaultValues.get(type);
	}

	/**
	 * Appends one bounded diagnostic.
	 *
	 * @param severity classification
	 * @param path JSON path
	 * @param message safe explanation
	 * @param fix corrective action
	 */
	private void add(Severity severity, String path, String message, String fix) {
		if (problems.size() < MAX_PROBLEMS) {
			problems.add(new Problem(severity, path, message, fix));
		}
	}

	/**
	 * Determines whether validation failed.
	 *
	 * @return true when at least one error exists
	 */
	private boolean hasErrors() {
		return problems.stream().anyMatch(problem -> problem.severity() == Severity.ERROR);
	}

	/**
	 * Logs bounded diagnostic paths and classifications, never values or exception messages.
	 *
	 * @param path document path
	 * @param schema schema version or unknown
	 * @param outcome read result
	 */
	private void log(Path path, String schema, String outcome) {
		String first = problems.stream().limit(3)
				.map(problem -> problem.severity() + " " + safe(problem.path()) + ": " + safe(problem.message()))
				.toList().toString();
		String message = "Incident read path=" + safe(path.toString()) + " schema=" + schema + " outcome=" + outcome
				+ " count=" + problems.size() + " first=" + first;
		if ("rejected".equals(outcome) || "stored-warning".equals(outcome) || "import-failed".equals(outcome)) {
			LOGGER.warning(message);
		} else {
			LOGGER.info(message);
		}
	}

	/**
	 * Bounds log fields and removes control characters to prevent multiline log injection.
	 *
	 * @param text untrusted path text
	 * @return single-line bounded text
	 */
	private String safe(String text) {
		String cleaned = text.replaceAll("[\\p{Cntrl}\\p{Zl}\\p{Zp}]", "?");
		return cleaned.substring(0, Math.min(cleaned.length(), 240));
	}
}
