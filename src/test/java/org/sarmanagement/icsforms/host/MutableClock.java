/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.host;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * A test clock that only moves when told to.
 */
final class MutableClock extends Clock {
	private volatile Instant now;

	/**
	 * Creates a clock at the current real time.
	 */
	MutableClock() {
		this.now = Instant.now();
	}

	/**
	 * Moves the clock forward.
	 *
	 * @param amount time to add
	 */
	void advance(Duration amount) {
		now = now.plus(amount);
	}

	/** {@inheritDoc} */
	@Override
	public ZoneId getZone() {
		return ZoneOffset.UTC;
	}

	/** {@inheritDoc} */
	@Override
	public Clock withZone(ZoneId zone) {
		return this;
	}

	/** {@inheritDoc} */
	@Override
	public Instant instant() {
		return now;
	}
}
