// SPDX-License-Identifier: GPL-3.0-or-later
package org.sarmanagement.icsforms.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Activity row recorded on an ICS 214 activity log.
 *
 * <p>
 * The event type is stored as a stable string identifier that references an
 * {@link ActivityEventType} held in {@link AppData#getActivityEventTypes()}.
 * This allows operators to configure custom event types without invalidating
 * previously recorded log entries.
 * Assignment context uses a stable identity independently of the displayed
 * resource, so follow-up entries retain the same task after reloading.
 * </p>
 */
public class ActivityLogEntry {
	private String entryId;
	private LocalDateTime timestamp;
	private String eventTypeId = ActivityEventType.ID_FREE_TEXT;
	private String resourceIdentifier = "";
	private String assignmentId = "";
	private String notableActivity = "";
	private boolean struckOut;
	private List<EventTypeChange> eventTypeChangeHistory = new ArrayList<>();

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
	 * Returns the stable task or ICS 204 assignment context for this activity.
	 *
	 * @return assignment identifier, or an empty string for legacy or unlinked entries
	 */
	public String getAssignmentId() {
		return assignmentId;
	}

	/**
	 * Sets assignment context independently of the activity's displayed resource.
	 *
	 * @param assignmentId stable assignment identifier; null clears the context
	 */
	public void setAssignmentId(String assignmentId) {
		this.assignmentId = assignmentId == null ? "" : assignmentId;
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
	 * Returns the recorded history of event type reclassifications.
	 *
	 * @return ordered classification changes, possibly empty.
	 */
	@JsonInclude(JsonInclude.Include.NON_EMPTY)
	public List<EventTypeChange> getEventTypeChangeHistory() {
		if (eventTypeChangeHistory == null) {
			eventTypeChangeHistory = new ArrayList<>();
		}
		return eventTypeChangeHistory;
	}

	/**
	 * Sets the recorded event type reclassification history during deserialization.
	 *
	 * @param eventTypeChangeHistory prior classification changes.
	 */
	public void setEventTypeChangeHistory(List<EventTypeChange> eventTypeChangeHistory) {
		this.eventTypeChangeHistory = eventTypeChangeHistory == null
				? new ArrayList<>() : new ArrayList<>(eventTypeChangeHistory);
	}

	/**
	 * Reclassifies a Note entry and records the previous and new event type.
	 *
	 * @param newEventTypeId replacement event type identifier.
	 * @param changedAt time the reclassification was made.
	 * @throws IllegalStateException if this entry is not currently a Note.
	 * @throws IllegalArgumentException if the replacement type or change time is invalid.
	 */
	public void reclassifyEventType(String newEventTypeId, LocalDateTime changedAt) {
		if (!ActivityEventType.ID_FREE_TEXT.equals(eventTypeId)) {
			throw new IllegalStateException("Only Note entries can be reclassified.");
		}
		if (newEventTypeId == null || newEventTypeId.isBlank()
				|| ActivityEventType.ID_FREE_TEXT.equals(newEventTypeId) || changedAt == null) {
			throw new IllegalArgumentException("A replacement event type and change time are required.");
		}
		getEventTypeChangeHistory().add(new EventTypeChange(eventTypeId, newEventTypeId, changedAt));
		setEventTypeId(newEventTypeId);
	}

	/**
	 * One durable event type reclassification audit record.
	 *
	 * @param previousEventTypeId event type before the change.
	 * @param newEventTypeId event type after the change.
	 * @param changedAt time the change was made.
	 */
	public record EventTypeChange(String previousEventTypeId, String newEventTypeId, LocalDateTime changedAt) {
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
