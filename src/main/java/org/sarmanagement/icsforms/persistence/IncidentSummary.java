/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.persistence;

import org.sarmanagement.icsforms.model.IapPhase;

import java.nio.file.Path;
import java.net.URI;
import java.time.Instant;
import java.util.Objects;

/**
 * Lightweight snapshot of a saved incident workspace used for listing without
 * loading the full document.
 * A filesystem path is retained only by deprecated compatibility constructors;
 * backend-neutral summaries need only a display location and an opaque token.
 */
public final class IncidentSummary {
	private final String storeLocation;
	private final String incidentName;
	private final IapPhase iapPhase;
	private final Instant lastModified;
	private final IncidentId incidentId;
	private final String incidentNumber;
	private final RevisionToken revision;
	private final Path legacyPath;

	/**
	 * Creates a backend-neutral listing snapshot.
	 *
	 * @param storeLocation human-readable backend location.
	 * @param incidentName incident display name.
	 * @param iapPhase saved planning phase.
	 * @param lastModified last update timestamp.
	 * @param incidentId stable incident identity.
	 * @param incidentNumber briefing incident number.
	 * @param revision opaque stored revision.
	 */
	public IncidentSummary(String storeLocation, String incidentName, IapPhase iapPhase, Instant lastModified,
			IncidentId incidentId, String incidentNumber, RevisionToken revision) {
		this(storeLocation, incidentName, iapPhase, lastModified, incidentId, incidentNumber, revision, null);
	}

	/**
	 * Initializes a summary with optional legacy path metadata.
	 *
	 * @param storeLocation display location.
	 * @param incidentName display name.
	 * @param iapPhase phase.
	 * @param lastModified update timestamp.
	 * @param incidentId identity.
	 * @param incidentNumber incident number.
	 * @param revision stored token.
	 * @param legacyPath exact deprecated constructor path, or null.
	 */
	private IncidentSummary(String storeLocation, String incidentName, IapPhase iapPhase, Instant lastModified,
			IncidentId incidentId, String incidentNumber, RevisionToken revision, Path legacyPath) {
		this.storeLocation = storeLocation;
		this.incidentName = incidentName;
		this.iapPhase = iapPhase;
		this.lastModified = lastModified;
		this.incidentId = incidentId;
		this.incidentNumber = incidentNumber;
		this.revision = revision;
		this.legacyPath = legacyPath;
	}

	/** @return backend-neutral display location. */
	public String storeLocation() {
		return storeLocation;
	}

	/** @return incident display name. */
	public String incidentName() {
		return incidentName;
	}

	/** @return saved planning phase. */
	public IapPhase iapPhase() {
		return iapPhase;
	}

	/** @return last update timestamp. */
	public Instant lastModified() {
		return lastModified;
	}

	/** @return stable incident identity. */
	public IncidentId incidentId() {
		return incidentId;
	}

	/** @return briefing incident number. */
	public String incidentNumber() {
		return incidentNumber;
	}

	/** @return opaque stored revision. */
	public RevisionToken revision() {
		return revision;
	}

	/**
	 * Retains the old eight-argument constructor's exact path and display location.
	 *
	 * @param path workspace path, no longer part of store-neutral identity
	 * @param incidentName incident name
	 * @param iapPhase phase
	 * @param lastModified modification time
	 * @param incidentId incident identity
	 * @param incidentNumber incident number
	 * @param revision legacy numeric revision
	 * @param storeLocation legacy display label
	 */
	@Deprecated
	public IncidentSummary(Path path, String incidentName, IapPhase iapPhase, Instant lastModified,
			IncidentId incidentId, String incidentNumber, long revision, String storeLocation) {
		this(storeLocation, incidentName, iapPhase, lastModified, incidentId, incidentNumber,
				LegacyIncidentStoreAdapter.revision(revision), path);
	}

	/**
	 * Retains the legacy path-based summary constructor.
	 *
	 * @param path
	 *            workspace path.
	 * @param incidentName
	 *            incident name.
	 * @param iapPhase
	 *            phase.
	 * @param lastModified
	 *            modification time.
	 */
	@Deprecated
	public IncidentSummary(Path path, String incidentName, IapPhase iapPhase, Instant lastModified) {
		this(path.toString(), incidentName, iapPhase, lastModified, null, "",
				LegacyIncidentStoreAdapter.revision(0), path);
	}

	/**
	 * Returns the exact legacy constructor path, or an absolute local path/file URI.
	 * Neutral remote, relative and malformed locations have no local path. Store-neutral
	 * callers must never use this method as incident identity; exact file integrations
	 * should use {@link IncidentStore#localFile}.
	 *
	 * @return legacy local path, or null when the location is not a local path
	 */
	@Deprecated
	public Path path() {
		if (legacyPath != null) {
			return legacyPath;
		}
		if (storeLocation == null) {
			return null;
		}
		try {
			Path local = storeLocation.startsWith("file:") ? Path.of(URI.create(storeLocation))
					: Path.of(storeLocation);
			return local.isAbsolute() ? local : null;
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	/**
	 * Returns a human-readable label suitable for display in a list.
	 *
	 * @return display label combining incident name and IAP phase.
	 */
	public String displayLabel() {
		String name = (incidentName == null || incidentName.isBlank()) ? "(unnamed)" : incidentName;
		String phase;
		if (iapPhase == null) {
			phase = "Unknown";
		} else {
			phase = switch (iapPhase) {
				case PRE_OP -> "Pre-Operational";
				case INITIAL_RESPONSE -> "Initial Response";
				case DURING_OP -> "Operational Period";
			};
		}
		return name + "  [" + phase + "]";
	}

	/**
	 * Compares backend-neutral listing values.
	 *
	 * @param other value to compare.
	 * @return whether all listing values match.
	 */
	@Override
	public boolean equals(Object other) {
		if (!(other instanceof IncidentSummary summary)) {
			return false;
		}
		return Objects.equals(storeLocation, summary.storeLocation)
				&& Objects.equals(incidentName, summary.incidentName) && iapPhase == summary.iapPhase
				&& Objects.equals(lastModified, summary.lastModified) && Objects.equals(incidentId, summary.incidentId)
				&& Objects.equals(incidentNumber, summary.incidentNumber) && Objects.equals(revision, summary.revision);
	}

	/** @return hash of the backend-neutral listing values. */
	@Override
	public int hashCode() {
		return Objects.hash(storeLocation, incidentName, iapPhase, lastModified, incidentId, incidentNumber, revision);
	}

	/** @return diagnostic representation of the backend-neutral listing values. */
	@Override
	public String toString() {
		return "IncidentSummary[storeLocation=" + storeLocation + ", incidentName=" + incidentName
				+ ", iapPhase=" + iapPhase + ", lastModified=" + lastModified + ", incidentId=" + incidentId
				+ ", incidentNumber=" + incidentNumber + ", revision=" + revision + "]";
	}
}
