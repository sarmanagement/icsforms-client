package org.sarmanagement.icsforms.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;

/**
 * Structured clue entry captured from SAR task debriefing and shown in the shared clue log.
 *
 * <p>{@link #immediateAction} records the action taken on the spot (e.g. flagged, photographed,
 * collected).  {@link #followUp} records any planned subsequent follow-up.  The two fields are
 * kept distinct so that the debrief form can render them separately.</p>
 *
 * <p>{@link #possibleDuplicate} flags entries that were <em>reported</em> by another resource
 * rather than directly detected by the logging resource — such entries may duplicate a clue
 * already recorded from the detecting resource's log.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ClueLogEntry {
    private String assignmentId = "";
    private String detectingTask = "";
    /** Free-text identifier of the specific person or resource that detected the clue. */
    private String detectedBy = "";
    private LocalDateTime dateTimeCollected;
    private String location = "";
    private String description = "";
    private String immediateAction = "";
    private String followUp = "";
    private boolean possibleDuplicate = false;

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

    /** @return identifier of the specific person or resource that detected the clue. */
    public String getDetectedBy() {
        return detectedBy;
    }

    /** @param detectedBy identifier of the specific person or resource that detected the clue. */
    public void setDetectedBy(String detectedBy) {
        this.detectedBy = detectedBy == null ? "" : detectedBy;
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

    /** @return immediate action taken at the time of clue detection (e.g. flagged, photographed, collected). */
    public String getImmediateAction() {
        return immediateAction;
    }

    /** @param immediateAction immediate action taken at the time of clue detection. */
    public void setImmediateAction(String immediateAction) {
        this.immediateAction = immediateAction == null ? "" : immediateAction;
    }

    public String getFollowUp() {
        return followUp;
    }

    public void setFollowUp(String followUp) {
        this.followUp = followUp == null ? "" : followUp;
    }

    /** @return {@code true} when this clue was reported by another resource and may already be logged elsewhere. */
    public boolean isPossibleDuplicate() {
        return possibleDuplicate;
    }

    /** @param possibleDuplicate {@code true} when this clue was reported by another resource. */
    public void setPossibleDuplicate(boolean possibleDuplicate) {
        this.possibleDuplicate = possibleDuplicate;
    }
}
