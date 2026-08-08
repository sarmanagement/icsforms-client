package org.sarmanagement.icsforms.model;

import java.time.LocalDateTime;

/**
 * Structured clue entry captured from SAR task debriefing and shown in the shared clue log.
 */
public class ClueLogEntry {
    private String assignmentId = "";
    private String detectingTask = "";
    private LocalDateTime dateTimeCollected;
    private String location = "";
    private String description = "";
    private String followUp = "";

    public String getAssignmentId() {
        return assignmentId;
    }

    public void setAssignmentId(String assignmentId) {
        this.assignmentId = assignmentId == null ? "" : assignmentId;
    }

    public String getDetectingTask() {
        return detectingTask;
    }

    public void setDetectingTask(String detectingTask) {
        this.detectingTask = detectingTask == null ? "" : detectingTask;
    }

    public LocalDateTime getDateTimeCollected() {
        return dateTimeCollected;
    }

    public void setDateTimeCollected(LocalDateTime dateTimeCollected) {
        this.dateTimeCollected = dateTimeCollected;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location == null ? "" : location;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description == null ? "" : description;
    }

    public String getFollowUp() {
        return followUp;
    }

    public void setFollowUp(String followUp) {
        this.followUp = followUp == null ? "" : followUp;
    }
}
