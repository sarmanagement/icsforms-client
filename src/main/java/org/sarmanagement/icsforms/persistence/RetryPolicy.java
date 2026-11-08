/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.persistence;

import java.time.Duration;
import java.util.Optional;

/**
 * Backend-selectable bounded retry/backoff policy. Conflicts require recovery and
 * are never automatically retried by the coordinator.
 */
@FunctionalInterface
public interface RetryPolicy {
	/**
	 * Selects whether and when to retry retained work.
	 *
	 * @param failure write failure
	 * @param failedAttempts consecutive failed attempts, starting at one
	 * @return delay, or empty to require an explicit retry
	 */
	Optional<Duration> retryDelay(RuntimeException failure, int failedAttempts);

	/**
	 * Requires an operator-initiated retry.
	 *
	 * @return explicit retry strategy
	 */
	static RetryPolicy explicit() {
		return (failure, attempts) -> Optional.empty();
	}

	/**
	 * Retries immediately a bounded number of times, suitable for transient local I/O.
	 *
	 * @param retries maximum automatic retries
	 * @return bounded immediate strategy
	 */
	static RetryPolicy immediate(int retries) {
		if (retries < 0) {
			throw new IllegalArgumentException("Retry count must not be negative.");
		}
		return (failure, attempts) -> attempts <= retries ? Optional.of(Duration.ZERO) : Optional.empty();
	}
}
