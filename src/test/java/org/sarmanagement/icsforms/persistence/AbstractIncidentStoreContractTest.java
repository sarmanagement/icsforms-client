/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.model.AppData;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Legacy numeric file-store compatibility checks. Backend-neutral token semantics
 * are verified by {@link AbstractSnapshotIncidentStoreContractTest}.
 */
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

	/**
	 * Acquires an edit lock through the implementation's lock API.
	 *
	 * @param store store under test.
	 * @param id incident identity.
	 * @return {@code true} when the lock was acquired.
	 */
	protected abstract boolean lock(IncidentStore store, IncidentId id);

	/**
	 * Releases an edit lock through the implementation's lock API.
	 *
	 * @param store store under test.
	 * @param id incident identity.
	 */
	protected abstract void unlock(IncidentStore store, IncidentId id);

	/**
	 * Schedules a deterministic save failure.
	 *
	 * @param store store under test.
	 */
	protected abstract void failNextSave(IncidentStore store);

	/**
	 * Schedules a deterministic delete failure.
	 *
	 * @param store store under test.
	 */
	protected abstract void failNextDelete(IncidentStore store);

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

	/** Exactly one simultaneous update from the same revision can commit. */
	@Test
	void concurrentWritesRejectOneStaleSnapshot() throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try (IncidentStore store = createStore(tempDir)) {
			IncidentId id = store.create(named("Concurrent"));
			AppData first = store.load(id);
			AppData second = store.load(id);
			first.getIncidentContext().setIncidentName("First writer");
			second.getIncidentContext().setIncidentName("Second writer");
			CountDownLatch ready = new CountDownLatch(2);
			CountDownLatch start = new CountDownLatch(1);
			Future<Boolean> firstResult = executor.submit(() -> trySave(store, id, first, ready, start));
			Future<Boolean> secondResult = executor.submit(() -> trySave(store, id, second, ready, start));

			assertTrue(ready.await(5, TimeUnit.SECONDS));
			start.countDown();
			assertNotEquals(firstResult.get(5, TimeUnit.SECONDS), secondResult.get(5, TimeUnit.SECONDS));
			assertEquals(1, store.load(id).getRevision());
		} finally {
			executor.shutdownNow();
		}
	}

	/** Separate loads are detached while preserving the incident's stable identity. */
	@Test
	void loadsAreDetachedAndPreserveIdentity() {
		try (IncidentStore store = createStore(tempDir)) {
			IncidentId id = store.create(named("Detached"));
			AppData first = store.load(id);
			AppData second = store.load(id);
			assertNotSame(first, second);
			assertEquals(id.value(), first.getIncidentId());
			assertEquals(id.value(), second.getIncidentId());
			first.getIncidentContext().setIncidentName("Changed only in memory");
			assertEquals("Detached", store.load(id).getIncidentContext().getIncidentName());
			assertEquals(0, store.load(id).getRevision());
		}
	}

	/** Saved data and its identity survive closing and reopening a store. */
	@Test
	void persistedIncidentsSurviveRestart() {
		IncidentId id;
		try (IncidentStore store = createStore(tempDir)) {
			AppData data = named("Durable");
			id = store.create(data);
			data.getIncidentContext().setIncidentName("Saved before restart");
			store.save(id, data);
		}
		try (IncidentStore reopened = createStore(tempDir)) {
			AppData restored = reopened.load(id);
			assertEquals(id.value(), restored.getIncidentId());
			assertEquals("Saved before restart", restored.getIncidentContext().getIncidentName());
			assertEquals(1, restored.getRevision());
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

	/** An active editor prevents deletion without erasing its persisted document. */
	@Test
	void deletionConflictsWithAnEditLock() {
		try (IncidentStore store = createStore(tempDir)) {
			IncidentId id = store.create(named("Locked"));
			assertTrue(lock(store, id));
			assertThrows(StoreConflictException.class, () -> store.delete(id));
			assertEquals("Locked", store.load(id).getIncidentContext().getIncidentName());
			assertEquals(1, store.list().size());
			unlock(store, id);
			store.delete(id);
			assertTrue(store.list().isEmpty());
		}
	}

	/** Failed writes and deletes preserve persisted content and the caller's edit. */
	@Test
	void failedOperationsPreservePersistedAndUnsavedData() {
		try (IncidentStore store = createStore(tempDir)) {
			IncidentId id = store.create(named("Persisted"));
			AppData unsaved = store.load(id);
			unsaved.getIncidentContext().setIncidentName("Unsaved editor input");
			failNextSave(store);
			assertThrows(IllegalStateException.class, () -> store.save(id, unsaved));
			assertEquals("Unsaved editor input", unsaved.getIncidentContext().getIncidentName());
			assertEquals(0, unsaved.getRevision());
			assertEquals("Persisted", store.load(id).getIncidentContext().getIncidentName());

			failNextDelete(store);
			assertThrows(IllegalStateException.class, () -> store.delete(id));
			assertEquals("Persisted", store.load(id).getIncidentContext().getIncidentName());
			assertEquals(1, store.list().size());
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

	/**
	 * Attempts a store update after both concurrent writers are ready.
	 *
	 * @param store store under test.
	 * @param id incident identity.
	 * @param data writer snapshot.
	 * @param ready barrier tracking waiting writers.
	 * @param start release barrier for simultaneous writes.
	 * @return {@code true} when the update committed.
	 * @throws InterruptedException if interrupted while waiting at the barrier.
	 */
	private boolean trySave(IncidentStore store, IncidentId id, AppData data, CountDownLatch ready,
			CountDownLatch start) throws InterruptedException {
		ready.countDown();
		start.await();
		try {
			store.save(id, data);
			return true;
		} catch (StoreConflictException exception) {
			return false;
		}
	}
}
