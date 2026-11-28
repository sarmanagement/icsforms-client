/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.host;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.sarmanagement.icsforms.host.pairing.ChannelSuite;
import org.sarmanagement.icsforms.host.pairing.SasSuite;

/**
 * The host's single in-person pairing window. While it is open one device at a time may start an exchange; the
 * device shows a short number and the host operator types it in. The window expires, burns after too many wrong
 * entries or failed confirmations, and closes on the first successful admission. Nothing here is persisted, so a
 * restart discards it.
 */
final class ShortPairing {
	/** Failed entries or confirmations that burn the window. */
	static final int MAX_FAILURES = 3;
	private static final Duration SESSION_LIFETIME = Duration.ofMinutes(2);
	private final Clock clock;
	private Instant expiresAt;
	private int failures;
	private Session current;

	/** Where an exchange stands. */
	enum Stage {
		/** The device sent its commitment and the host answered. */
		BEGUN,
		/** The exchange is complete and the host awaits the operator's entry of the device's number. */
		AWAITING_ENTRY,
		/** The operator typed the right number. */
		CONFIRMED
	}

	/** The result of the operator typing a number. */
	enum Entry {
		/** No device is waiting for an entry. */
		NONE,
		/** The number matched. */
		CONFIRMED,
		/** The number did not match; the window is still open. */
		MISMATCH,
		/** The number did not match and the window is now closed. */
		BURNED
	}

	/** One in-flight exchange. */
	static final class Session {
		private final String id = UUID.randomUUID().toString();
		private final SasSuite.Responder responder;
		private final ChannelSuite channelSuite;
		private final String deviceName;
		private final Instant expiresAt;
		private Stage stage = Stage.BEGUN;
		private SasSuite.Outcome outcome;

		/**
		 * Creates a session.
		 *
		 * @param responder host side of the key agreement
		 * @param channelSuite negotiated channel suite
		 * @param deviceName label the device gave itself; unauthenticated until confirmed
		 * @param expiresAt session expiry
		 */
		private Session(SasSuite.Responder responder, ChannelSuite channelSuite, String deviceName,
				Instant expiresAt) {
			this.responder = responder;
			this.channelSuite = channelSuite;
			this.deviceName = deviceName;
			this.expiresAt = expiresAt;
		}

		/**
		 * Returns the session identifier.
		 *
		 * @return identifier given to the device
		 */
		String id() {
			return id;
		}

		/**
		 * Returns the host side of the key agreement.
		 *
		 * @return responder
		 */
		SasSuite.Responder responder() {
			return responder;
		}

		/**
		 * Returns the negotiated channel suite.
		 *
		 * @return channel suite
		 */
		ChannelSuite channelSuite() {
			return channelSuite;
		}

		/**
		 * Returns the derived outcome.
		 *
		 * @return outcome, or null before the exchange is complete
		 */
		SasSuite.Outcome outcome() {
			return outcome;
		}
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
	 * Opens a window, replacing any open one.
	 *
	 * @param lifetime how long the window stays open
	 * @return when the window expires
	 */
	synchronized Instant open(Duration lifetime) {
		expiresAt = clock.instant().plus(lifetime);
		failures = 0;
		current = null;
		return expiresAt;
	}

	/** Closes the window and drops any exchange. */
	synchronized void close() {
		expiresAt = null;
		current = null;
	}

	/**
	 * Reports whether a window is open and unexpired.
	 *
	 * @return true when a device may start an exchange
	 */
	synchronized boolean isOpen() {
		if (expiresAt != null && !clock.instant().isBefore(expiresAt)) {
			close();
		}
		return expiresAt != null;
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
	 * Registers a new exchange. Only one runs at a time, so the operator always knows which device a number
	 * belongs to.
	 *
	 * @param responder host side of the key agreement
	 * @param channelSuite negotiated channel suite
	 * @param deviceName label the device gave itself
	 * @return the session, or null when the window is closed or another exchange is in progress
	 */
	synchronized Session begin(SasSuite.Responder responder, ChannelSuite channelSuite, String deviceName) {
		if (!isOpen() || live(current) != null) {
			return null;
		}
		current = new Session(responder, channelSuite, deviceName, clock.instant().plus(SESSION_LIFETIME));
		return current;
	}

	/**
	 * Tells whether an exchange is in progress.
	 *
	 * @return true when a live exchange is registered
	 */
	synchronized boolean busy() {
		return isOpen() && live(current) != null;
	}

	/**
	 * Finds a live session.
	 *
	 * @param id session identifier
	 * @return the session, or null when unknown, expired or the window closed
	 */
	synchronized Session find(String id) {
		return isOpen() && live(current) != null && current.id.equals(id) ? current : null;
	}

	/**
	 * Returns a session unless it has expired.
	 *
	 * @param session session or null
	 * @return the session, or null when absent or expired
	 */
	private Session live(Session session) {
		if (session != null && !clock.instant().isBefore(session.expiresAt)) {
			current = null;
			return null;
		}
		return session;
	}

	/**
	 * Records the completed key agreement and starts waiting for the operator.
	 *
	 * @param session session
	 * @param outcome derived outcome
	 */
	synchronized void opened(Session session, SasSuite.Outcome outcome) {
		session.outcome = outcome;
		session.stage = Stage.AWAITING_ENTRY;
	}

	/**
	 * Returns the stage of a session.
	 *
	 * @param session session
	 * @return its stage
	 */
	synchronized Stage stage(Session session) {
		return session.stage;
	}

	/**
	 * Returns the label of the device awaiting entry.
	 *
	 * @return the device's self-chosen label, or null when none is awaiting entry
	 */
	synchronized String awaitingDevice() {
		Session session = isOpen() ? live(current) : null;
		return session != null && session.stage == Stage.AWAITING_ENTRY ? session.deviceName : null;
	}

	/**
	 * Checks the number the operator typed against the waiting device's number.
	 *
	 * @param normalized normalized number
	 * @return what happened; a wrong number counts against the window
	 */
	synchronized Entry enter(String normalized) {
		Session session = isOpen() ? live(current) : null;
		if (session == null || session.stage != Stage.AWAITING_ENTRY) {
			return Entry.NONE;
		}
		if (MessageDigest.isEqual(normalized.getBytes(StandardCharsets.UTF_8),
				session.outcome.code().getBytes(StandardCharsets.UTF_8))) {
			session.stage = Stage.CONFIRMED;
			return Entry.CONFIRMED;
		}
		return failedEntry() ? Entry.BURNED : Entry.MISMATCH;
	}

	/**
	 * Takes a confirmed session for its single finishing attempt.
	 *
	 * @param id session identifier
	 * @return the session, or null when unknown, expired, not confirmed or the window closed
	 */
	synchronized Session take(String id) {
		Session session = find(id);
		if (session == null || session.stage != Stage.CONFIRMED) {
			return null;
		}
		current = null;
		return session;
	}

	/** Drops the exchange in progress, if any, without counting a failure. */
	synchronized void cancel() {
		current = null;
	}

	/** Records a failed confirmation and burns the window after {@link #MAX_FAILURES}. */
	synchronized void failed() {
		failedEntry();
	}

	/**
	 * Counts a failure and drops the exchange.
	 *
	 * @return true when the window is now burned
	 */
	private boolean failedEntry() {
		failures++;
		current = null;
		if (failures >= MAX_FAILURES) {
			close();
			return true;
		}
		return false;
	}
}
