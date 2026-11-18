/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Focused tests for persisted identity serialization and record-level comparison.
 */
class RecordIdentityTest {
	private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

	/**
	 * Reads legacy identity properties and writes the canonical identity property.
	 *
	 * @throws Exception when Jackson conversion fails
	 */
	@Test
	void legacyIdentityAliasReadsAndCanonicalIdWrites() throws Exception {
		String id = UUID.randomUUID().toString();
		Ics204Form loaded = mapper.readValue("{\"formId\":\"" + id + "\"}", Ics204Form.class);
		JsonNode written = mapper.valueToTree(loaded);

		assertEquals(id, loaded.getId());
		assertEquals(id, written.path("id").asText());
		assertFalse(written.has("formId"));
	}

	/**
	 * Keeps identity absent on reads until load-time migration assigns it.
	 */
	@Test
	void identityGetterDoesNotGenerateId() {
		ActivityLogEntry entry = new ActivityLogEntry();
		entry.setId(null);

		assertEquals(null, entry.getId());
		entry.setId(UUID.randomUUID().toString());
		assertNotNull(entry.getId());
	}

	/**
	 * Ensures every persisted record in the example document has a unique nonblank identity.
	 *
	 * @throws Exception when the example document cannot be read
	 */
	@Test
	void exampleDocumentHasStableIdentityForEveryRecord() throws Exception {
		AppData data = mapper.readValue(Path.of("docs/examples/maple-ridge-dementia-op1.json").toFile(),
				AppData.class);
		IdentityMigration.migrate(data);

		assertIdentities(data, "$", new HashMap<>(), new IdentityHashMap<>());
	}

	/**
	 * Detects a net field edit and ignores a child-list reorder.
	 */
	@Test
	void recordDiffUsesNetFieldValuesAndIgnoresChildReordering() {
		AppData base = new AppData();
		base.normalizeIdentity();
		AppData edited = mapper.convertValue(base, AppData.class);
		edited.normalizeIdentity();
		edited.getIncidentContext().setIncidentName("Changed");
		Ics214Form first = new Ics214Form();
		Ics214Form second = new Ics214Form();
		edited.getActivityLogs().add(first);
		edited.getActivityLogs().add(second);
		AppData reordered = mapper.convertValue(edited, AppData.class);
		reordered.getActivityLogs().clear();
		reordered.getActivityLogs().add(second);
		reordered.getActivityLogs().add(first);

		RecordDiff diff = RecordDiff.between(base, edited);
		RecordDiff reorderedDiff = RecordDiff.between(base, reordered);
		RecordIndex.RecordKey contextKey = new RecordIndex.RecordKey(
				IncidentContext.class.getName(), base.getIncidentContext().getId());

		assertEquals(java.util.Set.of("incidentName"), diff.changed().get(contextKey));
		assertEquals(diff.created(), reorderedDiff.created());
		assertEquals(diff.changed(), reorderedDiff.changed());
		assertTrue(RecordIndex.of(edited).records().containsKey(contextKey));
	}

	/**
	 * Indexes child records separately and groups net creates, removals and strike-outs.
	 */
	@Test
	void recordDiffIndexesNestedRecordsAndGroupsAtomicBatches() {
		AppData base = new AppData();
		Ics214Form form = new Ics214Form();
		ActivityLogEntry unchanged = new ActivityLogEntry();
		unchanged.setNotableActivity("unchanged");
		ActivityLogEntry removed = new ActivityLogEntry();
		removed.setNotableActivity("removed");
		form.getActivityLog().addAll(java.util.List.of(unchanged, removed));
		base.getActivityLogs().add(form);
		base.normalizeIdentity();

		AppData edited = mapper.convertValue(base, AppData.class);
		Ics214Form editedForm = edited.getActivityLogs().get(0);
		ActivityLogEntry editedUnchanged = editedForm.getActivityLog().get(0);
		editedUnchanged.setNotableActivity("temporarily changed");
		editedUnchanged.setNotableActivity("unchanged");
		ActivityLogEntry added = new ActivityLogEntry();
		added.setNotableActivity("added");
		added.setStruckOut(true);
		editedForm.getActivityLog().remove(1);
		editedForm.getActivityLog().add(added);

		RecordIndex index = RecordIndex.of(edited);
		RecordIndex.RecordKey formKey = new RecordIndex.RecordKey(Ics214Form.class.getName(), editedForm.getId());
		RecordIndex.RecordKey addedKey = new RecordIndex.RecordKey(ActivityLogEntry.class.getName(), added.getId());
		RecordIndex.RecordKey removedKey = new RecordIndex.RecordKey(ActivityLogEntry.class.getName(),
				removed.getId());
		JsonNode formSnapshot = index.record(formKey);
		JsonNode children = formSnapshot.path("activityLog");
		RecordDiff diff = RecordDiff.between(base, edited,
				key -> key.type().equals(ActivityLogEntry.class.getName()) ? "events" : "forms");

		assertEquals(formKey, index.parentOf(addedKey));
		assertTrue(children.toString().contains(added.getId()));
		assertTrue(diff.created().contains(addedKey));
		assertTrue(diff.removed().contains(removedKey));
		assertTrue(diff.struckOut().contains(addedKey));
		assertFalse(diff.changed().containsKey(new RecordIndex.RecordKey(ActivityLogEntry.class.getName(),
				unchanged.getId())));
		assertTrue(diff.batches().get("events").created().contains(addedKey));
		assertTrue(diff.batches().get("events").removed().contains(removedKey));
		assertTrue(diff.batches().get("forms").changed().containsKey(formKey));
	}

	/**
	 * Recursively checks all persisted records and collection elements.
	 *
	 * @param value current object
	 * @param path diagnostic object path
	 * @param identities IDs already seen by concrete record type
	 * @param visited objects already inspected
	 * @throws IllegalAccessException when a persisted model field cannot be inspected
	 */
	private void assertIdentities(Object value, String path, Map<Class<?>, Set<String>> identities,
			IdentityHashMap<Object, Boolean> visited) throws IllegalAccessException {
		if (value == null || visited.put(value, Boolean.TRUE) != null) {
			return;
		}
		if (value instanceof Identified identified) {
			assertNotNull(identified.getId(), path);
			assertFalse(identified.getId().isBlank(), path);
			assertTrue(identities.computeIfAbsent(value.getClass(), ignored -> new HashSet<>())
					.add(identified.getId()), path + " duplicate ID " + identified.getId());
		}
		if (value instanceof Collection<?> collection) {
			int index = 0;
			for (Object item : collection) {
				assertRecordCollectionElement(item, path + "[" + index + "]");
				assertIdentities(item, path + "[" + index++ + "]", identities, visited);
			}
		} else if (value instanceof Map<?, ?> map) {
			for (Map.Entry<?, ?> entry : map.entrySet()) {
				assertRecordCollectionElement(entry.getValue(), path + "." + entry.getKey());
				assertIdentities(entry.getValue(), path + "." + entry.getKey(), identities, visited);
			}
		} else if (value.getClass().getPackageName().equals(AppData.class.getPackageName())) {
			for (Class<?> type = value.getClass(); type != null
					&& type.getPackageName().equals(AppData.class.getPackageName()); type = type.getSuperclass()) {
				for (Field field : type.getDeclaredFields()) {
					if (!Modifier.isStatic(field.getModifiers()) && !field.isSynthetic()) {
						field.setAccessible(true);
						assertIdentities(field.get(value), path + "." + field.getName(), identities, visited);
					}
				}
			}
		}
	}

	/**
	 * Rejects record-like collection elements that do not implement the identity contract.
	 *
	 * @param value collection element
	 * @param path diagnostic object path
	 */
	private void assertRecordCollectionElement(Object value, String path) {
		if (value != null && value.getClass().getPackageName().equals(AppData.class.getPackageName())
				&& !(value instanceof Identified)
				&& !Set.of(PlanReviewAssertion.class, Ics215Form.CountPair.class, Ics215Form.ResourceAllocation.class,
						Ics215Form.ResourceLink.class)
						.contains(value.getClass())) {
			throw new AssertionError(path + " is a persisted model collection value without an identity");
		}
	}
}
