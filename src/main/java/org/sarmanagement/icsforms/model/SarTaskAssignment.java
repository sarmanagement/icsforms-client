package org.sarmanagement.icsforms.model;

public class SarTaskAssignment {
    private String incidentName;
    private String resourceIdentifier;
    private String assignment;
    private String contact;

    public static SarTaskAssignment fromResourceAssignment(ResourceAssignment resourceAssignment, String incidentName) {
        SarTaskAssignment task = new SarTaskAssignment();
        task.setIncidentName(incidentName);
        if (resourceAssignment != null) {
            task.setResourceIdentifier(resourceAssignment.getResourceIdentifier());
            task.setContact(resourceAssignment.getContact());
        }
        return task;
    }

    public String getIncidentName() { return incidentName; }
    public void setIncidentName(String incidentName) { this.incidentName = incidentName; }
    public String getResourceIdentifier() { return resourceIdentifier; }
    public void setResourceIdentifier(String resourceIdentifier) { this.resourceIdentifier = resourceIdentifier; }
    public String getAssignment() { return assignment; }
    public void setAssignment(String assignment) { this.assignment = assignment; }
    public String getContact() { return contact; }
    public void setContact(String contact) { this.contact = contact; }
}
