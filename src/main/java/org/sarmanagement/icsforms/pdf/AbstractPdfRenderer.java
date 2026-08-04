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

    /**
     * Writes wrapped lines across one or more pages.
     *
     * @param document target document.
     * @param title document title.
     * @param lines content lines.
     * @throws IOException when PDF output fails.
     */
    protected void writeLines(PDDocument document, String title, List<String> lines) throws IOException {
        PDPage page = new PDPage(PDRectangle.LETTER);
        document.addPage(page);
        PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        PDPageContentStream stream = new PDPageContentStream(document, page);
        float y = page.getMediaBox().getHeight() - MARGIN;
        stream.beginText();
        stream.setFont(bold, 14);
        stream.newLineAtOffset(MARGIN, y);
        stream.showText(title);
        stream.setFont(font, 10);
        stream.newLineAtOffset(0, -LEADING * 2);
        y -= LEADING * 2;

        for (String rawLine : lines) {
            for (String line : wrap(rawLine, 90)) {
                if (y <= MARGIN) {
                    stream.endText();
                    stream.close();
                    page = new PDPage(PDRectangle.LETTER);
                    document.addPage(page);
                    stream = new PDPageContentStream(document, page);
                    y = page.getMediaBox().getHeight() - MARGIN;
                    stream.beginText();
                    stream.setFont(font, 10);
                    stream.newLineAtOffset(MARGIN, y);
                }
                stream.showText(line);
                stream.newLineAtOffset(0, -LEADING);
                y -= LEADING;
            }
        }
        stream.endText();
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
}
