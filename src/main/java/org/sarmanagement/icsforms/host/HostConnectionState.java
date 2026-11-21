/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.host;

/**
 * What a device can tell about its host using only the pre-authentication status signal.
 */
public enum HostConnectionState {
	/** The host is running and the credential is active. */
	CONNECTED,
	/** The credential has expired. */
	CREDENTIAL_EXPIRED,
	/** The credential was revoked. */
	CREDENTIAL_REVOKED,
	/** The host does not recognise the credential. */
	CREDENTIAL_UNKNOWN,
	/** The host is reachable but not serving. */
	HOST_UNAVAILABLE,
	/** The host cannot be reached. */
	HOST_UNREACHABLE,
	/** The host presented a different identity from the pinned one. */
	HOST_IDENTITY_CHANGED
}
