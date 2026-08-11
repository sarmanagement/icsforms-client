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
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * First-cut editor for ICS 204 assignment list data, resources, and communications.
 */
public class Ics204Panel extends JPanel {
    private final AppController controller;
    private java.util.function.Consumer<ResourceAssignment> on214Request;
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
    private final JTable resourceTable = new JTable(resourceTableModel);
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

        JTable communicationsTable = new JTable(communicationsTableModel);
        resourceTable.setFillsViewportHeight(true);
        communicationsTable.setFillsViewportHeight(true);
        resourceTable.getColumnModel().getColumn(1).setCellEditor(new DefaultCellEditor(
                new JComboBox<>(SarTaskSupport.resourceTypes().toArray(String[]::new))));
        resourceTable.getColumnModel().getColumn(2).setCellEditor(new DefaultCellEditor(
                new JComboBox<>(SarTaskSupport.taskTypes().toArray(String[]::new))));
        installResourceRowEditor();

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
        formScrollPane.setPreferredSize(new Dimension(0, 260));
        add(formScrollPane, BorderLayout.NORTH);
        add(tablesPanel, BorderLayout.CENTER);
    }

    /**
     * Sets the callback invoked when the operator requests to add or open an ICS 214 log for a resource.
     *
     * @param handler callback receiving the selected {@link ResourceAssignment}.
     */
    public void setOn214Request(java.util.function.Consumer<ResourceAssignment> handler) {
        this.on214Request = handler;
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
        for (ManagementContactRow row : visibleManagementRows()) {
            GridBagConstraints label = new GridBagConstraints();
            label.gridx = 0;
            label.gridy = rowIndex;
            label.anchor = GridBagConstraints.NORTHWEST;
            label.insets = new Insets(2, 0, 2, 8);
            managementContactsPanel.add(new JLabel(row.label()), label);

            GridBagConstraints name = new GridBagConstraints();
            name.gridx = 1;
            name.gridy = rowIndex;
            name.weightx = 1.0;
            name.fill = GridBagConstraints.HORIZONTAL;
            name.insets = new Insets(2, 0, 2, 6);
            managementContactsPanel.add(row.nameField(), name);

            GridBagConstraints contactLabel = new GridBagConstraints();
            contactLabel.gridx = 2;
            contactLabel.gridy = rowIndex;
            contactLabel.anchor = GridBagConstraints.NORTHWEST;
            contactLabel.insets = new Insets(2, 0, 2, 6);
            managementContactsPanel.add(new JLabel("Contact"), contactLabel);

            GridBagConstraints contact = new GridBagConstraints();
            contact.gridx = 3;
            contact.gridy = rowIndex;
            contact.weightx = 0.7;
            contact.fill = GridBagConstraints.HORIZONTAL;
            contact.insets = new Insets(2, 0, 2, 0);
            managementContactsPanel.add(row.contactField(), contact);
            rowIndex++;
        }
        managementContactsPanel.revalidate();
        managementContactsPanel.repaint();
    }

    private List<ManagementContactRow> visibleManagementRows() {
        List<ManagementContactRow> rows = new ArrayList<>();
        rows.add(new ManagementContactRow("Operations section chief", operationsChiefNameField, operationsChiefContactField));
        if (Ics204Form.MANAGEMENT_BRANCH.equals(activeManagementContext)) {
            rows.add(new ManagementContactRow("Branch director", branchDirectorNameField, branchDirectorContactField));
        }
        if (Ics204Form.MANAGEMENT_DIVISION.equals(activeManagementContext)
                || Ics204Form.MANAGEMENT_GROUP.equals(activeManagementContext)) {
            rows.add(new ManagementContactRow(
                    Ics204Form.MANAGEMENT_GROUP.equals(activeManagementContext) ? "Group supervisor" : "Division supervisor",
                    supervisorNameField,
                    supervisorContactField));
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

    private void installResourceRowEditor() {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem editItem = new JMenuItem("Edit assignment…");
        editItem.addActionListener(event -> openSelectedResourceEditor());
        JMenuItem log214Item = new JMenuItem("Add ICS 214 Log…");
        log214Item.addActionListener(event -> open214ForSelectedResource());
        menu.add(editItem);
        menu.add(log214Item);
        menu.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent event) {
                int viewRow = resourceTable.getSelectedRow();
                if (viewRow >= 0) {
                    int modelRow = resourceTable.convertRowIndexToModel(viewRow);
                    ResourceAssignment ra = resourceTableModel.getRows().get(modelRow);
                    boolean exists = controller.getData().getActivityLogs().stream()
                            .anyMatch(f -> ra.getAssignmentId().equals(f.getLinkedSarTaskAssignmentId()));
                    log214Item.setText(exists ? "Open ICS 214 Log" : "Add ICS 214 Log…");
                }
                log214Item.setVisible(on214Request != null);
            }
            @Override public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent event) {}
            @Override public void popupMenuCanceled(javax.swing.event.PopupMenuEvent event) {}
        });
        resourceTable.setComponentPopupMenu(menu);
        resourceTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                if (!event.isPopupTrigger()) {
                    selectResourceRow(event);
                }
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                if (event.isPopupTrigger()) {
                    selectResourceRow(event);
                }
            }

            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2 && event.getButton() == MouseEvent.BUTTON1) {
                    selectResourceRow(event);
                    openSelectedResourceEditor();
                }
            }
        });
    }

    private void selectResourceRow(MouseEvent event) {
        int viewRow = resourceTable.rowAtPoint(event.getPoint());
        if (viewRow >= 0) {
            resourceTable.setRowSelectionInterval(viewRow, viewRow);
        }
    }

    private void openSelectedResourceEditor() {
        int viewRow = resourceTable.getSelectedRow();
        if (viewRow < 0) {
            return;
        }
        int modelRow = resourceTable.convertRowIndexToModel(viewRow);
        ResourceAssignment row = resourceTableModel.getRows().get(modelRow);
        ResourceAssignmentEditor editor = new ResourceAssignmentEditor(row);
        JScrollPane scrollPane = new JScrollPane(editor.panel);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        if (!UiSupport.showResizableConfirmDialog(this, editor.dialogTitle(), scrollPane, new Dimension(920, 560))) {
            return;
        }
        editor.applyTo(row);
        resourceTableModel.fireTableRowsUpdated(modelRow, modelRow);
    }

    private void open214ForSelectedResource() {
        if (on214Request == null) {
            return;
        }
        int viewRow = resourceTable.getSelectedRow();
        if (viewRow < 0) {
            return;
        }
        int modelRow = resourceTable.convertRowIndexToModel(viewRow);
        ResourceAssignment row = resourceTableModel.getRows().get(modelRow);
        on214Request.accept(row);
    }

    private static JPanel inlineFieldPanel(LabeledComponent... components) {
        JPanel panel = new JPanel(new GridLayout(1, components.length, 8, 0));
        panel.setOpaque(false);
        for (LabeledComponent component : components) {
            JPanel cell = new JPanel(new BorderLayout(0, 2));
            cell.setOpaque(false);
            cell.add(new JLabel(component.label()), BorderLayout.NORTH);
            cell.add(component.component(), BorderLayout.CENTER);
            panel.add(cell);
        }
        return panel;
    }

    private static JScrollPane textArea(String value, int rows) {
        JTextArea area = UiSupport.textArea(rows);
        area.setText(value == null ? "" : value);
        return new JScrollPane(area);
    }

    private static JTextArea textAreaFrom(JScrollPane scrollPane) {
        return (JTextArea) scrollPane.getViewport().getView();
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception ex) {
            return 0;
        }
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

    private record ManagementContactRow(String label, JTextField nameField, JTextField contactField) {
    }

    private record LabeledComponent(String label, java.awt.Component component) {
    }

    private static class ResourceAssignmentEditor {
        private final JPanel panel = UiSupport.formPanel();
        private final JTextField assignmentTeamNumberField = UiSupport.textField();
        private final JComboBox<String> resourceTypeField = new JComboBox<>(SarTaskSupport.resourceTypes().toArray(String[]::new));
        private final JComboBox<String> taskTypeField = new JComboBox<>(SarTaskSupport.taskTypes().toArray(String[]::new));
        private final JTextField resourceField = UiSupport.textField();
        private final JTextField leaderRoleField = UiSupport.textField();
        private final JTextField leaderField = UiSupport.textField();
        private final JTextField personsField = UiSupport.textField();
        private final JTextField contactField = UiSupport.textField();
        private final JTextField reportingField = UiSupport.textField();
        private final JScrollPane equipmentField;
        private final JScrollPane suppliesField;
        private final JScrollPane remarksField;
        private final JScrollPane notesField;
        private final JScrollPane assignmentField;

        private ResourceAssignmentEditor(ResourceAssignment row) {
            resourceTypeField.setEditable(true);
            taskTypeField.setEditable(true);
            assignmentTeamNumberField.setText(row.getAssignmentTeamNumber());
            resourceTypeField.setSelectedItem(row.getResourceType());
            taskTypeField.setSelectedItem(row.getTaskType());
            resourceField.setText(row.getResourceIdentifier());
            leaderRoleField.setText(row.getLeaderRole());
            leaderField.setText(row.getLeader());
            personsField.setColumns(4);
            personsField.setText(row.getNumberOfPersons() <= 0 ? "" : String.valueOf(row.getNumberOfPersons()));
            contactField.setText(row.getContact());
            reportingField.setText(row.getReportingLocation());
            equipmentField = textArea(row.getSpecialEquipment(), 2);
            suppliesField = textArea(row.getSupplies(), 2);
            remarksField = textArea(row.getRemarks(), 2);
            notesField = textArea(row.getNotes(), 2);
            assignmentField = textArea(row.getAssignment(), 4);

            int rowIndex = 0;
            UiSupport.addRequiredRow(panel, rowIndex++, "Assignment/Team #", assignmentTeamNumberField);
            UiSupport.addRow(panel, rowIndex++, "Task setup", inlineFieldPanel(
                    new LabeledComponent("Resource type", resourceTypeField),
                    new LabeledComponent("Task Geometry", taskTypeField),
                    new LabeledComponent("Persons", personsField)));
            UiSupport.addRow(panel, rowIndex++, "Resource", inlineFieldPanel(
                    new LabeledComponent("Identifier", resourceField),
                    new LabeledComponent("Primary contact", contactField)));
            UiSupport.addRow(panel, rowIndex++, "Leadership", inlineFieldPanel(
                    new LabeledComponent("Leader role", leaderRoleField),
                    new LabeledComponent("Leader", leaderField)));
            UiSupport.addRow(panel, rowIndex++, "Reporting location", reportingField);
            UiSupport.addRow(panel, rowIndex++, "Special equipment", equipmentField);
            UiSupport.addRow(panel, rowIndex++, "Supplies", suppliesField);
            UiSupport.addRow(panel, rowIndex++, "Remarks", remarksField);
            UiSupport.addRow(panel, rowIndex++, "Notes", notesField);
            UiSupport.addRow(panel, rowIndex, "Assignment", assignmentField);
        }

        private String dialogTitle() {
            String teamNumber = assignmentTeamNumberField.getText().trim();
            return teamNumber.isBlank() ? "Edit assignment" : "Edit assignment " + teamNumber;
        }

        private void applyTo(ResourceAssignment row) {
            row.setAssignmentTeamNumber(assignmentTeamNumberField.getText().trim());
            row.setResourceType(selectedComboValue(resourceTypeField));
            row.setTaskType(selectedComboValue(taskTypeField));
            row.setResourceIdentifier(resourceField.getText().trim());
            row.setLeaderRole(leaderRoleField.getText().trim());
            row.setLeader(leaderField.getText().trim());
            row.setNumberOfPersons(parseInt(personsField.getText()));
            row.setContact(contactField.getText().trim());
            row.setReportingLocation(reportingField.getText().trim());
            row.setSpecialEquipment(textAreaFrom(equipmentField).getText().trim());
            row.setSupplies(textAreaFrom(suppliesField).getText().trim());
            row.setRemarks(textAreaFrom(remarksField).getText().trim());
            row.setNotes(textAreaFrom(notesField).getText().trim());
            row.setAssignment(textAreaFrom(assignmentField).getText().trim());
        }

        private static String selectedComboValue(JComboBox<String> comboBox) {
            Object selected = comboBox.getEditor().getItem();
            return selected == null ? "" : selected.toString().trim();
        }
    }

    /**
     * Table model for editable resource assignment rows.
     */
    private static class ResourceTableModel extends AbstractTableModel {
        private final String[] columns = {"Assignment/Team #", "Resource Type", "Task Geometry", "Resource", "Leader Role", "Leader", "Persons", "Contact", "Reporting", "Equipment", "Supplies", "Remarks", "Notes", "Assignment"};
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
                case 0 -> row.getAssignmentTeamNumber();
                case 1 -> row.getResourceType();
                case 2 -> row.getTaskType();
                case 3 -> row.getResourceIdentifier();
                case 4 -> row.getLeaderRole();
                case 5 -> row.getLeader();
                case 6 -> row.getNumberOfPersons();
                case 7 -> row.getContact();
                case 8 -> row.getReportingLocation();
                case 9 -> row.getSpecialEquipment();
                case 10 -> row.getSupplies();
                case 11 -> row.getRemarks();
                case 12 -> row.getNotes();
                default -> row.getAssignment();
            };
        }
        @Override public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            ResourceAssignment row = rows.get(rowIndex);
            String value = aValue == null ? "" : aValue.toString();
            switch (columnIndex) {
                case 0 -> row.setAssignmentTeamNumber(value);
                case 1 -> row.setResourceType(value);
                case 2 -> row.setTaskType(value);
                case 3 -> row.setResourceIdentifier(value);
                case 4 -> row.setLeaderRole(value);
                case 5 -> row.setLeader(value);
                case 6 -> row.setNumberOfPersons(parseInt(value));
                case 7 -> row.setContact(value);
                case 8 -> row.setReportingLocation(value);
                case 9 -> row.setSpecialEquipment(value);
                case 10 -> row.setSupplies(value);
                case 11 -> row.setRemarks(value);
                case 12 -> row.setNotes(value);
                default -> row.setAssignment(value);
            }
            fireTableCellUpdated(rowIndex, columnIndex);
        }
        /** @param value string value. @return parsed integer or zero. */
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
