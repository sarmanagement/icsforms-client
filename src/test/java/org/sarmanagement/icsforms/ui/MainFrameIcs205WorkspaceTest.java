/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.IncidentMode;
import org.sarmanagement.icsforms.persistence.FileIncidentStore;
import org.sarmanagement.icsforms.pdf.Ics205PdfRenderer;
import org.sarmanagement.icsforms.pdf.PdfExportService;
import org.sarmanagement.icsforms.validation.IncidentValidator;
import org.sarmanagement.icsforms.comms.RadioChannelCsvService;
import org.sarmanagement.icsforms.comms.ChirpCsvService;
import org.sarmanagement.icsforms.model.Ics205Form;
import org.sarmanagement.icsforms.persistence.LocalRepository;

import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.JFrame;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.Dialog;
import java.awt.Dimension;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** Display-backed optional workspace and plan reuse workflow regressions. */
class MainFrameIcs205WorkspaceTest {
	@Test
	void workspaceToggleKeepsDataInSarAndGenericAndTypedNavigationReopensIt() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		Path directory = Files.createDirectories(Path.of("target", "ics205-workspace-" + UUID.randomUUID()));
		try (FileIncidentStore store = new FileIncidentStore(directory)) {
			AppData data = new AppData();
			data.setIcs205Enabled(false);
			data.getForm205().getChannels().add(Ics205PanelTest.channel("Retained channel"));
			data.getForm205().setSpecialInstructions("Retained instructions");
			var id = store.create(data);
			SwingUtilities.invokeAndWait(() -> {
				MainFrame frame = new MainFrame(data, store, id, new PdfExportService(new Ics205PdfRenderer()),
						new IncidentValidator(), directory);
				AppController controller = (AppController) Ics205PanelTest.field(frame, "controller");
				try {
					frame.setVisible(true);
					JTabbedPane tabs = (JTabbedPane) Ics205PanelTest.field(frame, "tabs");
					JCheckBoxMenuItem toggle = (JCheckBoxMenuItem) Ics205PanelTest.field(frame, "communicationsPlanItem");
					assertEquals(-1, tabs.indexOfTab("ICS 205"));
					for (IncidentMode mode : IncidentMode.values()) {
						data.setIncidentMode(mode);
						refresh(frame);
						assertFalse(toggle.isSelected());
						toggle.doClick();
						assertTrue(data.isIcs205Enabled());
						assertTrue(tabs.indexOfTab("ICS 205") >= 0);
						toggle.doClick();
						assertFalse(data.isIcs205Enabled());
						assertEquals(-1, tabs.indexOfTab("ICS 205"));
						assertEquals("Retained channel", data.getForm205().getChannels().get(0).getChannelName());
						assertEquals("Retained instructions", data.getForm205().getSpecialInstructions());
					}
					try {
						assertTrue(Files.exists(controller.exportSelected("ICS 205", directory, AppController.LinkSource.NONE)));
					} catch (java.io.IOException exception) {
						throw new AssertionError(exception);
					}
					assertFalse(data.isIcs205Enabled());
					frame.openWorkspace(WorkspaceDestination.of(WorkspaceId.ICS_205));
					assertTrue(data.isIcs205Enabled());
					assertTrue(toggle.isSelected());
					assertEquals("ICS 205", tabs.getTitleAt(tabs.getSelectedIndex()));
					Component radioEditor = tabs.getSelectedComponent();
					JTextArea instructions = (JTextArea) Ics205PanelTest.field(radioEditor, "instructions");
					instructions.setText("Pending typed navigation");
					tabs.setTitleAt(tabs.getSelectedIndex(), "Renamed radio plan");
					frame.openWorkspace(WorkspaceDestination.of(WorkspaceId.ICS_204));
					frame.openWorkspace(WorkspaceDestination.of(WorkspaceId.ICS_205));
					assertSame(radioEditor, tabs.getSelectedComponent());
					assertEquals("Pending typed navigation", data.getForm205().getSpecialInstructions());
					instructions.setText("Pending View navigation");
					tabs.setTitleAt(tabs.getSelectedIndex(), "Renamed before toggle");
					toggle.doClick();
					toggle.doClick();
					assertSame(radioEditor, tabs.getSelectedComponent());
					assertEquals("Pending View navigation", data.getForm205().getSpecialInstructions());
					refresh(frame);
					assertSame(radioEditor, tabs.getSelectedComponent());
					assertEquals("ICS 205", tabs.getTitleAt(tabs.getSelectedIndex()));
					instructions.setText("Pending stable workspace navigation");
					tabs.setTitleAt(tabs.getSelectedIndex(), "Renamed before workspace navigation");
					frame.openWorkspace(WorkspaceDestination.of(WorkspaceId.ICS_204));
					assertEquals("Pending stable workspace navigation", data.getForm205().getSpecialInstructions());
					frame.openWorkspace(WorkspaceDestination.of(WorkspaceId.ICS_205));
					assertSame(radioEditor, tabs.getSelectedComponent());
					assertEquals("ICS 205", tabs.getTitleAt(tabs.getSelectedIndex()));
				} finally {
					((Timer) Ics205PanelTest.field(controller, "autosaveTimer")).stop();
					frame.dispose();
				}
			});
		} finally {
			deleteTree(directory);
		}
	}

	@Test
	void actualDocumentModalImportPreviewCancelPreservesPlan() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		Path directory = Files.createDirectories(Path.of("target", "ics205-preview-" + UUID.randomUUID()));
		try {
			Ics205Form imported = new Ics205Form();
			imported.getChannels().add(Ics205PanelTest.channel("Imported"));
			Path path = directory.resolve("incoming.csv");
			new RadioChannelCsvService().write(path, imported);
			AppData data = new AppData();
			Ics205Form original = data.getForm205();
			original.getChannels().add(Ics205PanelTest.channel("Retained"));
			AppController controller = new AppController(data, new LocalRepository(directory.resolve("incident.json")),
					new PdfExportService(new Ics205PdfRenderer()), new IncidentValidator());
			SwingUtilities.invokeAndWait(() -> {
				JFrame frame = new JFrame("Import preview owner");
				Ics205Panel panel = new Ics205Panel(controller);
				frame.setContentPane(panel);
				frame.setSize(900, 700);
				frame.setVisible(true);
				try {
					PreviewDialogs dialogs = new PreviewDialogs(path);
					new RadioChannelCsvDialogs(panel, controller, dialogs).importCsv();
					assertTrue(dialogs.previewShown);
					assertEquals(Dialog.ModalityType.DOCUMENT_MODAL, dialogs.modality);
					assertSame(original, data.getForm205());
					assertEquals("Retained", original.getChannels().get(0).getChannelName());
					assertFalse(controller.isDirty());
				} finally {
					((Timer) Ics205PanelTest.field(controller, "autosaveTimer")).stop();
					frame.dispose();
				}
			});
		} finally {
			deleteTree(directory);
		}
	}

	@Test
	void startupOffersIcs215PromotionWithoutPlanReuseCheckbox() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		Path directory = Files.createDirectories(Path.of("target", "ics205-startup-" + UUID.randomUUID()));
		try (FileIncidentStore store = new FileIncidentStore(directory)) {
			SwingUtilities.invokeAndWait(() -> {
				StartupDialog dialog = new StartupDialog(null, store, false);
				try {
					assertFalse(dialog.isBringForwardCommunicationsPlan());
					assertFalse(dialog.isPromotePlannedIcs215());
					JCheckBox checkbox = promoteIcs215Checkbox(dialog);
					assertNotNull(checkbox);
					assertEquals("Promote planned ICS 215 target period when advancing", checkbox.getText());
					checkbox.doClick();
					assertTrue(dialog.isPromotePlannedIcs215());
				} finally {
					dialog.dispose();
				}
			});
		} finally {
			deleteTree(directory);
		}
	}

	/**
	 * Invokes the existing private refresh lifecycle.
	 *
	 * @param frame incident frame.
	 */
	private static void refresh(MainFrame frame) {
		try {
			var method = MainFrame.class.getDeclaredMethod("refreshFromModel");
			method.setAccessible(true);
			method.invoke(frame);
		} catch (ReflectiveOperationException exception) {
			throw new AssertionError(exception);
		}
	}

	/**
	 * Finds the worksheet-promotion checkbox without opening startup dialogs.
	 *
	 * @param component component tree.
	 * @return checkbox or null.
	 */
	private static JCheckBox promoteIcs215Checkbox(Component component) {
		if (component instanceof JCheckBox box && box.getText().contains("ICS 215")) {
			return box;
		}
		if (component instanceof Container container) {
			for (Component child : container.getComponents()) {
				JCheckBox result = promoteIcs215Checkbox(child);
				if (result != null) {
					return result;
				}
			}
		}
		return null;
	}

	/**
	 * Removes build-directory fixtures after the store is closed.
	 *
	 * @param directory fixture directory.
	 * @throws java.io.IOException when cleanup fails.
	 */
	private static void deleteTree(Path directory) throws java.io.IOException {
		try (var files = Files.walk(directory)) {
			for (Path path : files.sorted(Comparator.reverseOrder()).toList()) {
				Files.deleteIfExists(path);
			}
		}
	}

	/** Uses a real controlled preview while avoiding any uncontrolled file chooser. */
	private static final class PreviewDialogs implements RadioChannelCsvDialogs.Dialogs {
		private final Path input;
		private boolean previewShown;
		private Dialog.ModalityType modality;

		/**
		 * Supplies a project-local native CSV fixture.
		 *
		 * @param input import path.
		 */
		private PreviewDialogs(Path input) {
			this.input = input;
		}

		/** {@inheritDoc} */
		@Override
		public RadioChannelCsvDialogs.ImportSource chooseImport(Component parent) {
			return new RadioChannelCsvDialogs.ImportSource(input, false);
		}

		/** {@inheritDoc} */
		@Override
		public RadioChannelCsvDialogs.ImportSelection preview(Component parent, Ics205Form imported) {
			var preview = new RadioChannelCsvDialogs.ImportPreviewPanel(imported);
			Timer cancel = new Timer(150, event -> {
				var window = SwingUtilities.getWindowAncestor(preview);
				if (window instanceof JDialog dialog) {
					previewShown = dialog.isShowing();
					modality = dialog.getModalityType();
				}
				var pane = (JOptionPane) SwingUtilities.getAncestorOfClass(JOptionPane.class, preview);
				if (pane != null) {
					pane.setValue(JOptionPane.CANCEL_OPTION);
				} else if (window != null) {
					window.dispose();
				}
			});
			cancel.setRepeats(false);
			cancel.start();
			try {
				assertFalse(UiSupport.showResizableConfirmDialog(parent, "Controlled channel preview",
						preview, new Dimension(900, 500)));
				return null;
			} finally {
				cancel.stop();
			}
		}

		/** {@inheritDoc} */
		@Override
		public boolean confirmProjection(Component parent, ChirpCsvService.ExportReport report) {
			throw new AssertionError("Unexpected export dialog");
		}

		/** {@inheritDoc} */
		@Override
		public Path chooseExport(Component parent, boolean chirp) {
			throw new AssertionError("Unexpected export chooser");
		}

		/** {@inheritDoc} */
		@Override
		public boolean confirmOverwrite(Component parent, Path path) {
			throw new AssertionError("Unexpected overwrite dialog");
		}

		/** {@inheritDoc} */
		@Override
		public void error(Component parent, String message) {
			throw new AssertionError(message);
		}
	}
}
