/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.MedicalPlan;
import org.sarmanagement.icsforms.model.OrgChartEntry;
import org.sarmanagement.icsforms.persistence.LocalRepository;
import org.sarmanagement.icsforms.pdf.Ics206PdfRenderer;
import org.sarmanagement.icsforms.pdf.PdfExportService;
import org.sarmanagement.icsforms.validation.IncidentValidator;

import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
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
	void roleSuggestionsReadOrgChartWithoutApplyingNamesOrApproval() {
		AppData data = new AppData();
		data.getOrganizationalChart().setSafetyOfficerName("Fictional Safety Officer");
		OrgChartEntry medicalLeader = new OrgChartEntry();
		medicalLeader.setTitle("Medical Unit Leader");
		medicalLeader.setName("Fictional Medical Leader");
		data.getOrganizationalChart().getAdditionalPositions().add(medicalLeader);
		data.getMedicalPlan().setSafetyOfficerSignature("Manual signature");

		assertEquals(java.util.List.of("Fictional Medical Leader"), Ics206Panel.roleCandidates(data, true));
		assertEquals(java.util.List.of("Fictional Safety Officer"),
				Ics206Panel.roleCandidates(data, false));
		assertEquals("", data.getMedicalPlan().getReviewedBySafetyOfficer());
		assertEquals("Manual signature", data.getMedicalPlan().getSafetyOfficerSignature());
		assertEquals("Not reviewed", data.getMedicalPlan().getReviewStatus());
	}

	@Test
	void stagingLocationShortcutOpensTheSharedCanineWorkspace() throws Exception {
		Ics206Panel panel = new Ics206Panel(controller(new AppData()));
		java.util.List<String> opened = new java.util.ArrayList<>();
		panel.setWorkspaceRequest(opened::add);

		SwingUtilities.invokeAndWait(() -> ((JButton) fieldValue(panel, "stagingWorkspace")).doClick());

		assertEquals(java.util.List.of("Canine Medical Plan (ICS 206CANINE, adapted)"), opened);
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
