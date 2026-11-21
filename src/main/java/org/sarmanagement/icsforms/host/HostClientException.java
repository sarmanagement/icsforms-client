/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.host;

/**
 * A host request failed. The {@link Kind} lets a client tell an expired or revoked credential from an unreachable
 * host and from a changed host identity.
 */
public class HostClientException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	/**
	 * Failure category.
	 */
	public enum Kind {
		/** The host could not be reached. */
		UNREACHABLE,
		/** The host presented a certificate that does not match the pinned identity. */
		IDENTITY_CHANGED,
		/** The device credential has expired. */
		CREDENTIAL_EXPIRED,
		/** The device credential was revoked. */
		CREDENTIAL_REVOKED,
		/** The host does not know the device credential. */
		UNAUTHORIZED,
		/** The host is stopping or not serving. */
		HOST_UNAVAILABLE,
		/** The client or API version is not supported. */
		INCOMPATIBLE,
		/** The invitation was refused. */
		INVITATION_REJECTED,
		/** The host rate-limited the request. */
		RATE_LIMITED,
		/** The host returned another structured error. */
		REJECTED
	}

	private final Kind kind;
	private final int status;
	private final String code;

	/**
	 * Creates the exception.
	 *
	 * @param kind failure category
	 * @param status HTTP status, or 0 when no response was received
	 * @param code structured error code, or null
	 * @param message explanation that never contains secrets
	 * @param cause underlying failure, or null
	 */
	public HostClientException(Kind kind, int status, String code, String message, Throwable cause) {
		super(message, cause);
		this.kind = kind;
		this.status = status;
		this.code = code;
	}

	/**
	 * Returns the failure category.
	 *
	 * @return kind
	 */
	public Kind kind() {
		return kind;
	}

	/**
	 * Returns the HTTP status.
	 *
	 * @return status, or 0 when none
	 */
	public int status() {
		return status;
	}

	/**
	 * Returns the structured error code.
	 *
	 * @return code, or null
	 */
	public String code() {
		return code;
	}
}
