package org.sarmanagement.icsforms.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Canonical root document for the local first-cut ICS workspace.
 * Shared incident context, form content, and linked SAR task scaffolding are persisted together.
 */
public class AppData {
    /** Current persistence schema version for JSON storage. */
    public static final int CURRENT_SCHEMA_VERSION = 3;

    private int schemaVersion = CURRENT_SCHEMA_VERSION;
    private IncidentMode incidentMode = IncidentMode.SAR;
    private IncidentContext incidentContext = new IncidentContext();
    private OrganizationalChart organizationalChart = new OrganizationalChart();
    private Ics202Form form202 = new Ics202Form();
    private Ics204Form form204 = new Ics204Form();
    private List<Ics204Form> additionalForms204 = new ArrayList<>();
    private List<Ics214Form> activityLogs = new ArrayList<>();
    private List<ActivityEventType> activityEventTypes = new ArrayList<>();
    private List<SarTaskAssignment> sarTaskAssignments = new ArrayList<>();
    private List<ClueLogEntry> clueLogEntries = new ArrayList<>();
    private List<TCard> tCards = new ArrayList<>();

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
     * Returns additional ICS 204 forms beyond the primary assignment list.
     *
     * @return additional ICS 204 forms.
     */
    public List<Ics204Form> getAdditionalForms204() {
        return additionalForms204;
    }

    /**
     * Sets additional ICS 204 forms beyond the primary assignment list.
     *
     * @param additionalForms204 additional ICS 204 forms.
     */
    public void setAdditionalForms204(List<Ics204Form> additionalForms204) {
        this.additionalForms204 = additionalForms204 == null ? new ArrayList<>() : additionalForms204;
    }

    /**
     * Returns ICS 214 activity logs.
     *
     * @return ICS 214 activity logs.
     */
    public List<Ics214Form> getActivityLogs() {
        return activityLogs;
    }

    /**
     * Sets ICS 214 activity logs.
     *
     * @param activityLogs ICS 214 activity logs.
     */
    public void setActivityLogs(List<Ics214Form> activityLogs) {
        this.activityLogs = activityLogs == null ? new ArrayList<>() : activityLogs;
    }

    /**
     * Returns the configured activity event types used across all ICS 214 logs.
     *
     * <p>When this list is empty the application populates it with
     * {@link ActivityEventType#defaultTypes()} on startup.  Operators may add
     * custom types to extend the built-in set.</p>
     *
     * @return configured activity event types.
     */
    public List<ActivityEventType> getActivityEventTypes() {
        return activityEventTypes;
    }

    /**
     * Sets the configured activity event types.
     *
     * @param activityEventTypes configured activity event types.
     */
    public void setActivityEventTypes(List<ActivityEventType> activityEventTypes) {
        this.activityEventTypes = activityEventTypes == null ? new ArrayList<>() : activityEventTypes;
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

    /**
     * Returns the incident operational mode (SAR or Generic).
     *
     * <p>Defaults to {@link IncidentMode#SAR} for backward compatibility with existing files
     * that do not carry an explicit mode field.</p>
     *
     * @return incident mode.
     */
    public IncidentMode getIncidentMode() {
        return incidentMode == null ? IncidentMode.SAR : incidentMode;
    }

    /**
     * Sets the incident operational mode.
     *
     * @param incidentMode incident mode; {@code null} is treated as {@link IncidentMode#SAR}.
     */
    public void setIncidentMode(IncidentMode incidentMode) {
        this.incidentMode = incidentMode == null ? IncidentMode.SAR : incidentMode;
    }

    /**
     * Returns the list of T-Card (ICS 219) resource status records for this incident.
     *
     * @return T-card list.
     */
    public List<TCard> getTCards() {
        return tCards;
    }

    /**
     * Sets the list of T-Card records.
     *
     * @param tCards T-card list.
     */
    public void setTCards(List<TCard> tCards) {
        this.tCards = tCards == null ? new ArrayList<>() : tCards;
    }
}
