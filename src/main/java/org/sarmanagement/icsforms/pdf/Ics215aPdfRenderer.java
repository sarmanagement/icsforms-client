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
import org.sarmanagement.icsforms.model.Ics215aForm;
import org.sarmanagement.icsforms.model.SafetyAnalysisEntry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders the ICS 215A Incident Action Plan Safety Analysis.
 */
public class Ics215aPdfRenderer extends AbstractPdfRenderer implements PdfFormRenderer {
	private static final float FONT_SIZE = 9f;
	private static final float LINE_HEIGHT = 11f;
	private static final float METADATA_HEIGHT = 62f;
	private static final float TABLE_HEADER_HEIGHT = 25f;
	private static final float FOOTER_HEIGHT = 74f;
	private static final float CELL_PADDING = 5f;

	/** {@inheritDoc} */
	@Override
	public String getFormKey() {
		return "ICS 215A";
	}

	/**
	 * Counts pages using the same row wrapping and available table height as rendering.
	 *
	 * @param data incident data.
	 * @return rendered page count, at least one.
	 */
	public static int pageCount(AppData data) {
		AppData safeData = data == null ? new AppData() : data;
		Ics215aPdfRenderer renderer = new Ics215aPdfRenderer();
		FormLayout layout = renderer.formLayout(renderer.newPage(safeData), safeData);
		int pages = 1;
		float y = renderer.tableStartY(layout);
		float minY = renderer.tableBottomY(layout);
		int rowIndex = 0;
		int lineOffset = 0;
		while (rowIndex < safeData.getForm215a().getEntries().size()) {
			if (y - minY < 34f) {
				pages++;
				y = renderer.tableStartY(layout);
			}
			SafetyAnalysisEntry entry = safeData.getForm215a().getEntries().get(rowIndex);
			int lineCount = renderer.rowLineCount(entry, layout);
			int fit = Math.max(1, (int) ((y - minY - 2 * CELL_PADDING) / LINE_HEIGHT));
			int rendered = Math.min(lineCount - lineOffset, fit);
			y -= Math.max(34f, rendered * LINE_HEIGHT + 2 * CELL_PADDING);
			lineOffset += rendered;
			if (lineOffset == lineCount) {
				rowIndex++;
				lineOffset = 0;
			} else {
				pages++;
				y = renderer.tableStartY(layout);
			}
		}
		return pages;
	}

	/**
	 * Returns the top of the table body after metadata and its header.
	 *
	 * @param layout form layout.
	 * @return initial body baseline.
	 */
	private float tableStartY(FormLayout layout) {
		return layout.top() - METADATA_HEIGHT - TABLE_HEADER_HEIGHT;
	}

	/**
	 * Returns the bottom boundary of the table body above the signature footer.
	 *
	 * @param layout form layout.
	 * @return table bottom.
	 */
	private float tableBottomY(FormLayout layout) {
		return layout.y() + FOOTER_HEIGHT;
	}

	/**
	 * Calculates maximum wrapped row lines at the active page width.
	 *
	 * @param entry safety analysis row.
	 * @param layout form layout.
	 * @return wrapped line count, at least one.
	 */
	private int rowLineCount(SafetyAnalysisEntry entry, FormLayout layout) {
		float[] widths = columnWidths(layout);
		return Math.max(wrapCell(entry.getIncidentArea(), charWidth(widths[0])).size(),
				Math.max(wrapCell(entry.getHazardsRisks(), charWidth(widths[1])).size(),
						wrapCell(entry.getMitigations(), charWidth(widths[2])).size()));
	}

	/**
	 * Returns the number of characters that fit conservatively in a table column.
	 *
	 * @param width column width in points.
	 * @return maximum approximate characters.
	 */
	private int charWidth(float width) {
		return Math.max(1, (int) ((width - 2 * CELL_PADDING) / (FONT_SIZE * 0.53f)));
	}

	/**
	 * Calculates the table column widths.
	 *
	 * @param layout form layout.
	 * @return incident area, hazards, and mitigation widths.
	 */
	private float[] columnWidths(FormLayout layout) {
		return new float[] {layout.width() * .20f, layout.width() * .36f, layout.width() * .44f};
	}

	/** {@inheritDoc} */
	@Override
	public void render(AppData data, Path outputFile) throws IOException {
		PdfText.validateForm(data, getFormKey());
		Files.createDirectories(outputFile.getParent());
		AppData safeData = data == null ? new AppData() : data;
		try (PDDocument document = new PDDocument()) {
			renderPages(document, safeData);
			document.save(outputFile.toFile());
		}
	}

	/**
	 * Renders table rows and continuation pages inside the ICS 215A form frame.
	 *
	 * @param document target PDF document.
	 * @param data source incident data.
	 * @throws IOException when PDF output fails.
	 */
	private void renderPages(PDDocument document, AppData data) throws IOException {
		Ics215aForm form = data.getForm215a();
		PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
		PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
		int rowIndex = 0;
		int lineOffset = 0;
		int pageNumber = 1;
		do {
			PDPage page = newPage(data);
			document.addPage(page);
			FormLayout layout = formLayout(page, data);
			try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
				drawFormHeader(stream, bold, layout, "ICS 215A",
						"INCIDENT ACTION PLAN SAFETY ANALYSIS", pageNumber);
				drawFormFrame(stream, layout);
				drawMetadata(stream, bold, regular, layout, data);
				float[] widths = columnWidths(layout);
				drawTableHeader(stream, bold, layout, widths);
				float y = tableStartY(layout);
				float minY = tableBottomY(layout);
				while (rowIndex < form.getEntries().size() && y - minY >= 34f) {
					SafetyAnalysisEntry entry = form.getEntries().get(rowIndex);
					List<List<String>> columns = List.of(
							wrapCell(entry.getIncidentArea(), charWidth(widths[0])),
							wrapCell(entry.getHazardsRisks(), charWidth(widths[1])),
							wrapCell(entry.getMitigations(), charWidth(widths[2])));
					int totalLines = Math.max(columns.get(0).size(),
							Math.max(columns.get(1).size(), columns.get(2).size()));
					int fit = Math.max(1, (int) ((y - minY - 2 * CELL_PADDING) / LINE_HEIGHT));
					int rendered = Math.min(totalLines - lineOffset, fit);
					float rowHeight = Math.max(34f, rendered * LINE_HEIGHT + 2 * CELL_PADDING);
					drawTableRow(stream, regular, layout, widths, y, rowHeight, columns, lineOffset, rendered);
					y -= rowHeight;
					lineOffset += rendered;
					if (lineOffset >= totalLines) {
						rowIndex++;
						lineOffset = 0;
					}
				}
				if (form.getEntries().isEmpty() && pageNumber == 1) {
					drawTableRow(stream, regular, layout, widths, y, 34f,
							List.of(List.of(""), List.of(""), List.of("")), 0, 1);
				}
				drawFooter(stream, bold, regular, layout, data, form, pageNumber);
			}
			if (rowIndex < form.getEntries().size() && lineOffset > 0) {
				pageNumber++;
				continue;
			}
			if (rowIndex < form.getEntries().size()) {
				pageNumber++;
				continue;
			}
			break;
		} while (true);
	}

	/**
	 * Draws the incident and operational period metadata blocks.
	 *
	 * @param stream PDF page stream.
	 * @param bold bold font.
	 * @param regular regular font.
	 * @param layout form layout.
	 * @param data incident data.
	 * @throws IOException when PDF drawing fails.
	 */
	private void drawMetadata(PDPageContentStream stream, PDType1Font bold, PDType1Font regular, FormLayout layout,
			AppData data) throws IOException {
		float x = layout.x();
		float top = layout.top();
		float row1Bottom = top - 27f;
		float row2Bottom = top - METADATA_HEIGHT;
		stream.moveTo(x, row1Bottom);
		stream.lineTo(x + layout.width() * .55f, row1Bottom);
		stream.moveTo(x, row2Bottom);
		stream.lineTo(x + layout.width(), row2Bottom);
		float nameEnd = x + layout.width() * .55f;
		stream.moveTo(nameEnd, top);
		stream.lineTo(nameEnd, row2Bottom);
		stream.stroke();
		drawLine(stream, bold, x + CELL_PADDING, top - 12f,
				"1. Incident Name: " + safe(data.getIncidentContext().getIncidentName()));
		drawLine(stream, bold, nameEnd + CELL_PADDING, top - 12f, "2. Operational Period");
		drawLine(stream, regular, x + CELL_PADDING, row1Bottom - 13f,
				"3. Incident Number: " + safe(data.getForm215a().getIncidentNumber()));
		drawLine(stream, regular, x + CELL_PADDING, row1Bottom - 26f,
				"4. Date/Time Prepared: " + safe(formatPdfDateTime(data, data.getForm215a().getPreparedDateTime())));
		float rightX = nameEnd + CELL_PADDING;
		drawLine(stream, regular, rightX, row1Bottom - 13f,
				"Date From: " + safe(formatPdfDate(data, data.getIncidentContext().getOperationalPeriodStart())));
		drawLine(stream, regular, rightX, row1Bottom - 26f,
				"Time From: " + safe(formatPdfTime(data, data.getIncidentContext().getOperationalPeriodStart())));
		float dateToX = nameEnd + (layout.width() - layout.width() * .55f) / 2f;
		drawLine(stream, regular, dateToX, row1Bottom - 13f,
				"Date To: " + safe(formatPdfDate(data, data.getIncidentContext().getOperationalPeriodEnd())));
		drawLine(stream, regular, dateToX, row1Bottom - 26f,
				"Time To: " + safe(formatPdfTime(data, data.getIncidentContext().getOperationalPeriodEnd())));
	}

	/**
	 * Draws the column headings for the analysis table.
	 *
	 * @param stream page stream.
	 * @param bold bold font.
	 * @param layout form layout.
	 * @param widths column widths.
	 * @throws IOException when PDF drawing fails.
	 */
	private void drawTableHeader(PDPageContentStream stream, PDType1Font bold, FormLayout layout, float[] widths)
			throws IOException {
		float top = layout.top() - METADATA_HEIGHT;
		float bottom = top - TABLE_HEADER_HEIGHT;
		drawTableGrid(stream, layout, widths, top, TABLE_HEADER_HEIGHT);
		float x = layout.x();
		drawLine(stream, bold, x + CELL_PADDING, top - 15f, "5. Incident Area");
		x += widths[0];
		drawLine(stream, bold, x + CELL_PADDING, top - 15f, "6. Hazards / Risks");
		x += widths[1];
		drawLine(stream, bold, x + CELL_PADDING, top - 15f, "7. Mitigations / Controls");
	}

	/**
	 * Draws the cells and wrapped text for one table row segment.
	 *
	 * @param stream page stream.
	 * @param font cell font.
	 * @param layout form layout.
	 * @param widths column widths.
	 * @param top row top.
	 * @param height row height.
	 * @param columns wrapped cell lines.
	 * @param offset first line offset.
	 * @param count number of lines to draw.
	 * @throws IOException when PDF drawing fails.
	 */
	private void drawTableRow(PDPageContentStream stream, PDType1Font font, FormLayout layout, float[] widths,
			float top, float height, List<List<String>> columns, int offset, int count) throws IOException {
		drawTableGrid(stream, layout, widths, top, height);
		float x = layout.x() + CELL_PADDING;
		for (int column = 0; column < columns.size(); column++) {
			float y = top - CELL_PADDING - FONT_SIZE;
			List<String> lines = columns.get(column);
			for (int index = offset; index < Math.min(lines.size(), offset + count); index++) {
				drawLine(stream, font, x, y, lines.get(index));
				y -= LINE_HEIGHT;
			}
			x += widths[column];
		}
	}

	/**
	 * Draws table row borders and column dividers.
	 *
	 * @param stream page stream.
	 * @param layout form layout.
	 * @param widths column widths.
	 * @param top row top.
	 * @param height row height.
	 * @throws IOException when PDF drawing fails.
	 */
	private void drawTableGrid(PDPageContentStream stream, FormLayout layout, float[] widths, float top, float height)
			throws IOException {
		float bottom = top - height;
		stream.moveTo(layout.x(), bottom);
		stream.lineTo(layout.x() + layout.width(), bottom);
		float x = layout.x() + widths[0];
		stream.moveTo(x, top);
		stream.lineTo(x, bottom);
		x += widths[1];
		stream.moveTo(x, top);
		stream.lineTo(x, bottom);
		stream.stroke();
	}

	/**
	 * Draws preparer/signature metadata and the form/page footer band.
	 *
	 * @param stream page stream.
	 * @param bold bold font.
	 * @param regular regular font.
	 * @param layout form layout.
	 * @param data incident data.
	 * @param form ICS 215A content.
	 * @param pageNumber local page number.
	 * @throws IOException when PDF drawing fails.
	 */
	private void drawFooter(PDPageContentStream stream, PDType1Font bold, PDType1Font regular, FormLayout layout,
			AppData data, Ics215aForm form, int pageNumber) throws IOException {
		float x = layout.x();
		float bottom = layout.y();
		float width = layout.width();
		float top = bottom + FOOTER_HEIGHT;
		float divider = top - 30f;
		float footerBandTop = bottom + 18f;
		stream.moveTo(x, top);
		stream.lineTo(x + width, top);
		stream.moveTo(x, divider);
		stream.lineTo(x + width, divider);
		stream.moveTo(x, footerBandTop);
		stream.lineTo(x + width, footerBandTop);
		stream.stroke();
		drawLine(stream, bold, x + CELL_PADDING, top - 12f,
				"8. Safety Officer: " + safe(form.getSafetyOfficerName()) + "   Signature: "
						+ safe(form.getSafetyOfficerSignature()));
		drawLine(stream, regular, x + CELL_PADDING, divider - 13f,
				"Operations Section Chief: " + safe(form.getOperationsSectionChiefName()) + "   Signature: "
						+ safe(form.getOperationsSectionChiefSignature()));
		drawFormFooterBand(stream, bold, regular, FONT_SIZE, CELL_PADDING, x, bottom, width, footerBandTop - bottom,
				.42f, "ICS 215A", safe(addPageOffset(form.getIapPage(), pageNumber - 1)),
				safe(formatPdfDateTime(data, form.getPreparedDateTime())));
	}

	/**
	 * Draws one text line at its baseline.
	 *
	 * @param stream page stream.
	 * @param font text font.
	 * @param x left coordinate.
	 * @param y baseline.
	 * @param value display text.
	 * @throws IOException when PDF drawing fails.
	 */
	private static void drawLine(PDPageContentStream stream, PDType1Font font, float x, float y, String value)
			throws IOException {
		try (PdfText.TextBlock textBlock = PdfText.begin(stream)) {
			stream.setFont(font, FONT_SIZE);
			stream.newLineAtOffset(x, y);
			stream.showText(PdfText.singleLine(safe(value)));
		}
	}

	/**
	 * Wraps one cell's text to a conservative character width.
	 *
	 * @param value source text.
	 * @param width maximum characters.
	 * @return wrapped lines.
	 */
	private static List<String> wrapCell(String value, int width) {
		List<String> result = new ArrayList<>();
		for (String paragraph : safe(value).split("\n", -1)) {
			result.addAll(new Ics215aPdfRenderer().wrap(paragraph, width));
		}
		return result;
	}

	/**
	 * Normalizes optional layout text without discarding source characters.
	 *
	 * @param value source value.
	 * @return normalized text retaining logical line breaks.
	 */
	private static String safe(String value) {
		return PdfText.normalize(value);
	}

}
