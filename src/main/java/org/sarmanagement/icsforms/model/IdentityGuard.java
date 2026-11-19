/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.model;

import java.util.Objects;

/** Enforces the write-once identity contract for mutable persisted records. */
final class IdentityGuard {
	/** Prevents construction of the identity guard. */
	private IdentityGuard() {
	}

	/**
	 * Rejects replacing an identity that has already been assigned.
	 *
	 * @param current currently assigned identity
	 * @param requested requested identity
	 * @throws IllegalStateException when a nonblank identity would change
	 */
	static void check(String current, String requested) {
		if (current != null && !current.isBlank() && !Objects.equals(current, requested)) {
			throw new IllegalStateException("An assigned record identity cannot be changed.");
		}
	}
}
