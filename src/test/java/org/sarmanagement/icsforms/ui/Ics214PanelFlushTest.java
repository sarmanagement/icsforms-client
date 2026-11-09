/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.model.ActivityLogEntry;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.Ics214Form;
import org.sarmanagement.icsforms.model.SarTaskResource;

import javax.swing.DefaultCellEditor;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import java.awt.Point;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Focused coverage for activity header/resource merges and retained Swing drafts. */
class Ics214PanelFlushTest {
	@Test
	void unchangedFlushPreservesHeaderDocumentsSelectionResourceRowsAndScroll() throws Exception {
		AppData data = data();
		AppController controller = IncidentStoreTestFixtures.controller(data);
		SwingUtilities.invokeAndWait(() -> {
			Ics214Panel panel = load(controller, data);
			AtomicInteger documents = new AtomicInteger();
			for (String name : List.of("nameField", "icsPositionField", "homeAgencyField",
					"preparedByNameField", "preparedByPositionField")) {
				JTextField field = field(panel, name, JTextField.class);
				field.getDocument().addDocumentListener(new CountingDocumentListener(documents));
				field.select(1, 3);
			}
			JTable table = field(panel, "resourcesTable", JTable.class);
			JSpinner spinner = field(panel, "preparedDateTimeField", JSpinner.class);
			JTextField spinnerText = ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField();
			spinnerText.getDocument().addDocumentListener(new CountingDocumentListener(documents));
			spinnerText.select(1, 3);
			AtomicInteger notifications = new AtomicInteger();
			table.getModel().addTableModelListener(event -> notifications.incrementAndGet());
			List<SarTaskResource> displayed = resources(panel);
			table.setRowSelectionInterval(0, 0);
			JScrollPane scroll = field(panel, "activityLogScrollPane", JScrollPane.class);
			scroll.getViewport().setViewPosition(new Point(0, 16));
			Point position = scroll.getViewport().getViewPosition();
			assertTrue(panel.saveToModel());
			assertTrue(panel.saveToModel());
			assertEquals(0, documents.get());
			assertEquals(0, notifications.get());
			assertSame(displayed, resources(panel));
			assertEquals(0, table.getSelectedRow());
			assertEquals(position, scroll.getViewport().getViewPosition());
			assertEquals(1, spinnerText.getSelectionStart());
			assertEquals(3, spinnerText.getSelectionEnd());
			for (String name : List.of("nameField", "icsPositionField", "homeAgencyField",
					"preparedByNameField", "preparedByPositionField")) {
				JTextField field = field(panel, name, JTextField.class);
				assertEquals(1, field.getSelectionStart());
				assertEquals(3, field.getSelectionEnd());
			}
		});
	}

	@Test
	void localHeadersWinConflictsWhileUntouchedHeadersAndSpinnerAdoptCanonicalChanges() throws Exception {
		AppData data = data();
		AppController controller = IncidentStoreTestFixtures.controller(data);
		SwingUtilities.invokeAndWait(() -> {
			Ics214Panel panel = load(controller, data);
			Ics214Form form = data.getActivityLogs().get(0);
			JTextField name = field(panel, "nameField", JTextField.class);
			name.setText("Local header");
			name.select(2, 5);
			form.setName("Remote header");
			form.setIcsPosition("Remote position");
			form.setHomeAgency("Remote agency");
			form.setPreparedByName("Remote preparer");
			form.setPreparedByPositionTitle("Remote preparer position");
			LocalDateTime remoteTime = form.getPreparedDateTime().plusHours(2);
			form.setPreparedDateTime(remoteTime);
			assertTrue(panel.saveToModel());
			assertEquals("Local header", form.getName());
			assertEquals("Local header", name.getText());
			assertEquals(2, name.getSelectionStart());
			assertEquals(5, name.getSelectionEnd());
			assertEquals("Remote position", field(panel, "icsPositionField", JTextField.class).getText());
			assertEquals("Remote agency", field(panel, "homeAgencyField", JTextField.class).getText());
			assertEquals("Remote preparer", field(panel, "preparedByNameField", JTextField.class).getText());
			assertEquals("Remote preparer position", field(panel, "preparedByPositionField", JTextField.class).getText());
			JSpinner spinner = field(panel, "preparedDateTimeField", JSpinner.class);
			assertEquals(AppController.toDate(remoteTime), spinner.getValue());
			LocalDateTime localTime = remoteTime.plusHours(1);
			spinner.setValue(AppController.toDate(localTime));
			form.setPreparedDateTime(remoteTime.plusHours(3));
			assertTrue(panel.saveToModel());
			assertEquals(localTime, form.getPreparedDateTime());
		});
	}

	@Test
	void unchangedNullCanonicalDateDoesNotReplaceDisplayedSpinnerValue() throws Exception {
		AppData data = data();
		data.getActivityLogs().get(0).setPreparedDateTime(null);
		AppController controller = IncidentStoreTestFixtures.controller(data);
		SwingUtilities.invokeAndWait(() -> {
			Ics214Panel panel = load(controller, data);
			JSpinner spinner = field(panel, "preparedDateTimeField", JSpinner.class);
			Object displayed = spinner.getValue();
			AtomicInteger changes = new AtomicInteger();
			spinner.addChangeListener(event -> changes.incrementAndGet());
			assertTrue(panel.saveToModel());
			assertTrue(panel.saveToModel());
			assertEquals(displayed, spinner.getValue());
			assertEquals(0, changes.get());
			assertNull(data.getActivityLogs().get(0).getPreparedDateTime());
		});
	}

	@Test
	void canonicalResourcesKeepSelectedIdentitiesAcrossReorderSortingAndRemoval() throws Exception {
		AppData data = data();
		SarTaskResource first = resource("first", "Zulu");
		SarTaskResource second = resource("second", "Alpha");
		SarTaskResource third = resource("third", "Middle");
		Ics214Form form = data.getActivityLogs().get(0);
		form.setResourcesAssigned(new ArrayList<>(List.of(first, second, third)));
		AppController controller = IncidentStoreTestFixtures.controller(data);
		SwingUtilities.invokeAndWait(() -> {
			Ics214Panel panel = load(controller, data);
			JTable table = field(panel, "resourcesTable", JTable.class);
			table.setAutoCreateRowSorter(true);
			table.getRowSorter().toggleSortOrder(0);
			table.addRowSelectionInterval(table.convertRowIndexToView(0), table.convertRowIndexToView(0));
			table.addRowSelectionInterval(table.convertRowIndexToView(2), table.convertRowIndexToView(2));
			first.setName("Aardvark");
			form.setResourcesAssigned(new ArrayList<>(List.of(third, first, second)));
			assertTrue(panel.saveToModel());
			assertEquals(List.of("first", "third"), selectedIds(panel));
			assertEquals("Aardvark", table.getValueAt(0, 0));
			form.setResourcesAssigned(new ArrayList<>(List.of(second, third)));
			assertTrue(panel.saveToModel());
			assertEquals(List.of("third"), selectedIds(panel));
			form.setResourcesAssigned(new ArrayList<>(List.of(second)));
			assertTrue(panel.saveToModel());
			assertEquals(-1, table.getSelectedRow());
		});
	}

	@Test
	void unsortedCanonicalReorderTracksIdentityRatherThanIndexOrDuplicateName() throws Exception {
		AppData data = data();
		Ics214Form form = data.getActivityLogs().get(0);
		SarTaskResource first = resource("first", "Same name");
		SarTaskResource second = resource("second", "Same name");
		form.setResourcesAssigned(new ArrayList<>(List.of(first, second)));
		AppController controller = IncidentStoreTestFixtures.controller(data);
		SwingUtilities.invokeAndWait(() -> {
			Ics214Panel panel = load(controller, data);
			JTable table = field(panel, "resourcesTable", JTable.class);
			table.setRowSelectionInterval(0, 0);
			AtomicInteger notifications = new AtomicInteger();
			table.getModel().addTableModelListener(event -> notifications.incrementAndGet());
			form.setResourcesAssigned(new ArrayList<>(List.of(second, first)));
			assertTrue(panel.saveToModel());
			assertEquals(1, table.getSelectedRow());
			assertEquals(List.of("first"), selectedIds(panel));
			assertTrue(panel.saveToModel());
			assertEquals(1, notifications.get());
			form.setResourcesAssigned(new ArrayList<>(List.of(second)));
			assertTrue(panel.saveToModel());
			assertEquals(-1, table.getSelectedRow());
		});
	}

	@Test
	void deliberateResourceDraftWinsAndCannotReplaceAuthoritativeActivityEntries() throws Exception {
		AppData data = data();
		AppController controller = IncidentStoreTestFixtures.controller(data);
		SwingUtilities.invokeAndWait(() -> {
			Ics214Panel panel = load(controller, data);
			Ics214Form form = data.getActivityLogs().get(0);
			resources(panel).get(0).setName("Local resource");
			form.getResourcesAssigned().get(0).setName("Remote resource");
			ActivityLogEntry authoritative = new ActivityLogEntry();
			List<ActivityLogEntry> entries = new ArrayList<>(List.of(authoritative));
			form.setActivityLog(entries);
			JTable table = field(panel, "resourcesTable", JTable.class);
			AtomicInteger notifications = new AtomicInteger();
			table.getModel().addTableModelListener(event -> notifications.incrementAndGet());
			assertTrue(panel.saveToModel());
			assertEquals("Local resource", form.getResourcesAssigned().get(0).getName());
			assertSame(entries, form.getActivityLog());
			assertSame(authoritative, form.getActivityLog().get(0));
			assertNotSame(resources(panel).get(0), form.getResourcesAssigned().get(0));
			assertEquals(0, notifications.get());
		});
	}

	@Test
	void activeResourceEditorCommitsBeforeMergeAndRejectingOrStaleDraftIsRetained() throws Exception {
		AppData data = data();
		AppController controller = IncidentStoreTestFixtures.controller(data);
		SwingUtilities.invokeAndWait(() -> {
			Ics214Panel panel = load(controller, data);
			JTable table = field(panel, "resourcesTable", JTable.class);
			table.setModel(new EditableResourceFixture(resources(panel)));
			assertTrue(table.editCellAt(0, 0));
			((JTextField) table.getEditorComponent()).setText("Pending resource");
			assertTrue(panel.saveToModel());
			assertFalse(table.isEditing());
			assertEquals("Pending resource", data.getActivityLogs().get(0).getResourcesAssigned().get(0).getName());
			table.setDefaultEditor(Object.class, new RejectingEditor());
			assertTrue(table.editCellAt(0, 0));
			((JTextField) table.getEditorComponent()).setText("Rejected resource");
			JTextField name = field(panel, "nameField", JTextField.class);
			name.setText("Pending header");
			assertThrows(IllegalStateException.class, panel::saveToModel);
			assertEquals("Log header", data.getActivityLogs().get(0).getName());
			assertEquals("Pending header", name.getText());
			assertTrue(table.isEditing());
			assertEquals("Rejected resource", ((JTextField) table.getEditorComponent()).getText());
			controller.setData(new AppData());
			assertFalse(panel.saveToModel());
			assertTrue(table.isEditing());
			assertEquals("Rejected resource", ((JTextField) table.getEditorComponent()).getText());
			assertEquals("Pending resource", data.getActivityLogs().get(0).getResourcesAssigned().get(0).getName());
			table.getCellEditor().cancelCellEditing();
		});
	}

	/**
	 * Creates a fully populated header and resource fixture.
	 *
	 * @return incident fixture.
	 */
	private static AppData data() {
		AppData data = new AppData();
		Ics214Form form = new Ics214Form();
		form.setName("Log header");
		form.setIcsPosition("Log position");
		form.setHomeAgency("Log agency");
		form.setPreparedByName("Log preparer");
		form.setPreparedByPositionTitle("Log preparer position");
		form.setPreparedDateTime(LocalDateTime.of(2026, 1, 1, 8, 0));
		form.setResourcesAssigned(new ArrayList<>(List.of(resource("resource-1", "Resource name"))));
		data.setActivityLogs(new ArrayList<>(List.of(form)));
		return data;
	}

	/**
	 * Creates a resource identified independently of its display name.
	 *
	 * @param id stable resource identity.
	 * @param name display name.
	 * @return resource fixture.
	 */
	private static SarTaskResource resource(String id, String name) {
		SarTaskResource resource = new SarTaskResource();
		resource.setResourceId(id);
		resource.setName(name);
		return resource;
	}

	/**
	 * Loads a panel against its controller's incident.
	 *
	 * @param controller tested controller.
	 * @param data incident fixture.
	 * @return loaded editor.
	 */
	private static Ics214Panel load(AppController controller, AppData data) {
		Ics214Panel panel = new Ics214Panel(controller);
		panel.loadFromModel(data.getActivityLogs().get(0), data);
		return panel;
	}

	/**
	 * Reads detached resources without altering the public read-only table.
	 *
	 * @param panel tested editor.
	 * @return displayed detached resources.
	 */
	@SuppressWarnings("unchecked")
	private static List<SarTaskResource> resources(Ics214Panel panel) {
		try {
			Object model = field(panel, "resourcesTableModel", Object.class);
			Field rows = model.getClass().getDeclaredField("rows");
			rows.setAccessible(true);
			return (List<SarTaskResource>) rows.get(model);
		} catch (ReflectiveOperationException exception) {
			throw new AssertionError(exception);
		}
	}

	/**
	 * Resolves selected resource identities through the current sorter.
	 *
	 * @param panel tested editor.
	 * @return selected identities in displayed order.
	 */
	private static List<String> selectedIds(Ics214Panel panel) {
		JTable table = field(panel, "resourcesTable", JTable.class);
		List<String> ids = new ArrayList<>();
		for (int row : table.getSelectedRows()) {
			ids.add(resources(panel).get(table.convertRowIndexToModel(row)).getResourceId());
		}
		return ids;
	}

	/**
	 * Reads a private Swing fixture.
	 *
	 * @param panel tested panel.
	 * @param name field name.
	 * @param type expected fixture type.
	 * @param <T> fixture type.
	 * @return requested fixture.
	 */
	private static <T> T field(Ics214Panel panel, String name, Class<T> type) {
		try {
			Field field = Ics214Panel.class.getDeclaredField(name);
			field.setAccessible(true);
			return type.cast(field.get(panel));
		} catch (ReflectiveOperationException exception) {
			throw new AssertionError(exception);
		}
	}

	/**
	 * Counts all text document mutations.
	 *
	 * @param count shared mutation counter.
	 */
	private record CountingDocumentListener(AtomicInteger count) implements DocumentListener {
		/** {@inheritDoc} */
		@Override
		public void insertUpdate(DocumentEvent event) {
			count.incrementAndGet();
		}

		/** {@inheritDoc} */
		@Override
		public void removeUpdate(DocumentEvent event) {
			count.incrementAndGet();
		}

		/** {@inheritDoc} */
		@Override
		public void changedUpdate(DocumentEvent event) {
			count.incrementAndGet();
		}
	}

	/** Simulates a future active resource editor without changing production editability. */
	private static class EditableResourceFixture extends AbstractTableModel {
		private final List<SarTaskResource> rows;

		/**
		 * Binds the fixture to the detached resource draft.
		 *
		 * @param rows editor draft rows.
		 */
		EditableResourceFixture(List<SarTaskResource> rows) {
			this.rows = rows;
		}

		/** {@inheritDoc} */
		@Override
		public int getRowCount() {
			return rows.size();
		}

		/** {@inheritDoc} */
		@Override
		public int getColumnCount() {
			return 1;
		}

		/** {@inheritDoc} */
		@Override
		public Object getValueAt(int row, int column) {
			return rows.get(row).getName();
		}

		/** {@inheritDoc} */
		@Override
		public boolean isCellEditable(int row, int column) {
			return true;
		}

		/** {@inheritDoc} */
		@Override
		public void setValueAt(Object value, int row, int column) {
			rows.get(row).setName(value.toString());
			fireTableCellUpdated(row, column);
		}
	}

	/** Simulates a resource editor refusing invalid input. */
	private static class RejectingEditor extends DefaultCellEditor {
		/** Creates a rejecting text editor. */
		RejectingEditor() {
			super(new JTextField());
		}

		/** {@inheritDoc} */
		@Override
		public boolean stopCellEditing() {
			return false;
		}
	}
}
