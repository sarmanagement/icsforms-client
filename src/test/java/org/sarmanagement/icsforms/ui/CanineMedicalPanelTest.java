/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.IncidentLocation;
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
import static org.junit.jupiter.api.Assertions.assertEquals;
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

	@Test
	void caninePlanHasMatchingSectionsAndCanSelectSharedRoutingBase() throws Exception {
		AppData data = new AppData();
		IncidentLocation base = new IncidentLocation();
		base.setType("Base");
		base.setName("Fictional Base");
		base.setAddress("1 Fictional Road");
		data.getIncidentLocations().add(base);
		AppController controller = new AppController(data,
				new LocalRepository(tempDirectory.resolve("incident.json")),
				new PdfExportService(), new IncidentValidator());
		CanineMedicalPanel panel = new CanineMedicalPanel(controller, tempDirectory);

		SwingUtilities.invokeAndWait(() -> {
			assertEquals("3. Aid Stations", panel.planSectionTabs().getTitleAt(0));
			assertEquals("4. Transportation", panel.planSectionTabs().getTitleAt(1));
			assertEquals("5. Veterinary Hospitals", panel.planSectionTabs().getTitleAt(2));
			assertEquals("6. Special Procedures", panel.planSectionTabs().getTitleAt(3));
			javax.swing.JCheckBox aviation = (javax.swing.JCheckBox) fieldValue(panel, "aviationUsed");
			assertTrue(containsComponent(panel.planSectionTabs().getComponentAt(1), aviation));
			assertFalse(containsComponent(panel.planSectionTabs().getComponentAt(3), aviation));
			@SuppressWarnings("unchecked")
			javax.swing.JComboBox<Object> choices =
					(javax.swing.JComboBox<Object>) fieldValue(panel, "routingBaseLocations");
			choices.setSelectedIndex(1);
		});

		assertEquals(base.getId(), data.getCanineRoutingBaseLocationId());
		assertEquals("Fictional Base", data.getStagingLocation().getLabel());
		assertEquals("1 Fictional Road", data.getStagingLocation().getAddress());
	}

	/**
	 * Reads an arbitrary private canine-panel field for focused component tests.
	 *
	 * @param panel canine editor.
	 * @param name field name.
	 * @return requested field value.
	 */
	private static Object fieldValue(CanineMedicalPanel panel, String name) {
		try {
			Field field = CanineMedicalPanel.class.getDeclaredField(name);
			field.setAccessible(true);
			return field.get(panel);
		} catch (ReflectiveOperationException exception) {
			throw new IllegalStateException(exception);
		}
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

	/**
	 * Checks whether one component is nested under another.
	 *
	 * @param parent component tree root.
	 * @param target desired child.
	 * @return true if the target appears beneath the root.
	 */
	private static boolean containsComponent(Component parent, Component target) {
		if (parent == target) {
			return true;
		}
		if (parent instanceof Container container) {
			for (Component child : container.getComponents()) {
				if (containsComponent(child, target)) {
					return true;
				}
			}
		}
		return false;
	}
}
