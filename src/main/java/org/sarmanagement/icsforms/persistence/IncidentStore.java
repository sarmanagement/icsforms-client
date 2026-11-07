/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.persistence;

import org.sarmanagement.icsforms.model.AppData;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Store-neutral whole-document persistence. An incident's full identity is the store
 * scope plus {@link IncidentId}; a revision token is meaningful only for that identity.
 * Loads are detached. Create may assign identity and normalize caller metadata only
 * after success; save may update caller metadata/identity normalization only after
 * success. Failures must leave caller-owned data unchanged. Missing and deleted
 * incidents throw {@link IncidentNotFoundException}; creation cannot reuse retired IDs.
 * Token-aware save and delete compare atomically and need no caller-acquired locks.
 * Invalid documents/arguments fail with {@link StoreValidationException}; revision
 * conflicts retain expected/current tokens. {@link StoreUnavailableException} means
 * the store cannot be used or reached, while {@link StorePersistenceException} means
 * a read/write operation failed on an otherwise available store.
 *
 * <p>Close is idempotent and releases resources and owned locks. Operations after close
 * fail with {@link StoreUnavailableException}; descriptive capability queries may remain
 * available; releasing an already released lock remains an idempotent cleanup.
 * Optional locks are advisory, not a substitute for token checks.
 *
 * <p>Local import/export paths are transfer destinations, never incident identities.
 * Import creates a fresh identity and leaves its source unchanged; export writes a
 * detached snapshot without modifying the incident. Optional backups preserve stored
 * content and never overwrite their destination or change live data. Implementations
 * without local transfers may throw {@link UnsupportedOperationException}.
 *
 * <p>This contract covers whole-document commits only; partial saves, outboxes,
 * synchronization cursors and remote protocols remain outside its scope. Legacy
 * implementation bridges retain their original concurrency guarantees; new backends
 * must override the token-aware methods to provide atomic compare-and-write. A legacy
 * save bridge requires an already atomic document-revision save implementation;
 * legacy tokenless deletion has no safe bridge and token-aware deletion is unsupported
 * unless the backend overrides it.
 */
public interface IncidentStore extends AutoCloseable {
	/**
	 * Creates an incident without replacing an existing one.
	 *
	 * @param data
	 *            document to create.
	 * @return assigned identifier.
	 * @throws StoreValidationException when the document is invalid
	 * @throws DuplicateIncidentException when the identity already exists or is retired
	 * @throws StoreUnavailableException when the store cannot be used
	 * @throws StorePersistenceException when creation cannot be persisted
	 */
	IncidentId create(AppData data);

	/**
	 * Loads a non-deleted incident.
	 *
	 * @param id
	 *            incident identifier.
	 * @return incident document.
	 * @throws IncidentNotFoundException when missing or deleted
	 * @throws StoreUnavailableException when the store cannot be used
	 * @throws StorePersistenceException when the document cannot be read
	 */
	@Deprecated
	default AppData load(IncidentId id) {
		return loadSnapshot(id).data();
	}

	/**
	 * Loads detached data and its matching opaque revision.
	 *
	 * @param id incident identity
	 * @return detached snapshot
	 * @throws IncidentNotFoundException when missing or deleted
	 * @throws StoreValidationException when the stored schema is unsupported
	 * @throws StoreUnavailableException when the store cannot be used
	 * @throws StorePersistenceException when the document cannot be read
	 */
	default StoredIncident loadSnapshot(IncidentId id) {
		AppData data = load(id);
		return new StoredIncident(data, LegacyIncidentStoreAdapter.revision(data));
	}

	/**
	 * Saves a document when its revision matches the stored revision.
	 *
	 * @param id
	 *            incident identifier.
	 * @param data
	 *            updated document.
	 * @throws StoreUnavailableException when the store cannot be used
	 * @throws StorePersistenceException when the document cannot be persisted
	 * @throws UnsupportedOperationException when only token-aware save is implemented
	 */
	@Deprecated
	default void save(IncidentId id, AppData data) {
		throw new UnsupportedOperationException("Use token-aware save.");
	}

	/**
	 * Atomically saves against the supplied token. Native implementations never
	 * interpret AppData's legacy revision; the default numeric compatibility bridge
	 * requires a legacy save implementation that already compares revisions atomically.
	 *
	 * @param id incident identity matching the document
	 * @param data caller's edited document
	 * @param expected revision of the edited snapshot
	 * @return newly committed opaque revision
	 * @throws StoreConflictException when the current revision differs
	 * @throws StoreValidationException when document identity or content is invalid
	 * @throws IncidentNotFoundException when missing or deleted
	 * @throws StoreUnavailableException when the store cannot be used
	 * @throws StorePersistenceException when the updated document cannot be persisted
	 */
	default RevisionToken save(IncidentId id, AppData data, RevisionToken expected) {
		return LegacyIncidentStoreAdapter.save(this, id, data, expected);
	}

	/**
	 * Lists non-deleted incidents, most recently updated first.
	 *
	 * @return incident summaries.
	 * @throws StoreUnavailableException when the store cannot be used
	 * @throws StorePersistenceException when the listing cannot be read
	 */
	List<IncidentSummary> list();

	/**
	 * Tombstones an incident without erasing retained backups. Implementations must
	 * reject deletion while the incident is locked for editing, including local locks.
	 *
	 * @param id
	 *            incident identifier.
	 * @throws StoreUnavailableException when the store cannot be used
	 * @throws StorePersistenceException when deletion cannot be persisted
	 * @throws UnsupportedOperationException when safe tokenless deletion is unavailable
	 */
	@Deprecated
	default void delete(IncidentId id) {
		throw new UnsupportedOperationException("Use token-aware delete; tokenless deletion is unsafe.");
	}

	/**
	 * Atomically tombstones the revision observed by the caller, retaining backups.
	 * Advisory edit locks, including locally owned locks, exclude deletion.
	 *
	 * @param id incident identity
	 * @param expected last observed revision
	 * @throws StoreConflictException when the revision differs or editing excludes deletion
	 * @throws IncidentNotFoundException when missing or already deleted
	 * @throws StoreValidationException when the expected revision is invalid
	 * @throws StoreUnavailableException when the store cannot be used
	 * @throws StorePersistenceException when deletion cannot be persisted
	 * @throws UnsupportedOperationException when a legacy backend has not supplied atomic token-aware deletion
	 */
	default void delete(IncidentId id, RevisionToken expected) {
		throw new UnsupportedOperationException("This legacy store does not support atomic token-aware deletion.");
	}

	/**
	 * Returns optional advisory edit-lock support.
	 *
	 * @return empty for lockless stores
	 */
	default Optional<EditLockSupport> editLocks() {
		return Optional.empty();
	}

	/**
	 * Returns the revision issued for this exact caller-owned document by create,
	 * load or successful save. This accessor must not reload the incident or return
	 * a newer stored revision: a stale constructor input must remain stale. The
	 * default bridges legacy numeric metadata; nonnumeric backends must override it
	 * to retain issued tokens independently of AppData's legacy revision field.
	 *
	 * @param data caller-owned document previously created, loaded or saved
	 * @return token associated with that caller snapshot
	 * @throws StoreValidationException when a backend cannot identify the caller snapshot
	 */
	default RevisionToken revisionToken(AppData data) {
		return LegacyIncidentStoreAdapter.revision(data);
	}

	/**
	 * Returns a local document path only for legacy file integrations.
	 * The absence of a path does not imply that an incident is missing.
	 *
	 * @param id incident identity
	 * @return local primary document path when supported
	 */
	@Deprecated
	default Optional<Path> localFile(IncidentId id) {
		return Optional.empty();
	}

	/**
	 * Imports JSON as a distinct incident.
	 *
	 * @param jsonFile
	 *            external document.
	 * @return imported identifier, or empty if no document was selected.
	 * @throws ImportValidationException
	 *             when the external document fails validation.
	 * @throws StoreUnavailableException when the store cannot be used
	 * @throws StorePersistenceException when the imported document cannot be persisted
	 * @throws UnsupportedOperationException when local transfers are not supported
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
	 * @throws StoreUnavailableException when the store cannot be used
	 * @throws StorePersistenceException when the snapshot cannot be written
	 * @throws UnsupportedOperationException when local transfers are not supported
	 */
	void exportFile(IncidentId id, Path target);

	/**
	 * Validates a client-side document export destination without reading an incident
	 * or contacting the backend. Stores with managed local files must reject targets
	 * that could overwrite those files, including filesystem aliases. This safety
	 * query remains usable when the store is unavailable or closed so recovery
	 * export does not require a successful store operation.
	 *
	 * @param target requested client-local destination
	 * @return normalized destination that the caller must use for writing
	 * @throws StoreValidationException when the destination overlaps managed files
	 * @throws StorePersistenceException when local path safety cannot be established
	 */
	default Path validateLocalExportTarget(Path target) {
		return target.toAbsolutePath().normalize();
	}

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
	 * @throws StoreUnavailableException when the store cannot be used
	 * @throws StorePersistenceException when the backup cannot be safely written
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
