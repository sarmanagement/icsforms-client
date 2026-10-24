/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import java.util.Objects;

/**
 * Typed target for opening a base workspace, nested medical plan, or individual activity log.
 *
 * @param workspaceId stable workspace identifier.
 * @param instanceId activity-log instance identifier, blank for other destinations.
 */
public record WorkspaceDestination(WorkspaceId workspaceId, String instanceId) {
	/**
	 * Validates that per-instance identifiers are used only for activity-log destinations.
	 *
	 * @param workspaceId stable workspace identifier.
	 * @param instanceId activity-log instance identifier, blank for other destinations.
	 */
	public WorkspaceDestination {
		Objects.requireNonNull(workspaceId, "workspaceId");
		instanceId = instanceId == null ? "" : instanceId;
		if ((workspaceId == WorkspaceId.ACTIVITY_LOG) == instanceId.isBlank()) {
			throw new IllegalArgumentException("Activity-log destinations require an instance identifier");
		}
	}

	/**
	 * Creates a destination for a base workspace or the shared Medical workspace.
	 *
	 * @param workspaceId base workspace identifier.
	 * @return destination for the selected workspace.
	 */
	public static WorkspaceDestination of(WorkspaceId workspaceId) {
		if (workspaceId == WorkspaceId.ACTIVITY_LOG || workspaceId == WorkspaceId.HUMAN_MEDICAL_PLAN
				|| workspaceId == WorkspaceId.CANINE_MEDICAL_PLAN) {
			throw new IllegalArgumentException("Use the specialized factory for this workspace destination");
		}
		return new WorkspaceDestination(workspaceId, "");
	}

	/**
	 * Creates a destination for the nested human medical plan.
	 *
	 * @return human medical plan destination.
	 */
	public static WorkspaceDestination humanMedicalPlan() {
		return new WorkspaceDestination(WorkspaceId.HUMAN_MEDICAL_PLAN, "");
	}

	/**
	 * Creates a destination for the nested canine medical plan.
	 *
	 * @return canine medical plan destination.
	 */
	public static WorkspaceDestination canineMedicalPlan() {
		return new WorkspaceDestination(WorkspaceId.CANINE_MEDICAL_PLAN, "");
	}

	/**
	 * Creates a destination for one specific activity-log editor instance.
	 *
	 * @param instanceId runtime identifier for the activity-log instance.
	 * @return activity-log destination.
	 */
	public static WorkspaceDestination activityLog(String instanceId) {
		return new WorkspaceDestination(WorkspaceId.ACTIVITY_LOG, instanceId);
	}
}
