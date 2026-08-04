package org.sarmanagement.icsforms.model;

public class ResourceAssignment {
    private String resourceIdentifier;
    private String leader;
    private int numberOfPersons;
    private String contact;
    private String reportingLocation;
    private String specialEquipment;
    private String remarks;

    public String getResourceIdentifier() { return resourceIdentifier; }
    public void setResourceIdentifier(String resourceIdentifier) { this.resourceIdentifier = resourceIdentifier; }
    public String getLeader() { return leader; }
    public void setLeader(String leader) { this.leader = leader; }
    public int getNumberOfPersons() { return numberOfPersons; }
    public void setNumberOfPersons(int numberOfPersons) { this.numberOfPersons = numberOfPersons; }
    public String getContact() { return contact; }
    public void setContact(String contact) { this.contact = contact; }
    public String getReportingLocation() { return reportingLocation; }
    public void setReportingLocation(String reportingLocation) { this.reportingLocation = reportingLocation; }
    public String getSpecialEquipment() { return specialEquipment; }
    public void setSpecialEquipment(String specialEquipment) { this.specialEquipment = specialEquipment; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
}
