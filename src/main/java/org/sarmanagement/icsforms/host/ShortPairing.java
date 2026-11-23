/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.host;

import org.sarmanagement.icsforms.host.pairing.ChannelSuite;
import org.sarmanagement.icsforms.host.pairing.PairingCode;
import org.sarmanagement.icsforms.host.pairing.PakeExchange;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The host's single open short-code pairing window. It holds the code in memory only, expires, burns after too
 * many failed confirmations, and closes on first successful admission. At most one window is open at a time, like
 * a device's pairing mode.
 */
final class ShortPairing {
	/** Failed confirmations that burn the window. */
	static final int MAX_FAILURES = 3;
	private static final int MAX_SESSIONS = 8;
	private static final Duration SESSION_LIFETIME = Duration.ofSeconds(60);
	private final Clock clock;
	private final Map<String, Session> sessions = new HashMap<>();
	private String code;
	private Instant expiresAt;
	private int failures;

	/**
	 * One in-flight exchange.
	 *
	 * @param id session identifier returned to the device
	 * @param exchange host side of the PAKE
	 * @param channelSuite negotiated channel suite
	 * @param suiteId negotiated PAKE suite identifier
	 * @param transcript messages so far: suite ids, client round 1, host rounds 1 and 2
	 * @param expiresAt session expiry
	 */
	record Session(String id, PakeExchange exchange, ChannelSuite channelSuite, String suiteId,
			byte[][] transcript, Instant expiresAt) {
	}

	/**
	 * Creates a closed window holder.
	 *
	 * @param clock clock for expiry
	 */
	ShortPairing(Clock clock) {
		this.clock = clock;
	}

	/**
	 * Opens a window with a fresh code, replacing any open one.
	 *
	 * @param lifetime how long the window stays open
	 * @return normalized code and its expiry
	 */
	synchronized Offer open(Duration lifetime) {
		code = PairingCode.generate();
		expiresAt = clock.instant().plus(lifetime);
		failures = 0;
		sessions.clear();
		return new Offer(code, expiresAt);
	}

	/**
	 * A newly opened window.
	 *
	 * @param code normalized code
	 * @param expiresAt window expiry
	 */
	record Offer(String code, Instant expiresAt) {
		/**
		 * Redacts the code from diagnostic output.
		 *
		 * @return text that omits the code
		 */
		@Override
		public String toString() {
			return "Offer[expires " + expiresAt + "]";
		}
	}

	/** Closes the window and forgets the code. */
	synchronized void close() {
		code = null;
		expiresAt = null;
		sessions.clear();
	}

	/**
	 * Reports whether a window is open and unexpired.
	 *
	 * @return true when devices may pair with the code
	 */
	synchronized boolean isOpen() {
		if (code != null && !clock.instant().isBefore(expiresAt)) {
			close();
		}
		return code != null;
	}

	/**
	 * Returns when the open window expires.
	 *
	 * @return expiry, or null when closed
	 */
	synchronized Instant expiresAt() {
		return isOpen() ? expiresAt : null;
	}

	/**
	 * Returns the code for the PAKE; the caller must not retain it.
	 *
	 * @return code characters, or null when closed
	 */
	synchronized char[] codeChars() {
		return isOpen() ? code.toCharArray() : null;
	}

	/**
	 * Registers an in-flight exchange.
	 *
	 * @param exchange host side of the PAKE
	 * @param channelSuite negotiated channel suite
	 * @param suiteId negotiated PAKE suite
	 * @param transcript messages so far
	 * @return the session, or null when the window is closed or too busy
	 */
	synchronized Session begin(PakeExchange exchange, ChannelSuite channelSuite, String suiteId, byte[][] transcript) {
		if (!isOpen()) {
			return null;
		}
		Instant now = clock.instant();
		sessions.values().removeIf(session -> !now.isBefore(session.expiresAt()));
		if (sessions.size() >= MAX_SESSIONS) {
			return null;
		}
		Session session = new Session(UUID.randomUUID().toString(), exchange, channelSuite, suiteId, transcript,
				now.plus(SESSION_LIFETIME));
		sessions.put(session.id(), session);
		return session;
	}

	/**
	 * Takes a session out for its single finishing attempt.
	 *
	 * @param id session identifier
	 * @return the session, or null when unknown, expired or the window closed
	 */
	synchronized Session take(String id) {
		if (!isOpen()) {
			return null;
		}
		Session session = sessions.remove(id);
		return session == null || !clock.instant().isBefore(session.expiresAt()) ? null : session;
	}

	/** Records a failed confirmation and burns the window after {@link #MAX_FAILURES}. */
	synchronized void failed() {
		failures++;
		if (failures >= MAX_FAILURES) {
			close();
		}
	}
}
