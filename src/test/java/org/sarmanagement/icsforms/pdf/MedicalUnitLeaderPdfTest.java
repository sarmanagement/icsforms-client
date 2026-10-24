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
import org.sarmanagement.icsforms.model.MedicalUnitLeaderAssignment;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Confirms ICS PDFs use the authoritative Medical Unit Leader assignment. */
class MedicalUnitLeaderPdfTest {
	@TempDir
	Path temporaryDirectory;

	@Test
	void organizationAndMedicalFormsRenderCanonicalNameInsteadOfLegacyCopies() throws Exception {
		AppData data = new AppData();
		data.getOrganizationalChart().setMedicalUnitLeaderName("Legacy Chart Name");
		data.getOrganizationalChart().getAssignmentList()
				.setPosition(org.sarmanagement.icsforms.model.Ics203Form.Position.MEDICAL_UNIT_LEADER,
						"Legacy Assignment List Name");
		data.getMedicalPlan().setPreparedByMedicalUnitLeader("Legacy Human Plan Name");
		data.getCanineMedicalPlan().setPreparedByMedicalUnitLeader("Legacy Canine Plan Name");
		MedicalUnitLeaderAssignment assignment = new MedicalUnitLeaderAssignment();
		assignment.setName("Canonical Medical Leader");
		data.setMedicalUnitLeaderAssignment(assignment);

		List<String> pdfTexts = List.of(
				render(new Ics203PdfRenderer(), data, "ics-203.pdf"),
				render(new Ics207PdfRenderer(), data, "ics-207.pdf"),
				render(new Ics206PdfRenderer(), data, "ics-206.pdf"),
				render(new Ics206CaninePdfRenderer(), data, "ics-206canine.pdf"));

		for (String text : pdfTexts) {
			assertTrue(text.contains("Canonical Medical Leader"), text);
			assertFalse(text.contains("Legacy Chart Name"), text);
			assertFalse(text.contains("Legacy Assignment List Name"), text);
			assertFalse(text.contains("Legacy Human Plan Name"), text);
			assertFalse(text.contains("Legacy Canine Plan Name"), text);
		}
	}

	/**
	 * Renders a form and extracts its printable text.
	 *
	 * @param renderer renderer under test.
	 * @param data incident model.
	 * @param filename output PDF name.
	 * @return normalized PDF text.
	 * @throws Exception when rendering or extraction fails.
	 */
	private String render(PdfFormRenderer renderer, AppData data, String filename) throws Exception {
		Path output = temporaryDirectory.resolve(filename);
		renderer.render(data, output);
		try (PDDocument pdf = Loader.loadPDF(output.toFile())) {
			return new PDFTextStripper().getText(pdf).replaceAll("\\s+", " ");
		}
	}
}
