/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.persistence;

import java.util.Objects;

/**
 * Opaque equality-only revision value. Callers must not parse, order or increment it.
 *
 * @param value backend-issued value, scoped to an incident in a store
 */
public record RevisionToken(String value) {
	/**
	 * Requires a non-null opaque value.
	 *
	 * @param value backend-issued value
	 */
	public RevisionToken {
		Objects.requireNonNull(value, "value");
	}
}
