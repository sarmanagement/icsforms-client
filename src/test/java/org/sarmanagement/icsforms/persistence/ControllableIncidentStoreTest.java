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

/** Verifies deterministic fault and delay controls for test stores. */
class ControllableIncidentStoreTest {
	@TempDir
	Path directory;

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
