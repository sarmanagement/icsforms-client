/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.persistence;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Owner-thread-confined scheduler for abstract detached work. All I/O is serialized
 * on the worker; completions return to the owner (Swing's EDT in the application).
 * Generation replacement explicitly invalidates old queued units, but cannot undo
 * an already issued write. Callers must drain before switching or replacing a stored
 * document when that write could affect the document being loaded.
 */
public final class SaveCoordinator implements AutoCloseable {
	private static final Logger LOGGER = Logger.getLogger(SaveCoordinator.class.getName());
	private final Executor worker;
	private final Executor owner;
	private final Consumer<Completion> completed;
	private final Consumer<PersistenceStatus> statusListener;
	private final RetryPolicy retryPolicy;
	private final BiConsumer<Duration, Runnable> retryScheduler;
	private final ArrayDeque<SaveRequest> queue = new ArrayDeque<>();
	private final List<Runnable> idleListeners = new ArrayList<>();
	private PersistenceStatus status;
	private SaveRequest inFlight;
	private long dispatch;
	private long retryEpoch;
	private int failedAttempts;
	private boolean halted;
	private boolean closed;

	/**
	 * Result envelope retains the exact submitted identity, binding, edit and expected
	 * revision, rather than using payload identity as a completion key.
	 *
	 * @param request completed work
	 * @param revision accepted revision, or null on failure
	 * @param failure failure, or null on success
	 */
	public record Completion(SaveRequest request, RevisionToken revision, RuntimeException failure) {
	}

	/**
	 * Creates a scheduler. Every public operation must run on the owner thread;
	 * the owner executor must serialize completion callbacks on that same thread.
	 *
	 * @param worker executor for detached I/O
	 * @param owner serial executor for owner-thread completion
	 * @param completed matching-binding completion callback
	 * @param statusListener status presentation callback
	 * @param retryPolicy backend retry/backoff policy
	 */
	public SaveCoordinator(Executor worker, Executor owner, Consumer<Completion> completed,
			Consumer<PersistenceStatus> statusListener, RetryPolicy retryPolicy) {
		this(worker, owner, completed, statusListener, retryPolicy,
				(delay, retry) -> CompletableFuture.delayedExecutor(Math.max(0, delay.toMillis()), TimeUnit.MILLISECONDS)
						.execute(retry));
	}

	/**
	 * Creates a scheduler with an injectable timer for deterministic backoff tests.
	 *
	 * @param worker executor for detached I/O
	 * @param owner serial executor for owner-thread completion
	 * @param completed matching-binding completion callback
	 * @param statusListener status presentation callback
	 * @param retryPolicy backend retry/backoff policy
	 * @param retryScheduler timer that schedules retry delivery, without performing I/O
	 */
	public SaveCoordinator(Executor worker, Executor owner, Consumer<Completion> completed,
			Consumer<PersistenceStatus> statusListener, RetryPolicy retryPolicy,
			BiConsumer<Duration, Runnable> retryScheduler) {
		this.worker = Objects.requireNonNull(worker);
		this.owner = Objects.requireNonNull(owner);
		this.completed = Objects.requireNonNull(completed);
		this.statusListener = Objects.requireNonNull(statusListener);
		this.retryPolicy = Objects.requireNonNull(retryPolicy);
		this.retryScheduler = Objects.requireNonNull(retryScheduler);
	}

	/**
	 * Explicitly replaces the binding, invalidating queued work from the prior document.
	 * An old in-flight completion may release the worker but cannot change this status.
	 *
	 * @param incidentId active incident
	 * @param generation new editor generation
	 * @param editVersion initial edit version
	 * @param revision store revision of the replacement
	 */
	public void bind(IncidentId incidentId, long generation, long editVersion, RevisionToken revision) {
		if (closed) {
			throw new IllegalStateException("Persistence coordinator is closed.");
		}
		Objects.requireNonNull(incidentId);
		Objects.requireNonNull(revision);
		queue.clear();
		halted = false;
		failedAttempts = 0;
		retryEpoch++;
		update(new PersistenceStatus(incidentId, generation, editVersion,
				revision, PersistenceStatus.Durability.SAVED,
				PersistenceStatus.Synchronization.NOT_APPLICABLE, null));
	}

	/**
	 * Marks edits not yet submitted. Failure states remain visible until retry succeeds.
	 *
	 * @param editVersion latest monotonically increasing local edit version
	 */
	public void markDirty(long editVersion) {
		requireBound();
		if (editVersion < status.editVersion()) {
			throw new IllegalArgumentException("Edit versions cannot move backwards.");
		}
		setState(halted ? status.durability()
				: inFlight != null && matches(inFlight)
						? PersistenceStatus.Durability.SAVING : PersistenceStatus.Durability.PENDING,
				editVersion, status.revision(), status.failure());
	}

	/**
	 * Enqueues work, allowing only the concrete unit to coalesce adjacent units.
	 * Stale generations and out-of-order edits are rejected without dropping work.
	 *
	 * @param request detached work for the active binding
	 */
	public void submit(SaveRequest request) {
		requireBound();
		Objects.requireNonNull(request);
		if (!matches(request) || request.editVersion() < status.editVersion()
				|| !request.expectedRevision().equals(status.revision())) {
			throw new IllegalArgumentException("Save request does not match the current binding, edit or revision.");
		}
		SaveRequest previous = queue.peekLast();
		Optional<SaveRequest> combined = previous == null ? Optional.empty() : previous.coalesce(request);
		if (combined.isPresent()) {
			SaveRequest replacement = combined.get();
			if (!sameEnvelope(replacement, request)) {
				throw new IllegalArgumentException("Coalescing must retain the newest work envelope.");
			}
			queue.removeLast();
			queue.addLast(replacement);
		} else {
			queue.addLast(request);
		}
		setState(halted ? status.durability()
				: inFlight != null && matches(inFlight)
						? PersistenceStatus.Durability.SAVING : PersistenceStatus.Durability.PENDING,
				request.editVersion(), status.revision(), status.failure());
		startNext();
	}

	/**
	 * Explicitly retries retained work. Conflicts also require a fresh store revision
	 * through replacement/recovery; retrying alone never bypasses validation.
	 */
	public void retry() {
		requireBound();
		retryEpoch++;
		halted = false;
		failedAttempts = 0;
		startNext();
	}

	/**
	 * Returns current binding-scoped durability and synchronization.
	 *
	 * @return immutable current status, or null before binding
	 */
	public PersistenceStatus status() {
		return status;
	}

	/**
	 * Exposes retained detached work for recovery, including any issued write.
	 *
	 * @return ordered immutable work list
	 */
	public List<SaveRequest> pendingChanges() {
		List<SaveRequest> pending = new ArrayList<>();
		if (inFlight != null) {
			pending.add(inFlight);
		}
		pending.addAll(queue);
		return List.copyOf(pending);
	}

	/**
	 * Reports whether I/O is outstanding; a halted failed queue is not busy.
	 *
	 * @return true until worker completion is delivered
	 */
	public boolean isBusy() {
		return inFlight != null;
	}

	/**
	 * Runs after issued work drains or stops on failure, on the owner thread.
	 *
	 * @param listener continuation that must inspect status for success
	 */
	public void whenIdle(Runnable listener) {
		Objects.requireNonNull(listener);
		if (!isBusy()) {
			listener.run();
		} else {
			idleListeners.add(listener);
		}
	}

	/**
	 * Stops scheduling without destroying retained recovery work. Callers must drain
	 * issued writes before releasing stores or their locks.
	 */
	@Override
	public void close() {
		closed = true;
		retryEpoch++;
	}

	/** Requires an active, open binding before accepting editor operations. */
	private void requireBound() {
		if (closed || status == null) {
			throw new IllegalStateException("Persistence coordinator is not bound or is closed.");
		}
	}

	/**
	 * Compares binding identity without consulting a unit's payload.
	 *
	 * @param request work envelope
	 * @return true for the active incident and generation
	 */
	private boolean matches(SaveRequest request) {
		return status != null && status.incidentId().equals(request.incidentId())
				&& status.generation() == request.generation();
	}

	/**
	 * Compares the complete edit envelope, including expected revision.
	 *
	 * @param left first envelope
	 * @param right second envelope
	 * @return true when all matching fields agree
	 */
	private static boolean sameEnvelope(SaveRequest left, SaveRequest right) {
		return left.incidentId().equals(right.incidentId()) && left.generation() == right.generation()
				&& left.editVersion() == right.editVersion() && left.expectedRevision().equals(right.expectedRevision());
	}

	/** Dispatches one unit, never overlapping a predecessor's write or completion. */
	private void startNext() {
		if (closed || halted || inFlight != null || queue.isEmpty()) {
			notifyIdle();
			return;
		}
		SaveRequest request = queue.removeFirst();
		inFlight = request;
		long attempt = ++dispatch;
		setState(PersistenceStatus.Durability.SAVING, status.editVersion(), status.revision(), null);
		try {
			worker.execute(() -> {
				RevisionToken revision = null;
				RuntimeException failure = null;
				try {
					revision = Objects.requireNonNull(request.persist(), "Store returned no accepted revision.");
				} catch (Throwable exception) {
					failure = exception instanceof RuntimeException runtime ? runtime
							: new StorePersistenceException("Persistence worker could not complete the write", exception);
				}
				Completion result = new Completion(request, revision, failure);
				owner.execute(() -> finish(attempt, result));
			});
		} catch (RuntimeException failure) {
			finish(attempt, new Completion(request, null, failure));
		}
	}

	/**
	 * Applies a single matching dispatch. Duplicate or out-of-order callbacks cannot
	 * release a newer write, clear dirty state, or advance its accepted revision.
	 *
	 * @param attempt unique dispatch sequence
	 * @param result completed envelope
	 */
	private void finish(long attempt, Completion result) {
		if (attempt != dispatch || inFlight == null || !sameEnvelope(inFlight, result.request())) {
			return;
		}
		inFlight = null;
		if (matches(result.request())) {
			if (result.failure() == null) {
				failedAttempts = 0;
				rebaseQueued(result.request(), result.revision());
				setState(queue.isEmpty() && result.request().editVersion() == status.editVersion()
						? PersistenceStatus.Durability.SAVED : PersistenceStatus.Durability.PENDING,
						status.editVersion(), result.revision(), null);
			} else {
				queue.addFirst(result.request());
				halted = true;
				failedAttempts++;
				PersistenceStatus.Durability durability = result.failure() instanceof StoreConflictException
						? PersistenceStatus.Durability.CONFLICTED
						: result.failure() instanceof StoreUnavailableException
								? PersistenceStatus.Durability.UNAVAILABLE : PersistenceStatus.Durability.FAILED;
				setState(durability, status.editVersion(), status.revision(), result.failure());
			}
			if (matches(result.request())) {
				deliver(() -> completed.accept(result));
			}
			if (attempt == dispatch && matches(result.request()) && halted
					&& !(result.failure() instanceof StoreConflictException)) {
				scheduleRetry(result.failure());
			}
		}
		startNext();
	}

	/**
	 * Carries accepted predecessor revisions through ordered work without parsing them.
	 *
	 * @param predecessor completed work and its expected revision
	 * @param accepted newly issued revision
	 */
	private void rebaseQueued(SaveRequest predecessor, RevisionToken accepted) {
		ArrayDeque<SaveRequest> rebased = new ArrayDeque<>();
		for (SaveRequest request : queue) {
			rebased.addLast(matches(request) && request.expectedRevision().equals(predecessor.expectedRevision())
					? request.afterPredecessor(predecessor, accepted) : request);
		}
		queue.clear();
		queue.addAll(rebased);
	}

	/**
	 * Schedules policy-controlled retry; replacement/explicit retry invalidates timers.
	 *
	 * @param failure retained write failure
	 */
	private void scheduleRetry(RuntimeException failure) {
		try {
			Optional<Duration> delay = retryPolicy.retryDelay(failure, failedAttempts);
			if (delay.isEmpty()) {
				return;
			}
			if (delay.get().isZero()) {
				retryEpoch++;
				halted = false;
				return;
			}
			long epoch = ++retryEpoch;
			retryScheduler.accept(delay.get(), () -> owner.execute(() -> {
				if (!closed && halted && retryEpoch == epoch) {
					halted = false;
					startNext();
				}
			}));
		} catch (RuntimeException exception) {
			LOGGER.log(Level.WARNING, "Automatic retry could not be scheduled; retained work needs explicit retry", exception);
		}
	}

	/**
	 * Updates durability while preserving binding and independent synchronization.
	 *
	 * @param durability new durability
	 * @param editVersion latest local edit
	 * @param revision accepted revision
	 * @param failure current failure or null
	 */
	private void setState(PersistenceStatus.Durability durability, long editVersion, RevisionToken revision,
			RuntimeException failure) {
		update(new PersistenceStatus(status.incidentId(), status.generation(), editVersion, revision, durability,
				status.synchronization(), failure));
	}

	/**
	 * Publishes immutable status without letting presentation failures lose work.
	 *
	 * @param next replacement status
	 */
	private void update(PersistenceStatus next) {
		status = next;
		deliver(() -> statusListener.accept(next));
	}

	/** Delivers drain continuations only when no issued I/O remains. */
	private void notifyIdle() {
		if (inFlight != null || idleListeners.isEmpty()) {
			return;
		}
		List<Runnable> listeners = List.copyOf(idleListeners);
		idleListeners.clear();
		listeners.forEach(SaveCoordinator::deliver);
	}

	/**
	 * Isolates presentation callbacks from persistence scheduling.
	 *
	 * @param callback owner-thread callback
	 */
	private static void deliver(Runnable callback) {
		try {
			callback.run();
		} catch (RuntimeException exception) {
			LOGGER.log(Level.WARNING, "Persistence presentation callback failed", exception);
		}
	}
}
