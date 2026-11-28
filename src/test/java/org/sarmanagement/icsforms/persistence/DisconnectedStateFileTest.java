/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.model.AppData;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

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
	void olderJournalWithoutAcceptanceIndexLoadsWithoutInventingLabels() throws Exception {
		Path root = directory.resolve("cache");
		IncidentId incident = IncidentId.newId();
		Path journal = root.resolve("authority").resolve(incident.value() + ".json");
		try (DisconnectedStateFile file = new DisconnectedStateFile(root, "authority", incident)) {
			file.save(state("legacy"));
			var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
			var json = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(journal.toFile());
			json.remove("acceptedChanges");
			mapper.writeValue(journal.toFile(), json);
			assertEquals(AcceptedChanges.empty(), file.load().acceptedChanges());
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

	@Test
	void terminatedReplacementRecoversOnlyCompleteCommittedStates() throws Exception {
		assumeTrue(Files.getFileStore(directory).supportsFileAttributeView("posix"));
		for (DisconnectedStateFile.CommitPoint point : DisconnectedStateFile.CommitPoint.values()) {
			Path root = directory.resolve(point.name());
			IncidentId incident = IncidentId.newId();
			try (DisconnectedStateFile initial = new DisconnectedStateFile(root, "authority", incident)) {
				initial.save(queuedState("old"));
			}
			String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
			Process child = new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"),
					CrashWriter.class.getName(), root.toString(), incident.value(), point.name())
					.redirectErrorStream(true).start();
			try {
				BufferedReader output = new BufferedReader(
						new InputStreamReader(child.getInputStream(), StandardCharsets.UTF_8));
				CompletableFuture<String> signal = CompletableFuture.supplyAsync(() -> {
					try {
						return output.readLine();
					} catch (java.io.IOException failure) {
						throw new java.io.UncheckedIOException(failure);
					}
				});
				assertEquals("READY", signal.get(30, TimeUnit.SECONDS));
				child.destroyForcibly();
				assertTrue(child.waitFor(30, TimeUnit.SECONDS));
			} finally {
				if (child.isAlive()) {
					child.destroyForcibly();
					child.waitFor(30, TimeUnit.SECONDS);
				}
			}
			try (DisconnectedStateFile recovered = new DisconnectedStateFile(root, "authority", incident)) {
				String expected = point == DisconnectedStateFile.CommitPoint.STAGING_SYNCED ? "old" : "new";
				DisconnectedStateFile.State loaded = recovered.load();
				assertEquals(expected, loaded.base().getIncidentContext().getIncidentName());
				assertEquals(expected, loaded.draft().getIncidentContext().getIncidentName());
				assertEquals(List.of(expected + "-1", expected + "-2"),
						loaded.pending().stream().map(RemoteIncidentStore.PendingOperation::operationId).toList());
				assertTrue(loaded.pending().stream().allMatch(operation ->
						expected.equals(operation.draft().getIncidentContext().getIncidentName())));
				recovered.save(state("after recovery"));
				assertEquals("after recovery", recovered.load().draft().getIncidentContext().getIncidentName());
			}
		}
	}

	/** Child process paused at a journal replacement checkpoint. */
	public static final class CrashWriter {
		/**
		 * Saves a replacement and waits to be terminated at the requested checkpoint.
		 *
		 * @param args root, incident identifier and checkpoint name
		 * @throws Exception if the writer cannot reach the checkpoint
		 */
		public static void main(String[] args) throws Exception {
			DisconnectedStateFile.CommitPoint checkpoint = DisconnectedStateFile.CommitPoint.valueOf(args[2]);
			try (DisconnectedStateFile file = new DisconnectedStateFile(Path.of(args[0]), "authority",
					new IncidentId(args[1]), point -> {
						if (point == checkpoint) {
							System.out.println("READY");
							System.out.flush();
							System.in.read();
						}
					})) {
				file.save(queuedState("new"));
			}
		}
	}

	/**
	 * Creates a state with multiple ordered operations for crash recovery.
	 *
	 * @param name marker used throughout the state
	 * @return complete state with queued operations
	 */
	private static DisconnectedStateFile.State queuedState(String name) {
		AppData data = new AppData();
		data.getIncidentContext().setIncidentName(name);
		var first = new RemoteIncidentStore.PendingOperation(name + "-1", data, data, null,
				RemoteIncidentStore.PendingStatus.PENDING, List.of(), null);
		var second = new RemoteIncidentStore.PendingOperation(name + "-2", data, data, null,
				RemoteIncidentStore.PendingStatus.PENDING, List.of(), null);
		return new DisconnectedStateFile.State(data, List.of(), 0, data, new RevisionToken("revision"), null, 0,
				AcceptedChanges.empty(),
				List.of(first, second));
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
				AcceptedChanges.empty(),
				List.of());
	}
}
