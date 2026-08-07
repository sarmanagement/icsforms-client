package org.sarmanagement.icsforms.pdf;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.Ics202Form;
import org.sarmanagement.icsforms.model.IncidentContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Structured PDF renderer for the Incident Objectives (ICS 202) form.
 */
public class Ics202PdfRenderer extends AbstractPdfRenderer implements PdfFormRenderer {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final float BODY_FONT_SIZE = 10f;
    private static final float HEADING_FONT_SIZE = 10f;
    private static final float LINE_HEIGHT = 12f;
    private static final float CELL_PADDING = 4f;

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
            renderDocument(document, data);
            document.save(outputFile.toFile());
        }
    }

    private void renderDocument(PDDocument document, AppData data) throws IOException {
        IncidentContext context = data.getIncidentContext();
        Ics202Form form = data.getForm202();
        List<OverflowSection> overflowSections = new ArrayList<>();

        PDPage page = new PDPage(PDRectangle.LETTER);
        document.addPage(page);
        try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
            PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            FormLayout layout = formLayout(page);

            drawFormHeader(stream, bold, layout, "ICS 202", "INCIDENT OBJECTIVES");
            drawFormFrame(stream, layout);
            float gridTop = layout.top();
            float gridBottom = layout.y();
            float gridHeight = layout.height();
            float pageWidth = layout.width();

            float row1 = 60f;
            float row4 = 54f;
            float row5 = 34f;
            float row6 = 40f;
            float row7 = 34f;
            float row8 = 56f;
            float row9 = 24f;
            float row3 = 80f;
            float row2 = gridHeight - (row1 + row3 + row4 + row4 + row5 + row6 + row7 + row8 + row9);
            if (row2 < 110f) {
                row2 = 110f;
                row3 = Math.max(48f, gridHeight - (row1 + row2 + row4 + row4 + row5 + row6 + row7 + row8 + row9));
            }

            float y = gridTop;
            float halfWidth = pageWidth / 2f;

            drawCell(stream, layout.x(), y - row1, halfWidth, row1);
            drawCell(stream, layout.x() + halfWidth, y - row1, halfWidth, row1);
            overflowSections.addAll(drawSection(stream, bold, regular, layout.x(), y - row1, halfWidth, row1,
                    "1. Incident Name", List.of(safe(context.getIncidentName())), "1. Incident Name"));
            drawOperationalPeriodSection(stream, bold, regular, layout.x() + halfWidth, y - row1, halfWidth, row1, context);
            y -= row1;

            drawCell(stream, layout.x(), y - row2, pageWidth, row2);
            List<String> objectiveLines = numberedLines(form.getObjectives());
            if (objectiveLines.isEmpty()) {
                objectiveLines = List.of("");
            }
            overflowSections.addAll(drawSection(stream, bold, regular, layout.x(), y - row2, pageWidth, row2,
                    "3. Objectives", objectiveLines, "3. Objectives"));
            y -= row2;

            drawCell(stream, layout.x(), y - row3, pageWidth, row3);
            overflowSections.addAll(drawSection(stream, bold, regular, layout.x(), y - row3, pageWidth, row3,
                    "4. Operational Period Command Emphasis", wrap(form.getCommandEmphasis(), 92), "4. Operational Period Command Emphasis"));
            y -= row3;

            drawCell(stream, layout.x(), y - row4, pageWidth, row4);
            overflowSections.addAll(drawSection(stream, bold, regular, layout.x(), y - row4, pageWidth, row4,
                    "4. General Situational Awareness", wrap(form.getSituationalAwareness(), 92), "4. General Situational Awareness"));
            y -= row4;

            drawCell(stream, layout.x(), y - row5, pageWidth, row5);
            overflowSections.addAll(drawSection(stream, bold, regular, layout.x(), y - row5, pageWidth, row5,
                    "5. Site Safety Plan Required", List.of(form.isSiteSafetyPlanRequired() ? "Yes" : "No"), "5. Site Safety Plan Required"));
            y -= row5;

            drawCell(stream, layout.x(), y - row6, pageWidth, row6);
            List<String> formsLines = includedFormsLines(form.getIncidentActionPlanAttachments());
            overflowSections.addAll(drawSection(stream, bold, regular, layout.x(), y - row6, pageWidth, row6,
                    "6. Incident Action Plan (Included Forms)", formsLines, null));
            y -= row6;

            drawCell(stream, layout.x(), y - row7, pageWidth, row7);
            overflowSections.addAll(drawSection(stream, bold, regular, layout.x(), y - row7, pageWidth, row7,
                    "7. Prepared By", List.of(joinPreparedBy(context.getCurrentUser(), context.getCurrentUserPositionTitle())), "7. Prepared By"));
            y -= row7;

            // y is the current top of the big row
            float approvalBottom = y - row8;     // bottom of big approval cell
            float footerCellWidth = pageWidth / 8f;
            
            // Big outer cell (for “8. Approved by Incident Commander” block)
            drawCell(stream, layout.x(), approvalBottom, pageWidth, row8);
            
            // Bottom band cells (row9 tall, at bottom of big cell)
            drawCell(stream, layout.x(), approvalBottom, footerCellWidth, row9);                          // ICS 202
            drawCell(stream, layout.x() + footerCellWidth, approvalBottom, footerCellWidth, row9);        // IAP Page
            
            drawApprovalSection(stream, bold, regular, layout.x(), approvalBottom, pageWidth, row8, row9, form);
            
            y -= row8;  // move up for next row
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
            drawFormHeader(stream, bold, layout, "ICS 202", overflow.heading);
            drawFormFrame(stream, layout);
            drawTextBlock(stream, bold, regular, layout.x(), layout.y(), layout.width(), layout.height(), overflow.heading, overflow.lines);
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

    private void drawApprovalSection(PDPageContentStream stream, PDType1Font bold, PDType1Font regular,
                                     float x, float y, float width, float height,
                                     float footerHeight, Ics202Form form) throws IOException {
    
        // y is the BOTTOM of the big cell
        float cellBottom = y;
        float cellTop = y + height;                // top of the big cell
        float footerTop = cellBottom + footerHeight; // top of the footer band inside the big cell
    
        float footerCellWidth = width / 8f;
        float footerLeftWidth = width / 4f;
        float footerRightX = x + footerLeftWidth;
    
        // --- 1) Heading at the top of the big cell ---
        drawHeading(stream, bold, x, cellTop, "8. Approved By Incident Commander");
    
        // drawHeading uses: topY - CELL_PADDING - HEADING_FONT_SIZE
        float headingBaselineY = cellTop - CELL_PADDING - HEADING_FONT_SIZE;
    
        // --- 2) Name / Signature row below the heading ---
        float topRowY = headingBaselineY - LINE_HEIGHT; // adjust spacing as you like
    
        float nameX = x + CELL_PADDING;
        float signatureX = x + (width * 0.45f);
    
        drawInlinePair(stream, bold, regular, nameX, topRowY, "Name", safe(form.getApprovedByIncidentCommanderName()));
        drawInlinePair(stream, bold, regular, signatureX, topRowY, "Signature", "_______________________________");
    
        // --- 3) Bottom footer band: ICS 202 | IAP Page | Date/Time ---
        float footerContentY = footerTop - CELL_PADDING - BODY_FONT_SIZE;
    
        writeInlineHeadingValue(stream, bold, regular, x + CELL_PADDING, footerContentY, "ICS 202", "");
        writeInlineHeadingValue(stream, bold, regular, x + footerCellWidth + CELL_PADDING, footerContentY, "IAP Page", safe(form.getIapPage()));
        drawInlinePair(stream, bold, regular, signatureX, footerContentY, "Date/Time", formatDateTime(form.getApprovedDateTime()));
    
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
        return heading.startsWith("1.") || heading.startsWith("5.") || heading.startsWith("7.");
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
        stream.beginText();
        stream.setFont(bold, BODY_FONT_SIZE);
        stream.newLineAtOffset(x, y);
        stream.showText(label + ": ");
        stream.endText();

        stream.beginText();
        stream.setFont(regular, BODY_FONT_SIZE);
        stream.newLineAtOffset(x + 52f, y);
        stream.showText(safe(value));
        stream.endText();
    }

    private void drawCell(PDPageContentStream stream, float x, float y, float width, float height) throws IOException {
        stream.addRect(x, y, width, height);
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

    private List<String> numberedLines(List<String> values) {
        List<String> lines = new ArrayList<>();
        int index = 1;
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                lines.addAll(prefixWrapped(index++ + ". ", wrap(value, 90)));
            }
        }
        return lines;
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

    private List<String> includedFormsLines(List<String> attachments) {
        List<String> supported = List.of("ICS 202", "ICS 204", "Map packet");
        List<String> lines = new ArrayList<>();
        lines.add(checkLine("ICS 202", attachments));
        lines.add(checkLine("ICS 204", attachments));
        lines.add(checkLine("Map packet", attachments));
        List<String> additional = attachments == null ? List.of() : attachments.stream()
                .filter(item -> item != null && supported.stream().noneMatch(s -> s.equalsIgnoreCase(item)))
                .toList();
        if (!additional.isEmpty()) {
            lines.add("Additional: " + String.join(", ", additional));
        }
        return lines;
    }

    private String checkLine(String label, List<String> attachments) {
        boolean included = attachments != null && attachments.stream().anyMatch(item -> label.equalsIgnoreCase(item));
        return (included ? "[X] " : "[ ] ") + label;
    }

    private String joinPreparedBy(String name, String title) {
        if (safe(name).isBlank()) {
            return safe(title);
        }
        if (safe(title).isBlank()) {
            return safe(name);
        }
        return safe(name) + " / " + safe(title);
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
