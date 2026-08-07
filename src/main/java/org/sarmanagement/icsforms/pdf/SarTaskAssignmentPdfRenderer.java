package org.sarmanagement.icsforms.pdf;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.ClueLogEntry;
import org.sarmanagement.icsforms.model.CommunicationEntry;
import org.sarmanagement.icsforms.model.IncidentContext;
import org.sarmanagement.icsforms.model.PodFactorRating;
import org.sarmanagement.icsforms.model.SarTaskAssignment;
import org.sarmanagement.icsforms.model.SarTaskResource;
import org.sarmanagement.icsforms.model.SarTaskSupport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Printable two-page SAR task assignment and debriefing PDF renderer.
 */
public class SarTaskAssignmentPdfRenderer extends AbstractPdfRenderer implements PdfFormRenderer {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final float BODY_FONT_SIZE = 9f;
    private static final float HEADING_FONT_SIZE = 10f;
    private static final float LINE_HEIGHT = 11f;
    private static final float CELL_PADDING = 4f;
    private static final int RESOURCE_SLOT_COUNT = 18;

    @Override
    public String getFormKey() {
        return "SAR Task Assignment";
    }

    @Override
    public void render(AppData data, Path outputFile) throws IOException {
        Files.createDirectories(outputFile.getParent());
        try (PDDocument document = new PDDocument()) {
            List<SarTaskAssignment> tasks = data.getSarTaskAssignments().isEmpty()
                    ? List.of(new SarTaskAssignment()) : data.getSarTaskAssignments();
            for (SarTaskAssignment task : tasks) {
                renderAssignmentPage(document, data.getIncidentContext(), task);
                renderDebriefPage(document, task, data.getClueLogEntries());
            }
            document.save(outputFile.toFile());
        }
    }

    private void renderAssignmentPage(PDDocument document, IncidentContext context, SarTaskAssignment task) throws IOException {
        PDPage page = new PDPage(PDRectangle.LETTER);
        document.addPage(page);
        try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
            PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            FormLayout layout = formLayout(page);
            float pageWidth = layout.width();
            float formHeight = layout.height();

            drawFormHeader(stream, bold, layout, "SAR TASK ASSIGNMENT", "FORM");
            drawFormFrame(stream, layout);
            float y = layout.top();

            float[] rows = expandRowToFill(formHeight, 3, 58f, 58f, 176f, 104f, 42f, 104f, 74f, 44f);
            float row1 = rows[0];
            float row2 = rows[1];
            float row3 = rows[2];
            float row4 = rows[3];
            float row5 = rows[4];
            float row6 = rows[5];
            float row7 = rows[6];
            float row8 = rows[7];

            float leftWidth = pageWidth * 0.30f;
            float middleWidth = pageWidth * 0.44f;
            float rightWidth = pageWidth - leftWidth - middleWidth;

            drawCell(stream, layout.x(), y - row1, leftWidth, row1);
            drawCell(stream, layout.x() + leftWidth, y - row1, middleWidth, row1);
            drawCell(stream, layout.x() + leftWidth + middleWidth, y - row1, rightWidth, row1);
            drawSection(stream, bold, regular, layout.x(), y - row1, leftWidth, row1,
                    "1. Incident Name", wrap(taskOrContextIncident(task, context), 20));
            drawOperationalPeriodSection(stream, bold, regular, layout.x() + leftWidth, y - row1, middleWidth, row1, context);
            drawAssignmentNumberSection(stream, bold, regular, layout.x() + leftWidth + middleWidth, y - row1, rightWidth, row1,
                    task.getAssignmentTeamNumber());
            y -= row1;

            float contextWidth = pageWidth * 0.22f;
            float operationsWidth = pageWidth - contextWidth;
            drawCell(stream, layout.x(), y - row2, operationsWidth, row2);
            drawCell(stream, layout.x() + operationsWidth, y - row2, contextWidth, row2);
            drawOperationsSection(stream, bold, regular, layout.x(), y - row2, operationsWidth, row2, task);
            drawContextSection(stream, bold, regular, layout.x() + operationsWidth, y - row2, contextWidth, row2, task);
            y -= row2;

            drawCell(stream, layout.x(), y - row3, pageWidth, row3);
            drawResourcesSection(stream, bold, regular, layout.x(), y - row3, pageWidth, row3, task);
            y -= row3;

            drawCell(stream, layout.x(), y - row4, pageWidth, row4);
            drawSection(stream, bold, regular, layout.x(), y - row4, pageWidth, row4,
                    "6. Work Assignment", wrap(task.getAssignment(), 95));
            y -= row4;

            drawCell(stream, layout.x(), y - row5, pageWidth, row5);
            drawSection(stream, bold, regular, layout.x(), y - row5, pageWidth, row5,
                    "7. Transportation Instructions", wrap(task.getTransportationInstructions(), 95));
            y -= row5;

            float mapWidth = pageWidth * 0.68f;
            drawCell(stream, layout.x(), y - row6, mapWidth, row6);
            drawCell(stream, layout.x() + mapWidth, y - row6, pageWidth - mapWidth, row6);
            drawSection(stream, bold, regular, layout.x(), y - row6, mapWidth, row6,
                    "8. Task Map", wrap(task.getTaskMap(), 62));
            drawSection(stream, bold, regular, layout.x() + mapWidth, y - row6, pageWidth - mapWidth, row6,
                    "9. Special Equipment", wrap(task.getSpecialEquipment(), 26));
            y -= row6;

            drawCell(stream, layout.x(), y - row7, pageWidth, row7);
            drawCommunicationsSection(stream, bold, regular, layout.x(), y - row7, pageWidth, row7, task.getCommunications());
            y -= row7;

            drawPreparedBySection(stream, bold, regular, layout.x(), y - row8, pageWidth, row8,
                    "11. Prepared by", task.getPreparedByName(), task.getPreparedByPositionTitle(), task.getPreparedDateTime(),
                    "SAR Task Assignment Form - Page 1 of 2");
        }
    }

    private void renderDebriefPage(PDDocument document, SarTaskAssignment task, List<ClueLogEntry> clueLogEntries) throws IOException {
        PDPage page = new PDPage(PDRectangle.LETTER);
        document.addPage(page);
        try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
            PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            FormLayout layout = formLayout(page);
            float pageWidth = layout.width();
            float formHeight = layout.height();

            drawFormHeader(stream, bold, layout, "SAR TASK ASSIGNMENT FORM", "DEBRIEFING");
            drawFormFrame(stream, layout);
            float y = layout.top();

            float[] rows = expandRowToFill(formHeight, 1, 68f, 220f, 140f, 90f, 48f);
            float row1 = rows[0];
            float row2 = rows[1];
            float row3 = rows[2];
            float row4 = rows[3];
            float row5 = rows[4];

            float leftWidth = pageWidth * 0.34f;
            float middleWidth = pageWidth * 0.37f;
            float rightWidth = pageWidth - leftWidth - middleWidth;

            drawCell(stream, layout.x(), y - row1, leftWidth, row1);
            drawCell(stream, layout.x() + leftWidth, y - row1, middleWidth, row1);
            drawCell(stream, layout.x() + leftWidth + middleWidth, y - row1, rightWidth, row1);
            drawSection(stream, bold, regular, layout.x(), y - row1, leftWidth, row1,
                    "12. Debriefing Supervisor", wrap(task.getDebriefingSupervisor(), 24));
            drawTimeOnAssignmentSection(stream, bold, regular, layout.x() + leftWidth, y - row1, middleWidth, row1, task);
            drawDebriefHeaderRight(stream, bold, regular, layout.x() + leftWidth + middleWidth, y - row1, rightWidth, row1, task);
            y -= row1;

            drawCell(stream, layout.x(), y - row2, pageWidth, row2);
            drawDebriefingSection(stream, bold, regular, layout.x(), y - row2, pageWidth, row2,
                    task, cluesForTask(task, clueLogEntries));
            y -= row2;

            drawCell(stream, layout.x(), y - row3, pageWidth, row3);
            drawSection(stream, bold, regular, layout.x(), y - row3, pageWidth, row3,
                    "16. Areas Not Covered", wrap(task.getAreasNotCovered(), 96));
            y -= row3;

            drawCell(stream, layout.x(), y - row4, pageWidth, row4);
            drawSection(stream, bold, regular, layout.x(), y - row4, pageWidth, row4,
                    "17. Hazards Observed", wrap(task.getHazardsObserved(), 96));
            y -= row4;

            drawPreparedBySection(stream, bold, regular, layout.x(), y - row5, pageWidth, row5,
                    "18. Prepared by", task.getDebriefPreparedByName(), task.getDebriefPreparedByPositionTitle(),
                    task.getDebriefPreparedDateTime(), "SAR Task Assignment Form - Page 2 of 2");
        }
    }

    private void drawResourcesSection(PDPageContentStream stream, PDType1Font bold, PDType1Font regular,
                                      float x, float y, float width, float height, SarTaskAssignment task) throws IOException {
        drawHeading(stream, bold, x, y + height, "5. Resources Assigned");
        drawRightAlignedHeadingValue(stream, regular, x, y + height, width, task.getResourceIdentifier());
        float detailLineY = y + height - CELL_PADDING - HEADING_FONT_SIZE - 16f;
        drawInlinePair(stream, bold, regular, x + CELL_PADDING, detailLineY, "Resource Type", safe(task.getResourceType()));
        drawInlinePair(stream, bold, regular, x + (width * 0.42f), detailLineY, "Task Type", safe(task.getTaskType()));
        float tableTop = y + height - 30f;
        float headerHeight = 20f;
        float headerBottom = tableTop - headerHeight;
        float rowHeight = (headerBottom - y) / 9f;
        float[] widths = {0.16f, 0.34f, 0.16f, 0.34f};

        drawHorizontalLine(stream, x, x + width, tableTop);
        drawHorizontalLine(stream, x, x + width, headerBottom);
        float currentX = x;
        for (int i = 0; i < widths.length - 1; i++) {
            currentX += width * widths[i];
            drawVerticalLine(stream, currentX, y, tableTop);
        }
        String[] headings = {"Function", "Name", "Function", "Name"};
        float[] starts = columnStarts(x, width, widths);
        for (int i = 0; i < headings.length; i++) {
            writeWrappedCellText(stream, bold, starts[i], headerBottom, width * widths[i], headerHeight, List.of(headings[i]));
        }

        List<SarTaskResource> resources = printableResources(task);
        while (resources.size() < RESOURCE_SLOT_COUNT) {
            resources.add(new SarTaskResource());
        }
        for (int rowIndex = 0; rowIndex < 9; rowIndex++) {
            float rowTop = headerBottom - (rowIndex * rowHeight);
            float rowBottom = Math.max(y, rowTop - rowHeight);
            drawHorizontalLine(stream, x, x + width, rowBottom);
            SarTaskResource left = resources.get(rowIndex * 2);
            SarTaskResource right = resources.get((rowIndex * 2) + 1);
            writeWrappedCellText(stream, regular, starts[0], rowBottom, width * widths[0], rowTop - rowBottom, wrap(left.getFunction(), 12));
            writeWrappedCellText(stream, regular, starts[1], rowBottom, width * widths[1], rowTop - rowBottom, wrap(left.getName(), 22));
            writeWrappedCellText(stream, regular, starts[2], rowBottom, width * widths[2], rowTop - rowBottom, wrap(right.getFunction(), 12));
            writeWrappedCellText(stream, regular, starts[3], rowBottom, width * widths[3], rowTop - rowBottom, wrap(right.getName(), 22));
        }
    }

    private void drawCommunicationsSection(PDPageContentStream stream, PDType1Font bold, PDType1Font regular,
                                           float x, float y, float width, float height, List<CommunicationEntry> communications) throws IOException {
        drawHeading(stream, bold, x, y + height, "10. Communications");
        float tableTop = y + height - 18f;
        float headerHeight = 20f;
        float headerBottom = tableTop - headerHeight;
        float[] widths = {0.30f, 0.30f, 0.40f};
        float[] starts = columnStarts(x, width, widths);
        drawHorizontalLine(stream, x, x + width, tableTop);
        drawHorizontalLine(stream, x, x + width, headerBottom);
        float currentX = x;
        for (int i = 0; i < widths.length - 1; i++) {
            currentX += width * widths[i];
            drawVerticalLine(stream, currentX, y, tableTop);
        }
        String[] headings = {"Name", "Function", "Primary Contact"};
        for (int i = 0; i < headings.length; i++) {
            writeWrappedCellText(stream, bold, starts[i], headerBottom, width * widths[i], headerHeight, List.of(headings[i]));
        }
        int rows = Math.max(4, communications.size());
        float rowHeight = (headerBottom - y) / rows;
        for (int rowIndex = 0; rowIndex < rows; rowIndex++) {
            float rowTop = headerBottom - (rowIndex * rowHeight);
            float rowBottom = Math.max(y, rowTop - rowHeight);
            drawHorizontalLine(stream, x, x + width, rowBottom);
            if (rowIndex >= communications.size()) {
                continue;
            }
            CommunicationEntry entry = communications.get(rowIndex);
            writeWrappedCellText(stream, regular, starts[0], rowBottom, width * widths[0], rowTop - rowBottom, wrap(entry.getName(), 20));
            writeWrappedCellText(stream, regular, starts[1], rowBottom, width * widths[1], rowTop - rowBottom, wrap(entry.getFunction(), 20));
            writeWrappedCellText(stream, regular, starts[2], rowBottom, width * widths[2], rowTop - rowBottom, wrap(entry.getPrimaryContact(), 28));
        }
    }

    private void drawOperationsSection(PDPageContentStream stream, PDType1Font bold, PDType1Font regular,
                                       float x, float y, float width, float height, SarTaskAssignment task) throws IOException {
        drawHeading(stream, bold, x, y + height, "4. Operations Personnel");
        float lineY = y + height - CELL_PADDING - HEADING_FONT_SIZE - 14f;
        drawInlinePair(stream, bold, regular, x + CELL_PADDING, lineY, "Name", task.getOperationsSectionChiefName());
        drawInlinePair(stream, bold, regular, x + (width * 0.58f), lineY, "Contact Number(s)", task.getOperationsSectionChiefContact());
        if (!task.getSecondaryManagementRoleLabel().isBlank()) {
            drawInlinePair(stream, bold, regular, x + CELL_PADDING, lineY - 16f, task.getSecondaryManagementRoleLabel(), task.getSecondaryManagementName());
            drawInlinePair(stream, bold, regular, x + (width * 0.58f), lineY - 16f, "Contact", task.getSecondaryManagementContact());
        }
    }

    private void drawContextSection(PDPageContentStream stream, PDType1Font bold, PDType1Font regular,
                                    float x, float y, float width, float height, SarTaskAssignment task) throws IOException {
        LabeledValue relevantContext = relevantContext(task);
        drawSection(stream, bold, regular, x, y, width, height,
                "3. " + relevantContext.label(), wrap(relevantContext.value(), 14));
    }

    private void drawOperationalPeriodSection(PDPageContentStream stream, PDType1Font bold, PDType1Font regular,
                                              float x, float y, float width, float height, IncidentContext context) throws IOException {
        drawHeading(stream, bold, x, y + height, "2. Operational Period");
        float labelY = y + height - CELL_PADDING - HEADING_FONT_SIZE - 14f;
        drawInlinePair(stream, bold, regular, x + CELL_PADDING, labelY, "Date From", formatDate(context == null ? null : context.getOperationalPeriodStart()));
        drawInlinePair(stream, bold, regular, x + (width / 2f), labelY, "Date To", formatDate(context == null ? null : context.getOperationalPeriodEnd()));
        drawInlinePair(stream, bold, regular, x + CELL_PADDING, labelY - 16f, "Time From", formatTime(context == null ? null : context.getOperationalPeriodStart()));
        drawInlinePair(stream, bold, regular, x + (width / 2f), labelY - 16f, "Time To", formatTime(context == null ? null : context.getOperationalPeriodEnd()));
    }

    private void drawAssignmentNumberSection(PDPageContentStream stream, PDType1Font bold, PDType1Font regular,
                                             float x, float y, float width, float height, String assignmentTeamNumber) throws IOException {
        drawHeading(stream, bold, x, y + height, "3. Assignment/Team Number");
        stream.beginText();
        stream.setFont(bold, 16f);
        stream.newLineAtOffset(x + CELL_PADDING, y + (height / 2f) - 4f);
        stream.showText(safe(assignmentTeamNumber));
        stream.endText();
    }

    private void drawTimeOnAssignmentSection(PDPageContentStream stream, PDType1Font bold, PDType1Font regular,
                                             float x, float y, float width, float height, SarTaskAssignment task) throws IOException {
        drawHeading(stream, bold, x, y + height, "13. Time On Assignment");
        float labelY = y + height - CELL_PADDING - HEADING_FONT_SIZE - 14f;
        drawInlinePair(stream, bold, regular, x + CELL_PADDING, labelY, "Date From", formatDate(task.getAssignmentStart()));
        drawInlinePair(stream, bold, regular, x + (width / 2f), labelY, "Date To", formatDate(task.getAssignmentEnd()));
        drawInlinePair(stream, bold, regular, x + CELL_PADDING, labelY - 16f, "Time From", formatTime(task.getAssignmentStart()));
        drawInlinePair(stream, bold, regular, x + (width / 2f), labelY - 16f, "Time To", formatTime(task.getAssignmentEnd()));
    }

    private void drawDebriefHeaderRight(PDPageContentStream stream, PDType1Font bold, PDType1Font regular,
                                        float x, float y, float width, float height, SarTaskAssignment task) throws IOException {
        drawHeading(stream, bold, x, y + height, "14. Assignment/Team Number");
        stream.beginText();
        stream.setFont(bold, 14f);
        stream.newLineAtOffset(x + CELL_PADDING, y + height - 28f);
        stream.showText(safe(task.getAssignmentTeamNumber()));
        stream.endText();
        drawInlinePair(stream, bold, regular, x + CELL_PADDING, y + 16f, "Vehicle Miles", safe(task.getVehicleMiles()));
    }

    private void drawPreparedBySection(PDPageContentStream stream, PDType1Font bold, PDType1Font regular,
                                       float x, float y, float width, float height, String heading,
                                       String name, String title, LocalDateTime dateTime, String footerLabel) throws IOException {
        drawCell(stream, x, y, width, height);
        drawHeading(stream, bold, x, y + height, heading);
        float footerBandHeight = 14f;
        float footerCellWidth = width / 3f;
        drawCell(stream, x, y, footerCellWidth, footerBandHeight);
        float contentY = y + height - CELL_PADDING - HEADING_FONT_SIZE - 14f;
        drawInlinePair(stream, bold, regular, x + CELL_PADDING, contentY, "Name", safe(name));
        drawInlinePair(stream, bold, regular, x + (width * 0.42f), contentY, "Position/Title", safe(title));
        drawInlinePair(stream, bold, regular, x + (width * 0.74f), contentY, "Date/Time", formatDateTime(dateTime));
        stream.beginText();
        stream.setFont(regular, 8f);
        stream.newLineAtOffset(x + CELL_PADDING, y + 3f);
        stream.showText(footerLabel);
        stream.endText();
    }

    private void drawDebriefingSection(PDPageContentStream stream, PDType1Font bold, PDType1Font regular,
                                       float x, float y, float width, float height, SarTaskAssignment task,
                                       List<ClueLogEntry> clues) throws IOException {
        drawHeading(stream, bold, x, y + height, "15. Debriefing");
        if (!safe(task.getReportedPod()).isBlank()) {
            String podLabel = "REPORTED POD: " + safe(task.getReportedPod()) + "%";
            float podWidth = bold.getStringWidth(podLabel) / 1000f * BODY_FONT_SIZE;
            stream.beginText();
            stream.setFont(bold, BODY_FONT_SIZE);
            stream.newLineAtOffset(x + width - CELL_PADDING - podWidth, y + height - CELL_PADDING - HEADING_FONT_SIZE);
            stream.showText(podLabel);
            stream.endText();
        }
        writeWrappedCellText(stream, regular, x, y, width, height - 20f, debriefSectionLines(task, clues));
    }

    private void drawSection(PDPageContentStream stream, PDType1Font bold, PDType1Font regular,
                             float x, float y, float width, float height, String heading, List<String> lines) throws IOException {
        drawHeading(stream, bold, x, y + height, heading);
        writeWrappedCellText(stream, regular, x, y, width, height - 12f, lines == null || lines.isEmpty() ? List.of("") : lines);
    }

    private void drawHeading(PDPageContentStream stream, PDType1Font bold, float x, float topY, String heading) throws IOException {
        stream.beginText();
        stream.setFont(bold, HEADING_FONT_SIZE);
        stream.newLineAtOffset(x + CELL_PADDING, topY - CELL_PADDING - HEADING_FONT_SIZE);
        stream.showText(heading);
        stream.endText();
    }

    private void drawRightAlignedHeadingValue(PDPageContentStream stream, PDType1Font font,
                                              float x, float topY, float width, String value) throws IOException {
        String safeValue = safe(value);
        if (safeValue.isBlank()) {
            return;
        }
        float textWidth = font.getStringWidth(safeValue) / 1000f * BODY_FONT_SIZE;
        stream.beginText();
        stream.setFont(font, BODY_FONT_SIZE);
        stream.newLineAtOffset(x + width - CELL_PADDING - textWidth, topY - CELL_PADDING - HEADING_FONT_SIZE);
        stream.showText(safeValue);
        stream.endText();
    }

    private void drawInlinePair(PDPageContentStream stream, PDType1Font bold, PDType1Font regular,
                                float x, float y, String label, String value) throws IOException {
        float valueOffset = (bold.getStringWidth(label + ": ") / 1000f * BODY_FONT_SIZE) + 2f;
        stream.beginText();
        stream.setFont(bold, BODY_FONT_SIZE);
        stream.newLineAtOffset(x, y);
        stream.showText(label + ": ");
        stream.endText();

        stream.beginText();
        stream.setFont(regular, BODY_FONT_SIZE);
        stream.newLineAtOffset(x + valueOffset, y);
        stream.showText(safe(value));
        stream.endText();
    }

    private void writeWrappedCellText(PDPageContentStream stream, PDType1Font font,
                                      float x, float y, float width, float height, List<String> lines) throws IOException {
        List<String> normalized = lines == null || lines.isEmpty() ? List.of("") : lines;
        float startY = y + height - CELL_PADDING - BODY_FONT_SIZE - 2f;
        stream.beginText();
        stream.setFont(font, BODY_FONT_SIZE);
        stream.newLineAtOffset(x + CELL_PADDING, startY);
        int maxLines = Math.max(1, (int) ((height - (CELL_PADDING * 2)) / LINE_HEIGHT));
        for (int i = 0; i < Math.min(maxLines, normalized.size()); i++) {
            stream.showText(safe(normalized.get(i)));
            if (i < maxLines - 1) {
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

    private String taskOrContextIncident(SarTaskAssignment task, IncidentContext context) {
        return safe(task.getIncidentName()).isBlank() && context != null ? context.getIncidentName() : task.getIncidentName();
    }

    private List<SarTaskResource> printableResources(SarTaskAssignment task) {
        List<SarTaskResource> resources = new ArrayList<>();
        String taskResourceIdentifier = safe(task.getResourceIdentifier()).trim();
        for (SarTaskResource resource : task.getResourcesAssigned()) {
            // Ignore sparse/null entries so partially edited rows still render as blank slots
            // instead of failing the entire PDF export.
            if (resource == null) {
                continue;
            }
            String resourceName = safe(resource.getName()).trim();
            if (!taskResourceIdentifier.isBlank() && resourceName.equalsIgnoreCase(taskResourceIdentifier)) {
                continue;
            }
            resources.add(resource);
        }
        return resources;
    }

    private List<ClueLogEntry> cluesForTask(SarTaskAssignment task, List<ClueLogEntry> clueLogEntries) {
        List<ClueLogEntry> matches = new ArrayList<>();
        for (ClueLogEntry clue : clueLogEntries) {
            if (clue == null) {
                continue;
            }
            if (!safe(task.getAssignmentId()).isBlank() && safe(task.getAssignmentId()).equals(clue.getAssignmentId())) {
                matches.add(clue);
                continue;
            }
            if (safe(task.getAssignmentId()).isBlank() && !safe(task.getAssignmentTeamNumber()).isBlank()
                    && safe(task.getAssignmentTeamNumber()).equals(clue.getDetectingTask())) {
                matches.add(clue);
            }
        }
        return matches;
    }

    private List<String> debriefSectionLines(SarTaskAssignment task, List<ClueLogEntry> clues) {
        List<String> lines = new ArrayList<>();
        addWrappedBlock(lines, safe(task.getDebriefNotes()), 96);
        if (!clues.isEmpty()) {
            lines.add("");
            lines.add("Clues Detected:");
            for (ClueLogEntry clue : clues) {
                String clueSummary = joinNonBlank(
                        formatDateTime(clue.getDateTimeCollected()),
                        safe(clue.getLocation()),
                        safe(clue.getDescription()),
                        safe(clue.getFollowUp()));
                addWrappedBlock(lines, "- " + clueSummary, 92);
            }
        }
        List<PodFactorRating> factors = SarTaskSupport.factorRatings(task.getResourceType(), task.getQualitativePodFactors());
        boolean hasFactorContent = factors.stream().anyMatch(factor -> factor.getScore() != null || !safe(factor.getDescription()).isBlank());
        if (hasFactorContent) {
            lines.add("");
            lines.add("Qualitative POD Factors (" + SarTaskSupport.podProfileLabel(task.getResourceType()) + "):");
            for (PodFactorRating factor : factors) {
                if (factor.getScore() == null && safe(factor.getDescription()).isBlank()) {
                    continue;
                }
                String factorSummary = factor.getName() + " " + (factor.getScore() == null ? "" : factor.getScore() + "/" + factor.getMaxScore());
                if (!safe(factor.getDescription()).isBlank()) {
                    factorSummary += ": " + factor.getDescription();
                }
                addWrappedBlock(lines, "- " + factorSummary.trim(), 92);
            }
        }
        if (SarTaskSupport.usesCanineFactors(task.getResourceType())) {
            List<String> canineLines = new ArrayList<>();
            addIfPresent(canineLines, "Canine Type", task.getCanineSearchType());
            addIfPresent(canineLines, "Imprint", task.getCanineImprint());
            addIfPresent(canineLines, "Sun Angle", task.getCanineSunAngle());
            addIfPresent(canineLines, "Day/Night", task.getCanineDayNight());
            addIfPresent(canineLines, "Cloud Cover", task.getCanineCloudCover());
            addIfPresent(canineLines, "Wind Speed", task.getCanineWindSpeed());
            if (!canineLines.isEmpty()) {
                lines.add("");
                lines.add("Canine Assignment Conditions:");
                for (String canineLine : canineLines) {
                    addWrappedBlock(lines, "- " + canineLine, 92);
                }
            }
        }
        return lines.isEmpty() ? List.of("") : lines;
    }

    private void addWrappedBlock(List<String> lines, String value, int width) {
        if (!safe(value).isBlank()) {
            lines.addAll(wrap(value, width));
        }
    }

    private void addIfPresent(List<String> lines, String label, String value) {
        if (!safe(value).isBlank()) {
            lines.add(label + ": " + value);
        }
    }

    private LabeledValue relevantContext(SarTaskAssignment task) {
        // The printed form only has room for one management-context cell, so prefer the
        // first populated ICS 204 context field in its Branch -> Division -> Group ->
        // Staging Area order so the PDF matches the linked assignment context source.
        if (!safe(task.getBranch()).isBlank()) {
            return new LabeledValue("Branch", task.getBranch());
        }
        if (!safe(task.getDivision()).isBlank()) {
            return new LabeledValue("Division", task.getDivision());
        }
        if (!safe(task.getGroup()).isBlank()) {
            return new LabeledValue("Group", task.getGroup());
        }
        if (!safe(task.getStagingArea()).isBlank()) {
            return new LabeledValue("Staging Area", task.getStagingArea());
        }
        return new LabeledValue("Context", "");
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

    private String joinNonBlank(String... values) {
        List<String> parts = new ArrayList<>();
        for (String value : values) {
            if (!safe(value).isBlank()) {
                parts.add(value.trim());
            }
        }
        return String.join(" | ", parts);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private record LabeledValue(String label, String value) {
    }
}
