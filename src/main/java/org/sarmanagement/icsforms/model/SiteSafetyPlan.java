/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Site Safety Plan identification, approval details, and extensible text sections.
 */
public class SiteSafetyPlan {
	private String planName = "";
	private String locatedAt = "";
	private String revision = "";
	private String preparedByName = "";
	private String preparedByPositionTitle = "";
	private String preparedBySignature = "";
	private String preparedDateTime = "";
	private Map<String, String> textSections = new LinkedHashMap<>();

	/**
	 * Returns the Site Safety Plan name.
	 *
	 * @return plan name.
	 */
	public String getPlanName() {
		return safe(planName);
	}

	/**
	 * Sets the Site Safety Plan name.
	 *
	 * @param planName plan name.
	 */
	public void setPlanName(String planName) {
		this.planName = safe(planName);
	}

	/**
	 * Returns where the approved plan is located.
	 *
	 * @return plan location.
	 */
	public String getLocatedAt() {
		return safe(locatedAt);
	}

	/**
	 * Sets where the approved plan is located.
	 *
	 * @param locatedAt plan location.
	 */
	public void setLocatedAt(String locatedAt) {
		this.locatedAt = safe(locatedAt);
	}

	/**
	 * Returns the plan revision label.
	 *
	 * @return revision label.
	 */
	public String getRevision() {
		return safe(revision);
	}

	/**
	 * Sets the plan revision label.
	 *
	 * @param revision revision label.
	 */
	public void setRevision(String revision) {
		this.revision = safe(revision);
	}

	/**
	 * Returns the preparer name.
	 *
	 * @return preparer name.
	 */
	public String getPreparedByName() {
		return safe(preparedByName);
	}

	/**
	 * Sets the preparer name.
	 *
	 * @param preparedByName preparer name.
	 */
	public void setPreparedByName(String preparedByName) {
		this.preparedByName = safe(preparedByName);
	}

	/**
	 * Returns the preparer position.
	 *
	 * @return preparer position.
	 */
	public String getPreparedByPositionTitle() {
		return safe(preparedByPositionTitle);
	}

	/**
	 * Sets the preparer position.
	 *
	 * @param preparedByPositionTitle preparer position.
	 */
	public void setPreparedByPositionTitle(String preparedByPositionTitle) {
		this.preparedByPositionTitle = safe(preparedByPositionTitle);
	}

	/**
	 * Returns the preparer signature.
	 *
	 * @return signature.
	 */
	public String getPreparedBySignature() {
		return safe(preparedBySignature);
	}

	/**
	 * Sets the preparer signature.
	 *
	 * @param preparedBySignature signature.
	 */
	public void setPreparedBySignature(String preparedBySignature) {
		this.preparedBySignature = safe(preparedBySignature);
	}

	/**
	 * Returns the preparation date/time text in ISO date format.
	 *
	 * @return preparation date/time.
	 */
	public String getPreparedDateTime() {
		return safe(preparedDateTime);
	}

	/**
	 * Sets the preparation date/time text in ISO date format.
	 *
	 * @param preparedDateTime preparation date/time.
	 */
	public void setPreparedDateTime(String preparedDateTime) {
		this.preparedDateTime = safe(preparedDateTime);
	}

	/**
	 * Returns named additional site-plan text areas.
	 *
	 * @return mutable named sections, never null.
	 */
	public Map<String, String> getTextSections() {
		if (textSections == null) {
			textSections = new LinkedHashMap<>();
		}
		return textSections;
	}

	/**
	 * Replaces the additional site-plan text areas.
	 *
	 * @param textSections named section text; null becomes empty.
	 */
	public void setTextSections(Map<String, String> textSections) {
		this.textSections = textSections == null ? new LinkedHashMap<>() : textSections;
	}

	private static String safe(String value) {
		return value == null ? "" : value;
	}
}
