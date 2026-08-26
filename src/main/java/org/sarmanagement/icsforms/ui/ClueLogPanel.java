package org.sarmanagement.icsforms.ui;

import org.sarmanagement.icsforms.model.ClueLogEntry;
import org.sarmanagement.icsforms.model.Ics214Form;
import org.sarmanagement.icsforms.model.ActivityLogScope;
import org.sarmanagement.icsforms.model.SarTaskAssignment;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Shared clue log editor showing clues captured across SAR task debriefings.
 */
public class ClueLogPanel extends JPanel {
    private final AppController controller;
    private final ClueLogTableModel tableModel = new ClueLogTableModel();
    private final JTable table = new JTable(tableModel);

    public ClueLogPanel(AppController controller) {
        super(new BorderLayout());
        this.controller = controller;

        table.setFillsViewportHeight(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        setBorder(BorderFactory.createTitledBorder("Clue Log"));
        add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton add = new JButton("Add");
        JButton findDuplicates = new JButton("Find Duplicates…");
        JButton syncToLogs = new JButton("Sync to Activity Logs");
        syncToLogs.setToolTipText("Propagate clues with an assigned task to their linked ICS 214 activity logs");
        add.addActionListener(event -> addClueDialog());
        findDuplicates.addActionListener(event -> findAndResolveDuplicates());
        syncToLogs.addActionListener(event -> syncClueLogToActivityLogs());
        buttons.add(add);
        buttons.add(findDuplicates);
        buttons.add(syncToLogs);
        add(buttons, BorderLayout.SOUTH);
    }

    /** Opens a dialog to enter clue details and adds the new entry to the log. */
    private void addClueDialog() {
        // Build a picklist of detecting-task labels from all ICS 214 forms linked to a task
        // assignment.  Each entry shows the task team number and the resource identifier
        // (214 form name) so the user can see both which task and which resource detected
        // the clue.  We also keep a label→assignmentId map for linking the stored entry.
        Map<String, String> labelToAssignmentId = new LinkedHashMap<>();
        Map<String, SarTaskAssignment> taskById = new HashMap<>();
        if (controller.getData().getSarTaskAssignments() != null) {
            for (SarTaskAssignment t : controller.getData().getSarTaskAssignments()) {
                taskById.put(t.getAssignmentId(), t);
            }
        }
        if (controller.getData().getActivityLogs() != null) {
            for (Ics214Form form : controller.getData().getActivityLogs()) {
                if (form.getLogScope() == ActivityLogScope.TASK_ASSIGNMENT
                        && form.getName() != null && !form.getName().isBlank()) {
                    SarTaskAssignment task = taskById.get(form.getLinkedSarTaskAssignmentId());
                    String label = UiSupport.detectingTaskLabel(task, form.getName());
                    if (!label.isBlank() && !labelToAssignmentId.containsKey(label)) {
                        labelToAssignmentId.put(label, form.getLinkedSarTaskAssignmentId());
                    }
                }
            }
        }
        // Fall back to plain task labels when no 214 forms are linked yet.
        if (labelToAssignmentId.isEmpty() && controller.getData().getSarTaskAssignments() != null) {
            for (SarTaskAssignment t : controller.getData().getSarTaskAssignments()) {
                String label = UiSupport.taskLabel(t);
                if (!label.isBlank() && !labelToAssignmentId.containsKey(label)) {
                    labelToAssignmentId.put(label, t.getAssignmentId());
                }
            }
        }

        List<String> taskNames = new ArrayList<>(labelToAssignmentId.keySet());
        JPanel form = UiSupport.formPanel();
        String[] taskItems = taskNames.isEmpty() ? new String[]{""} : taskNames.toArray(new String[0]);
        javax.swing.JComboBox<String> taskCombo = new javax.swing.JComboBox<>(taskItems);
        taskCombo.setEditable(true);
        JSpinner dateTimeSpinner = UiSupport.dateTimeSpinner();
        JTextField locationField = UiSupport.textField();
        JTextArea descriptionArea = UiSupport.textArea(3);
        JTextArea immediateActionArea = UiSupport.textArea(2);
        JCheckBox possibleDuplicateCheck = new JCheckBox("Possible duplicate");

        int row = 0;
        UiSupport.addRow(form, row++, "Detecting task", taskCombo);
        UiSupport.addRow(form, row++, "Date/time collected", dateTimeSpinner);
        UiSupport.addRow(form, row++, "Location / position", locationField);
        UiSupport.addRow(form, row++, "Description", new JScrollPane(descriptionArea));
        UiSupport.addRow(form, row++, "Immediate action taken", new JScrollPane(immediateActionArea));
        UiSupport.addRow(form, row, "", possibleDuplicateCheck);

        JScrollPane scrollPane = new JScrollPane(form);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        if (!UiSupport.showResizableConfirmDialog(this, "Add Clue", scrollPane, new Dimension(640, 370))) {
            return;
        }

        ClueLogEntry clue = new ClueLogEntry();
        Object spinnerValue = dateTimeSpinner.getValue();
        if (spinnerValue instanceof Date) {
            clue.setDateTimeCollected(((Date) spinnerValue).toInstant()
                    .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime());
        } else {
            clue.setDateTimeCollected(LocalDateTime.now());
        }
        Object selectedTask = taskCombo.getSelectedItem();
        clue.setDetectingTask(selectedTask != null ? selectedTask.toString().trim() : "");
        // Link the assignmentId via the label map; fall back to a task-label scan for
        // manually typed entries.
        String assignmentId = labelToAssignmentId.get(clue.getDetectingTask());
        if (assignmentId == null && controller.getData().getSarTaskAssignments() != null) {
            for (SarTaskAssignment t : controller.getData().getSarTaskAssignments()) {
                if (UiSupport.taskLabel(t).equals(clue.getDetectingTask())) {
                    assignmentId = t.getAssignmentId();
                    break;
                }
            }
        }
        if (assignmentId != null) {
            clue.setAssignmentId(assignmentId);
        }
        clue.setLocation(locationField.getText().trim());
        clue.setDescription(descriptionArea.getText().trim());
        clue.setImmediateAction(immediateActionArea.getText().trim());
        clue.setPossibleDuplicate(possibleDuplicateCheck.isSelected());

        tableModel.addRow(clue);
        controller.markDirty();
    }

    public void refreshFromModel() {
        tableModel.setRows(controller.getData().getClueLogEntries());
    }

    public void pushToModel() {
        controller.getData().setClueLogEntries(tableModel.getRows());
    }

    /** Propagates all clues with an assigned task ID to their linked ICS 214 activity logs. */
    private void syncClueLogToActivityLogs() {
        pushToModel();
        int count = 0;
        for (ClueLogEntry clue : tableModel.getRows()) {
            if (clue.getAssignmentId() != null && !clue.getAssignmentId().isBlank()) {
                if (controller.propagateClueToActivityLog(clue)) {
                    count++;
                }
            }
        }
        controller.markDirty();
        JOptionPane.showMessageDialog(this,
                count + " clue(s) propagated to linked activity logs.",
                "Sync Complete", JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Finds potential duplicate clues (same detecting task and same location) and offers to
     * mark the later ones as possible duplicates or remove them.
     */
    private void findAndResolveDuplicates() {
        List<ClueLogEntry> rows = tableModel.getRows();
        // Group by detectingTask + location (case-insensitive, trimmed).
        Map<String, List<Integer>> groups = new LinkedHashMap<>();
        for (int i = 0; i < rows.size(); i++) {
            ClueLogEntry e = rows.get(i);
            String key = e.getDetectingTask().trim().toLowerCase()
                    + "|" + e.getLocation().trim().toLowerCase();
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(i);
        }
        List<Map.Entry<String, List<Integer>>> duplicateGroups = groups.entrySet().stream()
                .filter(en -> en.getValue().size() > 1)
                .collect(Collectors.toList());

        if (duplicateGroups.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "No potential duplicates found (matching detecting resource and location).",
                    "Find Duplicates", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        StringBuilder sb = new StringBuilder("<html><body>The following clues share the same detecting resource and location:<br><br>");
        for (Map.Entry<String, List<Integer>> group : duplicateGroups) {
            ClueLogEntry first = rows.get(group.getValue().get(0));
            sb.append("&bull; <b>").append(escape(first.getDetectingTask())).append("</b> @ <i>")
                    .append(escape(first.getLocation())).append("</i>: ")
                    .append(group.getValue().size()).append(" entries<br>");
        }
        sb.append("<br>Mark the later entries in each group as 'Possible Duplicate'?</body></html>");

        int choice = JOptionPane.showConfirmDialog(this, sb.toString(),
                "Resolve Duplicates", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) {
            return;
        }

        // Mark all but the first entry in each group as possible duplicate.
        for (Map.Entry<String, List<Integer>> group : duplicateGroups) {
            List<Integer> indices = group.getValue();
            for (int i = 1; i < indices.size(); i++) {
                rows.get(indices.get(i)).setPossibleDuplicate(true);
            }
        }
        tableModel.fireTableDataChanged();
        controller.markDirty();
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static class ClueLogTableModel extends AbstractTableModel {
        private final String[] columns = {"Detecting Task", "Date/Time Collected", "Location", "Description", "Immediate Action", "Poss. Dup", "Follow Up"};
        private List<ClueLogEntry> rows = new ArrayList<>();

        void setRows(List<ClueLogEntry> rows) {
            this.rows = rows == null ? new ArrayList<>() : rows;
            fireTableDataChanged();
        }

        List<ClueLogEntry> getRows() {
            return rows;
        }

        void addRow(ClueLogEntry entry) {
            rows.add(entry);
            fireTableRowsInserted(rows.size() - 1, rows.size() - 1);
        }

        void addRow() {
            rows.add(new ClueLogEntry());
            fireTableRowsInserted(rows.size() - 1, rows.size() - 1);
        }

        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return columns.length; }
        @Override public String getColumnName(int column) { return columns[column]; }
        @Override public boolean isCellEditable(int rowIndex, int columnIndex) { return true; }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return columnIndex == 5 ? Boolean.class : String.class;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            ClueLogEntry row = rows.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> row.getDetectingTask();
                case 1 -> SarTaskPanel.formatDateTimeValue(row.getDateTimeCollected());
                case 2 -> row.getLocation();
                case 3 -> row.getDescription();
                case 4 -> row.getImmediateAction();
                case 5 -> row.isPossibleDuplicate();
                default -> row.getFollowUp();
            };
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            ClueLogEntry row = rows.get(rowIndex);
            switch (columnIndex) {
                case 0 -> row.setDetectingTask(aValue == null ? "" : aValue.toString());
                case 1 -> row.setDateTimeCollected(SarTaskPanel.parseDateTimeValue(aValue == null ? "" : aValue.toString()));
                case 2 -> row.setLocation(aValue == null ? "" : aValue.toString());
                case 3 -> row.setDescription(aValue == null ? "" : aValue.toString());
                case 4 -> row.setImmediateAction(aValue == null ? "" : aValue.toString());
                case 5 -> row.setPossibleDuplicate(Boolean.TRUE.equals(aValue));
                default -> row.setFollowUp(aValue == null ? "" : aValue.toString());
            }
            fireTableCellUpdated(rowIndex, columnIndex);
        }
    }
}

