/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Regional veterinary facility record or immutable-in-practice plan snapshot.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class VetFacility {
	private String id = UUID.randomUUID().toString();
	private String name = "";
	private String town = "";
	private String address = "";
	private List<String> contactNumbers = new ArrayList<>();
	private Boolean available24Hours;
	private String availabilityNotes = "";
	private String capabilities = "";
	private Instant verifiedAt;
	private Double latitude;
	private Double longitude;
	private String geocodingProvider = "";
	private String geocodingProvenance = "";
	private double distanceMeters = Double.NaN;
	private double durationSeconds = Double.NaN;
	private String distanceMethod = "";
	private String distanceProvenance = "";
	private Instant calculatedAt;
	private long stagingGeneration = -1;
	private String sourceName = "";

	/** Creates an empty veterinary facility with a stable identifier. */
	public VetFacility() {
	}

	/** @return stable directory identifier. */
	public String getId() {
		if (id == null || id.isBlank()) {
			id = UUID.randomUUID().toString();
		}
		return id;
	}

	/** @param id stable directory identifier. */
	public void setId(String id) {
		this.id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
	}

	/** @return facility name. */
	public String getName() {
		return name;
	}

	/** @param name facility name. */
	public void setName(String name) {
		this.name = safe(name);
	}

	/** @return town or municipality. */
	public String getTown() {
		return town;
	}

	/** @param town town or municipality. */
	public void setTown(String town) {
		this.town = safe(town);
	}

	/** @return physical address. */
	public String getAddress() {
		return address;
	}

	/**
	 * Sets the physical address and invalidates results tied to an older address.
	 *
	 * @param address physical address.
	 */
	public void setAddress(String address) {
		String next = safe(address);
		if (!this.address.equals(next)) {
			this.address = next;
			latitude = null;
			longitude = null;
			geocodingProvider = "";
			geocodingProvenance = "";
			clearDistance();
		}
	}

	/** @return all contact numbers. */
	public List<String> getContactNumbers() {
		return contactNumbers;
	}

	/** @param contactNumbers contact numbers; null becomes empty. */
	public void setContactNumbers(List<String> contactNumbers) {
		this.contactNumbers = contactNumbers == null ? new ArrayList<>() : contactNumbers;
	}

	/**
	 * Returns the confirmed 24-hour status.
	 *
	 * @return true, false, or null when unknown.
	 */
	public Boolean getAvailable24Hours() {
		return available24Hours;
	}

	/**
	 * Sets the confirmed 24-hour status; null means unknown.
	 *
	 * @param available24Hours confirmed status.
	 */
	public void setAvailable24Hours(Boolean available24Hours) {
		this.available24Hours = available24Hours;
	}

	/** @return availability notes. */
	public String getAvailabilityNotes() {
		return availabilityNotes;
	}

	/** @param availabilityNotes availability notes. */
	public void setAvailabilityNotes(String availabilityNotes) {
		this.availabilityNotes = safe(availabilityNotes);
	}

	/** @return facility capabilities. */
	public String getCapabilities() {
		return capabilities;
	}

	/** @param capabilities facility capabilities. */
	public void setCapabilities(String capabilities) {
		this.capabilities = safe(capabilities);
	}

	/** @return last verified time, or null when not recorded. */
	public Instant getVerifiedAt() {
		return verifiedAt;
	}

	/** @param verifiedAt last verified time. */
	public void setVerifiedAt(Instant verifiedAt) {
		this.verifiedAt = verifiedAt;
	}

	/** @return latitude in decimal degrees. */
	public Double getLatitude() {
		return latitude;
	}

	/** @return longitude in decimal degrees. */
	public Double getLongitude() {
		return longitude;
	}

	/** @return geocoding provider or manual entry marker. */
	public String getGeocodingProvider() {
		return geocodingProvider;
	}

	/** @return geocoding provenance. */
	public String getGeocodingProvenance() {
		return geocodingProvenance;
	}

	/**
	 * Stores validated coordinates and geocoding provenance.
	 *
	 * @param latitude latitude in decimal degrees.
	 * @param longitude longitude in decimal degrees.
	 * @param provider provider or "manual".
	 * @param provenance resolution details.
	 * @throws IllegalArgumentException when coordinates are outside valid ranges.
	 */
	public void setCoordinates(double latitude, double longitude, String provider, String provenance) {
		StagingLocation.validateCoordinates(latitude, longitude);
		this.latitude = latitude;
		this.longitude = longitude;
		this.geocodingProvider = safe(provider);
		this.geocodingProvenance = safe(provenance);
	}

	/** @return road or explicitly labeled estimated distance in meters. */
	public double getDistanceMeters() {
		return distanceMeters;
	}

	/** @return route duration in seconds, or NaN when unavailable. */
	public double getDurationSeconds() {
		return durationSeconds;
	}

	/** @return route method such as "openrouteservice/driving-car". */
	public String getDistanceMethod() {
		return distanceMethod;
	}

	/** @return distance provenance. */
	public String getDistanceProvenance() {
		return distanceProvenance;
	}

	/** @return when the route was calculated. */
	public Instant getCalculatedAt() {
		return calculatedAt;
	}

	/** @return staging generation used for the route. */
	public long getStagingGeneration() {
		return stagingGeneration;
	}

	/** @return source display name, not a path dependency. */
	public String getSourceName() {
		return sourceName;
	}

	/** @param sourceName source display name. */
	public void setSourceName(String sourceName) {
		this.sourceName = safe(sourceName);
	}

	/**
	 * Stores a route result with explicit method and provenance.
	 *
	 * @param distanceMeters distance in meters.
	 * @param durationSeconds duration in seconds, or NaN when unavailable.
	 * @param method route method.
	 * @param provenance route provenance.
	 * @param calculatedAt calculation time.
	 * @param stagingGeneration staging generation used.
	 */
	public void setDistance(double distanceMeters, double durationSeconds, String method, String provenance,
			Instant calculatedAt, long stagingGeneration) {
		this.distanceMeters = Double.isFinite(distanceMeters) && distanceMeters >= 0 ? distanceMeters : Double.NaN;
		this.durationSeconds = Double.isFinite(durationSeconds) && durationSeconds >= 0 ? durationSeconds : Double.NaN;
		this.distanceMethod = safe(method);
		this.distanceProvenance = safe(provenance);
		this.calculatedAt = calculatedAt;
		this.stagingGeneration = stagingGeneration;
	}

	/**
	 * Returns whether this route belongs to the currently selected staging base.
	 *
	 * @param currentGeneration current staging generation.
	 * @return true when its provenance matches the current base.
	 */
	public boolean hasCurrentDistance(long currentGeneration) {
		return Double.isFinite(distanceMeters) && stagingGeneration == currentGeneration;
	}

	/**
	 * Clears route fields while retaining the facility and its source data.
	 */
	public void clearDistance() {
		distanceMeters = Double.NaN;
		durationSeconds = Double.NaN;
		distanceMethod = "";
		distanceProvenance = "";
		calculatedAt = null;
		stagingGeneration = -1;
	}

	/**
	 * Creates an independent copy for an accepted plan snapshot.
	 *
	 * @return copied facility and provenance.
	 */
	public VetFacility copy() {
		VetFacility copy = new VetFacility();
		copy.setId(getId());
		copy.setName(name);
		copy.setTown(town);
		copy.setAddress(address);
		copy.setContactNumbers(new ArrayList<>(contactNumbers));
		copy.setAvailable24Hours(available24Hours);
		copy.setAvailabilityNotes(availabilityNotes);
		copy.setCapabilities(capabilities);
		copy.setVerifiedAt(verifiedAt);
		if (latitude != null && longitude != null) {
			copy.setCoordinates(latitude, longitude, geocodingProvider, geocodingProvenance);
		}
		copy.setDistance(distanceMeters, durationSeconds, distanceMethod, distanceProvenance, calculatedAt,
				stagingGeneration);
		copy.setSourceName(sourceName);
		return copy;
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
