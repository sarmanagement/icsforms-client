package org.sarmanagement.icsforms.ui;

import org.sarmanagement.icsforms.model.IncidentContext;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JComboBox;
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
    private static final String[] POSITION_PRESETS = {
            "", "IC", "Planning Section Chief", "Documentation Unit Leader",
            "Logistics Section Chief", "Operations Section Chief"
    };

    private final AppController controller;
    private final JTextField incidentNameField = UiSupport.textField();
    private final JSpinner startField = UiSupport.dateTimeSpinner();
    private final JSpinner endField = UiSupport.dateTimeSpinner();
    private final JTextField taskMapField = UiSupport.textField();
    private final JTextField currentUserField = UiSupport.textField();
    private final JComboBox<String> currentUserPositionCombo = new JComboBox<>(POSITION_PRESETS);

    /**
     * Creates the shared incident context editor.
     *
     * @param controller application controller.
     */
    public IncidentContextPanel(AppController controller) {
        super(new BorderLayout());
        this.controller = controller;
        currentUserPositionCombo.setEditable(true);
        JPanel form = UiSupport.formPanel();
        form.setBorder(BorderFactory.createTitledBorder("Shared Incident Context"));
        UiSupport.addRow(form, 0, "Incident name", incidentNameField);
        UiSupport.addRow(form, 1, "Operational period start", startField);
        UiSupport.addRow(form, 2, "Operational period end", endField);
        UiSupport.addRow(form, 3, "Task map / CalTopo id", taskMapField);
        UiSupport.addRow(form, 4, "Preparer / current user", currentUserField);
        UiSupport.addRow(form, 5, "Preparer position/title", currentUserPositionCombo);
        JPanel topAlignedForm = new JPanel(new BorderLayout());
        topAlignedForm.setOpaque(false);
        topAlignedForm.add(form, BorderLayout.NORTH);
        topAlignedForm.add(Box.createVerticalGlue(), BorderLayout.CENTER);
        JScrollPane scrollPane = new JScrollPane(topAlignedForm);
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
