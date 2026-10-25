/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.pdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSNumber;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.PdfLayoutSettings;
import org.sarmanagement.icsforms.model.RadioChannel;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Verifies lossless radio-plan pagination and actual physical IAP print geometry. */
class Ics205PdfRendererTest {
	@TempDir
	Path directory;

	@Test
	void blankReferenceIsLandscapeWithEightRuledBodyRowsAndNoPageRotation() throws Exception {
		AppData data = new AppData();
		Path file = directory.resolve("blank.pdf");
		new Ics205PdfRenderer().render(data, file);
		try (PDDocument pdf = Loader.loadPDF(file.toFile())) {
			assertEquals(1, pdf.getNumberOfPages());
			assertBoxes(pdf.getPage(0), PDRectangle.LETTER, false);
			String text = new PDFTextStripper().getText(pdf);
			assertTrue(text.contains("INCIDENT RADIO COMMUNICATIONS PLAN"));
			assertTrue(text.contains("Special Instructions"));
			List<float[]> rectangles = operations(pdf.getPage(0), "re", 4);
			// Eight reference rows, each with thirteen independently ruled cells.
			assertEquals(8 * 13, rectangles.stream().filter(rect -> Math.abs(rect[3] - 37.25f) < 0.01).count());
			assertGeometry(pdf, 36);
		}
	}

	@Test
	void completeNativeFieldsDatesAndFrequencyPrecisionSurviveWithoutMutatingModel() throws Exception {
		AppData data = incident();
		RadioChannel channel = channel(1);
		channel.setZoneGroup("North");
		channel.setFunction("Tactical");
		channel.setAssignment("Division A");
		channel.setRxFrequency("155.16");
		channel.setRxBandwidth("12.5");
		channel.setRxToneNac("NAC293");
		channel.setTxFrequency("155.123456");
		channel.setTxBandwidth("25");
		channel.setTxToneNac("D023N");
		channel.setMode("Mixed");
		channel.setDigitalProtocol("P25");
		channel.setSystem("County");
		channel.setRemarks("Ridge repeater");
		channel.setTxPolicy("RECEIVE_ONLY");
		channel.getProgrammingMetadata().put("private", "Not printed");
		data.getForm205().getChannels().add(channel);
		data.getForm205().setSpecialInstructions("Monitor command before calling.");
		data.getForm205().setIapPage("17");
		RadioChannel excluded = channel(2);
		excluded.setChannelName("Excluded inventory");
		excluded.setIncludeOnForm(false);
		data.getForm205().getChannels().add(excluded);
		PdfExportService service = new PdfExportService(new Ics205PdfRenderer());
		Path file = service.exportSelected("ICS 205", data, directory);
		try (PDDocument pdf = Loader.loadPDF(file.toFile())) {
			String text = new PDFTextStripper().getText(pdf);
			String compact = text.replaceAll("\\s", "");
			for (String value : List.of("North", "Tactical", "Division A", "155.1600", "12.5", "NAC293",
					"155.1235", "D023N", "Mixed", "P25", "County", "Ridge repeater", "RECEIVE_ONLY",
					"Radio preparer", "Entered signature", "2026-10-24", "2026-10-25", "09:00", "08:00", "IAP Page: 17")) {
				assertTrue(compact.contains(value.replaceAll("\\s", "")), value);
			}
			assertFalse(text.contains("Excluded inventory"));
			assertFalse(text.contains("Not printed"));
			assertGeometry(pdf, 36);
		}
		assertEquals("155.16", channel.getRxFrequency());
		assertEquals("155.123456", channel.getTxFrequency());
		assertEquals("17", data.getForm205().getIapPage());
		assertEquals(2, data.getForm205().getChannels().size());
	}

	@Test
	void qualifiedFrequenciesRemainStringsAndOnlyIncludedFieldsAreValidated() throws Exception {
		AppData data = incident();
		RadioChannel included = channel(1);
		included.setRxFrequency("Marine 16");
		included.setTxFrequency("156.8000");
		data.getForm205().getChannels().add(included);
		RadioChannel excluded = channel(2);
		excluded.setIncludeOnForm(false);
		excluded.setRemarks("\u0000");
		included.getProgrammingMetadata().put("notPrinted", "\u0000");
		data.getForm205().getChannels().add(excluded);
		Path file = directory.resolve("strings.pdf");
		new Ics205PdfRenderer().render(data, file);
		try (PDDocument pdf = Loader.loadPDF(file.toFile())) {
			assertTrue(new PDFTextStripper().getText(pdf).contains("Marine 16"));
		}
		included.setRemarks("\u0000");
		IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
				() -> new Ics205PdfRenderer().render(data, file));
		assertTrue(error.getMessage().contains("form205.channels[0].remarks"));
	}

	@Test
	void longIncidentAndPreparerMetadataExpandsWithoutClippingOrLosingTailText() throws Exception {
		AppData data = incident();
		data.getIncidentContext().setIncidentName("Incident ".repeat(80) + "IncidentEnd");
		data.getForm205().setPreparedByName("Operations ".repeat(50) + "NameEnd");
		data.getForm205().setPreparedBySignature("Explicit ".repeat(50) + "SignatureEnd");
		for (int i = 1; i <= 9; i++) {
			data.getForm205().getChannels().add(channel(i));
		}
		for (boolean portrait : List.of(false, true)) {
			Path file = directory.resolve("metadata-" + portrait + ".pdf");
			new Ics205PdfRenderer().render(data, file, portrait);
			try (PDDocument pdf = Loader.loadPDF(file.toFile())) {
				assertEquals(Ics205PdfRenderer.pageCount(data), pdf.getNumberOfPages());
				for (int page = 1; page <= pdf.getNumberOfPages(); page++) {
					String text = pageText(pdf, page).replaceAll("\\s", "");
					assertTrue(text.contains("IncidentEnd"));
					assertTrue(text.contains("NameEnd"));
					assertTrue(text.contains("SignatureEnd"));
				}
				assertGeometry(pdf, 36);
			}
		}
	}

	@Test
	void nineChannelsAndLongUnbrokenRemarksAndInstructionsNeverTruncate() throws Exception {
		AppData data = incident();
		for (int i = 1; i <= 9; i++) {
			data.getForm205().getChannels().add(channel(i));
		}
		assertEquals(2, Ics205PdfRenderer.pageCount(data));
		RadioChannel first = data.getForm205().getChannels().get(0);
		List<String> remarks = new ArrayList<>();
		List<String> instructions = new ArrayList<>();
		for (int i = 0; i < 95; i++) {
			remarks.add("R" + String.format("%03d", i));
			instructions.add("Instruction " + String.format("%03d", i));
		}
		first.setRemarks(String.join("\n", remarks) + "\n" + "Q".repeat(300));
		data.getForm205().setSpecialInstructions(String.join("\n", instructions));
		Path file = directory.resolve("continuations.pdf");
		new Ics205PdfRenderer().render(data, file);
		try (PDDocument pdf = Loader.loadPDF(file.toFile())) {
			assertEquals(Ics205PdfRenderer.pageCount(data), pdf.getNumberOfPages());
			assertTrue(pdf.getNumberOfPages() > 4);
			String allText = new PDFTextStripper().getText(pdf);
			for (String remark : remarks) {
				assertTrue(allText.contains(remark), remark);
			}
			for (String instruction : instructions) {
				assertTrue(allText.contains(instruction), instruction);
			}
			assertEquals(300, allText.chars().filter(c -> c == 'Q').count());
			for (int i = 1; i <= 9; i++) {
				assertTrue(allText.contains("Channel" + i), "channel " + i);
			}
			for (int page = 1; page <= pdf.getNumberOfPages(); page++) {
				String text = pageText(pdf, page);
				assertTrue(text.contains("Radio incident"));
				assertTrue(text.contains("Operational Period"));
				assertTrue(text.contains("Radio preparer"));
				assertTrue(text.contains("Entered signature"));
				assertTrue(text.contains("Page " + page + " of " + pdf.getNumberOfPages()));
			}
			assertGeometry(pdf, 36);
		}
	}

	@Test
	void a4PaperAndLargeMarginsApplyToBothNativeAndPortraitFitGeometry() throws Exception {
		AppData data = incident();
		data.getPdfLayoutSettings().setPaperSize(PdfLayoutSettings.PaperSize.A4);
		data.getForm205().getChannels().add(channel(1));
		for (float margin : new float[]{54, 100, 144}) {
			data.getPdfLayoutSettings().setPageMarginPoints(margin);
			for (boolean portrait : List.of(false, true)) {
				Path file = directory.resolve("a4-" + margin + "-" + portrait + ".pdf");
				new Ics205PdfRenderer().render(data, file, portrait);
				try (PDDocument pdf = Loader.loadPDF(file.toFile())) {
					assertBoxes(pdf.getPage(0), PDRectangle.A4, portrait);
					assertRotationMatrix(pdf.getPage(0), portrait, PDRectangle.A4.getWidth());
					assertBodyDirection(pdf, 1, portrait);
					assertGeometry(pdf, margin);
					assertUprightIapNumber(pdf, 1, margin + 8);
				}
			}
		}
	}

	@Test
	void bundleDefaultsToPortraitMediaWithRotatedContentAndUprightSequentialNumbers() throws Exception {
		assertMixedBundle(false);
	}

	@Test
	void nativeBundleAlternativeDoesNotAlter205aOr233OrientationOrLaterPageNumbers() throws Exception {
		assertMixedBundle(true);
	}

	/**
	 * Checks mixed-form physical order, counts, content matrices and page labels.
	 *
	 * @param nativeLandscape explicit ICS 205 orientation choice
	 * @throws Exception when rendering or parsing fails
	 */
	private void assertMixedBundle(boolean nativeLandscape) throws Exception {
		AppData data = incident();
		data.setIcs233Portrait(false);
		data.setIcs205Enabled(false);
		for (int i = 1; i <= 9; i++) {
			data.getForm205().getChannels().add(channel(i));
		}
		PdfExportService service = new PdfExportService(new Ics205aPdfRenderer(), new Ics205PdfRenderer(),
				new Ics202PdfRenderer(), new Ics207PdfRenderer(), new Ics233CgPdfRenderer());
		List<String> keys = List.of("ICS 233-CG", "ICS 207", "ICS 205A", "ICS 205", "ICS 202");
		Path bundle = nativeLandscape ? service.exportIapBundle(data, directory, keys, true)
				: service.exportIapBundle(data, directory, keys);
		try (PDDocument pdf = Loader.loadPDF(bundle.toFile())) {
			assertEquals(7, pdf.getNumberOfPages());
			assertTrue(pageText(pdf, 1).contains("INCIDENT ACTION PLAN"));
			assertTrue(pageText(pdf, 2).contains("INCIDENT OBJECTIVES"));
			for (int page = 3; page <= 4; page++) {
				assertBoxes(pdf.getPage(page - 1), PDRectangle.LETTER, !nativeLandscape);
				assertRotationMatrix(pdf.getPage(page - 1), !nativeLandscape, PDRectangle.LETTER.getWidth());
				assertBodyDirection(pdf, page, !nativeLandscape);
				assertTrue(pageText(pdf, page).contains("IAP Page: " + (page - 1)));
				assertUprightIapNumber(pdf, page, 44);
			}
			assertTrue(pageText(pdf, 5).contains("COMMUNICATIONS LIST"));
			assertTrue(pageText(pdf, 5).matches("(?s).*IAP Page\\s+4\\b.*"));
			assertBoxes(pdf.getPage(4), PDRectangle.LETTER, true);
			assertTrue(pageText(pdf, 6).contains("INCIDENT ORGANIZATION CHART"));
			assertTrue(pageText(pdf, 6).matches("(?s).*IAP Page:?\\s+5\\b.*"));
			assertBoxes(pdf.getPage(6), PDRectangle.LETTER, false);
			assertEquals(0, pdf.getPage(6).getRotation());
			assertFalse(data.isIcs233Portrait());
			assertEquals("2", data.getForm205().getIapPage());
			assertEquals("4", data.getForm205aIapPage());
			assertEquals("5", data.getForm207().getIapPage());
			assertGeometry(pdf, 16);
		}
	}

	/**
	 * Provides entered incident metadata with distinct header and signature dates.
	 *
	 * @return source incident
	 */
	private AppData incident() {
		AppData data = new AppData();
		data.setUseSystemTimeZone(false);
		data.setConfiguredTimeZoneId("UTC");
		data.getIncidentContext().setIncidentName("Radio incident");
		data.getIncidentContext().setOperationalPeriodStart(LocalDateTime.of(2026, 10, 24, 10, 0));
		data.getIncidentContext().setOperationalPeriodEnd(LocalDateTime.of(2026, 10, 25, 10, 0));
		data.getForm205().setPreparedByName("Radio preparer");
		data.getForm205().setPreparedBySignature("Entered signature");
		data.getForm205().setDatePrepared(LocalDateTime.of(2026, 10, 24, 9, 0));
		data.getForm205().setPreparedDateTime(LocalDateTime.of(2026, 10, 25, 8, 0));
		return data;
	}

	/**
	 * Creates a short inventory row that fits the eight-row reference.
	 *
	 * @param number channel number
	 * @return included channel
	 */
	private RadioChannel channel(int number) {
		RadioChannel channel = new RadioChannel();
		channel.setChannelNumber(String.valueOf(number));
		channel.setChannelName("Channel" + number);
		channel.setRxFrequency("155.1600");
		return channel;
	}

	/**
	 * Extracts one physical page's text.
	 *
	 * @param pdf parsed document
	 * @param page one-based page number
	 * @return page text
	 * @throws IOException when text extraction fails
	 */
	private String pageText(PDDocument pdf, int page) throws IOException {
		PDFTextStripper stripper = new PDFTextStripper();
		stripper.setStartPage(page);
		stripper.setEndPage(page);
		return stripper.getText(pdf);
	}

	/**
	 * Checks physical media and crop boxes rather than orientation metadata.
	 *
	 * @param page physical page
	 * @param paper portrait reference paper
	 * @param portrait expected physical orientation
	 */
	private void assertBoxes(PDPage page, PDRectangle paper, boolean portrait) {
		for (PDRectangle box : List.of(page.getMediaBox(), page.getCropBox())) {
			assertEquals(portrait ? paper.getWidth() : paper.getHeight(), box.getWidth(), 0.01);
			assertEquals(portrait ? paper.getHeight() : paper.getWidth(), box.getHeight(), 0.01);
			assertEquals(0, box.getLowerLeftX(), 0.01);
			assertEquals(0, box.getLowerLeftY(), 0.01);
		}
		assertEquals(0, page.getRotation(), "No /Rotate shortcut is permitted");
	}

	/**
	 * Checks an actual rotation and translation in the content stream.
	 *
	 * @param page physical page
	 * @param rotated whether portrait fitting is expected
	 * @param portraitWidth required translation to keep rotated content on paper
	 * @throws IOException when stream parsing fails
	 */
	private void assertRotationMatrix(PDPage page, boolean rotated, float portraitWidth) throws IOException {
		List<float[]> matrices = operations(page, "cm", 6);
		boolean found = matrices.stream().anyMatch(matrix -> Math.abs(matrix[0]) < 0.001
				&& Math.abs(matrix[1] - 1) < 0.001 && Math.abs(matrix[2] + 1) < 0.001
				&& Math.abs(matrix[3]) < 0.001 && Math.abs(matrix[4] - portraitWidth) < 0.01
				&& Math.abs(matrix[5]) < 0.001);
		assertEquals(rotated, found);
	}

	/**
	 * Checks that the IAP label has an identity-oriented text matrix at the physical footer.
	 *
	 * @param pdf document
	 * @param page one-based page number
	 * @param expectedBaseline physical footer baseline
	 * @throws IOException when text extraction fails
	 */
	private void assertUprightIapNumber(PDDocument pdf, int page, float expectedBaseline) throws IOException {
		Positions positions = new Positions();
		positions.setStartPage(page);
		positions.setEndPage(page);
		positions.getText(pdf);
		List<TextPosition> number = positions.runs.stream().filter(run -> run.text().startsWith("IAP Page:"))
				.findFirst().orElseThrow().positions();
		for (TextPosition glyph : number) {
			assertEquals(0, glyph.getDir(), 0.01);
			assertEquals(expectedBaseline, glyph.getTextMatrix().getTranslateY(), 0.01);
			assertTrue(glyph.getTextMatrix().getScaleX() > 0);
			assertEquals(0, glyph.getTextMatrix().getShearX(), 0.01);
			assertEquals(0, glyph.getTextMatrix().getShearY(), 0.01);
		}
	}

	/**
	 * Verifies actual body glyphs, not just a potentially unused content transform.
	 *
	 * @param pdf parsed document
	 * @param page one-based physical page
	 * @param rotated whether the landscape body should run upward on the sheet
	 * @throws IOException when extraction fails
	 */
	private void assertBodyDirection(PDDocument pdf, int page, boolean rotated) throws IOException {
		Positions positions = new Positions();
		positions.setStartPage(page);
		positions.setEndPage(page);
		positions.getText(pdf);
		List<TextPosition> glyphs = positions.runs.stream().flatMap(run -> run.positions().stream()).toList();
		long rotatedGlyphs = glyphs.stream().filter(glyph -> glyph.getDir() == 90).count();
		assertTrue(rotated ? rotatedGlyphs > 100 : rotatedGlyphs == 0);
		if (rotated) {
			assertTrue(glyphs.size() - rotatedGlyphs < 60, "Only page numbering remains upright");
		}
		for (TextPosition glyph : glyphs) {
			if (rotated && glyph.getDir() == 0) {
				continue;
			}
			assertEquals(rotated ? 90 : 0, glyph.getDir(), 0.01);
			if (rotated) {
				assertEquals(0, glyph.getTextMatrix().getScaleX(), 0.01);
				assertTrue(glyph.getTextMatrix().getShearY() > 0);
				assertTrue(glyph.getTextMatrix().getShearX() < 0);
			} else {
				assertTrue(glyph.getTextMatrix().getScaleX() > 0);
				assertEquals(0, glyph.getTextMatrix().getShearY(), 0.01);
			}
		}
	}

	/**
	 * Asserts every actual glyph baseline lies inside the physical sheet with safe margins.
	 *
	 * @param pdf document
	 * @param margin minimum physical edge clearance
	 * @throws IOException when text extraction fails
	 */
	private void assertGeometry(PDDocument pdf, float margin) throws IOException {
		for (int i = 1; i <= pdf.getNumberOfPages(); i++) {
			Positions positions = new Positions();
			positions.setStartPage(i);
			positions.setEndPage(i);
			positions.getText(pdf);
			PDRectangle box = pdf.getPage(i - 1).getMediaBox();
			boolean radioPage = positions.runs.stream().anyMatch(run -> run.text().contains("RADIO COMMUNICATIONS")
					|| run.positions().stream().anyMatch(glyph -> glyph.getDir() == 90));
			for (Run run : positions.runs) {
				for (TextPosition glyph : run.positions()) {
					float x = glyph.getTextMatrix().getTranslateX();
					float y = glyph.getTextMatrix().getTranslateY();
					assertTrue(x >= margin - 0.1 && x <= box.getWidth() - margin + 0.1,
							"x=" + x + " text=" + run.text());
					assertTrue(y >= margin - 0.1 && y <= box.getHeight() - margin + 0.1,
							"y=" + y + " text=" + run.text());
					float advanceX = glyph.getTextMatrix().getScaleX();
					float advanceY = glyph.getTextMatrix().getShearY();
					float magnitude = (float) Math.hypot(advanceX, advanceY);
					float endX = x + glyph.getWidthDirAdj() * advanceX / magnitude;
					float endY = y + glyph.getWidthDirAdj() * advanceY / magnitude;
					assertTrue(endX >= 0 && endX <= box.getWidth(), "Glyph advance inside media: " + run.text());
					assertTrue(endY >= 0 && endY <= box.getHeight(), "Glyph advance inside media: " + run.text());
					if (radioPage) {
						assertTrue(glyph.getFontSizeInPt() >= 8, "Readable radio-plan font size");
					}
				}
			}
			if (radioPage) {
				boolean portrait = box.getWidth() < box.getHeight();
				assertCellContents(positions, box, portrait);
				for (float[] rect : operations(pdf.getPage(i - 1), "re", 4)) {
					float minX = portrait ? box.getWidth() - rect[1] - rect[3] : rect[0];
					float minY = portrait ? rect[0] : rect[1];
					float maxX = portrait ? box.getWidth() - rect[1] : rect[0] + rect[2];
					float maxY = portrait ? rect[0] + rect[2] : rect[1] + rect[3];
					assertTrue(minX >= margin - 0.1 && maxX <= box.getWidth() - margin + 0.1,
							"Transformed cell horizontal bounds inside sheet");
					assertTrue(minY >= margin - 0.1 && maxY <= box.getHeight() - margin + 0.1,
							"Transformed cell vertical bounds inside sheet");
				}
			}
		}
	}

	/**
	 * Checks actual glyphs against the exact cell active when they were painted.
	 * This detects header/row overlap that page-only bounds checks cannot detect.
	 *
	 * @param positions glyphs paired with their own cells
	 * @param box physical media
	 * @param portrait whether to invert the body rotation before checking cell bounds
	 */
	private void assertCellContents(Positions positions, PDRectangle box, boolean portrait) {
		assertFalse(positions.cells.isEmpty());
		for (GlyphCell cell : positions.cells) {
			TextPosition glyph = cell.glyph();
			float x = portrait ? glyph.getTextMatrix().getTranslateY() : glyph.getTextMatrix().getTranslateX();
			float y = portrait ? box.getWidth() - glyph.getTextMatrix().getTranslateX()
					: glyph.getTextMatrix().getTranslateY();
			float[] rect = cell.bounds();
			assertTrue(x >= rect[0] + 3.9f, "Glyph respects left cell padding: " + glyph.getUnicode());
			assertTrue(x + glyph.getWidthDirAdj() <= rect[0] + rect[2] - 3.9f,
					"Glyph advance stays inside its own column: " + glyph.getUnicode());
			assertTrue(y >= rect[1] + 3.9f, "Glyph baseline stays above its own row bottom: " + glyph.getUnicode());
			assertTrue(y + glyph.getHeightDir() <= rect[1] + rect[3] - 3.9f,
					"Glyph ascender stays below its own row top: " + glyph.getUnicode());
		}
	}

	/**
	 * Extracts numeric operands for the requested PDF drawing operator.
	 *
	 * @param page parsed page
	 * @param name operator name
	 * @param operands operand count
	 * @return numeric operand arrays
	 * @throws IOException when parsing fails
	 */
	private List<float[]> operations(PDPage page, String name, int operands) throws IOException {
		List<Object> tokens = new PDFStreamParser(page).parse();
		List<float[]> result = new ArrayList<>();
		for (int i = operands; i < tokens.size(); i++) {
			if (tokens.get(i) instanceof Operator operator && name.equals(operator.getName())) {
				float[] values = new float[operands];
				for (int j = 0; j < operands; j++) {
					values[j] = ((COSNumber) tokens.get(i - operands + j)).floatValue();
				}
				result.add(values);
			}
		}
		return result;
	}

	/**
	 * Retains extracted glyph matrices for physical geometry assertions.
	 */
	private static class Positions extends PDFTextStripper {
		private final List<Run> runs = new ArrayList<>();
		private final List<GlyphCell> cells = new ArrayList<>();
		private float[] currentCell;

		/**
		 * Creates a position-aware extractor.
		 *
		 * @throws IOException when initialization fails
		 */
		private Positions() throws IOException {
			super();
		}

		/**
		 * Tracks the ruled cell most recently painted by the renderer.
		 *
		 * @param operator PDF content operator
		 * @param operands operator operands
		 * @throws IOException when the superclass cannot process the operator
		 */
		@Override
		protected void processOperator(Operator operator, List<COSBase> operands) throws IOException {
			if ("re".equals(operator.getName())) {
				currentCell = new float[4];
				for (int i = 0; i < 4; i++) {
					currentCell[i] = ((COSNumber) operands.get(i)).floatValue();
				}
			} else if ("Q".equals(operator.getName())) {
				currentCell = null;
			}
			super.processOperator(operator, operands);
		}

		/**
		 * Associates the actual glyph matrix with the cell being drawn.
		 *
		 * @param position decoded glyph
		 */
		@Override
		protected void processTextPosition(TextPosition position) {
			if (currentCell != null) {
				cells.add(new GlyphCell(position, currentCell));
			}
			super.processTextPosition(position);
		}

		/** {@inheritDoc} */
		@Override
		protected void writeString(String text, List<TextPosition> positions) throws IOException {
			runs.add(new Run(text, List.copyOf(positions)));
			super.writeString(text, positions);
		}
	}

	/**
	 * Holds one extracted text run and its actual glyph positions.
	 *
	 * @param text extracted text
	 * @param positions glyph matrices
	 */
	private record Run(String text, List<TextPosition> positions) {
	}

	/**
	 * Associates an actual painted glyph with its native landscape cell.
	 *
	 * @param glyph decoded glyph matrix and dimensions
	 * @param bounds cell rectangle in native coordinates
	 */
	private record GlyphCell(TextPosition glyph, float[] bounds) {
	}
}
