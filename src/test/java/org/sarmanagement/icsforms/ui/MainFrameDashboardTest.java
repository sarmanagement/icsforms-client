/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.Ics214Form;
import org.sarmanagement.icsforms.pdf.PdfExportService;
import org.sarmanagement.icsforms.validation.IncidentValidator;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFrame;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.GraphicsEnvironment;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void unchangedTabSwitchRetainsComponentsWithoutCommitting(boolean populated) throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		var directory = Files.createTempDirectory("icsforms-tab-flicker");
		var notifications = new java.util.ArrayList<AppController.IncidentChange>();
		MainFrame[] frame = new MainFrame[1];
		try {
			SwingUtilities.invokeAndWait(() -> {
				AppData data = new AppData();
				if (populated) {
					var timestamp = java.time.LocalDateTime.of(2026, 11, 8, 14, 30, 27, 123456789);
					data.setIapPhase(org.sarmanagement.icsforms.model.IapPhase.DURING_OP);
					data.getIncidentContext().setIncidentName("Populated incident");
					data.getIncidentContext().setOperationalPeriodStart(timestamp);
					data.getIncidentContext().setOperationalPeriodEnd(timestamp.plusHours(12));
					data.getForm202().setObjectives(List.of("Search the north sector"));
					data.getForm201().setPreparedDateTime(timestamp);
					data.getForm202().setApprovedDateTime(timestamp);
					data.getForm204().setPreparedDateTime(timestamp);
					data.getForm215a().setPreparedDateTime(timestamp);
					data.getForm208().setPreparedDateTime(timestamp);
					data.getMedicalPlan().setPreparedAt(timestamp);
					data.getCanineMedicalPlan().setPreparedAt(timestamp);
					data.setIcs205Enabled(true);
					data.setIcs215Enabled(true);
				}
				frame[0] = IncidentStoreTestFixtures.frame(data, directory,
						new PdfExportService(), new IncidentValidator(), directory);
				AppController controller = (AppController) Ics205PanelTest.field(frame[0], "controller");
				JTabbedPane tabs = (JTabbedPane) Ics205PanelTest.field(frame[0], "tabs");
				var components = java.util.stream.IntStream.range(0, tabs.getTabCount())
						.mapToObj(tabs::getComponentAt).toList();
				java.util.concurrent.atomic.AtomicInteger removals = new java.util.concurrent.atomic.AtomicInteger();
				tabs.addContainerListener(new java.awt.event.ContainerAdapter() {
					@Override
					public void componentRemoved(java.awt.event.ContainerEvent event) {
						removals.incrementAndGet();
					}
				});
				controller.addIncidentChangeListener(notifications::add);
				var binding = controller.captureEditorCommitBinding();
				var baseline = UiRefreshStateTest.modelState(data);
				for (int pass = 0; pass < 2; pass++) {
					for (int index = 1; index < tabs.getTabCount(); index++) {
						tabs.setSelectedIndex(index);
						assertEquals(index, tabs.getSelectedIndex());
					}
					frame[0].openWorkspace(WorkspaceDestination.of(WorkspaceId.SHARED));
					assertEquals(0, tabs.getSelectedIndex());
				}
				assertEquals(0, removals.get());
				assertEquals(components, java.util.stream.IntStream.range(0, tabs.getTabCount())
						.mapToObj(tabs::getComponentAt).toList());
				var after = UiRefreshStateTest.modelState(data);
				baseline.fields().forEachRemaining(entry ->
						assertEquals(entry.getValue(), after.get(entry.getKey()), entry.getKey()));
				assertEquals(binding, controller.captureEditorCommitBinding());
				assertFalse(controller.hasUnsavedChanges());
				assertFalse(((Timer) Ics205PanelTest.field(controller, "autosaveTimer")).isRunning());
			});
			SwingUtilities.invokeAndWait(() -> assertTrue(notifications.isEmpty()));
		} finally {
			SwingUtilities.invokeAndWait(() -> {
				if (frame[0] != null) {
					AppController controller = (AppController) Ics205PanelTest.field(frame[0], "controller");
					frame[0].dispose();
					controller.close();
				}
			});
		}
	}

	@Test
	void changedEditorCommitsAndNotifiesOnTabSwitch() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		var directory = Files.createTempDirectory("icsforms-tab-edit");
		var notifications = new java.util.ArrayList<AppController.IncidentChange>();
		MainFrame[] frame = new MainFrame[1];
		try {
			SwingUtilities.invokeAndWait(() -> {
				AppData data = new AppData();
				AppController.ensureDefaultHeaderCards(data.getTCards(), data.getIapPhase());
				frame[0] = IncidentStoreTestFixtures.frame(data, directory,
						new PdfExportService(), new IncidentValidator(), directory);
				AppController controller = (AppController) Ics205PanelTest.field(frame[0], "controller");
				controller.addIncidentChangeListener(notifications::add);
				javax.swing.JTable cards = (javax.swing.JTable) Ics205PanelTest.field(
						Ics205PanelTest.field(frame[0], "tCardPanel"), "table");
				java.util.concurrent.atomic.AtomicInteger cardRefreshes = new java.util.concurrent.atomic.AtomicInteger();
				cards.getModel().addTableModelListener(event -> cardRefreshes.incrementAndGet());
				Object objectives = Ics205PanelTest.field(frame[0], "ics202Panel");
				var before = UiRefreshStateTest.modelState(controller.getData());
				((javax.swing.JTextArea) Ics205PanelTest.field(objectives, "commandEmphasisArea"))
						.setText("Edited command emphasis");
				((JTabbedPane) Ics205PanelTest.field(frame[0], "tabs")).setSelectedIndex(1);
				assertEquals("Edited command emphasis", controller.getData().getForm202().getCommandEmphasis());
				var after = UiRefreshStateTest.modelState(controller.getData());
				for (String key : List.of("incidentContext", "organizationalChart", "tCards", "sarTaskAssignments",
						"form204", "additionalForms204")) {
					assertEquals(before.get(key), after.get(key), key);
				}
				assertEquals(0, cardRefreshes.get());
				assertTrue(controller.hasUnsavedChanges());
			});
			SwingUtilities.invokeAndWait(() -> assertEquals(1, notifications.stream()
					.filter(change -> change.kind() == AppController.IncidentChangeKind.MODEL_COMMITTED).count()));
		} finally {
			SwingUtilities.invokeAndWait(() -> {
				if (frame[0] != null) {
					AppController controller = (AppController) Ics205PanelTest.field(frame[0], "controller");
					frame[0].dispose();
					controller.close();
				}
			});
		}
	}

	@Test
	void reorderedActivityLogsKeepSelectedPanelIdentity() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		var directory = Files.createTempDirectory("icsforms-reordered-logs");
		AppData data = new AppData();
		data.getActivityLogs().add(new Ics214Form());
		data.getActivityLogs().add(new Ics214Form());
		SwingUtilities.invokeAndWait(() -> {
			MainFrame frame = IncidentStoreTestFixtures.frame(data, directory,
					new PdfExportService(), new IncidentValidator(), directory);
			AppController controller = (AppController) Ics205PanelTest.field(frame, "controller");
			try {
				JTabbedPane tabs = (JTabbedPane) Ics205PanelTest.field(frame, "tabs");
				java.awt.Component selected = java.util.stream.IntStream.range(0, tabs.getTabCount())
						.mapToObj(tabs::getComponentAt).filter(Ics214Panel.class::isInstance)
						.findFirst().orElseThrow();
				tabs.setSelectedComponent(selected);
				java.util.Collections.swap(data.getActivityLogs(), 0, 1);
				try {
					var refresh = MainFrame.class.getDeclaredMethod("refreshFromModel");
					refresh.setAccessible(true);
					refresh.invoke(frame);
				} catch (ReflectiveOperationException exception) {
					throw new AssertionError(exception);
				}
				assertSame(selected, tabs.getSelectedComponent());
				Ics214Form retired = data.getActivityLogs().remove(0);
				try {
					var refresh = MainFrame.class.getDeclaredMethod("refreshFromModel");
					refresh.setAccessible(true);
					refresh.invoke(frame);
				} catch (ReflectiveOperationException exception) {
					throw new AssertionError(exception);
				}
				var states = (java.util.Map<?, ?>) Ics205PanelTest.field(frame, "editorRefreshStates");
				assertTrue(states.keySet().stream().noneMatch(component ->
						component instanceof Ics214Panel log && log.getCurrentForm() == retired));
				assertSame(selected, tabs.getSelectedComponent());
			} finally {
				frame.dispose();
				controller.close();
			}
		});
	}

	@Test
	void tabLabelConfigurationUpdatesExistingEditorsAndPersistsChoice() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		var directory = Files.createTempDirectory("icsforms-tab-labels");
		var preferences = java.util.prefs.Preferences.userNodeForPackage(MainFrame.class);
		String previous = preferences.get("descriptive-workspace-tabs", null);
		SwingUtilities.invokeAndWait(() -> {
			MainFrame frame = IncidentStoreTestFixtures.frame(new AppData(), directory,
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
	void objectiveAssessmentSettingLivesInConfigurationAndUpdatesOpenRolePanel() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		var directory = Files.createTempDirectory("icsforms-objective-assessment-setting");
		var preferences = java.util.prefs.Preferences.userNodeForPackage(MainFrame.class);
		String key = "show-objective-assessments";
		String previous = preferences.get(key, null);
		preferences.putBoolean(key, false);
		SwingUtilities.invokeAndWait(() -> {
			MainFrame frame = IncidentStoreTestFixtures.frame(new AppData(), directory,
					new PdfExportService(), new IncidentValidator(), directory);
			JFrame[] roleWindow = new JFrame[1];
			try {
				JMenu config = java.util.stream.IntStream.range(0, frame.getJMenuBar().getMenuCount())
						.mapToObj(frame.getJMenuBar()::getMenu)
						.filter(menu -> "Configuration".equals(menu.getText())).findFirst().orElseThrow();
				int medicalSettings = java.util.stream.IntStream.range(0, config.getItemCount())
						.filter(index -> config.getItem(index) != null
								&& config.getItem(index).getText().startsWith("Medical Routing"))
						.findFirst().orElseThrow();
				JCheckBoxMenuItem setting = (JCheckBoxMenuItem) config.getItem(medicalSettings + 1);
				assertEquals("Show objective assessments", setting.getText());
				assertFalse(setting.isSelected());
				setting.doClick();
				assertTrue(preferences.getBoolean(key, false));

				JMenu view = frame.getJMenuBar().getMenu(1);
				JMenu rolePanels = view.getItem(0) instanceof JMenu menu ? menu : null;
				assertTrue(rolePanels != null);
				for (int index = 0; index < rolePanels.getItemCount(); index++) {
					JMenuItem item = rolePanels.getItem(index);
					if (item != null && "Incident Command".equals(item.getText())) {
						item.doClick();
						break;
					}
				}
				roleWindow[0] = java.util.Arrays.stream(java.awt.Window.getWindows())
						.filter(JFrame.class::isInstance).map(JFrame.class::cast)
						.filter(candidate -> candidate.getTitle().startsWith("Incident Command —"))
						.findFirst().orElseThrow();
				IncidentCommandRolePanel panel = (IncidentCommandRolePanel) roleWindow[0].getContentPane();
				assertEquals(4, ((javax.swing.JTable) Ics205PanelTest.field(panel, "objectives")).getColumnCount());
				setting.doClick();
				assertFalse(preferences.getBoolean(key, true));
				assertEquals(3, ((javax.swing.JTable) Ics205PanelTest.field(panel, "objectives")).getColumnCount());
			} finally {
				if (roleWindow[0] != null) {
					roleWindow[0].dispose();
				}
				if (previous == null) {
					preferences.remove(key);
				} else {
					preferences.put(key, previous);
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
			MainFrame frame = IncidentStoreTestFixtures.frame(data, directory,
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
				MainFrame frame = IncidentStoreTestFixtures.frame(data, directory,
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
				MainFrame frame = IncidentStoreTestFixtures.frame(new AppData(), directory,
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
