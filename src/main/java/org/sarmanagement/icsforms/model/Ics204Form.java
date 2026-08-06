package org.sarmanagement.icsforms.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * First-cut Assignment List (ICS 204) content and resource records.
 */
public class Ics204Form {
    public static final String MANAGEMENT_STAGING_AREA = "stagingArea";
    public static final String MANAGEMENT_BRANCH = "branch";
    public static final String MANAGEMENT_DIVISION = "division";
    public static final String MANAGEMENT_GROUP = "group";

    private String managementContext = MANAGEMENT_STAGING_AREA;
    private String branch = "";
    private String division = "";
    private String group = "";
    private String stagingArea = "";
    private String operationsSectionChiefName = "";
    private String operationsSectionChiefContact = "";
    private String branchDirectorName = "";
    private String branchDirectorContact = "";
    private String divisionGroupSupervisorName = "";
    private String divisionGroupSupervisorContact = "";
    private List<ResourceAssignment> resourcesAssigned = new ArrayList<>();
    private String sharedWorkAssignment = "";
    private String specialInstructions = "";
    private List<CommunicationEntry> communications = new ArrayList<>();
    private String preparedByName = "";
    private String preparedByPositionTitle = "";
    private String preparedBySignature = "";
    private LocalDateTime preparedDateTime;
    private String formNumber = "ICS 204";
    private String iapPage = "";

    /**
     * Returns whether branch, division, or group context requires a supervisor.
     *
     * @return {@code true} when a division/group supervisor is required.
     */
    public boolean requiresBranchDivisionGroupSupervisor() {
        return MANAGEMENT_BRANCH.equals(getManagementContext())
                || MANAGEMENT_DIVISION.equals(getManagementContext())
                || MANAGEMENT_GROUP.equals(getManagementContext());
    }

    /**
     * Returns whether the required supervisor data is present when context requires it.
     *
     * @return {@code true} when supervisor requirements are met.
     */
    public boolean hasRequiredSupervisorWhenNeeded() {
        if (!requiresBranchDivisionGroupSupervisor()) {
            return true;
        }
        if (MANAGEMENT_BRANCH.equals(getManagementContext())) {
            return notBlank(branchDirectorName) && notBlank(branchDirectorContact);
        }
        return notBlank(divisionGroupSupervisorName) && notBlank(divisionGroupSupervisorContact);
    }

    /**
     * Returns the selected management context.
     *
     * @return selected management context key.
     */
    public String getManagementContext() {
        if (MANAGEMENT_BRANCH.equals(managementContext)
                || MANAGEMENT_DIVISION.equals(managementContext)
                || MANAGEMENT_GROUP.equals(managementContext)) {
            return managementContext;
        }
        if (MANAGEMENT_STAGING_AREA.equals(managementContext) && notBlank(stagingArea)) {
            return managementContext;
        }
        if (notBlank(branch)) {
            return MANAGEMENT_BRANCH;
        }
        if (notBlank(division)) {
            return MANAGEMENT_DIVISION;
        }
        if (notBlank(group)) {
            return MANAGEMENT_GROUP;
        }
        return MANAGEMENT_STAGING_AREA;
    }

    /**
     * Sets the selected management context.
     *
     * @param managementContext selected management context key.
     */
    public void setManagementContext(String managementContext) {
        this.managementContext = managementContext;
    }

    /**
     * Returns the selected context heading text for UI and PDF output.
     *
     * @return selected context heading.
     */
    @JsonIgnore
    public String getSelectedContextHeading() {
        return switch (getManagementContext()) {
            case MANAGEMENT_BRANCH -> "Branch";
            case MANAGEMENT_DIVISION -> "Division";
            case MANAGEMENT_GROUP -> "Group";
            default -> "Staging Area";
        };
    }

    /**
     * Returns the selected context value.
     *
     * @return selected context value.
     */
    @JsonIgnore
    public String getSelectedContextValue() {
        return switch (getManagementContext()) {
            case MANAGEMENT_BRANCH -> branch;
            case MANAGEMENT_DIVISION -> division;
            case MANAGEMENT_GROUP -> group;
            default -> stagingArea;
        };
    }

    /**
     * Returns the secondary management role label required by the selected context.
     *
     * @return secondary management role label or empty when none is needed.
     */
    @JsonIgnore
    public String getSecondaryManagementRoleLabel() {
        return switch (getManagementContext()) {
            case MANAGEMENT_BRANCH -> "Branch Director";
            case MANAGEMENT_DIVISION -> "Division Supervisor";
            case MANAGEMENT_GROUP -> "Group Supervisor";
            default -> "";
        };
    }

    /**
     * Returns the selected secondary management name.
     *
     * @return secondary management name.
     */
    @JsonIgnore
    public String getSecondaryManagementName() {
        return MANAGEMENT_BRANCH.equals(getManagementContext()) ? branchDirectorName : divisionGroupSupervisorName;
    }

    /**
     * Returns the selected secondary management contact.
     *
     * @return secondary management contact.
     */
    @JsonIgnore
    public String getSecondaryManagementContact() {
        return MANAGEMENT_BRANCH.equals(getManagementContext()) ? branchDirectorContact : divisionGroupSupervisorContact;
    }

    /**
     * Returns the branch context.
     *
     * @return branch name.
     */
    public String getBranch() {
        return branch;
    }

    /**
     * Sets the branch context.
     *
     * @param branch branch name.
     */
    public void setBranch(String branch) {
        this.branch = branch;
    }

    /**
     * Returns the division context.
     *
     * @return division name.
     */
    public String getDivision() {
        return division;
    }

    /**
     * Sets the division context.
     *
     * @param division division name.
     */
    public void setDivision(String division) {
        this.division = division;
    }

    /**
     * Returns the group context.
     *
     * @return group name.
     */
    public String getGroup() {
        return group;
    }

    /**
     * Sets the group context.
     *
     * @param group group name.
     */
    public void setGroup(String group) {
        this.group = group;
    }

    /**
     * Returns the staging area.
     *
     * @return staging area.
     */
    public String getStagingArea() {
        return stagingArea;
    }

    /**
     * Sets the staging area.
     *
     * @param stagingArea staging area.
     */
    public void setStagingArea(String stagingArea) {
        this.stagingArea = stagingArea;
    }

    /**
     * Returns the operations section chief name.
     *
     * @return chief name.
     */
    public String getOperationsSectionChiefName() {
        return operationsSectionChiefName;
    }

    /**
     * Sets the operations section chief name.
     *
     * @param operationsSectionChiefName chief name.
     */
    public void setOperationsSectionChiefName(String operationsSectionChiefName) {
        this.operationsSectionChiefName = operationsSectionChiefName;
    }

    /**
     * Returns the operations section chief contact.
     *
     * @return chief contact.
     */
    public String getOperationsSectionChiefContact() {
        return operationsSectionChiefContact;
    }

    /**
     * Sets the operations section chief contact.
     *
     * @param operationsSectionChiefContact chief contact.
     */
    public void setOperationsSectionChiefContact(String operationsSectionChiefContact) {
        this.operationsSectionChiefContact = operationsSectionChiefContact;
    }

    /**
     * Returns the branch director name.
     *
     * @return branch director name.
     */
    public String getBranchDirectorName() {
        return branchDirectorName;
    }

    /**
     * Sets the branch director name.
     *
     * @param branchDirectorName branch director name.
     */
    public void setBranchDirectorName(String branchDirectorName) {
        this.branchDirectorName = branchDirectorName;
    }

    /**
     * Returns the branch director contact.
     *
     * @return branch director contact.
     */
    public String getBranchDirectorContact() {
        return branchDirectorContact;
    }

    /**
     * Sets the branch director contact.
     *
     * @param branchDirectorContact branch director contact.
     */
    public void setBranchDirectorContact(String branchDirectorContact) {
        this.branchDirectorContact = branchDirectorContact;
    }

    /**
     * Returns the division/group supervisor name.
     *
     * @return supervisor name.
     */
    public String getDivisionGroupSupervisorName() {
        return divisionGroupSupervisorName;
    }

    /**
     * Sets the division/group supervisor name.
     *
     * @param divisionGroupSupervisorName supervisor name.
     */
    public void setDivisionGroupSupervisorName(String divisionGroupSupervisorName) {
        this.divisionGroupSupervisorName = divisionGroupSupervisorName;
    }

    /**
     * Returns the division/group supervisor contact.
     *
     * @return supervisor contact.
     */
    public String getDivisionGroupSupervisorContact() {
        return divisionGroupSupervisorContact;
    }

    /**
     * Sets the division/group supervisor contact.
     *
     * @param divisionGroupSupervisorContact supervisor contact.
     */
    public void setDivisionGroupSupervisorContact(String divisionGroupSupervisorContact) {
        this.divisionGroupSupervisorContact = divisionGroupSupervisorContact;
    }

    /**
     * Returns assigned resources.
     *
     * @return assigned resources.
     */
    public List<ResourceAssignment> getResourcesAssigned() {
        return resourcesAssigned;
    }

    /**
     * Sets assigned resources.
     *
     * @param resourcesAssigned resource list.
     */
    public void setResourcesAssigned(List<ResourceAssignment> resourcesAssigned) {
        this.resourcesAssigned = resourcesAssigned == null ? new ArrayList<>() : resourcesAssigned;
    }

    /**
     * Returns shared work assignment text.
     *
     * @return shared assignment text.
     */
    public String getSharedWorkAssignment() {
        return sharedWorkAssignment;
    }

    /**
     * Sets shared work assignment text.
     *
     * @param sharedWorkAssignment shared assignment text.
     */
    public void setSharedWorkAssignment(String sharedWorkAssignment) {
        this.sharedWorkAssignment = sharedWorkAssignment;
    }

    /**
     * Returns special instructions.
     *
     * @return special instructions.
     */
    public String getSpecialInstructions() {
        return specialInstructions;
    }

    /**
     * Sets special instructions.
     *
     * @param specialInstructions special instructions.
     */
    public void setSpecialInstructions(String specialInstructions) {
        this.specialInstructions = specialInstructions;
    }

    /**
     * Returns communications rows.
     *
     * @return communications table rows.
     */
    public List<CommunicationEntry> getCommunications() {
        return communications;
    }

    /**
     * Sets communications rows.
     *
     * @param communications communications table rows.
     */
    public void setCommunications(List<CommunicationEntry> communications) {
        this.communications = communications == null ? new ArrayList<>() : communications;
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
     * @param preparedBySignature preparer signature text.
     */
    public void setPreparedBySignature(String preparedBySignature) {
        this.preparedBySignature = preparedBySignature;
    }

    /**
     * Returns the preparer date/time.
     *
     * @return preparer date/time.
     */
    public LocalDateTime getPreparedDateTime() {
        return preparedDateTime;
    }

    /**
     * Sets the preparer date/time.
     *
     * @param preparedDateTime preparer date/time.
     */
    public void setPreparedDateTime(LocalDateTime preparedDateTime) {
        this.preparedDateTime = preparedDateTime;
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
     * @return IAP page.
     */
    public String getIapPage() {
        return iapPage;
    }

    /**
     * Sets the IAP page indicator.
     *
     * @param iapPage IAP page.
     */
    public void setIapPage(String iapPage) {
        this.iapPage = iapPage;
    }

    /**
     * Checks whether a text field contains meaningful content.
     *
     * @param value value to inspect.
     * @return {@code true} when the value is not blank.
     */
    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
