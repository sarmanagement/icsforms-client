/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.host;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;

/**
 * Host-owned storage: the certificate authority key store and the device registry, kept together in one owner-only
 * directory beneath the incident store root so that they are backed up with the incident data.
 */
public final class HostStorage {
	private final Path directory;
	private final HostKeyStore keys;
	private final DeviceRegistry registry;

	/**
	 * Opens (creating if needed) host-owned storage.
	 *
	 * @param storeRoot root directory of the incident store
	 * @param clock time source
	 * @throws HostStorageException when the directory cannot be prepared
	 */
	public HostStorage(Path storeRoot, Clock clock) {
		this.directory = storeRoot.toAbsolutePath().resolve("host");
		try {
			OwnerOnlyFiles.createDirectory(directory);
		} catch (IOException exception) {
			throw new HostStorageException("Cannot prepare host-owned storage", exception);
		}
		this.keys = new FileHostKeyStore(directory, clock);
		this.registry = new DeviceRegistry(directory.resolve("registry.json"), clock);
	}

	/**
	 * Creates storage with a caller-supplied key store, keeping key management behind its boundary.
	 *
	 * @param storeRoot root directory of the incident store
	 * @param keys key store implementation
	 * @param clock time source
	 * @throws HostStorageException when the directory cannot be prepared
	 */
	HostStorage(Path storeRoot, HostKeyStore keys, Clock clock) {
		this.directory = storeRoot.toAbsolutePath().resolve("host");
		try {
			OwnerOnlyFiles.createDirectory(directory);
		} catch (IOException exception) {
			throw new HostStorageException("Cannot prepare host-owned storage", exception);
		}
		this.keys = keys;
		this.registry = new DeviceRegistry(directory.resolve("registry.json"), clock);
	}

	/**
	 * Returns the directory holding host-owned files.
	 *
	 * @return directory
	 */
	Path directory() {
		return directory;
	}

	/**
	 * Returns the key store boundary.
	 *
	 * @return key store
	 */
	HostKeyStore keys() {
		return keys;
	}

	/**
	 * Returns the device registry.
	 *
	 * @return registry
	 */
	public DeviceRegistry registry() {
		return registry;
	}
}
