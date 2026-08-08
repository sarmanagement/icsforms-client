package org.sarmanagement.icsforms.ui;

import org.sarmanagement.icsforms.model.ClueLogEntry;
import org.sarmanagement.icsforms.model.CommunicationEntry;
import org.sarmanagement.icsforms.model.PodFactorRating;
import org.sarmanagement.icsforms.model.ResourceAssignment;
import org.sarmanagement.icsforms.model.SarTaskAssignment;
import org.sarmanagement.icsforms.model.SarTaskResource;
import org.sarmanagement.icsforms.model.SarTaskSupport;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.ParseException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Editable SAR task assignment and debriefing grid linked from ICS 204 resource assignments.
 */
public class SarTaskPanel extends JPanel {
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final int MAX_RESOURCE_ROWS = 18;
    private static final Set<Integer> READ_ONLY_COLUMNS = Set.of(3, 4, 5, 6, 7, 18, 19);
    private static final int RESOURCE_EDITOR_WIDTH = 420;
    private static final int RESOURCE_EDITOR_VISIBLE_ROWS = 9;
    private static final int RESOURCE_EDITOR_PADDING = 8;
    private static final int CLUE_EDITOR_WIDTH = 720;
    private static final int SCORE_FIELD_WIDTH = 48;
    private static final List<String> CANINE_SEARCH_TYPE_OPTIONS = List.of(
            "", "Wilderness air scent", "Tracking", "Trailing", "Tracking/Trailing",
            "HRD", "Article", "Patrol", "Water", "Other");
    private static final List<String> CANINE_IMPRINT_OPTIONS = List.of(
            "", "Living human", "HRD", "Both live and HRD", "Article/Track");
    private static final String CANINE_WEATHER_FACTOR_NAME = "Weather/Temperature";

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
        tableModel.setRows(controller.getData().getSarTaskAssignments(),
                controller.getData().getClueLogEntries(),
                controller.getData().getForm204().getResourcesAssigned());
    }

    /**
     * Applies edited rows back to the active document.
     */
    public void pushToModel() {
        controller.getData().setSarTaskAssignments(tableModel.getRows());
        controller.syncIcs204ResourcesFromSarTasks();
    }

    static String formatDateTimeValue(LocalDateTime value) {
        return value == null ? "" : DATE_TIME_FORMATTER.format(value);
    }

    static LocalDateTime parseDateTimeValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim(), DATE_TIME_FORMATTER);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private void installRowEditor() {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem editAssignmentItem = new JMenuItem("Edit assignment…");
        editAssignmentItem.addActionListener(event -> openSelectedRowEditor(EditorMode.ASSIGNMENT));
        menu.add(editAssignmentItem);
        JMenuItem editDebriefingItem = new JMenuItem("Debrief…");
        editDebriefingItem.addActionListener(event -> openSelectedRowEditor(EditorMode.DEBRIEFING));
        menu.add(editDebriefingItem);
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
                    openSelectedRowEditor(EditorMode.ASSIGNMENT);
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

    private void openSelectedRowEditor(EditorMode mode) {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            return;
        }
        editRow(table.convertRowIndexToModel(viewRow), mode);
    }

    private void editRow(int rowIndex, EditorMode mode) {
        if (rowIndex < 0 || rowIndex >= tableModel.getRowCount()) {
            return;
        }
        if (table.isEditing() && table.getCellEditor() != null) {
            table.getCellEditor().stopCellEditing();
        }
        SarTaskAssignment row = tableModel.getRows().get(rowIndex);
        SarTaskEditor editor = new SarTaskEditor(row, mode, controller.getData().getClueLogEntries(),
                defaultResourceEditorRowCount(row));
        JScrollPane scrollPane = new JScrollPane(editor.panel);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        String title = mode.dialogTitle(row.getAssignmentTeamNumber());
        if (!UiSupport.showResizableConfirmDialog(this, title, scrollPane,
                mode == EditorMode.ASSIGNMENT ? new Dimension(1040, 680) : new Dimension(980, 620))) {
            return;
        }
        controller.getData().setClueLogEntries(editor.applyTo(row, controller.getData().getClueLogEntries()));
        if (mode == EditorMode.ASSIGNMENT) {
            updateLinkedResourcePersonCount(row, editor.resourceCount());
        }
        controller.syncIcs204ResourcesFromSarTasks();
        tableModel.setRows(tableModel.getRows(),
                controller.getData().getClueLogEntries(),
                controller.getData().getForm204().getResourcesAssigned());
        controller.getData().setSarTaskAssignments(tableModel.getRows());
        controller.markDirty();
        int viewRow = table.convertRowIndexToView(rowIndex);
        if (viewRow >= 0 && viewRow < table.getRowCount()) {
            table.setRowSelectionInterval(viewRow, viewRow);
        }
    }

    private void updateLinkedResourcePersonCount(SarTaskAssignment row, int resourceCount) {
        ResourceAssignment assignment = SarTaskTableModel.findLinkedAssignment(
                row, controller.getData().getForm204().getResourcesAssigned());
        if (assignment != null) {
            assignment.setNumberOfPersons(resourceCount);
        }
    }

    private int defaultResourceEditorRowCount(SarTaskAssignment row) {
        int existing = editableResources(row).size();
        int persons = 0;
        for (ResourceAssignment assignment : controller.getData().getForm204().getResourcesAssigned()) {
            if (assignment == null) {
                continue;
            }
            if (!row.getAssignmentId().isBlank() && row.getAssignmentId().equals(assignment.getAssignmentId())) {
                persons = assignment.getNumberOfPersons();
                break;
            }
            if (row.getAssignmentId().isBlank()
                    && !row.getAssignmentTeamNumber().isBlank()
                    && row.getAssignmentTeamNumber().equals(assignment.getAssignmentTeamNumber())) {
                persons = assignment.getNumberOfPersons();
                break;
            }
        }
        int minimum = persons > 0 ? persons : 1;
        return Math.min(MAX_RESOURCE_ROWS, Math.max(existing, minimum));
    }

    private static JTextField textField(String value, boolean editable) {
        return textField(value, editable, 24);
    }

    private static JTextField textField(String value, boolean editable, int columns) {
        JTextField field = UiSupport.textField();
        field.setColumns(columns);
        field.setText(value == null ? "" : value);
        field.setEditable(editable);
        return field;
    }

    private static JComboBox<String> comboBox(List<String> options, String value) {
        JComboBox<String> field = new JComboBox<>(options.toArray(String[]::new));
        field.setEditable(true);
        field.setSelectedItem(value == null ? "" : value);
        return field;
    }

    private static JSpinner dateTimeSpinner(LocalDateTime value, int width) {
        JSpinner spinner = UiSupport.dateTimeSpinner();
        if (width > 0) {
            spinner.setPreferredSize(new Dimension(width, spinner.getPreferredSize().height));
        }
        if (value == null) {
            spinnerTextField(spinner).setText("");
        } else {
            spinner.setValue(AppController.toDate(value));
        }
        return spinner;
    }

    private static JTextField spinnerTextField(JSpinner spinner) {
        return ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField();
    }

    private static LocalDateTime spinnerDateTimeValue(JSpinner spinner) {
        String text = spinnerTextField(spinner).getText().trim();
        if (text.isBlank()) {
            return null;
        }
        try {
            spinner.commitEdit();
            return AppController.toLocalDateTime((Date) spinner.getValue());
        } catch (ParseException exception) {
            return null;
        }
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

    private static JPanel resourceEditorPanel(SarTaskAssignment row, ResourceEntriesTableModel model, int minimumRows) {
        model.setRows(editableResources(row), minimumRows);
        JTable table = new JTable(model);
        table.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        table.setFillsViewportHeight(true);

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setPreferredSize(new Dimension(RESOURCE_EDITOR_WIDTH,
                table.getRowHeight() * RESOURCE_EDITOR_VISIBLE_ROWS
                        + table.getTableHeader().getPreferredSize().height
                        + RESOURCE_EDITOR_PADDING));

        JButton addButton = new JButton("Add");
        addButton.addActionListener(event -> model.addRow());
        JButton removeButton = new JButton("Remove");
        removeButton.setEnabled(false);
        table.getSelectionModel().addListSelectionListener(event -> removeButton.setEnabled(table.getSelectedRow() >= 0));
        removeButton.addActionListener(event -> {
            if (table.isEditing() && table.getCellEditor() != null) {
                table.getCellEditor().stopCellEditing();
            }
            model.removeRow(table.getSelectedRow());
        });

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        buttons.setOpaque(false);
        buttons.add(addButton);
        buttons.add(removeButton);

        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setOpaque(false);
        panel.add(scrollPane, BorderLayout.CENTER);
        panel.add(buttons, BorderLayout.SOUTH);
        return panel;
    }

    private static JPanel compactFieldRowPanel(LabeledComponent... fields) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        panel.setOpaque(false);
        for (LabeledComponent field : fields) {
            panel.add(new JLabel(field.label()));
            panel.add(field.component());
        }
        return panel;
    }

    private static List<SarTaskResource> editableResources(SarTaskAssignment row) {
        List<SarTaskResource> resources = new ArrayList<>();
        if (row == null || row.getResourcesAssigned() == null) {
            return resources;
        }
        for (SarTaskResource resource : row.getResourcesAssigned()) {
            if (resource == null || isPrimaryTaskResource(row, resource)) {
                continue;
            }
            resources.add(resource);
        }
        return resources;
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

    private static JPanel clueEditorPanel(ClueEntriesTableModel model, List<ClueLogEntry> clues) {
        model.setRows(clues);
        JTable table = new JTable(model);
        table.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        table.setFillsViewportHeight(true);
        JScrollPane scrollPane = new JScrollPane(table);
        updateClueEditorSize(scrollPane, table, model.getRowCount());
        model.addTableModelListener(event -> updateClueEditorSize(scrollPane, table, model.getRowCount()));

        JButton addButton = new JButton("Add");
        addButton.addActionListener(event -> model.addRow());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        buttons.setOpaque(false);
        buttons.add(addButton);

        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setOpaque(false);
        panel.add(scrollPane, BorderLayout.CENTER);
        panel.add(buttons, BorderLayout.SOUTH);
        return panel;
    }

    private static void updateClueEditorSize(JScrollPane scrollPane, JTable table, int rowCount) {
        int visibleRows = Math.max(1, rowCount);
        Insets insets = scrollPane.getInsets();
        int height = table.getRowHeight() * visibleRows
                + table.getTableHeader().getPreferredSize().height
                + insets.top + insets.bottom;
        Dimension size = new Dimension(CLUE_EDITOR_WIDTH, height);
        scrollPane.setPreferredSize(size);
        scrollPane.setMinimumSize(size);
        scrollPane.revalidate();
        scrollPane.repaint();
    }

    private static List<SarTaskResource> resourceValuesFrom(ResourceEntriesTableModel model) {
        List<SarTaskResource> resources = new ArrayList<>();
        for (SarTaskResource entry : model.getRows()) {
            String function = entry.getFunction() == null ? "" : entry.getFunction().trim();
            String name = entry.getName() == null ? "" : entry.getName().trim();
            if (function.isBlank() && name.isBlank()) {
                continue;
            }
            if (resources.size() >= MAX_RESOURCE_ROWS) {
                break;
            }
            SarTaskResource resource = new SarTaskResource();
            resource.setFunction(function);
            resource.setName(name);
            resources.add(resource);
        }
        return resources;
    }

    private static boolean isPrimaryTaskResource(SarTaskAssignment row, SarTaskResource resource) {
        if (row == null || resource == null) {
            return false;
        }
        String resourceName = resource.getName() == null ? "" : resource.getName().trim();
        String resourceFunction = resource.getFunction() == null ? "" : resource.getFunction().trim();
        String taskResourceIdentifier = row.getResourceIdentifier() == null ? "" : row.getResourceIdentifier().trim();
        return !taskResourceIdentifier.isBlank()
                && resourceName.equalsIgnoreCase(taskResourceIdentifier)
                && (resourceFunction.isBlank() || "Resource".equalsIgnoreCase(resourceFunction));
    }

    private static JPanel inlineSummaryPanel(int columns, String... items) {
        JPanel panel = new JPanel(new GridLayout(0, columns, 8, 4));
        panel.setOpaque(false);
        for (String item : items) {
            panel.add(new JLabel(item));
        }
        return panel;
    }

    private static JPanel inlineFieldPanel(LabeledComponent... fields) {
        JPanel panel = new JPanel(new GridLayout(1, fields.length, 8, 0));
        panel.setOpaque(false);
        for (LabeledComponent field : fields) {
            JPanel cell = new JPanel(new BorderLayout(0, 2));
            cell.setOpaque(false);
            cell.add(new JLabel(field.label()), BorderLayout.NORTH);
            cell.add(field.component(), BorderLayout.CENTER);
            panel.add(cell);
        }
        return panel;
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

    private static List<ClueLogEntry> clueValuesFrom(List<ClueLogEntry> entries, SarTaskAssignment row, String detectingTask) {
        List<ClueLogEntry> clues = new ArrayList<>();
        for (ClueLogEntry entry : entries) {
            if (entry == null) {
                continue;
            }
            if (formatDateTimeValue(entry.getDateTimeCollected()).isBlank() && entry.getLocation().isBlank()
                    && entry.getDescription().isBlank() && entry.getFollowUp().isBlank()) {
                continue;
            }
            ClueLogEntry clue = new ClueLogEntry();
            clue.setAssignmentId(row.getAssignmentId());
            clue.setDetectingTask(detectingTask);
            clue.setDateTimeCollected(entry.getDateTimeCollected());
            clue.setLocation(entry.getLocation().trim());
            clue.setDescription(entry.getDescription().trim());
            clue.setFollowUp(entry.getFollowUp().trim());
            clues.add(clue);
        }
        return clues;
    }

    private static List<ClueLogEntry> cluesForTask(SarTaskAssignment row, List<ClueLogEntry> clues) {
        List<ClueLogEntry> matches = new ArrayList<>();
        if (clues == null) {
            return matches;
        }
        for (ClueLogEntry clue : clues) {
            if (clue == null) {
                continue;
            }
            if (!row.getAssignmentId().isBlank() && row.getAssignmentId().equals(clue.getAssignmentId())) {
                matches.add(clue);
                continue;
            }
            if (row.getAssignmentId().isBlank() && !row.getAssignmentTeamNumber().isBlank()
                    && row.getAssignmentTeamNumber().equals(clue.getDetectingTask())) {
                matches.add(clue);
            }
        }
        return matches;
    }

    private static String safeValue(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private record LabeledComponent(String label, JComponent component) {
    }

    private static class CommunicationEntryFields {
        private final JTextField nameField = UiSupport.textField();
        private final JTextField functionField = UiSupport.textField();
        private final JTextField primaryContactField = UiSupport.textField();
    }

    private static class ClueEntriesTableModel extends AbstractTableModel {
        private final String[] columns = {"Date/Time", "Location", "Description", "Follow Up"};
        private final List<ClueLogEntry> rows = new ArrayList<>();

        private void setRows(List<ClueLogEntry> clues) {
            rows.clear();
            if (clues != null) {
                for (ClueLogEntry clue : clues) {
                    ClueLogEntry copy = new ClueLogEntry();
                    copy.setAssignmentId(clue.getAssignmentId());
                    copy.setDetectingTask(clue.getDetectingTask());
                    copy.setDateTimeCollected(clue.getDateTimeCollected());
                    copy.setLocation(clue.getLocation());
                    copy.setDescription(clue.getDescription());
                    copy.setFollowUp(clue.getFollowUp());
                    rows.add(copy);
                }
            }
            if (rows.isEmpty()) {
                rows.add(new ClueLogEntry());
            }
            fireTableDataChanged();
        }

        private List<ClueLogEntry> getRows() {
            return rows;
        }

        private void addRow() {
            rows.add(new ClueLogEntry());
            fireTableRowsInserted(rows.size() - 1, rows.size() - 1);
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return true;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            ClueLogEntry row = rows.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> formatDateTimeValue(row.getDateTimeCollected());
                case 1 -> row.getLocation();
                case 2 -> row.getDescription();
                default -> row.getFollowUp();
            };
        }

        @Override
        public void setValueAt(Object value, int rowIndex, int columnIndex) {
            ClueLogEntry row = rows.get(rowIndex);
            String text = value == null ? "" : value.toString().trim();
            switch (columnIndex) {
                case 0 -> row.setDateTimeCollected(parseDateTimeValue(text));
                case 1 -> row.setLocation(text);
                case 2 -> row.setDescription(text);
                default -> row.setFollowUp(text);
            }
            fireTableCellUpdated(rowIndex, columnIndex);
        }
    }

    private static class PodFactorEntryFields {
        private final String factorName;
        private final int maxScore;
        private final JTextField scoreField = textField("", true, 2);
        private final JTextField descriptionField = UiSupport.textField();

        private PodFactorEntryFields(PodFactorRating rating, boolean descriptionAllowed) {
            this.factorName = rating.getName();
            this.maxScore = rating.getMaxScore();
            this.scoreField.setText(rating.getScore() == null ? "" : String.valueOf(rating.getScore()));
            this.scoreField.setHorizontalAlignment(JTextField.RIGHT);
            Dimension preferredSize = this.scoreField.getPreferredSize();
            this.scoreField.setPreferredSize(new Dimension(SCORE_FIELD_WIDTH, preferredSize.height));
            this.descriptionField.setText(rating.getDescription());
            this.descriptionField.setEditable(descriptionAllowed);
        }
    }

    private static class ResourceEntriesTableModel extends AbstractTableModel {
        private final String[] columns = {"Function", "Name"};
        private final List<SarTaskResource> rows = new ArrayList<>();

        private void setRows(List<SarTaskResource> resources, int minimumRows) {
            rows.clear();
            if (resources != null) {
                for (SarTaskResource resource : resources) {
                    if (rows.size() >= MAX_RESOURCE_ROWS) {
                        break;
                    }
                    SarTaskResource copy = new SarTaskResource();
                    copy.setFunction(resource.getFunction());
                    copy.setName(resource.getName());
                    rows.add(copy);
                }
            }
            while (rows.size() < Math.max(1, minimumRows) && rows.size() < MAX_RESOURCE_ROWS) {
                rows.add(new SarTaskResource());
            }
            fireTableDataChanged();
        }

        private List<SarTaskResource> getRows() {
            return rows;
        }

        private void addRow() {
            if (rows.size() >= MAX_RESOURCE_ROWS) {
                return;
            }
            rows.add(new SarTaskResource());
            fireTableRowsInserted(rows.size() - 1, rows.size() - 1);
        }

        private void removeRow(int rowIndex) {
            if (rowIndex < 0 || rowIndex >= rows.size()) {
                return;
            }
            rows.remove(rowIndex);
            fireTableRowsDeleted(rowIndex, rowIndex);
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return true;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            SarTaskResource row = rows.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> row.getFunction();
                case 1 -> row.getName();
                default -> "";
            };
        }

        @Override
        public void setValueAt(Object value, int rowIndex, int columnIndex) {
            SarTaskResource row = rows.get(rowIndex);
            String text = value == null ? "" : value.toString().trim();
            if (columnIndex == 0) {
                row.setFunction(text);
            } else if (columnIndex == 1) {
                row.setName(text);
            }
            fireTableCellUpdated(rowIndex, columnIndex);
        }
    }

    private enum EditorMode {
        ASSIGNMENT("Edit SAR Task", "Edit SAR Task "),
        DEBRIEFING("Debrief SAR Task", "Debrief SAR Task ");

        private final String untitledDialog;
        private final String titledDialogPrefix;

        EditorMode(String untitledDialog, String titledDialogPrefix) {
            this.untitledDialog = untitledDialog;
            this.titledDialogPrefix = titledDialogPrefix;
        }

        private String dialogTitle(String assignmentTeamNumber) {
            return assignmentTeamNumber == null || assignmentTeamNumber.isBlank()
                    ? untitledDialog
                    : titledDialogPrefix + assignmentTeamNumber;
        }
    }

    private static class SarTaskEditor {
        private final JPanel panel = UiSupport.formPanel();
        private final EditorMode mode;
        private final JTextField assignmentTeamNumberField;
        private final JComboBox<String> resourceTypeField;
        private final JComboBox<String> taskTypeField;
        private final JTextField incidentNameField;
        private final JTextField resourceIdentifierField;
        private final JTextField leaderRoleField;
        private final JTextField leaderField;
        private final JTextField contactField;
        private final JPanel assignmentSummaryField;
        private final JPanel debriefSummaryField;
        private final JScrollPane operationsField;
        private final JScrollPane contextField;
        private final JPanel resourcesAssignedField;
        private final ResourceEntriesTableModel resourceEntryTableModel = new ResourceEntriesTableModel();
        private final JScrollPane assignmentField;
        private final JScrollPane transportationField;
        private final JTextField taskMapField;
        private final JScrollPane specialEquipmentField;
        private final JTextField debriefingSupervisorField;
        private final JSpinner assignmentStartField;
        private final JSpinner assignmentEndField;
        private final JTextField vehicleMilesField;
        private final JTextField reportedPodField;
        private final JScrollPane debriefNotesField;
        private final JPanel clueEntriesField;
        private final ClueEntriesTableModel clueEntryTableModel = new ClueEntriesTableModel();
        private final JPanel podFactorsField = new JPanel(new GridBagLayout());
        private final List<PodFactorEntryFields> podFactorEntryFields = new ArrayList<>();
        private final JComboBox<String> canineSearchTypeField;
        private final JComboBox<String> canineImprintField;
        private final JTextField canineSunAngleField;
        private final JTextField canineDayNightField;
        private final JTextField canineCloudCoverField;
        private final JTextField canineWindSpeedField;
        private final JScrollPane areasNotCoveredField;
        private final JScrollPane hazardsObservedField;

        private SarTaskEditor(SarTaskAssignment row, EditorMode mode, List<ClueLogEntry> clueLogEntries, int resourceRowCount) {
            this.mode = mode;
            assignmentTeamNumberField = textField(row.getAssignmentTeamNumber(), true);
            resourceTypeField = new JComboBox<>(SarTaskSupport.resourceTypes().toArray(String[]::new));
            resourceTypeField.setEditable(true);
            resourceTypeField.setSelectedItem(row.getResourceType());
            taskTypeField = new JComboBox<>(SarTaskSupport.taskTypes().toArray(String[]::new));
            taskTypeField.setEditable(true);
            taskTypeField.setSelectedItem(row.getTaskType());
            incidentNameField = textField(row.getIncidentName(), false);
            resourceIdentifierField = textField(row.getResourceIdentifier(), false);
            leaderRoleField = textField(row.getLeaderRole(), false);
            leaderField = textField(row.getLeader(), false);
            contactField = textField(row.getContact(), false);
            assignmentSummaryField = inlineSummaryPanel(2,
                    "Incident: " + safeValue(row.getIncidentName()),
                    "Resource: " + safeValue(row.getResourceIdentifier()),
                    "Leader: " + safeValue(row.getLeader()),
                    "Leader Role: " + safeValue(row.getLeaderRole()),
                    "Leader Contact: " + safeValue(row.getContact()));
            debriefSummaryField = inlineSummaryPanel(2,
                    "Incident: " + safeValue(row.getIncidentName()),
                    "Resource: " + safeValue(row.getResourceIdentifier()));
            operationsField = textArea(SarTaskTableModel.joinOperations(row), 2, false);
            contextField = textArea(SarTaskTableModel.joinContext(row), 2, false);
            resourcesAssignedField = resourceEditorPanel(row, resourceEntryTableModel, resourceRowCount);
            assignmentField = textArea(row.getAssignment(), 3, true);
            transportationField = textArea(row.getTransportationInstructions(), 2, true);
            taskMapField = textField(row.getTaskMap(), true, 14);
            specialEquipmentField = textArea(row.getSpecialEquipment(), 2, true);
            debriefingSupervisorField = textField(row.getDebriefingSupervisor(), true, 12);
            assignmentStartField = dateTimeSpinner(row.getAssignmentStart(), 132);
            assignmentEndField = dateTimeSpinner(row.getAssignmentEnd(), 132);
            vehicleMilesField = textField(row.getVehicleMiles(), true, 6);
            reportedPodField = textField(row.getReportedPod(), true, 2);
            debriefNotesField = textArea(row.getDebriefNotes(), 4, true);
            clueEntriesField = clueEditorPanel(clueEntryTableModel, cluesForTask(row, clueLogEntries));
            canineSearchTypeField = comboBox(CANINE_SEARCH_TYPE_OPTIONS, row.getCanineSearchType());
            canineImprintField = comboBox(CANINE_IMPRINT_OPTIONS, row.getCanineImprint());
            canineSearchTypeField.setPreferredSize(new Dimension(140, canineSearchTypeField.getPreferredSize().height));
            canineImprintField.setPreferredSize(new Dimension(150, canineImprintField.getPreferredSize().height));
            canineSunAngleField = textField(row.getCanineSunAngle(), true, 8);
            canineDayNightField = textField(row.getCanineDayNight(), true, 8);
            canineCloudCoverField = textField(row.getCanineCloudCover(), true, 8);
            canineWindSpeedField = textField(row.getCanineWindSpeed(), true, 8);
            areasNotCoveredField = textArea(row.getAreasNotCovered(), 3, true);
            hazardsObservedField = textArea(row.getHazardsObserved(), 3, true);

            podFactorsField.setOpaque(false);
            rebuildPodFactorFields(row.getResourceType(), row.getQualitativePodFactors());
            resourceTypeField.addActionListener(event -> rebuildPodFactorFields(selectedComboValue(resourceTypeField), existingFactorValues()));

            int rowIndex = 0;
            UiSupport.addRequiredRow(panel, rowIndex++, "Assignment/Team #", assignmentTeamNumberField);
            UiSupport.addRow(panel, rowIndex++, "Task setup", inlineFieldPanel(
                    new LabeledComponent("Resource type", resourceTypeField),
                    new LabeledComponent("Task Geometry", taskTypeField)));
            if (mode == EditorMode.ASSIGNMENT) {
                UiSupport.addRow(panel, rowIndex++, "Inherited task data", assignmentSummaryField);
                UiSupport.addRow(panel, rowIndex++, "Operations personnel", operationsField);
                UiSupport.addRow(panel, rowIndex++, "Context", contextField);
                UiSupport.addRow(panel, rowIndex++, "Resources assigned", resourcesAssignedField);
                UiSupport.addRow(panel, rowIndex++, "Work assignment", assignmentField);
                UiSupport.addRow(panel, rowIndex++, "Field details", inlineFieldPanel(
                        new LabeledComponent("Transportation", transportationField),
                        new LabeledComponent("Task map", taskMapField)));
                UiSupport.addRow(panel, rowIndex++, "Special equipment", specialEquipmentField);
                return;
            }
            UiSupport.addRow(panel, rowIndex++, "Task summary", debriefSummaryField);
            UiSupport.addRow(panel, rowIndex++, "Debrief details", inlineFieldPanel(
                    new LabeledComponent("Debrief supervisor", debriefingSupervisorField),
                    new LabeledComponent("Reported POD (%)", reportedPodField),
                    new LabeledComponent("Vehicle miles", vehicleMilesField)));
            UiSupport.addRow(panel, rowIndex++, "Time on assignment", inlineFieldPanel(
                    new LabeledComponent("Assignment start", assignmentStartField),
                    new LabeledComponent("Assignment end", assignmentEndField)));
            UiSupport.addRow(panel, rowIndex++, "Debriefing", debriefNotesField);
            UiSupport.addRow(panel, rowIndex++, "Clues detected", clueEntriesField);
            UiSupport.addWideRow(panel, rowIndex++, podFactorsField);
            UiSupport.addRow(panel, rowIndex++, "Areas not covered", areasNotCoveredField);
            UiSupport.addRow(panel, rowIndex, "Hazards observed", hazardsObservedField);
        }

        private void rebuildPodFactorFields(String resourceType, List<PodFactorRating> existing) {
            podFactorsField.removeAll();
            podFactorEntryFields.clear();
            boolean canine = SarTaskSupport.usesCanineFactors(resourceType);
            boolean canineResourceDetailsAdded = false;
            boolean canineWeatherDetailsAdded = false;
            addPodFactorCell(new JLabel("<html>Qualitative POD Factors<br>Factor</html>"), 0, 0, 0.0, GridBagConstraints.NONE);
            addPodFactorCell(new JLabel("Score"), 1, 0, 0.0, GridBagConstraints.NONE);
            addPodFactorCell(new JLabel("Description"), 2, 0, 1.0, GridBagConstraints.HORIZONTAL);
            int rowIndex = 1;
            for (PodFactorRating rating : SarTaskSupport.factorRatings(resourceType, existing)) {
                boolean descriptionAllowed = SarTaskSupport.allowsDescription(resourceType, rating.getName());
                PodFactorEntryFields fields = new PodFactorEntryFields(rating, descriptionAllowed);
                podFactorEntryFields.add(fields);
                addPodFactorCell(new JLabel(rating.getName() + " (1-" + rating.getMaxScore() + ")"),
                        0, rowIndex, 0.0, GridBagConstraints.NONE);
                addPodFactorCell(fields.scoreField, 1, rowIndex, 0.0, GridBagConstraints.NONE);
                addPodFactorCell(fields.descriptionField, 2, rowIndex, 1.0, GridBagConstraints.HORIZONTAL);
                rowIndex++;
                if (canine && SarTaskSupport.CANINE_HANDLER_CERTIFICATION_FACTOR_NAME.equals(rating.getName())) {
                    rowIndex = addCanineResourceDetailRow(rowIndex);
                    canineResourceDetailsAdded = true;
                }
                if (canine && CANINE_WEATHER_FACTOR_NAME.equals(rating.getName())) {
                    rowIndex = addCanineWeatherRows(rowIndex);
                    canineWeatherDetailsAdded = true;
                }
            }
            if (canine && !canineResourceDetailsAdded) {
                rowIndex = addCanineResourceDetailRow(rowIndex);
            }
            if (canine && !canineWeatherDetailsAdded) {
                addCanineWeatherRows(rowIndex);
            }
            podFactorsField.revalidate();
            podFactorsField.repaint();
        }

        private void addPodFactorCell(Component component, int column, int row, double weightx, int fill) {
            GridBagConstraints constraints = new GridBagConstraints();
            constraints.gridx = column;
            constraints.gridy = row;
            constraints.weightx = weightx;
            constraints.anchor = GridBagConstraints.NORTHWEST;
            constraints.fill = fill;
            constraints.insets = new Insets(0, 0, 3, column == 2 ? 0 : 6);
            podFactorsField.add(component, constraints);
        }

        private void addPodFactorDetailCell(Component component, int row) {
            GridBagConstraints constraints = new GridBagConstraints();
            constraints.gridx = 1;
            constraints.gridy = row;
            constraints.gridwidth = 2;
            constraints.weightx = 1.0;
            constraints.anchor = GridBagConstraints.NORTHWEST;
            constraints.fill = GridBagConstraints.HORIZONTAL;
            constraints.insets = new Insets(0, 0, 3, 0);
            podFactorsField.add(component, constraints);
        }

        private int addCanineResourceDetailRow(int rowIndex) {
            addPodFactorDetailCell(compactFieldRowPanel(
                    new LabeledComponent("Canine resource type", canineSearchTypeField),
                    new LabeledComponent("Dog imprinted on", canineImprintField)), rowIndex++);
            return rowIndex;
        }

        private int addCanineWeatherRows(int rowIndex) {
            addPodFactorDetailCell(compactFieldRowPanel(
                    new LabeledComponent("Sun angle", canineSunAngleField),
                    new LabeledComponent("Day/night", canineDayNightField)), rowIndex++);
            addPodFactorDetailCell(compactFieldRowPanel(
                    new LabeledComponent("Cloud cover", canineCloudCoverField),
                    new LabeledComponent("Wind speed", canineWindSpeedField)), rowIndex++);
            return rowIndex;
        }

        private List<PodFactorRating> existingFactorValues() {
            List<PodFactorRating> ratings = new ArrayList<>();
            for (PodFactorEntryFields entry : podFactorEntryFields) {
                PodFactorRating rating = new PodFactorRating();
                rating.setName(entry.factorName);
                rating.setMaxScore(entry.maxScore);
                rating.setScore(parseInteger(entry.scoreField.getText()));
                rating.setDescription(entry.descriptionField.getText().trim());
                ratings.add(rating);
            }
            return ratings;
        }

        private List<ClueLogEntry> applyTo(SarTaskAssignment row, List<ClueLogEntry> clueLogEntries) {
            row.setAssignmentTeamNumber(assignmentTeamNumberField.getText().trim());
            row.setResourceType(selectedComboValue(resourceTypeField));
            row.setTaskType(selectedComboValue(taskTypeField));
            if (mode == EditorMode.ASSIGNMENT) {
                row.setResourcesAssigned(resourceValuesFrom(resourceEntryTableModel));
                row.setAssignment(textAreaFrom(assignmentField).getText().trim());
                row.setTransportationInstructions(textAreaFrom(transportationField).getText().trim());
                row.setTaskMap(taskMapField.getText().trim());
                row.setSpecialEquipment(textAreaFrom(specialEquipmentField).getText().trim());
                return clueLogEntries == null ? new ArrayList<>() : new ArrayList<>(clueLogEntries);
            }
            row.setDebriefingSupervisor(debriefingSupervisorField.getText().trim());
            row.setAssignmentStart(spinnerDateTimeValue(assignmentStartField));
            row.setAssignmentEnd(spinnerDateTimeValue(assignmentEndField));
            row.setVehicleMiles(vehicleMilesField.getText().trim());
            row.setReportedPod(reportedPodField.getText().trim());
            row.setDebriefNotes(textAreaFrom(debriefNotesField).getText().trim());
            row.setQualitativePodFactors(existingFactorValues());
            row.setCanineSearchType(selectedComboValue(canineSearchTypeField));
            row.setCanineImprint(selectedComboValue(canineImprintField));
            row.setCanineSunAngle(canineSunAngleField.getText().trim());
            row.setCanineDayNight(canineDayNightField.getText().trim());
            row.setCanineCloudCover(canineCloudCoverField.getText().trim());
            row.setCanineWindSpeed(canineWindSpeedField.getText().trim());
            row.setAreasNotCovered(textAreaFrom(areasNotCoveredField).getText().trim());
            row.setHazardsObserved(textAreaFrom(hazardsObservedField).getText().trim());

            List<ClueLogEntry> updatedClues = clueLogEntries == null ? new ArrayList<>() : new ArrayList<>(clueLogEntries);
            if (!row.getAssignmentId().isBlank()) {
                for (Iterator<ClueLogEntry> iterator = updatedClues.iterator(); iterator.hasNext(); ) {
                    ClueLogEntry clue = iterator.next();
                    if (row.getAssignmentId().equals(clue.getAssignmentId())) {
                        iterator.remove();
                    }
                }
            } else if (!row.getAssignmentTeamNumber().isBlank()) {
                for (Iterator<ClueLogEntry> iterator = updatedClues.iterator(); iterator.hasNext(); ) {
                    ClueLogEntry clue = iterator.next();
                    if (row.getAssignmentTeamNumber().equals(clue.getDetectingTask())) {
                        iterator.remove();
                    }
                }
            }
            updatedClues.addAll(clueValuesFrom(clueEntryTableModel.getRows(), row, row.getAssignmentTeamNumber()));
            return updatedClues;
        }

        private int resourceCount() {
            return resourceValuesFrom(resourceEntryTableModel).size();
        }
    }

    private static String selectedComboValue(JComboBox<String> comboBox) {
        Object selected = comboBox.getEditor().getItem();
        return selected == null ? "" : selected.toString().trim();
    }

    private static Integer parseInteger(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /**
     * Table model for editable SAR task rows.
     */
    private static class SarTaskTableModel extends AbstractTableModel {
        private final String[] columns = {
                "Assignment/Team # (required)", "Resource Type", "Task Geometry", "Resource", "Leader Role", "Leader", "Contact", "People",
                "Work Assignment", "Transportation", "Task Map", "Special Equipment", "Communications",
                "Debrief Supervisor", "Time On Start", "Time On End", "Vehicle Miles", "Reported POD",
                "Clues Detected", "POD Factors", "Debriefing", "Areas Not Covered", "Hazards Observed"
        };
        private List<SarTaskAssignment> rows = new ArrayList<>();
        private List<ClueLogEntry> clueLogEntries = new ArrayList<>();
        private List<ResourceAssignment> resourceAssignments = new ArrayList<>();

        void setRows(List<SarTaskAssignment> rows, List<ClueLogEntry> clueLogEntries, List<ResourceAssignment> resourceAssignments) {
            this.rows = rows == null ? new ArrayList<>() : rows;
            this.clueLogEntries = clueLogEntries == null ? new ArrayList<>() : clueLogEntries;
            this.resourceAssignments = resourceAssignments == null ? new ArrayList<>() : resourceAssignments;
            fireTableDataChanged();
        }

        List<SarTaskAssignment> getRows() {
            return rows;
        }

        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return columns.length; }
        @Override public String getColumnName(int column) { return columns[column]; }
        @Override public boolean isCellEditable(int rowIndex, int columnIndex) { return !READ_ONLY_COLUMNS.contains(columnIndex); }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            SarTaskAssignment row = rows.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> row.getAssignmentTeamNumber();
                case 1 -> row.getResourceType();
                case 2 -> row.getTaskType();
                case 3 -> row.getResourceIdentifier();
                case 4 -> row.getLeaderRole();
                case 5 -> row.getLeader();
                case 6 -> row.getContact();
                case 7 -> linkedPeople(row, resourceAssignments);
                case 8 -> row.getAssignment();
                case 9 -> row.getTransportationInstructions();
                case 10 -> row.getTaskMap();
                case 11 -> row.getSpecialEquipment();
                case 12 -> formatCommunications(row.getCommunications());
                case 13 -> row.getDebriefingSupervisor();
                case 14 -> formatDateTimeValue(row.getAssignmentStart());
                case 15 -> formatDateTimeValue(row.getAssignmentEnd());
                case 16 -> row.getVehicleMiles();
                case 17 -> row.getReportedPod();
                case 18 -> formatClues(row, clueLogEntries);
                case 19 -> formatPodFactors(row.getQualitativePodFactors());
                case 20 -> row.getDebriefNotes();
                case 21 -> row.getAreasNotCovered();
                case 22 -> row.getHazardsObserved();
                default -> "";
            };
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            SarTaskAssignment row = rows.get(rowIndex);
            String value = aValue == null ? "" : aValue.toString();
            switch (columnIndex) {
                case 0 -> row.setAssignmentTeamNumber(value);
                case 1 -> row.setResourceType(value);
                case 2 -> row.setTaskType(value);
                case 8 -> row.setAssignment(value);
                case 9 -> row.setTransportationInstructions(value);
                case 10 -> row.setTaskMap(value);
                case 11 -> row.setSpecialEquipment(value);
                case 12 -> row.setCommunications(parseCommunications(value));
                case 13 -> row.setDebriefingSupervisor(value);
                case 14 -> row.setAssignmentStart(parseDateTimeValue(value));
                case 15 -> row.setAssignmentEnd(parseDateTimeValue(value));
                case 16 -> row.setVehicleMiles(value);
                case 17 -> row.setReportedPod(value);
                case 20 -> row.setDebriefNotes(value);
                case 21 -> row.setAreasNotCovered(value);
                case 22 -> row.setHazardsObserved(value);
                default -> { return; }
            }
            fireTableCellUpdated(rowIndex, columnIndex);
        }

        private static int linkedPeople(SarTaskAssignment row, List<ResourceAssignment> resourceAssignments) {
            ResourceAssignment assignment = findLinkedAssignment(row, resourceAssignments);
            if (assignment != null) {
                return assignment.getNumberOfPersons();
            }
            return row.getResourcesAssigned().size();
        }

        private static ResourceAssignment findLinkedAssignment(SarTaskAssignment row, List<ResourceAssignment> resourceAssignments) {
            for (ResourceAssignment assignment : resourceAssignments) {
                if (assignment == null) {
                    continue;
                }
                if (!row.getAssignmentId().isBlank() && row.getAssignmentId().equals(assignment.getAssignmentId())) {
                    return assignment;
                }
                if (row.getAssignmentId().isBlank() && !row.getAssignmentTeamNumber().isBlank()
                        && row.getAssignmentTeamNumber().equals(assignment.getAssignmentTeamNumber())) {
                    return assignment;
                }
            }
            return null;
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

        private static String formatClues(SarTaskAssignment row, List<ClueLogEntry> clueLogEntries) {
            List<String> descriptions = new ArrayList<>();
            if (clueLogEntries == null) {
                return "";
            }
            for (ClueLogEntry clue : clueLogEntries) {
                if (clue == null || clue.getDescription().isBlank()) {
                    continue;
                }
                boolean matchesAssignment = !row.getAssignmentId().isBlank() && row.getAssignmentId().equals(clue.getAssignmentId());
                boolean matchesTeamNumber = row.getAssignmentId().isBlank() && !row.getAssignmentTeamNumber().isBlank()
                        && row.getAssignmentTeamNumber().equals(clue.getDetectingTask());
                if (matchesAssignment || matchesTeamNumber) {
                    descriptions.add(clue.getDescription());
                }
            }
            return String.join(" ; ", descriptions);
        }

        private static String formatPodFactors(List<PodFactorRating> factors) {
            List<String> values = new ArrayList<>();
            for (PodFactorRating factor : factors) {
                if (factor == null || factor.getScore() == null) {
                    continue;
                }
                values.add(factor.getName() + " " + factor.getScore() + "/" + factor.getMaxScore());
            }
            return String.join(" ; ", values);
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
