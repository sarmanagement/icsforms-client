/*
 * Copyright (C) 2026 SAR Management contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.pdf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.awt.geom.Rectangle2D;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.PDFTextStripperByArea;
import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.ClueLogEntry;
import org.sarmanagement.icsforms.model.OpenActionItem;
import org.sarmanagement.icsforms.model.PdfLayoutSettings;
import org.sarmanagement.icsforms.model.PocAssignment;

/**
 * Extraction and page-geometry regression tests for both ICS 233-CG exports.
 */
class Ics233PdfRendererTest {
	@Test
	void regularExportsAllActionsWithCurrentPocAndDates() throws Exception {
		AppData data = new AppData();
		data.getIncidentContext().setIncidentName("Mountain Search");
		OpenActionItem first = action(4, "Request aircraft");
		first.setPoc("Current Coordinator");
		first.setBriefedToPoc(true);
		first.setStartDate(LocalDateTime.of(2026, 10, 10, 8, 0));
		first.setTargetDate(LocalDateTime.of(2026, 10, 11, 9, 0));
		first.setActualDate(LocalDateTime.of(2026, 10, 12, 10, 0));
		first.setStatus("Completed");
		data.setOpenActionItems(List.of(first, action(9, "Arrange transport")));

		PdfResult pdf = render(new Ics233CgPdfRenderer(), data);
		String text = normalized(pdf.text());
		assertTrue(text.contains("ICS 233-CG OPEN ACTION TRACKER"));
		assertTrue(text.contains("Mountain Search"));
		assertTrue(text.contains("Request aircraft"));
		assertTrue(text.contains("Arrange transport"));
		assertTrue(text.contains("Current Coordinator"));
		assertTrue(text.contains("Completed"));
		assertTrue(text.contains("X"));
		assertTrue(text.contains("2026-10-10"));
		assertTrue(text.contains("2026-10-11"));
		assertTrue(text.contains("2026-10-12"));
		assertTrue(text.contains("ICS 233-CG Rev1/07"));
		assertTrue(text.contains("Page 1 of 1"));
		assertEquals("ICS 233-CG", new Ics233CgPdfRenderer().getFormKey());
	}

	@Test
	void adaptedSortIsStableAndPreservesOriginalClueNumbers() throws Exception {
		AppData data = new AppData();
		ClueLogEntry completed = clue("Finished clue");
		completed.setFollowUpStatus("Completed");
		ClueLogEntry assigned = clue("Assigned clue");
		assigned.setFollowUpAssignments(List.of(assignment("Coordinator", null)));
		ClueLogEntry open = clue("Open clue");
		ClueLogEntry secondOpen = clue("Second open clue");
		ClueLogEntry followedUp = clue("Followed clue");
		followedUp.setFollowUpCompletedDate(LocalDateTime.of(2026, 10, 10, 9, 0));
		ClueLogEntry statusFollowed = clue("Status followed clue");
		statusFollowed.setFollowUpStatus("Followed up on");
		data.setClueLogEntries(List.of(completed, assigned, open, secondOpen, followedUp, statusFollowed));

		String text = normalized(render(new ClueLogIcs233PdfRenderer(), data).text());
		assertTrue(text.contains("ICS 233-CG INCIDENT OPEN ACTION TRACKER Adapted as a Clue Log"));
		assertTrue(text.indexOf("Open clue") < text.indexOf("Second open clue"));
		assertTrue(text.indexOf("Second open clue") < text.indexOf("Assigned clue"));
		assertTrue(text.indexOf("Assigned clue") < text.indexOf("Finished clue"));
		assertTrue(text.indexOf("Finished clue") < text.indexOf("Followed clue"));
		assertTrue(text.indexOf("Followed clue") < text.indexOf("Status followed clue"));
		assertTrue(text.contains("3 Description: Open clue"));
		assertTrue(text.contains("4 Description: Second open clue"));
		assertTrue(text.contains("2 Description: Assigned clue"));
		assertTrue(text.contains("1 Description: Finished clue"));
		assertEquals("Clue Log (ICS 233-CG adapted)", new ClueLogIcs233PdfRenderer().getFormKey());
	}

	@Test
	void clearedAssignmentIsOpenButItsHistoryRemainsVisible() throws Exception {
		AppData data = new AppData();
		ClueLogEntry assigned = clue("Currently assigned");
		assigned.setFollowUpAssignments(List.of(assignment("Active Coordinator", null)));
		ClueLogEntry cleared = clue("Assignment cleared");
		cleared.setFollowUpAssignments(List.of(assignment("Previous Coordinator", null), assignment("", null)));
		data.setClueLogEntries(List.of(assigned, cleared));
		String text = normalized(render(new ClueLogIcs233PdfRenderer(), data).text());
		assertTrue(text.indexOf("Assignment cleared") < text.indexOf("Currently assigned"));
		assertTrue(text.contains("Previous Coordinator"));
		assertTrue(text.contains("Active Coordinator"));
	}

	@Test
	void adaptedItemHasSingleOrderedColumnAndIncludesCompletePocHistory() throws Exception {
		AppData data = new AppData();
		ClueLogEntry clue = clue("Blue jacket");
		clue.setLocation("Riverbank");
		clue.setDetectedBy("Pat Searcher");
		clue.setDetectingTask("Alpha Team");
		clue.setImmediateAction("Photographed");
		clue.setFollowUp("Check identity");
		clue.setPossibleDuplicate(true);
		clue.setFollowUpStatus("Assigned");
		clue.setFollowUpBriefed(true);
		PocAssignment first = assignment("Initial POC", LocalDateTime.of(2026, 10, 9, 7, 0));
		PocAssignment second = assignment("Current POC", LocalDateTime.of(2026, 10, 10, 8, 0));
		clue.setFollowUpAssignments(List.of(first, second));
		ClueLogEntry legacy = new LegacyClue();
		legacy.setDescription("Legacy clue");
		data.setClueLogEntries(List.of(clue, legacy));

		String text = normalized(render(new ClueLogIcs233PdfRenderer(), data).text());
		assertTrue(text.indexOf("Description: Blue jacket") < text.indexOf("Location: Riverbank"));
		assertTrue(text.indexOf("Location: Riverbank") < text.indexOf("Found by: Pat Searcher"));
		assertTrue(text.contains("Task: Alpha Team"));
		assertTrue(text.contains("Initial POC 2026-10-09"));
		assertTrue(text.contains("Current POC 2026-10-10"));
		assertTrue(text.contains("Legacy POC"));
		assertTrue(text.contains("Photographed"));
		assertTrue(text.contains("Check identity"));
		assertTrue(text.contains("Possible duplicate"));
		assertTrue(text.contains("Status: Assigned"));
	}

	@Test
	void unsupportedGlyphsAreReversiblyEscapedAndImpossibleMarginsFailClearly() throws Exception {
		AppData data = new AppData();
		data.setOpenActionItems(List.of(action(1, "Clue \u96ea \uD83D\uDD0E")));
		PdfResult pdf = render(new Ics233CgPdfRenderer(), data);
		String item = pdf.cellTexts().get(1).replaceAll("\\s+", "");
		assertTrue(item.contains("\\u{96EA}"));
		assertTrue(item.contains("\\u{1F50E}"));
		data.getPdfLayoutSettings().setPageMarginPoints(400f);
		java.io.IOException exception = assertThrows(java.io.IOException.class,
				() -> render(new Ics233CgPdfRenderer(), data));
		assertTrue(exception.getMessage().contains("margins"));
	}

	@Test
	void regularLongCellsSplitAcrossPagesWithoutLosingAnyText() throws Exception {
		AppData data = new AppData();
		String words = longText("Action");
		String unbroken = "Z".repeat(9000);
		OpenActionItem item = action(17, words + "\n" + unbroken + "\nFINAL_ACTION_SENTINEL");
		item.setPoc(longText("Coordinator"));
		item.setStatus(longText("Status"));
		data.setOpenActionItems(List.of(item));
		PdfResult pdf = render(new Ics233CgPdfRenderer(), data);
		assertTrue(pdf.pageTexts().size() > 2);
		String itemText = pdf.cellTexts().get(1).replaceAll("\\s+", "");
		assertTrue(itemText.contains(words.replaceAll("\\s+", "")));
		assertTrue(itemText.contains(unbroken));
		assertTrue(pdf.cellTexts().get(2).replaceAll("\\s+", "")
				.contains(longText("Coordinator").replaceAll("\\s+", "")));
		assertTrue(pdf.cellTexts().get(5).replaceAll("\\s+", "")
				.contains(longText("Status").replaceAll("\\s+", "")));
		assertTrue(itemText.contains("FINAL_ACTION_SENTINEL"));
		for (String page : pdf.pageTexts()) {
			assertTrue(normalized(page).contains("17"));
		}
		assertRepeatedHeaders(pdf);
	}

	@Test
	void adaptedLongDescriptionAndAssignmentHistoryArePreserved() throws Exception {
		AppData data = new AppData();
		data.setIcs233Portrait(true);
		ClueLogEntry clue = clue(longText("Clue"));
		clue.setLocation(longText("Location"));
		clue.setFollowUp(longText("Followup"));
		List<PocAssignment> history = new ArrayList<>();
		for (int index = 0; index < 100; index++) {
			history.add(assignment("Assignee" + index, LocalDateTime.of(2026, 10, 10, 8, 0).plusMinutes(index)));
		}
		clue.setFollowUpAssignments(history);
		data.setClueLogEntries(List.of(clue));
		PdfResult pdf = render(new ClueLogIcs233PdfRenderer(), data);
		assertTrue(pdf.pageTexts().size() > 2);
		String compact = pdf.cellTexts().get(1).replaceAll("\\s+", "");
		assertTrue(compact.contains(longText("Clue").replaceAll("\\s+", "")));
		assertTrue(compact.contains(longText("Location").replaceAll("\\s+", "")));
		assertTrue(pdf.cellTexts().get(5).replaceAll("\\s+", "")
				.contains(longText("Followup").replaceAll("\\s+", "")));
		String pocs = pdf.cellTexts().get(2).replaceAll("\\s+", "");
		for (int index = 0; index < 100; index++) {
			assertTrue(pocs.contains("Assignee" + index));
		}
		for (String page : pdf.pageTexts()) {
			assertTrue(normalized(page).contains("ICS 233-CG INCIDENT OPEN ACTION TRACKER Adapted as a Clue Log"));
		}
		assertRepeatedHeaders(pdf);
	}

	@Test
	void blankFormsUseGridInBothOrientationsAndConfiguredPaperAndMargins() throws Exception {
		for (PdfFormRenderer renderer : List.of(new Ics233CgPdfRenderer(), new ClueLogIcs233PdfRenderer())) {
			for (PdfLayoutSettings.PaperSize paper : PdfLayoutSettings.PaperSize.values()) {
				for (boolean portrait : List.of(false, true)) {
					AppData data = new AppData();
					data.setIcs233Portrait(portrait);
					data.getPdfLayoutSettings().setPaperSize(paper);
					data.getPdfLayoutSettings().setPageMarginPoints(54f);
					PdfResult pdf = render(renderer, data);
					PDRectangle expected = paper == PdfLayoutSettings.PaperSize.A4 ? PDRectangle.A4 : PDRectangle.LETTER;
					assertEquals(portrait ? expected.getWidth() : expected.getHeight(), pdf.width(), 0.1f);
					assertEquals(portrait ? expected.getHeight() : expected.getWidth(), pdf.height(), 0.1f);
					assertEquals(1, pdf.pageTexts().size());
					assertTrue(pdf.rectangles() > 40);
					assertTrue(pdf.firstPageStream().contains("54 "));
					assertFalse(pdf.text().contains("No clues"));
					assertRepeatedHeaders(pdf);
				}
			}
		}
		assertEquals(1, render(new Ics233CgPdfRenderer(), null).pageTexts().size());
		assertEquals(1, render(new ClueLogIcs233PdfRenderer(), null).pageTexts().size());
	}

	/**
	 * Builds an action fixture using the persisted public API.
	 *
	 * @param number action number
	 * @param item action description
	 * @return configured action
	 */
	private OpenActionItem action(int number, String item) {
		OpenActionItem action = new OpenActionItem();
		action.setNumber(number);
		action.setItem(item);
		return action;
	}

	/**
	 * Builds a clue fixture without a follow-up assignment.
	 *
	 * @param description clue description
	 * @return configured clue
	 */
	private ClueLogEntry clue(String description) {
		ClueLogEntry clue = new ClueLogEntry();
		clue.setDescription(description);
		return clue;
	}

	/**
	 * Builds a POC assignment fixture.
	 *
	 * @param assignee POC name
	 * @param assignedAt stored assignment time
	 * @return configured assignment
	 */
	private PocAssignment assignment(String assignee, LocalDateTime assignedAt) {
		PocAssignment assignment = new PocAssignment();
		assignment.setAssignee(assignee);
		assignment.setAssignedAt(assignedAt);
		return assignment;
	}

	/**
	 * Creates uniquely numbered tokens that expose any omitted continuation.
	 *
	 * @param prefix token category
	 * @return large multi-page text
	 */
	private String longText(String prefix) {
		StringBuilder text = new StringBuilder();
		for (int index = 0; index < 600; index++) {
			text.append(prefix).append(index).append(' ');
		}
		return text.toString();
	}

	/**
	 * Verifies that every page repeats its numbered blocks and final page count.
	 *
	 * @param pdf extracted document
	 */
	private void assertRepeatedHeaders(PdfResult pdf) {
		for (int index = 0; index < pdf.pageTexts().size(); index++) {
			String text = normalized(pdf.pageTexts().get(index));
			assertTrue(text.contains("1. Incident name:"));
			assertTrue(text.contains("2. No."));
			assertTrue(text.contains("3. Item"));
			assertTrue(text.contains("4. For/POC"));
			assertTrue(text.contains("5. Briefed"));
			assertTrue(text.contains("6. Start"));
			assertTrue(text.contains("7. Status"));
			assertTrue(text.contains("8. Target"));
			assertTrue(text.contains("9. Actual"));
			assertTrue(text.contains("Page " + (index + 1) + " of " + pdf.pageTexts().size()));
			assertTrue(text.contains("ICS 233-CG Rev1/07"));
		}
	}

	/**
	 * Normalizes extraction line breaks for phrase assertions.
	 *
	 * @param text extracted text
	 * @return single-spaced text
	 */
	private String normalized(String text) {
		return text.replaceAll("\\s+", " ");
	}

	/**
	 * Renders inside the project build directory, extracts text, and deletes output.
	 *
	 * @param renderer renderer under test
	 * @param data source document
	 * @return extracted text and geometry
	 * @throws Exception if rendering, extraction, or cleanup fails
	 */
	private PdfResult render(PdfFormRenderer renderer, AppData data) throws Exception {
		Path output = Path.of("target", "ics233-renderer-tests", UUID.randomUUID() + ".pdf");
		try {
			renderer.render(data, output);
			try (PDDocument document = Loader.loadPDF(output.toFile())) {
				List<String> pageTexts = new ArrayList<>();
				List<StringBuilder> cells = new ArrayList<>();
				for (int column = 0; column < 8; column++) {
					cells.add(new StringBuilder());
				}
				float margin = data == null ? 36f : Math.max(36f, data.getPdfLayoutSettings().getPageMarginPoints());
				float[] fractions = {0.05f, 0.285f, 0.17f, 0.075f, 0.105f, 0.105f, 0.105f, 0.105f};
				String[] headings = {"2.No.", "3.Item", "4.For/POC", "5.Briefed", "6.Start",
						"7.Status", "8.Target", "9.Actual"};
				for (int index = 0; index < document.getNumberOfPages(); index++) {
					PDFTextStripper stripper = new PDFTextStripper();
					stripper.setStartPage(index + 1);
					stripper.setEndPage(index + 1);
					pageTexts.add(stripper.getText(document));
					PDPage page = document.getPage(index);
					float x = margin;
					float width = page.getMediaBox().getWidth() - 2f * margin;
					PDFTextStripperByArea areas = new PDFTextStripperByArea();
					for (int column = 0; column < 8; column++) {
						float cellWidth = width * fractions[column];
						areas.addRegion("cell" + column, new Rectangle2D.Float(x, margin + 43f,
								cellWidth, page.getMediaBox().getHeight() - 2f * margin - 63f));
						x += cellWidth;
					}
					areas.extractRegions(page);
					for (int column = 0; column < 8; column++) {
						String cell = areas.getTextForRegion("cell" + column).replaceAll("\\s+", "");
						cells.get(column).append(cell.replaceFirst(java.util.regex.Pattern.quote(headings[column]), ""));
					}
				}
				PDPage first = document.getPage(0);
				String content;
				try (var stream = first.getContents()) {
					content = new String(stream.readAllBytes(), StandardCharsets.ISO_8859_1);
				}
				int rectangles = content.split(" re\\b", -1).length - 1;
				return new PdfResult(String.join("\n", pageTexts), pageTexts,
						first.getMediaBox().getWidth(), first.getMediaBox().getHeight(), rectangles, content,
						cells.stream().map(StringBuilder::toString).toList());
			}
		} finally {
			Files.deleteIfExists(output);
		}
	}

	/**
	 * Extracted PDF assertions without retaining document handles or output files.
	 *
	 * @param text whole document text
	 * @param pageTexts text by page
	 * @param width page width
	 * @param height page height
	 * @param rectangles first-page grid rectangle count
	 * @param firstPageStream decoded first-page operators
	 * @param cellTexts column text combined across continuations without headers
	 */
	private record PdfResult(String text, List<String> pageTexts, float width, float height,
			int rectangles, String firstPageStream, List<String> cellTexts) {
	}

	/** Exercises a current-only clue source with no assignment history. */
	private static final class LegacyClue extends ClueLogEntry {
		/** {@inheritDoc} */
		@Override
		public String getFollowUpAssignedTo() {
			return "Legacy POC";
		}
	}
}
