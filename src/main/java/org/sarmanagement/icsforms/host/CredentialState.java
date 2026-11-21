/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.host;

/**
 * Lifecycle state of a device credential as reported by the host.
 */
public enum CredentialState {
	/** The credential is admitted and unexpired. */
	ACTIVE,
	/** The credential lifetime has ended. */
	EXPIRED,
	/** The host operator revoked the credential. */
	REVOKED,
	/** The host has no record of the credential. */
	UNKNOWN
}
