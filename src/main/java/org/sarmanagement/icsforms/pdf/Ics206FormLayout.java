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
import org.sarmanagement.icsforms.model.OrgChartEntry;
import org.sarmanagement.icsforms.model.OrganizationalChart;

import java.io.IOException;
import java.awt.Color;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Shared numbered-block and table layout for standard and adapted ICS 206 forms.
 */
final class Ics206FormLayout {
	private static final float FONT_SIZE = 8f;
	private static final float LINE_HEIGHT = 9f;
	private static final float CELL_PADDING = 3f;
	private static final float SECTION_HEIGHT = 15f;
	private static final float TABLE_HEADER_HEIGHT = 24f;
	private static final float CONTEXT_HEIGHT = 48f;
	private static final float FOOTER_HEIGHT = 34f;
	private static final float METADATA_HEIGHT = 48f;
	private static final float SECTION_GAP = 2f;

	/**
	 * Prevents construction of this layout utility.
	 */
	private Ics206FormLayout() {
	}

	/**
	 * Writes the requested numbered ICS 206 form and all continuation pages.
	 *
	 * @param renderer shared form renderer.
	 * @param data incident data.
	 * @param outputFile destination PDF path.
	 * @param formKey form identifier.
	 * @param title human-readable form title.
	 * @param iapPage IAP page text.
	 * @param preparedAt preparation timestamp.
	 * @param sections ordered numbered form sections.
	 * @param metadata preparer and reviewer footer data.
	 * @throws IOException when PDF output fails.
	 */
	static void render(AbstractPdfRenderer renderer, AppData data, Path outputFile, String formKey, String title,
			String iapPage, LocalDateTime preparedAt, List<Section> sections, FooterMetadata metadata)
			throws IOException {
		AppData safeData = data == null ? new AppData() : data;
		PdfText.validateForm(safeData, formKey);
		if (outputFile.getParent() != null) {
			Files.createDirectories(outputFile.getParent());
		}
		try (PDDocument document = new PDDocument()) {
			int totalPages = paginate(renderer, safeData, formKey, title, iapPage, preparedAt, sections, metadata,
					null, 1);
			paginate(renderer, safeData, formKey, title, iapPage, preparedAt, sections, metadata, document, totalPages);
			document.save(outputFile.toFile());
		}
	}

	/**
	 * Counts pages using the same row measurement and available geometry as rendering.
	 *
	 * @param renderer shared form renderer.
	 * @param data incident data.
	 * @param formKey form identifier.
	 * @param title human-readable form title.
	 * @param iapPage IAP page text.
	 * @param preparedAt preparation timestamp.
	 * @param sections ordered numbered form sections.
	 * @param metadata preparer and reviewer footer data.
	 * @return page count, at least one.
	 */
	static int pageCount(AbstractPdfRenderer renderer, AppData data, String formKey, String title, String iapPage,
			LocalDateTime preparedAt, List<Section> sections, FooterMetadata metadata) {
		try {
			return paginate(renderer, data == null ? new AppData() : data, formKey, title, iapPage, preparedAt, sections,
					metadata, null, 1);
		} catch (IOException exception) {
			throw new IllegalStateException("Cannot measure " + formKey + " layout", exception);
		}
	}

	/**
	 * Paginates each section and draws pages when an output document is supplied.
	 *
	 * @param renderer shared form renderer.
	 * @param data incident data.
	 * @param formKey form identifier.
	 * @param title human-readable form title.
	 * @param iapPage IAP page text.
	 * @param preparedAt preparation timestamp.
	 * @param sections ordered numbered form sections.
	 * @param metadata preparer and reviewer footer data.
	 * @param document output document, or null for page counting.
	 * @param totalPages total page count when drawing.
	 * @return number of pages.
	 * @throws IOException when PDF drawing fails.
	 */
	private static int paginate(AbstractPdfRenderer renderer, AppData data, String formKey, String title,
			String iapPage, LocalDateTime preparedAt, List<Section> sections, FooterMetadata metadata,
			PDDocument document, int totalPages) throws IOException {
		PDPage samplePage = renderer.newPage(data);
		AbstractPdfRenderer.FormLayout layout = renderer.formLayout(samplePage, data);
		PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
		PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
		int sectionIndex = 0;
		int rowIndex = 0;
		int lineOffset = 0;
		int pageCount = 0;
		while (sectionIndex < sections.size()) {
			pageCount++;
			PDPage page = null;
			if (document != null) {
				page = renderer.newPage(data);
				document.addPage(page);
				drawPage(renderer, document, page, data, layout, regular, bold, formKey, title, iapPage, preparedAt,
						metadata, pageCount, totalPages);
			}
			float y = layout.top() - CONTEXT_HEIGHT - SECTION_GAP;
			float bottom = layout.y() + FOOTER_HEIGHT + METADATA_HEIGHT + SECTION_GAP;
			while (sectionIndex < sections.size()) {
				Section section = sections.get(sectionIndex);
				boolean continued = rowIndex > 0 || lineOffset > 0;
				float required = SECTION_HEIGHT + (section.headers().isEmpty() ? 0f : TABLE_HEADER_HEIGHT);
				if (y - bottom < required + LINE_HEIGHT + CELL_PADDING * 2f) {
					break;
				}
				if (document != null) {
					drawSectionHeading(document, page, layout, bold, y, section.title()
							+ (continued ? " (CONTINUED)" : ""));
				}
				y -= SECTION_HEIGHT;
				if (!section.headers().isEmpty()) {
					if (document != null) {
						drawRow(document, page, layout, bold, bold, y, TABLE_HEADER_HEIGHT, section.widths(),
								wrapCells(section.headers(), section.widths(), layout.width(), bold, formKey,
										section.title()), 0, -1, false);
					}
					y -= TABLE_HEADER_HEIGHT;
				}
				while (rowIndex < section.rows().size()) {
					List<String> row = section.rows().get(rowIndex);
					List<List<String>> lines = wrapCells(row, section.widths(), layout.width(), regular, formKey,
							section.title());
					int rowLineCount = Math.max(1, lines.stream().mapToInt(List::size).max().orElse(1));
					int capacity = (int) Math.floor((y - bottom - CELL_PADDING * 2f) / LINE_HEIGHT);
					if (capacity < 1) {
						break;
					}
					int count = Math.min(capacity, rowLineCount - lineOffset);
					float height = count * LINE_HEIGHT + CELL_PADDING * 2f;
					if (document != null) {
						drawRow(document, page, layout, regular, bold, y, height, section.widths(), lines, lineOffset,
								section.emphasisColumn(), section.emphasizedRows().contains(rowIndex));
					}
					y -= height;
					lineOffset += count;
					if (lineOffset < rowLineCount) {
						break;
					}
					rowIndex++;
					lineOffset = 0;
					if (rowIndex < section.rows().size() && y - bottom < LINE_HEIGHT + CELL_PADDING * 2f) {
						break;
					}
				}
				if (rowIndex < section.rows().size()) {
					break;
				}
				sectionIndex++;
				rowIndex = 0;
				lineOffset = 0;
				if (sectionIndex < sections.size()
						&& y - bottom < SECTION_HEIGHT + LINE_HEIGHT + CELL_PADDING * 2f) {
					break;
				}
			}
		}
		return pageCount;
	}

	/**
	 * Draws the shared ICS header, incident context, frame, and footer band.
	 *
	 * @param renderer shared form renderer.
	 * @param document output PDF.
	 * @param page current page.
	 * @param data incident data.
	 * @param layout printable form bounds.
	 * @param regular regular font.
	 * @param bold bold font.
	 * @param formKey form identifier.
	 * @param title human-readable form title.
	 * @param iapPage IAP page text.
	 * @param preparedAt preparation timestamp.
	 * @param metadata preparer and reviewer footer data.
	 * @param pageNumber one-based page number.
	 * @param totalPages total page count.
	 * @throws IOException when drawing fails.
	 */
	private static void drawPage(AbstractPdfRenderer renderer, PDDocument document, PDPage page, AppData data,
			AbstractPdfRenderer.FormLayout layout, PDType1Font regular, PDType1Font bold, String formKey, String title,
			String iapPage, LocalDateTime preparedAt, FooterMetadata metadata, int pageNumber, int totalPages)
			throws IOException {
		try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
			renderer.drawFormHeader(stream, bold, layout, formKey, title, pageNumber);
			renderer.drawFormFrame(stream, layout);
			drawContext(renderer, stream, regular, bold, layout, data);
			if (pageNumber == totalPages) {
				drawFooterMetadata(stream, bold, regular, layout, formKey, metadata);
			}
			renderer.drawFormFooterBand(stream, bold, regular, FONT_SIZE, CELL_PADDING, layout.x(), layout.y(),
					layout.width(), FOOTER_HEIGHT, 0.68f, renderer.formPageLabel(formKey, pageNumber, totalPages),
					renderer.addPageOffset(iapPage, pageNumber - 1), renderer.formatPdfDateTime(data, preparedAt));
		}
	}

	/**
	 * Draws preparer and reviewer blocks immediately above the shared form footer.
	 *
	 * @param stream page content stream.
	 * @param bold bold font.
	 * @param regular regular font.
	 * @param layout printable form bounds.
	 * @param formKey form identifier for validation diagnostics.
	 * @param metadata preparer and reviewer values.
	 * @throws IOException when drawing fails.
	 */
	private static void drawFooterMetadata(PDPageContentStream stream, PDType1Font bold, PDType1Font regular,
			AbstractPdfRenderer.FormLayout layout, String formKey, FooterMetadata metadata) throws IOException {
		String preparer = safe(metadata.preparedByName());
		String reviewer = safe(metadata.reviewedByName());
		PdfText.validate(preparer, formKey, "7. Prepared By");
		PdfText.validate(reviewer, formKey, "8. Approved By");
		float x = layout.x();
		float width = layout.width();
		float bottom = layout.y() + FOOTER_HEIGHT;
		float middle = bottom + METADATA_HEIGHT / 2f;
		float top = bottom + METADATA_HEIGHT;
		stream.moveTo(x, bottom);
		stream.lineTo(x, top);
		stream.lineTo(x + width, top);
		stream.lineTo(x + width, bottom);
		stream.moveTo(x, middle);
		stream.lineTo(x + width, middle);
		stream.stroke();
		write(stream, bold, FONT_SIZE, x + CELL_PADDING, top - 10f,
				"7. Prepared by (" + safe(metadata.preparedByPositionTitle()) + ")");
		writeSignatureLine(stream, regular, x, middle + 5f, width, preparer);
		write(stream, bold, FONT_SIZE, x + CELL_PADDING, middle - 10f,
				"8. Approved by (" + safe(metadata.reviewedByPositionTitle()) + ")");
		writeSignatureLine(stream, regular, x, bottom + 5f, width, reviewer);
	}

	/**
	 * Resolves a person's displayed ICS position, falling back to the expected role when unassigned.
	 *
	 * @param data incident document.
	 * @param name person whose position is displayed.
	 * @param defaultTitle expected form role.
	 * @return matching position title or the expected form role.
	 */
	static String positionTitle(AppData data, String name, String defaultTitle) {
		if (name == null || name.isBlank()) {
			return defaultTitle;
		}
		OrganizationalChart chart = data.getOrganizationalChart();
		for (String commander : chart.getIncidentCommanders()) {
			if (samePerson(name, commander)) {
				return "Incident Commander";
			}
		}
		for (OrgChartEntry commander : chart.getIncidentCommanderEntries()) {
			if (commander != null && samePerson(name, commander.getName())) {
				return "Incident Commander";
			}
		}
		String[] assignedNames = {chart.getSafetyOfficerName(), chart.getPublicInformationOfficerName(),
				chart.getLiaisonOfficerName(), chart.getOperationsSectionChiefName(),
				chart.getPlanningSectionChiefName(), chart.getLogisticsSectionChiefName(),
				chart.getFinanceAdminSectionChiefName(), chart.getDocumentationUnitLeaderName(),
				chart.getResourcesUnitLeaderName(), chart.getSituationUnitLeaderName(),
				chart.getDemobilizationUnitLeaderName(), chart.getStagingAreaManagerName(),
				chart.getCommunicationsUnitLeaderName(), chart.getCommunicationsTechnicianName(),
				chart.getSupplyUnitLeaderName(), chart.getFacilitiesUnitLeaderName(),
				chart.getGroundSupportUnitLeaderName(), chart.getFoodUnitLeaderName(),
				chart.getMedicalUnitLeaderName(), chart.getTimeUnitLeaderName(),
				chart.getProcurementUnitLeaderName(), chart.getCompClaimsUnitLeaderName(),
				chart.getCostUnitLeaderName()};
		String[] positionTitles = {"Safety Officer", "Public Information Officer", "Liaison Officer",
				"Operations Section Chief", "Planning Section Chief", "Logistics Section Chief",
				"Finance/Administration Section Chief", "Documentation Unit Leader", "Resources Unit Leader",
				"Situation Unit Leader", "Demobilization Unit Leader", "Staging Area Manager",
				"Communications Unit Leader", "Communications Technician", "Supply Unit Leader",
				"Facilities Unit Leader", "Ground Support Unit Leader", "Food Unit Leader",
				"Medical Unit Leader", "Time Unit Leader", "Procurement Unit Leader",
				"Compensation/Claims Unit Leader", "Cost Unit Leader"};
		for (int i = 0; i < assignedNames.length; i++) {
			if (samePerson(name, assignedNames[i])) {
				return positionTitles[i];
			}
		}
		for (OrgChartEntry entry : chart.getAdditionalPositions()) {
			if (entry != null && samePerson(name, entry.getName()) && entry.getTitle() != null
					&& !entry.getTitle().isBlank()) {
				return entry.getTitle();
			}
		}
		return defaultTitle;
	}

	/**
	 * Compares names without regard to surrounding whitespace or letter case.
	 *
	 * @param first first name.
	 * @param second second name.
	 * @return true when both names identify the same displayed person.
	 */
	private static boolean samePerson(String first, String second) {
		return first != null && second != null && first.trim().equalsIgnoreCase(second.trim());
	}

	/**
	 * Draws a name field and a printed signature line within one metadata row.
	 *
	 * @param stream page content stream.
	 * @param font regular font.
	 * @param x row left edge.
	 * @param y text baseline.
	 * @param width row width.
	 * @param name entered person name.
	 * @throws IOException when drawing fails.
	 */
	private static void writeSignatureLine(PDPageContentStream stream, PDType1Font font, float x, float y, float width,
			String name) throws IOException {
		float signatureX = x + width * 0.56f;
		write(stream, font, FONT_SIZE, x + CELL_PADDING, y, "Name: " + name);
		write(stream, font, FONT_SIZE, signatureX, y, "Signature: ______________________________");
	}

	/**
	 * Draws incident name beside separate date and time operational-period rows.
	 * All four period values use the configured export timezone.
	 *
	 * @param stream page content stream.
	 * @param regular regular font.
	 * @param bold bold font.
	 * @param layout printable form bounds.
	 * @param data incident data.
	 * @param renderer shared form renderer.
	 * @throws IOException when drawing fails.
	 */
	private static void drawContext(AbstractPdfRenderer renderer, PDPageContentStream stream, PDType1Font regular,
			PDType1Font bold, AbstractPdfRenderer.FormLayout layout, AppData data) throws IOException {
		IncidentContext incident = data.getIncidentContext();
		float top = layout.top();
		float y = top - CONTEXT_HEIGHT;
		float nameWidth = layout.width() * 0.38f;
		drawContextCell(stream, regular, bold, layout.x(), y, nameWidth, "1. INCIDENT NAME",
				incident.getIncidentName());
		float periodX = layout.x() + nameWidth;
		float periodWidth = layout.width() - nameWidth;
		stream.addRect(periodX, y, periodWidth, CONTEXT_HEIGHT);
		stream.stroke();
		write(stream, bold, FONT_SIZE, periodX + CELL_PADDING, top - 11f, "2. OPERATIONAL PERIOD");
		write(stream, regular, FONT_SIZE, periodX + CELL_PADDING, top - 25f,
				"Date From: " + renderer.formatPdfDate(data, incident.getOperationalPeriodStart()));
		write(stream, regular, FONT_SIZE, periodX + periodWidth / 2f + CELL_PADDING, top - 25f,
				"Date To: " + renderer.formatPdfDate(data, incident.getOperationalPeriodEnd()));
		write(stream, regular, FONT_SIZE, periodX + CELL_PADDING, top - 39f,
				"Time From: " + renderer.formatPdfTime(data, incident.getOperationalPeriodStart()));
		write(stream, regular, FONT_SIZE, periodX + periodWidth / 2f + CELL_PADDING, top - 39f,
				"Time To: " + renderer.formatPdfTime(data, incident.getOperationalPeriodEnd()));
	}

	/**
	 * Draws one incident-context cell with a compact label and value.
	 *
	 * @param stream page content stream.
	 * @param regular regular font.
	 * @param bold bold font.
	 * @param x left edge.
	 * @param y bottom edge.
	 * @param width cell width.
	 * @param label field label.
	 * @param value field value.
	 * @throws IOException when drawing fails.
	 */
	private static void drawContextCell(PDPageContentStream stream, PDType1Font regular, PDType1Font bold, float x,
			float y, float width, String label, String value) throws IOException {
		stream.addRect(x, y, width, CONTEXT_HEIGHT);
		stream.stroke();
		write(stream, bold, FONT_SIZE, x + CELL_PADDING, y + CONTEXT_HEIGHT - 11f, label);
		List<String> lines = wrap(value, regular, width - CELL_PADDING * 2f);
		for (int index = 0; index < Math.min(2, lines.size()); index++) {
			write(stream, regular, FONT_SIZE, x + CELL_PADDING, y + CONTEXT_HEIGHT - 25f - index * LINE_HEIGHT,
					lines.get(index));
		}
	}

	/**
	 * Draws a numbered section heading across the printable form width.
	 *
	 * @param document output PDF.
	 * @param page target page.
	 * @param layout printable form bounds.
	 * @param bold bold font.
	 * @param top heading top coordinate.
	 * @param title section title.
	 * @throws IOException when drawing fails.
	 */
	private static void drawSectionHeading(PDDocument document, PDPage page, AbstractPdfRenderer.FormLayout layout,
			PDType1Font bold, float top, String title) throws IOException {
		try (PDPageContentStream stream = append(document, page)) {
			float bottom = top - SECTION_HEIGHT;
			stream.addRect(layout.x(), bottom, layout.width(), SECTION_HEIGHT);
			stream.stroke();
			write(stream, bold, FONT_SIZE, layout.x() + CELL_PADDING, bottom + 4f, title);
		}
	}

	/**
	 * Draws a compact table or text row with ruled columns and wrapped cell lines.
	 *
	 * @param document output PDF.
	 * @param page target page.
	 * @param layout printable form bounds.
	 * @param font row font.
	 * @param top row top coordinate.
	 * @param height row height.
	 * @param widths relative column widths.
	 * @param emphasisFont bold font used for emphasized cells.
	 * @param cells wrapped cell lines.
	 * @param lineOffset first line displayed for this row segment.
	 * @param emphasisColumn zero-based column to emphasize, or -1.
	 * @param emphasize whether this row should use the emphasis style.
	 * @throws IOException when drawing fails.
	 */
	private static void drawRow(PDDocument document, PDPage page, AbstractPdfRenderer.FormLayout layout,
			PDType1Font font, PDType1Font emphasisFont, float top, float height, float[] widths,
			List<List<String>> cells, int lineOffset, int emphasisColumn, boolean emphasize) throws IOException {
		float bottom = top - height;
		try (PDPageContentStream stream = append(document, page)) {
			stream.addRect(layout.x(), bottom, layout.width(), height);
			stream.stroke();
			float x = layout.x();
			for (int column = 0; column < widths.length - 1; column++) {
				x += layout.width() * widths[column];
				stream.moveTo(x, bottom);
				stream.lineTo(x, top);
				stream.stroke();
			}
			x = layout.x();
			for (int column = 0; column < widths.length; column++) {
				List<String> lines = cells.get(column);
				boolean highlighted = emphasize && column == emphasisColumn;
				PDType1Font activeFont = highlighted ? emphasisFont : font;
				stream.setNonStrokingColor(highlighted ? Color.RED : Color.BLACK);
				int end = Math.min(lines.size(), lineOffset + (int) Math.floor((height - CELL_PADDING * 2f)
						/ LINE_HEIGHT));
				float textY = top - CELL_PADDING - FONT_SIZE;
				for (int line = lineOffset; line < end; line++) {
					write(stream, activeFont, FONT_SIZE, x + CELL_PADDING, textY, lines.get(line));
					textY -= LINE_HEIGHT;
				}
				x += layout.width() * widths[column];
			}
		}
	}

	/**
	 * Opens an append stream for a form section.
	 *
	 * @param document output PDF.
	 * @param page target page.
	 * @return append-mode content stream.
	 * @throws IOException when stream creation fails.
	 */
	private static PDPageContentStream append(PDDocument document, PDPage page) throws IOException {
		return new PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true);
	}

	/**
	 * Wraps every cell against the width assigned by the section's column ratios.
	 *
	 * @param cells source cell text.
	 * @param widths relative column widths.
	 * @param totalWidth printable table width.
	 * @param font measurement font.
	 * @param formKey form identifier for validation diagnostics.
	 * @param section section title for validation diagnostics.
	 * @return wrapped lines for each cell.
	 */
	private static List<List<String>> wrapCells(List<String> cells, float[] widths, float totalWidth,
			PDType1Font font, String formKey, String section) {
		List<List<String>> wrapped = new ArrayList<>();
		for (int column = 0; column < widths.length; column++) {
			String value = column < cells.size() ? cells.get(column) : "";
			PdfText.validate(value, formKey, section);
			wrapped.add(wrap(value, font, Math.max(20f, totalWidth * widths[column] - CELL_PADDING * 2f)));
		}
		return wrapped;
	}

	/**
	 * Wraps normalized paragraphs to a measured PDF font width.
	 *
	 * @param value source cell.
	 * @param font measurement font.
	 * @param width available cell width.
	 * @return wrapped display lines.
	 */
	private static List<String> wrap(String value, PDType1Font font, float width) {
		List<String> result = new ArrayList<>();
		for (String paragraph : PdfText.normalize(value).split("\\n", -1)) {
			StringBuilder line = new StringBuilder();
			for (String word : paragraph.split("\\s+")) {
				if (word.isEmpty()) {
					continue;
				}
				if (!line.isEmpty() && measure(font, line + " " + word) > width) {
					result.add(line.toString());
					line.setLength(0);
				}
				for (int index = 0; index < word.length(); index++) {
					char character = word.charAt(index);
					if (!line.isEmpty() && measure(font, line.toString() + character) > width) {
						result.add(line.toString());
						line.setLength(0);
					}
					line.append(character);
				}
				line.append(' ');
			}
			result.add(line.toString().stripTrailing());
		}
		return result.isEmpty() ? List.of("") : result;
	}

	/**
	 * Measures printable text at the form's body font size.
	 *
	 * @param font measurement font.
	 * @param value source text.
	 * @return width in PDF points.
	 */
	private static float measure(PDType1Font font, String value) {
		try {
			return font.getStringWidth(value) / 1000f * FONT_SIZE;
		} catch (IOException exception) {
			return value.length() * FONT_SIZE;
		}
	}

	/**
	 * Writes a validated single line of text.
	 *
	 * @param stream page content stream.
	 * @param font active font.
	 * @param size font size.
	 * @param x horizontal position.
	 * @param y baseline.
	 * @param value display text.
	 * @throws IOException when drawing fails.
	 */
	private static void write(PDPageContentStream stream, PDType1Font font, float size, float x, float y, String value)
			throws IOException {
		try (PdfText.TextBlock textBlock = PdfText.begin(stream)) {
			stream.setFont(font, size);
			stream.newLineAtOffset(x, y);
			stream.showText(PdfText.singleLine(value));
		}
	}

	/**
	 * Replaces nullable form text with an empty string.
	 *
	 * @param value source text.
	 * @return non-null text.
	 */
	private static String safe(String value) {
		return value == null ? "" : value;
	}

	/**
	 * Defines one numbered block and its optional table.
	 *
	 * @param title section number and label.
	 * @param widths relative column widths, summing to one.
	 * @param headers optional table header labels.
	 * @param rows ordered table or paragraph rows.
	 * @param emphasisColumn zero-based column to emphasize, or -1.
	 * @param emphasizedRows zero-based rows using the emphasis style.
	 */
	record Section(String title, float[] widths, List<String> headers, List<List<String>> rows,
			int emphasisColumn, Set<Integer> emphasizedRows) {
		/**
		 * Creates a section without specially styled data cells.
		 *
		 * @param title section number and label.
		 * @param widths relative column widths.
		 * @param headers optional table header labels.
		 * @param rows ordered table or paragraph rows.
		 */
		Section(String title, float[] widths, List<String> headers, List<List<String>> rows) {
			this(title, widths, headers, rows, -1, Set.of());
		}

		/**
		 * Creates a form section with a defensive copy of its layout lists.
		 *
		 * @param title section number and label.
		 * @param widths relative column widths.
		 * @param headers optional table header labels.
		 * @param rows ordered table or paragraph rows.
		 * @param emphasisColumn zero-based column to emphasize, or -1.
		 * @param emphasizedRows zero-based rows using the emphasis style.
		 */
		Section {
			widths = widths.clone();
			headers = List.copyOf(headers);
			rows = rows.stream().map(List::copyOf).toList();
			emphasizedRows = Set.copyOf(emphasizedRows);
		}
	}

	/**
	 * Footer values for the preparer and reviewer blocks.
	 *
	 * @param preparedByName preparer name.
	 * @param preparedByPositionTitle preparer position.
	 * @param reviewedByName reviewer name.
	 * @param reviewedByPositionTitle reviewer position.
	 */
	record FooterMetadata(String preparedByName, String preparedByPositionTitle, String reviewedByName,
			String reviewedByPositionTitle) {
		/**
		 * Creates footer metadata with non-null values.
		 *
		 * @param preparedByName preparer name.
		 * @param preparedByPositionTitle preparer position.
		 * @param reviewedByName reviewer name.
		 * @param reviewedByPositionTitle reviewer position.
		 */
		FooterMetadata {
			preparedByName = preparedByName == null ? "" : preparedByName;
			preparedByPositionTitle = preparedByPositionTitle == null ? "" : preparedByPositionTitle;
			reviewedByName = reviewedByName == null ? "" : reviewedByName;
			reviewedByPositionTitle = reviewedByPositionTitle == null ? "" : reviewedByPositionTitle;
		}
	}

}
