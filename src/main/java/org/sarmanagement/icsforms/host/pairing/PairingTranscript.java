/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.host.pairing;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/** Builds the unambiguous transcript both parties feed to the channel suite. */
public final class PairingTranscript {
	/** Not instantiable; use {@link #of}. */
	private PairingTranscript() {
	}

	/**
	 * Concatenates parts with length prefixes so no two different sequences share an encoding.
	 *
	 * @param parts suite identifiers and exchange messages, in order
	 * @return transcript bytes
	 */
	public static byte[] of(byte[]... parts) {
		try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
				DataOutputStream out = new DataOutputStream(bytes)) {
			for (byte[] part : parts) {
				out.writeInt(part.length);
				out.write(part);
			}
			out.flush();
			return bytes.toByteArray();
		} catch (IOException impossible) {
			throw new IllegalStateException(impossible);
		}
	}
}
