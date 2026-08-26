package org.sarmanagement.icsforms.ui;

import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.IncidentContext;
import org.sarmanagement.icsforms.model.TCard;
import org.sarmanagement.icsforms.model.TCardType;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;

/**
 * Global editor for incident metadata shared by ICS 202, ICS 204, and SAR scaffolding.
 *
 * <p>In addition to the editable fields, this panel shows read-only status information:
 * the workspace file path, the current IAP phase, and a table of past operational periods
 * (each with start/end date-times and the incident commander names recorded at that time).</p>
 */
public class IncidentContextPanel extends JPanel {
    private static final String[] POSITION_PRESETS = {
            "", "Incident Commander", "Unified Command",
            "Safety Officer", "Operations Section Chief",
            "Planning Section Chief", "Logistics Section Chief",
            "Finance / Admin Section Chief", "Documentation Unit Leader"
    };
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final AppController controller;

    // Read-only status labels (refreshed from model)
    private final JLabel filePathLabel = new JLabel(" ");
    private final JLabel phaseLabel = new JLabel(" ");

    // Editable fields
    private final JTextField incidentNameField = UiSupport.textField();
    private final JSpinner startField = UiSupport.dateTimeSpinner();
    private final JSpinner endField = UiSupport.dateTimeSpinner();
    private final JTextField taskMapField = UiSupport.textField();
    private final JTextField currentUserField = UiSupport.textField();
    private final JComboBox<String> currentUserPositionCombo = new JComboBox<>(POSITION_PRESETS);

    // Operational period history table
    private final DefaultTableModel historyTableModel = new DefaultTableModel(
            new String[]{"#", "Period Start", "Period End", "Incident Commander(s)"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable historyTable = new JTable(historyTableModel);

    /**
     * Creates the shared incident context editor.
     *
     * @param controller application controller.
     */
    public IncidentContextPanel(AppController controller) {
        super(new BorderLayout());
        this.controller = controller;
        currentUserPositionCombo.setEditable(true);

        // --- Status section (read-only) ---
        JPanel statusForm = UiSupport.formPanel();
        statusForm.setBorder(BorderFactory.createTitledBorder("Incident Status (read-only)"));
        UiSupport.addRow(statusForm, 0, "Workspace file", filePathLabel);
        UiSupport.addRow(statusForm, 1, "Current phase", phaseLabel);

        // --- Editable context section ---
        JPanel editForm = UiSupport.formPanel();
        editForm.setBorder(BorderFactory.createTitledBorder("Shared Incident Context"));
        UiSupport.addRow(editForm, 0, "Incident name", incidentNameField);
        UiSupport.addRow(editForm, 1, "Operational period start", startField);
        UiSupport.addRow(editForm, 2, "Operational period end", endField);
        UiSupport.addRow(editForm, 3, "Task map / CalTopo id", taskMapField);

        // Preparer / current user — plain text entry with an optional person picker.
        JPanel userRow = new JPanel(new BorderLayout(4, 0));
        userRow.setOpaque(false);
        userRow.add(currentUserField, BorderLayout.CENTER);
        JButton pickUserButton = new JButton("Pick…");
        pickUserButton.setToolTipText("Select from known personnel (T-cards)");
        pickUserButton.addActionListener(e -> pickCurrentUser());
        userRow.add(pickUserButton, BorderLayout.EAST);
        UiSupport.addRow(editForm, 4, "Preparer / current user", userRow);
        UiSupport.addRow(editForm, 5, "Preparer position/title", currentUserPositionCombo);

        // --- Operational period history table ---
        JPanel historyPanel = new JPanel(new BorderLayout());
        historyPanel.setBorder(BorderFactory.createTitledBorder("Operational Period History"));
        historyTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        historyTable.setFillsViewportHeight(false);
        historyPanel.add(new JScrollPane(historyTable), BorderLayout.CENTER);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);
        content.add(statusForm);
        content.add(Box.createVerticalStrut(6));
        content.add(editForm);
        content.add(Box.createVerticalStrut(6));
        content.add(historyPanel);

        JPanel topAligned = new JPanel(new BorderLayout());
        topAligned.setOpaque(false);
        topAligned.add(content, BorderLayout.NORTH);
        JScrollPane scrollPane = new JScrollPane(topAligned);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        add(scrollPane, BorderLayout.CENTER);
    }

    /**
     * Loads field values from the model.
     */
    public void refreshFromModel() {
        IncidentContext context = controller.getData().getIncidentContext();
        incidentNameField.setText(nullSafe(context.getIncidentName()));
        startField.setValue(toDate(context.getOperationalPeriodStart()));
        endField.setValue(toDate(context.getOperationalPeriodEnd()));
        taskMapField.setText(nullSafe(context.getTaskMap()));
        currentUserField.setText(nullSafe(context.getCurrentUser()));
        currentUserPositionCombo.setSelectedItem(nullSafe(context.getCurrentUserPositionTitle()));

        // Refresh read-only status labels.
        filePathLabel.setText(controller.getFilePath().toString());
        phaseLabel.setText(phaseDisplayName(controller.getData().getIapPhase()));

        // Rebuild the period history table.
        historyTableModel.setRowCount(0);
        List<AppData.OperationalPeriodRecord> history = controller.getData().getOperationalPeriodHistory();
        for (int i = 0; i < history.size(); i++) {
            AppData.OperationalPeriodRecord rec = history.get(i);
            historyTableModel.addRow(new Object[]{
                    i + 1,
                    formatDateTime(rec.getPeriodStart()),
                    formatDateTime(rec.getPeriodEnd()),
                    String.join("; ", rec.getIncidentCommanders())
            });
        }
        // The "current" period always appears as the last row (in progress).
        List<String> currentIcs = controller.getData().getOrganizationalChart().getIncidentCommanders();
        if (currentIcs == null) {
            currentIcs = List.of();
        }
        historyTableModel.addRow(new Object[]{
                history.size() + 1,
                formatDateTime(context.getOperationalPeriodStart()),
                formatDateTime(context.getOperationalPeriodEnd()),
                currentIcs.isEmpty() ? "(current)" : String.join("; ", currentIcs) + " (current)"
        });
    }

    /**
     * Shows a picker dialog listing all PERSONNEL T-cards so the operator can
     * select the current user/preparer rather than typing the name manually.
     * The text field remains editable so a name not in the list can still be entered.
     */
    private void pickCurrentUser() {
        List<TCard> cards = controller.getData().getTCards();
        if (cards == null || cards.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "No T-card resources found. Enter the name manually.",
                    "Pick Person", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        List<TCard> personnel = cards.stream()
                .filter(c -> c.getCardType() == TCardType.PERSONNEL && !c.getPersonName().isBlank())
                .collect(java.util.stream.Collectors.toList());
        if (personnel.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "No personnel T-cards found. Enter the name manually.",
                    "Pick Person", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        TCard[] cardArray = personnel.toArray(new TCard[0]);
        JComboBox<TCard> combo = new JComboBox<>(cardArray);
        javax.swing.DefaultListCellRenderer renderer = new javax.swing.DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(
                    javax.swing.JList<?> list, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                setText(value instanceof TCard tc ? personDisplayName(tc) : "");
                return this;
            }
        };
        combo.setRenderer(renderer);
        // Pre-select the card whose name matches the current field value, if any.
        String existing = currentUserField.getText().trim();
        if (!existing.isBlank()) {
            personnel.stream()
                    .filter(c -> existing.equalsIgnoreCase(c.getPersonName()))
                    .findFirst()
                    .ifPresent(combo::setSelectedItem);
        }
        int result = JOptionPane.showConfirmDialog(this, combo,
                "Select preparer / current user", JOptionPane.OK_CANCEL_OPTION);
        if (result != JOptionPane.OK_OPTION) {
            return;
        }
        TCard selected = (TCard) combo.getSelectedItem();
        if (selected == null) {
            return;
        }
        currentUserField.setText(selected.getPersonName());
        controller.markDirty();
    }

    private static String personDisplayName(TCard card) {
        if (card == null) return "";
        String agency = card.getHomeAgency();
        if (agency != null && !agency.isBlank()) {
            return card.getPersonName() + " (" + agency + ")";
        }
        return card.getPersonName();
    }

    /**
     * Applies field values to the model.
     */
    public void pushToModel() {
        IncidentContext context = controller.getData().getIncidentContext();
        context.setIncidentName(incidentNameField.getText().trim());
        context.setOperationalPeriodStart(AppController.toLocalDateTime((Date) startField.getValue()));
        context.setOperationalPeriodEnd(AppController.toLocalDateTime((Date) endField.getValue()));
        context.setTaskMap(taskMapField.getText().trim());
        context.setCurrentUser(currentUserField.getText().trim());
        Object posVal = currentUserPositionCombo.getSelectedItem();
        context.setCurrentUserPositionTitle(posVal == null ? "" : posVal.toString().trim());
    }

    private Date toDate(java.time.LocalDateTime value) {
        return AppController.toDate(value);
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? "" : value.format(DT_FMT);
    }

    private String phaseDisplayName(org.sarmanagement.icsforms.model.IapPhase phase) {
        if (phase == null) return "Pre-Operational";
        return switch (phase) {
            case PRE_OP -> "Pre-Operational (planning)";
            case INITIAL_RESPONSE -> "Initial Incident Response (ICS 201 active)";
            case DURING_OP -> "Subsequent Operational Period (ICS 201 read-only)";
        };
    }

    /**
     * Converts null strings to empty text for display.
     *
     * @param value input value.
     * @return display-safe text.
     */
    private String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
