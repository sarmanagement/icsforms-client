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
import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared editor for organizational chart roles linked across forms.
 *
 * <p>Shows Incident Command, Command Staff (Safety Officer, PIO, Liaison Officer),
 * and General Staff (Ops, Planning, Logistics, Finance chiefs plus Documentation Unit Leader).</p>
 */
public class OrganizationalChartPanel extends JPanel {
    private final AppController controller;
    private final JTextArea incidentCommanderArea = UiSupport.textArea(4);
    private final JTextField operationsSectionChiefField = UiSupport.textField();
    private final JTextField operationsSectionChiefContactField = UiSupport.textField();

    // Command Staff
    private final JTextField safetyOfficerNameField = UiSupport.textField();
    private final JTextField safetyOfficerContactField = UiSupport.textField();
    private final JTextField pioNameField = UiSupport.textField();
    private final JTextField pioContactField = UiSupport.textField();
    private final JTextField liaisonNameField = UiSupport.textField();
    private final JTextField liaisonContactField = UiSupport.textField();

    // General Staff
    private final JTextField planningSectionChiefNameField = UiSupport.textField();
    private final JTextField planningSectionChiefContactField = UiSupport.textField();
    private final JTextField logisticsSectionChiefNameField = UiSupport.textField();
    private final JTextField logisticsSectionChiefContactField = UiSupport.textField();
    private final JTextField financeAdminSectionChiefNameField = UiSupport.textField();
    private final JTextField financeAdminSectionChiefContactField = UiSupport.textField();

    // Planning Section
    private final JTextField documentationUnitLeaderNameField = UiSupport.textField();
    private final JTextField documentationUnitLeaderContactField = UiSupport.textField();

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

        JPanel commandSection = buildTwoColumnSection("Command Staff",
                new String[]{"Safety Officer", "PIO / Public Information Officer", "Liaison Officer"},
                new JTextField[]{safetyOfficerNameField, pioNameField, liaisonNameField},
                new JTextField[]{safetyOfficerContactField, pioContactField, liaisonContactField});

        JPanel generalSection = buildTwoColumnSection("General Staff",
                new String[]{"Operations Section Chief", "Planning Section Chief",
                        "Logistics Section Chief", "Finance / Admin Section Chief"},
                new JTextField[]{operationsSectionChiefField, planningSectionChiefNameField,
                        logisticsSectionChiefNameField, financeAdminSectionChiefNameField},
                new JTextField[]{operationsSectionChiefContactField, planningSectionChiefContactField,
                        logisticsSectionChiefContactField, financeAdminSectionChiefContactField});

        JPanel planningSection = buildTwoColumnSection("Planning Section Positions",
                new String[]{"Documentation Unit Leader"},
                new JTextField[]{documentationUnitLeaderNameField},
                new JTextField[]{documentationUnitLeaderContactField});

        // Install autocomplete on all name fields so existing T-card personnel
        // can be selected, auto-populating the adjacent contact field.
        installPersonAutocomplete(safetyOfficerNameField,          safetyOfficerContactField);
        installPersonAutocomplete(pioNameField,                    pioContactField);
        installPersonAutocomplete(liaisonNameField,                liaisonContactField);
        installPersonAutocomplete(operationsSectionChiefField,     operationsSectionChiefContactField);
        installPersonAutocomplete(planningSectionChiefNameField,   planningSectionChiefContactField);
        installPersonAutocomplete(logisticsSectionChiefNameField,  logisticsSectionChiefContactField);
        installPersonAutocomplete(financeAdminSectionChiefNameField, financeAdminSectionChiefContactField);
        installPersonAutocomplete(documentationUnitLeaderNameField, documentationUnitLeaderContactField);

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

        JPanel topAligned = new JPanel(new BorderLayout());
        topAligned.setOpaque(false);
        topAligned.add(all, BorderLayout.NORTH);

        JScrollPane scrollPane = new JScrollPane(topAligned);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        add(scrollPane, BorderLayout.CENTER);
    }

    /**
     * Installs name autocomplete on {@code nameField} from existing T-card personnel records.
     * When a known name is selected the adjacent {@code contactField} is auto-filled with the
     * T-card's radio channel (preferred) or phone number if the contact field is currently blank.
     */
    private void installPersonAutocomplete(JTextField nameField, JTextField contactField) {
        UiSupport.installNameAutocomplete(nameField,
                () -> controller.getPersonnelNames(),
                selectedName -> {
                    var card = controller.findPersonCard(selectedName);
                    if (card != null && contactField.getText().isBlank()) {
                        String contact = card.getRadioChannel().isBlank()
                                ? card.getPhoneNumber() : card.getRadioChannel();
                        if (!contact.isBlank()) {
                            contactField.setText(contact);
                        }
                    }
                });
    }

    /**
     * Loads field values from the model.
     */
    public void refreshFromModel() {
        OrganizationalChart chart = controller.getData().getOrganizationalChart();
        incidentCommanderArea.setText(String.join("\n", chart.getIncidentCommanders()));
        operationsSectionChiefField.setText(safe(chart.getOperationsSectionChiefName()));
        operationsSectionChiefContactField.setText(safe(chart.getOperationsSectionChiefContact()));
        safetyOfficerNameField.setText(safe(chart.getSafetyOfficerName()));
        safetyOfficerContactField.setText(safe(chart.getSafetyOfficerContact()));
        pioNameField.setText(safe(chart.getPublicInformationOfficerName()));
        pioContactField.setText(safe(chart.getPublicInformationOfficerContact()));
        liaisonNameField.setText(safe(chart.getLiaisonOfficerName()));
        liaisonContactField.setText(safe(chart.getLiaisonOfficerContact()));
        planningSectionChiefNameField.setText(safe(chart.getPlanningSectionChiefName()));
        planningSectionChiefContactField.setText(safe(chart.getPlanningSectionChiefContact()));
        logisticsSectionChiefNameField.setText(safe(chart.getLogisticsSectionChiefName()));
        logisticsSectionChiefContactField.setText(safe(chart.getLogisticsSectionChiefContact()));
        financeAdminSectionChiefNameField.setText(safe(chart.getFinanceAdminSectionChiefName()));
        financeAdminSectionChiefContactField.setText(safe(chart.getFinanceAdminSectionChiefContact()));
        documentationUnitLeaderNameField.setText(safe(chart.getDocumentationUnitLeaderName()));
        documentationUnitLeaderContactField.setText(safe(chart.getDocumentationUnitLeaderContact()));
    }

    /**
     * Applies field values to the model.
     */
    public void pushToModel() {
        OrganizationalChart chart = controller.getData().getOrganizationalChart();
        chart.setIncidentCommanders(lines(incidentCommanderArea.getText()));
        chart.setOperationsSectionChiefName(operationsSectionChiefField.getText().trim());
        chart.setOperationsSectionChiefContact(operationsSectionChiefContactField.getText().trim());
        chart.setSafetyOfficerName(safetyOfficerNameField.getText().trim());
        chart.setSafetyOfficerContact(safetyOfficerContactField.getText().trim());
        chart.setPublicInformationOfficerName(pioNameField.getText().trim());
        chart.setPublicInformationOfficerContact(pioContactField.getText().trim());
        chart.setLiaisonOfficerName(liaisonNameField.getText().trim());
        chart.setLiaisonOfficerContact(liaisonContactField.getText().trim());
        chart.setPlanningSectionChiefName(planningSectionChiefNameField.getText().trim());
        chart.setPlanningSectionChiefContact(planningSectionChiefContactField.getText().trim());
        chart.setLogisticsSectionChiefName(logisticsSectionChiefNameField.getText().trim());
        chart.setLogisticsSectionChiefContact(logisticsSectionChiefContactField.getText().trim());
        chart.setFinanceAdminSectionChiefName(financeAdminSectionChiefNameField.getText().trim());
        chart.setFinanceAdminSectionChiefContact(financeAdminSectionChiefContactField.getText().trim());
        chart.setDocumentationUnitLeaderName(documentationUnitLeaderNameField.getText().trim());
        chart.setDocumentationUnitLeaderContact(documentationUnitLeaderContactField.getText().trim());
    }

    private JPanel buildTwoColumnSection(String title, String[] labels, JTextField[] nameFields, JTextField[] contactFields) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder(title));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(2, 4, 2, 4);
        c.anchor = GridBagConstraints.WEST;

        // Header row
        c.gridx = 1; c.gridy = 0; c.weightx = 0.5;
        panel.add(new JLabel("Name"), c);
        c.gridx = 2;
        panel.add(new JLabel("Contact (Radio/Phone)"), c);

        for (int i = 0; i < labels.length; i++) {
            c.gridx = 0; c.gridy = i + 1; c.weightx = 0;
            panel.add(new JLabel(labels[i]), c);
            c.gridx = 1; c.weightx = 0.5; c.fill = GridBagConstraints.HORIZONTAL;
            panel.add(nameFields[i], c);
            c.gridx = 2;
            panel.add(contactFields[i], c);
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
