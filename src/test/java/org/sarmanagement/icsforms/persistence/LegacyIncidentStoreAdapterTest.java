/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.persistence;

import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.model.AppData;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies safe legacy adaptation without adding read-before-write races. */
class LegacyIncidentStoreAdapterTest {
	@Test
	void tokenAwareDeletionNeverFallsBackToUnsafeLegacyDeletion() {
		try (LegacyStore store = new LegacyStore()) {
			assertThrows(UnsupportedOperationException.class,
					() -> store.delete(IncidentId.newId(), new RevisionToken("1")));
			assertFalse(store.loadCalled);
			assertFalse(store.deleteCalled);
		}
	}

	@Test
	void atomicLegacySaveIsDelegatedWithoutAnUnconditionalLoad() {
		try (LegacyStore store = new LegacyStore()) {
			IncidentId id = IncidentId.newId();
			AppData data = new AppData();
			data.setIncidentId(id.value());
			data.setRevision(1);
			RevisionToken committed = store.save(id, data, store.revisionToken(data));
			assertEquals(new RevisionToken("2"), committed);
			assertEquals(2, data.getRevision());
			assertTrue(store.saveCalled);
			assertFalse(store.loadCalled);
		}
	}

	/** Legacy save-only fixture with its own atomic numeric revision comparison. */
	private static final class LegacyStore implements IncidentStore {
		private long revision = 1;
		private boolean loadCalled;
		private boolean deleteCalled;
		private boolean saveCalled;

		/** Creates the focused legacy fixture. */
		private LegacyStore() {
		}

		/** {@inheritDoc} */
		@Override
		public IncidentId create(AppData data) {
			throw new UnsupportedOperationException("Creation is outside this fixture.");
		}

		/** {@inheritDoc} */
		@Override
		public AppData load(IncidentId id) {
			loadCalled = true;
			throw new UnsupportedOperationException("Loading is outside this fixture.");
		}

		/** {@inheritDoc} */
		@Override
		public synchronized void save(IncidentId id, AppData data) {
			if (revision != data.getRevision()) {
				throw new StoreConflictException("Legacy revision conflict");
			}
			saveCalled = true;
			data.setRevision(++revision);
		}

		/** {@inheritDoc} */
		@Override
		public List<IncidentSummary> list() {
			throw new UnsupportedOperationException("Listing is outside this fixture.");
		}

		/** {@inheritDoc} */
		@Override
		public void delete(IncidentId id) {
			deleteCalled = true;
		}

		/** {@inheritDoc} */
		@Override
		public Optional<IncidentId> importFile(Path jsonFile) {
			throw new UnsupportedOperationException("Import is outside this fixture.");
		}

		/** {@inheritDoc} */
		@Override
		public void exportFile(IncidentId id, Path target) {
			throw new UnsupportedOperationException("Export is outside this fixture.");
		}

		/** {@inheritDoc} */
		@Override
		public StoreDescriptor describe() {
			return new StoreDescriptor("Legacy", "fixture:legacy");
		}

		/** {@inheritDoc} */
		@Override
		public void close() {
		}
	}
}
