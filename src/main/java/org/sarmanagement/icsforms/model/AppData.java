package org.sarmanagement.icsforms.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Canonical root document for the local first-cut ICS workspace.
 * Shared incident context, form content, and linked SAR task scaffolding are persisted together.
 */
public class AppData {
    /** Current persistence schema version for JSON storage. */
    public static final int CURRENT_SCHEMA_VERSION = 1;

    private int schemaVersion = CURRENT_SCHEMA_VERSION;
    private IncidentContext incidentContext = new IncidentContext();
    private OrganizationalChart organizationalChart = new OrganizationalChart();
    private Ics202Form form202 = new Ics202Form();
    private Ics204Form form204 = new Ics204Form();
    private List<SarTaskAssignment> sarTaskAssignments = new ArrayList<>();
    private List<ClueLogEntry> clueLogEntries = new ArrayList<>();

    /**
     * Creates an empty incident document.
     */
    public AppData() {
    }

    /**
     * Creates a populated incident document.
     *
     * @param incidentContext shared incident metadata.
     * @param form202 incident objectives form content.
     * @param form204 assignment list form content.
     * @param sarTaskAssignments SAR task scaffold records linked to assignments.
     */
    public AppData(IncidentContext incidentContext, Ics202Form form202, Ics204Form form204, List<SarTaskAssignment> sarTaskAssignments) {
        this.incidentContext = incidentContext == null ? new IncidentContext() : incidentContext;
        this.organizationalChart = new OrganizationalChart();
        this.form202 = form202 == null ? new Ics202Form() : form202;
        this.form204 = form204 == null ? new Ics204Form() : form204;
        if (sarTaskAssignments != null) {
            this.sarTaskAssignments = sarTaskAssignments;
        }
    }

    /**
     * Returns the JSON schema version.
     *
     * @return persisted schema version.
     */
    public int getSchemaVersion() {
        return schemaVersion;
    }

    /**
     * Sets the JSON schema version.
     *
     * @param schemaVersion schema version value.
     */
    public void setSchemaVersion(int schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    /**
     * Returns the shared incident metadata.
     *
     * @return shared incident context.
     */
    public IncidentContext getIncidentContext() {
        return incidentContext;
    }

    /**
     * Sets the shared incident metadata.
     *
     * @param incidentContext shared incident context.
     */
    public void setIncidentContext(IncidentContext incidentContext) {
        this.incidentContext = incidentContext == null ? new IncidentContext() : incidentContext;
    }

    /**
     * Returns the organizational chart data shared across forms.
     *
     * @return organizational chart data.
     */
    public OrganizationalChart getOrganizationalChart() {
        return organizationalChart;
    }

    /**
     * Sets the organizational chart data shared across forms.
     *
     * @param organizationalChart organizational chart data.
     */
    public void setOrganizationalChart(OrganizationalChart organizationalChart) {
        this.organizationalChart = organizationalChart == null ? new OrganizationalChart() : organizationalChart;
    }

    /**
     * Returns ICS 202 content.
     *
     * @return incident objectives form.
     */
    public Ics202Form getForm202() {
        return form202;
    }

    /**
     * Sets ICS 202 content.
     *
     * @param form202 incident objectives form.
     */
    public void setForm202(Ics202Form form202) {
        this.form202 = form202 == null ? new Ics202Form() : form202;
    }

    /**
     * Returns ICS 204 content.
     *
     * @return assignment list form.
     */
    public Ics204Form getForm204() {
        return form204;
    }

    /**
     * Sets ICS 204 content.
     *
     * @param form204 assignment list form.
     */
    public void setForm204(Ics204Form form204) {
        this.form204 = form204 == null ? new Ics204Form() : form204;
    }

    /**
     * Returns linked SAR task records.
     *
     * @return SAR task scaffold list.
     */
    public List<SarTaskAssignment> getSarTaskAssignments() {
        return sarTaskAssignments;
    }

    /**
     * Sets linked SAR task records.
     *
     * @param sarTaskAssignments SAR task scaffold list.
     */
    public void setSarTaskAssignments(List<SarTaskAssignment> sarTaskAssignments) {
        this.sarTaskAssignments = sarTaskAssignments == null ? new ArrayList<>() : sarTaskAssignments;
    }

    /**
     * Returns shared clue log entries collected from task debriefings.
     *
     * @return clue log entries.
     */
    public List<ClueLogEntry> getClueLogEntries() {
        return clueLogEntries;
    }

    /**
     * Sets shared clue log entries collected from task debriefings.
     *
     * @param clueLogEntries clue log entries.
     */
    public void setClueLogEntries(List<ClueLogEntry> clueLogEntries) {
        this.clueLogEntries = clueLogEntries == null ? new ArrayList<>() : clueLogEntries;
    }
}
