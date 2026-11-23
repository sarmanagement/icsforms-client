/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.host.pairing;

import java.security.GeneralSecurityException;

/**
 * One party's side of a password-authenticated key exchange, expressed as opaque byte messages so a suite owns its
 * own encoding and number of rounds. Suites with a single round return an empty array from {@link #round2} and
 * ignore the argument of {@link #complete}. An exchange is single use and not thread safe.
 */
public interface PakeExchange {
	/**
	 * Produces this party's first message.
	 *
	 * @return first message to send to the peer
	 */
	byte[] round1();

	/**
	 * Consumes the peer's first message and produces this party's second message.
	 *
	 * @param peerRound1 first message received from the peer
	 * @return second message to send, or an empty array when the suite has one round
	 * @throws GeneralSecurityException when the peer's message is malformed or fails validation
	 */
	byte[] round2(byte[] peerRound1) throws GeneralSecurityException;

	/**
	 * Consumes the peer's second message and derives the shared key. The key is only equal on both sides when both
	 * used the same password, which the caller proves with key confirmation.
	 *
	 * @param peerRound2 second message received from the peer
	 * @return shared secret key material
	 * @throws GeneralSecurityException when the peer's message is malformed or fails validation
	 */
	byte[] complete(byte[] peerRound2) throws GeneralSecurityException;
}
