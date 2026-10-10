package org.sarmanagement.icsforms.persistence;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.sarmanagement.icsforms.model.AppData;

import java.io.IOException;
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
import java.util.logging.Logger;

/** File-backed incident store with independent JSON documents and backups. */
public class FileIncidentStore implements IncidentStore {
	private static final Logger LOGGER = Logger.getLogger(FileIncidentStore.class.getName());
	private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule())
			.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
			.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
	private final Path root;
	private final Path incidents;
	private final String nodeId;
	private final Map<IncidentId, FileChannel> channels = new HashMap<>();
	private final Map<IncidentId, FileLock> locks = new HashMap<>();

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
			throw new IllegalStateException("Cannot open incident store at " + root, e);
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
			throw new IllegalStateException("Cannot check incident lock: " + id, e);
		}
	}

	/**
	 * Acquires the advisory lock for editing an incident.
	 *
	 * @param id
	 *            incident identifier.
	 * @return true when this store owns the lock.
	 */
	public synchronized boolean lock(IncidentId id) {
		if (locks.containsKey(id)) {
			return true;
		}

		try {
			FileChannel channel = FileChannel.open(incidents.resolve(id.value() + ".lock"),
					StandardOpenOption.CREATE, StandardOpenOption.WRITE);
			FileLock lock;
			try {
				lock = channel.tryLock();
			} catch (OverlappingFileLockException e) {
				lock = null;
			}
			if (lock == null) {
				channel.close();
				return false;
			}
			channels.put(id, channel);
			locks.put(id, lock);
			return true;
		} catch (IOException e) {
			throw new IllegalStateException("Cannot lock incident: " + id, e);
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
		try {
			if (lock != null) {
				lock.release();
			}
			if (channel != null) {
				channel.close();
			}
		} catch (IOException e) {
			throw new IllegalStateException("Cannot release incident lock: " + id, e);
		}
	}

	@Override
	public synchronized IncidentId create(AppData data) {
		IncidentId id = data.getIncidentId() == null ? IncidentId.newId() : IncidentId.of(data.getIncidentId());
		Path path = path(id);
		boolean created = false;
		try {
			if (Files.exists(path.resolveSibling(path.getFileName() + ".deleted"))) {
				throw new IllegalStateException("Incident identifier was previously deleted: " + id);
			}
			try (var output = Files.newOutputStream(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
				created = true;
				data.setIncidentId(id.value());
				data.setRevision(0);
				data.setSchemaVersion(AppData.CURRENT_SCHEMA_VERSION);
				if (data.getCreatedAt() == null) {
					data.setCreatedAt(Instant.now());
				}
				data.setUpdatedAt(data.getCreatedAt());
				if (data.getOriginNodeId() == null || data.getOriginNodeId().isBlank()) {
					data.setOriginNodeId(nodeId);
				}
				data.normalizeIdentity();
				MAPPER.writerWithDefaultPrettyPrinter().writeValue(output, data);
			}
			return id;
		} catch (IOException e) {
			if (created) {
				try {
					Files.deleteIfExists(path);
				} catch (IOException cleanupFailure) {
					e.addSuppressed(cleanupFailure);
				}
			}
			throw new IllegalStateException("Cannot create incident: " + id, e);
		}
	}

	@Override
	public AppData load(IncidentId id) {
		Path path = path(id);
		if (!Files.exists(path)) {
			throw new IllegalArgumentException("Incident not found: " + id);
		}
		AppData data = readWithBackup(path);
		if (data.getIncidentId() != null && !id.value().equals(data.getIncidentId())) {
			throw new IllegalStateException("Incident identifier does not match file: " + id);
		}
		upgrade(data, id);
		return data;
	}

	@Override
	public synchronized void save(IncidentId id, AppData data) {
		if (!id.value().equals(data.getIncidentId())) {
			throw new IllegalArgumentException("Incident identifier does not match save target");
		}
		if (!lock(id)) {
			throw new StoreConflictException("Incident is open in another window: " + id);
		}
		Path path = path(id);
		AppData stored = load(id);
		if (stored.getRevision() != data.getRevision()) {
			throw new StoreConflictException("Incident was modified elsewhere: " + id);
		}
		long revision = data.getRevision();
		Instant updated = data.getUpdatedAt();
		data.setRevision(revision + 1);
		data.setUpdatedAt(Instant.now());
		data.setSchemaVersion(AppData.CURRENT_SCHEMA_VERSION);
		Path temp = path.resolveSibling(path.getFileName() + ".tmp");
		try {
			MAPPER.writerWithDefaultPrettyPrinter().writeValue(temp.toFile(), data);
			// Never replace a good backup with a corrupt primary document.
			try {
				MAPPER.readValue(path.toFile(), AppData.class);
				Files.copy(path, backup(path), StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException corruptPrimary) {
				LOGGER.warning("Keeping previous backup for corrupt incident: " + path);
			}
			move(temp, path);
		} catch (IOException e) {
			data.setRevision(revision);
			data.setUpdatedAt(updated);
			throw new IllegalStateException("Cannot save incident: " + id, e);
		} finally {
			try {
				Files.deleteIfExists(temp);
			} catch (IOException e) {
				LOGGER.warning("Cannot remove temporary incident file: " + temp);
			}
		}
	}

	@Override
	public List<IncidentSummary> list() {
		List<IncidentSummary> results = new ArrayList<>();
		try (var files = Files.list(incidents)) {
			files.filter(p -> p.getFileName().toString().endsWith(".json")).forEach(p -> {
				try {
					IncidentId id = IncidentId.of(p.getFileName().toString().replaceFirst("\\.json$", ""));
					AppData data = load(id);
					Instant modified = data.getUpdatedAt() == null
							? Files.getLastModifiedTime(p).toInstant()
							: data.getUpdatedAt();
					results.add(new IncidentSummary(p, data.getIncidentContext().getIncidentName(),
							data.getIapPhase(), modified, id, data.getForm201().getIncidentNumber(),
							data.getRevision(), root.toString()));
				} catch (RuntimeException | IOException e) {
					LOGGER.warning("Skipping unreadable incident " + p + ": " + e.getMessage());
				}
			});
		} catch (IOException e) {
			throw new IllegalStateException("Cannot list incidents", e);
		}
		results.sort(Comparator.comparing(IncidentSummary::lastModified).reversed());
		return results;
	}

	@Override
	public synchronized void delete(IncidentId id) {
		if (!lock(id)) {
			throw new StoreConflictException("Incident is open in another window: " + id);
		}
		Path path = path(id);
		try {
			move(path, path.resolveSibling(path.getFileName() + ".deleted"));
		} catch (IOException e) {
			throw new IllegalStateException("Cannot delete incident: " + id, e);
		} finally {
			unlock(id);
		}
	}

	@Override
	public Optional<IncidentId> importFile(Path jsonFile) {
		if (jsonFile == null) {
			return Optional.empty();
		}
		try {
			AppData data = MAPPER.readValue(jsonFile.toFile(), AppData.class);
			data.setIncidentId(null);
			data.setRevision(0);
			data.setCreatedAt(null);
			data.setUpdatedAt(null);
			data.setOriginNodeId(null);
			return Optional.of(create(data));
		} catch (IOException e) {
			throw new IllegalStateException("Cannot import incident: " + jsonFile, e);
		}
	}

	@Override
	public void exportFile(IncidentId id, Path target) {
		try {
			MAPPER.writerWithDefaultPrettyPrinter().writeValue(target.toFile(), load(id));
		} catch (IOException e) {
			throw new IllegalStateException("Cannot export incident: " + id, e);
		}
	}

	@Override
	public StoreDescriptor describe() {
		return new StoreDescriptor("File", root.toString());
	}

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
			return MAPPER.readValue(path.toFile(), AppData.class);
		} catch (IOException primaryFailure) {
			try {
				return MAPPER.readValue(backup(path).toFile(), AppData.class);
			} catch (IOException backupFailure) {
				backupFailure.addSuppressed(primaryFailure);
				throw new IllegalStateException("Cannot read incident or backup: " + path, backupFailure);
			}
		}
	}

	/**
	 * Normalizes pre-v6 documents after deserialization.
	 *
	 * @param data
	 *            loaded document.
	 * @param id
	 *            file's assigned identity.
	 */
	private void upgrade(AppData data, IncidentId id) {
		if (data.getIncidentId() == null) {
			data.setIncidentId(id.value());
			data.setRevision(0);
		}
		data.setSchemaVersion(AppData.CURRENT_SCHEMA_VERSION);
		if (data.getOriginNodeId() == null || data.getOriginNodeId().isBlank()) {
			data.setOriginNodeId(nodeId);
		}
		data.normalizeIdentity();
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
		importFile(legacy);
		try {
			Files.createFile(marker);
		} catch (java.nio.file.FileAlreadyExistsException ignored) {
			// Another instance completed migration first.
		}
	}
}
