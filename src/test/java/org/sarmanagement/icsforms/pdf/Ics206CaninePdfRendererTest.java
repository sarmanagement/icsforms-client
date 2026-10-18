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

		Path output = tempDirectory.resolve("ics-206canine.pdf");
		new Ics206CaninePdfRenderer().render(data, output);

		try (PDDocument pdf = Loader.loadPDF(output.toFile())) {
			String text = new PDFTextStripper().getText(pdf).replaceAll("\\s+", " ");
			for (String block : new String[] {"1. INCIDENT NAME", "2. OPERATIONAL PERIOD",
					"3. MEDICAL AID STATIONS", "4. TRANSPORTATION", "5. VETERINARY HOSPITALS",
					"6. SPECIAL MEDICAL EMERGENCY PROCEDURES", "7. PREPARED BY", "8. REVIEWED BY"}) {
				assertTrue(text.contains(block), block);
			}
			assertTrue(text.contains("Fictional Veterinary Hospital"));
			assertTrue(text.contains("Unknown (not verified)"));
			assertTrue(text.contains("ICS 206CANINE"));
			assertTrue(!text.contains("DVM:"));
			assertTrue(!text.contains("Legacy DVM value"));
			assertTrue(text.contains("Signature: ______________________________________________"));
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
			String text = new PDFTextStripper().getText(pdf);
			assertTrue(pdf.getNumberOfPages() > 1);
			assertEquals(Ics206CaninePdfRenderer.pageCount(data), pdf.getNumberOfPages());
			assertTrue(text.contains("END_OF_VETERINARY_NOTES"));
			assertTrue(text.contains("CONTINUED"));
		}
	}
}
