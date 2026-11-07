/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.persistence;

/** The supplied document or operation arguments cannot be persisted. */
public class StoreValidationException extends IllegalArgumentException {
	/**
	 * Creates a validation failure.
	 *
	 * @param message explanation
	 */
	public StoreValidationException(String message) {
		super(message);
	}

	/**
	 * Creates a validation failure retaining its cause.
	 *
	 * @param message explanation
	 * @param cause validation cause
	 */
	public StoreValidationException(String message, Throwable cause) {
		super(message, cause);
	}
}
