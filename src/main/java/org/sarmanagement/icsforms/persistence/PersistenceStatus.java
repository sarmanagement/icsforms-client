/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.persistence;

/**
 * Binding-scoped status. Durability means acceptance by the configured store,
 * independently of any future synchronization with an authority.
 *
 * @param incidentId incident described by this status
 * @param generation binding generation described by this status
 * @param editVersion latest local edit version
 * @param revision last accepted store revision
 * @param durability durability state
 * @param synchronization independent synchronization state
 * @param failure actionable failure, or null
 */
public record PersistenceStatus(IncidentId incidentId, long generation, long editVersion,
		RevisionToken revision, Durability durability, Synchronization synchronization, RuntimeException failure) {
	/** Durability of the current local work in the configured store. */
	public enum Durability {
		PENDING, SAVING, SAVED, CONFLICTED, FAILED, UNAVAILABLE
	}

	/** Reserved, independent authority-synchronization dimension. */
	public enum Synchronization {
		NOT_APPLICABLE
	}
}
