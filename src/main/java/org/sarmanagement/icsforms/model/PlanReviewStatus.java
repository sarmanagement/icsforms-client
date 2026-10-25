/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.model;

/**
 * Current state of an explicit plan review assertion.
 */
public enum PlanReviewStatus {
	NOT_YET_REVIEWED("Not yet reviewed"),
	REVIEWED_APPROVED("Reviewed-approved"),
	CHANGED_SINCE_REVIEW("Changed since review");

	private final String displayLabel;

	/**
	 * Creates a status value.
	 *
	 * @param displayLabel operator-facing label.
	 */
	PlanReviewStatus(String displayLabel) {
		this.displayLabel = displayLabel;
	}

	/** @return operator-facing label. */
	public String displayLabel() {
		return displayLabel;
	}
}
