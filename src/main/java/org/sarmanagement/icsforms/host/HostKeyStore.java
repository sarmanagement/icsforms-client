/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.host;

import java.util.Map;

/**
 * Boundary around host-owned key material so key management can later be separated from data backup.
 */
public interface HostKeyStore {
	/**
	 * Loads the host authority, creating and persisting a new one when none exists yet.
	 *
	 * @return host authority
	 * @throws HostStorageException when stored key material is unreadable or cannot be persisted
	 */
	HostAuthority loadOrCreate();

	/**
	 * Exports the key material for inclusion in a host backup. The result is sensitive.
	 *
	 * @return entry name to content, using names relative to the key store
	 * @throws HostStorageException when the key material cannot be read
	 */
	Map<String, byte[]> exportForBackup();

	/**
	 * Replaces the key material with content exported earlier. The host must not be running.
	 *
	 * @param entries entry name to content as produced by {@link #exportForBackup()}
	 * @throws HostStorageException when the material is invalid or cannot be persisted
	 */
	void importFromBackup(Map<String, byte[]> entries);
}
