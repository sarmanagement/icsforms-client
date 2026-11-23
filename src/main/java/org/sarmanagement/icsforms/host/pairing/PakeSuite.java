/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.host.pairing;

/**
 * A balanced password-authenticated key exchange that can be offered during pairing. Implementations delegate to a
 * vetted library; the protocol only depends on this interface and the suite identifier.
 */
public interface PakeSuite {
	/** The party that begins pairing and holds the device key. */
	enum Role {
		/** The device joining the incident. */
		CLIENT,
		/** The host operator's instance. */
		SERVER
	}

	/**
	 * Returns the identifier carried on the wire, for example {@code jpake-ff-3072}.
	 *
	 * @return stable suite identifier
	 */
	String id();

	/**
	 * Starts an exchange for one party.
	 *
	 * @param role which party this is
	 * @param password normalized pairing code; the suite must not retain it after the exchange completes
	 * @return a fresh single-use exchange
	 */
	PakeExchange start(Role role, char[] password);
}
