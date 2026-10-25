/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

/**
 * Stable identifiers for navigable workspace destinations, independent of presentation labels.
 */
public enum WorkspaceId {
	SHARED("Shared"),
	ORG_CHART("Org Chart"),
	ICS_201("ICS 201"),
	ICS_202("ICS 202"),
	ICS_204("ICS 204"),
	ICS_205("ICS 205"),
	MEDICAL("Medical"),
	HUMAN_MEDICAL_PLAN("Medical Plan (ICS 206)"),
	CANINE_MEDICAL_PLAN("Canine Medical Plan (ICS 206CANINE, adapted)"),
	SAFETY("Safety"),
	SAFETY_OVERVIEW("Safety Overview"),
	SAR_TASKS("SAR Tasks"),
	CLUE_LOG("Clue Log"),
	OPEN_ACTION_TRACKER("Open Action Tracker"),
	TCARDS("T-Cards"),
	ACTIVITY_LOG("Activity Logs");

	private final String displayLabel;

	/**
	 * Creates a stable workspace identifier with its current presentation label.
	 *
	 * @param displayLabel user-facing workspace label.
	 */
	WorkspaceId(String displayLabel) {
		this.displayLabel = displayLabel;
	}

	/**
	 * Returns the current presentation label for menus and tabs.
	 *
	 * @return workspace display label.
	 */
	public String displayLabel() {
		return displayLabel;
	}
}
