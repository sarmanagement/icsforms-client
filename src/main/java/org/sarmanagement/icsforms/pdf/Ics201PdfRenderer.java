package org.sarmanagement.icsforms.pdf;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.Ics201Form;
import org.sarmanagement.icsforms.model.IncidentContext;
import org.sarmanagement.icsforms.model.OrganizationalChart;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Structured PDF renderer for the Incident Briefing (ICS 201) form.
 */
public class Ics201PdfRenderer extends AbstractPdfRenderer implements PdfFormRenderer {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final float BODY_FONT_SIZE = 10f;
    private static final float HEADING_FONT_SIZE = 10f;
    private static final float LINE_HEIGHT = 12f;
    private static final float CELL_PADDING = 4f;

    /** {@inheritDoc} */
    @Override
    public String getFormKey() {
        return "ICS 201";
    }

    /** {@inheritDoc} */
    @Override
    public void render(AppData data, Path outputFile) throws IOException {
        Files.createDirectories(outputFile.getParent());
        try (PDDocument document = new PDDocument()) {
            Ics201Form form = data.getForm201();
            renderPageOne(document, data, form);
            renderPageTwo(document, data, form);
            renderPageThree(document, data, form);
            renderPageFour(document, data, form);
            document.save(outputFile.toFile());
        }
    }

    private void renderPageOne(PDDocument document, AppData data, Ics201Form form) throws IOException {
        PDPage page = new PDPage(PDRectangle.LETTER);
        document.addPage(page);
        try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
            PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            FormLayout layout = formLayout(page);
            drawFormHeader(stream, bold, layout, "ICS 201", "INCIDENT BRIEFING");
            drawFormFrame(stream, layout);

            float headerHeight = 52f;
            float mapHeight = 220f;
            float footerHeight = 74f;
            float summaryHeight = layout.height() - headerHeight - mapHeight - footerHeight;
            float y = layout.top();

            drawTopHeader(stream, bold, regular, layout, form, data.getIncidentContext(), y - headerHeight, headerHeight);
            y -= headerHeight;
            drawSection(stream, bold, regular, layout.x(), y - mapHeight, layout.width(), mapHeight,
                    "4. Map/Sketch", wrap(safe(form.getMapSketch()), 92));
            y -= mapHeight;
            drawSection(stream, bold, regular, layout.x(), y - summaryHeight, layout.width(), summaryHeight,
                    "5. Situation Summary and Health and Safety Briefing", wrap(safe(form.getSituationSummary()), 92));
            y -= summaryHeight;
            drawPreparedBySection(stream, bold, regular, layout.x(), y - footerHeight, layout.width(), footerHeight, form, 1);
        }
    }

    private void renderPageTwo(PDDocument document, AppData data, Ics201Form form) throws IOException {
        PDPage page = new PDPage(PDRectangle.LETTER);
        document.addPage(page);
        try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
            PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            FormLayout layout = formLayout(page);
            drawFormHeader(stream, bold, layout, "ICS 201", "INCIDENT BRIEFING");
            drawFormFrame(stream, layout);

            float headerHeight = 52f;
            float objectivesHeight = 220f;
            float footerHeight = 74f;
            float actionsHeight = layout.height() - headerHeight - objectivesHeight - footerHeight;
            float y = layout.top();

            drawTopHeader(stream, bold, regular, layout, form, data.getIncidentContext(), y - headerHeight, headerHeight);
            y -= headerHeight;
            drawSection(stream, bold, regular, layout.x(), y - objectivesHeight, layout.width(), objectivesHeight,
                    "7. Current and Planned Objectives", numberedLines(form.getCurrentObjectives()));
            y -= objectivesHeight;
            drawActionsSection(stream, bold, regular, layout.x(), y - actionsHeight, layout.width(), actionsHeight, form.getCurrentActions());
            y -= actionsHeight;
            drawPreparedBySection(stream, bold, regular, layout.x(), y - footerHeight, layout.width(), footerHeight, form, 2);
        }
    }

    private void renderPageThree(PDDocument document, AppData data, Ics201Form form) throws IOException {
        PDPage page = new PDPage(PDRectangle.LETTER);
        document.addPage(page);
        try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
            PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            FormLayout layout = formLayout(page);
            drawFormHeader(stream, bold, layout, "ICS 201", "INCIDENT BRIEFING");
            drawFormFrame(stream, layout);

            float headerHeight = 52f;
            float footerHeight = 74f;
            float orgHeight = layout.height() - headerHeight - footerHeight;
            float y = layout.top();

            drawTopHeader(stream, bold, regular, layout, form, data.getIncidentContext(), y - headerHeight, headerHeight);
            y -= headerHeight;
            drawSection(stream, bold, regular, layout.x(), y - orgHeight, layout.width(), orgHeight,
                    "9. Current Organization", organizationLines(data.getOrganizationalChart()));
            y -= orgHeight;
            drawPreparedBySection(stream, bold, regular, layout.x(), y - footerHeight, layout.width(), footerHeight, form, 3);
        }
    }

    private void renderPageFour(PDDocument document, AppData data, Ics201Form form) throws IOException {
        PDPage page = new PDPage(PDRectangle.LETTER);
        document.addPage(page);
        try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
            PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            FormLayout layout = formLayout(page);
            drawFormHeader(stream, bold, layout, "ICS 201", "INCIDENT BRIEFING");
            drawFormFrame(stream, layout);

            float headerHeight = 52f;
            float footerHeight = 74f;
            float tableHeight = layout.height() - headerHeight - footerHeight;
            float y = layout.top();

            drawTopHeader(stream, bold, regular, layout, form, data.getIncidentContext(), y - headerHeight, headerHeight);
            y -= headerHeight;
            drawResourcesSection(stream, bold, regular, layout.x(), y - tableHeight, layout.width(), tableHeight, form.getResources());
            y -= tableHeight;
            drawPreparedBySection(stream, bold, regular, layout.x(), y - footerHeight, layout.width(), footerHeight, form, 4);
        }
    }

    private void drawTopHeader(PDPageContentStream stream, PDType1Font bold, PDType1Font regular,
                               FormLayout layout, Ics201Form form, IncidentContext context,
                               float y, float height) throws IOException {
        float[] widths = {layout.width() * 0.4f, layout.width() * 0.2f, layout.width() * 0.4f};
        float x = layout.x();
        drawCell(stream, x, y, widths[0], height);
        drawCell(stream, x + widths[0], y, widths[1], height);
        drawCell(stream, x + widths[0] + widths[1], y, widths[2], height);

        drawSection(stream, bold, regular, x, y, widths[0], height,
                "1. Incident Name", List.of(resolveIncidentName(form, context)));
        drawSection(stream, bold, regular, x + widths[0], y, widths[1], height,
                "2. Incident Number", List.of(safe(form.getIncidentNumber())));
        drawDateTimeInitiatedSection(stream, bold, regular, x + widths[0] + widths[1], y, widths[2], height, form);
    }

    private void drawDateTimeInitiatedSection(PDPageContentStream stream, PDType1Font bold, PDType1Font regular,
                                              float x, float y, float width, float height, Ics201Form form) throws IOException {
        drawCell(stream, x, y, width, height);
        drawHeading(stream, bold, x, y + height, "3. Date/Time Initiated");
        float baseline = y + height - CELL_PADDING - HEADING_FONT_SIZE - 16f;
        drawInlinePair(stream, bold, regular, x + CELL_PADDING, baseline, "Date", formatDate(form.getDateInitiated()));
        drawInlinePair(stream, bold, regular, x + (width / 2f), baseline, "Time", formatTime(form.getTimeInitiated()));
    }

    private void drawActionsSection(PDPageContentStream stream, PDType1Font bold, PDType1Font regular,
                                    float x, float y, float width, float height,
                                    List<Ics201Form.ActionEntry> actions) throws IOException {
        drawCell(stream, x, y, width, height);
        drawHeading(stream, bold, x, y + height, "8. Current and Planned Actions");
        float tableTop = y + height - CELL_PADDING - HEADING_FONT_SIZE - 14f;
        float headerHeight = 18f;
        float timeWidth = 80f;
        float actionsWidth = width - timeWidth;
        drawCell(stream, x, tableTop - headerHeight, timeWidth, headerHeight);
        drawCell(stream, x + timeWidth, tableTop - headerHeight, actionsWidth, headerHeight);
        writeCellText(stream, bold, x + CELL_PADDING, tableTop - 12f, "Time");
        writeCellText(stream, bold, x + timeWidth + CELL_PADDING, tableTop - 12f, "Actions");
        float rowTop = tableTop - headerHeight;
        float rowHeight = 22f;
        int maxRows = Math.max(1, (int) ((rowTop - y - CELL_PADDING) / rowHeight));
        List<Ics201Form.ActionEntry> rows = actions == null ? List.of() : actions;
        for (int i = 0; i < maxRows; i++) {
            float rowY = rowTop - ((i + 1) * rowHeight);
            drawCell(stream, x, rowY, timeWidth, rowHeight);
            drawCell(stream, x + timeWidth, rowY, actionsWidth, rowHeight);
            if (i < rows.size()) {
                writeCellText(stream, regular, x + CELL_PADDING, rowY + rowHeight - 14f, safe(rows.get(i).getTime()));
                writeCellText(stream, regular, x + timeWidth + CELL_PADDING, rowY + rowHeight - 14f,
                        safe(truncate(rows.get(i).getActions(), 90)));
            } else if (i == maxRows - 1 && rows.size() > maxRows) {
                writeCellText(stream, regular, x + timeWidth + CELL_PADDING, rowY + rowHeight - 14f, "Additional actions not shown");
            }
        }
    }

    private void drawResourcesSection(PDPageContentStream stream, PDType1Font bold, PDType1Font regular,
                                      float x, float y, float width, float height,
                                      List<Ics201Form.ResourceSummaryEntry> resources) throws IOException {
        drawCell(stream, x, y, width, height);
        drawHeading(stream, bold, x, y + height, "10. Resource Summary");
        float tableTop = y + height - CELL_PADDING - HEADING_FONT_SIZE - 14f;
        float headerHeight = 18f;
        float[] fractions = {0.17f, 0.17f, 0.18f, 0.18f, 0.10f, 0.20f};
        float[] widths = new float[fractions.length];
        float used = 0f;
        for (int i = 0; i < fractions.length; i++) {
            widths[i] = width * fractions[i];
            used += widths[i];
        }
        widths[widths.length - 1] += width - used;
        float cursor = x;
        String[] headings = {"Resource", "Resource Identifier", "Date/Time Ordered", "ETA", "Arrived", "Notes"};
        for (int i = 0; i < headings.length; i++) {
            drawCell(stream, cursor, tableTop - headerHeight, widths[i], headerHeight);
            writeCellText(stream, bold, cursor + CELL_PADDING, tableTop - 12f, headings[i]);
            cursor += widths[i];
        }
        float rowTop = tableTop - headerHeight;
        float rowHeight = 22f;
        int maxRows = Math.max(1, (int) ((rowTop - y - CELL_PADDING) / rowHeight));
        List<Ics201Form.ResourceSummaryEntry> rows = resources == null ? List.of() : resources;
        for (int i = 0; i < maxRows; i++) {
            float rowY = rowTop - ((i + 1) * rowHeight);
            cursor = x;
            for (float cellWidth : widths) {
                drawCell(stream, cursor, rowY, cellWidth, rowHeight);
                cursor += cellWidth;
            }
            if (i < rows.size()) {
                Ics201Form.ResourceSummaryEntry entry = rows.get(i);
                float textY = rowY + rowHeight - 14f;
                writeCellText(stream, regular, x + CELL_PADDING, textY, truncate(entry.getResource(), 18));
                writeCellText(stream, regular, x + widths[0] + CELL_PADDING, textY, truncate(entry.getResourceIdentifier(), 18));
                writeCellText(stream, regular, x + widths[0] + widths[1] + CELL_PADDING, textY, truncate(formatDateTime(entry.getDateTimeOrdered()), 18));
                writeCellText(stream, regular, x + widths[0] + widths[1] + widths[2] + CELL_PADDING, textY, truncate(formatDateTime(entry.getEta()), 18));
                writeCellText(stream, regular, x + widths[0] + widths[1] + widths[2] + widths[3] + CELL_PADDING, textY, entry.isArrived() ? "Yes" : "No");
                writeCellText(stream, regular, x + widths[0] + widths[1] + widths[2] + widths[3] + widths[4] + CELL_PADDING, textY,
                        truncate(entry.getNotes(), 20));
            } else if (i == maxRows - 1 && rows.size() > maxRows) {
                writeCellText(stream, regular, x + widths[0] + widths[1] + widths[2] + widths[3] + widths[4] + CELL_PADDING,
                        rowY + rowHeight - 14f, "Additional resources not shown");
            }
        }
    }

    private void drawPreparedBySection(PDPageContentStream stream, PDType1Font bold, PDType1Font regular,
                                       float x, float y, float width, float height,
                                       Ics201Form form, int pageNumber) throws IOException {
        drawCell(stream, x, y, width, height);
        drawHeading(stream, bold, x, y + height, "6. Prepared by");
        float firstLineY = y + height - CELL_PADDING - HEADING_FONT_SIZE - 16f;
        float secondLineY = firstLineY - 18f;
        drawInlinePair(stream, bold, regular, x + CELL_PADDING, firstLineY, "Name", safe(form.getPreparedByName()));
        drawInlinePair(stream, bold, regular, x + (width * 0.45f), firstLineY, "Position/Title", safe(form.getPreparedByPositionTitle()));
        drawInlinePair(stream, bold, regular, x + CELL_PADDING, secondLineY, "Date/Time", formatDateTime(form.getPreparedDateTime()));
        drawInlinePair(stream, bold, regular, x + (width * 0.45f), secondLineY, "Signature", safe(form.getPreparedBySignature()));
        writeCellText(stream, bold, x + CELL_PADDING, y + 10f, "IAP Page: " + safe(form.getIapPage()));
        float indicatorWidth = bold.getStringWidth("ICS 201, Page " + pageNumber) / 1000f * BODY_FONT_SIZE;
        writeCellText(stream, bold, x + width - indicatorWidth - CELL_PADDING, y + 10f, "ICS 201, Page " + pageNumber);
    }

    private void drawSection(PDPageContentStream stream, PDType1Font bold, PDType1Font regular,
                             float x, float y, float width, float height,
                             String heading, List<String> lines) throws IOException {
        drawCell(stream, x, y, width, height);
        drawHeading(stream, bold, x, y + height, heading);
        writeLines(stream, regular, x + CELL_PADDING, y + height - CELL_PADDING - HEADING_FONT_SIZE - 12f,
                lines == null || lines.isEmpty() ? List.of("") : lines,
                Math.max(1, (int) ((height - 26f) / LINE_HEIGHT)));
    }

    private void drawHeading(PDPageContentStream stream, PDType1Font bold, float x, float topY, String heading) throws IOException {
        stream.beginText();
        stream.setFont(bold, HEADING_FONT_SIZE);
        stream.newLineAtOffset(x + CELL_PADDING, topY - CELL_PADDING - HEADING_FONT_SIZE);
        stream.showText(heading);
        stream.endText();
    }

    private void drawInlinePair(PDPageContentStream stream, PDType1Font bold, PDType1Font regular,
                                float x, float y, String label, String value) throws IOException {
        stream.beginText();
        stream.setFont(bold, BODY_FONT_SIZE);
        stream.newLineAtOffset(x, y);
        stream.showText(label + ": ");
        stream.endText();

        stream.beginText();
        stream.setFont(regular, BODY_FONT_SIZE);
        stream.newLineAtOffset(x + 58f, y);
        stream.showText(safe(value));
        stream.endText();
    }

    private void writeLines(PDPageContentStream stream, PDType1Font font, float x, float startY,
                            List<String> lines, int maxLines) throws IOException {
        stream.beginText();
        stream.setFont(font, BODY_FONT_SIZE);
        stream.newLineAtOffset(x, startY);
        int lineCount = 0;
        for (String line : lines) {
            if (lineCount >= maxLines) {
                stream.showText("Additional content not shown");
                break;
            }
            stream.showText(safe(line));
            stream.newLineAtOffset(0, -LINE_HEIGHT);
            lineCount++;
        }
        stream.endText();
    }

    private void writeCellText(PDPageContentStream stream, PDType1Font font, float x, float y, String value) throws IOException {
        stream.beginText();
        stream.setFont(font, BODY_FONT_SIZE);
        stream.newLineAtOffset(x, y);
        stream.showText(safe(value));
        stream.endText();
    }

    private void drawCell(PDPageContentStream stream, float x, float y, float width, float height) throws IOException {
        stream.addRect(x, y, width, height);
        stream.stroke();
    }

    private List<String> numberedLines(List<String> values) {
        List<String> lines = new ArrayList<>();
        if (values == null) {
            return lines;
        }
        int index = 1;
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                List<String> wrapped = wrap(value, 90);
                if (wrapped.isEmpty()) {
                    continue;
                }
                lines.add(index++ + ". " + wrapped.get(0));
                for (int i = 1; i < wrapped.size(); i++) {
                    lines.add("   " + wrapped.get(i));
                }
            }
        }
        return lines;
    }

    private List<String> organizationLines(OrganizationalChart chart) {
        List<String> lines = new ArrayList<>();
        lines.addAll(wrap("Incident Commander(s): " + String.join(", ", chart.getIncidentCommanders()), 90));
        lines.addAll(wrap("Safety Officer: " + safe(chart.getSafetyOfficerName()), 90));
        lines.addAll(wrap("Public Information Officer: " + safe(chart.getPublicInformationOfficerName()), 90));
        lines.addAll(wrap("Liaison Officer: " + safe(chart.getLiaisonOfficerName()), 90));
        lines.addAll(wrap("Operations Section Chief: " + safe(chart.getOperationsSectionChiefName()), 90));
        lines.addAll(wrap("Planning Section Chief: " + safe(chart.getPlanningSectionChiefName()), 90));
        lines.addAll(wrap("Logistics Section Chief: " + safe(chart.getLogisticsSectionChiefName()), 90));
        lines.addAll(wrap("Finance/Admin Section Chief: " + safe(chart.getFinanceAdminSectionChiefName()), 90));
        return lines;
    }

    private String resolveIncidentName(Ics201Form form, IncidentContext context) {
        String contextName = context == null ? "" : safe(context.getIncidentName());
        return contextName.isBlank() ? safe(form.getIncidentName()) : contextName;
    }

    private String formatDate(LocalDate value) {
        return value == null ? "" : DATE_FORMATTER.format(value);
    }

    private String formatTime(LocalTime value) {
        return value == null ? "" : TIME_FORMATTER.format(value);
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? "" : DATE_TIME_FORMATTER.format(value);
    }

    private String truncate(String value, int maxLength) {
        String safeValue = safe(value);
        return safeValue.length() <= maxLength ? safeValue : safeValue.substring(0, Math.max(0, maxLength - 1)) + "...";
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
