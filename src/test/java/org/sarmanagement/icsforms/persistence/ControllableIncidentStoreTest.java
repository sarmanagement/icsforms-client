/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.model.AppData;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Verifies deterministic fault and delay controls for test stores. */
class ControllableIncidentStoreTest {
	@TempDir
	Path directory;

	@Test
	void ownershipDelegationDistinguishesExistingAndTransientLocks() {
		try (ControllableIncidentStore store = new ControllableIncidentStore(directory);
				FileIncidentStore other = new FileIncidentStore(directory)) {
			IncidentId id = store.create(new AppData());
			EditLockSupport support = store.editLocks().orElseThrow();
			assertFalse(store.isHeldByCaller(id));
			assertFalse(support.isHeldByCaller(id));
			StoredIncident snapshot = store.loadSnapshot(id);
			store.save(id, snapshot.data(), snapshot.revision());
			assertFalse(store.isHeldByCaller(id));
			assertTrue(store.lock(id));
			assertTrue(store.isHeldByCaller(id));
			assertTrue(support.isHeldByCaller(id));
			snapshot = store.loadSnapshot(id);
			store.save(id, snapshot.data(), snapshot.revision());
			assertTrue(store.isHeldByCaller(id));
			StoredIncident stale = snapshot;
			assertThrows(StoreConflictException.class,
					() -> store.save(id, stale.data(), stale.revision()));
			assertTrue(support.isHeldByCaller(id));
			assertFalse(other.lock(id));
			store.unlock(id);
			assertFalse(store.isHeldByCaller(id));
			assertTrue(other.lock(id));
		}
	}

	@Test
	void blockedOperationResumesOnlyWhenGateIsReleased() throws Exception {
		try (ControllableIncidentStore store = new ControllableIncidentStore(directory)) {
			IncidentId id = store.create(new AppData());
			ControllableIncidentStore.OperationGate gate = store.blockNext(ControllableIncidentStore.Operation.LOAD);

			CompletableFuture<AppData> load = CompletableFuture.supplyAsync(() -> store.load(id));

			assertTrue(gate.awaitStarted(5, TimeUnit.SECONDS));
			assertFalse(load.isDone());
			gate.release();
			assertEquals(id.value(), load.get(5, TimeUnit.SECONDS).getIncidentId());
		}
	}
}
