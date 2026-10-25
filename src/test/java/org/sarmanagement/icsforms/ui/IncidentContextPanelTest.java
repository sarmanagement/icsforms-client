/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.persistence.LocalRepository;
import org.sarmanagement.icsforms.pdf.PdfExportService;
import org.sarmanagement.icsforms.validation.IncidentValidator;

import javax.swing.JButton;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import java.awt.Component;
import java.awt.Container;
import java.lang.reflect.Field;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies that Shared incident locations are edited and persisted in the incident model.
 */
class IncidentContextPanelTest {
	@TempDir
	Path tempDirectory;

	@Test
	void quickAddAndEditsPersistSharedIncidentLocations() throws Exception {
		AppData data = new AppData();
		AppController controller = new AppController(data,
				new LocalRepository(tempDirectory.resolve("incident.json")),
				new PdfExportService(), new IncidentValidator());
		IncidentContextPanel[] panel = new IncidentContextPanel[1];

		SwingUtilities.invokeAndWait(() -> {
			panel[0] = new IncidentContextPanel(controller);
			panel[0].refreshFromModel();
			button(panel[0], "Add ICP").doClick();
			JTable table = table(panel[0]);
			DefaultTableModel model = (DefaultTableModel) table.getModel();
			model.setValueAt("Fictional Incident Command Post", 0, 1);
			model.setValueAt("10 Fictional Main Street", 0, 2);
		});

		assertEquals(1, data.getIncidentLocations().size());
		assertEquals("ICP", data.getIncidentLocations().get(0).getType());
		assertEquals("Fictional Incident Command Post", data.getIncidentLocations().get(0).getName());
		assertEquals("10 Fictional Main Street", data.getIncidentLocations().get(0).getAddress());
	}

	/**
	 * Finds a button by its visible label.
	 *
	 * @param root component tree root.
	 * @param label button text.
	 * @return matching button.
	 */
	private static JButton button(Component root, String label) {
		if (root instanceof JButton button && label.equals(button.getText())) {
			return button;
		}
		if (root instanceof Container container) {
			for (Component child : container.getComponents()) {
				JButton found = buttonOrNull(child, label);
				if (found != null) {
					return found;
				}
			}
		}
		throw new IllegalStateException("Button not found: " + label);
	}

	/**
	 * Searches descendants for a matching button without throwing during traversal.
	 *
	 * @param root component tree root.
	 * @param label button text.
	 * @return matching button or null.
	 */
	private static JButton buttonOrNull(Component root, String label) {
		if (root instanceof JButton button && label.equals(button.getText())) {
			return button;
		}
		if (root instanceof Container container) {
			for (Component child : container.getComponents()) {
				JButton found = buttonOrNull(child, label);
				if (found != null) {
					return found;
				}
			}
		}
		return null;
	}

	/**
	 * Reads the shared location table for focused editor assertions.
	 *
	 * @param panel shared context panel.
	 * @return editable incident locations.
	 */
	private static JTable table(IncidentContextPanel panel) {
		try {
			Field field = IncidentContextPanel.class.getDeclaredField("locationsTable");
			field.setAccessible(true);
			return (JTable) field.get(panel);
		} catch (ReflectiveOperationException exception) {
			throw new IllegalStateException(exception);
		}
	}
}
