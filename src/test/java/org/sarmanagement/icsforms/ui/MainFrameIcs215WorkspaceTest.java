/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.Ics215Form;
import org.sarmanagement.icsforms.model.IncidentMode;
import org.sarmanagement.icsforms.persistence.FileIncidentStore;
import org.sarmanagement.icsforms.pdf.PdfExportService;
import org.sarmanagement.icsforms.validation.IncidentValidator;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.GraphicsEnvironment;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** Covers ICS 215 optional visibility, retention, and typed navigation. */
class MainFrameIcs215WorkspaceTest {
	@TempDir
	Path directory;

	@Test
	void workspaceIsHiddenByDefaultAndRetainsDataInBothIncidentModes() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		try (FileIncidentStore store = new FileIncidentStore(directory)) {
			AppData data = new AppData();
			Ics215Form.ResourceHeading heading = new Ics215Form.ResourceHeading();
			heading.category = "Ground";
			data.getNextPeriodForm215().getResourceHeadings().add(heading);
			Ics215Form.WorkRow row = new Ics215Form.WorkRow();
			row.assignment = "Retained next-period assignment";
			data.getNextPeriodForm215().getWorkRows().add(row);
			var id = store.create(data);
			SwingUtilities.invokeAndWait(() -> {
				MainFrame frame = new MainFrame(data, store, id, new PdfExportService(),
						new IncidentValidator(), directory);
				AppController controller = (AppController) Ics205PanelTest.field(frame, "controller");
				JTabbedPane tabs = (JTabbedPane) Ics205PanelTest.field(frame, "tabs");
				JCheckBoxMenuItem toggle = (JCheckBoxMenuItem) Ics205PanelTest.field(frame, "ics215Item");
				try {
					frame.setVisible(true);
					for (IncidentMode mode : IncidentMode.values()) {
						data.setIncidentMode(mode);
						refresh(frame);
						assertFalse(data.isIcs215Enabled());
						assertFalse(toggle.isSelected());
						assertEquals(-1, tabs.indexOfTab(WorkspaceId.ICS_215.displayLabel()));
						toggle.doClick();
						assertTrue(data.isIcs215Enabled());
						assertTrue(tabs.indexOfTab(WorkspaceId.ICS_215.displayLabel()) >= 0);
						toggle.doClick();
						assertFalse(data.isIcs215Enabled());
						assertEquals(-1, tabs.indexOfTab(WorkspaceId.ICS_215.displayLabel()));
						assertEquals("Retained next-period assignment",
								data.getNextPeriodForm215().getWorkRows().get(0).assignment);
					}
					frame.openWorkspace(WorkspaceDestination.of(WorkspaceId.ICS_215));
					assertTrue(data.isIcs215Enabled());
					assertEquals("ICS 215 Operational Planning", tabs.getTitleAt(tabs.getSelectedIndex()));
					assertEquals("ICS-215 Operational Planning",
							MainFrame.workspaceTabLabel(WorkspaceId.ICS_215, "ICS 215", true));
					assertFalse(MainFrame.isExportFormSelectedByDefault("ICS 215", data));
				} finally {
					((Timer) Ics205PanelTest.field(controller, "autosaveTimer")).stop();
					frame.dispose();
				}
			});
		}
	}

	/**
	 * Invokes the frame's model refresh for incident-mode transitions.
	 *
	 * @param frame active incident frame.
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
}
