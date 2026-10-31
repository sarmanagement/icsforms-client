/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.model;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

/**
 * Provides injectable UTC timestamps for incident data.
 */
public final class UtcTimestamp {
	private static volatile Clock clock = Clock.systemUTC();

	/**
	 * Prevents construction of this timestamp utility.
	 */
	private UtcTimestamp() {
	}

	/**
	 * Returns the current instant from the configured clock.
	 *
	 * @return current instant.
	 */
	public static Instant nowInstant() {
		return clock.instant();
	}

	/**
	 * Returns the current UTC time as a local date/time for legacy model fields.
	 *
	 * @return current UTC date/time.
	 */
	public static LocalDateTime now() {
		return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
	}

	/**
	 * Replaces the clock used to generate timestamps.
	 *
	 * @param replacement clock supplying instants.
	 */
	public static void setClock(Clock replacement) {
		clock = Objects.requireNonNull(replacement);
	}

	/**
	 * Restores timestamp generation to the system UTC clock.
	 */
	public static void resetClock() {
		clock = Clock.systemUTC();
	}
}
