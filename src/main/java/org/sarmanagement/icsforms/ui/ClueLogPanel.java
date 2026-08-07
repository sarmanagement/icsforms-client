package org.sarmanagement.icsforms.ui;

import org.sarmanagement.icsforms.model.ClueLogEntry;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared clue log editor showing clues captured across SAR task debriefings.
 */
public class ClueLogPanel extends JPanel {
    private final AppController controller;
    private final ClueLogTableModel tableModel = new ClueLogTableModel();

    public ClueLogPanel(AppController controller) {
        super(new BorderLayout());
        this.controller = controller;

        JTable table = new JTable(tableModel);
        table.setFillsViewportHeight(true);
        setBorder(BorderFactory.createTitledBorder("Clue Log"));
        add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton add = new JButton("Add");
        JButton remove = new JButton("Remove");
        add.addActionListener(event -> tableModel.addRow());
        remove.addActionListener(event -> tableModel.removeRow(table.getSelectedRow()));
        buttons.add(add);
        buttons.add(remove);
        add(buttons, BorderLayout.SOUTH);
    }

    public void refreshFromModel() {
        tableModel.setRows(controller.getData().getClueLogEntries());
    }

    public void pushToModel() {
        controller.getData().setClueLogEntries(tableModel.getRows());
    }

    private static class ClueLogTableModel extends AbstractTableModel {
        private final String[] columns = {"Detecting Task", "Date/Time Collected", "Location", "Description", "Follow Up"};
        private List<ClueLogEntry> rows = new ArrayList<>();

        void setRows(List<ClueLogEntry> rows) {
            this.rows = rows == null ? new ArrayList<>() : rows;
            fireTableDataChanged();
        }

        List<ClueLogEntry> getRows() {
            return rows;
        }

        void addRow() {
            rows.add(new ClueLogEntry());
            fireTableRowsInserted(rows.size() - 1, rows.size() - 1);
        }

        void removeRow(int row) {
            if (row >= 0 && row < rows.size()) {
                rows.remove(row);
                fireTableRowsDeleted(row, row);
            }
        }

        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return columns.length; }
        @Override public String getColumnName(int column) { return columns[column]; }
        @Override public boolean isCellEditable(int rowIndex, int columnIndex) { return true; }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            ClueLogEntry row = rows.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> row.getDetectingTask();
                case 1 -> SarTaskPanel.formatDateTimeValue(row.getDateTimeCollected());
                case 2 -> row.getLocation();
                case 3 -> row.getDescription();
                default -> row.getFollowUp();
            };
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            ClueLogEntry row = rows.get(rowIndex);
            String value = aValue == null ? "" : aValue.toString();
            switch (columnIndex) {
                case 0 -> row.setDetectingTask(value);
                case 1 -> row.setDateTimeCollected(SarTaskPanel.parseDateTimeValue(value));
                case 2 -> row.setLocation(value);
                case 3 -> row.setDescription(value);
                default -> row.setFollowUp(value);
            }
            fireTableCellUpdated(rowIndex, columnIndex);
        }
    }
}
