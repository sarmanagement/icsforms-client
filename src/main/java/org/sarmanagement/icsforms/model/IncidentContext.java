package org.sarmanagement.icsforms.model;

import java.time.LocalDateTime;

/**
 * Shared incident metadata reused across ICS forms in the local workspace.
 */
public class IncidentContext {
    private String incidentName = "";
    private LocalDateTime operationalPeriodStart;
    private LocalDateTime operationalPeriodEnd;
    private String currentUser = "";

    /**
     * Creates an empty incident context.
     */
    public IncidentContext() {
    }

    /**
     * Creates a populated incident context.
     *
     * @param incidentName incident name shown on all forms.
     * @param operationalPeriodStart operational period start date/time.
     * @param operationalPeriodEnd operational period end date/time.
     * @param currentUser preparer or current user identity.
     */
    public IncidentContext(String incidentName, LocalDateTime operationalPeriodStart, LocalDateTime operationalPeriodEnd, String currentUser) {
        this.incidentName = incidentName;
        this.operationalPeriodStart = operationalPeriodStart;
        this.operationalPeriodEnd = operationalPeriodEnd;
        this.currentUser = currentUser;
    }

    /**
     * Returns the incident name.
     *
     * @return incident name.
     */
    public String getIncidentName() {
        return incidentName;
    }

    /**
     * Sets the incident name.
     *
     * @param incidentName incident name.
     */
    public void setIncidentName(String incidentName) {
        this.incidentName = incidentName;
    }

    /**
     * Returns the operational period start.
     *
     * @return start date/time.
     */
    public LocalDateTime getOperationalPeriodStart() {
        return operationalPeriodStart;
    }

    /**
     * Sets the operational period start.
     *
     * @param operationalPeriodStart start date/time.
     */
    public void setOperationalPeriodStart(LocalDateTime operationalPeriodStart) {
        this.operationalPeriodStart = operationalPeriodStart;
    }

    /**
     * Returns the operational period end.
     *
     * @return end date/time.
     */
    public LocalDateTime getOperationalPeriodEnd() {
        return operationalPeriodEnd;
    }

    /**
     * Sets the operational period end.
     *
     * @param operationalPeriodEnd end date/time.
     */
    public void setOperationalPeriodEnd(LocalDateTime operationalPeriodEnd) {
        this.operationalPeriodEnd = operationalPeriodEnd;
    }

    /**
     * Returns the current user or preparer identity.
     *
     * @return current user.
     */
    public String getCurrentUser() {
        return currentUser;
    }

    /**
     * Sets the current user or preparer identity.
     *
     * @param currentUser current user.
     */
    public void setCurrentUser(String currentUser) {
        this.currentUser = currentUser;
    }
}
