/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.pdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSNumber;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.PDVectorFont;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.CommunicationEntry;
import org.sarmanagement.icsforms.model.ResourceAssignment;
import org.sarmanagement.icsforms.model.TCard;
import org.sarmanagement.icsforms.model.TCardType;
import org.sarmanagement.icsforms.model.PdfLayoutSettings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Regression coverage for measured wrapping and bounded continuation pages. */
class PdfMeasuredLayoutTest {
	@Test
	void bothCommunicationsContactsAndDescendersStayInsideTheirRuledCells() throws Exception {
		for (PdfLayoutSettings.PaperSize paper : PdfLayoutSettings.PaperSize.values()) {
			AppData data = new AppData();
			data.getPdfLayoutSettings().setPaperSize(paper);
			CommunicationEntry first = new CommunicationEntry();
			first.setName("Gypsy");
			first.setFunction("Ops");
			first.setPrimaryContact("pager 155.160");
			CommunicationEntry second = new CommunicationEntry();
			second.setName("Grady");
			second.setFunction("Log");
			second.setPrimaryContact("paging 202-555-0101");
			data.getForm204().setCommunications(List.of(first, second));
			Path file = output("communications-bounds-" + paper);
			new Ics204PdfRenderer().render(data, file);
			try (PDDocument pdf = Loader.loadPDF(file.toFile())) {
				List<List<TextPosition>> contactRows = new ArrayList<>();
				PDFTextStripper stripper = new PDFTextStripper() {
					/** {@inheritDoc} */
					@Override
					protected void writeString(String text, List<TextPosition> positions) throws IOException {
						if (text.contains("Gypsy") || text.contains("Grady") || text.contains("pager")
								|| text.contains("paging")) {
							contactRows.add(List.copyOf(positions));
						}
						super.writeString(text, positions);
					}
				};
				stripper.setStartPage(1);
				stripper.setEndPage(1);
				String text = stripper.getText(pdf);
				for (String expected : List.of("Gypsy / Ops", "Grady / Log", "pager 155.160", "paging 202-555-0101")) {
					assertTrue(text.contains(expected), text);
				}
				assertTrue(text.contains("cell, pager, or radio (freq/system/channel)"), text);
				float width = pdf.getPage(0).getMediaBox().getWidth();
				List<Float> horizontalRules = new ArrayList<>();
				List<Object> tokens = new PDFStreamParser(pdf.getPage(0)).parse();
				for (int index = 2; index + 3 < tokens.size(); index++) {
					if (tokens.get(index) instanceof Operator move && "m".equals(move.getName())
							&& tokens.get(index + 3) instanceof Operator line && "l".equals(line.getName())
							&& Math.abs(((COSNumber) tokens.get(index - 2)).floatValue() - 36f) < .1f
							&& Math.abs(((COSNumber) tokens.get(index + 1)).floatValue() - (width - 36f)) < .1f) {
						float y = ((COSNumber) tokens.get(index - 1)).floatValue();
						assertEquals(y, ((COSNumber) tokens.get(index + 2)).floatValue(), .01f);
						horizontalRules.add(y);
					}
				}
				assertTrue(contactRows.size() >= 2);
				for (List<TextPosition> row : contactRows) {
					for (TextPosition glyph : row) {
						float baseline = glyph.getTextMatrix().getTranslateY();
						float bottom = horizontalRules.stream().filter(y -> y < baseline).max(Float::compare).orElseThrow();
						float top = horizontalRules.stream().filter(y -> y > baseline).min(Float::compare).orElseThrow();
						var bounds = ((PDVectorFont) glyph.getFont()).getPath(glyph.getCharacterCodes()[0]).getBounds2D();
						if (bounds.isEmpty()) {
							continue;
						}
						float scale = glyph.getFont().getFontMatrix().getScaleY() * glyph.getFontSizeInPt();
						float descent = (float) bounds.getMinY() * scale;
						float ascent = (float) bounds.getMaxY() * scale;
						assertTrue(baseline + descent > bottom + .5f, glyph.getUnicode() + " crosses lower rule");
						assertTrue(baseline + ascent < top - .5f, glyph.getUnicode() + " crosses upper rule");
						float cellRight = glyph.getXDirAdj() < width / 2f ? width / 2f : width - 36f;
						assertTrue(glyph.getXDirAdj() + glyph.getWidthDirAdj() < cellRight, glyph.getUnicode());
					}
				}
			}
		}
	}

	@Test
	void measuredWrappingUsesFullWidthAndSplitsLongTokensWithoutLosingCharacters() throws Exception {
		AbstractPdfRenderer renderer = new TestRenderer();
		PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
		List<String> lines = renderer.wrapText("narrow ".repeat(100), font, 10f, 520f);
		assertTrue(font.getStringWidth(lines.get(0)) / 1000f * 10f > 490f);
		for (String line : lines) {
			assertTrue(font.getStringWidth(line) / 1000f * 10f <= 520f);
		}
		String token = "W".repeat(300);
		List<String> split = renderer.wrapText(token, font, 10f, 100f);
		assertEquals(token, String.join("", split));
		assertTrue(split.stream().allMatch(line -> line.length() <= 10));
		assertThrows(IllegalArgumentException.class, () -> renderer.wrapText("bad\u0000text", font, 10f, 100f));
	}

	@Test
	void incidentNamesStayInsideTheirCellsForAllThreeFormsAndRendererReuse() throws Exception {
		String incident = "Maple Ridge Multi Jurisdiction Extended Incident Response ".repeat(6).trim();
		for (PdfFormRenderer renderer : List.of(new Ics202PdfRenderer(), new Ics204PdfRenderer(),
				new Ics205aPdfRenderer())) {
			AppData data = new AppData();
			data.getIncidentContext().setIncidentName(incident);
			Path file = output(renderer.getFormKey() + "-incident");
			renderer.render(data, file);
			try (PDDocument pdf = Loader.loadPDF(file.toFile())) {
				BoundsStripper stripper = new BoundsStripper();
				String text = stripper.getText(pdf).replaceAll("\\s+", " ");
				assertTrue(text.contains(incident));
				float nameRight = 306f;
				for (TextPosition position : stripper.positions) {
					if (position.getYDirAdj() > 59f && position.getYDirAdj() < 96f
							&& position.getXDirAdj() < 306f) {
						assertTrue(position.getXDirAdj() + position.getWidthDirAdj() <= nameRight - 3f);
					}
				}
			}
			data.getIncidentContext().setIncidentName("Short Name");
			renderer.render(data, file);
			try (PDDocument pdf = Loader.loadPDF(file.toFile())) {
				assertEquals(1, pdf.getNumberOfPages());
				assertTrue(new PDFTextStripper().getText(pdf).contains("Short Name"));
			}
		}
	}

	@Test
	void objectivesPaginateWithoutSplittingEntriesThatFitFreshPages() throws Exception {
		AppData data = new AppData();
		List<String> objectives = new ArrayList<>();
		for (int index = 0; index < 70; index++) {
			objectives.add("START" + index + " " + "Search drainage and protect responders throughout the incident. ".repeat(9)
					+ "END" + index);
		}
		data.getForm202().setObjectives(objectives);
		Path file = output("objectives");
		new Ics202PdfRenderer().render(data, file);
		try (PDDocument pdf = Loader.loadPDF(file.toFile())) {
			assertTrue(pdf.getNumberOfPages() > 4);
			String all = new BoundsStripper().getText(pdf).replaceAll("\\s+", " ");
			for (int index = 0; index < objectives.size(); index++) {
				assertTrue(all.contains(objectives.get(index)));
				boolean together = false;
				for (int page = 1; page <= pdf.getNumberOfPages(); page++) {
					String text = pageText(pdf, page);
					if (text.contains("START" + index + " ")) {
						assertTrue(text.contains("END" + index + "\n") || text.contains("END" + index + " "));
						together = true;
					}
				}
				assertTrue(together);
			}
			assertTrue(pageText(pdf, 2).contains("Incident Objectives (ICS 202) Page 2"));
		}
	}

	@Test
	void oversizedSingleObjectiveRetainsEveryTokenAcrossBoundedPages() throws Exception {
		AppData data = new AppData();
		List<String> tokens = new ArrayList<>();
		for (int index = 0; index < 3000; index++) {
			tokens.add(String.format("token%04d", index));
		}
		data.getForm202().setObjectives(List.of(String.join(" ", tokens)));
		Path file = output("oversized-objective");
		new Ics202PdfRenderer().render(data, file);
		try (PDDocument pdf = Loader.loadPDF(file.toFile())) {
			assertTrue(pdf.getNumberOfPages() > 3);
			String all = new BoundsStripper().getText(pdf).replaceAll("\\s+", " ");
			for (String token : tokens) {
				assertEquals(1, occurrences(all, token));
			}
		}
	}

	@Test
	void resourcesContinueOnlyAsTablesWithRepeatedHeadersAndNoDuplicatedDetails() throws Exception {
		AppData data = new AppData();
		List<ResourceAssignment> resources = new ArrayList<>();
		for (int index = 0; index < 65; index++) {
			ResourceAssignment resource = new ResourceAssignment();
			resource.setResourceIdentifier(String.format("RESOURCE%03d", index));
			resource.setLeader("Leader" + index);
			resource.setNumberOfPersons(index + 1);
			resource.setSpecialEquipment("Equipment" + index);
			resources.add(resource);
		}
		data.getForm204().setResourcesAssigned(resources);
		Path file = output("resources");
		new Ics204PdfRenderer().render(data, file);
		try (PDDocument pdf = Loader.loadPDF(file.toFile())) {
			assertTrue(pdf.getNumberOfPages() >= 3);
			String all = new BoundsStripper().getText(pdf);
			for (int index = 0; index < resources.size(); index++) {
				assertEquals(1, occurrences(all, String.format("RESOURCE%03d", index)));
				assertEquals(1L, Pattern.compile("\\bEquipment" + index + "\\b").matcher(all).results().count());
			}
			String first = pageText(pdf, 1);
			assertTrue(first.contains("RESOURCE008"));
			assertFalse(first.contains("RESOURCE009"));
			for (int page = 2; page <= pdf.getNumberOfPages(); page++) {
				String text = pageText(pdf, page);
				assertTrue(text.contains("Assignment List (ICS 204) Page " + page));
				assertTrue(text.contains("5. Resources Assigned"));
				assertTrue(text.contains("Resource Identifier"));
				assertTrue(text.contains("Leader"));
				assertFalse(text.contains("Resource Identifier:"));
			}
		}
	}

	@Test
	void longResourceRowMovesWholeToFreshTablePageWithItsIdentifierAndLeader() throws Exception {
		AppData data = new AppData();
		ResourceAssignment longRow = new ResourceAssignment();
		longRow.setResourceIdentifier("WHOLE-RESOURCE");
		longRow.setLeader("Whole Row Leader");
		List<String> location = new ArrayList<>();
		for (int index = 0; index < 70; index++) {
			location.add(String.format("site%03d", index));
		}
		longRow.setReportingLocation(String.join(" ", location));
		longRow.setSpecialEquipment("Unique trailing equipment");
		ResourceAssignment following = new ResourceAssignment();
		following.setResourceIdentifier("FOLLOWING-RESOURCE");
		data.getForm204().setResourcesAssigned(List.of(longRow, following));
		Path file = output("whole-resource-row");
		new Ics204PdfRenderer().render(data, file);
		try (PDDocument pdf = Loader.loadPDF(file.toFile())) {
			assertEquals(2, pdf.getNumberOfPages());
			String first = pageText(pdf, 1);
			String second = pageText(pdf, 2).replaceAll("\\s+", " ");
			assertFalse(first.contains("WHOLE-RESOURCE"));
			assertFalse(first.contains("FOLLOWING-RESOURCE"));
			assertTrue(second.contains("WHOLE-RESOURCE"));
			assertTrue(second.contains("Whole Row Leader"));
			assertTrue(second.contains(String.join(" ", location)));
			assertTrue(second.contains("Unique trailing equipment"));
			assertTrue(second.replaceAll("\\s+", "").contains("FOLLOWING-RESOURCE"));
			String all = new BoundsStripper().getText(pdf);
			assertEquals(1, occurrences(all, "WHOLE-RESOURCE"));
			for (String token : location) {
				assertEquals(1, occurrences(all, token));
			}
		}
	}

	@Test
	void longAssignmentContextFitsItsCellWithoutDiscardedContinuation() throws Exception {
		AppData data = new AppData();
		String division = "W".repeat(40);
		data.getForm204().setDivision(division);
		data.getForm204().setManagementContext("division");
		Path file = output("long-division");
		new Ics204PdfRenderer().render(data, file);
		try (PDDocument pdf = Loader.loadPDF(file.toFile())) {
			assertEquals(1, pdf.getNumberOfPages());
			String text = new BoundsStripper().getText(pdf);
			assertTrue(text.contains("3. Division"));
			assertTrue(text.replaceAll("\\s+", "").contains(division));
			assertFalse(text.contains("See next page"));
		}
	}

	@Test
	void longDirectoryCellsPreserveEveryTokenAcrossNumberedCommunicationsPages() throws Exception {
		AppData data = new AppData();
		TCard card = new TCard();
		card.setCardType(TCardType.PERSONNEL);
		card.setPersonName("Directory Person");
		List<String> tokens = new ArrayList<>();
		for (int index = 0; index < 1400; index++) {
			tokens.add(String.format("radio%04d", index));
		}
		card.setRadioChannel(String.join(" ", tokens));
		data.setTCards(List.of(card));
		Path file = output("directory");
		new Ics205aPdfRenderer().render(data, file);
		try (PDDocument pdf = Loader.loadPDF(file.toFile())) {
			assertTrue(pdf.getNumberOfPages() > 2);
			assertEquals(pdf.getNumberOfPages(), Ics205aPdfRenderer.pageCount(data));
			String all = new BoundsStripper().getText(pdf);
			for (String token : tokens) {
				assertEquals(1, occurrences(all, token));
			}
			assertTrue(pageText(pdf, 2).contains("Communications List (ICS 205A) Page 2"));
		}
	}

	@Test
	void workAssignmentsRetainSharedAndIndividualTextAndKeepOrdinaryEntriesTogether() throws Exception {
		AppData data = new AppData();
		data.getForm204().setSharedWorkAssignment("Shared safety briefing for all resources.");
		List<ResourceAssignment> resources = new ArrayList<>();
		for (int index = 0; index < 30; index++) {
			ResourceAssignment resource = new ResourceAssignment();
			resource.setResourceIdentifier("Crew" + index);
			resource.setAssignment("WORKSTART" + index + " "
					+ "Follow the assigned drainage and report findings to the team leader. ".repeat(7)
					+ "WORKEND" + index);
			resources.add(resource);
		}
		data.getForm204().setResourcesAssigned(resources);
		Path file = output("work-assignments");
		new Ics204PdfRenderer().render(data, file);
		try (PDDocument pdf = Loader.loadPDF(file.toFile())) {
			String all = new BoundsStripper().getText(pdf).replaceAll("\\s+", " ");
			assertEquals(1, occurrences(all, "Shared safety briefing for all resources."));
			for (int index = 0; index < resources.size(); index++) {
				assertTrue(all.contains(resources.get(index).getAssignment()));
				boolean together = false;
				for (int page = 1; page <= pdf.getNumberOfPages(); page++) {
					String text = pageText(pdf, page);
					if (text.contains("WORKSTART" + index + " ")) {
						assertTrue(text.contains("WORKEND" + index + "\n") || text.contains("WORKEND" + index + " "));
						together = true;
					}
				}
				assertTrue(together);
			}
		}
	}

	@Test
	void genericBlocksPaginateAtMeasuredWidthOnA4WithConfiguredMargins() throws Exception {
		AppData data = new AppData();
		data.getPdfLayoutSettings().setPaperSize(PdfLayoutSettings.PaperSize.A4);
		data.getPdfLayoutSettings().setPageMarginPoints(72f);
		String text = "fullwidthtoken ".repeat(2600);
		try (PDDocument pdf = new PDDocument()) {
			new TestRenderer().writeDocument(pdf, data, "ICS 999", "Generic Form", List.of(List.of(text)));
			assertTrue(pdf.getNumberOfPages() > 3);
			String extracted = new PDFTextStripper().getText(pdf);
			assertEquals(2600, occurrences(extracted, "fullwidthtoken"));
			assertTrue(pageText(pdf, 2).contains("Generic Form (ICS 999) Page 2"));
			PDFTextStripper bounds = new PDFTextStripper() {
				/** {@inheritDoc} */
				@Override
				protected void processTextPosition(TextPosition position) {
					assertTrue(position.getXDirAdj() >= 71f);
					assertTrue(position.getXDirAdj() + position.getWidthDirAdj() <= 524f);
					assertTrue(position.getYDirAdj() <= 771f);
					super.processTextPosition(position);
				}
			};
			bounds.getText(pdf);
		}
	}

	/**
	 * Creates a unique persistent test output inside the build directory.
	 * @param label output label.
	 * @return PDF output path.
	 * @throws IOException when creating the directory fails.
	 */
	private Path output(String label) throws IOException {
		Path directory = Path.of("target", "test-output", "measured-layout");
		Files.createDirectories(directory);
		return directory.resolve(label.replace(' ', '-') + "-" + System.nanoTime() + ".pdf");
	}

	/**
	 * Extracts one PDF page.
	 * @param document source document.
	 * @param page one-based page number.
	 * @return page text.
	 * @throws IOException when extraction fails.
	 */
	private String pageText(PDDocument document, int page) throws IOException {
		PDFTextStripper stripper = new PDFTextStripper();
		stripper.setStartPage(page);
		stripper.setEndPage(page);
		return stripper.getText(document);
	}

	/**
	 * Counts nonoverlapping exact token occurrences.
	 * @param text source text.
	 * @param token sought token.
	 * @return number of occurrences.
	 */
	private int occurrences(String text, String token) {
		return (text.length() - text.replace(token, "").length()) / token.length();
	}

	/** Concrete access to stateless shared layout helpers. */
	private static final class TestRenderer extends AbstractPdfRenderer {
	}

	/** Captures glyph positions while checking the letter printable bounds. */
	private static final class BoundsStripper extends PDFTextStripper {
		private final List<TextPosition> positions = new ArrayList<>();

		/**
		 * Creates a position-checking extractor.
		 * @throws IOException when the extractor cannot initialize.
		 */
		private BoundsStripper() throws IOException {
			super();
		}

		/** {@inheritDoc} */
		@Override
		protected void processTextPosition(TextPosition position) {
			positions.add(position);
			assertTrue(position.getXDirAdj() >= 35f, position.getUnicode());
			assertTrue(position.getXDirAdj() + position.getWidthDirAdj() <= 577f, position.getUnicode());
			assertTrue(position.getYDirAdj() <= 757f, position.getUnicode());
			super.processTextPosition(position);
		}
	}
}
