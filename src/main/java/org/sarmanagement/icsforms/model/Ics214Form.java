package org.sarmanagement.icsforms.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * First-cut ICS 214 activity log content and prepared-by details.
 *
 * <p>Each log is associated with at most one other data object through a typed
 * relationship: an ICS 204 assignment list (referenced by {@link #linkedIcs204FormId})
 * or a SAR task assignment (referenced by {@link #linkedSarTaskAssignmentId}).  When
 * neither reference is set the log belongs to the ICP/command-post level.
 * The operational scope is derived from these typed fields via {@link #getLogScope()}.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Ics214Form {
    private String linkedIcs204FormId = "";
    private String linkedSarTaskAssignmentId = "";
    private String name = "";
    private String icsPosition = "";
    private String homeAgency = "";
    private List<SarTaskResource> resourcesAssigned = new ArrayList<>();
    private List<ActivityLogEntry> activityLog = new ArrayList<>();
    private String preparedByName = "";
    private String preparedByPositionTitle = "";
    private String preparedBySignature = "";
    private LocalDateTime preparedDateTime;

    /**
     * Returns the operational scope derived from the typed association fields.
     *
     * <ul>
     *   <li>{@link ActivityLogScope#TASK_ASSIGNMENT} when {@link #linkedSarTaskAssignmentId} is set.</li>
     *   <li>{@link ActivityLogScope#ASSIGNMENT_LIST} when {@link #linkedIcs204FormId} is set.</li>
     *   <li>{@link ActivityLogScope#ICP} when neither typed reference is set.</li>
     * </ul>
     *
     * @return derived log scope.
     */
    public ActivityLogScope getLogScope() {
        if (linkedSarTaskAssignmentId != null && !linkedSarTaskAssignmentId.isBlank()) {
            return ActivityLogScope.TASK_ASSIGNMENT;
        }
        if (linkedIcs204FormId != null && !linkedIcs204FormId.isBlank()) {
            return ActivityLogScope.ASSIGNMENT_LIST;
        }
        return ActivityLogScope.ICP;
    }

    /**
     * Returns the {@link org.sarmanagement.icsforms.model.Ics204Form#getFormId() formId} of the
     * ICS 204 assignment list associated with this log, or an empty string when not linked.
     *
     * @return linked ICS 204 form identifier.
     */
    public String getLinkedIcs204FormId() {
        return linkedIcs204FormId;
    }

    /**
     * Sets the ICS 204 form identifier for an assignment-list-scoped log.
     * Clears {@link #linkedSarTaskAssignmentId} so the two typed references stay mutually exclusive.
     *
     * @param linkedIcs204FormId ICS 204 form identifier, or {@code null}/blank to clear.
     */
    public void setLinkedIcs204FormId(String linkedIcs204FormId) {
        this.linkedIcs204FormId = linkedIcs204FormId == null ? "" : linkedIcs204FormId;
        if (!this.linkedIcs204FormId.isBlank()) {
            this.linkedSarTaskAssignmentId = "";
        }
    }

    /**
     * Returns the {@link SarTaskAssignment#getAssignmentId() assignmentId} of the SAR task
     * assignment associated with this log, or an empty string when not linked.
     *
     * @return linked SAR task assignment identifier.
     */
    public String getLinkedSarTaskAssignmentId() {
        return linkedSarTaskAssignmentId;
    }

    /**
     * Sets the SAR task assignment identifier for a task-assignment-scoped log.
     * Clears {@link #linkedIcs204FormId} so the two typed references stay mutually exclusive.
     *
     * @param linkedSarTaskAssignmentId SAR task assignment identifier, or {@code null}/blank to clear.
     */
    public void setLinkedSarTaskAssignmentId(String linkedSarTaskAssignmentId) {
        this.linkedSarTaskAssignmentId = linkedSarTaskAssignmentId == null ? "" : linkedSarTaskAssignmentId;
        if (!this.linkedSarTaskAssignmentId.isBlank()) {
            this.linkedIcs204FormId = "";
        }
    }

    /**
     * Returns the section 3 name value.
     *
     * @return section 3 name value.
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the section 3 name value.
     *
     * @param name section 3 name value.
     */
    public void setName(String name) {
        this.name = name == null ? "" : name;
    }

    /**
     * Returns the section 4 ICS position value.
     *
     * @return section 4 ICS position value.
     */
    public String getIcsPosition() {
        return icsPosition;
    }

    /**
     * Sets the section 4 ICS position value.
     *
     * @param icsPosition section 4 ICS position value.
     */
    public void setIcsPosition(String icsPosition) {
        this.icsPosition = icsPosition == null ? "" : icsPosition;
    }

    /**
     * Returns the section 5 home agency value.
     *
     * @return section 5 home agency value.
     */
    public String getHomeAgency() {
        return homeAgency;
    }

    /**
     * Sets the section 5 home agency value.
     *
     * @param homeAgency section 5 home agency value.
     */
    public void setHomeAgency(String homeAgency) {
        this.homeAgency = homeAgency == null ? "" : homeAgency;
    }

    /**
     * Returns the section 6 resources assigned.
     *
     * @return section 6 resources assigned.
     */
    public List<SarTaskResource> getResourcesAssigned() {
        return resourcesAssigned;
    }

    /**
     * Sets the section 6 resources assigned.
     *
     * @param resourcesAssigned section 6 resources assigned.
     */
    public void setResourcesAssigned(List<SarTaskResource> resourcesAssigned) {
        this.resourcesAssigned = resourcesAssigned == null ? new ArrayList<>() : resourcesAssigned;
    }

    /**
     * Returns the section 7 activity log rows.
     *
     * @return section 7 activity log rows.
     */
    public List<ActivityLogEntry> getActivityLog() {
        return activityLog;
    }

    /**
     * Sets the section 7 activity log rows.
     *
     * @param activityLog section 7 activity log rows.
     */
    public void setActivityLog(List<ActivityLogEntry> activityLog) {
        this.activityLog = activityLog == null ? new ArrayList<>() : activityLog;
    }

    /**
     * Returns the section 8 preparer name.
     *
     * @return section 8 preparer name.
     */
    public String getPreparedByName() {
        return preparedByName;
    }

    /**
     * Sets the section 8 preparer name.
     *
     * @param preparedByName section 8 preparer name.
     */
    public void setPreparedByName(String preparedByName) {
        this.preparedByName = preparedByName == null ? "" : preparedByName;
    }

    /**
     * Returns the section 8 preparer position/title.
     *
     * @return section 8 preparer position/title.
     */
    public String getPreparedByPositionTitle() {
        return preparedByPositionTitle;
    }

    /**
     * Sets the section 8 preparer position/title.
     *
     * @param preparedByPositionTitle section 8 preparer position/title.
     */
    public void setPreparedByPositionTitle(String preparedByPositionTitle) {
        this.preparedByPositionTitle = preparedByPositionTitle == null ? "" : preparedByPositionTitle;
    }

    /**
     * Returns the section 8 preparer signature text.
     *
     * @return section 8 preparer signature text.
     */
    public String getPreparedBySignature() {
        return preparedBySignature;
    }

    /**
     * Sets the section 8 preparer signature text.
     *
     * @param preparedBySignature section 8 preparer signature text.
     */
    public void setPreparedBySignature(String preparedBySignature) {
        this.preparedBySignature = preparedBySignature == null ? "" : preparedBySignature;
    }

    /**
     * Returns the section 8 prepared date/time.
     *
     * @return section 8 prepared date/time.
     */
    public LocalDateTime getPreparedDateTime() {
        return preparedDateTime;
    }

    /**
     * Sets the section 8 prepared date/time.
     *
     * @param preparedDateTime section 8 prepared date/time.
     */
    public void setPreparedDateTime(LocalDateTime preparedDateTime) {
        this.preparedDateTime = preparedDateTime;
    }
}
