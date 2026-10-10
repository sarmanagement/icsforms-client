/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Structured clue entry captured from SAR task debriefing and shown in the
 * shared clue log.
 *
 * <p>
 * {@link #immediateAction} records the action taken on the spot (e.g. flagged,
 * photographed, collected). {@link #followUp} records any planned subsequent
 * follow-up. The two fields are kept distinct so that the debrief form can
 * render them separately.
 * </p>
 *
 * <p>
 * {@link #possibleDuplicate} flags entries that were <em>reported</em> by
 * another resource rather than directly detected by the logging resource — such
 * entries may duplicate a clue already recorded from the detecting resource's
 * log.
 * </p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ClueLogEntry {
	private String assignmentId = "";
	private String detectingTask = "";
	/**
	 * Free-text identifier of the specific person or resource that detected the
	 * clue.
	 */
	private String detectedBy = "";
	private LocalDateTime dateTimeCollected;
	private String location = "";
	private String description = "";
	private String immediateAction = "";
	private String followUp = "";
	private boolean possibleDuplicate = false;
	private List<PocAssignment> followUpAssignments = new ArrayList<>();
	private boolean followUpBriefed;
	private LocalDateTime followUpTargetDate;
	private LocalDateTime followUpCompletedDate;
	private String followUpStatus = "";

	/**
	 * Returns the linked task assignment identifier.
	 * @return identifier, never null
	 */
	public String getAssignmentId() {
		return assignmentId;
	}

	/**
	 * Sets the linked task assignment identifier.
	 * @param assignmentId identifier; null becomes empty
	 */
	public void setAssignmentId(String assignmentId) {
		this.assignmentId = assignmentId == null ? "" : assignmentId;
	}

	/**
	 * Returns the detecting task label.
	 * @return label, never null
	 */
	public String getDetectingTask() {
		return detectingTask;
	}

	/**
	 * Sets the detecting task label.
	 * @param detectingTask label; null becomes empty
	 */
	public void setDetectingTask(String detectingTask) {
		this.detectingTask = detectingTask == null ? "" : detectingTask;
	}

	/**
	 * @return identifier of the specific person or resource that detected the clue.
	 */
	public String getDetectedBy() {
		return detectedBy;
	}

	/**
	 * @param detectedBy
	 *            identifier of the specific person or resource that detected the
	 *            clue.
	 */
	public void setDetectedBy(String detectedBy) {
		this.detectedBy = detectedBy == null ? "" : detectedBy;
	}

	/**
	 * Returns the UTC clue collection time.
	 * @return collection time, or null when unknown
	 */
	public LocalDateTime getDateTimeCollected() {
		return dateTimeCollected;
	}

	/**
	 * Sets the UTC clue collection time.
	 * @param dateTimeCollected collection time, or null when unknown
	 */
	public void setDateTimeCollected(LocalDateTime dateTimeCollected) {
		this.dateTimeCollected = dateTimeCollected;
	}

	/**
	 * Returns the clue location.
	 * @return location, never null
	 */
	public String getLocation() {
		return location;
	}

	/**
	 * Sets the clue location.
	 * @param location location; null becomes empty
	 */
	public void setLocation(String location) {
		this.location = location == null ? "" : location;
	}

	/**
	 * Returns the clue description.
	 * @return description, never null
	 */
	public String getDescription() {
		return description;
	}

	/**
	 * Sets the clue description.
	 * @param description description; null becomes empty
	 */
	public void setDescription(String description) {
		this.description = description == null ? "" : description;
	}

	/**
	 * @return immediate action taken at the time of clue detection (e.g. flagged,
	 *         photographed, collected).
	 */
	public String getImmediateAction() {
		return immediateAction;
	}

	/**
	 * @param immediateAction
	 *            immediate action taken at the time of clue detection.
	 */
	public void setImmediateAction(String immediateAction) {
		this.immediateAction = immediateAction == null ? "" : immediateAction;
	}

	/**
	 * Returns the planned follow-up action.
	 * @return follow-up text, never null
	 */
	public String getFollowUp() {
		return followUp;
	}

	/**
	 * Sets the planned follow-up action.
	 * @param followUp action; null becomes empty
	 */
	public void setFollowUp(String followUp) {
		this.followUp = followUp == null ? "" : followUp;
	}

	/**
	 * @return {@code true} when this clue was reported by another resource and may
	 *         already be logged elsewhere.
	 */
	public boolean isPossibleDuplicate() {
		return possibleDuplicate;
	}

	/**
	 * @param possibleDuplicate
	 *            {@code true} when this clue was reported by another resource.
	 */
	public void setPossibleDuplicate(boolean possibleDuplicate) {
		this.possibleDuplicate = possibleDuplicate;
	}

	/**
	 * Returns assignment history in recorded order, oldest first.
	 * @return mutable history, never null
	 */
	public List<PocAssignment> getFollowUpAssignments() {
		return followUpAssignments;
	}

	/**
	 * Sets assignment history, excluding null records.
	 * @param followUpAssignments chronological history; null becomes empty
	 */
	public void setFollowUpAssignments(List<PocAssignment> followUpAssignments) {
		this.followUpAssignments = new ArrayList<>();
		if (followUpAssignments != null) {
			for (PocAssignment assignment : followUpAssignments) {
				if (assignment != null) {
					this.followUpAssignments.add(assignment);
				}
			}
		}
	}

	/**
	 * Returns the latest recorded POC, including an explicit empty assignment.
	 * @return current assignee, or empty when no history exists
	 */
	@JsonIgnore
	public String getFollowUpAssignedTo() {
		for (int i = followUpAssignments.size() - 1; i >= 0; i--) {
			PocAssignment assignment = followUpAssignments.get(i);
			if (assignment != null) {
				return assignment.getAssignee();
			}
		}
		return "";
	}

	/**
	 * Returns whether the current follow-up POC has been briefed.
	 * @return true when briefed
	 */
	public boolean isFollowUpBriefed() {
		return followUpBriefed;
	}

	/**
	 * Sets whether the current follow-up POC has been briefed.
	 * @param followUpBriefed true when briefed
	 */
	public void setFollowUpBriefed(boolean followUpBriefed) {
		this.followUpBriefed = followUpBriefed;
	}

	/**
	 * Returns the UTC follow-up target time.
	 * @return target time, or null when unset
	 */
	public LocalDateTime getFollowUpTargetDate() {
		return followUpTargetDate;
	}

	/**
	 * Sets the UTC follow-up target time.
	 * @param followUpTargetDate target time, or null when unset
	 */
	public void setFollowUpTargetDate(LocalDateTime followUpTargetDate) {
		this.followUpTargetDate = followUpTargetDate;
	}

	/**
	 * Returns the UTC follow-up completion time.
	 * @return completion time, or null when open
	 */
	public LocalDateTime getFollowUpCompletedDate() {
		return followUpCompletedDate;
	}

	/**
	 * Sets the UTC follow-up completion time.
	 * @param followUpCompletedDate completion time, or null when open
	 */
	public void setFollowUpCompletedDate(LocalDateTime followUpCompletedDate) {
		this.followUpCompletedDate = followUpCompletedDate;
	}

	/**
	 * Returns follow-up progress/status notes.
	 * @return notes, never null
	 */
	public String getFollowUpStatus() {
		return followUpStatus;
	}

	/**
	 * Sets follow-up progress/status notes.
	 * @param followUpStatus notes; null becomes empty
	 */
	public void setFollowUpStatus(String followUpStatus) {
		this.followUpStatus = followUpStatus == null ? "" : followUpStatus;
	}
}
