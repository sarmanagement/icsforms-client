/*
 * Copyright (C) 2026 SAR Management contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.pdf;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.sarmanagement.icsforms.model.AppData;

/**
 * Stateless, shared nine-block ICS 233-CG grid with lossless row pagination.
 */
final class Ics233PdfLayout extends AbstractPdfRenderer {
	private static final float FONT_SIZE = 8f;
	private static final float LEADING = 11f;
	private static final float PADDING = 4f;
	private static final float[] FRACTIONS = {0.05f, 0.285f, 0.17f, 0.075f, 0.105f, 0.105f, 0.105f, 0.105f};
	private static final String[] HEADERS = {"2. No.", "3. Item", "4. For/POC", "5. Briefed", "6. Start",
			"7. Status", "8. Target", "9. Actual"};

	/**
	 * Writes rows, splitting every cell in lockstep and repeating the form headers.
	 *
	 * @param data incident and layout preferences
	 * @param outputFile destination PDF
	 * @param title form title
	 * @param rows eight-cell rows corresponding to blocks 2 through 9
	 * @param adaptedClues whether to use clue-specific POC headings
	 * @throws IOException if output fails or the margins leave no usable grid
	 */
	void render(AppData data, Path outputFile, String title, List<List<String>> rows, boolean adaptedClues)
			throws IOException {
		Path parent = outputFile.getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		PDRectangle paper = pageSize(data);
		PDRectangle size = data.isIcs233Portrait() ? paper : new PDRectangle(paper.getHeight(), paper.getWidth());
		float margin = Math.max(FORM_MARGIN, pageMargin(data));
		float width = size.getWidth() - 2f * margin;
		PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
		PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
		if (width < 200f) {
			throw new IOException("PDF margins leave insufficient width for ICS 233-CG.");
		}
		String incident = data.getIncidentContext() == null ? "" : data.getIncidentContext().getIncidentName();
		List<String> titleLines = wrapCell(bold, title, width);
		float titleHeight = titleLines.size() * LEADING + 13f;
		List<String> incidentLines = wrapCell(regular, "1. Incident name: " + safe(incident), width - 2f * PADDING);
		float incidentHeight = incidentLines.size() * LEADING + 2f * PADDING;
		List<List<String>> headings = headerLines(bold, width, adaptedClues);
		int headingCount = 1;
		for (List<String> heading : headings) {
			headingCount = Math.max(headingCount, heading.size());
		}
		float headerHeight = headingCount * LEADING + 2f * PADDING;
		float gridTop = size.getHeight() - margin - titleHeight - incidentHeight - headerHeight;
		float gridBottom = margin + 20f;
		int capacity = (int) ((gridTop - gridBottom - 2f * PADDING) / LEADING);
		if (capacity < 1) {
			throw new IOException("PDF margins or incident name leave insufficient height for ICS 233-CG.");
		}
		List<List<Segment>> pages = paginate(regular, rows, width, capacity);
		try (PDDocument document = new PDDocument()) {
			for (int index = 0; index < pages.size(); index++) {
				PDPage page = new PDPage(size);
				document.addPage(page);
				try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
					float top = size.getHeight() - margin;
					for (int line = 0; line < titleLines.size(); line++) {
						text(stream, bold, titleLines.get(line), margin, top - FONT_SIZE - line * LEADING);
					}
					drawCell(stream, regular, incidentLines, margin, top - titleHeight, width, incidentHeight);
					drawRow(stream, bold, headings, margin, gridTop + headerHeight, width, headerHeight);
					float y = gridTop;
					for (Segment segment : pages.get(index)) {
						float height = segment.lineCount() * LEADING + 2f * PADDING;
						drawRow(stream, regular, segment.cells(), margin, y, width, height);
						y -= height;
					}
					while (y > gridBottom + 0.1f) {
						float height = Math.min(28f, y - gridBottom);
						drawRow(stream, regular, List.of(), margin, y, width, height);
						y -= height;
					}
					text(stream, regular, "ICS 233-CG Rev1/07", margin, margin);
					String pageLabel = "Page " + (index + 1) + " of " + pages.size();
					float labelWidth = regular.getStringWidth(pageLabel) / 1000f * FONT_SIZE;
					text(stream, regular, pageLabel, margin + width - labelWidth, margin);
				}
			}
			document.save(outputFile.toFile());
		}
	}

	/**
	 * Packs wrapped rows into pages, preserving all lines of oversized cells.
	 *
	 * @param font body font
	 * @param rows source rows
	 * @param width grid width
	 * @param capacity maximum body lines on an empty page
	 * @return pages of row segments, including one page for an empty form
	 * @throws IOException if font metrics cannot be read
	 */
	private List<List<Segment>> paginate(PDType1Font font, List<List<String>> rows, float width, int capacity)
			throws IOException {
		List<List<Segment>> pages = new ArrayList<>();
		List<Segment> page = new ArrayList<>();
		pages.add(page);
		float remaining = capacity * LEADING + 2f * PADDING;
		for (List<String> row : rows) {
			List<List<String>> cells = new ArrayList<>();
			int count = 1;
			for (int column = 0; column < FRACTIONS.length; column++) {
				List<String> lines = wrapCell(font, row.get(column), width * FRACTIONS[column] - 2f * PADDING);
				cells.add(lines);
				count = Math.max(count, lines.size());
			}
			int offset = 0;
			while (offset < count) {
				int available = (int) ((remaining - 2f * PADDING) / LEADING);
				if (available < 1 || (offset == 0 && count <= capacity && count > available)) {
					page = new ArrayList<>();
					pages.add(page);
					remaining = capacity * LEADING + 2f * PADDING;
					available = capacity;
				}
				int take = Math.min(available, count - offset);
				List<List<String>> slice = new ArrayList<>();
				for (List<String> cell : cells) {
					slice.add(new ArrayList<>(cell.subList(Math.min(offset, cell.size()),
							Math.min(offset + take, cell.size()))));
				}
				if (offset > 0 && slice.get(0).isEmpty()) {
					slice.set(0, cells.get(0).subList(0, Math.min(take, cells.get(0).size())));
				}
				page.add(new Segment(slice, take));
				remaining -= take * LEADING + 2f * PADDING;
				offset += take;
			}
		}
		return pages;
	}

	/**
	 * Wraps the eight column headings with the same measured widths as their cells.
	 *
	 * @param font heading font
	 * @param width grid width
	 * @param adaptedClues whether to use clue-specific POC headings
	 * @return wrapped heading cells
	 * @throws IOException if font metrics cannot be read
	 */
	private List<List<String>> headerLines(PDType1Font font, float width, boolean adaptedClues) throws IOException {
		String[] headers = HEADERS.clone();
		if (adaptedClues) {
			headers[2] = "4. Assigned to/POC";
			headers[3] = "5. POC Briefed";
		}
		List<List<String>> cells = new ArrayList<>();
		for (int column = 0; column < headers.length; column++) {
			cells.add(wrapCell(font, headers[column], width * FRACTIONS[column] - 2f * PADDING));
		}
		return cells;
	}

	/**
	 * Wraps by measured glyph widths, including long words without dropping text.
	 * Glyphs unavailable in the standard PDF font use reversible Unicode escapes.
	 *
	 * @param font text font
	 * @param value source text
	 * @param width available width
	 * @return all source lines wrapped to the available width
	 * @throws IOException if font metrics cannot be read
	 */
	private List<String> wrapCell(PDType1Font font, String value, float width) throws IOException {
		List<String> lines = new ArrayList<>();
		PdfText.validateEscaped(value, "ICS 233-CG", "cell text");
		for (String paragraph : PdfText.normalize(value).split("\n", -1)) {
			StringBuilder line = new StringBuilder();
			float used = 0f;
			for (int position = 0; position < paragraph.length();) {
				int codePoint = paragraph.codePointAt(position);
				position += Character.charCount(codePoint);
				String glyph = new String(Character.toChars(codePoint));
				try {
					font.getStringWidth(glyph);
				} catch (IllegalArgumentException exception) {
					glyph = String.format("\\u{%X}", codePoint);
				}
				for (int part = 0; part < glyph.length(); part++) {
					String character = glyph.substring(part, part + 1);
					float advance = font.getStringWidth(character) / 1000f * FONT_SIZE;
					if (used + advance > width && line.length() > 0) {
						int breakAt = line.lastIndexOf(" ");
						if (breakAt > 0) {
							lines.add(line.substring(0, breakAt + 1));
							line.delete(0, breakAt + 1);
							used = font.getStringWidth(line.toString()) / 1000f * FONT_SIZE;
						} else {
							lines.add(line.toString());
							line.setLength(0);
							used = 0f;
						}
					}
					line.append(character);
					used += advance;
				}
			}
			lines.add(line.toString());
		}
		return lines;
	}

	/**
	 * Draws a bordered eight-column row, or an empty grid row.
	 *
	 * @param stream output stream
	 * @param font cell font
	 * @param cells wrapped cell lines
	 * @param x left edge
	 * @param top top edge
	 * @param width grid width
	 * @param height row height
	 * @throws IOException if drawing fails
	 */
	private void drawRow(PDPageContentStream stream, PDType1Font font, List<List<String>> cells,
			float x, float top, float width, float height) throws IOException {
		for (int column = 0; column < FRACTIONS.length; column++) {
			float cellWidth = width * FRACTIONS[column];
			drawCell(stream, font, cells.isEmpty() ? List.of() : cells.get(column), x, top, cellWidth, height);
			x += cellWidth;
		}
	}

	/**
	 * Draws a cell's border and every supplied line.
	 *
	 * @param stream output stream
	 * @param font text font
	 * @param lines wrapped text
	 * @param x left edge
	 * @param top top edge
	 * @param width cell width
	 * @param height cell height
	 * @throws IOException if drawing fails
	 */
	private void drawCell(PDPageContentStream stream, PDType1Font font, List<String> lines,
			float x, float top, float width, float height) throws IOException {
		stream.addRect(x, top - height, width, height);
		stream.stroke();
		float y = top - PADDING - FONT_SIZE;
		for (String line : lines) {
			text(stream, font, line, x + PADDING, y);
			y -= LEADING;
		}
	}

	/**
	 * Writes a single already-wrapped text line.
	 *
	 * @param stream output stream
	 * @param font text font
	 * @param value text
	 * @param x baseline left position
	 * @param y baseline height
	 * @throws IOException if drawing fails
	 */
	private void text(PDPageContentStream stream, PDType1Font font, String value, float x, float y) throws IOException {
		try (PdfText.TextBlock textBlock = PdfText.begin(stream)) {
			stream.setFont(font, FONT_SIZE);
			stream.newLineAtOffset(x, y);
			stream.showText(PdfText.singleLine(value));
		}
	}

	/**
	 * Normalizes an absent optional value without altering supplied text.
	 *
	 * @param value optional text
	 * @return non-null text
	 */
	static String safe(String value) {
		return PdfText.normalize(value);
	}

	/**
	 * Wrapped row fragment occupying one page.
	 *
	 * @param cells column lines
	 * @param lineCount fragment height in body lines
	 */
	private record Segment(List<List<String>> cells, int lineCount) {
	}
}
