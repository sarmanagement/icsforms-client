package org.sarmanagement.icsforms.persistence;

import org.sarmanagement.icsforms.model.IapPhase;

import java.nio.file.Path;
import java.time.Instant;

/**
 * Lightweight snapshot of a saved incident workspace used for listing without
 * loading the full document.
 *
 * @param path
 *            path to the JSON workspace file.
 * @param incidentName
 *            incident name from the shared context (empty string when not set).
 * @param iapPhase
 *            IAP phase recorded in the workspace.
 * @param lastModified
 *            file-system last-modified timestamp.
 * @param incidentId
 *            stable incident identity, or null for legacy listings.
 * @param incidentNumber
 *            incident number shown on the briefing form.
 * @param revision
 *            stored document revision.
 * @param storeLocation
 *            human-readable store location.
 */
public record IncidentSummary(Path path, String incidentName, IapPhase iapPhase, Instant lastModified,
		IncidentId incidentId, String incidentNumber, long revision, String storeLocation) {
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
	public IncidentSummary(Path path, String incidentName, IapPhase iapPhase, Instant lastModified) {
		this(path, incidentName, iapPhase, lastModified, null, "", 0, path.toString());
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
}
