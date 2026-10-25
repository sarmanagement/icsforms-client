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
import org.apache.pdfbox.util.Matrix;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.Ics205Form;
import org.sarmanagement.icsforms.model.RadioChannel;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders the landscape ICS 205 radio plan with lossless, measured continuation pages.
 */
public class Ics205PdfRenderer extends AbstractPdfRenderer implements PdfFormRenderer {
	private static final float FONT_SIZE = 8f;
	private static final float LEADING = 10f;
	private static final float PADDING = 4f;
	private static final float TABLE_HEADER_HEIGHT = 38f;
	private static final float SECTION_HEADING_HEIGHT = 16f;
	private static final float INSTRUCTIONS_HEIGHT = 66f;
	private static final float FOOTER_HEIGHT = 20f;
	private static final float PORTRAIT_FOOTER_CLEARANCE = 24f;
	private static final int BODY_ROWS = 8;
	private static final float[] COLUMN_WEIGHTS = {34, 26, 44, 70, 60, 58, 32, 48, 58, 32, 48, 32, 78};
	private static final String[] COLUMN_HEADINGS = {"Zone / Group", "Ch #", "Function", "Channel Name",
			"Assignment", "RX Frequency", "RX BW", "RX Tone / NAC", "TX Frequency", "TX BW", "TX Tone / NAC",
			"Mode", "Remarks"};

	/** {@inheritDoc} */
	@Override
	public String getFormKey() {
		return "ICS 205";
	}

	/** {@inheritDoc} */
	@Override
	public void render(AppData data, Path outputFile) throws IOException {
		render(data, outputFile, false);
	}

	/**
	 * Renders native landscape content, optionally rotated onto physical portrait sheets.
	 *
	 * @param data source incident
	 * @param outputFile destination PDF
	 * @param portraitFit whether to rotate content, not page dictionaries, for an IAP
	 * @throws IOException when output fails
	 */
	void render(AppData data, Path outputFile, boolean portraitFit) throws IOException {
		AppData source = data == null ? new AppData() : data;
		PdfText.validateForm(source, getFormKey());
		if (outputFile.getParent() != null) {
			Files.createDirectories(outputFile.getParent());
		}
		try (PDDocument document = new PDDocument()) {
			Plan plan = plan(source);
			for (int i = 0; i < plan.pages().size(); i++) {
				drawPage(document, source, plan, i, portraitFit);
			}
			document.save(outputFile.toFile());
		}
	}

	/**
	 * Counts exactly the measured pages used for rendering and IAP numbering.
	 *
	 * @param data incident and paper settings
	 * @return number of landscape pages, including text continuations
	 */
	static int pageCount(AppData data) {
		try {
			return new Ics205PdfRenderer().plan(data == null ? new AppData() : data).pages().size();
		} catch (IOException exception) {
			throw new IllegalArgumentException("Cannot measure ICS 205 text", exception);
		}
	}

	/**
	 * Creates a landscape sheet using the configured portrait paper dimensions.
	 *
	 * @param data paper preferences
	 * @return unrotated landscape page
	 */
	private PDPage landscapePage(AppData data) {
		PDRectangle paper = pageSize(data);
		return new PDPage(new PDRectangle(paper.getHeight(), paper.getWidth()));
	}

	/**
	 * Reserves an upright portrait footer without scaling text or violating print margins.
	 * Symmetric side clearances also keep native landscape and portrait pagination identical.
	 *
	 * @param data paper and minimum-margin preferences
	 * @return shared landscape body bounds for both export orientations
	 */
	private FormLayout radioLayout(AppData data) {
		FormLayout layout = formLayout(landscapePage(data), data);
		return new FormLayout(layout.x() + PORTRAIT_FOOTER_CLEARANCE, layout.y(),
				layout.width() - 2 * PORTRAIT_FOOTER_CLEARANCE, layout.height(), layout.headerBaseline(),
				layout.pageWidth(), layout.pageHeight(), layout.pageMargin());
	}

	/**
	 * Measures and paginates all printable values without changing the incident.
	 *
	 * @param data incident document
	 * @return immutable layout plan
	 * @throws IOException when font metrics fail
	 */
	private Plan plan(AppData data) throws IOException {
		PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
		FormLayout layout = radioLayout(data);
		Ics205Form form = data.getForm205();
		float[] widths = columnWidths(layout.width());
		PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
		List<List<String>> headings = new ArrayList<>();
		for (int i = 0; i < COLUMN_HEADINGS.length; i++) {
			headings.add(lines(bold, COLUMN_HEADINGS[i], widths[i]));
		}
		float tableHeaderHeight = SECTION_HEADING_HEIGHT + Math.max(TABLE_HEADER_HEIGHT, height(headings));
		List<List<String>> metadata = List.of(
				lines(font, "1. Incident Name: " + data.getIncidentContext().getIncidentName()
						+ "\n2. Date Prepared: " + formatPdfDateTime(data, form.getDatePrepared()), layout.width() / 2),
				lines(font, "3. Operational Period: "
						+ formatPdfDateTime(data, data.getIncidentContext().getOperationalPeriodStart()) + " to "
						+ formatPdfDateTime(data, data.getIncidentContext().getOperationalPeriodEnd()), layout.width() / 2));
		List<List<String>> preparer = List.of(
				lines(font, "6. Prepared By: " + form.getPreparedByName(), layout.width() / 3),
				lines(font, "Signature: " + form.getPreparedBySignature(), layout.width() / 3),
				lines(font, "Date / Time: " + formatPdfDateTime(data, form.getPreparedDateTime()), layout.width() / 3));
		float metadataHeight = Math.max(48, height(metadata));
		float preparerHeight = Math.max(40, height(preparer));
		float bodyHeight = layout.height() - metadataHeight - preparerHeight - FOOTER_HEIGHT;
		float tableHeight = bodyHeight - INSTRUCTIONS_HEIGHT;
		float rowArea = tableHeight - tableHeaderHeight;
		if (rowArea < SECTION_HEADING_HEIGHT + LEADING + PADDING * 2) {
			throw new IllegalArgumentException("ICS 205 margins or repeated metadata leave no printable body area.");
		}
		float minimumRow = rowArea / BODY_ROWS;
		List<List<List<String>>> channels = new ArrayList<>();
		for (RadioChannel channel : form.getChannels()) {
			if (channel != null && channel.isIncludeOnForm()) {
				List<String> values = channelValues(channel);
				List<List<String>> cells = new ArrayList<>();
				for (int i = 0; i < values.size(); i++) {
					cells.add(lines(font, values.get(i), widths[i]));
				}
				channels.add(cells);
			}
		}
		List<String> instructions = lines(font, form.getSpecialInstructions(), layout.width());
		List<Sheet> sheets = new ArrayList<>();
		int channelIndex = 0;
		int channelLine = 0;
		int instructionLine = 0;
		do {
			List<Row> rows = new ArrayList<>();
			float used = 0;
			while (channelIndex < channels.size() && rows.size() < BODY_ROWS) {
				List<List<String>> cells = channels.get(channelIndex);
				int remaining = maxLines(cells) - channelLine;
				float continuationHeight = channelLine > 0 ? SECTION_HEADING_HEIGHT : 0;
				int capacity = (int) ((rowArea - used - continuationHeight - PADDING * 2) / LEADING);
				if (capacity < 1 || rowArea - used < minimumRow + continuationHeight - 0.01f) {
					break;
				}
				int count = Math.min(remaining, capacity);
				float rowHeight = Math.max(minimumRow, count * LEADING + PADDING * 2) + continuationHeight;
				String continuation = channelLine > 0
						? "Ch # " + cells.get(1).get(0) + " / " + cells.get(3).get(0) + " (continued)" : "";
				rows.add(new Row(slice(cells, channelLine, count), rowHeight, continuation));
				used += rowHeight;
				channelLine += count;
				if (channelLine >= maxLines(cells)) {
					channelIndex++;
					channelLine = 0;
				}
			}
			while (rows.size() < BODY_ROWS && used + minimumRow <= rowArea + 0.01f) {
				rows.add(new Row(List.of(), minimumRow, ""));
				used += minimumRow;
			}
			// Instruction-only continuations use the otherwise empty table body.
			boolean instructionsOnly = channelIndex >= channels.size() && !sheets.isEmpty()
					&& rows.stream().allMatch(row -> row.cells().isEmpty());
			if (instructionsOnly) {
				rows.clear();
				used = 0;
			}
			float instructionHeight = bodyHeight - tableHeaderHeight - used;
			int capacity = Math.max(1, (int) ((instructionHeight - 22) / LEADING));
			int end = Math.min(instructions.size(), instructionLine + capacity);
			sheets.add(new Sheet(List.copyOf(rows), List.copyOf(instructions.subList(instructionLine, end)),
					instructionHeight));
			instructionLine = end;
		} while (channelIndex < channels.size() || instructionLine < instructions.size());
		return new Plan(List.copyOf(sheets), metadata, preparer, metadataHeight, preparerHeight, widths,
				List.copyOf(headings), tableHeaderHeight);
	}

	/**
	 * Projects all native fields into the standard grid, labeling extra radio metadata.
	 *
	 * @param channel native channel with string-valued frequencies
	 * @return ordered cell values
	 */
	private List<String> channelValues(RadioChannel channel) {
		String remarks = PdfText.normalize(channel.getRemarks());
		for (String[] detail : new String[][]{{"Digital protocol", channel.getDigitalProtocol()},
				{"System", channel.getSystem()}, {"TX policy", channel.getTxPolicy()}}) {
			if (detail[1] != null && !detail[1].isBlank()) {
				remarks += (remarks.isEmpty() ? "" : "\n") + detail[0] + ": " + detail[1];
			}
		}
		return List.of(text(channel.getZoneGroup()), text(channel.getChannelNumber()), text(channel.getFunction()),
				text(channel.getChannelName()), text(channel.getAssignment()), frequency(channel.getRxFrequency()),
				text(channel.getRxBandwidth()), text(channel.getRxToneNac()), frequency(channel.getTxFrequency()),
				text(channel.getTxBandwidth()), text(channel.getTxToneNac()), text(channel.getMode()), remarks);
	}

	/**
	 * Formats numeric frequencies for the printed form without changing native model values.
	 *
	 * @param value native frequency text
	 * @return four-decimal display text, or the original nonnumeric frequency string
	 */
	private String frequency(String value) {
		String source = text(value);
		if (source.matches("[0-9]+(?:\\.[0-9]+)?")) {
			return new BigDecimal(source).setScale(4, RoundingMode.HALF_UP).toPlainString();
		}
		return source;
	}

	/**
	 * Draws a measured page, repeating incident, operational-period, and preparer data.
	 *
	 * @param document destination
	 * @param data source document
	 * @param plan measured layout
	 * @param index zero-based page index
	 * @param portraitFit whether the landscape body is placed on a portrait sheet
	 * @throws IOException when PDF output fails
	 */
	private void drawPage(PDDocument document, AppData data, Plan plan, int index, boolean portraitFit)
			throws IOException {
		PDPage page = portraitFit ? newPage(data) : landscapePage(data);
		page.setCropBox(new PDRectangle(page.getMediaBox().getWidth(), page.getMediaBox().getHeight()));
		document.addPage(page);
		FormLayout layout = radioLayout(data);
		PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
		PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
		Sheet sheet = plan.pages().get(index);
		try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
			stream.saveGraphicsState();
			if (portraitFit) {
				stream.transform(new Matrix(0, 1, -1, 0, page.getMediaBox().getWidth(), 0));
			}
			drawFormHeader(stream, bold, layout, "ICS 205",
					"INCIDENT RADIO COMMUNICATIONS PLAN" + (index == 0 ? "" : " (CONTINUED)"));
			float y = layout.top();
			cells(stream, font, layout.x(), y, new float[]{layout.width() / 2, layout.width() / 2},
					plan.metadataHeight(), plan.metadata());
			y -= plan.metadataHeight();
			cells(stream, bold, layout.x(), y, new float[]{layout.width()}, SECTION_HEADING_HEIGHT,
					List.of(List.of("4. Basic Radio Channel Use")));
			y -= SECTION_HEADING_HEIGHT;
			float headingHeight = plan.tableHeaderHeight() - SECTION_HEADING_HEIGHT;
			cells(stream, bold, layout.x(), y, plan.widths(), headingHeight, plan.headings());
			y -= headingHeight;
			for (Row row : sheet.rows()) {
				float height = row.height();
				if (!row.continuation().isEmpty()) {
					cells(stream, font, layout.x(), y, new float[]{layout.width()}, SECTION_HEADING_HEIGHT,
							List.of(List.of(row.continuation())));
					y -= SECTION_HEADING_HEIGHT;
					height -= SECTION_HEADING_HEIGHT;
				}
				cells(stream, font, layout.x(), y, plan.widths(), height, row.cells());
				y -= height;
			}
			List<String> instructionText = new ArrayList<>();
			instructionText.add("5. Special Instructions" + (index == 0 ? "" : " (continued)"));
			instructionText.addAll(sheet.instructions());
			cells(stream, font, layout.x(), y, new float[]{layout.width()}, sheet.instructionHeight(),
					List.of(instructionText));
			y -= sheet.instructionHeight();
			cells(stream, font, layout.x(), y, new float[]{layout.width() / 3, layout.width() / 3, layout.width() / 3},
					plan.preparerHeight(), plan.preparer());
			stream.restoreGraphicsState();
		}
		// Keep both page labels upright on the physical sheet, outside the rotated body.
		try (PDPageContentStream stream = new PDPageContentStream(document, page,
				PDPageContentStream.AppendMode.APPEND, true, true)) {
			float margin = Math.max(FORM_MARGIN, pageMargin(data));
			float numberBaseline = margin + 8;
			write(stream, font, margin + PADDING, numberBaseline,
					"ICS 205 - Page " + (index + 1) + " of " + plan.pages().size());
			write(stream, font, page.getMediaBox().getWidth() - margin - 90, numberBaseline,
					"IAP Page: " + addPageOffset(data.getForm205().getIapPage(), index));
		}
	}

	/**
	 * Draws cell borders and all measured lines without clipping or ellipsis.
	 *
	 * @param stream page stream
	 * @param font text font
	 * @param x left coordinate
	 * @param top top coordinate
	 * @param widths cell widths
	 * @param height row height
	 * @param values wrapped cell lines
	 * @throws IOException when drawing fails
	 */
	private void cells(PDPageContentStream stream, PDType1Font font, float x, float top, float[] widths,
			float height, List<List<String>> values) throws IOException {
		for (int i = 0; i < widths.length; i++) {
			stream.addRect(x, top - height, widths[i], height);
			stream.stroke();
			if (i < values.size()) {
				float baseline = top - PADDING - FONT_SIZE;
				for (String line : values.get(i)) {
					write(stream, font, x + PADDING, baseline, line);
					baseline -= LEADING;
				}
			}
			x += widths[i];
		}
	}

	/**
	 * Writes a balanced inline text block.
	 *
	 * @param stream page stream
	 * @param font text font
	 * @param x baseline left
	 * @param y baseline height
	 * @param value validated text
	 * @throws IOException when writing fails
	 */
	private void write(PDPageContentStream stream, PDType1Font font, float x, float y, String value) throws IOException {
		try (PdfText.TextBlock block = PdfText.begin(stream)) {
			stream.setFont(font, FONT_SIZE);
			stream.newLineAtOffset(x, y);
			stream.showText(PdfText.singleLine(value));
		}
	}

	/**
	 * Wraps by actual font width, including unbroken strings and explicit newlines.
	 *
	 * @param font measuring font
	 * @param value source text
	 * @param width cell width including padding
	 * @return complete wrapped content
	 * @throws IOException when metrics fail
	 */
	private List<String> lines(PDType1Font font, String value, float width) throws IOException {
		List<String> result = new ArrayList<>();
		for (String paragraph : PdfText.normalize(value).split("\n", -1)) {
			String remaining = paragraph;
			while (!remaining.isEmpty()) {
				int end = 0;
				while (end < remaining.length() && font.getStringWidth(remaining.substring(0, end + 1))
						/ 1000 * FONT_SIZE <= width - PADDING * 2) {
					end++;
				}
				end = Math.max(1, end);
				int space = remaining.lastIndexOf(' ', end - 1);
				if (end < remaining.length() && space > 0) {
					end = space + 1;
				}
				result.add(remaining.substring(0, end).stripTrailing());
				remaining = remaining.substring(end);
			}
			if (paragraph.isEmpty()) {
				result.add("");
			}
		}
		return result;
	}

	/**
	 * Resolves relative column widths against the printable form frame.
	 *
	 * @param width printable width
	 * @return column widths summing to the frame width
	 */
	private float[] columnWidths(float width) {
		float total = 0;
		for (float weight : COLUMN_WEIGHTS) {
			total += weight;
		}
		float[] widths = new float[COLUMN_WEIGHTS.length];
		for (int i = 0; i < widths.length; i++) {
			widths[i] = width * COLUMN_WEIGHTS[i] / total;
		}
		return widths;
	}

	/**
	 * Measures the height needed for a set of wrapped cells.
	 *
	 * @param cells wrapped cell text
	 * @return height including padding
	 */
	private float height(List<List<String>> cells) {
		return maxLines(cells) * LEADING + PADDING * 2;
	}

	/**
	 * Finds the longest cell in a row.
	 *
	 * @param cells wrapped cells
	 * @return number of logical lines
	 */
	private int maxLines(List<List<String>> cells) {
		return cells.stream().mapToInt(List::size).max().orElse(1);
	}

	/**
	 * Extracts a continuation segment from every cell, retaining column alignment.
	 *
	 * @param cells complete row
	 * @param start first logical line
	 * @param count maximum lines
	 * @return printable segment
	 */
	private List<List<String>> slice(List<List<String>> cells, int start, int count) {
		List<List<String>> result = new ArrayList<>();
		for (int i = 0; i < cells.size(); i++) {
			List<String> cell = cells.get(i);
			if (start > 0 && start >= cell.size() && i <= 4) {
				result.add(List.of(cell.get(0)));
			} else {
				result.add(cell.subList(Math.min(start, cell.size()), Math.min(start + count, cell.size())));
			}
		}
		return result;
	}

	/**
	 * Provides null-safe text without mutating native values.
	 *
	 * @param value optional native string
	 * @return non-null string
	 */
	private String text(String value) {
		return value == null ? "" : value;
	}

	/**
	 * Holds one complete measured export.
	 *
	 * @param pages continuation sheets
	 * @param metadata repeated incident and period lines
	 * @param preparer repeated preparer lines
	 * @param metadataHeight incident block height
	 * @param preparerHeight preparer block height
	 * @param widths table column widths
	 * @param headings measured column heading lines
	 * @param tableHeaderHeight combined section and column heading height
	 */
	private record Plan(List<Sheet> pages, List<List<String>> metadata, List<List<String>> preparer,
			float metadataHeight, float preparerHeight, float[] widths, List<List<String>> headings,
			float tableHeaderHeight) {
	}

	/**
	 * Holds one physical page's body.
	 *
	 * @param rows channel segments and blank reference rows
	 * @param instructions instruction continuation lines
	 * @param instructionHeight instruction block height
	 */
	private record Sheet(List<Row> rows, List<String> instructions, float instructionHeight) {
	}

	/**
	 * Holds a whole row or one lossless continuation segment.
	 *
	 * @param cells measured cell lines
	 * @param height printable row height
	 * @param continuation identifying banner for a split channel row, or empty for whole rows
	 */
	private record Row(List<List<String>> cells, float height, String continuation) {
	}
}
