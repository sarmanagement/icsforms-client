/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * Deterministic net-value diff between two incident documents.
 */
public final class RecordDiff {
	private final Set<RecordIndex.RecordKey> created;
	private final Map<RecordIndex.RecordKey, Set<String>> changed;
	private final Set<RecordIndex.RecordKey> removed;
	private final Set<RecordIndex.RecordKey> struckOut;
	private final Map<String, Batch> batches;

	/**
	 * Creates a diff with preordered immutable operations.
	 *
	 * @param created newly created records
	 * @param changed changed fields by record
	 * @param removed records absent from the edited document
	 * @param struckOut records newly marked struck out
	 * @param batches operations grouped by atomic batch name
	 */
	private RecordDiff(Set<RecordIndex.RecordKey> created, Map<RecordIndex.RecordKey, Set<String>> changed,
			Set<RecordIndex.RecordKey> removed, Set<RecordIndex.RecordKey> struckOut,
			Map<String, Batch> batches) {
		this.created = created;
		this.changed = changed;
		this.removed = removed;
		this.struckOut = struckOut;
		this.batches = batches;
	}

	/**
	 * Compares two documents as one atomic batch.
	 *
	 * @param base acknowledged base document
	 * @param edited current document
	 * @return deterministic record-level diff
	 */
	public static RecordDiff between(AppData base, AppData edited) {
		return between(base, edited, ignored -> "default");
	}

	/**
	 * Compares two documents and groups their operations by caller-defined batch key.
	 *
	 * @param base acknowledged base document
	 * @param edited current document
	 * @param grouping maps each record key to its atomic batch name
	 * @return deterministic record-level diff
	 */
	public static RecordDiff between(AppData base, AppData edited, Function<RecordIndex.RecordKey, String> grouping) {
		RecordIndex baseIndex = RecordIndex.of(base);
		RecordIndex editedIndex = RecordIndex.of(edited);
		Map<RecordIndex.RecordKey, JsonNode> oldRecords = baseIndex.records();
		Map<RecordIndex.RecordKey, JsonNode> newRecords = editedIndex.records();
		Set<RecordIndex.RecordKey> created = new TreeSet<>();
		Map<RecordIndex.RecordKey, Set<String>> changed = new TreeMap<>();
		Set<RecordIndex.RecordKey> removed = new TreeSet<>();
		Set<RecordIndex.RecordKey> struckOut = new TreeSet<>();
		for (RecordIndex.RecordKey key : newRecords.keySet()) {
			JsonNode after = newRecords.get(key);
			JsonNode before = oldRecords.get(key);
			if (before == null) {
				created.add(key);
			} else {
				Set<String> fields = changedFields(before, after);
				if (!fields.isEmpty()) {
					changed.put(key, fields);
				}
			}
			if (isStruckOut(after) && !isStruckOut(before)) {
				struckOut.add(key);
			}
		}
		for (RecordIndex.RecordKey key : oldRecords.keySet()) {
			if (!newRecords.containsKey(key)) {
				removed.add(key);
			}
		}
		Map<String, MutableBatch> grouped = new TreeMap<>();
		created.forEach(key -> batch(grouped, groupName(grouping, key)).created.add(key));
		changed.forEach((key, fields) -> batch(grouped, groupName(grouping, key)).changed.put(key, fields));
		removed.forEach(key -> batch(grouped, groupName(grouping, key)).removed.add(key));
		struckOut.forEach(key -> batch(grouped, groupName(grouping, key)).struckOut.add(key));
		Map<String, Batch> batches = new LinkedHashMap<>();
		grouped.forEach((name, value) -> batches.put(name, value.freeze()));
		return new RecordDiff(immutableSet(created), immutableChanges(changed), immutableSet(removed),
				immutableSet(struckOut), Collections.unmodifiableMap(batches));
	}

	/**
	 * Returns records introduced relative to the base document.
	 *
	 * @return immutable created record keys
	 */
	public Set<RecordIndex.RecordKey> created() {
		return created;
	}

	/**
	 * Returns changed record keys and their net changed field names.
	 *
	 * @return immutable changed-field map
	 */
	public Map<RecordIndex.RecordKey, Set<String>> changed() {
		return changed;
	}

	/**
	 * Returns records absent from the edited document.
	 *
	 * @return immutable removed record keys
	 */
	public Set<RecordIndex.RecordKey> removed() {
		return removed;
	}

	/**
	 * Returns records newly marked struck out in the edited document.
	 *
	 * @return immutable struck-out record keys
	 */
	public Set<RecordIndex.RecordKey> struckOut() {
		return struckOut;
	}

	/**
	 * Returns absent or newly struck-out records.
	 *
	 * @return immutable deleted-or-struck-out record keys
	 */
	public Set<RecordIndex.RecordKey> removedOrStruckOut() {
		Set<RecordIndex.RecordKey> result = new TreeSet<>(removed);
		result.addAll(struckOut);
		return immutableSet(result);
	}

	/**
	 * Returns operations partitioned into named atomic batches.
	 *
	 * @return ordered immutable batches
	 */
	public Map<String, Batch> batches() {
		return batches;
	}

	/**
	 * One group of operations that must be submitted atomically.
	 *
	 * @param created newly created records
	 * @param changed changed fields by record
	 * @param removed removed records
	 * @param struckOut newly struck-out records
	 */
	public record Batch(Set<RecordIndex.RecordKey> created, Map<RecordIndex.RecordKey, Set<String>> changed,
			Set<RecordIndex.RecordKey> removed, Set<RecordIndex.RecordKey> struckOut) {
	}

	/**
	 * Compares the scalar/value fields of two snapshots.
	 *
	 * @param before prior record snapshot
	 * @param after edited record snapshot
	 * @return sorted immutable changed-field set
	 */
	private static Set<String> changedFields(JsonNode before, JsonNode after) {
		Set<String> names = new TreeSet<>();
		before.fieldNames().forEachRemaining(names::add);
		after.fieldNames().forEachRemaining(names::add);
		Set<String> changed = new TreeSet<>();
		for (String name : names) {
			if (!before.path(name).equals(after.path(name))) {
				changed.add(name);
			}
		}
		return immutableSetOfStrings(changed);
	}

	/**
	 * Checks whether a record snapshot is marked as struck out.
	 *
	 * @param record record snapshot, or null when absent
	 * @return true when explicitly struck out
	 */
	private static boolean isStruckOut(JsonNode record) {
		return record != null && (record.path("struckOut").asBoolean(false)
				|| record.path("isStruckOut").asBoolean(false));
	}

	/**
	 * Gets a nonblank group name from the supplied grouping function.
	 *
	 * @param grouping batch grouping function
	 * @param key record key
	 * @return caller-supplied group or the default group
	 */
	private static String groupName(Function<RecordIndex.RecordKey, String> grouping, RecordIndex.RecordKey key) {
		String name = grouping == null ? null : grouping.apply(key);
		return name == null || name.isBlank() ? "default" : name;
	}

	/**
	 * Gets or creates one mutable batch accumulator.
	 *
	 * @param batches current accumulators
	 * @param name group name
	 * @return batch accumulator
	 */
	private static MutableBatch batch(Map<String, MutableBatch> batches, String name) {
		return batches.computeIfAbsent(name, ignored -> new MutableBatch());
	}

	/**
	 * Returns a sorted immutable record-key set.
	 *
	 * @param values source keys
	 * @return immutable ordered set
	 */
	private static Set<RecordIndex.RecordKey> immutableSet(Set<RecordIndex.RecordKey> values) {
		return Collections.unmodifiableSet(new LinkedHashSet<>(new TreeSet<>(values)));
	}

	/**
	 * Returns changed fields as ordered immutable collections.
	 *
	 * @param values changed field map
	 * @return immutable ordered map
	 */
	private static Map<RecordIndex.RecordKey, Set<String>> immutableChanges(
			Map<RecordIndex.RecordKey, Set<String>> values) {
		Map<RecordIndex.RecordKey, Set<String>> copy = new LinkedHashMap<>();
		values.forEach((key, fields) -> copy.put(key, immutableSetOfStrings(fields)));
		return Collections.unmodifiableMap(copy);
	}

	/**
	 * Returns sorted immutable field names.
	 *
	 * @param values field names
	 * @return immutable ordered field set
	 */
	private static Set<String> immutableSetOfStrings(Set<String> values) {
		return Collections.unmodifiableSet(new LinkedHashSet<>(new TreeSet<>(values)));
	}

	/**
	 * Mutable operation accumulator used before batches are frozen.
	 */
	private static final class MutableBatch {
		private final Set<RecordIndex.RecordKey> created = new TreeSet<>();
		private final Map<RecordIndex.RecordKey, Set<String>> changed = new TreeMap<>();
		private final Set<RecordIndex.RecordKey> removed = new TreeSet<>();
		private final Set<RecordIndex.RecordKey> struckOut = new TreeSet<>();

		/**
		 * Creates an immutable batch snapshot.
		 *
		 * @return frozen batch
		 */
		private Batch freeze() {
			return new Batch(immutableSet(created), immutableChanges(changed), immutableSet(removed),
					immutableSet(struckOut));
		}
	}
}
