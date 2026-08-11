package org.sarmanagement.icsforms.pdf;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.sarmanagement.icsforms.model.ActivityEventType;
import org.sarmanagement.icsforms.model.ActivityLogEntry;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.Ics214Form;
import org.sarmanagement.icsforms.model.IncidentContext;
import org.sarmanagement.icsforms.model.SarTaskResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Structured PDF renderer for the Activity Log (ICS 214) form.
 */
public class Ics214PdfRenderer extends AbstractPdfRenderer implements PdfFormRenderer {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final float BODY_FONT_SIZE = 10f;
    private static final float HEADING_FONT_SIZE = 10f;
    private static final float LINE_HEIGHT = 12f;
    private static final float CELL_PADDING = 4f;
    private static final int RESOURCE_ROW_COUNT = 4;

    /** {@inheritDoc} */
    @Override
    public String getFormKey() {
        return "ICS 214";
    }

    /** {@inheritDoc} */
    @Override
    public void render(AppData data, Path outputFile) throws IOException {
        Files.createDirectories(outputFile.getParent());
        try (PDDocument document = new PDDocument()) {
            renderDocument(document, data == null ? new AppData() : data);
            document.save(outputFile.toFile());
        }
    }

    private void renderDocument(PDDocument document, AppData data) throws IOException {
        IncidentContext context = data.getIncidentContext() == null ? new IncidentContext() : data.getIncidentContext();
        List<ActivityEventType> eventTypes = data.getActivityEventTypes().isEmpty()
                ? ActivityEventType.defaultTypes() : data.getActivityEventTypes();
        List<Ics214Form> logs = data.getActivityLogs().isEmpty()
                ? List.of(new Ics214Form()) : data.getActivityLogs();
        for (Ics214Form form : logs) {
            int rowsPerPage = activityRowsPerPage();
            int totalPages = Math.max(1, (int) Math.ceil(Math.max(1, form.getActivityLog().size()) / (double) rowsPerPage));
            for (int pageIndex = 0; pageIndex < totalPages; pageIndex++) {
                int startIndex = pageIndex * rowsPerPage;
                int endIndex = Math.min(form.getActivityLog().size(), startIndex + rowsPerPage);
                renderPage(document, context, form, eventTypes, form.getActivityLog().subList(startIndex, endIndex), pageIndex + 1, totalPages);
            }
        }
    }

    private void renderPage(PDDocument document, IncidentContext context, Ics214Form form,
                            List<ActivityEventType> eventTypes, List<ActivityLogEntry> entries,
                            int pageNumber, int totalPages) throws IOException {
        PDPage page = new PDPage(PDRectangle.LETTER);
        document.addPage(page);
        try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
            PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            FormLayout layout = formLayout(page);

            drawFormHeader(stream, bold, layout, "ICS 214", "ACTIVITY LOG");
            drawFormFrame(stream, layout);

            float gridTop = layout.top();
            float pageWidth = layout.width();
            float[] rows = expandRowToFill(layout.height(), 3, 60f, 62f, 92f, 320f, 84f);
            float row1 = rows[0];
            float row2 = rows[1];
            float row3 = rows[2];
            float row4 = rows[3];
            float row5 = rows[4];

            float y = gridTop;
            float halfWidth = pageWidth / 2f;
            float thirdWidth = pageWidth / 3f;

            drawHorizontalLine(stream, layout.x(), layout.x() + pageWidth, y - row1);
            drawVerticalLine(stream, layout.x() + halfWidth, y - row1, y);
            drawSimpleSection(stream, bold, regular, layout.x(), y - row1, halfWidth, row1,
                    "1. Incident Name", List.of(safe(context.getIncidentName())));
            drawOperationalPeriodSection(stream, bold, regular, layout.x() + halfWidth, y - row1, halfWidth, row1, context);
            y -= row1;

            drawHorizontalLine(stream, layout.x(), layout.x() + pageWidth, y - row2);
            drawVerticalLine(stream, layout.x() + thirdWidth, y - row2, y);
            drawVerticalLine(stream, layout.x() + (thirdWidth * 2f), y - row2, y);
            drawSimpleSection(stream, bold, regular, layout.x(), y - row2, thirdWidth, row2,
                    "3. Name", List.of(safe(form.getName())));
            drawSimpleSection(stream, bold, regular, layout.x() + thirdWidth, y - row2, thirdWidth, row2,
                    "4. ICS Position", List.of(safe(form.getIcsPosition())));
            drawSimpleSection(stream, bold, regular, layout.x() + (thirdWidth * 2f), y - row2, thirdWidth, row2,
                    "5. Home Agency", List.of(safe(form.getHomeAgency())));
            y -= row2;

            drawHorizontalLine(stream, layout.x(), layout.x() + pageWidth, y - row3);
            drawResourcesSection(stream, bold, regular, layout.x(), y - row3, pageWidth, row3, form.getResourcesAssigned());
            y -= row3;

            drawHorizontalLine(stream, layout.x(), layout.x() + pageWidth, y - row4);
            drawActivityLogSection(stream, bold, regular, layout.x(), y - row4, pageWidth, row4, entries, eventTypes);
            y -= row4;

            drawPreparedBySection(stream, bold, regular, layout.x(), y - row5, pageWidth, row5, 24f, form, pageNumber, totalPages);
        }
    }

    private void drawOperationalPeriodSection(PDPageContentStream stream, PDType1Font bold, PDType1Font regular,
                                              float x, float y, float width, float height, IncidentContext context) throws IOException {
        drawHeading(stream, bold, x, y + height, "2. Operational Period");
        float labelY = y + height - CELL_PADDING - HEADING_FONT_SIZE - 14f;
        drawInlinePair(stream, bold, regular, x + CELL_PADDING, labelY, "Date From", formatDate(context.getOperationalPeriodStart()));
        drawInlinePair(stream, bold, regular, x + (width / 2f), labelY, "Date To", formatDate(context.getOperationalPeriodEnd()));
        drawInlinePair(stream, bold, regular, x + CELL_PADDING, labelY - 18f, "Time From", formatTime(context.getOperationalPeriodStart()));
        drawInlinePair(stream, bold, regular, x + (width / 2f), labelY - 18f, "Time To", formatTime(context.getOperationalPeriodEnd()));
    }

    private void drawResourcesSection(PDPageContentStream stream, PDType1Font bold, PDType1Font regular,
                                      float x, float y, float width, float height, List<SarTaskResource> resources) throws IOException {
        drawHeading(stream, bold, x, y + height, "6. Resources Assigned");
        float tableTop = y + height - 18f;
        float headerHeight = 24f;
        float headerBottom = tableTop - headerHeight;
        float rowHeight = (headerBottom - y) / RESOURCE_ROW_COUNT;
        drawHorizontalLine(stream, x, x + width, tableTop);
        drawHorizontalLine(stream, x, x + width, headerBottom);

        float[] widths = {0.36f, 0.32f, 0.32f};
        float[] starts = columnStarts(x, width, widths);
        drawVerticalLine(stream, starts[1], y, tableTop);
        drawVerticalLine(stream, starts[2], y, tableTop);

        writeWrappedCellText(stream, bold, starts[0], headerBottom, width * widths[0], headerHeight, List.of("Name"));
        writeWrappedCellText(stream, bold, starts[1], headerBottom, width * widths[1], headerHeight, List.of("ICS Position"));
        writeWrappedCellText(stream, bold, starts[2], headerBottom, width * widths[2], headerHeight, List.of("Home Agency"));

        int visibleCount = Math.min(resources == null ? 0 : resources.size(), RESOURCE_ROW_COUNT);
        for (int rowIndex = 0; rowIndex < RESOURCE_ROW_COUNT; rowIndex++) {
            float rowTop = headerBottom - (rowIndex * rowHeight);
            float rowBottom = Math.max(y, rowTop - rowHeight);
            drawHorizontalLine(stream, x, x + width, rowBottom);
            if (resources == null || rowIndex >= visibleCount) {
                continue;
            }
            SarTaskResource resource = resources.get(rowIndex);
            writeWrappedCellText(stream, regular, starts[0], rowBottom, width * widths[0], rowTop - rowBottom, wrap(safe(resource.getName()), 22));
            writeWrappedCellText(stream, regular, starts[1], rowBottom, width * widths[1], rowTop - rowBottom, wrap(safe(resource.getIcsPosition()), 18));
            writeWrappedCellText(stream, regular, starts[2], rowBottom, width * widths[2], rowTop - rowBottom,
                    wrap(rowIndex == RESOURCE_ROW_COUNT - 1 && resources.size() > RESOURCE_ROW_COUNT ? "See next page" : safe(resource.getHomeAgency()), 18));
        }
    }

    private void drawActivityLogSection(PDPageContentStream stream, PDType1Font bold, PDType1Font regular,
                                        float x, float y, float width, float height, List<ActivityLogEntry> entries,
                                        List<ActivityEventType> eventTypes) throws IOException {
        drawHeading(stream, bold, x, y + height, "7. Activity Log");
        float tableTop = y + height - 18f;
        float headerHeight = 24f;
        float headerBottom = tableTop - headerHeight;
        float dateTimeWidth = width * 0.28f;
        float activityWidth = width - dateTimeWidth;
        float rowHeight = (headerBottom - y) / activityRowsPerPage();
        drawHorizontalLine(stream, x, x + width, tableTop);
        drawHorizontalLine(stream, x, x + width, headerBottom);
        drawVerticalLine(stream, x + dateTimeWidth, y, tableTop);

        writeWrappedCellText(stream, bold, x, headerBottom, dateTimeWidth, headerHeight, List.of("Date/Time"));
        writeWrappedCellText(stream, bold, x + dateTimeWidth, headerBottom, activityWidth, headerHeight, List.of("Notable Activities"));

        for (int rowIndex = 0; rowIndex < activityRowsPerPage(); rowIndex++) {
            float rowTop = headerBottom - (rowIndex * rowHeight);
            float rowBottom = Math.max(y, rowTop - rowHeight);
            drawHorizontalLine(stream, x, x + width, rowBottom);
            if (entries == null || rowIndex >= entries.size()) {
                continue;
            }
            ActivityLogEntry entry = entries.get(rowIndex);
            writeWrappedCellText(stream, regular, x, rowBottom, dateTimeWidth, rowTop - rowBottom,
                    wrap(formatDateTime(entry.getTimestamp()), 14));
            writeWrappedCellText(stream, regular, x + dateTimeWidth, rowBottom, activityWidth, rowTop - rowBottom,
                    wrap(activityText(entry, eventTypes), 48));
        }
    }

    private void drawPreparedBySection(PDPageContentStream stream, PDType1Font bold, PDType1Font regular,
                                       float x, float y, float width, float height, float footerHeight, Ics214Form form,
                                       int pageNumber, int totalPages) throws IOException {
        drawCell(stream, x, y, width, height);
        float footerCellWidth = width / 8f;
        float topRowY = y + height - CELL_PADDING - HEADING_FONT_SIZE - LINE_HEIGHT;
        float middleX = x + (width * 0.34f);
        float rightX = x + (width * 0.68f);
        float footerContentY = y + footerHeight - CELL_PADDING - BODY_FONT_SIZE;

        drawHeading(stream, bold, x, y + height, "8. Prepared By");
        drawCell(stream, x, y, footerCellWidth, footerHeight);
        drawInlinePair(stream, bold, regular, x + CELL_PADDING, topRowY, "Name", safe(form.getPreparedByName()));
        drawInlinePair(stream, bold, regular, middleX, topRowY, "Position/Title", safe(form.getPreparedByPositionTitle()));
        drawInlinePair(stream, bold, regular, rightX, topRowY, "Signature", safe(form.getPreparedBySignature()));
        writeInlineHeadingValue(stream, bold, regular, x + CELL_PADDING, footerContentY, "ICS 214", "");
        drawInlinePair(stream, bold, regular, x + footerCellWidth + CELL_PADDING, footerContentY, "Date/Time", formatDateTime(form.getPreparedDateTime()));
        writeInlineHeadingValue(stream, bold, regular, x + width - 118f, footerContentY, "Page", pageNumber + " of " + totalPages);
    }

    private void drawSimpleSection(PDPageContentStream stream, PDType1Font bold, PDType1Font regular,
                                   float x, float y, float width, float height, String heading, List<String> lines) throws IOException {
        drawHeading(stream, bold, x, y + height, heading);
        writeLines(stream, regular, x + CELL_PADDING, y + height - CELL_PADDING - HEADING_FONT_SIZE - 12f,
                lines == null || lines.isEmpty() ? List.of("") : lines);
    }

    private int activityRowsPerPage() {
        float sectionHeight = expandRowToFill(706f, 3, 60f, 62f, 92f, 320f, 84f)[3];
        float tableHeight = sectionHeight - 18f - 24f;
        return Math.max(1, (int) (tableHeight / 36f));
    }

    private String activityText(ActivityLogEntry entry, List<ActivityEventType> eventTypes) {
        List<String> parts = new ArrayList<>();
        String typeId = entry.getEventTypeId() == null ? ActivityEventType.ID_FREE_TEXT : entry.getEventTypeId();
        if (!ActivityEventType.ID_FREE_TEXT.equals(typeId)) {
            String label = eventTypes.stream()
                    .filter(t -> typeId.equals(t.getId()))
                    .map(ActivityEventType::getLabel)
                    .findFirst()
                    .orElse(typeId);
            parts.add(label);
        }
        if (!safe(entry.getResourceIdentifier()).isBlank()) {
            parts.add("Resource: " + safe(entry.getResourceIdentifier()));
        }
        if (!safe(entry.getNotableActivity()).isBlank()) {
            parts.add(safe(entry.getNotableActivity()));
        }
        return parts.isEmpty() ? "" : String.join(" - ", parts);
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
        String safeLabel = safe(label);
        if (!safeLabel.isBlank()) {
            stream.beginText();
            stream.setFont(bold, BODY_FONT_SIZE);
            stream.newLineAtOffset(x, y);
            stream.showText(safeLabel + ":");
            stream.endText();
        }

        stream.beginText();
        stream.setFont(regular, BODY_FONT_SIZE);
        stream.newLineAtOffset(x + (safeLabel.isBlank() ? 0f : (bold.getStringWidth(safeLabel + ":") / 1000f * BODY_FONT_SIZE) + 4f), y);
        stream.showText(safe(value));
        stream.endText();
    }

    private void writeInlineHeadingValue(PDPageContentStream stream, PDType1Font bold, PDType1Font regular,
                                         float x, float y, String heading, String value) throws IOException {
        stream.beginText();
        stream.setFont(bold, BODY_FONT_SIZE);
        stream.newLineAtOffset(x, y);
        stream.showText(heading + (safe(value).isBlank() ? "" : ": " + safe(value)));
        stream.endText();
    }

    private void writeWrappedCellText(PDPageContentStream stream, PDType1Font font,
                                      float x, float bottomY, float width, float height, List<String> lines) throws IOException {
        int maxLines = Math.max(1, (int) ((height - (CELL_PADDING * 2)) / LINE_HEIGHT));
        List<String> visible = lines == null || lines.isEmpty() ? List.of("") : lines.subList(0, Math.min(maxLines, lines.size()));
        stream.beginText();
        stream.setFont(font, BODY_FONT_SIZE);
        stream.newLineAtOffset(x + CELL_PADDING, bottomY + height - CELL_PADDING - BODY_FONT_SIZE);
        for (String line : visible) {
            stream.showText(safe(line));
            stream.newLineAtOffset(0, -LINE_HEIGHT);
        }
        stream.endText();
    }

    private void writeLines(PDPageContentStream stream, PDType1Font regular, float x, float startY, List<String> lines) throws IOException {
        stream.beginText();
        stream.setFont(regular, BODY_FONT_SIZE);
        stream.newLineAtOffset(x, startY);
        for (String line : lines) {
            stream.showText(safe(line));
            stream.newLineAtOffset(0, -LINE_HEIGHT);
        }
        stream.endText();
    }

    private void drawCell(PDPageContentStream stream, float x, float y, float width, float height) throws IOException {
        stream.addRect(x, y, width, height);
        stream.stroke();
    }

    private void drawHorizontalLine(PDPageContentStream stream, float startX, float endX, float y) throws IOException {
        stream.moveTo(startX, y);
        stream.lineTo(endX, y);
        stream.stroke();
    }

    private void drawVerticalLine(PDPageContentStream stream, float x, float startY, float endY) throws IOException {
        stream.moveTo(x, startY);
        stream.lineTo(x, endY);
        stream.stroke();
    }

    private float[] columnStarts(float x, float width, float[] widths) {
        float[] starts = new float[widths.length];
        float currentX = x;
        for (int i = 0; i < widths.length; i++) {
            starts[i] = currentX;
            currentX += width * widths[i];
        }
        return starts;
    }

    private String formatDate(LocalDateTime value) {
        return value == null ? "" : DATE_FORMATTER.format(value);
    }

    private String formatTime(LocalDateTime value) {
        return value == null ? "" : TIME_FORMATTER.format(value);
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? "" : DATE_TIME_FORMATTER.format(value);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
