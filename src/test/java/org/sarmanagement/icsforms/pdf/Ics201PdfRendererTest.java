package org.sarmanagement.icsforms.pdf;

import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.Ics201Form;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Smoke tests for ICS 201 PDF rendering.
 */
class Ics201PdfRendererTest {

    @Test
    void populatedFormRendersToFourPagePdf() throws Exception {
        Path outputDir = Path.of("target", "test-output", "ics201");
        Files.createDirectories(outputDir);
        Path outputFile = outputDir.resolve("ics-201-" + System.nanoTime() + ".pdf");

        AppData data = new AppData();
        Ics201Form form = data.getForm201();
        form.setIncidentName("Test Incident");
        form.setIncidentNumber("INC-201");
        form.setDateInitiated(LocalDate.parse("2026-01-01"));
        form.setTimeInitiated(LocalTime.parse("06:30"));
        form.setMapSketch("Grid reference map attached.");
        form.setSituationSummary("Initial briefing summary.");
        form.setCurrentObjectives(List.of("Protect life", "Stabilize scene"));
        Ics201Form.ActionEntry action = new Ics201Form.ActionEntry();
        action.setTime("07:00");
        action.setActions("Dispatch initial resources.");
        form.setCurrentActions(List.of(action));
        Ics201Form.ResourceSummaryEntry resource = new Ics201Form.ResourceSummaryEntry();
        resource.setResource("Ground Team");
        resource.setResourceIdentifier("GT-1");
        resource.setDateTimeOrdered(LocalDateTime.parse("2026-01-01T07:05:00"));
        resource.setEta(LocalDateTime.parse("2026-01-01T07:35:00"));
        resource.setArrived(true);
        resource.setNotes("Ready at ICP");
        form.setResources(List.of(resource));
        form.setPreparedByName("Planner");
        form.setPreparedByPositionTitle("Planning Section Chief");
        form.setPreparedDateTime(LocalDateTime.parse("2026-01-01T08:00:00"));
        form.setPreparedBySignature("Planner");
        form.setIapPage("1");

        new Ics201PdfRenderer().render(data, outputFile);

        assertTrue(Files.exists(outputFile));
        assertTrue(Files.size(outputFile) > 0L);
        try (org.apache.pdfbox.pdmodel.PDDocument pdf = org.apache.pdfbox.Loader.loadPDF(outputFile.toFile())) {
            assertEquals(4, pdf.getNumberOfPages());
        }
    }
}
