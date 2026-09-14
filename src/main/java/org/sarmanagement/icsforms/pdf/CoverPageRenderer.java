package org.sarmanagement.icsforms.pdf;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.IncidentContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;

/**
 * Renders the title page prepended to merged IAP bundle exports.
 */
public class CoverPageRenderer extends AbstractPdfRenderer implements PdfFormRenderer {
	private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
	private static final float TITLE_FONT_SIZE = 22f;
	private static final float LABEL_FONT_SIZE = 12f;
	private static final float VALUE_FONT_SIZE = 12f;
	private static final float LINE_SPACING = 28f;
	private final ApplicationMetadata applicationMetadata;

	public CoverPageRenderer() {
		this(ApplicationMetadata.detect());
	}

	CoverPageRenderer(ApplicationMetadata applicationMetadata) {
		this.applicationMetadata = applicationMetadata;
	}

	/** {@inheritDoc} */
	@Override
	public String getFormKey() {
		return "COVER";
	}

	/** {@inheritDoc} */
	@Override
	public void render(AppData data, Path outputFile) throws IOException {
		Files.createDirectories(outputFile.getParent());
		try (PDDocument document = new PDDocument()) {
			PDPage page = newPage(data);
			document.addPage(page);
			try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
				PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
				PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
				IncidentContext context = data == null ? null : data.getIncidentContext();

				String incidentName = context == null ? "" : safe(context.getIncidentName());
				String operationalPeriod = formatOperationalPeriod(data, context);
				String datePrepared = DATE_FORMATTER.format(java.time.LocalDate.now(pdfZoneId(data)));
				String preparedWith = applicationMetadata.preparedWithValue();
				String allTimesIn = pdfTimeZoneLabel(data);

				float pageWidth = page.getMediaBox().getWidth();
				float pageHeight = page.getMediaBox().getHeight();
				float titleWidth = bold.getStringWidth("INCIDENT ACTION PLAN") / 1000f * TITLE_FONT_SIZE;
				float[] lineWidths = new float[]{pairWidth(bold, regular, "Incident Name:", incidentName),
						pairWidth(bold, regular, "Operational Period:", operationalPeriod),
						pairWidth(bold, regular, "Date Prepared:", datePrepared),
						pairWidth(bold, regular, "All times in:", allTimesIn),
						pairWidth(bold, regular, "Prepared with:", preparedWith)};
				float maxLineWidth = Math.max(Math.max(lineWidths[0], lineWidths[1]),
						Math.max(lineWidths[2], Math.max(lineWidths[3], lineWidths[4])));
				float blockHeight = TITLE_FONT_SIZE + 20f + (LINE_SPACING * 5f);
				float centerX = pageWidth / 2f;
				float startY = (pageHeight + blockHeight) / 2f;
				float lineStartX = centerX - (maxLineWidth / 2f);

				writeCentered(stream, bold, TITLE_FONT_SIZE, centerX - (titleWidth / 2f), startY,
						"INCIDENT ACTION PLAN");
				drawInlinePair(stream, bold, regular, lineStartX, startY - 40f, "Incident Name:", incidentName);
				drawInlinePair(stream, bold, regular, lineStartX, startY - 40f - LINE_SPACING, "Operational Period:",
						operationalPeriod);
				drawInlinePair(stream, bold, regular, lineStartX, startY - 40f - (LINE_SPACING * 2f), "Date Prepared:",
						datePrepared);
				drawInlinePair(stream, bold, regular, lineStartX, startY - 40f - (LINE_SPACING * 3f), "All times in:",
						allTimesIn);
				drawInlinePair(stream, bold, regular, lineStartX, startY - 40f - (LINE_SPACING * 4f), "Prepared with:",
						preparedWith);
			}
			document.save(outputFile.toFile());
		}
	}

	private void writeCentered(PDPageContentStream stream, PDType1Font font, float fontSize, float x, float y,
			String value) throws IOException {
		stream.beginText();
		stream.setFont(font, fontSize);
		stream.newLineAtOffset(x, y);
		stream.showText(value);
		stream.endText();
	}

	private void drawInlinePair(PDPageContentStream stream, PDType1Font bold, PDType1Font regular, float x, float y,
			String label, String value) throws IOException {
		stream.beginText();
		stream.setFont(bold, LABEL_FONT_SIZE);
		stream.newLineAtOffset(x, y);
		stream.showText(label);
		stream.endText();

		float labelWidth = bold.getStringWidth(label) / 1000f * LABEL_FONT_SIZE;
		stream.beginText();
		stream.setFont(regular, VALUE_FONT_SIZE);
		stream.newLineAtOffset(x + labelWidth + 8f, y);
		stream.showText(safe(value));
		stream.endText();
	}

	private float pairWidth(PDType1Font bold, PDType1Font regular, String label, String value) throws IOException {
		float labelWidth = bold.getStringWidth(label) / 1000f * LABEL_FONT_SIZE;
		float valueWidth = regular.getStringWidth(safe(value)) / 1000f * VALUE_FONT_SIZE;
		return labelWidth + 8f + valueWidth;
	}

	/**
	 * Formats the operational period using the configured PDF timezone.
	 *
	 * @param data
	 *            incident document containing timezone settings.
	 * @param context
	 *            incident context containing the operational period.
	 * @return formatted operational-period text.
	 */
	private String formatOperationalPeriod(AppData data, IncidentContext context) {
		java.time.LocalDateTime start = context == null ? null : context.getOperationalPeriodStart();
		java.time.LocalDateTime end = context == null ? null : context.getOperationalPeriodEnd();
		if (start == null && end == null) {
			return "Not set";
		}
		if (start != null && end == null) {
			return formatPdfDateTime(data, start);
		}
		if (start == null) {
			return formatPdfDateTime(data, end);
		}
		return formatPdfDateTime(data, start) + " to " + formatPdfDateTime(data, end);
	}

	private String safe(String value) {
		return value == null ? "" : value;
	}
}
