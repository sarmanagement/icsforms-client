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
import org.sarmanagement.icsforms.model.CanineMedicalPlan;
import org.sarmanagement.icsforms.model.IncidentContext;
import org.sarmanagement.icsforms.model.StagingLocation;
import org.sarmanagement.icsforms.model.VetFacility;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders the adapted canine medical plan using the shared ICS form frame.
 */
public class Ics206CaninePdfRenderer extends AbstractPdfRenderer implements PdfFormRenderer {
	private static final String FORM_KEY = "ICS 206CANINE";
	private static final float FONT_SIZE = 8f;
	private static final float LINE_HEIGHT = 10f;
	private static final float SECTION_HEADER_HEIGHT = 17f;
	private static final float SECTION_GAP = 4f;
	private static final float FOOTER_HEIGHT = 34f;
	private static final float CONTEXT_HEIGHT = 56f;
	private static final float PADDING = 5f;

	/** {@inheritDoc} */
	@Override
	public String getFormKey() {
		return FORM_KEY;
	}

	/**
	 * Counts pages using the same section wrapping and geometry as rendering.
	 *
	 * @param data incident data.
	 * @return page count, at least one.
	 */
	public static int pageCount(AppData data) {
		try {
			return new Ics206CaninePdfRenderer().paginate(data == null ? new AppData() : data, null);
		} catch (IOException exception) {
			throw new IllegalStateException("Cannot measure ICS 206CANINE layout", exception);
		}
	}

	/** {@inheritDoc} */
	@Override
	public void render(AppData data, Path outputFile) throws IOException {
		if (outputFile.getParent() != null) {
			Files.createDirectories(outputFile.getParent());
		}
		try (PDDocument document = new PDDocument()) {
			paginate(data == null ? new AppData() : data, document);
			document.save(outputFile.toFile());
		}
	}

	/**
	 * Paginates section content without dropping overflow lines.
	 *
	 * @param data incident data.
	 * @param document destination document, or null for page counting.
	 * @return number of rendered pages.
	 * @throws IOException when PDF drawing fails.
	 */
	private int paginate(AppData data, PDDocument document) throws IOException {
		PDPage sample = newPage(data);
		FormLayout layout = formLayout(sample, data);
		PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
		PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
		List<Section> sections = sections(data);
		List<List<String>> wrapped = new ArrayList<>();
		for (Section section : sections) {
			List<String> lines = new ArrayList<>();
			for (String line : section.lines()) {
				lines.addAll(wrap(line, regular, contentWidth(layout)));
			}
			wrapped.add(lines.isEmpty() ? List.of("") : lines);
		}
		int sectionIndex = 0;
		int lineIndex = 0;
		int pages = 0;
		while (sectionIndex < sections.size()) {
			pages++;
			PDPage page = null;
			if (document != null) {
				page = newPage(data);
				document.addPage(page);
				drawPage(document, page, data, layout, regular, bold, pages);
			}
			float y = layout.top() - CONTEXT_HEIGHT - SECTION_GAP;
			float bottom = layout.y() + FOOTER_HEIGHT + SECTION_GAP;
			while (sectionIndex < sections.size()) {
				List<String> lines = wrapped.get(sectionIndex);
				int capacity = (int) Math.floor((y - bottom - SECTION_HEADER_HEIGHT - PADDING * 2) / LINE_HEIGHT);
				if (capacity < 1) {
					break;
				}
				int count = Math.min(capacity, lines.size() - lineIndex);
				float height = SECTION_HEADER_HEIGHT + PADDING * 2 + count * LINE_HEIGHT;
				if (document != null) {
					drawSection(document, page, layout, regular, bold, y, height,
							sections.get(sectionIndex).title() + (lineIndex == 0 ? "" : " (CONTINUED)"),
							lines.subList(lineIndex, lineIndex + count));
				}
				y -= height + SECTION_GAP;
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
	 * Builds the numbered adapted form blocks.
	 *
	 * @param data incident document.
	 * @return ordered form sections.
	 */
	private List<Section> sections(AppData data) {
		CanineMedicalPlan plan = data.getCanineMedicalPlan();
		IncidentContext incident = data.getIncidentContext();
		StagingLocation base = data.getStagingLocation();
		List<String> facilities = new ArrayList<>();
		facilities.add("Staging/base: " + text(base.getLabel()) + " | " + text(base.getAddress()));
		if (base.hasValidCoordinates()) {
			facilities.add("Base coordinates: " + base.getLatitude() + ", " + base.getLongitude()
					+ " (" + text(base.getGeocodingProvider()) + "; " + text(base.getGeocodingProvenance()) + ")");
		}
		if (plan.getSelectedFacilities().isEmpty()) {
			facilities.add("No veterinary facilities selected.");
		}
		for (VetFacility facility : plan.getSelectedFacilities()) {
			String distance = facility.hasCurrentDistance(base.getGeneration())
					? formatDistance(facility.getDistanceMeters()) + " (" + text(facility.getDistanceMethod()) + ")"
					: "Not calculated";
			facilities.add("Name: " + text(facility.getName()) + " | Town: " + text(facility.getTown())
					+ " | Distance from base: " + distance);
			facilities.add("Address: " + text(facility.getAddress()) + " | Contacts: "
					+ String.join("; ", facility.getContactNumbers()));
			facilities.add("24 hours: " + availability(facility.getAvailable24Hours()) + " | Capabilities/notes: "
					+ text(facility.getCapabilities()) + " " + text(facility.getAvailabilityNotes()));
		}
		List<String> procedures = new ArrayList<>();
		procedures.add("Poison control: " + text(plan.getPoisonControlContact()));
		procedures.add("Onsite veterinarian: " + text(plan.getOnsiteVeterinarian()) + " | DVM: "
				+ text(plan.getVeterinarianDvm()));
		procedures.add("Veterinary capabilities/preamble: " + text(plan.getVeterinaryCapabilityNotes()));
		procedures.addAll(paragraphs(plan.getEmergencyProcedures()));
		procedures.add(plan.isAviationAssetsUsed()
				? "[X] Aviation assets are used; coordinate with Air Operations."
				: "[ ] Aviation assets are used.");
		return List.of(
				new Section("1. INCIDENT NAME", List.of(text(incident.getIncidentName()))),
				new Section("2. OPERATIONAL PERIOD", List.of("From: " + formatPdfDate(data, incident.getOperationalPeriodStart())
						+ " " + formatPdfTime(data, incident.getOperationalPeriodStart()) + " | To: "
						+ formatPdfDate(data, incident.getOperationalPeriodEnd()) + " "
						+ formatPdfTime(data, incident.getOperationalPeriodEnd()))),
				new Section("3. MEDICAL AID STATIONS", plan.getAidStationInformation().isBlank()
						? List.of("No canine aid station information entered.")
						: paragraphs(plan.getAidStationInformation())),
				new Section("4. TRANSPORTATION (AIR OR GROUND)", plan.getTransportServices().isBlank()
						? List.of("No canine transport information entered.")
						: paragraphs(plan.getTransportServices())),
				new Section("5. VETERINARY HOSPITALS", facilities),
				new Section("6. SPECIAL MEDICAL EMERGENCY PROCEDURES", procedures),
				new Section("7. PREPARED BY (MEDICAL UNIT LEADER)", List.of(
						"Name: " + text(plan.getPreparedByMedicalUnitLeader()),
						"Signature: ______________________________________________",
						"Date/Time: " + (plan.getPreparedAt() == null ? "" : formatPdfDate(data, plan.getPreparedAt())
								+ " " + formatPdfTime(data, plan.getPreparedAt())))),
				new Section("8. REVIEWED BY (SAFETY OFFICER)", List.of(
						"Name: " + text(plan.getReviewedBySafetyOfficer()),
						"Signature: ______________________________________________",
						"Review status: " + text(plan.getReviewStatus()))));
	}

	/**
	 * Draws repeated form context and footer on each page.
	 *
	 * @param document output document.
	 * @param page current page.
	 * @param data incident.
	 * @param layout printable bounds.
	 * @param regular regular font.
	 * @param bold bold font.
	 * @param pageNumber current page number.
	 * @throws IOException when writing fails.
	 */
	private void drawPage(PDDocument document, PDPage page, AppData data, FormLayout layout, PDType1Font regular,
			PDType1Font bold, int pageNumber) throws IOException {
		try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
			drawFormHeader(stream, bold, layout, FORM_KEY, "CANINE MEDICAL PLAN (ADAPTED)"
					+ (pageNumber == 1 ? "" : " (CONTINUED)"));
			drawFormFrame(stream, layout);
			float half = CONTEXT_HEIGHT / 2f;
			drawCell(stream, bold, regular, layout.x(), layout.top() - half, layout.width(), half,
					"1. INCIDENT NAME", text(data.getIncidentContext().getIncidentName()));
			drawCell(stream, bold, regular, layout.x(), layout.top() - CONTEXT_HEIGHT, layout.width(), half,
					"2. OPERATIONAL PERIOD", formatPdfDate(data, data.getIncidentContext().getOperationalPeriodStart())
							+ " " + formatPdfTime(data, data.getIncidentContext().getOperationalPeriodStart()) + " to "
							+ formatPdfDate(data, data.getIncidentContext().getOperationalPeriodEnd()) + " "
							+ formatPdfTime(data, data.getIncidentContext().getOperationalPeriodEnd()));
			drawFormFooterBand(stream, bold, regular, FONT_SIZE, PADDING, layout.x(), layout.y(), layout.width(),
					FOOTER_HEIGHT, 0.68f, FORM_KEY, addPageOffset(data.getCanineMedicalPlan().getIapPage(),
							pageNumber - 1), formatPdfDate(data, data.getCanineMedicalPlan().getPreparedAt()) + " "
									+ formatPdfTime(data, data.getCanineMedicalPlan().getPreparedAt()));
		}
	}

	/**
	 * Draws one section and all pre-wrapped content lines.
	 *
	 * @param document output document.
	 * @param page target page.
	 * @param layout printable area.
	 * @param regular regular font.
	 * @param bold bold font.
	 * @param top section top.
	 * @param height section height.
	 * @param title section title.
	 * @param lines complete visible lines.
	 * @throws IOException when drawing fails.
	 */
	private void drawSection(PDDocument document, PDPage page, FormLayout layout, PDType1Font regular,
			PDType1Font bold, float top, float height, String title, List<String> lines) throws IOException {
		try (PDPageContentStream stream = new PDPageContentStream(document, page,
				PDPageContentStream.AppendMode.APPEND, true, true)) {
			stream.addRect(layout.x(), top - height, layout.width(), height);
			stream.stroke();
			write(stream, bold, layout.x() + PADDING, top - 12f, title);
			float y = top - SECTION_HEADER_HEIGHT - PADDING - FONT_SIZE;
			for (String line : lines) {
				write(stream, regular, layout.x() + PADDING, y, line);
				y -= LINE_HEIGHT;
			}
		}
	}

	/**
	 * Draws a compact titled context field.
	 *
	 * @param stream page stream.
	 * @param bold bold font.
	 * @param regular regular font.
	 * @param x left coordinate.
	 * @param y bottom coordinate.
	 * @param width field width.
	 * @param height field height.
	 * @param title field title.
	 * @param value field value.
	 * @throws IOException when drawing fails.
	 */
	private void drawCell(PDPageContentStream stream, PDType1Font bold, PDType1Font regular, float x, float y,
			float width, float height, String title, String value) throws IOException {
		stream.addRect(x, y, width, height);
		stream.stroke();
		write(stream, bold, x + PADDING, y + height - 12f, title);
		List<String> lines = wrap(value, regular, width - 140f);
		for (int index = 0; index < lines.size(); index++) {
			write(stream, regular, x + 130f, y + height - 12f - (index * 9f), lines.get(index));
		}
	}

	/**
	 * Writes one line of PDF text.
	 *
	 * @param stream page stream.
	 * @param font line font.
	 * @param x horizontal coordinate.
	 * @param y baseline.
	 * @param value display text.
	 * @throws IOException when drawing fails.
	 */
	private void write(PDPageContentStream stream, PDType1Font font, float x, float y, String value)
			throws IOException {
		stream.beginText();
		stream.setFont(font, FONT_SIZE);
		stream.newLineAtOffset(x, y);
		stream.showText(text(value));
		stream.endText();
	}

	/**
	 * Wraps paragraphs to measured printable width and splits overlong tokens.
	 *
	 * @param value source paragraph.
	 * @param font text font.
	 * @param width printable width.
	 * @return all wrapped lines.
	 */
	private List<String> wrap(String value, PDType1Font font, float width) {
		List<String> result = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : text(value).split("\\s+")) {
			if (word.isEmpty()) {
				continue;
			}
			if (measure(font, line.isEmpty() ? word : line + " " + word) > width && !line.isEmpty()) {
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
		if (!line.isEmpty()) {
			result.add(line.toString().stripTrailing());
		}
		return result.isEmpty() ? List.of("") : result;
	}

	/**
	 * Measures a string in PDF points.
	 *
	 * @param font font used for measurement.
	 * @param value text value.
	 * @return rendered width.
	 */
	private float measure(PDType1Font font, String value) {
		try {
			return font.getStringWidth(value) / 1000f * FONT_SIZE;
		} catch (IOException exception) {
			return value.length() * FONT_SIZE;
		}
	}

	/**
	 * Returns printable content width inside the section border.
	 *
	 * @param layout form layout.
	 * @return available line width.
	 */
	private float contentWidth(FormLayout layout) {
		return layout.width() - PADDING * 2;
	}

	/**
	 * Converts distance to a readable kilometer/meter string.
	 *
	 * @param meters distance in meters.
	 * @return formatted distance.
	 */
	private String formatDistance(double meters) {
		return String.format(java.util.Locale.ROOT, "%.1f km", meters / 1000d);
	}

	/**
	 * Displays unknown availability distinctly from yes or no.
	 *
	 * @param available availability state.
	 * @return status label.
	 */
	private String availability(Boolean available) {
		return available == null ? "Unknown (not verified)" : available ? "Yes" : "No";
	}

	/**
	 * Splits multiline procedures while retaining each paragraph for wrapping.
	 *
	 * @param value procedure text.
	 * @return procedure paragraphs.
	 */
	private List<String> paragraphs(String value) {
		return value == null || value.isBlank() ? List.of("No additional procedures entered.")
				: List.of(value.split("\\R", -1));
	}

	/**
	 * Normalizes nullable text.
	 *
	 * @param value source text.
	 * @return non-null text.
	 */
	private String text(String value) {
		return value == null ? "" : value;
	}

	/**
	 * One numbered block and its paragraphs.
	 *
	 * @param title section title.
	 * @param lines content lines.
	 */
	private record Section(String title, List<String> lines) {
	}
}
