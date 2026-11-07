/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Preserves unknown stored JSON properties without associating them with the wrong record.
 */
final class StoredJsonMerger {
	private static final List<String> ID_FIELDS = List.of("id", "entryId", "changeId", "resultId", "formId",
			"channelId", "logId", "worksheetId", "objectiveId", "resourceId", "assignmentId", "taskId", "recordId",
			"incidentId");

	/**
	 * Prevents construction of this utility class.
	 */
	private StoredJsonMerger() {
	}

	/**
	 * Overlays updated known properties on stored JSON while retaining unknown properties on matching records.
	 *
	 * @param mapper mapper used to create JSON containers
	 * @param original stored JSON
	 * @param updated serialized model
	 * @return merged JSON
	 * @throws IllegalStateException when unknown data cannot be associated safely with an updated record
	 */
	static JsonNode merge(ObjectMapper mapper, JsonNode original, JsonNode updated) {
		return merge(mapper, original, updated, "$");
	}

	/**
	 * Removes host-only acceptance timestamps from every provenance object in a
	 * locally stored document.
	 *
	 * @param node JSON tree to sanitize in place.
	 */
	static void removeAcceptedAt(JsonNode node) {
		if (node == null) {
			return;
		}
		if (node.isObject()) {
			ObjectNode object = (ObjectNode) node;
			JsonNode provenance = object.get("provenance");
			if (provenance instanceof ObjectNode provenanceObject) {
				provenanceObject.remove("acceptedAt");
			}
			object.elements().forEachRemaining(StoredJsonMerger::removeAcceptedAt);
		} else if (node.isArray()) {
			node.elements().forEachRemaining(StoredJsonMerger::removeAcceptedAt);
		}
	}

	/**
	 * Recursively merges an object, array, or scalar value at a known JSON path.
	 *
	 * @param mapper mapper used to create JSON containers
	 * @param original stored JSON value
	 * @param updated serialized model value
	 * @param path JSON path for diagnostic errors
	 * @return merged JSON value
	 */
	private static JsonNode merge(ObjectMapper mapper, JsonNode original, JsonNode updated, String path) {
		if (original != null && original.isObject() && updated.isObject()) {
			ObjectNode merged = original.deepCopy();
			updated.fields().forEachRemaining(entry -> {
				JsonNode previous = original.get(entry.getKey());
				merged.set(entry.getKey(), previous == null ? entry.getValue().deepCopy()
						: merge(mapper, previous, entry.getValue(), path + "." + entry.getKey()));
			});
			return merged;
		}
		if (original != null && original.isArray() && updated.isArray()) {
			return mergeArray(mapper, (ArrayNode) original, (ArrayNode) updated, path);
		}
		if (original != null && original.isNumber() && updated.isNumber()
				&& original.decimalValue().compareTo(updated.decimalValue()) == 0) {
			return original.deepCopy();
		}
		return updated.deepCopy();
	}

	/**
	 * Matches object records by a persistent ID, never by their array position.
	 *
	 * @param mapper mapper used to create JSON containers
	 * @param original stored array
	 * @param updated serialized model array
	 * @param path JSON path for diagnostic errors
	 * @return array in updated order with unknown fields retained on matching IDs
	 */
	private static ArrayNode mergeArray(ObjectMapper mapper, ArrayNode original, ArrayNode updated, String path) {
		String idField = identityField(original, updated);
		if (idField == null) {
			return mergeWithoutIdentity(mapper, original, updated, path);
		}

		Map<String, JsonNode> originalById;
		try {
			originalById = indexById(original, idField, path, "stored");
		} catch (IllegalStateException exception) {
			if (exception.getMessage().contains("without stable IDs")) {
				return mergeWithoutIdentity(mapper, original, updated, path);
			}
			throw exception;
		}
		Map<String, JsonNode> updatedById = indexById(updated, idField, path, "updated");
		ArrayNode merged = mapper.createArrayNode();
		for (JsonNode record : updated) {
			String id = key(record.path(idField).asText());
			JsonNode previous = originalById.get(id);
			merged.add(previous == null ? record.deepCopy() : merge(mapper, previous, record, path + "[" + id + "]"));
		}
		return merged;
	}

	/**
	 * Handles legacy arrays without usable IDs without guessing record associations.
	 *
	 * @param mapper mapper used to create JSON containers
	 * @param original stored array
	 * @param updated serialized model array
	 * @param path JSON array path
	 * @return updated array when no unknown data is at risk
	 */
	private static ArrayNode mergeWithoutIdentity(ObjectMapper mapper, ArrayNode original, ArrayNode updated,
			String path) {
		if (!containsObjectRecords(original) || original.isEmpty()) {
			return updated.deepCopy();
		}
		Map<Integer, JsonNode> originalsByUpdatedIndex = new HashMap<>();
		Set<Integer> claimedUpdatedIndices = new java.util.HashSet<>();
		for (JsonNode oldRecord : original) {
			boolean unknown = hasUnknownProperties(oldRecord, updated);
			int match = -1;
			int matchCount = 0;
			for (int index = 0; index < updated.size(); index++) {
				if (matchesKnownValues(oldRecord, updated.get(index))) {
					match = index;
					matchCount++;
				}
			}
			if (matchCount == 1 && claimedUpdatedIndices.add(match)) {
				originalsByUpdatedIndex.put(match, oldRecord);
			} else if (unknown) {
				throw new IllegalStateException(
						"Cannot safely merge stored records without stable IDs at " + path);
			}
		}
		ArrayNode merged = mapper.createArrayNode();
		for (int index = 0; index < updated.size(); index++) {
			JsonNode oldRecord = originalsByUpdatedIndex.get(index);
			JsonNode newRecord = updated.get(index);
			merged.add(oldRecord == null ? newRecord.deepCopy()
					: merge(mapper, oldRecord, newRecord, path + "[unique legacy record]"));
		}
		return merged;
	}

	/**
	 * Checks whether every stored property is represented in the current serialized shape.
	 *
	 * @param original stored value
	 * @param updated current serialized value
	 * @return true when the stored value has properties that cannot be recognized safely
	 */
	private static boolean hasUnknownProperties(JsonNode original, JsonNode updated) {
		if (original.isObject() && updated.isArray()) {
			for (JsonNode candidate : updated) {
				if (knownShape(original, candidate)) {
					return false;
				}
			}
			return true;
		}
		return !knownShape(original, updated);
	}

	/**
	 * Compares property shapes recursively without using array positions as record identity.
	 *
	 * @param original stored value
	 * @param updated current serialized value
	 * @return true when the stored shape is represented by the current shape
	 */
	private static boolean knownShape(JsonNode original, JsonNode updated) {
		if (original == null || original.isNull()) {
			return true;
		}
		if (original.isObject()) {
			if (updated == null || !updated.isObject()) {
				return original.isEmpty();
			}
			var fields = original.fields();
			while (fields.hasNext()) {
				var field = fields.next();
				if (!updated.has(field.getKey())) {
					return false;
				}
				if (updated.has(field.getKey()) && !knownShape(field.getValue(), updated.get(field.getKey()))) {
					return false;
				}
			}
			return true;
		}
		if (original.isArray()) {
			if (original.isEmpty()) {
				return true;
			}
			if (updated == null || !updated.isArray() || updated.isEmpty()) {
				return false;
			}
			for (JsonNode oldRecord : original) {
				boolean recognized = false;
				for (JsonNode newRecord : updated) {
					if (knownShape(oldRecord, newRecord)) {
						recognized = true;
						break;
					}
				}
				if (!recognized) {
					return false;
				}
			}
			return true;
		}
		return true;
	}

	/**
	 * Determines whether known stored values uniquely describe a current record candidate.
	 *
	 * @param original stored record
	 * @param updated current serialized record
	 * @return true when at least one known value agrees and no comparable value conflicts
	 */
	private static boolean matchesKnownValues(JsonNode original, JsonNode updated) {
		if (!original.isObject() || !updated.isObject()) {
			return original.equals(updated);
		}
		boolean comparable = false;
		var fields = original.fields();
		while (fields.hasNext()) {
			var field = fields.next();
			if (ID_FIELDS.contains(field.getKey()) || !updated.has(field.getKey())) {
				continue;
			}
			JsonNode current = updated.get(field.getKey());
			if (field.getValue().isObject() && current.isObject()) {
				if (!matchesKnownValues(field.getValue(), current)) {
					return false;
				}
				comparable = true;
			} else if (field.getValue().isArray() && current.isArray()) {
				if (field.getValue().isEmpty()) {
					continue;
				}
				Set<Integer> matchedIndices = new java.util.HashSet<>();
				for (JsonNode oldItem : field.getValue()) {
					int match = -1;
					int matches = 0;
					for (int index = 0; index < current.size(); index++) {
						JsonNode newItem = current.get(index);
						if (matchesKnownValues(oldItem, newItem)) {
							match = index;
							matches++;
						}
					}
					if (matches != 1 || !matchedIndices.add(match)) {
						return false;
					}
				}
				comparable = true;
			} else {
				if (!field.getValue().equals(current)) {
					return false;
				}
				comparable |= isInformativeValue(field.getValue());
			}
		}
		return comparable;
	}

	/**
	 * Distinguishes meaningful known values from default or empty values during legacy matching.
	 *
	 * @param value JSON scalar value
	 * @return true when the value can help identify a record
	 */
	private static boolean isInformativeValue(JsonNode value) {
		if (value.isTextual()) {
			return !value.asText().isBlank();
		}
		if (value.isNumber()) {
			return value.decimalValue().signum() != 0;
		}
		return value.isBoolean() && value.booleanValue();
	}

	/**
	 * Chooses the first persistent ID property present on every updated or stored object record.
	 *
	 * @param original stored array
	 * @param updated serialized model array
	 * @return canonical ID property or null for non-record arrays
	 */
	private static String identityField(ArrayNode original, ArrayNode updated) {
		for (String field : ID_FIELDS) {
			if (containsIdentityField(updated, field) || updated.isEmpty() && containsIdentityField(original, field)) {
				return field;
			}
		}
		return null;
	}

	/**
	 * Returns whether all object records in an array expose the candidate persistent ID.
	 *
	 * @param array candidate records
	 * @param field candidate ID property
	 * @return true when the property occurs on an object record
	 */
	private static boolean containsIdentityField(ArrayNode array, String field) {
		for (JsonNode element : array) {
			if (element.isObject() && element.has(field)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Indexes a record array and rejects missing or duplicate IDs instead of guessing associations.
	 *
	 * @param records record array
	 * @param idField persistent ID property
	 * @param path JSON array path
	 * @param label stored or updated role in the merge
	 * @return records keyed by ID
	 */
	private static Map<String, JsonNode> indexById(ArrayNode records, String idField, String path, String label) {
		Map<String, JsonNode> recordsById = new HashMap<>();
		for (JsonNode record : records) {
			if (!record.isObject()) {
				throw new IllegalStateException("Cannot safely merge non-object record at " + path);
			}
			JsonNode id = record.get(idField);
			if (id == null || !id.isTextual() || id.asText().isBlank()) {
				throw new IllegalStateException("Cannot safely merge " + label
						+ " records without stable IDs (" + idField + ") at " + path);
			}
			if (recordsById.putIfAbsent(key(id.asText()), record) != null) {
				throw new IllegalStateException("Cannot safely merge duplicate " + idField + " values at " + path);
			}
		}
		return recordsById;
	}

	/**
	 * Normalizes UUID text for case-insensitive ID matching.
	 *
	 * @param id persistent record ID
	 * @return normalized ID key
	 */
	private static String key(String id) {
		return id.toLowerCase(java.util.Locale.ROOT);
	}

	/**
	 * Checks whether an array contains JSON object records rather than scalar values.
	 *
	 * @param array JSON array
	 * @return true if any element is an object
	 */
	private static boolean containsObjectRecords(ArrayNode array) {
		for (JsonNode element : array) {
			if (element.isObject()) {
				return true;
			}
		}
		return false;
	}
}
