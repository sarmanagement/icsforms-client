/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.pdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSNumber;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.MedicalPlan;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Ics206PdfRendererTest {
	@TempDir
	Path tempDirectory;

	@Test
	void standardFormRendersNumberedBlocksAndKeepsUnknownDistinctFromNo() throws Exception {
		AppData data = new AppData();
		data.getIncidentContext().setIncidentName("Fictional Incident");
		data.getMedicalUnitLeaderAssignment().setName("Medical Lead");
		MedicalPlan plan = data.getMedicalPlan();
		plan.setActive(true);
		plan.setPreparedByMedicalUnitLeader("Medical Lead");
		plan.setReviewedBySafetyOfficer("Safety Reviewer");
		plan.setPreparerSignature("Legacy signature text");
		plan.setSafetyOfficerSignature("Legacy reviewer text");
		plan.setReviewStatus("Reviewed");

		MedicalPlan.AidStation station = new MedicalPlan.AidStation();
		station.setName("Fictional Aid Station");
		station.setParamedicsOnSite(null);
		plan.getAidStations().add(station);
		MedicalPlan.Hospital hospital = new MedicalPlan.Hospital();
		hospital.setName("Fictional Regional Hospital");
		hospital.setTraumaCenter(false);
		hospital.setBurnCenter(null);
		hospital.setHelipad(true);
		plan.getHospitals().add(hospital);
		plan.setSimpleMode(false);

		Path output = tempDirectory.resolve("ics-206.pdf");
		new Ics206PdfRenderer().render(data, output);

		try (PDDocument pdf = Loader.loadPDF(output.toFile())) {
			String text = new PDFTextStripper().getText(pdf).replaceAll("\\s+", " ");
			for (String block : new String[] {"1. INCIDENT NAME", "2. OPERATIONAL PERIOD",
					"3. MEDICAL AID STATIONS", "4. TRANSPORTATION", "5. HOSPITALS",
					"6. SPECIAL MEDICAL EMERGENCY PROCEDURES", "7. PREPARED BY", "8. APPROVED BY"}) {
				assertTrue(text.contains(block), block);
			}
			for (String column : new String[] {"Aid Station", "Location", "Contact Number(s) / Frequency",
					"Paramedics On Site", "Service", "Hospital", "Travel Time (Air / Ground)"}) {
				assertTrue(text.contains(column), column);
			}
			assertTrue(text.contains("UNKNOWN"));
			assertTrue(text.contains("NO"));
			assertTrue(text.contains("ICS 206"));
			assertTrue(text.contains("Name: Medical Lead"));
			assertTrue(text.contains("Name: Safety Reviewer"));
			assertTrue(text.contains("Signature: ______________________________"));
			assertTrue(text.contains("Date/Time:"));
			assertTrue(!text.contains("Legacy signature text"));
			assertTrue(!text.contains("Legacy reviewer text"));
			assertEquals(1, pdf.getNumberOfPages());
		}
	}

	@Test
	void longProceduresContinueAcrossPagesWithoutDroppingTheLastLine() throws Exception {
		AppData data = new AppData();
		String longText = IntStream.range(0, 800).mapToObj(index -> "procedure" + index)
				.collect(Collectors.joining(" ")) + " END_OF_PROCEDURES";
		data.getMedicalPlan().setEmergencyProcedures(longText);

		Path output = tempDirectory.resolve("ics-206-long.pdf");
		new Ics206PdfRenderer().render(data, output);

		try (PDDocument pdf = Loader.loadPDF(output.toFile())) {
			String text = new PDFTextStripper().getText(pdf);
			assertTrue(pdf.getNumberOfPages() > 1);
			assertEquals(Ics206PdfRenderer.pageCount(data), pdf.getNumberOfPages());
			assertTrue(text.contains("END_OF_PROCEDURES"));
			assertTrue(text.contains("CONTINUED"));
			PDFTextStripper firstPage = new PDFTextStripper();
			firstPage.setStartPage(1);
			firstPage.setEndPage(1);
			assertTrue(!firstPage.getText(pdf).contains("7. PREPARED BY"));
			PDFTextStripper lastPage = new PDFTextStripper();
			lastPage.setStartPage(pdf.getNumberOfPages());
			lastPage.setEndPage(pdf.getNumberOfPages());
			assertTrue(lastPage.getText(pdf).contains("7. PREPARED BY"));
		}
	}

	@Test
	void metadataBlockDoesNotDrawAnExtraFullWidthBottomSeparator() throws Exception {
		AppData data = new AppData();
		Ics206PdfRenderer renderer = new Ics206PdfRenderer();
		Path output = tempDirectory.resolve("ics-206-footer.pdf");

		renderer.render(data, output);

		try (PDDocument pdf = Loader.loadPDF(output.toFile())) {
			AbstractPdfRenderer.FormLayout layout = renderer.formLayout(renderer.newPage(data), data);
			float[] metadataRectangle = {layout.x(), layout.y() + 34f, layout.width(), 48f};
			List<Object> tokens = new PDFStreamParser(pdf.getPage(0)).parse();
			for (int index = 4; index < tokens.size(); index++) {
				if (tokens.get(index) instanceof Operator operator && "re".equals(operator.getName())
						&& tokens.get(index - 4) instanceof COSNumber x
						&& tokens.get(index - 3) instanceof COSNumber y
						&& tokens.get(index - 2) instanceof COSNumber width
						&& tokens.get(index - 1) instanceof COSNumber height) {
					assertFalse(closeTo(x.floatValue(), metadataRectangle[0])
							&& closeTo(y.floatValue(), metadataRectangle[1])
							&& closeTo(width.floatValue(), metadataRectangle[2])
							&& closeTo(height.floatValue(), metadataRectangle[3]));
				}
			}
		}
	}

	private boolean closeTo(float actual, float expected) {
		return Math.abs(actual - expected) < 0.2f;
	}
}
