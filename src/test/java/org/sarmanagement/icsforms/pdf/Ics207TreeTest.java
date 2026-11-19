/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.pdf;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.OrgChartEntry;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Covers nested (tree) layout of ICS 207 section positions via reportsTo. */
class Ics207TreeTest {
	@TempDir
	Path temporaryDirectory;

	@Test
	void nestedPositionsRenderIncludingCyclesAndUnknownParents() throws Exception {
		AppData data = new AppData();
		data.getOrganizationalChart().getAdditionalPositions().add(entry("Fire Group", "Alice Parent", ""));
		data.getOrganizationalChart().getAdditionalPositions().add(entry("Engine One", "Bob Child", "fire group"));
		data.getOrganizationalChart().getAdditionalPositions().add(entry("Cycle A", "Carol Cycle", "Cycle B"));
		data.getOrganizationalChart().getAdditionalPositions().add(entry("Cycle B", "Dan Cycle", "Cycle A"));
		data.getOrganizationalChart().getAdditionalPositions().add(entry("Orphan", "Eve Orphan", "No Such Parent"));

		Path pdf = temporaryDirectory.resolve("ics-207.pdf");
		new Ics207PdfRenderer().render(data, pdf);
		try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
			assertEquals(Ics207PdfRenderer.pageCount(data), document.getNumberOfPages());
			String text = new PDFTextStripper().getText(document);
			for (String name : new String[]{"Alice Parent", "Bob Child", "Carol Cycle", "Dan Cycle", "Eve Orphan"}) {
				assertTrue(text.contains(name), name);
			}
		}
	}

	@Test
	void reportsToRoundTripsAndIsOmittedWhenBlank() throws Exception {
		ObjectMapper mapper = new ObjectMapper();
		OrgChartEntry child = entry("Engine One", "Bob", "  Fire Group ");
		assertEquals("Fire Group", child.getReportsTo());
		assertTrue(mapper.writeValueAsString(child).contains("\"reportsTo\":\"Fire Group\""));
		OrgChartEntry root = entry("Fire Group", "Alice", null);
		assertFalse(mapper.writeValueAsString(root).contains("reportsTo"));
		assertEquals("Fire Group",
				mapper.readValue(mapper.writeValueAsString(child), OrgChartEntry.class).getReportsTo());
	}

	private static OrgChartEntry entry(String title, String name, String reportsTo) {
		OrgChartEntry entry = new OrgChartEntry();
		entry.setSection(OrgChartEntry.Section.OPERATIONS);
		entry.setTitle(title);
		entry.setName(name);
		entry.setReportsTo(reportsTo);
		return entry;
	}
}
