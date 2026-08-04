package org.sarmanagement.icsforms.model;

import java.util.ArrayList;
import java.util.List;

public class AppData {
    private IncidentContext incidentContext;
    private Ics202Form form202;
    private Ics204Form form204;
    private List<SarTaskAssignment> sarTaskAssignments = new ArrayList<>();

    public AppData() {
    }

    public AppData(IncidentContext incidentContext, Ics202Form form202, Ics204Form form204, List<SarTaskAssignment> sarTaskAssignments) {
        this.incidentContext = incidentContext;
        this.form202 = form202;
        this.form204 = form204;
        if (sarTaskAssignments != null) {
            this.sarTaskAssignments = sarTaskAssignments;
        }
    }

    public IncidentContext getIncidentContext() { return incidentContext; }
    public void setIncidentContext(IncidentContext incidentContext) { this.incidentContext = incidentContext; }
    public Ics202Form getForm202() { return form202; }
    public void setForm202(Ics202Form form202) { this.form202 = form202; }
    public Ics204Form getForm204() { return form204; }
    public void setForm204(Ics204Form form204) { this.form204 = form204; }
    public List<SarTaskAssignment> getSarTaskAssignments() { return sarTaskAssignments; }
    public void setSarTaskAssignments(List<SarTaskAssignment> sarTaskAssignments) { this.sarTaskAssignments = sarTaskAssignments; }
}
