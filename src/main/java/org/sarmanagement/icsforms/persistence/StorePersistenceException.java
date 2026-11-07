/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.persistence;

/** A store operation failed to read or persist content, distinct from availability. */
public class StorePersistenceException extends IllegalStateException {
	/**
	 * Creates a persistence failure.
	 *
	 * @param message explanation
	 * @param cause persistence cause
	 */
	public StorePersistenceException(String message, Throwable cause) {
		super(message, cause);
	}
}
