/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.host.pairing;

import java.security.GeneralSecurityException;
import java.util.Arrays;

/**
 * A key agreement whose two ends are tied to the people at them by a short number. The device commits to its key
 * share before it sees the host's, the host answers with its share, and the device then opens the commitment.
 * Both ends derive the same key and the same short number only if neither saw a different exchange. The device
 * displays the number and the host operator types it in, so an attacker who relays or alters the exchange gets a
 * matching number only by chance, once per attempt.
 *
 * <p>Suites are identified on the wire by {@link #id()} and registered in {@link PairingSuites}, so the
 * agreement can be replaced without changing the pairing protocol.
 */
public interface SasSuite {
	/**
	 * Returns the wire identifier of this suite.
	 *
	 * @return stable identifier
	 */
	String id();

	/**
	 * Starts the device side.
	 *
	 * @return a new initiator with a fresh key share
	 * @throws GeneralSecurityException when key generation fails
	 */
	Initiator initiator() throws GeneralSecurityException;

	/**
	 * Starts the host side.
	 *
	 * @return a new responder
	 */
	Responder responder();

	/**
	 * What both ends derive.
	 *
	 * @param code normalized short number, see {@link PairingCode}
	 * @param secret shared secret for the keyed channel
	 * @param transcript bytes both ends bound into the derivation
	 */
	record Outcome(String code, byte[] secret, byte[] transcript) {
		/**
		 * Redacts the derived values from diagnostic output.
		 *
		 * @return text that omits the code and secret
		 */
		@Override
		public String toString() {
			return "Outcome[redacted]";
		}

		/**
		 * Compares by value.
		 *
		 * @param other other object
		 * @return true when all components match
		 */
		@Override
		public boolean equals(Object other) {
			return other instanceof Outcome that && code.equals(that.code) && Arrays.equals(secret, that.secret)
					&& Arrays.equals(transcript, that.transcript);
		}

		/**
		 * Hashes by value.
		 *
		 * @return hash code
		 */
		@Override
		public int hashCode() {
			return 31 * (31 * code.hashCode() + Arrays.hashCode(secret)) + Arrays.hashCode(transcript);
		}
	}

	/** The device side of the exchange. */
	interface Initiator {
		/**
		 * Returns the commitment to send first.
		 *
		 * @return commitment bytes
		 */
		byte[] commitment();

		/**
		 * Accepts the host's key share and returns the opening that reveals this device's share.
		 *
		 * @param responderShare host's key share
		 * @return opening to send to the host
		 * @throws GeneralSecurityException when the share is invalid
		 */
		byte[] accept(byte[] responderShare) throws GeneralSecurityException;

		/**
		 * Derives the short number and shared secret, bound to the host identity this device observed.
		 *
		 * @param hostIdentity host authority fingerprint as this device saw it
		 * @return the outcome
		 */
		Outcome outcome(byte[] hostIdentity);
	}

	/** The host side of the exchange. */
	interface Responder {
		/**
		 * Receives the device's commitment and returns the host's key share.
		 *
		 * @param commitment device commitment
		 * @return host key share
		 * @throws GeneralSecurityException when key generation fails
		 */
		byte[] receiveCommitment(byte[] commitment) throws GeneralSecurityException;

		/**
		 * Verifies the opening against the commitment and derives the outcome.
		 *
		 * @param opening device opening
		 * @param hostIdentity host's own authority fingerprint
		 * @return the outcome
		 * @throws GeneralSecurityException when the opening does not match the commitment or is invalid
		 */
		Outcome open(byte[] opening, byte[] hostIdentity) throws GeneralSecurityException;
	}
}
