package org.sarmanagement.icsforms.ui;

import org.sarmanagement.icsforms.model.ActivityEventType;
import org.sarmanagement.icsforms.model.ActivityLogEntry;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.Ics214Form;
import org.sarmanagement.icsforms.model.SarTaskResource;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
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
import java.awt.GridLayout;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * First-cut editor for ICS 214 activity log data.
 *
 * <p>Event types shown in the entry dialog are read from
 * {@link AppData#getActivityEventTypes()}, which can be extended by the operator
 * using the <em>Manage Event Types</em> button.</p>
 */
public class Ics214Panel extends JPanel {
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final AppController controller;
    private final JTextField nameField = UiSupport.textField();
    private final JTextField icsPositionField = UiSupport.textField();
    private final JTextField homeAgencyField = UiSupport.textField();
    private final JTextField preparedByNameField = UiSupport.textField();
    private final JTextField preparedByPositionField = UiSupport.textField();
    private final JTextField preparedBySignatureField = UiSupport.textField();
    private final JSpinner preparedDateTimeField = UiSupport.dateTimeSpinner();
    private final ResourcesTableModel resourcesTableModel = new ResourcesTableModel();
    private final ActivityLogTableModel activityLogTableModel = new ActivityLogTableModel();
    private final JTable resourcesTable = new JTable(resourcesTableModel);
    private final JTable activityLogTable = new JTable(activityLogTableModel);
    private AppData currentData;
    private Ics214Form currentForm;

    /**
     * Creates the ICS 214 editor panel.
     *
     * @param controller application controller.
     */
    public Ics214Panel(AppController controller) {
        super(new BorderLayout(8, 8));
        this.controller = controller;

        JPanel form = UiSupport.formPanel();
        form.setBorder(BorderFactory.createTitledBorder("ICS 214 Activity Log"));
        UiSupport.addRow(form, 0, "Name", nameField);
        UiSupport.addRow(form, 1, "ICS position", icsPositionField);
        UiSupport.addRow(form, 2, "Home agency", homeAgencyField);
        UiSupport.addRow(form, 3, "Prepared by name", preparedByNameField);
        UiSupport.addRow(form, 4, "Prepared by position/title", preparedByPositionField);
        UiSupport.addRow(form, 5, "Prepared by signature", preparedBySignatureField);
        UiSupport.addRow(form, 6, "Prepared date/time", preparedDateTimeField);

        resourcesTable.setFillsViewportHeight(true);
        activityLogTable.setFillsViewportHeight(true);

        JPanel resourcesPanel = new JPanel(new BorderLayout());
        resourcesPanel.setBorder(BorderFactory.createTitledBorder("Section 6 - Resources Assigned"));
        resourcesPanel.add(new JScrollPane(resourcesTable), BorderLayout.CENTER);

        JPanel activityPanel = new JPanel(new BorderLayout());
        activityPanel.setBorder(BorderFactory.createTitledBorder("Section 7 - Activity Log"));
        activityPanel.add(new JScrollPane(activityLogTable), BorderLayout.CENTER);
        activityPanel.add(activityButtonsPanel(), BorderLayout.SOUTH);

        JPanel tablesPanel = new JPanel(new GridLayout(2, 1, 8, 8));
        tablesPanel.add(resourcesPanel);
        tablesPanel.add(activityPanel);

        JScrollPane formScrollPane = new JScrollPane(form);
        formScrollPane.setBorder(BorderFactory.createEmptyBorder());
        formScrollPane.setPreferredSize(new Dimension(0, 240));

        add(formScrollPane, BorderLayout.NORTH);
        add(tablesPanel, BorderLayout.CENTER);
    }

    /**
     * Loads values from the supplied model.
     *
     * @param form activity log form.
     * @param data source document.
     */
    public void loadFromModel(Ics214Form form, AppData data) {
        currentForm = form;
        currentData = data == null ? new AppData() : data;
        if (currentForm == null) {
            clearFields();
            return;
        }
        nameField.setText(nullSafe(currentForm.getName()));
        icsPositionField.setText(nullSafe(currentForm.getIcsPosition()));
        homeAgencyField.setText(nullSafe(currentForm.getHomeAgency()));
        preparedByNameField.setText(nullSafe(currentForm.getPreparedByName()));
        preparedByPositionField.setText(nullSafe(currentForm.getPreparedByPositionTitle()));
        preparedBySignatureField.setText(nullSafe(currentForm.getPreparedBySignature()));
        preparedDateTimeField.setValue(AppController.toDate(currentForm.getPreparedDateTime()));
        resourcesTableModel.setRows(currentForm.getResourcesAssigned());
        activityLogTableModel.setRows(currentForm.getActivityLog(), resolvedEventTypes());
    }

    /** Saves current field values into the stored model reference. */
    public void saveToModel() {
        if (currentForm == null) {
            return;
        }
        currentForm.setName(nameField.getText().trim());
        currentForm.setIcsPosition(icsPositionField.getText().trim());
        currentForm.setHomeAgency(homeAgencyField.getText().trim());
        currentForm.setPreparedByName(preparedByNameField.getText().trim());
        currentForm.setPreparedByPositionTitle(preparedByPositionField.getText().trim());
        currentForm.setPreparedBySignature(preparedBySignatureField.getText().trim());
        currentForm.setPreparedDateTime(AppController.toLocalDateTime((Date) preparedDateTimeField.getValue()));
        currentForm.setResourcesAssigned(resourcesTableModel.getRows());
        currentForm.setActivityLog(activityLogTableModel.getRows());
    }

    /** Returns the configured event types, falling back to defaults when empty. */
    private List<ActivityEventType> resolvedEventTypes() {
        if (currentData == null || currentData.getActivityEventTypes().isEmpty()) {
            return ActivityEventType.defaultTypes();
        }
        return currentData.getActivityEventTypes();
    }

    private void addActivityEntry() {
        if (currentForm == null) {
            return;
        }
        List<ActivityEventType> types = resolvedEventTypes();
        ActivityEntryEditor editor = new ActivityEntryEditor(types);
        JScrollPane scrollPane = new JScrollPane(editor.panel);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        if (!UiSupport.showResizableConfirmDialog(this, "Add activity entry", scrollPane, new Dimension(640, 320))) {
            return;
        }
        currentForm.getActivityLog().add(editor.toEntry());
        activityLogTableModel.setRows(currentForm.getActivityLog(), types);
        controller.markDirty();
    }

    private void removeSelectedActivityEntry(int row) {
        if (currentForm == null || row < 0 || row >= currentForm.getActivityLog().size()) {
            return;
        }
        currentForm.getActivityLog().remove(row);
        activityLogTableModel.setRows(currentForm.getActivityLog(), resolvedEventTypes());
        controller.markDirty();
    }

    private void manageEventTypes() {
        if (currentData == null) {
            return;
        }
        EventTypeManagerDialog dialog = new EventTypeManagerDialog(
                new ArrayList<>(resolvedEventTypes()));
        JScrollPane scrollPane = new JScrollPane(dialog.panel);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        if (UiSupport.showResizableConfirmDialog(this, "Manage event types", scrollPane, new Dimension(480, 380))) {
            currentData.setActivityEventTypes(dialog.getEventTypes());
            activityLogTableModel.setRows(
                    currentForm != null ? currentForm.getActivityLog() : List.of(),
                    resolvedEventTypes());
            controller.markDirty();
        }
    }

    private JPanel activityButtonsPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton add = new JButton("Add Entry");
        JButton remove = new JButton("Remove Entry");
        JButton manage = new JButton("Manage Event Types");
        add.addActionListener(event -> addActivityEntry());
        remove.addActionListener(event -> removeSelectedActivityEntry(activityLogTable.getSelectedRow()));
        manage.addActionListener(event -> manageEventTypes());
        panel.add(add);
        panel.add(remove);
        panel.add(manage);
        return panel;
    }

    private void clearFields() {
        nameField.setText("");
        icsPositionField.setText("");
        homeAgencyField.setText("");
        preparedByNameField.setText("");
        preparedByPositionField.setText("");
        preparedBySignatureField.setText("");
        preparedDateTimeField.setValue(AppController.toDate(null));
        resourcesTableModel.setRows(List.of());
        activityLogTableModel.setRows(List.of(), List.of());
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    // -------------------------------------------------------------------------
    // Activity entry editor dialog
    // -------------------------------------------------------------------------

    private static class ActivityEntryEditor {
        private final JPanel panel = UiSupport.formPanel();
        private final JSpinner timestampField = UiSupport.dateTimeSpinner();
        private final JComboBox<ActivityEventType> eventTypeField;
        private final JTextField resourceIdentifierField = UiSupport.textField();
        private final JTextArea notableActivityField = UiSupport.textArea(4);

        private ActivityEntryEditor(List<ActivityEventType> eventTypes) {
            ActivityEventType[] typeArray = eventTypes.toArray(new ActivityEventType[0]);
            eventTypeField = new JComboBox<>(typeArray);
            // Select the free-text / Note type by default.
            for (ActivityEventType t : typeArray) {
                if (ActivityEventType.ID_FREE_TEXT.equals(t.getId())) {
                    eventTypeField.setSelectedItem(t);
                    break;
                }
            }
            UiSupport.addRow(panel, 0, "Date/time", timestampField);
            UiSupport.addRow(panel, 1, "Event type", eventTypeField);
            UiSupport.addRow(panel, 2, "Resource identifier", resourceIdentifierField);
            UiSupport.addRow(panel, 3, "Notable activity", new JScrollPane(notableActivityField));
        }

        private ActivityLogEntry toEntry() {
            ActivityLogEntry entry = new ActivityLogEntry();
            entry.setTimestamp(AppController.toLocalDateTime((Date) timestampField.getValue()));
            ActivityEventType selected = (ActivityEventType) eventTypeField.getSelectedItem();
            entry.setEventTypeId(selected != null ? selected.getId() : ActivityEventType.ID_FREE_TEXT);
            entry.setResourceIdentifier(resourceIdentifierField.getText().trim());
            entry.setNotableActivity(notableActivityField.getText().trim());
            return entry;
        }
    }

    // -------------------------------------------------------------------------
    // Event type manager dialog
    // -------------------------------------------------------------------------

    private static class EventTypeManagerDialog {
        private final JPanel panel = new JPanel(new BorderLayout(4, 4));
        private final EventTypesTableModel tableModel;
        private final JTable table;

        private EventTypeManagerDialog(List<ActivityEventType> initial) {
            tableModel = new EventTypesTableModel(initial);
            table = new JTable(tableModel);
            table.setFillsViewportHeight(true);

            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
            JButton add = new JButton("Add Custom Type");
            JButton remove = new JButton("Remove Selected");
            add.addActionListener(e -> addCustomType());
            remove.addActionListener(e -> removeSelected());
            buttons.add(add);
            buttons.add(remove);

            panel.add(new JLabel("Built-in types cannot be removed. Add custom types below."), BorderLayout.NORTH);
            panel.add(new JScrollPane(table), BorderLayout.CENTER);
            panel.add(buttons, BorderLayout.SOUTH);
        }

        private void addCustomType() {
            JTextField idField = new JTextField(12);
            JTextField labelField = new JTextField(20);
            JPanel input = new JPanel(new FlowLayout(FlowLayout.LEFT));
            input.add(new JLabel("ID (letters, digits, underscores):"));
            input.add(idField);
            input.add(new JLabel("Label:"));
            input.add(labelField);
            int result = JOptionPane.showConfirmDialog(panel, input, "New Event Type", JOptionPane.OK_CANCEL_OPTION);
            if (result != JOptionPane.OK_OPTION) {
                return;
            }
            String id = idField.getText().trim().toUpperCase().replace(' ', '_');
            String label = labelField.getText().trim();
            if (id.isEmpty() || label.isEmpty()) {
                JOptionPane.showMessageDialog(panel,
                        "Both ID and Label are required.",
                        "Invalid Event Type", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (!id.matches("[A-Z0-9_]+")) {
                JOptionPane.showMessageDialog(panel,
                        "ID may only contain letters (A-Z), digits (0-9), and underscores.",
                        "Invalid Event Type ID", JOptionPane.ERROR_MESSAGE);
                return;
            }
            tableModel.addType(new ActivityEventType(id, label, false));
        }

        private void removeSelected() {
            int row = table.getSelectedRow();
            if (row >= 0) {
                tableModel.removeRow(row);
            }
        }

        private List<ActivityEventType> getEventTypes() {
            return tableModel.getTypes();
        }
    }

    private static class EventTypesTableModel extends AbstractTableModel {
        private final String[] columns = {"ID", "Label", "Built-in"};
        private final List<ActivityEventType> types;

        private EventTypesTableModel(List<ActivityEventType> types) {
            this.types = new ArrayList<>(types);
        }

        void addType(ActivityEventType type) {
            types.add(type);
            fireTableRowsInserted(types.size() - 1, types.size() - 1);
        }

        void removeRow(int row) {
            if (row >= 0 && row < types.size() && !types.get(row).isBuiltIn()) {
                types.remove(row);
                fireTableRowsDeleted(row, row);
            }
        }

        List<ActivityEventType> getTypes() {
            return types;
        }

        @Override public int getRowCount() { return types.size(); }
        @Override public int getColumnCount() { return columns.length; }
        @Override public String getColumnName(int col) { return columns[col]; }
        @Override public boolean isCellEditable(int row, int col) { return false; }

        @Override
        public Object getValueAt(int row, int col) {
            ActivityEventType t = types.get(row);
            return switch (col) {
                case 0 -> t.getId();
                case 1 -> t.getLabel();
                default -> t.isBuiltIn() ? "Yes" : "No";
            };
        }
    }

    // -------------------------------------------------------------------------
    // Table models for resources and activity log
    // -------------------------------------------------------------------------

    private static class ResourcesTableModel extends AbstractTableModel {
        private final String[] columns = {"Name", "ICS Position", "Home Agency"};
        private List<SarTaskResource> rows = new ArrayList<>();

        void setRows(List<SarTaskResource> rows) {
            this.rows = rows == null ? new ArrayList<>() : rows;
            fireTableDataChanged();
        }

        List<SarTaskResource> getRows() {
            return rows;
        }

        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return columns.length; }
        @Override public String getColumnName(int col) { return columns[col]; }
        @Override public boolean isCellEditable(int row, int col) { return false; }

        @Override
        public Object getValueAt(int row, int col) {
            SarTaskResource r = rows.get(row);
            return switch (col) {
                case 0 -> r.getName();
                case 1 -> r.getIcsPosition();
                default -> r.getHomeAgency();
            };
        }
    }

    private static class ActivityLogTableModel extends AbstractTableModel {
        private final String[] columns = {"Date/Time", "Event Type", "Notable Activity"};
        private List<ActivityLogEntry> rows = new ArrayList<>();
        private List<ActivityEventType> eventTypes = new ArrayList<>();

        void setRows(List<ActivityLogEntry> rows, List<ActivityEventType> eventTypes) {
            this.rows = rows == null ? new ArrayList<>() : rows;
            this.eventTypes = eventTypes == null ? ActivityEventType.defaultTypes() : eventTypes;
            fireTableDataChanged();
        }

        List<ActivityLogEntry> getRows() {
            return rows;
        }

        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return columns.length; }
        @Override public String getColumnName(int col) { return columns[col]; }
        @Override public boolean isCellEditable(int row, int col) { return false; }

        @Override
        public Object getValueAt(int row, int col) {
            ActivityLogEntry entry = rows.get(row);
            return switch (col) {
                case 0 -> entry.getTimestamp() == null ? "" : DATE_TIME_FORMATTER.format(entry.getTimestamp());
                case 1 -> resolveLabel(entry.getEventTypeId());
                default -> entry.getNotableActivity();
            };
        }

        private String resolveLabel(String id) {
            if (id == null) {
                return "Note";
            }
            return eventTypes.stream()
                    .filter(t -> id.equals(t.getId()))
                    .map(ActivityEventType::getLabel)
                    .findFirst()
                    .orElse(id);
        }
    }
}
