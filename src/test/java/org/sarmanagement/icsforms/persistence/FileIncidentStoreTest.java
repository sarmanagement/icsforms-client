package org.sarmanagement.icsforms.persistence;

import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.model.AppData;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** File-specific behavior of the multi-incident store. */
class FileIncidentStoreTest extends AbstractIncidentStoreContractTest {
	@Override
	protected IncidentStore createStore(Path directory) {
		return new FileIncidentStore(directory);
	}

	/** Backups and temporary writes are isolated per incident. */
	@Test
	void backupsArePerIncidentAndCorruptionRecovers() throws Exception {
		try (FileIncidentStore store = new FileIncidentStore(tempDir)) {
			IncidentId first = store.create(new AppData());
			IncidentId second = store.create(new AppData());
			AppData one = store.load(first);
			AppData two = store.load(second);
			one.getIncidentContext().setIncidentName("One");
			two.getIncidentContext().setIncidentName("Two");
			store.save(first, one);
			store.save(second, two);
			Path firstFile = tempDir.resolve("incidents").resolve(first.value() + ".json");
			Path secondFile = tempDir.resolve("incidents").resolve(second.value() + ".json");
			assertTrue(Files.exists(firstFile.resolveSibling(firstFile.getFileName() + ".bak")));
			assertTrue(Files.exists(secondFile.resolveSibling(secondFile.getFileName() + ".bak")));
			assertFalse(Files.exists(firstFile.resolveSibling(firstFile.getFileName() + ".tmp")));
			Files.writeString(firstFile, "{invalid");
			assertEquals(first, IncidentId.of(store.load(first).getIncidentId()));
			assertEquals("Two", store.load(second).getIncidentContext().getIncidentName());
		}
	}

	/** Single-workspace migration leaves its input untouched and runs only once. */
	@Test
	void migrationIsOnceOnly() throws Exception {
		Files.writeString(tempDir.resolve("incident.json"), "{\"schemaVersion\":5}");
		try (FileIncidentStore store = new FileIncidentStore(tempDir)) {
			assertEquals(1, store.list().size());
			assertEquals(6, store.load(store.list().get(0).incidentId()).getSchemaVersion());
			assertEquals(0, store.list().get(0).revision());
			assertTrue(Files.exists(tempDir.resolve("incident.json.migrated")));
			assertEquals("{\"schemaVersion\":5}", Files.readString(tempDir.resolve("incident.json")));
		}
		try (FileIncidentStore reopened = new FileIncidentStore(tempDir)) {
			assertEquals(1, reopened.list().size());
		}
	}

	/** Missing v5 identity is assigned on load and retained when saved. */
	@Test
	void legacyVersionInIncidentDirectoryUpgradesOnLoad() throws Exception {
		IncidentId id = IncidentId.newId();
		Path directory = tempDir.resolve("incidents");
		Files.createDirectories(directory);
		Files.writeString(directory.resolve(id.value() + ".json"),
				"{\"schemaVersion\":5,\"revision\":0,\"futureField\":true}");
		try (FileIncidentStore store = new FileIncidentStore(tempDir)) {
			AppData upgraded = store.load(id);
			assertEquals(id.value(), upgraded.getIncidentId());
			assertEquals(AppData.CURRENT_SCHEMA_VERSION, upgraded.getSchemaVersion());
			assertEquals(0, upgraded.getRevision());
			store.save(id, upgraded);
			assertEquals(1, store.load(id).getRevision());
		}
	}

	/** A second store cannot acquire an incident's advisory lock. */
	@Test
	void anotherStoreDetectsLock() {
		try (FileIncidentStore first = new FileIncidentStore(tempDir);
				FileIncidentStore second = new FileIncidentStore(tempDir)) {
			IncidentId id = first.create(new AppData());
			assertTrue(first.lock(id));
			assertTrue(second.isLockedElsewhere(id));
			assertFalse(second.lock(id));
			assertThrows(StoreConflictException.class, () -> second.save(id, second.load(id)));
			assertNotEquals("", first.load(id).getOriginNodeId());
			first.unlock(id);
			assertFalse(second.isLockedElsewhere(id));
		}
		try (FileIncidentStore reopened = new FileIncidentStore(tempDir)) {
			assertFalse(reopened.isLockedElsewhere(reopened.list().get(0).incidentId()));
		}
	}

	/** Store restarts preserve the installation's origin node identity. */
	@Test
	void nodeIdentityPersistsAcrossStoreInstances() {
		String node;
		try (FileIncidentStore first = new FileIncidentStore(tempDir)) {
			node = first.load(first.create(new AppData())).getOriginNodeId();
		}
		try (FileIncidentStore second = new FileIncidentStore(tempDir)) {
			IncidentId next = second.create(new AppData());
			assertEquals(node, second.load(next).getOriginNodeId());
		}
	}
}
