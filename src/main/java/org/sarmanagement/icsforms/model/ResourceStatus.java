package org.sarmanagement.icsforms.model;

/**
 * Ordered lifecycle status for a resource within an operational period.
 *
 * <p>Status values are ordered from least to most active; the ordering may be used
 * to drive visual priority on the T-card rack.</p>
 *
 * <ul>
 *   <li>{@link #ORDERED} – resource has been requested but has not yet departed for
 *       the incident.</li>
 *   <li>{@link #ENROUTE} – resource is in transit to the incident or staging area.</li>
 *   <li>{@link #AVAILABLE} – resource has arrived and is available for assignment.</li>
 *   <li>{@link #ASSIGNED} – resource is currently deployed on a task or position.</li>
 *   <li>{@link #OUT_OF_SERVICE} – resource is temporarily unavailable (equipment issue,
 *       rest, medical, etc.).</li>
 *   <li>{@link #DEMOBILIZED} – resource has completed its assignment and been formally
 *       released from the incident; it may be re-activated in a later operational
 *       period but is no longer considered part of the active resource pool.</li>
 * </ul>
 */
public enum ResourceStatus {

    /** Resource has been ordered/requested; not yet arrived. */
    ORDERED("Ordered"),

    /** Resource is en route to the incident. */
    ENROUTE("Enroute"),

    /** Resource is available for assignment. */
    AVAILABLE("Available"),

    /** Resource is currently assigned to a task or position. */
    ASSIGNED("Assigned"),

    /** Resource is out of service. */
    OUT_OF_SERVICE("Out of Service"),

    /**
     * Resource has been formally released from the incident.
     *
     * <p>A demobilized resource may be reactivated and checked in during a later
     * operational period, at which point a new {@link OperationalPeriodAssignment}
     * is created with a fresh check-in date/time and status.</p>
     */
    DEMOBILIZED("Demobilized");

    private final String label;

    ResourceStatus(String label) {
        this.label = label;
    }

    /**
     * Returns the human-readable label for this status.
     *
     * @return status label.
     */
    public String getLabel() {
        return label;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return label;
    }

    /**
     * Returns the {@link ResourceStatus} whose {@link #getLabel() label} matches
     * {@code text} (case-insensitive), or {@code null} if not found.
     *
     * @param text status label text to look up.
     * @return matching status or {@code null}.
     */
    public static ResourceStatus fromLabel(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String norm = text.trim();
        for (ResourceStatus s : values()) {
            if (s.label.equalsIgnoreCase(norm)) {
                return s;
            }
        }
        return null;
    }
}
