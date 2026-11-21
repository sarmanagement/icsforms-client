/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.model;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.sarmanagement.icsforms.model.RecordIndex.RecordKey;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Virtual singleton record carrying the incident-wide settings that no identified record contains, such as the
 * IAP phase, incident mode and time zone. It lets those values travel through the versioned record service with
 * per-field base versions, three-way merge and conflicts like any other record. It is never stored in the
 * incident document itself: it is derived from, and applied back onto, the {@link AppData} root.
 */
public final class RootSettings implements Identified {
	/** Stable identity of the one root-settings record in every incident. */
	public static final String ID = "incident-root-settings";
	private static final Set<String> METADATA = Set.of("incidentId", "revision", "createdAt", "updatedAt",
			"schemaVersion", "originNodeId");
	private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule())
			.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
			.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
	private static final Set<String> PROPERTIES = properties();

	/**
	 * Creates the singleton record shell for reflective construction.
	 */
	public RootSettings() {
	}

	/** {@inheritDoc} */
	@Override
	public String getId() {
		return ID;
	}

	/** {@inheritDoc} */
	@Override
	public void setId(String id) {
		IdentityGuard.check(ID, id);
	}

	/**
	 * Returns the identity of the root-settings record.
	 *
	 * @return record key, identical for every incident
	 */
	public static RecordKey key() {
		return new RecordKey(RootSettings.class.getName(), ID);
	}

	/**
	 * Tells whether a key names the root-settings record.
	 *
	 * @param key record key to test
	 * @return true for the root-settings record
	 */
	public static boolean isRoot(RecordKey key) {
		return key != null && key().equals(key);
	}

	/**
	 * Lists the document properties carried by the root-settings record.
	 *
	 * @return unmodifiable ordered property names
	 */
	public static Set<String> propertyNames() {
		return PROPERTIES;
	}

	/**
	 * Derives the root-settings snapshot from a document.
	 *
	 * @param data incident document
	 * @return snapshot holding the record identity and every root value
	 */
	public static ObjectNode snapshot(AppData data) {
		JsonNode tree = MAPPER.valueToTree(data);
		ObjectNode node = MAPPER.createObjectNode();
		node.put("id", ID);
		for (String name : PROPERTIES) {
			JsonNode value = tree.get(name);
			if (value != null) {
				node.set(name, value);
			}
		}
		return node;
	}

	/**
	 * Applies a root-settings snapshot to a document, leaving every other property untouched.
	 *
	 * @param data detached incident document to update
	 * @param content snapshot to apply
	 * @throws IllegalArgumentException when the snapshot cannot be applied
	 */
	public static void apply(AppData data, JsonNode content) {
		ObjectNode writable = MAPPER.createObjectNode();
		for (String name : PROPERTIES) {
			JsonNode value = content.get(name);
			if (value != null) {
				writable.set(name, value);
			}
		}
		try {
			MAPPER.readerForUpdating(data).readValue(writable.toString());
		} catch (IOException failure) {
			throw new IllegalArgumentException("Cannot apply root settings", failure);
		}
	}

	/**
	 * Finds the root fields that are neither metadata nor identified-record links.
	 *
	 * @return ordered property names
	 */
	private static Set<String> properties() {
		Set<String> names = new LinkedHashSet<>();
		java.util.List<Field> fields = new java.util.ArrayList<>();
		for (Field field : AppData.class.getDeclaredFields()) {
			if (!Modifier.isStatic(field.getModifiers()) && !Modifier.isTransient(field.getModifiers())
					&& !field.isSynthetic() && !METADATA.contains(field.getName()) && !linksRecords(field)) {
				fields.add(field);
			}
		}
		fields.sort(java.util.Comparator.comparing(Field::getName));
		fields.forEach(field -> names.add(field.getName()));
		return Collections.unmodifiableSet(names);
	}

	/**
	 * Checks whether a field holds identified records, which are versioned individually.
	 *
	 * @param field document field
	 * @return true for an identified record, or a collection or array of them
	 */
	private static boolean linksRecords(Field field) {
		if (Identified.class.isAssignableFrom(field.getType())) {
			return true;
		}
		if (Collection.class.isAssignableFrom(field.getType())
				&& field.getGenericType() instanceof java.lang.reflect.ParameterizedType parameterized) {
			for (java.lang.reflect.Type argument : parameterized.getActualTypeArguments()) {
				if (argument instanceof Class<?> type && Identified.class.isAssignableFrom(type)) {
					return true;
				}
			}
		}
		return field.getType().isArray() && Identified.class.isAssignableFrom(field.getType().getComponentType());
	}
}
