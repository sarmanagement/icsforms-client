/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.host.pairing;

import java.util.Locale;

/**
 * The short number a device shows and the host operator types: four digits and two letters, written
 * {@code 4821-KH}. The letters I and O are never used. It is derived from the key exchange, so it is not a secret
 * shared in advance; it lets a person confirm that both ends of the exchange are the devices in front of them.
 * It has {@value #SPACE} values, about 22.5 bits.
 */
public final class PairingCode {
	/** Letters used, without I and O. */
	static final String LETTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ";
	/** Number of distinct codes. */
	public static final int SPACE = 10_000 * LETTERS.length() * LETTERS.length();
	/** Characters in a normalized code. */
	public static final int LENGTH = 6;

	private PairingCode() {
	}

	/**
	 * Builds the normalized code for a number in {@code [0, SPACE)}.
	 *
	 * @param number value derived from the exchange
	 * @return normalized code, four digits then two letters
	 * @throws IllegalArgumentException when the number is out of range
	 */
	public static String of(long number) {
		if (number < 0 || number >= SPACE) {
			throw new IllegalArgumentException("Code number out of range");
		}
		int letters = (int) (number % (LETTERS.length() * LETTERS.length()));
		int digits = (int) (number / (LETTERS.length() * LETTERS.length()));
		return String.format(Locale.ROOT, "%04d%c%c", digits, LETTERS.charAt(letters / LETTERS.length()),
				LETTERS.charAt(letters % LETTERS.length()));
	}

	/**
	 * Formats a normalized code for display.
	 *
	 * @param code normalized code
	 * @return the code with a dash after the digits
	 */
	public static String display(String code) {
		return code.substring(0, 4) + "-" + code.substring(4);
	}

	/**
	 * Normalizes typed input: case and the dash or spaces are ignored. In the digit positions the letters O and I
	 * are read as 0 and 1; the letters I and O are not valid in the letter positions.
	 *
	 * @param input typed or pasted code
	 * @return normalized code of {@link #LENGTH} characters
	 * @throws IllegalArgumentException when the input is not a valid code
	 */
	public static String normalize(String input) {
		String text = input == null ? "" : input.strip().toUpperCase(Locale.ROOT).replace("-", "").replace(" ", "");
		if (text.length() != LENGTH) {
			throw new IllegalArgumentException("A code is four digits and two letters, like 4821-KH");
		}
		StringBuilder result = new StringBuilder(LENGTH);
		for (int index = 0; index < LENGTH; index++) {
			char c = text.charAt(index);
			if (index < 4) {
				c = c == 'O' ? '0' : c == 'I' ? '1' : c;
				if (c < '0' || c > '9') {
					throw new IllegalArgumentException("The first four characters of a code are digits");
				}
			} else if (LETTERS.indexOf(c) < 0) {
				throw new IllegalArgumentException("The last two characters of a code are letters (not I or O)");
			}
			result.append(c);
		}
		return result.toString();
	}
}
