package org.sarmanagement.icsforms.ui;

import org.sarmanagement.icsforms.model.Ics202Form;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * First-cut editor for ICS 202 Incident Objectives content.
 */
public class Ics202Panel extends JPanel {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final AppController controller;
    private final JTextArea objectivesArea = UiSupport.textArea(5);
    private final JTextArea commandEmphasisArea = UiSupport.textArea(3);
    private final JTextArea situationalAwarenessArea = UiSupport.textArea(3);
    private final JCheckBox siteSafetyPlanRequired = new JCheckBox("Site safety plan required");
    private final JTextArea attachmentsArea = UiSupport.textArea(3);
    private final JTextField preparedByNameField = UiSupport.textField();
    private final JTextField preparedByPositionField = UiSupport.textField();
    private final JTextField preparedBySignatureField = UiSupport.textField();
    private final JTextField approvedByNameField = UiSupport.textField();
    private final JTextField approvedBySignatureField = UiSupport.textField();
    private final JTextField approvedDateTimeField = UiSupport.textField();
    private final JTextField formNumberField = UiSupport.textField();
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
        UiSupport.addRow(form, 4, "Included forms / attachments (one per line)", new JScrollPane(attachmentsArea));
        UiSupport.addRow(form, 5, "Prepared by name", preparedByNameField);
        UiSupport.addRow(form, 6, "Prepared by position/title", preparedByPositionField);
        UiSupport.addRow(form, 7, "Prepared by signature", preparedBySignatureField);
        UiSupport.addRow(form, 8, "Approved by incident commander", approvedByNameField);
        UiSupport.addRow(form, 9, "Incident commander signature", approvedBySignatureField);
        UiSupport.addRow(form, 10, "Approval date/time (yyyy-MM-dd HH:mm)", approvedDateTimeField);
        UiSupport.addRow(form, 11, "Form number", formNumberField);
        UiSupport.addRow(form, 12, "IAP page", iapPageField);
        add(new JScrollPane(form), BorderLayout.CENTER);
    }

    /** Loads values from the model. */
    public void refreshFromModel() {
        Ics202Form form = controller.getData().getForm202();
        objectivesArea.setText(String.join("\n", form.getObjectives()));
        commandEmphasisArea.setText(nullSafe(form.getCommandEmphasis()));
        situationalAwarenessArea.setText(nullSafe(form.getSituationalAwareness()));
        siteSafetyPlanRequired.setSelected(form.isSiteSafetyPlanRequired());
        attachmentsArea.setText(String.join("\n", form.getIncidentActionPlanAttachments()));
        preparedByNameField.setText(nullSafe(form.getPreparedByName()));
        preparedByPositionField.setText(nullSafe(form.getPreparedByPositionTitle()));
        preparedBySignatureField.setText(nullSafe(form.getPreparedBySignature()));
        approvedByNameField.setText(nullSafe(form.getApprovedByIncidentCommanderName()));
        approvedBySignatureField.setText(nullSafe(form.getApprovedBySignature()));
        approvedDateTimeField.setText(format(form.getApprovedDateTime()));
        formNumberField.setText(nullSafe(form.getFormNumber()));
        iapPageField.setText(nullSafe(form.getIapPage()));
    }

    /** Applies field values to the model. */
    public void pushToModel() {
        Ics202Form form = controller.getData().getForm202();
        form.setObjectives(lines(objectivesArea.getText()));
        form.setCommandEmphasis(commandEmphasisArea.getText().trim());
        form.setSituationalAwareness(situationalAwarenessArea.getText().trim());
        form.setSiteSafetyPlanRequired(siteSafetyPlanRequired.isSelected());
        form.setIncidentActionPlanAttachments(lines(attachmentsArea.getText()));
        form.setPreparedByName(preparedByNameField.getText().trim());
        form.setPreparedByPositionTitle(preparedByPositionField.getText().trim());
        form.setPreparedBySignature(preparedBySignatureField.getText().trim());
        form.setApprovedByIncidentCommanderName(approvedByNameField.getText().trim());
        form.setApprovedBySignature(approvedBySignatureField.getText().trim());
        form.setApprovedDateTime(parse(approvedDateTimeField.getText()));
        form.setFormNumber(formNumberField.getText().trim());
        form.setIapPage(iapPageField.getText().trim());
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
     * Parses date/time field input.
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
     * Formats date/time for display.
     *
     * @param value date/time.
     * @return formatted text.
     */
    private String format(LocalDateTime value) {
        return value == null ? "" : FORMATTER.format(value);
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
