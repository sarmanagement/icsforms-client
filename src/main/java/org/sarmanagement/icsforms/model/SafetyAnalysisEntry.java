/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.model;

import java.util.UUID;

/**
 * One incident-area hazard and mitigation row on an ICS 215A analysis.
 */
public class SafetyAnalysisEntry {
	private String entryId = UUID.randomUUID().toString();
	private String incidentArea = "All";
	private String hazardsRisks = "";
	private String mitigations = "";
	private String equipmentPpe = "";

	/**
	 * Returns the stable row identifier used to link message blocks to this row.
	 *
	 * @return row identifier.
	 */
	public String getEntryId() {
		if (entryId == null || entryId.isBlank()) {
			entryId = UUID.randomUUID().toString();
		}
		return entryId;
	}

	/**
	 * Sets the stable row identifier.
	 *
	 * @param entryId row identifier; blank values are replaced on access.
	 */
	public void setEntryId(String entryId) {
		this.entryId = entryId;
	}

	/**
	 * Returns the incident area where the hazard applies.
	 *
	 * @return incident area.
	 */
	public String getIncidentArea() {
		return safe(incidentArea);
	}

	/**
	 * Sets the incident area.
	 *
	 * @param incidentArea incident area; null or blank becomes {@code All}.
	 */
	public void setIncidentArea(String incidentArea) {
		this.incidentArea = incidentArea == null || incidentArea.isBlank() ? "All" : incidentArea.trim();
	}

	/**
	 * Returns the hazards and risks for the analysis row.
	 *
	 * @return hazard and risk description.
	 */
	public String getHazardsRisks() {
		return safe(hazardsRisks);
	}

	/**
	 * Sets the hazards and risks.
	 *
	 * @param hazardsRisks hazard and risk description.
	 */
	public void setHazardsRisks(String hazardsRisks) {
		this.hazardsRisks = safe(hazardsRisks);
	}

	/**
	 * Returns the mitigations for the hazards and risks.
	 *
	 * @return mitigation text.
	 */
	public String getMitigations() {
		return safe(mitigations);
	}

	/**
	 * Sets the mitigation text.
	 *
	 * @param mitigations mitigation text.
	 */
	public void setMitigations(String mitigations) {
		this.mitigations = safe(mitigations);
	}

	/**
	 * Returns explicitly identified equipment and PPE requirements.
	 *
	 * @return equipment and PPE text.
	 */
	public String getEquipmentPpe() {
		return safe(equipmentPpe);
	}

	/**
	 * Sets explicitly identified equipment and PPE requirements.
	 *
	 * @param equipmentPpe equipment and PPE text.
	 */
	public void setEquipmentPpe(String equipmentPpe) {
		this.equipmentPpe = safe(equipmentPpe);
	}

	private static String safe(String value) {
		return value == null ? "" : value;
	}
}
