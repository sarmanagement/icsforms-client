package org.sarmanagement.icsforms.ui;

import org.sarmanagement.icsforms.model.ClueLogEntry;
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
        // Build a picklist of task labels (team number + task name) for the task combo.
        // The combo stores the task relationship via assignmentId; a separate "Detected by"
        // field captures the specific person or resource that found the clue.
        Map<String, String> labelToAssignmentId = new LinkedHashMap<>();
        if (controller.getData().getSarTaskAssignments() != null) {
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
        JTextField detectedByField = UiSupport.textField();
        JSpinner dateTimeSpinner = UiSupport.dateTimeSpinner();
        JTextField locationField = UiSupport.textField();
        JTextArea descriptionArea = UiSupport.textArea(3);
        JTextArea immediateActionArea = UiSupport.textArea(2);
        JCheckBox possibleDuplicateCheck = new JCheckBox("Possible duplicate");

        int row = 0;
        UiSupport.addRow(form, row++, "Detecting task", taskCombo);
        UiSupport.addRow(form, row++, "Detected by", detectedByField);
        UiSupport.addRow(form, row++, "Date/time collected", dateTimeSpinner);
        UiSupport.addRow(form, row++, "Location / position", locationField);
        UiSupport.addRow(form, row++, "Description", new JScrollPane(descriptionArea));
        UiSupport.addRow(form, row++, "Immediate action taken", new JScrollPane(immediateActionArea));
        UiSupport.addRow(form, row, "", possibleDuplicateCheck);

        JScrollPane scrollPane = new JScrollPane(form);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        if (!UiSupport.showResizableConfirmDialog(this, "Add Clue", scrollPane, new Dimension(640, 400))) {
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
        // Link the task via assignmentId; the detectingTask text label is no longer used for
        // display (the table derives the label from assignmentId), but we store it for backward
        // compatibility with serialised data that predates this redesign.
        Object selectedTask = taskCombo.getSelectedItem();
        String taskLabel = selectedTask != null ? selectedTask.toString().trim() : "";
        String assignmentId = labelToAssignmentId.get(taskLabel);
        // Fall back to a label scan for manually typed entries that match a real task.
        if (assignmentId == null && !taskLabel.isBlank() && controller.getData().getSarTaskAssignments() != null) {
            for (SarTaskAssignment t : controller.getData().getSarTaskAssignments()) {
                if (UiSupport.taskLabel(t).equals(taskLabel)) {
                    assignmentId = t.getAssignmentId();
                    break;
                }
            }
        }
        if (assignmentId != null) {
            clue.setAssignmentId(assignmentId);
        }
        clue.setDetectingTask(taskLabel);
        clue.setDetectedBy(detectedByField.getText().trim());
        clue.setLocation(locationField.getText().trim());
        clue.setDescription(descriptionArea.getText().trim());
        clue.setImmediateAction(immediateActionArea.getText().trim());
        clue.setPossibleDuplicate(possibleDuplicateCheck.isSelected());

        tableModel.addRow(clue);
        controller.markDirty();
    }

    /**
     * Builds a map from assignment ID to task label for display in the "Task" column.
     * Uses {@link UiSupport#taskLabel} so the column shows the team number and task name.
     */
    private Map<String, String> buildTaskLabelMap() {
        Map<String, String> map = new HashMap<>();
        if (controller.getData().getSarTaskAssignments() != null) {
            for (SarTaskAssignment t : controller.getData().getSarTaskAssignments()) {
                if (t.getAssignmentId() != null && !t.getAssignmentId().isBlank()) {
                    map.put(t.getAssignmentId(), UiSupport.taskLabel(t));
                }
            }
        }
        return map;
    }

    public void refreshFromModel() {
        tableModel.setRows(controller.getData().getClueLogEntries(), buildTaskLabelMap());
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
        private final String[] columns = {"Task", "Detected By", "Date/Time Collected", "Location", "Description", "Immediate Action", "Poss. Dup", "Follow Up"};
        private List<ClueLogEntry> rows = new ArrayList<>();
        /** Map from assignmentId → computed task label (e.g. "T3 – Dog X"), refreshed on setRows. */
        private Map<String, String> taskLabelById = new HashMap<>();

        void setRows(List<ClueLogEntry> rows, Map<String, String> taskLabelById) {
            this.rows = rows == null ? new ArrayList<>() : rows;
            this.taskLabelById = taskLabelById == null ? new HashMap<>() : taskLabelById;
            fireTableDataChanged();
        }

        /** @deprecated kept for callers that don't have task context. */
        void setRows(List<ClueLogEntry> rows) {
            setRows(rows, null);
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

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            // Col 0 (Task) is read-only — derived from assignmentId.
            return columnIndex != 0;
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return columnIndex == 6 ? Boolean.class : String.class;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            ClueLogEntry row = rows.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> {
                    // Derive the task label from assignmentId; fall back to stored detectingTask.
                    String id = row.getAssignmentId();
                    if (id != null && !id.isBlank() && taskLabelById.containsKey(id)) {
                        yield taskLabelById.get(id);
                    }
                    yield row.getDetectingTask();
                }
                case 1 -> row.getDetectedBy();
                case 2 -> SarTaskPanel.formatDateTimeValue(row.getDateTimeCollected());
                case 3 -> row.getLocation();
                case 4 -> row.getDescription();
                case 5 -> row.getImmediateAction();
                case 6 -> row.isPossibleDuplicate();
                default -> row.getFollowUp();
            };
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            ClueLogEntry row = rows.get(rowIndex);
            switch (columnIndex) {
                case 0 -> {} // read-only, derived from assignmentId
                case 1 -> row.setDetectedBy(aValue == null ? "" : aValue.toString());
                case 2 -> row.setDateTimeCollected(SarTaskPanel.parseDateTimeValue(aValue == null ? "" : aValue.toString()));
                case 3 -> row.setLocation(aValue == null ? "" : aValue.toString());
                case 4 -> row.setDescription(aValue == null ? "" : aValue.toString());
                case 5 -> row.setImmediateAction(aValue == null ? "" : aValue.toString());
                case 6 -> row.setPossibleDuplicate(Boolean.TRUE.equals(aValue));
                default -> row.setFollowUp(aValue == null ? "" : aValue.toString());
            }
            fireTableCellUpdated(rowIndex, columnIndex);
        }
    }
}

