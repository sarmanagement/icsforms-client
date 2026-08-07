package org.sarmanagement.icsforms.ui;

import org.sarmanagement.icsforms.model.CommunicationEntry;
import org.sarmanagement.icsforms.model.Ics204Form;
import org.sarmanagement.icsforms.model.ResourceAssignment;
import org.sarmanagement.icsforms.model.SarTaskSupport;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.DefaultCellEditor;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;

/**
 * First-cut editor for ICS 204 assignment list data, resources, and communications.
 */
public class Ics204Panel extends JPanel {
    private final AppController controller;
    private final JComboBox<String> managementContextSelector = new JComboBox<>(new String[]{"Staging Area", "Branch", "Division", "Group"});
    private final JLabel selectedContextLabel = new JLabel("Staging area name");
    private final JTextField selectedContextValueField = UiSupport.textField();
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
    private final JSpinner preparedDateTimeField = UiSupport.dateTimeSpinner();
    private final JTextField iapPageField = UiSupport.textField();
    private final JPanel managementContactsPanel = new JPanel(new GridBagLayout());
    private final ResourceTableModel resourceTableModel = new ResourceTableModel();
    private final CommunicationsTableModel communicationsTableModel = new CommunicationsTableModel();
    private String activeManagementContext = Ics204Form.MANAGEMENT_STAGING_AREA;
    private boolean updatingContextSelection;

    /**
     * Creates the ICS 204 editor panel.
     *
     * @param controller application controller.
     */
    public Ics204Panel(AppController controller) {
        super(new BorderLayout(8, 8));
        this.controller = controller;

        JPanel form = UiSupport.formPanel();
        selectedContextValueField.setColumns(16);
        operationsChiefNameField.setColumns(16);
        operationsChiefContactField.setColumns(12);
        branchDirectorNameField.setColumns(16);
        branchDirectorContactField.setColumns(12);
        supervisorNameField.setColumns(16);
        supervisorContactField.setColumns(12);
        preparedByNameField.setColumns(18);
        preparedByPositionField.setColumns(18);
        iapPageField.setColumns(8);
        JPanel selectedContextPanel = new JPanel(new BorderLayout(0, 2));
        selectedContextPanel.setOpaque(false);
        selectedContextPanel.add(selectedContextLabel, BorderLayout.NORTH);
        selectedContextPanel.add(selectedContextValueField, BorderLayout.CENTER);
        form.setBorder(BorderFactory.createTitledBorder("ICS 204 Assignment Context"));
        UiSupport.addRow(form, 0, "Management level", managementContextSelector);
        UiSupport.addRow(form, 1, "Selected context", selectedContextPanel);
        UiSupport.addRow(form, 2, "Management contacts", managementContactsPanel);
        UiSupport.addRow(form, 3, "Shared work assignment", new JScrollPane(sharedAssignmentArea));
        UiSupport.addRow(form, 4, "Special instructions", new JScrollPane(specialInstructionsArea));
        UiSupport.addRow(form, 5, "Prepared by name", preparedByNameField);
        UiSupport.addRow(form, 6, "Prepared by position/title", preparedByPositionField);
        UiSupport.addRow(form, 7, "Prepared date/time", preparedDateTimeField);
        UiSupport.addRow(form, 8, "IAP page", iapPageField);
        preparedByNameField.setEditable(false);
        preparedByPositionField.setEditable(false);
        managementContextSelector.addActionListener(event -> {
            if (updatingContextSelection) {
                return;
            }
            storeSelectedContextValue(activeManagementContext);
            activeManagementContext = selectedManagementContext();
            updateSelectedContextLabel();
            loadSelectedContextValue(activeManagementContext);
            rebuildManagementContacts();
        });
        updateSelectedContextLabel();
        rebuildManagementContacts();

        JTable resourceTable = new JTable(resourceTableModel);
        JTable communicationsTable = new JTable(communicationsTableModel);
        resourceTable.setFillsViewportHeight(true);
        communicationsTable.setFillsViewportHeight(true);
        resourceTable.getColumnModel().getColumn(2).setCellEditor(new DefaultCellEditor(
                new JComboBox<>(SarTaskSupport.resourceTypes().toArray(String[]::new))));
        resourceTable.getColumnModel().getColumn(3).setCellEditor(new DefaultCellEditor(
                new JComboBox<>(SarTaskSupport.taskTypes().toArray(String[]::new))));

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

        JPanel tablesPanel = new JPanel(new GridLayout(2, 1, 8, 8));
        tablesPanel.add(resourcesPanel);
        tablesPanel.add(communicationsPanel);

        JScrollPane formScrollPane = new JScrollPane(form);
        formScrollPane.setBorder(BorderFactory.createEmptyBorder());
        formScrollPane.setPreferredSize(new Dimension(0, 320));
        add(formScrollPane, BorderLayout.NORTH);
        add(tablesPanel, BorderLayout.CENTER);
    }

    /** Loads values from the model. */
    public void refreshFromModel() {
        Ics204Form form = controller.getData().getForm204();
        branchField.setText(nullSafe(form.getBranch()));
        divisionField.setText(nullSafe(form.getDivision()));
        groupField.setText(nullSafe(form.getGroup()));
        stagingAreaField.setText(nullSafe(form.getStagingArea()));
        activeManagementContext = form.getManagementContext();
        setSelectedManagementContext(activeManagementContext);
        updateSelectedContextLabel();
        loadSelectedContextValue(activeManagementContext);
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
        preparedDateTimeField.setValue(AppController.toDate(form.getPreparedDateTime()));
        iapPageField.setText(nullSafe(form.getIapPage()));
        rebuildManagementContacts();
        resourceTableModel.setRows(form.getResourcesAssigned());
        communicationsTableModel.setRows(form.getCommunications());
    }

    /** Applies field values to the model. */
    public void pushToModel() {
        Ics204Form form = controller.getData().getForm204();
        storeSelectedContextValue(activeManagementContext);
        form.setManagementContext(activeManagementContext);
        form.setBranch("");
        form.setDivision("");
        form.setGroup("");
        form.setStagingArea("");
        String selectedContextValue = selectedContextValueField.getText().trim();
        if (Ics204Form.MANAGEMENT_BRANCH.equals(activeManagementContext)) {
            form.setBranch(selectedContextValue);
        } else if (Ics204Form.MANAGEMENT_DIVISION.equals(activeManagementContext)) {
            form.setDivision(selectedContextValue);
        } else if (Ics204Form.MANAGEMENT_GROUP.equals(activeManagementContext)) {
            form.setGroup(selectedContextValue);
        } else {
            form.setStagingArea(selectedContextValue);
        }
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
        form.setPreparedDateTime(AppController.toLocalDateTime((java.util.Date) preparedDateTimeField.getValue()));
        form.setIapPage(iapPageField.getText().trim());
        form.setResourcesAssigned(resourceTableModel.getRows());
        form.setCommunications(communicationsTableModel.getRows());
        rebuildManagementContacts();
    }

    private void rebuildManagementContacts() {
        managementContactsPanel.removeAll();
        int rowIndex = 0;
        for (Object[] row : visibleManagementRows()) {
            GridBagConstraints left = new GridBagConstraints();
            left.gridx = 0;
            left.gridy = rowIndex;
            left.anchor = GridBagConstraints.WEST;
            left.insets = new Insets(2, 0, 2, 8);
            managementContactsPanel.add(new JLabel((String) row[0]), left);

            GridBagConstraints right = new GridBagConstraints();
            right.gridx = 1;
            right.gridy = rowIndex;
            right.weightx = 1.0;
            right.fill = GridBagConstraints.HORIZONTAL;
            right.insets = new Insets(2, 0, 2, 0);
            managementContactsPanel.add((JTextField) row[1], right);
            rowIndex++;
        }
        managementContactsPanel.revalidate();
        managementContactsPanel.repaint();
    }

    private List<Object[]> visibleManagementRows() {
        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{"Operations section chief", operationsChiefNameField});
        rows.add(new Object[]{"Operations contact", operationsChiefContactField});
        if (Ics204Form.MANAGEMENT_BRANCH.equals(activeManagementContext)) {
            rows.add(new Object[]{"Branch director", branchDirectorNameField});
            rows.add(new Object[]{"Branch contact", branchDirectorContactField});
        }
        if (Ics204Form.MANAGEMENT_DIVISION.equals(activeManagementContext)
                || Ics204Form.MANAGEMENT_GROUP.equals(activeManagementContext)) {
            rows.add(new Object[]{Ics204Form.MANAGEMENT_GROUP.equals(activeManagementContext) ? "Group supervisor" : "Division supervisor",
                    supervisorNameField});
            rows.add(new Object[]{Ics204Form.MANAGEMENT_GROUP.equals(activeManagementContext) ? "Group contact" : "Division contact",
                    supervisorContactField});
        }
        return rows;
    }

    private void updateSelectedContextLabel() {
        selectedContextLabel.setText(switch (activeManagementContext) {
            case Ics204Form.MANAGEMENT_BRANCH -> "Branch name";
            case Ics204Form.MANAGEMENT_DIVISION -> "Division name";
            case Ics204Form.MANAGEMENT_GROUP -> "Group name";
            default -> "Staging area name";
        });
    }

    private void storeSelectedContextValue(String managementContext) {
        String value = selectedContextValueField.getText().trim();
        switch (managementContext) {
            case Ics204Form.MANAGEMENT_BRANCH -> branchField.setText(value);
            case Ics204Form.MANAGEMENT_DIVISION -> divisionField.setText(value);
            case Ics204Form.MANAGEMENT_GROUP -> groupField.setText(value);
            default -> stagingAreaField.setText(value);
        }
    }

    private void loadSelectedContextValue(String managementContext) {
        selectedContextValueField.setText(switch (managementContext) {
            case Ics204Form.MANAGEMENT_BRANCH -> nullSafe(branchField.getText());
            case Ics204Form.MANAGEMENT_DIVISION -> nullSafe(divisionField.getText());
            case Ics204Form.MANAGEMENT_GROUP -> nullSafe(groupField.getText());
            default -> nullSafe(stagingAreaField.getText());
        });
    }

    private String selectedManagementContext() {
        Object selected = managementContextSelector.getSelectedItem();
        if ("Branch".equals(selected)) {
            return Ics204Form.MANAGEMENT_BRANCH;
        }
        if ("Division".equals(selected)) {
            return Ics204Form.MANAGEMENT_DIVISION;
        }
        if ("Group".equals(selected)) {
            return Ics204Form.MANAGEMENT_GROUP;
        }
        return Ics204Form.MANAGEMENT_STAGING_AREA;
    }

    private void setSelectedManagementContext(String managementContext) {
        updatingContextSelection = true;
        managementContextSelector.setSelectedItem(switch (managementContext) {
            case Ics204Form.MANAGEMENT_BRANCH -> "Branch";
            case Ics204Form.MANAGEMENT_DIVISION -> "Division";
            case Ics204Form.MANAGEMENT_GROUP -> "Group";
            default -> "Staging Area";
        });
        updatingContextSelection = false;
        activeManagementContext = managementContext;
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
        private final String[] columns = {"Assignment ID", "Assignment/Team #", "Resource Type", "Task Geometry", "Resource", "Leader Role", "Leader", "Persons", "Contact", "Reporting", "Equipment", "Supplies", "Remarks", "Notes", "Assignment"};
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
                case 1 -> row.getAssignmentTeamNumber();
                case 2 -> row.getResourceType();
                case 3 -> row.getTaskType();
                case 4 -> row.getResourceIdentifier();
                case 5 -> row.getLeaderRole();
                case 6 -> row.getLeader();
                case 7 -> row.getNumberOfPersons();
                case 8 -> row.getContact();
                case 9 -> row.getReportingLocation();
                case 10 -> row.getSpecialEquipment();
                case 11 -> row.getSupplies();
                case 12 -> row.getRemarks();
                case 13 -> row.getNotes();
                default -> row.getAssignment();
            };
        }
        @Override public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            ResourceAssignment row = rows.get(rowIndex);
            String value = aValue == null ? "" : aValue.toString();
            switch (columnIndex) {
                case 0 -> row.setAssignmentId(value);
                case 1 -> row.setAssignmentTeamNumber(value);
                case 2 -> row.setResourceType(value);
                case 3 -> row.setTaskType(value);
                case 4 -> row.setResourceIdentifier(value);
                case 5 -> row.setLeaderRole(value);
                case 6 -> row.setLeader(value);
                case 7 -> row.setNumberOfPersons(parseInt(value));
                case 8 -> row.setContact(value);
                case 9 -> row.setReportingLocation(value);
                case 10 -> row.setSpecialEquipment(value);
                case 11 -> row.setSupplies(value);
                case 12 -> row.setRemarks(value);
                case 13 -> row.setNotes(value);
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
        private final String[] columns = {"Name", "Function", "Primary Contact (Phone/Radio)"};
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
        @Override public Object getValueAt(int rowIndex, int columnIndex) {
            return switch (columnIndex) {
                case 0 -> rows.get(rowIndex).getName();
                case 1 -> rows.get(rowIndex).getFunction();
                default -> rows.get(rowIndex).getPrimaryContact();
            };
        }
        @Override public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            String value = aValue == null ? "" : aValue.toString();
            if (columnIndex == 0) {
                rows.get(rowIndex).setName(value);
            } else if (columnIndex == 1) {
                rows.get(rowIndex).setFunction(value);
            } else {
                rows.get(rowIndex).setPrimaryContact(value);
            }
            fireTableCellUpdated(rowIndex, columnIndex);
        }
    }
}
