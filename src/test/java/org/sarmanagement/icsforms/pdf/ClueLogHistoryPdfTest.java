/*
 * Licensed under the GNU General Public License, version 3 or later.
 * See LICENSE for details.
 */
package org.sarmanagement.icsforms.pdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.ClueLogEntry;
import org.sarmanagement.icsforms.model.PocAssignment;
import org.sarmanagement.icsforms.model.PdfLayoutSettings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Protects the standard clue layout while exposing follow-up history. */
class ClueLogHistoryPdfTest {
	private final Path output = Path.of("target", "clue-history-pdf-tests");

	@Test
	void standardClueExportKeepsFieldsAndIncludesHistory() throws Exception {
		AppData data = new AppData();
		ClueLogEntry clue = new ClueLogEntry();
		clue.setDescription("Boot print");
		clue.setLocation("Trail junction");
		clue.setImmediateAction("Photographed");
		clue.setFollowUp("Compare with missing person's boots");
		clue.setFollowUpAssignments(List.of(
				new PocAssignment("Initial team", LocalDateTime.of(2026, 10, 10, 10, 0)),
				new PocAssignment("Follow-up team", LocalDateTime.of(2026, 10, 10, 11, 0))));
		clue.getFollowUpAssignments().add(null);
		data.setClueLogEntries(List.of(clue));
		Path pdf = output.resolve("clues.pdf");
		new ClueLogPdfRenderer().render(data, pdf);
		try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
			assertEquals(1, document.getNumberOfPages());
			String text = new PDFTextStripper().getText(document);
			assertTrue(text.contains("CLUE LOG"));
			assertTrue(text.contains("Boot print"));
			assertTrue(text.contains("Trail junction"));
			assertTrue(text.contains("Photographed"));
			assertTrue(text.contains("Compare with missing person's boots"));
			assertTrue(text.contains("Initial team"));
			assertTrue(text.contains("Follow-up team"));
		} finally {
			Files.deleteIfExists(pdf);
		}
	}

	@Test
	void oversizedDescriptionAndHistoryStayVisibleAcrossLetterAndA4Pages() throws Exception {
		for (PdfLayoutSettings.PaperSize paper : PdfLayoutSettings.PaperSize.values()) {
			AppData data = new AppData();
			data.getPdfLayoutSettings().setPaperSize(paper);
			ClueLogEntry clue = new ClueLogEntry();
			StringBuilder description = new StringBuilder();
			for (int index = 0; index < 700; index++) {
				description.append("DescriptionSentinel").append(index).append(' ');
			}
			clue.setDescription(description + "\n" + "W".repeat(1200) + "\nFINAL_DESCRIPTION_SENTINEL");
			clue.setLocation("Trail junction");
			clue.setDetectingTask("Alpha");
			clue.setImmediateAction("Photographed");
			clue.setFollowUp("Compare boots");
			List<PocAssignment> assignments = new ArrayList<>();
			for (int index = 0; index < 60; index++) {
				assignments.add(new PocAssignment("HistorySentinel" + index,
						LocalDateTime.of(2026, 10, 10, 10, 0).plusMinutes(index)));
			}
			clue.setFollowUpAssignments(assignments);
			data.setClueLogEntries(List.of(clue));
			Path pdf = output.resolve("long-" + paper + ".pdf");
			new ClueLogPdfRenderer().render(data, pdf);
			try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
				assertTrue(document.getNumberOfPages() > 2);
				String text = new VisibleTextStripper().getText(document);
				String compact = text.replaceAll("\\s+", "");
				for (int index = 0; index < 700; index++) {
					assertTrue(compact.contains("DescriptionSentinel" + index));
				}
				assertTrue(compact.contains("W".repeat(1200)));
				assertTrue(text.contains("FINAL_DESCRIPTION_SENTINEL"));
				for (int index = 0; index < 60; index++) {
					assertTrue(text.contains("HistorySentinel" + index));
				}
				assertTrue(text.contains("Clue #1 (continued)"));
				assertTrue(text.contains("Trail junction"));
				assertTrue(text.contains("Photographed"));
				assertTrue(text.contains("Compare boots"));
				assertTrue(text.contains("Detecting Task:"));
				assertTrue(text.contains("Date/Time:"));
				assertTrue(text.indexOf("HistorySentinel0") < text.indexOf("HistorySentinel59"));
			} finally {
				Files.deleteIfExists(pdf);
			}
		}
	}

	/** Validates that extracted glyphs are also within the visible page bounds. */
	private static final class VisibleTextStripper extends PDFTextStripper {
		/**
		 * Creates an extractor that validates rendered glyph bounds.
		 *
		 * @throws IOException if text extraction initialization fails
		 */
		private VisibleTextStripper() throws IOException {
			super();
		}

		/** {@inheritDoc} */
		@Override
		protected void processTextPosition(TextPosition position) {
			assertTrue(position.getXDirAdj() >= 50f);
			assertTrue(position.getXDirAdj() + position.getWidthDirAdj() <= position.getPageWidth() - 50f + 0.1f);
			assertTrue(position.getYDirAdj() >= 50f);
			assertTrue(position.getYDirAdj() <= position.getPageHeight() - 50f);
			super.processTextPosition(position);
		}
	}
}
