/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.host;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.sarmanagement.icsforms.persistence.EditLockSupport;
import org.sarmanagement.icsforms.persistence.IncidentId;
import org.sarmanagement.icsforms.persistence.IncidentStore;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Backs up and restores a shared incident together with the host-owned certificate authority key and device
 * registry as one consistent operation, so a restore can never combine versions that would re-admit a revoked
 * device. Backup files contain the authority private key: they are written owner-only and must be treated as
 * secrets. Nothing here logs backup content.
 */
public final class HostBackup {
	private static final int FORMAT = 1;
	private static final String MANIFEST = "manifest.json";
	private static final String REGISTRY = "host/registry.json";
	private static final String KEY_PREFIX = "host/keys/";
	private static final long MAX_ENTRY_BYTES = 256L * 1024 * 1024;

	/**
	 * Prevents instantiation.
	 */
	private HostBackup() {
	}

	/**
	 * Creates a backup of the incident and host-owned storage as a single consistent snapshot. The host identity
	 * is created first when sharing has never been started, so the backup always carries the authority key.
	 *
	 * @param store file-based incident store holding the incident
	 * @param incident incident to back up
	 * @param storage host-owned storage
	 * @param target new backup file, which must be outside the live store
	 * @throws HostStorageException when the backup cannot be created
	 */
	public static void create(IncidentStore store, IncidentId incident, HostStorage storage, Path target) {
		Path root = storeRoot(store);
		Path destination = target.toAbsolutePath().normalize();
		if (destination.startsWith(root)) {
			throw new HostStorageException("Choose a backup location outside the live incident store");
		}
		if (Files.exists(destination)) {
			throw new HostStorageException("The backup file already exists");
		}
		storage.keys().loadOrCreate();
		byte[] archive = store.withIncidentLock(incident, () -> storage.registry().withLock(() -> {
			try {
				return buildArchive(root, incident, storage);
			} catch (IOException exception) {
				throw new HostStorageException("Cannot read the files to back up", exception);
			}
		}));
		try {
			OwnerOnlyFiles.write(destination, archive);
		} catch (IOException exception) {
			throw new HostStorageException("Cannot write the backup file", exception);
		}
	}

	/**
	 * Restores a backup. Hosting must be stopped and the incident closed elsewhere; the incident's edit lock is
	 * taken for the duration so no editor can write concurrently. All content is verified before anything changes.
	 *
	 * @param store file-based incident store to restore into
	 * @param incident incident being restored
	 * @param storage host-owned storage
	 * @param source backup file
	 * @throws HostStorageException when verification fails or the incident is in use
	 */
	public static void restore(IncidentStore store, IncidentId incident, HostStorage storage, Path source) {
		restore(store, incident, storage, source, false);
	}

	/**
	 * Restores a backup, optionally into an incident the caller already holds open for editing. In that case the
	 * caller's existing exclusive lock is used and left in place, and the caller must reload the incident.
	 *
	 * @param store file-based incident store to restore into
	 * @param incident incident being restored
	 * @param storage host-owned storage
	 * @param source backup file
	 * @param callerMayHoldLock true when the calling editor may already hold the incident's edit lock
	 * @throws HostStorageException when verification fails or the incident is in use
	 */
	public static void restore(IncidentStore store, IncidentId incident, HostStorage storage, Path source,
			boolean callerMayHoldLock) {
		Path root = storeRoot(store);
		Optional<EditLockSupport> locks = store.editLocks();
		if (locks.isEmpty()) {
			throw new HostStorageException("This store cannot guarantee exclusive access for a restore");
		}
		Map<String, byte[]> entries = readVerified(source, incident);
		DeviceRegistry.validateBackup(entries.get(REGISTRY));
		Map<String, byte[]> keys = new LinkedHashMap<>();
		for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
			if (entry.getKey().startsWith(KEY_PREFIX)) {
				keys.put(entry.getKey().substring(KEY_PREFIX.length()), entry.getValue());
			}
		}
		EditLockSupport lockSupport = locks.get();
		boolean held = lockSupport.isHeldByCaller(incident);
		if (held && !callerMayHoldLock || !held && !lockSupport.lock(incident)) {
			throw new HostStorageException("The incident is open or being shared; stop sharing and close it first");
		}
		try {
			store.withIncidentLock(incident, () -> storage.registry().withLock(() -> {
				applyRestore(root, incident, storage, entries, keys);
				return null;
			}));
		} finally {
			if (!held) {
				lockSupport.unlock(incident);
			}
		}
	}

	/**
	 * Writes the verified content into place.
	 *
	 * @param root store root
	 * @param incident incident being restored
	 * @param storage host-owned storage
	 * @param entries verified archive content
	 * @param keys key store entries without the archive prefix
	 */
	private static void applyRestore(Path root, IncidentId incident, HostStorage storage, Map<String, byte[]> entries,
			Map<String, byte[]> keys) {
		try {
			Path incidents = root.resolve("incidents");
			Files.createDirectories(incidents);
			stage(incidents.resolve(incident.value() + ".json"), entries.get(incidentFile(incident)));
			byte[] records = entries.get(recordsFile(incident));
			Path recordsPath = incidents.resolve(incident.value() + ".records.json");
			if (records != null) {
				stage(recordsPath, records);
			} else {
				Files.deleteIfExists(recordsPath);
			}
			storage.keys().importFromBackup(keys);
			storage.registry().restore(entries.get(REGISTRY));
		} catch (IOException exception) {
			throw new HostStorageException("Cannot restore the backup", exception);
		}
	}

	/**
	 * Writes a file through a temporary sibling and an atomic move.
	 *
	 * @param target destination
	 * @param content bytes to write
	 * @throws IOException when the write fails
	 */
	private static void stage(Path target, byte[] content) throws IOException {
		Path temporary = target.resolveSibling(target.getFileName() + ".restore");
		Files.write(temporary, content);
		try {
			Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} catch (java.nio.file.AtomicMoveNotSupportedException exception) {
			Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	/**
	 * Builds the archive bytes.
	 *
	 * @param root store root
	 * @param incident incident to back up
	 * @param storage host-owned storage
	 * @return zip content
	 * @throws IOException when a source file cannot be read
	 */
	private static byte[] buildArchive(Path root, IncidentId incident, HostStorage storage) throws IOException {
		Map<String, byte[]> entries = new LinkedHashMap<>();
		Path incidents = root.resolve("incidents");
		Path document = incidents.resolve(incident.value() + ".json");
		if (!Files.exists(document)) {
			throw new HostStorageException("The incident to back up was not found");
		}
		entries.put(incidentFile(incident), Files.readAllBytes(document));
		Path records = incidents.resolve(incident.value() + ".records.json");
		if (Files.exists(records)) {
			entries.put(recordsFile(incident), Files.readAllBytes(records));
		}
		entries.put(REGISTRY, storage.registry().snapshot());
		for (Map.Entry<String, byte[]> key : storage.keys().exportForBackup().entrySet()) {
			entries.put(KEY_PREFIX + key.getKey(), key.getValue());
		}
		ObjectNode manifest = HostHttp.MAPPER.createObjectNode();
		manifest.put("format", FORMAT);
		manifest.put("incidentId", incident.value());
		ObjectNode hashes = manifest.putObject("sha256");
		for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
			hashes.put(entry.getKey(), HostCrypto.sha256(new String(entry.getValue(), StandardCharsets.ISO_8859_1)));
		}
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
			add(zip, MANIFEST, manifest.toString().getBytes(StandardCharsets.UTF_8));
			for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
				add(zip, entry.getKey(), entry.getValue());
			}
		}
		return bytes.toByteArray();
	}

	/**
	 * Adds one entry.
	 *
	 * @param zip archive stream
	 * @param name entry name
	 * @param content entry bytes
	 * @throws IOException when writing fails
	 */
	private static void add(ZipOutputStream zip, String name, byte[] content) throws IOException {
		zip.putNextEntry(new ZipEntry(name));
		zip.write(content);
		zip.closeEntry();
	}

	/**
	 * Reads an archive, accepting only known entry names and verifying every hash.
	 *
	 * @param source archive file
	 * @param incident incident the restore targets
	 * @return verified entries by name, excluding the manifest
	 * @throws HostStorageException when the archive is unreadable, tampered with or for another incident
	 */
	private static Map<String, byte[]> readVerified(Path source, IncidentId incident) {
		Map<String, byte[]> entries = new HashMap<>();
		try (InputStream in = Files.newInputStream(source); ZipInputStream zip = new ZipInputStream(in)) {
			for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
				String name = entry.getName();
				if (!allowed(name, incident) || entries.containsKey(name)) {
					throw new HostStorageException("The backup contains an unexpected entry");
				}
				entries.put(name, readLimited(zip));
			}
		} catch (IOException exception) {
			throw new HostStorageException("The backup file cannot be read", exception);
		}
		byte[] manifestBytes = entries.remove(MANIFEST);
		if (manifestBytes == null) {
			throw new HostStorageException("The backup has no manifest");
		}
		try {
			JsonNode manifest = HostHttp.MAPPER.readTree(new ByteArrayInputStream(manifestBytes));
			if (manifest.path("format").asInt() != FORMAT) {
				throw new HostStorageException("The backup format is not supported");
			}
			if (!incident.value().equals(manifest.path("incidentId").asText())) {
				throw new HostStorageException("The backup is for a different incident");
			}
			JsonNode hashes = manifest.path("sha256");
			for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
				String expected = hashes.path(entry.getKey()).asText("");
				String actual = HostCrypto.sha256(new String(entry.getValue(), StandardCharsets.ISO_8859_1));
				if (!actual.equals(expected)) {
					throw new HostStorageException("The backup failed its integrity check");
				}
			}
			if (hashes.size() != entries.size()) {
				throw new HostStorageException("The backup failed its integrity check");
			}
		} catch (IOException exception) {
			throw new HostStorageException("The backup manifest is not valid", exception);
		}
		if (!entries.containsKey(incidentFile(incident)) || !entries.containsKey(REGISTRY)) {
			throw new HostStorageException("The backup is incomplete");
		}
		return entries;
	}

	/**
	 * Reads an entry with a size cap.
	 *
	 * @param in archive stream positioned at an entry
	 * @return entry bytes
	 * @throws IOException when reading fails or the entry is too large
	 */
	private static byte[] readLimited(InputStream in) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buffer = new byte[8192];
		long total = 0;
		for (int read = in.read(buffer); read >= 0; read = in.read(buffer)) {
			total += read;
			if (total > MAX_ENTRY_BYTES) {
				throw new IOException("Backup entry is too large");
			}
			out.write(buffer, 0, read);
		}
		return out.toByteArray();
	}

	/**
	 * Tests whether an entry name is one this format defines.
	 *
	 * @param name entry name
	 * @param incident incident being restored
	 * @return true for known names
	 */
	private static boolean allowed(String name, IncidentId incident) {
		return MANIFEST.equals(name) || REGISTRY.equals(name) || incidentFile(incident).equals(name)
				|| recordsFile(incident).equals(name) || name.startsWith(KEY_PREFIX)
						&& name.substring(KEY_PREFIX.length()).matches("[A-Za-z0-9.]{1,32}");
	}

	/**
	 * Names the incident document entry.
	 *
	 * @param incident incident
	 * @return entry name
	 */
	private static String incidentFile(IncidentId incident) {
		return "incidents/" + incident.value() + ".json";
	}

	/**
	 * Names the record history entry.
	 *
	 * @param incident incident
	 * @return entry name
	 */
	private static String recordsFile(IncidentId incident) {
		return "incidents/" + incident.value() + ".records.json";
	}

	/**
	 * Resolves the root directory of a file store.
	 *
	 * @param store store
	 * @return absolute normalized root
	 * @throws HostStorageException when the store is not file based
	 */
	private static Path storeRoot(IncidentStore store) {
		if (!"File".equals(store.describe().type())) {
			throw new HostStorageException("Host backup requires a file-based incident store");
		}
		return Path.of(store.describe().location()).toAbsolutePath().normalize();
	}
}
