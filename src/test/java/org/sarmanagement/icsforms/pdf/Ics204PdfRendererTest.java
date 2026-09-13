package org.sarmanagement.icsforms.pdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.Ics204Form;
import org.sarmanagement.icsforms.model.IncidentContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertTrue;

class Ics204PdfRendererTest {

    @Test
    void preparedBySectionKeepsTypicalFullNamesVisible() throws Exception {
        AppData data = new AppData();
        IncidentContext context = data.getIncidentContext();
        context.setIncidentName("Test Incident");
        context.setOperationalPeriodStart(LocalDateTime.of(2026, 1, 1, 8, 0));
        context.setOperationalPeriodEnd(LocalDateTime.of(2026, 1, 1, 20, 0));

        Ics204Form form = data.getForm204();
        form.setPreparedByName("Elizabeth Montgomery");
        form.setPreparedByPositionTitle("Planning Section Chief");
        form.setPreparedBySignature("Elizabeth Montgomery");
        form.setPreparedDateTime(LocalDateTime.of(2026, 1, 1, 12, 30));
        form.setIapPage("3");
        form.setOperationsSectionChiefName("Ops Chief");
        form.setOperationsSectionChiefContact("555-0100");
        form.setDivision("Division A");

        Path outputDir = Path.of("target", "test-output", "ics204");
        Files.createDirectories(outputDir);
        Path outputFile = outputDir.resolve("prepared-by-ics-204-" + System.nanoTime() + ".pdf");

        new Ics204PdfRenderer().render(data, outputFile);

        try (PDDocument pdf = Loader.loadPDF(outputFile.toFile())) {
            String text = new PDFTextStripper().getText(pdf).replaceAll("\\s+", " ");
            assertTrue(text.contains("Elizabeth Montgomery"));
            assertTrue(text.contains("Planning Section Chief"));
            assertTrue(text.contains("Date/Time"));
            assertTrue(text.contains("IAP Page 3") || text.contains("IAP Page: 3"));
        }
    }
}
