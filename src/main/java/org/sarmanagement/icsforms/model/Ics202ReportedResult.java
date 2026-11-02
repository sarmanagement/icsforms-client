/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.model;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Operator-recorded result associated with an ICS 202 objective and its stated source.
 */
public class Ics202ReportedResult {
	private String resultId = UUID.randomUUID().toString();
	private String objectiveId = "";
	private String result = "";
	private String source = "";
	private String reportedBy = "";
	private LocalDateTime reportedAt;

	/** Creates an empty reported result. */
	public Ics202ReportedResult() {
	}

	/** @return stable result identifier. */
	public String getResultId() {
		return resultId;
	}

	/** @param resultId stable result identifier. */
	public void setResultId(String resultId) {
		this.resultId = resultId == null || resultId.isBlank() ? UUID.randomUUID().toString() : resultId;
	}

	/** @return associated objective identifier, or blank for an incident-level result. */
	public String getObjectiveId() {
		return objectiveId == null ? "" : objectiveId;
	}

	/** @param objectiveId associated objective identifier. */
	public void setObjectiveId(String objectiveId) {
		this.objectiveId = objectiveId == null ? "" : objectiveId;
	}

	/** @return operator-recorded result text. */
	public String getResult() {
		return result == null ? "" : result;
	}

	/** @param result operator-recorded result text. */
	public void setResult(String result) {
		this.result = result == null ? "" : result;
	}

	/** @return operator-recorded source description. */
	public String getSource() {
		return source == null ? "" : source;
	}

	/** @param source operator-recorded source description. */
	public void setSource(String source) {
		this.source = source == null ? "" : source;
	}

	/** @return person recording the result. */
	public String getReportedBy() {
		return reportedBy == null ? "" : reportedBy;
	}

	/** @param reportedBy person recording the result. */
	public void setReportedBy(String reportedBy) {
		this.reportedBy = reportedBy == null ? "" : reportedBy;
	}

	/** @return result recording time, or null when not recorded. */
	public LocalDateTime getReportedAt() {
		return reportedAt;
	}

	/** @param reportedAt result recording time. */
	public void setReportedAt(LocalDateTime reportedAt) {
		this.reportedAt = reportedAt;
	}
}
