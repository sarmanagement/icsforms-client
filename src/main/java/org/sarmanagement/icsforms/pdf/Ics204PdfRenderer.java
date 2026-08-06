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
    private static final float MARGIN = 36f;
    private static final float HEADER_HEIGHT = 14f;
    private static final float BODY_FONT_SIZE = 10f;
    private static final float HEADING_FONT_SIZE = 10f;
    private static final float LINE_HEIGHT = 12f;
    private static final float CELL_PADDING = 4f;
    private static final float PAGE_BOTTOM_MARGIN = 36f;
    private static final float PAGE_WIDTH = PDRectangle.LETTER.getWidth() - (MARGIN * 2);

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

            float pageTop = page.getMediaBox().getHeight() - MARGIN;
            drawCenteredHeader(stream, bold, pageTop, "ICS 204", "ASSIGNMENT LIST");
            float gridTop = pageTop - HEADER_HEIGHT;
            float gridBottom = PAGE_BOTTOM_MARGIN;
            float gridHeight = gridTop - gridBottom;

            float row1 = 60f;
            float row2 = 46f;
            float row3 = 56f;
            float row6 = 54f;
            float row7 = 70f;
            float row8 = 64f;
            float row5 = gridHeight - (row1 + row2 + row3 + row6 + row7 + row8);
            if (row5 < 220f) {
                row5 = 220f;
                row7 = Math.max(56f, gridHeight - (row1 + row2 + row3 + row5 + row6 + row8));
            }

            drawCell(stream, MARGIN, gridBottom, PAGE_WIDTH, gridHeight);

            float y = gridTop;
            float halfWidth = PAGE_WIDTH / 2f;

            drawHorizontalLine(stream, MARGIN, MARGIN + PAGE_WIDTH, y - row1);
            drawVerticalLine(stream, MARGIN + halfWidth, y - row1, y);
            drawSection(stream, bold, regular, MARGIN, y - row1, halfWidth, row1,
                    "1. Incident Name", List.of(safe(context.getIncidentName())), null);
            drawOperationalPeriodSection(stream, bold, regular, MARGIN + halfWidth, y - row1, halfWidth, row1, context);
            y -= row1;

            drawHorizontalLine(stream, MARGIN, MARGIN + PAGE_WIDTH, y - row2);
            drawAssignmentContextSection(stream, bold, regular, MARGIN, y - row2, PAGE_WIDTH, row2, form);
            y -= row2;

            drawHorizontalLine(stream, MARGIN, MARGIN + PAGE_WIDTH, y - row3);
            drawManagementSection(stream, bold, regular, MARGIN, y - row3, PAGE_WIDTH, row3, form);
            y -= row3;

            drawHorizontalLine(stream, MARGIN, MARGIN + PAGE_WIDTH, y - row5);
            overflowSections.addAll(drawResourcesSection(stream, bold, regular, MARGIN, y - row5, PAGE_WIDTH, row5, form));
            y -= row5;

            drawHorizontalLine(stream, MARGIN, MARGIN + PAGE_WIDTH, y - row6);
            overflowSections.addAll(drawSection(stream, bold, regular, MARGIN, y - row6, PAGE_WIDTH, row6,
                    "6. Work Assignment", workAssignmentLines(form), "6. Work Assignment"));
            y -= row6;

            drawHorizontalLine(stream, MARGIN, MARGIN + PAGE_WIDTH, y - row7);
            drawVerticalLine(stream, MARGIN + halfWidth, y - row7, y);
            overflowSections.addAll(drawSection(stream, bold, regular, MARGIN, y - row7, halfWidth, row7,
                    "7. Special Instructions", wrap(form.getSpecialInstructions(), 42), "7. Special Instructions"));
            overflowSections.addAll(drawSection(stream, bold, regular, MARGIN + halfWidth, y - row7, halfWidth, row7,
                    "8. Communications", communicationLines(form.getCommunications()), "8. Communications"));
            y -= row7;

            drawPreparedBySection(stream, bold, regular, MARGIN, y - row8, PAGE_WIDTH, row8, 24f, form);
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
            float pageTop = page.getMediaBox().getHeight() - MARGIN;
            drawCenteredHeader(stream, bold, pageTop, "ICS 204", overflow.heading);
            float boxTop = pageTop - HEADER_HEIGHT;
            float boxHeight = boxTop - PAGE_BOTTOM_MARGIN;
            drawCell(stream, MARGIN, PAGE_BOTTOM_MARGIN, PAGE_WIDTH, boxHeight);
            drawTextBlock(stream, bold, regular, MARGIN, PAGE_BOTTOM_MARGIN, PAGE_WIDTH, boxHeight, overflow.heading, overflow.lines);
        }
    }

    private void drawAssignmentContextSection(PDPageContentStream stream, PDType1Font bold, PDType1Font regular,
                                              float x, float y, float width, float height, Ics204Form form) throws IOException {
        drawHeading(stream, bold, x, y + height, "3. Assignment Context");
        float bandBottom = y + height - 18f;
        drawHorizontalLine(stream, x, x + width, bandBottom);

        float columnWidth = width / 4f;
        for (int i = 1; i < 4; i++) {
            drawVerticalLine(stream, x + (columnWidth * i), y, bandBottom);
        }

        drawLabeledColumn(stream, bold, regular, x, y, columnWidth, bandBottom, "Branch", List.of(safe(form.getBranch())));
        drawLabeledColumn(stream, bold, regular, x + columnWidth, y, columnWidth, bandBottom, "Division", List.of(safe(form.getDivision())));
        drawLabeledColumn(stream, bold, regular, x + (columnWidth * 2f), y, columnWidth, bandBottom, "Group", List.of(safe(form.getGroup())));
        drawLabeledColumn(stream, bold, regular, x + (columnWidth * 3f), y, columnWidth, bandBottom, "Staging Area", List.of(safe(form.getStagingArea())));
    }

    private void drawManagementSection(PDPageContentStream stream, PDType1Font bold, PDType1Font regular,
                                       float x, float y, float width, float height, Ics204Form form) throws IOException {
        drawHeading(stream, bold, x, y + height, "4. Supervisory Personnel");
        float bandBottom = y + height - 18f;
        drawHorizontalLine(stream, x, x + width, bandBottom);

        float columnWidth = width / 3f;
        for (int i = 1; i < 3; i++) {
            drawVerticalLine(stream, x + (columnWidth * i), y, bandBottom);
        }

        drawLabeledColumn(stream, bold, regular, x, y, columnWidth, bandBottom,
                "Operations Section Chief",
                combineLines(safe(form.getOperationsSectionChiefName()), labelValue("Contact", form.getOperationsSectionChiefContact())));
        drawLabeledColumn(stream, bold, regular, x + columnWidth, y, columnWidth, bandBottom,
                "Branch Director",
                combineLines(safe(form.getBranchDirectorName()), labelValue("Contact", form.getBranchDirectorContact())));
        drawLabeledColumn(stream, bold, regular, x + (columnWidth * 2f), y, columnWidth, bandBottom,
                "Division/Group Supervisor",
                combineLines(safe(form.getDivisionGroupSupervisorName()), labelValue("Contact", form.getDivisionGroupSupervisorContact())));
    }

    private List<OverflowSection> drawResourcesSection(PDPageContentStream stream, PDType1Font bold, PDType1Font regular,
                                                       float x, float y, float width, float height, Ics204Form form) throws IOException {
        List<OverflowSection> overflowSections = new ArrayList<>();
        drawHeading(stream, bold, x, y + height, "5. Resources Assigned");
        float tableTop = y + height - 18f;
        float headerHeight = 40f;
        float rowHeight = 56f;
        float headerBottom = tableTop - headerHeight;
        drawHorizontalLine(stream, x, x + width, tableTop);
        drawHorizontalLine(stream, x, x + width, headerBottom);

        float[] widths = {0.18f, 0.15f, 0.14f, 0.15f, 0.38f};
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
                    wrap(headings[i], widths[i] < 0.15f ? 12 : widths[i] < 0.18f ? 16 : 30));
        }

        List<ResourceAssignment> resources = form.getResourcesAssigned();
        int availableRows = Math.max(1, (int) ((headerBottom - y) / rowHeight));
        int visibleCount = resources.isEmpty() ? 1 : Math.min(resources.size(), availableRows);

        for (int rowIndex = 0; rowIndex < visibleCount; rowIndex++) {
            float rowTop = headerBottom - (rowIndex * rowHeight);
            float rowBottom = Math.max(y, rowTop - rowHeight);
            drawHorizontalLine(stream, x, x + width, rowBottom);

            if (resources.isEmpty()) {
                continue;
            }

            ResourceAssignment resource = resources.get(rowIndex);
            List<List<String>> columns = resourceColumns(resource, form, rowIndex == (visibleCount - 1) && resources.size() > availableRows);
            for (int col = 0; col < columns.size(); col++) {
                writeWrappedCellText(stream, regular, starts[col], rowBottom, width * widths[col], rowTop - rowBottom, columns.get(col));
            }
        }

        if (resources.size() > availableRows) {
            overflowSections.add(new OverflowSection("5. Resources Assigned", flattenOverflowResources(resources.subList(availableRows, resources.size()), form)));
        }
        return overflowSections;
    }

    private void drawPreparedBySection(PDPageContentStream stream, PDType1Font bold, PDType1Font regular,
                                       float x, float y, float width, float height, float footerHeight, Ics204Form form) throws IOException {
        float footerCellWidth = width / 8f;
        float footerTop = y + footerHeight;
        drawHeading(stream, bold, x, y + height, "9. Prepared By");

        drawCell(stream, x, y, footerCellWidth, footerHeight);
        drawCell(stream, x + footerCellWidth, y, footerCellWidth, footerHeight);

        float nameY = y + height - CELL_PADDING - HEADING_FONT_SIZE - 14f;
        float titleY = nameY - LINE_HEIGHT;
        float dateTimeY = footerTop + 12f;
        float rightX = x + (width * 0.58f);

        drawInlinePair(stream, bold, regular, x + CELL_PADDING, nameY, "Name", safe(form.getPreparedByName()));
        drawInlinePair(stream, bold, regular, x + CELL_PADDING, titleY, "Position/Title", safe(form.getPreparedByPositionTitle()));
        drawInlinePair(stream, bold, regular, rightX, dateTimeY, "Date/Time", formatDateTime(form.getPreparedDateTime()));
        writeInlineHeadingValue(stream, bold, regular, x + CELL_PADDING, y + footerHeight - CELL_PADDING - BODY_FONT_SIZE, "ICS 204", "");
        writeInlineHeadingValue(stream, bold, regular, x + footerCellWidth + CELL_PADDING, y + footerHeight - CELL_PADDING - BODY_FONT_SIZE,
                "IAP Page", safe(form.getIapPage()));
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

    private void drawCenteredHeader(PDPageContentStream stream, PDType1Font bold, float y, String formNumber, String title)
            throws IOException {
        String header = formNumber + " " + title;
        float headerWidth = bold.getStringWidth(header) / 1000f * 14f;
        stream.beginText();
        stream.setFont(bold, 14f);
        stream.newLineAtOffset((PDRectangle.LETTER.getWidth() - headerWidth) / 2f, y);
        stream.showText(header);
        stream.endText();
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
        stream.newLineAtOffset(x + (safeLabel.isBlank() ? 0f : 52f), y);
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

    private List<List<String>> resourceColumns(ResourceAssignment resource, Ics204Form form, boolean appendContinuationNotice) {
        List<List<String>> columns = new ArrayList<>();
        columns.add(wrap(safe(resource.getResourceIdentifier()), 16));
        columns.add(wrap(safe(resource.getLeader()), 14));
        columns.add(List.of(resource.getNumberOfPersons() > 0 ? String.valueOf(resource.getNumberOfPersons()) : ""));
        columns.add(wrap(safe(resource.getContact()), 14));

        List<String> lastColumn = new ArrayList<>();
        addWrapped(lastColumn, safe(resource.getReportingLocation()), 26);
        addWrapped(lastColumn, labelValue("Equipment", resource.getSpecialEquipment()), 26);
        addWrapped(lastColumn, labelValue("Assignment", effectiveAssignment(resource, form)), 26);
        addWrapped(lastColumn, joinAvailable(labelValue("Supplies", resource.getSupplies()), labelValue("Remarks", resource.getRemarks())), 26);
        if (!safe(resource.getNotes()).isBlank()) {
            addWrapped(lastColumn, labelValue("Notes", resource.getNotes()), 26);
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
