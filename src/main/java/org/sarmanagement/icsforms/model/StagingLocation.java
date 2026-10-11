/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

/**
 * Address-first incident staging/base location shared by medical plans.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class StagingLocation {
	private String label = "";
	private String address = "";
	private String resolvedAddress = "";
	private Double latitude;
	private Double longitude;
	private String geocodingProvider = "";
	private String geocodingProvenance = "";
	private Instant resolvedAt;
	private long generation;

	/** Creates an empty staging/base location. */
	public StagingLocation() {
	}

	/**
	 * Returns the staging/base label.
	 *
	 * @return label.
	 */
	public String getLabel() {
		return label;
	}

	/**
	 * Sets the staging/base label.
	 *
	 * @param label label.
	 */
	public void setLabel(String label) {
		this.label = safe(label);
	}

	/**
	 * Returns the entered address.
	 *
	 * @return address.
	 */
	public String getAddress() {
		return address;
	}

	/**
	 * Sets the address and invalidates any result resolved for an older address.
	 *
	 * @param address entered address.
	 */
	public void setAddress(String address) {
		String next = safe(address);
		if (!this.address.equals(next)) {
			this.address = next;
			invalidateResolution();
			generation++;
		}
	}

	/**
	 * Returns the address confirmed from a geocoder candidate.
	 *
	 * @return resolved address.
	 */
	public String getResolvedAddress() {
		return resolvedAddress;
	}

	/**
	 * Returns the latitude of the resolved or corrected location.
	 *
	 * @return latitude, or null when unavailable.
	 */
	public Double getLatitude() {
		return latitude;
	}

	/**
	 * Returns the longitude of the resolved or corrected location.
	 *
	 * @return longitude, or null when unavailable.
	 */
	public Double getLongitude() {
		return longitude;
	}

	/**
	 * Returns the geocoder or manual-entry provenance.
	 *
	 * @return provenance.
	 */
	public String getGeocodingProvider() {
		return geocodingProvider;
	}

	/**
	 * Returns the geocoding details.
	 *
	 * @return provenance details.
	 */
	public String getGeocodingProvenance() {
		return geocodingProvenance;
	}

	/**
	 * Returns when coordinates were resolved or manually confirmed.
	 *
	 * @return result time, or null when unresolved.
	 */
	public Instant getResolvedAt() {
		return resolvedAt;
	}

	/**
	 * Returns the monotonically increasing location generation.
	 *
	 * @return location generation.
	 */
	public long getGeneration() {
		return generation;
	}

	/**
	 * Stores validated coordinates and their source.
	 *
	 * @param resolvedAddress confirmed address.
	 * @param latitude latitude in decimal degrees.
	 * @param longitude longitude in decimal degrees.
	 * @param provider provider or "manual".
	 * @param provenance resolution details.
	 * @param resolvedAt resolution time.
	 */
	public void setResolution(String resolvedAddress, double latitude, double longitude, String provider,
			String provenance, Instant resolvedAt) {
		validateCoordinates(latitude, longitude);
		this.resolvedAddress = safe(resolvedAddress);
		this.latitude = latitude;
		this.longitude = longitude;
		this.geocodingProvider = safe(provider);
		this.geocodingProvenance = safe(provenance);
		this.resolvedAt = resolvedAt;
		generation++;
	}

	/**
	 * Clears resolved location details after an address edit.
	 */
	public void clearResolution() {
		invalidateResolution();
		generation++;
	}

	/**
	 * Returns whether the coordinates are both present and in valid geographic
	 * ranges.
	 *
	 * @return true when latitude and longitude are valid.
	 */
	public boolean hasValidCoordinates() {
		return latitude != null && longitude != null && latitude >= -90 && latitude <= 90 && longitude >= -180
				&& longitude <= 180;
	}

	/**
	 * Validates a coordinate pair.
	 *
	 * @param latitude latitude in decimal degrees.
	 * @param longitude longitude in decimal degrees.
	 * @throws IllegalArgumentException when either coordinate is out of range.
	 */
	public static void validateCoordinates(double latitude, double longitude) {
		if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90 || !Double.isFinite(longitude)
				|| longitude < -180 || longitude > 180) {
			throw new IllegalArgumentException("Coordinates must be finite latitude/longitude values in range");
		}
	}

	/**
	 * Clears provider data that no longer describes the entered address.
	 */
	private void invalidateResolution() {
		resolvedAddress = "";
		latitude = null;
		longitude = null;
		geocodingProvider = "";
		geocodingProvenance = "";
		resolvedAt = null;
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
