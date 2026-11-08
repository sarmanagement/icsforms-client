/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms;

import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.IapPhase;
import org.sarmanagement.icsforms.model.IncidentMode;
import org.sarmanagement.icsforms.persistence.ControllableIncidentStore;
import org.sarmanagement.icsforms.persistence.ControllableIncidentStore.Operation;

import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** Startup waits for detached persistence results while Swing dispatch remains active. */
class AppStartupPersistenceTest {
	@Test
	void startupCreateLoadAndNewPeriodSaveKeepSwingResponsiveUntilCompletion() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		Path root = Files.createDirectories(Path.of("target", "startup-worker-" + UUID.randomUUID()));
		try (ControllableIncidentStore store = new ControllableIncidentStore(root)) {
			var id = store.create(new AppData());
			for (Operation operation : new Operation[]{Operation.CREATE, Operation.LOAD, Operation.SAVE}) {
				var gate = store.blockNext(operation);
				AtomicReference<Object> result = new AtomicReference<>();
				AtomicReference<Throwable> failure = new AtomicReference<>();
				CountDownLatch finished = new CountDownLatch(1);
				SwingUtilities.invokeLater(() -> {
					try {
						result.set(operation == Operation.CREATE
								? App.createNewIncident(store, IapPhase.INITIAL_RESPONSE, IncidentMode.SAR)
								: App.loadExistingIncident(store, id, operation == Operation.SAVE, false));
					} catch (Throwable exception) {
						failure.set(exception);
					} finally {
						finished.countDown();
					}
				});
				try {
					assertTrue(gate.awaitStarted(5, TimeUnit.SECONDS));
					CountDownLatch heartbeat = new CountDownLatch(1);
					SwingUtilities.invokeLater(heartbeat::countDown);
					assertTrue(heartbeat.await(5, TimeUnit.SECONDS), "Startup must dispatch while I/O waits");
					assertNull(result.get(), "No partially loaded or prematurely saved startup result");
					assertEquals(1, finished.getCount());
				} finally {
					gate.release();
					assertTrue(finished.await(5, TimeUnit.SECONDS));
				}
				assertNull(failure.get());
				assertNotNull(result.get());
				if (operation == Operation.SAVE) {
					assertEquals(1, store.load(id).getOperationalPeriodHistory().size());
				}
			}
		} finally {
			deleteTree(root);
		}
	}

	/**
	 * Removes the project-local fixture after store handles have closed.
	 *
	 * @param root fixture root.
	 * @throws java.io.IOException if cleanup fails.
	 */
	private static void deleteTree(Path root) throws java.io.IOException {
		try (var paths = Files.walk(root)) {
			for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
				Files.deleteIfExists(path);
			}
		}
	}
}
