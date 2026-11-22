/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
public class ClueLogEntry implements Identified {
	@JsonProperty("id")
	@JsonAlias("entryId")
	private String entryId = UUID.randomUUID().toString();
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
	private Provenance provenance;
	private boolean struckOut;
	private DuplicateConfirmation duplicateConfirmation;

	/**
	 * Records the explicit duplicate decision on the retained, struck-out clue.
	 *
	 * @param duplicateOfId identity of the other retained clue
	 * @param confirmedAt UTC time of the decision
	 * @param provenance reviewer attribution, or null when unavailable
	 */
	public record DuplicateConfirmation(String duplicateOfId, Instant confirmedAt, Provenance provenance) {
	}

	/**
	 * Returns whether this clue has been struck out rather than deleted.
	 * @return true for a struck-out clue
	 */
	@JsonInclude(JsonInclude.Include.NON_DEFAULT)
	public boolean isStruckOut() {
		return struckOut;
	}

	/**
	 * Sets the strike-out state when loading or updating a clue.
	 * @param struckOut true when struck out
	 */
	public void setStruckOut(boolean struckOut) {
		this.struckOut = struckOut;
	}

	/**
	 * Returns the recorded duplicate decision, if any.
	 * @return confirmation or null for an unreviewed clue
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public DuplicateConfirmation getDuplicateConfirmation() {
		return duplicateConfirmation;
	}

	/**
	 * Restores the recorded duplicate decision.
	 * @param duplicateConfirmation confirmation or null
	 */
	public void setDuplicateConfirmation(DuplicateConfirmation duplicateConfirmation) {
		this.duplicateConfirmation = duplicateConfirmation;
	}

	/**
	 * Confirms that this clue duplicates another retained clue without deleting either.
	 * @param otherId identity of the clue to retain as the reference
	 * @param confirmedAt UTC time of confirmation
	 * @param reviewer reviewer attribution, or null when unavailable
	 * @throws IllegalArgumentException if the reference or time is invalid
	 * @throws IllegalStateException if this clue is already struck out
	 */
	public void confirmDuplicate(String otherId, Instant confirmedAt, Provenance reviewer) {
		if (otherId == null || otherId.isBlank() || otherId.equals(getEntryId()) || confirmedAt == null) {
			throw new IllegalArgumentException("A different clue and confirmation time are required.");
		}
		if (struckOut) {
			throw new IllegalStateException("The clue is already struck out.");
		}
		duplicateConfirmation = new DuplicateConfirmation(otherId, confirmedAt, reviewer);
		struckOut = true;
	}

	/**
	 * Returns the optional attribution for this clue entry.
	 * @return clue provenance, or {@code null} for legacy or unattributed entries
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public Provenance getProvenance() {
		return provenance;
	}

	/**
	 * Sets the optional attribution for this clue entry.
	 * @param provenance clue provenance, or {@code null} when unattributed
	 */
	public void setProvenance(Provenance provenance) {
		this.provenance = provenance;
	}

	/**
	 * Returns this clue's stable identity, assigning a UUID to legacy entries
	 * without one.
	 *
	 * @return clue entry UUID, never {@code null} or blank.
	 */
	@JsonIgnore
	public String getEntryId() {
		return entryId;
	}

	/** {@inheritDoc} */
	@Override
	public String getId() {
		return entryId;
	}

	/**
	 * Sets this clue's identity during deserialization or migration.
	 *
	 * @param entryId
	 *            stable entry UUID; missing values are generated on first access.
	 */
	@JsonIgnore
	public void setEntryId(String entryId) {
		setId(entryId);
	}

	/** {@inheritDoc} */
	@Override
	@com.fasterxml.jackson.annotation.JsonIgnore
	public void setId(String id) {
		IdentityGuard.check(entryId, id);
		entryId = id;
	}

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
