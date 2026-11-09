/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.MedicalPlan;
import org.sarmanagement.icsforms.pdf.Ics206PdfRenderer;
import org.sarmanagement.icsforms.pdf.PdfExportService;
import org.sarmanagement.icsforms.validation.IncidentValidator;

import javax.swing.DefaultCellEditor;
import javax.swing.JComboBox;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import java.awt.Component;
import java.lang.reflect.Field;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the human ICS 206 editor's model round-trip and edit flushing.
 */
class Ics206PanelTest {
	@TempDir
	Path tempDirectory;

	@Test
	void modeSwitchPreservesDetailedRecordsAndUnknownChoices() throws Exception {
		AppData data = new AppData();
		MedicalPlan.Hospital hospital = new MedicalPlan.Hospital();
		hospital.setName("Fictional City Hospital");
		hospital.setHelipad(null);
		data.getMedicalPlan().getHospitals().add(hospital);
		data.getMedicalPlan().setSimpleMode(false);
		Ics206Panel panel = new Ics206Panel(controller(data));

		SwingUtilities.invokeAndWait(() -> {
			JComboBox<?> mode = (JComboBox<?>) fieldValue(panel, "mode");
			mode.setSelectedItem("Simple");
			assertTrue(data.getMedicalPlan().isSimpleMode());
			mode.setSelectedItem("Detailed");
			panel.pushToModel();
		});

		assertFalse(data.getMedicalPlan().isSimpleMode());
		assertEquals("Fictional City Hospital", data.getMedicalPlan().getHospitals().get(0).getName());
		assertNull(data.getMedicalPlan().getHospitals().get(0).getHelipad());
	}

	@Test
	void pushRetainsCustomRecordedReviewStatus() throws Exception {
		AppData data = new AppData();
		data.getMedicalPlan().setReviewStatus("Reviewed - fictional exercise only");
		Ics206Panel panel = new Ics206Panel(controller(data));

		SwingUtilities.invokeAndWait(() -> {
			panel.refreshFromModel();
			panel.pushToModel();
		});

		assertEquals("Reviewed - fictional exercise only", data.getMedicalPlan().getReviewStatus());
	}

	@Test
	void pushFlushesAnActiveHospitalCellEditor() throws Exception {
		AppData data = new AppData();
		Ics206Panel panel = new Ics206Panel(controller(data));

		SwingUtilities.invokeAndWait(() -> {
			JTable table = (JTable) fieldValue(panel, "hospitalTable");
			table.getColumnModel().getColumn(0).setCellEditor(new DefaultCellEditor(new JTextField()));
			((DefaultTableModel) table.getModel()).addRow(
					new Object[] {"", "", "", "", "", "", "", "", "", "", "", "", "Unknown", "", "Unknown", "Unknown"});
			table.editCellAt(0, 0);
			((JTextField) table.getEditorComponent()).setText("Fictional County Hospital");
			panel.pushToModel();
		});

		assertEquals("Fictional County Hospital", data.getMedicalPlan().getHospitals().get(0).getName());
	}

	@Test
	void aidStationIdsSurviveNoOpFlushesAndRowMutations() throws Exception {
		AppData data = new AppData();
		MedicalPlan.AidStation first = new MedicalPlan.AidStation();
		first.setName("First station");
		MedicalPlan.AidStation second = new MedicalPlan.AidStation();
		second.setName("Second station");
		data.getMedicalPlan().getAidStations().addAll(java.util.List.of(first, second));
		Ics206Panel panel = new Ics206Panel(controller(data));
		String firstId = first.getId();
		String secondId = second.getId();

		SwingUtilities.invokeAndWait(() -> {
			DefaultTableModel model = (DefaultTableModel) fieldValue(panel, "aidModel");
			panel.pushToModel();
			panel.pushToModel();
			assertEquals(java.util.List.of(firstId, secondId), data.getMedicalPlan().getAidStations().stream()
					.map(MedicalPlan.AidStation::getId).toList());

			model.moveRow(0, 0, 1);
			panel.pushToModel();
			assertEquals(java.util.List.of(secondId, firstId), data.getMedicalPlan().getAidStations().stream()
					.map(MedicalPlan.AidStation::getId).toList());

			model.addRow(new Object[] {"New station", "", "", "", "Unknown", null});
			panel.pushToModel();
			String newId = data.getMedicalPlan().getAidStations().get(2).getId();
			assertFalse(java.util.List.of(firstId, secondId).contains(newId));
			panel.pushToModel();
			assertEquals(newId, data.getMedicalPlan().getAidStations().get(2).getId());

			model.removeRow(1);
			panel.pushToModel();
			assertEquals(java.util.List.of(secondId, newId), data.getMedicalPlan().getAidStations().stream()
					.map(MedicalPlan.AidStation::getId).toList());
			panel.refreshFromModel();
			panel.pushToModel();
			assertEquals(java.util.List.of(secondId, newId), data.getMedicalPlan().getAidStations().stream()
					.map(MedicalPlan.AidStation::getId).toList());
		});
	}

	@Test
	void unrelatedTextChangeDoesNotFlushAnActiveHospitalCell() throws Exception {
		AppData data = new AppData();
		MedicalPlan.Hospital hospital = new MedicalPlan.Hospital();
		hospital.setName("Recorded hospital");
		data.getMedicalPlan().getHospitals().add(hospital);
		Ics206Panel panel = new Ics206Panel(controller(data));

		SwingUtilities.invokeAndWait(() -> {
			JTable table = (JTable) fieldValue(panel, "hospitalTable");
			table.getColumnModel().getColumn(0).setCellEditor(new DefaultCellEditor(new JTextField()));
			assertTrue(table.editCellAt(0, 0));
			((JTextField) table.getEditorComponent()).setText("Uncommitted cell draft");
			((javax.swing.JTextArea) fieldValue(panel, "procedures")).setText("Updated emergency procedure");
			assertTrue(table.isEditing());
		});

		assertEquals("Updated emergency procedure", data.getMedicalPlan().getEmergencyProcedures());
		assertEquals("Recorded hospital", data.getMedicalPlan().getHospitals().get(0).getName());
	}

	@Test
	void staleEditorFlushCannotOverwriteNewerMedicalPlanValue() throws Exception {
		AppData data = new AppData();
		AppController controller = controller(data);
		Ics206Panel panel = new Ics206Panel(controller);

		SwingUtilities.invokeAndWait(() -> {
			((javax.swing.JTextArea) fieldValue(panel, "procedures")).setText("Older editor draft");
			AppController.EditorCommitBinding newer = controller.captureEditorCommitBinding();
			assertTrue(controller.commitEditorIfCurrent(newer, AppController.LinkSource.NONE,
					() -> data.getMedicalPlan().setEmergencyProcedures("Newer role-panel value")));
			assertFalse(panel.pushToModel());
		});

		assertEquals("Newer role-panel value", data.getMedicalPlan().getEmergencyProcedures());
		assertEquals("This medical plan editor is stale. Keep the draft and refresh before retrying.",
				((javax.swing.JLabel) fieldValue(panel, "validation")).getText());
	}

	@Test
	void staleEditorCannotWriteIntoTheNextOperationalPeriod() throws Exception {
		AppData data = new AppData();
		AppController controller = controller(data);
		Ics206Panel panel = new Ics206Panel(controller);

		SwingUtilities.invokeAndWait(() -> {
			((javax.swing.JTextArea) fieldValue(panel, "procedures")).setText("Old-period draft");
			controller.save();
			controller.newOperationalPeriod();
			AppController.EditorCommitBinding newPeriod = controller.captureEditorCommitBinding();
			assertTrue(controller.commitEditorIfCurrent(newPeriod, AppController.LinkSource.NONE,
					() -> data.getMedicalPlan().setEmergencyProcedures("New-period value")));
			assertFalse(panel.pushToModel());
		});

		assertEquals("New-period value", data.getMedicalPlan().getEmergencyProcedures());
	}

	@Test
	void medicalRolePickerFieldsEditTheSharedRoleWithoutRewritingLegacyCopies() throws Exception {
		AppData data = new AppData();
		data.getOrganizationalChart().setMedicalUnitLeaderName("Fictional Medical Leader");
		data.getOrganizationalChart().setSafetyOfficerName("Fictional Safety Officer");
		Ics206Panel panel = new Ics206Panel(controller(data));

		SwingUtilities.invokeAndWait(() -> {
			assertEquals("Fictional Medical Leader", ((JTextField) fieldValue(panel, "medicalName")).getText());
			assertEquals("Fictional Medical Leader", ((JTextField) fieldValue(panel, "preparedBy")).getText());
			assertEquals("Fictional Safety Officer", ((JTextField) fieldValue(panel, "reviewedBy")).getText());
			((JTextField) fieldValue(panel, "medicalName")).setText("Selected Medical Leader");
			((JTextField) fieldValue(panel, "preparedBy")).setText("Selected Medical Leader");
			((JTextField) fieldValue(panel, "reviewedBy")).setText("Selected Safety Officer");
			panel.pushToModel();
		});

		assertEquals("Selected Medical Leader", data.getMedicalUnitLeaderAssignment().getName());
		assertEquals("Fictional Medical Leader", data.getOrganizationalChart().getMedicalUnitLeaderName());
		assertEquals("", data.getOrganizationalChart().getAssignmentList().getPosition(
				org.sarmanagement.icsforms.model.Ics203Form.Position.MEDICAL_UNIT_LEADER));
		assertEquals("", data.getMedicalPlan().getPreparedByMedicalUnitLeader());
		assertEquals("", data.getCanineMedicalPlan().getPreparedByMedicalUnitLeader());
		assertEquals("Selected Safety Officer", data.getOrganizationalChart().getSafetyOfficerName());
		assertEquals("Selected Safety Officer", data.getCanineMedicalPlan().getReviewedBySafetyOfficer());
	}

	@Test
	void detailedSectionsAreVisibleAndSignatureValuesRemainOnThePdfModel() throws Exception {
		AppData data = new AppData();
		data.getMedicalPlan().setPreparerSignature("Previously recorded preparer");
		data.getMedicalPlan().setSafetyOfficerSignature("Previously recorded reviewer");
		Ics206Panel panel = new Ics206Panel(controller(data));

		SwingUtilities.invokeAndWait(() -> {
			((JComboBox<?>) fieldValue(panel, "mode")).setSelectedItem("Detailed");
			assertTrue(((javax.swing.JTabbedPane) fieldValue(panel, "detailTabs")).isVisible());
			javax.swing.JTabbedPane details = (javax.swing.JTabbedPane) fieldValue(panel, "detailTabs");
			assertTrue(contains(details.getComponentAt(1), (Component) fieldValue(panel, "aviationUsed")));
			assertFalse(contains(details.getComponentAt(3), (Component) fieldValue(panel, "aviationUsed")));
			panel.pushToModel();
		});

		assertEquals("Previously recorded preparer", data.getMedicalPlan().getPreparerSignature());
		assertEquals("Previously recorded reviewer", data.getMedicalPlan().getSafetyOfficerSignature());
	}

	/**
	 * Searches a nested component tree for a specific editor component.
	 *
	 * @param parent root component.
	 * @param target component to find.
	 * @return true when the target is a descendant of the root.
	 */
	private static boolean contains(Component parent, Component target) {
		if (parent == target) {
			return true;
		}
		if (parent instanceof java.awt.Container container) {
			for (Component child : container.getComponents()) {
				if (contains(child, target)) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Creates a test controller backed by a temporary local incident file.
	 *
	 * @param data active incident document.
	 * @return configured controller.
	 * @throws Exception when the temporary repository cannot be created.
	 */
	private AppController controller(AppData data) throws Exception {
		return IncidentStoreTestFixtures.controller(data, new PdfExportService(new Ics206PdfRenderer()),
				new IncidentValidator());
	}

	/**
	 * Returns a private Swing field for focused component tests.
	 *
	 * @param instance panel under test.
	 * @param fieldName component field name.
	 * @return field value.
	 */
	private static Object fieldValue(Object instance, String fieldName) {
		try {
			Field field = instance.getClass().getDeclaredField(fieldName);
			field.setAccessible(true);
			return field.get(instance);
		} catch (ReflectiveOperationException exception) {
			throw new IllegalStateException(exception);
		}
	}
}
