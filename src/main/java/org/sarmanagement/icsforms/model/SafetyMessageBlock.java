/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Editable hazard and precaution block linked to one or more 215A rows.
 */
public class SafetyMessageBlock {
	private String id = UUID.randomUUID().toString();
	private String hazard = "";
	private String incidentArea = "All";
	private String precautions = "";
	private List<String> sourceEntryIds = new ArrayList<>();
	private boolean needsReview;

	/**
	 * Returns this safety message block's stable identity.
	 *
	 * @return message block UUID, never null or blank
	 */
	public String getId() {
		if (id == null || id.isBlank()) {
			id = UUID.randomUUID().toString();
		}
		return id;
	}

	/**
	 * Sets this safety message block's stable identity.
	 *
	 * @param id message block UUID; null or blank generates a UUID
	 */
	public void setId(String id) {
		this.id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
	}

	/**
	 * Returns the hazard heading.
	 *
	 * @return hazard heading.
	 */
	public String getHazard() {
		return safe(hazard);
	}

	/**
	 * Sets the hazard heading.
	 *
	 * @param hazard hazard heading.
	 */
	public void setHazard(String hazard) {
		this.hazard = safe(hazard);
	}

	/**
	 * Returns the applicable incident area.
	 *
	 * @return incident area.
	 */
	public String getIncidentArea() {
		return incidentArea == null || incidentArea.isBlank() ? "All" : incidentArea;
	}

	/**
	 * Sets the applicable incident area.
	 *
	 * @param incidentArea incident area; null becomes {@code All}.
	 */
	public void setIncidentArea(String incidentArea) {
		this.incidentArea = incidentArea == null || incidentArea.isBlank() ? "All" : incidentArea.trim();
	}

	/**
	 * Returns the editable precautions.
	 *
	 * @return precaution text.
	 */
	public String getPrecautions() {
		return safe(precautions);
	}

	/**
	 * Sets the editable precautions.
	 *
	 * @param precautions precaution text.
	 */
	public void setPrecautions(String precautions) {
		this.precautions = safe(precautions);
	}

	/**
	 * Returns the stable identifiers of contributing analysis rows.
	 *
	 * @return linked analysis row identifiers.
	 */
	public List<String> getSourceEntryIds() {
		if (sourceEntryIds == null) {
			sourceEntryIds = new ArrayList<>();
		}
		return sourceEntryIds;
	}

	/**
	 * Sets the linked analysis row identifiers.
	 *
	 * @param sourceEntryIds row identifiers; null becomes empty.
	 */
	public void setSourceEntryIds(List<String> sourceEntryIds) {
		this.sourceEntryIds = sourceEntryIds == null ? new ArrayList<>() : sourceEntryIds;
	}

	/**
	 * Returns whether source content changed after the block was generated.
	 *
	 * @return true when officer review is needed.
	 */
	public boolean isNeedsReview() {
		return needsReview;
	}

	/**
	 * Sets whether officer review is needed.
	 *
	 * @param needsReview true when review is needed.
	 */
	public void setNeedsReview(boolean needsReview) {
		this.needsReview = needsReview;
	}

	private static String safe(String value) {
		return value == null ? "" : value;
	}
}
