/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.persistence;

/**
 * The remote host cannot be used for a reason that waiting will not fix, or that it announced itself. It is a
 * {@link StoreUnavailableException}, so persistence keeps unsaved work and reports the store as unavailable.
 */
public class RemoteAccessException extends StoreUnavailableException {
	private static final long serialVersionUID = 1L;

	/**
	 * Why the host is not usable.
	 */
	public enum Reason {
		/** The host announced that it is stopping. */
		HOST_STOPPING,
		/** The device credential has expired. */
		CREDENTIAL_EXPIRED,
		/** The device credential was revoked by the host. */
		CREDENTIAL_REVOKED,
		/** The host does not recognise the credential. */
		CREDENTIAL_UNKNOWN,
		/** The host presented a different identity than the pinned one. */
		IDENTITY_CHANGED,
		/** The host and client versions are incompatible. */
		INCOMPATIBLE
	}

	private final Reason reason;

	/**
	 * Creates the exception.
	 *
	 * @param reason classified reason
	 * @param message operator-facing message without secrets or file paths
	 * @param cause transport failure, or null
	 */
	public RemoteAccessException(Reason reason, String message, Throwable cause) {
		super(message, cause);
		this.reason = reason;
	}

	/**
	 * Returns the classified reason.
	 *
	 * @return reason
	 */
	public Reason reason() {
		return reason;
	}
}
