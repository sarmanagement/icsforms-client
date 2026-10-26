/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.model;

/**
 * Independent plan-content areas that support explicit review assertions.
 */
public enum PlanReviewArea {
	SAFETY("Safety"),
	HUMAN_MEDICAL("Human medical"),
	CANINE_MEDICAL("Canine medical"),
	COMMUNICATIONS("Communications");

	private final String displayLabel;

	/**
	 * Creates a review area.
	 *
	 * @param displayLabel operator-facing area label.
	 */
	PlanReviewArea(String displayLabel) {
		this.displayLabel = displayLabel;
	}

	/** @return operator-facing area label. */
	public String displayLabel() {
		return displayLabel;
	}
}
