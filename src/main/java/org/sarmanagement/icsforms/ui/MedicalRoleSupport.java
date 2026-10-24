/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.MedicalUnitLeaderAssignment;

/**
 * Synchronizes medical-plan role names with their shared organizational-chart assignments.
 */
final class MedicalRoleSupport {
	/**
	 * Prevents construction of this utility class.
	 */
	private MedicalRoleSupport() {
	}

	/**
	 * Returns the authoritative Medical Unit Leader name.
	 *
	 * @param data incident data.
	 * @return assigned Medical Unit Leader name.
	 */
	static String medicalUnitLeaderName(AppData data) {
		return data.getMedicalUnitLeaderAssignment().getName();
	}

	/**
	 * Updates the authoritative Medical Unit Leader name without changing contact data.
	 *
	 * @param data incident data.
	 * @param name assigned Medical Unit Leader name.
	 */
	static void setMedicalUnitLeaderName(AppData data, String name) {
		data.getMedicalUnitLeaderAssignment().setName(name);
	}

	/**
	 * Updates the authoritative role name and contacts from the Org Chart editor.
	 *
	 * @param data incident data.
	 * @param name assigned Medical Unit Leader.
	 * @param radioChannel role radio channel.
	 * @param phoneNumber role phone number.
	 */
	static void setMedicalUnitLeader(AppData data, String name, String radioChannel, String phoneNumber) {
		MedicalUnitLeaderAssignment assignment = data.getMedicalUnitLeaderAssignment();
		assignment.setName(name);
		assignment.setRadioChannel(radioChannel);
		assignment.setPhoneNumber(phoneNumber);
	}

	/**
	 * Updates only the Medical Unit Leader radio contact.
	 *
	 * @param data incident data.
	 * @param radioChannel role radio channel.
	 */
	static void setMedicalUnitLeaderRadio(AppData data, String radioChannel) {
		data.getMedicalUnitLeaderAssignment().setRadioChannel(radioChannel);
	}

	/**
	 * Updates only the Medical Unit Leader phone contact.
	 *
	 * @param data incident data.
	 * @param phoneNumber role phone number.
	 */
	static void setMedicalUnitLeaderPhone(AppData data, String phoneNumber) {
		data.getMedicalUnitLeaderAssignment().setPhoneNumber(phoneNumber);
	}

	/**
	 * Resolves the shared Safety Officer name, preferring the Org Chart assignment.
	 *
	 * @param data incident data.
	 * @return the first assigned Safety Officer name.
	 */
	static String safetyOfficerName(AppData data) {
		return firstNonBlank(data.getOrganizationalChart().getSafetyOfficerName(),
				data.getMedicalPlan().getReviewedBySafetyOfficer(),
				data.getCanineMedicalPlan().getReviewedBySafetyOfficer());
	}

	/**
	 * Updates the shared Safety Officer assignment and medical-plan displays.
	 *
	 * @param data incident data.
	 * @param name selected Safety Officer name.
	 */
	static void setSafetyOfficerName(AppData data, String name) {
		String value = safe(name);
		data.getOrganizationalChart().setSafetyOfficerName(value);
		data.getMedicalPlan().setReviewedBySafetyOfficer(value);
		data.getCanineMedicalPlan().setReviewedBySafetyOfficer(value);
	}

	/**
	 * Returns the first nonblank value in the ordered candidates.
	 *
	 * @param values candidate values.
	 * @return trimmed first nonblank value, or blank.
	 */
	private static String firstNonBlank(String... values) {
		for (String value : values) {
			if (value != null && !value.isBlank()) {
				return value.trim();
			}
		}
		return "";
	}

	/**
	 * Converts nullable names to trimmed, non-null text.
	 *
	 * @param value source name.
	 * @return trimmed name or blank.
	 */
	private static String safe(String value) {
		return value == null ? "" : value.trim();
	}
}
