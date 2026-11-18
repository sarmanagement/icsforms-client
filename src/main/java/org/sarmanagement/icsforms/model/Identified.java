/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.model;

/**
 * Contract for persisted records with a stable identity.
 *
 * <p>Identity setters exist for Jackson deserialization and persistence migration.
 * Application code must treat an assigned identity as immutable.</p>
 */
public interface Identified {
	/**
	 * Returns the stable record identity.
	 *
	 * @return record identity, or {@code null} before legacy migration
	 */
	String getId();

	/**
	 * Sets the identity during deserialization or persistence migration.
	 *
	 * @param id stable record identity
	 */
	void setId(String id);
}
