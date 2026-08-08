package org.sarmanagement.icsforms.ui;

import org.sarmanagement.icsforms.model.Ics202Form;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JCheckBox;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;

/**
 * First-cut editor for ICS 202 Incident Objectives content.
 */
public class Ics202Panel extends JPanel {
    private final AppController controller;
    private final JTextArea objectivesArea = UiSupport.textArea(5);
    private final JTextArea commandEmphasisArea = UiSupport.textArea(3);
    private final JTextArea situationalAwarenessArea = UiSupport.textArea(3);
    private final JCheckBox siteSafetyPlanRequired = new JCheckBox("Site safety plan required");
    private final JCheckBox includeIcs202 = new JCheckBox("ICS 202");
    private final JCheckBox includeIcs204 = new JCheckBox("ICS 204");
    private final JCheckBox includeSarTaskAssignment = new JCheckBox("SAR Task Assignment");
    private final JCheckBox includeMapPacket = new JCheckBox("Map packet");
    private final JTextArea additionalFormsArea = UiSupport.textArea(2);
    private final JTextField preparedByNameField = UiSupport.textField();
    private final JTextField preparedByPositionField = UiSupport.textField();
    private final JTextField approvedByNameField = UiSupport.textField();
    private final javax.swing.JSpinner approvedDateTimeField = UiSupport.dateTimeSpinner();
    private final JTextField iapPageField = UiSupport.textField();

    /**
     * Creates the ICS 202 editor panel.
     *
     * @param controller application controller.
     */
    public Ics202Panel(AppController controller) {
        super(new BorderLayout());
        this.controller = controller;
        JPanel form = UiSupport.formPanel();
        form.setBorder(BorderFactory.createTitledBorder("ICS 202 Incident Objectives"));
        UiSupport.addRow(form, 0, "Objectives (one per line)", new JScrollPane(objectivesArea));
        UiSupport.addRow(form, 1, "Command emphasis", new JScrollPane(commandEmphasisArea));
        UiSupport.addRow(form, 2, "General situational awareness", new JScrollPane(situationalAwarenessArea));
        UiSupport.addRow(form, 3, "Safety plan", siteSafetyPlanRequired);
        UiSupport.addRow(form, 4, "Included forms / attachments", includedFormsPanel());
        UiSupport.addRow(form, 5, "Prepared by name", preparedByNameField);
        UiSupport.addRow(form, 6, "Prepared by position/title", preparedByPositionField);
        preparedByNameField.setEditable(false);
        preparedByPositionField.setEditable(false);
        UiSupport.addRow(form, 7, "Incident commander", approvedByNameField);
        UiSupport.addRow(form, 8, "IC Approval date/time", approvedDateTimeField);
        UiSupport.addRow(form, 9, "IAP page", iapPageField);
        JScrollPane scrollPane = new JScrollPane(form);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        add(scrollPane, BorderLayout.CENTER);
    }

    /** Loads values from the model. */
    public void refreshFromModel() {
        Ics202Form form = controller.getData().getForm202();
        objectivesArea.setText(String.join("\n", form.getObjectives()));
        commandEmphasisArea.setText(nullSafe(form.getCommandEmphasis()));
        situationalAwarenessArea.setText(nullSafe(form.getSituationalAwareness()));
        siteSafetyPlanRequired.setSelected(form.isSiteSafetyPlanRequired());
        setIncludedForms(form.getIncidentActionPlanAttachments());
        preparedByNameField.setText(nullSafe(form.getPreparedByName()));
        preparedByPositionField.setText(nullSafe(form.getPreparedByPositionTitle()));
        approvedByNameField.setText(nullSafe(form.getApprovedByIncidentCommanderName()));
        approvedDateTimeField.setValue(AppController.toDate(form.getApprovedDateTime()));
        iapPageField.setText(nullSafe(form.getIapPage()));
    }

    /** Applies field values to the model. */
    public void pushToModel() {
        Ics202Form form = controller.getData().getForm202();
        form.setObjectives(lines(objectivesArea.getText()));
        form.setCommandEmphasis(commandEmphasisArea.getText().trim());
        form.setSituationalAwareness(situationalAwarenessArea.getText().trim());
        form.setSiteSafetyPlanRequired(siteSafetyPlanRequired.isSelected());
        form.setIncidentActionPlanAttachments(getIncludedForms());
        form.setApprovedByIncidentCommanderName(approvedByNameField.getText().trim());
        form.setPreparedByName(preparedByNameField.getText().trim());
        form.setPreparedByPositionTitle(preparedByPositionField.getText().trim());
        form.setApprovedDateTime(AppController.toLocalDateTime((java.util.Date) approvedDateTimeField.getValue()));
        form.setIapPage(iapPageField.getText().trim());
    }

    private JPanel includedFormsPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setOpaque(false);
        JPanel checks = new JPanel(new GridLayout(0, 2, 4, 4));
        checks.setOpaque(false);
        checks.add(includeIcs202);
        checks.add(includeIcs204);
        checks.add(includeSarTaskAssignment);
        checks.add(includeMapPacket);
        panel.add(checks, BorderLayout.NORTH);
        JScrollPane scrollPane = new JScrollPane(additionalFormsArea);
        scrollPane.setPreferredSize(new java.awt.Dimension(100, 60));
        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    private void setIncludedForms(List<String> items) {
        includeIcs202.setSelected(false);
        includeIcs204.setSelected(false);
        includeSarTaskAssignment.setSelected(false);
        includeMapPacket.setSelected(false);
        List<String> additional = new ArrayList<>();
        for (String item : items) {
            if ("ICS 202".equalsIgnoreCase(item)) {
                includeIcs202.setSelected(true);
            } else if ("ICS 204".equalsIgnoreCase(item)) {
                includeIcs204.setSelected(true);
            } else if ("SAR Task Assignment".equalsIgnoreCase(item)) {
                includeSarTaskAssignment.setSelected(true);
            } else if ("Map packet".equalsIgnoreCase(item)) {
                includeMapPacket.setSelected(true);
            } else {
                additional.add(item);
            }
        }
        additionalFormsArea.setText(String.join("\n", additional));
    }

    private List<String> getIncludedForms() {
        List<String> items = new ArrayList<>();
        if (includeIcs202.isSelected()) {
            items.add("ICS 202");
        }
        if (includeIcs204.isSelected()) {
            items.add("ICS 204");
        }
        if (includeSarTaskAssignment.isSelected()) {
            items.add("SAR Task Assignment");
        }
        if (includeMapPacket.isSelected()) {
            items.add("Map packet");
        }
        items.addAll(lines(additionalFormsArea.getText()));
        return items;
    }

    /**
     * Splits textarea input into non-empty lines.
     *
     * @param value textarea content.
     * @return non-empty lines.
     */
    private List<String> lines(String value) {
        List<String> items = new ArrayList<>();
        for (String line : value.split("\\R")) {
            if (!line.isBlank()) {
                items.add(line.trim());
            }
        }
        return items;
    }

    /**
     * Converts null text to empty text.
     *
     * @param value input value.
     * @return safe text.
     */
    private String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
