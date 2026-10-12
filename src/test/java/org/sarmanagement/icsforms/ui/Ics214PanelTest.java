// SPDX-License-Identifier: GPL-3.0-or-later
package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.model.ActivityLogEntry;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.Ics214Form;
import org.sarmanagement.icsforms.model.ResourceAssignment;
import org.sarmanagement.icsforms.model.SarTaskAssignment;
import org.sarmanagement.icsforms.persistence.LocalRepository;
import org.sarmanagement.icsforms.pdf.PdfExportService;
import org.sarmanagement.icsforms.validation.IncidentValidator;

import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** Regression coverage for resource-linked activity views. */
class Ics214PanelTest {

	@Test
	void duplicatePopOutRequestsReuseIndependentWindowAndPreservePendingMainFields() throws Exception {
		assumeFalse(java.awt.GraphicsEnvironment.isHeadless());
		AppData data = sampleDataWithTaskLog(1);
		Ics214Panel panel = new Ics214Panel(sampleController(data));
		Ics214Form form = data.getActivityLogs().get(0);
		panel.loadFromModel(form, data);
		Method open = Ics214Panel.class.getDeclaredMethod("popOutInNewWindow");
		open.setAccessible(true);
		Field windowsField = Ics214Panel.class.getDeclaredField("POP_OUT_WINDOWS");
		windowsField.setAccessible(true);
		@SuppressWarnings("unchecked")
		java.util.Map<Ics214Form, javax.swing.JFrame> windows =
				(java.util.Map<Ics214Form, javax.swing.JFrame>) windowsField.get(null);
		SwingUtilities.invokeAndWait(() -> {
			javax.swing.JFrame frame = null;
			try {
				open.invoke(panel);
				frame = windows.get(form);
				open.invoke(panel);
				assertSame(frame, windows.get(form));
				assertEquals(null, frame.getOwner());
				Field nameField = Ics214Panel.class.getDeclaredField("nameField");
				nameField.setAccessible(true);
				((javax.swing.JTextField) nameField.get(panel)).setText("Pending embedded header");
				Ics214Panel detached = (Ics214Panel) frame.getContentPane().getComponent(0);
				((javax.swing.JTextField) nameField.get(detached)).setText("Saved detached header");
				Method close = Ics214Panel.class.getDeclaredMethod("closePopOut", javax.swing.JFrame.class,
						Ics214Panel.class, Ics214Form.class, AppData.class);
				close.setAccessible(true);
				close.invoke(panel, frame, detached, form, data);
				assertEquals("Saved detached header", form.getName());
				assertEquals("Pending embedded header", ((javax.swing.JTextField) nameField.get(panel)).getText());
				assertFalse(frame.isDisplayable());
			} catch (ReflectiveOperationException exception) {
				throw new IllegalStateException(exception);
			} finally {
				if (frame != null) {
					frame.dispose();
				}
			}
		});
	}

	@Test
	void sortedActivitySelectionResolvesModelEntry() throws Exception {
		AppData data = sampleDataWithTaskLog(2);
		Ics214Panel panel = new Ics214Panel(sampleController(data));
		panel.loadFromModel(data.getActivityLogs().get(0), data);
		JTable table = activityLogTable(panel);
		table.setAutoCreateRowSorter(true);
		table.getRowSorter().toggleSortOrder(0);
		table.getRowSorter().toggleSortOrder(0);
		table.setRowSelectionInterval(0, 0);
		Method method = Ics214Panel.class.getDeclaredMethod("selectedActivityEntry");
		method.setAccessible(true);
		assertEquals(data.getActivityLogs().get(0).getActivityLog().get(1), method.invoke(panel));
	}

	@Test
	void savingHeaderEditsCannotReplaceNewerActivityRows() throws Exception {
		AppData data = sampleDataWithTaskLog(1);
		Ics214Panel panel = new Ics214Panel(sampleController(data));
		Ics214Form form = data.getActivityLogs().get(0);
		panel.loadFromModel(form, data);
		ActivityLogEntry newer = new ActivityLogEntry();
		form.setActivityLog(new ArrayList<>(List.of(newer)));
		panel.saveToModel();
		assertEquals(List.of(newer), form.getActivityLog());
	}

	@Test
	void staleIncidentEditorCannotSaveAddOrOpenAnotherWindow() throws Exception {
		AppData data = sampleDataWithTaskLog(1);
		AppController controller = sampleController(data);
		Ics214Panel panel = new Ics214Panel(controller);
		Ics214Form form = data.getActivityLogs().get(0);
		panel.loadFromModel(form, data);
		Field field = Ics214Panel.class.getDeclaredField("nameField");
		field.setAccessible(true);
		((javax.swing.JTextField) field.get(panel)).setText("Stale edit");
		controller.setData(new AppData());

		panel.saveToModel();
		for (String methodName : List.of("addActivityEntry", "popOutInNewWindow")) {
			Method method = Ics214Panel.class.getDeclaredMethod(methodName);
			method.setAccessible(true);
			method.invoke(panel);
		}
		assertEquals("Task Log", form.getName());
		assertEquals(1, form.getActivityLog().size());
		assertTrue(controller.getData().getActivityLogs().isEmpty());
	}

	@Test
	void refreshingEntriesPreservesPendingHeaderEdits() throws Exception {
		Ics214Panel panel = new Ics214Panel(sampleController());
		AppData data = sampleDataWithTaskLog(1);
		panel.loadFromModel(data.getActivityLogs().get(0), data);
		Field field = Ics214Panel.class.getDeclaredField("nameField");
		field.setAccessible(true);
		javax.swing.JTextField name = (javax.swing.JTextField) field.get(panel);
		name.setText("Pending header edit");
		panel.refreshActivityEntries();
		assertEquals("Pending header edit", name.getText());
		assertEquals("Task Log", data.getActivityLogs().get(0).getName());
	}

	@Test
	void taskLinkedLogDisplaysCanonicalResourceIdentifierInResourceColumn() throws Exception {
		Ics214Panel panel = new Ics214Panel(sampleController());
		AppData data = sampleDataWithTaskLog(1);
		Ics214Form form = data.getActivityLogs().get(0);

		SwingUtilities.invokeAndWait(() -> panel.loadFromModel(form, data));

		JTable table = activityLogTable(panel);
		assertEquals("1: Team Frodo", table.getValueAt(0, 2));
	}

	@Test
	void strikingOutActivityEntryKeepsRowAndRendersStrikeThrough() throws Exception {
		Ics214Panel panel = new Ics214Panel(sampleController());
		AppData data = sampleDataWithTaskLog(1);
		Ics214Form form = data.getActivityLogs().get(0);
		SwingUtilities.invokeAndWait(() -> panel.loadFromModel(form, data));

		Method method = Ics214Panel.class.getDeclaredMethod("strikeOutActivityEntry", int.class);
		method.setAccessible(true);
		SwingUtilities.invokeAndWait(() -> {
			try {
				method.invoke(panel, 0);
			} catch (ReflectiveOperationException exception) {
				throw new IllegalStateException(exception);
			}
		});

		JTable table = activityLogTable(panel);
		Component renderer = table.prepareRenderer(table.getCellRenderer(0, 3), 0, 3);
		assertEquals(1, form.getActivityLog().size());
		assertTrue(form.getActivityLog().get(0).isStruckOut());
		assertTrue(((JLabel) renderer).getText().contains("<strike>"));
	}

	@Test
	void loadingFormScrollsActivityLogToMostRecentEntry() throws Exception {
		Ics214Panel panel = new Ics214Panel(sampleController());
		AppData data = sampleDataWithTaskLog(30);
		Ics214Form form = data.getActivityLogs().get(0);
		JScrollPane scrollPane = activityLogScrollPane(panel);

		SwingUtilities.invokeAndWait(() -> {
			panel.setSize(900, 700);
			panel.doLayout();
			scrollPane.setSize(900, 300);
			scrollPane.doLayout();
			panel.loadFromModel(form, data);
		});
		SwingUtilities.invokeAndWait(() -> {
		});

		assertTrue(scrollPane.getViewport().getViewPosition().y > 0);
	}

	/**
	 * Returns the activity table fixture.
	 *
	 * @param panel tested panel
	 * @return activity table
	 * @throws Exception if fixture access fails
	 */
	private static JTable activityLogTable(Ics214Panel panel) throws Exception {
		Field field = Ics214Panel.class.getDeclaredField("activityLogTable");
		field.setAccessible(true);
		return (JTable) field.get(panel);
	}

	/**
	 * Returns the activity scroll pane fixture.
	 *
	 * @param panel tested panel
	 * @return scroll pane
	 * @throws Exception if fixture access fails
	 */
	private static JScrollPane activityLogScrollPane(Ics214Panel panel) throws Exception {
		Field field = Ics214Panel.class.getDeclaredField("activityLogScrollPane");
		field.setAccessible(true);
		return (JScrollPane) field.get(panel);
	}

	@Test
	void staticOpenInNewWindowAndClosePopOutWindowManageFrameLifecycle() throws Exception {
		assumeFalse(java.awt.GraphicsEnvironment.isHeadless());
		AppData data = sampleDataWithTaskLog(1);
		AppController controller = sampleController(data);
		Ics214Form form = data.getActivityLogs().get(0);
		Field windowsField = Ics214Panel.class.getDeclaredField("POP_OUT_WINDOWS");
		windowsField.setAccessible(true);
		@SuppressWarnings("unchecked")
		java.util.Map<Ics214Form, javax.swing.JFrame> windows =
				(java.util.Map<Ics214Form, javax.swing.JFrame>) windowsField.get(null);
		SwingUtilities.invokeAndWait(() -> {
			try {
				Ics214Panel.openInNewWindow(controller, form, data, () -> {});
				assertTrue(windows.containsKey(form));
				javax.swing.JFrame frame = windows.get(form);
				assertTrue(frame.isDisplayable());
				Ics214Panel.closePopOutWindow(form);
				assertFalse(windows.containsKey(form));
				assertFalse(frame.isDisplayable());
			} finally {
				Ics214Panel.closePopOutWindow(form);
			}
		});
	}

	/**
	 * Creates a linked log fixture.
	 *
	 * @param entryCount number of activity rows
	 * @return sample incident
	 */
	private static AppData sampleDataWithTaskLog(int entryCount) {
		AppData data = new AppData();
		SarTaskAssignment task = new SarTaskAssignment();
		task.setAssignmentId("assign-1");
		task.setAssignmentTeamNumber("1");
		task.setResourceIdentifier("Team Frodo");
		data.setSarTaskAssignments(new ArrayList<>(List.of(task)));

		Ics214Form form = new Ics214Form();
		form.setLinkedSarTaskAssignmentId("assign-1");
		form.setName("Task Log");
		for (int i = 0; i < entryCount; i++) {
			ActivityLogEntry entry = new ActivityLogEntry();
			entry.setTimestamp(LocalDateTime.parse("2026-01-01T08:00:00").plusMinutes(i));
			entry.setResourceIdentifier("Team Frodo");
			entry.setNotableActivity("Activity " + i);
			form.getActivityLog().add(entry);
		}
		data.setActivityLogs(new ArrayList<>(List.of(form)));

		ResourceAssignment resource = new ResourceAssignment();
		resource.setAssignmentId(task.getAssignmentId());
		resource.setAssignmentTeamNumber(task.getAssignmentTeamNumber());
		resource.setResourceIdentifier(task.getResourceIdentifier());
		data.getForm204().setResourcesAssigned(List.of(resource));
		return data;
	}

	/**
	 * Creates a controller fixture.
	 *
	 * @return test controller
	 * @throws Exception if the fixture directory cannot be created
	 */
	private static AppController sampleController() throws Exception {
		return sampleController(new AppData());
	}

	/**
	 * Creates a controller bound to a specific incident fixture.
	 *
	 * @param data incident fixture
	 * @return test controller
	 * @throws Exception if the fixture directory cannot be created
	 */
	private static AppController sampleController(AppData data) throws Exception {
		return new AppController(data,
				new LocalRepository(Files.createTempDirectory("icsforms-214-ui").resolve("incident.json")),
				new PdfExportService(), new IncidentValidator());
	}
}
