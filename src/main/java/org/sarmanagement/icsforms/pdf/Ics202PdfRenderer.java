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

            float pageTop = page.getMediaBox().getHeight() - MARGIN;
            drawCenteredHeader(stream, bold, pageTop, "ICS 202", "INCIDENT OBJECTIVES");
            float gridTop = pageTop - HEADER_HEIGHT;
            float gridBottom = PAGE_BOTTOM_MARGIN;
            float gridHeight = gridTop - gridBottom;

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
            float halfWidth = PAGE_WIDTH / 2f;

            drawCell(stream, MARGIN, y - row1, halfWidth, row1);
            drawCell(stream, MARGIN + halfWidth, y - row1, halfWidth, row1);
            overflowSections.addAll(drawSection(stream, bold, regular, MARGIN, y - row1, halfWidth, row1,
                    "1. Incident Name", List.of(safe(context.getIncidentName())), "1. Incident Name"));
            drawOperationalPeriodSection(stream, bold, regular, MARGIN + halfWidth, y - row1, halfWidth, row1, context);
            y -= row1;

            drawCell(stream, MARGIN, y - row2, PAGE_WIDTH, row2);
            List<String> objectiveLines = numberedLines(form.getObjectives());
            if (objectiveLines.isEmpty()) {
                objectiveLines = List.of("");
            }
            overflowSections.addAll(drawSection(stream, bold, regular, MARGIN, y - row2, PAGE_WIDTH, row2,
                    "3. Objectives", objectiveLines, "3. Objectives"));
            y -= row2;

            drawCell(stream, MARGIN, y - row3, PAGE_WIDTH, row3);
            overflowSections.addAll(drawSection(stream, bold, regular, MARGIN, y - row3, PAGE_WIDTH, row3,
                    "4. Operational Period Command Emphasis", wrap(form.getCommandEmphasis(), 92), "4. Operational Period Command Emphasis"));
            y -= row3;

            drawCell(stream, MARGIN, y - row4, PAGE_WIDTH, row4);
            overflowSections.addAll(drawSection(stream, bold, regular, MARGIN, y - row4, PAGE_WIDTH, row4,
                    "4. General Situational Awareness", wrap(form.getSituationalAwareness(), 92), "4. General Situational Awareness"));
            y -= row4;

            drawCell(stream, MARGIN, y - row5, PAGE_WIDTH, row5);
            overflowSections.addAll(drawSection(stream, bold, regular, MARGIN, y - row5, PAGE_WIDTH, row5,
                    "5. Site Safety Plan Required", List.of(form.isSiteSafetyPlanRequired() ? "Yes" : "No"), "5. Site Safety Plan Required"));
            y -= row5;

            drawCell(stream, MARGIN, y - row6, PAGE_WIDTH, row6);
            List<String> formsLines = includedFormsLines(form.getIncidentActionPlanAttachments());
            overflowSections.addAll(drawSection(stream, bold, regular, MARGIN, y - row6, PAGE_WIDTH, row6,
                    "6. Incident Action Plan (Included Forms)", formsLines, null));
            y -= row6;

            drawCell(stream, MARGIN, y - row7, PAGE_WIDTH, row7);
            overflowSections.addAll(drawSection(stream, bold, regular, MARGIN, y - row7, PAGE_WIDTH, row7,
                    "7. Prepared By", List.of(joinPreparedBy(context.getCurrentUser(), context.getCurrentUserPositionTitle())), "7. Prepared By"));
            y -= row7;

            drawCell(stream, MARGIN, y - row8, PAGE_WIDTH, row8);
            drawApprovalSection(stream, bold, regular, MARGIN, y - row8, PAGE_WIDTH, row8, form);
            y -= row8;

            drawCell(stream, MARGIN, y - row9, PAGE_WIDTH / 2f, row9);
            drawFooterSection(stream, bold, regular, MARGIN, y - row9, PAGE_WIDTH / 2f, row9, form);
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
            drawCenteredHeader(stream, bold, pageTop, "ICS 202", overflow.heading);
            float boxTop = pageTop - HEADER_HEIGHT;
            float boxHeight = boxTop - PAGE_BOTTOM_MARGIN;
            drawCell(stream, MARGIN, PAGE_BOTTOM_MARGIN, PAGE_WIDTH, boxHeight);
            drawTextBlock(stream, bold, regular, MARGIN, PAGE_BOTTOM_MARGIN, PAGE_WIDTH, boxHeight, overflow.heading, overflow.lines);
        }
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

    private void drawOperationalPeriodSection(PDPageContentStream stream, PDType1Font bold, PDType1Font regular,
                                              float x, float y, float width, float height, IncidentContext context) throws IOException {
        drawHeading(stream, bold, x, y, "2. Operational Period");
        float labelY = y + height - CELL_PADDING - HEADING_FONT_SIZE - 14f;
        drawInlinePair(stream, bold, regular, x + CELL_PADDING, labelY, "Date From", formatDate(context.getOperationalPeriodStart()));
        drawInlinePair(stream, bold, regular, x + (width / 2f), labelY, "Date To", formatDate(context.getOperationalPeriodEnd()));
        drawInlinePair(stream, bold, regular, x + CELL_PADDING, labelY - 18f, "Time From", formatTime(context.getOperationalPeriodStart()));
        drawInlinePair(stream, bold, regular, x + (width / 2f), labelY - 18f, "Time To", formatTime(context.getOperationalPeriodEnd()));
    }

    private void drawApprovalSection(PDPageContentStream stream, PDType1Font bold, PDType1Font regular,
                                     float x, float y, float width, float height, Ics202Form form) throws IOException {
        drawHeading(stream, bold, x, y, "8. Approved By Incident Commander");
        float textY = y + height - CELL_PADDING - HEADING_FONT_SIZE - 14f;
        float leftWidth = width * 0.45f;
        float rightX = x + leftWidth + 18f;
        drawInlinePair(stream, bold, regular, x + CELL_PADDING, textY, "Name", safe(form.getApprovedByIncidentCommanderName()));
        drawInlinePair(stream, bold, regular, rightX, textY, "Date/Time", formatDateTime(form.getApprovedDateTime()));
        drawInlinePair(stream, bold, regular, rightX, textY - 18f, "Signature", "");
    }

    private void drawFooterSection(PDPageContentStream stream, PDType1Font bold, PDType1Font regular,
                                   float x, float y, float width, float height, Ics202Form form) throws IOException {
        float textY = y + height - CELL_PADDING - 12f;
        writeInlineHeadingValue(stream, bold, regular, x + CELL_PADDING, textY, "ICS 202", "");
        writeInlineHeadingValue(stream, bold, regular, x + (width / 2f), textY, "IAP Page", safe(form.getIapPage()));
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
        drawHeading(stream, bold, x, y, heading);
        float contentTop = y + height - CELL_PADDING - HEADING_FONT_SIZE - 12f;
        writeLines(stream, regular, x + CELL_PADDING, contentTop, lines);
    }

    private void drawHeading(PDPageContentStream stream, PDType1Font bold, float x, float y, String heading) throws IOException {
        stream.beginText();
        stream.setFont(bold, HEADING_FONT_SIZE);
        stream.newLineAtOffset(x + CELL_PADDING, y + CELL_PADDING + 2f);
        stream.showText(heading);
        stream.endText();
    }

    private float writeHeadingWithInlineContent(PDPageContentStream stream, PDType1Font bold, PDType1Font regular,
                                                float x, float y, float width, float height, String heading, List<String> lines) throws IOException {
        List<String> normalized = lines == null || lines.isEmpty() ? List.of("") : lines;
        drawHeading(stream, bold, x, y, heading);
        float headingWidth = bold.getStringWidth(heading) / 1000f * HEADING_FONT_SIZE;
        float inlineX = x + CELL_PADDING + headingWidth + 8f;
        float headingBaseline = y + CELL_PADDING + 2f;
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
        stream.showText(label + ":");
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
