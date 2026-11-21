/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.host;

/**
 * Host-owned storage (key material or device registry) cannot be read or written.
 */
public class HostStorageException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	/**
	 * Creates the exception.
	 *
	 * @param message failure description that never contains secrets
	 * @param cause underlying failure
	 */
	public HostStorageException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * Creates the exception without a cause.
	 *
	 * @param message failure description that never contains secrets
	 */
	public HostStorageException(String message) {
		super(message);
	}
}
