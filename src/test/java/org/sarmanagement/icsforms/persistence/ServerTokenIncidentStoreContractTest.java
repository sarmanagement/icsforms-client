/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.persistence;

import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.model.AppData;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Runs the same contract against a pathless backend issuing nonnumeric server tokens. */
class ServerTokenIncidentStoreContractTest extends AbstractSnapshotIncidentStoreContractTest {
	private final Map<IncidentId, StoredIncident> documents = new HashMap<>();
	private final Set<IncidentId> retired = new HashSet<>();

	/** {@inheritDoc} */
	@Override
	protected IncidentStore openStore() {
		return new RemoteIncidentStore(documents, retired);
	}

	/** {@inheritDoc} */
	@Override
	protected void failNext(IncidentStore store, ControllableIncidentStore.Operation operation) {
		((RemoteIncidentStore) store).failNext(operation);
	}

	@Test
	void accessorIgnoresLegacyMetadataAndRejectsUnissuedObjectsRatherThanReloading() {
		try (IncidentStore store = openStore()) {
			AppData data = new AppData();
			IncidentId id = store.create(data);
			RevisionToken issued = store.revisionToken(data);
			data.setRevision(Long.MAX_VALUE);
			assertEquals(issued, store.revisionToken(data));
			AppData unissued = new AppData();
			unissued.setIncidentId(id.value());
			assertThrows(StoreValidationException.class, () -> store.revisionToken(unissued));
			assertEquals(issued, store.loadSnapshot(id).revision());
		}
	}
}
