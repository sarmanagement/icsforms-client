package org.sarmanagement.icsforms.pdf;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.sarmanagement.icsforms.model.SarTaskAssignment;
import org.sarmanagement.icsforms.model.SarTaskResource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared PDF layout helpers for first-cut structured ICS exports.
 */
abstract class AbstractPdfRenderer {
    private static final float MARGIN = 50f;
    private static final float LEADING = 15f;
    private static final float HEADER_FONT_SIZE = 14f;
    private static final float BODY_FONT_SIZE = 10f;
    private static final float BLOCK_PADDING = 8f;
    private static final float BLOCK_SPACING = 10f;
    protected static final float FORM_MARGIN = 36f;
    protected static final float FORM_HEADER_HEIGHT = 14f;

    /**
     * Writes a centered header line plus bordered content blocks across one or more pages.
     *
     * @param document target document.
     * @param formNumber form number label.
     * @param title document title.
     * @param blocks content blocks.
     * @throws IOException when PDF output fails.
     */
    protected void writeDocument(PDDocument document, String formNumber, String title, List<List<String>> blocks) throws IOException {
        PDPage page = new PDPage(PDRectangle.LETTER);
        document.addPage(page);
        PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        PDPageContentStream stream = new PDPageContentStream(document, page);
        float pageWidth = page.getMediaBox().getWidth();
        float y = page.getMediaBox().getHeight() - MARGIN;
        String header = (formNumber + " " + title).toUpperCase();
        float headerWidth = bold.getStringWidth(header) / 1000f * HEADER_FONT_SIZE;
        stream.beginText();
        stream.setFont(bold, HEADER_FONT_SIZE);
        stream.newLineAtOffset((pageWidth - headerWidth) / 2f, y);
        stream.showText(header);
        stream.endText();
        y -= LEADING * 2;

        for (List<String> block : blocks) {
            List<String> wrapped = new ArrayList<>();
            for (String rawLine : block) {
                wrapped.addAll(wrap(rawLine, 88));
            }
            float blockHeight = Math.max(LEADING + (BLOCK_PADDING * 2), wrapped.size() * LEADING + (BLOCK_PADDING * 2));
            if (y - blockHeight < MARGIN) {
                stream.close();
                page = new PDPage(PDRectangle.LETTER);
                document.addPage(page);
                stream = new PDPageContentStream(document, page);
                y = page.getMediaBox().getHeight() - MARGIN;
            }
            float boxTop = y;
            float boxBottom = y - blockHeight;
            stream.addRect(MARGIN, boxBottom, pageWidth - (MARGIN * 2), blockHeight);
            stream.stroke();

            stream.beginText();
            stream.setFont(font, BODY_FONT_SIZE);
            stream.newLineAtOffset(MARGIN + BLOCK_PADDING, boxTop - BLOCK_PADDING - BODY_FONT_SIZE);
            for (String line : wrapped) {
                stream.showText(line);
                stream.newLineAtOffset(0, -LEADING);
            }
            stream.endText();
            y = boxBottom - BLOCK_SPACING;
        }
        stream.close();
    }

    /**
     * Counts the people in a SAR task's resource list, excluding the primary task resource
     * (which is typically equipment such as a canine or vehicle).
     *
     * @param task SAR task assignment.
     * @return number of human resources assigned to the task.
     */
    protected int countPeople(SarTaskAssignment task) {
        if (task.getResourcesAssigned() == null) {
            return 0;
        }
        String taskResourceId = (task.getResourceIdentifier() == null ? "" : task.getResourceIdentifier()).trim().toLowerCase();
        int count = 0;
        for (SarTaskResource r : task.getResourcesAssigned()) {
            if (r == null) {
                continue;
            }
            String name = (r.getName() == null ? "" : r.getName()).trim().toLowerCase();
            String function = (r.getFunction() == null ? "" : r.getFunction()).trim();
            boolean isPrimary = !taskResourceId.isBlank()
                    && name.equals(taskResourceId)
                    && (function.isBlank() || "resource".equalsIgnoreCase(function));
            if (isPrimary) {
                continue;
            }
            // Exclude equipment/canine resources from the personnel count.
            org.sarmanagement.icsforms.model.TCardType ct = r.getCardType();
            if (ct != null && ct != org.sarmanagement.icsforms.model.TCardType.PERSONNEL
                    && ct != org.sarmanagement.icsforms.model.TCardType.CREW
                    && ct != org.sarmanagement.icsforms.model.TCardType.GENERIC) {
                continue;
            }
            count++;
        }
        return count;
    }

    /**
     * Wraps a line to a simple character width for stable smoke-test-friendly exports.
     *
     * @param text text to wrap.
     * @param maxWidth maximum characters per line.
     * @return wrapped lines.
     */
    protected List<String> wrap(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        String normalized = text == null ? "" : text;
        if (normalized.length() <= maxWidth) {
            lines.add(normalized);
            return lines;
        }
        String[] words = normalized.split("\s+");
        StringBuilder current = new StringBuilder();
        for (String word : words) {
            if (current.length() == 0) {
                current.append(word);
            } else if (current.length() + 1 + word.length() <= maxWidth) {
                current.append(' ').append(word);
            } else {
                lines.add(current.toString());
                current = new StringBuilder(word);
            }
        }
        if (current.length() > 0) {
            lines.add(current.toString());
        }
        return lines;
    }

    /**
     * Returns the shared printable form frame for bordered ICS/SAR documents.
     *
     * @param page PDF page.
     * @return shared form layout bounds.
     */
    protected FormLayout formLayout(PDPage page) {
        float width = page.getMediaBox().getWidth() - (FORM_MARGIN * 2f);
        float top = page.getMediaBox().getHeight() - FORM_MARGIN - FORM_HEADER_HEIGHT;
        return new FormLayout(FORM_MARGIN, FORM_MARGIN, width, top - FORM_MARGIN, top + FORM_HEADER_HEIGHT);
    }

    /**
     * Draws the shared heading line above the printable form.
     *
     * @param stream page stream.
     * @param bold bold font.
     * @param layout shared form layout.
     * @param formNumber form number text.
     * @param title title text.
     * @throws IOException when PDF output fails.
     */
    protected void drawFormHeader(PDPageContentStream stream, PDType1Font bold, FormLayout layout,
                                  String formNumber, String title) throws IOException {
        String header = formNumber + " " + title;
        float headerWidth = bold.getStringWidth(header) / 1000f * HEADER_FONT_SIZE;
        stream.beginText();
        stream.setFont(bold, HEADER_FONT_SIZE);
        stream.newLineAtOffset((PDRectangle.LETTER.getWidth() - headerWidth) / 2f, layout.headerBaseline());
        stream.showText(header);
        stream.endText();
    }

    /**
     * Draws the shared outer border that fills the printable form area.
     *
     * @param stream page stream.
     * @param layout shared form layout.
     * @throws IOException when PDF output fails.
     */
    protected void drawFormFrame(PDPageContentStream stream, FormLayout layout) throws IOException {
        stream.addRect(layout.x(), layout.y(), layout.width(), layout.height());
        stream.stroke();
    }

    /**
     * Expands one designated row to consume any unused form height while leaving
     * the other rows at their preferred heights.
     *
     * @param totalHeight total height available for the rows.
     * @param expandableRowIndex zero-based index of the row that should absorb extra space.
     * @param preferredHeights preferred heights for each row.
     * @return resolved row heights.
     */
    protected float[] expandRowToFill(float totalHeight, int expandableRowIndex, float... preferredHeights) {
        float[] resolved = preferredHeights.clone();
        if (expandableRowIndex < 0 || expandableRowIndex >= resolved.length) {
            return resolved;
        }
        float usedHeight = 0f;
        for (float height : resolved) {
            usedHeight += height;
        }
        if (usedHeight < totalHeight) {
            resolved[expandableRowIndex] += totalHeight - usedHeight;
        }
        return resolved;
    }

    protected void drawPreparedByMetadataSection(PDPageContentStream stream,
                                                 PDType1Font bold,
                                                 PDType1Font regular,
                                                 float bodyFontSize,
                                                 float headingFontSize,
                                                 float cellPadding,
                                                 float x,
                                                 float y,
                                                 float width,
                                                 float height,
                                                 float footerBandHeight,
                                                 String heading,
                                                 String name,
                                                 String positionTitle,
                                                 String signature,
                                                 String formLabel,
                                                 String iapPage,
                                                 String dateTime) throws IOException {
        drawPreparedByMetadataSection(stream, bold, regular, bodyFontSize, headingFontSize, cellPadding,
                x, y, width, height, footerBandHeight, 14f, 0.20f, 0.58f, 0.58f,
                heading, name, positionTitle, signature, formLabel, iapPage, dateTime);
    }

    protected void drawPreparedByMetadataSection(PDPageContentStream stream,
                                                 PDType1Font bold,
                                                 PDType1Font regular,
                                                 float bodyFontSize,
                                                 float headingFontSize,
                                                 float cellPadding,
                                                 float x,
                                                 float y,
                                                 float width,
                                                 float height,
                                                 float footerBandHeight,
                                                 float topRowOffset,
                                                 String heading,
                                                 String name,
                                                 String positionTitle,
                                                 String signature,
                                                 String formLabel,
                                                 String iapPage,
                                                 String dateTime) throws IOException {
        drawPreparedByMetadataSection(stream, bold, regular, bodyFontSize, headingFontSize, cellPadding,
                x, y, width, height, footerBandHeight, topRowOffset, 0.20f, 0.58f, 0.58f,
                heading, name, positionTitle, signature, formLabel, iapPage, dateTime);
    }

    protected void drawPreparedByMetadataSection(PDPageContentStream stream,
                                                 PDType1Font bold,
                                                 PDType1Font regular,
                                                 float bodyFontSize,
                                                 float headingFontSize,
                                                 float cellPadding,
                                                 float x,
                                                 float y,
                                                 float width,
                                                 float height,
                                                 float footerBandHeight,
                                                 float topRowOffset,
                                                 float nameEndRatio,
                                                 float positionEndRatio,
                                                 String heading,
                                                 String name,
                                                 String positionTitle,
                                                 String signature,
                                                 String formLabel,
                                                 String iapPage,
                                                 String dateTime) throws IOException {
        drawPreparedByMetadataSection(stream, bold, regular, bodyFontSize, headingFontSize, cellPadding,
                x, y, width, height, footerBandHeight, topRowOffset, nameEndRatio, positionEndRatio, positionEndRatio,
                heading, name, positionTitle, signature, formLabel, iapPage, dateTime);
    }

    protected void drawPreparedByMetadataSection(PDPageContentStream stream,
                                                 PDType1Font bold,
                                                 PDType1Font regular,
                                                 float bodyFontSize,
                                                 float headingFontSize,
                                                 float cellPadding,
                                                 float x,
                                                 float y,
                                                 float width,
                                                 float height,
                                                 float footerBandHeight,
                                                 float topRowOffset,
                                                 float nameEndRatio,
                                                 float positionEndRatio,
                                                 float lowerBandRightStartRatio,
                                                 String heading,
                                                 String name,
                                                 String positionTitle,
                                                 String signature,
                                                 String formLabel,
                                                 String iapPage,
                                                 String dateTime) throws IOException {
        stream.addRect(x, y, width, height);
        stream.stroke();
        drawMetadataHeading(stream, bold, headingFontSize, cellPadding, x, y + height, safeText(heading));

        float footerTop = y + footerBandHeight;
        float footerContentY = footerTop - cellPadding - bodyFontSize;
        float topRowY = footerTop + topRowOffset;

        float nameStart = x + cellPadding;
        float nameEnd = x + (width * nameEndRatio);
        float positionStart = nameEnd + cellPadding;
        float positionEnd = x + (width * positionEndRatio);
        float signatureStart = positionEnd + cellPadding;
        float signatureEnd = x + width - cellPadding;
        float lowerBandRightStart = x + (width * lowerBandRightStartRatio) + cellPadding;
        float minFooterCellWidth = width / 8f;
        float footerBandAvailableWidth = Math.max(minFooterCellWidth * 2f, lowerBandRightStart - x - cellPadding);
        float formBoxWidth = Math.max(minFooterCellWidth, fittedTextWidth(bold, bodyFontSize, safeText(formLabel)) + (cellPadding * 2f));
        float iapBoxWidth = Math.max(minFooterCellWidth,
                inlineTextWidth(bold, regular, bodyFontSize, "IAP Page", iapPage) + (cellPadding * 2f));
        float combinedFooterWidth = formBoxWidth + iapBoxWidth;
        if (combinedFooterWidth > footerBandAvailableWidth) {
            float overflow = combinedFooterWidth - footerBandAvailableWidth;
            float formFlex = Math.max(0f, formBoxWidth - minFooterCellWidth);
            float iapFlex = Math.max(0f, iapBoxWidth - minFooterCellWidth);
            float totalFlex = formFlex + iapFlex;
            if (totalFlex > 0f) {
                formBoxWidth -= overflow * (formFlex / totalFlex);
                iapBoxWidth -= overflow * (iapFlex / totalFlex);
            }
            formBoxWidth = Math.max(minFooterCellWidth, formBoxWidth);
            iapBoxWidth = Math.max(minFooterCellWidth, iapBoxWidth);
        }

        writeInlineHeadingValueWithinWidth(stream, bold, regular, bodyFontSize,
                nameStart, topRowY, Math.max(0f, nameEnd - nameStart), "Name:", name);
        writeInlineHeadingValueWithinWidth(stream, bold, regular, bodyFontSize,
                positionStart, topRowY, Math.max(0f, positionEnd - positionStart), "Position/Title:", positionTitle);
        writeInlineHeadingValueWithinWidth(stream, bold, regular, bodyFontSize,
                signatureStart, topRowY, Math.max(0f, signatureEnd - signatureStart), "Signature:", signature);

        stream.addRect(x, y, formBoxWidth, footerBandHeight);
        stream.stroke();
        stream.addRect(x + formBoxWidth, y, iapBoxWidth, footerBandHeight);
        stream.stroke();

        writeFittedText(stream, bold, bodyFontSize, x + cellPadding, footerContentY,
                Math.max(0f, formBoxWidth - (cellPadding * 2f)), formLabel);
        writeInlineHeadingValue(stream, bold, regular, bodyFontSize,
                x + formBoxWidth + cellPadding, footerContentY, "IAP Page", iapPage);
        writeInlineHeadingValueWithinWidth(stream, bold, regular, bodyFontSize,
                lowerBandRightStart, footerContentY, Math.max(0f, signatureEnd - lowerBandRightStart), "Date/Time:", dateTime);
    }

    protected String addPageOffset(String startPage, int offset) {
        String normalized = safeText(startPage).trim();
        if (normalized.isBlank()) {
            return "";
        }
        try {
            return String.valueOf(Integer.parseInt(normalized) + Math.max(0, offset));
        } catch (NumberFormatException e) {
            return normalized;
        }
    }

    protected String formPageLabel(String formNumber, int pageNumber, int totalPages) {
        return totalPages > 1 ? formNumber + ", Page " + pageNumber + " of " + totalPages : formNumber;
    }

    private void drawMetadataHeading(PDPageContentStream stream,
                                     PDType1Font bold,
                                     float headingFontSize,
                                     float cellPadding,
                                     float x,
                                     float topY,
                                     String heading) throws IOException {
        stream.beginText();
        stream.setFont(bold, headingFontSize);
        stream.newLineAtOffset(x + cellPadding, topY - cellPadding - headingFontSize);
        stream.showText(heading);
        stream.endText();
    }

    private void writeInlineHeadingValue(PDPageContentStream stream,
                                         PDType1Font bold,
                                         PDType1Font regular,
                                         float bodyFontSize,
                                         float x,
                                         float y,
                                         String label,
                                         String value) throws IOException {
        String safeLabel = safeText(label);
        String safeValue = safeText(value);
        stream.beginText();
        stream.setFont(bold, bodyFontSize);
        stream.newLineAtOffset(x, y);
        stream.showText(safeLabel);
        float labelWidth = bold.getStringWidth(safeLabel) / 1000f * bodyFontSize;
        stream.setFont(regular, bodyFontSize);
        stream.newLineAtOffset(labelWidth + 4f, 0);
        if (!safeValue.isBlank()) {
            stream.showText(safeValue);
        }
        stream.endText();
    }

    private void writeInlineHeadingValueWithinWidth(PDPageContentStream stream,
                                                    PDType1Font bold,
                                                    PDType1Font regular,
                                                    float bodyFontSize,
                                                    float x,
                                                    float y,
                                                    float width,
                                                    String label,
                                                    String value) throws IOException {
        String safeLabel = safeText(label);
        stream.beginText();
        stream.setFont(bold, bodyFontSize);
        stream.newLineAtOffset(x, y);
        stream.showText(safeLabel);
        float labelWidth = bold.getStringWidth(safeLabel) / 1000f * bodyFontSize;
        stream.setFont(regular, bodyFontSize);
        stream.newLineAtOffset(labelWidth + 4f, 0);
        stream.showText(fitText(regular, safeText(value), bodyFontSize, Math.max(0f, width - labelWidth - 4f)));
        stream.endText();
    }

    private void writeFittedText(PDPageContentStream stream,
                                 PDType1Font font,
                                 float fontSize,
                                 float x,
                                 float y,
                                 float maxWidth,
                                 String value) throws IOException {
        stream.beginText();
        stream.setFont(font, fontSize);
        stream.newLineAtOffset(x, y);
        stream.showText(fitText(font, safeText(value), fontSize, maxWidth));
        stream.endText();
    }

    private float inlineTextWidth(PDType1Font bold,
                                  PDType1Font regular,
                                  float bodyFontSize,
                                  String label,
                                  String value) throws IOException {
        String safeLabel = safeText(label);
        String safeValue = safeText(value);
        float width = fittedTextWidth(bold, bodyFontSize, safeLabel);
        if (!safeValue.isBlank()) {
            width += 4f + fittedTextWidth(regular, bodyFontSize, safeValue);
        }
        return width;
    }

    private float fittedTextWidth(PDType1Font font, float fontSize, String value) throws IOException {
        return font.getStringWidth(safeText(value)) / 1000f * fontSize;
    }

    private String fitText(PDType1Font font, String value, float fontSize, float maxWidth) throws IOException {
        String normalized = safeText(value).trim();
        if (normalized.isBlank() || maxWidth <= 0f) {
            return "";
        }
        if (font.getStringWidth(normalized) / 1000f * fontSize <= maxWidth) {
            return normalized;
        }
        String ellipsis = "...";
        float ellipsisWidth = font.getStringWidth(ellipsis) / 1000f * fontSize;
        if (ellipsisWidth >= maxWidth) {
            return "";
        }
        for (int end = normalized.length() - 1; end > 0; end--) {
            String candidate = normalized.substring(0, end).trim() + ellipsis;
            if (font.getStringWidth(candidate) / 1000f * fontSize <= maxWidth) {
                return candidate;
            }
        }
        return "";
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    /**
     * Shared outer form bounds beneath the centered page heading.
     *
     * @param x left edge.
     * @param y bottom edge.
     * @param width form width.
     * @param height form height.
     * @param headerBaseline baseline for the centered header text.
     */
    protected record FormLayout(float x, float y, float width, float height, float headerBaseline) {
        float top() {
            return y + height;
        }
    }
}
