/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.persistence;

import java.util.Optional;

/** Optional advisory editing capability; revision checks never require callers to lock. */
public interface EditLockSupport {
	/**
	 * Attempts to acquire an edit lock, retaining an existing local lock.
	 *
	 * @param id incident identity
	 * @return whether this store owns the lock
	 */
	boolean lock(IncidentId id);

	/**
	 * Checks for a different editor without acquiring a persistent lock.
	 *
	 * @param id incident identity
	 * @return whether another editor owns the lock
	 */
	boolean isLockedElsewhere(IncidentId id);

	/**
	 * Releases this store's lock; does nothing if not owned, including cleanup after close.
	 *
	 * @param id incident identity
	 */
	void unlock(IncidentId id);

	/**
	 * Reports this handle's ownership so failed switches retain preexisting locks.
	 * This resource query remains safe after close and then returns false.
	 *
	 * @param id incident identity
	 * @return whether this handle owns an advisory lock
	 */
	boolean isHeldByCaller(IncidentId id);

	/**
	 * Returns informational holder text, never authorization or concurrency evidence.
	 *
	 * @param id incident identity
	 * @return holder description when available
	 */
	default Optional<String> holder(IncidentId id) {
		return Optional.empty();
	}
}
