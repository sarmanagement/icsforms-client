package org.sarmanagement.icsforms.pdf;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

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
