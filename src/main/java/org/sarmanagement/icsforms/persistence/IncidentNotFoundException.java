/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.persistence;

/** The incident does not exist or has been deleted. */
public class IncidentNotFoundException extends IllegalArgumentException {
	/**
	 * Creates a missing incident failure.
	 *
	 * @param message explanation
	 */
	public IncidentNotFoundException(String message) {
		super(message);
	}
}
