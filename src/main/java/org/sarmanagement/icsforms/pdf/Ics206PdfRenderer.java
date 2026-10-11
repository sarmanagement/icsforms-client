/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.pdf;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.IncidentContext;
import org.sarmanagement.icsforms.model.MedicalPlan;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders the standard human ICS 206 Medical Plan in the shared form layout.
 */
public class Ics206PdfRenderer extends AbstractPdfRenderer implements PdfFormRenderer {
	private static final String FORM_KEY = "ICS 206";
	private static final float FONT_SIZE = 8f;
	private static final float LINE_HEIGHT = 10f;
	private static final float SECTION_HEADING_HEIGHT = 18f;
	private static final float CELL_PADDING = 5f;
	private static final float CONTEXT_HEIGHT = 44f;
	private static final float FOOTER_HEIGHT = 34f;
	private static final float SECTION_SPACING = 4f;

	/** {@inheritDoc} */
	@Override
	public String getFormKey() {
		return FORM_KEY;
	}

	/**
	 * Counts pages using the same wrapping and available geometry used to render.
	 *
	 * @param data incident data.
	 * @return rendered page count, at least one.
	 */
	public static int pageCount(AppData data) {
		AppData safeData = data == null ? new AppData() : data;
		Ics206PdfRenderer renderer = new Ics206PdfRenderer();
		FormLayout layout = renderer.formLayout(renderer.newPage(safeData), safeData);
		try {
			return renderer.paginate(safeData, layout, null);
		} catch (IOException exception) {
			throw new IllegalStateException("Cannot measure ICS 206 layout", exception);
		}
	}

	/** {@inheritDoc} */
	@Override
	public void render(AppData data, Path outputFile) throws IOException {
		if (outputFile.getParent() != null) {
			Files.createDirectories(outputFile.getParent());
		}
		AppData safeData = data == null ? new AppData() : data;
		try (PDDocument document = new PDDocument()) {
			paginate(safeData, formLayout(newPage(safeData), safeData), document);
			document.save(outputFile.toFile());
		}
	}

	/**
	 * Lays out the sections across pages, retaining every wrapped line.
	 *
	 * @param data incident document.
	 * @param layout printable area.
	 * @param document output document, or null for counting.
	 * @return number of pages.
	 */
	private int paginate(AppData data, FormLayout layout, PDDocument document) throws IOException {
		List<Section> sections = buildSections(data);
		PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
		PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
		List<List<String>> wrappedSections = new ArrayList<>();
		for (Section section : sections) {
			List<String> lines = new ArrayList<>();
			for (String row : section.lines()) {
				lines.addAll(wrapMeasured(row, regular, FONT_SIZE, contentWidth(layout), false));
			}
			if (lines.isEmpty()) {
				lines.add("");
			}
			wrappedSections.add(lines);
		}

		int sectionIndex = 0;
		int lineIndex = 0;
		int pages = 0;
		while (sectionIndex < sections.size()) {
			pages++;
			float y = layout.top() - CONTEXT_HEIGHT - SECTION_SPACING;
			float bottom = layout.y() + FOOTER_HEIGHT + SECTION_SPACING;
			if (document != null) {
				drawPage(document, data, layout, regular, bold, pages);
			}
			while (sectionIndex < sections.size()) {
				Section section = sections.get(sectionIndex);
				List<String> lines = wrappedSections.get(sectionIndex);
				int capacity = (int) Math.floor((y - bottom - SECTION_HEADING_HEIGHT - CELL_PADDING * 2)
						/ LINE_HEIGHT);
				if (capacity < 1) {
					break;
				}
				int count = Math.min(capacity, lines.size() - lineIndex);
				float height = SECTION_HEADING_HEIGHT + CELL_PADDING * 2 + count * LINE_HEIGHT;
				if (document != null) {
					drawSection(document, document.getPage(document.getNumberOfPages() - 1), layout, regular, bold, y,
							height, section.title() + (lineIndex == 0 ? "" : " (CONTINUED)"),
							lines.subList(lineIndex, lineIndex + count));
				}
				y -= height + SECTION_SPACING;
				lineIndex += count;
				if (lineIndex < lines.size()) {
					break;
				}
				sectionIndex++;
				lineIndex = 0;
			}
		}
		return pages;
	}

	/**
	 * Builds the numbered ICS 206 blocks from the incident and plan models.
	 *
	 * @param data incident document.
	 * @return form sections in block order.
	 */
	private List<Section> buildSections(AppData data) {
		MedicalPlan plan = data.getMedicalPlan();
		IncidentContext context = data.getIncidentContext();
		List<Section> sections = new ArrayList<>();
		sections.add(new Section("1. INCIDENT NAME", List.of(safe(context.getIncidentName()))));
		sections.add(new Section("2. OPERATIONAL PERIOD", List.of("From: "
				+ formatPdfDateTime(data, context.getOperationalPeriodStart()) + " | To: "
				+ formatPdfDateTime(data, context.getOperationalPeriodEnd()))));
		sections.add(new Section("3. MEDICAL AID STATIONS", aidStationLines(plan)));
		sections.add(new Section("4. TRANSPORTATION (AIR OR GROUND)", transportLines(plan)));
		sections.add(new Section("5. HOSPITALS", hospitalLines(plan)));
		List<String> procedures = new ArrayList<>();
		if (plan.isSimpleMode()) {
			procedures.add("EMERGENCY INSTRUCTION: " + plan.getCall911Procedure());
			if (!plan.getDesignatedMedicalUnitRole().isBlank()) {
				procedures.add("Designated medical unit role: " + plan.getDesignatedMedicalUnitRole());
			}
			if (!plan.getDesignatedMedicalUnitName().isBlank()) {
				procedures.add("Designated person: " + plan.getDesignatedMedicalUnitName());
			}
			if (!plan.getDesignatedMedicalUnitContact().isBlank()) {
				procedures.add("Contact: " + plan.getDesignatedMedicalUnitContact());
			}
		}
		addTextLines(procedures, plan.getEmergencyProcedures());
		if (plan.isAviationAssetsUsed()) {
			procedures.add("[X] Aviation assets are used. Coordinate with Air Operations.");
			addTextLines(procedures, plan.getAirOperationsReminder());
		} else {
			procedures.add("[ ] Aviation assets are used.");
		}
		sections.add(new Section("6. SPECIAL MEDICAL EMERGENCY PROCEDURES", emptyIfNoData(procedures)));
		sections.add(new Section("7. PREPARED BY (MEDICAL UNIT LEADER)", List.of(
				"Name: " + plan.getPreparedByMedicalUnitLeader(),
				"Signature: " + plan.getPreparerSignature(),
				"Date/Time: " + formatPdfDateTime(data, plan.getPreparedAt()))));
		sections.add(new Section("8. APPROVED BY (SAFETY OFFICER)", List.of(
				"Name: " + plan.getReviewedBySafetyOfficer(),
				"Signature: " + plan.getSafetyOfficerSignature(),
				"Review status: " + plan.getReviewStatus())));
		return sections;
	}

	/**
	 * Returns aid-station rows with an explicit unknown marker.
	 *
	 * @param plan standard plan.
	 * @return display rows.
	 */
	private List<String> aidStationLines(MedicalPlan plan) {
		if (plan.isSimpleMode() || plan.getAidStations().isEmpty()) {
			return List.of(plan.isSimpleMode() ? "Detailed aid station listings not entered."
					: "No medical aid stations entered.");
		}
		List<String> lines = new ArrayList<>();
		for (MedicalPlan.AidStation station : plan.getAidStations()) {
			lines.add("Name: " + station.getName() + " | Location: " + station.getLocation());
			lines.add("Contact: " + station.getContactNumbers() + " | Frequency: " + station.getFrequency()
					+ " | Paramedics on site: " + yesNoUnknown(station.getParamedicsOnSite()));
		}
		return lines;
	}

	/**
	 * Returns all human transport rows without assuming a service is available.
	 *
	 * @param plan standard plan.
	 * @return display rows.
	 */
	private List<String> transportLines(MedicalPlan plan) {
		if (plan.isSimpleMode() || plan.getTransportServices().isEmpty()) {
			return List.of(plan.isSimpleMode() ? "Detailed transport listings not entered."
					: "No transport services entered.");
		}
		List<String> lines = new ArrayList<>();
		for (MedicalPlan.TransportService service : plan.getTransportServices()) {
			lines.add("Service: " + service.getService() + " | Location: " + service.getLocation());
			lines.add("Contact: " + service.getContactNumbers() + " | Frequency: " + service.getFrequency()
					+ " | Type: " + service.getAirOrGround() + " | Level: " + service.getLevelOfService());
		}
		return lines;
	}

	/**
	 * Returns all hospital rows, preserving nullable capability status.
	 *
	 * @param plan standard plan.
	 * @return display rows.
	 */
	private List<String> hospitalLines(MedicalPlan plan) {
		if (plan.isSimpleMode() || plan.getHospitals().isEmpty()) {
			return List.of(plan.isSimpleMode() ? "Detailed hospital listings not entered."
					: "No hospitals entered.");
		}
		List<String> lines = new ArrayList<>();
		for (MedicalPlan.Hospital hospital : plan.getHospitals()) {
			lines.add("Hospital: " + hospital.getName() + " | Address: " + hospital.getAddress());
			lines.add("Contact: " + hospital.getContactNumbers() + " | Frequency: " + hospital.getFrequency());
			String helipadCoordinates = hospital.getHelipadCoordinates().isBlank()
					? coordinates(hospital.getLatitude(), hospital.getLongitude())
					: hospital.getHelipadCoordinates();
			lines.add("Helipad coordinates: " + helipadCoordinates + " | Coordinate format: "
					+ hospital.getCoordinateFormat());
			lines.add("Travel time - Air: " + measure(hospital.getAirTravelTime(), hospital.getAirTravelProvenance())
					+ " | Ground: "
					+ measure(hospital.getGroundTravelTime(), hospital.getGroundTravelProvenance()));
			lines.add("Trauma center: " + yesNoUnknown(hospital.getTraumaCenter()) + " | Level: "
					+ hospital.getTraumaLevel() + " | Burn center: " + yesNoUnknown(hospital.getBurnCenter())
					+ " | Helipad: " + yesNoUnknown(hospital.getHelipad()));
		}
		return lines;
	}

	/**
	 * Draws shared page structure, context header, and footer.
	 *
	 * @param document output PDF.
	 * @param data incident data.
	 * @param layout printable area.
	 * @param regular regular font.
	 * @param bold bold font.
	 * @param pageNumber one-based page number.
	 * @throws IOException when drawing fails.
	 */
	private void drawPage(PDDocument document, AppData data, FormLayout layout, PDType1Font regular, PDType1Font bold,
			int pageNumber) throws IOException {
		PDPage page = newPage(data);
		document.addPage(page);
		try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
			drawFormHeader(stream, bold, layout, FORM_KEY,
					"MEDICAL PLAN" + (pageNumber == 1 ? "" : " (CONTINUED)"));
			drawFormFrame(stream, layout);
			drawContext(stream, regular, bold, layout, data);
			MedicalPlan plan = data.getMedicalPlan();
			drawFormFooterBand(stream, bold, regular, FONT_SIZE, CELL_PADDING, layout.x(), layout.y(),
					layout.width(), FOOTER_HEIGHT, 0.68f, FORM_KEY, addPageOffset(plan.getIapPage(), pageNumber - 1),
					formatPdfDateTime(data, plan.getPreparedAt()));
		}
	}

	/**
	 * Draws incident name and the shared operational-period range at the top.
	 *
	 * @param stream page stream.
	 * @param regular regular font.
	 * @param bold bold font.
	 * @param layout page layout.
	 * @param data incident document.
	 * @throws IOException when drawing fails.
	 */
	private void drawContext(PDPageContentStream stream, PDType1Font regular, PDType1Font bold, FormLayout layout,
			AppData data) throws IOException {
		IncidentContext context = data.getIncidentContext();
		float x = layout.x();
		float y = layout.top() - CONTEXT_HEIGHT;
		float nameHeight = CONTEXT_HEIGHT / 2f;
		drawCell(stream, bold, regular, x, y + nameHeight, layout.width(), nameHeight,
				"1. INCIDENT NAME", safe(context.getIncidentName()));
		String period = "From: " + formatPdfDateTime(data, context.getOperationalPeriodStart()) + "    To: "
				+ formatPdfDateTime(data, context.getOperationalPeriodEnd());
		drawCell(stream, bold, regular, x, y, layout.width(), nameHeight, "2. OPERATIONAL PERIOD", period);
	}

	/**
	 * Draws one section rectangle and its complete text lines.
	 *
	 * @param page page receiving the section.
	 * @param data incident document.
	 * @param layout printable area.
	 * @param regular regular font.
	 * @param bold bold font.
	 * @param top section top.
	 * @param height section height.
	 * @param heading section heading.
	 * @param lines visible section lines.
	 * @throws IOException when drawing fails.
	 */
	private void drawSection(PDDocument document, PDPage page, FormLayout layout, PDType1Font regular,
			PDType1Font bold, float top, float height, String heading, List<String> lines) throws IOException {
		float x = layout.x();
		float width = layout.width();
		float bottom = top - height;
		try (PDPageContentStream stream = new PDPageContentStream(document, page, AppendMode.APPEND, true, true)) {
			stream.addRect(x, bottom, width, height);
			stream.stroke();
			drawText(stream, bold, FONT_SIZE, x + CELL_PADDING, top - 12f, heading);
			float y = top - SECTION_HEADING_HEIGHT - CELL_PADDING - FONT_SIZE;
			for (String line : lines) {
				drawText(stream, regular, FONT_SIZE, x + CELL_PADDING, y, line);
				y -= LINE_HEIGHT;
			}
		}
	}

	/**
	 * Draws a titled form cell.
	 *
	 * @param stream page stream.
	 * @param bold bold font.
	 * @param regular regular font.
	 * @param x left coordinate.
	 * @param y bottom coordinate.
	 * @param width cell width.
	 * @param height cell height.
	 * @param heading field heading.
	 * @param value field value.
	 * @throws IOException when drawing fails.
	 */
	private void drawCell(PDPageContentStream stream, PDType1Font bold, PDType1Font regular, float x, float y,
			float width, float height, String heading, String value) throws IOException {
		stream.addRect(x, y, width, height);
		stream.stroke();
		drawText(stream, bold, FONT_SIZE, x + CELL_PADDING, y + height - 12f, heading);
		drawText(stream, regular, FONT_SIZE, x + 130f, y + height - 12f,
				fitToWidth(value, regular, FONT_SIZE, width - 140f));
	}

	/**
	 * Writes a single PDF text line.
	 *
	 * @param stream page stream.
	 * @param font font.
	 * @param size font size.
	 * @param x horizontal coordinate.
	 * @param y baseline.
	 * @param text text value.
	 * @throws IOException when drawing fails.
	 */
	private void drawText(PDPageContentStream stream, PDType1Font font, float size, float x, float y, String text)
			throws IOException {
		stream.beginText();
		stream.setFont(font, size);
		stream.newLineAtOffset(x, y);
		stream.showText(safe(text));
		stream.endText();
	}

	/**
	 * Wraps text by the active font's measured width.
	 *
	 * @param text source text.
	 * @param font font used for measurement.
	 * @param size font size.
	 * @param width available width.
	 * @param allowOverflow whether one long word may overflow.
	 * @return wrapped lines without discarding content.
	 */
	private List<String> wrapMeasured(String text, PDType1Font font, float size, float width, boolean allowOverflow) {
		List<String> result = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : safe(text).split("\\s+")) {
			if (word.isEmpty()) {
				continue;
			}
			String candidate = line.isEmpty() ? word : line + " " + word;
			if (!line.isEmpty() && textWidth(font, size, candidate) > width) {
				result.add(line.toString());
				line.setLength(0);
			}
			if (!line.isEmpty()) {
				line.append(' ');
			}
			if (allowOverflow || textWidth(font, size, word) <= width) {
				line.append(word);
			} else {
				appendLongWord(result, line, word, font, size, width);
			}
		}
		if (!line.isEmpty()) {
			result.add(line.toString());
		}
		return result.isEmpty() ? List.of("") : result;
	}

	/**
	 * Splits a word wider than the printable column into measured fragments.
	 *
	 * @param lines target lines.
	 * @param current current line buffer.
	 * @param word long word.
	 * @param font measurement font.
	 * @param size font size.
	 * @param width available width.
	 */
	private void appendLongWord(List<String> lines, StringBuilder current, String word, PDType1Font font, float size,
			float width) {
		StringBuilder fragment = new StringBuilder();
		for (int index = 0; index < word.length(); index++) {
			char character = word.charAt(index);
			if (!fragment.isEmpty() && textWidth(font, size, fragment.toString() + character) > width) {
				if (!current.isEmpty()) {
					lines.add(current.toString());
					current.setLength(0);
				}
				lines.add(fragment.toString());
				fragment.setLength(0);
			}
			fragment.append(character);
		}
		current.append(fragment);
	}

	/**
	 * Returns the available text width inside a section.
	 *
	 * @param layout printable area.
	 * @return content width.
	 */
	private float contentWidth(FormLayout layout) {
		return layout.width() - 2 * CELL_PADDING;
	}

	/**
	 * Measures a line in points.
	 *
	 * @param font PDF font.
	 * @param size font size.
	 * @param text text value.
	 * @return line width in points.
	 */
	private float textWidth(PDType1Font font, float size, String text) {
		try {
			return font.getStringWidth(text) / 1000f * size;
		} catch (IOException exception) {
			return text.length() * size;
		}
	}

	/**
	 * Fits a context value without modifying the stored source string.
	 *
	 * @param value text value.
	 * @param font PDF font.
	 * @param size font size.
	 * @param width available width.
	 * @return fitted display text.
	 */
	private String fitToWidth(String value, PDType1Font font, float size, float width) {
		String normalized = safe(value).replaceAll("\\s+", " ");
		if (textWidth(font, size, normalized) <= width) {
			return normalized;
		}
		StringBuilder fitted = new StringBuilder();
		for (int index = 0; index < normalized.length(); index++) {
			String candidate = fitted.toString() + normalized.charAt(index);
			if (textWidth(font, size, candidate) > width) {
				break;
			}
			fitted.append(normalized.charAt(index));
		}
		return fitted.toString();
	}

	/**
	 * Formats an explicitly provided yes/no/unknown capability.
	 *
	 * @param value capability value.
	 * @return readable state.
	 */
	private String yesNoUnknown(Boolean value) {
		return value == null ? "[ ] YES  [ ] NO  [X] UNKNOWN"
				: value ? "[X] YES  [ ] NO  [ ] UNKNOWN" : "[ ] YES  [X] NO  [ ] UNKNOWN";
	}

	/**
	 * Formats a coordinate pair only when both components are present.
	 *
	 * @param latitude latitude in decimal degrees.
	 * @param longitude longitude in decimal degrees.
	 * @return coordinate pair or blank text.
	 */
	private String coordinates(Double latitude, Double longitude) {
		return latitude == null || longitude == null ? "" : latitude + ", " + longitude;
	}

	/**
	 * Formats a travel time alongside any manual or calculated provenance.
	 *
	 * @param value travel time.
	 * @param provenance source of the value.
	 * @return labeled travel time.
	 */
	private String measure(String value, String provenance) {
		return safe(value) + (provenance.isBlank() ? "" : " (" + provenance + ")");
	}

	/**
	 * Adds nonblank procedure paragraphs as independently wrappable lines.
	 *
	 * @param lines target lines.
	 * @param text source procedure text.
	 */
	private void addTextLines(List<String> lines, String text) {
		if (text != null && !text.isBlank()) {
			for (String line : text.split("\\R")) {
				lines.add(line);
			}
		}
	}

	/**
	 * Returns a printable empty-state statement for a section with no instructions.
	 *
	 * @param lines section lines.
	 * @return original lines or a neutral empty state.
	 */
	private List<String> emptyIfNoData(List<String> lines) {
		return lines.isEmpty() ? List.of("No special emergency procedures entered.") : lines;
	}

	/**
	 * Normalizes a nullable display value.
	 *
	 * @param value source value.
	 * @return non-null text.
	 */
	private String safe(String value) {
		return value == null ? "" : value;
	}

	/**
	 * One numbered ICS form section.
	 *
	 * @param title section title.
	 * @param lines content paragraphs.
	 */
	private record Section(String title, List<String> lines) {
	}
}
