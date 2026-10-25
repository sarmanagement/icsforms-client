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

import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** Display-backed optional workspace and explicit carry-forward control regressions. */
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
				try {
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
					frame.openWorkspace(WorkspaceDestination.of(WorkspaceId.ICS_205));
					assertTrue(data.isIcs205Enabled());
					assertTrue(toggle.isSelected());
					assertEquals("ICS 205", tabs.getTitleAt(tabs.getSelectedIndex()));
					refresh(frame);
					assertEquals("ICS 205", tabs.getTitleAt(tabs.getSelectedIndex()));
				} finally {
					AppController controller = (AppController) Ics205PanelTest.field(frame, "controller");
					((Timer) Ics205PanelTest.field(controller, "autosaveTimer")).stop();
					frame.dispose();
				}
			});
		} finally {
			deleteTree(directory);
		}
	}

	@Test
	void startupCarryForwardCheckboxIsExplicitAndUnchecked() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		Path directory = Files.createDirectories(Path.of("target", "ics205-startup-" + UUID.randomUUID()));
		try (FileIncidentStore store = new FileIncidentStore(directory)) {
			SwingUtilities.invokeAndWait(() -> {
				StartupDialog dialog = new StartupDialog(null, store, false);
				try {
					assertFalse(dialog.isBringForwardCommunicationsPlan());
					JCheckBox checkbox = carryForwardCheckbox(dialog);
					assertNotNull(checkbox);
					assertEquals("Bring forward communications plan (ICS 205)", checkbox.getText());
					checkbox.doClick();
					assertTrue(dialog.isBringForwardCommunicationsPlan());
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
	 * Finds the carry-forward checkbox without opening startup dialogs.
	 *
	 * @param component component tree.
	 * @return checkbox or null.
	 */
	private static JCheckBox carryForwardCheckbox(Component component) {
		if (component instanceof JCheckBox box && box.getText().contains("ICS 205")) {
			return box;
		}
		if (component instanceof Container container) {
			for (Component child : container.getComponents()) {
				JCheckBox result = carryForwardCheckbox(child);
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
}
