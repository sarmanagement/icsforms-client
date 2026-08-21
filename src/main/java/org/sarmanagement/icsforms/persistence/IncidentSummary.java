package org.sarmanagement.icsforms.persistence;

import org.sarmanagement.icsforms.model.IapPhase;

import java.nio.file.Path;
import java.time.Instant;

/**
 * Lightweight snapshot of a saved incident workspace used for listing without loading the full
 * document.
 *
 * @param path         path to the JSON workspace file.
 * @param incidentName incident name from the shared context (empty string when not set).
 * @param iapPhase     IAP phase recorded in the workspace.
 * @param lastModified file-system last-modified timestamp.
 */
public record IncidentSummary(Path path, String incidentName, IapPhase iapPhase, Instant lastModified) {

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
