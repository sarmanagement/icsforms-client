/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.model.AppData;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Tests private local journal files and failure behavior. */
class DisconnectedStateFileTest {
	@TempDir
	Path directory;

	@Test
	void journalUsesOwnerOnlyPermissionsAndSurvivesReopening() throws Exception {
		assumeTrue(Files.getFileStore(directory).supportsFileAttributeView("posix"));
		Path root = directory.resolve("cache");
		IncidentId incident = IncidentId.newId();
		Path authority = root.resolve("authority");
		Path journal = authority.resolve(incident.value() + ".json");
		try (DisconnectedStateFile state = new DisconnectedStateFile(root, "authority", incident)) {
			assertNull(state.load());
			state.save(state("first"));
			state.save(state("second"));
			assertFalse(state.failed());
			assertEquals("second", state.load().draft().getIncidentContext().getIncidentName());
			assertEquals(PosixFilePermissions.fromString("rwx------"), Files.getPosixFilePermissions(root));
			assertEquals(PosixFilePermissions.fromString("rwx------"), Files.getPosixFilePermissions(authority));
			assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(journal));
			assertEquals(PosixFilePermissions.fromString("rw-------"),
					Files.getPosixFilePermissions(authority.resolve(incident.value() + ".lock")));
		}
		try (DisconnectedStateFile reopened = new DisconnectedStateFile(root, "authority", incident)) {
			assertNotNull(reopened.load());
			assertEquals("second", reopened.load().draft().getIncidentContext().getIncidentName());
		}
	}

	@Test
	void secondWriterIsExcludedUntilFirstCloses() throws Exception {
		assumeTrue(Files.getFileStore(directory).supportsFileAttributeView("posix"));
		Path root = directory.resolve("cache");
		IncidentId incident = IncidentId.newId();
		try (DisconnectedStateFile state = new DisconnectedStateFile(root, "authority", incident)) {
			assertThrows(StorePersistenceException.class, () -> new DisconnectedStateFile(root, "authority", incident));
		}
		try (DisconnectedStateFile reopened = new DisconnectedStateFile(root, "authority", incident)) {
			assertFalse(reopened.failed());
		}
	}

	@Test
	void stagingSymlinkFailsWithoutModifyingTargetOrCommittedState() throws Exception {
		assumeTrue(Files.getFileStore(directory).supportsFileAttributeView("posix"));
		Path root = directory.resolve("cache");
		IncidentId incident = IncidentId.newId();
		Path victim = directory.resolve("victim");
		Files.writeString(victim, "untouched");
		try (DisconnectedStateFile state = new DisconnectedStateFile(root, "authority", incident)) {
			state.save(state("committed"));
			Path staging = root.resolve("authority").resolve(incident.value() + ".json.new");
			Files.createSymbolicLink(staging, victim);
			assertThrows(StorePersistenceException.class, () -> state.save(state("not committed")));
			assertTrue(state.failed());
			assertEquals("untouched", Files.readString(victim));
			assertEquals("committed", state.load().draft().getIncidentContext().getIncidentName());
		}
	}

	@Test
	void symlinkInCachePathIsRejected() throws Exception {
		assumeTrue(Files.getFileStore(directory).supportsFileAttributeView("posix"));
		Path target = Files.createDirectory(directory.resolve("target"));
		Path root = directory.resolve("link");
		Files.createSymbolicLink(root, target);
		assertThrows(StorePersistenceException.class,
				() -> new DisconnectedStateFile(root, "authority", IncidentId.newId()));
		try (var children = Files.list(target)) {
			assertEquals(0, children.count());
		}
	}

	/**
	 * Creates a small complete journal state.
	 *
	 * @param name incident name to persist
	 * @return state containing the named draft
	 */
	private static DisconnectedStateFile.State state(String name) {
		AppData data = new AppData();
		data.getIncidentContext().setIncidentName(name);
		return new DisconnectedStateFile.State(data, List.of(), 0, data, new RevisionToken("revision"), null, 0,
				List.of());
	}
}
