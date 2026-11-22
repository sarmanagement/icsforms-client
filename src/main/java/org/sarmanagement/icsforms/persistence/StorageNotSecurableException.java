/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.persistence;

/**
 * The file system holding disconnected storage cannot restrict access to its owner, for example a FAT or exFAT
 * volume. Callers may continue without disconnected storage but must not write an unprotected journal.
 */
public class StorageNotSecurableException extends StorePersistenceException {
	/**
	 * Creates the failure.
	 *
	 * @param message explanation suitable for display to the operator
	 * @param cause underlying cause, or null
	 */
	public StorageNotSecurableException(String message, Throwable cause) {
		super(message, cause);
	}
}
