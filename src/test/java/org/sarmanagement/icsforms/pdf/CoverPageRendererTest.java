package org.sarmanagement.icsforms.pdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.IncidentContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

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

	@Test
	void renderIncludesConfiguredTimezoneLabelAndOperationalPeriodInConfiguredZone() throws Exception {
		Path output = Files.createTempFile("cover-page-timezone-", ".pdf");
		AppData data = new AppData();
		data.setUseSystemTimeZone(false);
		data.setConfiguredTimeZoneId("America/Denver");
		IncidentContext context = data.getIncidentContext();
		context.setIncidentName("Training Incident");
		context.setOperationalPeriodStart(LocalDateTime.parse("2026-01-01T16:00:00"));
		context.setOperationalPeriodEnd(LocalDateTime.parse("2026-01-01T19:30:00"));

		new CoverPageRenderer(new ApplicationMetadata("icsforms-client", "1.2.3", "abcdef1234")).render(data, output);

		try (PDDocument pdf = Loader.loadPDF(output.toFile())) {
			String text = new PDFTextStripper().getText(pdf).replaceAll("\\s+", " ");
			assertTrue(text.contains("All times in:"));
			assertTrue(text.contains("America/Denver"));
			assertTrue(text.contains("MST") || text.contains("MDT"));
			assertTrue(text.contains("Operational Period:"));
			assertTrue(text.contains("2026-01-01 09:00"));
			assertTrue(text.contains("2026-01-01 12:30"));
		}
	}
}
