package org.sarmanagement.icsforms.pdf;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.Ics207Form;
import org.sarmanagement.icsforms.model.IncidentContext;
import org.sarmanagement.icsforms.model.OrgChartEntry;
import org.sarmanagement.icsforms.model.OrganizationalChart;
import org.sarmanagement.icsforms.model.SarTaskAssignment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Structured PDF renderer for the Incident Organization Chart (ICS 207) form.
 *
 * <p>Produces a single {@link PDRectangle#LETTER} page containing:
 * <ol>
 *   <li>Section 1 — Incident Name</li>
 *   <li>Section 2 — Operational Period</li>
 *   <li>Section 3 — Graphical organization chart</li>
 *   <li>Section 4 — Prepared by footer</li>
 * </ol>
 */
public class Ics207PdfRenderer extends AbstractPdfRenderer implements PdfFormRenderer {

    private static final DateTimeFormatter DATE_FMT      = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FMT      = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static final float BODY_FONT_SIZE    = 9f;
    private static final float HEADING_FONT_SIZE = 9f;
    private static final float CELL_PADDING      = 4f;

    /** {@inheritDoc} */
    @Override
    public String getFormKey() {
        return "ICS 207";
    }

    /** {@inheritDoc} */
    @Override
    public void render(AppData data, Path outputFile) throws IOException {
        Files.createDirectories(outputFile.getParent());
        try (PDDocument document = new PDDocument()) {
            renderPage(document, data);
            document.save(outputFile.toFile());
        }
    }

    private void renderPage(PDDocument document, AppData data) throws IOException {
        IncidentContext context = data.getIncidentContext();
        OrganizationalChart chart = data.getOrganizationalChart();
        Ics207Form form = data.getForm207();
        List<SarTaskAssignment> tasks = data.getSarTaskAssignments() == null
                ? List.of() : data.getSarTaskAssignments();

        PDPage page = new PDPage(PDRectangle.LETTER);
        document.addPage(page);
        try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
            PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDType1Font bold    = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            FormLayout layout   = formLayout(page);

            drawFormHeader(stream, bold, layout, "ICS 207", "INCIDENT ORGANIZATION CHART");
            drawFormFrame(stream, layout);

            float y        = layout.top();
            float fullW    = layout.width();
            float halfW    = fullW / 2f;
            float x        = layout.x();
            float formBot  = layout.y();

            // Row 1: Incident Name | Operational Period
            float row1H = 50f;
            drawCell(stream, x,          y - row1H, halfW, row1H);
            drawCell(stream, x + halfW,  y - row1H, halfW, row1H);
            drawHeading(stream, bold, x, y, "1. Incident Name");
            writeCellText(stream, regular, x + CELL_PADDING, y - CELL_PADDING - HEADING_FONT_SIZE - 16f,
                    safe(context == null ? "" : context.getIncidentName()));
            drawOpPeriod(stream, bold, regular, x + halfW, y - row1H, halfW, row1H, context);
            y -= row1H;

            // Footer row: Prepared By
            float footerH = 50f;
            float footerY = formBot;

            // Row 2 (middle): org chart
            float chartH = y - footerY - footerH;
            drawOrgChart(stream, bold, regular, x, footerY + footerH, fullW, chartH, chart, tasks);

            // Footer: Prepared by
            drawFooter(stream, bold, regular, x, footerY, fullW, footerH, form);
        }
    }

    // -----------------------------------------------------------------------
    // Section sub-renderers
    // -----------------------------------------------------------------------

    private void drawOpPeriod(PDPageContentStream stream, PDType1Font bold, PDType1Font regular,
                               float x, float y, float width, float height,
                               IncidentContext ctx) throws IOException {
        drawHeading(stream, bold, x, y + height, "2. Operational Period");
        float labelY = y + height - CELL_PADDING - HEADING_FONT_SIZE - 14f;
        float halfW  = width / 2f;
        drawInlinePair(stream, bold, regular, x + CELL_PADDING, labelY,
                "Date From", formatDate(ctx == null ? null : ctx.getOperationalPeriodStart()));
        drawInlinePair(stream, bold, regular, x + CELL_PADDING + halfW, labelY,
                "Date To",   formatDate(ctx == null ? null : ctx.getOperationalPeriodEnd()));
        drawInlinePair(stream, bold, regular, x + CELL_PADDING, labelY - 16f,
                "Time From", formatTime(ctx == null ? null : ctx.getOperationalPeriodStart()));
        drawInlinePair(stream, bold, regular, x + CELL_PADDING + halfW, labelY - 16f,
                "Time To",   formatTime(ctx == null ? null : ctx.getOperationalPeriodEnd()));
    }

    private void drawFooter(PDPageContentStream stream, PDType1Font bold, PDType1Font regular,
                             float x, float y, float width, float height,
                             Ics207Form form) throws IOException {
        drawCell(stream, x, y, width, height);
        drawHeading(stream, bold, x, y + height, "4. Prepared by");
        float lineY1 = y + height - CELL_PADDING - HEADING_FONT_SIZE - 14f;
        float lineY2 = lineY1 - 16f;
        float col2X  = x + width * 0.4f;
        drawInlinePair(stream, bold, regular, x + CELL_PADDING, lineY1,
                "Name", safe(form.getPreparedByName()));
        drawInlinePair(stream, bold, regular, col2X, lineY1,
                "Position/Title", safe(form.getPreparedByPositionTitle()));
        drawInlinePair(stream, bold, regular, x + CELL_PADDING, lineY2,
                "Date/Time", formatDateTime(form.getPreparedDateTime()));
        drawInlinePair(stream, bold, regular, col2X, lineY2, "Signature", "");

        // Bottom band: "IAP Page: X" left-aligned, "ICS 207" right-aligned
        String iapLabel = "IAP Page: " + safe(form.getIapPage());
        writeCellText(stream, bold, x + CELL_PADDING, y + 10f, iapLabel);
        String formLabel = "ICS 207";
        float formLabelW = bold.getStringWidth(formLabel) / 1000f * BODY_FONT_SIZE;
        writeCellText(stream, bold, x + width - formLabelW - CELL_PADDING, y + 10f, formLabel);
    }

    // -----------------------------------------------------------------------
    // Graphical org chart (section 3)
    // -----------------------------------------------------------------------

    private void drawOrgChart(PDPageContentStream stream, PDType1Font bold, PDType1Font regular,
                               float sX, float sY, float width, float height,
                               OrganizationalChart chart, List<SarTaskAssignment> tasks) throws IOException {
        drawCell(stream, sX, sY, width, height);
        drawHeading(stream, bold, sX, sY + height, "3. Organization Chart");

        final float LEFT   = sX + CELL_PADDING;
        final float CWIDTH = width - 2f * CELL_PADDING;
        final float TOP    = sY + height - CELL_PADDING - HEADING_FONT_SIZE - 8f;

        // --- IC box ---
        List<String> icNames = chart.getIncidentCommanders();
        List<String> nonBlankIc = new ArrayList<>();
        if (icNames != null) {
            for (String n : icNames) { if (!safe(n).isBlank()) nonBlankIc.add(n); }
        }
        boolean isUC     = nonBlankIc.size() > 1;
        String icLabel   = isUC ? "Unified Command" : "Incident Commander";
        String icNameLine = String.join(", ", nonBlankIc);

        float icBoxW  = Math.min(180f, CWIDTH * 0.36f);
        float icBoxH  = 32f;
        float icCX    = LEFT + CWIDTH * 0.42f;
        float icLeft  = icCX - icBoxW / 2f;
        float icTop   = TOP - 4f;
        float icBot   = icTop - icBoxH;

        // --- Command staff (always shown) ---
        List<String[]> cmdStaff = buildCmdStaff(chart);

        float cmdBot = icBot;
        {
            float sBoxH = 18f, sGap = 3f;
            float sLeft = icLeft + icBoxW + 14f;
            float sBoxW = Math.max(36f, Math.min(140f, LEFT + CWIDTH - sLeft - 4f));
            float vLineX = sLeft - 6f;
            float vTop   = icTop - 4f;
            float vBot   = vTop - (cmdStaff.size() - 1) * (sBoxH + sGap) - sBoxH / 2f;

            float icMidY = icBot + icBoxH / 2f;
            stream.moveTo(icLeft + icBoxW, icMidY);
            stream.lineTo(vLineX, icMidY);
            stream.stroke();
            stream.moveTo(vLineX, vBot);
            stream.lineTo(vLineX, vTop);
            stream.stroke();

            for (int i = 0; i < cmdStaff.size(); i++) {
                float sTop  = icTop - 4f - i * (sBoxH + sGap);
                float sBotY = sTop - sBoxH;
                cmdBot = Math.min(cmdBot, sBotY);
                float midY = sTop - sBoxH / 2f;
                stream.moveTo(vLineX, midY);
                stream.lineTo(sLeft, midY);
                stream.stroke();
                stream.addRect(sLeft, sBotY, sBoxW, sBoxH);
                stream.stroke();
                drawOrgText(stream, bold,    7f, cmdStaff.get(i)[0], sLeft + 2f, sTop - 9f);
                drawOrgText(stream, regular, 7f, truncate(cmdStaff.get(i)[1], (int) (sBoxW / 4.3f)),
                            sLeft + 2f, sTop - 17f);
            }
        }

        // IC box on top of connectors
        stream.addRect(icLeft, icBot, icBoxW, icBoxH);
        stream.stroke();
        drawCentered(stream, bold,    7f, icLabel,                                     icLeft, icTop - 9f,  icBoxW);
        drawCentered(stream, regular, 7f, truncate(icNameLine, (int) (icBoxW / 4.5f)), icLeft, icTop - 18f, icBoxW);

        // --- Vertical stem from IC box bottom to section bar ---
        float sBarY = Math.min(cmdBot, icBot) - 14f;
        stream.moveTo(icCX, icBot);
        stream.lineTo(icCX, sBarY);
        stream.stroke();

        // --- Four section boxes ---
        float secW    = CWIDTH / 4f;
        float secBoxH = 32f;
        stream.moveTo(LEFT + secW / 2f, sBarY);
        stream.lineTo(LEFT + CWIDTH - secW / 2f, sBarY);
        stream.stroke();

        float secBoxTop = sBarY - 12f;
        float secBoxBot = secBoxTop - secBoxH;

        String[] secTitles = {"Operations", "Planning", "Logistics", "Finance/Admin"};
        String[] secChiefs = {
            safe(chart.getOperationsSectionChiefName()),
            safe(chart.getPlanningSectionChiefName()),
            safe(chart.getLogisticsSectionChiefName()),
            safe(chart.getFinanceAdminSectionChiefName())
        };

        float[] secCX = new float[4];
        for (int i = 0; i < 4; i++) {
            float secLeft = LEFT + i * secW;
            secCX[i] = secLeft + secW / 2f;
            stream.moveTo(secCX[i], sBarY);
            stream.lineTo(secCX[i], secBoxTop);
            stream.stroke();
            float bL = secLeft + 4f, bW = secW - 8f;
            stream.addRect(bL, secBoxBot, bW, secBoxH);
            stream.stroke();
            drawCentered(stream, bold, 7f, secTitles[i],    bL, secBoxTop - 9f,  bW);
            drawCentered(stream, bold, 7f, "Section Chief", bL, secBoxTop - 17f, bW);
            if (!secChiefs[i].isBlank()) {
                drawCentered(stream, regular, 7f, truncate(secChiefs[i], (int) (bW / 4.3f)),
                             bL, secBoxTop - 26f, bW);
            }
        }

        // --- Sub-unit boxes below each section ---
        float subH   = 18f;
        float subGap = 2f;
        float subTopStart = secBoxBot - 6f;

        // Operations sub-units
        drawSubUnits(stream, bold, regular, LEFT, secCX[0], secBoxBot, secW, subTopStart, subH, subGap,
                buildOpsSubUnits(chart, tasks), sY);

        // Planning sub-units
        drawSubUnits(stream, bold, regular, LEFT + secW, secCX[1], secBoxBot, secW, subTopStart, subH, subGap,
                buildPlanningSubUnits(chart), sY);

        // Logistics sub-units
        drawSubUnits(stream, bold, regular, LEFT + 2f * secW, secCX[2], secBoxBot, secW, subTopStart, subH, subGap,
                buildLogisticsSubUnits(chart), sY);

        // Finance/Admin sub-units
        drawSubUnits(stream, bold, regular, LEFT + 3f * secW, secCX[3], secBoxBot, secW, subTopStart, subH, subGap,
                buildFinanceSubUnits(chart), sY);
    }

    /** Draws vertically-stacked sub-unit boxes connected to the section box above. */
    private void drawSubUnits(PDPageContentStream stream, PDType1Font bold, PDType1Font regular,
                               float sectionLeft, float sectionCX, float sectionBoxBot,
                               float secW, float subTopStart, float subH, float subGap,
                               List<String[]> subUnits, float minY) throws IOException {
        if (subUnits.isEmpty()) return;
        float bL = sectionLeft + 4f, bW = secW - 8f;
        float connectorTop = subTopStart;
        stream.moveTo(sectionCX, sectionBoxBot);
        stream.lineTo(sectionCX, connectorTop);
        stream.stroke();

        float curTop = connectorTop;
        for (int i = 0; i < subUnits.size(); i++) {
            String[] unit = subUnits.get(i);
            float botY = curTop - subH;
            if (botY < minY + 4f) break; // don't draw below section bottom
            stream.addRect(bL, botY, bW, subH);
            stream.stroke();
            drawCentered(stream, bold,    6.5f, unit[0],     bL, curTop - 8f,  bW);
            drawCentered(stream, regular, 6.5f, truncate(unit[1], (int) (bW / 3.9f)), bL, curTop - 15f, bW);
            if (i < subUnits.size() - 1) {
                stream.moveTo(sectionCX, botY);
                stream.lineTo(sectionCX, botY - subGap);
                stream.stroke();
            }
            curTop = botY - subGap;
        }
    }

    // -----------------------------------------------------------------------
    // Sub-unit list builders
    // -----------------------------------------------------------------------

    private List<String[]> buildCmdStaff(OrganizationalChart chart) {
        List<String[]> list = new ArrayList<>();
        list.add(new String[]{"Safety Officer",       safe(chart.getSafetyOfficerName())});
        list.add(new String[]{"Public Info. Officer", safe(chart.getPublicInformationOfficerName())});
        list.add(new String[]{"Liaison Officer",      safe(chart.getLiaisonOfficerName())});
        for (OrgChartEntry e : chart.getAdditionalPositions()) {
            if (e.getSection() == OrgChartEntry.Section.COMMAND_STAFF) {
                list.add(new String[]{safe(e.getTitle()), safe(e.getName())});
            }
        }
        return list;
    }

    private List<String[]> buildOpsSubUnits(OrganizationalChart chart, List<SarTaskAssignment> tasks) {
        List<String[]> list = new ArrayList<>();
        addIfNamed(list, "Staging Area Mgr", chart.getStagingAreaManagerName());
        for (SarTaskAssignment t : tasks) {
            String label = safe(t.getResourceIdentifier()).isBlank()
                    ? (safe(t.getAssignmentTeamNumber()).isBlank() ? "Task" : safe(t.getAssignmentTeamNumber()))
                    : safe(t.getResourceIdentifier());
            int people = countPeople(t);
            list.add(new String[]{truncate(label, 14),
                    people == 1 ? "1 person" : people + " people"});
        }
        for (OrgChartEntry e : chart.getAdditionalPositions()) {
            if (e.getSection() == OrgChartEntry.Section.OPERATIONS) {
                list.add(new String[]{safe(e.getTitle()), safe(e.getName())});
            }
        }
        return list;
    }



    private List<String[]> buildPlanningSubUnits(OrganizationalChart chart) {
        List<String[]> list = new ArrayList<>();
        addIfNamed(list, "Doc Unit Leader",   chart.getDocumentationUnitLeaderName());
        addIfNamed(list, "Resources Unit Ldr", chart.getResourcesUnitLeaderName());
        addIfNamed(list, "Situation Unit Ldr", chart.getSituationUnitLeaderName());
        addIfNamed(list, "Demob Unit Ldr",    chart.getDemobilizationUnitLeaderName());
        for (OrgChartEntry e : chart.getAdditionalPositions()) {
            if (e.getSection() == OrgChartEntry.Section.PLANNING) {
                list.add(new String[]{safe(e.getTitle()), safe(e.getName())});
            }
        }
        return list;
    }

    private List<String[]> buildLogisticsSubUnits(OrganizationalChart chart) {
        List<String[]> list = new ArrayList<>();
        addIfNamed(list, "Comms Unit Leader",   chart.getCommunicationsUnitLeaderName());
        addIfNamed(list, "Comms Technician",    chart.getCommunicationsTechnicianName());
        addIfNamed(list, "Supply Unit Ldr",     chart.getSupplyUnitLeaderName());
        addIfNamed(list, "Facilities Unit Ldr", chart.getFacilitiesUnitLeaderName());
        addIfNamed(list, "Gnd Support Unit Ldr", chart.getGroundSupportUnitLeaderName());
        addIfNamed(list, "Food Unit Ldr",       chart.getFoodUnitLeaderName());
        for (OrgChartEntry e : chart.getAdditionalPositions()) {
            if (e.getSection() == OrgChartEntry.Section.LOGISTICS) {
                list.add(new String[]{safe(e.getTitle()), safe(e.getName())});
            }
        }
        return list;
    }

    private List<String[]> buildFinanceSubUnits(OrganizationalChart chart) {
        List<String[]> list = new ArrayList<>();
        addIfNamed(list, "Time Unit Ldr",          chart.getTimeUnitLeaderName());
        addIfNamed(list, "Procurement Unit Ldr",   chart.getProcurementUnitLeaderName());
        addIfNamed(list, "Comp/Claims Unit Ldr",   chart.getCompClaimsUnitLeaderName());
        addIfNamed(list, "Cost Unit Ldr",          chart.getCostUnitLeaderName());
        for (OrgChartEntry e : chart.getAdditionalPositions()) {
            if (e.getSection() == OrgChartEntry.Section.FINANCE_ADMIN) {
                list.add(new String[]{safe(e.getTitle()), safe(e.getName())});
            }
        }
        return list;
    }

    /** Adds a two-element array {title, name} only when {@code name} is non-blank. */
    private static void addIfNamed(List<String[]> list, String title, String name) {
        if (name != null && !name.isBlank()) {
            list.add(new String[]{title, name});
        }
    }

    // -----------------------------------------------------------------------
    // Drawing helpers
    // -----------------------------------------------------------------------

    private void drawCell(PDPageContentStream stream, float x, float y, float width, float height) throws IOException {
        stream.addRect(x, y, width, height);
        stream.stroke();
    }

    private void drawHeading(PDPageContentStream stream, PDType1Font bold,
                              float x, float topY, String heading) throws IOException {
        stream.beginText();
        stream.setFont(bold, HEADING_FONT_SIZE);
        stream.newLineAtOffset(x + CELL_PADDING, topY - CELL_PADDING - HEADING_FONT_SIZE);
        stream.showText(heading);
        stream.endText();
    }

    private void writeCellText(PDPageContentStream stream, PDType1Font font,
                                float x, float y, String value) throws IOException {
        String s = safe(value);
        if (s.isBlank()) return;
        stream.beginText();
        stream.setFont(font, BODY_FONT_SIZE);
        stream.newLineAtOffset(x, y);
        stream.showText(s);
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
        stream.newLineAtOffset(x + bold.getStringWidth(label + ": ") / 1000f * BODY_FONT_SIZE, y);
        stream.showText(safe(value));
        stream.endText();
    }

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

    // -----------------------------------------------------------------------
    // Utility
    // -----------------------------------------------------------------------

    private static String safe(String v)  { return v == null ? "" : stripControlChars(v); }

    private static String stripControlChars(String s) {
        return s.chars()
                .filter(c -> c >= 0x20 || c == '\t')
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, Math.max(0, max - 1)) + "…";
    }

    private static String formatDate(LocalDateTime dt) {
        return dt == null ? "" : dt.format(DATE_FMT);
    }

    private static String formatTime(LocalDateTime dt) {
        return dt == null ? "" : dt.format(TIME_FMT);
    }

    private static String formatDateTime(java.time.LocalDateTime dt) {
        return dt == null ? "" : dt.format(DATE_TIME_FMT);
    }
}
