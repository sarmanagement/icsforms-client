package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.ClueLogEntry;
import org.sarmanagement.icsforms.model.CommunicationEntry;
import org.sarmanagement.icsforms.model.Ics202Form;
import org.sarmanagement.icsforms.model.Ics204Form;
import org.sarmanagement.icsforms.model.IncidentContext;
import org.sarmanagement.icsforms.model.PodFactorRating;
import org.sarmanagement.icsforms.model.ResourceAssignment;
import org.sarmanagement.icsforms.model.SarTaskAssignment;
import org.sarmanagement.icsforms.model.SarTaskResource;
import org.sarmanagement.icsforms.model.SarTaskSupport;
import org.sarmanagement.icsforms.persistence.LocalRepository;
import org.sarmanagement.icsforms.pdf.Ics202PdfRenderer;
import org.sarmanagement.icsforms.pdf.Ics204PdfRenderer;
import org.sarmanagement.icsforms.pdf.PdfExportService;
import org.sarmanagement.icsforms.pdf.SarTaskAssignmentPdfRenderer;
import org.sarmanagement.icsforms.validation.IncidentValidator;

import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SarTaskPanelTest {

    @Test
    void sarTaskEditorMarksRequiredAssignmentFieldAccessibly() throws Exception {
        SarTaskAssignment task = sampleTask();
        Object editor = createEditor(task, "ASSIGNMENT");
        Class<?> editorClass = editor.getClass();

        Field panelField = editorClass.getDeclaredField("panel");
        panelField.setAccessible(true);
        Field assignmentField = editorClass.getDeclaredField("assignmentTeamNumberField");
        assignmentField.setAccessible(true);

        JLabel[] labelHolder = new JLabel[1];
        JTextField[] fieldHolder = new JTextField[1];
        SwingUtilities.invokeAndWait(() -> {
            fieldHolder[0] = (JTextField) getFieldValue(assignmentField, editor);
            labelHolder[0] = findLabel((Component) getFieldValue(panelField, editor), "Assignment/Team # (required)");
        });

        assertNotNull(labelHolder[0]);
        assertNotNull(fieldHolder[0]);
        assertSame(fieldHolder[0], labelHolder[0].getLabelFor());
        assertEquals(UiSupport.REQUIRED_FIELD_BACKGROUND, fieldHolder[0].getBackground());
        assertTrue(fieldHolder[0].getAccessibleContext().getAccessibleDescription().contains("required field"));
    }

    @Test
    void debriefEditorShowsDebriefFieldsWithoutAssignmentOnlyFields() throws Exception {
        Object editor = createEditor(sampleTask(), "DEBRIEFING");
        Field panelField = editor.getClass().getDeclaredField("panel");
        panelField.setAccessible(true);

        JLabel[] debriefLabel = new JLabel[1];
        JLabel[] reportedPodLabel = new JLabel[1];
        JLabel[] resourcesLabel = new JLabel[1];
        SwingUtilities.invokeAndWait(() -> {
            Component panel = (Component) getFieldValue(panelField, editor);
            debriefLabel[0] = findLabel(panel, "Debriefing");
            reportedPodLabel[0] = findLabel(panel, "Reported POD (%)");
            resourcesLabel[0] = findLabel(panel, "Resources assigned");
        });

        assertNotNull(debriefLabel[0]);
        assertNotNull(reportedPodLabel[0]);
        assertNull(resourcesLabel[0]);
    }

    @Test
    void sarTaskGridMarksRequiredAssignmentColumn() throws Exception {
        AppController controller = sampleController();
        SarTaskPanel panel = new SarTaskPanel(controller);
        SwingUtilities.invokeAndWait(panel::refreshFromModel);

        Field tableField = SarTaskPanel.class.getDeclaredField("table");
        tableField.setAccessible(true);
        JTable table = (JTable) tableField.get(panel);

        assertEquals("Assignment/Team # (required)", table.getColumnName(0));
        assertEquals("Task Geometry", table.getColumnName(3));
        Component component = table.prepareRenderer(table.getCellRenderer(0, 0), 0, 0);
        assertEquals(UiSupport.REQUIRED_FIELD_BACKGROUND, component.getBackground());
    }

    @Test
    void sarTaskGridPopupOffersAssignmentAndDebriefingEditors() throws Exception {
        AppController controller = sampleController();
        SarTaskPanel panel = new SarTaskPanel(controller);
        SwingUtilities.invokeAndWait(panel::refreshFromModel);

        Field tableField = SarTaskPanel.class.getDeclaredField("table");
        tableField.setAccessible(true);
        JTable table = (JTable) tableField.get(panel);
        JPopupMenu menu = table.getComponentPopupMenu();

        assertNotNull(menu);
        assertEquals("Edit assignment…", ((JMenuItem) menu.getComponent(0)).getText());
        assertEquals("Edit debriefing…", ((JMenuItem) menu.getComponent(1)).getText());
    }

    @Test
    void assignmentEditorUsesCompactResourceListWithoutPrimaryTaskResource() throws Exception {
        SarTaskAssignment task = sampleTask();
        List<SarTaskResource> resources = new ArrayList<>(task.getResourcesAssigned());
        SarTaskResource primary = new SarTaskResource();
        primary.setFunction("Resource");
        primary.setName(task.getResourceIdentifier());
        resources.add(primary);
        SarTaskResource medic = new SarTaskResource();
        medic.setFunction("Medic");
        medic.setName("Alex");
        resources.add(medic);
        task.setResourcesAssigned(resources);

        Object editor = createEditor(task, "ASSIGNMENT");
        Field panelField = editor.getClass().getDeclaredField("panel");
        panelField.setAccessible(true);
        Field resourceFieldsField = editor.getClass().getDeclaredField("resourceEntryFields");
        resourceFieldsField.setAccessible(true);

        JLabel[] communicationsLabel = new JLabel[1];
        List<?> resourceFields = (List<?>) getFieldValue(resourceFieldsField, editor);
        SwingUtilities.invokeAndWait(() -> communicationsLabel[0] = findLabel((Component) getFieldValue(panelField, editor), "Communications"));

        assertNull(communicationsLabel[0]);
        assertEquals(18, resourceFields.size());
        assertEquals("Leader/Handler", fieldText(resourceFields.get(0), "functionField"));
        assertEquals("Leader A", fieldText(resourceFields.get(0), "nameField"));
        assertEquals("Medic", fieldText(resourceFields.get(1), "functionField"));
        assertEquals("Alex", fieldText(resourceFields.get(1), "nameField"));
        for (Object resourceField : resourceFields) {
            assertTrue(!"Team 1".equals(fieldText(resourceField, "nameField")));
        }
    }

    private static Object createEditor(SarTaskAssignment task, String modeName) throws Exception {
        Class<?> editorClass = Class.forName("org.sarmanagement.icsforms.ui.SarTaskPanel$SarTaskEditor");
        Class<?> modeClass = Class.forName("org.sarmanagement.icsforms.ui.SarTaskPanel$EditorMode");
        Object mode = enumConstant(modeClass, modeName);
        Constructor<?> constructor = editorClass.getDeclaredConstructor(SarTaskAssignment.class, modeClass, List.class);
        constructor.setAccessible(true);
        return constructor.newInstance(task, mode, List.of());
    }

    private static Object enumConstant(Class<?> enumClass, String name) {
        for (Object constant : enumClass.getEnumConstants()) {
            if (name.equals(((Enum<?>) constant).name())) {
                return constant;
            }
        }
        throw new IllegalArgumentException("Missing enum constant " + name);
    }

    private static Object getFieldValue(Field field, Object instance) {
        try {
            return field.get(instance);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static JLabel findLabel(Component component, String text) {
        if (component instanceof JLabel label && text.equals(label.getText())) {
            return label;
        }
        if (component instanceof java.awt.Container container) {
            for (Component child : container.getComponents()) {
                JLabel label = findLabel(child, text);
                if (label != null) {
                    return label;
                }
            }
        }
        return null;
    }

    private static SarTaskAssignment sampleTask() {
        ResourceAssignment resource = new ResourceAssignment();
        resource.setAssignmentId("assign-1");
        resource.setAssignmentTeamNumber("A-1");
        resource.setResourceType(SarTaskSupport.RESOURCE_TYPE_CANINE);
        resource.setTaskType(SarTaskSupport.TASK_TYPE_AREA);
        resource.setResourceIdentifier("Team 1");
        resource.setLeaderRole("Leader/Handler");
        resource.setLeader("Leader A");
        resource.setContact("555-0101");
        resource.setAssignment("Search trail segment");
        resource.setSpecialEquipment("ATV");
        resource.setSupplies("Medical kit");

        CommunicationEntry communicationEntry = new CommunicationEntry();
        communicationEntry.setName("Team 1 Lead");
        communicationEntry.setFunction("Medical");
        communicationEntry.setPrimaryContact("Tac 1");

        IncidentContext context = new IncidentContext("Test Incident",
                LocalDateTime.parse("2026-01-01T00:00:00"),
                LocalDateTime.parse("2026-01-01T12:00:00"),
                "Planner", "Planning Section Chief");
        context.setTaskMap("Map-42");

        Ics204Form form204 = new Ics204Form();
        form204.setManagementContext(Ics204Form.MANAGEMENT_DIVISION);
        form204.setDivision("Division A");
        form204.setOperationsSectionChiefName("Ops Chief");
        form204.setOperationsSectionChiefContact("555-0199");
        form204.setDivisionGroupSupervisorName("Supervisor");
        form204.setDivisionGroupSupervisorContact("Tac 2");
        form204.setCommunications(List.of(communicationEntry));
        form204.setSharedWorkAssignment("Shared assignment");
        form204.setSpecialInstructions("Maintain radio discipline");
        form204.setPreparedDateTime(LocalDateTime.parse("2026-01-01T02:00:00"));

        SarTaskAssignment task = SarTaskAssignment.fromResourceAssignment(resource, context, form204);
        task.setReportedPod("60");
        List<PodFactorRating> ratings = SarTaskSupport.factorRatings(task.getResourceType(), List.of());
        task.setQualitativePodFactors(ratings);
        return task;
    }

    private static String fieldText(Object instance, String fieldName) {
        try {
            Field field = instance.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return ((JTextField) field.get(instance)).getText();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static AppController sampleController() throws Exception {
        SarTaskAssignment task = sampleTask();
        ResourceAssignment resource = new ResourceAssignment();
        resource.setAssignmentId(task.getAssignmentId());
        resource.setAssignmentTeamNumber(task.getAssignmentTeamNumber());
        resource.setResourceType(task.getResourceType());
        resource.setTaskType(task.getTaskType());
        resource.setResourceIdentifier(task.getResourceIdentifier());
        resource.setLeaderRole(task.getLeaderRole());
        resource.setLeader(task.getLeader());
        resource.setContact(task.getContact());
        resource.setAssignment(task.getAssignment());

        Ics204Form form204 = new Ics204Form();
        form204.setManagementContext(Ics204Form.MANAGEMENT_DIVISION);
        form204.setDivision(task.getDivision());
        form204.setOperationsSectionChiefName(task.getOperationsSectionChiefName());
        form204.setOperationsSectionChiefContact(task.getOperationsSectionChiefContact());
        form204.setDivisionGroupSupervisorName(task.getSecondaryManagementName());
        form204.setDivisionGroupSupervisorContact(task.getSecondaryManagementContact());
        form204.setResourcesAssigned(List.of(resource));
        form204.setCommunications(task.getCommunications());
        form204.setSharedWorkAssignment(task.getAssignment());

        IncidentContext context = new IncidentContext(task.getIncidentName(),
                LocalDateTime.parse("2026-01-01T00:00:00"),
                LocalDateTime.parse("2026-01-01T12:00:00"),
                "Planner", "Planning Section Chief");
        context.setTaskMap(task.getTaskMap());

        AppData data = new AppData(context, new Ics202Form(), form204, List.of(task));
        ClueLogEntry clue = new ClueLogEntry();
        clue.setAssignmentId(task.getAssignmentId());
        clue.setDetectingTask(task.getAssignmentTeamNumber());
        clue.setDescription("Footprint");
        data.setClueLogEntries(List.of(clue));
        return new AppController(
                data,
                new LocalRepository(Files.createTempDirectory("icsforms-ui").resolve("incident.json")),
                new PdfExportService(new Ics202PdfRenderer(), new Ics204PdfRenderer(), new SarTaskAssignmentPdfRenderer()),
                new IncidentValidator());
    }
}
