/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.persistence;

/**
 * A submission was committed durably, but the host could not finish projecting it into its whole-document file. The
 * operation took effect; retrying the identical save is safe and returns the original receipt.
 */
public class StoreCommittedException extends StorePersistenceException {
	/** Operator-facing explanation shared by the service, the protocol and the client. */
	public static final String MESSAGE = "The change was committed on the host, but the host could not finish "
			+ "writing its incident file. Nothing was lost; retrying the same save is safe.";

	/**
	 * Creates the exception.
	 *
	 * @param cause projection failure
	 */
	public StoreCommittedException(Throwable cause) {
		super(MESSAGE, cause);
	}
}
