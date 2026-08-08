package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.Ics202Form;
import org.sarmanagement.icsforms.model.Ics204Form;
import org.sarmanagement.icsforms.model.IncidentContext;
import org.sarmanagement.icsforms.model.ResourceAssignment;
import org.sarmanagement.icsforms.persistence.LocalRepository;
import org.sarmanagement.icsforms.pdf.Ics202PdfRenderer;
import org.sarmanagement.icsforms.pdf.Ics204PdfRenderer;
import org.sarmanagement.icsforms.pdf.PdfExportService;
import org.sarmanagement.icsforms.pdf.SarTaskAssignmentPdfRenderer;
import org.sarmanagement.icsforms.validation.IncidentValidator;

import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class Ics204PanelTest {

    @Test
    void resourcesGridHidesAssignmentIdAndOffersPopupEditor() throws Exception {
        AppController controller = sampleController();
        Ics204Panel panel = new Ics204Panel(controller);
        SwingUtilities.invokeAndWait(panel::refreshFromModel);

        JTable table = (JTable) fieldValue(panel, "resourceTable");
        JPopupMenu menu = table.getComponentPopupMenu();

        assertEquals("Assignment/Team #", table.getColumnName(0));
        assertEquals("Resource Type", table.getColumnName(1));
        assertNotNull(menu);
        assertEquals("Edit assignment…", ((JMenuItem) menu.getComponent(0)).getText());
    }

    @Test
    void managementContactsUseCompactSingleRowPerRole() throws Exception {
        AppController controller = sampleController();
        Ics204Panel panel = new Ics204Panel(controller);
        SwingUtilities.invokeAndWait(panel::refreshFromModel);

        JPanel contactsPanel = (JPanel) fieldValue(panel, "managementContactsPanel");

        assertEquals(8, contactsPanel.getComponentCount());
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
        Ics204Form form204 = new Ics204Form();
        form204.setManagementContext(Ics204Form.MANAGEMENT_DIVISION);
        form204.setDivision("Division A");
        form204.setOperationsSectionChiefName("Ops Chief");
        form204.setOperationsSectionChiefContact("Tac 1");
        form204.setDivisionGroupSupervisorName("Supervisor");
        form204.setDivisionGroupSupervisorContact("Tac 2");
        ResourceAssignment resource = new ResourceAssignment();
        resource.setAssignmentTeamNumber("A-1");
        resource.setResourceType("Ground");
        resource.setTaskType("Area");
        resource.setResourceIdentifier("Team 1");
        resource.setLeader("Leader A");
        resource.setNumberOfPersons(3);
        resource.setAssignment("Search trail");
        form204.setResourcesAssigned(List.of(resource));
        AppData data = new AppData(context, new Ics202Form(), form204, List.of());
        return new AppController(
                data,
                new LocalRepository(Files.createTempDirectory("icsforms-ui").resolve("incident.json")),
                new PdfExportService(new Ics202PdfRenderer(), new Ics204PdfRenderer(), new SarTaskAssignmentPdfRenderer()),
                new IncidentValidator());
    }
}
