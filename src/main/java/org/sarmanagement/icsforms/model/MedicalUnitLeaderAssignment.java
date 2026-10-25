/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Authoritative incident assignment and contact details for the Medical Unit Leader.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MedicalUnitLeaderAssignment {
	private String name = "";
	private String radioChannel = "";
	private String phoneNumber = "";
	private List<String> preservedLegacyConflicts = new ArrayList<>();

	/**
	 * Creates an unassigned Medical Unit Leader role.
	 */
	public MedicalUnitLeaderAssignment() {
	}

	/**
	 * Returns the assigned person's name.
	 *
	 * @return Medical Unit Leader name.
	 */
	public String getName() {
		return name;
	}

	/**
	 * Sets the assigned person's name.
	 *
	 * @param name Medical Unit Leader name.
	 */
	public void setName(String name) {
		this.name = safe(name);
	}

	/**
	 * Returns the role's radio contact information.
	 *
	 * @return radio channel.
	 */
	public String getRadioChannel() {
		return radioChannel;
	}

	/**
	 * Sets the role's radio contact information.
	 *
	 * @param radioChannel radio channel.
	 */
	public void setRadioChannel(String radioChannel) {
		this.radioChannel = safe(radioChannel);
	}

	/**
	 * Returns the role's phone contact information.
	 *
	 * @return phone number.
	 */
	public String getPhoneNumber() {
		return phoneNumber;
	}

	/**
	 * Sets the role's phone contact information.
	 *
	 * @param phoneNumber phone number.
	 */
	public void setPhoneNumber(String phoneNumber) {
		this.phoneNumber = safe(phoneNumber);
	}

	/**
	 * Returns conflicting legacy values retained during migration.
	 *
	 * @return preserved source/value descriptions.
	 */
	public List<String> getPreservedLegacyConflicts() {
		return preservedLegacyConflicts;
	}

	/**
	 * Replaces preserved legacy conflict descriptions.
	 *
	 * @param preservedLegacyConflicts source/value descriptions; null becomes empty.
	 */
	public void setPreservedLegacyConflicts(List<String> preservedLegacyConflicts) {
		this.preservedLegacyConflicts = preservedLegacyConflicts == null
				? new ArrayList<>() : new ArrayList<>(preservedLegacyConflicts);
	}

	/**
	 * Retains a legacy source value when it conflicts with the canonical assignment.
	 *
	 * @param source legacy field path.
	 * @param value original legacy value.
	 */
	public void preserveLegacyConflict(String source, String value) {
		String legacyValue = safe(value);
		String conflict = safe(source) + ": " + legacyValue;
		if (!legacyValue.isBlank() && !preservedLegacyConflicts.contains(conflict)) {
			preservedLegacyConflicts.add(conflict);
		}
	}

	/**
	 * Converts nullable text to a non-null value.
	 *
	 * @param value source text.
	 * @return non-null text.
	 */
	private String safe(String value) {
		return value == null ? "" : value;
	}
}
