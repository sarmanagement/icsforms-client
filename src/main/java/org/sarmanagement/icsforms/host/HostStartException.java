/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.host;

/**
 * Hosting could not start. A failed start leaves the incident usable and no partially shared session behind.
 */
public class HostStartException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	/**
	 * Why hosting could not start.
	 */
	public enum Reason {
		/** A configured port is already bound. */
		PORT_IN_USE,
		/** Host-owned storage or the incident's record storage failed. */
		STORAGE_FAILURE,
		/** Exclusive ownership of the incident's files could not be established. */
		NOT_EXCLUSIVE,
		/** Editors could not be flushed before sharing. */
		EDITOR_FLUSH_FAILED,
		/** The configuration is invalid. */
		INVALID_CONFIGURATION,
		/** The incident to share was not found. */
		INCIDENT_NOT_FOUND,
		/** Another failure prevented startup. */
		OTHER
	}

	private final Reason reason;

	/**
	 * Creates the exception.
	 *
	 * @param reason failure category
	 * @param message operator-readable explanation that never contains secrets
	 * @param cause underlying failure, or null
	 */
	public HostStartException(Reason reason, String message, Throwable cause) {
		super(message, cause);
		this.reason = reason;
	}

	/**
	 * Returns the failure category.
	 *
	 * @return reason
	 */
	public Reason reason() {
		return reason;
	}
}
