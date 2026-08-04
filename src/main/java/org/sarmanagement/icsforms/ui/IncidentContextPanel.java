package org.sarmanagement.icsforms.ui;

import org.sarmanagement.icsforms.model.IncidentContext;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.util.Date;

/**
 * Global editor for incident metadata shared by ICS 202, ICS 204, and SAR scaffolding.
 */
public class IncidentContextPanel extends JPanel {
    private final AppController controller;
    private final JTextField incidentNameField = UiSupport.textField();
    private final JSpinner startField = UiSupport.dateTimeSpinner();
    private final JSpinner endField = UiSupport.dateTimeSpinner();
    private final JTextField currentUserField = UiSupport.textField();
    private final JTextField currentUserPositionField = UiSupport.textField();

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
        UiSupport.addRow(form, 1, "Operational period start", startField);
        UiSupport.addRow(form, 2, "Operational period end", endField);
        UiSupport.addRow(form, 3, "Preparer / current user", currentUserField);
        UiSupport.addRow(form, 4, "Preparer position/title", currentUserPositionField);
        add(new JScrollPane(form), BorderLayout.CENTER);
    }

    /**
     * Loads field values from the model.
     */
    public void refreshFromModel() {
        IncidentContext context = controller.getData().getIncidentContext();
        incidentNameField.setText(nullSafe(context.getIncidentName()));
        startField.setValue(toDate(context.getOperationalPeriodStart()));
        endField.setValue(toDate(context.getOperationalPeriodEnd()));
        currentUserField.setText(nullSafe(context.getCurrentUser()));
        currentUserPositionField.setText(nullSafe(context.getCurrentUserPositionTitle()));
    }

    /**
     * Applies field values to the model.
     */
    public void pushToModel() {
        IncidentContext context = controller.getData().getIncidentContext();
        context.setIncidentName(incidentNameField.getText().trim());
        context.setOperationalPeriodStart(AppController.toLocalDateTime((Date) startField.getValue()));
        context.setOperationalPeriodEnd(AppController.toLocalDateTime((Date) endField.getValue()));
        context.setCurrentUser(currentUserField.getText().trim());
        context.setCurrentUserPositionTitle(currentUserPositionField.getText().trim());
    }

    private Date toDate(java.time.LocalDateTime value) {
        return AppController.toDate(value);
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
