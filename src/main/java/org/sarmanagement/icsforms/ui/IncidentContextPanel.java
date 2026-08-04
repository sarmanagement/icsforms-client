package org.sarmanagement.icsforms.ui;

import org.sarmanagement.icsforms.model.IncidentContext;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Global editor for incident metadata shared by ICS 202, ICS 204, and SAR scaffolding.
 */
public class IncidentContextPanel extends JPanel {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final AppController controller;
    private final JTextField incidentNameField = UiSupport.textField();
    private final JTextField startField = UiSupport.textField();
    private final JTextField endField = UiSupport.textField();
    private final JTextField currentUserField = UiSupport.textField();

    /**
     * Creates the shared incident context editor.
     *
     * @param controller application controller.
     */
    public IncidentContextPanel(AppController controller) {
        super(new BorderLayout());
        this.controller = controller;
        JPanel form = UiSupport.formPanel();
        form.setBorder(BorderFactory.createTitledBorder("Shared Incident Context"));
        UiSupport.addRow(form, 0, "Incident name", incidentNameField);
        UiSupport.addRow(form, 1, "Operational period start (yyyy-MM-dd HH:mm)", startField);
        UiSupport.addRow(form, 2, "Operational period end (yyyy-MM-dd HH:mm)", endField);
        UiSupport.addRow(form, 3, "Preparer / current user", currentUserField);
        add(new JScrollPane(form), BorderLayout.CENTER);
    }

    /**
     * Loads field values from the model.
     */
    public void refreshFromModel() {
        IncidentContext context = controller.getData().getIncidentContext();
        incidentNameField.setText(nullSafe(context.getIncidentName()));
        startField.setText(format(context.getOperationalPeriodStart()));
        endField.setText(format(context.getOperationalPeriodEnd()));
        currentUserField.setText(nullSafe(context.getCurrentUser()));
    }

    /**
     * Applies field values to the model.
     */
    public void pushToModel() {
        IncidentContext context = controller.getData().getIncidentContext();
        context.setIncidentName(incidentNameField.getText().trim());
        context.setOperationalPeriodStart(parse(startField.getText()));
        context.setOperationalPeriodEnd(parse(endField.getText()));
        context.setCurrentUser(currentUserField.getText().trim());
    }

    /**
     * Formats date/time values for display.
     *
     * @param value date/time value.
     * @return formatted text.
     */
    private String format(LocalDateTime value) {
        return value == null ? "" : FORMATTER.format(value);
    }

    /**
     * Parses date/time input entered by the user.
     *
     * @param value raw field value.
     * @return parsed date/time or {@code null} if blank/invalid.
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
     * Converts null strings to empty text for display.
     *
     * @param value input value.
     * @return display-safe text.
     */
    private String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
