/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.Ics203Form;

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
	 * Resolves the shared Medical Unit Leader name, including legacy ICS 203 data.
	 *
	 * @param data incident data.
	 * @return the first assigned Medical Unit Leader name.
	 */
	static String medicalUnitLeaderName(AppData data) {
		return firstNonBlank(data.getOrganizationalChart().getMedicalUnitLeaderName(),
				data.getOrganizationalChart().getAssignmentList()
						.getPosition(Ics203Form.Position.MEDICAL_UNIT_LEADER),
				data.getMedicalPlan().getDesignatedMedicalUnitName(),
				data.getMedicalPlan().getPreparedByMedicalUnitLeader(),
				data.getCanineMedicalPlan().getPreparedByMedicalUnitLeader());
	}

	/**
	 * Updates every shared Medical Unit Leader display location.
	 *
	 * @param data incident data.
	 * @param name selected Medical Unit Leader name.
	 */
	static void setMedicalUnitLeaderName(AppData data, String name) {
		String value = safe(name);
		data.getOrganizationalChart().setMedicalUnitLeaderName(value);
		data.getOrganizationalChart().getAssignmentList()
				.setPosition(Ics203Form.Position.MEDICAL_UNIT_LEADER, value);
		data.getMedicalPlan().setDesignatedMedicalUnitName(value);
		data.getMedicalPlan().setPreparedByMedicalUnitLeader(value);
		data.getCanineMedicalPlan().setPreparedByMedicalUnitLeader(value);
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
	 * Returns the first nonblank value in the given ordered list.
	 *
	 * @param values candidate values.
	 * @return trimmed first nonblank value, or an empty string.
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
	 * Converts a nullable name into safe text.
	 *
	 * @param value source text.
	 * @return non-null text.
	 */
	private static String safe(String value) {
		return value == null ? "" : value.trim();
	}
}
