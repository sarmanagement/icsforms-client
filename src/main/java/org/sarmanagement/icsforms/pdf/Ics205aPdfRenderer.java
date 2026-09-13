package org.sarmanagement.icsforms.pdf;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.IncidentContext;
import org.sarmanagement.icsforms.model.ResourceDirectoryEntry;
import org.sarmanagement.icsforms.model.ResourceDirectorySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Structured PDF renderer for the Communications List (ICS 205A) form.
 */
public class Ics205aPdfRenderer extends AbstractPdfRenderer implements PdfFormRenderer {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final float BODY_FONT_SIZE = 10f;
    private static final float HEADING_FONT_SIZE = 10f;
    private static final float CELL_PADDING = 4f;
    private static final float LINE_HEIGHT = 12f;
    private static final float TOP_SECTION_HEIGHT = 62f;
    private static final float FOOTER_HEIGHT = 74f;
    private static final float SECTION_HEADING_HEIGHT = 18f;
    private static final float TABLE_HEADER_HEIGHT = 28f;

    @Override
    public String getFormKey() {
        return "ICS 205A";
    }

    @Override
    public void render(AppData data, Path outputFile) throws IOException {
        Path parent = outputFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        AppData safeData = data == null ? new AppData() : data;
        List<ResourceDirectoryEntry> entries = ResourceDirectorySource.build(safeData).stream()
                .sorted(ResourceDirectorySource.byLastName())
                .toList();
        LocalDateTime preparedAt = LocalDateTime.now();
        try (PDDocument document = new PDDocument()) {
            int rowsPerPage = rowsPerPage();
            int totalPages = Math.max(1, (int) Math.ceil(Math.max(1, entries.size()) / (double) rowsPerPage));
            for (int pageIndex = 0; pageIndex < totalPages; pageIndex++) {
                int start = pageIndex * rowsPerPage;
                int end = Math.min(entries.size(), start + rowsPerPage);
                List<ResourceDirectoryEntry> pageEntries = start < end ? entries.subList(start, end) : List.of();
                renderPage(document, safeData, pageEntries, pageIndex + 1, totalPages, preparedAt, rowsPerPage);
            }
            document.save(outputFile.toFile());
        }
    }

    private void renderPage(PDDocument document, AppData data, List<ResourceDirectoryEntry> entries,
                            int pageNumber, int totalPages, LocalDateTime preparedAt,
                            int rowsPerPage) throws IOException {
        IncidentContext context = data.getIncidentContext() == null ? new IncidentContext() : data.getIncidentContext();
        PDPage page = new PDPage(PDRectangle.LETTER);
        document.addPage(page);
        try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
            PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            FormLayout layout = formLayout(page);

            drawFormHeader(stream, bold, layout, "ICS 205A", "COMMUNICATIONS LIST");
            drawFormFrame(stream, layout);

            float y = layout.top();
            float halfWidth = layout.width() / 2f;
            drawHorizontalLine(stream, layout.x(), layout.x() + layout.width(), y - TOP_SECTION_HEIGHT);
            drawVerticalLine(stream, layout.x() + halfWidth, y - TOP_SECTION_HEIGHT, y);
            drawSimpleSection(stream, bold, regular, layout.x(), y - TOP_SECTION_HEIGHT, halfWidth, TOP_SECTION_HEIGHT,
                    "1. Incident Name", List.of(safe(context.getIncidentName())));
            drawOperationalPeriodSection(stream, bold, regular,
                    layout.x() + halfWidth, y - TOP_SECTION_HEIGHT, halfWidth, TOP_SECTION_HEIGHT, context);
            y -= TOP_SECTION_HEIGHT;

            float tableSectionHeight = layout.height() - TOP_SECTION_HEIGHT - FOOTER_HEIGHT;
            drawHorizontalLine(stream, layout.x(), layout.x() + layout.width(), y - tableSectionHeight);
            drawCommunicationsSection(stream, bold, regular, layout.x(), y - tableSectionHeight,
                    layout.width(), tableSectionHeight, entries, rowsPerPage);
            y -= tableSectionHeight;

            drawPreparedBySection(stream, bold, regular, layout.x(), y - FOOTER_HEIGHT, layout.width(),
                    FOOTER_HEIGHT, context, preparedAt, pageNumber, totalPages);
        }
    }

    private int rowsPerPage() {
        float tableSectionHeight = expandRowToFill(706f, 1, TOP_SECTION_HEIGHT, 420f, FOOTER_HEIGHT)[1];
        float dataHeight = tableSectionHeight - SECTION_HEADING_HEIGHT - TABLE_HEADER_HEIGHT;
        return Math.max(1, (int) (dataHeight / 24f));
    }

    private void drawOperationalPeriodSection(PDPageContentStream stream, PDType1Font bold, PDType1Font regular,
                                              float x, float y, float width, float height,
                                              IncidentContext context) throws IOException {
        drawHeading(stream, bold, x, y + height, "2. Operational Period");
        float labelY = y + height - CELL_PADDING - HEADING_FONT_SIZE - 14f;
        drawInlinePair(stream, bold, regular, x + CELL_PADDING, labelY, "Date From", formatDate(context.getOperationalPeriodStart()));
        drawInlinePair(stream, bold, regular, x + (width / 2f), labelY, "Date To", formatDate(context.getOperationalPeriodEnd()));
        drawInlinePair(stream, bold, regular, x + CELL_PADDING, labelY - 18f, "Time From", formatTime(context.getOperationalPeriodStart()));
        drawInlinePair(stream, bold, regular, x + (width / 2f), labelY - 18f, "Time To", formatTime(context.getOperationalPeriodEnd()));
    }

    private void drawCommunicationsSection(PDPageContentStream stream, PDType1Font bold, PDType1Font regular,
                                           float x, float y, float width, float height,
                                           List<ResourceDirectoryEntry> entries, int rowsPerPage) throws IOException {
        // The shared directory model carries extra directory-only fields such as unit, state,
        // current assignment, and status. ICS 205A section 3 intentionally renders only the
        // assigned position, person name, and contact methods required by the form itself.
        drawHeading(stream, bold, x, y + height, "3. Basic Local Communications Information");
        float tableTop = y + height - SECTION_HEADING_HEIGHT;
        float headerBottom = tableTop - TABLE_HEADER_HEIGHT;
        drawHorizontalLine(stream, x, x + width, tableTop);
        drawHorizontalLine(stream, x, x + width, headerBottom);

        float[] widths = {0.28f, 0.27f, 0.45f};
        float[] starts = columnStarts(x, width, widths);
        drawVerticalLine(stream, starts[1], y, tableTop);
        drawVerticalLine(stream, starts[2], y, tableTop);

        writeWrappedCellText(stream, bold, starts[0], headerBottom, width * widths[0], TABLE_HEADER_HEIGHT,
                List.of("Incident Assigned Position"));
        writeWrappedCellText(stream, bold, starts[1], headerBottom, width * widths[1], TABLE_HEADER_HEIGHT,
                List.of("Name"));
        writeWrappedCellText(stream, bold, starts[2], headerBottom, width * widths[2], TABLE_HEADER_HEIGHT,
                List.of("Method(s) of Contact"));

        float rowHeight = (headerBottom - y) / rowsPerPage;
        for (int rowIndex = 0; rowIndex < rowsPerPage; rowIndex++) {
            float rowTop = headerBottom - (rowIndex * rowHeight);
            float rowBottom = Math.max(y, rowTop - rowHeight);
            drawHorizontalLine(stream, x, x + width, rowBottom);
            if (rowIndex >= entries.size()) {
                continue;
            }
            ResourceDirectoryEntry entry = entries.get(rowIndex);
            writeWrappedCellText(stream, regular, starts[0], rowBottom, width * widths[0], rowTop - rowBottom,
                    wrap(safe(entry.assignedPosition()), 18));
            writeWrappedCellText(stream, regular, starts[1], rowBottom, width * widths[1], rowTop - rowBottom,
                    wrap(safe(entry.name()), 18));
            writeWrappedCellText(stream, regular, starts[2], rowBottom, width * widths[2], rowTop - rowBottom,
                    wrap(safe(entry.contactMethods()), 30));
        }
    }

    private void drawPreparedBySection(PDPageContentStream stream, PDType1Font bold, PDType1Font regular,
                                       float x, float y, float width, float height,
                                       IncidentContext context, LocalDateTime preparedAt,
                                       int pageNumber, int totalPages) throws IOException {
        drawCell(stream, x, y, width, height);
        drawHeading(stream, bold, x, y + height, "4. Prepared By");

        float[] widths = {0.28f, 0.28f, 0.18f, 0.26f};
        float[] starts = columnStarts(x, width, widths);
        drawVerticalLine(stream, starts[1], y, y + height);
        drawVerticalLine(stream, starts[2], y, y + height);
        drawVerticalLine(stream, starts[3], y, y + height);

        float lineY = y + height - CELL_PADDING - HEADING_FONT_SIZE - 14f;
        drawInlinePair(stream, bold, regular, starts[0] + CELL_PADDING, lineY, "Name", safe(context.getCurrentUser()));
        drawInlinePair(stream, bold, regular, starts[1] + CELL_PADDING, lineY, "Position/Title",
                safe(context.getCurrentUserPositionTitle()));
        drawInlinePair(stream, bold, regular, starts[2] + CELL_PADDING, lineY, "Signature", "");
        drawInlinePair(stream, bold, regular, starts[3] + CELL_PADDING, lineY, "Date/Time", formatDateTime(preparedAt));

        if (totalPages > 1) {
            stream.beginText();
            stream.setFont(regular, 9f);
            stream.newLineAtOffset(x + width - 70f, y + CELL_PADDING + 2f);
            stream.showText("Page " + pageNumber + " of " + totalPages);
            stream.endText();
        }
    }

    private void drawSimpleSection(PDPageContentStream stream, PDType1Font bold, PDType1Font regular,
                                   float x, float y, float width, float height,
                                   String heading, List<String> lines) throws IOException {
        drawHeading(stream, bold, x, y + height, heading);
        writeLines(stream, regular, x + CELL_PADDING, y + height - CELL_PADDING - HEADING_FONT_SIZE - 12f,
                lines == null || lines.isEmpty() ? List.of("") : lines);
    }

    private void drawHeading(PDPageContentStream stream, PDType1Font bold,
                             float x, float topY, String heading) throws IOException {
        stream.beginText();
        stream.setFont(bold, HEADING_FONT_SIZE);
        stream.newLineAtOffset(x + CELL_PADDING, topY - CELL_PADDING - HEADING_FONT_SIZE);
        stream.showText(heading);
        stream.endText();
    }

    private void drawInlinePair(PDPageContentStream stream, PDType1Font bold, PDType1Font regular,
                                float x, float y, String label, String value) throws IOException {
        writeInlineHeadingValue(stream, bold, regular, x, y, label + ":", value);
    }

    private void writeInlineHeadingValue(PDPageContentStream stream, PDType1Font bold, PDType1Font regular,
                                         float x, float y, String label, String value) throws IOException {
        stream.beginText();
        stream.setFont(bold, BODY_FONT_SIZE);
        stream.newLineAtOffset(x, y);
        stream.showText(label);
        float labelWidth = bold.getStringWidth(label) / 1000f * BODY_FONT_SIZE;
        stream.setFont(regular, BODY_FONT_SIZE);
        stream.newLineAtOffset(labelWidth + 4f, 0);
        stream.showText(safe(value));
        stream.endText();
    }

    private void writeLines(PDPageContentStream stream, PDType1Font regular,
                            float x, float y, List<String> lines) throws IOException {
        stream.beginText();
        stream.setFont(regular, BODY_FONT_SIZE);
        stream.newLineAtOffset(x, y);
        for (String line : lines) {
            stream.showText(safe(line));
            stream.newLineAtOffset(0, -LINE_HEIGHT);
        }
        stream.endText();
    }

    private void writeWrappedCellText(PDPageContentStream stream, PDType1Font font,
                                      float x, float y, float width, float height,
                                      List<String> lines) throws IOException {
        float top = y + height;
        float textY = top - CELL_PADDING - BODY_FONT_SIZE;
        stream.beginText();
        stream.setFont(font, BODY_FONT_SIZE);
        stream.newLineAtOffset(x + CELL_PADDING, textY);
        int maxLines = Math.max(1, (int) ((height - (CELL_PADDING * 2f)) / LINE_HEIGHT));
        for (int i = 0; i < lines.size() && i < maxLines; i++) {
            stream.showText(safe(lines.get(i)));
            if (i < lines.size() - 1 && i + 1 < maxLines) {
                stream.newLineAtOffset(0, -LINE_HEIGHT);
            }
        }
        stream.endText();
    }

    private float[] columnStarts(float x, float width, float[] widths) {
        float[] starts = new float[widths.length];
        float current = x;
        for (int i = 0; i < widths.length; i++) {
            starts[i] = current;
            current += width * widths[i];
        }
        return starts;
    }

    private void drawHorizontalLine(PDPageContentStream stream, float x1, float x2, float y) throws IOException {
        stream.moveTo(x1, y);
        stream.lineTo(x2, y);
        stream.stroke();
    }

    private void drawVerticalLine(PDPageContentStream stream, float x, float y1, float y2) throws IOException {
        stream.moveTo(x, y1);
        stream.lineTo(x, y2);
        stream.stroke();
    }

    private void drawCell(PDPageContentStream stream, float x, float y, float width, float height) throws IOException {
        stream.addRect(x, y, width, height);
        stream.stroke();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String formatDate(LocalDateTime value) {
        return value == null ? "" : value.format(DATE_FORMATTER);
    }

    private String formatTime(LocalDateTime value) {
        return value == null ? "" : value.format(TIME_FORMATTER);
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? "" : value.format(DATE_TIME_FORMATTER);
    }
}
