/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.medical;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Set;

/**
 * Stores the ORS credential outside incident documents and regional files.
 */
public class OrsApiKeyStore {
	private final Path keyFile;

	/**
	 * Creates a key store in the current user's local ICS Forms directory.
	 */
	public OrsApiKeyStore() {
		this(Path.of(System.getProperty("user.home"), ".icsforms", "ors-api-key"));
	}

	/**
	 * Creates a key store at an explicit local configuration file.
	 *
	 * @param keyFile local credential file.
	 */
	public OrsApiKeyStore(Path keyFile) {
		this.keyFile = keyFile.toAbsolutePath();
	}

	/**
	 * Returns the key from the environment or local file without logging it.
	 *
	 * @return configured key.
	 */
	public String getApiKey() {
		String environmentKey = System.getenv("ORS_API_KEY");
		if (environmentKey != null && !environmentKey.isBlank()) {
			return environmentKey.trim();
		}
		try {
			if (Files.isRegularFile(keyFile)) {
				String key = Files.readString(keyFile).trim();
				if (!key.isBlank()) {
					return key;
				}
			}
		} catch (IOException exception) {
			throw new IllegalStateException("Cannot read the local OpenRouteService key file", exception);
		}
		throw new IllegalStateException("Configure ORS_API_KEY or the local ~/.icsforms/ors-api-key file");
	}

	/**
	 * Saves a key in a separate local file with owner-only permissions when supported.
	 *
	 * @param apiKey provider key.
	 * @throws IOException when the local configuration cannot be written.
	 */
	public void saveApiKey(String apiKey) throws IOException {
		if (apiKey == null || apiKey.isBlank()) {
			throw new IllegalArgumentException("API key must not be blank");
		}
		Path parent = keyFile.getParent();
		Files.createDirectories(parent);
		Path temporary = Files.createTempFile(parent, "ors-key-", ".tmp");
		try {
			Files.writeString(temporary, apiKey.trim());
			setOwnerOnlyPermissions(temporary);
			try {
				Files.move(temporary, keyFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			} catch (AtomicMoveNotSupportedException exception) {
				Files.move(temporary, keyFile, StandardCopyOption.REPLACE_EXISTING);
			}
			setOwnerOnlyPermissions(keyFile);
		} finally {
			Files.deleteIfExists(temporary);
		}
	}

	/**
	 * Restricts a credential file to its owner on POSIX filesystems.
	 *
	 * @param path credential path.
	 * @throws IOException when permissions cannot be applied.
	 */
	private void setOwnerOnlyPermissions(Path path) throws IOException {
		Set<PosixFilePermission> ownerOnly = EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
		try {
			Files.setPosixFilePermissions(path, ownerOnly);
		} catch (UnsupportedOperationException ignored) {
			// The platform does not expose POSIX permissions; keep the local file isolated.
		}
	}
}
