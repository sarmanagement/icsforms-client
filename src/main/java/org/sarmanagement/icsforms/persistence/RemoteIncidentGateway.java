/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.persistence;

/**
 * Transport seam between {@link RemoteIncidentStore} and a host's record service. Implementations perform blocking
 * network I/O, translate transport failures into the store exceptions, and never expose host file paths. Callers
 * must not invoke them on the Swing event-dispatch thread.
 */
public interface RemoteIncidentGateway extends AutoCloseable {
	/**
	 * Identifies the authenticated host authority without exposing credentials.
	 *
	 * @return pinned SHA-256 authority fingerprint, or empty when unavailable
	 */
	default java.util.Optional<String> authorityFingerprint() {
		return java.util.Optional.empty();
	}

	/**
	 * Host reachability as observed by a probe.
	 */
	enum HostProbe {
		/** The host is running and the credential is active. */
		RUNNING,
		/** The host announced that it is stopping. */
		STOPPING
	}

	/**
	 * Returns an opaque, path-free description of the host such as its address.
	 *
	 * @return display location
	 */
	String location();

	/**
	 * Reads every current record with its version and placement.
	 *
	 * @param incident shared incident
	 * @return current records
	 * @throws IncidentNotFoundException when the host does not share the incident
	 * @throws StoreUnavailableException when the host cannot be reached or is not serving
	 */
	IncidentService.ReadResult read(IncidentId incident);

	/**
	 * Reads changes after a committed cursor; the response cursor is the conditional-polling signal.
	 *
	 * @param incident shared incident
	 * @param after cursor already observed
	 * @return current cursor and later changes
	 * @throws StoreUnavailableException when the host cannot be reached or is not serving
	 */
	IncidentService.ChangeResult changesSince(IncidentId incident, long after);

	/**
	 * Submits an atomic batch. Field conflicts are returned as a result with {@code applied == false}.
	 *
	 * @param batch idempotent batch
	 * @return the host's result
	 * @throws StoreValidationException when the host rejects the batch as malformed
	 * @throws StoreUnavailableException when the host cannot be reached or is not serving
	 */
	IncidentService.SubmissionResult submit(IncidentService.RecordBatch batch);

	/**
	 * Checks the host's announced state and the credential's standing without touching incident data.
	 *
	 * @return the announced host state
	 * @throws RemoteAccessException when the credential or host identity is no longer accepted
	 * @throws StoreUnavailableException when the host cannot be reached
	 */
	HostProbe probe();

	/**
	 * Releases transport resources. Safe to call repeatedly.
	 */
	@Override
	void close();
}
