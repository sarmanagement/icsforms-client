/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.host;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Sliding-window limiter that bounds how many events each key may cause in a period.
 */
final class RateLimiter {
	private static final int MAX_KEYS = 4096;
	private final int limit;
	private final Duration window;
	private final Clock clock;
	private final Map<String, Deque<Instant>> events = new HashMap<>();

	/**
	 * Creates a limiter.
	 *
	 * @param limit maximum events per key within the window
	 * @param window sliding window length
	 * @param clock time source
	 */
	RateLimiter(int limit, Duration window, Clock clock) {
		this.limit = limit;
		this.window = Objects.requireNonNull(window, "window");
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	/**
	 * Records an attempt when the key is under its limit.
	 *
	 * @param key caller identity, for example a remote address
	 * @return true when the attempt is allowed and was counted; false when the limit is exceeded
	 */
	synchronized boolean tryAcquire(String key) {
		Instant now = clock.instant();
		Instant cutoff = now.minus(window);
		if (events.size() >= MAX_KEYS && !events.containsKey(key)) {
			events.values().removeIf(queue -> queue.isEmpty() || !queue.peekLast().isAfter(cutoff));
			if (events.size() >= MAX_KEYS) {
				return false;
			}
		}
		Deque<Instant> queue = events.computeIfAbsent(key, ignored -> new ArrayDeque<>());
		while (!queue.isEmpty() && !queue.peekFirst().isAfter(cutoff)) {
			queue.removeFirst();
		}
		if (queue.size() >= limit) {
			return false;
		}
		queue.addLast(now);
		return true;
	}
}
