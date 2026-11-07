/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.model;

/**
 * Operator identity asserted by the current application user.
 *
 * @param operatorName display name entered locally, or blank when unknown.
 * @param stationId optional device or workstation identifier.
 */
public record OperatorContext(String operatorName, String stationId) {
	/**
	 * Normalizes a missing operator name and optional station identifier.
	 *
	 * @param operatorName display name entered locally, or blank when unknown.
	 * @param stationId optional device or workstation identifier.
	 */
	public OperatorContext {
		operatorName = operatorName == null ? "" : operatorName.trim();
		stationId = stationId == null || stationId.isBlank() ? null : stationId.trim();
	}

	/**
	 * Returns the empty context used by default to preserve standalone behavior.
	 *
	 * @return a context with no operator or station identity.
	 */
	public static OperatorContext empty() {
		return new OperatorContext("", null);
	}

	/**
	 * Returns whether this context contains any self-asserted attribution.
	 *
	 * @return {@code true} when an operator name or station identifier is present.
	 */
	public boolean isEmpty() {
		return operatorName.isBlank() && stationId == null;
	}
}
