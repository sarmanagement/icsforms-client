/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.persistence;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Provides process-wide serialization for operations on the same incident identity.
 */
final class IncidentOperationLock {
	private static final Map<IncidentId, LockEntry> LOCKS = new HashMap<>();

	/**
	 * Prevents construction of this static coordination utility.
	 */
	private IncidentOperationLock() {
	}

	/**
	 * Runs an operation while holding the lock for its incident.
	 *
	 * @param incident incident identity
	 * @param operation operation to run
	 * @param <T> operation result type
	 * @return operation result
	 */
	static <T> T withLock(IncidentId incident, Supplier<T> operation) {
		Objects.requireNonNull(incident, "incident");
		Objects.requireNonNull(operation, "operation");
		LockEntry entry;
		synchronized (LOCKS) {
			entry = LOCKS.computeIfAbsent(incident, ignored -> new LockEntry());
			entry.users++;
		}
		entry.lock.lock();
		try {
			return operation.get();
		} finally {
			entry.lock.unlock();
			synchronized (LOCKS) {
				entry.users--;
				if (entry.users == 0) {
					LOCKS.remove(incident, entry);
				}
			}
		}
	}

	/**
	 * Tracks active callers so unused incident locks can be removed.
	 */
	private static final class LockEntry {
		private final ReentrantLock lock = new ReentrantLock();
		private int users;
	}
}
