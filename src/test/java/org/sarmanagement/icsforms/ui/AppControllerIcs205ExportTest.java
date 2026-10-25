/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.persistence.LocalRepository;
import org.sarmanagement.icsforms.pdf.Ics205PdfRenderer;
import org.sarmanagement.icsforms.pdf.PdfExportService;
import org.sarmanagement.icsforms.validation.IncidentValidator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** End-to-end controller export options remain transient and allow explicit hidden-plan output. */
class AppControllerIcs205ExportTest {
	@Test
	void explicitHiddenRadioExportUsesNativeIndividualAndPerExportBundleOrientation() throws Exception {
		Path directory = Files.createDirectories(Path.of("target", "ics205-controller-export-" + UUID.randomUUID()));
		AppData data = new AppData();
		data.setIcs205Enabled(false);
		data.getForm205().getChannels().add(Ics205PanelTest.channel("Command"));
		data.getForm205().setIapPage("88");
		data.getForm205().setPreparedBySignature("Supplied signature");
		try {
			AppController controller = new AppController(data, new LocalRepository(directory.resolve("incident.json")),
					new PdfExportService(new Ics205PdfRenderer()), new IncidentValidator());
			Path individual = controller.exportSelectedForms(List.of("ICS 205"), directory,
					AppController.LinkSource.NONE, true).get("ICS 205");
			assertNotNull(individual);
			assertOrientation(individual, true);
			Path portraitBundle = controller.exportSelectedIapBundle(List.of("ICS 205"), directory,
					AppController.LinkSource.NONE, true);
			assertOrientation(portraitBundle, false);
			Path nativeBundle = controller.exportSelectedIapBundle(List.of("ICS 205"), directory,
					AppController.LinkSource.NONE, true, true);
			assertOrientation(nativeBundle, true);
			assertEquals("88", data.getForm205().getIapPage());
			assertEquals("Supplied signature", data.getForm205().getPreparedBySignature());
			assertFalse(data.isIcs205Enabled());
			assertFalse(controller.isDirty());
		} finally {
			try (var files = Files.walk(directory)) {
				for (Path path : files.sorted(Comparator.reverseOrder()).toList()) {
					Files.deleteIfExists(path);
				}
			}
		}
	}

	/**
	 * Checks actual final form-page media, not just PDF rotation metadata.
	 *
	 * @param path exported file.
	 * @param landscape expected native landscape.
	 * @throws java.io.IOException when opening fails.
	 */
	private static void assertOrientation(Path path, boolean landscape) throws java.io.IOException {
		try (PDDocument document = Loader.loadPDF(path.toFile())) {
			var page = document.getPage(document.getNumberOfPages() - 1);
			assertEquals(landscape, page.getMediaBox().getWidth() > page.getMediaBox().getHeight());
			assertEquals(0, page.getRotation());
		}
	}
}
