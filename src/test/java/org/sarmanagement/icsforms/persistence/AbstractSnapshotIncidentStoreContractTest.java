/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.persistence;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.model.AppData;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Whole-document contract shared by file and lockless server-token backends. */
abstract class AbstractSnapshotIncidentStoreContractTest {
	private static final ObjectMapper RAW = new ObjectMapper().registerModule(new JavaTimeModule())
			.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE)
			.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);

	/**
	 * Opens a new handle on the test's persistent store scope.
	 *
	 * @return open handle
	 */
	protected abstract IncidentStore openStore();

	/**
	 * Schedules an operation failure before it commits.
	 *
	 * @param store handle
	 * @param operation operation to fail
	 */
	protected abstract void failNext(IncidentStore store, ControllableIncidentStore.Operation operation);

	@Test
	void constructorCompatibleAccessorRetainsStaleCallerTokens() {
		try (IncidentStore store = openStore()) {
			AppData original = named("Constructor input");
			IncidentId id = store.create(original);
			RevisionToken issued = store.revisionToken(original);
			StoredIncident stale = store.loadSnapshot(id);
			assertEquals(issued, store.revisionToken(stale.data()));
			StoredIncident writer = store.loadSnapshot(id);
			writer.data().getIncidentContext().setIncidentName("Other writer");
			RevisionToken committed = store.save(id, writer.data(), writer.revision());
			assertEquals(committed, store.revisionToken(writer.data()));
			assertEquals(issued, store.revisionToken(original));
			assertEquals(issued, store.revisionToken(stale.data()));
			StoreConflictException conflict = assertThrows(StoreConflictException.class,
					() -> store.save(id, original, store.revisionToken(original)));
			assertEquals(issued, conflict.expected());
			assertEquals(committed, conflict.current());
			assertEquals(issued, store.revisionToken(original));
			assertEquals("Constructor input", original.getIncidentContext().getIncidentName());
		}
	}

	@Test
	void tokensProtectSaveAndDeleteWithoutLocks() {
		try (IncidentStore store = openStore()) {
			IncidentId id = store.create(named("Original"));
			StoredIncident first = store.loadSnapshot(id);
			StoredIncident stale = store.loadSnapshot(id);
			first.data().getIncidentContext().setIncidentName("Committed");
			first.data().setRevision(987654); // Transport-neutral writes ignore legacy model revisions.
			RevisionToken committed = store.save(id, first.data(), first.revision());
			assertNotEquals(first.revision(), committed);
			assertEquals(committed, store.loadSnapshot(id).revision());
			assertEquals(committed, store.list().get(0).revision());
			JsonNode unsaved = raw(stale.data());
			StoreConflictException saveConflict = assertThrows(StoreConflictException.class,
					() -> store.save(id, stale.data(), stale.revision()));
			assertEquals(stale.revision(), saveConflict.expected());
			assertEquals(committed, saveConflict.current());
			assertEquals(unsaved, raw(stale.data()));
			StoreConflictException deleteConflict = assertThrows(StoreConflictException.class,
					() -> store.delete(id, stale.revision()));
			assertEquals(stale.revision(), deleteConflict.expected());
			assertEquals(committed, deleteConflict.current());
			assertEquals("Committed", store.loadSnapshot(id).data().getIncidentContext().getIncidentName());
			store.delete(id, committed);
			assertTrue(store.list().isEmpty());
			assertThrows(IncidentNotFoundException.class, () -> store.loadSnapshot(id));
			assertThrows(IncidentNotFoundException.class, () -> store.save(id, first.data(), committed));
			assertThrows(IncidentNotFoundException.class, () -> store.delete(id, committed));
			assertThrows(DuplicateIncidentException.class, () -> store.create(first.data()));
		}
	}

	@Test
	void failuresPreserveEntireCallerAndStoredSnapshot() {
		try (IncidentStore store = openStore()) {
			AppData creation = named("Before create");
			JsonNode beforeCreate = raw(creation);
			failNext(store, ControllableIncidentStore.Operation.CREATE);
			assertThrows(StorePersistenceException.class, () -> store.create(creation));
			assertEquals(beforeCreate, raw(creation));
			assertTrue(store.list().isEmpty());
			IncidentId id = store.create(creation);
			StoredIncident loaded = store.loadSnapshot(id);
			loaded.data().setSchemaVersion(1);
			loaded.data().getIncidentContext().setIncidentName("Unsaved");
			JsonNode caller = raw(loaded.data());
			failNext(store, ControllableIncidentStore.Operation.SAVE);
			assertThrows(StorePersistenceException.class,
					() -> store.save(id, loaded.data(), loaded.revision()));
			assertEquals(caller, raw(loaded.data()));
			assertEquals(loaded.revision(), store.loadSnapshot(id).revision());
			assertEquals("Before create", store.loadSnapshot(id).data().getIncidentContext().getIncidentName());
			failNext(store, ControllableIncidentStore.Operation.DELETE);
			assertThrows(StorePersistenceException.class, () -> store.delete(id, loaded.revision()));
			assertEquals(1, store.list().size());
			assertEquals(caller, raw(loaded.data()));
		}
	}

	@Test
	void snapshotsAreDetachedAndValidationPreservesCaller() {
		try (IncidentStore store = openStore()) {
			IncidentId id = store.create(named("Detached"));
			StoredIncident first = store.loadSnapshot(id);
			StoredIncident second = store.loadSnapshot(id);
			assertNotSame(first.data(), second.data());
			first.data().getIncidentContext().setIncidentName("Unsaved");
			assertEquals("Detached", second.data().getIncidentContext().getIncidentName());
			assertEquals(id.value(), first.data().getIncidentId());
			first.data().setIncidentId(IncidentId.newId().value());
			JsonNode before = raw(first.data());
			assertThrows(StoreValidationException.class, () -> store.save(id, first.data(), first.revision()));
			assertEquals(before, raw(first.data()));
			assertEquals("Detached", store.loadSnapshot(id).data().getIncidentContext().getIncidentName());
			assertThrows(IncidentNotFoundException.class, () -> store.loadSnapshot(IncidentId.newId()));
		}
	}

	@Test
	void closeIsIdempotentAndReopenRetainsIdentityAndToken() {
		IncidentStore store = openStore();
		IncidentId id = store.create(named("Durable"));
		StoredIncident snapshot = store.loadSnapshot(id);
		store.close();
		store.close();
		assertThrows(StoreUnavailableException.class, () -> store.loadSnapshot(id));
		assertThrows(StoreUnavailableException.class, store::list);
		assertThrows(StoreUnavailableException.class, () -> store.create(named("Closed")));
		assertThrows(StoreUnavailableException.class, () -> store.save(id, snapshot.data(), snapshot.revision()));
		assertThrows(StoreUnavailableException.class, () -> store.delete(id, snapshot.revision()));
		try (IncidentStore reopened = openStore()) {
			assertEquals(snapshot.revision(), reopened.loadSnapshot(id).revision());
			assertEquals(id.value(), reopened.loadSnapshot(id).data().getIncidentId());
		}
	}

	@Test
	void capabilitiesDoNotRequirePathsOrLocks() {
		try (IncidentStore store = openStore()) {
			IncidentId id = store.create(named("Neutral"));
			IncidentSummary summary = store.list().get(0);
			assertEquals(id, summary.incidentId());
			assertFalse(summary.storeLocation().isBlank());
			if (store.editLocks().isPresent()) {
				EditLockSupport locks = store.editLocks().orElseThrow();
				assertTrue(locks.lock(id));
				StoredIncident snapshot = store.loadSnapshot(id);
				assertThrows(StoreLockConflictException.class, () -> store.delete(id, snapshot.revision()));
				locks.unlock(id);
				store.delete(id, snapshot.revision());
			}
		}
	}

	@Test
	void onlyOneConcurrentCompareAndWriteCommits() throws Exception {
		var executor = Executors.newFixedThreadPool(2);
		try (IncidentStore firstStore = openStore(); IncidentStore secondStore = openStore()) {
			IncidentId id = firstStore.create(named("Concurrent"));
			StoredIncident first = firstStore.loadSnapshot(id);
			StoredIncident second = secondStore.loadSnapshot(id);
			var ready = new CountDownLatch(2);
			var start = new CountDownLatch(1);
			var firstResult = executor.submit(() -> attempt(firstStore, id, first, ready, start));
			var secondResult = executor.submit(() -> attempt(secondStore, id, second, ready, start));
			assertTrue(ready.await(5, TimeUnit.SECONDS));
			start.countDown();
			assertNotEquals(firstResult.get(5, TimeUnit.SECONDS), secondResult.get(5, TimeUnit.SECONDS));
			assertNotEquals(first.revision(), firstStore.loadSnapshot(id).revision());
		} finally {
			executor.shutdownNow();
		}
	}

	/**
	 * Attempts a concurrent token-aware save.
	 *
	 * @param store writer handle
	 * @param id incident identity
	 * @param snapshot writer's initial snapshot
	 * @param ready ready barrier
	 * @param start start barrier
	 * @return whether the save committed
	 * @throws InterruptedException when interrupted at the barrier
	 */
	private boolean attempt(IncidentStore store, IncidentId id, StoredIncident snapshot,
			CountDownLatch ready, CountDownLatch start) throws InterruptedException {
		ready.countDown();
		start.await();
		try {
			store.save(id, snapshot.data(), snapshot.revision());
			return true;
		} catch (StoreConflictException conflict) {
			return false;
		}
	}

	/**
	 * Creates a named incident.
	 *
	 * @param name incident name
	 * @return mutable document
	 */
	private AppData named(String name) {
		AppData data = new AppData();
		data.getIncidentContext().setIncidentName(name);
		return data;
	}

	/**
	 * Captures fields without invoking normalizing getters.
	 *
	 * @param data caller-owned document
	 * @return immutable comparison tree
	 */
	private JsonNode raw(AppData data) {
		return RAW.valueToTree(data);
	}
}
