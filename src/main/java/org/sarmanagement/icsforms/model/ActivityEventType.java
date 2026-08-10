package org.sarmanagement.icsforms.model;

import java.util.List;

/**
 * Configurable event type used to categorise ICS 214 activity log entries.
 *
 * <p>Built-in types are provided by {@link #defaultTypes()} and are pre-populated
 * into {@link AppData#getActivityEventTypes()} on first use.  Operators may extend
 * or replace this list with types that suit the specific incident or organisation.</p>
 */
public class ActivityEventType {

    /** Stable identifier for the built-in "Clue Detected" type. */
    public static final String ID_CLUE_DETECTED = "CLUE_DETECTED";

    /** Stable identifier for the built-in "Subject Found" type. */
    public static final String ID_SUBJECT_FOUND = "SUBJECT_FOUND";

    /** Stable identifier for the built-in "Resource Going on Task" type. */
    public static final String ID_RESOURCE_ON_TASK = "RESOURCE_ON_TASK";

    /** Stable identifier for the built-in "Task Completed" type. */
    public static final String ID_TASK_COMPLETED = "TASK_COMPLETED";

    /** Stable identifier for the built-in "Resource Departed Staging" type. */
    public static final String ID_RESOURCE_DEPARTED_STAGING = "RESOURCE_DEPARTED_STAGING";

    /** Stable identifier for the built-in "Resource Returned to Staging" type. */
    public static final String ID_RESOURCE_RETURNED_STAGING = "RESOURCE_RETURNED_STAGING";

    /** Stable identifier for the built-in free-text "Note" type. */
    public static final String ID_FREE_TEXT = "FREE_TEXT";

    private String id = "";
    private String label = "";
    private boolean builtIn = false;

    /**
     * Creates an empty event type (required for JSON deserialisation).
     */
    public ActivityEventType() {
    }

    /**
     * Creates an event type with the supplied id and label.
     *
     * @param id    stable identifier used for JSON storage and look-up.
     * @param label display label shown in the UI and printed on the ICS 214.
     * @param builtIn {@code true} when this is a pre-defined system type.
     */
    public ActivityEventType(String id, String label, boolean builtIn) {
        this.id = id == null ? "" : id;
        this.label = label == null ? "" : label;
        this.builtIn = builtIn;
    }

    /**
     * Returns the default set of built-in event types supplied with the application.
     *
     * @return ordered list of built-in event types.
     */
    public static List<ActivityEventType> defaultTypes() {
        return List.of(
                new ActivityEventType(ID_FREE_TEXT, "Note", true),
                new ActivityEventType(ID_CLUE_DETECTED, "Clue Detected", true),
                new ActivityEventType(ID_SUBJECT_FOUND, "Subject Found", true),
                new ActivityEventType(ID_RESOURCE_ON_TASK, "Resource Going on Task", true),
                new ActivityEventType(ID_TASK_COMPLETED, "Task Completed", true),
                new ActivityEventType(ID_RESOURCE_DEPARTED_STAGING, "Resource Departed Staging", true),
                new ActivityEventType(ID_RESOURCE_RETURNED_STAGING, "Resource Returned to Staging", true)
        );
    }

    /**
     * Returns the stable identifier for this event type.
     *
     * @return stable identifier.
     */
    public String getId() {
        return id;
    }

    /**
     * Sets the stable identifier for this event type.
     *
     * @param id stable identifier.
     */
    public void setId(String id) {
        this.id = id == null ? "" : id;
    }

    /**
     * Returns the display label for this event type.
     *
     * @return display label.
     */
    public String getLabel() {
        return label;
    }

    /**
     * Sets the display label for this event type.
     *
     * @param label display label.
     */
    public void setLabel(String label) {
        this.label = label == null ? "" : label;
    }

    /**
     * Returns whether this is a pre-defined built-in type.
     *
     * @return {@code true} for built-in types.
     */
    public boolean isBuiltIn() {
        return builtIn;
    }

    /**
     * Sets whether this is a built-in type.
     *
     * @param builtIn {@code true} for built-in types.
     */
    public void setBuiltIn(boolean builtIn) {
        this.builtIn = builtIn;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return label;
    }
}
