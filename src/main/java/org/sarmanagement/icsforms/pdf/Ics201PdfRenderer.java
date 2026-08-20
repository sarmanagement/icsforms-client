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
import org.sarmanagement.icsforms.model.SarTaskAssignment;
import org.sarmanagement.icsforms.model.SarTaskResource;

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
            drawOrgChartSection(stream, bold, regular, layout.x(), y - orgHeight, layout.width(), orgHeight,
                    data.getOrganizationalChart(), data.getSarTaskAssignments());
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
        // Draw two separate sub-cells side by side: 3a Date | 3b Time
        float halfWidth = width / 2f;
        drawCell(stream, x, y, halfWidth, height);
        drawCell(stream, x + halfWidth, y, halfWidth, height);
        float valueY = y + height - CELL_PADDING - HEADING_FONT_SIZE - 16f;
        // 3a Date
        drawHeading(stream, bold, x, y + height, "3a. Date Initiated");
        stream.beginText();
        stream.setFont(regular, BODY_FONT_SIZE);
        stream.newLineAtOffset(x + CELL_PADDING, valueY);
        stream.showText(safe(formatDate(form.getDateInitiated())));
        stream.endText();
        // 3b Time
        drawHeading(stream, bold, x + halfWidth, y + height, "3b. Time Initiated");
        stream.beginText();
        stream.setFont(regular, BODY_FONT_SIZE);
        stream.newLineAtOffset(x + halfWidth + CELL_PADDING, valueY);
        stream.showText(safe(formatTime(form.getTimeInitiated())));
        stream.endText();
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
        // Expand any embedded newlines so each physical line is a separate string.
        List<String> expanded = new java.util.ArrayList<>();
        for (String line : lines) {
            if (line == null) {
                expanded.add("");
            } else {
                for (String part : line.split("\n", -1)) {
                    expanded.add(part);
                }
            }
        }
        stream.beginText();
        stream.setFont(font, BODY_FONT_SIZE);
        stream.newLineAtOffset(x, startY);
        int lineCount = 0;
        for (String line : expanded) {
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

    /** Draws a graphical ICS organizational chart (boxes and connecting lines) for section 9. */
    private void drawOrgChartSection(PDPageContentStream stream, PDType1Font bold, PDType1Font regular,
                                     float sectionX, float sectionY, float width, float height,
                                     OrganizationalChart chart, List<SarTaskAssignment> tasks) throws IOException {
        drawCell(stream, sectionX, sectionY, width, height);
        drawHeading(stream, bold, sectionX, sectionY + height, "9. Current Organization");

        final float LEFT   = sectionX + CELL_PADDING;
        final float CWIDTH = width - 2f * CELL_PADDING;
        // Content top: below the section heading text
        final float TOP    = sectionY + height - CELL_PADDING - HEADING_FONT_SIZE - 8f;

        // --- Incident Commander / Unified Command box ---
        List<String> icNames = chart.getIncidentCommanders();
        List<String> nonBlankIc = new ArrayList<>();
        if (icNames != null) {
            for (String n : icNames) {
                if (!safe(n).isBlank()) nonBlankIc.add(n);
            }
        }
        boolean isUC = nonBlankIc.size() > 1;
        String icLabel = isUC ? "Unified Command" : "Incident Commander";
        StringBuilder icSb = new StringBuilder();
        for (String n : nonBlankIc) {
            if (icSb.length() > 0) icSb.append(", ");
            icSb.append(n);
        }
        String icNameLine = icSb.toString();

        float icBoxW   = Math.min(200f, CWIDTH * 0.38f);
        float icBoxH   = 36f;
        // Offset IC center slightly left so the command staff boxes fit on the right
        float icCX     = LEFT + CWIDTH * 0.42f;
        float icLeft   = icCX - icBoxW / 2f;
        float icTop    = TOP - 4f;
        float icBottom = icTop - icBoxH;

        // --- Command Staff boxes (Safety, PIO, Liaison) — always shown ---
        List<String[]> cmdStaff = new ArrayList<>();
        cmdStaff.add(new String[]{"Safety Officer",            safe(chart.getSafetyOfficerName())});
        cmdStaff.add(new String[]{"Public Info. Officer",      safe(chart.getPublicInformationOfficerName())});
        cmdStaff.add(new String[]{"Liaison Officer",           safe(chart.getLiaisonOfficerName())});

        float cmdBottom = icBottom;
        {
            float sBoxH = 22f, sGap = 4f;
            float sLeft  = icLeft + icBoxW + 16f;
            float sBoxW  = Math.max(40f, Math.min(150f, LEFT + CWIDTH - sLeft - 4f));
            float vLineX = sLeft - 6f;
            float vTop   = icTop - 4f;
            float vBot   = vTop - (cmdStaff.size() - 1) * (sBoxH + sGap) - sBoxH / 2f;

            // Horizontal connector from IC box right-edge midpoint to vertical branch line
            float icMidY = icBottom + icBoxH / 2f;
            stream.moveTo(icLeft + icBoxW, icMidY);
            stream.lineTo(vLineX, icMidY);
            stream.stroke();
            // Vertical branch line connecting all staff boxes (extends from vBot to vTop)
            stream.moveTo(vLineX, vBot);
            stream.lineTo(vLineX, vTop);
            stream.stroke();

            for (int i = 0; i < cmdStaff.size(); i++) {
                float sTop  = icTop - 4f - i * (sBoxH + sGap);
                float sBotY = sTop - sBoxH;
                cmdBottom = Math.min(cmdBottom, sBotY);
                // Horizontal connector to each staff box
                float midY = sTop - sBoxH / 2f;
                stream.moveTo(vLineX, midY);
                stream.lineTo(sLeft, midY);
                stream.stroke();
                // Staff box
                stream.addRect(sLeft, sBotY, sBoxW, sBoxH);
                stream.stroke();
                // Title (bold, top) + name (regular, below)
                drawOrgText(stream, bold,    7f, cmdStaff.get(i)[0], sLeft + 2f, sTop - 10f);
                drawOrgText(stream, regular, 7f, truncate(cmdStaff.get(i)[1], (int) (sBoxW / 4.5f)),
                            sLeft + 2f, sTop - 19f);
            }
        }

        // Draw IC box on top of connectors so borders stay clean
        stream.addRect(icLeft, icBottom, icBoxW, icBoxH);
        stream.stroke();
        drawCentered(stream, bold,    8f, icLabel,                              icLeft, icTop - 10f, icBoxW);
        drawCentered(stream, regular, 8f, truncate(icNameLine, (int) (icBoxW / 4.8f)), icLeft, icTop - 22f, icBoxW);

        // --- Vertical stem from IC box bottom to section bar ---
        float sBarY = Math.min(cmdBottom, icBottom) - 16f;
        stream.moveTo(icCX, icBottom);
        stream.lineTo(icCX, sBarY);
        stream.stroke();

        // --- Four General Staff section boxes ---
        float secW    = CWIDTH / 4f;
        float secBoxH = 36f;
        float barLeft  = LEFT + secW / 2f;
        float barRight = LEFT + CWIDTH - secW / 2f;
        // Horizontal bar connecting section midpoints
        stream.moveTo(barLeft, sBarY);
        stream.lineTo(barRight, sBarY);
        stream.stroke();

        float secBoxTop = sBarY - 14f;
        float secBoxBot = secBoxTop - secBoxH;

        String[] secTitles = {"Operations", "Planning", "Logistics", "Finance/Admin"};
        String[] secChiefs = {
            safe(chart.getOperationsSectionChiefName()),
            safe(chart.getPlanningSectionChiefName()),
            safe(chart.getLogisticsSectionChiefName()),
            safe(chart.getFinanceAdminSectionChiefName())
        };
        // Taller section boxes to accommodate title + name
        float[] secCX = new float[4];
        for (int i = 0; i < 4; i++) {
            float secLeft = LEFT + i * secW;
            secCX[i] = secLeft + secW / 2f;
            // Vertical drop from bar to box
            stream.moveTo(secCX[i], sBarY);
            stream.lineTo(secCX[i], secBoxTop);
            stream.stroke();
            // Section box
            float bLeft = secLeft + 4f, bWidth = secW - 8f;
            stream.addRect(bLeft, secBoxBot, bWidth, secBoxH);
            stream.stroke();
            // Title line 1: e.g. "Operations", line 2: "Section Chief"
            drawCentered(stream, bold, 7f, secTitles[i],  bLeft, secBoxTop - 10f, bWidth);
            drawCentered(stream, bold, 7f, "Section Chief", bLeft, secBoxTop - 19f, bWidth);
            if (!secChiefs[i].isBlank()) {
                drawCentered(stream, regular, 7f, truncate(secChiefs[i], (int) (bWidth / 4.3f)),
                             bLeft, secBoxTop - 30f, bWidth);
            }
        }

        // --- Sub-unit boxes below the relevant sections ---
        float subTop = secBoxBot - 8f, subH = 24f, subBot = subTop - subH;

        // Operations: one box per SAR task, showing task name and number of people
        if (!tasks.isEmpty()) {
            float bLeft = LEFT + 4f, bWidth = secW - 8f;
            float taskSubTop = subTop;
            float taskSubH = 24f;
            // Draw a single connector from the Operations section box center down
            stream.moveTo(secCX[0], secBoxBot);
            stream.lineTo(secCX[0], taskSubTop);
            stream.stroke();
            for (int ti = 0; ti < tasks.size(); ti++) {
                SarTaskAssignment task = tasks.get(ti);
                float taskSubBot = taskSubTop - taskSubH;
                stream.addRect(bLeft, taskSubBot, bWidth, taskSubH);
                stream.stroke();
                String label = safe(task.getResourceIdentifier()).isBlank()
                        ? (safe(task.getAssignmentTeamNumber()).isBlank() ? "Task" : safe(task.getAssignmentTeamNumber()))
                        : safe(task.getResourceIdentifier());
                String taskLabel = truncate(label, (int) (bWidth / 4.8f));
                int people = countPeople(task);
                String peopleStr = people == 1 ? "1 person" : people + " people";
                drawCentered(stream, bold,    7f, taskLabel,  bLeft, taskSubTop - 10f, bWidth);
                drawCentered(stream, regular, 7f, peopleStr,  bLeft, taskSubTop - 19f, bWidth);
                // connector to next task box
                if (ti < tasks.size() - 1) {
                    stream.moveTo(secCX[0], taskSubBot);
                    stream.lineTo(secCX[0], taskSubBot - 2f);
                    stream.stroke();
                }
                taskSubTop = taskSubBot - 2f;
            }
        }

        // Planning: Documentation Unit Leader
        String docLeader = safe(chart.getDocumentationUnitLeaderName());
        if (!docLeader.isBlank()) {
            float bLeft = LEFT + secW + 4f, bWidth = secW - 8f;
            stream.moveTo(secCX[1], secBoxBot);
            stream.lineTo(secCX[1], subTop);
            stream.stroke();
            stream.addRect(bLeft, subBot, bWidth, subH);
            stream.stroke();
            drawCentered(stream, bold,    7f, "Doc Unit Leader",                        bLeft, subTop - 10f, bWidth);
            drawCentered(stream, regular, 7f, truncate(docLeader, (int) (bWidth / 4.3f)), bLeft, subTop - 20f, bWidth);
        }

        // Logistics: Communications Unit Leader
        String commLeader = safe(chart.getCommunicationsUnitLeaderName());
        if (!commLeader.isBlank()) {
            float bLeft = LEFT + 2f * secW + 4f, bWidth = secW - 8f;
            stream.moveTo(secCX[2], secBoxBot);
            stream.lineTo(secCX[2], subTop);
            stream.stroke();
            stream.addRect(bLeft, subBot, bWidth, subH);
            stream.stroke();
            drawCentered(stream, bold,    7f, "Comms Unit Leader",                         bLeft, subTop - 10f, bWidth);
            drawCentered(stream, regular, 7f, truncate(commLeader, (int) (bWidth / 4.3f)), bLeft, subTop - 20f, bWidth);
        }
    }

    /** Draws horizontally-centered text with its baseline at {@code baselineY}. */
    private void drawCentered(PDPageContentStream stream, PDType1Font font, float size,
                               String text, float boxLeft, float baselineY, float boxWidth) throws IOException {
        String s = safe(text);
        if (s.isBlank()) return;
        float tw  = font.getStringWidth(s) / 1000f * size;
        float off = Math.max(0f, (boxWidth - tw) / 2f);
        stream.beginText();
        stream.setFont(font, size);
        stream.newLineAtOffset(boxLeft + off, baselineY);
        stream.showText(s);
        stream.endText();
    }

    /** Draws left-aligned org-chart label text at the given position. */
    private void drawOrgText(PDPageContentStream stream, PDType1Font font, float size,
                              String text, float x, float y) throws IOException {
        String s = safe(text);
        if (s.isBlank()) return;
        stream.beginText();
        stream.setFont(font, size);
        stream.newLineAtOffset(x, y);
        stream.showText(s);
        stream.endText();
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

    private int countPeople(SarTaskAssignment task) {
        if (task.getResourcesAssigned() == null) {
            return 0;
        }
        String taskResourceId = safe(task.getResourceIdentifier()).trim().toLowerCase();
        int count = 0;
        for (SarTaskResource r : task.getResourcesAssigned()) {
            if (r == null) {
                continue;
            }
            String name = safe(r.getName()).trim().toLowerCase();
            String function = safe(r.getFunction()).trim();
            boolean isPrimary = !taskResourceId.isBlank()
                    && name.equals(taskResourceId)
                    && (function.isBlank() || "resource".equalsIgnoreCase(function));
            if (!isPrimary) {
                count++;
            }
        }
        return count;
    }


    private String safe(String value) {
        if (value == null) return "";
        // PDFBox WinAnsiEncoding cannot encode control characters; strip them.
        return value.replaceAll("[\\p{Cntrl}]", "");
    }
}
