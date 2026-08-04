package org.sarmanagement.icsforms.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Ics204Form {
    private String branch;
    private String division;
    private String group;
    private String stagingArea;
    private String operationsSectionChiefName;
    private String operationsSectionChiefContact;
    private String branchDirectorName;
    private String branchDirectorContact;
    private String divisionGroupSupervisorName;
    private String divisionGroupSupervisorContact;
    private List<ResourceAssignment> resourcesAssigned = new ArrayList<>();
    private List<String> workAssignments = new ArrayList<>();
    private String specialInstructions;
    private List<CommunicationEntry> communications = new ArrayList<>();
    private String preparedByName;
    private String preparedByPositionTitle;
    private String preparedBySignature;
    private LocalDateTime preparedDateTime;
    private String formNumber = "ICS 204";
    private String iapPage;

    public boolean requiresBranchDivisionGroupSupervisor() {
        return notBlank(branch) || notBlank(division) || notBlank(group);
    }

    public boolean hasRequiredSupervisorWhenNeeded() {
        if (!requiresBranchDivisionGroupSupervisor()) {
            return true;
        }
        return notBlank(divisionGroupSupervisorName);
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    public String getBranch() { return branch; }
    public void setBranch(String branch) { this.branch = branch; }
    public String getDivision() { return division; }
    public void setDivision(String division) { this.division = division; }
    public String getGroup() { return group; }
    public void setGroup(String group) { this.group = group; }
    public String getStagingArea() { return stagingArea; }
    public void setStagingArea(String stagingArea) { this.stagingArea = stagingArea; }
    public String getOperationsSectionChiefName() { return operationsSectionChiefName; }
    public void setOperationsSectionChiefName(String operationsSectionChiefName) { this.operationsSectionChiefName = operationsSectionChiefName; }
    public String getOperationsSectionChiefContact() { return operationsSectionChiefContact; }
    public void setOperationsSectionChiefContact(String operationsSectionChiefContact) { this.operationsSectionChiefContact = operationsSectionChiefContact; }
    public String getBranchDirectorName() { return branchDirectorName; }
    public void setBranchDirectorName(String branchDirectorName) { this.branchDirectorName = branchDirectorName; }
    public String getBranchDirectorContact() { return branchDirectorContact; }
    public void setBranchDirectorContact(String branchDirectorContact) { this.branchDirectorContact = branchDirectorContact; }
    public String getDivisionGroupSupervisorName() { return divisionGroupSupervisorName; }
    public void setDivisionGroupSupervisorName(String divisionGroupSupervisorName) { this.divisionGroupSupervisorName = divisionGroupSupervisorName; }
    public String getDivisionGroupSupervisorContact() { return divisionGroupSupervisorContact; }
    public void setDivisionGroupSupervisorContact(String divisionGroupSupervisorContact) { this.divisionGroupSupervisorContact = divisionGroupSupervisorContact; }
    public List<ResourceAssignment> getResourcesAssigned() { return resourcesAssigned; }
    public void setResourcesAssigned(List<ResourceAssignment> resourcesAssigned) { this.resourcesAssigned = resourcesAssigned; }
    public List<String> getWorkAssignments() { return workAssignments; }
    public void setWorkAssignments(List<String> workAssignments) { this.workAssignments = workAssignments; }
    public String getSpecialInstructions() { return specialInstructions; }
    public void setSpecialInstructions(String specialInstructions) { this.specialInstructions = specialInstructions; }
    public List<CommunicationEntry> getCommunications() { return communications; }
    public void setCommunications(List<CommunicationEntry> communications) { this.communications = communications; }
    public String getPreparedByName() { return preparedByName; }
    public void setPreparedByName(String preparedByName) { this.preparedByName = preparedByName; }
    public String getPreparedByPositionTitle() { return preparedByPositionTitle; }
    public void setPreparedByPositionTitle(String preparedByPositionTitle) { this.preparedByPositionTitle = preparedByPositionTitle; }
    public String getPreparedBySignature() { return preparedBySignature; }
    public void setPreparedBySignature(String preparedBySignature) { this.preparedBySignature = preparedBySignature; }
    public LocalDateTime getPreparedDateTime() { return preparedDateTime; }
    public void setPreparedDateTime(LocalDateTime preparedDateTime) { this.preparedDateTime = preparedDateTime; }
    public String getFormNumber() { return formNumber; }
    public void setFormNumber(String formNumber) { this.formNumber = formNumber; }
    public String getIapPage() { return iapPage; }
    public void setIapPage(String iapPage) { this.iapPage = iapPage; }
}
