/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.ClueLogEntry;
import org.sarmanagement.icsforms.pdf.ClueLogIcs233PdfRenderer;
import org.sarmanagement.icsforms.pdf.ClueLogPdfRenderer;
import org.sarmanagement.icsforms.pdf.Ics202PdfRenderer;
import org.sarmanagement.icsforms.pdf.Ics233CgPdfRenderer;
import org.sarmanagement.icsforms.pdf.PdfExportService;
import org.sarmanagement.icsforms.pdf.PdfFormRenderer;
import org.sarmanagement.icsforms.persistence.FileIncidentStore;
import org.sarmanagement.icsforms.validation.IncidentValidator;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Verifies transient print options, real PDF layouts, and incident isolation. */
class PdfPrintOptionsTest {
	@Test
	void defaultsAndClueMappingAreTransientDistinctAndRespectUnchecking() {
		PdfPrintOptionsPanel panel = new PdfPrintOptionsPanel(List.of("Clue Log",
				PdfPrintOptionsPanel.ADAPTED_CLUE_LOG, "ICS 233-CG"));
		JCheckBox adapt = components(panel, JCheckBox.class).get(0);
		assertFalse(adapt.isSelected());
		assertTrue(panel.isPortrait());
		assertEquals(List.of("Clue Log", "ICS 233-CG"),
				panel.selectedFormKeys(List.of("Clue Log", "ICS 233-CG")));
		adapt.setSelected(true);
		assertEquals(List.of(PdfPrintOptionsPanel.ADAPTED_CLUE_LOG, "ICS 233-CG"),
				panel.selectedFormKeys(List.of("Clue Log", PdfPrintOptionsPanel.ADAPTED_CLUE_LOG, "ICS 233-CG")));
		assertEquals(List.of("ICS 233-CG"), panel.selectedFormKeys(List.of("ICS 233-CG")));
		assertTrue(panel.selectedFormKeys(List.of()).isEmpty());
		components(panel, JComboBox.class).get(0).setSelectedItem("Landscape");
		assertFalse(panel.isPortrait());
		PdfPrintOptionsPanel reopened = new PdfPrintOptionsPanel(List.of("Clue Log",
				PdfPrintOptionsPanel.ADAPTED_CLUE_LOG));
		assertTrue(reopened.isPortrait());
		assertFalse(components(reopened, JCheckBox.class).get(0).isSelected());
	}

	@Test
	void unavailableAdaptedRendererCannotReplaceStandardClues() {
		for (List<String> keys : List.of(List.of("Clue Log"), List.of(PdfPrintOptionsPanel.ADAPTED_CLUE_LOG))) {
			PdfPrintOptionsPanel panel = new PdfPrintOptionsPanel(keys);
			JCheckBox adapt = components(panel, JCheckBox.class).get(0);
			assertFalse(adapt.isEnabled());
			adapt.setSelected(true);
			assertEquals(List.of("Clue Log"), panel.selectedFormKeys(List.of("Clue Log")));
		}
	}

	@Test
	void startupHasOnlyActionAndModeControls() throws Exception {
		Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());
		SwingUtilities.invokeAndWait(() -> {
			StartupDialog startup = new StartupDialog(null, null, false);
			try {
				assertTrue(components(startup, JCheckBox.class).isEmpty());
				assertTrue(components(startup, JComboBox.class).isEmpty());
				assertTrue(components(startup, ActionTrackerSettingsPanel.class).isEmpty());
				assertEquals(org.sarmanagement.icsforms.model.IncidentMode.SAR, startup.getChosenMode());
			} finally {
				startup.dispose();
			}
		});
	}

	@Test
	void cancelledGenerateDialogDoesNotModifyOrPersistPrintChoices() throws Exception {
		Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());
		Path directory = testDirectory();
		try (FileIncidentStore store = new FileIncidentStore(directory)) {
			AppData data = new AppData();
			data.setIcs233Portrait(true);
			data.setClueLogIcs233Enabled(true);
			var id = store.create(data);
			long revision = data.getRevision();
			SwingUtilities.invokeAndWait(() -> {
				MainFrame frame = new MainFrame(data, store, id, service(), new IncidentValidator(), directory);
				AtomicReference<Throwable> dialogFailure = new AtomicReference<>();
				Timer cancel = new Timer(100, event -> {
					for (Window window : Window.getWindows()) {
						if (window instanceof JDialog dialog && dialog.isShowing()
								&& "Export forms as PDF".equals(dialog.getTitle())) {
							try {
								PdfPrintOptionsPanel options = components(dialog, PdfPrintOptionsPanel.class).get(0);
								assertTrue(options.isPortrait());
								JCheckBox adapt = components(options, JCheckBox.class).get(0);
								assertFalse(adapt.isSelected());
								adapt.setSelected(true);
								components(options, JComboBox.class).get(0).setSelectedItem("Landscape");
								List<JCheckBox> selectors = components(dialog, JCheckBox.class);
								assertEquals(1, selectors.stream().filter(check -> check.getText().equals("Clue Log")).count());
								assertTrue(selectors.stream().filter(check -> check.getText().equals("Clue Log"))
										.allMatch(JCheckBox::isSelected));
								assertFalse(selectors.stream().anyMatch(check -> check.getText().equals(
										"Clue Log – Adapted ICS 233-CG")));
							} catch (Throwable failure) {
								dialogFailure.set(failure);
							} finally {
								components(dialog, JOptionPane.class).get(0).setValue(JOptionPane.CANCEL_OPTION);
							}
							return;
						}
					}
				});
				cancel.start();
				try {
					var open = MainFrame.class.getDeclaredMethod("openFormsExportDialog", Path.class);
					open.setAccessible(true);
					open.invoke(frame, directory);
					if (dialogFailure.get() != null) {
						throw new AssertionError(dialogFailure.get());
					}
					var controllerField = MainFrame.class.getDeclaredField("controller");
					controllerField.setAccessible(true);
					assertFalse(((AppController) controllerField.get(frame)).isDirty());
				} catch (ReflectiveOperationException exception) {
					throw new AssertionError(exception);
				} finally {
					cancel.stop();
					frame.dispose();
				}
			});
			assertTrue(data.isIcs233Portrait());
			assertTrue(data.isClueLogIcs233Enabled());
			assertEquals(revision, data.getRevision());
			assertEquals(revision, store.load(id).getRevision());
			assertTrue(store.load(id).isIcs233Portrait());
			assertTrue(store.load(id).isClueLogIcs233Enabled());
		} finally {
			deleteDirectory(directory);
		}
	}

	@Test
	void realIndividualAndCombinedPdfsHonorPrintOptionsWithoutChangingIncident() throws Exception {
		Path directory = testDirectory();
		try (FileIncidentStore store = new FileIncidentStore(directory)) {
			AppData data = new AppData();
			data.setOpenActionTrackerEnabled(true);
			data.setIcs233Portrait(true);
			data.setClueLogIcs233Enabled(true);
			ClueLogEntry clue = new ClueLogEntry();
			clue.setDescription("Print-only clue");
			data.setClueLogEntries(List.of(clue));
			var id = store.create(data);
			AppController controller = new AppController(data, store, id, service(), new IncidentValidator());
			data.getForm202().setIapPage("88");
			data.setForm205aIapPage("77");
			long revision = data.getRevision();
			for (boolean portrait : List.of(false, true)) {
				data.setIcs233Portrait(!portrait);
				data.setClueLogIcs233Enabled(true);
				for (boolean adapted : List.of(false, true)) {
					String clueKey = adapted ? PdfPrintOptionsPanel.ADAPTED_CLUE_LOG : "Clue Log";
					List<String> keys = List.of("ICS 202", "ICS 233-CG", clueKey);
					var individual = controller.exportSelectedForms(keys, directory, AppController.LinkSource.NONE,
							portrait);
					assertEquals(3, individual.size());
					try (PDDocument tracker = Loader.loadPDF(individual.get("ICS 233-CG").toFile());
							PDDocument clues = Loader.loadPDF(individual.get(clueKey).toFile())) {
						assertOrientation(tracker, 0, portrait);
						if (adapted) {
							assertOrientation(clues, 0, portrait);
						}
						assertClueText(clues, adapted);
					}
					Path bundle = controller.exportSelectedIapBundle(keys, directory, AppController.LinkSource.NONE,
							portrait);
					try (PDDocument document = Loader.loadPDF(bundle.toFile())) {
						assertEquals(4, document.getNumberOfPages());
						assertOrientation(document, 2, portrait);
						if (adapted) {
							assertOrientation(document, 3, portrait);
						}
						assertClueText(document, adapted);
					}
					assertSame(data, controller.getData());
					assertEquals(!portrait, data.isIcs233Portrait());
					assertTrue(data.isClueLogIcs233Enabled());
					assertEquals("88", data.getForm202().getIapPage());
					assertEquals("77", data.getForm205aIapPage());
					assertFalse(controller.isDirty());
					assertEquals(revision, data.getRevision());
					assertEquals(revision, store.load(id).getRevision());
					assertTrue(store.load(id).isIcs233Portrait());
					assertTrue(store.load(id).isClueLogIcs233Enabled());
				}
			}
			Path standardBundle = controller.exportSelectedIapBundle(null, directory, AppController.LinkSource.NONE,
					false);
			try (PDDocument document = Loader.loadPDF(standardBundle.toFile())) {
				assertClueText(document, false);
			}
		} finally {
			deleteDirectory(directory);
		}
	}

	@Test
	void failingIndividualAndCombinedExportsCannotLeakRendererMutations() throws Exception {
		Path directory = testDirectory();
		try (FileIncidentStore store = new FileIncidentStore(directory)) {
			AppData data = new AppData();
			var id = store.create(data);
			AppController controller = new AppController(data, store, id,
					new PdfExportService(new MutatingFailureRenderer()), new IncidentValidator());
			long revision = data.getRevision();
			data.setIcs233Portrait(true);
			data.setClueLogIcs233Enabled(true);
			data.getForm202().setIapPage("99");
			for (boolean combined : List.of(false, true)) {
				assertThrows(IOException.class, () -> {
					if (combined) {
						controller.exportSelectedIapBundle(List.of("ICS 202"), directory,
								AppController.LinkSource.NONE, false);
					} else {
						controller.exportSelectedForms(List.of("ICS 202", "ICS 202"), directory,
								AppController.LinkSource.NONE, false);
					}
				});
				assertTrue(data.isIcs233Portrait());
				assertTrue(data.isClueLogIcs233Enabled());
				assertEquals("99", data.getForm202().getIapPage());
				assertNotEquals("Renderer mutation", data.getIncidentContext().getIncidentName());
				assertFalse(controller.isDirty());
				assertEquals(revision, data.getRevision());
				assertEquals(revision, store.load(id).getRevision());
				assertFalse(store.load(id).isIcs233Portrait());
				assertFalse(store.load(id).isClueLogIcs233Enabled());
			}
		} finally {
			deleteDirectory(directory);
		}
	}

	/**
	 * Creates the real renderers used by generation dialog tests.
	 *
	 * @return export service for standard clues, adapted clues, tracker and ICS 202
	 */
	private PdfExportService service() {
		return new PdfExportService(new Ics202PdfRenderer(), new ClueLogPdfRenderer(), new Ics233CgPdfRenderer(),
				new ClueLogIcs233PdfRenderer());
	}

	/**
	 * Asserts geometry of an ICS 233 page.
	 *
	 * @param document exported document
	 * @param page zero-based page number
	 * @param portrait expected orientation
	 */
	private void assertOrientation(PDDocument document, int page, boolean portrait) {
		var box = document.getPage(page).getMediaBox();
		assertEquals(portrait, box.getHeight() > box.getWidth());
	}

	/**
	 * Asserts the chosen clue renderer's output text.
	 *
	 * @param document exported document
	 * @param adapted whether the adapted layout was selected
	 * @throws IOException when text cannot be extracted
	 */
	private void assertClueText(PDDocument document, boolean adapted) throws IOException {
		String text = new PDFTextStripper().getText(document);
		assertTrue(text.contains("Print-only clue"));
		assertEquals(adapted, text.contains("Adapted as a Clue Log"));
	}

	/**
	 * Finds descendants by Swing component type.
	 *
	 * @param parent root container
	 * @param type requested component class
	 * @param <T> component type
	 * @return matching descendants
	 */
	private static <T extends Component> List<T> components(Container parent, Class<T> type) {
		List<T> matches = new ArrayList<>();
		for (Component component : parent.getComponents()) {
			if (type.isInstance(component)) {
				matches.add(type.cast(component));
			}
			if (component instanceof Container container) {
				matches.addAll(components(container, type));
			}
		}
		return matches;
	}

	/**
	 * Creates an isolated project-local test directory.
	 *
	 * @return created directory
	 * @throws IOException when directory creation fails
	 */
	private Path testDirectory() throws IOException {
		return Files.createDirectories(Path.of("target", "print-options-" + UUID.randomUUID()));
	}

	/**
	 * Removes a project-local test directory and all exported files.
	 *
	 * @param directory directory to remove
	 * @throws IOException when cleanup fails
	 */
	private void deleteDirectory(Path directory) throws IOException {
		try (var files = Files.walk(directory)) {
			for (Path file : files.sorted(java.util.Comparator.reverseOrder()).toList()) {
				Files.deleteIfExists(file);
			}
		}
	}

	/** Simulates a failing renderer that tries to mutate its input document. */
	private static class MutatingFailureRenderer implements PdfFormRenderer {
		/** {@inheritDoc} */
		@Override
		public String getFormKey() {
			return "ICS 202";
		}

		/** {@inheritDoc} */
		@Override
		public void render(AppData data, Path outputFile) throws IOException {
			assertFalse(data.isIcs233Portrait());
			assertFalse(data.isClueLogIcs233Enabled());
			data.setIcs233Portrait(true);
			data.setClueLogIcs233Enabled(true);
			data.getForm202().setIapPage("Renderer page");
			data.getIncidentContext().setIncidentName("Renderer mutation");
			throw new IOException("Simulated renderer failure");
		}
	}
}
