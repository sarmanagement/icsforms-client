/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.host;

import java.util.List;

/**
 * Wire-protocol constants and version negotiation shared by the host and its clients.
 */
public final class HostProtocol {
	/** The API version implemented by this host. */
	public static final int API_VERSION = 1;
	/** Request header carrying the client's API version. */
	public static final String API_VERSION_HEADER = "X-ICS-Api-Version";
	/** Request header carrying the client application version. */
	public static final String CLIENT_VERSION_HEADER = "X-ICS-Client-Version";
	/** Default lowest client application version accepted by a host. */
	public static final String DEFAULT_MINIMUM_CLIENT_VERSION = "0.1.0";
	/** Largest accepted request body of the data endpoints, in bytes. */
	public static final int MAX_REQUEST_BYTES = 1024 * 1024;
	/** Largest accepted record count in a submitted batch. */
	public static final int MAX_BATCH_RECORDS = 100;
	/** Largest accepted request body of the unauthenticated pairing endpoint, in bytes. */
	public static final int MAX_PAIRING_BYTES = 16 * 1024;
	/** Versions this host supports. */
	static final List<Integer> SUPPORTED_API_VERSIONS = List.of(API_VERSION);

	/**
	 * Prevents construction of this utility class.
	 */
	private HostProtocol() {
	}

	/**
	 * Returns this application's version for the client version header.
	 *
	 * @return implementation version, or the default minimum when running from classes
	 */
	public static String clientVersion() {
		String version = HostProtocol.class.getPackage().getImplementationVersion();
		return version == null ? DEFAULT_MINIMUM_CLIENT_VERSION : version;
	}

	/**
	 * Compares dotted numeric versions, ignoring qualifiers such as {@code -SNAPSHOT}.
	 *
	 * @param version version to test
	 * @param minimum lowest acceptable version
	 * @return true when {@code version} is well formed and at least {@code minimum}
	 */
	static boolean atLeast(String version, String minimum) {
		int[] actual = parse(version);
		int[] required = parse(minimum);
		if (actual == null || required == null) {
			return false;
		}
		for (int index = 0; index < actual.length; index++) {
			if (actual[index] != required[index]) {
				return actual[index] > required[index];
			}
		}
		return true;
	}

	/**
	 * Parses the first three numeric components of a version.
	 *
	 * @param version version text
	 * @return three components, or null when malformed
	 */
	private static int[] parse(String version) {
		if (version == null) {
			return null;
		}
		String numeric = version.strip().split("[-+ ]", 2)[0];
		String[] parts = numeric.split("\\.");
		if (parts.length == 0 || parts.length > 4) {
			return null;
		}
		int[] result = new int[3];
		try {
			for (int index = 0; index < parts.length && index < 3; index++) {
				result[index] = Integer.parseInt(parts[index]);
				if (result[index] < 0) {
					return null;
				}
			}
		} catch (NumberFormatException exception) {
			return null;
		}
		return result;
	}
}
