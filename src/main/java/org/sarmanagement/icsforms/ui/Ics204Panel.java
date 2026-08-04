package org.sarmanagement.icsforms.ui;

import org.sarmanagement.icsforms.model.CommunicationEntry;
import org.sarmanagement.icsforms.model.Ics204Form;
import org.sarmanagement.icsforms.model.ResourceAssignment;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * First-cut editor for ICS 204 assignment list data, resources, and communications.
 */
public class Ics204Panel extends JPanel {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final AppController controller;
    private final JTextField branchField = UiSupport.textField();
    private final JTextField divisionField = UiSupport.textField();
    private final JTextField groupField = UiSupport.textField();
    private final JTextField stagingAreaField = UiSupport.textField();
    private final JTextField operationsChiefNameField = UiSupport.textField();
    private final JTextField operationsChiefContactField = UiSupport.textField();
    private final JTextField branchDirectorNameField = UiSupport.textField();
    private final JTextField branchDirectorContactField = UiSupport.textField();
    private final JTextField supervisorNameField = UiSupport.textField();
    private final JTextField supervisorContactField = UiSupport.textField();
    private final JTextArea sharedAssignmentArea = UiSupport.textArea(3);
    private final JTextArea specialInstructionsArea = UiSupport.textArea(3);
    private final JTextField preparedByNameField = UiSupport.textField();
    private final JTextField preparedByPositionField = UiSupport.textField();
    private final JTextField preparedBySignatureField = UiSupport.textField();
    private final JTextField preparedDateTimeField = UiSupport.textField();
    private final JTextField formNumberField = UiSupport.textField();
    private final JTextField iapPageField = UiSupport.textField();
    private final ResourceTableModel resourceTableModel = new ResourceTableModel();
    private final CommunicationsTableModel communicationsTableModel = new CommunicationsTableModel();

    /**
     * Creates the ICS 204 editor panel.
     *
     * @param controller application controller.
     */
    public Ics204Panel(AppController controller) {
        super(new BorderLayout(8, 8));
        this.controller = controller;

        JPanel form = UiSupport.formPanel();
        form.setBorder(BorderFactory.createTitledBorder("ICS 204 Assignment Context"));
        UiSupport.addRow(form, 0, "Branch", branchField);
        UiSupport.addRow(form, 1, "Division", divisionField);
        UiSupport.addRow(form, 2, "Group", groupField);
        UiSupport.addRow(form, 3, "Staging area", stagingAreaField);
        UiSupport.addRow(form, 4, "Operations section chief", operationsChiefNameField);
        UiSupport.addRow(form, 5, "Operations chief contact", operationsChiefContactField);
        UiSupport.addRow(form, 6, "Branch director", branchDirectorNameField);
        UiSupport.addRow(form, 7, "Branch director contact", branchDirectorContactField);
        UiSupport.addRow(form, 8, "Division/group supervisor", supervisorNameField);
        UiSupport.addRow(form, 9, "Supervisor contact", supervisorContactField);
        UiSupport.addRow(form, 10, "Shared work assignment", new JScrollPane(sharedAssignmentArea));
        UiSupport.addRow(form, 11, "Special instructions", new JScrollPane(specialInstructionsArea));
        UiSupport.addRow(form, 12, "Prepared by name", preparedByNameField);
        UiSupport.addRow(form, 13, "Prepared by position/title", preparedByPositionField);
        UiSupport.addRow(form, 14, "Prepared by signature", preparedBySignatureField);
        UiSupport.addRow(form, 15, "Prepared date/time (yyyy-MM-dd HH:mm)", preparedDateTimeField);
        UiSupport.addRow(form, 16, "Form number", formNumberField);
        UiSupport.addRow(form, 17, "IAP page", iapPageField);

        JTable resourceTable = new JTable(resourceTableModel);
        JTable communicationsTable = new JTable(communicationsTableModel);

        JPanel resourcesPanel = new JPanel(new BorderLayout());
        resourcesPanel.setBorder(BorderFactory.createTitledBorder("Resources Assigned"));
        resourcesPanel.add(new JScrollPane(resourceTable), BorderLayout.CENTER);
        resourcesPanel.add(buttonsPanel(
                () -> resourceTableModel.addRow(),
                () -> resourceTableModel.removeRow(resourceTable.getSelectedRow())
        ), BorderLayout.SOUTH);

        JPanel communicationsPanel = new JPanel(new BorderLayout());
        communicationsPanel.setBorder(BorderFactory.createTitledBorder("Communications"));
        communicationsPanel.add(new JScrollPane(communicationsTable), BorderLayout.CENTER);
        communicationsPanel.add(buttonsPanel(
                () -> communicationsTableModel.addRow(),
                () -> communicationsTableModel.removeRow(communicationsTable.getSelectedRow())
        ), BorderLayout.SOUTH);

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, resourcesPanel, communicationsPanel);
        splitPane.setResizeWeight(0.65);

        add(new JScrollPane(form), BorderLayout.NORTH);
        add(splitPane, BorderLayout.CENTER);
    }

    /** Loads values from the model. */
    public void refreshFromModel() {
        Ics204Form form = controller.getData().getForm204();
        branchField.setText(nullSafe(form.getBranch()));
        divisionField.setText(nullSafe(form.getDivision()));
        groupField.setText(nullSafe(form.getGroup()));
        stagingAreaField.setText(nullSafe(form.getStagingArea()));
        operationsChiefNameField.setText(nullSafe(form.getOperationsSectionChiefName()));
        operationsChiefContactField.setText(nullSafe(form.getOperationsSectionChiefContact()));
        branchDirectorNameField.setText(nullSafe(form.getBranchDirectorName()));
        branchDirectorContactField.setText(nullSafe(form.getBranchDirectorContact()));
        supervisorNameField.setText(nullSafe(form.getDivisionGroupSupervisorName()));
        supervisorContactField.setText(nullSafe(form.getDivisionGroupSupervisorContact()));
        sharedAssignmentArea.setText(nullSafe(form.getSharedWorkAssignment()));
        specialInstructionsArea.setText(nullSafe(form.getSpecialInstructions()));
        preparedByNameField.setText(nullSafe(form.getPreparedByName()));
        preparedByPositionField.setText(nullSafe(form.getPreparedByPositionTitle()));
        preparedBySignatureField.setText(nullSafe(form.getPreparedBySignature()));
        preparedDateTimeField.setText(format(form.getPreparedDateTime()));
        formNumberField.setText(nullSafe(form.getFormNumber()));
        iapPageField.setText(nullSafe(form.getIapPage()));
        resourceTableModel.setRows(form.getResourcesAssigned());
        communicationsTableModel.setRows(form.getCommunications());
    }

    /** Applies field values to the model. */
    public void pushToModel() {
        Ics204Form form = controller.getData().getForm204();
        form.setBranch(branchField.getText().trim());
        form.setDivision(divisionField.getText().trim());
        form.setGroup(groupField.getText().trim());
        form.setStagingArea(stagingAreaField.getText().trim());
        form.setOperationsSectionChiefName(operationsChiefNameField.getText().trim());
        form.setOperationsSectionChiefContact(operationsChiefContactField.getText().trim());
        form.setBranchDirectorName(branchDirectorNameField.getText().trim());
        form.setBranchDirectorContact(branchDirectorContactField.getText().trim());
        form.setDivisionGroupSupervisorName(supervisorNameField.getText().trim());
        form.setDivisionGroupSupervisorContact(supervisorContactField.getText().trim());
        form.setSharedWorkAssignment(sharedAssignmentArea.getText().trim());
        form.setSpecialInstructions(specialInstructionsArea.getText().trim());
        form.setPreparedByName(preparedByNameField.getText().trim());
        form.setPreparedByPositionTitle(preparedByPositionField.getText().trim());
        form.setPreparedBySignature(preparedBySignatureField.getText().trim());
        form.setPreparedDateTime(parse(preparedDateTimeField.getText()));
        form.setFormNumber(formNumberField.getText().trim());
        form.setIapPage(iapPageField.getText().trim());
        form.setResourcesAssigned(resourceTableModel.getRows());
        form.setCommunications(communicationsTableModel.getRows());
    }

    /**
     * Creates shared add/remove buttons.
     *
     * @param onAdd add handler.
     * @param onRemove remove handler.
     * @return button panel.
     */
    private JPanel buttonsPanel(Runnable onAdd, Runnable onRemove) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton add = new JButton("Add");
        JButton remove = new JButton("Remove");
        add.addActionListener(event -> onAdd.run());
        remove.addActionListener(event -> onRemove.run());
        panel.add(add);
        panel.add(remove);
        return panel;
    }

    /**
     * Parses user-entered date/time.
     *
     * @param value text value.
     * @return parsed date/time or {@code null}.
     */
    private LocalDateTime parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim(), FORMATTER);
        } catch (Exception exception) {
            return null;
        }
    }

    /**
     * Formats a date/time for display.
     *
     * @param value date/time.
     * @return display text.
     */
    private String format(LocalDateTime value) {
        return value == null ? "" : FORMATTER.format(value);
    }

    /**
     * Converts null strings to empty strings.
     *
     * @param value input text.
     * @return safe text.
     */
    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    /**
     * Table model for editable resource assignment rows.
     */
    private static class ResourceTableModel extends AbstractTableModel {
        private final String[] columns = {"Assignment ID", "Resource", "Leader", "Persons", "Contact", "Reporting", "Equipment", "Supplies", "Remarks", "Notes", "Assignment"};
        private java.util.List<ResourceAssignment> rows = new java.util.ArrayList<>();

        /** @param rows replacement rows. */
        void setRows(java.util.List<ResourceAssignment> rows) { this.rows = rows == null ? new java.util.ArrayList<>() : rows; fireTableDataChanged(); }
        /** @return editable rows. */
        java.util.List<ResourceAssignment> getRows() { return rows; }
        /** Adds a blank row. */
        void addRow() { rows.add(new ResourceAssignment()); fireTableRowsInserted(rows.size() - 1, rows.size() - 1); }
        /** @param row row index to remove. */
        void removeRow(int row) { if (row >= 0 && row < rows.size()) { rows.remove(row); fireTableRowsDeleted(row, row); } }
        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return columns.length; }
        @Override public String getColumnName(int column) { return columns[column]; }
        @Override public boolean isCellEditable(int rowIndex, int columnIndex) { return true; }
        @Override public Object getValueAt(int rowIndex, int columnIndex) {
            ResourceAssignment row = rows.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> row.getAssignmentId();
                case 1 -> row.getResourceIdentifier();
                case 2 -> row.getLeader();
                case 3 -> row.getNumberOfPersons();
                case 4 -> row.getContact();
                case 5 -> row.getReportingLocation();
                case 6 -> row.getSpecialEquipment();
                case 7 -> row.getSupplies();
                case 8 -> row.getRemarks();
                case 9 -> row.getNotes();
                default -> row.getAssignment();
            };
        }
        @Override public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            ResourceAssignment row = rows.get(rowIndex);
            String value = aValue == null ? "" : aValue.toString();
            switch (columnIndex) {
                case 0 -> row.setAssignmentId(value);
                case 1 -> row.setResourceIdentifier(value);
                case 2 -> row.setLeader(value);
                case 3 -> row.setNumberOfPersons(parseInt(value));
                case 4 -> row.setContact(value);
                case 5 -> row.setReportingLocation(value);
                case 6 -> row.setSpecialEquipment(value);
                case 7 -> row.setSupplies(value);
                case 8 -> row.setRemarks(value);
                case 9 -> row.setNotes(value);
                default -> row.setAssignment(value);
            }
            fireTableCellUpdated(rowIndex, columnIndex);
        }
        /** @param value string value. @return parsed integer or zero. */
        private int parseInt(String value) { try { return Integer.parseInt(value.trim()); } catch (Exception ex) { return 0; } }
    }

    /**
     * Table model for communications rows.
     */
    private static class CommunicationsTableModel extends AbstractTableModel {
        private final String[] columns = {"Name / Function", "Primary Contact"};
        private java.util.List<CommunicationEntry> rows = new java.util.ArrayList<>();

        /** @param rows replacement rows. */
        void setRows(java.util.List<CommunicationEntry> rows) { this.rows = rows == null ? new java.util.ArrayList<>() : rows; fireTableDataChanged(); }
        /** @return editable rows. */
        java.util.List<CommunicationEntry> getRows() { return rows; }
        /** Adds a blank row. */
        void addRow() { rows.add(new CommunicationEntry()); fireTableRowsInserted(rows.size() - 1, rows.size() - 1); }
        /** @param row row index to remove. */
        void removeRow(int row) { if (row >= 0 && row < rows.size()) { rows.remove(row); fireTableRowsDeleted(row, row); } }
        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return columns.length; }
        @Override public String getColumnName(int column) { return columns[column]; }
        @Override public boolean isCellEditable(int rowIndex, int columnIndex) { return true; }
        @Override public Object getValueAt(int rowIndex, int columnIndex) { return columnIndex == 0 ? rows.get(rowIndex).getNameOrFunction() : rows.get(rowIndex).getPrimaryContact(); }
        @Override public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            String value = aValue == null ? "" : aValue.toString();
            if (columnIndex == 0) { rows.get(rowIndex).setNameOrFunction(value); } else { rows.get(rowIndex).setPrimaryContact(value); }
            fireTableCellUpdated(rowIndex, columnIndex);
        }
    }
}
