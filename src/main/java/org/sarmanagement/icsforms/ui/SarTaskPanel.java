package org.sarmanagement.icsforms.ui;

import org.sarmanagement.icsforms.model.CommunicationEntry;
import org.sarmanagement.icsforms.model.SarTaskAssignment;
import org.sarmanagement.icsforms.model.SarTaskResource;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
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

    /**
     * Creates the SAR task assignment panel.
     *
     * @param controller application controller.
     */
    public SarTaskPanel(AppController controller) {
        super(new BorderLayout());
        this.controller = controller;
        JTable table = new JTable(tableModel);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
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

    /**
     * Table model for editable SAR task rows.
     */
    private static class SarTaskTableModel extends AbstractTableModel {
        private final String[] columns = {
                "Assignment/Team #", "Incident", "Resource", "Leader Role", "Leader", "Contact",
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

        private String joinOperations(SarTaskAssignment row) {
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

        private String joinContext(SarTaskAssignment row) {
            List<String> values = new ArrayList<>();
            addIfPresent(values, "Branch", row.getBranch());
            addIfPresent(values, "Division", row.getDivision());
            addIfPresent(values, "Group", row.getGroup());
            addIfPresent(values, "Staging", row.getStagingArea());
            return String.join(" | ", values);
        }

        private void addIfPresent(List<String> values, String label, String value) {
            if (value != null && !value.isBlank()) {
                values.add(label + ": " + value);
            }
        }

        private String formatResources(List<SarTaskResource> resources) {
            List<String> lines = new ArrayList<>();
            for (SarTaskResource resource : resources) {
                if (resource.getFunction().isBlank() && resource.getName().isBlank()) {
                    continue;
                }
                lines.add(resource.getFunction() + ": " + resource.getName());
            }
            return String.join(" ; ", lines);
        }

        private List<SarTaskResource> parseResources(String value) {
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

        private String formatCommunications(List<CommunicationEntry> communications) {
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

        private List<CommunicationEntry> parseCommunications(String value) {
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

        private List<String> splitEntries(String value) {
            List<String> entries = new ArrayList<>();
            for (String raw : value.split("\\s*;\\s*")) {
                String trimmed = raw.trim();
                if (!trimmed.isBlank()) {
                    entries.add(trimmed);
                }
            }
            return entries;
        }

        private String formatDateTime(LocalDateTime value) {
            return value == null ? "" : DATE_TIME_FORMATTER.format(value);
        }

        private LocalDateTime parseDateTime(String value) {
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
}
