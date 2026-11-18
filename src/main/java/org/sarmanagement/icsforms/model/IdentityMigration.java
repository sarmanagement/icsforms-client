/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.model;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Assigns missing identities and repairs duplicate stored identities once per document load.
 */
public final class IdentityMigration {
	private static final Logger LOGGER = Logger.getLogger(IdentityMigration.class.getName());
	private static final String MODEL_PACKAGE = "org.sarmanagement.icsforms.model";

	/**
	 * Prevents construction of the migration utility.
	 */
	private IdentityMigration() {
	}

	/**
	 * Runs legacy normalization and repairs absent or duplicate record identities.
	 *
	 * @param data incident document to migrate
	 */
	public static void migrate(AppData data) {
		data.normalizeLegacyIdentity();
		normalizeUnambiguousBlankAssignmentLink(data);
		IdentityMigration migration = new IdentityMigration();
		migration.normalizeValue(data, "$", new HashMap<>(), new IdentityHashMap<>(), true);
	}

	/**
	 * Assigns missing identities before a write without repairing duplicate stored identities.
	 *
	 * @param data incident document to normalize
	 */
	public static void assignMissing(AppData data) {
		normalizeUnambiguousBlankAssignmentLink(data);
		IdentityMigration migration = new IdentityMigration();
		migration.normalizeValue(data, "$", new HashMap<>(), new IdentityHashMap<>(), false);
	}

	/**
	 * Gives one uniquely unlinked ICS 204 row and SAR task a shared assignment identity.
	 *
	 * @param data incident document containing the rows and tasks
	 */
	private static void normalizeUnambiguousBlankAssignmentLink(AppData data) {
		List<ResourceAssignment> assignments = data.getAllAssignmentResources().stream()
				.filter(assignment -> assignment != null
						&& (assignment.getAssignmentId() == null || assignment.getAssignmentId().isBlank()))
				.toList();
		List<SarTaskAssignment> tasks = data.getSarTaskAssignments().stream()
				.filter(task -> task != null && (task.getAssignmentId() == null || task.getAssignmentId().isBlank()))
				.toList();
		if (assignments.size() == 1 && tasks.size() == 1) {
			String assignmentId = UUID.randomUUID().toString();
			assignments.get(0).setId(assignmentId);
			tasks.get(0).setAssignmentId(assignmentId);
		}
	}

	/**
	 * Reassigns identities in detached copies and remaps references among copied records.
	 *
	 * @param roots copied model roots whose records should receive fresh identities
	 */
	public static void remint(Object... roots) {
		IdentityMigration migration = new IdentityMigration();
		Map<String, Set<String>> replacements = new HashMap<>();
		IdentityHashMap<Object, Boolean> visited = new IdentityHashMap<>();
		for (int index = 0; index < roots.length; index++) {
			roots[index] = migration.remintValue(roots[index], replacements, visited);
		}
		visited.clear();
		for (Object root : roots) {
			migration.remapReferences(root, replacements, visited);
		}
	}

	/**
	 * Assigns new identities to records in a copied object graph.
	 *
	 * @param value copied value
	 * @param replacements original IDs to replacement IDs
	 * @param visited visited object references
	 * @return possibly rebuilt value
	 */
	private Object remintValue(Object value, Map<String, Set<String>> replacements,
			IdentityHashMap<Object, Boolean> visited) {
		if (value == null || visited.put(value, Boolean.TRUE) != null) {
			return value;
		}
		if (value instanceof Collection<?> collection) {
			return remintCollection(collection, replacements, visited);
		}
		if (value instanceof Map<?, ?> map) {
			return remintMap(map, replacements, visited);
		}
		if (value.getClass().isArray()) {
			remintArray(value, replacements, visited);
			return value;
		}
		if (!value.getClass().getPackageName().equals(MODEL_PACKAGE)) {
			return value;
		}
		if (value instanceof Identified identified) {
			String oldId = identified.getId();
			String newId = UUID.randomUUID().toString();
			replacements.computeIfAbsent(oldId, ignored -> new HashSet<>()).add(newId);
			value = withIdentity(value, newId);
		}
		remintFields(value, replacements, visited);
		return value;
	}

	/**
	 * Rebuilds list/set contents after renewing record identities.
	 *
	 * @param collection copied collection
	 * @param replacements original IDs to replacement IDs
	 * @param visited visited object references
	 * @return collection, potentially updated in place
	 */
	@SuppressWarnings("unchecked")
	private Collection<?> remintCollection(Collection<?> collection, Map<String, Set<String>> replacements,
			IdentityHashMap<Object, Boolean> visited) {
		if (collection instanceof List<?> list) {
			for (int index = 0; index < list.size(); index++) {
				Object value = list.get(index);
				Object updated = remintValue(value, replacements, visited);
				if (updated != value) {
					((List<Object>) list).set(index, updated);
				}
			}
		} else {
			List<Object> values = new ArrayList<>(collection);
			((Collection<Object>) collection).clear();
			for (Object value : values) {
				((Collection<Object>) collection).add(remintValue(value, replacements, visited));
			}
		}
		return collection;
	}

	/**
	 * Renews record identities in a map's values.
	 *
	 * @param map copied map
	 * @param replacements original IDs to replacement IDs
	 * @param visited visited object references
	 * @return map, potentially updated in place
	 */
	@SuppressWarnings("unchecked")
	private Map<?, ?> remintMap(Map<?, ?> map, Map<String, Set<String>> replacements,
			IdentityHashMap<Object, Boolean> visited) {
		for (Map.Entry<?, ?> entry : new ArrayList<>(map.entrySet())) {
			Object value = entry.getValue();
			Object updated = remintValue(value, replacements, visited);
			if (updated != value) {
				((Map<Object, Object>) map).put(entry.getKey(), updated);
			}
		}
		return map;
	}

	/**
	 * Renews identities in object-array values.
	 *
	 * @param array copied array
	 * @param replacements original IDs to replacement IDs
	 * @param visited visited object references
	 */
	private void remintArray(Object array, Map<String, Set<String>> replacements,
			IdentityHashMap<Object, Boolean> visited) {
		for (int index = 0; index < Array.getLength(array); index++) {
			Object value = Array.get(array, index);
			Object updated = remintValue(value, replacements, visited);
			if (updated != value) {
				Array.set(array, index, updated);
			}
		}
	}

	/**
	 * Renews identities in fields that contain child model objects.
	 *
	 * @param value copied model object
	 * @param replacements original IDs to replacement IDs
	 * @param visited visited object references
	 */
	private void remintFields(Object value, Map<String, Set<String>> replacements,
			IdentityHashMap<Object, Boolean> visited) {
		for (Field field : modelFields(value.getClass())) {
			try {
				field.setAccessible(true);
				Object child = field.get(value);
				Object updated = remintValue(child, replacements, visited);
				if (updated != child && !Modifier.isFinal(field.getModifiers())) {
					field.set(value, updated);
				}
			} catch (IllegalAccessException | RuntimeException exception) {
				throw new IllegalStateException("Cannot renew copied identity in " + value.getClass().getSimpleName(),
						exception);
			}
		}
	}

	/**
	 * Remaps ID references that point to records within the copied object graph.
	 *
	 * @param value copied value
	 * @param replacements original IDs to replacement IDs
	 * @param visited visited object references
	 */
	private void remapReferences(Object value, Map<String, Set<String>> replacements,
			IdentityHashMap<Object, Boolean> visited) {
		if (value == null || visited.put(value, Boolean.TRUE) != null) {
			return;
		}
		if (value instanceof List<?> list) {
			for (int index = 0; index < list.size(); index++) {
				Object item = list.get(index);
				Object updated = remappedValue(item, replacements);
				if (updated != item) {
					((List<Object>) list).set(index, updated);
				} else {
					remapReferences(item, replacements, visited);
				}
			}
		} else if (value instanceof Map<?, ?> map) {
			remapMapReferences(map, replacements, visited);
		} else if (value instanceof Collection<?> collection) {
			remapCollectionReferences(collection, replacements, visited);
		} else if (value.getClass().isArray()) {
			remapArrayReferences(value, replacements, visited);
		} else if (value.getClass().getPackageName().equals(MODEL_PACKAGE)) {
			remapFieldReferences(value, replacements, visited);
		}
	}

	/**
	 * Remaps string references in object fields and traverses nested values.
	 *
	 * @param value copied model object
	 * @param replacements original IDs to replacement IDs
	 * @param visited visited object references
	 */
	private void remapFieldReferences(Object value, Map<String, Set<String>> replacements,
			IdentityHashMap<Object, Boolean> visited) {
		for (Field field : modelFields(value.getClass())) {
			try {
				field.setAccessible(true);
				Object child = field.get(value);
				if (child instanceof String id && field.getName().endsWith("Id")
						&& (! (value instanceof Identified identified) || !id.equals(identified.getId()))) {
					String updated = remappedId(id, replacements);
					if (!updated.equals(id) && !Modifier.isFinal(field.getModifiers())) {
						field.set(value, updated);
					}
				} else if (child instanceof Collection<?> collection && field.getName().endsWith("Ids")) {
					remapIdCollection(collection, replacements, visited);
				} else {
					remapReferences(child, replacements, visited);
				}
			} catch (IllegalAccessException | RuntimeException exception) {
				throw new IllegalStateException("Cannot remap copied reference in " + value.getClass().getSimpleName(),
						exception);
			}
		}
	}

	/**
	 * Remaps references stored as map keys or values.
	 *
	 * @param map copied map
	 * @param replacements original IDs to replacement IDs
	 * @param visited visited object references
	 */
	@SuppressWarnings("unchecked")
	private void remapMapReferences(Map<?, ?> map, Map<String, Set<String>> replacements,
			IdentityHashMap<Object, Boolean> visited) {
		Map<Object, Object> updated = new HashMap<>();
		for (Map.Entry<?, ?> entry : map.entrySet()) {
			Object key = entry.getKey() instanceof String id ? remappedValue(id, replacements) : entry.getKey();
			Object value = entry.getValue();
			updated.put(key, value);
			if (!(value instanceof String)) {
				remapReferences(value, replacements, visited);
			}
		}
		((Map<Object, Object>) map).clear();
		((Map<Object, Object>) map).putAll(updated);
	}

	/**
	 * Remaps references in collection elements.
	 *
	 * @param collection copied collection
	 * @param replacements original IDs to replacement IDs
	 * @param visited visited object references
	 */
	@SuppressWarnings("unchecked")
	private void remapCollectionReferences(Collection<?> collection, Map<String, Set<String>> replacements,
			IdentityHashMap<Object, Boolean> visited) {
		for (Object item : collection) {
			if (!(item instanceof String)) {
				remapReferences(item, replacements, visited);
			}
		}
	}

	/**
	 * Remaps identity references stored in a collection-valued *Ids field.
	 *
	 * @param collection reference IDs
	 * @param replacements original IDs to replacement IDs
	 * @param visited visited object references
	 */
	@SuppressWarnings("unchecked")
	private void remapIdCollection(Collection<?> collection, Map<String, Set<String>> replacements,
			IdentityHashMap<Object, Boolean> visited) {
		List<Object> updated = new ArrayList<>();
		for (Object item : collection) {
			Object replacement = remappedValue(item, replacements);
			updated.add(replacement);
			if (!(item instanceof String)) {
				remapReferences(item, replacements, visited);
			}
		}
		((Collection<Object>) collection).clear();
		((Collection<Object>) collection).addAll(updated);
	}

	/**
	 * Remaps references in an object array.
	 *
	 * @param array copied array
	 * @param replacements original IDs to replacement IDs
	 * @param visited visited object references
	 */
	private void remapArrayReferences(Object array, Map<String, Set<String>> replacements,
			IdentityHashMap<Object, Boolean> visited) {
		for (int index = 0; index < Array.getLength(array); index++) {
			Object item = Array.get(array, index);
			Object replacement = remappedValue(item, replacements);
			Array.set(array, index, replacement);
			if (replacement == item) {
				remapReferences(item, replacements, visited);
			}
		}
	}

	/**
	 * Returns a remapped string value when its source identity was copied.
	 *
	 * @param value candidate value
	 * @param replacements original IDs to replacement IDs
	 * @return remapped string or original value
	 */
	private Object remappedValue(Object value, Map<String, Set<String>> replacements) {
		return value instanceof String id ? remappedId(id, replacements) : value;
	}

	/**
	 * Resolves one reference only when its old identity maps unambiguously.
	 *
	 * @param id referenced identity
	 * @param replacements original IDs to replacement IDs
	 * @return replacement identity or original
	 */
	private String remappedId(String id, Map<String, Set<String>> replacements) {
		Set<String> matches = replacements.get(id);
		return matches != null && matches.size() == 1 ? matches.iterator().next() : id;
	}

	/**
	 * Lists model fields in deterministic name order.
	 *
	 * @param type model type
	 * @return non-static fields from the model class hierarchy
	 */
	private List<Field> modelFields(Class<?> type) {
		List<Field> fields = new ArrayList<>();
		for (Class<?> current = type; current != null && current.getPackageName().equals(MODEL_PACKAGE);
				current = current.getSuperclass()) {
			for (Field field : current.getDeclaredFields()) {
				if (!Modifier.isStatic(field.getModifiers()) && !field.isSynthetic()) {
					fields.add(field);
				}
			}
		}
		fields.sort(Comparator.comparing(Field::getName));
		return fields;
	}

	/**
	 * Walks a document value, assigning an ID where absent and replacing duplicate IDs.
	 *
	 * @param value value to normalize
	 * @param path deterministic path of the value
	 * @param seen identities already retained, grouped by concrete record type
	 * @param visited object references already traversed
	 * @return possibly replaced value
	 */
	private Object normalizeValue(Object value, String path, Map<Class<?>, Set<String>> seen,
			IdentityHashMap<Object, Boolean> visited, boolean repairDuplicates) {
		if (value == null || visited.put(value, Boolean.TRUE) != null) {
			return value;
		}
		if (value instanceof Collection<?> collection) {
			normalizeCollection(collection, path, seen, visited, repairDuplicates);
			return value;
		}
		if (value instanceof Map<?, ?> map) {
			normalizeMap(map, path, seen, visited, repairDuplicates);
			return value;
		}
		if (value.getClass().isArray()) {
			normalizeArray(value, path, seen, visited, repairDuplicates);
			return value;
		}
		if (!value.getClass().getPackageName().equals(MODEL_PACKAGE)) {
			return value;
		}
		if (value instanceof Identified identified) {
			String id = identified.getId();
			if (id == null || id.isBlank()) {
				id = UUID.randomUUID().toString();
				value = withIdentity(value, id);
				identified = (Identified) value;
			}
			Set<String> identities = seen.computeIfAbsent(value.getClass(), ignored -> new HashSet<>());
			if (!identities.add(id) && repairDuplicates) {
				String replacement = duplicateId(value.getClass(), id, path, identities);
				LOGGER.warning("Duplicate record identity repaired at " + path + " for "
						+ value.getClass().getSimpleName());
				value = withIdentity(value, replacement);
				identified = (Identified) value;
				identities.add(identified.getId());
			}
		}
		normalizeFields(value, path, seen, visited, repairDuplicates);
		return value;
	}

	/**
	 * Normalizes collection elements in their deterministic iteration order.
	 *
	 * @param collection records or value objects
	 * @param path parent path
	 * @param seen identities already retained
	 * @param visited object references already traversed
	 */
	@SuppressWarnings("unchecked")
	private void normalizeCollection(Collection<?> collection, String path, Map<Class<?>, Set<String>> seen,
			IdentityHashMap<Object, Boolean> visited, boolean repairDuplicates) {
		if (collection instanceof List<?> list) {
			for (int index = 0; index < list.size(); index++) {
				Object value = list.get(index);
				Object normalized = normalizeValue(value, path + "[" + index + "]", seen, visited, repairDuplicates);
				if (normalized != value) {
					((List<Object>) list).set(index, normalized);
				}
			}
		} else {
			List<Object> values = new ArrayList<>(collection);
			collection.clear();
			for (int index = 0; index < values.size(); index++) {
				((Collection<Object>) collection).add(normalizeValue(values.get(index), path + "[" + index + "]",
						seen, visited, repairDuplicates));
			}
		}
	}

	/**
	 * Normalizes map values in key-sorted order for deterministic duplicate repair.
	 *
	 * @param map persisted map
	 * @param path parent path
	 * @param seen identities already retained
	 * @param visited object references already traversed
	 */
	@SuppressWarnings("unchecked")
	private void normalizeMap(Map<?, ?> map, String path, Map<Class<?>, Set<String>> seen,
			IdentityHashMap<Object, Boolean> visited, boolean repairDuplicates) {
		List<Map.Entry<?, ?>> entries = new ArrayList<>(map.entrySet());
		entries.sort(Comparator.comparing(entry -> String.valueOf(entry.getKey())));
		for (Map.Entry<?, ?> entry : entries) {
			Object value = entry.getValue();
			Object normalized = normalizeValue(value, path + "." + entry.getKey(), seen, visited, repairDuplicates);
			if (normalized != value) {
				((Map<Object, Object>) map).put(entry.getKey(), normalized);
			}
		}
	}

	/**
	 * Normalizes object-array elements.
	 *
	 * @param array array value
	 * @param path parent path
	 * @param seen identities already retained
	 * @param visited object references already traversed
	 */
	private void normalizeArray(Object array, String path, Map<Class<?>, Set<String>> seen,
			IdentityHashMap<Object, Boolean> visited, boolean repairDuplicates) {
		for (int index = 0; index < Array.getLength(array); index++) {
			Object value = Array.get(array, index);
			Object normalized = normalizeValue(value, path + "[" + index + "]", seen, visited, repairDuplicates);
			if (normalized != value) {
				Array.set(array, index, normalized);
			}
		}
	}

	/**
	 * Normalizes non-static fields to reach nested records.
	 *
	 * @param value model object
	 * @param path parent path
	 * @param seen identities already retained
	 * @param visited object references already traversed
	 */
	private void normalizeFields(Object value, String path, Map<Class<?>, Set<String>> seen,
			IdentityHashMap<Object, Boolean> visited, boolean repairDuplicates) {
		List<Field> fields = new ArrayList<>();
		for (Class<?> type = value.getClass(); type != null && type.getPackageName().equals(MODEL_PACKAGE);
				type = type.getSuperclass()) {
			for (Field field : type.getDeclaredFields()) {
				if (!java.lang.reflect.Modifier.isStatic(field.getModifiers()) && !field.isSynthetic()) {
					fields.add(field);
				}
			}
		}
		fields.sort(Comparator.comparing(Field::getName));
		for (Field field : fields) {
			try {
				field.setAccessible(true);
				Object child = field.get(value);
				Object normalized = normalizeValue(child, path + "." + field.getName(), seen, visited,
						repairDuplicates);
				if (normalized != child && !java.lang.reflect.Modifier.isFinal(field.getModifiers())) {
					field.set(value, normalized);
				}
			} catch (IllegalAccessException | RuntimeException exception) {
				throw new IllegalStateException("Cannot migrate identity at " + path + "." + field.getName(),
						exception);
			}
		}
	}

	/**
	 * Produces a path-stable replacement ID for a duplicate.
	 *
	 * @param type concrete record type
	 * @param id duplicate identity
	 * @param path record path
	 * @param seen IDs already retained for the type
	 * @return unique deterministic UUID
	 */
	private String duplicateId(Class<?> type, String id, String path, Set<String> seen) {
		String seed = type.getName() + ":" + id + ":" + path;
		String replacement = UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString();
		int attempt = 0;
		while (seen.contains(replacement)) {
			replacement = UUID.nameUUIDFromBytes((seed + ":" + ++attempt).getBytes(StandardCharsets.UTF_8))
					.toString();
		}
		return replacement;
	}

	/**
	 * Changes an identity, rebuilding immutable record objects when needed.
	 *
	 * @param value record value
	 * @param id replacement identity
	 * @return updated record
	 */
	private Object withIdentity(Object value, String id) {
		if (!value.getClass().isRecord()) {
			((Identified) value).setId(id);
			return value;
		}
		try {
			RecordComponent[] components = value.getClass().getRecordComponents();
			Class<?>[] parameterTypes = new Class<?>[components.length];
			Object[] arguments = new Object[components.length];
			for (int index = 0; index < components.length; index++) {
				RecordComponent component = components[index];
				parameterTypes[index] = component.getType();
				arguments[index] = component.getName().equals("id") ? id : component.getAccessor().invoke(value);
			}
			Constructor<?> constructor = value.getClass().getDeclaredConstructor(parameterTypes);
			constructor.setAccessible(true);
			return constructor.newInstance(arguments);
		} catch (ReflectiveOperationException exception) {
			throw new IllegalStateException("Cannot update immutable record identity.", exception);
		}
	}
}
