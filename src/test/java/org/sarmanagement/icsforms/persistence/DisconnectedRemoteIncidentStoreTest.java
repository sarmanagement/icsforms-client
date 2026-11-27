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

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises durable offline edits against the real host record service. */
class DisconnectedRemoteIncidentStoreTest {
	@TempDir
	Path directory;
	private ControllableIncidentStore backing;
	private HostSession session;
	private IncidentId incident;
	private Path cache;

	@BeforeEach
	void start() {
		cache = directory.resolve("cache");
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
	 * Pairs a gateway and transfers its ownership.
	 *
	 * @return authenticated gateway
	 */
	private RemoteIncidentGateway pair() {
		RemoteIncidentStore paired = HostGateway.pair(session.issueInvitation(), "offline-test");
		RemoteIncidentGateway gateway = paired.detachGateway();
		paired.close();
		return gateway;
	}

	@Test
	void offlineRestartAndDependentEditsReplayWithoutLosingMergedHostFields() {
		RemoteIncidentGateway firstGateway = pair();
		String fingerprint = firstGateway.authorityFingerprint().orElseThrow();
		try (RemoteIncidentStore local = new RemoteIncidentStore(firstGateway, incident, cache);
				RemoteIncidentStore other = new RemoteIncidentStore(pair(), incident)) {
			StoredIncident original = local.loadSnapshot(incident);
			StoredIncident remote = other.loadSnapshot(incident);
			remote.data().getIncidentContext().setTaskMap("remote map");
			other.save(incident, remote.data(), remote.revision());
			original.data().getIncidentContext().setIncidentName("first");
			RevisionToken revision = local.save(incident, original.data(), original.revision());
			original.data().getIncidentContext().setIncidentName("second");
			local.save(incident, original.data(), revision);
			assertEquals(2, local.pendingOperations().size());
		}
		assertEquals(1, RemoteIncidentStore.discoverCached(cache).size());
		try (RemoteIncidentStore restored = RemoteIncidentStore.openOffline(cache, fingerprint, incident)) {
			assertEquals("second", restored.loadSnapshot(incident).data().getIncidentContext().getIncidentName());
			assertNotNull(restored.lastConfirmedAt().orElse(null));
			restored.attachGateway(pair());
			restored.replayPending();
			assertTrue(restored.pendingOperations().isEmpty());
			assertEquals(2, restored.acceptedReceiptCount());
		}
		AppData result = backing.loadSnapshot(incident).data();
		assertEquals("second", result.getIncidentContext().getIncidentName());
		assertEquals("remote map", result.getIncidentContext().getTaskMap());
	}

	@Test
	void lostResponseRetainsExactBatchAcrossRestartAndIsIdempotent() {
		LosingGateway gateway = new LosingGateway(pair());
		String fingerprint = gateway.authorityFingerprint().orElseThrow();
		String operation;
		try (RemoteIncidentStore local = new RemoteIncidentStore(gateway, incident, cache)) {
			StoredIncident snapshot = local.loadSnapshot(incident);
			snapshot.data().getIncidentContext().setIncidentName("once");
			local.save(incident, snapshot.data(), snapshot.revision());
			gateway.lose = true;
			assertThrows(StoreUnavailableException.class, local::replayPending);
			operation = local.pendingOperations().get(0).batch().operationId();
		}
		try (RemoteIncidentStore local = RemoteIncidentStore.openOffline(cache, fingerprint, incident)) {
			assertEquals(operation, local.pendingOperations().get(0).batch().operationId());
			local.attachGateway(pair());
			local.replayPending();
			assertTrue(local.pendingOperations().isEmpty());
			assertEquals(1, local.acceptedReceiptCount());
		}
		assertEquals("once", backing.loadSnapshot(incident).data().getIncidentContext().getIncidentName());
	}

	@Test
	void lostHistoryReadAfterAcceptedResponseRetainsReceiptForReplay() {
		LosingGateway gateway = new LosingGateway(pair());
		String fingerprint = gateway.authorityFingerprint().orElseThrow();
		String operation;
		try (RemoteIncidentStore local = new RemoteIncidentStore(gateway, incident, cache)) {
			StoredIncident snapshot = local.loadSnapshot(incident);
			snapshot.data().getIncidentContext().setIncidentName("accepted before history");
			local.save(incident, snapshot.data(), snapshot.revision());
			gateway.loseHistory = true;
			assertThrows(StoreUnavailableException.class, local::replayPending);
			operation = local.pendingOperations().get(0).batch().operationId();
		}
		try (RemoteIncidentStore local = RemoteIncidentStore.openOffline(cache, fingerprint, incident)) {
			assertEquals(operation, local.pendingOperations().get(0).batch().operationId());
			local.attachGateway(pair());
			local.replayPending();
			assertEquals(1, local.acceptedReceiptCount());
			assertTrue(local.pendingOperations().isEmpty());
		}
	}

	@Test
	void unseenMergedFieldInPredecessorRequiresReviewBeforeDependentEdit() {
		try (RemoteIncidentStore local = new RemoteIncidentStore(pair(), incident, cache);
				RemoteIncidentStore other = new RemoteIncidentStore(pair(), incident)) {
			StoredIncident draft = local.loadSnapshot(incident);
			StoredIncident host = other.loadSnapshot(incident);
			draft.data().getIncidentContext().setIncidentName("first");
			RevisionToken revision = local.save(incident, draft.data(), draft.revision());
			draft.data().getIncidentContext().setTaskMap("local later");
			local.save(incident, draft.data(), revision);
			host.data().getIncidentContext().setTaskMap("unseen remote");
			other.save(incident, host.data(), host.revision());
			local.replayPending();
			assertEquals(1, local.acceptedReceiptCount());
			RemoteIncidentStore.PendingOperation conflict = local.pendingOperations().get(0);
			assertEquals(RemoteIncidentStore.PendingStatus.CONFLICT, conflict.status());
			assertEquals("unseen remote", backing.loadSnapshot(incident).data().getIncidentContext().getTaskMap());
			IncidentService.FieldConflict field = conflict.conflicts().get(0);
			local.resolvePending(conflict.operationId(), Map.of(
					new RemoteIncidentStore.ConflictField(field.key(), field.field()),
					RemoteIncidentStore.ConflictChoice.MINE));
			local.replayPending();
			assertEquals("local later", backing.loadSnapshot(incident).data().getIncidentContext().getTaskMap());
		}
	}

	@Test
	void resolvingHeadDoesNotEraseConflictInLaterQueuedEdit() {
		try (RemoteIncidentStore local = new RemoteIncidentStore(pair(), incident, cache);
				RemoteIncidentStore other = new RemoteIncidentStore(pair(), incident)) {
			StoredIncident draft = local.loadSnapshot(incident);
			StoredIncident host = other.loadSnapshot(incident);
			draft.data().getIncidentContext().setIncidentName("mine");
			RevisionToken revision = local.save(incident, draft.data(), draft.revision());
			draft.data().getIncidentContext().setTaskMap("later mine");
			local.save(incident, draft.data(), revision);
			host.data().getIncidentContext().setIncidentName("theirs");
			host.data().getIncidentContext().setTaskMap("later theirs");
			other.save(incident, host.data(), host.revision());
			local.replayPending();
			RemoteIncidentStore.PendingOperation head = local.pendingOperations().get(0);
			IncidentService.FieldConflict conflict = head.conflicts().get(0);
			local.resolvePending(head.operationId(), Map.of(
					new RemoteIncidentStore.ConflictField(conflict.key(), conflict.field()),
					RemoteIncidentStore.ConflictChoice.THEIRS));
			local.replayPending();
			assertEquals(1, local.pendingOperations().size());
			assertEquals(RemoteIncidentStore.PendingStatus.CONFLICT, local.pendingOperations().get(0).status());
			assertEquals("later theirs", backing.loadSnapshot(incident).data().getIncidentContext().getTaskMap());
			assertEquals("later mine", local.pendingOperations().get(0).draft().getIncidentContext().getTaskMap());
		}
	}

	@Test
	void ambiguousAcceptanceFollowedByAccessFailureCannotBeDiscarded() {
		LosingGateway gateway = new LosingGateway(pair());
		try (RemoteIncidentStore local = new RemoteIncidentStore(gateway, incident, cache)) {
			StoredIncident draft = local.loadSnapshot(incident);
			draft.data().getIncidentContext().setIncidentName("accepted");
			local.save(incident, draft.data(), draft.revision());
			gateway.lose = true;
			assertThrows(StoreUnavailableException.class, local::replayPending);
			gateway.reject = true;
			assertThrows(RemoteAccessException.class, local::replayPending);
			RemoteIncidentStore.PendingOperation operation = local.pendingOperations().get(0);
			assertEquals(RemoteIncidentStore.PendingStatus.PENDING, operation.status());
			assertThrows(StoreValidationException.class, () -> local.discardPending(operation.operationId()));
			assertEquals("accepted", backing.loadSnapshot(incident).data().getIncidentContext().getIncidentName());
		}
	}

	@Test
	void validationRejectionSurvivesRestartAndKeepsExactBatch() {
		LosingGateway gateway = new LosingGateway(pair());
		String fingerprint = gateway.authorityFingerprint().orElseThrow();
		String operation;
		try (RemoteIncidentStore local = new RemoteIncidentStore(gateway, incident, cache)) {
			StoredIncident draft = local.loadSnapshot(incident);
			draft.data().getIncidentContext().setIncidentName("retained");
			local.save(incident, draft.data(), draft.revision());
			gateway.rejectValidation = true;
			local.replayPending();
			RemoteIncidentStore.PendingOperation rejected = local.pendingOperations().get(0);
			assertEquals(RemoteIncidentStore.PendingStatus.REJECTED, rejected.status());
			assertNotNull(rejected.rejection());
			operation = rejected.operationId();
			assertThrows(StoreValidationException.class, () -> local.discardPending(operation));
		}
		try (RemoteIncidentStore local = RemoteIncidentStore.openOffline(cache, fingerprint, incident)) {
			assertEquals(RemoteIncidentStore.PendingStatus.REJECTED, local.pendingOperations().get(0).status());
			assertEquals(operation, local.pendingOperations().get(0).batch().operationId());
			assertEquals("retained", local.loadSnapshot(incident).data().getIncidentContext().getIncidentName());
			local.attachGateway(pair());
			local.retryRejected(operation);
			local.replayPending();
			assertTrue(local.pendingOperations().isEmpty());
		}
	}

	@Test
	void conflictAndDependentDraftSurviveOfflineRestart() {
		RemoteIncidentGateway gateway = pair();
		String fingerprint = gateway.authorityFingerprint().orElseThrow();
		try (RemoteIncidentStore local = new RemoteIncidentStore(gateway, incident, cache);
				RemoteIncidentStore other = new RemoteIncidentStore(pair(), incident)) {
			StoredIncident draft = local.loadSnapshot(incident);
			StoredIncident host = other.loadSnapshot(incident);
			draft.data().getIncidentContext().setIncidentName("mine");
			RevisionToken revision = local.save(incident, draft.data(), draft.revision());
			draft.data().getIncidentContext().setTaskMap("later");
			local.save(incident, draft.data(), revision);
			host.data().getIncidentContext().setIncidentName("theirs");
			other.save(incident, host.data(), host.revision());
			local.replayPending();
		}
		try (RemoteIncidentStore local = RemoteIncidentStore.openOffline(cache, fingerprint, incident)) {
			assertEquals(2, local.pendingOperations().size());
			RemoteIncidentStore.PendingOperation conflict = local.pendingOperations().get(0);
			assertEquals(RemoteIncidentStore.PendingStatus.CONFLICT, conflict.status());
			assertFalse(conflict.conflicts().isEmpty());
			assertEquals("later", local.loadSnapshot(incident).data().getIncidentContext().getTaskMap());
			local.attachGateway(pair());
			IncidentService.FieldConflict field = conflict.conflicts().get(0);
			local.resolvePending(conflict.operationId(), Map.of(
					new RemoteIncidentStore.ConflictField(field.key(), field.field()),
					RemoteIncidentStore.ConflictChoice.THEIRS));
			assertEquals("theirs", local.loadSnapshot(incident).data().getIncidentContext().getIncidentName());
			local.replayPending();
			assertTrue(local.pendingOperations().isEmpty());
		}
	}

	@Test
	void listFallsBackToCacheWhenAttachedHostIsUnavailable() {
		LosingGateway gateway = new LosingGateway(pair());
		try (RemoteIncidentStore local = new RemoteIncidentStore(gateway, incident, cache)) {
			local.loadSnapshot(incident);
			gateway.offline = true;
			assertEquals(1, local.list().size());
			assertEquals(incident, local.list().get(0).incidentId());
			gateway.offline = false;
			gateway.denyReads = true;
			assertThrows(RemoteAccessException.class, local::list);
		}
	}

	@Test
	void offlineCreatedRecordsAndLaterEditsUseAcceptedVersions() {
		try (RemoteIncidentStore local = new RemoteIncidentStore(pair(), incident, cache)) {
			StoredIncident draft = local.loadSnapshot(incident);
			ClueLogEntry first = new ClueLogEntry();
			first.setDescription("first clue");
			ClueLogEntry second = new ClueLogEntry();
			second.setDescription("second clue");
			draft.data().getClueLogEntries().add(first);
			draft.data().getClueLogEntries().add(second);
			RevisionToken revision = local.save(incident, draft.data(), draft.revision());
			first.setDescription("revised clue");
			second.setLocation("ridge");
			local.save(incident, draft.data(), revision);
			local.replayPending();
			assertTrue(local.pendingOperations().isEmpty());
			AppData committed = backing.loadSnapshot(incident).data();
			assertEquals(2, committed.getClueLogEntries().size());
			assertEquals("revised clue", committed.getClueLogEntries().stream()
					.filter(item -> item.getId().equals(first.getId())).findFirst().orElseThrow().getDescription());
			assertEquals("ridge", committed.getClueLogEntries().stream()
					.filter(item -> item.getId().equals(second.getId())).findFirst().orElseThrow().getLocation());
		}
	}

	@Test
	void sameClueEnteredOfflineByTwoClientsReplaysAsTwoRetainedUnstruckRecords() {
		String[] fingerprint = new String[2];
		String[] ids = new String[2];
		Path[] caches = {directory.resolve("cache-a"), directory.resolve("cache-b")};
		for (int client = 0; client < 2; client++) {
			RemoteIncidentGateway gateway = pair();
			fingerprint[client] = gateway.authorityFingerprint().orElseThrow();
			try (RemoteIncidentStore online = new RemoteIncidentStore(gateway, incident, caches[client])) {
				online.loadSnapshot(incident);
			}
		}
		for (int client = 0; client < 2; client++) {
			try (RemoteIncidentStore offline = RemoteIncidentStore.openOffline(caches[client], fingerprint[client],
					incident)) {
				StoredIncident draft = offline.loadSnapshot(incident);
				ClueLogEntry clue = new ClueLogEntry();
				clue.setLocation("North  ridge");
				clue.setDescription("Blue glove");
				draft.data().getClueLogEntries().add(clue);
				ids[client] = clue.getEntryId();
				offline.save(incident, draft.data(), draft.revision());
				assertEquals(1, offline.pendingOperations().size());
			}
		}
		for (int client = 0; client < 2; client++) {
			try (RemoteIncidentStore offline = RemoteIncidentStore.openOffline(caches[client], fingerprint[client],
					incident)) {
				offline.attachGateway(pair());
				offline.replayPending();
				assertTrue(offline.pendingOperations().isEmpty());
			}
		}
		assertNotEquals(ids[0], ids[1]);
		java.util.List<ClueLogEntry> committed = backing.loadSnapshot(incident).data().getClueLogEntries();
		assertEquals(2, committed.size());
		for (ClueLogEntry clue : committed) {
			assertFalse(clue.isStruckOut());
			assertEquals("Blue glove", clue.getDescription());
		}
		assertEquals(java.util.Set.of(ids[0], ids[1]),
				java.util.Set.of(committed.get(0).getEntryId(), committed.get(1).getEntryId()));
	}

	@Test
	void revertingFieldAcrossOrderedOfflineOperationsIsNotCoalescedAway() {
		try (RemoteIncidentStore local = new RemoteIncidentStore(pair(), incident, cache)) {
			StoredIncident draft = local.loadSnapshot(incident);
			String original = draft.data().getIncidentContext().getIncidentName();
			draft.data().getIncidentContext().setIncidentName("temporary");
			RevisionToken revision = local.save(incident, draft.data(), draft.revision());
			draft.data().getIncidentContext().setIncidentName(original);
			local.save(incident, draft.data(), revision);
			local.replayPending();
			assertTrue(local.pendingOperations().isEmpty());
			assertEquals(2, local.acceptedReceiptCount());
			assertEquals(original, backing.loadSnapshot(incident).data().getIncidentContext().getIncidentName());
		}
	}

	@Test
	void orphanedStagingFileNeverReplacesAcknowledgedJournal() throws Exception {
		RemoteIncidentGateway gateway = pair();
		String fingerprint = gateway.authorityFingerprint().orElseThrow();
		try (RemoteIncidentStore local = new RemoteIncidentStore(gateway, incident, cache)) {
			StoredIncident draft = local.loadSnapshot(incident);
			draft.data().getIncidentContext().setIncidentName("durable");
			local.save(incident, draft.data(), draft.revision());
		}
		Files.writeString(cache.resolve(fingerprint).resolve(incident.value() + ".json.new"), "interrupted");
		try (RemoteIncidentStore local = RemoteIncidentStore.openOffline(cache, fingerprint, incident)) {
			assertEquals("durable", local.loadSnapshot(incident).data().getIncidentContext().getIncidentName());
			assertEquals(1, local.pendingOperations().size());
		}
	}

	@Test
	void conflictResolutionPreservesLaterEditsAndUsesNewOperation() {
		try (RemoteIncidentStore local = new RemoteIncidentStore(pair(), incident, cache);
				RemoteIncidentStore other = new RemoteIncidentStore(pair(), incident)) {
			StoredIncident draft = local.loadSnapshot(incident);
			StoredIncident host = other.loadSnapshot(incident);
			draft.data().getIncidentContext().setIncidentName("mine");
			RevisionToken localRevision = local.save(incident, draft.data(), draft.revision());
			draft.data().getIncidentContext().setTaskMap("later");
			local.save(incident, draft.data(), localRevision);
			host.data().getIncidentContext().setIncidentName("theirs");
			other.save(incident, host.data(), host.revision());
			local.replayPending();
			RemoteIncidentStore.PendingOperation conflict = local.pendingOperations().get(0);
			assertEquals(RemoteIncidentStore.PendingStatus.CONFLICT, conflict.status());
			assertEquals(2, local.pendingOperations().size());
			IncidentService.FieldConflict field = conflict.conflicts().get(0);
			local.resolvePending(conflict.operationId(), Map.of(
					new RemoteIncidentStore.ConflictField(field.key(), field.field()),
					RemoteIncidentStore.ConflictChoice.THEIRS));
			assertNotEquals(conflict.operationId(), local.pendingOperations().get(0).operationId());
			assertEquals("later", local.loadSnapshot(incident).data().getIncidentContext().getTaskMap());
			assertEquals("theirs", local.loadSnapshot(incident).data().getIncidentContext().getIncidentName());
			local.replayPending();
			assertTrue(local.pendingOperations().isEmpty());
			AppData committed = backing.loadSnapshot(incident).data();
			assertEquals("theirs", committed.getIncidentContext().getIncidentName());
			assertEquals("later", committed.getIncidentContext().getTaskMap());
		}
	}

	@Test
	void conflictResolutionRejectsChangedHostValueWithoutLosingDraft() {
		try (RemoteIncidentStore local = new RemoteIncidentStore(pair(), incident, cache);
				RemoteIncidentStore other = new RemoteIncidentStore(pair(), incident)) {
			StoredIncident draft = local.loadSnapshot(incident);
			StoredIncident host = other.loadSnapshot(incident);
			draft.data().getIncidentContext().setIncidentName("mine");
			local.save(incident, draft.data(), draft.revision());
			host.data().getIncidentContext().setIncidentName("theirs");
			RevisionToken revision = other.save(incident, host.data(), host.revision());
			local.replayPending();
			RemoteIncidentStore.PendingOperation conflict = local.pendingOperations().get(0);
			IncidentService.FieldConflict field = conflict.conflicts().get(0);
			host.data().getIncidentContext().setIncidentName("changed again");
			other.save(incident, host.data(), revision);
			StoreValidationException stale = assertThrows(StoreValidationException.class,
					() -> local.resolvePending(conflict.operationId(), Map.of(
							new RemoteIncidentStore.ConflictField(field.key(), field.field()),
							RemoteIncidentStore.ConflictChoice.MINE)));
			assertEquals(RemoteIncidentStore.HOST_CHANGED, stale.getMessage());
			assertEquals(conflict.operationId(), local.pendingOperations().get(0).operationId());
			assertEquals("mine", local.loadSnapshot(incident).data().getIncidentContext().getIncidentName());
			RemoteIncidentStore.PendingOperation refreshed = local.refreshConflict(conflict.operationId());
			assertEquals("changed again", refreshed.conflicts().get(0).theirs().asText());
			local.resolvePending(refreshed.operationId(), Map.of(
					new RemoteIncidentStore.ConflictField(field.key(), field.field()),
					RemoteIncidentStore.ConflictChoice.MINE));
			local.replayPending();
			assertEquals("mine", backing.loadSnapshot(incident).data().getIncidentContext().getIncidentName());
		}
	}

	@Test
	void conflictRefreshWithNoRemainingConflictsCreatesUnsubmittedNewOperation() {
		try (RemoteIncidentStore local = new RemoteIncidentStore(pair(), incident, cache);
				RemoteIncidentStore other = new RemoteIncidentStore(pair(), incident)) {
			StoredIncident draft = local.loadSnapshot(incident);
			StoredIncident host = other.loadSnapshot(incident);
			String original = host.data().getIncidentContext().getIncidentName();
			draft.data().getIncidentContext().setIncidentName("mine");
			local.save(incident, draft.data(), draft.revision());
			host.data().getIncidentContext().setIncidentName("theirs");
			RevisionToken revision = other.save(incident, host.data(), host.revision());
			local.replayPending();
			RemoteIncidentStore.PendingOperation conflict = local.pendingOperations().get(0);
			host.data().getIncidentContext().setIncidentName(original);
			other.save(incident, host.data(), revision);
			RemoteIncidentStore.PendingOperation refreshed = local.refreshConflict(conflict.operationId());
			assertEquals(RemoteIncidentStore.PendingStatus.REVIEW_REQUIRED, refreshed.status());
			assertNotEquals(conflict.operationId(), refreshed.operationId());
			assertEquals(original, backing.loadSnapshot(incident).data().getIncidentContext().getIncidentName());
			local.replayPending();
			assertEquals(original, backing.loadSnapshot(incident).data().getIncidentContext().getIncidentName());
			assertEquals(RemoteIncidentStore.PendingStatus.REVIEW_REQUIRED, local.pendingOperations().get(0).status());
			local.confirmPending(refreshed.operationId());
			local.replayPending();
			assertTrue(local.pendingOperations().isEmpty());
			assertEquals("mine", backing.loadSnapshot(incident).data().getIncidentContext().getIncidentName());
		}
	}

	@Test
	void cacheBoundsAutosaveBases() throws Exception {
		try (RemoteIncidentStore local = new RemoteIncidentStore(pair(), incident, cache)) {
			StoredIncident draft = local.loadSnapshot(incident);
			RevisionToken revision = draft.revision();
			for (int index = 0; index < 24; index++) {
				draft.data().getIncidentContext().setIncidentName("save " + index);
				revision = local.save(incident, draft.data(), revision);
			}
			java.lang.reflect.Field field = RemoteIncidentStore.class.getDeclaredField("bases");
			field.setAccessible(true);
			assertTrue(((Map<?, ?>) field.get(local)).size() <= 16);
			assertEquals(24, local.pendingOperations().size());
		}
	}

	@Test
	void activeDirtyEditorBaseSurvivesMoreThanCacheLimitRefreshes() {
		try (RemoteIncidentStore local = new RemoteIncidentStore(pair(), incident, cache);
				RemoteIncidentStore other = new RemoteIncidentStore(pair(), incident)) {
			StoredIncident editor = local.loadSnapshot(incident);
			editor.data().getIncidentContext().setIncidentName("dirty");
			for (int index = 0; index < 18; index++) {
				StoredIncident host = other.loadSnapshot(incident);
				host.data().getIncidentContext().setTaskMap("remote " + index);
				other.save(incident, host.data(), host.revision());
				local.loadSnapshot(incident);
			}
			local.save(incident, editor.data(), editor.revision());
			local.replayPending();
			assertTrue(local.pendingOperations().isEmpty());
			AppData committed = backing.loadSnapshot(incident).data();
			assertEquals("dirty", committed.getIncidentContext().getIncidentName());
			assertEquals("remote 17", committed.getIncidentContext().getTaskMap());
		}
	}

	@Test
	void rejectedRefreshDoesNotPreventSavingTheOlderDirtyEditor() {
		try (RemoteIncidentStore local = new RemoteIncidentStore(pair(), incident, cache);
				RemoteIncidentStore other = new RemoteIncidentStore(pair(), incident)) {
			StoredIncident editor = local.loadSnapshot(incident);
			StoredIncident host = other.loadSnapshot(incident);
			editor.data().getIncidentContext().setIncidentName("dirty editor");
			host.data().getIncidentContext().setTaskMap("remote map");
			other.save(incident, host.data(), host.revision());
			StoredIncident refresh = local.loadSnapshot(incident);
			assertNotEquals(editor.revision(), refresh.revision());
			RevisionToken saved = local.save(incident, editor.data(), editor.revision());
			assertEquals(saved, local.revisionToken(editor.data()));
			local.replayPending();
			assertTrue(local.pendingOperations().isEmpty());
			assertEquals("dirty editor", backing.loadSnapshot(incident).data().getIncidentContext().getIncidentName());
			assertEquals("remote map", backing.loadSnapshot(incident).data().getIncidentContext().getTaskMap());
			editor.data().getIncidentContext().setTaskMap("unseen change");
			local.save(incident, editor.data(), saved);
			local.replayPending();
			assertEquals(RemoteIncidentStore.PendingStatus.CONFLICT, local.pendingOperations().get(0).status());
			assertEquals("remote map", backing.loadSnapshot(incident).data().getIncidentContext().getTaskMap());
		}
	}

	@Test
	void rejectDifferentAuthorityAndConcurrentWritersAndProtectRecovery() throws Exception {
		RemoteIncidentGateway gateway = pair();
		String fingerprint = gateway.authorityFingerprint().orElseThrow();
		try (RemoteIncidentStore local = new RemoteIncidentStore(gateway, incident, cache)) {
			local.loadSnapshot(incident);
			assertThrows(StorePersistenceException.class,
					() -> RemoteIncidentStore.openOffline(cache, fingerprint, incident));
			assertThrows(StoreValidationException.class,
					() -> local.exportOutbox(cache.resolve(fingerprint).resolve(incident.value() + ".json")));
			assertThrows(StoreValidationException.class,
					() -> local.exportFile(incident, cache.resolve(fingerprint).resolve(incident.value() + ".lock")));
			Path alias = directory.resolve("alias");
			Files.createSymbolicLink(alias, cache.resolve(fingerprint));
			assertThrows(StoreValidationException.class,
					() -> local.exportOutbox(alias.resolve(incident.value() + ".json")));
			Path hardlink = directory.resolve("lock-alias");
			Files.createLink(hardlink, cache.resolve(fingerprint).resolve(incident.value() + ".lock"));
			assertThrows(StoreValidationException.class, () -> local.exportOutbox(hardlink));
			assertThrows(StoreValidationException.class,
					() -> local.attachGateway(new LosingGateway(pair()) {
						@Override
						public Optional<String> authorityFingerprint() {
							return Optional.of("a".repeat(64));
						}
					}));
		}
	}

	@Test
	void queuedNoopDoesNotImplyAnotherHostConfirmation() throws Exception {
		try (RemoteIncidentStore local = new RemoteIncidentStore(pair(), incident, cache)) {
			StoredIncident draft = local.loadSnapshot(incident);
			var confirmed = local.lastConfirmedAt();
			local.save(incident, draft.data(), draft.revision());
			Path export = directory.resolve("outbox.json");
			local.exportOutbox(export);
			assertFalse(Files.readString(export).isEmpty());
			local.replayPending();
			assertEquals(confirmed, local.lastConfirmedAt());
			assertEquals(0, local.acceptedReceiptCount());
			assertTrue(local.pendingOperations().isEmpty());
		}
	}

	/** Gateway that can simulate loss of an accepted submission response. */
	private static class LosingGateway implements RemoteIncidentGateway {
		private final RemoteIncidentGateway delegate;
		private boolean lose;
		private boolean reject;
		private boolean offline;
		private boolean denyReads;
		private boolean loseHistory;
		private boolean rejectValidation;

		/**
		 * Wraps an authenticated gateway.
		 *
		 * @param delegate actual host gateway
		 */
		LosingGateway(RemoteIncidentGateway delegate) {
			this.delegate = delegate;
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
			if (denyReads) {
				throw new RemoteAccessException(RemoteAccessException.Reason.CREDENTIAL_REVOKED, "revoked", null);
			}
			if (offline) {
				throw new StoreUnavailableException("Simulated offline host");
			}
			return delegate.read(id);
		}

		/** {@inheritDoc} */
		@Override
		public IncidentService.ChangeResult changesSince(IncidentId id, long cursor) {
			if (loseHistory) {
				loseHistory = false;
				throw new StoreUnavailableException("Simulated lost history response");
			}
			return delegate.changesSince(id, cursor);
		}

		/** {@inheritDoc} */
		@Override
		public IncidentService.SubmissionResult submit(IncidentService.RecordBatch batch) {
			if (rejectValidation) {
				rejectValidation = false;
				throw new StoreValidationException("Simulated rejection");
			}
			if (reject) {
				throw new RemoteAccessException(RemoteAccessException.Reason.CREDENTIAL_REVOKED, "revoked", null);
			}
			IncidentService.SubmissionResult result = delegate.submit(batch);
			if (lose) {
				lose = false;
				throw new StoreUnavailableException("Simulated lost response");
			}
			return result;
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
