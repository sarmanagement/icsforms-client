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
import org.sarmanagement.icsforms.model.SarTaskAssignment;
import org.sarmanagement.icsforms.model.SarTaskResource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Layout coverage for inline command staff and assignment leaders inside ICS 207 nodes. */
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

	@Test
	void medicalUnitLeaderAppearsInTheLogisticsChart() throws Exception {
		AppData data = new AppData();
		data.getOrganizationalChart().setMedicalUnitLeaderName("Fictional Medical Leader");
		Path file = directory.resolve("medical-unit-leader.pdf");

		new Ics207PdfRenderer().render(data, file);

		try (PDDocument pdf = Loader.loadPDF(file.toFile())) {
			String text = new PDFTextStripper().getText(pdf);
			assertTrue(text.contains("Medical Unit Ldr"));
			assertTrue(text.contains("Fictional Medical Leader"));
		}
	}

	@Test
	void thirteenTaskLeadersStayInsideTheirOwnAssignmentNodesWithoutSeparateOverheadBoxes() throws Exception {
		for (PdfLayoutSettings.PaperSize paper : PdfLayoutSettings.PaperSize.values()) {
			AppData data = tasks(13);
			data.getPdfLayoutSettings().setPaperSize(paper);
			data.getOrganizationalChart().setStagingAreaManagerName("Staging Manager");
			Path file = directory.resolve("task-leaders-" + paper + ".pdf");
			new Ics207PdfRenderer().render(data, file);
			try (PDDocument pdf = Loader.loadPDF(file.toFile())) {
				assertEquals(1, pdf.getNumberOfPages());
				assertEquals(Ics207PdfRenderer.pageCount(data), pdf.getNumberOfPages());
				StaffPositions positions = new StaffPositions();
				String text = positions.getText(pdf).replaceAll("\\s+", " ");
				for (int index = 0; index < 13; index++) {
					String title = String.format("Assignment %02d", index);
					String leader = String.format("Leader: Task Leader %02d", index);
					assertTrue(text.contains(title), title);
					assertTrue(text.contains(leader), leader);
					assertEquals(1, occurrences(text, leader));
					assertAssignmentNode(pdf.getPage(0), positions.find(title, false),
							positions.find(leader, false), positions.find(leader, true));
				}
				TextPosition leader = positions.find("Leader: Task Leader 00", false);
				TextPosition people = positions.find("1 person", false);
				assertTrue(people.getYDirAdj() > leader.getYDirAdj());
				assertTrue(people.getYDirAdj() - leader.getYDirAdj() < 9f);
				assertTrue(text.contains("Staging Manager"));
			}
		}
	}

	@Test
	void assignmentAndOtherSectionOverflowContinueWithoutLosingNodesOnLetterAndA4() throws Exception {
		for (PdfLayoutSettings.PaperSize paper : PdfLayoutSettings.PaperSize.values()) {
			AppData data = tasks(40);
			data.getPdfLayoutSettings().setPaperSize(paper);
			data.getPdfLayoutSettings().setPageMarginPoints(108f);
			data.getForm207().setIapPage("10");
			OrgChartEntry lastRole = new OrgChartEntry();
			lastRole.setSection(OrgChartEntry.Section.OPERATIONS);
			lastRole.setTitle("Custom Operations Role");
			lastRole.setName("Last Operations Person");
			data.getOrganizationalChart().getAdditionalPositions().add(lastRole);
			data.getOrganizationalChart().setDocumentationUnitLeaderName("Planning Person");
			data.getOrganizationalChart().setMedicalUnitLeaderName("Medical Person");
			data.getOrganizationalChart().setCostUnitLeaderName("Finance Person");
			Path file = directory.resolve("overflow-" + paper + ".pdf");
			new Ics207PdfRenderer().render(data, file);
			try (PDDocument pdf = Loader.loadPDF(file.toFile())) {
				assertTrue(pdf.getNumberOfPages() > 1);
				assertEquals(Ics207PdfRenderer.pageCount(data), pdf.getNumberOfPages());
				String all = new PDFTextStripper().getText(pdf).replaceAll("\\s+", " ");
				for (int index = 0; index < 40; index++) {
					String leader = String.format("Task Leader %02d", index);
					assertTrue(all.contains(leader), leader);
					assertEquals(1, occurrences(all, leader));
				}
				for (String person : List.of("Planning Person", "Medical Person", "Finance Person",
						"Last Operations Person")) {
					assertTrue(all.contains(person), person);
				}
				for (int page = 2; page <= pdf.getNumberOfPages(); page++) {
					PDFTextStripper stripper = new PDFTextStripper();
					stripper.setStartPage(page);
					stripper.setEndPage(page);
					String text = stripper.getText(pdf);
					assertTrue(text.contains("INCIDENT ORGANIZATION CHART (ICS 207) Page " + page));
					for (String block : List.of("1. Incident Name", "2. Operational Period",
							"3. Organization Chart", "4. Prepared By")) {
						assertTrue(text.contains(block), block);
					}
					assertTrue(text.contains("IAP Page " + (9 + page)));
				}
			}
		}
	}

	@Test
	void longAssignmentLabelsAndLeaderNamesWrapWithoutTruncationOrAdditionalLeaderNodes() throws Exception {
		AppData data = tasks(1);
		SarTaskAssignment task = data.getSarTaskAssignments().get(0);
		task.setResourceIdentifier("Very Long Assignment Identifier Without Truncation");
		task.setLeader("Elizabeth Montgomery / Alexander Robertson (T)");
		Path file = directory.resolve("long-task-leader.pdf");
		new Ics207PdfRenderer().render(data, file);
		try (PDDocument pdf = Loader.loadPDF(file.toFile())) {
			String text = new PDFTextStripper().getText(pdf).replaceAll("\\s+", " ");
			assertTrue(text.contains(task.getResourceIdentifier()));
			assertTrue(text.contains("Leader: " + task.getLeader()));
			assertEquals(1, occurrences(text, task.getLeader()));
			assertTrue(text.contains("1 person"));
		}
	}

	/**
	 * Builds assignments with distinct leaders and one personnel entry each.
	 *
	 * @param count requested assignment count.
	 * @return source incident without duplicate additional leader positions.
	 */
	private AppData tasks(int count) {
		AppData data = new AppData();
		for (int index = 0; index < count; index++) {
			SarTaskAssignment task = new SarTaskAssignment();
			task.setResourceIdentifier(String.format("Assignment %02d", index));
			task.setLeader(String.format("Task Leader %02d", index));
			SarTaskResource person = new SarTaskResource();
			person.setName(task.getLeader());
			task.getResourcesAssigned().add(person);
			data.getSarTaskAssignments().add(task);
		}
		return data;
	}

	/**
	 * Counts non-overlapping appearances of one rendered value.
	 *
	 * @param text complete PDF text.
	 * @param value value to count.
	 * @return number of occurrences.
	 */
	private int occurrences(String text, String value) {
		return (text.length() - text.replace(value, "").length()) / value.length();
	}

	/**
	 * Confirms a task title and complete leader share a single three-line node rectangle.
	 *
	 * @param page rendered chart page.
	 * @param title title's first glyph.
	 * @param leader leader label's first glyph.
	 * @param leaderEnd leader value's last glyph.
	 * @throws IOException when reading the page stream fails.
	 */
	private void assertAssignmentNode(PDPage page, TextPosition title, TextPosition leader,
			TextPosition leaderEnd) throws IOException {
		List<Object> tokens = new PDFStreamParser(page).parse();
		float pageHeight = page.getMediaBox().getHeight();
		for (int index = 4; index < tokens.size(); index++) {
			if (tokens.get(index) instanceof Operator operator && "re".equals(operator.getName())) {
				float x = ((COSNumber) tokens.get(index - 4)).floatValue();
				float y = ((COSNumber) tokens.get(index - 3)).floatValue();
				float width = ((COSNumber) tokens.get(index - 2)).floatValue();
				float height = ((COSNumber) tokens.get(index - 1)).floatValue();
				if (width < 180f && height >= 28f && height < 60f
						&& title.getXDirAdj() >= x && title.getXDirAdj() < x + width
						&& pageHeight - title.getYDirAdj() > y
						&& pageHeight - title.getYDirAdj() < y + height) {
					assertTrue(leader.getXDirAdj() >= x);
					assertTrue(leaderEnd.getXDirAdj() + leaderEnd.getWidthDirAdj() <= x + width);
					assertTrue(pageHeight - leader.getYDirAdj() > y);
					assertTrue(pageHeight - leader.getYDirAdj() < y + height);
					assertTrue(leader.getYDirAdj() > title.getYDirAdj());
					return;
				}
			}
		}
		throw new AssertionError("Assignment title and leader do not share a node");
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
