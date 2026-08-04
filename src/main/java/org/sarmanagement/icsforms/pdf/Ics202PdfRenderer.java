package org.sarmanagement.icsforms.pdf;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.Ics202Form;
import org.sarmanagement.icsforms.model.IncidentContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Structured PDF renderer for the Incident Objectives (ICS 202) form.
 */
public class Ics202PdfRenderer extends AbstractPdfRenderer implements PdfFormRenderer {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** {@inheritDoc} */
    @Override
    public String getFormKey() {
        return "ICS 202";
    }

    /** {@inheritDoc} */
    @Override
    public void render(AppData data, Path outputFile) throws IOException {
        Files.createDirectories(outputFile.getParent());
        try (PDDocument document = new PDDocument()) {
            List<List<String>> blocks = new ArrayList<>();
            IncidentContext context = data.getIncidentContext();
            Ics202Form form = data.getForm202();
            blocks.add(List.of(
                    "Incident Name: " + safe(context.getIncidentName()),
                    "Operational Period: " + format(context.getOperationalPeriodStart()) + " to " + format(context.getOperationalPeriodEnd()),
                    "Prepared/Current User: " + safe(context.getCurrentUser()) + " / " + safe(context.getCurrentUserPositionTitle())
            ));
            List<String> objectivesBlock = new ArrayList<>();
            objectivesBlock.add("Objectives:");
            int index = 1;
            for (String objective : form.getObjectives()) {
                objectivesBlock.add(index++ + ". " + safe(objective));
            }
            blocks.add(objectivesBlock);
            blocks.add(List.of(
                    "Command Emphasis: " + safe(form.getCommandEmphasis()),
                    "General Situational Awareness: " + safe(form.getSituationalAwareness()),
                    "Site Safety Plan Required: " + (form.isSiteSafetyPlanRequired() ? "Yes" : "No"),
                    "Included Forms / Attachments: " + String.join(", ", form.getIncidentActionPlanAttachments())
            ));
            blocks.add(List.of(
                    "Prepared By: " + safe(form.getPreparedByName()) + " / " + safe(form.getPreparedByPositionTitle()),
                    "Approved By IC: " + safe(form.getApprovedByIncidentCommanderName()) + " / " + format(form.getApprovedDateTime()),
                    "IAP Page: " + safe(form.getIapPage())
            ));
            writeDocument(document, "ICS 202", "Incident Objectives", blocks);
            document.save(outputFile.toFile());
        }
    }

    /**
     * Formats a date/time for PDF output.
     *
     * @param value date/time value.
     * @return formatted text.
     */
    private String format(java.time.LocalDateTime value) {
        return value == null ? "" : FORMATTER.format(value);
    }

    /**
     * Converts null values to empty text for PDF output.
     *
     * @param value value to normalize.
     * @return safe string.
     */
    private String safe(String value) {
        return value == null ? "" : value;
    }
}
