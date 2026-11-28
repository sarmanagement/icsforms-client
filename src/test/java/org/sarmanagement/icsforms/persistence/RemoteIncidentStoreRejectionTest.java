/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.persistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.host.HostConfig;
import org.sarmanagement.icsforms.host.HostGateway;
import org.sarmanagement.icsforms.host.HostSession;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.ClueLogEntry;
import org.sarmanagement.icsforms.model.PocAssignment;
import org.sarmanagement.icsforms.model.RecordDiff;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Covers durable replay validation and recovery of rejected operations. */
class RemoteIncidentStoreRejectionTest {
	@TempDir
	Path directory;
	private ControllableIncidentStore backing;
	private HostSession session;
	private IncidentId incident;

	@BeforeEach
	void start() {
		backing = new ControllableIncidentStore(directory.resolve("host"));
		incident = backing.create(new AppData());
		session = new HostSession(backing, incident,
				HostConfig.defaults("127.0.0.1", 0, 0).withBindAddress("127.0.0.1")
						.withStopTiming(Duration.ZERO, Duration.ofSeconds(2)), null);
		session.start();
	}

	@AfterEach
	void stop() {
		session.stop();
		backing.close();
	}

	/**
	 * Pairs and detaches a transport for the shared host.
	 *
	 * @return authenticated transport
	 */
	private RemoteIncidentGateway pair() {
		RemoteIncidentStore paired = HostGateway.pair(session.issueInvitation(), "rejection-test");
		RemoteIncidentGateway gateway = paired.detachGateway();
		paired.close();
		return gateway;
	}

	@Test
	void oversizedCountIsDurablyRejectedWithAllDependentEditsExportable() throws Exception {
		RemoteIncidentGateway gateway = pair();
		String fingerprint = gateway.authorityFingerprint().orElseThrow();
		Path cache = directory.resolve("cache");
		String id;
		try (RemoteIncidentStore local = new RemoteIncidentStore(gateway, incident, cache)) {
			StoredIncident snapshot = local.loadSnapshot(incident);
			for (int index = 0; index < 101; index++) {
				ClueLogEntry entry = new ClueLogEntry();
				entry.setDescription("clue " + index);
				snapshot.data().getClueLogEntries().add(entry);
			}
			RevisionToken revision = local.save(incident, snapshot.data(), snapshot.revision());
			snapshot.data().getIncidentContext().setIncidentName("later edit");
			local.save(incident, snapshot.data(), revision);
			local.replayPending();
			var head = local.pendingOperations().get(0);
			assertEquals(RemoteIncidentStore.PendingStatus.REJECTED, head.status());
			assertNull(head.batch());
			assertTrue(head.rejection().contains("100"));
			assertEquals(2, local.pendingOperations().size());
			assertThrows(StoreValidationException.class, () -> local.discardPending(head.operationId()));
			local.replayPending();
			assertEquals(RemoteIncidentStore.PendingStatus.REJECTED, local.pendingOperations().get(0).status());
			local.retryRejected(head.operationId());
			local.replayPending();
			assertEquals(RemoteIncidentStore.PendingStatus.REJECTED, local.pendingOperations().get(0).status());
			Path exported = directory.resolve("outbox.json");
			local.exportOutbox(exported);
			String text = Files.readString(exported);
			assertTrue(text.contains("clue 100"));
			assertTrue(text.contains("later edit"));
			assertTrue(backing.loadSnapshot(incident).data().getClueLogEntries().isEmpty());
			id = head.operationId();
		}
		try (RemoteIncidentStore restored = RemoteIncidentStore.openOffline(cache, fingerprint, incident)) {
			assertEquals(id, restored.pendingOperations().get(0).operationId());
			assertEquals(RemoteIncidentStore.PendingStatus.REJECTED, restored.pendingOperations().get(0).status());
			assertEquals(101, restored.loadSnapshot(incident).data().getClueLogEntries().size());
			assertEquals("later edit", restored.loadSnapshot(incident).data().getIncidentContext().getIncidentName());
		}
	}

	@Test
	void byteLimitCountsUtf8InsteadOfCharacters() {
		try (RemoteIncidentStore local = new RemoteIncidentStore(pair(), incident, directory.resolve("cache"))) {
			StoredIncident snapshot = local.loadSnapshot(incident);
			String original = snapshot.data().getIncidentContext().getIncidentName();
			snapshot.data().getIncidentContext().setIncidentName("é".repeat(460_000));
			local.save(incident, snapshot.data(), snapshot.revision());
			local.replayPending();
			var rejected = local.pendingOperations().get(0);
			assertEquals(RemoteIncidentStore.PendingStatus.REJECTED, rejected.status());
			assertNull(rejected.batch());
			assertTrue(rejected.rejection().contains("larger"));
			assertEquals(original, backing.loadSnapshot(incident).data().getIncidentContext().getIncidentName());
		}
	}

	@Test
	void rejectionAfterAmbiguousAcceptanceCannotBeDiscardedAndRetriesExactBatch() {
		AmbiguousGateway gateway = new AmbiguousGateway(pair());
		try (RemoteIncidentStore local = new RemoteIncidentStore(gateway, incident, directory.resolve("cache"))) {
			StoredIncident snapshot = local.loadSnapshot(incident);
			snapshot.data().getIncidentContext().setIncidentName("accepted once");
			local.save(incident, snapshot.data(), snapshot.revision());
			assertThrows(StoreUnavailableException.class, local::replayPending);
			var exact = local.pendingOperations().get(0).batch();
			assertNotNull(exact);
			local.replayPending();
			var rejected = local.pendingOperations().get(0);
			assertEquals(RemoteIncidentStore.PendingStatus.REJECTED, rejected.status());
			assertEquals(exact, rejected.batch());
			assertThrows(StoreValidationException.class, () -> local.discardPending(rejected.operationId()));
			local.retryRejected(rejected.operationId());
			local.replayPending();
			assertTrue(local.pendingOperations().isEmpty());
			assertEquals(1, local.acceptedReceiptCount());
			assertEquals("accepted once", backing.loadSnapshot(incident).data().getIncidentContext().getIncidentName());
		}
	}

	@Test
	void unsecurableCacheVolumeDegradesToConnectedOnlyWithANotice() {
		PrivateFiles.setStoreResolver(path -> new PrivateFilesTest.FakeStore(false, false));
		try (RemoteIncidentStore store = RemoteIncidentStore.connect(pair(), incident, List.of(directory.resolve("fat")))) {
			assertFalse(store.disconnectedStorageEnabled());
			assertTrue(store.disconnectedStorageNotice().orElseThrow().contains("exFAT"));
			StoredIncident snapshot = store.loadSnapshot(incident);
			snapshot.data().getIncidentContext().setIncidentName("online only");
			store.save(incident, snapshot.data(), snapshot.revision());
			assertEquals("online only", backing.loadSnapshot(incident).data().getIncidentContext().getIncidentName());
		} finally {
			PrivateFiles.setStoreResolver(null);
		}
	}

	@Test
	void unsecurableCachePrimaryFailsOverToASecureLocationAndIsRediscovered() throws Exception {
		Path fat = directory.resolve("fat");
		Path secure = directory.resolve("secure");
		PrivateFiles.setStoreResolver(path -> path.startsWith(fat) ? new PrivateFilesTest.FakeStore(false, false)
				: realStore(path));
		try {
			RemoteIncidentGateway gateway = pair();
			String fingerprint = gateway.authorityFingerprint().orElseThrow();
			try (RemoteIncidentStore store = RemoteIncidentStore.connect(gateway, incident, List.of(fat, secure))) {
				assertTrue(store.disconnectedStorageEnabled());
				assertTrue(store.disconnectedStorageNotice().orElseThrow().contains("protected fallback"));
				StoredIncident snapshot = store.loadSnapshot(incident);
				snapshot.data().getIncidentContext().setIncidentName("kept offline");
				store.save(incident, snapshot.data(), snapshot.revision());
			}
			assertEquals(List.of(new RemoteIncidentStore.CachedIncident(fingerprint, incident)),
					RemoteIncidentStore.discoverCached(List.of(fat, secure)));
			try (RemoteIncidentStore restored = RemoteIncidentStore.openOffline(List.of(fat, secure), fingerprint,
					incident)) {
				assertEquals(1, restored.pendingOperations().size());
			}
			try (RemoteIncidentStore reconnected = RemoteIncidentStore.connect(pair(), incident, List.of(fat, secure))) {
				assertEquals(1, reconnected.pendingOperations().size());
				assertTrue(reconnected.disconnectedStorageNotice().orElseThrow().contains("protected fallback"));
				assertFalse(Files.exists(fat.resolve(fingerprint).resolve(incident.value() + ".json")));
			}
		} finally {
			PrivateFiles.setStoreResolver(null);
		}
	}

	/**
	 * Resolves the real file store of a path.
	 *
	 * @param path existing path
	 * @return its store
	 */
	private static java.nio.file.FileStore realStore(Path path) {
		try {
			return Files.getFileStore(path);
		} catch (java.io.IOException failure) {
			throw new java.io.UncheckedIOException(failure);
		}
	}

	@Test
	void rejectedHeadIsReplacedByCorrectedDraftAfterArchivingTheOriginal() throws Exception {
		AmbiguousGateway gateway = new AmbiguousGateway(pair(), true);
		Path cache = directory.resolve("cache");
		try (RemoteIncidentStore local = new RemoteIncidentStore(gateway, incident, cache)) {
			StoredIncident snapshot = local.loadSnapshot(incident);
			snapshot.data().getIncidentContext().setIncidentName("wrong name");
			RevisionToken revision = local.save(incident, snapshot.data(), snapshot.revision());
			local.replayPending();
			var rejected = local.pendingOperations().get(0);
			assertEquals(RemoteIncidentStore.PendingStatus.REJECTED, rejected.status());
			assertThrows(StoreValidationException.class, () -> local.replaceRejected(rejected.operationId()));
			snapshot.data().getIncidentContext().setIncidentName("corrected name");
			local.save(incident, snapshot.data(), revision);
			assertEquals(2, local.pendingOperations().size());
			assertThrows(StoreValidationException.class, () -> local.replaceRejected("unknown"));
			String replacement = local.replaceRejected(rejected.operationId());
			assertEquals(1, local.pendingOperations().size());
			assertEquals(replacement, local.pendingOperations().get(0).operationId());
			assertEquals(RemoteIncidentStore.PendingStatus.PENDING, local.pendingOperations().get(0).status());
			Path archive;
			try (var files = Files.walk(cache)) {
				archive = files.filter(path -> path.getFileName().toString().contains(".rejected-")).findFirst()
						.orElseThrow();
			}
			String archived = Files.readString(archive);
			assertTrue(archived.contains(rejected.operationId()));
			assertTrue(archived.contains("wrong name"));
			local.replayPending();
			assertTrue(local.pendingOperations().isEmpty());
			assertEquals("corrected name",
					backing.loadSnapshot(incident).data().getIncidentContext().getIncidentName());
		}
	}

	@Test
	void splitsAndArchivesOversizedHeadBeforeReplayingDependentEditsAfterRestart() throws Exception {
		RemoteIncidentGateway gateway = pair();
		String fingerprint = gateway.authorityFingerprint().orElseThrow();
		Path cache = directory.resolve("cache");
		List<String> ids;
		try (RemoteIncidentStore local = new RemoteIncidentStore(gateway, incident, cache)) {
			StoredIncident snapshot = local.loadSnapshot(incident);
			for (int index = 0; index < 101; index++) {
				ClueLogEntry clue = new ClueLogEntry();
				clue.setDescription("clue " + index);
				snapshot.data().getClueLogEntries().add(clue);
			}
			RevisionToken revision = local.save(incident, snapshot.data(), snapshot.revision());
			snapshot.data().getIncidentContext().setIncidentName("dependent");
			local.save(incident, snapshot.data(), revision);
			local.replayPending();
			String rejected = local.pendingOperations().get(0).operationId();
			Path exported = directory.resolve("original.json");
			local.exportOutbox(exported);
			ids = local.splitRejected(rejected);
			assertEquals(2, ids.size());
			assertEquals(3, local.pendingOperations().size());
			try (var files = Files.walk(cache)) {
				Path archive = files.filter(path -> path.getFileName().toString().contains(".rejected-"))
						.findFirst().orElseThrow();
				assertEquals(Files.readString(exported), Files.readString(archive));
			}
		}
		try (RemoteIncidentStore restored = RemoteIncidentStore.openOffline(cache, fingerprint, incident)) {
			assertEquals(ids.get(0), restored.pendingOperations().get(0).operationId());
			assertEquals(101, restored.loadSnapshot(incident).data().getClueLogEntries().size());
			restored.attachGateway(pair());
			restored.replayPending();
			assertTrue(restored.pendingOperations().isEmpty());
			assertEquals(101, backing.loadSnapshot(incident).data().getClueLogEntries().size());
			assertEquals("dependent", backing.loadSnapshot(incident).data().getIncidentContext().getIncidentName());
		}
	}

	@Test
	void discardArchivesExactlyAndArchiveFailureKeepsQueueIntact() throws Exception {
		Path cache = directory.resolve("cache");
		try (RemoteIncidentStore local = new RemoteIncidentStore(pair(), incident, cache)) {
			StoredIncident snapshot = local.loadSnapshot(incident);
			snapshot.data().getIncidentContext().setIncidentName("preserve this edit");
			local.save(incident, snapshot.data(), snapshot.revision());
			String id = local.pendingOperations().get(0).operationId();
			Path exported = directory.resolve("discard.json");
			local.exportOutbox(exported);
			PrivateFiles.setStoreResolver(path -> new PrivateFilesTest.FakeStore(false, false));
			try {
				assertThrows(StorePersistenceException.class, () -> local.discardPending(id));
				assertEquals(id, local.pendingOperations().get(0).operationId());
				assertEquals("preserve this edit", local.loadSnapshot(incident).data().getIncidentContext()
						.getIncidentName());
			} finally {
				PrivateFiles.setStoreResolver(null);
			}
			local.discardPending(id);
			assertTrue(local.pendingOperations().isEmpty());
			try (var files = Files.walk(cache)) {
				List<Path> archives = files.filter(path -> path.getFileName().toString().contains(".rejected-"))
						.toList();
				assertEquals(2, archives.size());
				assertTrue(archives.stream().anyMatch(path -> {
					try {
						return Files.readString(path).equals(Files.readString(exported));
					} catch (java.io.IOException failure) {
						throw new java.io.UncheckedIOException(failure);
					}
				}));
			}
		}
	}

	@Test
	void definitiveMaterializedRejectionCanBeArchivedAndDiscarded() throws Exception {
		Path cache = directory.resolve("cache");
		try (RemoteIncidentStore local = new RemoteIncidentStore(new AmbiguousGateway(pair(), true), incident, cache)) {
			StoredIncident snapshot = local.loadSnapshot(incident);
			snapshot.data().getIncidentContext().setIncidentName("rejected value");
			local.save(incident, snapshot.data(), snapshot.revision());
			local.replayPending();
			var rejected = local.pendingOperations().get(0);
			assertNotNull(rejected.batch());
			assertTrue(rejected.definitivelyRejected());
			Path exported = directory.resolve("materialized.json");
			local.exportOutbox(exported);
			local.discardPending(rejected.operationId());
			assertTrue(local.pendingOperations().isEmpty());
			try (var files = Files.walk(cache)) {
				Path archive = files.filter(path -> path.getFileName().toString().contains(".rejected-"))
						.findFirst().orElseThrow();
				assertEquals(Files.readString(exported), Files.readString(archive));
			}
		}
	}

	@Test
	void definitiveMaterializedRejectionCanBeArchivedAndSplit() throws Exception {
		Path cache = directory.resolve("cache");
		try (RemoteIncidentStore local = new RemoteIncidentStore(new AmbiguousGateway(pair(), true), incident, cache)) {
			StoredIncident snapshot = local.loadSnapshot(incident);
			snapshot.data().getClueLogEntries().add(new ClueLogEntry());
			snapshot.data().getClueLogEntries().add(new ClueLogEntry());
			local.save(incident, snapshot.data(), snapshot.revision());
			local.replayPending();
			var rejected = local.pendingOperations().get(0);
			assertNotNull(rejected.batch());
			assertTrue(rejected.definitivelyRejected());
			Path exported = directory.resolve("materialized-split.json");
			local.exportOutbox(exported);
			assertEquals(2, local.splitRejected(rejected.operationId()).size());
			try (var files = Files.walk(cache)) {
				Path archive = files.filter(path -> path.getFileName().toString().contains(".rejected-"))
						.findFirst().orElseThrow();
				assertEquals(Files.readString(exported), Files.readString(archive));
			}
			local.replayPending();
			assertTrue(local.pendingOperations().isEmpty());
			assertEquals(2, backing.loadSnapshot(incident).data().getClueLogEntries().size());
		}
	}

	@Test
	void choosingHostConflictValueArchivesQueueAndFailsClosedOnArchiveFailure() throws Exception {
		Path cache = directory.resolve("cache");
		try (RemoteIncidentStore local = new RemoteIncidentStore(pair(), incident, cache);
				RemoteIncidentStore other = new RemoteIncidentStore(pair(), incident)) {
			StoredIncident mine = local.loadSnapshot(incident);
			StoredIncident theirs = other.loadSnapshot(incident);
			mine.data().getIncidentContext().setIncidentName("local value");
			local.save(incident, mine.data(), mine.revision());
			theirs.data().getIncidentContext().setIncidentName("host value");
			other.save(incident, theirs.data(), theirs.revision());
			local.replayPending();
			var conflict = local.pendingOperations().get(0);
			var field = conflict.conflicts().get(0);
			var choices = Map.of(new RemoteIncidentStore.ConflictField(field.key(), field.field()),
					RemoteIncidentStore.ConflictChoice.THEIRS);
			Path exported = directory.resolve("conflict.json");
			local.exportOutbox(exported);
			Path absoluteCache = cache.toAbsolutePath();
			PrivateFiles.setStoreResolver(path -> path.startsWith(absoluteCache)
					? new PrivateFilesTest.FakeStore(false, false) : realStore(path));
			try {
				assertThrows(StorePersistenceException.class, () -> local.resolvePending(conflict.operationId(), choices));
				assertEquals(conflict.operationId(), local.pendingOperations().get(0).operationId());
				assertEquals("local value", local.loadSnapshot(incident).data().getIncidentContext().getIncidentName());
			} finally {
				PrivateFiles.setStoreResolver(null);
			}
			local.resolvePending(conflict.operationId(), choices);
			assertEquals("host value", local.loadSnapshot(incident).data().getIncidentContext().getIncidentName());
			try (var files = Files.walk(cache)) {
				assertTrue(files.filter(path -> path.getFileName().toString().contains(".rejected-")).anyMatch(path -> {
					try {
						return Files.readString(path).equals(Files.readString(exported));
					} catch (java.io.IOException failure) {
						throw new java.io.UncheckedIOException(failure);
					}
				}));
			}
		}
	}

	@Test
	void conflictRefreshArchivesOriginalBeforeAutomaticRebase() throws Exception {
		Path cache = directory.resolve("cache");
		try (RemoteIncidentStore local = new RemoteIncidentStore(pair(), incident, cache);
				RemoteIncidentStore other = new RemoteIncidentStore(pair(), incident)) {
			StoredIncident mine = local.loadSnapshot(incident);
			StoredIncident theirs = other.loadSnapshot(incident);
			mine.data().getIncidentContext().setIncidentName("local value");
			local.save(incident, mine.data(), mine.revision());
			theirs.data().getIncidentContext().setIncidentName("host value");
			RevisionToken revision = other.save(incident, theirs.data(), theirs.revision());
			local.replayPending();
			var conflict = local.pendingOperations().get(0);
			Path exported = directory.resolve("refresh.json");
			local.exportOutbox(exported);
			theirs.data().getIncidentContext().setIncidentName("local value");
			other.save(incident, theirs.data(), revision);
			assertEquals(RemoteIncidentStore.PendingStatus.REVIEW_REQUIRED,
					local.refreshConflict(conflict.operationId()).status());
			try (var files = Files.walk(cache)) {
				assertTrue(files.filter(path -> path.getFileName().toString().contains(".rejected-")).anyMatch(path -> {
					try {
						return Files.readString(path).equals(Files.readString(exported));
					} catch (java.io.IOException failure) {
						throw new java.io.UncheckedIOException(failure);
					}
				}));
			}
		}
	}

	@Test
	void existingArchiveCannotBeTruncatedByRepeatedLabel() throws Exception {
		try (DisconnectedStateFile file = new DisconnectedStateFile(directory.resolve("other"), "authority",
				incident)) {
			file.archive("same", List.of());
			Path archive = directory.resolve("other/authority/" + incident.value() + ".json.rejected-same.json");
			String original = Files.readString(archive);
			assertThrows(java.io.IOException.class, () -> file.archive("same", List.of()));
			assertEquals(original, Files.readString(archive));
		}
	}

	@Test
	void splitsUtf8ByteLimitedBatchWithoutDroppingRecords() {
		try (RemoteIncidentStore local = new RemoteIncidentStore(pair(), incident, directory.resolve("cache"))) {
			StoredIncident snapshot = local.loadSnapshot(incident);
			for (int index = 0; index < 2; index++) {
				ClueLogEntry clue = new ClueLogEntry();
				clue.setDescription(index + "é".repeat(240_000));
				snapshot.data().getClueLogEntries().add(clue);
			}

			local.save(incident, snapshot.data(), snapshot.revision());
			local.replayPending();
			assertEquals(RemoteIncidentStore.PendingStatus.REJECTED, local.pendingOperations().get(0).status());
			assertEquals(2, local.splitRejected(local.pendingOperations().get(0).operationId()).size());
			local.replayPending();
			assertTrue(local.pendingOperations().isEmpty());
			assertEquals(2, backing.loadSnapshot(incident).data().getClueLogEntries().size());
		}
	}

	@Test
	void individualOversizedRecordCannotBeSplitOrLost() {
		try (RemoteIncidentStore local = new RemoteIncidentStore(pair(), incident, directory.resolve("cache"))) {
			StoredIncident snapshot = local.loadSnapshot(incident);
			String large = "é".repeat(460_000);
			snapshot.data().getIncidentContext().setIncidentName(large);
			local.save(incident, snapshot.data(), snapshot.revision());
			local.replayPending();
			String id = local.pendingOperations().get(0).operationId();
			assertThrows(StoreValidationException.class, () -> local.splitRejected(id));
			assertEquals(id, local.pendingOperations().get(0).operationId());
			assertEquals(large, local.loadSnapshot(incident).data().getIncidentContext().getIncidentName());
		}
	}

	@Test
	void correctedRejectedEditCanRemoveARecordAndReplayLaterEdits() {
		StoredIncident host = backing.loadSnapshot(incident);
		ClueLogEntry clue = new ClueLogEntry();
		clue.setDescription("to remove");
		host.data().getClueLogEntries().add(clue);
		backing.save(incident, host.data(), host.revision());
		try (RemoteIncidentStore local = new RemoteIncidentStore(pair(), incident, directory.resolve("cache"))) {
			StoredIncident snapshot = local.loadSnapshot(incident);
			snapshot.data().getIncidentContext().setIncidentName("é".repeat(460_000));
			RevisionToken revision = local.save(incident, snapshot.data(), snapshot.revision());
			local.replayPending();
			String rejected = local.pendingOperations().get(0).operationId();
			snapshot.data().getIncidentContext().setIncidentName("corrected");
			snapshot.data().getClueLogEntries().clear();
			revision = local.save(incident, snapshot.data(), revision);
			local.replaceRejected(rejected);
			snapshot.data().getIncidentContext().setIncidentName("later");
			local.save(incident, snapshot.data(), revision);
			local.replayPending();
			assertTrue(local.pendingOperations().isEmpty());
			assertTrue(backing.loadSnapshot(incident).data().getClueLogEntries().isEmpty());
			assertEquals("later", backing.loadSnapshot(incident).data().getIncidentContext().getIncidentName());
		}
	}

	@Test
	void firstAttemptRejectionRemainsReplaceableAfterRestart() {
		RemoteIncidentGateway transport = pair();
		String fingerprint = transport.authorityFingerprint().orElseThrow();
		Path cache = directory.resolve("cache");
		String rejected;
		try (RemoteIncidentStore local = new RemoteIncidentStore(new AmbiguousGateway(transport, true), incident,
				cache)) {
			StoredIncident snapshot = local.loadSnapshot(incident);
			snapshot.data().getIncidentContext().setIncidentName("first");
			local.save(incident, snapshot.data(), snapshot.revision());
			local.replayPending();
			assertTrue(local.pendingOperations().get(0).definitivelyRejected());
			rejected = local.pendingOperations().get(0).operationId();
		}

		try (RemoteIncidentStore restored = RemoteIncidentStore.openOffline(cache, fingerprint, incident)) {
			assertTrue(restored.pendingOperations().get(0).definitivelyRejected());
			StoredIncident snapshot = restored.loadSnapshot(incident);
			snapshot.data().getIncidentContext().setIncidentName("corrected");
			restored.save(incident, snapshot.data(), snapshot.revision());
			restored.replaceRejected(rejected);
			restored.attachGateway(pair());
			restored.replayPending();
			assertEquals("corrected", backing.loadSnapshot(incident).data().getIncidentContext().getIncidentName());
		}
	}

	@Test
	void ambiguousRejectionCannotBeReplacedOrSplitAfterRestart() {
		RemoteIncidentGateway transport = pair();
		String fingerprint = transport.authorityFingerprint().orElseThrow();
		Path cache = directory.resolve("cache");
		String rejected;
		try (RemoteIncidentStore local = new RemoteIncidentStore(new AmbiguousGateway(transport), incident, cache)) {
			StoredIncident snapshot = local.loadSnapshot(incident);
			snapshot.data().getIncidentContext().setIncidentName("accepted once");
			local.save(incident, snapshot.data(), snapshot.revision());
			assertThrows(StoreUnavailableException.class, local::replayPending);
			local.replayPending();
			assertFalse(local.pendingOperations().get(0).definitivelyRejected());
			rejected = local.pendingOperations().get(0).operationId();
		}
		try (RemoteIncidentStore restored = RemoteIncidentStore.openOffline(cache, fingerprint, incident)) {
			StoredIncident snapshot = restored.loadSnapshot(incident);
			snapshot.data().getIncidentContext().setIncidentName("later correction");
			restored.save(incident, snapshot.data(), snapshot.revision());
			assertThrows(StoreValidationException.class, () -> restored.replaceRejected(rejected));
			assertThrows(StoreValidationException.class, () -> restored.splitRejected(rejected));
			assertEquals(rejected, restored.pendingOperations().get(0).operationId());
		}
	}

	@Test
	void crossReferencedChangedRecordsRemainInTheSameSplitBatch() {
		try (RemoteIncidentStore local = new RemoteIncidentStore(pair(), incident, directory.resolve("cache"))) {
			StoredIncident snapshot = local.loadSnapshot(incident);
			ClueLogEntry first = new ClueLogEntry();
			ClueLogEntry second = new ClueLogEntry();
			first.confirmDuplicate(second.getId(), Instant.EPOCH, null);
			snapshot.data().getClueLogEntries().add(first);
			snapshot.data().getClueLogEntries().add(second);
			for (int index = 0; index < 100; index++) {
				snapshot.data().getClueLogEntries().add(new ClueLogEntry());
			}
			local.save(incident, snapshot.data(), snapshot.revision());
			local.replayPending();
			local.splitRejected(local.pendingOperations().get(0).operationId());
			assertTrue(local.pendingOperations().stream().anyMatch(operation -> {
				var keys = RecordDiff.between(operation.before(), operation.draft()).created();
				return keys.stream().anyMatch(key -> key.id().equals(first.getId()))
						&& keys.stream().anyMatch(key -> key.id().equals(second.getId()));
			}));
			local.replayPending();
			assertTrue(local.pendingOperations().isEmpty());
			assertEquals(102, backing.loadSnapshot(incident).data().getClueLogEntries().size());
		}
	}

	@Test
	void oversizedDependentGroupIsLeftIntactWhenItCannotBeSplitSafely() {
		try (RemoteIncidentStore local = new RemoteIncidentStore(pair(), incident, directory.resolve("cache"))) {
			StoredIncident snapshot = local.loadSnapshot(incident);
			ClueLogEntry clue = new ClueLogEntry();
			for (int index = 0; index < 101; index++) {
				clue.getFollowUpAssignments().add(new PocAssignment());
			}
			snapshot.data().getClueLogEntries().add(clue);
			local.save(incident, snapshot.data(), snapshot.revision());
			local.replayPending();
			String rejected = local.pendingOperations().get(0).operationId();
			assertThrows(StoreValidationException.class, () -> local.splitRejected(rejected));
			assertEquals(rejected, local.pendingOperations().get(0).operationId());
			assertEquals(101, local.loadSnapshot(incident).data().getClueLogEntries().get(0)
					.getFollowUpAssignments().size());
		}
	}

	/** Simulates a lost acceptance, followed by a validation error, then normal receipt replay. */
	private static final class AmbiguousGateway implements RemoteIncidentGateway {
		private final RemoteIncidentGateway delegate;
		private final boolean rejectFirst;
		private int submissions;

		/**
		 * Wraps an authenticated transport.
		 *
		 * @param delegate actual host transport
		 */
		AmbiguousGateway(RemoteIncidentGateway delegate) {
			this(delegate, false);
		}

		/**
		 * Wraps an authenticated transport.
		 *
		 * @param delegate actual host transport
		 * @param rejectFirst when true, the first submission is rejected without reaching the host
		 */
		AmbiguousGateway(RemoteIncidentGateway delegate, boolean rejectFirst) {
			this.delegate = delegate;
			this.rejectFirst = rejectFirst;
		}

		/** {@inheritDoc} */
		@Override
		public Optional<String> authorityFingerprint() {
			return delegate.authorityFingerprint();
		}

		/** {@inheritDoc} */
		@Override
		public String location() {
			return delegate.location();
		}

		/** {@inheritDoc} */
		@Override
		public IncidentService.ReadResult read(IncidentId id) {
			return delegate.read(id);
		}

		/** {@inheritDoc} */
		@Override
		public IncidentService.ChangeResult changesSince(IncidentId id, long cursor) {
			return delegate.changesSince(id, cursor);
		}

		/** {@inheritDoc} */
		@Override
		public IncidentService.SubmissionResult submit(IncidentService.RecordBatch batch) {
			submissions++;
			if (rejectFirst) {
				if (submissions == 1) {
					throw new StoreValidationException("Host policy rejected the edit");
				}
				return delegate.submit(batch);
			}
			if (submissions == 1) {
				delegate.submit(batch);
				throw new StoreUnavailableException("Lost response");
			}
			if (submissions == 2) {
				throw new StoreValidationException("Rejected retry");
			}
			return delegate.submit(batch);
		}

		/** {@inheritDoc} */
		@Override
		public HostProbe probe() {
			return delegate.probe();
		}

		/** {@inheritDoc} */
		@Override
		public void close() {
			delegate.close();
		}
	}
}
