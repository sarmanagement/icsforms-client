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
import org.sarmanagement.icsforms.model.IncidentContext;
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

	/** {@inheritDoc} */
	@Override
	public String getFormKey() {
		return "ICS 215A";
	}

	/**
	 * Estimates continuation pages using the same wrapped row sizing as the renderer.
	 *
	 * @param data incident data.
	 * @return rendered page count, at least one.
	 */
	public static int pageCount(AppData data) {
		AppData safeData = data == null ? new AppData() : data;
		PDPage page = new Ics215aPdfRenderer().newPage(safeData);
		FormLayout layout = new Ics215aPdfRenderer().formLayout(page, safeData);
		float y = layout.top() - 22 - (4 * 14) - 5 - 22;
		float minY = layout.y() + 34;
		int count = 1;
		int rowIndex = 0;
		int rowLineOffset = 0;
		float[] widths = {layout.width() * .18f, layout.width() * .36f, layout.width() * .46f};
		List<SafetyAnalysisEntry> entries = safeData.getForm215a().getEntries();
		while (rowIndex < entries.size()) {
			if (y - minY < 34) {
				count++;
				y = layout.top() - 22 - (4 * 14) - 5 - 22;
			}
			SafetyAnalysisEntry entry = entries.get(rowIndex);
			int totalLines = Math.max(wrapCell(entry.getIncidentArea(), (int) (widths[0] / 5)).size(),
					Math.max(wrapCell(entry.getHazardsRisks(), (int) (widths[1] / 5)).size(),
							wrapCell(entry.getMitigations(), (int) (widths[2] / 5)).size()));
			int fitLines = Math.max(1, (int) ((y - minY - 12) / LINE_HEIGHT));
			int renderedLines = Math.min(totalLines - rowLineOffset, fitLines);
			float height = Math.max(34, renderedLines * LINE_HEIGHT + 12);
			y -= height;
			rowLineOffset += renderedLines;
			if (rowLineOffset >= totalLines) {
				rowIndex++;
				rowLineOffset = 0;
			} else {
				count++;
				y = layout.top() - 22 - (4 * 14) - 5 - 22;
			}
		}
		return count;
	}

	/** {@inheritDoc} */
	@Override
	public void render(AppData data, Path outputFile) throws IOException {
		Files.createDirectories(outputFile.getParent());
		AppData safeData = data == null ? new AppData() : data;
		try (PDDocument document = new PDDocument()) {
			renderPages(document, safeData);
			document.save(outputFile.toFile());
		}
	}

	/**
	 * Renders row content across continuation pages.
	 *
	 * @param document target PDF document.
	 * @param data source incident data.
	 * @throws IOException when PDF output fails.
	 */
	private void renderPages(PDDocument document, AppData data) throws IOException {
		Ics215aForm form = data.getForm215a();
		IncidentContext context = data.getIncidentContext();
		PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
		PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
		int rowIndex = 0;
		int rowLineOffset = 0;
		int pageNumber = 1;
		do {
			PDPage page = newPage(data);
			document.addPage(page);
			try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
				FormLayout layout = formLayout(page, data);
				drawFormHeader(stream, bold, layout, "ICS 215A", "INCIDENT ACTION PLAN SAFETY ANALYSIS");
				float y = layout.top() - 22;
				y = line(stream, bold, layout.x(), y, 10, "1. Incident Name: " + safe(context.getIncidentName()));
				y = line(stream, regular, layout.x(), y, 10, "2. Incident Number: " + form.getIncidentNumber());
				y = line(stream, regular, layout.x(), y, 10,
						"3. Date/Time Prepared: " + formatPdfDateTime(data, form.getPreparedDateTime()));
				y = line(stream, regular, layout.x(), y, 10, "4. Operational Period: "
						+ formatPdfDateTime(data, context.getOperationalPeriodStart()) + " to "
						+ formatPdfDateTime(data, context.getOperationalPeriodEnd()));
				y -= 5;
				float[] widths = {layout.width() * .18f, layout.width() * .36f, layout.width() * .46f};
				float[] xs = {layout.x(), layout.x() + widths[0], layout.x() + widths[0] + widths[1]};
				float headerHeight = 22;
				drawRow(stream, bold, xs, widths, y, headerHeight,
						List.of("5. Incident Area", "6. Hazards / Risks", "7. Mitigations"));
				y -= headerHeight;
				float maxY = layout.y() + 34;
				while (rowIndex < form.getEntries().size() && y > maxY) {
					SafetyAnalysisEntry entry = form.getEntries().get(rowIndex);
					List<List<String>> cells = List.of(wrapCell(entry.getIncidentArea(), 18),
							wrapCell(entry.getHazardsRisks(), 36), wrapCell(entry.getMitigations(), 46));
					int totalLines = Math.max(cells.get(0).size(), Math.max(cells.get(1).size(), cells.get(2).size()));
					if (y - maxY < 34) {
						break;
					}
					int fitLines = Math.max(1, (int) ((y - maxY - 12) / LINE_HEIGHT));
					int renderedLines = Math.min(totalLines - rowLineOffset, fitLines);
					float renderedHeight = Math.max(34, renderedLines * LINE_HEIGHT + 12);
					drawRow(stream, regular, xs, widths, y, renderedHeight,
							List.of(joinLines(cells.get(0), rowLineOffset, renderedLines),
									joinLines(cells.get(1), rowLineOffset, renderedLines),
									joinLines(cells.get(2), rowLineOffset, renderedLines)));
					y -= renderedHeight;
					rowLineOffset += renderedLines;
					if (rowLineOffset >= totalLines) {
						rowIndex++;
						rowLineOffset = 0;
					}
				}
				if (form.getEntries().isEmpty()) {
					drawRow(stream, regular, xs, widths, y, 34, List.of("", "", ""));
					y -= 34;
				}
				y -= 12;
				if (y > layout.y() + 15) {
					y = line(stream, regular, layout.x(), y, 10, "8. Safety Officer: "
							+ form.getSafetyOfficerName() + " | Signature: " + form.getSafetyOfficerSignature());
					y = line(stream, regular, layout.x(), y, 10, "Operations Section Chief: "
							+ form.getOperationsSectionChiefName() + " | Signature: "
							+ form.getOperationsSectionChiefSignature());
					line(stream, regular, layout.x(), y, 10, "Reviewed: "
							+ formatPdfDateTime(data, form.getReviewedDateTime()) + "    Prepared: "
							+ formatPdfDateTime(data, form.getPreparedDateTime()));
				}
				String label = "ICS 215A - Page " + pageNumber
						+ (rowIndex < form.getEntries().size() ? " (continued)" : "")
						+ iapPageLabel(form.getIapPage(), pageNumber);
				line(stream, regular, layout.x(), layout.y() + 8, 8, label);
			}
			pageNumber++;
		} while (rowIndex < form.getEntries().size());
	}

	/**
	 * Draws an outlined table row and wrapped cells.
	 *
	 * @param stream PDF content stream.
	 * @param font cell font.
	 * @param xs column x-coordinates.
	 * @param widths column widths.
	 * @param top row top.
	 * @param height row height.
	 * @param cells cell text.
	 * @throws IOException when PDF drawing fails.
	 */
	private static void drawRow(PDPageContentStream stream, PDType1Font font, float[] xs, float[] widths,
			float top, float height, List<String> cells) throws IOException {
		for (int column = 0; column < cells.size(); column++) {
			stream.addRect(xs[column], top - height, widths[column], height);
			stream.stroke();
			stream.beginText();
			stream.setFont(font, FONT_SIZE);
			stream.newLineAtOffset(xs[column] + 4, top - 13);
			for (String line : cells.get(column).split("\n", -1)) {
				stream.showText(safe(line));
				stream.newLineAtOffset(0, -LINE_HEIGHT);
			}
			stream.endText();
		}
	}

	/**
	 * Draws a single text line at the specified baseline.
	 *
	 * @param stream PDF stream.
	 * @param font text font.
	 * @param x left coordinate.
	 * @param y baseline.
	 * @param size font size.
	 * @param value displayed text.
	 * @return the next baseline.
	 * @throws IOException when PDF drawing fails.
	 */
	private static float line(PDPageContentStream stream, PDType1Font font, float x, float y, float size, String value)
			throws IOException {
		stream.beginText();
		stream.setFont(font, size);
		stream.newLineAtOffset(x, y);
		stream.showText(safe(value));
		stream.endText();
		return y - size - 4;
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
		for (String paragraph : safe(value).split("\\R", -1)) {
			result.addAll(new Ics215aPdfRenderer().wrap(paragraph, width));
		}
		return result;
	}

	/**
	 * Joins the requested cell-line segment for the current printable row.
	 *
	 * @param lines wrapped cell lines.
	 * @param offset first line index.
	 * @param count maximum lines.
	 * @return text for this row segment.
	 */
	private static String joinLines(List<String> lines, int offset, int count) {
		int start = Math.min(offset, lines.size());
		int end = Math.min(start + count, lines.size());
		return String.join("\n", lines.subList(start, end));
	}

	/**
	 * Returns a non-null display string.
	 *
	 * @param value source value.
	 * @return non-null value.
	 */
	private static String safe(String value) {
		return value == null ? "" : value.replaceAll("[^\\x20-\\x7E]", "?");
	}

	/**
	 * Returns the IAP page label for a continuation page.
	 *
	 * @param firstPage first IAP page label.
	 * @param pageOffset local page number.
	 * @return optional IAP page suffix.
	 */
	private static String iapPageLabel(String firstPage, int pageOffset) {
		if (firstPage == null || firstPage.isBlank()) {
			return "";
		}
		try {
			return " | IAP Page " + (Integer.parseInt(firstPage) + pageOffset - 1);
		} catch (NumberFormatException exception) {
			return " | IAP Page " + firstPage;
		}
	}
}
