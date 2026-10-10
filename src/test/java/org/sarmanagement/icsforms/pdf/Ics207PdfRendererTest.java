/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.pdf;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSNumber;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.OrgChartEntry;
import org.sarmanagement.icsforms.model.PdfLayoutSettings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Layout regression coverage for inline ICS 207 command-staff assignments. */
class Ics207PdfRendererTest {
	@TempDir
	Path directory;

	@Test
	void commandStaffNamesShareTitleBaselineInsideNarrowerBoxesShiftedRight() throws Exception {
		for (float margin : List.of(18f, 54f, 108f)) {
			AppData data = new AppData();
			if (margin > 18f) {
				data.getPdfLayoutSettings().setPaperSize(PdfLayoutSettings.PaperSize.A4);
			}
			data.getPdfLayoutSettings().setPageMarginPoints(margin);
			data.getOrganizationalChart().setSafetyOfficerName("John Doe");
			data.getOrganizationalChart().setPublicInformationOfficerName("Jane Public");
			data.getOrganizationalChart().setLiaisonOfficerName("Alex Liaison");
			OrgChartEntry custom = new OrgChartEntry();
			custom.setSection(OrgChartEntry.Section.COMMAND_STAFF);
			custom.setTitle("Assistant Safety Officer");
			custom.setName("Elizabeth Montgomery / Alexander Robertson (T)");
			data.getOrganizationalChart().getAdditionalPositions().add(custom);
			Path file = directory.resolve("staff-" + margin + ".pdf");
			new Ics207PdfRenderer().render(data, file);
			try (PDDocument pdf = Loader.loadPDF(file.toFile())) {
				assertEquals(1, pdf.getNumberOfPages());
				StaffPositions positions = new StaffPositions();
				positions.getText(pdf);
				PDPage page = pdf.getPage(0);
				for (String[] pair : List.of(new String[]{"Safety Officer", "John Doe"},
						new String[]{"Public Info. Officer", "Jane Public"},
						new String[]{"Liaison Officer", "Alex Liaison"},
						new String[]{custom.getTitle(), custom.getName()})) {
					TextPosition title = positions.find(pair[0], false);
					TextPosition name = positions.find(pair[1], false);
					TextPosition nameEnd = positions.find(pair[1], true);
					assertEquals(title.getYDirAdj(), name.getYDirAdj(), .1f);
					assertTrue(name.getXDirAdj() > title.getXDirAdj());
					assertTrue(title.getXDirAdj() > page.getMediaBox().getWidth() / 2f);
					assertTrue(nameEnd.getXDirAdj() + nameEnd.getWidthDirAdj()
							< page.getMediaBox().getWidth() - Math.max(36f, margin));
					assertStaffBox(page, title, margin);
				}
			}
		}
	}

	@Test
	void emptyCommandStaffStillHasLabeledBoxes() throws Exception {
		Path file = directory.resolve("blank.pdf");
		new Ics207PdfRenderer().render(new AppData(), file);
		try (PDDocument pdf = Loader.loadPDF(file.toFile())) {
			String text = new PDFTextStripper().getText(pdf);
			for (String title : List.of("Safety Officer", "Public Info. Officer", "Liaison Officer")) {
				assertTrue(text.contains(title));
			}
			assertEquals(1, pdf.getNumberOfPages());
		}
	}

	/**
	 * Verifies the staff rectangle is one-third narrower and shifted right of the stem.
	 *
	 * @param page rendered page.
	 * @param title title position inside the box.
	 * @param margin configured page margin.
	 * @throws IOException when page content cannot be parsed.
	 */
	private void assertStaffBox(PDPage page, TextPosition title, float margin) throws IOException {
		List<Object> tokens = new PDFStreamParser(page).parse();
		float baseline = page.getMediaBox().getHeight() - title.getYDirAdj();
		for (int i = 4; i < tokens.size(); i++) {
			if (tokens.get(i) instanceof Operator operator && "re".equals(operator.getName())) {
				float x = ((COSNumber) tokens.get(i - 4)).floatValue();
				float y = ((COSNumber) tokens.get(i - 3)).floatValue();
				float width = ((COSNumber) tokens.get(i - 2)).floatValue();
				float height = ((COSNumber) tokens.get(i - 1)).floatValue();
				if (Math.abs(height - 18f) < .1f && baseline > y && baseline < y + height
						&& Math.abs(x + 2f - title.getXDirAdj()) < .1f) {
					float stemX = page.getMediaBox().getWidth() / 2f;
					float previousWidth = stemX - Math.max(36f, margin) - 4f - 12f;
					assertEquals(stemX + 20f, x, .1f);
					assertEquals(previousWidth * 2f / 3f, width, .1f);
					assertTrue(x + width < page.getMediaBox().getWidth() - Math.max(36f, margin));
					return;
				}
			}
		}
		throw new AssertionError("No enclosing command-staff box");
	}

	/** Collects glyph positions to compare title and name baselines precisely. */
	private static class StaffPositions extends PDFTextStripper {
		private final List<TextPosition> glyphs = new ArrayList<>();

		/**
		 * Creates a glyph collector.
		 * @throws IOException when the text extractor cannot initialize.
		 */
		StaffPositions() throws IOException {
			super();
		}

		/** {@inheritDoc} */
		@Override
		protected void processTextPosition(TextPosition text) {
			glyphs.add(text);
			super.processTextPosition(text);
		}

		/**
		 * Finds the first or final glyph of a complete title/name.
		 *
		 * @param text rendered text.
		 * @param last whether to return its last glyph.
		 * @return matching glyph position.
		 */
		TextPosition find(String text, boolean last) {
			StringBuilder contents = new StringBuilder();
			for (TextPosition glyph : glyphs) {
				contents.append(glyph.getUnicode());
			}
			int index = contents.indexOf(text);
			assertTrue(index >= 0, "Missing text: " + text);
			return glyphs.get(index + (last ? text.length() - 1 : 0));
		}
	}
}
