package org.sarmanagement.icsforms.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.model.AppData;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reusable behavioral contract for incident stores. */
public abstract class AbstractIncidentStoreContractTest {
	@TempDir
	Path tempDir;

	/**
	 * Creates a store under an isolated directory.
	 *
	 * @param directory
	 *            isolated test directory.
	 * @return open store.
	 */
	protected abstract IncidentStore createStore(Path directory);

	/** Distinct incidents retain independent identities and documents. */
	@Test
	void multipleIncidentsAreIndependent() {
		try (IncidentStore store = createStore(tempDir)) {
			AppData first = named("First");
			AppData second = named("Second");
			IncidentId firstId = store.create(first);
			IncidentId secondId = store.create(second);
			assertNotEquals(firstId, secondId);
			assertEquals(2, store.list().size());
			assertEquals("First", store.load(firstId).getIncidentContext().getIncidentName());
			assertEquals("Second", store.load(secondId).getIncidentContext().getIncidentName());
			assertThrows(IllegalStateException.class, () -> store.create(first));
		}
	}

	/** Saving increments the revision, but stale documents cannot overwrite it. */
	@Test
	void revisionProtectsWrites() {
		try (IncidentStore store = createStore(tempDir)) {
			IncidentId id = store.create(named("Initial"));
			AppData current = store.load(id);
			AppData stale = store.load(id);
			current.getIncidentContext().setIncidentName("Changed");
			store.save(id, current);
			assertEquals(1, current.getRevision());
			assertEquals(1, store.load(id).getRevision());
			assertThrows(StoreConflictException.class, () -> store.save(id, stale));
			assertEquals("Changed", store.load(id).getIncidentContext().getIncidentName());
		}
	}

	/** Tombstones are hidden and cannot be reopened. */
	@Test
	void deletedIncidentsStayDeleted() {
		try (IncidentStore store = createStore(tempDir)) {
			IncidentId id = store.create(named("Deleted"));
			store.delete(id);
			assertTrue(store.list().isEmpty());
			assertThrows(IllegalArgumentException.class, () -> store.load(id));
		}
	}

	/** Exported JSON can be imported as an independent incident. */
	@Test
	void exportAndImportCreateIndependentCopies() {
		try (IncidentStore store = createStore(tempDir)) {
			IncidentId id = store.create(named("Exported"));
			Path json = tempDir.resolve("export.json");
			store.exportFile(id, json);
			IncidentId copy = store.importFile(json).orElseThrow();
			assertNotEquals(id, copy);
			assertEquals("Exported", store.load(copy).getIncidentContext().getIncidentName());
			assertEquals(2, store.list().size());
		}
	}

	/** Recently updated incidents appear first. */
	@Test
	void listSortsMostRecentlyUpdatedFirst() {
		try (IncidentStore store = createStore(tempDir)) {
			IncidentId old = store.create(named("Old"));
			IncidentId recent = store.create(named("Recent"));
			AppData updated = store.load(old);
			store.save(old, updated);
			assertEquals(old, store.list().get(0).incidentId());
			assertEquals(recent, store.list().get(1).incidentId());
			assertFalse(store.describe().location().isBlank());
		}
	}

	/**
	 * Creates a named document.
	 *
	 * @param name
	 *            incident name.
	 * @return document.
	 */
	private AppData named(String name) {
		AppData data = new AppData();
		data.getIncidentContext().setIncidentName(name);
		return data;
	}
}
