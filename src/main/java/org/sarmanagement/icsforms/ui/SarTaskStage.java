/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.sarmanagement.icsforms.model.SarTaskAssignment;

import java.awt.Color;
import java.util.Locale;

/**
 * Board stage and colour of a SAR task, shared by the SAR task board and table, the Operations
 * task list and the Incident Command task board so every view colour-codes tasks identically.
 */
enum SarTaskStage {
	/** Task is planned and not yet assigned. */
	PLANNED("planned", new Color(173, 216, 230)),
	/** Task is assigned (enroute, on task or returning). */
	ASSIGNED("assigned", new Color(255, 200, 100)),
	/** Task has returned and has not been debriefed. */
	RETURNED("returned (not debriefed)", new Color(144, 238, 144)),
	/** Task has returned and debriefing data has been entered. */
	COMPLETED("Completed", new Color(200, 200, 200));

	private final String label;
	private final Color color;

	/**
	 * Creates a stage.
	 *
	 * @param label board column title.
	 * @param color board column and row colour.
	 */
	SarTaskStage(String label, Color color) {
		this.label = label;
		this.color = color;
	}

	/**
	 * Returns the board column title.
	 *
	 * @return column title.
	 */
	String label() {
		return label;
	}

	/**
	 * Returns the colour used for this stage.
	 *
	 * @return stage colour.
	 */
	Color color() {
		return color;
	}

	/**
	 * Classifies a task by its recorded lifecycle status and debrief state.
	 *
	 * @param task task to classify, possibly null.
	 * @return the stage; unknown or missing statuses are treated as planned.
	 */
	static SarTaskStage of(SarTaskAssignment task) {
		if (task == null) {
			return PLANNED;
		}
		String status = task.getTaskLifecycleStatus();
		String normalized = status == null ? "" : status.trim().toLowerCase(Locale.ROOT);
		if (normalized.startsWith("assigned -")) {
			return ASSIGNED;
		}
		if ("returned".equals(normalized)) {
			String supervisor = task.getDebriefingSupervisor();
			return supervisor != null && !supervisor.isBlank() ? COMPLETED : RETURNED;
		}
		return PLANNED;
	}
}
