/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.pdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.ClueLogEntry;
import org.sarmanagement.icsforms.model.TCard;
import org.sarmanagement.icsforms.model.SarTaskAssignment;
import org.sarmanagement.icsforms.persistence.LocalRepository;
import org.sarmanagement.icsforms.ui.AppController;
import org.sarmanagement.icsforms.ui.Ics202Panel;
import org.sarmanagement.icsforms.ui.MainFrame;
import org.sarmanagement.icsforms.validation.IncidentValidator;

import javax.swing.JTextArea;
import javax.swing.JLabel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.GraphicsEnvironment;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * Verifies logical whitespace, actionable encoding errors, and balanced PDF text scopes.
 */
class PdfTextTest {
	@TempDir
	Path directory;

	@Test
	void logicalBreaksAndTabsNormalizeBeforeWrapping() {
		TestRenderer renderer = new TestRenderer();
		assertEquals(List.of("one", "two", "three", "", "four    five", ""),
				renderer.wrap("one\r\ntwo\rthree\n\nfour\tfive\n", 80));
		assertEquals(List.of("abcdefghij"), renderer.wrap("abcdefghij", 4));
		assertEquals(List.of("Leader/Handler"), renderer.wrap("Leader/Handler", 12));
		assertEquals(List.of("a b"), renderer.wrap("a\tb", 4));
		assertEquals("one two three    four", PdfText.singleLine("one\r\ntwo\rthree\tfour"));
	}

	@Test
	void unwantedControlsAndUnsupportedPrintableCharactersAreNeverDiscarded() {
		for (String value : List.of("before\u0000after", "before\u000Bafter", "before\u007Fafter",
				"before\u0085after", "before\u200Bafter", "before\u96EAafter", "before\uD83D\uDD0Eafter")) {
			IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
					() -> PdfText.validate(value, "ICS 202", "Objectives"));
			assertTrue(exception.getMessage().contains("ICS 202"));
			assertTrue(exception.getMessage().contains("Objectives"));
			assertTrue(exception.getMessage().contains("U+"));
			assertTrue(exception.getMessage().contains("remove or replace"));
		}
		PdfText.validate("Café – safety\r\n\tbriefing", "ICS 202", "Objectives");
	}

	@Test
	void rendererRejectsOriginalTextWithFormAndFieldBeforePdfOutput() {
		AppData data = new AppData();
		String objectives = "First\nSecond\u0000objective";
		data.getForm202().setObjectives(List.of(objectives));
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
				() -> new Ics202PdfRenderer().render(data, directory.resolve("form.pdf")));
		assertTrue(exception.getMessage().contains("form202.objectives[1]"));
		assertTrue(exception.getMessage().contains("U+0000"));
		assertEquals(List.of(objectives), data.getForm202().getObjectives());
	}

	@Test
	void multilineObjectivesRenderWithoutChangingStoredContent() throws Exception {
		AppData data = new AppData();
		String objective = "First objective\r\nSecond objective\rThird\tobjective\nFourth objective";
		data.getForm202().setObjectives(List.of(objective));
		data.getIncidentContext().setIncidentName("Inline\tname\r\ncontinued");
		Path output = directory.resolve("form.pdf");
		new Ics202PdfRenderer().render(data, output);
		try (PDDocument document = Loader.loadPDF(output.toFile())) {
			String text = new PDFTextStripper().getText(document);
			assertTrue(text.contains("First objective"));
			assertTrue(text.contains("Second objective"));
			assertTrue(text.contains("Third"));
			assertTrue(text.contains("Fourth objective"));
		}
		assertEquals(List.of(objective), data.getForm202().getObjectives());
	}

	@Test
	void everyRendererNormalizesInlineMetadataAndPreservesItsEncodingPolicy() throws Exception {
		for (PdfFormRenderer renderer : renderers()) {
			AppData data = new AppData();
			String name = "Incident\tOne\r\nSecond line\rThird line";
			data.getIncidentContext().setIncidentName(name);
			ClueLogEntry clue = new ClueLogEntry();
			clue.setDescription(name);
			data.getClueLogEntries().add(clue);
			Path output = directory.resolve(renderer.getClass().getSimpleName() + ".pdf");
			renderer.render(data, output);
			try (PDDocument document = Loader.loadPDF(output.toFile())) {
				String text = new PDFTextStripper().getText(document).replaceAll("\\s+", " ");
				assertTrue(text.contains("Incident One"), renderer.getFormKey());
			}
			assertEquals(name, data.getIncidentContext().getIncidentName());
			data.getIncidentContext().setIncidentName("Incident\uD83D\uDD0E");
			clue.setDescription("Incident\uD83D\uDD0E");
			if (renderer instanceof Ics233CgPdfRenderer || renderer instanceof ClueLogIcs233PdfRenderer) {
				renderer.render(data, output);
				try (PDDocument document = Loader.loadPDF(output.toFile())) {
					assertTrue(new PDFTextStripper().getText(document).contains("\\u{1F50E}"),
							renderer.getFormKey());
				}
				data.getIncidentContext().setIncidentName("Incident\u0000");
				IllegalArgumentException control = assertThrows(IllegalArgumentException.class,
						() -> renderer.render(data, output));
				assertTrue(control.getMessage().contains("incidentContext.incidentName"), renderer.getFormKey());
				assertTrue(control.getMessage().contains("U+0000"), renderer.getFormKey());
				continue;
			}
			IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
					() -> renderer.render(data, output));
			String field = renderer instanceof ClueLogPdfRenderer
					? "clueLogEntries[1].description" : "incidentContext.incidentName";
			assertTrue(exception.getMessage().contains(field), renderer.getFormKey());
			assertTrue(exception.getMessage().contains("U+1F50E"), renderer.getFormKey());
		}
	}

	@Test
	void resourceFieldEncodingFailureIdentifiesItsSource() {
		AppData data = new AppData();
		TCard card = new TCard();
		card.setPersonName("Searcher\u96EA");
		data.getTCards().add(card);
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
				() -> new Ics205aPdfRenderer().render(data, directory.resolve("contacts.pdf")));
		assertTrue(exception.getMessage().contains("personName"));
		assertTrue(exception.getMessage().contains("U+96EA"));
	}

	@Test
	void assignmentExportIgnoresUnprintedSupportingNotesButRejectsPrintedInstructions() throws Exception {
		AppData data = new AppData();
		TCard card = new TCard();
		card.setNotes("Unprinted card notes \uD83D\uDD0E");
		data.getTCards().add(card);
		SarTaskAssignment task = new SarTaskAssignment();
		task.setDebriefNotes("Unprinted task debrief \uD83D\uDD0E");
		data.getSarTaskAssignments().add(task);
		PdfExportService service = new PdfExportService(new Ics204PdfRenderer());
		Path output = service.exportSelected("ICS 204", data, directory);
		try (PDDocument document = Loader.loadPDF(output.toFile())) {
			assertTrue(document.getNumberOfPages() > 0);
		}
		data.getForm204().setSpecialInstructions("Printed instruction \u96EA");
		IOException exception = assertThrows(IOException.class,
				() -> service.exportSelected("ICS 204", data, directory));
		assertTrue(exception.getMessage().contains("form204.specialInstructions"));
		assertTrue(exception.getMessage().contains("U+96EA"));
		assertEquals("Unprinted card notes \uD83D\uDD0E", card.getNotes());
		assertEquals("Unprinted task debrief \uD83D\uDD0E", task.getDebriefNotes());
		try (PDDocument document = Loader.loadPDF(output.toFile())) {
			assertTrue(document.getNumberOfPages() > 0);
		}
	}

	@Test
	void supportingModelsAreProjectedToDirectoryAndOrganizationFields() throws Exception {
		AppData data = new AppData();
		TCard card = new TCard();
		card.setPersonName("Searcher");
		card.setNotes("Unprinted \u96EA");
		data.getTCards().add(card);
		SarTaskAssignment task = new SarTaskAssignment();
		task.setResourceIdentifier("Team Alpha");
		task.setAssignmentTeamNumber("Unprinted fallback \u96EA");
		task.setDebriefNotes("Unprinted \u96EA");
		task.setLeader("Unprinted unlabeled supervisor \u96EA");
		data.getSarTaskAssignments().add(task);
		for (PdfFormRenderer renderer : List.of(new Ics201PdfRenderer(), new Ics203PdfRenderer(),
				new Ics205aPdfRenderer(), new Ics207PdfRenderer())) {
			renderer.render(data, directory.resolve(renderer.getClass().getSimpleName() + ".pdf"));
		}
		task.setDivision("Alpha");
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
				() -> new Ics203PdfRenderer().render(data, directory.resolve("invalid203.pdf")));
		assertTrue(exception.getMessage().contains("sarTaskAssignments[1].leader"));
		assertTrue(exception.getMessage().contains("U+96EA"));
	}

	@Test
	void taskExportValidatesOnlyCluesActuallyAssociatedWithThatTask() throws Exception {
		AppData data = new AppData();
		SarTaskAssignment task = new SarTaskAssignment();
		task.setAssignmentId("task-a");
		data.getSarTaskAssignments().add(task);
		ClueLogEntry clue = new ClueLogEntry();
		clue.setAssignmentId("other-task");
		clue.setDescription("Unrelated clue \u96EA");
		data.getClueLogEntries().add(clue);
		new SarTaskAssignmentPdfRenderer().render(data, directory.resolve("task.pdf"));
		clue.setAssignmentId("task-a");
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
				() -> new SarTaskAssignmentPdfRenderer().render(data, directory.resolve("invalid-task.pdf")));
		assertTrue(exception.getMessage().contains("clueLogEntries[1].description"));
		assertTrue(exception.getMessage().contains("U+96EA"));
	}

	@Test
	void textBlocksRemainBalancedAfterRuntimeAndIoFailures() throws Exception {
		try (PDDocument document = new PDDocument()) {
			PDPage page = new PDPage();
			document.addPage(page);
			try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
				assertThrows(IllegalArgumentException.class, () -> {
					try (PdfText.TextBlock textBlock = PdfText.begin(stream)) {
						stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10f);
						stream.showText("\u96EA");
					}
				});
				assertThrows(IOException.class, () -> {
					try (PdfText.TextBlock textBlock = PdfText.begin(stream)) {
						throw new IOException("Simulated drawing failure");
					}
				});
				stream.addRect(10f, 10f, 20f, 20f);
				stream.stroke();
				try (PdfText.TextBlock textBlock = PdfText.begin(stream)) {
					stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10f);
					stream.showText("Recovered");
				}
			}
			assertTrue(new PDFTextStripper().getText(document).contains("Recovered"));
		}
	}

	@Test
	void objectiveEditorWarnsOnInputAndPasteWithoutRejectingOrTrimmingText() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			AppData data = new AppData();
			data.getForm202().setObjectives(List.of("Original objective"));
			AppController controller = new AppController(data, null, new PdfExportService(), new IncidentValidator());
			Ics202Panel panel = new Ics202Panel(controller);
			panel.refreshFromModel();
			try {
				JTextArea area = field(panel, "objectivesArea", JTextArea.class);
				JLabel warning = field(panel, "objectiveWarning", JLabel.class);
				Color originalBackground = area.getBackground();
				area.append("\u96EA");
				assertTrue(warning.getText().contains("U+96EA"));
				assertTrue(area.getToolTipText().contains("remove or replace"));
				assertNotEquals(originalBackground, area.getBackground());
				assertEquals(List.of("Original objective"), data.getForm202().getObjectives());
				area.selectAll();
				String pasted = " \u0000Changed objective\u0000 \n\u000B\n\tTabbed objective\t";
				assertTrue(area.getTransferHandler().importData(area, new StringSelection(pasted)));
				assertTrue(warning.getText().contains("U+0000"));
				assertEquals(pasted, area.getText());
				assertDoesNotThrow(panel::pushToModel);
				assertEquals(List.of(" \u0000Changed objective\u0000 ", "\u000B", "\tTabbed objective\t"),
						data.getForm202().getObjectives());
				panel.refreshFromModel();
				assertEquals(pasted, area.getText());
				assertTrue(warning.getText().contains("U+0000"));
				area.setText("Valid objective");
				assertEquals(" ", warning.getText());
				assertNull(area.getToolTipText());
				assertEquals(originalBackground, area.getBackground());
			} catch (ReflectiveOperationException exception) {
				throw new AssertionError(exception);
			}
		});
	}

	@Test
	void objectiveWarningAllowsRealTabSwitchAndIncidentSaveWhilePdfExportStillRejectsControls() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		SwingUtilities.invokeAndWait(() -> {
			AppData data = new AppData();
			LocalRepository repository = new LocalRepository(directory.resolve("incident.json"));
			PdfExportService service = new PdfExportService(new Ics202PdfRenderer());
			MainFrame frame = new MainFrame(data, repository, service, new IncidentValidator(), directory);
			try {
				Ics202Panel panel = field(frame, "ics202Panel", Ics202Panel.class);
				JTabbedPane tabs = field(frame, "tabs", JTabbedPane.class);
				tabs.setSelectedComponent(panel);
				JTextArea area = field(panel, "objectivesArea", JTextArea.class);
				String objective = " \u0000Pasted \u96EA objective\u0000 ";
				area.selectAll();
				assertTrue(area.getTransferHandler().importData(area, new StringSelection(objective)));
				assertDoesNotThrow(() -> tabs.setSelectedIndex(0));
				assertEquals(List.of(objective), data.getForm202().getObjectives());
				Method saveBeforeSwitch = MainFrame.class.getDeclaredMethod("saveBeforeSwitch");
				saveBeforeSwitch.setAccessible(true);
				assertTrue((Boolean) saveBeforeSwitch.invoke(frame));
				AppData saved = repository.loadOrDefault();
				assertEquals(List.of(objective), saved.getForm202().getObjectives());
				IOException exception = assertThrows(IOException.class,
						() -> service.exportSelected("ICS 202", saved, directory));
				assertTrue(exception.getMessage().contains("form202.objectives[1]"));
				assertTrue(exception.getMessage().contains("U+0000"));
			} catch (ReflectiveOperationException exception) {
				throw new AssertionError(exception);
			} finally {
				frame.dispose();
			}
		});
	}

	/**
	 * Reads an editor's private Swing component for direct regression assertions.
	 *
	 * @param target containing editor.
	 * @param name field name.
	 * @param type expected component type.
	 * @param <T> component type.
	 * @return component stored in the requested field.
	 * @throws ReflectiveOperationException when the field cannot be accessed.
	 */
	private static <T> T field(Object target, String name, Class<T> type) throws ReflectiveOperationException {
		Field field = target.getClass().getDeclaredField(name);
		field.setAccessible(true);
		return type.cast(field.get(target));
	}

	/**
	 * Exposes the shared character wrapping implementation to tests.
	 */
	private static final class TestRenderer extends AbstractPdfRenderer {
	}

	/**
	 * Supplies every supported text-rendering form and the bundle cover.
	 *
	 * @return renderers exercising shared whitespace and encoding behavior.
	 */
	private static List<PdfFormRenderer> renderers() {
		return List.of(new Ics201PdfRenderer(), new Ics202PdfRenderer(), new Ics203PdfRenderer(),
				new Ics204PdfRenderer(), new Ics205aPdfRenderer(), new Ics207PdfRenderer(), new Ics208PdfRenderer(),
				new Ics214PdfRenderer(), new Ics215aPdfRenderer(), new Ics233CgPdfRenderer(),
				new SarTaskAssignmentPdfRenderer(), new ClueLogPdfRenderer(), new ClueLogIcs233PdfRenderer(),
				new CoverPageRenderer());
	}
}
