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
import org.sarmanagement.icsforms.model.IncidentContext;
import org.sarmanagement.icsforms.model.SafetyMessageBlock;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders the ICS 208 Safety Message/Plan and hazardous materials variant.
 */
public class Ics208PdfRenderer extends AbstractPdfRenderer implements PdfFormRenderer {
	private static final float FONT_SIZE = 10f;
	private static final float LINE_HEIGHT = 13f;

	/** {@inheritDoc} */
	@Override
	public String getFormKey() {
		return "ICS 208";
	}

	/**
	 * Counts the pages required by the same wrapped message sections used by rendering.
	 *
	 * @param data incident data.
	 * @return page count, at least one.
	 */
	public static int pageCount(AppData data) {
		AppData safeData = data == null ? new AppData() : data;
		Ics208PdfRenderer renderer = new Ics208PdfRenderer();
		PDPage page = renderer.newPage(safeData);
		FormLayout layout = renderer.formLayout(page, safeData);
		int pages = 1;
		float y = renderer.contextStartY(layout, safeData);
		for (Section section : renderer.contentSections(safeData.getForm208(), safeData)) {
			int remaining = wrapContent(section.content(), 98).size();
			while (remaining > 0) {
				if (y < layout.y() + LINE_HEIGHT * 2) {
					pages++;
					y = layout.top() - 24;
				}
				int available = Math.max(1, (int) ((y - layout.y() - LINE_HEIGHT * 2) / LINE_HEIGHT));
				int fit = Math.min(remaining, available);
				remaining -= fit;
				y -= LINE_HEIGHT + 2 + (fit * LINE_HEIGHT) + 8;
				if (remaining > 0) {
					pages++;
					y = layout.top() - 24;
				}
			}
		}
		return pages;
	}

	/**
	 * Returns vertical space consumed by a bold label and its wrapped body.
	 *
	 * @param text body text.
	 * @return vertical space in points.
	 */
	private static float textHeight(String text) {
		return LINE_HEIGHT + (wrapContent(text, 98).size() * LINE_HEIGHT) + 4;
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
	 * Renders safety message sections with continuation pages.
	 *
	 * @param document target document.
	 * @param data source incident data.
	 * @throws IOException when PDF output fails.
	 */
	private void renderDocument(PDDocument document, AppData data) throws IOException {
		Ics208Form form = data.getForm208();
		IncidentContext context = data.getIncidentContext();
		List<Section> sections = contentSections(form, data);
		PDPage page = newPage(data);
		document.addPage(page);
		PDPageContentStream stream = new PDPageContentStream(document, page);
		PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
		PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
		FormLayout layout = formLayout(page, data);
		drawFormHeader(stream, bold, layout, "ICS 208",
				form.getMessageType() == Ics208Form.MessageType.HAZARDOUS_MATERIALS_SITE_SAFETY_PLAN
						? "SITE SAFETY AND CONTROL PLAN" : "SAFETY MESSAGE / PLAN");
		float y = drawContext(stream, bold, regular, layout, layout.top() - 24, context, data);
		int pageNumber = 1;
		for (Section section : sections) {
			List<String> lines = wrapContent(section.content(), 98);
			List<String> remaining = new ArrayList<>(lines);
			while (!remaining.isEmpty()) {
				if (y < layout.y() + LINE_HEIGHT * 2) {
					drawPageLabel(stream, regular, layout, pageNumber, form.getIapPage());
					stream.close();
					page = newPage(data);
					document.addPage(page);
					stream = new PDPageContentStream(document, page);
					layout = formLayout(page, data);
					drawFormHeader(stream, bold, layout, "ICS 208", "SAFETY MESSAGE / PLAN (CONTINUED)");
					y = drawContext(stream, bold, regular, layout, layout.top() - 24, context, data);
					pageNumber++;
				}
				float sectionStart = y;
				float maxLines = Math.max(1, (y - layout.y() - LINE_HEIGHT * 2) / LINE_HEIGHT);
				int fit = Math.min(remaining.size(), (int) maxLines);
				stream.beginText();
				stream.setFont(bold, FONT_SIZE);
				stream.newLineAtOffset(layout.x(), y);
				stream.showText(safe(section.heading()));
				stream.endText();
				y -= LINE_HEIGHT + 2;
				for (int index = 0; index < fit; index++) {
					String text = remaining.remove(0);
					stream.beginText();
					stream.setFont(section.hazard() ? bold : regular, FONT_SIZE);
					stream.newLineAtOffset(layout.x() + 8, y);
					stream.showText(safe(text));
					stream.endText();
					y -= LINE_HEIGHT;
				}
				stream.addRect(layout.x(), y - 3, layout.width(), sectionStart - y + 3);
				stream.stroke();
				y -= 8;
				if (!remaining.isEmpty()) {
					drawPageLabel(stream, regular, layout, pageNumber, form.getIapPage());
					stream.close();
					page = newPage(data);
					document.addPage(page);
					stream = new PDPageContentStream(document, page);
					layout = formLayout(page, data);
					drawFormHeader(stream, bold, layout, "ICS 208", "SAFETY MESSAGE / PLAN (CONTINUED)");
					y = drawContext(stream, bold, regular, layout, layout.top() - 24, context, data);
					pageNumber++;
				}
			}
		}
		drawPageLabel(stream, regular, layout, pageNumber, form.getIapPage());
		stream.close();
	}

	/**
	 * Creates printable sections from the selected message representation and site plan.
	 *
	 * @param form safety message/plan.
	 * @return ordered printable sections.
	 */
	private List<Section> contentSections(Ics208Form form, AppData data) {
		List<Section> sections = new ArrayList<>();
		if (form.getMessageMode() == Ics208Form.MessageMode.FREE_TEXT) {
			sections.add(new Section("3. Safety Message / Expanded Safety Message / Safety Plan / Site Safety Plan",
					form.getFreeText(), false));
		} else {
			sections.add(new Section("3. Safety Message — General Requirements",
					form.getGeneralRequirements(), false));
			for (SafetyMessageBlock block : form.getHazardBlocks()) {
				sections.add(new Section(block.getIncidentArea() + " — Hazard",
						block.getHazard(), true));
				sections.add(new Section("Precautions", block.getPrecautions(), false));
			}

		}
		for (var section : form.getSiteSafetyPlan().getTextSections().entrySet()) {
			sections.add(new Section("Site Safety Plan — " + section.getKey(), section.getValue(), false));
		}
		if (!form.getSiteSafetyPlan().getPreparedByName().isBlank()
				|| !form.getSiteSafetyPlan().getPreparedByPositionTitle().isBlank()) {
			sections.add(new Section("Site Safety Plan Prepared by",
					form.getSiteSafetyPlan().getPreparedByName() + " — "
							+ form.getSiteSafetyPlan().getPreparedByPositionTitle() + " — "
							+ form.getSiteSafetyPlan().getPreparedBySignature() + " — "
							+ formatPdfDateTime(data, form.getSiteSafetyPlan().getPreparedDateTime()), false));
		}
		sections.add(new Section("4. Site Safety Plan Required?",
				form.getSiteSafetyPlanRequired() == null ? "Not answered"
						: form.getSiteSafetyPlanRequired() ? "Yes" : "No", false));
		sections.add(new Section("5. Approved Site Safety Plan(s) Located At",
				form.getSiteSafetyPlan().getLocatedAt(), false));
		sections.add(new Section("Prepared by",
				form.getPreparedByName() + " — " + form.getPreparedByPositionTitle() + " — "
						+ form.getPreparedBySignature() + " — " + formatPdfDateTime(data, form.getPreparedDateTime()),
				false));
		return sections;
	}

	/**
	 * Draws repeated incident and operational-period context.
	 *
	 * @param stream PDF content stream.
	 * @param bold bold font.
	 * @param regular regular font.
	 * @param layout current page layout.
	 * @param y initial baseline.
	 * @param context shared incident context.
	 * @param data incident timezone preferences.
	 * @return next content baseline.
	 * @throws IOException when drawing fails.
	 */
	private float drawContext(PDPageContentStream stream, PDType1Font bold, PDType1Font regular, FormLayout layout,
			float y, IncidentContext context, AppData data) throws IOException {
		y = drawText(stream, bold, regular, layout.x(), y, 10, "1. Incident Name", safe(context.getIncidentName()),
				false);
		y = drawText(stream, bold, regular, layout.x(), y, 10, "2. Operational Period",
				formatPdfDateTime(data, context.getOperationalPeriodStart()) + " to "
						+ formatPdfDateTime(data, context.getOperationalPeriodEnd()), false);
		return y - 4;
	}

	/**
	 * Calculates the first message baseline after repeated incident context.
	 *
	 * @param layout current form layout.
	 * @param data incident data.
	 * @return baseline for content sections.
	 */
	private float contextStartY(FormLayout layout, AppData data) {
		IncidentContext context = data.getIncidentContext();
		return layout.top() - 24 - textHeight(context.getIncidentName())
				- textHeight(formatPdfDateTime(data, context.getOperationalPeriodStart()) + " to "
						+ formatPdfDateTime(data, context.getOperationalPeriodEnd()))
				- 4;
	}

	/**
	 * Draws section label followed by wrapped text.
	 *
	 * @param stream PDF stream.
	 * @param bold bold font.
	 * @param regular regular font.
	 * @param x left coordinate.
	 * @param y baseline.
	 * @param size font size.
	 * @param heading section label.
	 * @param content section content.
	 * @param hazard whether body text is hazard text.
	 * @return next baseline.
	 * @throws IOException when PDF output fails.
	 */
	private static float drawText(PDPageContentStream stream, PDType1Font bold, PDType1Font regular, float x, float y,
			float size, String heading, String content, boolean hazard) throws IOException {
		stream.beginText();
		stream.setFont(bold, size);
		stream.newLineAtOffset(x, y);
		stream.showText(safe(heading));
		stream.endText();
		y -= LINE_HEIGHT;
		for (String line : wrapContent(content, 98)) {
			stream.beginText();
			stream.setFont(hazard ? bold : regular, size);
			stream.newLineAtOffset(x + 8, y);
			stream.showText(safe(line));
			stream.endText();
			y -= LINE_HEIGHT;
		}
		return y - 4;
	}

	/**
	 * Wraps paragraph text for the fixed-width PDF font.
	 *
	 * @param content text content.
	 * @param width target character width.
	 * @return wrapped lines preserving paragraph boundaries.
	 */
	private static List<String> wrapContent(String content, int width) {
		List<String> result = new ArrayList<>();
		for (String paragraph : safe(content).split("\\R", -1)) {
			if (paragraph.isBlank()) {
				result.add("");
			} else {
				result.addAll(new Ics208PdfRenderer().wrap(paragraph, width));
			}
		}
		return result;
	}

	/**
	 * Returns a non-null printable value.
	 *
	 * @param value source text.
	 * @return safe text for PDFBox standard fonts.
	 */
	private static String safe(String value) {
		return value == null ? "" : value.replaceAll("[^\\x20-\\x7E]", "?");
	}

	/**
	 * Draws local and IAP page labels at the bottom of the current page.
	 *
	 * @param stream PDF stream.
	 * @param font regular font.
	 * @param layout page layout.
	 * @param pageNumber local page number.
	 * @param iapFirstPage first IAP page label.
	 * @throws IOException when PDF drawing fails.
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
		drawTextLine(stream, font, layout.x(), layout.y() + 8, label);
	}

	/**
	 * Draws footer text at a fixed baseline.
	 *
	 * @param stream PDF stream.
	 * @param font display font.
	 * @param x left coordinate.
	 * @param y baseline.
	 * @param label footer text.
	 * @throws IOException when PDF drawing fails.
	 */
	private static void drawTextLine(PDPageContentStream stream, PDType1Font font, float x, float y, String label)
			throws IOException {
		stream.beginText();
		stream.setFont(font, 8);
		stream.newLineAtOffset(x, y);
		stream.showText(safe(label));
		stream.endText();
	}

	/**
	 * One independently paginated section.
	 *
	 * @param heading visible section heading.
	 * @param content body text.
	 * @param hazard whether content is hazard text.
	 */
	private record Section(String heading, String content, boolean hazard) {
	}
}
