/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.model;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Stable, ordered incident objective with an explicit human assessment.
 */
public class IncidentObjective {
	private String objectiveId = UUID.randomUUID().toString();
	private String text = "";
	private int displayOrder;
	private ObjectiveAssessment assessment = ObjectiveAssessment.NOT_ASSESSED;
	private String assessmentNote = "";
	private String assessedBy = "";
	private LocalDateTime assessedAt;
	private String predecessorObjectiveId = "";

	/** Creates an empty objective. */
	public IncidentObjective() {
	}

	/**
	 * Creates an objective containing legacy or newly entered text.
	 *
	 * @param text objective text.
	 * @param displayOrder zero-based display order.
	 */
	public IncidentObjective(String text, int displayOrder) {
		this.text = text == null ? "" : text;
		this.displayOrder = displayOrder;
	}

	/** @return stable objective identifier. */
	public String getObjectiveId() {
		if (objectiveId == null || objectiveId.isBlank()) {
			objectiveId = UUID.randomUUID().toString();
		}
		return objectiveId;
	}

	/** @param objectiveId stable objective identifier. */
	public void setObjectiveId(String objectiveId) {
		this.objectiveId = objectiveId == null || objectiveId.isBlank() ? UUID.randomUUID().toString() : objectiveId;
	}

	/** @return objective wording. */
	public String getText() {
		return text == null ? "" : text;
	}

	/** @param text objective wording. */
	public void setText(String text) {
		this.text = text == null ? "" : text;
	}

	/** @return zero-based display order. */
	public int getDisplayOrder() {
		return displayOrder;
	}

	/** @param displayOrder zero-based display order. */
	public void setDisplayOrder(int displayOrder) {
		this.displayOrder = Math.max(0, displayOrder);
	}

	/** @return explicit assessment. */
	public ObjectiveAssessment getAssessment() {
		return assessment == null ? ObjectiveAssessment.NOT_ASSESSED : assessment;
	}

	/** @param assessment explicit assessment. */
	public void setAssessment(ObjectiveAssessment assessment) {
		this.assessment = assessment == null ? ObjectiveAssessment.NOT_ASSESSED : assessment;
	}

	/** @return assessment note. */
	public String getAssessmentNote() {
		return assessmentNote == null ? "" : assessmentNote;
	}

	/** @param assessmentNote assessment note. */
	public void setAssessmentNote(String assessmentNote) {
		this.assessmentNote = assessmentNote == null ? "" : assessmentNote;
	}

	/** @return person who recorded the assessment. */
	public String getAssessedBy() {
		return assessedBy == null ? "" : assessedBy;
	}

	/** @param assessedBy person recording the assessment. */
	public void setAssessedBy(String assessedBy) {
		this.assessedBy = assessedBy == null ? "" : assessedBy;
	}

	/** @return assessment update time, or null. */
	public LocalDateTime getAssessedAt() {
		return assessedAt;
	}

	/** @param assessedAt assessment update time. */
	public void setAssessedAt(LocalDateTime assessedAt) {
		this.assessedAt = assessedAt;
	}

	/** @return prior-period objective identifier, or blank. */
	public String getPredecessorObjectiveId() {
		return predecessorObjectiveId == null ? "" : predecessorObjectiveId;
	}

	/** @param predecessorObjectiveId prior-period objective identifier. */
	public void setPredecessorObjectiveId(String predecessorObjectiveId) {
		this.predecessorObjectiveId = predecessorObjectiveId == null ? "" : predecessorObjectiveId;
	}

	/**
	 * Returns a compact numbered objective label for pickers.
	 *
	 * @return numbered objective text.
	 */
	@Override
	public String toString() {
		return (displayOrder + 1) + ". " + getText();
	}
}
