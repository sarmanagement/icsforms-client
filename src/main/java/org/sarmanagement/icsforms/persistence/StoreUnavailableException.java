/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.persistence;

/** The store cannot be used, including after its resources have been closed. */
public class StoreUnavailableException extends IllegalStateException {
	/**
	 * Creates an availability failure.
	 *
	 * @param message explanation
	 */
	public StoreUnavailableException(String message) {
		super(message);
	}

	/**
	 * Creates an availability failure with a cause.
	 *
	 * @param message explanation
	 * @param cause availability cause
	 */
	public StoreUnavailableException(String message, Throwable cause) {
		super(message, cause);
	}
}
