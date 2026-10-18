/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.pdf.PdfExportService;
import org.sarmanagement.icsforms.persistence.LocalRepository;
import org.sarmanagement.icsforms.validation.IncidentValidator;

import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies canine editor role pickers and the simplified veterinary controls. */
class CanineMedicalPanelTest {
	@TempDir
	Path tempDirectory;

	@Test
	void canineEditorUsesPersonPickersWithoutRedundantDvmOrAvailabilityRouteOption() throws Exception {
		AppController controller = new AppController(new AppData(),
				new LocalRepository(tempDirectory.resolve("incident.json")),
				new PdfExportService(), new IncidentValidator());
		CanineMedicalPanel panel = new CanineMedicalPanel(controller, tempDirectory);

		SwingUtilities.invokeAndWait(() -> {
			assertTrue(field(panel, "preparedBy").getClientProperty("uiSupport.namePicker.open")
					instanceof Consumer<?>);
			assertTrue(field(panel, "safetyOfficer").getClientProperty("uiSupport.namePicker.open")
					instanceof Consumer<?>);
			assertFalse(hasLabel(panel, "DVM"));
			assertFalse(hasLabel(panel, "Also route unknown 24-hour status (not confirmed)"));
		});
	}

	/**
	 * Reads a canine-panel text field for focused UI assertions.
	 *
	 * @param panel canine editor.
	 * @param name field name.
	 * @return requested text field.
	 */
	private static JTextField field(CanineMedicalPanel panel, String name) {
		try {
			Field field = CanineMedicalPanel.class.getDeclaredField(name);
			field.setAccessible(true);
			return (JTextField) field.get(panel);
		} catch (ReflectiveOperationException exception) {
			throw new IllegalStateException(exception);
		}
	}

	/**
	 * Finds whether the component tree contains the requested label.
	 *
	 * @param component root component.
	 * @param label label text.
	 * @return true when a matching label is present.
	 */
	private static boolean hasLabel(Component component, String label) {
		if (component instanceof JLabel candidate && label.equals(candidate.getText())) {
			return true;
		}
		if (component instanceof Container container) {
			for (Component child : container.getComponents()) {
				if (hasLabel(child, label)) {
					return true;
				}
			}
		}
		return false;
	}
}
