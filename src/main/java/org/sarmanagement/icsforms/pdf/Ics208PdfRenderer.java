/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.pdf;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.Ics208Form;
import org.sarmanagement.icsforms.model.SafetyMessageBlock;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Renders the ICS 208 Safety Message/Plan and hazardous materials variant.
 */
public class Ics208PdfRenderer extends AbstractPdfRenderer implements PdfFormRenderer {
	private static final float FONT_SIZE = 9f;
	private static final float LINE_HEIGHT = 11f;
	private static final float BLOCK_PADDING = 6f;
	private static final float FIRST_PAGE_BOTTOM_BLOCKS = 91f;

	/** {@inheritDoc} */
	@Override
	public String getFormKey() {
		return "ICS 208";
	}

	/**
	 * Counts the pages required by the wrapped block 3 text.
	 *
	 * @param data incident data.
	 * @return page count, at least one.
	 */
	public static int pageCount(AppData data) {
		AppData safeData = data == null ? new AppData() : data;
		Ics208PdfRenderer renderer = new Ics208PdfRenderer();
		FormLayout layout = renderer.formLayout(renderer.newPage(safeData), safeData);
		int firstPageCapacity = renderer.block3Capacity(layout, FIRST_PAGE_BOTTOM_BLOCKS);
		int remainingLines = renderer.messageLines(safeData.getForm208()).size();
		if (remainingLines <= firstPageCapacity) {
			return 1;
		}
		int continuationCapacity = renderer.block3Capacity(layout, 24f);
		return 1 + (int) Math.ceil((double) (remainingLines - firstPageCapacity) / continuationCapacity);
	}

	/**
	 * Returns the number of message lines that fit in block 3.
	 *
	 * @param layout page layout.
	 * @param bottomReserved height reserved below block 3.
	 * @return line capacity.
	 */
	private int block3Capacity(FormLayout layout, float bottomReserved) {
		float blockHeight = layout.height() - 53f - bottomReserved;
		return Math.max(1, (int) ((blockHeight - BLOCK_PADDING * 2 - (LINE_HEIGHT * 2)) / LINE_HEIGHT));
	}

	/**
	 * Returns printable block 3 lines.
	 *
	 * @param form safety message/plan.
	 * @return wrapped text lines.
	 */
	private List<String> messageLines(Ics208Form form) {
		List<String> lines = new ArrayList<>();
		if (form.getMessageMode() == Ics208Form.MessageMode.FREE_TEXT) {
			lines.addAll(wrapContent(form.getFreeText(), 104));
		} else {
			addParagraph(lines, "General requirements", form.getGeneralRequirements());
			for (SafetyMessageBlock block : form.getHazardBlocks()) {
				addParagraph(lines, block.getIncidentArea() + " — Hazard", block.getHazard());
				addParagraph(lines, "Precautions", block.getPrecautions());
			}
		}
		for (Map.Entry<String, String> section : form.getSiteSafetyPlan().getTextSections().entrySet()) {
			addParagraph(lines, "Site Safety Plan — " + section.getKey(), section.getValue());
		}
		return lines;
	}

	/**
	 * Adds a labeled non-empty paragraph to block 3.
	 *
	 * @param lines destination lines.
	 * @param heading paragraph heading.
	 * @param content paragraph content.
	 */
	private void addParagraph(List<String> lines, String heading, String content) {
		if (content != null && !content.isBlank()) {
			lines.add(heading + ":");
			lines.addAll(wrapContent(content, 104));
			lines.add("");
		}
	}

	/** {@inheritDoc} */
	@Override
	public void render(AppData data, Path outputFile) throws IOException {
		Files.createDirectories(outputFile.getParent());
		AppData safeData = data == null ? new AppData() : data;
		try (PDDocument document = new PDDocument()) {
			renderDocument(document, safeData);
			document.save(outputFile.toFile());
		}
	}

	/**
	 * Renders the bordered ICS 208 form and any block 3 continuation pages.
	 *
	 * @param document target document.
	 * @param data source incident data.
	 * @throws IOException when PDF output fails.
	 */
	private void renderDocument(PDDocument document, AppData data) throws IOException {
		Ics208Form form = data.getForm208();
		List<String> lines = messageLines(form);
		PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
		PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
		PDPage page = newPage(data);
		document.addPage(page);
		FormLayout layout = formLayout(page, data);
		float firstCapacity = block3Capacity(layout, FIRST_PAGE_BOTTOM_BLOCKS);
		int offset = drawPage(document, page, data, layout, form, regular, bold, lines, 0,
				(int) firstCapacity, 1, false);
		int pageNumber = 2;
		while (offset < lines.size()) {
			page = newPage(data);
			document.addPage(page);
			layout = formLayout(page, data);
			offset = drawPage(document, page, data, layout, form, regular, bold, lines, offset,
					block3Capacity(layout, 24f), pageNumber++, true);
		}
	}

	/**
	 * Draws one form page and returns the next unread message line.
	 *
	 * @param document PDF document.
	 * @param page target page.
	 * @param data incident metadata.
	 * @param layout shared form layout.
	 * @param form safety form.
	 * @param regular regular font.
	 * @param bold bold font.
	 * @param lines wrapped message text.
	 * @param offset first unread message line.
	 * @param capacity number of message lines to render.
	 * @param pageNumber local page number.
	 * @param continuation whether this is a continuation page.
	 * @return next unread message line.
	 * @throws IOException when PDF drawing fails.
	 */
	private int drawPage(PDDocument document, PDPage page, AppData data, FormLayout layout, Ics208Form form,
			PDType1Font regular, PDType1Font bold, List<String> lines, int offset, int capacity, int pageNumber,
			boolean continuation) throws IOException {
		try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
			String title = form.getMessageType() == Ics208Form.MessageType.HAZARDOUS_MATERIALS_SITE_SAFETY_PLAN
					? "SITE SAFETY AND CONTROL PLAN (ICS 208)" : "SAFETY MESSAGE/PLAN (ICS 208)";
			drawIcs208Header(stream, bold, layout, continuation ? title + " (CONTINUED)" : title);
			drawFormFrame(stream, layout);
			float left = layout.x();
			float right = left + layout.width();
			float top = layout.top();
			float metadataBottom = top - 43f;
			float messageBottom = continuation ? layout.y() + 24f
					: Math.max(layout.y() + FIRST_PAGE_BOTTOM_BLOCKS, metadataBottom - 53f);
			stream.moveTo(left, metadataBottom);
			stream.lineTo(right, metadataBottom);
			stream.moveTo(left, messageBottom);
			stream.lineTo(right, messageBottom);
			stream.stroke();
			drawIncidentMetadata(stream, bold, regular, layout, data, top);
			drawTextLine(stream, bold, left + BLOCK_PADDING, metadataBottom - 12f,
					continuation ? "3. Safety Message/Plan (continued)" : "3. Safety Message/Expanded Safety Message, Safety Plan, Site Safety Plan");
			if (!continuation) {
				drawBottomBlocks(stream, bold, regular, layout, form, data);
			}
			float y = metadataBottom - 27f;
			int end = Math.min(lines.size(), offset + capacity);
			for (int index = offset; index < end; index++) {
				String line = lines.get(index);
				drawTextLine(stream, regular, left + BLOCK_PADDING, y, safe(line));
				y -= LINE_HEIGHT;
			}
			drawPageLabel(stream, regular, layout, pageNumber, form.getIapPage());
			return end;
		}
	}

	/**
	 * Draws the ICS 208 template title centered above the form frame.
	 *
	 * @param stream page stream.
	 * @param bold bold font.
	 * @param layout shared form layout.
	 * @param title form title.
	 * @throws IOException when PDF drawing fails.
	 */
	private void drawIcs208Header(PDPageContentStream stream, PDType1Font bold, FormLayout layout, String title)
			throws IOException {
		float titleWidth = bold.getStringWidth(safe(title)) / 1000f * 14f;
		stream.beginText();
		stream.setFont(bold, 14f);
		stream.newLineAtOffset((layout.pageWidth() - titleWidth) / 2f, layout.headerBaseline());
		stream.showText(safe(title));
		stream.endText();
	}

	/**
	 * Draws the ICS 208 incident name and operational period blocks.
	 *
	 * @param stream page stream.
	 * @param bold bold font.
	 * @param regular regular font.
	 * @param layout page layout.
	 * @param data incident metadata.
	 * @param top top edge of frame.
	 * @throws IOException when PDF drawing fails.
	 */
	private void drawIncidentMetadata(PDPageContentStream stream, PDType1Font bold, PDType1Font regular,
			FormLayout layout, AppData data, float top) throws IOException {
		var context = data.getIncidentContext();
		float middle = layout.x() + layout.width() * 0.52f;
		stream.moveTo(middle, top);
		stream.lineTo(middle, top - 43f);
		stream.stroke();
		drawTextLine(stream, bold, layout.x() + BLOCK_PADDING, top - 12f, "1. Incident Name");
		drawTextLine(stream, regular, layout.x() + BLOCK_PADDING, top - 29f,
				safe(context.getIncidentName()));
		drawTextLine(stream, bold, middle + BLOCK_PADDING, top - 12f, "2. Operational Period");
		drawTextLine(stream, regular, middle + BLOCK_PADDING, top - 27f,
				"From: " + safe(formatPdfDateTime(data, context.getOperationalPeriodStart())));
		drawTextLine(stream, regular, middle + BLOCK_PADDING, top - 38f,
				"To: " + safe(formatPdfDateTime(data, context.getOperationalPeriodEnd())));
	}

	/**
	 * Draws the site-plan requirement, location, and preparer blocks.
	 *
	 * @param stream page stream.
	 * @param bold bold font.
	 * @param regular regular font.
	 * @param layout page layout.
	 * @param form safety form.
	 * @throws IOException when PDF drawing fails.
	 */
	private void drawBottomBlocks(PDPageContentStream stream, PDType1Font bold, PDType1Font regular,
			FormLayout layout, Ics208Form form, AppData data) throws IOException {
		float left = layout.x();
		float right = left + layout.width();
		float bottom = layout.y() + 24f;
		float top = bottom + FIRST_PAGE_BOTTOM_BLOCKS - 24f;
		float block4Bottom = top - 29f;
		float block5Bottom = block4Bottom - 34f;
		stream.moveTo(left, block4Bottom);
		stream.lineTo(right, block4Bottom);
		stream.moveTo(left, block5Bottom);
		stream.lineTo(right, block5Bottom);
		stream.stroke();
		drawTextLine(stream, bold, left + BLOCK_PADDING, top - 12f, "4. Site Safety Plan Required?");
		drawTextLine(stream, regular, left + BLOCK_PADDING, top - 24f,
				form.getSiteSafetyPlanRequired() == null ? "Yes [ ]   No [ ]"
						: form.getSiteSafetyPlanRequired() ? "Yes [X]   No [ ]" : "Yes [ ]   No [X]");
		drawTextLine(stream, bold, left + BLOCK_PADDING, block4Bottom - 13f,
				"5. Approved Site Safety Plan(s) Located At");
		drawTextLine(stream, regular, left + BLOCK_PADDING, block5Bottom + 7f,
				safe(form.getSiteSafetyPlan().getLocatedAt()));
		drawPreparedByMetadataSection(stream, bold, regular, FONT_SIZE, 8f, BLOCK_PADDING, left,
				layout.y() + 1f, layout.width(), 22f, 6f, "Prepared by", form.getPreparedByName(),
				form.getPreparedByPositionTitle(), form.getPreparedBySignature(), "ICS 208", form.getIapPage(),
				formatPdfDateTime(data, form.getPreparedDateTime()));
	}

	/**
	 * Draws one line of text at its baseline.
	 *
	 * @param stream page stream.
	 * @param font display font.
	 * @param x left coordinate.
	 * @param y baseline.
	 * @param text printable text.
	 * @throws IOException when PDF drawing fails.
	 */
	private static void drawTextLine(PDPageContentStream stream, PDType1Font font, float x, float y, String text)
			throws IOException {
		stream.beginText();
		stream.setFont(font, FONT_SIZE);
		stream.newLineAtOffset(x, y);
		stream.showText(safe(text));
		stream.endText();
	}

	/**
	 * Wraps paragraphs for the standard PDF font.
	 *
	 * @param content source text.
	 * @param width target character width.
	 * @return wrapped lines preserving paragraph breaks.
	 */
	private static List<String> wrapContent(String content, int width) {
		List<String> result = new ArrayList<>();
		if (content == null || content.isEmpty()) {
			return result;
		}
		for (String paragraph : content.split("\\R", -1)) {
			result.addAll(paragraph.isBlank() ? List.of("") : new Ics208PdfRenderer().wrap(paragraph, width));
		}
		return result;
	}

	/**
	 * Returns text safe for PDFBox standard fonts.
	 *
	 * @param value source text.
	 * @return printable ASCII text.
	 */
	private static String safe(String value) {
		return value == null ? "" : value.replaceAll("[^\\x20-\\x7E]", "?");
	}

	/**
	 * Draws local and IAP page labels.
	 *
	 * @param stream page stream.
	 * @param font regular font.
	 * @param layout page layout.
	 * @param pageNumber local page number.
	 * @param iapFirstPage first IAP page.
	 * @throws IOException when drawing fails.
	 */
	private static void drawPageLabel(PDPageContentStream stream, PDType1Font font, FormLayout layout, int pageNumber,
			String iapFirstPage) throws IOException {
		String label = "ICS 208 - Page " + pageNumber;
		if (iapFirstPage != null && !iapFirstPage.isBlank()) {
			try {
				label += " | IAP Page " + (Integer.parseInt(iapFirstPage) + pageNumber - 1);
			} catch (NumberFormatException exception) {
				label += " | IAP Page " + iapFirstPage;
			}
		}
		stream.beginText();
		stream.setFont(font, 8f);
		stream.newLineAtOffset(layout.x() + BLOCK_PADDING, layout.y() + 8f);
		stream.showText(safe(label));
		stream.endText();
	}
}
