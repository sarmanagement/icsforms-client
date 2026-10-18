/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.MedicalPlan;
import org.sarmanagement.icsforms.persistence.LocalRepository;
import org.sarmanagement.icsforms.pdf.Ics206PdfRenderer;
import org.sarmanagement.icsforms.pdf.PdfExportService;
import org.sarmanagement.icsforms.validation.IncidentValidator;

import javax.swing.DefaultCellEditor;
import javax.swing.JComboBox;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
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
	void medicalRolePickerFieldsShareTheOrgChartAssignments() throws Exception {
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

		assertEquals("Selected Medical Leader", data.getOrganizationalChart().getMedicalUnitLeaderName());
		assertEquals("Selected Medical Leader",
				data.getOrganizationalChart().getAssignmentList().getPosition(
						org.sarmanagement.icsforms.model.Ics203Form.Position.MEDICAL_UNIT_LEADER));
		assertEquals("Selected Medical Leader", data.getMedicalPlan().getPreparedByMedicalUnitLeader());
		assertEquals("Selected Medical Leader", data.getCanineMedicalPlan().getPreparedByMedicalUnitLeader());
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
			panel.pushToModel();
		});

		assertEquals("Previously recorded preparer", data.getMedicalPlan().getPreparerSignature());
		assertEquals("Previously recorded reviewer", data.getMedicalPlan().getSafetyOfficerSignature());
	}

	/**
	 * Creates a test controller backed by a temporary local incident file.
	 *
	 * @param data active incident document.
	 * @return configured controller.
	 * @throws Exception when the temporary repository cannot be created.
	 */
	private AppController controller(AppData data) throws Exception {
		return new AppController(data, new LocalRepository(tempDirectory.resolve("incident.json")),
				new PdfExportService(new Ics206PdfRenderer()), new IncidentValidator());
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
