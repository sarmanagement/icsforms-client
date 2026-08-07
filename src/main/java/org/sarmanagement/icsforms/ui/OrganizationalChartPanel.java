package org.sarmanagement.icsforms.ui;

import org.sarmanagement.icsforms.model.OrganizationalChart;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared editor for organizational chart roles linked across forms.
 */
public class OrganizationalChartPanel extends JPanel {
    private final AppController controller;
    private final JTextArea incidentCommanderArea = UiSupport.textArea(4);
    private final JTextField operationsSectionChiefField = UiSupport.textField();

    /**
     * Creates the organizational chart editor.
     *
     * @param controller application controller.
     */
    public OrganizationalChartPanel(AppController controller) {
        super(new BorderLayout());
        this.controller = controller;
        JPanel form = UiSupport.formPanel();
        form.setBorder(BorderFactory.createTitledBorder("Organizational Chart"));
        UiSupport.addRow(form, 0, "Incident commander / unified command (one per line)", new JScrollPane(incidentCommanderArea));
        UiSupport.addRow(form, 1, "Operations section chief", operationsSectionChiefField);
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
        OrganizationalChart chart = controller.getData().getOrganizationalChart();
        incidentCommanderArea.setText(String.join("\n", chart.getIncidentCommanders()));
        operationsSectionChiefField.setText(nullSafe(chart.getOperationsSectionChiefName()));
    }

    /**
     * Applies field values to the model.
     */
    public void pushToModel() {
        OrganizationalChart chart = controller.getData().getOrganizationalChart();
        chart.setIncidentCommanders(lines(incidentCommanderArea.getText()));
        chart.setOperationsSectionChiefName(operationsSectionChiefField.getText().trim());
    }

    private List<String> lines(String value) {
        List<String> items = new ArrayList<>();
        for (String line : value.split("\\R")) {
            if (!line.isBlank()) {
                items.add(line.trim());
            }
        }
        return items;
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
