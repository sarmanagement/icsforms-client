/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.pdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.ClueLogEntry;
import org.sarmanagement.icsforms.model.OpenActionItem;
import org.sarmanagement.icsforms.model.PdfLayoutSettings;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Checks explicit tracker and adapted-clue orientations without mutable renderer state. */
class PdfOrientationExportTest {
	@TempDir
	Path directory;

	@Test
	void trackerAndAdaptedCluesSupportAllOrientationsAndPaperSizes() throws Exception {
		AppData data = new AppData();
		data.setIcs233Portrait(true);
		OpenActionItem action = new OpenActionItem();
		action.setNumber(17);
		action.setItem("Action ".repeat(500) + "ActionTail");
		data.getOpenActionItems().add(action);
		ClueLogEntry clue = new ClueLogEntry();
		clue.setDescription("Clue ".repeat(500) + "ClueTail");
		data.getClueLogEntries().add(clue);
		PdfExportService service = new PdfExportService(new Ics233CgPdfRenderer(), new ClueLogIcs233PdfRenderer());
		List<String> keys = service.formKeys();
		for (PdfLayoutSettings.PaperSize paper : PdfLayoutSettings.PaperSize.values()) {
			data.getPdfLayoutSettings().setPaperSize(paper);
			data.getPdfLayoutSettings().setPageMarginPoints(54);
			for (PdfOrientation orientation : PdfOrientation.values()) {
				var exported = service.exportForms(keys, data, directory, PdfOrientation.LANDSCAPE, orientation);
				for (String key : keys) {
					try (PDDocument pdf = Loader.loadPDF(exported.get(key).toFile())) {
						String text = new PDFTextStripper().getText(pdf).replaceAll("\\s", "");
						assertTrue(text.contains(key.equals("ICS 233-CG") ? "ActionTail" : "ClueTail"));
						assertTrue(pdf.getNumberOfPages() > 1);
						PDRectangle portrait = paper == PdfLayoutSettings.PaperSize.A4
								? PDRectangle.A4 : PDRectangle.LETTER;
						assertGeometryAndDirections(pdf, portrait, orientation, 54);
					}
				}
				assertTrue(data.isIcs233Portrait());
			}
		}
		var legacy = service.exportForms(keys, data, directory);
		try (PDDocument pdf = Loader.loadPDF(legacy.get("ICS 233-CG").toFile())) {
			assertGeometryAndDirections(pdf, PDRectangle.A4, PdfOrientation.PORTRAIT, 54);
		}
	}

	/**
	 * Checks physical media, counterclockwise body glyphs, safe margins and upright labels.
	 *
	 * @param pdf rendered document
	 * @param portrait portrait paper dimensions
	 * @param orientation expected layout
	 * @param margin minimum physical margin
	 * @throws IOException when extracting text fails
	 */
	private static void assertGeometryAndDirections(PDDocument pdf, PDRectangle portrait,
			PdfOrientation orientation, float margin) throws IOException {
		for (int index = 0; index < pdf.getNumberOfPages(); index++) {
			var page = pdf.getPage(index);
			boolean landscape = orientation == PdfOrientation.LANDSCAPE;
			for (PDRectangle box : List.of(page.getMediaBox(), page.getCropBox())) {
				assertEquals(landscape ? portrait.getHeight() : portrait.getWidth(), box.getWidth(), 0.01);
				assertEquals(landscape ? portrait.getWidth() : portrait.getHeight(), box.getHeight(), 0.01);
			}
			assertEquals(0, page.getRotation());
			Positions positions = new Positions();
			positions.setStartPage(index + 1);
			positions.setEndPage(index + 1);
			positions.getText(pdf);
			boolean rotated = orientation == PdfOrientation.PORTRAIT_FIT;
			assertEquals(rotated, positions.body.stream().anyMatch(glyph -> glyph.getDir() == 90));
			assertFalse(positions.labels.isEmpty());
			for (TextPosition glyph : positions.labels) {
				assertEquals(0, glyph.getDir(), 0.01);
				assertEquals(margin, glyph.getTextMatrix().getTranslateY(), 0.01);
			}
			for (TextPosition glyph : positions.body) {
				assertEquals(rotated ? 90 : 0, glyph.getDir(), 0.01);
				float x = glyph.getTextMatrix().getTranslateX();
				float y = glyph.getTextMatrix().getTranslateY();
				assertTrue(x >= margin - 0.1 && x <= page.getMediaBox().getWidth() - margin + 0.1, "x=" + x);
				assertTrue(y >= margin - 0.1 && y <= page.getMediaBox().getHeight() - margin + 0.1, "y=" + y);
				if (rotated) {
					assertTrue(y >= margin + 20, "Rotated body must clear upright footer");
					assertTrue(glyph.getTextMatrix().getShearY() > 0);
					assertTrue(glyph.getTextMatrix().getShearX() < 0);
				}
			}
		}
	}

	/** Captures actual glyph transforms separately for body text and physical footers. */
	private static final class Positions extends PDFTextStripper {
		private final List<TextPosition> body = new ArrayList<>();
		private final List<TextPosition> labels = new ArrayList<>();

		/**
		 * Creates a PDF text collector.
		 *
		 * @throws IOException when the text engine cannot initialize
		 */
		private Positions() throws IOException {
			super();
		}

		/** {@inheritDoc} */
		@Override
		protected void writeString(String text, List<TextPosition> positions) {
			(text.startsWith("Page ") || text.startsWith("ICS 233-CG Rev1/07") ? labels : body).addAll(positions);
		}
	}
}
