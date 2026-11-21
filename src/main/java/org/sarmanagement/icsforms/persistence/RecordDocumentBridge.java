/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.persistence;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.Identified;
import org.sarmanagement.icsforms.model.RecordIndex;
import org.sarmanagement.icsforms.model.RecordIndex.RecordKey;
import org.sarmanagement.icsforms.model.RootSettings;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.sarmanagement.icsforms.persistence.RecordVersionStore.*;

/**
 * Applies versioned record snapshots to the existing incident object graph.
 */
final class RecordDocumentBridge {
	private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule())
			.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
			.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

	/**
	 * Validates that a record can be attached at its declared parent location.
	 *
	 * @param key record identity
	 * @param parent parent record, or null for the incident root
	 * @param parentField containing Java field name
	 */
	void validateCreate(RecordKey key, RecordKey parent, String parentField) {
		try {
			Class<?> recordType = Class.forName(key.type(), false, RecordDocumentBridge.class.getClassLoader());
			Class<?> parentType = parent == null ? AppData.class
					: Class.forName(parent.type(), false, RecordDocumentBridge.class.getClassLoader());
			if (Modifier.isAbstract(recordType.getModifiers())) {
				throw new StoreValidationException("Cannot create an abstract record type");
			}
			recordType.getDeclaredConstructor();
			Field identity = identityField(recordType);
			Field relation = field(parentType, parentField);
			if (identity == null || relation == null || !accepts(relation, recordType)) {
				throw new StoreValidationException("Record type or parent field is invalid");
			}
		} catch (ClassNotFoundException | NoSuchMethodException exception) {
			throw new StoreValidationException("Record type cannot be created", exception);
		}
	}

	/**
	 * Projects latest record snapshots into a detached incident document.
	 *
	 * @param data detached incident document
	 * @param histories retained record histories
	 * @return true when the document was changed
	 */
	boolean apply(AppData data, List<RecordHistory> histories) {
		return apply(data, histories, false);
	}

	/**
	 * Projects latest record snapshots into a detached incident document.
	 *
	 * @param data detached incident document
	 * @param histories retained record histories
	 * @param replaceDefaults whether a single-valued parent field holding another record, such as the default a
	 *            new document carries, is replaced by the record from the histories rather than rejected
	 * @return true when the document was changed
	 */
	boolean apply(AppData data, List<RecordHistory> histories, boolean replaceDefaults) {
		JsonNode before = MAPPER.valueToTree(data);
		RecordIndex index = RecordIndex.of(data);
		Map<RecordKey, Object> values = new HashMap<>();
		index.records().keySet().forEach(key -> values.put(key, index.value(key)));
		List<RecordHistory> ordered = histories.stream()
				.sorted(Comparator.comparing(history -> history.key().type() + ":" + history.key().id())).toList();
		List<RecordHistory> missing = new ArrayList<>(ordered.stream()
				.filter(history -> !values.containsKey(history.key()) && !RootSettings.isRoot(history.key()))
				.toList());
		while (!missing.isEmpty()) {
			boolean attached = false;
			for (int indexPosition = 0; indexPosition < missing.size();) {
				RecordHistory history = missing.get(indexPosition);
				Object parent = history.parent() == null ? data : values.get(history.parent());
				if (parent == null) {
					indexPosition++;
					continue;
				}
				Object created = create(history.key(), latest(history).content());
				attach(parent, history.parentField(), created, replaceDefaults);
				values.put(history.key(), created);
				missing.remove(indexPosition);
				attached = true;
			}
			if (!attached) {
				throw new StoreValidationException("Record history contains an unresolved parent");
			}
		}
		for (RecordHistory history : ordered) {
			if (RootSettings.isRoot(history.key())) {
				try {
					RootSettings.apply(data, latest(history).content());
				} catch (IllegalArgumentException exception) {
					throw new StoreValidationException("Cannot apply incident-wide settings", exception);
				}
				continue;
			}
			Object target = values.get(history.key());
			if (target != null) {
				update(target, latest(history).content());
			}
		}
		return !RecordJson.same(before, MAPPER.valueToTree(data));
	}

	/**
	 * Constructs a record from its stored JSON snapshot.
	 *
	 * @param key record identity
	 * @param content record content
	 * @return model record
	 */
	private Object create(RecordKey key, JsonNode content) {
		try {
			Class<?> type = Class.forName(key.type(), false, RecordDocumentBridge.class.getClassLoader());
			if (Modifier.isAbstract(type.getModifiers())) {
				throw new StoreValidationException("Cannot create an abstract record type");
			}
			Object value = type.getDeclaredConstructor().newInstance();
			setIdentity(value, key.id());
			update(value, content);
			return value;
		} catch (ReflectiveOperationException | RuntimeException exception) {
			throw new StoreValidationException("Cannot construct record: " + key, exception);
		}
	}

	/**
	 * Assigns a stable ID to a newly constructed identified object.
	 *
	 * @param value new model record
	 * @param id stable record identity
	 * @throws ReflectiveOperationException when the ID field cannot be set
	 */
	private void setIdentity(Object value, String id) throws ReflectiveOperationException {
		Field field = identityField(value.getClass());
		if (field == null) {
			throw new StoreValidationException("Record type has no serialized ID field");
		}
		field.setAccessible(true);
		field.set(value, id);
	}

	/**
	 * Finds the field serialized as the stable record ID.
	 *
	 * @param type record class
	 * @return identity field, or null
	 */
	private Field identityField(Class<?> type) {
		for (Field field : fields(type)) {
			JsonProperty property = field.getAnnotation(JsonProperty.class);
			if (field.getName().equals("id") || property != null && property.value().equals("id")) {
				return field;
			}
		}
		return null;
	}

	/**
	 * Checks whether a field can contain the submitted record type.
	 *
	 * @param field containing field
	 * @param valueType submitted record type
	 * @return true when the field directly or collectively stores that type
	 */
	private boolean accepts(Field field, Class<?> valueType) {
		if (field.getType().isAssignableFrom(valueType)) {
			return true;
		}
		if (Collection.class.isAssignableFrom(field.getType())
				&& field.getGenericType() instanceof ParameterizedType parameterized) {
			for (Type argument : parameterized.getActualTypeArguments()) {
				if (argument instanceof Class<?> type && type.isAssignableFrom(valueType)) {
					return true;
				}
			}
		}
		return field.getType().isArray() && field.getType().getComponentType().isAssignableFrom(valueType);
	}

	/**
	 * Attaches a new record to its parent field.
	 *
	 * @param parent parent model object
	 * @param fieldName parent field name
	 * @param value new record object
	 * @param replaceDefaults whether an existing single-valued record is replaced instead of rejected
	 */
	private void attach(Object parent, String fieldName, Object value, boolean replaceDefaults) {
		if (fieldName == null || fieldName.isBlank()) {
			throw new StoreValidationException("New record is missing its parent field");
		}
		Field field = field(parent.getClass(), fieldName);
		if (field == null) {
			throw new StoreValidationException("Unknown parent field: " + fieldName);
		}
		try {
			field.setAccessible(true);
			Object existing = field.get(parent);
			if (existing instanceof Collection<?> collection) {
				@SuppressWarnings("unchecked")
				Collection<Object> writable = (Collection<Object>) collection;
				writable.add(value);
			} else if (existing == null || replaceDefaults && existing instanceof Identified) {
				field.set(parent, value);
			} else {
				throw new StoreValidationException("Parent field already contains a record");
			}
		} catch (IllegalAccessException | RuntimeException exception) {
			if (exception instanceof StoreValidationException validationException) {
				throw validationException;
			}
			throw new StoreValidationException("Cannot attach record to parent field: " + fieldName, exception);
		}
	}

	/**
	 * Updates scalar and value-object properties while retaining child record links.
	 *
	 * @param target existing model record
	 * @param content latest record snapshot
	 */
	private void update(Object target, JsonNode content) {
		ObjectNode writable = content.deepCopy();
		writable.remove("id");
		JsonNode current = MAPPER.valueToTree(target);
		for (Field field : fields(target.getClass())) {
			JsonProperty property = field.getAnnotation(JsonProperty.class);
			String name = property == null || property.value().isBlank() ? field.getName() : property.value();
			if (isRecordLink(field)) {
				writable.remove(name);
			} else if (RecordJson.same(current.get(name), content.get(name))) {
				writable.remove(name);
			}
		}
		try {
			MAPPER.readerForUpdating(target).readValue(writable.toString().getBytes(StandardCharsets.UTF_8));
		} catch (IOException | RuntimeException exception) {
			throw new StoreValidationException("Cannot apply record snapshot", exception);
		}
	}

	/**
	 * Checks whether a field directly references an identified child record.
	 *
	 * @param field model field
	 * @return true when this property is a record relationship
	 */
	private boolean isRecordLink(Field field) {
		if (Identified.class.isAssignableFrom(field.getType())) {
			return true;
		}
		Type generic = field.getGenericType();
		if (generic instanceof ParameterizedType parameterized) {
			for (Type argument : parameterized.getActualTypeArguments()) {
				if (argument instanceof Class<?> type && Identified.class.isAssignableFrom(type)) {
					return true;
				}
			}
		}
		return field.getType().isArray() && Identified.class.isAssignableFrom(field.getType().getComponentType());
	}

	/**
	 * Finds a declared instance field by name.
	 *
	 * @param type model class
	 * @param name field name
	 * @return matching field, or null
	 */
	private Field field(Class<?> type, String name) {
		for (Class<?> current = type; current != null; current = current.getSuperclass()) {
			try {
				return current.getDeclaredField(name);
			} catch (NoSuchFieldException ignored) {
				continue;
			}
		}
		return null;
	}

	/**
	 * Lists instance fields in a model inheritance chain.
	 *
	 * @param type model class
	 * @return declared fields
	 */
	private List<Field> fields(Class<?> type) {
		List<Field> result = new ArrayList<>();
		for (Class<?> current = type; current != null; current = current.getSuperclass()) {
			for (Field field : current.getDeclaredFields()) {
				if (!Modifier.isStatic(field.getModifiers()) && !field.isSynthetic()) {
					result.add(field);
				}
			}
		}
		return result;
	}

	/**
	 * Returns the latest version in a history.
	 *
	 * @param history record history
	 * @return newest retained version
	 */
	private RecordVersion latest(RecordHistory history) {
		return history.versions().get(history.versions().size() - 1);
	}
}
