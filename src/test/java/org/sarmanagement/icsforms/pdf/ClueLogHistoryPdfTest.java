/*
 * Licensed under the GNU General Public License, version 3 or later.
 * See LICENSE for details.
 */
package org.sarmanagement.icsforms.pdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.ClueLogEntry;
import org.sarmanagement.icsforms.model.PocAssignment;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Protects the standard clue layout while exposing follow-up history. */
class ClueLogHistoryPdfTest {
	@TempDir
	Path output;

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
			String text = new PDFTextStripper().getText(document);
			assertTrue(text.contains("CLUE LOG"));
			assertTrue(text.contains("Boot print"));
			assertTrue(text.contains("Trail junction"));
			assertTrue(text.contains("Photographed"));
			assertTrue(text.contains("Compare with missing person's boots"));
			assertTrue(text.contains("Initial team"));
			assertTrue(text.contains("Follow-up team"));
		}
	}
}
