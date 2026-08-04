package org.sarmanagement.icsforms.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Search and rescue task scaffold linked from an ICS 204 assignment entry.
 */
public class SarTaskAssignment {
    private String assignmentId = "";
    private String incidentName = "";
    private String resourceIdentifier = "";
    private String leader = "";
    private String assignment = "";
    private String contact = "";
    private String branch = "";
    private String division = "";
    private String group = "";
    private String stagingArea = "";
    private String specialInstructions = "";
    private List<CommunicationEntry> communications = new ArrayList<>();
    private String debriefNotes = "";

    /**
     * Creates a SAR scaffold from a linked ICS 204 resource assignment.
     *
     * @param resourceAssignment ICS 204 resource assignment.
     * @param incidentContext shared incident context.
     * @param form204 containing ICS 204 form.
     * @return SAR task scaffold record.
     */
    public static SarTaskAssignment fromResourceAssignment(ResourceAssignment resourceAssignment, IncidentContext incidentContext, Ics204Form form204) {
        SarTaskAssignment task = new SarTaskAssignment();
        if (resourceAssignment != null) {
            task.setAssignmentId(resourceAssignment.getAssignmentId());
            task.setResourceIdentifier(resourceAssignment.getResourceIdentifier());
            task.setLeader(resourceAssignment.getLeader());
            task.setContact(resourceAssignment.getContact());
            task.setAssignment(resourceAssignment.getAssignment() == null || resourceAssignment.getAssignment().isBlank()
                    ? form204.getSharedWorkAssignment()
                    : resourceAssignment.getAssignment());
        }
        if (incidentContext != null) {
            task.setIncidentName(incidentContext.getIncidentName());
        }
        if (form204 != null) {
            task.setBranch(form204.getBranch());
            task.setDivision(form204.getDivision());
            task.setGroup(form204.getGroup());
            task.setStagingArea(form204.getStagingArea());
            task.setSpecialInstructions(form204.getSpecialInstructions());
            List<CommunicationEntry> copied = new ArrayList<>();
            for (CommunicationEntry entry : form204.getCommunications()) {
                CommunicationEntry clone = new CommunicationEntry();
                clone.setName(entry.getName());
                clone.setFunction(entry.getFunction());
                clone.setPrimaryContact(entry.getPrimaryContact());
                copied.add(clone);
            }
            task.setCommunications(copied);
        }
        return task;
    }

    /** @return linked assignment identifier. */
    public String getAssignmentId() { return assignmentId; }
    /** @param assignmentId linked assignment identifier. */
    public void setAssignmentId(String assignmentId) { this.assignmentId = assignmentId; }
    /** @return incident name. */
    public String getIncidentName() { return incidentName; }
    /** @param incidentName incident name. */
    public void setIncidentName(String incidentName) { this.incidentName = incidentName; }
    /** @return resource identifier. */
    public String getResourceIdentifier() { return resourceIdentifier; }
    /** @param resourceIdentifier resource identifier. */
    public void setResourceIdentifier(String resourceIdentifier) { this.resourceIdentifier = resourceIdentifier; }
    /** @return leader. */
    public String getLeader() { return leader; }
    /** @param leader leader. */
    public void setLeader(String leader) { this.leader = leader; }
    /** @return assignment text. */
    public String getAssignment() { return assignment; }
    /** @param assignment assignment text. */
    public void setAssignment(String assignment) { this.assignment = assignment; }
    /** @return contact. */
    public String getContact() { return contact; }
    /** @param contact contact. */
    public void setContact(String contact) { this.contact = contact; }
    /** @return branch. */
    public String getBranch() { return branch; }
    /** @param branch branch. */
    public void setBranch(String branch) { this.branch = branch; }
    /** @return division. */
    public String getDivision() { return division; }
    /** @param division division. */
    public void setDivision(String division) { this.division = division; }
    /** @return group. */
    public String getGroup() { return group; }
    /** @param group group. */
    public void setGroup(String group) { this.group = group; }
    /** @return staging area. */
    public String getStagingArea() { return stagingArea; }
    /** @param stagingArea staging area. */
    public void setStagingArea(String stagingArea) { this.stagingArea = stagingArea; }
    /** @return special instructions. */
    public String getSpecialInstructions() { return specialInstructions; }
    /** @param specialInstructions special instructions. */
    public void setSpecialInstructions(String specialInstructions) { this.specialInstructions = specialInstructions; }
    /** @return communications context. */
    public List<CommunicationEntry> getCommunications() { return communications; }
    /** @param communications communications context. */
    public void setCommunications(List<CommunicationEntry> communications) { this.communications = communications == null ? new ArrayList<>() : communications; }
    /** @return debrief notes. */
    public String getDebriefNotes() { return debriefNotes; }
    /** @param debriefNotes debrief notes. */
    public void setDebriefNotes(String debriefNotes) { this.debriefNotes = debriefNotes; }
}
