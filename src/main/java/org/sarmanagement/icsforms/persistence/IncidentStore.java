/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.persistence;

import org.sarmanagement.icsforms.model.AppData;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** Store-neutral incident persistence contract. */
public interface IncidentStore extends AutoCloseable {
	/**
	 * Creates an incident without replacing an existing one.
	 *
	 * @param data
	 *            document to create.
	 * @return assigned identifier.
	 */
	IncidentId create(AppData data);

	/**
	 * Loads a non-deleted incident.
	 *
	 * @param id
	 *            incident identifier.
	 * @return incident document.
	 */
	AppData load(IncidentId id);

	/**
	 * Saves a document when its revision matches the stored revision.
	 *
	 * @param id
	 *            incident identifier.
	 * @param data
	 *            updated document.
	 */
	void save(IncidentId id, AppData data);

	/**
	 * Lists non-deleted incidents, most recently updated first.
	 *
	 * @return incident summaries.
	 */
	List<IncidentSummary> list();

	/**
	 * Tombstones an incident without erasing retained backups. Implementations must
	 * reject deletion while the incident is locked for editing, including local locks.
	 *
	 * @param id
	 *            incident identifier.
	 */
	void delete(IncidentId id);

	/**
	 * Imports JSON as a distinct incident.
	 *
	 * @param jsonFile
	 *            external document.
	 * @return imported identifier, or empty if no document was selected.
	 * @throws ImportValidationException
	 *             when the external document fails validation.
	 */
	Optional<IncidentId> importFile(Path jsonFile);

	/**
	 * Returns warnings from the last successful import, or an empty list when none exist.
	 *
	 * @return immutable import diagnostic snapshot
	 */
	default List<ImportValidationException.Problem> getLastImportProblems() {
		return List.of();
	}

	/**
	 * Exports an incident document.
	 *
	 * @param id
	 *            incident identifier.
	 * @param target
	 *            target JSON file.
	 */
	void exportFile(IncidentId id, Path target);

	/**
	 * Reports whether this store supports safe, non-overwriting backups.
	 *
	 * @return true when {@link #backupFile} is supported.
	 */
	default boolean supportsBackup() {
		return false;
	}

	/**
	 * Backs up stored content to a new external file without changing the incident.
	 * Existing targets must never be replaced. Unsupported backends retain their
	 * existing export API but do not expose management backups.
	 *
	 * @param id incident identifier.
	 * @param target new external JSON file.
	 * @throws UnsupportedOperationException when safe backups are not implemented.
	 */
	default void backupFile(IncidentId id, Path target) {
		throw new UnsupportedOperationException("This store does not support safe incident backups.");
	}

	/**
	 * Describes the store.
	 *
	 * @return store type and location.
	 */
	StoreDescriptor describe();

	/** Releases store resources and incident locks. */
	@Override
	void close();
}
