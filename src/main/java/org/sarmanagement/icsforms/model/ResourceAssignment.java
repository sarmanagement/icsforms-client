package org.sarmanagement.icsforms.model;

import java.util.UUID;

/**
 * Resource row on ICS 204 with linkage to a SAR task scaffold record.
 */
public class ResourceAssignment {
    private String assignmentId = UUID.randomUUID().toString();
    private String assignmentTeamNumber = "";
    private String resourceIdentifier = "";
    private String leaderRole = "Leader";
    private String leader = "";
    private int numberOfPersons;
    private String contact = "";
    private String reportingLocation = "";
    private String specialEquipment = "";
    private String supplies = "";
    private String remarks = "";
    private String notes = "";
    private String assignment = "";

    /**
     * Returns the stable assignment linkage identifier.
     *
     * @return assignment linkage identifier.
     */
    public String getAssignmentId() {
        return assignmentId;
    }

    /**
     * Sets the stable assignment linkage identifier.
     *
     * @param assignmentId assignment linkage identifier.
     */
    public void setAssignmentId(String assignmentId) {
        this.assignmentId = assignmentId;
    }

    /**
     * Returns the task assignment or team number shown on linked SAR forms.
     *
     * @return assignment or team number.
     */
    public String getAssignmentTeamNumber() {
        return assignmentTeamNumber;
    }

    /**
     * Sets the task assignment or team number shown on linked SAR forms.
     *
     * @param assignmentTeamNumber assignment or team number.
     */
    public void setAssignmentTeamNumber(String assignmentTeamNumber) {
        this.assignmentTeamNumber = assignmentTeamNumber == null ? "" : assignmentTeamNumber;
    }

    /**
     * Returns the resource identifier.
     *
     * @return resource identifier.
     */
    public String getResourceIdentifier() {
        return resourceIdentifier;
    }

    /**
     * Sets the resource identifier.
     *
     * @param resourceIdentifier resource identifier.
     */
    public void setResourceIdentifier(String resourceIdentifier) {
        this.resourceIdentifier = resourceIdentifier;
    }

    /**
     * Returns the leader role label used by linked SAR task resources.
     *
     * @return leader role label.
     */
    public String getLeaderRole() {
        return leaderRole;
    }

    /**
     * Sets the leader role label used by linked SAR task resources.
     *
     * @param leaderRole leader role label.
     */
    public void setLeaderRole(String leaderRole) {
        this.leaderRole = leaderRole == null || leaderRole.isBlank() ? "Leader" : leaderRole;
    }

    /**
     * Returns the leader name.
     *
     * @return leader name.
     */
    public String getLeader() {
        return leader;
    }

    /**
     * Sets the leader name.
     *
     * @param leader leader name.
     */
    public void setLeader(String leader) {
        this.leader = leader;
    }

    /**
     * Returns the number of persons.
     *
     * @return number of persons.
     */
    public int getNumberOfPersons() {
        return numberOfPersons;
    }

    /**
     * Sets the number of persons.
     *
     * @param numberOfPersons number of persons.
     */
    public void setNumberOfPersons(int numberOfPersons) {
        this.numberOfPersons = numberOfPersons;
    }

    /**
     * Returns the primary contact.
     *
     * @return contact.
     */
    public String getContact() {
        return contact;
    }

    /**
     * Sets the primary contact.
     *
     * @param contact primary contact.
     */
    public void setContact(String contact) {
        this.contact = contact;
    }

    /**
     * Returns the reporting location.
     *
     * @return reporting location.
     */
    public String getReportingLocation() {
        return reportingLocation;
    }

    /**
     * Sets the reporting location.
     *
     * @param reportingLocation reporting location.
     */
    public void setReportingLocation(String reportingLocation) {
        this.reportingLocation = reportingLocation;
    }

    /**
     * Returns special equipment notes.
     *
     * @return special equipment notes.
     */
    public String getSpecialEquipment() {
        return specialEquipment;
    }

    /**
     * Sets special equipment notes.
     *
     * @param specialEquipment special equipment notes.
     */
    public void setSpecialEquipment(String specialEquipment) {
        this.specialEquipment = specialEquipment;
    }

    /**
     * Returns supply notes.
     *
     * @return supply notes.
     */
    public String getSupplies() {
        return supplies;
    }

    /**
     * Sets supply notes.
     *
     * @param supplies supply notes.
     */
    public void setSupplies(String supplies) {
        this.supplies = supplies;
    }

    /**
     * Returns remarks.
     *
     * @return remarks.
     */
    public String getRemarks() {
        return remarks;
    }

    /**
     * Sets remarks.
     *
     * @param remarks remarks.
     */
    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    /**
     * Returns additional notes.
     *
     * @return additional notes.
     */
    public String getNotes() {
        return notes;
    }

    /**
     * Sets additional notes.
     *
     * @param notes additional notes.
     */
    public void setNotes(String notes) {
        this.notes = notes;
    }

    /**
     * Returns resource-specific assignment text.
     *
     * @return assignment text.
     */
    public String getAssignment() {
        return assignment;
    }

    /**
     * Sets resource-specific assignment text.
     *
     * @param assignment assignment text.
     */
    public void setAssignment(String assignment) {
        this.assignment = assignment;
    }
}
