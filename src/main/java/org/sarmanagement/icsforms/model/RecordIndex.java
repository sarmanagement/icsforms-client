/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Ordered flattened view of every identified record in an incident document.
 */
public final class RecordIndex {
	private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());
	private final Map<RecordKey, JsonNode> records;
	private final Map<RecordKey, RecordKey> parents;
	private final Map<RecordKey, String> parentFields;
	private final Map<RecordKey, Object> values;

	/**
	 * Creates an index from ordered snapshots, parent links, and model objects.
	 *
	 * @param records record snapshots
	 * @param parents immediate parent keys
	 * @param parentFields containing fields
	 * @param values identified model objects
	 */
	private RecordIndex(Map<RecordKey, JsonNode> records, Map<RecordKey, RecordKey> parents,
			Map<RecordKey, String> parentFields, Map<RecordKey, Object> values) {
		this.records = records;
		this.parents = parents;
		this.parentFields = parentFields;
		this.values = values;
	}

	/**
	 * Flattens all identified records reachable from a document.
	 *
	 * @param data incident document
	 * @return deterministic record index with each child represented by its ID in its parent's snapshot
	 */
	public static RecordIndex of(AppData data) {
		Objects.requireNonNull(data, "data");
		Builder builder = new Builder();
		builder.visit(data, null, null, "$", new IdentityHashMap<>());
		return new RecordIndex(builder.sortedRecords(), builder.sortedParents(),
				builder.sortedParentFields(), builder.sortedValues());
	}

	/**
	 * Returns record snapshots in deterministic type-and-ID order.
	 *
	 * @return unmodifiable ordered record map
	 */
	public Map<RecordKey, JsonNode> records() {
		return records;
	}

	/**
	 * Returns the record's immediate parent, or {@code null} for top-level records.
	 *
	 * @param key record key
	 * @return immediate parent key, or {@code null}
	 */
	public RecordKey parentOf(RecordKey key) {
		return parents.get(key);
	}

	/**
	 * Returns the containing field for a record, or null for a top-level record.
	 *
	 * @param key record key
	 * @return Java field name in the parent object, or null
	 */
	public String parentFieldOf(RecordKey key) {
		return parentFields.get(key);
	}

	/**
	 * Returns the model object referenced by this index.
	 *
	 * @param key record key
	 * @return mutable model object, or null when absent
	 */
	public Object value(RecordKey key) {
		return values.get(key);
	}

	/**
	 * Returns a detached snapshot for one record.
	 *
	 * @param key record key
	 * @return deep-copied snapshot, or {@code null} when absent
	 */
	public JsonNode record(RecordKey key) {
		JsonNode snapshot = records.get(key);
		return snapshot == null ? null : snapshot.deepCopy();
	}

	/**
	 * Record identity, scoped by its concrete Java model type.
	 *
	 * @param type fully-qualified model type name
	 * @param id stable record identity
	 */
	public record RecordKey(String type, String id) implements Comparable<RecordKey> {
		/**
		 * Orders record keys by type and then identity.
		 *
		 * @param other comparison key
		 * @return lexical comparison result
		 */
		@Override
		public int compareTo(RecordKey other) {
			int typeOrder = type.compareTo(other.type);
			return typeOrder == 0 ? id.compareTo(other.id) : typeOrder;
		}
	}

	/**
	 * Builds snapshots and parent relationships while walking model objects.
	 */
	private static final class Builder {
		private final Map<RecordKey, JsonNode> records = new LinkedHashMap<>();
		private final Map<RecordKey, RecordKey> parents = new LinkedHashMap<>();
		private final Map<RecordKey, String> parentFields = new LinkedHashMap<>();
		private final Map<RecordKey, Object> values = new LinkedHashMap<>();

		/**
		 * Visits an object graph node and indexes identified records.
		 *
		 * @param value model node
		 * @param parent enclosing identified record
		 * @param path path used for deterministic traversal
		 * @param visited visited object references
		 */
		private void visit(Object value, RecordKey parent, String parentField, String path,
				IdentityHashMap<Object, Boolean> visited) {
			if (value == null || visited.put(value, Boolean.TRUE) != null) {
				return;
			}
			RecordKey childParent = parent;
			if (value instanceof Identified identified && identified.getId() != null
					&& !identified.getId().isBlank()) {
				RecordKey key = new RecordKey(value.getClass().getName(), identified.getId());
				records.put(key, snapshot(value));
				parents.put(key, parent);
				parentFields.put(key, parentField);
				values.put(key, value);
				childParent = key;
			}
			if (value instanceof Collection<?> collection) {
				int index = 0;
				for (Object item : collection) {
					visit(item, childParent, parentField, path + "[" + index++ + "]", visited);
				}
			} else if (value instanceof Map<?, ?> map) {
				List<? extends Map.Entry<?, ?>> entries = new ArrayList<>(map.entrySet());
				entries.sort(Comparator.comparing(entry -> String.valueOf(entry.getKey())));
				for (Map.Entry<?, ?> entry : entries) {
					visit(entry.getValue(), childParent, parentField, path + "." + entry.getKey(), visited);
				}
			} else if (value.getClass().isArray()) {
				for (int index = 0; index < Array.getLength(value); index++) {
					visit(Array.get(value, index), childParent, parentField, path + "[" + index + "]", visited);
				}
			} else if (value.getClass().getPackageName().equals(AppData.class.getPackageName())) {
				visitFields(value, childParent, path, visited);
			}
		}

		/**
		 * Visits the non-static fields of a model value.
		 *
		 * @param value model object
		 * @param parent enclosing record
		 * @param path path used for deterministic traversal
		 * @param visited visited object references
		 */
		private void visitFields(Object value, RecordKey parent, String path,
				IdentityHashMap<Object, Boolean> visited) {
			List<Field> fields = new ArrayList<>();
			for (Class<?> type = value.getClass(); type != null
					&& type.getPackageName().equals(AppData.class.getPackageName()); type = type.getSuperclass()) {
				for (Field field : type.getDeclaredFields()) {
					if (!Modifier.isStatic(field.getModifiers()) && !field.isSynthetic()) {
						fields.add(field);
					}
				}
			}
			fields.sort(Comparator.comparing(Field::getName));
			for (Field field : fields) {
				try {
					field.setAccessible(true);
					visit(field.get(value), parent, field.getName(), path + "." + field.getName(), visited);
				} catch (IllegalAccessException | RuntimeException exception) {
					throw new IllegalStateException("Cannot index " + path + "." + field.getName(), exception);
				}
			}
		}

		/**
		 * Serializes a record and masks child record subtrees with their IDs.
		 *
		 * @param value record value
		 * @return independent JSON snapshot
		 */
		private JsonNode snapshot(Object value) {
			JsonNode node = MAPPER.valueToTree(value);
			maskChildren(node);
			return node;
		}

		/**
		 * Replaces nested records by IDs while preserving non-record values.
		 *
		 * @param node current JSON node
		 * @param root whether this node is the record's own snapshot
		 */
		private void maskChildren(JsonNode node) {
			if (node instanceof ObjectNode object) {
				List<String> properties = new ArrayList<>();
				object.fieldNames().forEachRemaining(properties::add);
				for (String name : properties) {
					JsonNode child = object.get(name);
					if (isRecord(child)) {
						object.set(name, JsonNodeFactory.instance.textNode(child.path("id").asText()));
					} else {
						maskChildren(child);
					}
				}
			} else if (node instanceof ArrayNode array) {
				boolean allRecordChildren = array.size() > 0;
				for (int index = 0; index < array.size(); index++) {
					JsonNode child = array.get(index);
					if (isRecord(child)) {
						array.set(index, JsonNodeFactory.instance.textNode(child.path("id").asText()));
					} else {
						allRecordChildren = false;
						maskChildren(child);
					}
				}
				if (allRecordChildren) {
					List<String> identities = new ArrayList<>();
					array.forEach(child -> identities.add(child.asText()));
					identities.sort(String::compareTo);
					array.removeAll();
					identities.forEach(identity -> array.add(identity));
				}
			}
		}

		/**
		 * Checks whether a JSON object represents an identified child record.
		 *
		 * @param node candidate JSON value
		 * @return true for an object with a nonblank canonical ID
		 */
		private boolean isRecord(JsonNode node) {
			return node != null && node.isObject() && node.path("id").isTextual()
					&& !node.path("id").asText().isBlank();
		}

		/**
		 * Orders and freezes snapshots.
		 *
		 * @return ordered immutable record map
		 */
		private Map<RecordKey, JsonNode> sortedRecords() {
			Map<RecordKey, JsonNode> sorted = new LinkedHashMap<>();
			records.keySet().stream().sorted().forEach(key -> sorted.put(key, records.get(key)));
			return java.util.Collections.unmodifiableMap(sorted);
		}

		/**
		 * Orders and freezes parent relationships.
		 *
		 * @return ordered immutable parent map
		 */
		private Map<RecordKey, RecordKey> sortedParents() {
			Map<RecordKey, RecordKey> sorted = new LinkedHashMap<>();
			parents.keySet().stream().sorted().forEach(key -> sorted.put(key, parents.get(key)));
			return java.util.Collections.unmodifiableMap(sorted);
		}

		/**
		 * Orders containing-field metadata.
		 *
		 * @return immutable ordered parent-field map
		 */
		private Map<RecordKey, String> sortedParentFields() {
			Map<RecordKey, String> sorted = new LinkedHashMap<>();
			parentFields.keySet().stream().sorted().forEach(key -> sorted.put(key, parentFields.get(key)));
			return java.util.Collections.unmodifiableMap(sorted);
		}

		/**
		 * Orders identified model values.
		 *
		 * @return immutable ordered value map
		 */
		private Map<RecordKey, Object> sortedValues() {
			Map<RecordKey, Object> sorted = new LinkedHashMap<>();
			values.keySet().stream().sorted().forEach(key -> sorted.put(key, values.get(key)));
			return java.util.Collections.unmodifiableMap(sorted);
		}
	}
}
