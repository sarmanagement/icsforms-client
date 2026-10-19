/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.IncidentLocation;
import org.sarmanagement.icsforms.model.VetFacility;
import org.sarmanagement.icsforms.medical.AddressGeocoder;
import org.sarmanagement.icsforms.medical.GeoPoint;
import org.sarmanagement.icsforms.pdf.PdfExportService;
import org.sarmanagement.icsforms.persistence.LocalRepository;
import org.sarmanagement.icsforms.validation.IncidentValidator;

import javax.swing.JLabel;
import javax.swing.JButton;
import javax.swing.AbstractButton;
import javax.swing.JEditorPane;
import javax.swing.JTable;
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

	@Test
	void removingFacilityWhileItsDirectoryCellIsBeingEditedDoesNotWriteToDeletedRow() throws Exception {
		AppData data = new AppData();
		data.getVetDirectory().getFacilities().add(new org.sarmanagement.icsforms.model.VetFacility());
		AppController controller = new AppController(data,
				new LocalRepository(tempDirectory.resolve("incident.json")),
				new PdfExportService(), new IncidentValidator());
		CanineMedicalPanel[] panel = new CanineMedicalPanel[1];

		SwingUtilities.invokeAndWait(() -> {
			panel[0] = new CanineMedicalPanel(controller, tempDirectory);
			JTable table = (JTable) fieldValue(panel[0], "directoryTable");
			assertTrue(table.editCellAt(0, 1));
			((JTextField) table.getEditorComponent()).setText("Incomplete facility");
			table.setRowSelectionInterval(0, 0);

			panel[0].removeDirectoryRows(new int[]{0});
		});

		assertEquals(0, data.getVetDirectory().getFacilities().size());
		assertEquals(0, ((JTable) fieldValue(panel[0], "directoryTable")).getRowCount());
	}

	@Test
	void confirmedFacilityGeocodePersistsForStreetCityStateAndZipAddress() throws Exception {
		AppData data = new AppData();
		VetFacility facility = new VetFacility();
		facility.setName("Fictional Veterinary Hospital");
		facility.setAddress("350 South Huntington Avenue");
		facility.setTown("Boston");
		facility.setState("MA");
		facility.setPostalCode("02130");
		data.getVetDirectory().getFacilities().add(facility);
		AppController controller = new AppController(data,
				new LocalRepository(tempDirectory.resolve("incident.json")),
				new PdfExportService(), new IncidentValidator());
		CanineMedicalPanel panel = new CanineMedicalPanel(controller, tempDirectory);
		AddressGeocoder.Candidate candidate = new AddressGeocoder.Candidate(
				"350 South Huntington Avenue, Boston, MA", new GeoPoint(42.3, -71.1), 0.9);
		String address = "350 South Huntington Avenue, Boston, MA, 02130";

		boolean[] applied = new boolean[1];
		SwingUtilities.invokeAndWait(() ->
				applied[0] = panel.applyGeocodedVetCandidate(data, facility.getId(), address, candidate));

		assertTrue(applied[0]);
		assertEquals(42.3, data.getVetDirectory().getFacilities().get(0).getLatitude());
		assertEquals(-71.1, data.getVetDirectory().getFacilities().get(0).getLongitude());
		assertEquals("openrouteservice-geocoder",
				data.getVetDirectory().getFacilities().get(0).getGeocodingProvider());
	}

	@Test
	void routingActionsRequireCoordinatesAndAConfirmedCurrentRoute() throws Exception {
		AppData data = new AppData();
		data.getStagingLocation().setAddress("1 Fictional Base Road");
		data.getStagingLocation().setResolution("Confirmed base", 42.0, -71.0, "manual", "test", null);
		VetFacility facility = new VetFacility();
		facility.setAddress("2 Fictional Vet Road");
		facility.setAvailable24Hours(true);
		facility.setCoordinates(42.1, -71.1, "manual", "test");
		data.getVetDirectory().getFacilities().add(facility);
		AppController controller = new AppController(data,
				new LocalRepository(tempDirectory.resolve("incident.json")),
				new PdfExportService(), new IncidentValidator());
		CanineMedicalPanel panel = new CanineMedicalPanel(controller, tempDirectory);

		SwingUtilities.invokeAndWait(() -> {
			JButton calculate = (JButton) fieldValue(panel, "calculateRoutesButton");
			JButton preview = (JButton) fieldValue(panel, "previewSelectionButton");
			assertTrue(calculate.isEnabled());
			assertFalse(preview.isEnabled());
			facility.setDistance(1500, 300, "openrouteservice/driving-car", "test", null,
					data.getStagingLocation().getGeneration());
			panel.refreshFromModel();
			assertTrue(preview.isEnabled());
			facility.setAvailable24Hours(false);
			panel.refreshFromModel();
			assertFalse(preview.isEnabled());
		});
	}

	@Test
	void selectedFacilityGeocodingIsEnabledOnlyForAnAddressWithoutCoordinates() throws Exception {
		AppData data = new AppData();
		VetFacility facility = new VetFacility();
		facility.setAddress("2 Fictional Vet Road");
		data.getVetDirectory().getFacilities().add(facility);
		AppController controller = new AppController(data,
				new LocalRepository(tempDirectory.resolve("incident.json")),
				new PdfExportService(), new IncidentValidator());
		CanineMedicalPanel panel = new CanineMedicalPanel(controller, tempDirectory);
		JButton geocode = (JButton) fieldValue(panel, "geocodeVetButton");
		JTable directory = (JTable) fieldValue(panel, "directoryTable");

		SwingUtilities.invokeAndWait(() -> {
			assertFalse(geocode.isEnabled());
			directory.setRowSelectionInterval(0, 0);
			assertTrue(geocode.isEnabled());
			facility.setCoordinates(42.1, -71.1, "manual", "test");
			panel.refreshFromModel();
			assertFalse(geocode.isEnabled());
		});
	}

	@Test
	void routeCalculationIsDisabledUntilBaseAndFacilityCoordinatesExist() throws Exception {
		AppData data = new AppData();
		AppController controller = new AppController(data,
				new LocalRepository(tempDirectory.resolve("incident.json")),
				new PdfExportService(), new IncidentValidator());
		CanineMedicalPanel panel = new CanineMedicalPanel(controller, tempDirectory);

		SwingUtilities.invokeAndWait(() -> {
			assertFalse(((JButton) fieldValue(panel, "calculateRoutesButton")).isEnabled());
			assertFalse(((JButton) fieldValue(panel, "previewSelectionButton")).isEnabled());
			assertTrue(hasText(panel, "1. Load regional JSON/CSV…"));
			assertTrue(hasText(panel, "openrouteservice by HeiGIT | Data from"));
		});
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
	 * Finds text in visible labels and embedded HTML attribution.
	 *
	 * @param component root component.
	 * @param text expected text fragment.
	 * @return true when the fragment appears in the visible component tree.
	 */
	private static boolean hasText(Component component, String text) {
		if (component instanceof JLabel candidate && candidate.getText().contains(text)) {
			return true;
		}
		if (component instanceof AbstractButton candidate && candidate.getText().contains(text)) {
			return true;
		}
		if (component instanceof JEditorPane candidate && candidate.getText().contains(text)) {
			return true;
		}
		if (component instanceof Container container) {
			for (Component child : container.getComponents()) {
				if (hasText(child, text)) {
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
