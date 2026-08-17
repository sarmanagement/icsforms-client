package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.Ics201Form;
import org.sarmanagement.icsforms.model.Ics202Form;
import org.sarmanagement.icsforms.model.Ics204Form;
import org.sarmanagement.icsforms.model.IncidentContext;
import org.sarmanagement.icsforms.persistence.LocalRepository;
import org.sarmanagement.icsforms.pdf.Ics201PdfRenderer;
import org.sarmanagement.icsforms.pdf.Ics202PdfRenderer;
import org.sarmanagement.icsforms.pdf.Ics204PdfRenderer;
import org.sarmanagement.icsforms.pdf.PdfExportService;
import org.sarmanagement.icsforms.pdf.SarTaskAssignmentPdfRenderer;
import org.sarmanagement.icsforms.validation.IncidentValidator;

import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Ics201PanelTest {

    @Test
    void objectivesAndActionRowsRoundTripThroughPanel() throws Exception {
        AppController controller = sampleController();
        Ics201Panel panel = new Ics201Panel(controller);

        JTextArea objectivesArea = (JTextArea) fieldValue(panel, "objectivesArea");
        JTable actionTable = (JTable) fieldValue(panel, "actionTable");
        JTextField incidentNumberField = (JTextField) fieldValue(panel, "incidentNumberField");

        SwingUtilities.invokeAndWait(() -> {
            panel.refreshFromModel();
            incidentNumberField.setText("INC-201");
            objectivesArea.setText("Protect life\nStabilize scene");
            actionTable.getModel().setValueAt("07:00", 0, 0);
            actionTable.getModel().setValueAt("Dispatch team", 0, 1);
            panel.pushToModel();
        });

        Ics201Form form = controller.getData().getForm201();
        assertEquals("INC-201", form.getIncidentNumber());
        assertEquals(List.of("Protect life", "Stabilize scene"), form.getCurrentObjectives());
        assertEquals(1, form.getCurrentActions().size());
        assertEquals("07:00", form.getCurrentActions().get(0).getTime());
        assertEquals("Dispatch team", form.getCurrentActions().get(0).getActions());
    }

    private static Object fieldValue(Object instance, String fieldName) throws Exception {
        Field field = instance.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(instance);
    }

    private static AppController sampleController() {
        IncidentContext context = new IncidentContext("Test Incident",
                LocalDateTime.parse("2026-01-01T00:00:00"),
                LocalDateTime.parse("2026-01-01T12:00:00"),
                "Planner", "Planning Section Chief");
        AppData data = new AppData(context, new Ics202Form(), new Ics204Form(), List.of());
        data.getForm201().setCurrentActions(List.of(new Ics201Form.ActionEntry()));
        Path path = Path.of("target", "test-output", "ics201-panel-" + System.nanoTime(), "incident.json");
        return new AppController(
                data,
                new LocalRepository(path),
                new PdfExportService(new Ics201PdfRenderer(), new Ics202PdfRenderer(), new Ics204PdfRenderer(), new SarTaskAssignmentPdfRenderer()),
                new IncidentValidator());
    }
}
