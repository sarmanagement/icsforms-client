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

class Ics214PanelTest {

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

	private static JTable activityLogTable(Ics214Panel panel) throws Exception {
		Field field = Ics214Panel.class.getDeclaredField("activityLogTable");
		field.setAccessible(true);
		return (JTable) field.get(panel);
	}

	private static JScrollPane activityLogScrollPane(Ics214Panel panel) throws Exception {
		Field field = Ics214Panel.class.getDeclaredField("activityLogScrollPane");
		field.setAccessible(true);
		return (JScrollPane) field.get(panel);
	}

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

	private static AppController sampleController() throws Exception {
		return new AppController(new AppData(),
				new LocalRepository(Files.createTempDirectory("icsforms-214-ui").resolve("incident.json")),
				new PdfExportService(), new IncidentValidator());
	}
}
