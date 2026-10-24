/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Incident-period human medical plan content for the standard ICS 206.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MedicalPlan {
	private boolean active;
	private boolean simpleMode = true;
	private String call911Procedure = "Call 911";
	private String designatedMedicalUnitRole = "";
	private String designatedMedicalUnitName = "";
	private String designatedMedicalUnitContact = "";
	private List<AidStation> aidStations = new ArrayList<>();
	private List<TransportService> transportServices = new ArrayList<>();
	private List<Hospital> hospitals = new ArrayList<>();
	private String emergencyProcedures = "";
	private boolean aviationAssetsUsed;
	private String airOperationsReminder = "";
	private String preparedByMedicalUnitLeader = "";
	private String preparerSignature = "";
	private LocalDateTime preparedAt;
	private String reviewedBySafetyOfficer = "";
	private String safetyOfficerSignature = "";
	private String reviewStatus = "Not reviewed";
	private String iapPage = "";

	/**
	 * Creates an empty, inactive medical plan.
	 */
	public MedicalPlan() {
	}

	/**
	 * Returns whether this plan is active for export and readiness reporting.
	 *
	 * @return true when active.
	 */
	public boolean isActive() {
		return active;
	}

	/**
	 * Sets whether this plan is active.
	 *
	 * @param active true to activate the plan.
	 */
	public void setActive(boolean active) {
		this.active = active;
	}

	/**
	 * Returns whether the concise emergency-instruction mode is selected.
	 *
	 * @return true for simple mode.
	 */
	public boolean isSimpleMode() {
		return simpleMode;
	}

	/**
	 * Selects simple or detailed mode without deleting either mode's data.
	 *
	 * @param simpleMode true to show the simple instruction.
	 */
	public void setSimpleMode(boolean simpleMode) {
		this.simpleMode = simpleMode;
	}

	/**
	 * Returns the editable simple-mode emergency procedure.
	 *
	 * @return emergency instruction.
	 */
	public String getCall911Procedure() {
		return call911Procedure;
	}

	/**
	 * Sets the simple-mode emergency procedure.
	 *
	 * @param call911Procedure emergency instruction.
	 */
	public void setCall911Procedure(String call911Procedure) {
		this.call911Procedure = safe(call911Procedure);
	}

	/**
	 * Returns the designated medical unit role.
	 *
	 * @return role title.
	 */
	public String getDesignatedMedicalUnitRole() {
		return designatedMedicalUnitRole;
	}

	/**
	 * Sets the designated medical unit role without implying an assigned person.
	 *
	 * @param designatedMedicalUnitRole role title.
	 */
	public void setDesignatedMedicalUnitRole(String designatedMedicalUnitRole) {
		this.designatedMedicalUnitRole = safe(designatedMedicalUnitRole);
	}

	/**
	 * Returns the optional designated medical unit person's name.
	 *
	 * @return entered person name.
	 */
	public String getDesignatedMedicalUnitName() {
		return designatedMedicalUnitName;
	}

	/**
	 * Sets the optional designated medical unit person's name.
	 *
	 * @param designatedMedicalUnitName entered person name.
	 */
	public void setDesignatedMedicalUnitName(String designatedMedicalUnitName) {
		this.designatedMedicalUnitName = safe(designatedMedicalUnitName);
	}

	/**
	 * Returns the designated medical unit contact.
	 *
	 * @return entered contact details.
	 */
	public String getDesignatedMedicalUnitContact() {
		return designatedMedicalUnitContact;
	}

	/**
	 * Sets the designated medical unit contact.
	 *
	 * @param designatedMedicalUnitContact entered contact details.
	 */
	public void setDesignatedMedicalUnitContact(String designatedMedicalUnitContact) {
		this.designatedMedicalUnitContact = safe(designatedMedicalUnitContact);
	}

	/**
	 * Returns the editable medical aid stations.
	 *
	 * @return aid stations.
	 */
	public List<AidStation> getAidStations() {
		return aidStations;
	}

	/**
	 * Replaces the medical aid stations.
	 *
	 * @param aidStations aid stations; null becomes empty.
	 */
	public void setAidStations(List<AidStation> aidStations) {
		this.aidStations = aidStations == null ? new ArrayList<>() : aidStations;
	}

	/**
	 * Returns the editable transport services.
	 *
	 * @return transport services.
	 */
	public List<TransportService> getTransportServices() {
		return transportServices;
	}

	/**
	 * Replaces the transport services.
	 *
	 * @param transportServices transport services; null becomes empty.
	 */
	public void setTransportServices(List<TransportService> transportServices) {
		this.transportServices = transportServices == null ? new ArrayList<>() : transportServices;
	}

	/**
	 * Returns the editable hospitals.
	 *
	 * @return hospitals.
	 */
	public List<Hospital> getHospitals() {
		return hospitals;
	}

	/**
	 * Replaces the hospitals.
	 *
	 * @param hospitals hospitals; null becomes empty.
	 */
	public void setHospitals(List<Hospital> hospitals) {
		this.hospitals = hospitals == null ? new ArrayList<>() : hospitals;
	}

	/**
	 * Returns special emergency procedures.
	 *
	 * @return procedure text.
	 */
	public String getEmergencyProcedures() {
		return emergencyProcedures;
	}

	/**
	 * Sets special emergency procedures.
	 *
	 * @param emergencyProcedures procedure text.
	 */
	public void setEmergencyProcedures(String emergencyProcedures) {
		this.emergencyProcedures = safe(emergencyProcedures);
	}

	/**
	 * Returns whether aviation assets are used for rescue.
	 *
	 * @return true when checked by the user.
	 */
	public boolean isAviationAssetsUsed() {
		return aviationAssetsUsed;
	}

	/**
	 * Records whether aviation assets are used.
	 *
	 * @param aviationAssetsUsed user-entered checkbox value.
	 */
	public void setAviationAssetsUsed(boolean aviationAssetsUsed) {
		this.aviationAssetsUsed = aviationAssetsUsed;
	}

	/**
	 * Returns the Air Operations coordination reminder.
	 *
	 * @return reminder text.
	 */
	public String getAirOperationsReminder() {
		return airOperationsReminder;
	}

	/**
	 * Sets the Air Operations coordination reminder.
	 *
	 * @param airOperationsReminder reminder text.
	 */
	public void setAirOperationsReminder(String airOperationsReminder) {
		this.airOperationsReminder = safe(airOperationsReminder);
	}

	/**
	 * Returns the Medical Unit Leader name entered as preparer.
	 *
	 * @return preparer name.
	 */
	public String getPreparedByMedicalUnitLeader() {
		return preparedByMedicalUnitLeader;
	}

	/**
	 * Sets the Medical Unit Leader name entered as preparer.
	 *
	 * @param preparedByMedicalUnitLeader preparer name.
	 */
	public void setPreparedByMedicalUnitLeader(String preparedByMedicalUnitLeader) {
		this.preparedByMedicalUnitLeader = safe(preparedByMedicalUnitLeader);
	}

	/**
	 * Returns the entered preparer signature.
	 *
	 * @return signature text.
	 */
	public String getPreparerSignature() {
		return preparerSignature;
	}

	/**
	 * Sets the entered preparer signature.
	 *
	 * @param preparerSignature signature text.
	 */
	public void setPreparerSignature(String preparerSignature) {
		this.preparerSignature = safe(preparerSignature);
	}

	/**
	 * Returns the preparation date and time.
	 *
	 * @return preparation timestamp, or null.
	 */
	public LocalDateTime getPreparedAt() {
		return preparedAt;
	}

	/**
	 * Sets the preparation date and time.
	 *
	 * @param preparedAt preparation timestamp.
	 */
	public void setPreparedAt(LocalDateTime preparedAt) {
		this.preparedAt = preparedAt;
	}

	/**
	 * Returns the entered Safety Officer reviewer.
	 *
	 * @return reviewer name.
	 */
	public String getReviewedBySafetyOfficer() {
		return reviewedBySafetyOfficer;
	}

	/**
	 * Sets the entered Safety Officer reviewer.
	 *
	 * @param reviewedBySafetyOfficer reviewer name.
	 */
	public void setReviewedBySafetyOfficer(String reviewedBySafetyOfficer) {
		this.reviewedBySafetyOfficer = safe(reviewedBySafetyOfficer);
	}

	/**
	 * Returns the entered Safety Officer signature.
	 *
	 * @return signature text.
	 */
	public String getSafetyOfficerSignature() {
		return safetyOfficerSignature;
	}

	/**
	 * Sets the entered Safety Officer signature.
	 *
	 * @param safetyOfficerSignature signature text.
	 */
	public void setSafetyOfficerSignature(String safetyOfficerSignature) {
		this.safetyOfficerSignature = safe(safetyOfficerSignature);
	}

	/**
	 * Returns the explicit review status; new plans remain not reviewed.
	 *
	 * @return review status.
	 */
	public String getReviewStatus() {
		return reviewStatus;
	}

	/**
	 * Sets the review status without inferring approval from a name.
	 *
	 * @param reviewStatus review status.
	 */
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

	/**
	 * Returns the IAP page entry.
	 *
	 * @return IAP page text.
	 */
	public String getIapPage() {
		return iapPage;
	}

	/**
	 * Sets the IAP page entry.
	 *
	 * @param iapPage IAP page text.
	 */
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

	/**
	 * One medical aid station entry.
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class AidStation {
		private String name = "";
		private String location = "";
		private String contactNumbers = "";
		private String frequency = "";
		private Boolean paramedicsOnSite;

		/** Creates an empty aid station. */
		public AidStation() {
		}

		/** @return station name. */
		public String getName() {
			return name;
		}

		/** @param name station name. */
		public void setName(String name) {
			this.name = safe(name);
		}

		/** @return station location. */
		public String getLocation() {
			return location;
		}

		/** @param location station location. */
		public void setLocation(String location) {
			this.location = safe(location);
		}

		/** @return contact numbers. */
		public String getContactNumbers() {
			return contactNumbers;
		}

		/** @param contactNumbers contact numbers. */
		public void setContactNumbers(String contactNumbers) {
			this.contactNumbers = safe(contactNumbers);
		}

		/** @return radio frequency. */
		public String getFrequency() {
			return frequency;
		}

		/** @param frequency radio frequency. */
		public void setFrequency(String frequency) {
			this.frequency = safe(frequency);
		}

		/** @return paramedic presence, or null when unknown. */
		public Boolean getParamedicsOnSite() {
			return paramedicsOnSite;
		}

		/** @param paramedicsOnSite paramedic presence, or null when unknown. */
		public void setParamedicsOnSite(Boolean paramedicsOnSite) {
			this.paramedicsOnSite = paramedicsOnSite;
		}
	}

	/**
	 * One ambulance or other medical transport service.
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class TransportService {
		private String service = "";
		private String location = "";
		private String contactNumbers = "";
		private String frequency = "";
		private String airOrGround = "";
		private String levelOfService = "";

		/** Creates an empty transport service. */
		public TransportService() {
		}

		/** @return service name. */
		public String getService() {
			return service;
		}

		/** @param service service name. */
		public void setService(String service) {
			this.service = safe(service);
		}

		/** @return service location. */
		public String getLocation() {
			return location;
		}

		/** @param location service location. */
		public void setLocation(String location) {
			this.location = safe(location);
		}

		/** @return contact numbers. */
		public String getContactNumbers() {
			return contactNumbers;
		}

		/** @param contactNumbers contact numbers. */
		public void setContactNumbers(String contactNumbers) {
			this.contactNumbers = safe(contactNumbers);
		}

		/** @return radio frequency. */
		public String getFrequency() {
			return frequency;
		}

		/** @param frequency radio frequency. */
		public void setFrequency(String frequency) {
			this.frequency = safe(frequency);
		}

		/** @return air or ground transport. */
		public String getAirOrGround() {
			return airOrGround;
		}

		/** @param airOrGround air or ground transport. */
		public void setAirOrGround(String airOrGround) {
			this.airOrGround = safe(airOrGround);
		}

		/** @return human ALS/BLS or another entered service capability. */
		public String getLevelOfService() {
			return levelOfService;
		}

		/** @param levelOfService entered service capability. */
		public void setLevelOfService(String levelOfService) {
			this.levelOfService = safe(levelOfService);
		}
	}

	/**
	 * One human hospital entry with nullable medical capability answers.
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Hospital {
		private String name = "";
		private String address = "";
		private String contactNumbers = "";
		private String frequency = "";
		private Double latitude;
		private Double longitude;
		private String coordinateFormat = "";
		private String helipadCoordinates = "";
		private String airTravelTime = "";
		private String groundTravelTime = "";
		private String airTravelProvenance = "";
		private String groundTravelProvenance = "";
		private Boolean traumaCenter;
		private String traumaLevel = "";
		private Boolean burnCenter;
		private Boolean helipad;

		/** Creates an empty hospital. */
		public Hospital() {
		}

		/** @return hospital name. */
		public String getName() {
			return name;
		}

		/** @param name hospital name. */
		public void setName(String name) {
			this.name = safe(name);
		}

		/** @return physical address. */
		public String getAddress() {
			return address;
		}

		/** @param address physical address. */
		public void setAddress(String address) {
			this.address = safe(address);
		}

		/** @return contact numbers. */
		public String getContactNumbers() {
			return contactNumbers;
		}

		/** @param contactNumbers contact numbers. */
		public void setContactNumbers(String contactNumbers) {
			this.contactNumbers = safe(contactNumbers);
		}

		/** @return radio frequency. */
		public String getFrequency() {
			return frequency;
		}

		/** @param frequency radio frequency. */
		public void setFrequency(String frequency) {
			this.frequency = safe(frequency);
		}

		/** @return latitude in decimal degrees. */
		public Double getLatitude() {
			return latitude;
		}

		/** @param latitude latitude in decimal degrees. */
		public void setLatitude(Double latitude) {
			this.latitude = latitude;
		}

		/** @return longitude in decimal degrees. */
		public Double getLongitude() {
			return longitude;
		}

		/** @param longitude longitude in decimal degrees. */
		public void setLongitude(Double longitude) {
			this.longitude = longitude;
		}

		/** @return coordinate display format. */
		public String getCoordinateFormat() {
			return coordinateFormat;
		}

		/** @param coordinateFormat coordinate display format. */
		public void setCoordinateFormat(String coordinateFormat) {
			this.coordinateFormat = safe(coordinateFormat);
		}

		/** @return helipad coordinates. */
		public String getHelipadCoordinates() {
			return helipadCoordinates;
		}

		/** @param helipadCoordinates helipad coordinates. */
		public void setHelipadCoordinates(String helipadCoordinates) {
			this.helipadCoordinates = safe(helipadCoordinates);
		}

		/** @return air travel time, manual or provenance-labeled. */
		public String getAirTravelTime() {
			return airTravelTime;
		}

		/** @param airTravelTime air travel time. */
		public void setAirTravelTime(String airTravelTime) {
			this.airTravelTime = safe(airTravelTime);
		}

		/** @return ground travel time, manual or provenance-labeled. */
		public String getGroundTravelTime() {
			return groundTravelTime;
		}

		/** @param groundTravelTime ground travel time. */
		public void setGroundTravelTime(String groundTravelTime) {
			this.groundTravelTime = safe(groundTravelTime);
		}

		/** @return provenance for the air travel time. */
		public String getAirTravelProvenance() {
			return airTravelProvenance;
		}

		/** @param airTravelProvenance provenance for the air travel time. */
		public void setAirTravelProvenance(String airTravelProvenance) {
			this.airTravelProvenance = safe(airTravelProvenance);
		}

		/** @return provenance for the ground travel time. */
		public String getGroundTravelProvenance() {
			return groundTravelProvenance;
		}

		/** @param groundTravelProvenance provenance for the ground travel time. */
		public void setGroundTravelProvenance(String groundTravelProvenance) {
			this.groundTravelProvenance = safe(groundTravelProvenance);
		}

		/** @return trauma center status, or null when unknown. */
		public Boolean getTraumaCenter() {
			return traumaCenter;
		}

		/** @param traumaCenter trauma center status, or null when unknown. */
		public void setTraumaCenter(Boolean traumaCenter) {
			this.traumaCenter = traumaCenter;
		}

		/** @return trauma level. */
		public String getTraumaLevel() {
			return traumaLevel;
		}

		/** @param traumaLevel trauma level. */
		public void setTraumaLevel(String traumaLevel) {
			this.traumaLevel = safe(traumaLevel);
		}

		/** @return burn center status, or null when unknown. */
		public Boolean getBurnCenter() {
			return burnCenter;
		}

		/** @param burnCenter burn center status, or null when unknown. */
		public void setBurnCenter(Boolean burnCenter) {
			this.burnCenter = burnCenter;
		}

		/** @return helipad status, or null when unknown. */
		public Boolean getHelipad() {
			return helipad;
		}

		/** @param helipad helipad status, or null when unknown. */
		public void setHelipad(Boolean helipad) {
			this.helipad = helipad;
		}
	}
}
