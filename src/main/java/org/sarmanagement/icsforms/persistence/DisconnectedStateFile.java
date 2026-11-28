/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.persistence;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.sarmanagement.icsforms.model.AppData;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Set;
import java.time.Instant;

/**
 * Exclusively locked, atomically replaced disconnected incident journal.
 */
final class DisconnectedStateFile implements AutoCloseable {
	private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule())
			.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE)
			.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);

	/**
	 * One durable state, including both the visible draft and the ordered queue.
	 *
	 * @param base acknowledged document
	 * @param records record versions corresponding to the acknowledged base
	 * @param cursor last acknowledged host cursor
	 * @param draft most recently saved local document
	 * @param revision local snapshot token
	 * @param confirmedAt last successful host confirmation, or null
	 * @param acceptedReceipts number of applied submission receipts recorded locally
	 * @param acceptedChanges latest per-property applied receipt attribution
	 * @param pending ordered operations
	 */
	record State(AppData base, List<IncidentService.VersionedRecord> records, long cursor, AppData draft,
			RevisionToken revision, Instant confirmedAt, long acceptedReceipts,
			AcceptedChanges acceptedChanges,
			List<RemoteIncidentStore.PendingOperation> pending) {
		/**
		 * Copies collections on construction.
		 */
		State {
			records = List.copyOf(records);
			acceptedChanges = acceptedChanges == null ? AcceptedChanges.empty() : acceptedChanges;
			pending = List.copyOf(pending);
		}
	}

	/**
	 * Durable access cutoff and edits excluded from an administrator's approval.
	 *
	 * @param reason expired or revoked while approval is required, otherwise null
	 * @param blocked operation IDs created after the approval request
	 * @param reviewed operation IDs included in the approval request
	 */
	record Cutoff(RemoteAccessException.Reason reason, List<String> blocked, List<String> reviewed) {
		/** Copies operation identifiers and accepts older marker files without the newer fields. */
		Cutoff {
			blocked = blocked == null ? List.of() : List.copyOf(blocked);
			reviewed = reviewed == null ? List.of() : List.copyOf(reviewed);
		}
	}

	private final Path directory;
	private final Path file;
	private final FileChannel lockChannel;
	private final FileLock lock;
	private final CommitObserver observer;
	private boolean failed;

	/** Checkpoints at which an interrupted replacement can be recovered. */
	enum CommitPoint {
		STAGING_SYNCED, REPLACED
	}

	/** Observes replacement checkpoints. */
	@FunctionalInterface
	interface CommitObserver {
		/**
		 * Called at a replacement checkpoint.
		 *
		 * @param point reached checkpoint
		 * @throws IOException if the observer cannot proceed
		 */
		void reached(CommitPoint point) throws IOException;
	}

	/**
	 * Acquires the exclusive writer lease for one authority and incident.
	 *
	 * @param root private storage root
	 * @param authority pinned authority fingerprint
	 * @param incident incident identity
	 */
	DisconnectedStateFile(Path root, String authority, IncidentId incident) {
		this(root, authority, incident, point -> { });
	}

	/**
	 * Acquires a writer lease with a replacement checkpoint observer.
	 *
	 * @param root private storage root
	 * @param authority pinned authority fingerprint
	 * @param incident incident identity
	 * @param observer replacement checkpoint observer
	 */
	DisconnectedStateFile(Path root, String authority, IncidentId incident, CommitObserver observer) {
		try {
			Path absoluteRoot = root.toAbsolutePath().normalize();
			ensureNoSymlinks(absoluteRoot);
			PrivateFiles.secureDirectory(absoluteRoot);
			directory = absoluteRoot.resolve(authority);
			ensureNoSymlinks(directory);
			PrivateFiles.secureDirectory(directory);
			file = directory.resolve(incident.value() + ".json");
			Path lockPath = directory.resolve(incident.value() + ".lock");
			ensureNoSymlinks(file);
			lockChannel = PrivateFiles.openPrivate(lockPath,
					Set.of(StandardOpenOption.CREATE, StandardOpenOption.WRITE));
			FileLock acquired;
			try {
				acquired = lockChannel.tryLock();
			} catch (OverlappingFileLockException failure) {
				acquired = null;
			}
			if (acquired == null) {
				lockChannel.close();
				throw new StorePersistenceException("Disconnected incident is already open for writing", null);
			}
			lock = acquired;
			this.observer = observer;
		} catch (PrivateFiles.UnsecurableStorageException failure) {
			throw new StorageNotSecurableException("Offline editing is unavailable: this storage location "
					+ "(for example a FAT or exFAT drive) cannot be restricted to your account", failure);
		} catch (IOException | UnsupportedOperationException failure) {
			throw new StorePersistenceException("Cannot secure disconnected incident storage", failure);
		}
	}

	/**
	 * Reads the committed journal, if present.
	 *
	 * @return state or null
	 */
	State load() {
		if (!Files.exists(file)) {
			return null;
		}
		try {
			return MAPPER.readValue(file.toFile(), State.class);
		} catch (IOException | RuntimeException failure) {
			throw new StorePersistenceException("Cannot read disconnected incident state", failure);
		}
	}

	/**
	 * Loads the access cutoff independently of the incident journal.
	 *
	 * @return persisted cutoff, or an unrestricted state
	 */
	Cutoff loadCutoff() {
		Path cutoff = directory.resolve(file.getFileName() + ".cutoff");
		try {
			ensureNoSymlinks(cutoff);
			return Files.exists(cutoff) ? MAPPER.readValue(cutoff.toFile(), Cutoff.class)
					: new Cutoff(null, List.of(), List.of());
		} catch (IOException | RuntimeException failure) {
			throw new StorePersistenceException("Cannot read disconnected access cutoff", failure);
		}
	}

	/**
	 * Atomically persists the access cutoff before changing replay permissions.
	 *
	 * @param cutoff replacement cutoff
	 */
	void saveCutoff(Cutoff cutoff) {
		Path target = directory.resolve(file.getFileName() + ".cutoff");
		Path staging = directory.resolve(file.getFileName() + ".cutoff.new");
		try {
			ensureNoSymlinks(directory);
			ensureNoSymlinks(target);
			ensureNoSymlinks(staging);
			try (FileChannel output = PrivateFiles.openPrivate(staging, Set.of(StandardOpenOption.CREATE,
					StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE))) {
				java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(MAPPER.writeValueAsBytes(cutoff));
				while (buffer.hasRemaining()) {
					output.write(buffer);
				}
				output.force(true);
			}
			Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			PrivateFiles.syncDirectory(directory);
		} catch (IOException | UnsupportedOperationException failure) {
			failed = true;
			throw new StorePersistenceException("Cannot commit disconnected access cutoff", failure);
		}
	}

	/**
	 * Commits a complete replacement and synchronizes the file and containing directory.
	 *
	 * @param state complete replacement
	 */
	void save(State state) {
		Path staging = directory.resolve(file.getFileName() + ".new");
		try {
			ensureNoSymlinks(directory);
			ensureNoSymlinks(file);
			byte[] bytes = MAPPER.writeValueAsBytes(state);
			try (FileChannel output = PrivateFiles.openPrivate(staging, Set.of(StandardOpenOption.CREATE,
					StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE))) {
				java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(bytes);
				while (buffer.hasRemaining()) {
					output.write(buffer);
				}
				output.force(true);
			}
			observer.reached(CommitPoint.STAGING_SYNCED);
			Files.move(staging, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			observer.reached(CommitPoint.REPLACED);
			PrivateFiles.syncDirectory(directory);
		} catch (IOException | UnsupportedOperationException failure) {
			failed = true;
			throw new StorePersistenceException("Cannot commit disconnected incident state atomically", failure);
		}
	}

	/**
	 * Reports an uncertain local commit outcome requiring this handle to be reopened.
	 *
	 * @return true after a failed commit
	 */
	boolean failed() {
		return failed;
	}

	/**
	 * Returns the real private cache root for transfer-path validation.
	 *
	 * @return canonical root path
	 * @throws IOException when the root cannot be resolved
	 */
	Path realRoot() throws IOException {
		return directory.getParent().toRealPath();
	}

	/**
	 * Writes a detached recovery copy of the complete pending queue.
	 *
	 * @param target validated external destination
	 * @param pending ordered operations
	 * @throws IOException when the copy cannot be written
	 */
	void export(Path target, List<RemoteIncidentStore.PendingOperation> pending) throws IOException {
		MAPPER.writerWithDefaultPrettyPrinter().writeValue(target.toFile(), pending);
	}

	/**
	 * Durably keeps an owner-only audit copy of operations that are being superseded.
	 *
	 * @param label file-name-safe label for this archive
	 * @param operations operations to preserve exactly
	 * @throws IOException when the archive cannot be written
	 */
	void archive(String label, List<RemoteIncidentStore.PendingOperation> operations) throws IOException {
		Path target = directory.resolve(file.getFileName() + ".rejected-" + label + ".json");
		ensureNoSymlinks(target);
		try (FileChannel output = PrivateFiles.openPrivate(target, Set.of(StandardOpenOption.CREATE_NEW,
				StandardOpenOption.WRITE))) {
			java.nio.ByteBuffer buffer = java.nio.ByteBuffer
					.wrap(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(operations));
			while (buffer.hasRemaining()) {
				output.write(buffer);
			}
			output.force(true);
		}
		PrivateFiles.syncDirectory(directory);
	}

	/**
	 * Rejects symbolic links in every existing component of a managed path.
	 *
	 * @param path path to inspect
	 * @throws IOException when a component is a symbolic link
	 */
	private static void ensureNoSymlinks(Path path) throws IOException {
		Path current = path.getRoot();
		for (Path component : path) {
			current = current.resolve(component);
			if (Files.isSymbolicLink(current)) {
				throw new IOException("Symbolic links are not allowed in disconnected storage");
			}
		}
	}

	/**
	 * Releases the writer lease.
	 */
	@Override
	public void close() {
		try {
			lock.release();
			lockChannel.close();
		} catch (IOException failure) {
			throw new StorePersistenceException("Cannot close disconnected incident state", failure);
		}
	}
}
