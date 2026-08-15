package org.sarmanagement.icsforms.model;

/**
 * Operational mode for the incident workspace.
 *
 * <ul>
 *   <li>{@link #SAR} – search and rescue mode: clue log and SAR task assignment forms are enabled.</li>
 *   <li>{@link #GENERIC} – generic incident mode: clue log and SAR task assignment forms are hidden.</li>
 * </ul>
 */
public enum IncidentMode {
    /** Search and rescue mode – all SAR-specific features enabled. */
    SAR,
    /** Generic incident mode – SAR-specific features (clue log, SAR task forms) are hidden. */
    GENERIC
}
