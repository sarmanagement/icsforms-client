/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.persistence;

/** Creation attempted to reuse an existing or retired incident identity. */
public class DuplicateIncidentException extends IllegalStateException {
	/**
	 * Creates an identity collision failure.
	 *
	 * @param message explanation
	 */
	public DuplicateIncidentException(String message) {
		super(message);
	}
}
