package org.sarmanagement.icsforms.pdf;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.CommunicationEntry;
import org.sarmanagement.icsforms.model.Ics204Form;
import org.sarmanagement.icsforms.model.IncidentContext;
import org.sarmanagement.icsforms.model.ResourceAssignment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Structured PDF renderer for the Assignment List (ICS 204) form.
 */
public class Ics204PdfRenderer extends AbstractPdfRenderer implements PdfFormRenderer {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final float BODY_FONT_SIZE = 10f;
    private static final float HEADING_FONT_SIZE = 10f;
    private static final float LINE_HEIGHT = 12f;
    private static final float CELL_PADDING = 4f;
    private static final int RESOURCE_ROW_COUNT = 9;

    /** {@inheritDoc} */
    @Override
    public String getFormKey() {
        return "ICS 204";
    }

    /** {@inheritDoc} */
    @Override
    public void render(AppData data, Path outputFile) throws IOException {
        Files.createDirectories(outputFile.getParent());
        try (PDDocument document = new PDDocument()) {
            renderDocument(document, data);
            document.save(outputFile.toFile());
        }
    }

    private void renderDocument(PDDocument document, AppData data) throws IOException {
        IncidentContext context = data.getIncidentContext();
        Ics204Form form = data.getForm204();
        List<OverflowSection> overflowSections = new ArrayList<>();

        PDPage page = new PDPage(PDRectangle.LETTER);
        document.addPage(page);
        try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
            PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            FormLayout layout = formLayout(page);

            drawFormHeader(stream, bold, layout, "ICS 204", "ASSIGNMENT LIST");
            drawFormFrame(stream, layout);
            float gridTop = layout.top();
            float gridBottom = layout.y();
            float gridHeight = layout.height();
            float pageWidth = layout.width();

            float[] rows = expandRowToFill(gridHeight, 3, 60f, 64f, 282f, 108f, 120f, 72f);
            float row1 = rows[0];
            float row2 = rows[1];
            float row5 = rows[2];
            float row6 = rows[3];
            float row7 = rows[4];
            float row8 = rows[5];

            float y = gridTop;
            float halfWidth = pageWidth / 2f;
            float operationsWidth = pageWidth * 0.8f;

            drawHorizontalLine(stream, layout.x(), layout.x() + pageWidth, y - row1);
            drawVerticalLine(stream, layout.x() + halfWidth, y - row1, y);
            drawSection(stream, bold, regular, layout.x(), y - row1, halfWidth, row1,
                    "1. Incident Name", List.of(safe(context.getIncidentName())), null);
            drawOperationalPeriodSection(stream, bold, regular, layout.x() + halfWidth, y - row1, halfWidth, row1, context);
            y -= row1;

            drawHorizontalLine(stream, layout.x(), layout.x() + pageWidth, y - row2);
            drawVerticalLine(stream, layout.x() + operationsWidth, y - row2, y);
            drawManagementSection(stream, bold, regular, layout.x(), y - row2, operationsWidth, row2, form);
            drawAssignmentContextSection(stream, bold, regular, layout.x() + operationsWidth, y - row2,
                    pageWidth - operationsWidth, row2, form);
            y -= row2;

            drawHorizontalLine(stream, layout.x(), layout.x() + pageWidth, y - row5);
            overflowSections.addAll(drawResourcesSection(stream, bold, regular, layout.x(), y - row5, pageWidth, row5, form));
            y -= row5;

            drawHorizontalLine(stream, layout.x(), layout.x() + pageWidth, y - row6);
            overflowSections.addAll(drawSection(stream, bold, regular, layout.x(), y - row6, pageWidth, row6,
                    "6. Work Assignment", workAssignmentLines(form), "6. Work Assignment"));
            y -= row6;

            drawHorizontalLine(stream, layout.x(), layout.x() + pageWidth, y - row7);
            drawVerticalLine(stream, layout.x() + halfWidth, y - row7, y);
            overflowSections.addAll(drawSection(stream, bold, regular, layout.x(), y - row7, halfWidth, row7,
                    "7. Special Instructions", wrap(form.getSpecialInstructions(), 42), "7. Special Instructions"));
            overflowSections.addAll(drawSection(stream, bold, regular, layout.x() + halfWidth, y - row7, halfWidth, row7,
                    "8. Communications", communicationLines(form.getCommunications()), "8. Communications"));
            y -= row7;

            drawPreparedBySection(stream, bold, regular, layout.x(), y - row8, pageWidth, row8, 24f, form);
        }

        for (OverflowSection overflow : overflowSections) {
            renderOverflowPage(document, overflow);
        }
    }

    private void renderOverflowPage(PDDocument document, OverflowSection overflow) throws IOException {
        PDPage page = new PDPage(PDRectangle.LETTER);
        document.addPage(page);
        try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
            PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            FormLayout layout = formLayout(page);
            drawFormHeader(stream, bold, layout, "ICS 204", overflow.heading);
            drawFormFrame(stream, layout);
            drawTextBlock(stream, bold, regular, layout.x(), layout.y(), layout.width(), layout.height(), overflow.heading, overflow.lines);
        }
    }

    private void drawAssignmentContextSection(PDPageContentStream stream, PDType1Font bold, PDType1Font regular,
                                              float x, float y, float width, float height, Ics204Form form) throws IOException {
        String heading = "3. " + form.getSelectedContextHeading();
        List<String> lines = wrap(form.getSelectedContextValue(), Math.max(10, (int) (width / 6f)));
        if (lines.isEmpty()) {
            lines = List.of("");
        }
        drawSection(stream, bold, regular, x, y, width, height, heading, lines, null);
    }

    private void drawManagementSection(PDPageContentStream stream, PDType1Font bold, PDType1Font regular,
                                       float x, float y, float width, float height, Ics204Form form) throws IOException {
        drawHeading(stream, bold, x, y + height, "4. Operations Personnel");
        float firstLineY = y + height - CELL_PADDING - HEADING_FONT_SIZE - 14f;
        drawInlinePair(stream, bold, regular, x + CELL_PADDING, firstLineY,
                "Operations Section Chief", safe(form.getOperationsSectionChiefName()));
        drawInlinePair(stream, bold, regular, x + (width * 0.58f), firstLineY,
                "Contact", safe(form.getOperationsSectionChiefContact()));
        if (!safe(form.getSecondaryManagementRoleLabel()).isBlank()) {
            float secondLineY = firstLineY - (LINE_HEIGHT + 4f);
            drawInlinePair(stream, bold, regular, x + CELL_PADDING, secondLineY,
                    form.getSecondaryManagementRoleLabel(), safe(form.getSecondaryManagementName()));
            drawInlinePair(stream, bold, regular, x + (width * 0.58f), secondLineY,
                    "Contact", safe(form.getSecondaryManagementContact()));
        }
    }

    private List<OverflowSection> drawResourcesSection(PDPageContentStream stream, PDType1Font bold, PDType1Font regular,
                                                       float x, float y, float width, float height, Ics204Form form) throws IOException {
        List<OverflowSection> overflowSections = new ArrayList<>();
        drawHeading(stream, bold, x, y + height, "5. Resources Assigned");
        float tableTop = y + height - 18f;
        float headerHeight = 42f;
        float headerBottom = tableTop - headerHeight;
        float rowHeight = (headerBottom - y) / RESOURCE_ROW_COUNT;
        drawHorizontalLine(stream, x, x + width, tableTop);
        drawHorizontalLine(stream, x, x + width, headerBottom);

        float[] widths = {4f / 18f, 4f / 18f, 1f / 18f, 5f / 18f, 4f / 18f};
        float currentX = x;
        for (int i = 0; i < widths.length - 1; i++) {
            currentX += width * widths[i];
            drawVerticalLine(stream, currentX, y, tableTop);
        }

        String[] headings = {
                "Resource Identifier",
                "Leader",
                "# of Persons",
                "Contact",
                "Reporting Location / Special Equipment / Remarks"
        };

        float[] starts = columnStarts(x, width, widths);
        for (int i = 0; i < headings.length; i++) {
            writeWrappedCellText(stream, bold, starts[i], headerBottom, width * widths[i], headerHeight,
                    wrap(headings[i], widths[i] <= (1f / 18f) ? 10 : widths[i] <= (4f / 18f) ? 18 : 24));
        }

        List<ResourceAssignment> resources = form.getResourcesAssigned();
        int visibleCount = Math.min(resources.size(), RESOURCE_ROW_COUNT);
        for (int rowIndex = 0; rowIndex < RESOURCE_ROW_COUNT; rowIndex++) {
            float rowTop = headerBottom - (rowIndex * rowHeight);
            float rowBottom = Math.max(y, rowTop - rowHeight);
            drawHorizontalLine(stream, x, x + width, rowBottom);

            if (rowIndex >= visibleCount) {
                continue;
            }

            ResourceAssignment resource = resources.get(rowIndex);
            List<List<String>> columns = resourceColumns(resource, rowIndex == (visibleCount - 1) && resources.size() > RESOURCE_ROW_COUNT);
            for (int col = 0; col < columns.size(); col++) {
                writeWrappedCellText(stream, regular, starts[col], rowBottom, width * widths[col], rowTop - rowBottom, columns.get(col));
            }
        }

        if (resources.size() > RESOURCE_ROW_COUNT) {
            overflowSections.add(new OverflowSection("5. Resources Assigned", flattenOverflowResources(resources.subList(RESOURCE_ROW_COUNT, resources.size()), form)));
        }
        return overflowSections;
    }

    private void drawPreparedBySection(PDPageContentStream stream, PDType1Font bold, PDType1Font regular,
                                       float x, float y, float width, float height, float footerHeight, Ics204Form form) throws IOException {
        drawCell(stream, x, y, width, height);
        float footerCellWidth = width / 8f;
        float footerContentY = y + footerHeight - CELL_PADDING - BODY_FONT_SIZE;
        float topRowY = y + height - CELL_PADDING - HEADING_FONT_SIZE - LINE_HEIGHT;
        float rightX = x + (width * 0.45f);

        drawHeading(stream, bold, x, y + height, "9. Prepared By");
        drawCell(stream, x, y, footerCellWidth, footerHeight);
        drawCell(stream, x + footerCellWidth, y, footerCellWidth, footerHeight);
        drawInlinePair(stream, bold, regular, x + CELL_PADDING, topRowY, "Name", safe(form.getPreparedByName()));
        drawInlinePair(stream, bold, regular, rightX, topRowY, "Position/Title", safe(form.getPreparedByPositionTitle()));
        writeInlineHeadingValue(stream, bold, regular, x + CELL_PADDING, footerContentY, "ICS 204", "");
        writeInlineHeadingValue(stream, bold, regular, x + footerCellWidth + CELL_PADDING, footerContentY, "IAP Page", safe(form.getIapPage()));
        drawInlinePair(stream, bold, regular, rightX, footerContentY, "Date/Time", formatDateTime(form.getPreparedDateTime()));
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

    private List<OverflowSection> drawSection(PDPageContentStream stream, PDType1Font bold, PDType1Font regular,
                                              float x, float y, float width, float height, String heading, List<String> lines,
                                              String overflowHeading) throws IOException {
        float contentTop = writeHeadingWithInlineContent(stream, bold, regular, x, y, width, height, heading, lines);
        int capacity = contentCapacity(height, contentTop - y);
        List<String> normalized = lines == null || lines.isEmpty() ? List.of("") : lines;
        List<OverflowSection> overflowSections = new ArrayList<>();
        if (capacity >= normalized.size()) {
            if (usesInlineHeadingContent(heading, normalized)) {
                if (normalized.size() > 1) {
                    writeLines(stream, regular, x + CELL_PADDING, contentTop, normalized.subList(1, normalized.size()));
                }
            } else {
                writeLines(stream, regular, x + CELL_PADDING, contentTop, normalized);
            }
            return overflowSections;
        }
        int startIndex = usesInlineHeadingContent(heading, normalized) ? 1 : 0;
        int visibleCapacity = Math.max(1, capacity - startIndex);
        List<String> visible = new ArrayList<>(normalized.subList(startIndex, Math.min(startIndex + visibleCapacity, normalized.size())));
        if (overflowHeading != null && (normalized.size() - startIndex) > visibleCapacity && !visible.isEmpty()) {
            visible.set(visible.size() - 1, "See next page");
            overflowSections.add(new OverflowSection(overflowHeading, normalized.subList(startIndex + visibleCapacity - 1, normalized.size())));
        }
        writeLines(stream, regular, x + CELL_PADDING, contentTop, visible);
        return overflowSections;
    }

    private void drawTextBlock(PDPageContentStream stream, PDType1Font bold, PDType1Font regular,
                               float x, float y, float width, float height, String heading, List<String> lines) throws IOException {
        drawHeading(stream, bold, x, y + height, heading);
        float contentTop = y + height - CELL_PADDING - HEADING_FONT_SIZE - 12f;
        writeLines(stream, regular, x + CELL_PADDING, contentTop, lines);
    }

    private void drawHeading(PDPageContentStream stream, PDType1Font bold, float x, float topY, String heading) throws IOException {
        stream.beginText();
        stream.setFont(bold, HEADING_FONT_SIZE);
        stream.newLineAtOffset(x + CELL_PADDING, topY - CELL_PADDING - HEADING_FONT_SIZE);
        stream.showText(heading);
        stream.endText();
    }

    private float writeHeadingWithInlineContent(PDPageContentStream stream, PDType1Font bold, PDType1Font regular,
                                                float x, float y, float width, float height, String heading, List<String> lines) throws IOException {
        List<String> normalized = lines == null || lines.isEmpty() ? List.of("") : lines;
        drawHeading(stream, bold, x, y + height, heading);
        float headingWidth = bold.getStringWidth(heading) / 1000f * HEADING_FONT_SIZE;
        float inlineX = x + CELL_PADDING + headingWidth + 8f;
        float headingBaseline = y + height - CELL_PADDING - HEADING_FONT_SIZE;
        if (usesInlineHeadingContent(heading, normalized)) {
            stream.beginText();
            stream.setFont(regular, BODY_FONT_SIZE);
            stream.newLineAtOffset(inlineX, headingBaseline);
            stream.showText(safe(normalized.get(0)));
            stream.endText();
            return y + height - CELL_PADDING - HEADING_FONT_SIZE - 12f;
        }
        return y + height - CELL_PADDING - HEADING_FONT_SIZE - 12f;
    }

    private boolean usesInlineHeadingContent(String heading, List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return false;
        }
        String first = lines.get(0);
        if (first == null || first.isBlank()) {
            return false;
        }
        return heading.startsWith("1.");
    }

    private void drawLabeledColumn(PDPageContentStream stream, PDType1Font bold, PDType1Font regular,
                                   float x, float bottomY, float width, float topY, String label, List<String> values) throws IOException {
        stream.beginText();
        stream.setFont(bold, BODY_FONT_SIZE);
        stream.newLineAtOffset(x + CELL_PADDING, topY - CELL_PADDING - BODY_FONT_SIZE);
        stream.showText(label);
        stream.endText();

        List<String> wrapped = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                wrapped.addAll(wrap(value, Math.max(10, (int) (width / 6f))));
            }
        }
        writeWrappedCellText(stream, regular, x, bottomY, width, topY - bottomY - 14f, wrapped);
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

    private int contentCapacity(float height, float contentOffset) {
        float availableHeight = height - (height - contentOffset) - CELL_PADDING;
        return Math.max(1, (int) (availableHeight / LINE_HEIGHT));
    }

    private void writeInlineHeadingValue(PDPageContentStream stream, PDType1Font bold, PDType1Font regular,
                                         float x, float y, String heading, String value) throws IOException {
        stream.beginText();
        stream.setFont(bold, BODY_FONT_SIZE);
        stream.newLineAtOffset(x, y);
        stream.showText(heading + (safe(value).isBlank() ? "" : ": " + safe(value)));
        stream.endText();
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

    private List<List<String>> resourceColumns(ResourceAssignment resource, boolean appendContinuationNotice) {
        List<List<String>> columns = new ArrayList<>();
        String resourceIdentifier = safe(resource.getResourceIdentifier());
        if (!safe(resource.getResourceType()).isBlank() || !safe(resource.getTaskType()).isBlank()) {
            resourceIdentifier = joinAvailable(resourceIdentifier,
                    labelValue("Type", resource.getResourceType()),
                    labelValue("Task", resource.getTaskType()));
        }
        columns.add(wrap(resourceIdentifier, 18));
        columns.add(wrap(safe(resource.getLeader()), 18));
        columns.add(List.of(resource.getNumberOfPersons() > 0 ? String.valueOf(resource.getNumberOfPersons()) : ""));
        columns.add(wrap(safe(resource.getContact()), 24));

        List<String> lastColumn = new ArrayList<>();
        addWrapped(lastColumn, safe(resource.getReportingLocation()), 18);
        addWrapped(lastColumn, labelValue("Equipment", resource.getSpecialEquipment()), 18);
        addWrapped(lastColumn, labelValue("Remarks", resource.getRemarks()), 18);
        if (!safe(resource.getNotes()).isBlank()) {
            addWrapped(lastColumn, labelValue("Notes", resource.getNotes()), 18);
        }
        if (appendContinuationNotice) {
            lastColumn.add("See next page");
        }
        columns.add(lastColumn.isEmpty() ? List.of("") : lastColumn);
        return columns;
    }

    private List<String> flattenOverflowResources(List<ResourceAssignment> resources, Ics204Form form) {
        List<String> lines = new ArrayList<>();
        for (ResourceAssignment resource : resources) {
            lines.add("Resource Identifier: " + safe(resource.getResourceIdentifier()));
            if (!safe(resource.getResourceType()).isBlank() || !safe(resource.getTaskType()).isBlank()) {
                lines.add("Resource / Task Type: " + joinAvailable(safe(resource.getResourceType()), safe(resource.getTaskType())));
            }
            lines.add("Leader: " + safe(resource.getLeader()) + " | # of Persons: "
                    + (resource.getNumberOfPersons() > 0 ? resource.getNumberOfPersons() : "")
                    + " | Contact: " + safe(resource.getContact()));
            lines.add("Reporting Location: " + safe(resource.getReportingLocation()));
            lines.add("Special Equipment: " + safe(resource.getSpecialEquipment()));
            lines.add("Work Assignment: " + effectiveAssignment(resource, form));
            lines.add("Supplies / Remarks / Notes: " + joinAvailable(safe(resource.getSupplies()), safe(resource.getRemarks()), safe(resource.getNotes())));
            lines.add("");
        }
        return lines;
    }

    private List<String> workAssignmentLines(Ics204Form form) {
        List<String> lines = new ArrayList<>();
        if (!safe(form.getSharedWorkAssignment()).isBlank()) {
            lines.addAll(wrap(form.getSharedWorkAssignment(), 92));
        }
        for (ResourceAssignment resource : form.getResourcesAssigned()) {
            String assignment = effectiveAssignment(resource, form);
            if (!assignment.isBlank() && !assignment.equals(safe(form.getSharedWorkAssignment()))) {
                lines.addAll(prefixWrapped(safe(resource.getResourceIdentifier()) + ": ", wrap(assignment, 80)));
            }
        }
        return lines.isEmpty() ? List.of("") : lines;
    }

    private List<String> communicationLines(List<CommunicationEntry> communications) {
        List<String> lines = new ArrayList<>();
        if (communications != null) {
            for (CommunicationEntry entry : communications) {
                String prefix = safe(entry.getName());
                if (!safe(entry.getFunction()).isBlank()) {
                    prefix = prefix.isBlank() ? safe(entry.getFunction()) : prefix + " (" + safe(entry.getFunction()) + ")";
                }
                String line = prefix.isBlank() ? safe(entry.getPrimaryContact()) : prefix + ": " + safe(entry.getPrimaryContact());
                if (!line.isBlank()) {
                    lines.addAll(wrap(line, 42));
                }
            }
        }
        return lines.isEmpty() ? List.of("") : lines;
    }

    private List<String> prefixWrapped(String prefix, List<String> wrapped) {
        List<String> lines = new ArrayList<>();
        if (wrapped.isEmpty()) {
            lines.add(prefix);
            return lines;
        }
        lines.add(prefix + wrapped.get(0));
        for (int i = 1; i < wrapped.size(); i++) {
            lines.add("   " + wrapped.get(i));
        }
        return lines;
    }

    private void addWrapped(List<String> lines, String text, int width) {
        if (text != null && !text.isBlank()) {
            lines.addAll(wrap(text, width));
        }
    }

    private String effectiveAssignment(ResourceAssignment resource, Ics204Form form) {
        return safe(resource.getAssignment()).isBlank() ? safe(form.getSharedWorkAssignment()) : safe(resource.getAssignment());
    }

    private List<String> combineLines(String... values) {
        List<String> lines = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                lines.add(value);
            }
        }
        return lines.isEmpty() ? List.of("") : lines;
    }

    private String joinAvailable(String... values) {
        List<String> present = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                present.add(value);
            }
        }
        return String.join(" | ", present);
    }

    private String labelValue(String label, String value) {
        return safe(value).isBlank() ? "" : label + ": " + safe(value);
    }

    private String formatDate(LocalDateTime value) {
        return value == null ? "" : DATE_FORMATTER.format(value);
    }

    private String formatTime(LocalDateTime value) {
        return value == null ? "" : TIME_FORMATTER.format(value);
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? "" : formatDate(value) + " " + formatTime(value);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private record OverflowSection(String heading, List<String> lines) {
    }
}
