/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.host;

import java.io.Closeable;

/**
 * Receives the short number a joining device must show so the host operator can type it in. Implementations
 * display it, and the returned handle is closed when pairing ends.
 */
@FunctionalInterface
public interface PairingListener {
	/** A listener that shows nothing, for callers that obtain the number another way. */
	PairingListener NONE = code -> () -> {
	};

	/**
	 * Shows the number to the person operating this device.
	 *
	 * @param displayCode the number, for example {@code 4821-KH}
	 * @return handle closed when pairing finishes or fails
	 */
	Closeable showCode(String displayCode);
}
