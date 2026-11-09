/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.CommunicationEntry;
import org.sarmanagement.icsforms.model.Ics204Form;

import javax.swing.DefaultCellEditor;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Focused regression coverage for non-destructive assignment editor flushes. */
class Ics204PanelFlushTest {
	@Test
	void contactDraftAndUnchangedFlushRetainChildrenAndSelection() throws Exception {
		AppController controller = IncidentStoreTestFixtures.controller(new AppData());
		SwingUtilities.invokeAndWait(() -> {
			Ics204Panel panel = new Ics204Panel(controller);
			panel.refreshFromModel();
			JPanel contacts = field(panel, "managementContactsPanel", JPanel.class);
			Component[] children = contacts.getComponents();
			AtomicInteger hierarchyChanges = new AtomicInteger();
			for (Component child : children) {
				child.addHierarchyListener(event -> hierarchyChanges.incrementAndGet());
			}
			JTextField name = field(panel, "operationsChiefNameField", JTextField.class);
			JTextField contact = field(panel, "operationsChiefContactField", JTextField.class);
			name.setText("Edited chief");
			contact.setText("Tac 9");
			name.select(1, 6);
			assertTrue(panel.pushToModel());
			assertTrue(panel.pushToModel());
			assertEquals("Edited chief", controller.getData().getForm204().getOperationsSectionChiefName());
			assertEquals("Tac 9", controller.getData().getForm204().getOperationsSectionChiefContact());
			assertArrayEquals(children, contacts.getComponents());
			assertEquals(0, hierarchyChanges.get());
			assertEquals(1, name.getSelectionStart());
			assertEquals(6, name.getSelectionEnd());
		});
	}

	@Test
	void contextChangesRebuildRolesButReuseContactFieldsAndDrafts() throws Exception {
		AppController controller = IncidentStoreTestFixtures.controller(new AppData());
		SwingUtilities.invokeAndWait(() -> {
			Ics204Panel panel = new Ics204Panel(controller);
			panel.refreshFromModel();
			JComboBox<?> context = field(panel, "managementContextSelector", JComboBox.class);
			JPanel contacts = field(panel, "managementContactsPanel", JPanel.class);
			JTextField supervisor = field(panel, "supervisorNameField", JTextField.class);
			context.setSelectedItem("Division");
			supervisor.setText("Retained supervisor");
			Component firstLabel = contacts.getComponent(0);
			assertEquals("Division supervisor", ((JLabel) contacts.getComponent(4)).getText());
			assertSame(supervisor, contacts.getComponent(5));
			assertTrue(panel.pushToModel());
			assertSame(firstLabel, contacts.getComponent(0));
			context.setSelectedItem("Group");
			assertNotSame(firstLabel, contacts.getComponent(0));
			assertEquals("Group supervisor", ((JLabel) contacts.getComponent(4)).getText());
			assertSame(supervisor, contacts.getComponent(5));
			assertEquals("Retained supervisor", supervisor.getText());
			assertTrue(panel.pushToModel());
			assertEquals(Ics204Form.MANAGEMENT_GROUP, controller.getData().getForm204().getManagementContext());
			context.setSelectedItem("Branch");
			assertEquals("Branch director", ((JLabel) contacts.getComponent(4)).getText());
			context.setSelectedItem("Incident");
			assertEquals(4, contacts.getComponentCount());
		});
	}

	@Test
	void communicationsPendingEditCommitsAndRejectedOrStaleDraftRemainsEditable() throws Exception {
		AppData data = new AppData();
		data.getForm204().setCommunications(List.of(new CommunicationEntry()));
		AppController controller = IncidentStoreTestFixtures.controller(data);
		SwingUtilities.invokeAndWait(() -> {
			Ics204Panel panel = new Ics204Panel(controller);
			panel.refreshFromModel();
			JTable table = field(panel, "communicationsTable", JTable.class);
			assertTrue(table.editCellAt(0, 0));
			((JTextField) table.getEditorComponent()).setText("Pending contact");
			assertTrue(panel.pushToModel());
			assertFalse(table.isEditing());
			assertEquals("Pending contact", data.getForm204().getCommunications().get(0).getName());
			table.setDefaultEditor(Object.class, new RejectingEditor());
			assertTrue(table.editCellAt(0, 0));
			((JTextField) table.getEditorComponent()).setText("Rejected contact");
			assertThrows(IllegalStateException.class, panel::pushToModel);
			assertTrue(table.isEditing());
			assertEquals("Rejected contact", ((JTextField) table.getEditorComponent()).getText());
			assertEquals("Pending contact", data.getForm204().getCommunications().get(0).getName());
			controller.discardUnsavedChangesAndSetData(new AppData());
			assertFalse(panel.pushToModel());
			assertTrue(table.isEditing());
			assertEquals("Rejected contact", ((JTextField) table.getEditorComponent()).getText());
			table.getCellEditor().cancelCellEditing();
		});
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
	private static <T> T field(Ics204Panel panel, String name, Class<T> type) {
		try {
			Field field = Ics204Panel.class.getDeclaredField(name);
			field.setAccessible(true);
			return type.cast(field.get(panel));
		} catch (ReflectiveOperationException exception) {
			throw new AssertionError(exception);
		}
	}

	/** Simulates a validating editor which refuses its current draft. */
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
