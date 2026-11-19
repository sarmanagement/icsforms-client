/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.model;

/**
 * Contract for persisted records with a stable identity.
 *
 * <p>Identity setters reject replacement after a nonblank identity is assigned.
 * Jackson binds the canonical identity field directly. Persistence migration,
 * reminting, and explicit detached-copy operations use the corresponding
 * {@link IdentityMigration} helpers. Application code must treat assigned
 * identities as immutable, and new record graphs must use
 * {@link IdentityMigration#remint(Object...)}.</p>
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
