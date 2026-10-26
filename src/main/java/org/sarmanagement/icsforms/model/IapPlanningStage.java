/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.model;

/**
 * Explicit operator-recorded position in the incident action planning cycle.
 */
public enum IapPlanningStage {
	/** No planning-cycle stage has been recorded. */
	NOT_RECORDED("Not recorded"),
	/** Initial response and incident briefing. */
	INITIAL_RESPONSE("Initial response"),
	/** Objectives are being developed or revised. */
	OBJECTIVES_DEVELOPMENT("Objectives development"),
	/** Command and general staff meeting. */
	COMMAND_GENERAL_STAFF_MEETING("Command/general staff meeting"),
	/** Tactics meeting. */
	TACTICS_MEETING("Tactics meeting"),
	/** Planning meeting. */
	PLANNING_MEETING("Planning meeting"),
	/** IAP preparation and command approval. */
	IAP_PREPARATION_APPROVAL("IAP preparation / approval"),
	/** Operations briefing. */
	OPERATIONS_BRIEFING("Operations briefing"),
	/** The recorded operational period is active. */
	OPERATIONAL_PERIOD_ACTIVE("Operational period active");

	private final String displayLabel;

	/**
	 * Creates a planning-cycle stage.
	 *
	 * @param displayLabel operator-facing label.
	 */
	IapPlanningStage(String displayLabel) {
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
