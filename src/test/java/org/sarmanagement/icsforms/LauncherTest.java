/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verifies extraction of the embedded cryptographic module. */
class LauncherTest {
	@TempDir
	Path directory;

	@Test
	void extractsOnceAndRepairsADamagedCopy() throws IOException {
		ClassLoader loader = LauncherTest.class.getClassLoader();
		Path first = Launcher.extract(loader, "org/sarmanagement/icsforms/launcher-sample.bin", directory);
		byte[] original = Files.readAllBytes(first);
		assertEquals(first, Launcher.extract(loader, "org/sarmanagement/icsforms/launcher-sample.bin", directory));
		Files.write(first, new byte[] {1, 2, 3});
		Path repaired = Launcher.extract(loader, "org/sarmanagement/icsforms/launcher-sample.bin", directory);
		assertEquals(first, repaired);
		assertArrayEquals(original, Files.readAllBytes(repaired));
	}

	@Test
	void missingResourceFails() {
		assertThrows(IOException.class,
				() -> Launcher.extract(LauncherTest.class.getClassLoader(), "no/such/resource.jar", directory));
	}
}
