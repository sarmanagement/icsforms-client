/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.pdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.Ics215Form;

import java.nio.file.Path;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Ics215PdfRendererTest {
	@TempDir
	Path outputDirectory;

	@Test
	void rendersSelectedPeriodAndSeparateLandscapePaperChoice() throws Exception {
		AppData data = new AppData();
		data.getIncidentContext().setIncidentName("River Search");
		Ics215Form active = data.getForm215();
		active.setTargetPeriodStart(LocalDateTime.of(2026, 10, 31, 8, 0));
		active.setTargetPeriodEnd(LocalDateTime.of(2026, 10, 31, 20, 0));
		active.setPreparerName("A. Planner");
		active.setPreparerPositionTitle("Operations Section Chief");
		active.setPreparerSignature("A. Planner");
		active.setPreparedDateTime(LocalDateTime.of(2026, 10, 30, 17, 15));
		active.setPaperSize(Ics215Form.PaperSize.LEGAL);
		Ics215Form.ResourceHeading activeHeading = heading("Engine", "Type 3");
		active.getResourceHeadings().add(activeHeading);
		active.getWorkRows().add(row("Search Division A", activeHeading));

		Ics215Form planned = new Ics215Form();
		planned.setTargetPeriodStart(LocalDateTime.of(2026, 11, 1, 8, 0));
		planned.setTargetPeriodEnd(LocalDateTime.of(2026, 11, 1, 20, 0));
		planned.setPaperSize(Ics215Form.PaperSize.LEGAL);
		planned.getWorkRows().add(row("Planned assignment", null));
		data.setNextPeriodForm215(planned);
		data.setIcs215ExportNextPeriod(true);

		Path output = outputDirectory.resolve("ics-215.pdf");
		new Ics215PdfRenderer().render(data, output);

		try (PDDocument pdf = Loader.loadPDF(output.toFile())) {
			assertEquals(Ics215PdfRenderer.pageCount(data), pdf.getNumberOfPages());
			assertEquals(1, pdf.getNumberOfPages());
			assertEquals(1008f, pdf.getPage(0).getMediaBox().getWidth(), 0.1f);
			assertTrue(pdf.getPage(0).getMediaBox().getWidth() > pdf.getPage(0).getMediaBox().getHeight());
			String text = new PDFTextStripper().getText(pdf);
			assertTrue(text.contains("Planned assignment"));
			assertTrue(text.contains("ICS 215"));
			assertTrue(text.contains("Date From"));
			assertTrue(text.contains("Page 1 of 1"));
		}
	}

	@Test
	void paginatesArbitraryResourceColumnsAndLongAssignmentsWithoutDroppingText() throws Exception {
		AppData data = new AppData();
		Ics215Form form = data.getForm215();
		for (int index = 0; index < 7; index++) {
			form.getResourceHeadings().add(heading("Category " + index, "Type " + index));
		}
		String longAssignment = "Start" + "assignment ".repeat(700) + "FINISH";
		form.getWorkRows().add(row(longAssignment, null));

		Path output = outputDirectory.resolve("ics-215-continuation.pdf");
		new Ics215PdfRenderer().render(data, output);

		try (PDDocument pdf = Loader.loadPDF(output.toFile())) {
			assertEquals(Ics215PdfRenderer.pageCount(data), pdf.getNumberOfPages());
			assertTrue(pdf.getNumberOfPages() > 2);
			String text = new PDFTextStripper().getText(pdf).replaceAll("\\s+", " ");
			assertTrue(text.contains("Category 0"));
			assertTrue(text.contains("Category 6"));
			assertTrue(text.contains("FINISH"));
		}
	}

	@Test
	void bundlePageCountingUsesExplicitWorksheetSelection() {
		AppData data = new AppData();
		data.getForm215().getResourceHeadings().add(heading("Active", "Type 1"));
		for (int index = 0; index < 4; index++) {
			data.getNextPeriodForm215().getResourceHeadings().add(heading("Planned " + index, "Type 2"));
		}

		assertEquals(1, Ics215PdfRenderer.pageCount(data));
		data.setIcs215ExportNextPeriod(true);
		assertEquals(2, Ics215PdfRenderer.pageCount(data));
	}

	private Ics215Form.ResourceHeading heading(String category, String type) {
		Ics215Form.ResourceHeading heading = new Ics215Form.ResourceHeading();
		heading.category = category;
		heading.kind = "Ground";
		heading.type = type;
		heading.countingUnit = "engines";
		heading.acceptedOnHand = new Ics215Form.CountPair(2, 0);
		return heading;
	}

	private Ics215Form.WorkRow row(String assignment, Ics215Form.ResourceHeading heading) {
		Ics215Form.WorkRow row = new Ics215Form.WorkRow();
		row.assignment = assignment;
		row.managementContext = org.sarmanagement.icsforms.model.Ics204Form.MANAGEMENT_DIVISION;
		row.division = "Division A";
		row.requestedArrival = LocalDateTime.of(2026, 10, 31, 8, 30);
		if (heading != null) {
			Ics215Form.ResourceAllocation allocation = new Ics215Form.ResourceAllocation();
			allocation.required = new Ics215Form.CountPair(1, 0);
			allocation.acceptedHave = new Ics215Form.CountPair(0, 0);
			row.resourceAllocations.put(heading.id, allocation);
		}
		return row;
	}
}
