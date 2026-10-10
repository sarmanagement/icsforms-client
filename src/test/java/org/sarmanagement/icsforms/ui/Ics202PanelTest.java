package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.Ics202Form;
import org.sarmanagement.icsforms.model.Ics204Form;
import org.sarmanagement.icsforms.model.IncidentContext;
import org.sarmanagement.icsforms.persistence.LocalRepository;
import org.sarmanagement.icsforms.pdf.Ics202PdfRenderer;
import org.sarmanagement.icsforms.pdf.Ics204PdfRenderer;
import org.sarmanagement.icsforms.pdf.PdfExportService;
import org.sarmanagement.icsforms.pdf.SarTaskAssignmentPdfRenderer;
import org.sarmanagement.icsforms.validation.IncidentValidator;

import javax.swing.JCheckBox;
import javax.swing.SwingUtilities;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Ics202PanelTest {

	@Test
	void sarTaskAssignmentIncludedFormRoundTripsThroughPanel() throws Exception {
		AppController controller = sampleController();
		controller.getData().getForm202().setIncidentActionPlanAttachments(List.of("SAR Task Assignment"));
		Ics202Panel panel = new Ics202Panel(controller);

		JCheckBox[] checkbox = new JCheckBox[1];
		SwingUtilities.invokeAndWait(() -> {
			panel.refreshFromModel();
			checkbox[0] = (JCheckBox) fieldValue(panel, "includeSarTaskAssignment");
		});

		assertTrue(checkbox[0].isSelected());

		SwingUtilities.invokeAndWait(() -> {
			checkbox[0].setSelected(false);
			panel.pushToModel();
		});

		assertFalse(
				controller.getData().getForm202().getIncidentActionPlanAttachments().contains("SAR Task Assignment"));
	}

	@Test
	void safetyAttachmentsRoundTripAndDriveExportDialogDefaults() throws Exception {
		AppController controller = sampleController();
		controller.getData().getForm202().setIncidentActionPlanAttachments(List.of("ICS 215A"));
		Ics202Panel panel = new Ics202Panel(controller);
		JCheckBox[] checkbox = new JCheckBox[1];
		SwingUtilities.invokeAndWait(() -> {
			panel.refreshFromModel();
			checkbox[0] = (JCheckBox) fieldValue(panel, "includeIcs215a");
		});

		assertTrue(checkbox[0].isSelected());
		assertTrue(MainFrame.isExportFormSelectedByDefault("ICS 215A", controller.getData()));
		assertFalse(MainFrame.isExportFormSelectedByDefault("ICS 208", controller.getData()));
		assertEquals("ICS 208 – Safety Message / Plan", MainFrame.exportFormDisplayLabel("ICS 208"));
		assertEquals("ICS 215A – Incident Action Plan Safety Analysis",
				MainFrame.exportFormDisplayLabel("ICS 215A"));
		assertEquals(List.of("ICS 204", "ICS 205A", "ICS 207", "ICS 208", "ICS 214", "ICS 215A", "SAR Task Assignment"),
				MainFrame.sortExportFormKeys(
						List.of("ICS 215A", "SAR Task Assignment", "ICS 208", "ICS 214", "ICS 207", "ICS 205A", "ICS 204")));
		assertEquals(List.of("ICS 204", "ICS 208", "ICS 215A"),
				MainFrame.mergeSelectedFormsWithIcs202Attachments(List.of("ICS 204", "ICS 208"),
						List.of("ICS 215A", "ICS 208")));

		SwingUtilities.invokeAndWait(() -> {
			checkbox[0].setSelected(false);
			panel.pushToModel();
		});
		assertFalse(controller.getData().getForm202().getIncidentActionPlanAttachments().contains("ICS 215A"));
	}

	private static Object fieldValue(Object instance, String fieldName) {
		try {
			Field field = instance.getClass().getDeclaredField(fieldName);
			field.setAccessible(true);
			return field.get(instance);
		} catch (ReflectiveOperationException exception) {
			throw new IllegalStateException(exception);
		}
	}

	private static AppController sampleController() throws Exception {
		IncidentContext context = new IncidentContext("Test Incident", LocalDateTime.parse("2026-01-01T00:00:00"),
				LocalDateTime.parse("2026-01-01T12:00:00"), "Planner", "Planning Section Chief");
		AppData data = new AppData(context, new Ics202Form(), new Ics204Form(), List.of());
		return new AppController(data,
				new LocalRepository(Files.createTempDirectory("icsforms-ui").resolve("incident.json")),
				new PdfExportService(new Ics202PdfRenderer(), new Ics204PdfRenderer(),
						new SarTaskAssignmentPdfRenderer()),
				new IncidentValidator());
	}
}
