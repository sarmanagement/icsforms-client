package org.sarmanagement.icsforms.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Incident Briefing (ICS 201) content captured by the desktop editor.
 */
public class Ics201Form {
    private String incidentName = "";
    private String incidentNumber = "";
    private LocalDate dateInitiated;
    private LocalTime timeInitiated;
    private String mapSketch = "";
    private String situationSummary = "";
    private List<String> currentObjectives = new ArrayList<>();
    private List<ActionEntry> currentActions = new ArrayList<>();
    private List<ResourceSummaryEntry> resources = new ArrayList<>();
    private String preparedByName = "";
    private String preparedByPositionTitle = "";
    private LocalDateTime preparedDateTime;
    private String preparedBySignature = "";
    private String iapPage = "";

    /**
     * Returns the incident name.
     *
     * @return incident name.
     */
    public String getIncidentName() {
        return incidentName;
    }

    /**
     * Sets the incident name.
     *
     * @param incidentName incident name.
     */
    public void setIncidentName(String incidentName) {
        this.incidentName = incidentName == null ? "" : incidentName;
    }

    /**
     * Returns the incident number.
     *
     * @return incident number.
     */
    public String getIncidentNumber() {
        return incidentNumber;
    }

    /**
     * Sets the incident number.
     *
     * @param incidentNumber incident number.
     */
    public void setIncidentNumber(String incidentNumber) {
        this.incidentNumber = incidentNumber == null ? "" : incidentNumber;
    }

    /**
     * Returns the initiated date.
     *
     * @return initiated date.
     */
    public LocalDate getDateInitiated() {
        return dateInitiated;
    }

    /**
     * Sets the initiated date.
     *
     * @param dateInitiated initiated date.
     */
    public void setDateInitiated(LocalDate dateInitiated) {
        this.dateInitiated = dateInitiated;
    }

    /**
     * Returns the initiated time.
     *
     * @return initiated time.
     */
    public LocalTime getTimeInitiated() {
        return timeInitiated;
    }

    /**
     * Sets the initiated time.
     *
     * @param timeInitiated initiated time.
     */
    public void setTimeInitiated(LocalTime timeInitiated) {
        this.timeInitiated = timeInitiated;
    }

    /**
     * Returns the map/sketch description or reference.
     *
     * @return map/sketch description.
     */
    public String getMapSketch() {
        return mapSketch;
    }

    /**
     * Sets the map/sketch description or reference.
     *
     * @param mapSketch map/sketch description.
     */
    public void setMapSketch(String mapSketch) {
        this.mapSketch = mapSketch == null ? "" : mapSketch;
    }

    /**
     * Returns the situation summary and safety briefing text.
     *
     * @return situation summary.
     */
    public String getSituationSummary() {
        return situationSummary;
    }

    /**
     * Sets the situation summary and safety briefing text.
     *
     * @param situationSummary situation summary.
     */
    public void setSituationSummary(String situationSummary) {
        this.situationSummary = situationSummary == null ? "" : situationSummary;
    }

    /**
     * Returns the current and planned objectives.
     *
     * @return objectives list.
     */
    public List<String> getCurrentObjectives() {
        return currentObjectives;
    }

    /**
     * Sets the current and planned objectives.
     *
     * @param currentObjectives objectives list.
     */
    public void setCurrentObjectives(List<String> currentObjectives) {
        this.currentObjectives = currentObjectives == null ? new ArrayList<>() : new ArrayList<>(currentObjectives);
    }

    /**
     * Returns the current and planned actions.
     *
     * @return action entries.
     */
    public List<ActionEntry> getCurrentActions() {
        return currentActions;
    }

    /**
     * Sets the current and planned actions.
     *
     * @param currentActions action entries.
     */
    public void setCurrentActions(List<ActionEntry> currentActions) {
        this.currentActions = currentActions == null ? new ArrayList<>() : new ArrayList<>(currentActions);
    }

    /**
     * Returns the resource summary rows.
     *
     * @return resource summary rows.
     */
    public List<ResourceSummaryEntry> getResources() {
        return resources;
    }

    /**
     * Sets the resource summary rows.
     *
     * @param resources resource summary rows.
     */
    public void setResources(List<ResourceSummaryEntry> resources) {
        this.resources = resources == null ? new ArrayList<>() : new ArrayList<>(resources);
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
        this.preparedByName = preparedByName == null ? "" : preparedByName;
    }

    /**
     * Returns the preparer position/title.
     *
     * @return preparer position/title.
     */
    public String getPreparedByPositionTitle() {
        return preparedByPositionTitle;
    }

    /**
     * Sets the preparer position/title.
     *
     * @param preparedByPositionTitle preparer position/title.
     */
    public void setPreparedByPositionTitle(String preparedByPositionTitle) {
        this.preparedByPositionTitle = preparedByPositionTitle == null ? "" : preparedByPositionTitle;
    }

    /**
     * Returns the prepared date/time.
     *
     * @return prepared date/time.
     */
    public LocalDateTime getPreparedDateTime() {
        return preparedDateTime;
    }

    /**
     * Sets the prepared date/time.
     *
     * @param preparedDateTime prepared date/time.
     */
    public void setPreparedDateTime(LocalDateTime preparedDateTime) {
        this.preparedDateTime = preparedDateTime;
    }

    /**
     * Returns the preparer signature.
     *
     * @return preparer signature.
     */
    public String getPreparedBySignature() {
        return preparedBySignature;
    }

    /**
     * Sets the preparer signature.
     *
     * @param preparedBySignature preparer signature.
     */
    public void setPreparedBySignature(String preparedBySignature) {
        this.preparedBySignature = preparedBySignature == null ? "" : preparedBySignature;
    }

    /**
     * Returns the IAP page value.
     *
     * @return IAP page value.
     */
    public String getIapPage() {
        return iapPage;
    }

    /**
     * Sets the IAP page value.
     *
     * @param iapPage IAP page value.
     */
    public void setIapPage(String iapPage) {
        this.iapPage = iapPage == null ? "" : iapPage;
    }

    /**
     * Current and planned actions row.
     */
    public static class ActionEntry {
        private String time = "";
        private String actions = "";

        /**
         * Returns the action time.
         *
         * @return action time.
         */
        public String getTime() {
            return time;
        }

        /**
         * Sets the action time.
         *
         * @param time action time.
         */
        public void setTime(String time) {
            this.time = time == null ? "" : time;
        }

        /**
         * Returns the action description.
         *
         * @return action description.
         */
        public String getActions() {
            return actions;
        }

        /**
         * Sets the action description.
         *
         * @param actions action description.
         */
        public void setActions(String actions) {
            this.actions = actions == null ? "" : actions;
        }
    }

    /**
     * Resource summary row.
     */
    public static class ResourceSummaryEntry {
        private String resource = "";
        private String resourceIdentifier = "";
        private LocalDateTime dateTimeOrdered;
        private LocalDateTime eta;
        private boolean arrived;
        private String notes = "";

        /**
         * Returns the resource description.
         *
         * @return resource description.
         */
        public String getResource() {
            return resource;
        }

        /**
         * Sets the resource description.
         *
         * @param resource resource description.
         */
        public void setResource(String resource) {
            this.resource = resource == null ? "" : resource;
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
            this.resourceIdentifier = resourceIdentifier == null ? "" : resourceIdentifier;
        }

        /**
         * Returns the ordered date/time.
         *
         * @return ordered date/time.
         */
        public LocalDateTime getDateTimeOrdered() {
            return dateTimeOrdered;
        }

        /**
         * Sets the ordered date/time.
         *
         * @param dateTimeOrdered ordered date/time.
         */
        public void setDateTimeOrdered(LocalDateTime dateTimeOrdered) {
            this.dateTimeOrdered = dateTimeOrdered;
        }

        /**
         * Returns the estimated time of arrival.
         *
         * @return estimated time of arrival.
         */
        public LocalDateTime getEta() {
            return eta;
        }

        /**
         * Sets the estimated time of arrival.
         *
         * @param eta estimated time of arrival.
         */
        public void setEta(LocalDateTime eta) {
            this.eta = eta;
        }

        /**
         * Returns whether the resource has arrived.
         *
         * @return {@code true} when arrived.
         */
        public boolean isArrived() {
            return arrived;
        }

        /**
         * Sets whether the resource has arrived.
         *
         * @param arrived arrival flag.
         */
        public void setArrived(boolean arrived) {
            this.arrived = arrived;
        }

        /**
         * Returns notes for the resource row.
         *
         * @return notes.
         */
        public String getNotes() {
            return notes;
        }

        /**
         * Sets notes for the resource row.
         *
         * @param notes notes.
         */
        public void setNotes(String notes) {
            this.notes = notes == null ? "" : notes;
        }
    }
}
