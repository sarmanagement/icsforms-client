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
import org.sarmanagement.icsforms.model.StagingLocation;
import org.sarmanagement.icsforms.model.VetFacility;

import java.nio.file.Path;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Ics206CaninePdfRendererTest {
	@TempDir
	Path tempDirectory;

	@Test
	void adaptedFormIncludesCanineBlocksAndExplicitUnknownAvailability() throws Exception {
		AppData data = new AppData();
		data.getIncidentContext().setIncidentName("Fictional Incident");
		data.getMedicalUnitLeaderAssignment().setName("Medical Lead");
		data.getOrganizationalChart().setIncidentCommanders(java.util.List.of("Medical Lead"));
		data.getOrganizationalChart().setOperationsSectionChiefName("Safety Reviewer");
		StagingLocation base = data.getStagingLocation();
		base.setLabel("Fictional Base");
		base.setAddress("1 Fictional Road");
		VetFacility vet = new VetFacility();
		vet.setName("Fictional Veterinary Hospital");
		vet.setTown("Fictional Town");
		vet.setAddress("2 Sample Street");
		vet.setCapabilities("Emergency stabilization");
		vet.setAvailable24Hours(null);
		data.getCanineMedicalPlan().getSelectedFacilities().add(vet);
		data.getCanineMedicalPlan().setVeterinarianDvm("Legacy DVM value");
		data.getCanineMedicalPlan().setPreparerSignature("Legacy canine signature");
		data.getCanineMedicalPlan().setSafetyOfficerSignature("Legacy canine review");
		data.getCanineMedicalPlan().setReviewedBySafetyOfficer("Safety Reviewer");

		Path output = tempDirectory.resolve("ics-206canine.pdf");
		new Ics206CaninePdfRenderer().render(data, output);

		try (PDDocument pdf = Loader.loadPDF(output.toFile())) {
			String text = new PDFTextStripper().getText(pdf).replaceAll("\\s+", " ");
			for (String block : new String[] {"1. INCIDENT NAME", "2. OPERATIONAL PERIOD",
					"3. MEDICAL AID STATIONS", "4. TRANSPORTATION", "5. VETERINARY HOSPITALS",
					"6. SPECIAL MEDICAL EMERGENCY PROCEDURES", "7. Prepared by (Incident Commander)",
					"8. Approved by (Operations Section Chief)"}) {
				assertTrue(text.contains(block), block);
			}
			for (String column : new String[] {"Hospital Name", "Town", "Address", "Contact Number(s)",
					"24 Hours", "Notes"}) {
				assertTrue(text.contains(column), column);
			}
			assertTrue(text.contains("Fictional Veterinary Hospital"));
			assertTrue(text.contains("Unknown (not verified)"));
			assertTrue(text.contains("ICS 206CANINE"));
			assertTrue(text.contains("CANINE MEDICAL PLAN (ICS 206CANINE)"));
			assertTrue(!text.contains("CANINE MEDICAL PLAN (ADAPTED)"));
			assertTrue(!text.contains("DVM:"));
			assertTrue(!text.contains("Legacy DVM value"));
			assertTrue(text.contains("Name: Medical Lead"));
			assertTrue(text.contains("Name: Safety Reviewer"));
			assertTrue(text.contains("Signature: ______________________________"));
			assertTrue(text.contains("Date/Time:"));
			assertTrue(!text.contains("Legacy canine signature"));
			assertTrue(!text.contains("Legacy canine review"));
		}
	}

	@Test
	void longVeterinaryNotesContinueAcrossPagesWithoutDroppingTheLastLine() throws Exception {
		AppData data = new AppData();
		VetFacility vet = new VetFacility();
		vet.setName("Fictional Vet");
		vet.setAvailable24Hours(true);
		vet.setCapabilities(IntStream.range(0, 800).mapToObj(index -> "capability" + index)
				.collect(Collectors.joining(" ")) + " END_OF_VETERINARY_NOTES");
		data.getCanineMedicalPlan().getSelectedFacilities().add(vet);

		Path output = tempDirectory.resolve("ics-206canine-long.pdf");
		new Ics206CaninePdfRenderer().render(data, output);

		try (PDDocument pdf = Loader.loadPDF(output.toFile())) {
			String text = new PDFTextStripper().getText(pdf).replaceAll("\\s+", " ");
			assertTrue(pdf.getNumberOfPages() > 1);
			assertEquals(Ics206CaninePdfRenderer.pageCount(data), pdf.getNumberOfPages());
			assertTrue(text.contains("END_OF_VETERINARY_NOTES"));
			assertTrue(text.contains("CONTINUED"));
			PDFTextStripper firstPage = new PDFTextStripper();
			firstPage.setStartPage(1);
			firstPage.setEndPage(1);
			assertTrue(!firstPage.getText(pdf).contains("7. Prepared by"));
			PDFTextStripper lastPage = new PDFTextStripper();
			lastPage.setStartPage(pdf.getNumberOfPages());
			lastPage.setEndPage(pdf.getNumberOfPages());
			assertTrue(lastPage.getText(pdf).contains("7. Prepared by"));
		}
	}

	@Test
	void compactCanineFormUsesTheExpectedTitleAndKeepsAllBlocksOnOnePageWhenTheyFit() throws Exception {
		AppData data = new AppData();
		data.getIncidentContext().setIncidentName("Fictional Incident");
		VetFacility vet = new VetFacility();
		vet.setName("Fictional Veterinary Hospital");
		vet.setTown("Sampletown");
		vet.setAddress("1 Example Road");
		vet.setAvailable24Hours(true);
		data.getCanineMedicalPlan().getSelectedFacilities().add(vet);

		Path output = tempDirectory.resolve("ics-206canine-compact.pdf");
		new Ics206CaninePdfRenderer().render(data, output);

		try (PDDocument pdf = Loader.loadPDF(output.toFile())) {
			String text = new PDFTextStripper().getText(pdf).replaceAll("\\s+", " ");
			assertEquals(1, pdf.getNumberOfPages());
			assertEquals(Ics206CaninePdfRenderer.pageCount(data), pdf.getNumberOfPages());
			assertTrue(text.contains("CANINE MEDICAL PLAN (ICS 206CANINE)"));
			assertTrue(text.contains("Fictional Veterinary Hospital"));
			assertTrue(text.contains("8. Approved by (Safety Officer)"));
		}
	}
}
