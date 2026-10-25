/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.model;

/**
 * State of the Incident Commander's objective approval assertion.
 */
public enum ObjectiveApprovalStatus {
	/** No objective approval is recorded. */
	NOT_APPROVED("Not approved"),
	/** The recorded approval matches the current ordered objective content. */
	APPROVED_CURRENT("Approved — current"),
	/** Objective wording or order changed after approval. */
	CONTENT_CHANGED("Approval stale — objectives changed"),
	/** Legacy approval exists without a verifiable content snapshot. */
	LEGACY_UNVERIFIED("Legacy approval — content not verified");

	private final String displayLabel;

	/**
	 * Creates an approval state.
	 *
	 * @param displayLabel operator-facing label.
	 */
	ObjectiveApprovalStatus(String displayLabel) {
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
}
