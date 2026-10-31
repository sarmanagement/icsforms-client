/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.model.AppData;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Safety contracts for external backups and soft deletion. */
class IncidentManagementTest {
	@TempDir
	Path directory;

	@Test
	void backupPreservesUnknownBytesIdentityRevisionAndLocalLock() throws Exception {
		Path root = directory.resolve("store");
		try (FileIncidentStore store = new FileIncidentStore(root);
				FileIncidentStore other = new FileIncidentStore(root)) {
			IncidentId id = store.create(new AppData());
			Path source = document(root, id);
			String original = Files.readString(source).replaceFirst("\\{", "{\n\"futureField\": {\"keep\": true},");
			Files.writeString(source, original);
			long revision = store.load(id).getRevision();
			assertTrue(store.lock(id));
			Path target = directory.resolve("offline.json");
			store.backupFile(id, target);
			assertEquals(original, Files.readString(target));
			assertEquals(original, Files.readString(source));
			assertEquals(id.value(), store.load(id).getIncidentId());
			assertEquals(revision, store.load(id).getRevision());
			assertFalse(other.lock(id), "Backup must retain the editor's preexisting lock");
			store.unlock(id);
			assertTrue(other.lock(id));
		}
	}

	@Test
	void namedCompatibleBackupImportsAsNewIncidentWithoutChangingSource() throws Exception {
		try (FileIncidentStore store = new FileIncidentStore(directory.resolve("store"))) {
			AppData data = new AppData();
			data.getIncidentContext().setIncidentName("Ridge search");
			IncidentId id = store.create(data);
			Path backup = directory.resolve("ridge.json");
			store.backupFile(id, backup);
			IncidentId imported = store.importFile(backup).orElseThrow();
			assertNotEquals(id, imported);
			assertEquals("Ridge search", store.load(imported).getIncidentContext().getIncidentName());
			assertEquals(0, store.load(id).getRevision());
		}
	}

	@Test
	void corruptPrimaryUsesGoodBackupWithoutChangingEitherSourceAndReleasesTransientLock() throws Exception {
		Path root = directory.resolve("store");
		try (FileIncidentStore store = new FileIncidentStore(root);
				FileIncidentStore other = new FileIncidentStore(root)) {
			IncidentId id = store.create(new AppData());
			AppData updated = store.load(id);
			updated.getIncidentContext().setIncidentName("Updated");
			store.save(id, updated);
			store.unlock(id);
			Path source = document(root, id);
			Path automatic = source.resolveSibling(source.getFileName() + ".bak");
			String good = Files.readString(automatic);
			Files.writeString(source, "{corrupt");
			Path external = directory.resolve("recovered.json");
			store.backupFile(id, external);
			assertEquals(good, Files.readString(external));
			assertEquals(good, Files.readString(automatic));
			assertEquals("{corrupt", Files.readString(source));
			assertTrue(other.lock(id), "Transient snapshot lock must be released");
		}
	}

	@Test
	void reservedExistingAndSymlinkTargetsAreRejectedWithoutChanges() throws Exception {
		Path root = directory.resolve("store");
		try (FileIncidentStore store = new FileIncidentStore(root);
				FileIncidentStore other = new FileIncidentStore(root)) {
			IncidentId id = store.create(new AppData());
			Path source = document(root, id);
			String original = Files.readString(source);
			String node = Files.readString(root.resolve("node.json"));
			Path existing = directory.resolve("existing.json");
			Files.writeString(existing, "keep existing");
			Path alias = directory.resolve("alias");
			Files.createSymbolicLink(alias, root);
			Path ancestorAlias = directory.resolve("ancestor");
			Files.createSymbolicLink(ancestorAlias, directory);
			Path fileAlias = directory.resolve("source-link.json");
			Files.createSymbolicLink(fileAlias, source);
			for (Path target : new Path[]{source, root.resolve("node.json"), root.resolve("new.json"),
					root.resolve("incidents/new.json"), root.resolve("incidents/" + id.value() + ".lock"),
					alias.resolve("new.json"), ancestorAlias.resolve("store/new.json"), existing, fileAlias,
					directory.resolve("missing-parent/new.json")}) {
				assertThrows(RuntimeException.class, () -> store.backupFile(id, target), target.toString());
				assertEquals(original, Files.readString(source));
				assertEquals(node, Files.readString(root.resolve("node.json")));
				assertEquals("keep existing", Files.readString(existing));
				assertTrue(other.lock(id), "Failure must release only the transient lock");
				other.unlock(id);
			}
			assertFalse(Files.exists(root.resolve("new.json")));
			assertFalse(Files.exists(root.resolve("incidents/new.json")));
			assertTrue(Files.isSymbolicLink(fileAlias));
		}
	}

	@Test
	void physicalTargetsInsideSymlinkedIncidentsAreRejectedWithoutChanges() throws Exception {
		Path root = Files.createDirectory(directory.resolve("store"));
		Path physicalIncidents = Files.createDirectory(directory.resolve("physical-incidents"));
		Files.createSymbolicLink(root.resolve("incidents"), physicalIncidents);
		try (FileIncidentStore store = new FileIncidentStore(root)) {
			IncidentId id = store.create(new AppData());
			Path source = document(root, id);
			String original = Files.readString(source);
			for (Path target : new Path[]{physicalIncidents.resolve("new.json"),
					physicalIncidents.resolve(id.value() + ".json.bak")}) {
				IllegalStateException failure = assertThrows(IllegalStateException.class,
						() -> store.backupFile(id, target), target.toString());
				assertTrue(failure.getMessage().contains("outside the live incident store"));
				assertFalse(Files.exists(target));
				assertEquals(original, Files.readString(source));
			}
		}
	}

	@Test
	void lockedDeletionRejectsBothLocalAndOtherEditorsAndNeverReleasesTheirLocks() {
		Path root = directory.resolve("store");
		try (FileIncidentStore store = new FileIncidentStore(root);
				FileIncidentStore other = new FileIncidentStore(root)) {
			IncidentId id = store.create(new AppData());
			assertTrue(store.lock(id));
			assertThrows(StoreConflictException.class, () -> store.delete(id));
			assertThrows(StoreConflictException.class, () -> other.delete(id));
			assertThrows(StoreConflictException.class, () -> other.backupFile(id, directory.resolve("locked.json")));
			assertFalse(other.lock(id));
			assertTrue(Files.exists(document(root, id)));
			assertFalse(Files.exists(directory.resolve("locked.json")));
			store.unlock(id);
			assertTrue(other.lock(id), "Rejected deletion must not damage the original editor's lock");
			assertThrows(StoreConflictException.class, () -> store.delete(id));
			assertFalse(store.lock(id));
		}
	}

	@Test
	void unlockedDeletionTombstonesRetainsBackupsAndIsIdempotent() throws Exception {
		Path root = directory.resolve("store");
		try (FileIncidentStore store = new FileIncidentStore(root);
				FileIncidentStore other = new FileIncidentStore(root)) {
			IncidentId id = store.create(new AppData());
			IncidentId unrelated = store.create(new AppData());
			store.save(id, store.load(id));
			store.unlock(id);
			Path source = document(root, id);
			String primary = Files.readString(source);
			Path automatic = source.resolveSibling(source.getFileName() + ".bak");
			String previous = Files.readString(automatic);
			String untouched = Files.readString(document(root, unrelated));
			AppData deletedDocument = store.load(id);
			store.delete(id);
			store.delete(id);
			assertFalse(Files.exists(source));
			assertEquals(primary, Files.readString(source.resolveSibling(source.getFileName() + ".deleted")));
			assertEquals(previous, Files.readString(automatic));
			assertEquals(untouched, Files.readString(document(root, unrelated)));
			assertEquals(1, store.list().size());
			assertTrue(other.lock(id));
			other.unlock(id);
			assertThrows(IllegalArgumentException.class, () -> store.load(id));
			assertThrows(IllegalStateException.class, () -> store.create(deletedDocument));
		}
	}

	@Test
	void failedBackupOrDeletionKeepsExistingEditorLocksAndSources() throws Exception {
		Path root = directory.resolve("store");
		try (FileIncidentStore store = new FileIncidentStore(root);
				FileIncidentStore other = new FileIncidentStore(root)) {
			IncidentId id = store.create(new AppData());
			Path source = document(root, id);
			Files.writeString(source, "{corrupt");
			Path output = directory.resolve("invalid.json");
			assertTrue(store.lock(id));
			assertThrows(IllegalStateException.class, () -> store.backupFile(id, output));
			assertFalse(other.lock(id));
			assertFalse(Files.exists(output));
			store.unlock(id);
			assertThrows(IllegalStateException.class, () -> store.backupFile(id, output));
			assertTrue(other.lock(id));
			other.unlock(id);
			IncidentId missing = IncidentId.newId();
			assertThrows(IllegalStateException.class, () -> store.delete(missing));
			assertTrue(other.lock(missing));
			assertEquals("{corrupt", Files.readString(source));
		}
	}

	@Test
	void newerSchemaIsNotBackedUpUsingAnOlderFallback() throws Exception {
		Path root = directory.resolve("store");
		try (FileIncidentStore store = new FileIncidentStore(root);
				FileIncidentStore other = new FileIncidentStore(root)) {
			IncidentId id = store.create(new AppData());
			Path source = document(root, id);
			Path automatic = source.resolveSibling(source.getFileName() + ".bak");
			String good = Files.readString(source);
			Files.writeString(automatic, good);
			String future = good.replace("\"schemaVersion\" : " + AppData.CURRENT_SCHEMA_VERSION,
					"\"schemaVersion\" : " + (AppData.CURRENT_SCHEMA_VERSION + 1));
			assertNotEquals(good, future);
			Files.writeString(source, future);
			Path target = directory.resolve("future.json");
			assertThrows(IllegalStateException.class, () -> store.backupFile(id, target));
			assertFalse(Files.exists(target));
			assertEquals(future, Files.readString(source));
			assertEquals(good, Files.readString(automatic));
			assertTrue(other.lock(id));
		}
	}

	@Test
	void deletionFailureNeverReplacesAnExistingTombstone() throws Exception {
		Path root = directory.resolve("store");
		try (FileIncidentStore store = new FileIncidentStore(root);
				FileIncidentStore other = new FileIncidentStore(root)) {
			IncidentId id = store.create(new AppData());
			Path source = document(root, id);
			String original = Files.readString(source);
			Path deleted = source.resolveSibling(source.getFileName() + ".deleted");
			Files.writeString(deleted, "retained tombstone");
			assertThrows(IllegalStateException.class, () -> store.delete(id));
			assertEquals(original, Files.readString(source));
			assertEquals("retained tombstone", Files.readString(deleted));
			assertTrue(other.lock(id));
		}
	}

	/**
	 * Resolves the primary document in a test store.
	 *
	 * @param root store root.
	 * @param id incident identity.
	 * @return primary JSON path.
	 */
	private static Path document(Path root, IncidentId id) {
		return root.resolve("incidents").resolve(id.value() + ".json");
	}
}
