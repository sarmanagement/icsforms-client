/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.persistence.LocalRepository;
import org.sarmanagement.icsforms.pdf.Ics206PdfRenderer;
import org.sarmanagement.icsforms.pdf.PdfExportService;
import org.sarmanagement.icsforms.validation.IncidentValidator;

import javax.swing.JTabbedPane;
import javax.swing.DefaultCellEditor;
import javax.swing.Icon;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import java.nio.file.Path;
import java.awt.image.BufferedImage;
import java.awt.Graphics;
import java.util.List;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies grouped medical navigation and the canine editor's usable subtabs.
 */
class MedicalWorkspacePanelTest {
	@TempDir
	Path temporaryDirectory;

	@Test
	void caninePlanAppearsOnlyWhenEligibleOrExplicitlyOpenedAndHidingPreservesData() throws Exception {
		AppData data = new AppData();
		data.getCanineMedicalPlan().setEmergencyProcedures("Preserve this saved procedure.");
		MedicalWorkspacePanel panel = medicalWorkspace(data);

		assertEquals(1, panel.planTabs().getTabCount());
		assertTrue(panel.planTabs().getIconAt(0) != null);
		assertEquals("Human medical plan", panel.planTabs().getToolTipTextAt(0));
		panel.setCanineVisible(true);
		assertEquals(2, panel.planTabs().getTabCount());
		assertTrue(panel.planTabs().getIconAt(1) != null);
		assertEquals("Canine medical plan", panel.planTabs().getToolTipTextAt(1));
		panel.openCaninePlan();
		assertEquals(1, panel.planTabs().getSelectedIndex());
		panel.setCanineVisible(false);

		assertEquals(1, panel.planTabs().getTabCount());
		assertEquals(0, panel.planTabs().getSelectedIndex());
		assertEquals("Preserve this saved procedure.", data.getCanineMedicalPlan().getEmergencyProcedures());
	}

	@Test
	void caninePlanIconUsesSimpleSingleColorSideProfileFacingRight() throws Exception {
		MedicalWorkspacePanel panel = medicalWorkspace(new AppData());
		panel.setCanineVisible(true);
		Icon canineIcon = panel.planTabs().getIconAt(1);
		BufferedImage image = new BufferedImage(canineIcon.getIconWidth(), canineIcon.getIconHeight(),
				BufferedImage.TYPE_INT_ARGB);
		Graphics graphics = image.createGraphics();
		try {
			canineIcon.paintIcon(panel, graphics, 0, 0);
		} finally {
			graphics.dispose();
		}

		assertTrue((image.getRGB(18, 10) >>> 24) > 0);
		assertEquals(0, image.getRGB(1, 10) >>> 24);
		for (int row = 0; row < image.getHeight(); row++) {
			for (int column = 0; column < image.getWidth(); column++) {
				int pixel = image.getRGB(column, row);
				if ((pixel >>> 24) > 0) {
					assertTrue(((pixel >> 16) & 0xff) < 120);
					assertTrue(((pixel >> 8) & 0xff) < 120);
					assertTrue((pixel & 0xff) < 120);
				}
			}
		}
	}

	@Test
	void canineWorkspaceSeparatesPlanAndVeterinaryControls() throws Exception {
		AppData data = new AppData();
		data.getCanineMedicalPlan().setPreparerSignature("Saved preparer signature");
		data.getCanineMedicalPlan().setSafetyOfficerSignature("Saved safety signature");
		CanineMedicalPanel canine = caninePanel(data);
		JTabbedPane tabs = canine.workspaceTabs();

		assertEquals(2, tabs.getTabCount());
		assertEquals("Canine ICS 206CANINE plan", tabs.getTitleAt(0));
		assertEquals("Veterinary facilities and routes", tabs.getTitleAt(1));
		assertFalse(fieldExists(canine, "active"));
		assertFalse(fieldExists(canine, "preparerSignature"));
		assertFalse(fieldExists(canine, "safetySignature"));
		DefaultTableModel directory = (DefaultTableModel) fieldValue(canine, "directoryModel");
		assertTrue(directory.findColumn("24 Hours") >= 0);
		canine.pushToModel();
		assertEquals("Saved preparer signature", data.getCanineMedicalPlan().getPreparerSignature());
		assertEquals("Saved safety signature", data.getCanineMedicalPlan().getSafetyOfficerSignature());
	}

	@Test
	void switchingMedicalSubtabsFlushesAnActiveTableCellEditor() throws Exception {
		AppData data = new AppData();
		AppController controller = controller(data);
		Ics206Panel human = new Ics206Panel(controller);
		MedicalWorkspacePanel panel = new MedicalWorkspacePanel(human,
				new CanineMedicalPanel(controller, temporaryDirectory,
						address -> List.of(), (source, destinations) -> List.of()));
		panel.setCanineVisible(true);
		assertFalse(fieldExists(human, "active"));
		assertFalse(fieldExists(human, "stagingWorkspace"));

		SwingUtilities.invokeAndWait(() -> {
			JTable table = (JTable) fieldValue(human, "hospitalTable");
			table.getColumnModel().getColumn(0).setCellEditor(new DefaultCellEditor(new JTextField()));
			((DefaultTableModel) table.getModel()).addRow(
					new Object[] {"", "", "", "", "", "", "", "", "", "", "", "", "Unknown", "", "Unknown", "Unknown"});
			table.editCellAt(0, 0);
			((JTextField) table.getEditorComponent()).setText("Fictional Emergency Hospital");
			panel.planTabs().setSelectedIndex(1);
		});

		assertEquals("Fictional Emergency Hospital", data.getMedicalPlan().getHospitals().get(0).getName());
	}

	/**
	 * Creates the shared medical workspace with test routing services.
	 *
	 * @param data incident model.
	 * @return configured workspace.
	 * @throws Exception when a temporary repository cannot be created.
	 */
	private MedicalWorkspacePanel medicalWorkspace(AppData data) throws Exception {
		AppController controller = controller(data);
		return new MedicalWorkspacePanel(new Ics206Panel(controller),
				new CanineMedicalPanel(controller, temporaryDirectory,
						address -> List.of(), (source, destinations) -> List.of()));
	}

	/**
	 * Creates a canine editor with a test incident controller.
	 *
	 * @param data incident model.
	 * @return configured canine editor.
	 * @throws Exception when a temporary repository cannot be created.
	 */
	private CanineMedicalPanel caninePanel(AppData data) throws Exception {
		return new CanineMedicalPanel(controller(data), temporaryDirectory,
				address -> List.of(), (source, destinations) -> List.of());
	}

	/**
	 * Creates an incident controller backed by temporary local storage.
	 *
	 * @param data incident model.
	 * @return configured controller.
	 * @throws Exception when a temporary repository cannot be created.
	 */
	private AppController controller(AppData data) throws Exception {
		return new AppController(data, new LocalRepository(temporaryDirectory.resolve("incident.json")),
				new PdfExportService(new Ics206PdfRenderer()), new IncidentValidator());
	}

	/**
	 * Checks whether a component declares a named field.
	 *
	 * @param instance component object.
	 * @param name field name.
	 * @return true when declared.
	 */
	private boolean fieldExists(Object instance, String name) {
		try {
			instance.getClass().getDeclaredField(name);
			return true;
		} catch (NoSuchFieldException exception) {
			return false;
		}
	}

	/**
	 * Returns a private component field for focused UI tests.
	 *
	 * @param instance component object.
	 * @param name field name.
	 * @return field value.
	 */
	private Object fieldValue(Object instance, String name) {
		try {
			Field field = instance.getClass().getDeclaredField(name);
			field.setAccessible(true);
			return field.get(instance);
		} catch (ReflectiveOperationException exception) {
			throw new IllegalStateException(exception);
		}
	}
}
