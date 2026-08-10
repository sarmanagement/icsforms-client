package org.sarmanagement.icsforms.model;

/**
 * Identifies the operational scope of an ICS 214 activity log.
 */
public enum ActivityLogScope {
    ICP("ICP Communications Log"),
    ASSIGNMENT_LIST("Assignment List Log"),
    TASK_ASSIGNMENT("Task Assignment Log");

    private final String label;

    ActivityLogScope(String label) {
        this.label = label;
    }

    /**
     * Returns the display label for the scope.
     *
     * @return display label.
     */
    public String getLabel() {
        return label;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return label;
    }
}
