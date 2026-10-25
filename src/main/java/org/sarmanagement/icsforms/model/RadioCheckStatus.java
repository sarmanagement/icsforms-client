/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.model;

/**
 * Explicit operator-recorded result of a radio check for one channel.
 */
public enum RadioCheckStatus {
	/** No radio check result has been recorded. */
	NOT_RECORDED("Not recorded"),
	/** The radio check was successful. */
	SUCCESSFUL("Successful"),
	/** The radio check had partial or intermittent results. */
	PARTIAL("Partial / intermittent"),
	/** The radio check failed. */
	FAILED("Failed");

	private final String displayLabel;

	/**
	 * Creates a radio-check status.
	 *
	 * @param displayLabel operator-facing label.
	 */
	RadioCheckStatus(String displayLabel) {
		this.displayLabel = displayLabel;
	}

	/**
	 * Returns the operator-facing label.
	 *
	 * @return display label.
	 */
	public String displayLabel() {
		return displayLabel;
	}

	/**
	 * Returns the operator-facing label.
	 *
	 * @return display label.
	 */
	@Override
	public String toString() {
		return displayLabel;
	}
}
