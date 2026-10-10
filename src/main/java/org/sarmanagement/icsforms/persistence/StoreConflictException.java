package org.sarmanagement.icsforms.persistence;

/** Indicates that another writer has changed an incident since it was loaded. */
public class StoreConflictException extends IllegalStateException {
	/**
	 * Creates a revision conflict.
	 *
	 * @param message
	 *            explanation of the conflict.
	 */
	public StoreConflictException(String message) {
		super(message);
	}
}
