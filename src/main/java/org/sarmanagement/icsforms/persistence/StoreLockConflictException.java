/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.persistence;

/** An advisory editor lock prevented an operation, not a revision mismatch. */
public class StoreLockConflictException extends StoreConflictException {
	/**
	 * Creates an editing lock conflict.
	 *
	 * @param message explanation
	 */
	public StoreLockConflictException(String message) {
		super(message, null, null);
	}
}
