/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

/**
 * Incident-owned location shared by operational workspaces.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class IncidentLocation {
	private String id = UUID.randomUUID().toString();
	private String type = "Other";
	private String name = "";
	private String address = "";

	/**
	 * Creates an empty incident location.
	 */
	public IncidentLocation() {
	}

	/**
	 * Returns the stable location identifier.
	 *
	 * @return location identifier.
	 */
	public String getId() {
		return id;
	}

	/**
	 * Sets the stable location identifier.
	 *
	 * @param id location identifier; blank values are replaced with a generated identifier.
	 */
	public void setId(String id) {
		this.id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
	}

	/**
	 * Returns the operational location category.
	 *
	 * @return location category.
	 */
	public String getType() {
		return type;
	}

	/**
	 * Sets the operational location category.
	 *
	 * @param type location category.
	 */
	public void setType(String type) {
		this.type = safe(type);
	}

	/**
	 * Returns the location name.
	 *
	 * @return location name.
	 */
	public String getName() {
		return name;
	}

	/**
	 * Sets the location name.
	 *
	 * @param name location name.
	 */
	public void setName(String name) {
		this.name = safe(name);
	}

	/**
	 * Returns the street or descriptive address.
	 *
	 * @return location address.
	 */
	public String getAddress() {
		return address;
	}

	/**
	 * Sets the location address.
	 *
	 * @param address location address.
	 */
	public void setAddress(String address) {
		this.address = safe(address);
	}

	/**
	 * Replaces null values with empty text.
	 *
	 * @param value input text.
	 * @return non-null text.
	 */
	private static String safe(String value) {
		return value == null ? "" : value;
	}
}
