package org.sarmanagement.icsforms.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Ics202Form {
    private List<String> objectives = new ArrayList<>();
    private String commandEmphasis;
    private String situationalAwareness;
    private boolean siteSafetyPlanRequired;
    private List<String> incidentActionPlanAttachments = new ArrayList<>();
    private String preparedByName;
    private String preparedByPositionTitle;
    private String preparedBySignature;
    private String approvedByIncidentCommanderName;
    private String approvedBySignature;
    private LocalDateTime approvedDateTime;
    private String formNumber = "ICS 202";
    private String iapPage;

    public List<String> getObjectives() { return objectives; }
    public void setObjectives(List<String> objectives) { this.objectives = objectives; }
    public String getCommandEmphasis() { return commandEmphasis; }
    public void setCommandEmphasis(String commandEmphasis) { this.commandEmphasis = commandEmphasis; }
    public String getSituationalAwareness() { return situationalAwareness; }
    public void setSituationalAwareness(String situationalAwareness) { this.situationalAwareness = situationalAwareness; }
    public boolean isSiteSafetyPlanRequired() { return siteSafetyPlanRequired; }
    public void setSiteSafetyPlanRequired(boolean siteSafetyPlanRequired) { this.siteSafetyPlanRequired = siteSafetyPlanRequired; }
    public List<String> getIncidentActionPlanAttachments() { return incidentActionPlanAttachments; }
    public void setIncidentActionPlanAttachments(List<String> incidentActionPlanAttachments) { this.incidentActionPlanAttachments = incidentActionPlanAttachments; }
    public String getPreparedByName() { return preparedByName; }
    public void setPreparedByName(String preparedByName) { this.preparedByName = preparedByName; }
    public String getPreparedByPositionTitle() { return preparedByPositionTitle; }
    public void setPreparedByPositionTitle(String preparedByPositionTitle) { this.preparedByPositionTitle = preparedByPositionTitle; }
    public String getPreparedBySignature() { return preparedBySignature; }
    public void setPreparedBySignature(String preparedBySignature) { this.preparedBySignature = preparedBySignature; }
    public String getApprovedByIncidentCommanderName() { return approvedByIncidentCommanderName; }
    public void setApprovedByIncidentCommanderName(String approvedByIncidentCommanderName) { this.approvedByIncidentCommanderName = approvedByIncidentCommanderName; }
    public String getApprovedBySignature() { return approvedBySignature; }
    public void setApprovedBySignature(String approvedBySignature) { this.approvedBySignature = approvedBySignature; }
    public LocalDateTime getApprovedDateTime() { return approvedDateTime; }
    public void setApprovedDateTime(LocalDateTime approvedDateTime) { this.approvedDateTime = approvedDateTime; }
    public String getFormNumber() { return formNumber; }
    public void setFormNumber(String formNumber) { this.formNumber = formNumber; }
    public String getIapPage() { return iapPage; }
    public void setIapPage(String iapPage) { this.iapPage = iapPage; }
}
