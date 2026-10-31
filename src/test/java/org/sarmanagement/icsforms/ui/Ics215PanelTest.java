/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.Ics215Form;
import org.sarmanagement.icsforms.persistence.LocalRepository;
import org.sarmanagement.icsforms.pdf.PdfExportService;
import org.sarmanagement.icsforms.validation.IncidentValidator;

import javax.swing.JComboBox;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.lang.reflect.Field;
import java.awt.GridLayout;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies ICS 215 editor date isolation, matrix expansion, and rejected input retention. */
class Ics215PanelTest {
	@TempDir
	Path directory;

	@Test
	void activeAndNextPeriodDatesStayIndependentAndMatrixSupportsDynamicHeadings() throws Exception {
		AppData data = new AppData();
		LocalDateTime sharedStart = LocalDateTime.of(2026, 10, 31, 6, 0);
		LocalDateTime sharedEnd = sharedStart.plusHours(12);
		data.getIncidentContext().setOperationalPeriodStart(sharedStart);
		data.getIncidentContext().setOperationalPeriodEnd(sharedEnd);
		Ics215Form.ResourceHeading heading = new Ics215Form.ResourceHeading();
		heading.category = "Ground";
		heading.kind = "Team";
		heading.type = "Type 2";
		heading.countingUnit = "resource/team";
		data.getForm215().getResourceHeadings().add(heading);
		Ics215Form.WorkRow row = new Ics215Form.WorkRow();
		row.assignment = "Search sector";
		Ics215Form.ResourceAllocation allocation = new Ics215Form.ResourceAllocation();
		allocation.required = new Ics215Form.CountPair(2, 1);
		allocation.acceptedHave = new Ics215Form.CountPair(1, 0);
		row.resourceAllocations.put(heading.id, allocation);
		data.getForm215().getWorkRows().add(row);
		AppController controller = new AppController(data, new LocalRepository(directory.resolve("incident.json")),
				new PdfExportService(), new IncidentValidator());
		Ics215Panel[] panel = new Ics215Panel[1];
		try {
			SwingUtilities.invokeAndWait(() -> {
				panel[0] = new Ics215Panel(controller);
				JTextField start = field(panel[0], "targetStart", JTextField.class);
				JTextField end = field(panel[0], "targetEnd", JTextField.class);
				JTable matrix = field(panel[0], "matrix", JTable.class);
				assertEquals(4, matrix.getColumnCount());
				assertEquals("R 2/1 / H 1/0 / N 1/1", matrix.getValueAt(0, 3));
				start.setText("2026-10-31 18:00");
				end.setText("2026-11-01 06:00");
				panel[0].pushToModel();
				assertEquals(toUtc("2026-10-31 18:00"), data.getForm215().getTargetPeriodStart());
				assertEquals(sharedStart, data.getIncidentContext().getOperationalPeriodStart());
				JComboBox<?> period = field(panel[0], "periodSelector", JComboBox.class);
				period.setSelectedIndex(1);
				assertEquals(sharedStart, data.getIncidentContext().getOperationalPeriodStart());
				assertEquals(display(sharedStart), field(panel[0], "targetStart", JTextField.class).getText());
				assertNull(data.getNextPeriodForm215().getTargetPeriodStart());
				JTextField plannedStart = field(panel[0], "targetStart", JTextField.class);
				plannedStart.setText("2026-11-01 06:00");
				field(panel[0], "targetEnd", JTextField.class).setText("2026-11-01 18:00");
				panel[0].pushToModel();
				assertEquals(toUtc("2026-11-01 06:00"), data.getNextPeriodForm215().getTargetPeriodStart());
				assertEquals(toUtc("2026-10-31 18:00"), data.getForm215().getTargetPeriodStart());
			});
		} finally {
			SwingUtilities.invokeAndWait(() -> field(controller, "autosaveTimer", Timer.class).stop());
		}
	}

	@Test
	void actionButtonsFollowMatrixSelectionAndLayoutUsesAvailableWidth() throws Exception {
		AppData data = new AppData();
		Ics215Form.ResourceHeading heading = new Ics215Form.ResourceHeading();
		heading.category = "Ground";
		heading.type = "Type 1";
		data.getForm215().getResourceHeadings().add(heading);
		Ics215Form.WorkRow row = new Ics215Form.WorkRow();
		row.assignment = "Search sector";
		data.getForm215().getWorkRows().add(row);
		AppController controller = new AppController(data, new LocalRepository(directory.resolve("layout.json")),
				new PdfExportService(), new IncidentValidator());
		Ics215Panel[] panel = new Ics215Panel[1];
		try {
			SwingUtilities.invokeAndWait(() -> {
				panel[0] = new Ics215Panel(controller);
				JTable matrix = field(panel[0], "matrix", JTable.class);
				JButton editRow = field(panel[0], "editAssignmentButton", JButton.class);
				JButton editCounts = field(panel[0], "editCountsButton", JButton.class);
				JButton editHeading = field(panel[0], "editHeadingButton", JButton.class);
				assertFalse(editRow.isEnabled());
				assertFalse(editCounts.isEnabled());
				assertFalse(editHeading.isEnabled());

				matrix.changeSelection(0, 0, false, false);
				assertTrue(editRow.isEnabled());
				assertFalse(editCounts.isEnabled());
				matrix.changeSelection(0, 3, false, false);
				assertTrue(editCounts.isEnabled());
				assertTrue(editHeading.isEnabled());

				panel[0].setSize(1100, 700);
				layoutRecursively(panel[0]);
				JScrollPane matrixScroll = field(panel[0], "matrixScroll", JScrollPane.class);
				assertTrue(matrixScroll.getViewport().getExtentSize().width > 0);
				invoke(panel[0], "configureMatrixColumns");
				assertTrue(matrix.getColumnModel().getColumn(2).getWidth() > 380);
				JPanel matrixPanel = (JPanel) matrixScroll.getParent();
				JPanel actions = (JPanel) ((java.awt.BorderLayout) matrixPanel.getLayout())
						.getLayoutComponent(java.awt.BorderLayout.SOUTH);
				assertEquals(GridLayout.class, actions.getLayout().getClass());
				assertEquals(15, actions.getComponentCount());
			});
		} finally {
			SwingUtilities.invokeAndWait(() -> field(controller, "autosaveTimer", Timer.class).stop());
		}
	}

	/**
	 * Lays out a component subtree as a containing frame would.
	 *
	 * @param component component to lay out.
	 */
	private static void layoutRecursively(java.awt.Container component) {
		component.doLayout();
		for (java.awt.Component child : component.getComponents()) {
			if (child instanceof java.awt.Container container) {
				layoutRecursively(container);
			}
		}
	}

	/**
	 * Invokes a private layout helper after sizing the editor fixture.
	 *
	 * @param target editor instance.
	 * @param name helper method name.
	 */
	private static void invoke(Object target, String name) {
		try {
			var method = target.getClass().getDeclaredMethod(name);
			method.setAccessible(true);
			method.invoke(target);
		} catch (ReflectiveOperationException exception) {
			throw new IllegalStateException(exception);
		}
	}

	@Test
	void invalidDateRemainsEditableAndBlocksFlush() throws Exception {
		AppData data = new AppData();
		AppController controller = new AppController(data, new LocalRepository(directory.resolve("invalid.json")),
				new PdfExportService(), new IncidentValidator());
		Ics215Panel[] panel = new Ics215Panel[1];
		try {
			SwingUtilities.invokeAndWait(() -> {
				panel[0] = new Ics215Panel(controller);
				JTextField start = field(panel[0], "targetStart", JTextField.class);
				start.setText("not a date");
				assertThrows(IllegalStateException.class, panel[0]::pushToModel);
				assertEquals("not a date", start.getText());
			});
		} finally {
			SwingUtilities.invokeAndWait(() -> field(controller, "autosaveTimer", Timer.class).stop());
		}
	}

	/**
	 * Reads a private editor component for focused lifecycle tests.
	 *
	 * @param target editor object.
	 * @param name field name.
	 * @param type expected type.
	 * @param <T> component type.
	 * @return component.
	 * @throws IllegalStateException when the test fixture field is unavailable.
	 */
	private static <T> T field(Object target, String name, Class<T> type) {
		try {
			Field field = target.getClass().getDeclaredField(name);
			field.setAccessible(true);
			return type.cast(field.get(target));
		} catch (ReflectiveOperationException exception) {
			throw new IllegalStateException(exception);
		}
	}

	/**
	 * Converts a user-displayed ICS date/time into the UTC persistence convention.
	 *
	 * @param text displayed date/time.
	 * @return UTC local date/time.
	 */
	private static LocalDateTime toUtc(String text) {
		LocalDateTime displayed = LocalDateTime.parse(text, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
		return LocalDateTime.ofInstant(displayed.atZone(UiSupport.getDateTimeDisplayZone()).toInstant(),
				ZoneOffset.UTC);
	}

	/**
	 * Converts a stored UTC value into the date/time text shown by the editor.
	 *
	 * @param value stored date/time.
	 * @return configured-zone display text.
	 */
	private static String display(LocalDateTime value) {
		return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
				.format(LocalDateTime.ofInstant(value.toInstant(ZoneOffset.UTC), UiSupport.getDateTimeDisplayZone()));
	}
}
