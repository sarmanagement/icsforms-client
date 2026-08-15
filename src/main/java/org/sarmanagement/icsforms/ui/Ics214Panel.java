package org.sarmanagement.icsforms.ui;

import org.sarmanagement.icsforms.model.ActivityEventType;
import org.sarmanagement.icsforms.model.ActivityLogEntry;
import org.sarmanagement.icsforms.model.ActivityLogScope;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.ClueLogEntry;
import org.sarmanagement.icsforms.model.Ics214Form;
import org.sarmanagement.icsforms.model.ResourceAssignment;
import org.sarmanagement.icsforms.model.SarTaskAssignment;
import org.sarmanagement.icsforms.model.SarTaskResource;
import org.sarmanagement.icsforms.model.TCard;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * First-cut editor for ICS 214 activity log data.
 *
 * <p>Event types shown in the entry dialog are read from
 * {@link AppData#getActivityEventTypes()}, which can be managed via the
 * Configuration menu.</p>
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
    private final JButton pickPreparerButton = new JButton("Pick preparer from task…");
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

        pickPreparerButton.setEnabled(false);
        pickPreparerButton.addActionListener(event -> pickPreparerFromTask());
        JPanel pickPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        pickPanel.add(pickPreparerButton);

        resourcesTable.setFillsViewportHeight(true);
        activityLogTable.setFillsViewportHeight(true);

        // Item 6: Double-click on a resource row shows its linked T-card (read-only).
        resourcesTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && javax.swing.SwingUtilities.isLeftMouseButton(e)) {
                    showTCardForSelectedResource();
                }
            }
        });

        JPanel resourcesPanel = new JPanel(new BorderLayout());
        resourcesPanel.setBorder(BorderFactory.createTitledBorder("Section 6 - Resources Assigned"));
        // Item 7: "Refresh from ICP" button populates resources from ICP T-cards and 204 forms.
        JButton refreshResourcesBtn = new JButton("Refresh from ICP & 204…");
        refreshResourcesBtn.setToolTipText("Populate resources list from T-cards at ICP and from 204 assignment forms");
        refreshResourcesBtn.addActionListener(e -> refreshResourcesFromIcp());
        JPanel resourcesButtonRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        resourcesButtonRow.add(refreshResourcesBtn);
        resourcesPanel.add(new JScrollPane(resourcesTable), BorderLayout.CENTER);
        resourcesPanel.add(resourcesButtonRow, BorderLayout.SOUTH);

        JPanel activityPanel = new JPanel(new BorderLayout());
        activityPanel.setBorder(BorderFactory.createTitledBorder("Section 7 - Activity Log"));
        activityPanel.add(new JScrollPane(activityLogTable), BorderLayout.CENTER);
        activityPanel.add(activityButtonsPanel(), BorderLayout.SOUTH);

        JPanel tablesPanel = new JPanel(new GridLayout(2, 1, 8, 8));
        tablesPanel.add(resourcesPanel);
        tablesPanel.add(activityPanel);

        JPanel northPanel = new JPanel(new BorderLayout());

        JScrollPane formScrollPane = new JScrollPane(form);
        formScrollPane.setBorder(BorderFactory.createEmptyBorder());
        formScrollPane.setPreferredSize(new Dimension(0, 240));

        northPanel.add(formScrollPane, BorderLayout.CENTER);
        northPanel.add(pickPanel, BorderLayout.SOUTH);

        add(northPanel, BorderLayout.NORTH);
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
            pickPreparerButton.setEnabled(false);
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
        pickPreparerButton.setEnabled(currentForm.getLogScope() == ActivityLogScope.TASK_ASSIGNMENT
                && !linkedTaskResources().isEmpty());
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

    /** Reloads event type labels after the event type list has been modified. */
    public void refreshEventTypes() {
        if (currentForm != null) {
            activityLogTableModel.setRows(currentForm.getActivityLog(), resolvedEventTypes());
        }
    }

    /** Returns the configured event types, falling back to defaults when empty. */
    private List<ActivityEventType> resolvedEventTypes() {
        if (currentData == null || currentData.getActivityEventTypes().isEmpty()) {
            return ActivityEventType.defaultTypes();
        }
        return currentData.getActivityEventTypes();
    }

    /** Returns the resources assigned to the linked SAR task, or an empty list. */
    private List<SarTaskResource> linkedTaskResources() {
        if (currentForm == null || currentData == null) {
            return List.of();
        }
        String taskId = currentForm.getLinkedSarTaskAssignmentId();
        if (taskId == null || taskId.isBlank()) {
            return List.of();
        }
        return currentData.getSarTaskAssignments().stream()
                .filter(t -> taskId.equals(t.getAssignmentId()))
                .findFirst()
                .map(SarTaskAssignment::getResourcesAssigned)
                .orElse(List.of());
    }

    private void pickPreparerFromTask() {
        List<SarTaskResource> resources = linkedTaskResources();
        if (resources.isEmpty()) {
            return;
        }
        SarTaskResource[] resourceArray = resources.toArray(new SarTaskResource[0]);
        JComboBox<SarTaskResource> combo = new JComboBox<>(resourceArray);
        combo.setRenderer((list, value, index, isSelected, cellHasFocus) -> {
            JLabel label = new JLabel(value == null ? "" : value.getName() + " (" + value.getIcsPosition() + ")");
            if (isSelected) {
                label.setBackground(list.getSelectionBackground());
                label.setForeground(list.getSelectionForeground());
                label.setOpaque(true);
            }
            return label;
        });
        int result = JOptionPane.showConfirmDialog(this, combo, "Pick preparer", JOptionPane.OK_CANCEL_OPTION);
        if (result != JOptionPane.OK_OPTION) {
            return;
        }
        SarTaskResource selected = (SarTaskResource) combo.getSelectedItem();
        if (selected == null) {
            return;
        }
        preparedByNameField.setText(nullSafe(selected.getName()));
        preparedByPositionField.setText(nullSafe(selected.getIcsPosition()));
        homeAgencyField.setText(nullSafe(selected.getHomeAgency()));
        controller.markDirty();
    }

    private void addActivityEntry() {
        if (currentForm == null) {
            return;
        }
        List<ActivityEventType> types = resolvedEventTypes();
        ActivityEntryEditor editor = new ActivityEntryEditor(types, currentForm.getName());
        JScrollPane scrollPane = new JScrollPane(editor.panel);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        if (!UiSupport.showResizableConfirmDialog(this, "Add activity entry", scrollPane, new Dimension(640, 320))) {
            return;
        }
        ActivityLogEntry entry = editor.toEntry();
        currentForm.getActivityLog().add(entry);
        activityLogTableModel.setRows(currentForm.getActivityLog(), types);

        // For clue-related event types, open the clue capture dialog.
        String eventTypeId = entry.getEventTypeId();
        boolean isClueDetected = ActivityEventType.ID_CLUE_DETECTED.equals(eventTypeId);
        boolean isClueReported = ActivityEventType.ID_CLUE_REPORTED.equals(eventTypeId);
        if ((isClueDetected || isClueReported) && currentData != null) {
            captureClue(entry, eventTypeId);
        }

        controller.markDirty();
    }

    /**
     * Shows the clue capture dialog pre-populated from the given activity log entry and adds the
     * result to the shared clue log.
     *
     * <p>Behaviour differs by log type and event type:</p>
     * <ul>
     *   <li>Resource 214 (task-linked): detecting resource is fixed to the form name; no
     *       possible-duplicate checkbox.</li>
     *   <li>Management 214 (ICP / assignment-list): detecting resource is chosen from a
     *       picklist of task-linked 214 forms; possible-duplicate checkbox is shown.</li>
     *   <li>CLUE_REPORTED: the follow-up field is hidden (follow-up belongs to the detecting
     *       resource's own log, not the reporter's).</li>
     * </ul>
     *
     * @param sourceEntry the activity log entry that triggered clue capture.
     * @param eventTypeId the event type identifier ({@code CLUE_DETECTED} or {@code CLUE_REPORTED}).
     */
    private void captureClue(ActivityLogEntry sourceEntry, String eventTypeId) {
        boolean isClueReported = ActivityEventType.ID_CLUE_REPORTED.equals(eventTypeId);
        boolean isResourceLog = currentForm.getLogScope() == ActivityLogScope.TASK_ASSIGNMENT;

        JPanel form = UiSupport.formPanel();
        JTextField locationField = UiSupport.textField();
        JTextArea descriptionArea = UiSupport.textArea(3);
        JTextArea immediateActionArea = UiSupport.textArea(2);

        // Detecting resource: fixed for resource 214, picklist for management 214.
        JComboBox<String> detectingResourceCombo = null;
        JLabel detectingResourceLabel = null;
        if (isResourceLog) {
            detectingResourceLabel = new JLabel(nullSafe(currentForm.getName()));
        } else {
            List<String> resourceNames = taskLinkedFormNames();
            String[] items = resourceNames.isEmpty()
                    ? new String[]{""}
                    : resourceNames.toArray(new String[0]);
            detectingResourceCombo = new JComboBox<>(items);
        }

        // Follow-up: hidden for CLUE_REPORTED (belongs to the detecting resource's own log).
        JTextArea followUpArea = isClueReported ? null : UiSupport.textArea(2);

        // Possible duplicate: shown only for management 214.
        JCheckBox possibleDuplicateCheck = null;
        if (!isResourceLog) {
            possibleDuplicateCheck = new JCheckBox("Possible duplicate (detecting resource may have already logged this clue)");
            possibleDuplicateCheck.setSelected(isClueReported);
        }

        int row = 0;
        if (detectingResourceCombo != null) {
            UiSupport.addRow(form, row++, "Detecting resource", detectingResourceCombo);
        } else {
            UiSupport.addRow(form, row++, "Detecting resource", detectingResourceLabel);
        }
        UiSupport.addRow(form, row++, "Location / position", locationField);
        UiSupport.addRow(form, row++, "Description", new JScrollPane(descriptionArea));
        UiSupport.addRow(form, row++, "Immediate action taken", new JScrollPane(immediateActionArea));
        if (followUpArea != null) {
            UiSupport.addRow(form, row++, "Follow-up required", new JScrollPane(followUpArea));
        }
        if (possibleDuplicateCheck != null) {
            UiSupport.addRow(form, row++, "", possibleDuplicateCheck);
        }

        JScrollPane scrollPane = new JScrollPane(form);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        int dialogHeight = 340 + (followUpArea != null ? 60 : 0) + (possibleDuplicateCheck != null ? 30 : 0);
        if (!UiSupport.showResizableConfirmDialog(this, "Capture clue details", scrollPane, new Dimension(640, dialogHeight))) {
            return;
        }

        // Resolve detecting task from dialog.
        String detectingTask;
        if (isResourceLog) {
            detectingTask = nullSafe(currentForm.getName());
        } else if (detectingResourceCombo != null && detectingResourceCombo.getSelectedItem() != null) {
            detectingTask = detectingResourceCombo.getSelectedItem().toString();
        } else {
            detectingTask = "";
        }

        ClueLogEntry clue = new ClueLogEntry();
        clue.setDateTimeCollected(sourceEntry.getTimestamp());
        clue.setDetectingTask(detectingTask);
        clue.setLocation(locationField.getText().trim());
        clue.setDescription(descriptionArea.getText().trim());
        clue.setImmediateAction(immediateActionArea.getText().trim());
        clue.setFollowUp(followUpArea != null ? followUpArea.getText().trim() : "");
        clue.setPossibleDuplicate(possibleDuplicateCheck != null && possibleDuplicateCheck.isSelected());
        String taskId = currentForm.getLinkedSarTaskAssignmentId();
        if (taskId != null && !taskId.isBlank()) {
            clue.setAssignmentId(taskId);
        }
        currentData.getClueLogEntries().add(clue);
    }

    /**
     * Returns the names of all ICS 214 forms in the document that are linked to a SAR task
     * assignment (i.e. resource-level 214 forms).  Used to populate the detecting-resource
     * picklist in the management-214 clue capture dialog.
     */
    private List<String> taskLinkedFormNames() {
        if (currentData == null) {
            return List.of();
        }
        return currentData.getActivityLogs().stream()
                .filter(f -> f.getLogScope() == ActivityLogScope.TASK_ASSIGNMENT)
                .map(Ics214Form::getName)
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .collect(Collectors.toList());
    }

    private void removeSelectedActivityEntry(int row) {
        if (currentForm == null || row < 0 || row >= currentForm.getActivityLog().size()) {
            return;
        }
        currentForm.getActivityLog().remove(row);
        activityLogTableModel.setRows(currentForm.getActivityLog(), resolvedEventTypes());
        controller.markDirty();
    }

    private JPanel activityButtonsPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton add = new JButton("Add Entry");
        JButton remove = new JButton("Remove Entry");
        add.addActionListener(event -> addActivityEntry());
        remove.addActionListener(event -> removeSelectedActivityEntry(activityLogTable.getSelectedRow()));
        panel.add(add);
        panel.add(remove);
        return panel;
    }

    /**
     * Item 6: Shows a non-editable view of the T-card linked to the selected resource row.
     * Looks up the T-card by person name from the controller's current T-card list.
     */
    private void showTCardForSelectedResource() {
        int row = resourcesTable.getSelectedRow();
        if (row < 0 || currentData == null) {
            return;
        }
        int modelRow = resourcesTable.convertRowIndexToModel(row);
        SarTaskResource resource = resourcesTableModel.getRows().get(modelRow);
        String name = resource.getName().isBlank() ? resource.getIcsPosition() : resource.getName();
        TCard tcard = currentData.getTCards().stream()
                .filter(c -> !c.getPersonName().isBlank() && c.getPersonName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
        if (tcard == null) {
            JOptionPane.showMessageDialog(this,
                    "No T-card record found for: " + name,
                    "T-Card", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        JPanel form = UiSupport.formPanel();
        int r = 0;
        UiSupport.addRow(form, r++, "Card type",         new JLabel(tcard.getCardType().getLabel()));
        UiSupport.addRow(form, r++, "Name",               new JLabel(tcard.getPersonName()));
        UiSupport.addRow(form, r++, "Home agency",        new JLabel(tcard.getHomeAgency()));
        UiSupport.addRow(form, r++, "Home state",         new JLabel(tcard.getHomeState()));
        UiSupport.addRow(form, r++, "Phone",              new JLabel(tcard.getPhoneNumber()));
        UiSupport.addRow(form, r++, "Radio channel",      new JLabel(tcard.getRadioChannel()));
        UiSupport.addRow(form, r++, "Resource identifier",new JLabel(tcard.getResourceIdentifier()));
        UiSupport.addRow(form, r++, "Location",           new JLabel(tcard.getLocation()));
        UiSupport.addRow(form, r++, "Status",             new JLabel(tcard.getStatus()));
        UiSupport.addRow(form, r,   "Notes",              new JLabel(tcard.getNotes()));
        JScrollPane scroll = new JScrollPane(form);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        JOptionPane.showMessageDialog(this, scroll, "T-Card: " + tcard.getPersonName(),
                JOptionPane.PLAIN_MESSAGE);
    }

    /**
     * Item 7: Populates the resources list of the current ICP-scoped form from T-cards
     * placed at the ICP and from resources on ICS 204 assignment forms.
     * Shows a confirmation dialog listing the proposed resources before adding.
     */
    private void refreshResourcesFromIcp() {
        if (currentForm == null || currentData == null) {
            return;
        }
        List<SarTaskResource> toAdd = new ArrayList<>();
        // T-cards at ICP.
        for (TCard card : currentData.getTCards()) {
            if (card.getCardType() == org.sarmanagement.icsforms.model.TCardType.HEADER) {
                continue;
            }
            String loc = card.getLocation() == null ? "" : card.getLocation();
            if ("ICP".equalsIgnoreCase(loc) && !card.getPersonName().isBlank()) {
                SarTaskResource r = new SarTaskResource();
                r.setName(card.getPersonName());
                r.setHomeAgency(card.getHomeAgency());
                toAdd.add(r);
            }
        }
        // Resources from 204 forms.
        for (ResourceAssignment ra : currentData.getForm204().getResourcesAssigned()) {
            if (!ra.getLeader().isBlank()) {
                SarTaskResource r = new SarTaskResource();
                r.setName(ra.getLeader());
                r.setIcsPosition(ra.getLeaderRole());
                toAdd.add(r);
            }
        }
        if (toAdd.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "No ICP resources or 204 assignments found to add.",
                    "Refresh Resources", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        // Remove duplicates against existing list.
        java.util.Set<String> existing = new java.util.HashSet<>();
        for (SarTaskResource r : resourcesTableModel.getRows()) {
            String n = r.getName().isBlank() ? r.getIcsPosition() : r.getName();
            existing.add(n.trim().toLowerCase());
        }
        toAdd.removeIf(r -> {
            String n = r.getName().isBlank() ? r.getIcsPosition() : r.getName();
            return existing.contains(n.trim().toLowerCase());
        });
        if (toAdd.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "All ICP resources are already in the list.",
                    "Refresh Resources", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String summary = toAdd.stream()
                .map(r -> "  • " + (r.getName().isBlank() ? r.getIcsPosition() : r.getName()))
                .collect(Collectors.joining("\n"));
        int choice = JOptionPane.showConfirmDialog(this,
                "Add the following resources to this form?\n" + summary,
                "Refresh from ICP & 204", JOptionPane.OK_CANCEL_OPTION);
        if (choice != JOptionPane.OK_OPTION) {
            return;
        }
        List<SarTaskResource> merged = new ArrayList<>(resourcesTableModel.getRows());
        merged.addAll(toAdd);
        resourcesTableModel.setRows(merged);
        if (currentForm != null) {
            currentForm.setResourcesAssigned(merged);
        }
        controller.markDirty();
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

        private ActivityEntryEditor(List<ActivityEventType> eventTypes, String defaultResourceIdentifier) {
            ActivityEventType[] typeArray = eventTypes.toArray(new ActivityEventType[0]);
            eventTypeField = new JComboBox<>(typeArray);
            // Select the free-text / Note type by default.
            for (ActivityEventType t : typeArray) {
                if (ActivityEventType.ID_FREE_TEXT.equals(t.getId())) {
                    eventTypeField.setSelectedItem(t);
                    break;
                }
            }
            if (defaultResourceIdentifier != null && !defaultResourceIdentifier.isBlank()) {
                resourceIdentifierField.setText(defaultResourceIdentifier);
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
    // Event type manager dialog (invoked from Configuration menu via MainFrame)
    // -------------------------------------------------------------------------

    static class EventTypeManagerDialog {
        final JPanel panel = new JPanel(new BorderLayout(4, 4));
        private final EventTypesTableModel tableModel;
        private final JTable table;

        EventTypeManagerDialog(List<ActivityEventType> initial) {
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

        List<ActivityEventType> getEventTypes() {
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
                // Show resource identifier (function) when name is blank (e.g. canine resources).
                case 0 -> r.getName().isBlank() ? r.getFunction() : r.getName();
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
