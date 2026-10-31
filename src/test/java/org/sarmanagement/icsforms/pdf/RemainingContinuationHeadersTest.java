/*
 * Copyright (C) 2026 SAR Management contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.pdf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSNumber;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.model.ActivityLogEntry;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.ClueLogEntry;
import org.sarmanagement.icsforms.model.Ics208Form;
import org.sarmanagement.icsforms.model.Ics214Form;
import org.sarmanagement.icsforms.model.OpenActionItem;
import org.sarmanagement.icsforms.model.RadioChannel;
import org.sarmanagement.icsforms.model.SafetyAnalysisEntry;

/** Verifies remaining overflow titles and the enclosure of repeated numbered blocks. */
class RemainingContinuationHeadersTest {
	@Test
	void activityAndRadioPagesKeepTheirCanonicalTitles() throws Exception {
		AppData activity = new AppData();
		Ics214Form log = new Ics214Form();
		for (int index = 0; index < 40; index++) {
			ActivityLogEntry entry = new ActivityLogEntry();
			entry.setNotableActivity("Activity " + index);
			log.getActivityLog().add(entry);
		}
		activity.getActivityLogs().add(log);
		assertOverflow(new Ics214PdfRenderer(), activity, "ACTIVITY LOG (ICS 214)",
				"ACTIVITY LOG (ICS 214)", "7. Activity Log");

		AppData radio = new AppData();
		RadioChannel channel = new RadioChannel();
		channel.setChannelName("Search");
		channel.setRemarks("Radio continuation\n".repeat(150));
		radio.getForm205().getChannels().add(channel);
		assertOverflow(new Ics205PdfRenderer(), radio, "INCIDENT RADIO COMMUNICATIONS PLAN (ICS 205)",
				"INCIDENT RADIO COMMUNICATIONS PLAN (ICS 205)", "4. Basic Radio Channel Use");
	}

	@Test
	void safetyPagesPreserveBoth208TitlesAndRepeatNumberedBlocksInsideTheirFrame() throws Exception {
		AppData analysis = new AppData();
		SafetyAnalysisEntry entry = new SafetyAnalysisEntry();
		entry.setMitigations("Safety mitigation\n".repeat(150));
		analysis.getForm215a().setEntries(List.of(entry));
		assertOverflow(new Ics215aPdfRenderer(), analysis, "INCIDENT ACTION PLAN SAFETY ANALYSIS (ICS 215A)",
				"INCIDENT ACTION PLAN SAFETY ANALYSIS (ICS 215A)", "5. Incident Area");

		for (Ics208Form.MessageType type : Ics208Form.MessageType.values()) {
			AppData safety = new AppData();
			safety.getForm208().setMessageType(type);
			safety.getForm208().setMessageMode(Ics208Form.MessageMode.FREE_TEXT);
			safety.getForm208().setFreeText("Safety continuation\n".repeat(150));
			String title = type == Ics208Form.MessageType.HAZARDOUS_MATERIALS_SITE_SAFETY_PLAN
					? "SITE SAFETY AND CONTROL PLAN (ICS 208)" : "SAFETY MESSAGE/PLAN (ICS 208)";
			assertOverflow(new Ics208PdfRenderer(), safety, title, title, "3. Safety Message");
		}
	}

	@Test
	void clueAndTrackerPagesKeepTheirFormIdentityOutsideTheNumberedGrid() throws Exception {
		AppData clues = new AppData();
		ClueLogEntry clue = new ClueLogEntry();
		clue.setDescription("Clue continuation\n".repeat(150));
		clues.setClueLogEntries(List.of(clue));
		assertOverflow(new ClueLogPdfRenderer(), clues, "SAR CLUE LOG", "CLUE LOG (SAR)", "Clue #1");
		assertOverflow(new ClueLogIcs233PdfRenderer(), clues,
				"ICS 233-CG INCIDENT OPEN ACTION TRACKER Adapted as a Clue Log",
				"INCIDENT OPEN ACTION TRACKER Adapted as a Clue Log (ICS 233-CG)", "2. No.");

		AppData actions = new AppData();
		OpenActionItem action = new OpenActionItem();
		action.setNumber(1);
		action.setItem("Action continuation\n".repeat(150));
		actions.setOpenActionItems(List.of(action));
		assertOverflow(new Ics233CgPdfRenderer(), actions, "ICS 233-CG OPEN ACTION TRACKER",
				"OPEN ACTION TRACKER (ICS 233-CG)", "2. No.");
	}

	/**
	 * Checks every local continuation header and its separation from bordered content.
	 *
	 * @param renderer renderer under test
	 * @param data overflowing fixture
	 * @param primaryTitle unchanged first-page title
	 * @param continuationTitle canonical continuation title before its page suffix
	 * @param blockLabel repeated numbered content label
	 * @throws Exception when rendering, parsing, or deleting the PDF fails
	 */
	private void assertOverflow(PdfFormRenderer renderer, AppData data, String primaryTitle, String continuationTitle,
			String blockLabel) throws Exception {
		Path directory = Path.of("target", "test-output", "remaining-continuation-headers");
		Files.createDirectories(directory);
		Path file = directory.resolve(UUID.randomUUID() + ".pdf");
		try {
			renderer.render(data, file);
			try (PDDocument pdf = Loader.loadPDF(file.toFile())) {
				assertTrue(pdf.getNumberOfPages() > 1, renderer.getFormKey());
				for (int page = 1; page <= pdf.getNumberOfPages(); page++) {
					String title = page == 1 ? primaryTitle : continuationTitle + " Page " + page;
					PositionedText text = new PositionedText();
					text.setStartPage(page);
					text.setEndPage(page);
					String extracted = text.getText(pdf).strip();
					assertTrue(extracted.startsWith(title), extracted);
					assertFalse(extracted.contains("(CONTINUED)"), extracted);
					List<Rectangle2D.Float> rectangles = rectangles(pdf.getPage(page - 1));
					List<TextPosition> heading = text.lines.get(0);
					float highestBorder = (float) rectangles.stream().mapToDouble(Rectangle2D::getMaxY).max()
							.orElseThrow();
					float pageHeight = pdf.getPage(page - 1).getMediaBox().getHeight();
					for (TextPosition glyph : heading) {
						assertTrue(pageHeight - glyph.getYDirAdj() > highestBorder, title);
						assertTrue(glyph.getXDirAdj() >= 0, title);
						assertTrue(glyph.getXDirAdj() + glyph.getWidthDirAdj()
								<= pdf.getPage(page - 1).getMediaBox().getWidth() + .1f, title);
					}
					int labelIndex = -1;
					for (int index = 0; index < text.values.size(); index++) {
						if (text.values.get(index).startsWith(blockLabel)) {
							labelIndex = index;
							break;
						}
					}
					assertTrue(labelIndex >= 0, blockLabel + ": " + extracted);
					List<TextPosition> label = text.lines.get(labelIndex);
					for (TextPosition glyph : label) {
						float x = glyph.getXDirAdj();
						float y = pageHeight - glyph.getYDirAdj();
						assertTrue(rectangles.stream().anyMatch(rectangle ->
								rectangle.contains(x, y)
										&& rectangle.contains(x + glyph.getWidthDirAdj(), y)), blockLabel);
					}
					assertEquals(page == 1, !text.values.get(0).contains(" Page "), title);
				}
			}
		} finally {
			Files.deleteIfExists(file);
		}
	}

	/**
	 * Extracts unrotated ruled cells in PDF coordinates.
	 *
	 * @param page source PDF page
	 * @return bordered rectangles
	 * @throws IOException when the content stream cannot be parsed
	 */
	private List<Rectangle2D.Float> rectangles(PDPage page) throws IOException {
		List<Object> tokens = new PDFStreamParser(page).parse();
		List<Rectangle2D.Float> rectangles = new ArrayList<>();
		for (int index = 4; index < tokens.size(); index++) {
			if (tokens.get(index) instanceof Operator operator && "re".equals(operator.getName())) {
				float x = ((COSNumber) tokens.get(index - 4)).floatValue();
				float y = ((COSNumber) tokens.get(index - 3)).floatValue();
				float width = ((COSNumber) tokens.get(index - 2)).floatValue();
				float height = ((COSNumber) tokens.get(index - 1)).floatValue();
				rectangles.add(new Rectangle2D.Float(x, y, width, height));
			}
		}
		return rectangles;
	}

	/** Captures extracted strings together with their glyph coordinates. */
	private static final class PositionedText extends PDFTextStripper {
		private final List<String> values = new ArrayList<>();
		private final List<List<TextPosition>> lines = new ArrayList<>();

		/**
		 * Creates a position-capturing text stripper.
		 *
		 * @throws IOException when text extraction cannot be initialized
		 */
		private PositionedText() throws IOException {
			super();
		}

		/** {@inheritDoc} */
		@Override
		protected void writeString(String text, List<TextPosition> positions) throws IOException {
			values.add(text);
			lines.add(List.copyOf(positions));
			super.writeString(text, positions);
		}
	}
}
