package org.sarmanagement.icsforms.pdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.model.AppData;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoverPageRendererTest {

	@Test
	void renderIncludesPreparedWithSnapshotCommit() throws Exception {
		Path output = Files.createTempFile("cover-page-", ".pdf");

		new CoverPageRenderer(new ApplicationMetadata("icsforms-client", "1.2.3-SNAPSHOT", "abcdef1234"))
				.render(new AppData(), output);

		try (PDDocument pdf = Loader.loadPDF(output.toFile())) {
			String text = new PDFTextStripper().getText(pdf).replaceAll("\\s+", " ");
			assertTrue(text.contains("Prepared with: icsforms-client 1.2.3-SNAPSHOT abcdef1234"));
		}
	}

	@Test
	void renderOmitsCommitWhenVersionIsNotSnapshot() throws Exception {
		Path output = Files.createTempFile("cover-page-", ".pdf");

		new CoverPageRenderer(new ApplicationMetadata("icsforms-client", "1.2.3", "abcdef1234")).render(new AppData(),
				output);

		try (PDDocument pdf = Loader.loadPDF(output.toFile())) {
			String text = new PDFTextStripper().getText(pdf).replaceAll("\\s+", " ");
			assertTrue(text.contains("Prepared with: icsforms-client 1.2.3"));
			assertFalse(text.contains("abcdef1234"));
		}
	}
}
