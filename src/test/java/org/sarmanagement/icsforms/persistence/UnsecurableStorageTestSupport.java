/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.persistence;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Allows UI tests to simulate an unsecurable cache volume without affecting host storage. */
public final class UnsecurableStorageTestSupport {
	private UnsecurableStorageTestSupport() {
	}

	/**
	 * Simulates an unsecurable volume under the given root.
	 *
	 * @param root cache root on the simulated volume
	 */
	public static void install(Path root) {
		Path absolute = root.toAbsolutePath().normalize();
		PrivateFiles.setStoreResolver(path -> {
			if (path.toAbsolutePath().normalize().startsWith(absolute)) {
				return new PrivateFilesTest.FakeStore(false, false);
			}
			try {
				return Files.getFileStore(path);
			} catch (IOException failure) {
				throw new UncheckedIOException(failure);
			}
		});
	}

	/** Restores normal file-store resolution. */
	public static void reset() {
		PrivateFiles.setStoreResolver(null);
	}
}
