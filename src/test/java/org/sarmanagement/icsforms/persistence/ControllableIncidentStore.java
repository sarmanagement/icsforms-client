/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.persistence;

import org.sarmanagement.icsforms.model.AppData;

import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * File-backed test store with deterministic operation failures and gates.
 */
public final class ControllableIncidentStore implements IncidentStore {
	private final FileIncidentStore delegate;
	private final Map<Operation, RuntimeException> failures = new EnumMap<>(Operation.class);
	private final Map<Operation, OperationGate> gates = new EnumMap<>(Operation.class);

	/**
	 * Opens a controllable store under an isolated test directory.
	 *
	 * @param root test store directory.
	 */
	public ControllableIncidentStore(Path root) {
		delegate = new FileIncidentStore(root);
	}

	/**
	 * Schedules a one-time failure before the next operation of the given type.
	 *
	 * @param operation operation to fail.
	 * @param failure exception to throw.
	 */
	public synchronized void failNext(Operation operation, RuntimeException failure) {
		failures.put(operation, failure);
	}

	/**
	 * Blocks the next operation of the given type until the returned gate is released.
	 *
	 * @param operation operation to block.
	 * @return gate for coordinating the operation without sleeps.
	 */
	public synchronized OperationGate blockNext(Operation operation) {
		OperationGate gate = new OperationGate();
		gates.put(operation, gate);
		return gate;
	}

	/**
	 * Acquires the delegate's edit lock for deletion-conflict contract tests.
	 *
	 * @param id incident identifier.
	 * @return {@code true} when the lock was acquired.
	 */
	public boolean lock(IncidentId id) {
		return delegate.lock(id);
	}

	/**
	 * Releases the delegate's edit lock.
	 *
	 * @param id incident identifier.
	 */
	public void unlock(IncidentId id) {
		delegate.unlock(id);
	}

	/** {@inheritDoc} */
	@Override
	public IncidentId create(AppData data) {
		before(Operation.CREATE);
		return delegate.create(data);
	}

	/** {@inheritDoc} */
	@Override
	public AppData load(IncidentId id) {
		before(Operation.LOAD);
		return delegate.load(id);
	}

	/** {@inheritDoc} */
	@Override
	public void save(IncidentId id, AppData data) {
		before(Operation.SAVE);
		delegate.save(id, data);
	}

	/** {@inheritDoc} */
	@Override
	public List<IncidentSummary> list() {
		before(Operation.LIST);
		return delegate.list();
	}

	/** {@inheritDoc} */
	@Override
	public void delete(IncidentId id) {
		before(Operation.DELETE);
		delegate.delete(id);
	}

	/** {@inheritDoc} */
	@Override
	public Optional<IncidentId> importFile(Path jsonFile) {
		before(Operation.IMPORT);
		return delegate.importFile(jsonFile);
	}

	/** {@inheritDoc} */
	@Override
	public List<ImportValidationException.Problem> getLastImportProblems() {
		return delegate.getLastImportProblems();
	}

	/** {@inheritDoc} */
	@Override
	public void exportFile(IncidentId id, Path target) {
		before(Operation.EXPORT);
		delegate.exportFile(id, target);
	}

	/** {@inheritDoc} */
	@Override
	public boolean supportsBackup() {
		return delegate.supportsBackup();
	}

	/** {@inheritDoc} */
	@Override
	public void backupFile(IncidentId id, Path target) {
		before(Operation.BACKUP);
		delegate.backupFile(id, target);
	}

	/** {@inheritDoc} */
	@Override
	public StoreDescriptor describe() {
		before(Operation.DESCRIBE);
		return delegate.describe();
	}

	/** {@inheritDoc} */
	@Override
	public void close() {
		delegate.close();
	}

	/**
	 * Identifies an operation that can be failed or blocked.
	 */
	public enum Operation {
		CREATE, LOAD, SAVE, LIST, DELETE, IMPORT, EXPORT, BACKUP, DESCRIBE
	}

	/**
	 * Coordinates a blocked operation using latches rather than timing sleeps.
	 */
	public static final class OperationGate {
		private final CountDownLatch started = new CountDownLatch(1);
		private final CountDownLatch released = new CountDownLatch(1);

		/**
		 * Creates a gate for one store operation.
		 */
		private OperationGate() {
		}

		/**
		 * Waits until the controlled operation has reached the gate.
		 *
		 * @param timeout maximum wait duration.
		 * @param unit timeout unit.
		 * @return {@code true} when the operation reached the gate.
		 * @throws InterruptedException if interrupted while waiting.
		 */
		public boolean awaitStarted(long timeout, TimeUnit unit) throws InterruptedException {
			return started.await(timeout, unit);
		}

		/**
		 * Allows the blocked operation to continue.
		 */
		public void release() {
			released.countDown();
		}
	}

	/**
	 * Applies the next scheduled failure or operation gate.
	 *
	 * @param operation operation about to be delegated.
	 */
	private void before(Operation operation) {
		RuntimeException failure;
		OperationGate gate;
		synchronized (this) {
			failure = failures.remove(operation);
			gate = gates.remove(operation);
		}
		if (gate != null) {
			gate.started.countDown();
			try {
				gate.released.await();
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException("Interrupted while waiting at the test store gate", exception);
			}
		}
		if (failure != null) {
			throw failure;
		}
	}
}
