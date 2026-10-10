/*
 * Licensed under the GNU General Public License, version 3 or later.
 * See LICENSE for details.
 */
package org.sarmanagement.icsforms.pdf;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.sarmanagement.icsforms.model.AppData;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies optional export selection without changing existing form defaults. */
class OpenActionExportSelectionTest {
	@TempDir
	Path output;

	@Test
	void legacyDefaultsKeepStandardClueLog() throws IOException {
		List<String> rendered = new ArrayList<>();
		PdfExportService service = service(rendered);
		service.exportAll(new AppData(), output);
		assertEquals(List.of("ICS 202", "Clue Log"), rendered);
		assertTrue(service.supports("ICS 233-CG"));
	}

	@Test
	void trackerAndAdaptedCluesAreIndependentPreferences() throws IOException {
		List<String> rendered = new ArrayList<>();
		PdfExportService service = service(rendered);
		AppData data = new AppData();
		data.setOpenActionTrackerEnabled(true);
		service.exportAll(data, output);
		assertEquals(List.of("ICS 202", "Clue Log", "ICS 233-CG"), rendered);

		rendered.clear();
		data.setClueLogIcs233Enabled(true);
		service.exportAll(data, output);
		assertEquals(List.of("ICS 202", "ICS 233-CG", "Clue Log (ICS 233-CG adapted)"), rendered);

		rendered.clear();
		data.setOpenActionTrackerEnabled(false);
		service.exportAll(data, output);
		assertEquals(List.of("ICS 202", "Clue Log (ICS 233-CG adapted)"), rendered);
	}

	@Test
	void explicitExportsRemainAvailableRegardlessOfDefaults() throws IOException {
		List<String> rendered = new ArrayList<>();
		service(rendered).exportSelected("Clue Log (ICS 233-CG adapted)", new AppData(), output);
		assertEquals(List.of("Clue Log (ICS 233-CG adapted)"), rendered);
	}

	@Test
	void defaultIapHonorsPreferencesAndExplicitIapCanOverrideThem() throws IOException {
		PdfExportService service = new PdfExportService(new ClueLogPdfRenderer(),
				new Ics233CgPdfRenderer(), new ClueLogIcs233PdfRenderer());
		AppData data = new AppData();
		Path bundle = service.exportIapBundle(data, output);
		try (PDDocument document = Loader.loadPDF(bundle.toFile())) {
			assertEquals(2, document.getNumberOfPages(), "Cover plus standard clues only");
		}
		data.setOpenActionTrackerEnabled(true);
		data.setClueLogIcs233Enabled(true);
		bundle = service.exportIapBundle(data, output);
		try (PDDocument document = Loader.loadPDF(bundle.toFile())) {
			assertEquals(3, document.getNumberOfPages(), "Cover, tracker, adapted clues");
		}
		bundle = service.exportIapBundle(data, output, List.of("Clue Log"));
		try (PDDocument document = Loader.loadPDF(bundle.toFile())) {
			assertEquals(2, document.getNumberOfPages(), "Explicit standard clues remain supported");
		}
	}

	/**
	 * Creates a recording export service for all relevant formats.
	 *
	 * @param rendered destination list for rendered keys.
	 * @return recording service.
	 */
	private PdfExportService service(List<String> rendered) {
		return new PdfExportService(new RecordingRenderer("ICS 202", rendered),
				new RecordingRenderer("Clue Log", rendered),
				new RecordingRenderer("ICS 233-CG", rendered),
				new RecordingRenderer("Clue Log (ICS 233-CG adapted)", rendered));
	}

	/**
	 * Records calls without creating PDF files.
	 *
	 * @param key form identifier.
	 * @param rendered destination list for rendered keys.
	 */
	private record RecordingRenderer(String key, List<String> rendered) implements PdfFormRenderer {
		/** {@inheritDoc} */
		@Override
		public String getFormKey() {
			return key;
		}

		/** {@inheritDoc} */
		@Override
		public void render(AppData data, Path outputFile) {
			rendered.add(key);
		}
	}
}
