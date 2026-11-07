/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.persistence;

import org.sarmanagement.icsforms.model.AppData;

/**
 * Compatibility bridge for legacy stores whose revisions live in AppData.
 * Legacy save implementations must already atomically compare document revisions;
 * this adapter does not add concurrency protection or bridge tokenless deletion.
 *
 * @deprecated new stores must implement snapshot/token operations directly
 */
@Deprecated
final class LegacyIncidentStoreAdapter {
	/** Prevents instantiation of this compatibility utility. */
	private LegacyIncidentStoreAdapter() {
	}

	/**
	 * Wraps a legacy numeric revision.
	 *
	 * @param data legacy document
	 * @return opaque revision
	 */
	static RevisionToken revision(AppData data) {
		if (data == null) {
			throw new StoreValidationException("Caller snapshot is required");
		}
		return revision(data.getRevision());
	}

	/**
	 * Wraps a legacy numeric summary revision.
	 *
	 * @param revision legacy revision
	 * @return opaque revision
	 */
	static RevisionToken revision(long revision) {
		return new RevisionToken(Long.toString(revision));
	}

	/**
	 * Checks caller metadata before invoking the existing legacy save integration.
	 * The legacy implementation remains responsible for comparing its stored revision;
	 * a new unconditional load would break integrations implementing only legacy save.
	 *
	 * @param store legacy implementation
	 * @param id incident identity
	 * @param data updated document
	 * @param expected expected revision
	 * @return committed revision
	 */
	static RevisionToken save(IncidentStore store, IncidentId id, AppData data, RevisionToken expected) {
		RevisionToken caller = revision(data);
		if (!caller.equals(expected)) {
			throw new StoreValidationException("Expected revision does not match the legacy caller snapshot: " + id);
		}
		store.save(id, data);
		return revision(data);
	}

}
