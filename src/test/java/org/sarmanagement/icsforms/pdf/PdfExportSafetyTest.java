/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.pdf;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.ui.AppController;
import org.sarmanagement.icsforms.validation.IncidentValidator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies atomic PDF publication and complete cleanup after rendering or merging failures.
 */
class PdfExportSafetyTest {
	@TempDir
	Path directory;

	@Test
	void failedSingleExportPreservesExistingFileAndRemovesPartialOutput() throws Exception {
		Files.writeString(directory.resolve("ics-202.pdf"), "existing PDF");
		PdfExportService service = new PdfExportService(new FixtureRenderer("ICS 202", true, false));
		IOException exception = assertThrows(IOException.class,
				() -> service.exportSelected("ICS 202", new AppData(), directory));
		assertTrue(exception.getMessage().contains("ICS 202"));
		assertEquals("existing PDF", Files.readString(directory.resolve("ics-202.pdf")));
		assertEquals(List.of("ics-202.pdf"), outputNames());
	}

	@Test
	void failedSelectionPublishesNothingEvenAfterAnEarlierFormRendered() throws Exception {
		Files.writeString(directory.resolve("ics-202.pdf"), "original 202");
		Files.writeString(directory.resolve("ics-204.pdf"), "original 204");
		PdfExportService service = new PdfExportService(new FixtureRenderer("ICS 202", false, false),
				new FixtureRenderer("ICS 204", true, false));
		assertThrows(IOException.class, () -> service.exportAll(new AppData(), directory));
		assertEquals("original 202", Files.readString(directory.resolve("ics-202.pdf")));
		assertEquals("original 204", Files.readString(directory.resolve("ics-204.pdf")));
		assertEquals(List.of("ics-202.pdf", "ics-204.pdf"), outputNames());
	}

	@Test
	void bothControllerSelectionPathsPreserveExistingFilesWhenAnyRenderFails() throws Exception {
		Files.writeString(directory.resolve("ics-202.pdf"), "original 202");
		Files.writeString(directory.resolve("ics-204.pdf"), "original 204");
		PdfExportService service = new PdfExportService(new FixtureRenderer("ICS 202", false, false),
				new FixtureRenderer("ICS 204", true, false));
		AppController controller = new AppController(new AppData(), null, service, new IncidentValidator());
		List<String> keys = List.of("ICS 202", "ICS 204");
		assertThrows(IOException.class,
				() -> controller.exportSelectedForms(keys, directory, AppController.LinkSource.NONE));
		assertThrows(IOException.class,
				() -> controller.exportSelectedForms(keys, directory, AppController.LinkSource.NONE, true));
		assertEquals("original 202", Files.readString(directory.resolve("ics-202.pdf")));
		assertEquals("original 204", Files.readString(directory.resolve("ics-204.pdf")));
		assertEquals(List.of("ics-202.pdf", "ics-204.pdf"), outputNames());
	}

	@Test
	void controllerExposesTheBundleSummaryForTheCompletionDialog() throws Exception {
		PdfExportService service = new PdfExportService(new Ics202PdfRenderer());
		AppController controller = new AppController(new AppData(), null, service, new IncidentValidator());
		controller.exportSelectedIapBundle(List.of("ICS 202"), directory, AppController.LinkSource.NONE, true);
		assertTrue(controller.getLastPdfBundleSummary().contains("Included forms: ICS 202"));
	}

	@Test
	void publicationFailureRestoresEarlierOutputs() throws Exception {
		Files.writeString(directory.resolve("ics-202.pdf"), "original 202");
		Files.createDirectory(directory.resolve("ics-204.pdf"));
		PdfExportService service = new PdfExportService(new FixtureRenderer("ICS 202", false, false),
				new FixtureRenderer("ICS 204", false, false));
		assertThrows(IOException.class, () -> service.exportAll(new AppData(), directory));
		assertEquals("original 202", Files.readString(directory.resolve("ics-202.pdf")));
		assertTrue(Files.isDirectory(directory.resolve("ics-204.pdf")));
		assertEquals(List.of("ics-202.pdf", "ics-204.pdf"), outputNames());
	}

	@Test
	void failedRollbackRetainsTheOnlyOriginalAndReportsItsRecoveryPath() throws Exception {
		for (boolean runtimeFailure : List.of(false, true)) {
			Path original = directory.resolve("ics-202.pdf");
			Files.writeString(original, "original 202");
			Files.writeString(directory.resolve("ics-204.pdf"), "original 204");
			PdfExportService service = new FailedRollbackService(directory, runtimeFailure);
			IOException exception = assertThrows(IOException.class, () -> service.exportAll(new AppData(), directory));
			Path workspace;
			try (var paths = Files.list(directory)) {
				workspace = paths.filter(Files::isDirectory).findFirst().orElseThrow();
			}
			List<Path> recovery;
			try (var paths = Files.list(workspace)) {
				recovery = paths.toList();
			}
			assertEquals(1, recovery.size());
			Path backup = recovery.get(0);
			assertEquals("original 202", Files.readString(backup));
			assertEquals("original 204", Files.readString(directory.resolve("ics-204.pdf")));
			assertTrue(exception.getMessage().contains("Rollback was incomplete"));
			assertTrue(exception.getMessage().contains(backup.toAbsolutePath().toString()));
			assertTrue(exception.getMessage().contains(original.toAbsolutePath().toString()));
			assertEquals(1, exception.getCause().getCause().getSuppressed().length);
			Files.move(backup, original, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			Files.delete(workspace);
			assertEquals("original 202", Files.readString(original));
			assertEquals(List.of("ics-202.pdf", "ics-204.pdf"), outputNames());
		}
	}

	@Test
	void successfulSelectionPublishesAllCompleteFilesWithoutWorkspaceLeaks() throws Exception {
		Files.writeString(directory.resolve("ics-202.pdf"), "original 202");
		PdfExportService service = new PdfExportService(new FixtureRenderer("ICS 202", false, false),
				new FixtureRenderer("ICS 204", false, false));
		assertEquals(2, service.exportForms(List.of("ICS 202", "ICS 204"), new AppData(), directory).size());
		assertTrue(Files.readString(directory.resolve("ics-202.pdf"), java.nio.charset.StandardCharsets.ISO_8859_1)
				.startsWith("%PDF-"));
		assertEquals(List.of("ics-202.pdf", "ics-204.pdf"), outputNames());
	}

	@Test
	void bundleSuccessDoesNotOverwriteOrDeleteIndividualExports() throws Exception {
		Files.writeString(directory.resolve("ics-202.pdf"), "individual export");
		PdfExportService service = new PdfExportService(new Ics202PdfRenderer());
		service.exportIapBundle(new AppData(), directory);
		assertEquals("individual export", Files.readString(directory.resolve("ics-202.pdf")));
		assertTrue(service.getLastBundleSummary().contains("Included forms: ICS 202"));
		assertEquals(List.of("ics-202.pdf", "incident_IAP.pdf"), outputNames());
	}

	@Test
	void failedBundleIncludesRenderedAndFailedFormsAndPreservesAllExistingOutputs() throws Exception {
		Files.writeString(directory.resolve("ics-202.pdf"), "individual export");
		Files.writeString(directory.resolve("incident_IAP.pdf"), "existing bundle");
		PdfExportService service = new PdfExportService(new FixtureRenderer("ICS 202", false, false),
				new FixtureRenderer("ICS 204", true, false));
		IOException exception = assertThrows(IOException.class,
				() -> service.exportIapBundle(new AppData(), directory));
		assertTrue(exception.getMessage().contains("failed at ICS 204"));
		assertTrue(exception.getMessage().contains("Successfully rendered: ICS 202"));
		assertEquals(exception.getMessage(), service.getLastBundleSummary());
		assertEquals("individual export", Files.readString(directory.resolve("ics-202.pdf")));
		assertEquals("existing bundle", Files.readString(directory.resolve("incident_IAP.pdf")));
		assertEquals(List.of("ics-202.pdf", "incident_IAP.pdf"), outputNames());
	}

	@Test
	void failedMergeRemovesAllComponentsAndPreservesExistingBundle() throws Exception {
		Files.writeString(directory.resolve("incident_IAP.pdf"), "existing bundle");
		PdfExportService service = new PdfExportService(new FixtureRenderer("ICS 202", false, true));
		IOException exception = assertThrows(IOException.class,
				() -> service.exportIapBundle(new AppData(), directory));
		assertTrue(exception.getMessage().contains("bundle merge"));
		assertEquals("existing bundle", Files.readString(directory.resolve("incident_IAP.pdf")));
		assertEquals(List.of("incident_IAP.pdf"), outputNames());
	}

	@Test
	void failedCoverIsReportedAndItsWorkspaceIsRemoved() throws Exception {
		AppData data = new AppData();
		data.getIncidentContext().setIncidentName("Unsupported\u96EA");
		PdfExportService service = new PdfExportService(new Ics202PdfRenderer());
		IOException exception = assertThrows(IOException.class, () -> service.exportIapBundle(data, directory));
		assertTrue(exception.getMessage().contains("IAP cover"));
		assertTrue(exception.getMessage().contains("incidentName"));
		assertTrue(exception.getMessage().contains("U+96EA"));
		assertEquals(List.of(), outputNames());
	}

	@Test
	void outputSetupFailureHasABundleSummaryWithoutChangingExistingContent() throws Exception {
		Path blocked = directory.resolve("blocked");
		Files.writeString(blocked, "existing content");
		PdfExportService service = new PdfExportService(new Ics202PdfRenderer());
		IOException exception = assertThrows(IOException.class,
				() -> service.exportIapBundle(new AppData(), blocked));
		assertTrue(exception.getMessage().contains("failed at output setup"));
		assertEquals(exception.getMessage(), service.getLastBundleSummary());
		assertEquals("existing content", Files.readString(blocked));
		assertEquals(List.of("blocked"), outputNames());
	}

	/**
	 * Lists all remaining files, detecting leaked export workspaces too.
	 *
	 * @return sorted output names.
	 * @throws IOException when the output directory cannot be listed.
	 */
	private List<String> outputNames() throws IOException {
		try (var paths = Files.list(directory)) {
			return paths.map(path -> path.getFileName().toString()).sorted().toList();
		}
	}

	/**
	 * Simulates a filesystem publication error followed by a failed original-file restoration.
	 */
	private static final class FailedRollbackService extends PdfExportService {
		private final Path output;
		private final boolean runtimeFailure;

		/**
		 * Configures independent publication and rollback failures.
		 *
		 * @param output final destination directory.
		 * @param runtimeFailure whether restoration fails with an unchecked exception.
		 */
		private FailedRollbackService(Path output, boolean runtimeFailure) {
			super(new FixtureRenderer("ICS 202", false, false), new FixtureRenderer("ICS 204", false, false));
			this.output = output;
			this.runtimeFailure = runtimeFailure;
		}

		/** {@inheritDoc} */
		@Override
		protected void replaceFile(Path source, Path destination) throws IOException {
			if (destination.equals(output.resolve("ics-204.pdf"))) {
				throw new IOException("Simulated publication failure");
			}
			if (source.getFileName().toString().startsWith("backup-")) {
				if (runtimeFailure) {
					throw new IllegalStateException("Simulated unchecked restoration failure");
				}
				throw new IOException("Simulated restoration failure");
			}
			super.replaceFile(source, destination);
		}
	}

	/**
	 * Produces a valid PDF, a partial failed render, or invalid merge input.
	 *
	 * @param key fixture form identifier.
	 * @param fail whether rendering fails after writing partial bytes.
	 * @param invalid whether rendering returns an invalid PDF.
	 */
	private record FixtureRenderer(String key, boolean fail, boolean invalid) implements PdfFormRenderer {
		/** {@inheritDoc} */
		@Override
		public String getFormKey() {
			return key;
		}

		/** {@inheritDoc} */
		@Override
		public void render(AppData data, Path outputFile) throws IOException {
			Files.writeString(outputFile, "partial PDF");
			if (fail) {
				throw new IllegalArgumentException("Simulated unsupported character U+96EA in field objectives");
			}
			if (!invalid) {
				try (PDDocument document = new PDDocument()) {
					document.addPage(new PDPage());
					document.save(outputFile.toFile());
				}
			}
		}
	}
}
