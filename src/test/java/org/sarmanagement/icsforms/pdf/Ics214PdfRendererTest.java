package org.sarmanagement.icsforms.pdf;

import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.model.ActivityEventType;
import org.sarmanagement.icsforms.model.ActivityLogEntry;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.Ics214Form;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Smoke tests for ICS 214 PDF rendering.
 */
class Ics214PdfRendererTest {

    @Test
    void blankFormRendersToNonEmptyPdf() throws Exception {
        Path outputDir = Path.of("target", "test-output", "ics214");
        Files.createDirectories(outputDir);
        Path outputFile = outputDir.resolve("blank-ics-214-" + System.nanoTime() + ".pdf");

        new Ics214PdfRenderer().render(new AppData(), outputFile);

        assertTrue(Files.exists(outputFile));
        assertTrue(Files.size(outputFile) > 0L);
    }

    @Test
    void formWithCustomEventTypeRendersToNonEmptyPdf() throws Exception {
        Path outputDir = Path.of("target", "test-output", "ics214");
        Files.createDirectories(outputDir);
        Path outputFile = outputDir.resolve("custom-event-ics-214-" + System.nanoTime() + ".pdf");

        AppData data = new AppData();
        // Seed a custom event type alongside the built-in defaults.
        List<ActivityEventType> types = new ArrayList<>(ActivityEventType.defaultTypes());
        ActivityEventType custom = new ActivityEventType("AERIAL_SEARCH", "Aerial Search Completed", false);
        types.add(custom);
        data.setActivityEventTypes(types);

        // Add one log entry using the custom type and one using the built-in clue type.
        Ics214Form form = new Ics214Form();
        ActivityLogEntry entry1 = new ActivityLogEntry();
        entry1.setTimestamp(LocalDateTime.now());
        entry1.setEventTypeId("AERIAL_SEARCH");
        entry1.setNotableActivity("Grid 42 completed with no find.");
        form.getActivityLog().add(entry1);

        ActivityLogEntry entry2 = new ActivityLogEntry();
        entry2.setTimestamp(LocalDateTime.now().minusHours(1));
        entry2.setEventTypeId(ActivityEventType.ID_CLUE_DETECTED);
        entry2.setNotableActivity("Boot print near creek.");
        form.getActivityLog().add(entry2);

        data.getActivityLogs().add(form);

        new Ics214PdfRenderer().render(data, outputFile);

        assertTrue(Files.exists(outputFile));
        assertTrue(Files.size(outputFile) > 0L);
    }
}
