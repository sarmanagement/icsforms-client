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

import javax.swing.JButton;
import javax.swing.JPanel;
import java.awt.Component;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClueLogPanelTest {

    @Test
    void clueLogOnlyShowsAddButton() throws Exception {
        ClueLogPanel panel = new ClueLogPanel(sampleController());
        JPanel buttons = (JPanel) panel.getComponent(1);

        assertEquals(1, buttons.getComponentCount());
        assertEquals("Add", ((JButton) buttons.getComponent(0)).getText());
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
