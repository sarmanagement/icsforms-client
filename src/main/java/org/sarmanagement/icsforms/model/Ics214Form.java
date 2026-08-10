package org.sarmanagement.icsforms.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * First-cut ICS 214 activity log content and prepared-by details.
 */
public class Ics214Form {
    private ActivityLogScope logScope = ActivityLogScope.ICP;
    private String linkedFormId = "";
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
     * Returns the log scope.
     *
     * @return log scope.
     */
    public ActivityLogScope getLogScope() {
        return logScope;
    }

    /**
     * Sets the log scope.
     *
     * @param logScope log scope.
     */
    public void setLogScope(ActivityLogScope logScope) {
        this.logScope = logScope == null ? ActivityLogScope.ICP : logScope;
    }

    /**
     * Returns the linked form identifier.
     *
     * @return linked form identifier.
     */
    public String getLinkedFormId() {
        return linkedFormId;
    }

    /**
     * Sets the linked form identifier.
     *
     * @param linkedFormId linked form identifier.
     */
    public void setLinkedFormId(String linkedFormId) {
        this.linkedFormId = linkedFormId == null ? "" : linkedFormId;
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
