package org.sarmanagement.icsforms.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * First-cut Incident Objectives (ICS 202) content captured by the desktop editor.
 */
public class Ics202Form {
    private List<String> objectives = new ArrayList<>();
    private String commandEmphasis = "";
    private String situationalAwareness = "";
    private boolean siteSafetyPlanRequired;
    private List<String> incidentActionPlanAttachments = new ArrayList<>();
    private String preparedByName = "";
    private String preparedByPositionTitle = "";
    private String preparedBySignature = "";
    private String approvedByIncidentCommanderName = "";
    private String approvedBySignature = "";
    private LocalDateTime approvedDateTime;
    private String formNumber = "ICS 202";
    private String iapPage = "";

    /**
     * Returns the incident objectives.
     *
     * @return objectives list.
     */
    public List<String> getObjectives() {
        return objectives;
    }

    /**
     * Sets the incident objectives.
     *
     * @param objectives objectives list.
     */
    public void setObjectives(List<String> objectives) {
        this.objectives = objectives == null ? new ArrayList<>() : objectives;
    }

    /**
     * Returns command emphasis text.
     *
     * @return command emphasis.
     */
    public String getCommandEmphasis() {
        return commandEmphasis;
    }

    /**
     * Sets command emphasis text.
     *
     * @param commandEmphasis command emphasis.
     */
    public void setCommandEmphasis(String commandEmphasis) {
        this.commandEmphasis = commandEmphasis;
    }

    /**
     * Returns situational awareness text.
     *
     * @return situational awareness.
     */
    public String getSituationalAwareness() {
        return situationalAwareness;
    }

    /**
     * Sets situational awareness text.
     *
     * @param situationalAwareness situational awareness.
     */
    public void setSituationalAwareness(String situationalAwareness) {
        this.situationalAwareness = situationalAwareness;
    }

    /**
     * Returns whether a site safety plan is required.
     *
     * @return {@code true} when required.
     */
    public boolean isSiteSafetyPlanRequired() {
        return siteSafetyPlanRequired;
    }

    /**
     * Sets whether a site safety plan is required.
     *
     * @param siteSafetyPlanRequired site safety plan flag.
     */
    public void setSiteSafetyPlanRequired(boolean siteSafetyPlanRequired) {
        this.siteSafetyPlanRequired = siteSafetyPlanRequired;
    }

    /**
     * Returns IAP attachment labels or notes.
     *
     * @return attachment list.
     */
    public List<String> getIncidentActionPlanAttachments() {
        return incidentActionPlanAttachments;
    }

    /**
     * Sets IAP attachment labels or notes.
     *
     * @param incidentActionPlanAttachments attachment list.
     */
    public void setIncidentActionPlanAttachments(List<String> incidentActionPlanAttachments) {
        this.incidentActionPlanAttachments = incidentActionPlanAttachments == null ? new ArrayList<>() : incidentActionPlanAttachments;
    }

    /**
     * Returns the preparer name.
     *
     * @return preparer name.
     */
    public String getPreparedByName() {
        return preparedByName;
    }

    /**
     * Sets the preparer name.
     *
     * @param preparedByName preparer name.
     */
    public void setPreparedByName(String preparedByName) {
        this.preparedByName = preparedByName;
    }

    /**
     * Returns the preparer position/title.
     *
     * @return preparer title.
     */
    public String getPreparedByPositionTitle() {
        return preparedByPositionTitle;
    }

    /**
     * Sets the preparer position/title.
     *
     * @param preparedByPositionTitle preparer title.
     */
    public void setPreparedByPositionTitle(String preparedByPositionTitle) {
        this.preparedByPositionTitle = preparedByPositionTitle;
    }

    /**
     * Returns the preparer signature text.
     *
     * @return preparer signature.
     */
    public String getPreparedBySignature() {
        return preparedBySignature;
    }

    /**
     * Sets the preparer signature text.
     *
     * @param preparedBySignature preparer signature.
     */
    public void setPreparedBySignature(String preparedBySignature) {
        this.preparedBySignature = preparedBySignature;
    }

    /**
     * Returns the incident commander approver name.
     *
     * @return approver name.
     */
    public String getApprovedByIncidentCommanderName() {
        return approvedByIncidentCommanderName;
    }

    /**
     * Sets the incident commander approver name.
     *
     * @param approvedByIncidentCommanderName approver name.
     */
    public void setApprovedByIncidentCommanderName(String approvedByIncidentCommanderName) {
        this.approvedByIncidentCommanderName = approvedByIncidentCommanderName;
    }

    /**
     * Returns the incident commander signature text.
     *
     * @return signature text.
     */
    public String getApprovedBySignature() {
        return approvedBySignature;
    }

    /**
     * Sets the incident commander signature text.
     *
     * @param approvedBySignature signature text.
     */
    public void setApprovedBySignature(String approvedBySignature) {
        this.approvedBySignature = approvedBySignature;
    }

    /**
     * Returns approval date/time.
     *
     * @return approval time.
     */
    public LocalDateTime getApprovedDateTime() {
        return approvedDateTime;
    }

    /**
     * Sets approval date/time.
     *
     * @param approvedDateTime approval time.
     */
    public void setApprovedDateTime(LocalDateTime approvedDateTime) {
        this.approvedDateTime = approvedDateTime;
    }

    /**
     * Returns the form number label.
     *
     * @return form number label.
     */
    public String getFormNumber() {
        return formNumber;
    }

    /**
     * Sets the form number label.
     *
     * @param formNumber form number label.
     */
    public void setFormNumber(String formNumber) {
        this.formNumber = formNumber;
    }

    /**
     * Returns the IAP page indicator.
     *
     * @return IAP page value.
     */
    public String getIapPage() {
        return iapPage;
    }

    /**
     * Sets the IAP page indicator.
     *
     * @param iapPage IAP page value.
     */
    public void setIapPage(String iapPage) {
        this.iapPage = iapPage;
    }
}
