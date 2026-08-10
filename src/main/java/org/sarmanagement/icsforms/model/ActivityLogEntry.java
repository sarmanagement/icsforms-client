package org.sarmanagement.icsforms.model;

import java.time.LocalDateTime;

/**
 * Activity row recorded on an ICS 214 activity log.
 *
 * <p>The event type is stored as a stable string identifier that references an
 * {@link ActivityEventType} held in {@link AppData#getActivityEventTypes()}.
 * This allows operators to configure custom event types without invalidating
 * previously recorded log entries.</p>
 */
public class ActivityLogEntry {
    private LocalDateTime timestamp;
    private String eventTypeId = ActivityEventType.ID_FREE_TEXT;
    private String resourceIdentifier = "";
    private String notableActivity = "";

    /**
     * Returns the recorded date/time.
     *
     * @return recorded date/time.
     */
    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    /**
     * Sets the recorded date/time.
     *
     * @param timestamp recorded date/time.
     */
    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * Returns the event type identifier referencing an {@link ActivityEventType}.
     *
     * @return event type identifier.
     */
    public String getEventTypeId() {
        return eventTypeId;
    }

    /**
     * Sets the event type identifier.
     *
     * @param eventTypeId event type identifier; defaults to {@link ActivityEventType#ID_FREE_TEXT} when {@code null}.
     */
    public void setEventTypeId(String eventTypeId) {
        this.eventTypeId = eventTypeId == null ? ActivityEventType.ID_FREE_TEXT : eventTypeId;
    }

    /**
     * Returns the linked resource identifier.
     *
     * @return linked resource identifier.
     */
    public String getResourceIdentifier() {
        return resourceIdentifier;
    }

    /**
     * Sets the linked resource identifier.
     *
     * @param resourceIdentifier linked resource identifier.
     */
    public void setResourceIdentifier(String resourceIdentifier) {
        this.resourceIdentifier = resourceIdentifier == null ? "" : resourceIdentifier;
    }

    /**
     * Returns the notable activity text.
     *
     * @return notable activity text.
     */
    public String getNotableActivity() {
        return notableActivity;
    }

    /**
     * Sets the notable activity text.
     *
     * @param notableActivity notable activity text.
     */
    public void setNotableActivity(String notableActivity) {
        this.notableActivity = notableActivity == null ? "" : notableActivity;
    }
}
