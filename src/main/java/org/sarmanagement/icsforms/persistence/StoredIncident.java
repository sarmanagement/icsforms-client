/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.persistence;

import org.sarmanagement.icsforms.model.AppData;
import java.util.Objects;

/**
 * Detached editable document and the concurrency token for exactly that snapshot.
 *
 * @param data detached document
 * @param revision backend-issued revision
 */
public record StoredIncident(AppData data, RevisionToken revision) {
	/**
	 * Requires a complete snapshot.
	 *
	 * @param data detached document
	 * @param revision backend-issued revision
	 */
	public StoredIncident {
		Objects.requireNonNull(data, "data");
		Objects.requireNonNull(revision, "revision");
	}
}
