/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.model;

/**
 * Contract for persisted records with a stable identity.
 *
 * <p>Identity setters remain public for Jackson deserialization and persistence
 * migration, but application code must treat an assigned identity as immutable.
 * The architecture test allow-lists existing UI/import assignment sites outside
 * the model and persistence packages. Copies that become new records must use
 * {@link IdentityMigration#remint(Object...)} instead of directly replacing IDs.</p>
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
