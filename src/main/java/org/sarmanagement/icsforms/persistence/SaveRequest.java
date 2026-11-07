/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.persistence;

import java.util.Optional;

/**
 * Detached persistence work. Implementations own their payload and coalescing policy;
 * the scheduler neither inspects documents nor assumes intermediate work can be dropped.
 */
public interface SaveRequest {
	/**
	 * Identifies the target within the configured store.
	 *
	 * @return target incident
	 */
	IncidentId incidentId();

	/**
	 * Identifies the incident/period/editor binding that produced this work.
	 *
	 * @return binding generation
	 */
	long generation();

	/**
	 * Identifies the last local edit represented by this work.
	 *
	 * @return local edit version
	 */
	long editVersion();

	/**
	 * Supplies the opaque revision against which the work was prepared.
	 *
	 * @return expected store revision
	 */
	RevisionToken expectedRevision();

	/**
	 * Rebases queued work onto the predecessor's accepted revision. Implementations
	 * must preserve their payload, identity, generation and edit version.
	 *
	 * @param revision accepted predecessor revision
	 * @return equivalent work with the new expected revision
	 */
	SaveRequest withExpectedRevision(RevisionToken revision);

	/**
	 * Prepares queued work after its predecessor is durable. The default only
	 * rebases the opaque token; concrete adapters can additionally carry backend
	 * metadata required by older store integrations, without scheduler knowledge.
	 *
	 * @param predecessor completed unit from the same binding
	 * @param revision accepted predecessor revision
	 * @return equivalent work prepared for the accepted predecessor
	 */
	default SaveRequest afterPredecessor(SaveRequest predecessor, RevisionToken revision) {
		return withExpectedRevision(revision);
	}

	/**
	 * Optionally combines adjacent queued work. Never called for in-flight work.
	 *
	 * @param newer next unit in edit order for the same binding and revision
	 * @return combined work, or empty to preserve both units in order
	 */
	Optional<SaveRequest> coalesce(SaveRequest newer);

	/**
	 * Performs I/O using only detached state, on the worker.
	 *
	 * @return revision issued by the successful write
	 */
	RevisionToken persist();
}
