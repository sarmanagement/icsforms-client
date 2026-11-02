/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.Ics214Form;
import org.sarmanagement.icsforms.persistence.LocalRepository;
import org.sarmanagement.icsforms.pdf.PdfExportService;
import org.sarmanagement.icsforms.validation.IncidentValidator;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.GraphicsEnvironment;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** Verifies dashboard navigation and configurable tab presentation. */
class MainFrameDashboardTest {
	@Test
	void descriptiveLabelsPreserveCompactDefaultsAndLogOwnership() {
		assertEquals("ICS 202", MainFrame.workspaceTabLabel(WorkspaceId.ICS_202, "ICS 202", false));
		assertEquals("ICS-202 Objectives", MainFrame.workspaceTabLabel(WorkspaceId.ICS_202, "ICS 202", true));
		assertEquals("ICS-204 Assignments", MainFrame.workspaceTabLabel(WorkspaceId.ICS_204, "ICS 204", true));
		assertEquals("ICS-205 Comms", MainFrame.workspaceTabLabel(WorkspaceId.ICS_205, "ICS 205", true));
		assertEquals("ICS 215", MainFrame.workspaceTabLabel(WorkspaceId.ICS_215, "ICS 215", false));
		assertEquals("ICS-215 Operational Planning",
				MainFrame.workspaceTabLabel(WorkspaceId.ICS_215, "ICS 215", true));
		assertEquals("ICS-214 Log – Team A",
				MainFrame.workspaceTabLabel(WorkspaceId.ACTIVITY_LOG, "ICS 214 – Team A", true));
		assertEquals("ICS 214 – ICP",
				MainFrame.workspaceTabLabel(WorkspaceId.ACTIVITY_LOG, "ICS 214 – ICP", false));
		assertEquals("Safety", MainFrame.workspaceTabLabel(WorkspaceId.SAFETY, "Safety", true));
		var log = new org.sarmanagement.icsforms.model.Ics214Form();
		assertEquals("ICS-214 Log – ICP", MainFrame.activityLogTabLabel(log, "ICS 214 – ICP", true));
		log.setName("Responder A");
		assertEquals("ICS-214 Log – Responder A", MainFrame.activityLogTabLabel(log, "ICS 214 – ICP", true));
		assertEquals("ICS 214 – ICP", MainFrame.activityLogTabLabel(log, "ICS 214 – ICP", false));
	}

	@Test
	void tabLabelConfigurationUpdatesExistingEditorsAndPersistsChoice() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		var directory = Files.createTempDirectory("icsforms-tab-labels");
		var preferences = java.util.prefs.Preferences.userNodeForPackage(MainFrame.class);
		String previous = preferences.get("descriptive-workspace-tabs", null);
		SwingUtilities.invokeAndWait(() -> {
			MainFrame frame = new MainFrame(new AppData(), new LocalRepository(directory.resolve("incident.json")),
					new PdfExportService(), new IncidentValidator(), directory);
			try {
				JCheckBoxMenuItem setting = null;
				for (int menuIndex = 0; menuIndex < frame.getJMenuBar().getMenuCount(); menuIndex++) {
					JMenu menu = frame.getJMenuBar().getMenu(menuIndex);
					if ("Configuration".equals(menu.getText())) {
						setting = (JCheckBoxMenuItem) menu.getItem(0);
					}
				}
				assertTrue(setting != null);
				JTabbedPane tabs = (JTabbedPane) Ics205PanelTest.field(frame, "tabs");
				Object editor = Ics205PanelTest.field(frame, "ics202Panel");
				int index = tabs.indexOfComponent((java.awt.Component) editor);
				for (boolean descriptive : new boolean[]{true, false}) {
					if (setting.isSelected() != descriptive) {
						setting.doClick();
					}
					assertEquals(descriptive ? "ICS-202 Objectives" : "ICS 202", tabs.getTitleAt(index));
					assertSame(editor, tabs.getComponentAt(index));
					assertEquals(descriptive, preferences.getBoolean("descriptive-workspace-tabs", !descriptive));
				}
			} finally {
				if (previous == null) {
					preferences.remove("descriptive-workspace-tabs");
				} else {
					preferences.put("descriptive-workspace-tabs", previous);
				}
				AppController controller = (AppController) Ics205PanelTest.field(frame, "controller");
				((Timer) Ics205PanelTest.field(controller, "autosaveTimer")).stop();
				frame.dispose();
			}
		});
	}

	@Test
	void enabledIcs215FollowsAllVisibleActivityLogs() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		var directory = Files.createTempDirectory("icsforms-215-tab-order");
		AppData data = new AppData();
		data.setIcs215Enabled(true);
		data.getActivityLogs().add(new Ics214Form());
		data.getActivityLogs().add(new Ics214Form());
		SwingUtilities.invokeAndWait(() -> {
			MainFrame frame = new MainFrame(data, new LocalRepository(directory.resolve("incident.json")),
					new PdfExportService(), new IncidentValidator(), directory);
			try {
				JTabbedPane tabs = (JTabbedPane) Ics205PanelTest.field(frame, "tabs");
				int ics215Index = tabs.indexOfComponent((java.awt.Component) Ics205PanelTest.field(frame, "ics215Panel"));
				int lastLogIndex = -1;
				for (int index = 0; index < tabs.getTabCount(); index++) {
					if (tabs.getComponentAt(index) instanceof Ics214Panel) {
						lastLogIndex = index;
					}
				}
				assertTrue(ics215Index > lastLogIndex);
			} finally {
				AppController controller = (AppController) Ics205PanelTest.field(frame, "controller");
				((Timer) Ics205PanelTest.field(controller, "autosaveTimer")).stop();
				frame.dispose();
			}
		});
	}

	@Test
	void bringForwardMenuRequiresAnAvailableSource() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		var directory = Files.createTempDirectory("icsforms-bring-forward-menu");
		SwingUtilities.invokeAndWait(() -> {
			for (boolean archivedPlan : new boolean[]{false, true}) {
				AppData data = new AppData();
				if (archivedPlan) {
					data.getForm205().setSpecialInstructions("Contact command before changing channels.");
					data.advanceToNewOperationalPeriod();
				}
				MainFrame frame = new MainFrame(data, new LocalRepository(directory.resolve("incident.json")),
						new PdfExportService(), new IncidentValidator(), directory);
				try {
					JMenuItem item = (JMenuItem) Ics205PanelTest.field(frame, "bringForwardItem");
					assertEquals("Bring Forward…", item.getText());
					assertEquals(archivedPlan, item.isEnabled());
					assertSame(frame.getJMenuBar().getMenu(0),
							((javax.swing.JPopupMenu) item.getParent()).getInvoker());
				} finally {
					AppController controller = (AppController) Ics205PanelTest.field(frame, "controller");
					((Timer) Ics205PanelTest.field(controller, "autosaveTimer")).stop();
					frame.dispose();
				}
			}
		});
	}

	@Test
	void rolePanelsUseStableWorkspaceOrderAndIgnoreLegacyDashboardPreference() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		var directory = Files.createTempDirectory("icsforms-stable-workspace-order");
		var preferences = java.util.prefs.Preferences.userNodeForPackage(MainFrame.class);
		String previous = preferences.get("workspace-dashboard", null);
		try {
			preferences.put("workspace-dashboard", "Operations");
			SwingUtilities.invokeAndWait(() -> {
				MainFrame frame = new MainFrame(new AppData(), new LocalRepository(directory.resolve("incident.json")),
						new PdfExportService(), new IncidentValidator(), directory);
				try {
					JMenu view = frame.getJMenuBar().getMenu(1);
					List<String> labels = new java.util.ArrayList<>();
					for (int index = 0; index < view.getItemCount(); index++) {
						JMenuItem item = view.getItem(index);
						labels.add(item == null ? "" : item.getText());
					}
					assertTrue(labels.contains("Role Panels"));
					assertTrue(!labels.contains("Dashboard"));
					JTabbedPane tabs = (JTabbedPane) Ics205PanelTest.field(frame, "tabs");
					assertEquals("Shared", tabs.getTitleAt(0));
					assertEquals("Org Chart", tabs.getTitleAt(1));
				} finally {
					AppController controller = (AppController) Ics205PanelTest.field(frame, "controller");
					((Timer) Ics205PanelTest.field(controller, "autosaveTimer")).stop();
					frame.dispose();
				}
			});
		} finally {
			if (previous == null) {
				preferences.remove("workspace-dashboard");
			} else {
				preferences.put("workspace-dashboard", previous);
			}
		}
	}

	@Test
	void workspaceDestinationsSeparateStableIdsFromDisplayLabelsAndTargetNestedPlans() {
		assertEquals(WorkspaceId.HUMAN_MEDICAL_PLAN, WorkspaceDestination.humanMedicalPlan().workspaceId());
		assertEquals(WorkspaceId.CANINE_MEDICAL_PLAN, WorkspaceDestination.canineMedicalPlan().workspaceId());
		assertEquals(WorkspaceId.SAFETY_OVERVIEW,
				WorkspaceDestination.of(WorkspaceId.SAFETY_OVERVIEW).workspaceId());
		assertEquals("Safety Overview", WorkspaceId.SAFETY_OVERVIEW.displayLabel());
		assertThrows(IllegalArgumentException.class, () -> WorkspaceDestination.of(WorkspaceId.ACTIVITY_LOG));
		assertThrows(IllegalArgumentException.class, () -> WorkspaceDestination.activityLog(""));
	}

	@Test
	void activityLogDestinationsRequireAnInstanceIdentifier() {
		WorkspaceDestination first = WorkspaceDestination.activityLog("log-instance-one");
		WorkspaceDestination second = WorkspaceDestination.activityLog("log-instance-two");

		assertEquals(WorkspaceId.ACTIVITY_LOG, first.workspaceId());
		assertTrue(!first.equals(second));
		assertEquals("log-instance-one", first.instanceId());
	}

}
