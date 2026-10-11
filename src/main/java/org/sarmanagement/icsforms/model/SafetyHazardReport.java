/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.model;

import java.time.LocalDateTime;

/**
 * Archived SAR debrief hazard report retained across operational periods.
 */
public class SafetyHazardReport {
	private String taskId = "";
	private String taskLabel = "";
	private String incidentArea = "";
	private String hazards = "";
	private LocalDateTime periodStart;
	private LocalDateTime periodEnd;
	private boolean debriefingCompleted;
	private String analysisEntryId = "";
	private String notedBySafetyOfficer = "";
	private LocalDateTime notedDateTime;

	/**
	 * Returns the source task identifier.
	 *
	 * @return task identifier.
	 */
	public String getTaskId() {
		return taskId == null ? "" : taskId;
	}

	/**
	 * Sets the source task identifier.
	 *
	 * @param taskId source task identifier.
	 */
	public void setTaskId(String taskId) {
		this.taskId = taskId == null ? "" : taskId;
	}

	/**
	 * Returns the display label for the source task.
	 *
	 * @return task label.
	 */
	public String getTaskLabel() {
		return taskLabel == null ? "" : taskLabel;
	}

	/**
	 * Sets the source task label.
	 *
	 * @param taskLabel source task label.
	 */
	public void setTaskLabel(String taskLabel) {
		this.taskLabel = taskLabel == null ? "" : taskLabel;
	}

	/**
	 * Returns the area associated with the report.
	 *
	 * @return reported area.
	 */
	public String getIncidentArea() {
		return incidentArea == null ? "" : incidentArea;
	}

	/**
	 * Sets the reported area.
	 *
	 * @param incidentArea reported area.
	 */
	public void setIncidentArea(String incidentArea) {
		this.incidentArea = incidentArea == null ? "" : incidentArea;
	}

	/**
	 * Returns the observed hazards.
	 *
	 * @return reported hazards.
	 */
	public String getHazards() {
		return hazards == null ? "" : hazards;
	}

	/**
	 * Sets the observed hazards.
	 *
	 * @param hazards reported hazards.
	 */
	public void setHazards(String hazards) {
		this.hazards = hazards == null ? "" : hazards;
	}

	/**
	 * Returns the operational period start.
	 *
	 * @return period start.
	 */
	public LocalDateTime getPeriodStart() {
		return periodStart;
	}

	/**
	 * Sets the operational period start.
	 *
	 * @param periodStart period start.
	 */
	public void setPeriodStart(LocalDateTime periodStart) {
		this.periodStart = periodStart;
	}

	/**
	 * Returns the operational period end.
	 *
	 * @return period end.
	 */
	public LocalDateTime getPeriodEnd() {
		return periodEnd;
	}

	/**
	 * Sets the operational period end.
	 *
	 * @param periodEnd period end.
	 */
	public void setPeriodEnd(LocalDateTime periodEnd) {
		this.periodEnd = periodEnd;
	}

	/**
	 * Returns whether the debrief was completed.
	 *
	 * @return true when completed.
	 */
	public boolean isDebriefingCompleted() {
		return debriefingCompleted;
	}

	/**
	 * Sets whether the debrief was completed.
	 *
	 * @param debriefingCompleted true when completed.
	 */
	public void setDebriefingCompleted(boolean debriefingCompleted) {
		this.debriefingCompleted = debriefingCompleted;
	}

	/**
	 * Returns the linked analysis row identifier.
	 *
	 * @return analysis entry identifier.
	 */
	public String getAnalysisEntryId() {
		return analysisEntryId == null ? "" : analysisEntryId;
	}

	/**
	 * Links the report to an ICS 215A row.
	 *
	 * @param analysisEntryId analysis entry identifier.
	 */
	public void setAnalysisEntryId(String analysisEntryId) {
		this.analysisEntryId = analysisEntryId == null ? "" : analysisEntryId;
	}

	/**
	 * Returns the acknowledging Safety Officer.
	 *
	 * @return Safety Officer name, or blank when unacknowledged.
	 */
	public String getNotedBySafetyOfficer() {
		return notedBySafetyOfficer == null ? "" : notedBySafetyOfficer;
	}

	/**
	 * Sets the acknowledging Safety Officer.
	 *
	 * @param notedBySafetyOfficer Safety Officer name.
	 */
	public void setNotedBySafetyOfficer(String notedBySafetyOfficer) {
		this.notedBySafetyOfficer = notedBySafetyOfficer == null ? "" : notedBySafetyOfficer;
	}

	/**
	 * Returns the acknowledgement time.
	 *
	 * @return acknowledgement date/time.
	 */
	public LocalDateTime getNotedDateTime() {
		return notedDateTime;
	}

	/**
	 * Sets the acknowledgement time.
	 *
	 * @param notedDateTime acknowledgement date/time.
	 */
	public void setNotedDateTime(LocalDateTime notedDateTime) {
		this.notedDateTime = notedDateTime;
	}
}
