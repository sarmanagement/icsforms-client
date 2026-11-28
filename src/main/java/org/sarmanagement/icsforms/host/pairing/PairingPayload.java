/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.host.pairing;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * What a QR code (or its pasted text) carries for short-code pairing: where the host is, and the host authority
 * fingerprint. The fingerprint travels out of band, so a device that reads it pins the host before
 * any network exchange. The text is upper case so it encodes in a QR code's compact alphanumeric mode.
 *
 * @param host host name or address
 * @param port mutual-TLS port
 * @param enrollmentPort enrollment port
 * @param fingerprint SHA-256 fingerprint of the host authority certificate, lower-case hex
 */
public record PairingPayload(String host, int port, int enrollmentPort, String fingerprint) {
	/** Text prefix identifying the payload and its version. */
	public static final String PREFIX = "ICSFORMS-PAIR-3:";
	private static final Pattern FINGERPRINT = Pattern.compile("[0-9a-f]{64}");

	/**
	 * Validates the components.
	 */
	public PairingPayload {
		if (host == null || host.isBlank() || host.contains(" ")) {
			throw new IllegalArgumentException("Missing host");
		}
		if (port < 1 || port > 65535 || enrollmentPort < 1 || enrollmentPort > 65535) {
			throw new IllegalArgumentException("Invalid port");
		}
		fingerprint = fingerprint == null ? "" : fingerprint.toLowerCase(Locale.ROOT);
		if (!FINGERPRINT.matcher(fingerprint).matches()) {
			throw new IllegalArgumentException("Invalid fingerprint");
		}
	}

	/**
	 * Tells whether text looks like a pairing payload.
	 *
	 * @param text candidate text
	 * @return true when the text starts with the payload prefix
	 */
	public static boolean isPayload(String text) {
		return text != null && text.strip().toUpperCase(Locale.ROOT).startsWith(PREFIX);
	}

	/**
	 * Encodes the payload in upper case. The host is the only part that is parsed from the left, so IPv6 literals
	 * with colons survive.
	 *
	 * @return text for a QR code or clipboard
	 */
	public String toText() {
		return (PREFIX + host + ":" + port + ":" + enrollmentPort + ":" + fingerprint)
				.toUpperCase(Locale.ROOT);
	}

	/**
	 * Parses payload text.
	 *
	 * @param text payload text, surrounding whitespace ignored
	 * @return the payload
	 * @throws IllegalArgumentException when the text is not a valid payload
	 */
	public static PairingPayload parse(String text) {
		String trimmed = text == null ? "" : text.strip();
		if (!isPayload(trimmed)) {
			throw new IllegalArgumentException("Not an ICS Forms pairing payload");
		}
		String[] parts = trimmed.substring(PREFIX.length()).split(":");
		if (parts.length < 4) {
			throw new IllegalArgumentException("Pairing payload is malformed");
		}
		int last = parts.length - 1;
		try {
			String host = String.join(":", java.util.Arrays.copyOfRange(parts, 0, parts.length - 3));
			return new PairingPayload(host.toLowerCase(Locale.ROOT), Integer.parseInt(parts[last - 2]),
					Integer.parseInt(parts[last - 1]), parts[last]);
		} catch (NumberFormatException exception) {
			throw new IllegalArgumentException("Pairing payload is malformed", exception);
		}
	}
}
