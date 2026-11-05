/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A point-of-contact assignment recorded in chronological history, using UTC.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PocAssignment {
	private String id = UUID.randomUUID().toString();
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
	 * Returns this point-of-contact history entry's stable identity.
	 *
	 * @return assignment UUID, never null or blank
	 */
	public String getId() {
		if (id == null || id.isBlank()) {
			id = UUID.randomUUID().toString();
		}
		return id;
	}

	/**
	 * Sets this point-of-contact history entry's identity.
	 *
	 * @param id assignment UUID; null or blank generates a new UUID
	 */
	public void setId(String id) {
		this.id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
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
