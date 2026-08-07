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
            checkbox[0].setSelected(true);
            panel.pushToModel();
        });

        assertTrue(controller.getData().getForm202().getIncidentActionPlanAttachments().contains("SAR Task Assignment"));
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
        IncidentContext context = new IncidentContext("Test Incident",
                LocalDateTime.parse("2026-01-01T00:00:00"),
                LocalDateTime.parse("2026-01-01T12:00:00"),
                "Planner", "Planning Section Chief");
        AppData data = new AppData(context, new Ics202Form(), new Ics204Form(), List.of());
        return new AppController(
                data,
                new LocalRepository(Files.createTempDirectory("icsforms-ui").resolve("incident.json")),
                new PdfExportService(new Ics202PdfRenderer(), new Ics204PdfRenderer(), new SarTaskAssignmentPdfRenderer()),
                new IncidentValidator());
    }
}
