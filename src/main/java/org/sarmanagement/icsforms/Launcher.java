/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;

/**
 * Entry point of the packaged application jar. The Bouncy Castle FIPS module verifies the checksum of its own jar
 * file, so it cannot be merged into the application jar. The build embeds it unmodified; this launcher copies it
 * to a per-user directory and runs {@link App} from a class loader that can see it. When the module is already on
 * the class path (development and tests) the application is started directly.
 */
public final class Launcher {
	private static final String[] MODULES = {"bc-fips.jar", "bcpkix-fips.jar"};
	private static final String PROBE = "org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider";

	private Launcher() {
	}

	/**
	 * Starts the application.
	 *
	 * @param args command line arguments, passed to {@link App#main(String[])}
	 * @throws Exception when the cryptographic module cannot be prepared or the application cannot start
	 */
	public static void main(String[] args) throws Exception {
		if (onClassPath()) {
			App.main(args);
			return;
		}
		Path directory = Path.of(System.getProperty("user.home"), ".icsforms", "lib");
		URL[] urls = new URL[MODULES.length + 1];
		for (int i = 0; i < MODULES.length; i++) {
			urls[i] = extract(Launcher.class.getClassLoader(), "fips/" + MODULES[i], directory).toUri().toURL();
		}
		urls[MODULES.length] = Launcher.class.getProtectionDomain().getCodeSource().getLocation();
		// The platform loader as parent keeps application classes out of the system loader, so the module and
		// everything that uses it load together from the extracted files. The loader stays open for the life of
		// the application.
		URLClassLoader loader = new URLClassLoader(urls, ClassLoader.getPlatformClassLoader());
		Thread.currentThread().setContextClassLoader(loader);
		try {
			loader.loadClass(App.class.getName()).getMethod("main", String[].class).invoke(null, (Object) args);
		} catch (InvocationTargetException failure) {
			if (failure.getCause() instanceof Exception cause) {
				throw cause;
			}
			throw failure;
		}
	}

	/**
	 * Tells whether the FIPS module is already visible to this class's loader.
	 *
	 * @return true when it is on the class path
	 */
	private static boolean onClassPath() {
		try {
			Class.forName(PROBE, false, Launcher.class.getClassLoader());
			return true;
		} catch (ClassNotFoundException absent) {
			return false;
		}
	}

	/**
	 * Copies an embedded resource to a directory unless an identical file is already there. A file that differs is
	 * replaced, so a damaged or outdated copy never reaches the module's integrity check.
	 *
	 * @param loader loader holding the resource
	 * @param resource resource name
	 * @param directory target directory, created when missing
	 * @return the extracted file
	 * @throws IOException when the resource is missing or the file cannot be written
	 */
	static Path extract(ClassLoader loader, String resource, Path directory) throws IOException {
		byte[] wanted;
		try (InputStream in = loader.getResourceAsStream(resource)) {
			if (in == null) {
				throw new IOException("Missing embedded resource " + resource);
			}
			wanted = in.readAllBytes();
		}
		Files.createDirectories(directory);
		String name = resource.substring(resource.lastIndexOf('/') + 1);
		String hash = HexFormat.of().formatHex(sha256(wanted)).substring(0, 16);
		Path target = directory.resolve(name.replace(".jar", "-" + hash + ".jar"));
		if (Files.isRegularFile(target) && Arrays.equals(sha256(Files.readAllBytes(target)), sha256(wanted))) {
			return target;
		}
		Path temporary = Files.createTempFile(directory, name, ".tmp");
		try {
			Files.write(temporary, wanted);
			Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} finally {
			Files.deleteIfExists(temporary);
		}
		return target;
	}

	/**
	 * Computes a SHA-256 digest.
	 *
	 * @param data bytes to hash
	 * @return the digest
	 */
	private static byte[] sha256(byte[] data) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(data);
		} catch (NoSuchAlgorithmException impossible) {
			throw new IllegalStateException(impossible);
		}
	}
}
