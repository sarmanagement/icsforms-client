package org.sarmanagement.icsforms.model;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Activity row recorded on an ICS 214 activity log.
 *
 * <p>
 * The event type is stored as a stable string identifier that references an
 * {@link ActivityEventType} held in {@link AppData#getActivityEventTypes()}.
 * This allows operators to configure custom event types without invalidating
 * previously recorded log entries.
 * </p>
 */
public class ActivityLogEntry {
	private String entryId;
	private LocalDateTime timestamp;
	private String eventTypeId = ActivityEventType.ID_FREE_TEXT;
	private String resourceIdentifier = "";
	private String notableActivity = "";
	private boolean struckOut;

	/**
	 * Returns this activity row's stable identity, assigning a UUID to legacy
	 * rows without one.
	 *
	 * @return activity entry UUID, never {@code null} or blank.
	 */
	public String getEntryId() {
		if (entryId == null || entryId.isBlank()) {
			entryId = UUID.randomUUID().toString();
		}
		return entryId;
	}

	/**
	 * Sets this activity row's identity during deserialization or migration.
	 *
	 * @param entryId
	 *            stable entry UUID; missing values are generated on first access.
	 */
	public void setEntryId(String entryId) {
		this.entryId = entryId;
	}

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
	 * @param timestamp
	 *            recorded date/time.
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
	 * @param eventTypeId
	 *            event type identifier; defaults to
	 *            {@link ActivityEventType#ID_FREE_TEXT} when {@code null}.
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
	 * @param resourceIdentifier
	 *            linked resource identifier.
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
	 * @param notableActivity
	 *            notable activity text.
	 */
	public void setNotableActivity(String notableActivity) {
		this.notableActivity = notableActivity == null ? "" : notableActivity;
	}

	/**
	 * Returns whether the entry has been struck out instead of removed.
	 *
	 * @return {@code true} when the entry should render struck through.
	 */
	public boolean isStruckOut() {
		return struckOut;
	}

	/**
	 * Sets whether the entry has been struck out instead of removed.
	 *
	 * @param struckOut
	 *            {@code true} to render the entry struck through.
	 */
	public void setStruckOut(boolean struckOut) {
		this.struckOut = struckOut;
	}
}
