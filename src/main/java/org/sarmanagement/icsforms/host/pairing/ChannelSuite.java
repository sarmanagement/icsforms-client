/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.host.pairing;

import java.security.GeneralSecurityException;

/**
 * A keyed channel suite: how a PAKE-derived secret becomes confirmation tags and protected payloads, bound to the
 * pairing transcript. Identified on the wire so it can be replaced independently of the PAKE.
 */
public interface ChannelSuite {
	/**
	 * Returns the identifier carried on the wire.
	 *
	 * @return stable suite identifier
	 */
	String id();

	/**
	 * Derives the channel keys.
	 *
	 * @param pakeKey key material produced by the PAKE
	 * @param transcript every pairing message in order, so the keys differ for any altered exchange
	 * @return an open channel
	 */
	PairingChannel open(byte[] pakeKey, byte[] transcript);

	/** Confirmation and payload protection under keys derived by a {@link ChannelSuite}. */
	interface PairingChannel {
		/**
		 * Computes a confirmation tag that binds a party's role and the host identity it observed.
		 *
		 * @param label party label such as {@code client} or {@code server}
		 * @param hostIdentity host authority fingerprint as observed by that party
		 * @return confirmation tag
		 */
		byte[] confirmation(String label, byte[] hostIdentity);

		/**
		 * Verifies a confirmation tag in constant time.
		 *
		 * @param label party label the tag claims
		 * @param hostIdentity host authority fingerprint as observed by the verifier
		 * @param tag received tag
		 * @return true when the tag is valid
		 */
		boolean verifyConfirmation(String label, byte[] hostIdentity, byte[] tag);

		/**
		 * Encrypts and authenticates a payload.
		 *
		 * @param purpose context string bound to the ciphertext
		 * @param plaintext payload
		 * @return protected payload including its nonce
		 * @throws GeneralSecurityException when protection fails
		 */
		byte[] seal(String purpose, byte[] plaintext) throws GeneralSecurityException;

		/**
		 * Decrypts and authenticates a payload.
		 *
		 * @param purpose context string it was sealed with
		 * @param sealed protected payload
		 * @return plaintext
		 * @throws GeneralSecurityException when authentication fails
		 */
		byte[] unseal(String purpose, byte[] sealed) throws GeneralSecurityException;
	}
}
