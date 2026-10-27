/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.pdf;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.PdfLayoutSettings.PaperSize;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Coordinate and continuation coverage shared by human and canine medical plans. */
class Ics206FormLayoutTest {
	@Test
	void operationalPeriodUsesTwoNonOverlappingTimezoneAwareRowsOnLetterAndA4() throws Exception {
		for (PaperSize paper : PaperSize.values()) {
			for (AbstractPdfRenderer renderer : List.of(new Ics206PdfRenderer(), new Ics206CaninePdfRenderer())) {
				AppData data = data(paper);
				try (PDDocument pdf = render((PdfFormRenderer) renderer, data)) {
					assertEquals(1, pdf.getNumberOfPages());
					Coordinates coordinates = new Coordinates();
					String text = coordinates.getText(pdf).replaceAll("\\s+", " ");
					for (String value : List.of("Date From: 2025-12-31", "Date To: 2026-01-01",
							"Time From: 20:30", "Time To: 03:45")) {
						assertTrue(text.contains(value), value);
					}
					assertTrue(text.contains("Full incident name retained"));
					AbstractPdfRenderer.FormLayout layout = renderer.formLayout(pdf.getPage(0), data);
					assertEquals(coordinates.y("Date From:"), coordinates.y("Date To:"), .1f);
					assertEquals(coordinates.y("Time From:"), coordinates.y("Time To:"), .1f);
					assertTrue(coordinates.y("Date From:") < coordinates.y("Time From:"));
					assertTrue(coordinates.right("Date From: 2025-12-31") < coordinates.x("Date To:"));
					assertTrue(coordinates.right("Time From: 20:30") < coordinates.x("Time To:"));
					assertTrue(coordinates.right("Date To: 2026-01-01") < layout.x() + layout.width());
					assertTrue(coordinates.right("Full incident name retained")
							< layout.x() + layout.width() * .38f);
					assertTrue(coordinates.y("2. OPERATIONAL PERIOD") < coordinates.y("Date From:"));
					assertTrue(coordinates.y("Time From:") < coordinates.y("3. MEDICAL AID STATIONS"));
					assertTrue(coordinates.y("1. INCIDENT NAME") > pdf.getPage(0).getMediaBox().getHeight()
							- layout.top());
				}
			}
		}
	}

	@Test
	void continuationsRepeatCanonicalTitlesAndKeepHeaderAndProcedureContentInsideTheFrame() throws Exception {
		for (PaperSize paper : PaperSize.values()) {
			for (PdfFormRenderer renderer : List.of(new Ics206PdfRenderer(), new Ics206CaninePdfRenderer())) {
				AppData data = data(paper);
				String procedures = "Continue the medical response safely. ".repeat(600) + " LAST_PROCEDURE";
				data.getMedicalPlan().setEmergencyProcedures(procedures);
				data.getCanineMedicalPlan().setEmergencyProcedures(procedures);
				try (PDDocument pdf = render(renderer, data)) {
					assertTrue(pdf.getNumberOfPages() > 1);
					int expected = renderer instanceof Ics206PdfRenderer
							? Ics206PdfRenderer.pageCount(data) : Ics206CaninePdfRenderer.pageCount(data);
					assertEquals(expected, pdf.getNumberOfPages());
					assertTrue(new PDFTextStripper().getText(pdf).contains("LAST_PROCEDURE"));
					for (int page = 2; page <= pdf.getNumberOfPages(); page++) {
						Coordinates coordinates = new Coordinates();
						coordinates.setStartPage(page);
						coordinates.setEndPage(page);
						String text = coordinates.getText(pdf);
						String title = renderer instanceof Ics206PdfRenderer
								? "MEDICAL PLAN (ICS 206)" : "CANINE MEDICAL PLAN (ICS 206CANINE)";
						assertTrue(text.contains(title + " Page " + page), text);
						assertTrue(coordinates.y(title) < coordinates.y("1. INCIDENT NAME"));
						assertTrue(coordinates.y("2. OPERATIONAL PERIOD") < coordinates.y("Date From:"));
						assertTrue(coordinates.y("Time From:") < coordinates.y("6. SPECIAL MEDICAL"));
						assertTrue(text.contains("Date From: 2025-12-31"));
						assertTrue(text.contains("Time To: 03:45"));
					}
				}
			}
		}
	}

	/**
	 * Creates a timezone-sensitive medical plan for one paper size.
	 *
	 * @param paper requested export paper.
	 * @return source document with timestamps crossing a local date boundary.
	 */
	private AppData data(PaperSize paper) {
		AppData data = new AppData();
		data.getPdfLayoutSettings().setPaperSize(paper);
		data.setUseSystemTimeZone(false);
		data.setConfiguredTimeZoneId("America/New_York");
		data.getIncidentContext().setIncidentName("Full incident name retained");
		data.getIncidentContext().setOperationalPeriodStart(LocalDateTime.of(2026, 1, 1, 1, 30));
		data.getIncidentContext().setOperationalPeriodEnd(LocalDateTime.of(2026, 1, 1, 8, 45));
		return data;
	}

	/**
	 * Renders and loads a medical form, deleting its intermediate PDF.
	 *
	 * @param renderer requested medical form.
	 * @param data export data and settings.
	 * @return loaded PDF owned by the caller.
	 * @throws IOException when rendering or loading fails.
	 */
	private PDDocument render(PdfFormRenderer renderer, AppData data) throws IOException {
		Path output = Path.of("target", "test-output", "medical-layout", "form-" + System.nanoTime() + ".pdf");
		try {
			renderer.render(data, output);
			return Loader.loadPDF(Files.readAllBytes(output));
		} finally {
			Files.deleteIfExists(output);
		}
	}

	/** Captures glyph bounds without combining adjacent operational-period columns. */
	private static final class Coordinates extends PDFTextStripper {
		private final List<TextPosition> positions = new ArrayList<>();

		/**
		 * Creates a coordinate extractor.
		 *
		 * @throws IOException when initialization fails.
		 */
		private Coordinates() throws IOException {
			super();
		}

		/** {@inheritDoc} */
		@Override
		protected void processTextPosition(TextPosition position) {
			positions.add(position);
			super.processTextPosition(position);
		}

		/**
		 * Finds the glyphs belonging to an explicitly rendered text value.
		 *
		 * @param value complete text or prefix.
		 * @return matching glyph positions.
		 */
		private List<TextPosition> find(String value) {
			StringBuilder text = new StringBuilder();
			for (TextPosition position : positions) {
				text.append(position.getUnicode());
			}
			int start = text.indexOf(value);
			assertTrue(start >= 0, "Missing coordinates for " + value);
			List<TextPosition> result = new ArrayList<>();
			int offset = 0;
			for (TextPosition position : positions) {
				int end = offset + position.getUnicode().length();
				if (offset < start + value.length() && end > start) {
					result.add(position);
				}
				offset = end;
			}
			return result;
		}

		/**
		 * Returns a text value's left edge.
		 *
		 * @param value displayed text.
		 * @return left coordinate.
		 */
		private float x(String value) {
			return find(value).get(0).getXDirAdj();
		}

		/**
		 * Returns a text value's right edge.
		 *
		 * @param value displayed text.
		 * @return right coordinate.
		 */
		private float right(String value) {
			List<TextPosition> found = find(value);
			TextPosition last = found.get(found.size() - 1);
			return last.getXDirAdj() + last.getWidthDirAdj();
		}

		/**
		 * Returns a text value's baseline measured down from the page top.
		 *
		 * @param value displayed text.
		 * @return top-relative baseline.
		 */
		private float y(String value) {
			return find(value).get(0).getYDirAdj();
		}
	}
}
