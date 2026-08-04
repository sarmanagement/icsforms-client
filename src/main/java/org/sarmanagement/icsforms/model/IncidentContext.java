package org.sarmanagement.icsforms.model;

import java.time.LocalDateTime;

public class IncidentContext {
    private String incidentName;
    private LocalDateTime operationalPeriodStart;
    private LocalDateTime operationalPeriodEnd;
    private String currentUser;

    public IncidentContext() {
    }

    public IncidentContext(String incidentName, LocalDateTime operationalPeriodStart, LocalDateTime operationalPeriodEnd, String currentUser) {
        this.incidentName = incidentName;
        this.operationalPeriodStart = operationalPeriodStart;
        this.operationalPeriodEnd = operationalPeriodEnd;
        this.currentUser = currentUser;
    }

    public String getIncidentName() { return incidentName; }
    public void setIncidentName(String incidentName) { this.incidentName = incidentName; }
    public LocalDateTime getOperationalPeriodStart() { return operationalPeriodStart; }
    public void setOperationalPeriodStart(LocalDateTime operationalPeriodStart) { this.operationalPeriodStart = operationalPeriodStart; }
    public LocalDateTime getOperationalPeriodEnd() { return operationalPeriodEnd; }
    public void setOperationalPeriodEnd(LocalDateTime operationalPeriodEnd) { this.operationalPeriodEnd = operationalPeriodEnd; }
    public String getCurrentUser() { return currentUser; }
    public void setCurrentUser(String currentUser) { this.currentUser = currentUser; }
}
