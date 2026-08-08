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
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;
import java.awt.Component;
import java.lang.reflect.Method;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SarTaskPanelTest {

    @Test
    void sarTaskEditorMarksRequiredAssignmentFieldAccessibly() throws Exception {
        SarTaskAssignment task = sampleTask();
        Object editor = createEditor(task, "ASSIGNMENT", 1);
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
        Object editor = createEditor(sampleTask(), "DEBRIEFING", 1);
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
        assertEquals("Task Geometry", table.getColumnName(2));
        assertEquals("People", table.getColumnName(7));
        assertEquals(3, table.getValueAt(0, 7));
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
        assertEquals("Debrief…", ((JMenuItem) menu.getComponent(1)).getText());
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

        Object editor = createEditor(task, "ASSIGNMENT", 2);
        Field panelField = editor.getClass().getDeclaredField("panel");
        panelField.setAccessible(true);
        Field resourceModelField = editor.getClass().getDeclaredField("resourceEntryTableModel");
        resourceModelField.setAccessible(true);

        JLabel[] communicationsLabel = new JLabel[1];
        SwingUtilities.invokeAndWait(() -> communicationsLabel[0] = findLabel((Component) getFieldValue(panelField, editor), "Communications"));

        AbstractTableModel resourceModel = (AbstractTableModel) getFieldValue(resourceModelField, editor);
        assertNull(communicationsLabel[0]);
        assertEquals(2, resourceModel.getRowCount());
        assertEquals("Leader/Handler", resourceModel.getValueAt(0, 0));
        assertEquals("Leader A", resourceModel.getValueAt(0, 1));
        assertEquals("Medic", resourceModel.getValueAt(1, 0));
        assertEquals("Alex", resourceModel.getValueAt(1, 1));
        for (int rowIndex = 0; rowIndex < resourceModel.getRowCount(); rowIndex++) {
            assertNotEquals("Team 1", resourceModel.getValueAt(rowIndex, 1));
        }
    }

    @Test
    void assignmentEditorDefaultsResourceRowsFromIcs204PersonCount() throws Exception {
        AppController controller = sampleController();
        SarTaskPanel panel = new SarTaskPanel(controller);
        Method method = SarTaskPanel.class.getDeclaredMethod("defaultResourceEditorRowCount", SarTaskAssignment.class);
        method.setAccessible(true);

        int rowCount = (int) method.invoke(panel, controller.getData().getSarTaskAssignments().get(0));

        assertEquals(3, rowCount);
    }

    @Test
    void debriefEditorUsesCompactPodFactorHeaderAndScoreWidth() throws Exception {
        Object editor = createEditor(sampleTask(), "DEBRIEFING", 1);
        Field podFactorsField = editor.getClass().getDeclaredField("podFactorsField");
        podFactorsField.setAccessible(true);
        Field podFactorEntriesField = editor.getClass().getDeclaredField("podFactorEntryFields");
        podFactorEntriesField.setAccessible(true);

        JLabel[] header = new JLabel[1];
        SwingUtilities.invokeAndWait(() -> header[0] = (JLabel) ((java.awt.Container) getFieldValue(podFactorsField, editor)).getComponent(0));

        List<?> entries = (List<?>) getFieldValue(podFactorEntriesField, editor);
        assertTrue(header[0].getText().contains("Qualitative POD Factors"));
        assertTrue(header[0].getText().contains("Factor"));
        assertEquals(2, textFieldColumns(entries.get(0), "scoreField"));
    }

    @Test
    void debriefEditorUsesGridCluesAndInlineCanineDetails() throws Exception {
        Object editor = createEditor(sampleTask(), "DEBRIEFING", 1);
        Field panelField = editor.getClass().getDeclaredField("panel");
        panelField.setAccessible(true);
        Field clueEntriesFieldField = editor.getClass().getDeclaredField("clueEntriesField");
        clueEntriesFieldField.setAccessible(true);
        Field clueTableModelField = editor.getClass().getDeclaredField("clueEntryTableModel");
        clueTableModelField.setAccessible(true);
        Field podFactorsFieldField = editor.getClass().getDeclaredField("podFactorsField");
        podFactorsFieldField.setAccessible(true);
        Field canineSearchTypeField = editor.getClass().getDeclaredField("canineSearchTypeField");
        canineSearchTypeField.setAccessible(true);
        Field canineImprintField = editor.getClass().getDeclaredField("canineImprintField");
        canineImprintField.setAccessible(true);

        JLabel[] canineHeading = new JLabel[1];
        JLabel[] resourceTypeLabel = new JLabel[1];
        JLabel[] imprintLabel = new JLabel[1];
        Component[] resourceTypeRow = new Component[1];
        Component[] sunAngleRow = new Component[1];
        Component[] cloudCoverRow = new Component[1];
        Component[] handlerCertificationRow = new Component[1];
        Component[] lightRow = new Component[1];
        SwingUtilities.invokeAndWait(() ->
                canineHeading[0] = findLabel((Component) getFieldValue(panelField, editor), "Canine assignment details"));
        SwingUtilities.invokeAndWait(() -> {
            JPanel podFactors = (JPanel) getFieldValue(podFactorsFieldField, editor);
            resourceTypeLabel[0] = findLabel(podFactors, "Canine resource type");
            imprintLabel[0] = findLabel(podFactors, "Dog imprinted on");
            resourceTypeRow[0] = childContainingLabel(podFactors, "Canine resource type");
            sunAngleRow[0] = childContainingLabel(podFactors, "Sun angle");
            cloudCoverRow[0] = childContainingLabel(podFactors, "Cloud cover");
            handlerCertificationRow[0] = childContainingLabel(podFactors, "Handler/K-9 Certification (1-5)");
            lightRow[0] = childContainingLabel(podFactors, "Light (1-5)");
        });

        AbstractTableModel clueModel = (AbstractTableModel) getFieldValue(clueTableModelField, editor);
        JPanel[] cluePanel = new JPanel[1];
        SwingUtilities.invokeAndWait(() -> cluePanel[0] = (JPanel) getFieldValue(clueEntriesFieldField, editor));
        assertNull(canineHeading[0]);
        assertNotNull(resourceTypeLabel[0]);
        assertNotNull(imprintLabel[0]);
        assertNotNull(resourceTypeRow[0]);
        assertNotNull(sunAngleRow[0]);
        assertNotNull(cloudCoverRow[0]);
        assertEquals(4, ((JPanel) resourceTypeRow[0]).getComponentCount());
        assertEquals(1, gridX(resourceTypeRow[0]));
        assertEquals(1, gridX(sunAngleRow[0]));
        assertEquals(1, gridX(cloudCoverRow[0]));
        assertTrue(gridY(resourceTypeRow[0]) > gridY(handlerCertificationRow[0]));
        assertTrue(gridY(resourceTypeRow[0]) < gridY(lightRow[0]));
        assertEquals("Date/Time", clueModel.getColumnName(0));
        assertEquals("Location", clueModel.getColumnName(1));
        JScrollPane scrollPane = findScrollPane(cluePanel[0]);
        assertNotNull(scrollPane);
        int initialHeight = scrollPane.getPreferredSize().height;
        assertEquals(1, clueModel.getRowCount());
        JComboBox<?> searchTypeCombo = (JComboBox<?>) getFieldValue(canineSearchTypeField, editor);
        JComboBox<?> imprintCombo = (JComboBox<?>) getFieldValue(canineImprintField, editor);
        assertEquals(List.of("", "Wilderness air scent", "Tracking", "Trailing", "Tracking/Trailing",
                        "HRD", "Article", "Patrol", "Water", "Other"),
                comboItems(searchTypeCombo));
        assertEquals(List.of("", "Living human", "HRD", "Both live and HRD", "Article/Track"),
                comboItems(imprintCombo));

        Method addRow = clueModel.getClass().getDeclaredMethod("addRow");
        addRow.setAccessible(true);
        SwingUtilities.invokeAndWait(() -> {
            try {
                addRow.invoke(clueModel);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException(exception);
            }
        });

        assertEquals(2, clueModel.getRowCount());
        assertTrue(scrollPane.getPreferredSize().height > initialHeight);
    }

    private static Object createEditor(SarTaskAssignment task, String modeName) throws Exception {
        return createEditor(task, modeName, 1);
    }

    private static Object createEditor(SarTaskAssignment task, String modeName, int resourceRows) throws Exception {
        Class<?> editorClass = Class.forName("org.sarmanagement.icsforms.ui.SarTaskPanel$SarTaskEditor");
        Class<?> modeClass = Class.forName("org.sarmanagement.icsforms.ui.SarTaskPanel$EditorMode");
        Object mode = enumConstant(modeClass, modeName);
        Constructor<?> constructor = editorClass.getDeclaredConstructor(SarTaskAssignment.class, modeClass, List.class, int.class);
        constructor.setAccessible(true);
        return constructor.newInstance(task, mode, List.of(), resourceRows);
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

    private static JScrollPane findScrollPane(Component component) {
        if (component instanceof JScrollPane scrollPane) {
            return scrollPane;
        }
        if (component instanceof java.awt.Container container) {
            for (Component child : container.getComponents()) {
                JScrollPane scrollPane = findScrollPane(child);
                if (scrollPane != null) {
                    return scrollPane;
                }
            }
        }
        return null;
    }

    private static Component childContainingLabel(JPanel panel, String text) {
        for (Component child : panel.getComponents()) {
            if (findLabel(child, text) != null) {
                return child;
            }
        }
        return null;
    }

    private static int gridX(Component component) {
        return gridBagConstraints(component).gridx;
    }

    private static int gridY(Component component) {
        return gridBagConstraints(component).gridy;
    }

    private static java.awt.GridBagConstraints gridBagConstraints(Component component) {
        return ((java.awt.GridBagLayout) component.getParent().getLayout()).getConstraints(component);
    }

    private static List<String> comboItems(JComboBox<?> comboBox) {
        return IntStream.range(0, comboBox.getItemCount())
                .mapToObj(index -> String.valueOf(comboBox.getItemAt(index)))
                .toList();
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

    private static int textFieldColumns(Object instance, String fieldName) {
        try {
            Field field = instance.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return ((JTextField) field.get(instance)).getColumns();
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
        resource.setNumberOfPersons(3);
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
