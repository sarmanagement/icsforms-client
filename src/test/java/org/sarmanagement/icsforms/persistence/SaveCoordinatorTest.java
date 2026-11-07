/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.model.AppData;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises deterministic worker/completion ordering without sleeps or live payloads. */
class SaveCoordinatorTest {
	@TempDir
	Path directory;

	@Test
	void snapshotsAreDetachedAndNewerEditsSurviveAnInFlightCompletion() {
		try (ControllableIncidentStore store = new ControllableIncidentStore(directory)) {
			AppData live = new AppData();
			IncidentId id = store.create(live);
			Harness harness = new Harness(id, store.revisionToken(live));
			live.getIncidentContext().setIncidentName("first");
			harness.coordinator.markDirty(1);
			harness.coordinator.submit(snapshot(store, live, id, 1, harness.coordinator.status().revision()));
			live.getIncidentContext().setIncidentName("second");
			harness.coordinator.markDirty(2);
			harness.coordinator.submit(snapshot(store, live, id, 2, harness.coordinator.status().revision()));
			live.getIncidentContext().setIncidentName("third");
			harness.coordinator.markDirty(3);
			harness.coordinator.submit(snapshot(store, live, id, 3, harness.coordinator.status().revision()));
			assertEquals(2, harness.coordinator.pendingChanges().size());
			assertEquals(1, harness.worker.tasks.size());
			harness.worker.next().run();
			assertEquals("first", store.loadSnapshot(id).data().getIncidentContext().getIncidentName());
			assertEquals(PersistenceStatus.Durability.SAVING, harness.coordinator.status().durability());
			Runnable oldCompletion = harness.owner.next();
			oldCompletion.run();
			assertEquals(PersistenceStatus.Durability.SAVING, harness.coordinator.status().durability());
			assertEquals(3, harness.coordinator.status().editVersion());
			assertEquals(1, harness.completions.size());
			harness.worker.next().run();
			harness.owner.next().run();
			RevisionToken latest = harness.coordinator.status().revision();
			oldCompletion.run();
			assertEquals(latest, harness.coordinator.status().revision());
			assertEquals(2, harness.completions.size());
			assertEquals(PersistenceStatus.Durability.SAVED, harness.coordinator.status().durability());
			assertEquals("third", store.loadSnapshot(id).data().getIncidentContext().getIncidentName());
			assertEquals("third", live.getIncidentContext().getIncidentName());
			assertEquals(0, live.getRevision());
		}
	}

	@Test
	void unqueuedEditsRemainPendingAfterTheirPredecessorIsDurable() {
		Harness harness = new Harness(IncidentId.newId(), new RevisionToken("host/initial"));
		List<String> accepted = new ArrayList<>();
		harness.coordinator.submit(operation(harness, 1, "one", accepted));
		harness.coordinator.markDirty(2);
		harness.complete();
		assertEquals(PersistenceStatus.Durability.PENDING, harness.coordinator.status().durability());
		assertTrue(harness.coordinator.pendingChanges().isEmpty());
		assertFalse(harness.coordinator.isBusy());
		assertEquals(2, harness.coordinator.status().editVersion());
	}

	@Test
	void secondUnitTypePreservesAllOperationsAndUsesServerIssuedTokens() {
		Harness harness = new Harness(IncidentId.newId(), new RevisionToken("host/initial"));
		List<String> accepted = new ArrayList<>();
		harness.coordinator.submit(operation(harness, 1, "one", accepted));
		harness.coordinator.submit(operation(harness, 2, "two", accepted));
		harness.coordinator.submit(operation(harness, 3, "three", accepted));
		assertEquals(3, harness.coordinator.pendingChanges().size());
		harness.complete();
		assertEquals(new RevisionToken("host/accepted/one"), harness.coordinator.pendingChanges().get(0).expectedRevision());
		harness.complete();
		assertEquals(new RevisionToken("host/accepted/two"), harness.coordinator.pendingChanges().get(0).expectedRevision());
		harness.complete();
		assertEquals(List.of("one", "two", "three"), accepted);
		assertEquals(new RevisionToken("host/accepted/three"), harness.coordinator.status().revision());
		assertEquals(PersistenceStatus.Durability.SAVED, harness.coordinator.status().durability());
		assertEquals(PersistenceStatus.Synchronization.NOT_APPLICABLE, harness.coordinator.status().synchronization());
	}

	@Test
	void failedNoncoalescingWorkRetainsItsEntireOrderedOperationQueue() {
		Harness harness = new Harness(IncidentId.newId(), new RevisionToken("host/initial"));
		List<String> accepted = new FailOnceLog();
		harness.coordinator.submit(operation(harness, 1, "one", accepted));
		harness.coordinator.submit(operation(harness, 2, "two", accepted));
		harness.complete();
		assertEquals(PersistenceStatus.Durability.UNAVAILABLE, harness.coordinator.status().durability());
		assertEquals(2, harness.coordinator.pendingChanges().size());
		harness.coordinator.submit(operation(harness, 3, "three", accepted));
		assertEquals(3, harness.coordinator.pendingChanges().size());
		assertTrue(accepted.isEmpty());
		harness.coordinator.retry();
		harness.complete();
		harness.complete();
		harness.complete();
		assertEquals(List.of("one", "two", "three"), accepted);
		assertEquals(PersistenceStatus.Durability.SAVED, harness.coordinator.status().durability());
	}

	@Test
	void delayedPreviousIncidentAndGenerationCompletionsCannotChangeCurrentStatus() {
		Harness harness = new Harness(IncidentId.newId(), new RevisionToken("old"));
		List<String> accepted = new ArrayList<>();
		harness.coordinator.submit(operation(harness, 1, "old", accepted));
		harness.worker.next().run();
		Runnable oldCompletion = harness.owner.next();
		IncidentId replacement = IncidentId.newId();
		harness.coordinator.bind(replacement, 2, 10, new RevisionToken("current"));
		harness.coordinator.submit(new OperationUnit(replacement, 2, 11, new RevisionToken("current"), "current", accepted));
		assertEquals(PersistenceStatus.Durability.PENDING, harness.coordinator.status().durability());
		oldCompletion.run();
		assertTrue(harness.completions.isEmpty());
		assertEquals(replacement, harness.coordinator.status().incidentId());
		assertEquals(new RevisionToken("current"), harness.coordinator.status().revision());
		harness.complete();
		RevisionToken current = harness.coordinator.status().revision();
		oldCompletion.run();
		assertEquals(current, harness.coordinator.status().revision());
		harness.coordinator.submit(new OperationUnit(replacement, 2, 12, current, "stale-generation", accepted));
		harness.worker.next().run();
		harness.coordinator.bind(replacement, 3, 20, new RevisionToken("replacement"));
		harness.owner.next().run();
		assertEquals(1, harness.completions.size());
		assertEquals(3, harness.coordinator.status().generation());
		assertEquals(new RevisionToken("replacement"), harness.coordinator.status().revision());
	}

	@Test
	void failedAndUnavailableSavesRetainWorkUntilExplicitRetrySucceeds() {
		for (RuntimeException failure : List.of(new StorePersistenceException("Disk full", null),
				new StoreUnavailableException("Offline"))) {
			try (ControllableIncidentStore store = new ControllableIncidentStore(directory.resolve(failure.getClass().getSimpleName()))) {
				AppData live = new AppData();
				IncidentId id = store.create(live);
				Harness harness = new Harness(id, store.revisionToken(live));
				store.failNext(ControllableIncidentStore.Operation.SAVE, failure);
				live.getIncidentContext().setIncidentName("unsaved");
				harness.coordinator.submit(snapshot(store, live, id, 1, harness.coordinator.status().revision()));
				harness.complete();
				assertEquals(failure instanceof StoreUnavailableException ? PersistenceStatus.Durability.UNAVAILABLE
						: PersistenceStatus.Durability.FAILED, harness.coordinator.status().durability());
				assertEquals(failure, harness.coordinator.status().failure());
				assertEquals(1, harness.coordinator.pendingChanges().size());
				assertFalse(harness.coordinator.isBusy());
				RevisionToken previous = harness.coordinator.status().revision();
				live.getIncidentContext().setIncidentName("newest unsaved");
				harness.coordinator.markDirty(2);
				harness.coordinator.submit(snapshot(store, live, id, 2, previous));
				assertEquals(1, harness.coordinator.pendingChanges().size());
				assertTrue(harness.worker.tasks.isEmpty());
				harness.coordinator.retry();
				harness.complete();
				assertEquals(PersistenceStatus.Durability.SAVED, harness.coordinator.status().durability());
				assertNotEquals(previous, harness.coordinator.status().revision());
				assertEquals("newest unsaved", store.loadSnapshot(id).data().getIncidentContext().getIncidentName());
			}
		}
	}

	@Test
	void conflictDoesNotAutomaticallyRetryOrReplaceItsExpectedRevision() {
		try (ControllableIncidentStore store = new ControllableIncidentStore(directory)) {
			AppData live = new AppData();
			IncidentId id = store.create(live);
			Harness harness = new Harness(id, store.revisionToken(live));
			StoredIncident concurrent = store.loadSnapshot(id);
			store.save(id, concurrent.data(), concurrent.revision());
			harness.coordinator.submit(snapshot(store, live, id, 1, harness.coordinator.status().revision()));
			harness.complete();
			assertEquals(PersistenceStatus.Durability.CONFLICTED, harness.coordinator.status().durability());
			assertEquals(store.revisionToken(live), harness.coordinator.status().revision());
			assertEquals(1, harness.coordinator.pendingChanges().size());
			assertTrue(harness.worker.tasks.isEmpty());
		}
	}

	@Test
	void rejectedDispatchRetainsWorkAndClosingDoesNotDiscardRecoveryPayload() {
		IncidentId id = IncidentId.newId();
		RevisionToken revision = new RevisionToken("initial");
		SaveCoordinator coordinator = new SaveCoordinator(command -> {
			throw new java.util.concurrent.RejectedExecutionException("Stopped worker");
		}, Runnable::run, completion -> { }, status -> { }, RetryPolicy.explicit());
		coordinator.bind(id, 1, 0, revision);
		coordinator.submit(new OperationUnit(id, 1, 1, revision, "retained", new ArrayList<>()));
		assertEquals(PersistenceStatus.Durability.FAILED, coordinator.status().durability());
		coordinator.close();
		assertEquals(1, coordinator.pendingChanges().size());
		assertThrows(IllegalStateException.class, coordinator::retry);
	}

	@Test
	void unexpectedWorkerErrorsCompleteTheDrainAndRetainRetryableWork() {
		Harness harness = new Harness(IncidentId.newId(), new RevisionToken("host/initial"));
		List<String> accepted = new ArrayList<>() {
			private boolean fail = true;

			/**
			 * Simulates a worker error before accepting the first operation.
			 *
			 * @param operation operation payload
			 * @return true after acceptance
			 */
			@Override
			public boolean add(String operation) {
				if (fail) {
					fail = false;
					throw new AssertionError("Unexpected worker failure");
				}
				return super.add(operation);
			}
		};
		boolean[] drained = {false};
		harness.coordinator.submit(operation(harness, 1, "one", accepted));
		harness.coordinator.whenIdle(() -> drained[0] = true);
		harness.complete();
		assertTrue(drained[0]);
		assertFalse(harness.coordinator.isBusy());
		assertEquals(PersistenceStatus.Durability.FAILED, harness.coordinator.status().durability());
		assertInstanceOf(AssertionError.class, harness.coordinator.status().failure().getCause());
		assertEquals(1, harness.coordinator.pendingChanges().size());
		harness.coordinator.retry();
		harness.complete();
		assertEquals(List.of("one"), accepted);
		assertEquals(PersistenceStatus.Durability.SAVED, harness.coordinator.status().durability());
	}

	@Test
	void staleEnvelopesAreRejectedWithoutDiscardingQueuedWork() {
		Harness harness = new Harness(IncidentId.newId(), new RevisionToken("initial"));
		List<String> accepted = new ArrayList<>();
		harness.coordinator.submit(operation(harness, 1, "one", accepted));
		assertThrows(IllegalArgumentException.class, () -> harness.coordinator.submit(
				new OperationUnit(harness.coordinator.status().incidentId(), 2, 2,
						harness.coordinator.status().revision(), "wrong-binding", accepted)));
		assertThrows(IllegalArgumentException.class, () -> harness.coordinator.submit(
				new OperationUnit(harness.coordinator.status().incidentId(), 1, 2,
						new RevisionToken("wrong-revision"), "wrong-revision", accepted)));
		assertThrows(NullPointerException.class, () -> harness.coordinator.bind(
				harness.coordinator.status().incidentId(), 2, 10, null));
		assertEquals(1, harness.coordinator.pendingChanges().size());
		assertEquals(1, harness.coordinator.status().generation());
	}

	@Test
	void boundedBackoffAndBindingReplacementInvalidateOldRetryTimers() {
		try (ControllableIncidentStore store = new ControllableIncidentStore(directory)) {
			AppData live = new AppData();
			IncidentId id = store.create(live);
			ManualExecutor worker = new ManualExecutor();
			ManualExecutor owner = new ManualExecutor();
			List<Runnable> retries = new ArrayList<>();
			List<Duration> delays = new ArrayList<>();
			SaveCoordinator coordinator = new SaveCoordinator(worker, owner, result -> { }, status -> { },
					(failure, attempts) -> attempts <= 1 ? Optional.of(Duration.ofSeconds(3)) : Optional.empty(),
					(delay, retry) -> {
						delays.add(delay);
						retries.add(retry);
					});
			coordinator.bind(id, 1, 0, store.revisionToken(live));
			store.failNext(ControllableIncidentStore.Operation.SAVE, new StoreUnavailableException("Offline"));
			coordinator.submit(snapshot(store, live, id, 1, coordinator.status().revision()));
			worker.next().run();
			owner.next().run();
			assertEquals(List.of(Duration.ofSeconds(3)), delays);
			assertTrue(worker.tasks.isEmpty());
			store.failNext(ControllableIncidentStore.Operation.SAVE, new StoreUnavailableException("Still offline"));
			retries.get(0).run();
			owner.next().run();
			worker.next().run();
			owner.next().run();
			assertEquals(1, retries.size(), "Automatic attempts are bounded");
			assertEquals(1, coordinator.pendingChanges().size());
			coordinator.bind(id, 2, 10, store.loadSnapshot(id).revision());
			retries.get(0).run();
			owner.next().run();
			assertTrue(worker.tasks.isEmpty());
			assertEquals(PersistenceStatus.Durability.SAVED, coordinator.status().durability());
			assertEquals(2, coordinator.status().generation());
			assertTrue(RetryPolicy.explicit().retryDelay(new StoreUnavailableException("Offline"), 1).isEmpty());
			assertEquals(Optional.of(Duration.ZERO),
					RetryPolicy.immediate(1).retryDelay(new StoreUnavailableException("Offline"), 1));
			assertTrue(RetryPolicy.immediate(1).retryDelay(new StoreUnavailableException("Offline"), 2).isEmpty());
			assertThrows(IllegalArgumentException.class, () -> RetryPolicy.immediate(-1));
		}
	}

	@Test
	void presentationTriggeredReplacementCannotDeliverAnOldCompletionToTheNewBinding() {
		ManualExecutor worker = new ManualExecutor();
		ManualExecutor owner = new ManualExecutor();
		List<SaveCoordinator.Completion> completed = new ArrayList<>();
		AtomicReference<SaveCoordinator> reference = new AtomicReference<>();
		IncidentId id = IncidentId.newId();
		SaveCoordinator coordinator = new SaveCoordinator(worker, owner, completed::add, status -> {
			if (status.generation() == 1 && status.editVersion() == 1
					&& status.durability() == PersistenceStatus.Durability.SAVED) {
				reference.get().bind(id, 2, 10, new RevisionToken("store-notification"));
			}
		}, RetryPolicy.explicit());
		reference.set(coordinator);
		coordinator.bind(id, 1, 0, new RevisionToken("host/initial"));
		coordinator.submit(new OperationUnit(id, 1, 1, coordinator.status().revision(), "one", new ArrayList<>()));
		worker.next().run();
		owner.next().run();
		assertTrue(completed.isEmpty());
		assertEquals(2, coordinator.status().generation());
		assertEquals(new RevisionToken("store-notification"), coordinator.status().revision());
	}

	/**
	 * Captures detached work for the fixture's single editor generation.
	 *
	 * @param store test destination
	 * @param live source document
	 * @param id target identity
	 * @param edit local edit version
	 * @param revision expected token
	 * @return full snapshot work
	 */
	private static SaveRequest snapshot(IncidentStore store, AppData live, IncidentId id, long edit, RevisionToken revision) {
		return new FullDocumentSnapshot(id, 1, edit, revision, FullDocumentSnapshot.detach(live), store);
	}

	/**
	 * Builds an operation without introducing any document dependency.
	 *
	 * @param harness scheduler fixture
	 * @param edit local edit version
	 * @param name operation name
	 * @param accepted ordered accepted operation list
	 * @return operation unit
	 */
	private static SaveRequest operation(Harness harness, long edit, String name, List<String> accepted) {
		PersistenceStatus status = harness.coordinator.status();
		return new OperationUnit(status.incidentId(), status.generation(), edit, status.revision(), name, accepted);
	}

	/**
	 * Noncoalescing operation stub proves work and revisions remain abstract.
	 *
	 * @param incidentId target identity
	 * @param generation editor binding generation
	 * @param editVersion represented edit
	 * @param expectedRevision predecessor token
	 * @param name operation payload
	 * @param accepted accepted operation log
	 */
	private record OperationUnit(IncidentId incidentId, long generation, long editVersion, RevisionToken expectedRevision,
			String name, List<String> accepted) implements SaveRequest {
		/** {@inheritDoc} */
		@Override
		public SaveRequest withExpectedRevision(RevisionToken revision) {
			return new OperationUnit(incidentId, generation, editVersion, revision, name, accepted);
		}

		/** {@inheritDoc} */
		@Override
		public Optional<SaveRequest> coalesce(SaveRequest newer) {
			return Optional.empty();
		}

		/** {@inheritDoc} */
		@Override
		public RevisionToken persist() {
			String predecessor = accepted.isEmpty() ? "host/initial" : "host/accepted/" + accepted.get(accepted.size() - 1);
			if (expectedRevision.value().startsWith("host/")) {
				assertEquals(new RevisionToken(predecessor), expectedRevision);
			}
			accepted.add(name);
			return new RevisionToken("host/accepted/" + name);
		}
	}

	/** Separate manually drained executors make callback order deterministic. */
	private static final class ManualExecutor implements Executor {
		private final ArrayDeque<Runnable> tasks = new ArrayDeque<>();

		/** {@inheritDoc} */
		@Override
		public void execute(Runnable command) {
			tasks.addLast(command);
		}

		/**
		 * Removes the next scheduled task without running it.
		 *
		 * @return scheduled task
		 */
		private Runnable next() {
			assertFalse(tasks.isEmpty(), "Expected a scheduled task");
			return tasks.removeFirst();
		}
	}

	/** Ordered log whose first write fails before accepting any operation. */
	private static final class FailOnceLog extends ArrayList<String> {
		private boolean unavailable = true;

		/**
		 * Accepts operations only after the simulated store becomes available.
		 *
		 * @param operation operation payload
		 * @return true after accepting the operation
		 */
		@Override
		public boolean add(String operation) {
			if (unavailable) {
				unavailable = false;
				throw new StoreUnavailableException("Operation store is temporarily unavailable");
			}
			return super.add(operation);
		}
	}

	/** Owner/worker fixture retaining all matching completion envelopes. */
	private static final class Harness {
		private final ManualExecutor worker = new ManualExecutor();
		private final ManualExecutor owner = new ManualExecutor();
		private final List<SaveCoordinator.Completion> completions = new ArrayList<>();
		private final SaveCoordinator coordinator =
				new SaveCoordinator(worker, owner, completions::add, status -> { }, RetryPolicy.explicit());

		/**
		 * Binds the initial fixture.
		 *
		 * @param id incident identity
		 * @param revision starting store token
		 */
		private Harness(IncidentId id, RevisionToken revision) {
			coordinator.bind(id, 1, 0, revision);
		}

		/** Executes one write and delivers its owner-thread completion. */
		private void complete() {
			worker.next().run();
			owner.next().run();
		}
	}
}
