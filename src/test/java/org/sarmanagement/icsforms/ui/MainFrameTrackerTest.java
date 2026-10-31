/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.Ics214Form;
import org.sarmanagement.icsforms.model.IncidentMode;
import org.sarmanagement.icsforms.model.OpenActionItem;
import org.sarmanagement.icsforms.persistence.FileIncidentStore;
import org.sarmanagement.icsforms.persistence.IncidentId;
import org.sarmanagement.icsforms.pdf.PdfExportService;
import org.sarmanagement.icsforms.pdf.Ics202PdfRenderer;
import org.sarmanagement.icsforms.pdf.Ics204PdfRenderer;
import org.sarmanagement.icsforms.pdf.SarTaskAssignmentPdfRenderer;
import org.sarmanagement.icsforms.validation.IncidentValidator;
import javax.swing.Timer;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

/** Display-backed smoke coverage for independent tracker tab visibility. */
class MainFrameTrackerTest {
	@TempDir
	Path directory;

	@Test
void typedWorkspaceApiOpensNestedMedicalPlansAndSafetyOverview() throws Exception {
	Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());
	Path path = directory.resolve("workspace-navigation-store");
	AppData data = new AppData();
	data.setIncidentMode(IncidentMode.GENERIC);
	data.getCanineMedicalPlan().setAidStationInformation("Saved canine aid station");
	try (FileIncidentStore store = new FileIncidentStore(path)) {
		IncidentId id = store.create(data);
		SwingUtilities.invokeAndWait(() -> {
			MainFrame frame = new MainFrame(data, store, id,
					new PdfExportService(new Ics202PdfRenderer(), new Ics204PdfRenderer(),
							new SarTaskAssignmentPdfRenderer()),
					new IncidentValidator(), Path.of("target"));
			try {
				frame.openWorkspace(WorkspaceDestination.humanMedicalPlan());
				MedicalWorkspacePanel medical = (MedicalWorkspacePanel) field(frame, "medicalWorkspacePanel");
				JTabbedPane tabs = (JTabbedPane) field(frame, "tabs");
				assertEquals(medical, tabs.getSelectedComponent());
				assertEquals("Medical Plan (ICS 206)", medical.planTabs().getTitleAt(0));
				assertEquals(0, medical.planTabs().getSelectedIndex());

				frame.openWorkspace(WorkspaceDestination.canineMedicalPlan());
				assertEquals(medical, tabs.getSelectedComponent());
				assertEquals(2, medical.planTabs().getTabCount());
				assertEquals(WorkspaceId.CANINE_MEDICAL_PLAN.displayLabel(),
						medical.planTabs().getTitleAt(medical.planTabs().getSelectedIndex()));
				assertEquals("Saved canine aid station",
						data.getCanineMedicalPlan().getAidStationInformation());

				frame.openWorkspace(WorkspaceDestination.of(WorkspaceId.SAFETY_OVERVIEW));
				assertEquals("Safety Overview", tabs.getTitleAt(tabs.getSelectedIndex()));
				var refresh = MainFrame.class.getDeclaredMethod("refreshFromModel");
				refresh.setAccessible(true);
				refresh.invoke(frame);
				assertEquals("Safety Overview", tabs.getTitleAt(tabs.getSelectedIndex()));
			} catch (Throwable exception) {
				throw new AssertionError(exception);
			} finally {
				stop(frame);
			}
		});
	}
}

@Test
void trackerSurvivesSarGenericDisableAndPersistence() throws Exception {
		Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());
	Path path = directory.resolve("tracker-store");
		AppData data = new AppData();
		OpenActionItem item = new OpenActionItem();
		item.setNumber(1);
		item.setItem("Preserved action");
		data.setOpenActionItems(List.of(item));
		data.setOpenActionTrackerEnabled(true);
		try (FileIncidentStore store = new FileIncidentStore(path)) {
			IncidentId id = store.create(data);
			SwingUtilities.invokeAndWait(() -> {
				MainFrame frame = new MainFrame(data, store, id,
						new PdfExportService(new Ics202PdfRenderer(), new Ics204PdfRenderer(),
								new SarTaskAssignmentPdfRenderer()),
						new IncidentValidator(), Path.of("target"));
				try {
					frame.setVisible(true);
					var tabField = MainFrame.class.getDeclaredField("tabs");
					tabField.setAccessible(true);
					JTabbedPane tabs = (JTabbedPane) tabField.get(frame);
					var refresh = MainFrame.class.getDeclaredMethod("refreshFromModel");
					refresh.setAccessible(true);
					assertTrue(tabs.indexOfTab("Open Action Tracker") >= 0);
					assertTrue(tabs.indexOfTab("Clue Log") >= 0);
					data.setIncidentMode(IncidentMode.GENERIC);
					refresh.invoke(frame);
					assertTrue(tabs.indexOfTab("Open Action Tracker") >= 0);
					assertEquals(-1, tabs.indexOfTab("Clue Log"));
					data.setOpenActionTrackerEnabled(false);
					refresh.invoke(frame);
					assertEquals(-1, tabs.indexOfTab("Open Action Tracker"));
					assertEquals("Preserved action", data.getOpenActionItems().get(0).getItem());
					data.setIncidentMode(IncidentMode.SAR);
					data.setOpenActionTrackerEnabled(true);
					refresh.invoke(frame);
					assertTrue(tabs.indexOfTab("Open Action Tracker") >= 0);
					assertTrue(tabs.indexOfTab("Clue Log") >= 0);
				} catch (ReflectiveOperationException exception) {
					throw new AssertionError(exception);
				} finally {
					stop(frame);
				}
			});
			data.setOpenActionTrackerEnabled(false);
			store.save(id, data);
			AppData restored = store.load(id);
			assertFalse(restored.isOpenActionTrackerEnabled());
			assertEquals("Preserved action", restored.getOpenActionItems().get(0).getItem());
		}
	}

	@Test
	void logsMenuOrganizationAndRemovalLifecycle() throws Exception {
		Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());
		Path path = directory.resolve("logs-menu-store");
		AppData data = new AppData();
		Ics214Form icpLog = new Ics214Form();
		icpLog.setName("ICP");
		Ics214Form taskLog = new Ics214Form();
		taskLog.setName("Team One Log");
		taskLog.setLinkedSarTaskAssignmentId("assignment-1");
		data.setActivityLogs(new ArrayList<>(List.of(icpLog, taskLog)));

		try (FileIncidentStore store = new FileIncidentStore(path)) {
			IncidentId id = store.create(data);
			SwingUtilities.invokeAndWait(() -> {
				MainFrame frame = new MainFrame(data, store, id,
						new PdfExportService(new Ics202PdfRenderer(), new Ics204PdfRenderer(),
								new SarTaskAssignmentPdfRenderer()),
						new IncidentValidator(), Path.of("target"));
				try {
					frame.setVisible(true);
					javax.swing.JMenuBar menuBar = frame.getJMenuBar();
					javax.swing.JMenu logsMenu = null;
					for (int i = 0; i < menuBar.getMenuCount(); i++) {
						if ("Logs".equals(menuBar.getMenu(i).getText())) {
							logsMenu = menuBar.getMenu(i);
							break;
						}
					}
					assertNotNull(logsMenu);
					assertEquals("Go to Log", logsMenu.getItem(0).getText());
					assertEquals("Open Log in New Window", logsMenu.getItem(1).getText());
					assertNull(logsMenu.getItem(2));
					assertTrue(logsMenu.getMenuComponent(2) instanceof javax.swing.JSeparator);
					assertEquals("Add Log", logsMenu.getItem(3).getText());
					assertEquals("Remove Log", logsMenu.getItem(4).getText());

					javax.swing.JMenu goToLog = (javax.swing.JMenu) logsMenu.getItem(0);
					javax.swing.JMenu openInNewWindow = (javax.swing.JMenu) logsMenu.getItem(1);
					assertEquals(2, goToLog.getItemCount());
					assertEquals(2, openInNewWindow.getItemCount());

					openInNewWindow.getItem(1).doClick();
					Field windowsField = Ics214Panel.class.getDeclaredField("POP_OUT_WINDOWS");
					windowsField.setAccessible(true);
					@SuppressWarnings("unchecked")
					java.util.Map<Ics214Form, javax.swing.JFrame> windows =
							(java.util.Map<Ics214Form, javax.swing.JFrame>) windowsField.get(null);
					assertTrue(windows.containsKey(taskLog));
					Ics214Panel.closePopOutWindow(taskLog);
					assertFalse(windows.containsKey(taskLog));

					Field closedLogsField = MainFrame.class.getDeclaredField("closedLogForms");
					closedLogsField.setAccessible(true);
					@SuppressWarnings("unchecked")
					Set<Ics214Form> closedLogs = (Set<Ics214Form>) closedLogsField.get(frame);

					// Closing/removing an empty log removes it from model and pop-out
					closedLogs.add(icpLog);
					var rebuildTabs = MainFrame.class.getDeclaredMethod("rebuildLogTabs");
					rebuildTabs.setAccessible(true);
					rebuildTabs.invoke(frame);

					// Re-selecting via Go to Log restores the log
					goToLog.getItem(0).doClick();
					assertFalse(closedLogs.contains(icpLog));
					JTabbedPane tabs = (JTabbedPane) field(frame, "tabs");
					assertEquals(icpLog, ((Ics214Panel) tabs.getSelectedComponent()).getCurrentForm());
					goToLog.getItem(1).doClick();
					assertEquals(taskLog, ((Ics214Panel) tabs.getSelectedComponent()).getCurrentForm());
					goToLog.getItem(0).doClick();
					assertEquals(icpLog, ((Ics214Panel) tabs.getSelectedComponent()).getCurrentForm());
				} catch (Throwable exception) {
					throw new AssertionError(exception);
				} finally {
					stop(frame);
				}
			});
		}
	}

	/**
	 * Reads a private MainFrame component for display-backed navigation assertions.
	 *
	 * @param frame application frame.
	 * @param fieldName component field.
	 * @return field value.
	 * @throws ReflectiveOperationException when the field is unavailable.
	 */
	private static Object field(Object target, String fieldName) throws ReflectiveOperationException {
		Field field = target.getClass().getDeclaredField(fieldName);
		field.setAccessible(true);
		return field.get(target);
	}

	/**
	 * Stops background saves before disposing a frame backed by a temporary store.
	 *
	 * @param frame editor frame to dispose.
	 */
	private static void stop(MainFrame frame) {
		try {
			Object controller = field(frame, "controller");
			((Timer) field(controller, "autosaveTimer")).stop();
		} catch (ReflectiveOperationException exception) {
			throw new AssertionError(exception);
		}
		frame.dispose();
	}
}
