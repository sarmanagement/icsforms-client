/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Incident Action Plan Safety Analysis (ICS 215A) content and review metadata.
 */
public class Ics215aForm {
	private String incidentNumber = "";
	private LocalDateTime preparedDateTime;
	private String safetyOfficerName = "";
	private String operationsSectionChiefName = "";
	private LocalDateTime reviewedDateTime;
	private List<SafetyAnalysisEntry> entries = new ArrayList<>();
	private boolean includeInIap;
	private String iapPage = "";

	/**
	 * Returns the incident number.
	 *
	 * @return incident number.
	 */
	public String getIncidentNumber() {
		return safe(incidentNumber);
	}

	/**
	 * Sets the incident number.
	 *
	 * @param incidentNumber incident number.
	 */
	public void setIncidentNumber(String incidentNumber) {
		this.incidentNumber = safe(incidentNumber);
	}

	/**
	 * Returns when this analysis was prepared.
	 *
	 * @return preparation date and time.
	 */
	public LocalDateTime getPreparedDateTime() {
		return preparedDateTime;
	}

	/**
	 * Sets when this analysis was prepared.
	 *
	 * @param preparedDateTime preparation date and time.
	 */
	public void setPreparedDateTime(LocalDateTime preparedDateTime) {
		this.preparedDateTime = preparedDateTime;
	}

	/**
	 * Returns the collaborating Safety Officer.
	 *
	 * @return Safety Officer name.
	 */
	public String getSafetyOfficerName() {
		return safe(safetyOfficerName);
	}

	/**
	 * Sets the collaborating Safety Officer.
	 *
	 * @param safetyOfficerName Safety Officer name.
	 */
	public void setSafetyOfficerName(String safetyOfficerName) {
		this.safetyOfficerName = safe(safetyOfficerName);
	}

	/**
	 * Returns the collaborating Operations Section Chief.
	 *
	 * @return Operations Section Chief name.
	 */
	public String getOperationsSectionChiefName() {
		return safe(operationsSectionChiefName);
	}

	/**
	 * Sets the collaborating Operations Section Chief.
	 *
	 * @param operationsSectionChiefName Operations Section Chief name.
	 */
	public void setOperationsSectionChiefName(String operationsSectionChiefName) {
		this.operationsSectionChiefName = safe(operationsSectionChiefName);
	}

	/**
	 * Returns when the analysis was reviewed.
	 *
	 * @return review date and time.
	 */
	public LocalDateTime getReviewedDateTime() {
		return reviewedDateTime;
	}

	/**
	 * Sets when the analysis was reviewed.
	 *
	 * @param reviewedDateTime review date and time.
	 */
	public void setReviewedDateTime(LocalDateTime reviewedDateTime) {
		this.reviewedDateTime = reviewedDateTime;
	}

	/**
	 * Returns the mutable incident-area analysis rows.
	 *
	 * @return analysis rows, never null.
	 */
	public List<SafetyAnalysisEntry> getEntries() {
		if (entries == null) {
			entries = new ArrayList<>();
		}
		return entries;
	}

	/**
	 * Replaces the incident-area analysis rows.
	 *
	 * @param entries analysis rows; null becomes empty.
	 */
	public void setEntries(List<SafetyAnalysisEntry> entries) {
		this.entries = entries == null ? new ArrayList<>() : entries;
	}

	/**
	 * Returns whether the 215A is selected for the IAP bundle.
	 *
	 * @return true when included.
	 */
	public boolean isIncludeInIap() {
		return includeInIap;
	}

	/**
	 * Sets whether the 215A is selected for the IAP bundle.
	 *
	 * @param includeInIap true to include.
	 */
	public void setIncludeInIap(boolean includeInIap) {
		this.includeInIap = includeInIap;
	}

	/**
	 * Returns the assigned IAP page label.
	 *
	 * @return IAP page label.
	 */
	public String getIapPage() {
		return safe(iapPage);
	}

	/**
	 * Sets the assigned IAP page label.
	 *
	 * @param iapPage IAP page label.
	 */
	public void setIapPage(String iapPage) {
		this.iapPage = safe(iapPage);
	}

	private static String safe(String value) {
		return value == null ? "" : value;
	}
}
