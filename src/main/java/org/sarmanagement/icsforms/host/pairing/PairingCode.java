/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.host.pairing;

import java.security.SecureRandom;
import java.util.Locale;

/**
 * A short pairing code that a person can read aloud or type: eight characters of the Crockford base32 alphabet
 * (40 bits), shown as {@code XXXX-XXXX}. Entry is forgiving of case, hyphens, spaces and the look-alike letters
 * {@code O}, {@code I} and {@code L}.
 */
public final class PairingCode {
	/** Crockford base32: digits and letters without I, L, O and U. */
	static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
	/** Number of code characters. */
	public static final int LENGTH = 8;
	private static final SecureRandom RANDOM = new SecureRandom();

	/** Not instantiable; use the static methods. */
	private PairingCode() {
	}

	/**
	 * Generates a random code.
	 *
	 * @return normalized code of {@link #LENGTH} characters
	 */
	public static String generate() {
		StringBuilder code = new StringBuilder(LENGTH);
		for (int index = 0; index < LENGTH; index++) {
			code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
		}
		return code.toString();
	}

	/**
	 * Formats a normalized code for display.
	 *
	 * @param code normalized code
	 * @return code with a hyphen after the fourth character
	 */
	public static String display(String code) {
		return code.substring(0, LENGTH / 2) + "-" + code.substring(LENGTH / 2);
	}

	/**
	 * Normalizes typed input: upper case, no hyphens or spaces, {@code O} read as 0 and {@code I} or {@code L} as 1.
	 *
	 * @param input typed or pasted code
	 * @return normalized code
	 * @throws IllegalArgumentException when the input is not a valid code
	 */
	public static String normalize(String input) {
		String text = input == null ? "" : input.strip().toUpperCase(Locale.ROOT).replace("-", "").replace(" ", "");
		text = text.replace('O', '0').replace('I', '1').replace('L', '1');
		if (text.length() != LENGTH) {
			throw new IllegalArgumentException("A pairing code has " + LENGTH + " characters");
		}
		for (int index = 0; index < text.length(); index++) {
			if (ALPHABET.indexOf(text.charAt(index)) < 0) {
				throw new IllegalArgumentException("The pairing code contains an invalid character");
			}
		}
		return text;
	}
}
