/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.pdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.Ics208Form;
import org.sarmanagement.icsforms.model.SafetyAnalysisEntry;
import org.sarmanagement.icsforms.model.SafetyMessageBlock;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers safety PDF text, ISO dates, and continuation pages.
 */
class SafetyPdfRendererTest {
	@org.junit.jupiter.api.io.TempDir
	Path outputDirectory;

	@Test
	void renders215aHazardAndIsoDateAcrossContinuationPages() throws Exception {
		AppData data = new AppData();
		data.setUseSystemTimeZone(false);
		data.setConfiguredTimeZoneId("UTC");
		data.getIncidentContext().setIncidentName("Mountain Search");
		data.getIncidentContext().setOperationalPeriodStart(LocalDateTime.of(2026, 10, 10, 8, 0));
		data.getForm215a().setSafetyOfficerName("A. Safety");
		data.getForm215a().setSafetyOfficerSignature("A. Safety");
		SafetyAnalysisEntry entry = new SafetyAnalysisEntry();
		entry.setIncidentArea("Division A");
		entry.setHazardsRisks("Terrain, Dense Brush, Trip Hazards");
		entry.setMitigations("Appropriate equipment and footwear.\n".repeat(180));
		data.getForm215a().setPreparedDateTime(LocalDateTime.of(2026, 10, 10, 7, 30));
		data.getForm215a().setEntries(List.of(entry));
		Path output = Files.createTempFile("ics-215a-", ".pdf");

		new Ics215aPdfRenderer().render(data, output);

		try (PDDocument pdf = Loader.loadPDF(output.toFile())) {
			String text = new PDFTextStripper().getText(pdf);
			assertTrue(text.contains("INCIDENT ACTION PLAN SAFETY ANALYSIS"));
			assertTrue(text.contains("INCIDENT ACTION PLAN SAFETY ANALYSIS (ICS 215A)"));
			assertTrue(text.contains("1. Incident Name: Mountain Search"));
			assertTrue(text.contains("2. Operational Period"));
			assertTrue(text.contains("5. Incident Area"));
			assertTrue(text.contains("6. Hazards / Risks"));
			assertTrue(text.contains("7. Mitigations / Controls"));
			assertTrue(text.contains("A. Safety"));
			assertTrue(text.contains("2026-10-10 07:30"));
			assertTrue(text.contains("Terrain, Dense Brush, Trip Hazards"));
			assertTrue(text.contains("Appropriate equipment"));
			PDFStreamParser parser = new PDFStreamParser(pdf.getPage(0));
			assertEquals(3, parser.parse().stream()
					.filter(token -> token instanceof Operator operator && operator.getName().equals("re")).count());
			assertTrue(pdf.getNumberOfPages() > 1);
			assertEquals(Ics215aPdfRenderer.pageCount(data), pdf.getNumberOfPages());
		}
	}

	@Test
	void renders208HazardAndPrecautionsAsSeparateEditableMeaning() throws Exception {
		AppData data = new AppData();
		data.setUseSystemTimeZone(false);
		data.setConfiguredTimeZoneId("Pacific/Honolulu");
		data.getIncidentContext().setIncidentName("Mountain Search");
		data.getForm208().setMessageMode(Ics208Form.MessageMode.HAZARD_PRECAUTIONS);
		data.getForm208().setSiteSafetyPlanRequired(Boolean.TRUE);
		data.getForm208().setGeneralRequirements("Monitor weather. Maintain radio contact.");
		data.getForm208().setPreparedDateTime(LocalDateTime.of(2026, 10, 10, 7, 30));
		data.getForm208().setPreparedByName("Safety Officer");
		data.getForm208().setPreparedByPositionTitle("Safety Officer");
		data.getForm208().setPreparedBySignature("Signed");
		data.getForm208().setIapPage("12");
		SafetyMessageBlock block = new SafetyMessageBlock();
		block.setIncidentArea("All");
		block.setHazard("Terrain, Dense Brush, Trip Hazards");
		block.setPrecautions("Avoid steep drops and cliffs.");
		data.getForm208().setHazardBlocks(List.of(block));
		Path output = Files.createTempFile("ics-208-", ".pdf");

		new Ics208PdfRenderer().render(data, output);

		try (PDDocument pdf = Loader.loadPDF(output.toFile())) {
			String text = new PDFTextStripper().getText(pdf);
			assertTrue(text.contains("SAFETY MESSAGE/PLAN (ICS 208)"));
			assertTrue(text.contains("1. Incident Name"));
			assertTrue(text.contains("2. Operational Period"));
			assertTrue(text.contains("3. Safety Message"));
			assertTrue(text.contains("4. Site Safety Plan Required?"));
			assertTrue(text.contains("5. Approved Site Safety Plan(s) Located At"));
			assertTrue(text.contains("6. Prepared by"));
			assertTrue(text.contains("Name: Safety Officer"));
			assertTrue(text.contains("Position/Title: Safety Officer"));
			assertTrue(text.contains("Signature: Signed"));
			assertTrue(text.contains("ICS 208"));
			assertTrue(text.contains("IAP Page"));
			assertTrue(text.contains("Date/Time"));
			assertFalse(text.contains("ICS 208 - Page 1"));
			assertTrue(text.contains("Terrain, Dense Brush, Trip Hazards"));
			assertTrue(text.contains("Avoid steep drops and cliffs."));
			assertTrue(text.contains("Hazard: Terrain, Dense Brush, Trip Hazards"));
			assertTrue(text.contains("Mitigation: Avoid steep drops and cliffs."));
			assertTrue(text.indexOf("Monitor weather") < text.indexOf("Hazard: Terrain"));
			assertFalse(text.contains("All Hazard"));
			assertFalse(text.contains("Precautions:"));
			assertTrue(text.contains("2026-10-09 21:30"));
			PDFStreamParser parser = new PDFStreamParser(pdf.getPage(0));
			List<Object> tokens = parser.parse();
			assertTrue(tokens.stream().filter(token -> token instanceof Operator operator
					&& operator.getName().equals("re")).count() <= 4);
			assertTrue(tokens.stream().anyMatch(token -> token instanceof Operator operator
					&& operator.getName().equals("rg")));
			assertEquals(Ics208PdfRenderer.pageCount(data), pdf.getNumberOfPages());
		}
	}

	@Test
	void ics202ListsCheckedIncludedFormsInCanonicalOrder() throws Exception {
		AppData data = new AppData();
		data.getForm202().setIncidentActionPlanAttachments(
				List.of("ICS 202", "ICS 204", "SAR Task Assignment", "ICS 208", "ICS 215A", "Clue Log"));
		Path output = Files.createTempFile("ics-202-", ".pdf");

		new Ics202PdfRenderer().render(data, output);

		try (PDDocument pdf = Loader.loadPDF(output.toFile())) {
			String text = new PDFTextStripper().getText(pdf);
			assertTrue(text.contains("INCIDENT OBJECTIVES (ICS 202)"));
			assertTrue(text.contains("[X] ICS 202"));
			assertTrue(text.contains("[X] ICS 204"));
			assertTrue(text.contains("[X] SAR Task Assignment"));
			assertTrue(text.contains("[X] ICS 208"));
			assertTrue(text.contains("[X] ICS 215A"));
			assertTrue(text.contains("[X] Clue Log"));
			assertTrue(text.indexOf("[X] ICS 204") < text.indexOf("[X] SAR Task Assignment"));
			assertTrue(text.indexOf("[X] ICS 208") < text.indexOf("[X] ICS 215A"));
		}
	}

	@Test
	void iapIncludesSafetyFormsOnlyWhenSelectedAndNumbersTheirPages() throws Exception {
		AppData data = new AppData();
		data.getIncidentContext().setIncidentName("Without Safety Forms");
		data.getForm202().setCommandEmphasis("Command emphasis");
		SafetyAnalysisEntry entry = new SafetyAnalysisEntry();
		entry.setHazardsRisks("Steep terrain");
		entry.setMitigations("Use caution.");
		data.getForm215a().getEntries().add(entry);
		PdfExportService service = new PdfExportService(new Ics202PdfRenderer(), new Ics215aPdfRenderer(),
				new Ics208PdfRenderer());

		Path withoutSafety = service.exportIapBundle(data, outputDirectory);
		try (PDDocument pdf = Loader.loadPDF(withoutSafety.toFile())) {
			assertEquals(2, pdf.getNumberOfPages());
			assertFalse(new PDFTextStripper().getText(pdf).contains("Steep terrain"));
		}

		data.getForm202().setIncidentActionPlanAttachments(List.of("ICS 215A", "ICS 208"));
		data.getIncidentContext().setIncidentName("With Safety Forms");
		Path withSafety = service.exportIapBundle(data, outputDirectory);
		assertEquals("3", data.getForm215a().getIapPage());
		assertEquals("2", data.getForm208().getIapPage());
		try (PDDocument pdf = Loader.loadPDF(withSafety.toFile())) {
			String text = new PDFTextStripper().getText(pdf);
			assertEquals(4, pdf.getNumberOfPages());
			assertTrue(text.contains("Steep terrain"));
		}
	}

	@Test
	void long208MessageUsesMatchingContinuationPageCount() throws Exception {
		AppData data = new AppData();
		data.getIncidentContext().setIncidentName("Long incident");
		data.getForm208().setMessageMode(Ics208Form.MessageMode.FREE_TEXT);
		data.getForm208().setFreeText(String.join("\n", Collections.nCopies(220,
				"Search teams maintain communication and monitor changing conditions.")));
		Path output = Files.createTempFile("ics-208-long-", ".pdf");

		new Ics208PdfRenderer().render(data, output);

		try (PDDocument pdf = Loader.loadPDF(output.toFile())) {
			assertTrue(pdf.getNumberOfPages() > 1);
			assertEquals(Ics208PdfRenderer.pageCount(data), pdf.getNumberOfPages());
			String text = new PDFTextStripper().getText(pdf);
			assertTrue(text.contains("ICS 208 - Page 2"));
			assertFalse(text.contains("ICS 208 - Page 1"));
			assertTrue(text.contains("ICS 208"));
			assertTrue(text.contains("IAP Page"));
			PDFTextStripper pageTwo = new PDFTextStripper();
			pageTwo.setStartPage(2);
			pageTwo.setEndPage(2);
			assertTrue(pageTwo.getText(pdf).contains("Long incident"));
		}
	}
}
