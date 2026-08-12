package org.sarmanagement.icsforms.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused tests for ICS 214 defaults and schema version changes.
 */
class Ics214FormTest {

    @Test
    void newFormDefaultsToIcpScope() {
        Ics214Form form = new Ics214Form();

        assertSame(ActivityLogScope.ICP, form.getLogScope());
        assertTrue(form.getLinkedIcs204FormId().isBlank());
        assertTrue(form.getLinkedSarTaskAssignmentId().isBlank());
    }

    @Test
    void formLinkedToIcs204ReportsAssignmentListScope() {
        Ics214Form form = new Ics214Form();
        form.setLinkedIcs204FormId("some-uuid");

        assertSame(ActivityLogScope.ASSIGNMENT_LIST, form.getLogScope());
        assertEquals("some-uuid", form.getLinkedIcs204FormId());
        assertTrue(form.getLinkedSarTaskAssignmentId().isBlank());
    }

    @Test
    void formLinkedToSarTaskReportsTaskAssignmentScope() {
        Ics214Form form = new Ics214Form();
        form.setLinkedSarTaskAssignmentId("T-01");

        assertSame(ActivityLogScope.TASK_ASSIGNMENT, form.getLogScope());
        assertEquals("T-01", form.getLinkedSarTaskAssignmentId());
        assertTrue(form.getLinkedIcs204FormId().isBlank());
    }

    @Test
    void settingIcs204IdClearsSarTaskId() {
        Ics214Form form = new Ics214Form();
        form.setLinkedSarTaskAssignmentId("T-01");
        form.setLinkedIcs204FormId("some-uuid");

        assertTrue(form.getLinkedSarTaskAssignmentId().isBlank());
        assertSame(ActivityLogScope.ASSIGNMENT_LIST, form.getLogScope());
    }

    @Test
    void settingSarTaskIdClearsIcs204Id() {
        Ics214Form form = new Ics214Form();
        form.setLinkedIcs204FormId("some-uuid");
        form.setLinkedSarTaskAssignmentId("T-01");

        assertTrue(form.getLinkedIcs204FormId().isBlank());
        assertSame(ActivityLogScope.TASK_ASSIGNMENT, form.getLogScope());
    }

    @Test
    void activityLogEntriesCanBeAdded() {
        Ics214Form form = new Ics214Form();
        ActivityLogEntry entry = new ActivityLogEntry();
        entry.setNotableActivity("Subject located near trail junction.");
        form.getActivityLog().add(entry);

        assertEquals(1, form.getActivityLog().size());
        assertEquals("Subject located near trail junction.", form.getActivityLog().get(0).getNotableActivity());
    }

    @Test
    void appDataSchemaVersionIsNowThree() {
        AppData data = new AppData();

        assertEquals(3, AppData.CURRENT_SCHEMA_VERSION);
        assertEquals(3, data.getSchemaVersion());
    }

    // --- ActivityEventType configurable behaviour ----------------------------

    @Test
    void defaultTypesContainsExpectedBuiltIns() {
        List<ActivityEventType> defaults = ActivityEventType.defaultTypes();

        assertFalse(defaults.isEmpty());
        assertTrue(defaults.stream().anyMatch(t -> ActivityEventType.ID_FREE_TEXT.equals(t.getId())));
        assertTrue(defaults.stream().anyMatch(t -> ActivityEventType.ID_CLUE_DETECTED.equals(t.getId())));
        assertTrue(defaults.stream().anyMatch(t -> ActivityEventType.ID_SUBJECT_FOUND.equals(t.getId())));
        assertTrue(defaults.stream().anyMatch(t -> ActivityEventType.ID_RESOURCE_ON_TASK.equals(t.getId())));
        assertTrue(defaults.stream().allMatch(ActivityEventType::isBuiltIn));
    }

    @Test
    void customEventTypeCanBeCreatedAndIsNotBuiltIn() {
        ActivityEventType custom = new ActivityEventType("MY_EVENT", "My Custom Event", false);

        assertEquals("MY_EVENT", custom.getId());
        assertEquals("My Custom Event", custom.getLabel());
        assertFalse(custom.isBuiltIn());
        assertEquals("My Custom Event", custom.toString());
    }

    @Test
    void appDataActivityEventTypesStartsEmpty() {
        AppData data = new AppData();

        // Empty until AppController seeds the defaults (that happens at runtime).
        assertTrue(data.getActivityEventTypes().isEmpty());
    }

    @Test
    void appDataActivityEventTypesCanBeSetAndRetrieved() {
        AppData data = new AppData();
        List<ActivityEventType> types = new java.util.ArrayList<>(ActivityEventType.defaultTypes());
        types.add(new ActivityEventType("SPECIAL", "Special Operation", false));
        data.setActivityEventTypes(types);

        assertEquals(ActivityEventType.defaultTypes().size() + 1, data.getActivityEventTypes().size());
        assertEquals("SPECIAL", data.getActivityEventTypes().get(data.getActivityEventTypes().size() - 1).getId());
    }

    @Test
    void activityLogEntryStoresEventTypeIdAsString() {
        ActivityLogEntry entry = new ActivityLogEntry();

        assertEquals(ActivityEventType.ID_FREE_TEXT, entry.getEventTypeId());

        entry.setEventTypeId(ActivityEventType.ID_CLUE_DETECTED);
        assertEquals(ActivityEventType.ID_CLUE_DETECTED, entry.getEventTypeId());
    }

    @Test
    void activityLogEntryDefaultsToFreeTextWhenNullSet() {
        ActivityLogEntry entry = new ActivityLogEntry();
        entry.setEventTypeId(null);

        assertEquals(ActivityEventType.ID_FREE_TEXT, entry.getEventTypeId());
    }
}
