/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.persistence;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.Provenance;
import org.sarmanagement.icsforms.model.UtcTimestamp;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * File-backed incident store with independent JSON documents and backups.
 * Client exports, including recovery copies, cannot overwrite managed files.
 */
public class FileIncidentStore implements IncidentStore, EditLockSupport {
	private static final Logger LOGGER = Logger.getLogger(FileIncidentStore.class.getName());
	private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule())
			.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
			.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
			.addMixIn(Provenance.class, Provenance.LocalStorageMixin.class);
	private static final ObjectMapper COPIER = MAPPER.copy()
			.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE)
			.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);

	/**
	 * A validated stored document and its original JSON tree.
	 *
	 * @param source source path, including backup when recovering
	 * @param tree original document tree
	 * @param data deserialized incident data
	 */
	private record StoredDocument(Path source, JsonNode tree, AppData data) {
	}

	private final Path root;
	private final Path incidents;
	private final String nodeId;
	private final Map<IncidentId, FileChannel> channels = new HashMap<>();
	private final Map<IncidentId, FileLock> locks = new HashMap<>();
	private final Map<IncidentId, RevisionToken> observed = new HashMap<>();
	private boolean closed;
	private List<ImportValidationException.Problem> lastImportProblems = List.of();

	/**
	 * Validated external content and its immutable warning snapshot.
	 *
	 * @param data validated incident data
	 * @param problems import warnings
	 */
	public record ImportResult(AppData data, List<ImportValidationException.Problem> problems) {
		/**
		 * Captures validated content and an immutable copy of its diagnostics.
		 *
		 * @param data validated incident data
		 * @param problems import warnings
		 */
		public ImportResult(AppData data, List<ImportValidationException.Problem> problems) {
			this.data = data;
			this.problems = List.copyOf(problems);
		}
	}

	/** Opens the default per-user file store. */
	public FileIncidentStore() {
		this(Path.of(System.getProperty("user.home"), ".icsforms"));
	}

	/**
	 * Opens a store rooted in a directory.
	 *
	 * @param root
	 *            directory holding node identity and incident documents.
	 */
	public FileIncidentStore(Path root) {
		this.root = root.toAbsolutePath();
		this.incidents = this.root.resolve("incidents");
		try {
			Files.createDirectories(incidents);
			Path nodeFile = this.root.resolve("node.json");
			if (!Files.exists(nodeFile)) {
				try {
					Files.writeString(nodeFile, MAPPER.writeValueAsString(Map.of("nodeId", IncidentId.newId().value())),
							StandardOpenOption.CREATE_NEW);
				} catch (java.nio.file.FileAlreadyExistsException ignored) {
					// Another store created the node identity first.
				}
			}
			nodeId = MAPPER.readTree(nodeFile.toFile()).get("nodeId").asText();
			IncidentId.of(nodeId);
			migrateLegacy();
		} catch (IOException e) {
			throw new StoreUnavailableException("Cannot open incident store at " + root, e);
		}
	}

	/**
	 * Returns whether another store currently holds an incident lock.
	 *
	 * @param id
	 *            incident identifier.
	 * @return true when another process or store has the lock.
	 */
	public synchronized boolean isLockedElsewhere(IncidentId id) {
		ensureOpen();
		if (locks.containsKey(id)) {
			return false;
		}
		Path lockFile = incidents.resolve(id.value() + ".lock");
		try (FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
			try (FileLock lock = channel.tryLock()) {
				return lock == null;
			}
		} catch (OverlappingFileLockException e) {
			return true;
		} catch (IOException e) {
			throw new StorePersistenceException("Cannot check incident lock: " + id, e);
		}
	}

	/**
	 * Acquires the advisory lock for editing an incident. Failed acquisition closes
	 * the newly opened channel without releasing any preexisting lock.
	 *
	 * @param id
	 *            incident identifier.
	 * @return true when this store owns the lock.
	 */
	public synchronized boolean lock(IncidentId id) {
		ensureOpen();
		if (locks.containsKey(id)) {
			return true;
		}

		Path lockPath = incidents.resolve(id.value() + ".lock");
		try {
			FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
			FileLock lock;
			try {
				lock = channel.tryLock();
			} catch (OverlappingFileLockException e) {
				lock = null;
			} catch (IOException | RuntimeException | Error failure) {
				try {
					channel.close();
				} catch (IOException | RuntimeException | Error cleanupFailure) {
					failure.addSuppressed(cleanupFailure);
				}
				throw failure;
			}
			if (lock == null) {
				channel.close();
				return false;
			}
			channels.put(id, channel);
			locks.put(id, lock);
			return true;
		} catch (IOException e) {
			throw new StorePersistenceException(
					"Cannot lock incident: " + id + " at " + lockPath + ": " + e.getMessage(), e);
		}
	}

	/**
	 * Releases an incident's advisory edit lock without closing the store.
	 *
	 * @param id
	 *            incident identifier.
	 */
	public synchronized void unlock(IncidentId id) {
		FileLock lock = locks.remove(id);
		FileChannel channel = channels.remove(id);
		IOException failure = null;
		try {
			if (lock != null) {
				lock.release();
			}
		} catch (IOException e) {
			failure = e;
		}
		try {
			if (channel != null) {
				channel.close();
			}
		} catch (IOException e) {
			if (failure == null) {
				failure = e;
			} else {
				failure.addSuppressed(e);
			}
		}
		if (failure != null) {
			throw new StorePersistenceException("Cannot release incident lock: " + id, failure);
		}
	}

	/** {@inheritDoc} */
	@Override
	public synchronized IncidentId create(AppData data) {
		ensureOpen();
		AppData candidate = copy(data);
		IncidentId id;
		try {
			id = candidate.getIncidentId() == null ? IncidentId.newId() : IncidentId.of(candidate.getIncidentId());
		} catch (IllegalArgumentException e) {
			throw new StoreValidationException("Invalid incident identity", e);
		}
		Path path = path(id);
		boolean created = false;
		try {
			if (Files.exists(path) || Files.exists(path.resolveSibling(path.getFileName() + ".deleted"))) {
				throw new DuplicateIncidentException("Incident identifier already exists or was deleted: " + id);
			}
			candidate.setIncidentId(id.value());
			candidate.setRevision(0);
			candidate.setSchemaVersion(AppData.CURRENT_SCHEMA_VERSION);
			if (candidate.getCreatedAt() == null) {
				candidate.setCreatedAt(UtcTimestamp.nowInstant());
			}
			candidate.setUpdatedAt(candidate.getCreatedAt());
			if (candidate.getOriginNodeId() == null || candidate.getOriginNodeId().isBlank()) {
				candidate.setOriginNodeId(nodeId);
			}
			normalize(candidate);
			JsonNode prepared = MAPPER.valueToTree(candidate);
			validateReconciliation(data, candidate, new IdentityHashMap<>());
			try (var output = Files.newOutputStream(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
				created = true;
				MAPPER.writerWithDefaultPrettyPrinter().writeValue(output, prepared);
			}
			applyMetadata(data, candidate);
			observed.put(id, revision(candidate));
			return id;
		} catch (java.nio.file.FileAlreadyExistsException e) {
			throw new DuplicateIncidentException("Incident identifier already exists: " + id);
		} catch (IOException e) {
			if (created) {
				try {
					Files.deleteIfExists(path);
				} catch (IOException cleanupFailure) {
					e.addSuppressed(cleanupFailure);
				}
			}
			throw new StorePersistenceException("Cannot create incident: " + id, e);
		}
	}

	/** {@inheritDoc} */
	@Deprecated
	@Override
	public synchronized AppData load(IncidentId id) {
		AppData data = loadDocument(id);
		observed.put(id, revision(data));
		return data;
	}

	/**
	 * Loads and migrates without advancing the legacy caller's last observed revision.
	 *
	 * @param id incident identity
	 * @return detached incident
	 */
	private AppData loadDocument(IncidentId id) {
		ensureOpen();
		Path path = path(id);
		if (!Files.exists(path)) {
			throw new IncidentNotFoundException("Incident not found: " + id);
		}
		StoredDocument stored = readWithBackupDocument(path);
		JsonNode migrated = prepareMigration(stored, id);
		if (!stored.tree().equals(migrated)) {
			boolean alreadyOwned = locks.containsKey(id);
			if (!lock(id)) {
				throw new StoreLockConflictException("Incident is open in another window: " + id);
			}
			try {
				stored = readWithBackupDocument(path);
				migrated = prepareMigration(stored, id);
				if (!stored.tree().equals(migrated)) {
					persistMigration(path, stored, migrated);
				}
			} finally {
				if (!alreadyOwned) {
					unlock(id);
				}
			}
		}
		return stored.data();
	}

	/** {@inheritDoc} */
	@Override
	public synchronized StoredIncident loadSnapshot(IncidentId id) {
		AppData data = load(id);
		return new StoredIncident(data, revision(data));
	}

	/** {@inheritDoc} */
	@Deprecated
	@Override
	public synchronized void save(IncidentId id, AppData data) {
		save(id, data, revision(data));
	}

	/** {@inheritDoc} */
	@Override
	public synchronized RevisionToken save(IncidentId id, AppData data, RevisionToken expected) {
		ensureOpen();
		AppData candidate = copy(data);
		if (!id.value().equals(candidate.getIncidentId()) || expected == null) {
			throw new StoreValidationException("Incident identity or expected revision is invalid");
		}
		boolean alreadyOwned = locks.containsKey(id);
		if (!lock(id)) {
			throw new StoreLockConflictException("Incident is open in another window: " + id);
		}
		Path path = path(id);
		Path temp = path.resolveSibling(path.getFileName() + ".tmp");
		Throwable failure = null;
		try {
			AppData stored = loadDocument(id);
			RevisionToken current = revision(stored);
			if (!current.equals(expected)) {
				throw new StoreConflictException("Incident was modified elsewhere: " + id, expected, current);
			}
			if (stored.getRevision() == Long.MAX_VALUE) {
				throw new StorePersistenceException("File revision range is exhausted",
						new ArithmeticException("Revision counter overflow"));
			}
			candidate.setRevision(stored.getRevision() + 1);
			candidate.setUpdatedAt(UtcTimestamp.nowInstant());
			candidate.setSchemaVersion(AppData.CURRENT_SCHEMA_VERSION);
			normalize(candidate);
			StoredDocument source = readWithBackupDocument(path);
			JsonNode prepared = MAPPER.valueToTree(candidate);
			validateReconciliation(data, candidate, new IdentityHashMap<>());
			JsonNode updatedTree = mergeKnownProperties(source.tree(), prepared);
			MAPPER.writerWithDefaultPrettyPrinter().writeValue(temp.toFile(), updatedTree);
			// Never replace a good backup with a corrupt primary document.
			boolean readable = true;
			try {
				readDocument(path);
			} catch (IOException corruptPrimary) {
				readable = false;
				LOGGER.warning("Keeping previous backup for corrupt incident: " + path);
			}
			if (readable) {
				Files.copy(path, backup(path), StandardCopyOption.REPLACE_EXISTING);
			}
			move(temp, path);
			applyMetadata(data, candidate);
			RevisionToken committed = revision(candidate);
			observed.put(id, committed);
			return committed;
		} catch (IOException e) {
			StorePersistenceException persistenceFailure = new StorePersistenceException("Cannot save incident: " + id, e);
			failure = persistenceFailure;
			throw persistenceFailure;
		} catch (RuntimeException e) {
			RuntimeException typed = e;
			if (!(e instanceof StoreConflictException || e instanceof StoreValidationException
					|| e instanceof IncidentNotFoundException || e instanceof StorePersistenceException
					|| e instanceof StoreUnavailableException || e instanceof DuplicateIncidentException)) {
				typed = new StorePersistenceException("Cannot save incident: " + id + ": " + e.getMessage(), e);
			}
			failure = typed;
			throw typed;
		} catch (Error e) {
			failure = e;
			throw e;
		} finally {
			try {
				Files.deleteIfExists(temp);
			} catch (IOException | RuntimeException e) {
				if (failure != null) {
					failure.addSuppressed(e);
				} else {
					LOGGER.warning("Cannot remove temporary incident file: " + temp + ": " + e.getMessage());
				}
			}
			if (!alreadyOwned) {
				try {
					unlock(id);
				} catch (RuntimeException cleanupFailure) {
					if (failure != null) {
						failure.addSuppressed(cleanupFailure);
					} else {
						LOGGER.warning("Cannot release transient incident lock after save: " + id
								+ ": " + cleanupFailure.getMessage());
					}
				}
			}
		}
	}

	/** {@inheritDoc} */
	@Override
	public synchronized List<IncidentSummary> list() {
		ensureOpen();
		List<IncidentSummary> results = new ArrayList<>();
		try (var files = Files.list(incidents)) {
			files.filter(p -> p.getFileName().toString().endsWith(".json")).forEach(p -> {
				try {
					IncidentId id = IncidentId.of(p.getFileName().toString().replaceFirst("\\.json$", ""));
					AppData data = load(id);
					Instant modified = data.getUpdatedAt() == null
							? Files.getLastModifiedTime(p).toInstant()
							: data.getUpdatedAt();
					results.add(new IncidentSummary(p.toString(), data.getIncidentContext().getIncidentName(),
							data.getIapPhase(), modified, id, data.getForm201().getIncidentNumber(),
							revision(data)));
				} catch (RuntimeException | IOException e) {
					LOGGER.warning("Skipping unreadable incident " + p + ": " + e.getMessage());
				}
			});
		} catch (IOException e) {
			throw new StorePersistenceException("Cannot list incidents", e);
		}
		results.sort(Comparator.comparing(IncidentSummary::lastModified).reversed());
		return results;
	}

	/**
	 * Tombstones an unlocked incident using this handle's last created, loaded or
	 * listed revision. New integrations must pass an explicit token. Already
	 * tombstoned incidents retain legacy idempotency.
	 *
	 * @param id incident identifier.
	 * @throws StoreConflictException when any editor owns the incident lock.
	 */
	@Override
	@Deprecated
	public synchronized void delete(IncidentId id) {
		ensureOpen();
		if (!Files.exists(path(id)) && Files.exists(path(id).resolveSibling(path(id).getFileName() + ".deleted"))) {
			return;
		}
		if (!Files.exists(path(id))) {
			throw new IncidentNotFoundException("Incident not found: " + id);
		}
		RevisionToken expected = observed.get(id);
		if (expected == null) {
			if (locks.containsKey(id) || isLockedElsewhere(id)) {
				throw new StoreLockConflictException("Incident is open for editing: " + id);
			}
			throw new StoreValidationException("Load or list the incident before legacy deletion: " + id);
		}
		delete(id, expected);
	}

	/** {@inheritDoc} */
	@Override
	public synchronized void delete(IncidentId id, RevisionToken expected) {
		ensureOpen();
		if (expected == null) {
			throw new StoreValidationException("Expected revision is required");
		}
		if (locks.containsKey(id) || !lock(id)) {
			throw new StoreLockConflictException("Incident is open for editing; switch to another incident first: " + id);
		}
		Path path = path(id);
		Path deleted = path.resolveSibling(path.getFileName() + ".deleted");
		try {
			if (!Files.exists(path)) {
				throw new IncidentNotFoundException("Incident not found: " + id);
			}
			RevisionToken current = revision(loadDocument(id));
			if (!current.equals(expected)) {
				throw new StoreConflictException("Incident was modified elsewhere: " + id, expected, current);
			}
			Files.move(path, deleted);
			observed.remove(id);
		} catch (IOException e) {
			throw new StorePersistenceException("Cannot delete incident: " + id, e);
		} finally {
			unlock(id);
		}
	}

	/**
	 * Validates external JSON and imports it under a fresh identity.
	 *
	 * @param jsonFile external document, or null when selection was cancelled
	 * @return new identifier, or empty for cancelled selection
	 * @throws ImportValidationException when validation fails
	 */
	@Override
	public synchronized Optional<IncidentId> importFile(Path jsonFile) {
		ensureOpen();
		lastImportProblems = List.of();
		if (jsonFile == null) {
			return Optional.empty();
		}
		IncidentDocumentReader reader = new IncidentDocumentReader();
		AppData data = reader.readImport(jsonFile);
		try {
			IncidentId imported = createImported(data);
			lastImportProblems = reader.problems();
			reader.logOutcome(jsonFile, data.getSchemaVersion(), "import-persisted");
			return Optional.of(imported);
		} catch (RuntimeException failure) {
			reader.logOutcome(jsonFile, data.getSchemaVersion(), "import-failed");
			throw failure;
		}
	}

	/**
	 * Returns warnings from the last successful import.
	 *
	 * @return immutable diagnostic snapshot
	 */
	@Override
	public synchronized List<ImportValidationException.Problem> getLastImportProblems() {
		ensureOpen();
		return lastImportProblems;
	}

	/**
	 * Validates an external document without creating an incident in a store.
	 *
	 * @param jsonFile external document path
	 * @return validated incident data
	 * @throws ImportValidationException when validation fails
	 */
	public static AppData readImportFile(Path jsonFile) {
		return readImport(jsonFile).data();
	}

	/**
	 * Validates external content without a store and retains successful warnings.
	 *
	 * @param jsonFile external document path
	 * @return validated data and immutable warnings
	 * @throws ImportValidationException when validation fails
	 */
	public static ImportResult readImport(Path jsonFile) {
		IncidentDocumentReader reader = new IncidentDocumentReader();
		AppData data = reader.readImport(jsonFile);
		return new ImportResult(data, reader.problems());
	}

	/**
	 * Creates a distinct copy of an imported or migrated document.
	 *
	 * @param data validated external or tolerantly loaded legacy data
	 * @return new identifier
	 */
	private IncidentId createImported(AppData data) {
		data.setIncidentId(null);
		data.setRevision(0);
		data.setCreatedAt(null);
		data.setUpdatedAt(null);
		data.setOriginNodeId(null);
		return create(data);
	}

	/** {@inheritDoc} */
	@Override
	public synchronized void exportFile(IncidentId id, Path target) {
		ensureOpen();
		try {
			Path destination = validateLocalExportTarget(target);
			loadDocument(id);
			MAPPER.writerWithDefaultPrettyPrinter().writeValue(destination.toFile(), readWithBackupDocument(path(id)).tree());
		} catch (IOException e) {
			throw new StorePersistenceException("Cannot export incident: " + id, e);
		}
	}

	/**
	 * Protects incident files and node identity during stored and unsaved exports,
	 * including through symlinks. No open store or readable incident is required.
	 *
	 * @param target requested local export destination
	 * @return normalized safe destination
	 * @throws StoreValidationException when managed files could be overwritten
	 * @throws StorePersistenceException when the local destination cannot be resolved
	 */
	@Override
	public Path validateLocalExportTarget(Path target) {
		Path destination = target.toAbsolutePath().normalize();
		Path nodeFile = root.resolve("node.json");
		if (destination.startsWith(incidents.toAbsolutePath().normalize())
				|| destination.equals(nodeFile.toAbsolutePath().normalize())) {
			throw new StoreValidationException("Export destination must be outside the live incident store");
		}
		try {
			Path resolved = resolveExportPath(destination);
			if (resolved.startsWith(resolveExportPath(incidents)) || resolved.equals(resolveExportPath(nodeFile))) {
				throw new StoreValidationException("Export destination must be outside the live incident store");
			}
			return destination;
		} catch (IOException exception) {
			throw new StorePersistenceException("Cannot validate local export destination: " + target, exception);
		}
	}

	/**
	 * Resolves existing aliases while permitting a new directory or an unavailable
	 * store directory. Every existing path component is resolved before appending
	 * the still-missing suffix.
	 *
	 * @param path requested local path
	 * @return physical path through its closest existing ancestor
	 * @throws IOException when an existing ancestor cannot be resolved
	 */
	private Path resolveExportPath(Path path) throws IOException {
		Path absolute = path.toAbsolutePath();
		Path existing = absolute;
		while (!Files.exists(existing, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
			existing = existing.getParent();
			if (existing == null) {
				throw new IOException("No existing ancestor for " + path);
			}
		}
		return existing.toRealPath().resolve(existing.relativize(absolute)).normalize();
	}

	/**
	 * Reports support for byte-preserving, non-overwriting external backups.
	 *
	 * @return true.
	 */
	@Override
	public boolean supportsBackup() {
		return true;
	}

	/**
	 * Copies the readable primary JSON verbatim, falling back to its good automatic
	 * backup when the primary is corrupt. A transient advisory lock protects the
	 * snapshot without releasing an existing local edit lock.
	 *
	 * @param id incident identifier.
	 * @param target new file outside the live store.
	 * @throws IllegalStateException when the backup cannot be safely written.
	 * @throws StoreConflictException when another editor owns the incident.
	 */
	@Override
	public synchronized void backupFile(IncidentId id, Path target) {
		ensureOpen();
		Path destination = target.toAbsolutePath().normalize();
		boolean owned = locks.containsKey(id);
		if (!lock(id)) {
			throw new StoreLockConflictException("Incident is open in another window: " + id);
		}
		boolean created = false;
		try {
			Path realParent = destination.getParent().toRealPath();
			if (destination.startsWith(root.normalize()) || realParent.startsWith(root.toRealPath())
					|| realParent.startsWith(incidents.toRealPath())) {
				throw new IllegalArgumentException("Choose a new backup filename outside the live incident store.");
			}
			if (Files.exists(destination, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
				throw new IllegalArgumentException("Backup already exists; choose a new filename (no overwriting).");
			}
			Path source = path(id);
			if (!Files.exists(source)) {
				throw new IncidentNotFoundException("Incident not found: " + id);
			}
			AppData stored;
			try {
				stored = readDocument(source);
			} catch (IOException corruptPrimary) {
				source = backup(source);
				stored = readDocument(source);
			}
			if (stored.getIncidentId() != null && !id.value().equals(stored.getIncidentId())) {
				throw new IllegalStateException("Incident identifier does not match file: " + id);
			}
			try (var input = Files.newInputStream(source);
					var output = Files.newOutputStream(destination, StandardOpenOption.CREATE_NEW,
							StandardOpenOption.WRITE)) {
				created = true;
				input.transferTo(output);
			}
		} catch (IOException | RuntimeException failure) {
			if (created) {
				try {
					Files.deleteIfExists(destination);
				} catch (IOException cleanupFailure) {
					failure.addSuppressed(cleanupFailure);
				}
			}
			if (failure instanceof IncidentNotFoundException missing) {
				throw missing;
			}
			throw new StorePersistenceException("Cannot back up incident: " + failure.getMessage(), failure);
		} finally {
			if (!owned) {
				unlock(id);
			}
		}
	}

	/** {@inheritDoc} */
	@Override
	public StoreDescriptor describe() {
		return new StoreDescriptor("File", root.toString());
	}

	/** {@inheritDoc} */
	@Override
	public Optional<EditLockSupport> editLocks() {
		return Optional.of(this);
	}

	/** {@inheritDoc} */
	@Override
	public RevisionToken revisionToken(AppData data) {
		if (data == null) {
			throw new StoreValidationException("Caller snapshot is required");
		}
		return revision(data);
	}

	/** {@inheritDoc} */
	@Deprecated
	@Override
	public Optional<Path> localFile(IncidentId id) {
		return Optional.of(path(id));
	}

	/** {@inheritDoc} */
	@Override
	public synchronized boolean isHeldByCaller(IncidentId id) {
		return locks.containsKey(id);
	}

	/** {@inheritDoc} */
	@Override
	public synchronized void close() {
		for (FileLock lock : locks.values()) {
			try {
				lock.release();
			} catch (IOException e) {
				LOGGER.warning("Cannot release incident lock: " + e.getMessage());
			}
		}
		for (FileChannel channel : channels.values()) {
			try {
				channel.close();
			} catch (IOException e) {
				LOGGER.warning("Cannot close incident lock channel: " + e.getMessage());
			}
		}
		locks.clear();
		channels.clear();
		closed = true;
	}

	/** Rejects operations on a closed store. */
	private void ensureOpen() {
		if (closed) {
			throw new StoreUnavailableException("Incident store is closed");
		}
	}

	/**
	 * Copies fields without invoking caller getters that can normalize mutable child IDs.
	 *
	 * @param data caller-owned document
	 * @return detached editable copy
	 */
	private AppData copy(AppData data) {
		if (data == null || data.getSchemaVersion() > AppData.CURRENT_SCHEMA_VERSION) {
			throw new StoreValidationException("Missing document or unsupported schema");
		}
		try {
			return COPIER.treeToValue(COPIER.valueToTree(data), AppData.class);
		} catch (IOException | IllegalArgumentException e) {
			throw new StoreValidationException("Cannot serialize incident document", e);
		}
	}

	/**
	 * Applies the exact successful normalized state, including assigned child identities.
	 * Transient UI-only fields, compatible caller child model objects and their
	 * collection identities are retained. Unsupported structural changes to immutable
	 * collections are rejected before committing.
	 *
	 * @param data caller-owned document
	 * @param saved successfully persisted document
	 */
	private void applyMetadata(AppData data, AppData saved) {
		mergeSuccessfulState(data, saved, new IdentityHashMap<>());
	}

	/**
	 * Applies exact committed fields without regenerating identities or replacing compatible
	 * caller child objects. Shared caller references are retained only when consistent with
	 * the committed tree; immutable/nonmodel values are assigned directly.
	 *
	 * @param original existing caller value
	 * @param saved committed candidate value
	 * @param visited caller values already paired with committed values
	 * @return original updated in place, or a replacement when needed
	 */
	@SuppressWarnings("unchecked")
	private Object mergeSuccessfulState(Object original, Object saved, Map<Object, Object> visited) {
		if (original == null || saved == null) {
			return saved;
		}
		if (visited.containsKey(original)) {
			return original;
		}
		if (original instanceof Map<?, ?> originalMap && saved instanceof Map<?, ?> savedMap) {
			visited.put(original, saved);
			Map<Object, Object> mutable = (Map<Object, Object>) originalMap;
			for (var entry : savedMap.entrySet()) {
				Object existing = originalMap.get(entry.getKey());
				Object merged = mergeSuccessfulState(existing, entry.getValue(), visited);
				if (!originalMap.containsKey(entry.getKey()) || !Objects.equals(existing, merged)) {
					mutable.put(entry.getKey(), merged);
				}
			}
			if (!originalMap.keySet().equals(savedMap.keySet())) {
				mutable.keySet().removeIf(key -> !savedMap.containsKey(key));
			}
			return original;
		}
		if (saved instanceof List<?> savedList && original instanceof List<?> originalList) {
			visited.put(original, saved);
			List<Object> merged = new ArrayList<>(savedList.size());
			for (int index = 0; index < savedList.size(); index++) {
				Object existing = index < originalList.size() ? originalList.get(index) : null;
				merged.add(mergeSuccessfulState(existing, savedList.get(index), visited));
			}
			List<Object> mutable = (List<Object>) originalList;
			int common = Math.min(originalList.size(), merged.size());
			for (int index = 0; index < common; index++) {
				if (!Objects.equals(mutable.get(index), merged.get(index))) {
					mutable.set(index, merged.get(index));
				}
			}
			for (int index = common; index < merged.size(); index++) {
				mutable.add(merged.get(index));
			}
			while (mutable.size() > merged.size()) {
				mutable.remove(mutable.size() - 1);
			}
			return original;
		}
		if (!saved.getClass().isInstance(original)) {
			return saved;
		}
		Class<?> type = saved.getClass();
		if (type.isEnum() || type.isRecord() || !type.getPackageName().startsWith("org.sarmanagement.icsforms.model")) {
			return Objects.equals(original, saved) ? original : saved;
		}
		visited.put(original, saved);
		for (Class<?> declaring = type; declaring != Object.class; declaring = declaring.getSuperclass()) {
			for (var field : declaring.getDeclaredFields()) {
				if (!Modifier.isStatic(field.getModifiers()) && !Modifier.isTransient(field.getModifiers())) {
					try {
						field.setAccessible(true);
						field.set(original, mergeSuccessfulState(field.get(original), field.get(saved), visited));
					} catch (IllegalAccessException e) {
						throw new AssertionError("Cannot apply persisted incident fields", e);
					}
				}
			}
		}
		return original;
	}

	/**
	 * Checks that normalized state can be applied without replacing caller collections
	 * or inconsistently normalizing a shared child reference. No caller fields are changed.
	 *
	 * @param original caller-owned value
	 * @param saved normalized candidate value
	 * @param visited caller values already paired with normalized values
	 */
	private void validateReconciliation(Object original, Object saved, Map<Object, Object> visited) {
		if (original == null || saved == null) {
			return;
		}
		if (visited.containsKey(original)) {
			if (!COPIER.valueToTree(visited.get(original)).equals(COPIER.valueToTree(saved))) {
				throw new StoreValidationException("Shared caller references require incompatible normalization");
			}
			return;
		}
		if (original instanceof Map<?, ?> originalMap && saved instanceof Map<?, ?> savedMap) {
			visited.put(original, saved);
			boolean replace = !originalMap.keySet().equals(savedMap.keySet());
			for (var entry : savedMap.entrySet()) {
				Object existing = originalMap.get(entry.getKey());
				replace |= requiresReplacement(existing, entry.getValue());
				validateReconciliation(existing, entry.getValue(), visited);
			}
			if (replace && !(original instanceof HashMap<?, ?> || original instanceof java.util.EnumMap<?, ?>
					|| original instanceof java.util.TreeMap<?, ?>)) {
				throw new StoreValidationException("Normalization requires changing an unsupported caller map");
			}
			return;
		}
		if (original instanceof List<?> originalList && saved instanceof List<?> savedList) {
			visited.put(original, saved);
			boolean resize = originalList.size() != savedList.size();
			boolean replace = resize;
			int common = Math.min(originalList.size(), savedList.size());
			for (int index = 0; index < common; index++) {
				Object existing = originalList.get(index);
				Object normalized = savedList.get(index);
				replace |= requiresReplacement(existing, normalized);
				validateReconciliation(existing, normalized, visited);
			}
			boolean mutable = original instanceof ArrayList<?> || original instanceof java.util.LinkedList<?>
					|| original instanceof java.util.Vector<?>;
			boolean fixedSizeMutable = original.getClass().getName().equals("java.util.Arrays$ArrayList");
			if (replace && !mutable && !(fixedSizeMutable && !resize)) {
				throw new StoreValidationException("Normalization requires changing an unsupported caller list");
			}
			return;
		}
		Class<?> type = saved.getClass();
		if (!type.isInstance(original) || type.isEnum() || type.isRecord()
				|| !type.getPackageName().startsWith("org.sarmanagement.icsforms.model")) {
			return;
		}
		visited.put(original, saved);
		for (Class<?> declaring = type; declaring != Object.class; declaring = declaring.getSuperclass()) {
			for (var field : declaring.getDeclaredFields()) {
				if (!Modifier.isStatic(field.getModifiers()) && !Modifier.isTransient(field.getModifiers())) {
					try {
						field.setAccessible(true);
						validateReconciliation(field.get(original), field.get(saved), visited);
					} catch (IllegalAccessException e) {
						throw new StoreValidationException("Cannot reconcile caller fields", e);
					}
				}
			}
		}
	}

	/**
	 * Reports whether a list entry needs replacement rather than in-place model updates.
	 *
	 * @param original caller entry
	 * @param saved normalized entry
	 * @return whether the list must replace this entry
	 */
	private boolean requiresReplacement(Object original, Object saved) {
		if (Objects.equals(original, saved)) {
			return false;
		}
		if (original == null || saved == null) {
			return true;
		}
		if ((original instanceof List<?> && saved instanceof List<?>)
				|| (original instanceof Map<?, ?> && saved instanceof Map<?, ?>)) {
			return false;
		}
		Class<?> type = saved.getClass();
		if (!type.isInstance(original)) {
			return true;
		}
		return type.isEnum() || type.isRecord()
				|| !type.getPackageName().startsWith("org.sarmanagement.icsforms.model");
	}

	/**
	 * Normalizes detached candidates while classifying invalid model values.
	 *
	 * @param data detached candidate document
	 */
	private void normalize(AppData data) {
		try {
			data.normalizeIdentity();
		} catch (RuntimeException e) {
			throw new StoreValidationException("Cannot normalize incident document", e);
		}
	}

	/**
	 * Converts the file format's numeric revision to a store-neutral token.
	 *
	 * @param data stored file document
	 * @return opaque revision
	 */
	private RevisionToken revision(AppData data) {
		return new RevisionToken(Long.toString(data.getRevision()));
	}

	/**
	 * Resolves the primary document path.
	 *
	 * @param id
	 *            incident identifier.
	 * @return JSON path.
	 */
	private Path path(IncidentId id) {
		return incidents.resolve(id.value() + ".json");
	}

	/**
	 * Resolves the backup for a document.
	 *
	 * @param path
	 *            primary path.
	 * @return backup path.
	 */
	private Path backup(Path path) {
		return path.resolveSibling(path.getFileName() + ".bak");
	}

	/**
	 * Reads the primary document, recovering from a previous good backup.
	 *
	 * @param path
	 *            primary document path.
	 * @return recovered document.
	 */
	private AppData readWithBackup(Path path) {
		try {
			return readDocument(path);
		} catch (IOException primaryFailure) {
			try {
				return readDocument(backup(path));
			} catch (IOException backupFailure) {
				backupFailure.addSuppressed(primaryFailure);
				throw new StorePersistenceException("Cannot read incident or backup: " + path, backupFailure);
			}
		}
	}

	/**
	 * Reads and validates the primary document or its last-known-good backup.
	 *
	 * @param path primary incident document
	 * @return validated source tree and incident data
	 */
	private StoredDocument readWithBackupDocument(Path path) {
		try {
			return readStoredDocument(path);
		} catch (IOException primaryFailure) {
			try {
				return readStoredDocument(backup(path));
			} catch (IOException backupFailure) {
				backupFailure.addSuppressed(primaryFailure);
				throw new StorePersistenceException("Cannot read incident or backup: " + path, backupFailure);
			}
		}
	}

	/**
	 * Reads one supported stored document while retaining unknown JSON fields.
	 *
	 * @param path stored document path
	 * @return validated source tree and incident data
	 * @throws IOException when the document cannot be read
	 */
	private StoredDocument readStoredDocument(Path path) throws IOException {
		AppData data = readDocument(path);
		return new StoredDocument(path, MAPPER.readTree(path.toFile()), data);
	}

	/**
	 * Normalizes a legacy incident and overlays the result onto its original JSON.
	 *
	 * @param stored validated stored document
	 * @param id file's assigned incident identity
	 * @return migrated tree with unknown properties retained
	 */
	private JsonNode prepareMigration(StoredDocument stored, IncidentId id) {
		AppData data = stored.data();
		if (data.getIncidentId() != null && !id.value().equals(data.getIncidentId())) {
			throw new StoreValidationException("Incident identifier does not match file: " + id);
		}
		upgrade(data, id);
		return mergeKnownProperties(stored.tree(), MAPPER.valueToTree(data));
	}

	/**
	 * Merges serialized model properties into an original tree without dropping unknown fields.
	 *
	 * @param original original JSON value
	 * @param updated normalized model value
	 * @return updated value retaining unknown original properties
	 */
	private JsonNode mergeKnownProperties(JsonNode original, JsonNode updated) {
		JsonNode localOriginal = original.deepCopy();
		StoredJsonMerger.removeAcceptedAt(localOriginal);
		return StoredJsonMerger.merge(MAPPER, localOriginal, updated);
	}

	/**
	 * Atomically persists identity migration, preserving the original as a backup.
	 *
	 * @param primary primary incident document
	 * @param stored validated source document
	 * @param migrated normalized JSON tree
	 */
	private void persistMigration(Path primary, StoredDocument stored, JsonNode migrated) {
		Path temp = primary.resolveSibling(primary.getFileName() + ".tmp");
		try {
			if (stored.source().equals(primary)) {
				Files.copy(primary, backup(primary), StandardCopyOption.REPLACE_EXISTING);
			}
			MAPPER.writerWithDefaultPrettyPrinter().writeValue(temp.toFile(), migrated);
			move(temp, primary);
			Files.copy(primary, backup(primary), StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			throw new StorePersistenceException("Cannot persist incident identity migration: " + primary, e);
		} finally {
			try {
				Files.deleteIfExists(temp);
			} catch (IOException e) {
				LOGGER.warning("Cannot remove temporary incident migration file: " + temp);
			}
		}
	}

	/**
	 * Reads an incident document only when its schema is supported by this client.
	 *
	 * @param path incident document path.
	 * @return deserialized incident.
	 * @throws IOException when the file cannot be parsed.
	 * @throws StoreValidationException when a newer schema must not be rewritten.
	 */
	private AppData readDocument(Path path) throws IOException {
		try {
			return new IncidentDocumentReader().readStored(path);
		} catch (StoreValidationException e) {
			throw e;
		} catch (RuntimeException e) {
			throw new StorePersistenceException("Cannot read stored incident: " + path, e);
		}
	}

	/**
	 * Upgrades legacy documents and normalizes incident and child identities,
	 * including schema-12 communications plans.
	 *
	 * @param data
	 *            loaded document.
	 * @param id
	 *            file's assigned identity.
	 */
	private void upgrade(AppData data, IncidentId id) {
		data.reconcileMedicalUnitLeaderAssignment();
		if (data.getIncidentId() == null) {
			data.setIncidentId(id.value());
			data.setRevision(0);
		}
		data.setSchemaVersion(AppData.CURRENT_SCHEMA_VERSION);
		if (data.getOriginNodeId() == null || data.getOriginNodeId().isBlank()) {
			data.setOriginNodeId(nodeId);
		}
		normalize(data);
	}

	/**
	 * Replaces a destination atomically where the filesystem supports it.
	 *
	 * @param source
	 *            source path.
	 * @param destination
	 *            destination path.
	 * @throws IOException
	 *             if the move fails.
	 */
	private void move(Path source, Path destination) throws IOException {
		try {
			Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	/**
	 * Imports the former single-workspace document once, preserving the original.
	 *
	 * @throws IOException
	 *             if migration marker creation fails.
	 */
	private void migrateLegacy() throws IOException {
		Path legacy = root.resolve("incident.json");
		Path marker = root.resolve("incident.json.migrated");
		if (!Files.exists(legacy) || Files.exists(marker)) {
			return;
		}
		try (var files = Files.list(incidents)) {
			if (files.anyMatch(p -> p.getFileName().toString().endsWith(".json"))) {
				return;
			}
		}
		createImported(readWithBackup(legacy));
		try {
			Files.createFile(marker);
		} catch (java.nio.file.FileAlreadyExistsException ignored) {
			// Another instance completed migration first.
		}
	}
}
