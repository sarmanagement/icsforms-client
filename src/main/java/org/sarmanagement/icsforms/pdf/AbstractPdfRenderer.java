/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.pdf;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.PdfLayoutSettings;
import org.sarmanagement.icsforms.model.SarTaskAssignment;
import org.sarmanagement.icsforms.model.SarTaskResource;
import org.sarmanagement.icsforms.ui.AppController;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
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
	private static final DateTimeFormatter PDF_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
	private static final DateTimeFormatter PDF_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

	/**
	 * Wraps validated text using actual font advances, including oversized tokens.
	 * @param text source text.
	 * @param font measuring font.
	 * @param fontSize font size in points.
	 * @param width available width in points.
	 * @return lines fitting the available width.
	 * @throws IOException when font metrics fail.
	 */
	protected List<String> wrapText(String text, PDType1Font font, float fontSize, float width) throws IOException {
		if (width <= 0f || fontSize <= 0f) {
			throw new IllegalArgumentException("PDF text width and size must be positive");
		}
		PdfText.validate(text, "PDF", "text");
		List<String> lines = new ArrayList<>();
		for (String paragraph : PdfText.normalize(text).split("\n", -1)) {
			String remaining = paragraph.strip();
			while (!remaining.isEmpty()) {
				int end = 0;
				int lastSpace = -1;
				while (end < remaining.length()) {
					int next = remaining.offsetByCodePoints(end, 1);
					if (font.getStringWidth(remaining.substring(0, next)) * fontSize / 1000f > width) {
						break;
					}
					if (remaining.charAt(end) == ' ') {
						lastSpace = end;
					}
					end = next;
				}
				if (end == remaining.length()) {
					lines.add(remaining);
					remaining = "";
				} else {
					if (end == 0) {
						throw new IllegalArgumentException("PDF text width cannot fit a character");
					}
					int split = lastSpace > 0 ? lastSpace : end;
					lines.add(remaining.substring(0, split).stripTrailing());
					remaining = remaining.substring(split).stripLeading();
				}
			}
			if (paragraph.isBlank()) {
				lines.add("");
			}
		}
		return lines;
	}

	/**
	 * Draws a bounded section while retaining entries that fit a fresh page intact.
	 * @param stream page stream.
	 * @param bold heading font.
	 * @param regular body font.
	 * @param x left edge.
	 * @param y bottom edge.
	 * @param width section width.
	 * @param height section height.
	 * @param heading numbered block heading.
	 * @param entries logical entries, not prewrapped lines.
	 * @param freshCapacity line capacity of a fresh continuation page.
	 * @return undrawn logical entries.
	 * @throws IOException when drawing fails.
	 */
	protected List<String> drawFlowSection(PDPageContentStream stream, PDType1Font bold, PDType1Font regular,
			float x, float y, float width, float height, String heading, List<String> entries, int freshCapacity)
			throws IOException {
		int capacity = Math.max(0, 1 + (int) Math.floor((height - 30f) / 12f));
		List<String> visible = new ArrayList<>();
		List<String> pending = new ArrayList<>();
		List<String> source = entries == null ? List.of() : entries;
		for (int index = 0; index < source.size(); index++) {
			List<String> lines = wrapText(source.get(index), regular, 10f, width - 8f);
			int available = capacity - visible.size();
			if (lines.size() <= available) {
				visible.addAll(lines);
			} else {
				int take = lines.size() > freshCapacity ? Math.max(0, available - 1) : 0;
				visible.addAll(lines.subList(0, take));
				pending.add(String.join("\n", lines.subList(take, lines.size())));
				pending.addAll(source.subList(index + 1, source.size()));
				break;
			}
		}
		if (!pending.isEmpty() && visible.size() < capacity) {
			visible.add("See next page");
		}
		try (PdfText.TextBlock block = PdfText.begin(stream)) {
			stream.setFont(bold, 10f);
			stream.newLineAtOffset(x + 4f, y + height - 14f);
			stream.showText(PdfText.singleLine(heading));
			stream.setFont(regular, 10f);
			stream.newLineAtOffset(0, -12f);
			for (String line : visible) {
				stream.showText(PdfText.singleLine(line));
				stream.newLineAtOffset(0, -12f);
			}
		}
		return pending;
	}

	/**
	 * Paginates a numbered block within the printable frame.
	 * @param document target document.
	 * @param data page settings.
	 * @param formNumber form identifier.
	 * @param title canonical form title.
	 * @param heading numbered block heading.
	 * @param entries logical entries.
	 * @throws IOException when drawing fails.
	 */
	protected void writeContinuationPages(PDDocument document, AppData data, String formNumber, String title,
			String heading, List<String> entries) throws IOException {
		List<String> remaining = new ArrayList<>(entries);
		PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
		PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
		while (!remaining.isEmpty()) {
			PDPage page = newPage(data);
			document.addPage(page);
			FormLayout layout = formLayout(page, data);
			try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
				drawFormHeader(stream, bold, layout, formNumber, title, document.getNumberOfPages());
				drawFormFrame(stream, layout);
				remaining = drawFlowSection(stream, bold, regular, layout.x(), layout.y(), layout.width(),
						layout.height(), heading, remaining, Math.max(1, 1 + (int) ((layout.height() - 30f) / 12f)));
			}
		}
	}

	/**
	 * Draws a complete incident name, wrapping and reducing its size only as needed.
	 * @param stream page stream.
	 * @param bold heading font.
	 * @param regular value font.
	 * @param x left edge.
	 * @param y bottom edge.
	 * @param width cell width.
	 * @param height cell height.
	 * @param name incident name.
	 * @throws IOException when drawing fails.
	 */
	protected void drawIncidentName(PDPageContentStream stream, PDType1Font bold, PDType1Font regular, float x,
			float y, float width, float height, String name) throws IOException {
		drawFittedSection(stream, bold, regular, x, y, width, height, "1. Incident Name", name);
	}

	/**
	 * Draws a complete bounded value below its heading, shrinking only as needed.
	 * @param stream page stream.
	 * @param bold heading font.
	 * @param regular value font.
	 * @param x left edge.
	 * @param y bottom edge.
	 * @param width cell width.
	 * @param height cell height.
	 * @param heading numbered block heading.
	 * @param value complete cell value.
	 * @throws IOException when drawing fails.
	 */
	protected void drawFittedSection(PDPageContentStream stream, PDType1Font bold, PDType1Font regular, float x,
			float y, float width, float height, String heading, String value) throws IOException {
		float size = 10f;
		List<String> lines = wrapText(value, regular, size, width - 8f);
		while (lines.size() * size * 1.2f > height - 24f) {
			size *= 0.9f;
			lines = wrapText(value, regular, size, width - 8f);
		}
		try (PdfText.TextBlock block = PdfText.begin(stream)) {
			stream.setFont(bold, 10f);
			stream.newLineAtOffset(x + 4f, y + height - 14f);
			stream.showText(PdfText.singleLine(heading));
			stream.setFont(regular, size);
			stream.newLineAtOffset(0, -12f);
			for (String line : lines) {
				stream.showText(PdfText.singleLine(line));
				stream.newLineAtOffset(0, -size * 1.2f);
			}
		}
	}

	/**
	 * Writes a centered header line plus bordered content blocks across one or more
	 * pages.
	 *
	 * @param document
	 *            target document.
	 * @param formNumber
	 *            form number label.
	 * @param title
	 *            document title.
	 * @param blocks
	 *            content blocks.
	 * @throws IOException
	 *             when PDF output fails.
	 */
	protected void writeDocument(PDDocument document, String formNumber, String title, List<List<String>> blocks)
			throws IOException {
		writeDocument(document, null, formNumber, title, blocks);
	}

	/**
	 * Writes bordered blocks with the selected page settings.
	 * @param document target PDF.
	 * @param data incident and page settings.
	 * @param formNumber form identifier.
	 * @param title document title.
	 * @param blocks source text blocks.
	 * @throws IOException when drawing fails.
	 */
	protected void writeDocument(PDDocument document, AppData data, String formNumber, String title,
			List<List<String>> blocks) throws IOException {
		PDPage page = newPage(data);
		document.addPage(page);
		PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
		PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
		PDPageContentStream stream = new PDPageContentStream(document, page);
		float pageWidth = page.getMediaBox().getWidth();
		float margin = Math.max(MARGIN, pageMargin(data));
		float y = page.getMediaBox().getHeight() - margin;
		String header = (formNumber + " " + title).toUpperCase();
		float headerWidth = bold.getStringWidth(PdfText.singleLine(header)) / 1000f * HEADER_FONT_SIZE;
		try (PdfText.TextBlock textBlock = PdfText.begin(stream)) {
			stream.setFont(bold, HEADER_FONT_SIZE);
			stream.newLineAtOffset((pageWidth - headerWidth) / 2f, y);
			stream.showText(PdfText.singleLine(header));
		}
		y -= LEADING * 2;

		for (List<String> block : blocks) {
			List<String> wrapped = new ArrayList<>();
			for (String rawLine : block) {
				wrapped.addAll(wrapText(rawLine, font, BODY_FONT_SIZE, pageWidth - margin * 2f - BLOCK_PADDING * 2f));
			}
			int offset = 0;
			do {
				int available = Math.max(0, (int) ((y - margin - BLOCK_PADDING * 2f) / LEADING));
				if (available == 0 || (offset == 0 && wrapped.size() > available
						&& wrapped.size() <= (int) ((page.getMediaBox().getHeight() - margin * 2f - LEADING * 2f
								- BLOCK_PADDING * 2f) / LEADING))) {
					stream.close();
					page = newPage(data);
					document.addPage(page);
					stream = new PDPageContentStream(document, page);
					FormLayout headerLayout = new FormLayout(margin, margin, pageWidth - margin * 2f, 0f,
							page.getMediaBox().getHeight() - margin, pageWidth, page.getMediaBox().getHeight(), margin);
					drawFormHeader(stream, bold, headerLayout, formNumber, title, document.getNumberOfPages());
					y = page.getMediaBox().getHeight() - margin - LEADING * 2f;
					available = Math.max(1, (int) ((y - margin - BLOCK_PADDING * 2f) / LEADING));
				}
				int count = Math.min(available, wrapped.size() - offset);
				float blockHeight = Math.max(LEADING, count * LEADING) + BLOCK_PADDING * 2f;
				float boxTop = y;
				float boxBottom = y - blockHeight;
				stream.addRect(margin, boxBottom, pageWidth - (margin * 2), blockHeight);
				stream.stroke();

				try (PdfText.TextBlock textBlock = PdfText.begin(stream)) {
					stream.setFont(font, BODY_FONT_SIZE);
					stream.newLineAtOffset(margin + BLOCK_PADDING, boxTop - BLOCK_PADDING - BODY_FONT_SIZE);
					for (String line : wrapped.subList(offset, offset + count)) {
						stream.showText(PdfText.singleLine(line));
						stream.newLineAtOffset(0, -LEADING);
					}
				}
				y = boxBottom - BLOCK_SPACING;
				offset += count;
			} while (offset < wrapped.size());
		}
		stream.close();
	}

	/**
	 * Counts the people in a SAR task's resource list, excluding the primary task
	 * resource (which is typically equipment such as a canine or vehicle).
	 *
	 * @param task
	 *            SAR task assignment.
	 * @return number of human resources assigned to the task.
	 */
	protected int countPeople(SarTaskAssignment task) {
		if (task.getResourcesAssigned() == null) {
			return 0;
		}
		String taskResourceId = (task.getResourceIdentifier() == null ? "" : task.getResourceIdentifier()).trim()
				.toLowerCase();
		int count = 0;
		for (SarTaskResource r : task.getResourcesAssigned()) {
			if (r == null) {
				continue;
			}
			String name = (r.getName() == null ? "" : r.getName()).trim().toLowerCase();
			String function = (r.getFunction() == null ? "" : r.getFunction()).trim();
			boolean isPrimary = !taskResourceId.isBlank() && name.equals(taskResourceId)
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
	 * Wraps a line to a simple character width for stable smoke-test-friendly
	 * exports.
	 *
	 * @param text
	 *            text to wrap.
	 * @param maxWidth
	 *            maximum characters per line.
	 * @return wrapped lines.
	 */
	protected List<String> wrap(String text, int maxWidth) {
		List<String> lines = new ArrayList<>();
		if (maxWidth <= 0) {
			throw new IllegalArgumentException("PDF wrap width must be positive");
		}
		PdfText.validate(text, "PDF", "text");
		for (String paragraph : PdfText.normalize(text).split("\n", -1)) {
			if (paragraph.length() <= maxWidth) {
				lines.add(paragraph);
				continue;
			}
			StringBuilder current = new StringBuilder();
			for (String word : paragraph.split(" +")) {
				if (current.length() == 0) {
					current.append(word);
				} else if (current.length() + 1 + word.length() <= maxWidth) {
					current.append(' ').append(word);
				} else {
					lines.add(current.toString());
					current = new StringBuilder(word);
				}
			}
			lines.add(current.toString());
		}
		return lines;
	}

	/**
	 * Returns the shared printable form frame for bordered ICS/SAR documents.
	 *
	 * @param page
	 *            PDF page.
	 * @return shared form layout bounds.
	 */
	protected PDPage newPage(AppData data) {
		return new PDPage(pageSize(data));
	}

	protected PDRectangle pageSize(AppData data) {
		PdfLayoutSettings.PaperSize paperSize = pdfLayoutSettings(data).getPaperSize();
		return paperSize == PdfLayoutSettings.PaperSize.A4 ? PDRectangle.A4 : PDRectangle.LETTER;
	}

	protected float pageMargin(AppData data) {
		return pdfLayoutSettings(data).getPageMarginPoints();
	}

	protected FormLayout formLayout(PDPage page) {
		return formLayout(page, null);
	}

	protected FormLayout formLayout(PDPage page, AppData data) {
		float formMargin = Math.max(FORM_MARGIN, pageMargin(data));
		float width = page.getMediaBox().getWidth() - (formMargin * 2f);
		float top = page.getMediaBox().getHeight() - formMargin - FORM_HEADER_HEIGHT;
		return new FormLayout(formMargin, formMargin, width, top - formMargin, top + FORM_HEADER_HEIGHT,
				page.getMediaBox().getWidth(), page.getMediaBox().getHeight(), pageMargin(data));
	}

	/**
	 * Formats a stored UTC date/time for PDF display using the configured export
	 * timezone.
	 *
	 * @param data
	 *            incident document containing timezone settings.
	 * @param value
	 *            stored UTC local date/time.
	 * @return formatted local date/time text.
	 */
	protected String formatPdfDateTime(AppData data, LocalDateTime value) {
		return value == null ? "" : formatPdfDate(data, value) + " " + formatPdfTime(data, value);
	}

	/**
	 * Formats a stored UTC date/time date portion for PDF display using the
	 * configured export timezone.
	 *
	 * @param data
	 *            incident document containing timezone settings.
	 * @param value
	 *            stored UTC local date/time.
	 * @return formatted local date text.
	 */
	protected String formatPdfDate(AppData data, LocalDateTime value) {
		ZonedDateTime zonedDateTime = zonedPdfDateTime(data, value);
		return zonedDateTime == null ? "" : PDF_DATE_FORMATTER.format(zonedDateTime);
	}

	/**
	 * Formats a stored UTC date/time time portion for PDF display using the
	 * configured export timezone.
	 *
	 * @param data
	 *            incident document containing timezone settings.
	 * @param value
	 *            stored UTC local date/time.
	 * @return formatted local time text.
	 */
	protected String formatPdfTime(AppData data, LocalDateTime value) {
		ZonedDateTime zonedDateTime = zonedPdfDateTime(data, value);
		return zonedDateTime == null ? "" : PDF_TIME_FORMATTER.format(zonedDateTime);
	}

	/**
	 * Returns a human-readable timezone label for PDF headers.
	 *
	 * @param data
	 *            incident document containing timezone settings.
	 * @return timezone label with ID, offset, and abbreviation.
	 */
	protected String pdfTimeZoneLabel(AppData data) {
		ZoneId zoneId = pdfZoneId(data);
		ZonedDateTime now = ZonedDateTime.now(zoneId);
		return zoneId.getId() + " (UTC" + now.getOffset() + ", " + now.format(DateTimeFormatter.ofPattern("z")) + ")";
	}

	/**
	 * Resolves the timezone to use for PDF export.
	 *
	 * @param data
	 *            incident document containing timezone settings.
	 * @return configured export timezone.
	 */
	protected ZoneId pdfZoneId(AppData data) {
		if (data == null || data.isUseSystemTimeZone()) {
			return ZoneId.systemDefault();
		}
		try {
			return ZoneId.of(data.getConfiguredTimeZoneId());
		} catch (RuntimeException exception) {
			return ZoneId.systemDefault();
		}
	}

	/**
	 * Converts a stored UTC local date/time into the configured PDF timezone.
	 *
	 * @param data
	 *            incident document containing timezone settings.
	 * @param value
	 *            stored UTC local date/time.
	 * @return zoned value in the configured export timezone.
	 */
	protected ZonedDateTime zonedPdfDateTime(AppData data, LocalDateTime value) {
		if (value == null) {
			return null;
		}
		Date instant = AppController.toDate(value);
		return instant.toInstant().atZone(pdfZoneId(data));
	}

	/**
	 * Draws the shared heading line above the printable form.
	 *
	 * @param stream
	 *            page stream.
	 * @param bold
	 *            bold font.
	 * @param layout
	 *            shared form layout.
	 * @param formNumber
	 *            form number text.
	 * @param title
	 *            title text.
	 * @throws IOException
	 *             when PDF output fails.
	 */
	protected void drawFormHeader(PDPageContentStream stream, PDType1Font bold, FormLayout layout, String formNumber,
			String title) throws IOException {
		String headerTitle = safeText(title);
		if (formNumber.startsWith("ICS ")) {
			boolean continued = headerTitle.endsWith(" (CONTINUED)");
			if (continued) {
				headerTitle = headerTitle.substring(0, headerTitle.length() - " (CONTINUED)".length());
			}
			headerTitle += " (" + formNumber + ")";
			if (continued) {
				headerTitle += " (CONTINUED)";
			}
		} else {
			headerTitle = formNumber + " " + headerTitle;
		}
		String header = headerTitle;
		float headerWidth = bold.getStringWidth(PdfText.singleLine(header)) / 1000f * HEADER_FONT_SIZE;
		try (PdfText.TextBlock textBlock = PdfText.begin(stream)) {
			stream.setFont(bold, HEADER_FONT_SIZE);
			stream.newLineAtOffset((layout.pageWidth() - headerWidth) / 2f, layout.headerBaseline());
			stream.showText(PdfText.singleLine(header));
		}
	}

	/**
	 * Draws a canonical form title and continuation page number above the frame.
	 * @param stream page stream.
	 * @param bold title font.
	 * @param layout printable bounds.
	 * @param formNumber form identifier.
	 * @param title canonical form title.
	 * @param pageNumber one-based document page.
	 * @throws IOException when drawing fails.
	 */
	protected void drawFormHeader(PDPageContentStream stream, PDType1Font bold, FormLayout layout, String formNumber,
			String title, int pageNumber) throws IOException {
		if (pageNumber <= 1) {
			drawFormHeader(stream, bold, layout, formNumber, title);
			return;
		}
		String header = title + " (" + formNumber + ") Page " + pageNumber;
		float size = Math.min(HEADER_FONT_SIZE,
				layout.width() * 1000f / Math.max(1f, bold.getStringWidth(PdfText.singleLine(header))));
		float width = bold.getStringWidth(PdfText.singleLine(header)) * size / 1000f;
		try (PdfText.TextBlock block = PdfText.begin(stream)) {
			stream.setFont(bold, size);
			stream.newLineAtOffset((layout.pageWidth() - width) / 2f, layout.headerBaseline());
			stream.showText(PdfText.singleLine(header));
		}
	}

	/**
	 * Draws the shared outer border that fills the printable form area.
	 *
	 * @param stream
	 *            page stream.
	 * @param layout
	 *            shared form layout.
	 * @throws IOException
	 *             when PDF output fails.
	 */
	protected void drawFormFrame(PDPageContentStream stream, FormLayout layout) throws IOException {
		stream.addRect(layout.x(), layout.y(), layout.width(), layout.height());
		stream.stroke();
	}

	/**
	 * Expands one designated row to consume any unused form height while leaving
	 * the other rows at their preferred heights.
	 *
	 * @param totalHeight
	 *            total height available for the rows.
	 * @param expandableRowIndex
	 *            zero-based index of the row that should absorb extra space.
	 * @param preferredHeights
	 *            preferred heights for each row.
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

	protected void drawPreparedByMetadataSection(PDPageContentStream stream, PDType1Font bold, PDType1Font regular,
			float bodyFontSize, float headingFontSize, float cellPadding, float x, float y, float width, float height,
			float footerBandHeight, String heading, String name, String positionTitle, String signature,
			String formLabel, String iapPage, String dateTime) throws IOException {
		drawPreparedByMetadataSection(stream, bold, regular, bodyFontSize, headingFontSize, cellPadding, x, y, width,
				height, footerBandHeight, 14f, 0.20f, 0.58f, 0.58f, heading, name, positionTitle, signature, formLabel,
				iapPage, dateTime);
	}

	protected void drawPreparedByMetadataSection(PDPageContentStream stream, PDType1Font bold, PDType1Font regular,
			float bodyFontSize, float headingFontSize, float cellPadding, float x, float y, float width, float height,
			float footerBandHeight, float topRowOffset, String heading, String name, String positionTitle,
			String signature, String formLabel, String iapPage, String dateTime) throws IOException {
		drawPreparedByMetadataSection(stream, bold, regular, bodyFontSize, headingFontSize, cellPadding, x, y, width,
				height, footerBandHeight, topRowOffset, 0.20f, 0.58f, 0.58f, heading, name, positionTitle, signature,
				formLabel, iapPage, dateTime);
	}

	protected void drawPreparedByMetadataSection(PDPageContentStream stream, PDType1Font bold, PDType1Font regular,
			float bodyFontSize, float headingFontSize, float cellPadding, float x, float y, float width, float height,
			float footerBandHeight, float topRowOffset, float nameEndRatio, float positionEndRatio, String heading,
			String name, String positionTitle, String signature, String formLabel, String iapPage, String dateTime)
			throws IOException {
		drawPreparedByMetadataSection(stream, bold, regular, bodyFontSize, headingFontSize, cellPadding, x, y, width,
				height, footerBandHeight, topRowOffset, nameEndRatio, positionEndRatio, positionEndRatio, heading, name,
				positionTitle, signature, formLabel, iapPage, dateTime);
	}

	protected void drawPreparedByMetadataSection(PDPageContentStream stream, PDType1Font bold, PDType1Font regular,
			float bodyFontSize, float headingFontSize, float cellPadding, float x, float y, float width, float height,
			float footerBandHeight, float topRowOffset, float nameEndRatio, float positionEndRatio,
			float lowerBandRightStartRatio, String heading, String name, String positionTitle, String signature,
			String formLabel, String iapPage, String dateTime) throws IOException {
		stream.addRect(x, y, width, height);
		stream.stroke();
		drawMetadataHeading(stream, bold, headingFontSize, cellPadding, x, y + height, safeText(heading));

		float footerTop = y + footerBandHeight;
		float topRowY = footerTop + topRowOffset;

		float nameStart = x + cellPadding;
		float nameEnd = x + (width * nameEndRatio);
		float positionStart = nameEnd + cellPadding;
		float positionEnd = x + (width * positionEndRatio);
		float signatureStart = positionEnd + cellPadding;
		float signatureEnd = x + width - cellPadding;
		writeInlineHeadingValueWithinWidth(stream, bold, regular, bodyFontSize, nameStart, topRowY,
				Math.max(0f, nameEnd - nameStart), "Name:", name);
		writeInlineHeadingValueWithinWidth(stream, bold, regular, bodyFontSize, positionStart, topRowY,
				Math.max(0f, positionEnd - positionStart), "Position/Title:", positionTitle);
		writeInlineHeadingValueWithinWidth(stream, bold, regular, bodyFontSize, signatureStart, topRowY,
				Math.max(0f, signatureEnd - signatureStart), "Signature:", signature);

		drawFormFooterBand(stream, bold, regular, bodyFontSize, cellPadding, x, y, width, footerBandHeight,
				lowerBandRightStartRatio, formLabel, iapPage, dateTime);
	}

	/**
	 * Draws the shared form-number, IAP-page, and date/time footer band.
	 *
	 * @param stream page stream.
	 * @param bold bold font.
	 * @param regular regular font.
	 * @param bodyFontSize body font size.
	 * @param cellPadding padding inside footer cells.
	 * @param x left coordinate.
	 * @param y bottom coordinate.
	 * @param width footer width.
	 * @param footerBandHeight footer-band height.
	 * @param lowerBandRightStartRatio position where date/time begins.
	 * @param formLabel form identifier.
	 * @param iapPage IAP page number.
	 * @param dateTime prepared date and time.
	 * @throws IOException when PDF drawing fails.
	 */
	protected void drawFormFooterBand(PDPageContentStream stream, PDType1Font bold, PDType1Font regular,
			float bodyFontSize, float cellPadding, float x, float y, float width, float footerBandHeight,
			float lowerBandRightStartRatio, String formLabel, String iapPage, String dateTime) throws IOException {
		float footerContentY = y + footerBandHeight - cellPadding - bodyFontSize;
		float lowerBandRightStart = x + (width * lowerBandRightStartRatio) + cellPadding;
		float minFooterCellWidth = width / 8f;
		float footerBandAvailableWidth = Math.max(minFooterCellWidth * 2f, lowerBandRightStart - x - cellPadding);
		float formBoxWidth = Math.max(minFooterCellWidth,
				fittedTextWidth(bold, bodyFontSize, safeText(formLabel)) + (cellPadding * 2f));
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
		stream.addRect(x, y, formBoxWidth, footerBandHeight);
		stream.stroke();
		stream.addRect(x + formBoxWidth, y, iapBoxWidth, footerBandHeight);
		stream.stroke();
		writeFittedText(stream, bold, bodyFontSize, x + cellPadding, footerContentY,
				Math.max(0f, formBoxWidth - (cellPadding * 2f)), formLabel);
		writeInlineHeadingValue(stream, bold, regular, bodyFontSize, x + formBoxWidth + cellPadding, footerContentY,
				"IAP Page", iapPage);
		writeInlineHeadingValueWithinWidth(stream, bold, regular, bodyFontSize, lowerBandRightStart, footerContentY,
				Math.max(0f, x + width - cellPadding - lowerBandRightStart), "Date/Time:", dateTime);
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

	private PdfLayoutSettings pdfLayoutSettings(AppData data) {
		return data == null ? new PdfLayoutSettings() : data.getPdfLayoutSettings();
	}

	/**
	 * Draws a metadata section heading in a balanced text block.
	 * @param stream page stream.
	 * @param bold heading font.
	 * @param headingFontSize heading size.
	 * @param cellPadding inner padding.
	 * @param x left edge.
	 * @param topY top edge.
	 * @param heading section heading.
	 * @throws IOException when drawing fails.
	 */
	private void drawMetadataHeading(PDPageContentStream stream, PDType1Font bold, float headingFontSize,
			float cellPadding, float x, float topY, String heading) throws IOException {
		try (PdfText.TextBlock textBlock = PdfText.begin(stream)) {
			stream.setFont(bold, headingFontSize);
			stream.newLineAtOffset(x + cellPadding, topY - cellPadding - headingFontSize);
			stream.showText(PdfText.singleLine(heading));
		}
	}

	/**
	 * Draws an inline label and normalized value.
	 * @param stream page stream.
	 * @param bold label font.
	 * @param regular value font.
	 * @param bodyFontSize font size.
	 * @param x left edge.
	 * @param y baseline.
	 * @param label field label.
	 * @param value source value.
	 * @throws IOException when drawing fails.
	 */
	private void writeInlineHeadingValue(PDPageContentStream stream, PDType1Font bold, PDType1Font regular,
			float bodyFontSize, float x, float y, String label, String value) throws IOException {
		String safeLabel = safeText(label);
		String safeValue = safeText(value);
		try (PdfText.TextBlock textBlock = PdfText.begin(stream)) {
			stream.setFont(bold, bodyFontSize);
			stream.newLineAtOffset(x, y);
			stream.showText(PdfText.singleLine(safeLabel));
			float labelWidth = bold.getStringWidth(PdfText.singleLine(safeLabel)) / 1000f * bodyFontSize;
			stream.setFont(regular, bodyFontSize);
			stream.newLineAtOffset(labelWidth + 4f, 0);
			if (!safeValue.isBlank()) {
				stream.showText(PdfText.singleLine(safeValue));
			}
		}
	}

	/**
	 * Draws an inline field, fitting its normalized value to the available width.
	 * @param stream page stream.
	 * @param bold label font.
	 * @param regular value font.
	 * @param bodyFontSize font size.
	 * @param x left edge.
	 * @param y baseline.
	 * @param width available width.
	 * @param label field label.
	 * @param value source value.
	 * @throws IOException when drawing fails.
	 */
	private void writeInlineHeadingValueWithinWidth(PDPageContentStream stream, PDType1Font bold, PDType1Font regular,
			float bodyFontSize, float x, float y, float width, String label, String value) throws IOException {
		String safeLabel = safeText(label);
		try (PdfText.TextBlock textBlock = PdfText.begin(stream)) {
			stream.setFont(bold, bodyFontSize);
			stream.newLineAtOffset(x, y);
			stream.showText(PdfText.singleLine(safeLabel));
			float labelWidth = bold.getStringWidth(PdfText.singleLine(safeLabel)) / 1000f * bodyFontSize;
			stream.setFont(regular, bodyFontSize);
			stream.newLineAtOffset(labelWidth + 4f, 0);
			stream.showText(fitText(regular, value, bodyFontSize, Math.max(0f, width - labelWidth - 4f)));
		}
	}

	/**
	 * Draws normalized inline text fitted to a cell.
	 * @param stream page stream.
	 * @param font text font.
	 * @param fontSize text size.
	 * @param x left edge.
	 * @param y baseline.
	 * @param maxWidth available width.
	 * @param value source text.
	 * @throws IOException when drawing fails.
	 */
	private void writeFittedText(PDPageContentStream stream, PDType1Font font, float fontSize, float x, float y,
			float maxWidth, String value) throws IOException {
		try (PdfText.TextBlock textBlock = PdfText.begin(stream)) {
			stream.setFont(font, fontSize);
			stream.newLineAtOffset(x, y);
			stream.showText(fitText(font, value, fontSize, maxWidth));
		}
	}

	private float inlineTextWidth(PDType1Font bold, PDType1Font regular, float bodyFontSize, String label, String value)
			throws IOException {
		String safeLabel = safeText(label);
		String safeValue = safeText(value);
		float width = fittedTextWidth(bold, bodyFontSize, safeLabel);
		if (!safeValue.isBlank()) {
			width += 4f + fittedTextWidth(regular, bodyFontSize, safeValue);
		}
		return width;
	}

	/**
	 * Measures the normalized text that will actually be drawn.
	 * @param font measuring font.
	 * @param fontSize text size.
	 * @param value source text.
	 * @return rendered width in points.
	 * @throws IOException when font metrics fail.
	 */
	private float fittedTextWidth(PDType1Font font, float fontSize, String value) throws IOException {
		return font.getStringWidth(PdfText.singleLine(value)) / 1000f * fontSize;
	}

	/**
	 * Fits normalized text using the existing ellipsis convention.
	 * @param font measuring font.
	 * @param value source text.
	 * @param fontSize text size.
	 * @param maxWidth available width.
	 * @return fitted text or an empty string when no text fits.
	 * @throws IOException when font metrics fail.
	 */
	private String fitText(PDType1Font font, String value, float fontSize, float maxWidth) throws IOException {
		String normalized = PdfText.singleLine(value).trim();
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

	/**
	 * Normalizes optional layout text without discarding characters.
	 * @param value optional text.
	 * @return normalized text retaining logical line breaks.
	 */
	private String safeText(String value) {
		return PdfText.normalize(value);
	}

	/**
	 * Shared outer form bounds beneath the centered page heading.
	 *
	 * @param x
	 *            left edge.
	 * @param y
	 *            bottom edge.
	 * @param width
	 *            form width.
	 * @param height
	 *            form height.
	 * @param headerBaseline
	 *            baseline for the centered header text.
	 * @param pageWidth
	 *            PDF page width in points.
	 * @param pageHeight
	 *            PDF page height in points.
	 * @param pageMargin
	 *            minimum inner margin in points.
	 */
	protected record FormLayout(float x, float y, float width, float height, float headerBaseline, float pageWidth,
			float pageHeight, float pageMargin) {
		float top() {
			return y + height;
		}
	}
}
