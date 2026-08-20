package org.sarmanagement.icsforms.ui;

import org.sarmanagement.icsforms.model.OrganizationalChart;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared editor for organizational chart roles linked across forms.
 *
 * <p>Each staff position shows a Name field and — visible only when the name is non-blank —
 * separate Radio and Phone fields.  Positions are grouped into: Incident Command, Command Staff,
 * General Staff, Planning Section Positions, and Logistics Section Positions.</p>
 */
public class OrganizationalChartPanel extends JPanel {
    private final AppController controller;
    private final JTextArea incidentCommanderArea = UiSupport.textArea(4);
    private final JTextField incidentCommanderRadioField = UiSupport.textField();
    private final JTextField incidentCommanderPhoneField = UiSupport.textField();

    // Command Staff
    private final JTextField safetyOfficerNameField = UiSupport.textField();
    private final JTextField safetyOfficerRadioField = UiSupport.textField();
    private final JTextField safetyOfficerPhoneField = UiSupport.textField();

    private final JTextField pioNameField = UiSupport.textField();
    private final JTextField pioRadioField = UiSupport.textField();
    private final JTextField pioPhoneField = UiSupport.textField();

    private final JTextField liaisonNameField = UiSupport.textField();
    private final JTextField liaisonRadioField = UiSupport.textField();
    private final JTextField liaisonPhoneField = UiSupport.textField();

    // General Staff
    private final JTextField operationsSectionChiefField = UiSupport.textField();
    private final JTextField operationsSectionChiefRadioField = UiSupport.textField();
    private final JTextField operationsSectionChiefPhoneField = UiSupport.textField();

    private final JTextField planningSectionChiefNameField = UiSupport.textField();
    private final JTextField planningSectionChiefRadioField = UiSupport.textField();
    private final JTextField planningSectionChiefPhoneField = UiSupport.textField();

    private final JTextField logisticsSectionChiefNameField = UiSupport.textField();
    private final JTextField logisticsSectionChiefRadioField = UiSupport.textField();
    private final JTextField logisticsSectionChiefPhoneField = UiSupport.textField();

    private final JTextField financeAdminSectionChiefNameField = UiSupport.textField();
    private final JTextField financeAdminSectionChiefRadioField = UiSupport.textField();
    private final JTextField financeAdminSectionChiefPhoneField = UiSupport.textField();

    // Planning Section
    private final JTextField documentationUnitLeaderNameField = UiSupport.textField();
    private final JTextField documentationUnitLeaderRadioField = UiSupport.textField();
    private final JTextField documentationUnitLeaderPhoneField = UiSupport.textField();

    // Logistics Section
    private final JTextField commUnitLeaderNameField = UiSupport.textField();
    private final JTextField commUnitLeaderRadioField = UiSupport.textField();
    private final JTextField commUnitLeaderPhoneField = UiSupport.textField();

    private final JTextField commTechNameField = UiSupport.textField();
    private final JTextField commTechRadioField = UiSupport.textField();
    private final JTextField commTechPhoneField = UiSupport.textField();

    /**
     * Creates the organizational chart editor.
     *
     * @param controller application controller.
     */
    public OrganizationalChartPanel(AppController controller) {
        super(new BorderLayout());
        this.controller = controller;

        JPanel icSection = UiSupport.formPanel();
        icSection.setBorder(BorderFactory.createTitledBorder("Incident Command"));
        UiSupport.addRow(icSection, 0, "Incident commander / unified command (one per line)", new JScrollPane(incidentCommanderArea));
        UiSupport.addRow(icSection, 1, "IC / UC radio", incidentCommanderRadioField);
        UiSupport.addRow(icSection, 2, "IC / UC phone", incidentCommanderPhoneField);

        JPanel commandSection = buildSection("Command Staff",
                new String[]{"Safety Officer", "PIO / Public Information Officer", "Liaison Officer"},
                new JTextField[]{safetyOfficerNameField, pioNameField, liaisonNameField},
                new JTextField[]{safetyOfficerRadioField, pioRadioField, liaisonRadioField},
                new JTextField[]{safetyOfficerPhoneField, pioPhoneField, liaisonPhoneField});

        JPanel generalSection = buildSection("General Staff",
                new String[]{"Operations Section Chief", "Planning Section Chief",
                        "Logistics Section Chief", "Finance / Admin Section Chief"},
                new JTextField[]{operationsSectionChiefField, planningSectionChiefNameField,
                        logisticsSectionChiefNameField, financeAdminSectionChiefNameField},
                new JTextField[]{operationsSectionChiefRadioField, planningSectionChiefRadioField,
                        logisticsSectionChiefRadioField, financeAdminSectionChiefRadioField},
                new JTextField[]{operationsSectionChiefPhoneField, planningSectionChiefPhoneField,
                        logisticsSectionChiefPhoneField, financeAdminSectionChiefPhoneField});

        JPanel planningSection = buildSection("Planning Section Positions",
                new String[]{"Documentation Unit Leader"},
                new JTextField[]{documentationUnitLeaderNameField},
                new JTextField[]{documentationUnitLeaderRadioField},
                new JTextField[]{documentationUnitLeaderPhoneField});

        JPanel logisticsSection = buildSection("Logistics Section Positions",
                new String[]{"Communications Unit Leader", "Communications Technician"},
                new JTextField[]{commUnitLeaderNameField, commTechNameField},
                new JTextField[]{commUnitLeaderRadioField, commTechRadioField},
                new JTextField[]{commUnitLeaderPhoneField, commTechPhoneField});

        // Install autocomplete on all name fields.
        installPersonAutocomplete(safetyOfficerNameField,          safetyOfficerRadioField,         safetyOfficerPhoneField);
        installPersonAutocomplete(pioNameField,                    pioRadioField,                   pioPhoneField);
        installPersonAutocomplete(liaisonNameField,                liaisonRadioField,               liaisonPhoneField);
        installPersonAutocomplete(operationsSectionChiefField,     operationsSectionChiefRadioField, operationsSectionChiefPhoneField);
        installPersonAutocomplete(planningSectionChiefNameField,   planningSectionChiefRadioField,  planningSectionChiefPhoneField);
        installPersonAutocomplete(logisticsSectionChiefNameField,  logisticsSectionChiefRadioField, logisticsSectionChiefPhoneField);
        installPersonAutocomplete(financeAdminSectionChiefNameField, financeAdminSectionChiefRadioField, financeAdminSectionChiefPhoneField);
        installPersonAutocomplete(documentationUnitLeaderNameField, documentationUnitLeaderRadioField, documentationUnitLeaderPhoneField);
        installPersonAutocomplete(commUnitLeaderNameField,         commUnitLeaderRadioField,        commUnitLeaderPhoneField);
        installPersonAutocomplete(commTechNameField,               commTechRadioField,              commTechPhoneField);

        JPanel all = new JPanel();
        all.setLayout(new BoxLayout(all, BoxLayout.Y_AXIS));
        all.setOpaque(false);
        all.add(icSection);
        all.add(Box.createVerticalStrut(8));
        all.add(commandSection);
        all.add(Box.createVerticalStrut(8));
        all.add(generalSection);
        all.add(Box.createVerticalStrut(8));
        all.add(planningSection);
        all.add(Box.createVerticalStrut(8));
        all.add(logisticsSection);

        JPanel topAligned = new JPanel(new BorderLayout());
        topAligned.setOpaque(false);
        topAligned.add(all, BorderLayout.NORTH);

        JScrollPane scrollPane = new JScrollPane(topAligned);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        add(scrollPane, BorderLayout.CENTER);
    }

    /**
     * Installs autocomplete on {@code nameField} and hides the radio/phone fields when the
     * name is blank.  When a known T-card name is selected the radio field is auto-filled
     * (preferred) or the phone field (fallback) if both are currently blank.
     */
    private void installPersonAutocomplete(JTextField nameField, JTextField radioField, JTextField phoneField) {
        // Show/hide contact fields based on whether the name is currently blank.
        updateContactVisibility(nameField, radioField, phoneField);
        nameField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e)  { updateContactVisibility(nameField, radioField, phoneField); }
            @Override public void removeUpdate(DocumentEvent e)  { updateContactVisibility(nameField, radioField, phoneField); }
            @Override public void changedUpdate(DocumentEvent e) { updateContactVisibility(nameField, radioField, phoneField); }
        });

        UiSupport.installNameAutocomplete(nameField,
                () -> controller.getPersonnelNames(),
                selectedName -> {
                    var card = controller.findPersonCard(selectedName);
                    if (card != null) {
                        if (radioField.getText().isBlank() && !card.getRadioChannel().isBlank()) {
                            radioField.setText(card.getRadioChannel());
                        }
                        if (phoneField.getText().isBlank() && !card.getPhoneNumber().isBlank()) {
                            phoneField.setText(card.getPhoneNumber());
                        }
                    }
                });
    }

    /** Shows contact (radio/phone) fields only when the name field is non-blank. */
    private static void updateContactVisibility(JTextField nameField, JTextField radioField, JTextField phoneField) {
        boolean visible = !nameField.getText().isBlank();
        radioField.setVisible(visible);
        phoneField.setVisible(visible);
        Component parent = radioField.getParent();
        if (parent != null) {
            parent.revalidate();
            parent.repaint();
        }
    }

    /**
     * Loads field values from the model.
     */
    public void refreshFromModel() {
        // Suppress autocomplete popups while we set field values from the model.
        // This prevents the DocumentListener and focusGained handler from opening the
        // suggestions popup when refreshFromModel() is called during a tab switch.
        // 300 ms is enough for all setText() calls and the subsequent focus transfer to complete.
        UiSupport.suppressSuggestionsFor(300);
        OrganizationalChart chart = controller.getData().getOrganizationalChart();
        incidentCommanderArea.setText(String.join("\n", chart.getIncidentCommanders()));
        incidentCommanderRadioField.setText(safe(chart.getIncidentCommanderRadio()));
        incidentCommanderPhoneField.setText(safe(chart.getIncidentCommanderPhone()));

        safetyOfficerNameField.setText(safe(chart.getSafetyOfficerName()));
        safetyOfficerRadioField.setText(safe(chart.getSafetyOfficerRadio()));
        safetyOfficerPhoneField.setText(safe(chart.getSafetyOfficerPhone()));

        pioNameField.setText(safe(chart.getPublicInformationOfficerName()));
        pioRadioField.setText(safe(chart.getPublicInformationOfficerRadio()));
        pioPhoneField.setText(safe(chart.getPublicInformationOfficerPhone()));

        liaisonNameField.setText(safe(chart.getLiaisonOfficerName()));
        liaisonRadioField.setText(safe(chart.getLiaisonOfficerRadio()));
        liaisonPhoneField.setText(safe(chart.getLiaisonOfficerPhone()));

        operationsSectionChiefField.setText(safe(chart.getOperationsSectionChiefName()));
        operationsSectionChiefRadioField.setText(safe(chart.getOperationsSectionChiefRadio()));
        operationsSectionChiefPhoneField.setText(safe(chart.getOperationsSectionChiefPhone()));

        planningSectionChiefNameField.setText(safe(chart.getPlanningSectionChiefName()));
        planningSectionChiefRadioField.setText(safe(chart.getPlanningSectionChiefRadio()));
        planningSectionChiefPhoneField.setText(safe(chart.getPlanningSectionChiefPhone()));

        logisticsSectionChiefNameField.setText(safe(chart.getLogisticsSectionChiefName()));
        logisticsSectionChiefRadioField.setText(safe(chart.getLogisticsSectionChiefRadio()));
        logisticsSectionChiefPhoneField.setText(safe(chart.getLogisticsSectionChiefPhone()));

        financeAdminSectionChiefNameField.setText(safe(chart.getFinanceAdminSectionChiefName()));
        financeAdminSectionChiefRadioField.setText(safe(chart.getFinanceAdminSectionChiefRadio()));
        financeAdminSectionChiefPhoneField.setText(safe(chart.getFinanceAdminSectionChiefPhone()));

        documentationUnitLeaderNameField.setText(safe(chart.getDocumentationUnitLeaderName()));
        documentationUnitLeaderRadioField.setText(safe(chart.getDocumentationUnitLeaderRadio()));
        documentationUnitLeaderPhoneField.setText(safe(chart.getDocumentationUnitLeaderPhone()));

        commUnitLeaderNameField.setText(safe(chart.getCommunicationsUnitLeaderName()));
        commUnitLeaderRadioField.setText(safe(chart.getCommunicationsUnitLeaderRadio()));
        commUnitLeaderPhoneField.setText(safe(chart.getCommunicationsUnitLeaderPhone()));

        commTechNameField.setText(safe(chart.getCommunicationsTechnicianName()));
        commTechRadioField.setText(safe(chart.getCommunicationsTechnicianRadio()));
        commTechPhoneField.setText(safe(chart.getCommunicationsTechnicianPhone()));
    }

    /**
     * Applies field values to the model.
     */
    public void pushToModel() {
        OrganizationalChart chart = controller.getData().getOrganizationalChart();
        chart.setIncidentCommanders(lines(incidentCommanderArea.getText()));
        chart.setIncidentCommanderRadio(incidentCommanderRadioField.getText().trim());
        chart.setIncidentCommanderPhone(incidentCommanderPhoneField.getText().trim());

        chart.setSafetyOfficerName(safetyOfficerNameField.getText().trim());
        chart.setSafetyOfficerRadio(safetyOfficerRadioField.getText().trim());
        chart.setSafetyOfficerPhone(safetyOfficerPhoneField.getText().trim());

        chart.setPublicInformationOfficerName(pioNameField.getText().trim());
        chart.setPublicInformationOfficerRadio(pioRadioField.getText().trim());
        chart.setPublicInformationOfficerPhone(pioPhoneField.getText().trim());

        chart.setLiaisonOfficerName(liaisonNameField.getText().trim());
        chart.setLiaisonOfficerRadio(liaisonRadioField.getText().trim());
        chart.setLiaisonOfficerPhone(liaisonPhoneField.getText().trim());

        chart.setOperationsSectionChiefName(operationsSectionChiefField.getText().trim());
        chart.setOperationsSectionChiefRadio(operationsSectionChiefRadioField.getText().trim());
        chart.setOperationsSectionChiefPhone(operationsSectionChiefPhoneField.getText().trim());

        chart.setPlanningSectionChiefName(planningSectionChiefNameField.getText().trim());
        chart.setPlanningSectionChiefRadio(planningSectionChiefRadioField.getText().trim());
        chart.setPlanningSectionChiefPhone(planningSectionChiefPhoneField.getText().trim());

        chart.setLogisticsSectionChiefName(logisticsSectionChiefNameField.getText().trim());
        chart.setLogisticsSectionChiefRadio(logisticsSectionChiefRadioField.getText().trim());
        chart.setLogisticsSectionChiefPhone(logisticsSectionChiefPhoneField.getText().trim());

        chart.setFinanceAdminSectionChiefName(financeAdminSectionChiefNameField.getText().trim());
        chart.setFinanceAdminSectionChiefRadio(financeAdminSectionChiefRadioField.getText().trim());
        chart.setFinanceAdminSectionChiefPhone(financeAdminSectionChiefPhoneField.getText().trim());

        chart.setDocumentationUnitLeaderName(documentationUnitLeaderNameField.getText().trim());
        chart.setDocumentationUnitLeaderRadio(documentationUnitLeaderRadioField.getText().trim());
        chart.setDocumentationUnitLeaderPhone(documentationUnitLeaderPhoneField.getText().trim());

        chart.setCommunicationsUnitLeaderName(commUnitLeaderNameField.getText().trim());
        chart.setCommunicationsUnitLeaderRadio(commUnitLeaderRadioField.getText().trim());
        chart.setCommunicationsUnitLeaderPhone(commUnitLeaderPhoneField.getText().trim());

        chart.setCommunicationsTechnicianName(commTechNameField.getText().trim());
        chart.setCommunicationsTechnicianRadio(commTechRadioField.getText().trim());
        chart.setCommunicationsTechnicianPhone(commTechPhoneField.getText().trim());
    }

    /**
     * Builds a section panel with Name / Radio / Phone columns per staff position.
     * Radio and Phone cells are initially hidden when the corresponding Name is blank.
     */
    private JPanel buildSection(String title, String[] labels,
                                 JTextField[] nameFields,
                                 JTextField[] radioFields,
                                 JTextField[] phoneFields) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder(title));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(2, 4, 2, 4);
        c.anchor = GridBagConstraints.WEST;

        // Header row
        c.gridx = 1; c.gridy = 0; c.weightx = 0.5; panel.add(new JLabel("Name"), c);
        c.gridx = 2; panel.add(new JLabel("Radio"), c);
        c.gridx = 3; panel.add(new JLabel("Phone"), c);

        for (int i = 0; i < labels.length; i++) {
            c.fill = GridBagConstraints.NONE; c.weightx = 0;
            c.gridx = 0; c.gridy = i + 1;
            panel.add(new JLabel(labels[i]), c);
            c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 0.5;
            c.gridx = 1; panel.add(nameFields[i], c);
            c.gridx = 2; panel.add(radioFields[i], c);
            c.gridx = 3; panel.add(phoneFields[i], c);
        }
        return panel;
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

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
