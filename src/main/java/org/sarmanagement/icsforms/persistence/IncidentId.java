package org.sarmanagement.icsforms.persistence;

import java.util.UUID;

/**
 * Stable, store-independent identity of an incident.
 *
 * @param value
 *            canonical UUID string.
 */
public record IncidentId(String value) {
	/**
	 * Validates and canonicalizes the incident identifier.
	 *
	 * @param value
	 *            UUID string.
	 */
	public IncidentId {
		value = UUID.fromString(value).toString();
	}

	/**
	 * Generates a new incident identifier.
	 *
	 * @return new identifier.
	 */
	public static IncidentId newId() {
		return new IncidentId(UUID.randomUUID().toString());
	}

	/**
	 * Parses an existing identifier.
	 *
	 * @param value
	 *            UUID string.
	 * @return validated identifier.
	 */
	public static IncidentId of(String value) {
		return new IncidentId(value);
	}
}
