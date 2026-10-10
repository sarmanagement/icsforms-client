/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDateTime;

/**
 * A point-of-contact assignment recorded in chronological history, using UTC.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PocAssignment {
	private String assignee = "";
	private LocalDateTime assignedAt;

	/** Creates an empty assignment for JSON persistence. */
	public PocAssignment() {
	}

	/**
	 * Creates an assignment.
	 * @param assignee responsible person or resource; null becomes empty
	 * @param assignedAt UTC assignment time, or null when unknown
	 */
	public PocAssignment(String assignee, LocalDateTime assignedAt) {
		setAssignee(assignee);
		this.assignedAt = assignedAt;
	}

	/**
	 * Returns the responsible person or resource.
	 * @return assignee, never null
	 */
	public String getAssignee() {
		return assignee;
	}

	/**
	 * Sets the responsible person or resource.
	 * @param assignee name; null becomes empty
	 */
	public void setAssignee(String assignee) {
		this.assignee = assignee == null ? "" : assignee;
	}

	/**
	 * Returns the UTC assignment time.
	 * @return time, or null when unknown
	 */
	public LocalDateTime getAssignedAt() {
		return assignedAt;
	}

	/**
	 * Sets the UTC assignment time.
	 * @param assignedAt time, or null when unknown
	 */
	public void setAssignedAt(LocalDateTime assignedAt) {
		this.assignedAt = assignedAt;
	}
}
