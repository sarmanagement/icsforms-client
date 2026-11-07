/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.persistence;

import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;

/** Runs the token contract against the real file backend and controlled failures. */
class FileSnapshotIncidentStoreContractTest extends AbstractSnapshotIncidentStoreContractTest {
	@TempDir
	Path directory;

	/** {@inheritDoc} */
	@Override
	protected IncidentStore openStore() {
		return new ControllableIncidentStore(directory);
	}

	/** {@inheritDoc} */
	@Override
	protected void failNext(IncidentStore store, ControllableIncidentStore.Operation operation) {
		((ControllableIncidentStore) store).failNext(operation,
				new StorePersistenceException("Scheduled operation failure", null));
	}
}
