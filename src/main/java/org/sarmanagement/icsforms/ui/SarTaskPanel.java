package org.sarmanagement.icsforms.ui;

import org.sarmanagement.icsforms.model.SarTaskAssignment;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only first-cut SAR task scaffold view linked from ICS 204 resource assignments.
 */
public class SarTaskPanel extends JPanel {
    private final AppController controller;
    private final SarTaskTableModel tableModel = new SarTaskTableModel();

    /**
     * Creates the SAR task scaffold panel.
     *
     * @param controller application controller.
     */
    public SarTaskPanel(AppController controller) {
        super(new BorderLayout());
        this.controller = controller;
        JTable table = new JTable(tableModel);
        setBorder(BorderFactory.createTitledBorder("SAR Task Scaffold (linked from ICS 204)"));
        add(new JScrollPane(table), BorderLayout.CENTER);
    }

    /**
     * Reloads table rows from the latest synced SAR task assignments.
     */
    public void refreshTable() {
        controller.syncSarTasks();
        tableModel.setRows(controller.getData().getSarTaskAssignments());
    }

    /**
     * Table model for SAR task scaffold rows.
     */
    private static class SarTaskTableModel extends AbstractTableModel {
        private final String[] columns = {"Assignment ID", "Incident", "Resource", "Leader", "Assignment", "Contact", "Context", "Special Instructions", "Debrief Notes"};
        private List<SarTaskAssignment> rows = new ArrayList<>();

        /** @param rows replacement rows. */
        void setRows(List<SarTaskAssignment> rows) { this.rows = rows == null ? new ArrayList<>() : rows; fireTableDataChanged(); }
        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return columns.length; }
        @Override public String getColumnName(int column) { return columns[column]; }
        @Override public Object getValueAt(int rowIndex, int columnIndex) {
            SarTaskAssignment row = rows.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> row.getAssignmentId();
                case 1 -> row.getIncidentName();
                case 2 -> row.getResourceIdentifier();
                case 3 -> row.getLeader();
                case 4 -> row.getAssignment();
                case 5 -> row.getContact();
                case 6 -> String.join(" / ", row.getBranch(), row.getDivision(), row.getGroup(), row.getStagingArea());
                case 7 -> row.getSpecialInstructions();
                default -> row.getDebriefNotes();
            };
        }
    }
}
