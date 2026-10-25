/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.model;

/**
 * Explicit operator assessment of an incident objective.
 */
public enum ObjectiveAssessment {
	NOT_ASSESSED("Not assessed"),
	ONGOING("Ongoing"),
	ACHIEVED("Achieved"),
	NO_LONGER_APPLICABLE("No longer applicable");

	private final String displayLabel;

	/**
	 * Creates an assessment value.
	 *
	 * @param displayLabel operator-facing label.
	 */
	ObjectiveAssessment(String displayLabel) {
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
