/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Independently stored canine medical plan, adapted from the ICS 206 structure.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CanineMedicalPlan {
	private boolean active;
	private String aidStationInformation = "";
	private String transportServices = "";
	private List<MedicalPlan.TransportService> transportServiceEntries = new ArrayList<>();
	private String poisonControlContact = "";
	private String onsiteVeterinarian = "";
	private String veterinarianDvm = "";
	private String veterinaryCapabilityNotes = "";
	private String emergencyProcedures = "";
	private boolean aviationAssetsUsed;
	private List<VetFacility> selectedFacilities = new ArrayList<>();
	private String preparedByMedicalUnitLeader = "";
	private String preparerSignature = "";
	private LocalDateTime preparedAt;
	private String reviewedBySafetyOfficer = "";
	private String safetyOfficerSignature = "";
	private String reviewStatus = "Not reviewed";
	private String iapPage = "";

	/** Creates an empty, inactive canine medical plan. */
	public CanineMedicalPlan() {
	}

	/** @return true when active for export and readiness reporting. */
	public boolean isActive() {
		return active;
	}

	/** @param active true to activate the plan. */
	public void setActive(boolean active) {
		this.active = active;
	}

	/** @return canine medical aid-station entries. */
	public String getAidStationInformation() {
		return aidStationInformation;
	}

	/** @param aidStationInformation editable canine aid-station entries. */
	public void setAidStationInformation(String aidStationInformation) {
		this.aidStationInformation = safe(aidStationInformation);
	}

	/** @return canine-specific transport entries. */
	public String getTransportServices() {
		return transportServices;
	}

	/** @param transportServices editable canine transport services and capability notes. */
	public void setTransportServices(String transportServices) {
		this.transportServices = safe(transportServices);
	}

	/**
	 * Returns structured canine-capable transportation service entries.
	 *
	 * @return transport service entries.
	 */
	public List<MedicalPlan.TransportService> getTransportServiceEntries() {
		return transportServiceEntries;
	}

	/**
	 * Replaces structured canine-capable transportation service entries.
	 *
	 * @param transportServiceEntries service entries; null becomes empty.
	 */
	public void setTransportServiceEntries(List<MedicalPlan.TransportService> transportServiceEntries) {
		this.transportServiceEntries = transportServiceEntries == null
				? new ArrayList<>() : transportServiceEntries;
	}

	/** @return editable poison-control contact text. */
	public String getPoisonControlContact() {
		return poisonControlContact;
	}

	/** @param poisonControlContact editable contact text. */
	public void setPoisonControlContact(String poisonControlContact) {
		this.poisonControlContact = safe(poisonControlContact);
	}

	/** @return onsite veterinarian name. */
	public String getOnsiteVeterinarian() {
		return onsiteVeterinarian;
	}

	/** @param onsiteVeterinarian onsite veterinarian name. */
	public void setOnsiteVeterinarian(String onsiteVeterinarian) {
		this.onsiteVeterinarian = safe(onsiteVeterinarian);
	}

	/** @return entered DVM credential/name. */
	public String getVeterinarianDvm() {
		return veterinarianDvm;
	}

	/** @param veterinarianDvm entered DVM credential/name. */
	public void setVeterinarianDvm(String veterinarianDvm) {
		this.veterinarianDvm = safe(veterinarianDvm);
	}

	/** @return veterinary capability notes and preamble. */
	public String getVeterinaryCapabilityNotes() {
		return veterinaryCapabilityNotes;
	}

	/** @param veterinaryCapabilityNotes capability notes and preamble. */
	public void setVeterinaryCapabilityNotes(String veterinaryCapabilityNotes) {
		this.veterinaryCapabilityNotes = safe(veterinaryCapabilityNotes);
	}

	/** @return medical emergency procedures. */
	public String getEmergencyProcedures() {
		return emergencyProcedures;
	}

	/** @param emergencyProcedures medical emergency procedures. */
	public void setEmergencyProcedures(String emergencyProcedures) {
		this.emergencyProcedures = safe(emergencyProcedures);
	}

	/** @return whether aviation assets are used. */
	public boolean isAviationAssetsUsed() {
		return aviationAssetsUsed;
	}

	/** @param aviationAssetsUsed user-entered aviation checkbox value. */
	public void setAviationAssetsUsed(boolean aviationAssetsUsed) {
		this.aviationAssetsUsed = aviationAssetsUsed;
	}

	/**
	 * Returns the accepted plan snapshot independent of the source directory.
	 *
	 * @return selected facility snapshots.
	 */
	public List<VetFacility> getSelectedFacilities() {
		return selectedFacilities;
	}

	/**
	 * Replaces selected snapshots without changing the incident directory.
	 *
	 * @param selectedFacilities facility snapshots; null becomes empty.
	 */
	public void setSelectedFacilities(List<VetFacility> selectedFacilities) {
		this.selectedFacilities = selectedFacilities == null ? new ArrayList<>() : selectedFacilities;
	}

	/** @return Medical Unit Leader preparer name. */
	public String getPreparedByMedicalUnitLeader() {
		return preparedByMedicalUnitLeader;
	}

	/** @param preparedByMedicalUnitLeader preparer name. */
	public void setPreparedByMedicalUnitLeader(String preparedByMedicalUnitLeader) {
		this.preparedByMedicalUnitLeader = safe(preparedByMedicalUnitLeader);
	}

	/** @return preparer signature text. */
	public String getPreparerSignature() {
		return preparerSignature;
	}

	/** @param preparerSignature preparer signature text. */
	public void setPreparerSignature(String preparerSignature) {
		this.preparerSignature = safe(preparerSignature);
	}

	/** @return preparation timestamp, or null. */
	public LocalDateTime getPreparedAt() {
		return preparedAt;
	}

	/** @param preparedAt preparation timestamp. */
	public void setPreparedAt(LocalDateTime preparedAt) {
		this.preparedAt = preparedAt;
	}

	/** @return Safety Officer reviewer name. */
	public String getReviewedBySafetyOfficer() {
		return reviewedBySafetyOfficer;
	}

	/** @param reviewedBySafetyOfficer Safety Officer reviewer name. */
	public void setReviewedBySafetyOfficer(String reviewedBySafetyOfficer) {
		this.reviewedBySafetyOfficer = safe(reviewedBySafetyOfficer);
	}

	/** @return Safety Officer signature text. */
	public String getSafetyOfficerSignature() {
		return safetyOfficerSignature;
	}

	/** @param safetyOfficerSignature Safety Officer signature text. */
	public void setSafetyOfficerSignature(String safetyOfficerSignature) {
		this.safetyOfficerSignature = safe(safetyOfficerSignature);
	}

	/** @return explicit review status; empty plans begin not reviewed. */
	public String getReviewStatus() {
		return reviewStatus;
	}

	/** @param reviewStatus explicit review status. */
	public void setReviewStatus(String reviewStatus) {
		this.reviewStatus = reviewStatus == null || reviewStatus.isBlank() ? "Not reviewed" : reviewStatus;
	}

	/**
	 * Clears prior-period reviewer, signature, status, and review timestamp metadata without changing
	 * plan content or preparer details. Any future review timestamp fields should also be cleared here.
	 */
	public void resetReviewState() {
		reviewedBySafetyOfficer = "";
		safetyOfficerSignature = "";
		reviewStatus = "Not reviewed";
	}

	/** @return IAP page text. */
	public String getIapPage() {
		return iapPage;
	}

	/** @param iapPage IAP page text. */
	public void setIapPage(String iapPage) {
		this.iapPage = safe(iapPage);
	}

	/**
	 * Replaces null text with an empty value.
	 *
	 * @param value input text.
	 * @return non-null text.
	 */
	private static String safe(String value) {
		return value == null ? "" : value;
	}
}
