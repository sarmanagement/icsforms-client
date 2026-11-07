/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable attribution for one locally entered record or lifecycle event.
 *
 * @param operatorName display name asserted by the person entering the record.
 * @param stationId optional device or workstation identifier.
 * @param enteredAt UTC instant when the record was entered on this device.
 * @param acceptedAt UTC instant when an authoritative host accepted the record, if any.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Provenance(String operatorName, String stationId, Instant enteredAt, Instant acceptedAt) {
	/**
	 * Jackson mix-in that prevents local persistence from reading or writing a
	 * host-accepted timestamp.
	 */
	@JsonIgnoreProperties("acceptedAt")
	public abstract static class LocalStorageMixin {
	}

	/**
	 * Normalizes optional display fields and requires an entry timestamp.
	 *
	 * @param operatorName display name asserted by the person entering the record.
	 * @param stationId optional device or workstation identifier.
	 * @param enteredAt UTC instant when the record was entered on this device.
	 * @param acceptedAt UTC instant when an authoritative host accepted the record, if any.
	 */
	public Provenance {
		operatorName = operatorName == null ? "" : operatorName.trim();
		stationId = stationId == null || stationId.isBlank() ? null : stationId.trim();
		Objects.requireNonNull(enteredAt, "enteredAt");
	}

	/**
	 * Creates local provenance without host-accepted attribution.
	 *
	 * @param operatorName display name asserted by the person entering the record.
	 * @param stationId optional device or workstation identifier.
	 * @param enteredAt UTC instant when the record was entered.
	 * @return provenance whose accepted-at value is absent.
	 */
	public static Provenance local(String operatorName, String stationId, Instant enteredAt) {
		return new Provenance(operatorName, stationId, enteredAt, null);
	}
}
