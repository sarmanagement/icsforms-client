package org.sarmanagement.icsforms.ui;

import org.sarmanagement.icsforms.model.CommunicationEntry;
import org.sarmanagement.icsforms.model.SarTaskAssignment;
import org.sarmanagement.icsforms.model.SarTaskResource;

import javax.swing.BorderFactory;
import javax.swing.JMenuItem;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Set;
import java.util.List;

/**
 * Editable SAR task assignment and debriefing grid linked from ICS 204 resource assignments.
 */
public class SarTaskPanel extends JPanel {
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Set<Integer> READ_ONLY_COLUMNS = Set.of(1, 2, 3, 4, 5, 6, 7);

    private final AppController controller;
    private final SarTaskTableModel tableModel = new SarTaskTableModel();
    private final JTable table = new JTable(tableModel);

    /**
     * Creates the SAR task assignment panel.
     *
     * @param controller application controller.
     */
    public SarTaskPanel(AppController controller) {
        super(new BorderLayout());
        this.controller = controller;
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setFillsViewportHeight(true);
        table.getColumnModel().getColumn(0).setCellRenderer(new RequiredFieldCellRenderer());
        installRowEditor();
        setBorder(BorderFactory.createTitledBorder("SAR Task Assignment / Debriefing"));
        add(new JScrollPane(table), BorderLayout.CENTER);
    }

    /**
     * Reloads table rows from the latest synced SAR task assignments.
     */
    public void refreshFromModel() {
        controller.syncSarTasks();
        tableModel.setRows(controller.getData().getSarTaskAssignments());
    }

    /**
     * Applies edited rows back to the active document.
     */
    public void pushToModel() {
        controller.getData().setSarTaskAssignments(tableModel.getRows());
    }

    private void installRowEditor() {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem editRowItem = new JMenuItem("Edit row…");
        editRowItem.addActionListener(event -> openSelectedRowEditor());
        menu.add(editRowItem);
        table.setComponentPopupMenu(menu);
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                selectRowAtEvent(event);
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                selectRowAtEvent(event);
            }

            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(event)) {
                    selectRowAtEvent(event);
                    openSelectedRowEditor();
                }
            }
        });
    }

    private void selectRowAtEvent(MouseEvent event) {
        int viewRow = table.rowAtPoint(event.getPoint());
        if (viewRow >= 0) {
            table.setRowSelectionInterval(viewRow, viewRow);
        }
    }

    private void openSelectedRowEditor() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            return;
        }
        editRow(table.convertRowIndexToModel(viewRow));
    }

    private void editRow(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= tableModel.getRowCount()) {
            return;
        }
        if (table.isEditing() && table.getCellEditor() != null) {
            table.getCellEditor().stopCellEditing();
        }
        SarTaskAssignment row = tableModel.getRows().get(rowIndex);
        SarTaskEditor editor = new SarTaskEditor(row);
        JScrollPane scrollPane = new JScrollPane(editor.panel);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setPreferredSize(new Dimension(760, 560));
        String assignmentTeamNumber = row.getAssignmentTeamNumber();
        String title = assignmentTeamNumber == null || assignmentTeamNumber.isBlank()
                ? "Edit SAR Task"
                : "Edit SAR Task " + assignmentTeamNumber;
        if (JOptionPane.showConfirmDialog(this, scrollPane, title,
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
            return;
        }
        editor.applyTo(row);
        tableModel.fireTableRowsUpdated(rowIndex, rowIndex);
        controller.getData().setSarTaskAssignments(tableModel.getRows());
        controller.markDirty();
        int viewRow = table.convertRowIndexToView(rowIndex);
        if (viewRow >= 0 && viewRow < table.getRowCount()) {
            table.setRowSelectionInterval(viewRow, viewRow);
        }
    }

    private static JTextField textField(String value, boolean editable) {
        JTextField field = UiSupport.textField();
        field.setText(value == null ? "" : value);
        field.setEditable(editable);
        return field;
    }

    private static JScrollPane textArea(String value, int rows, boolean editable) {
        JTextArea area = UiSupport.textArea(rows);
        area.setText(value == null ? "" : value);
        area.setEditable(editable);
        return new JScrollPane(area);
    }

    private static JTextArea textAreaFrom(JScrollPane scrollPane) {
        if (!(scrollPane.getViewport().getView() instanceof JTextArea area)) {
            throw new IllegalArgumentException("Expected a JTextArea inside the scroll pane");
        }
        return area;
    }

    private static JPanel resourceEditorPanel(List<SarTaskResource> resources, List<ResourceEntryFields> fields) {
        JPanel panel = new JPanel(new GridLayout(0, 2, 6, 3));
        panel.setOpaque(false);
        panel.add(new JLabel("Function"));
        panel.add(new JLabel("Name"));
        int rowCount = Math.max(3, (resources == null ? 0 : resources.size()) + 1);
        for (int i = 0; i < rowCount; i++) {
            ResourceEntryFields entry = new ResourceEntryFields();
            if (resources != null && i < resources.size()) {
                entry.functionField.setText(resources.get(i).getFunction());
                entry.nameField.setText(resources.get(i).getName());
            }
            fields.add(entry);
            panel.add(entry.functionField);
            panel.add(entry.nameField);
        }
        return panel;
    }

    private static JPanel communicationEditorPanel(List<CommunicationEntry> communications, List<CommunicationEntryFields> fields) {
        JPanel panel = new JPanel(new GridLayout(0, 3, 6, 3));
        panel.setOpaque(false);
        panel.add(new JLabel("Name"));
        panel.add(new JLabel("Function"));
        panel.add(new JLabel("Primary contact"));
        int rowCount = Math.max(3, (communications == null ? 0 : communications.size()) + 1);
        for (int i = 0; i < rowCount; i++) {
            CommunicationEntryFields entry = new CommunicationEntryFields();
            if (communications != null && i < communications.size()) {
                entry.nameField.setText(communications.get(i).getName());
                entry.functionField.setText(communications.get(i).getFunction());
                entry.primaryContactField.setText(communications.get(i).getPrimaryContact());
            }
            fields.add(entry);
            panel.add(entry.nameField);
            panel.add(entry.functionField);
            panel.add(entry.primaryContactField);
        }
        return panel;
    }

    private static List<SarTaskResource> resourceValuesFrom(List<ResourceEntryFields> fields) {
        List<SarTaskResource> resources = new ArrayList<>();
        for (ResourceEntryFields entry : fields) {
            if (entry.functionField.getText().isBlank() && entry.nameField.getText().isBlank()) {
                continue;
            }
            SarTaskResource resource = new SarTaskResource();
            resource.setFunction(entry.functionField.getText().trim());
            resource.setName(entry.nameField.getText().trim());
            resources.add(resource);
        }
        return resources;
    }

    private static List<CommunicationEntry> communicationValuesFrom(List<CommunicationEntryFields> fields) {
        List<CommunicationEntry> communications = new ArrayList<>();
        for (CommunicationEntryFields entry : fields) {
            if (entry.nameField.getText().isBlank() && entry.functionField.getText().isBlank()
                    && entry.primaryContactField.getText().isBlank()) {
                continue;
            }
            CommunicationEntry communication = new CommunicationEntry();
            communication.setName(entry.nameField.getText().trim());
            communication.setFunction(entry.functionField.getText().trim());
            communication.setPrimaryContact(entry.primaryContactField.getText().trim());
            communications.add(communication);
        }
        return communications;
    }

    private static class ResourceEntryFields {
        private final JTextField functionField = UiSupport.textField();
        private final JTextField nameField = UiSupport.textField();
    }

    private static class CommunicationEntryFields {
        private final JTextField nameField = UiSupport.textField();
        private final JTextField functionField = UiSupport.textField();
        private final JTextField primaryContactField = UiSupport.textField();
    }

    private static class SarTaskEditor {
        private final JPanel panel = UiSupport.formPanel();
        private final JTextField assignmentTeamNumberField;
        private final JTextField incidentNameField;
        private final JTextField resourceIdentifierField;
        private final JTextField leaderRoleField;
        private final JTextField leaderField;
        private final JTextField contactField;
        private final JScrollPane operationsField;
        private final JScrollPane contextField;
        private final JPanel resourcesAssignedField;
        private final List<ResourceEntryFields> resourceEntryFields = new ArrayList<>();
        private final JScrollPane assignmentField;
        private final JScrollPane transportationField;
        private final JTextField taskMapField;
        private final JScrollPane specialEquipmentField;
        private final JPanel communicationsField;
        private final List<CommunicationEntryFields> communicationEntryFields = new ArrayList<>();
        private final JTextField debriefingSupervisorField;
        private final JTextField assignmentStartField;
        private final JTextField assignmentEndField;
        private final JTextField vehicleMilesField;
        private final JScrollPane debriefNotesField;
        private final JScrollPane areasNotCoveredField;
        private final JScrollPane hazardsObservedField;

        private SarTaskEditor(SarTaskAssignment row) {
            assignmentTeamNumberField = textField(row.getAssignmentTeamNumber(), true);
            incidentNameField = textField(row.getIncidentName(), false);
            resourceIdentifierField = textField(row.getResourceIdentifier(), false);
            leaderRoleField = textField(row.getLeaderRole(), false);
            leaderField = textField(row.getLeader(), false);
            contactField = textField(row.getContact(), false);
            operationsField = textArea(SarTaskTableModel.joinOperations(row), 2, false);
            contextField = textArea(SarTaskTableModel.joinContext(row), 2, false);
            resourcesAssignedField = resourceEditorPanel(row.getResourcesAssigned(), resourceEntryFields);
            assignmentField = textArea(row.getAssignment(), 3, true);
            transportationField = textArea(row.getTransportationInstructions(), 2, true);
            taskMapField = textField(row.getTaskMap(), true);
            specialEquipmentField = textArea(row.getSpecialEquipment(), 2, true);
            communicationsField = communicationEditorPanel(row.getCommunications(), communicationEntryFields);
            debriefingSupervisorField = textField(row.getDebriefingSupervisor(), true);
            assignmentStartField = textField(SarTaskTableModel.formatDateTime(row.getAssignmentStart()), true);
            assignmentEndField = textField(SarTaskTableModel.formatDateTime(row.getAssignmentEnd()), true);
            vehicleMilesField = textField(row.getVehicleMiles(), true);
            debriefNotesField = textArea(row.getDebriefNotes(), 4, true);
            areasNotCoveredField = textArea(row.getAreasNotCovered(), 3, true);
            hazardsObservedField = textArea(row.getHazardsObserved(), 3, true);

            int rowIndex = 0;
            UiSupport.addRequiredRow(panel, rowIndex++, "Assignment/Team #", assignmentTeamNumberField);
            UiSupport.addRow(panel, rowIndex++, "Incident", incidentNameField);
            UiSupport.addRow(panel, rowIndex++, "Resource", resourceIdentifierField);
            UiSupport.addRow(panel, rowIndex++, "Leader role", leaderRoleField);
            UiSupport.addRow(panel, rowIndex++, "Leader", leaderField);
            UiSupport.addRow(panel, rowIndex++, "Contact", contactField);
            UiSupport.addRow(panel, rowIndex++, "Operations personnel", operationsField);
            UiSupport.addRow(panel, rowIndex++, "Context", contextField);
            UiSupport.addRow(panel, rowIndex++, "Resources assigned", resourcesAssignedField);
            UiSupport.addRow(panel, rowIndex++, "Work assignment", assignmentField);
            UiSupport.addRow(panel, rowIndex++, "Transportation", transportationField);
            UiSupport.addRow(panel, rowIndex++, "Task map", taskMapField);
            UiSupport.addRow(panel, rowIndex++, "Special equipment", specialEquipmentField);
            UiSupport.addRow(panel, rowIndex++, "Communications", communicationsField);
            UiSupport.addRow(panel, rowIndex++, "Debrief supervisor", debriefingSupervisorField);
            UiSupport.addRow(panel, rowIndex++, "Assignment start (yyyy-MM-dd HH:mm)", assignmentStartField);
            UiSupport.addRow(panel, rowIndex++, "Assignment end (yyyy-MM-dd HH:mm)", assignmentEndField);
            UiSupport.addRow(panel, rowIndex++, "Vehicle miles", vehicleMilesField);
            UiSupport.addRow(panel, rowIndex++, "Debriefing", debriefNotesField);
            UiSupport.addRow(panel, rowIndex++, "Areas not covered", areasNotCoveredField);
            UiSupport.addRow(panel, rowIndex, "Hazards observed", hazardsObservedField);
        }

        private void applyTo(SarTaskAssignment row) {
            row.setAssignmentTeamNumber(assignmentTeamNumberField.getText().trim());
            row.setResourcesAssigned(resourceValuesFrom(resourceEntryFields));
            row.setAssignment(textAreaFrom(assignmentField).getText().trim());
            row.setTransportationInstructions(textAreaFrom(transportationField).getText().trim());
            row.setTaskMap(taskMapField.getText().trim());
            row.setSpecialEquipment(textAreaFrom(specialEquipmentField).getText().trim());
            row.setCommunications(communicationValuesFrom(communicationEntryFields));
            row.setDebriefingSupervisor(debriefingSupervisorField.getText().trim());
            row.setAssignmentStart(SarTaskTableModel.parseDateTime(assignmentStartField.getText().trim()));
            row.setAssignmentEnd(SarTaskTableModel.parseDateTime(assignmentEndField.getText().trim()));
            row.setVehicleMiles(vehicleMilesField.getText().trim());
            row.setDebriefNotes(textAreaFrom(debriefNotesField).getText().trim());
            row.setAreasNotCovered(textAreaFrom(areasNotCoveredField).getText().trim());
            row.setHazardsObserved(textAreaFrom(hazardsObservedField).getText().trim());
        }
    }

    /**
     * Table model for editable SAR task rows.
     */
    private static class SarTaskTableModel extends AbstractTableModel {
        private final String[] columns = {
                "Assignment/Team # (required)", "Incident", "Resource", "Leader Role", "Leader", "Contact",
                "Operations Personnel", "Context", "Resources Assigned", "Work Assignment",
                "Transportation", "Task Map", "Special Equipment", "Communications",
                "Debrief Supervisor", "Time On Start", "Time On End", "Vehicle Miles",
                "Debriefing", "Areas Not Covered", "Hazards Observed"
        };
        private List<SarTaskAssignment> rows = new ArrayList<>();

        /** @param rows replacement rows. */
        void setRows(List<SarTaskAssignment> rows) {
            this.rows = rows == null ? new ArrayList<>() : rows;
            fireTableDataChanged();
        }

        /** @return editable rows. */
        List<SarTaskAssignment> getRows() {
            return rows;
        }

        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return columns.length; }
        @Override public String getColumnName(int column) { return columns[column]; }
        @Override public boolean isCellEditable(int rowIndex, int columnIndex) { return !READ_ONLY_COLUMNS.contains(columnIndex); }

        @Override public Object getValueAt(int rowIndex, int columnIndex) {
            SarTaskAssignment row = rows.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> row.getAssignmentTeamNumber();
                case 1 -> row.getIncidentName();
                case 2 -> row.getResourceIdentifier();
                case 3 -> row.getLeaderRole();
                case 4 -> row.getLeader();
                case 5 -> row.getContact();
                case 6 -> joinOperations(row);
                case 7 -> joinContext(row);
                case 8 -> formatResources(row.getResourcesAssigned());
                case 9 -> row.getAssignment();
                case 10 -> row.getTransportationInstructions();
                case 11 -> row.getTaskMap();
                case 12 -> row.getSpecialEquipment();
                case 13 -> formatCommunications(row.getCommunications());
                case 14 -> row.getDebriefingSupervisor();
                case 15 -> formatDateTime(row.getAssignmentStart());
                case 16 -> formatDateTime(row.getAssignmentEnd());
                case 17 -> row.getVehicleMiles();
                case 18 -> row.getDebriefNotes();
                case 19 -> row.getAreasNotCovered();
                default -> row.getHazardsObserved();
            };
        }

        @Override public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            SarTaskAssignment row = rows.get(rowIndex);
            String value = aValue == null ? "" : aValue.toString();
            switch (columnIndex) {
                case 0 -> row.setAssignmentTeamNumber(value);
                case 8 -> row.setResourcesAssigned(parseResources(value));
                case 9 -> row.setAssignment(value);
                case 10 -> row.setTransportationInstructions(value);
                case 11 -> row.setTaskMap(value);
                case 12 -> row.setSpecialEquipment(value);
                case 13 -> row.setCommunications(parseCommunications(value));
                case 14 -> row.setDebriefingSupervisor(value);
                case 15 -> row.setAssignmentStart(parseDateTime(value));
                case 16 -> row.setAssignmentEnd(parseDateTime(value));
                case 17 -> row.setVehicleMiles(value);
                case 18 -> row.setDebriefNotes(value);
                case 19 -> row.setAreasNotCovered(value);
                case 20 -> row.setHazardsObserved(value);
                default -> { return; }
            }
            fireTableCellUpdated(rowIndex, columnIndex);
        }

        private static String joinOperations(SarTaskAssignment row) {
            List<String> values = new ArrayList<>();
            if (!row.getOperationsSectionChiefName().isBlank()) {
                values.add("Ops: " + row.getOperationsSectionChiefName()
                        + (row.getOperationsSectionChiefContact().isBlank() ? "" : " (" + row.getOperationsSectionChiefContact() + ")"));
            }
            if (!row.getSecondaryManagementRoleLabel().isBlank() && !row.getSecondaryManagementName().isBlank()) {
                values.add(row.getSecondaryManagementRoleLabel() + ": " + row.getSecondaryManagementName()
                        + (row.getSecondaryManagementContact().isBlank() ? "" : " (" + row.getSecondaryManagementContact() + ")"));
            }
            return String.join(" | ", values);
        }

        private static String joinContext(SarTaskAssignment row) {
            List<String> values = new ArrayList<>();
            addIfPresent(values, "Branch", row.getBranch());
            addIfPresent(values, "Division", row.getDivision());
            addIfPresent(values, "Group", row.getGroup());
            addIfPresent(values, "Staging", row.getStagingArea());
            return String.join(" | ", values);
        }

        private static void addIfPresent(List<String> values, String label, String value) {
            if (value != null && !value.isBlank()) {
                values.add(label + ": " + value);
            }
        }

        private static String formatResources(List<SarTaskResource> resources) {
            List<String> lines = new ArrayList<>();
            for (SarTaskResource resource : resources) {
                if (resource.getFunction().isBlank() && resource.getName().isBlank()) {
                    continue;
                }
                lines.add(resource.getFunction() + ": " + resource.getName());
            }
            return String.join(" ; ", lines);
        }

        private static List<SarTaskResource> parseResources(String value) {
            List<SarTaskResource> resources = new ArrayList<>();
            for (String entry : splitEntries(value)) {
                SarTaskResource resource = new SarTaskResource();
                if (entry.contains(":")) {
                    String[] parts = entry.split(":", 2);
                    resource.setFunction(parts[0].trim());
                    resource.setName(parts[1].trim());
                } else {
                    resource.setName(entry.trim());
                }
                resources.add(resource);
            }
            return resources;
        }

        private static String formatCommunications(List<CommunicationEntry> communications) {
            List<String> lines = new ArrayList<>();
            for (CommunicationEntry communication : communications) {
                if (communication.getName().isBlank() && communication.getFunction().isBlank() && communication.getPrimaryContact().isBlank()) {
                    continue;
                }
                String left = communication.getNameOrFunction();
                lines.add(left + (communication.getPrimaryContact().isBlank() ? "" : ": " + communication.getPrimaryContact()));
            }
            return String.join(" ; ", lines);
        }

        private static List<CommunicationEntry> parseCommunications(String value) {
            List<CommunicationEntry> communications = new ArrayList<>();
            for (String entry : splitEntries(value)) {
                CommunicationEntry communication = new CommunicationEntry();
                if (entry.contains(":")) {
                    String[] parts = entry.split(":", 2);
                    communication.setNameOrFunction(parts[0].trim());
                    communication.setPrimaryContact(parts[1].trim());
                } else {
                    communication.setNameOrFunction(entry.trim());
                }
                communications.add(communication);
            }
            return communications;
        }

        private static List<String> splitEntries(String value) {
            List<String> entries = new ArrayList<>();
            for (String raw : value.split("\\s*;\\s*")) {
                String trimmed = raw.trim();
                if (!trimmed.isBlank()) {
                    entries.add(trimmed);
                }
            }
            return entries;
        }

        private static String formatDateTime(LocalDateTime value) {
            return value == null ? "" : DATE_TIME_FORMATTER.format(value);
        }

        private static LocalDateTime parseDateTime(String value) {
            if (value == null || value.isBlank()) {
                return null;
            }
            try {
                return LocalDateTime.parse(value.trim(), DATE_TIME_FORMATTER);
            } catch (DateTimeParseException exception) {
                return null;
            }
        }
    }

    private static class RequiredFieldCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
                                                       int row, int column) {
            Component component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (isSelected) {
                component.setBackground(table.getSelectionBackground());
            } else {
                component.setBackground(UiSupport.REQUIRED_FIELD_BACKGROUND);
            }
            return component;
        }
    }
}
