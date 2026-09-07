package org.sarmanagement.icsforms.model;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.util.ArrayList;
import java.util.List;

/**
 * Search and rescue task scaffold linked from an ICS 204 assignment entry.
 */
public class SarTaskAssignment {
    private String assignmentId = "";
    private String assignmentTeamNumber = "";
    private String resourceType = "";
    private String taskType = "";
    private String incidentName = "";
    private String resourceIdentifier = "";
    private String leaderRole = "Leader";
    private String leader = "";
    private String assignment = "";
    private String contact = "";
    private String branch = "";
    private String division = "";
    private String group = "";
    private String stagingArea = "";
    private String taskMap = "";
    private String operationsSectionChiefName = "";
    private String operationsSectionChiefContact = "";
    private String secondaryManagementRoleLabel = "";
    private String secondaryManagementName = "";
    private String secondaryManagementContact = "";
    private List<SarTaskResource> resourcesAssigned = new ArrayList<>();
    private String transportationInstructions = "";
    @JsonAlias("specialInstructions")
    private String specialEquipment = "";
    private List<CommunicationEntry> communications = new ArrayList<>();
    private String preparedByName = "";
    private String preparedByPositionTitle = "";
    private java.time.LocalDateTime preparedDateTime;
    private String debriefingSupervisor = "";
    private java.time.LocalDateTime assignmentStart;
    private java.time.LocalDateTime assignmentEnd;
    private String vehicleMiles = "";
    private String debriefNotes = "";
    private String reportedPod = "";
    private String areasNotCovered = "";
    private String hazardsObserved = "";
    private String canineSearchType = "";
    private String canineImprint = "";
    private String canineSunAngle = "";
    private String canineDayNight = "";
    private String canineCloudCover = "";
    private String canineWindSpeed = "";
    private List<PodFactorRating> qualitativePodFactors = new ArrayList<>();
    private String debriefPreparedByName = "";
    private String debriefPreparedByPositionTitle = "";
    private java.time.LocalDateTime debriefPreparedDateTime;
    /**
     * Lifecycle status of this task assignment.
     *
     * <p>Values: {@code "planned"} (default), {@code "assigned - enroute to assignment"},
     * {@code "assigned - on task"}, {@code "assigned - returning from assignment"},
     * {@code "returned"} (resources Out of Service or Available depending on incident
     * configuration).</p>
     */
    private String taskLifecycleStatus = "planned";
    /**
     * Whether the debriefing for this task assignment has been formally completed.
     *
     * <p>A debriefing is considered complete when the supervisor has been named and the
     * debrief notes, areas-not-covered, and hazards-observed fields are all non-blank.
     * This flag must be explicitly set by the operator via the debrief editor.</p>
     */
    private boolean debriefingCompleted = false;
    private String iapPage = "";

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
            task.setAssignmentTeamNumber(resourceAssignment.getAssignmentTeamNumber());
            task.setResourceType(resourceAssignment.getResourceType());
            task.setTaskType(resourceAssignment.getTaskType());
            task.setResourceIdentifier(resourceAssignment.getResourceIdentifier());
            task.setLeaderRole(resourceAssignment.getLeaderRole());
            task.setLeader(resourceAssignment.getLeader());
            task.setContact(resourceAssignment.getContact());
            task.setAssignment(resourceAssignment.getAssignment() == null || resourceAssignment.getAssignment().isBlank()
                    ? form204.getSharedWorkAssignment()
                    : resourceAssignment.getAssignment());
            task.setTransportationInstructions(resourceAssignment.getReportingLocation());
            task.setSpecialEquipment(joinNonBlank(resourceAssignment.getSpecialEquipment(), resourceAssignment.getSupplies()));
            task.setResourcesAssigned(defaultResources(resourceAssignment));
        }
        if (incidentContext != null) {
            task.setIncidentName(incidentContext.getIncidentName());
            task.setTaskMap(incidentContext.getTaskMap());
            task.setPreparedByName(incidentContext.getCurrentUser());
            task.setPreparedByPositionTitle(incidentContext.getCurrentUserPositionTitle());
            task.setDebriefPreparedByName(incidentContext.getCurrentUser());
            task.setDebriefPreparedByPositionTitle(incidentContext.getCurrentUserPositionTitle());
        }
        if (form204 != null) {
            task.setBranch(form204.getBranch());
            task.setDivision(form204.getDivision());
            task.setGroup(form204.getGroup());
            task.setStagingArea(form204.getStagingArea());
            task.setOperationsSectionChiefName(form204.getOperationsSectionChiefName());
            task.setOperationsSectionChiefContact(form204.getOperationsSectionChiefContact());
            task.setSecondaryManagementRoleLabel(form204.getSecondaryManagementRoleLabel());
            task.setSecondaryManagementName(form204.getSecondaryManagementName());
            task.setSecondaryManagementContact(form204.getSecondaryManagementContact());
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

    private static List<SarTaskResource> defaultResources(ResourceAssignment resourceAssignment) {
        List<SarTaskResource> resources = new ArrayList<>();
        if (resourceAssignment == null) {
            return resources;
        }
        if (!safe(resourceAssignment.getLeader()).isBlank()) {
            SarTaskResource leaderResource = new SarTaskResource();
            leaderResource.setFunction(resourceAssignment.getLeaderRole());
            leaderResource.setIcsPosition(resourceAssignment.getLeaderRole());
            leaderResource.setName(resourceAssignment.getLeader());
            resources.add(leaderResource);
        }
        return resources;
    }

    private static String joinNonBlank(String... values) {
        List<String> parts = new ArrayList<>();
        for (String value : values) {
            if (!safe(value).isBlank()) {
                parts.add(value.trim());
            }
        }
        return String.join(" / ", parts);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    /** @return linked assignment identifier. */
    public String getAssignmentId() { return assignmentId; }
    /** @param assignmentId linked assignment identifier. */
    public void setAssignmentId(String assignmentId) { this.assignmentId = assignmentId == null ? "" : assignmentId; }
    /** @return assignment/team number. */
    public String getAssignmentTeamNumber() { return assignmentTeamNumber; }
    /** @param assignmentTeamNumber assignment/team number. */
    public void setAssignmentTeamNumber(String assignmentTeamNumber) { this.assignmentTeamNumber = assignmentTeamNumber == null ? "" : assignmentTeamNumber; }
    /** @return resource type. */
    public String getResourceType() { return resourceType; }
    /** @param resourceType resource type. */
    public void setResourceType(String resourceType) { this.resourceType = SarTaskSupport.normalizedResourceType(resourceType); }
    /** @return task type. */
    public String getTaskType() { return taskType; }
    /** @param taskType task type. */
    public void setTaskType(String taskType) { this.taskType = SarTaskSupport.normalizedTaskType(taskType); }
    /** @return incident name. */
    public String getIncidentName() { return incidentName; }
    /** @param incidentName incident name. */
    public void setIncidentName(String incidentName) { this.incidentName = incidentName == null ? "" : incidentName; }
    /** @return resource identifier. */
    public String getResourceIdentifier() { return resourceIdentifier; }
    /** @param resourceIdentifier resource identifier. */
    public void setResourceIdentifier(String resourceIdentifier) { this.resourceIdentifier = resourceIdentifier == null ? "" : resourceIdentifier; }
    /** @return leader role label. */
    public String getLeaderRole() { return leaderRole; }
    /** @param leaderRole leader role label. */
    public void setLeaderRole(String leaderRole) { this.leaderRole = leaderRole == null || leaderRole.isBlank() ? "Leader" : leaderRole; }
    /** @return leader. */
    public String getLeader() { return leader; }
    /** @param leader leader. */
    public void setLeader(String leader) { this.leader = leader == null ? "" : leader; }
    /** @return assignment text. */
    public String getAssignment() { return assignment; }
    /** @param assignment assignment text. */
    public void setAssignment(String assignment) { this.assignment = assignment == null ? "" : assignment; }
    /** @return contact. */
    public String getContact() { return contact; }
    /** @param contact contact. */
    public void setContact(String contact) { this.contact = contact == null ? "" : contact; }
    /** @return branch. */
    public String getBranch() { return branch; }
    /** @param branch branch. */
    public void setBranch(String branch) { this.branch = branch == null ? "" : branch; }
    /** @return division. */
    public String getDivision() { return division; }
    /** @param division division. */
    public void setDivision(String division) { this.division = division == null ? "" : division; }
    /** @return group. */
    public String getGroup() { return group; }
    /** @param group group. */
    public void setGroup(String group) { this.group = group == null ? "" : group; }
    /** @return staging area. */
    public String getStagingArea() { return stagingArea; }
    /** @param stagingArea staging area. */
    public void setStagingArea(String stagingArea) { this.stagingArea = stagingArea == null ? "" : stagingArea; }
    /** @return shared task map identifier. */
    public String getTaskMap() { return taskMap; }
    /** @param taskMap shared task map identifier. */
    public void setTaskMap(String taskMap) { this.taskMap = taskMap == null ? "" : taskMap; }
    /** @return operations section chief name. */
    public String getOperationsSectionChiefName() { return operationsSectionChiefName; }
    /** @param operationsSectionChiefName operations section chief name. */
    public void setOperationsSectionChiefName(String operationsSectionChiefName) { this.operationsSectionChiefName = operationsSectionChiefName == null ? "" : operationsSectionChiefName; }
    /** @return operations section chief contact. */
    public String getOperationsSectionChiefContact() { return operationsSectionChiefContact; }
    /** @param operationsSectionChiefContact operations section chief contact. */
    public void setOperationsSectionChiefContact(String operationsSectionChiefContact) { this.operationsSectionChiefContact = operationsSectionChiefContact == null ? "" : operationsSectionChiefContact; }
    /** @return secondary management role label. */
    public String getSecondaryManagementRoleLabel() { return secondaryManagementRoleLabel; }
    /** @param secondaryManagementRoleLabel secondary management role label. */
    public void setSecondaryManagementRoleLabel(String secondaryManagementRoleLabel) { this.secondaryManagementRoleLabel = secondaryManagementRoleLabel == null ? "" : secondaryManagementRoleLabel; }
    /** @return secondary management name. */
    public String getSecondaryManagementName() { return secondaryManagementName; }
    /** @param secondaryManagementName secondary management name. */
    public void setSecondaryManagementName(String secondaryManagementName) { this.secondaryManagementName = secondaryManagementName == null ? "" : secondaryManagementName; }
    /** @return secondary management contact. */
    public String getSecondaryManagementContact() { return secondaryManagementContact; }
    /** @param secondaryManagementContact secondary management contact. */
    public void setSecondaryManagementContact(String secondaryManagementContact) { this.secondaryManagementContact = secondaryManagementContact == null ? "" : secondaryManagementContact; }
    /** @return printable task resources. */
    public List<SarTaskResource> getResourcesAssigned() { return resourcesAssigned; }
    /** @param resourcesAssigned printable task resources. */
    public void setResourcesAssigned(List<SarTaskResource> resourcesAssigned) { this.resourcesAssigned = resourcesAssigned == null ? new ArrayList<>() : resourcesAssigned; }
    /** @return transportation instructions. */
    public String getTransportationInstructions() { return transportationInstructions; }
    /** @param transportationInstructions transportation instructions. */
    public void setTransportationInstructions(String transportationInstructions) { this.transportationInstructions = transportationInstructions == null ? "" : transportationInstructions; }
    /** @return special equipment. */
    public String getSpecialEquipment() { return specialEquipment; }
    /** @param specialEquipment special equipment. */
    public void setSpecialEquipment(String specialEquipment) { this.specialEquipment = specialEquipment == null ? "" : specialEquipment; }
    /** @return communications context. */
    public List<CommunicationEntry> getCommunications() { return communications; }
    /** @param communications communications context. */
    public void setCommunications(List<CommunicationEntry> communications) { this.communications = communications == null ? new ArrayList<>() : communications; }
    /** @return assignment-side preparer name. */
    public String getPreparedByName() { return preparedByName; }
    /** @param preparedByName assignment-side preparer name. */
    public void setPreparedByName(String preparedByName) { this.preparedByName = preparedByName == null ? "" : preparedByName; }
    /** @return assignment-side preparer title. */
    public String getPreparedByPositionTitle() { return preparedByPositionTitle; }
    /** @param preparedByPositionTitle assignment-side preparer title. */
    public void setPreparedByPositionTitle(String preparedByPositionTitle) { this.preparedByPositionTitle = preparedByPositionTitle == null ? "" : preparedByPositionTitle; }
    /** @return assignment-side prepared date/time. */
    public java.time.LocalDateTime getPreparedDateTime() { return preparedDateTime; }
    /** @param preparedDateTime assignment-side prepared date/time. */
    public void setPreparedDateTime(java.time.LocalDateTime preparedDateTime) { this.preparedDateTime = preparedDateTime; }
    /** @return debriefing supervisor. */
    public String getDebriefingSupervisor() { return debriefingSupervisor; }
    /** @param debriefingSupervisor debriefing supervisor. */
    public void setDebriefingSupervisor(String debriefingSupervisor) { this.debriefingSupervisor = debriefingSupervisor == null ? "" : debriefingSupervisor; }
    /** @return assignment start. */
    public java.time.LocalDateTime getAssignmentStart() { return assignmentStart; }
    /** @param assignmentStart assignment start. */
    public void setAssignmentStart(java.time.LocalDateTime assignmentStart) { this.assignmentStart = assignmentStart; }
    /** @return assignment end. */
    public java.time.LocalDateTime getAssignmentEnd() { return assignmentEnd; }
    /** @param assignmentEnd assignment end. */
    public void setAssignmentEnd(java.time.LocalDateTime assignmentEnd) { this.assignmentEnd = assignmentEnd; }
    /** @return vehicle miles. */
    public String getVehicleMiles() { return vehicleMiles; }
    /** @param vehicleMiles vehicle miles. */
    public void setVehicleMiles(String vehicleMiles) { this.vehicleMiles = vehicleMiles == null ? "" : vehicleMiles; }
    /** @return debrief notes. */
    public String getDebriefNotes() { return debriefNotes; }
    /** @param debriefNotes debrief notes. */
    public void setDebriefNotes(String debriefNotes) { this.debriefNotes = debriefNotes == null ? "" : debriefNotes; }
    /** @return reported POD percent. */
    public String getReportedPod() { return reportedPod; }
    /** @param reportedPod reported POD percent. */
    public void setReportedPod(String reportedPod) { this.reportedPod = reportedPod == null ? "" : reportedPod; }
    /** @return areas not covered notes. */
    public String getAreasNotCovered() { return areasNotCovered; }
    /** @param areasNotCovered areas not covered notes. */
    public void setAreasNotCovered(String areasNotCovered) { this.areasNotCovered = areasNotCovered == null ? "" : areasNotCovered; }
    /** @return hazards observed notes. */
    public String getHazardsObserved() { return hazardsObserved; }
    /** @param hazardsObserved hazards observed notes. */
    public void setHazardsObserved(String hazardsObserved) { this.hazardsObserved = hazardsObserved == null ? "" : hazardsObserved; }
    /** @return canine resource subtype. */
    public String getCanineSearchType() { return canineSearchType; }
    /** @param canineSearchType canine resource subtype. */
    public void setCanineSearchType(String canineSearchType) { this.canineSearchType = canineSearchType == null ? "" : canineSearchType; }
    /** @return canine imprint. */
    public String getCanineImprint() { return canineImprint; }
    /** @param canineImprint canine imprint. */
    public void setCanineImprint(String canineImprint) { this.canineImprint = canineImprint == null ? "" : canineImprint; }
    /** @return canine sun angle notes. */
    public String getCanineSunAngle() { return canineSunAngle; }
    /** @param canineSunAngle canine sun angle notes. */
    public void setCanineSunAngle(String canineSunAngle) { this.canineSunAngle = canineSunAngle == null ? "" : canineSunAngle; }
    /** @return canine day/night notes. */
    public String getCanineDayNight() { return canineDayNight; }
    /** @param canineDayNight canine day/night notes. */
    public void setCanineDayNight(String canineDayNight) { this.canineDayNight = canineDayNight == null ? "" : canineDayNight; }
    /** @return canine cloud cover notes. */
    public String getCanineCloudCover() { return canineCloudCover; }
    /** @param canineCloudCover canine cloud cover notes. */
    public void setCanineCloudCover(String canineCloudCover) { this.canineCloudCover = canineCloudCover == null ? "" : canineCloudCover; }
    /** @return canine wind speed notes. */
    public String getCanineWindSpeed() { return canineWindSpeed; }
    /** @param canineWindSpeed canine wind speed notes. */
    public void setCanineWindSpeed(String canineWindSpeed) { this.canineWindSpeed = canineWindSpeed == null ? "" : canineWindSpeed; }
    /** @return qualitative POD factors. */
    public List<PodFactorRating> getQualitativePodFactors() { return qualitativePodFactors; }
    /** @param qualitativePodFactors qualitative POD factors. */
    public void setQualitativePodFactors(List<PodFactorRating> qualitativePodFactors) {
        this.qualitativePodFactors = qualitativePodFactors == null ? new ArrayList<>() : qualitativePodFactors;
    }
    /** @return debrief prepared by name. */
    public String getDebriefPreparedByName() { return debriefPreparedByName; }
    /** @param debriefPreparedByName debrief prepared by name. */
    public void setDebriefPreparedByName(String debriefPreparedByName) { this.debriefPreparedByName = debriefPreparedByName == null ? "" : debriefPreparedByName; }
    /** @return debrief prepared by title. */
    public String getDebriefPreparedByPositionTitle() { return debriefPreparedByPositionTitle; }
    /** @param debriefPreparedByPositionTitle debrief prepared by title. */
    public void setDebriefPreparedByPositionTitle(String debriefPreparedByPositionTitle) { this.debriefPreparedByPositionTitle = debriefPreparedByPositionTitle == null ? "" : debriefPreparedByPositionTitle; }
    /** @return debrief prepared date/time. */
    public java.time.LocalDateTime getDebriefPreparedDateTime() { return debriefPreparedDateTime; }
    /** @param debriefPreparedDateTime debrief prepared date/time. */
    public void setDebriefPreparedDateTime(java.time.LocalDateTime debriefPreparedDateTime) { this.debriefPreparedDateTime = debriefPreparedDateTime; }
    /**
     * Returns the task lifecycle status.
     *
     * @return lifecycle status; never {@code null}.
     */
    public String getTaskLifecycleStatus() { return normalizeLifecycle(taskLifecycleStatus); }
    /**
     * Sets the task lifecycle status.
     *
     * @param taskLifecycleStatus lifecycle status; null treated as "planned".
     */
    public void setTaskLifecycleStatus(String taskLifecycleStatus) {
        this.taskLifecycleStatus = normalizeLifecycle(taskLifecycleStatus);
    }

    private static String normalizeLifecycle(String value) {
        if (value == null || value.isBlank()) {
            return "planned";
        }
        String normalized = value.trim().toLowerCase();
        return switch (normalized) {
            case "planned", "planning" -> "planned";
            case "on task" -> "assigned - on task";
            case "returned" -> "returned";
            case "assigned - enroute to assignment",
                 "assigned - on task",
                 "assigned - returning from assignment" -> normalized;
            default -> normalized;
        };
    }

    /**
     * Returns whether the debriefing has been formally marked as completed.
     *
     * @return {@code true} when the debriefing is complete.
     */
    public boolean isDebriefingCompleted() { return debriefingCompleted; }

    /**
     * Sets the debriefing-completed flag.
     *
     * @param debriefingCompleted {@code true} to mark as complete.
     */
    public void setDebriefingCompleted(boolean debriefingCompleted) {
        this.debriefingCompleted = debriefingCompleted;
    }

    /** @return IAP page number string for this form in the bundle. */
    public String getIapPage() { return iapPage == null ? "" : iapPage; }
    /** @param iapPage IAP page number string. */
    public void setIapPage(String iapPage) { this.iapPage = iapPage == null ? "" : iapPage; }
}
