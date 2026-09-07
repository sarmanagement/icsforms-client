package org.sarmanagement.icsforms.ui;

import org.sarmanagement.icsforms.model.Ics207Form;
import org.sarmanagement.icsforms.model.OrgChartEntry;
import org.sarmanagement.icsforms.model.OrganizationalChart;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
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
    private final JPanel incidentCommanderRowsPanel = new JPanel(new GridBagLayout());
    private final JButton addIncidentCommanderButton = new JButton("Add");
    private final JLabel incidentCommanderModeLabel = new JLabel("Incident Commander");
    private final List<IncidentCommanderRow> incidentCommanderRows = new ArrayList<>();

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

    // Operations Section positions
    private final JTextField stagingAreaManagerNameField  = UiSupport.textField();
    private final JTextField stagingAreaManagerRadioField = UiSupport.textField();
    private final JTextField stagingAreaManagerPhoneField = UiSupport.textField();

    // Additional Planning Section positions
    private final JTextField resourcesUnitLeaderNameField  = UiSupport.textField();
    private final JTextField resourcesUnitLeaderRadioField = UiSupport.textField();
    private final JTextField resourcesUnitLeaderPhoneField = UiSupport.textField();

    private final JTextField situationUnitLeaderNameField  = UiSupport.textField();
    private final JTextField situationUnitLeaderRadioField = UiSupport.textField();
    private final JTextField situationUnitLeaderPhoneField = UiSupport.textField();

    private final JTextField demobUnitLeaderNameField  = UiSupport.textField();
    private final JTextField demobUnitLeaderRadioField = UiSupport.textField();
    private final JTextField demobUnitLeaderPhoneField = UiSupport.textField();

    // Additional Logistics Section positions
    private final JTextField supplyUnitLeaderNameField      = UiSupport.textField();
    private final JTextField supplyUnitLeaderRadioField     = UiSupport.textField();
    private final JTextField supplyUnitLeaderPhoneField     = UiSupport.textField();

    private final JTextField facilitiesUnitLeaderNameField  = UiSupport.textField();
    private final JTextField facilitiesUnitLeaderRadioField = UiSupport.textField();
    private final JTextField facilitiesUnitLeaderPhoneField = UiSupport.textField();

    private final JTextField groundSupportUnitLeaderNameField  = UiSupport.textField();
    private final JTextField groundSupportUnitLeaderRadioField = UiSupport.textField();
    private final JTextField groundSupportUnitLeaderPhoneField = UiSupport.textField();

    private final JTextField foodUnitLeaderNameField  = UiSupport.textField();
    private final JTextField foodUnitLeaderRadioField = UiSupport.textField();
    private final JTextField foodUnitLeaderPhoneField = UiSupport.textField();

    // Finance/Admin Section positions
    private final JTextField timeUnitLeaderNameField      = UiSupport.textField();
    private final JTextField timeUnitLeaderRadioField     = UiSupport.textField();
    private final JTextField timeUnitLeaderPhoneField     = UiSupport.textField();

    private final JTextField procurementUnitLeaderNameField  = UiSupport.textField();
    private final JTextField procurementUnitLeaderRadioField = UiSupport.textField();
    private final JTextField procurementUnitLeaderPhoneField = UiSupport.textField();

    private final JTextField compClaimsUnitLeaderNameField  = UiSupport.textField();
    private final JTextField compClaimsUnitLeaderRadioField = UiSupport.textField();
    private final JTextField compClaimsUnitLeaderPhoneField = UiSupport.textField();

    private final JTextField costUnitLeaderNameField  = UiSupport.textField();
    private final JTextField costUnitLeaderRadioField = UiSupport.textField();
    private final JTextField costUnitLeaderPhoneField = UiSupport.textField();

    // ICS 207 Preparer
    private final JTextField ics207PreparedByNameField          = UiSupport.textField();
    private final JTextField ics207PreparedByPositionTitleField = UiSupport.textField();
    private final JSpinner   ics207PreparedDateTimeSpinner      = UiSupport.dateTimeSpinner();

    // Custom / additional positions table
    private static final String[] CUSTOM_COLS = {"Section", "Title", "Name", "Radio", "Phone"};
    private final DefaultTableModel customPositionsModel =
            new DefaultTableModel(CUSTOM_COLS, 0) {
                @Override public boolean isCellEditable(int row, int col) { return true; }
            };
    private final JTable customPositionsTable = new JTable(customPositionsModel);

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
        incidentCommanderRowsPanel.setOpaque(false);
        addIncidentCommanderButton.addActionListener(e -> {
            addIncidentCommanderRow("", "", "", false);
            pickIncidentCommanderResource(incidentCommanderRows.get(incidentCommanderRows.size() - 1));
            rebuildIncidentCommanderRowsUi();
        });
        JPanel icRowsWrap = new JPanel(new BorderLayout(0, 4));
        icRowsWrap.setOpaque(false);
        icRowsWrap.add(incidentCommanderModeLabel, BorderLayout.NORTH);
        icRowsWrap.add(incidentCommanderRowsPanel, BorderLayout.CENTER);
        JPanel icActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        icActions.setOpaque(false);
        icActions.add(addIncidentCommanderButton);
        icRowsWrap.add(icActions, BorderLayout.SOUTH);
        UiSupport.addRow(icSection, 0, "Command", icRowsWrap);

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
                new String[]{"Documentation Unit Leader", "Resources Unit Leader",
                        "Situation Unit Leader", "Demobilization Unit Leader"},
                new JTextField[]{documentationUnitLeaderNameField, resourcesUnitLeaderNameField,
                        situationUnitLeaderNameField, demobUnitLeaderNameField},
                new JTextField[]{documentationUnitLeaderRadioField, resourcesUnitLeaderRadioField,
                        situationUnitLeaderRadioField, demobUnitLeaderRadioField},
                new JTextField[]{documentationUnitLeaderPhoneField, resourcesUnitLeaderPhoneField,
                        situationUnitLeaderPhoneField, demobUnitLeaderPhoneField});

        JPanel opsSection = buildSection("Operations Section Positions",
                new String[]{"Staging Area Manager"},
                new JTextField[]{stagingAreaManagerNameField},
                new JTextField[]{stagingAreaManagerRadioField},
                new JTextField[]{stagingAreaManagerPhoneField});

        JPanel logisticsSection = buildSection("Logistics Section Positions",
                new String[]{"Communications Unit Leader", "Communications Technician",
                        "Supply Unit Leader", "Facilities Unit Leader",
                        "Ground Support Unit Leader", "Food Unit Leader"},
                new JTextField[]{commUnitLeaderNameField, commTechNameField,
                        supplyUnitLeaderNameField, facilitiesUnitLeaderNameField,
                        groundSupportUnitLeaderNameField, foodUnitLeaderNameField},
                new JTextField[]{commUnitLeaderRadioField, commTechRadioField,
                        supplyUnitLeaderRadioField, facilitiesUnitLeaderRadioField,
                        groundSupportUnitLeaderRadioField, foodUnitLeaderRadioField},
                new JTextField[]{commUnitLeaderPhoneField, commTechPhoneField,
                        supplyUnitLeaderPhoneField, facilitiesUnitLeaderPhoneField,
                        groundSupportUnitLeaderPhoneField, foodUnitLeaderPhoneField});

        JPanel financeSection = buildSection("Finance / Admin Section Positions",
                new String[]{"Time Unit Leader", "Procurement Unit Leader",
                        "Comp / Claims Unit Leader", "Cost Unit Leader"},
                new JTextField[]{timeUnitLeaderNameField, procurementUnitLeaderNameField,
                        compClaimsUnitLeaderNameField, costUnitLeaderNameField},
                new JTextField[]{timeUnitLeaderRadioField, procurementUnitLeaderRadioField,
                        compClaimsUnitLeaderRadioField, costUnitLeaderRadioField},
                new JTextField[]{timeUnitLeaderPhoneField, procurementUnitLeaderPhoneField,
                        compClaimsUnitLeaderPhoneField, costUnitLeaderPhoneField});

        // Install autocomplete on all name fields.
        installPersonAutocomplete(safetyOfficerNameField,          safetyOfficerRadioField,         safetyOfficerPhoneField);
        installPersonAutocomplete(pioNameField,                    pioRadioField,                   pioPhoneField);
        installPersonAutocomplete(liaisonNameField,                liaisonRadioField,               liaisonPhoneField);
        installPersonAutocomplete(operationsSectionChiefField,     operationsSectionChiefRadioField, operationsSectionChiefPhoneField);
        installPersonAutocomplete(planningSectionChiefNameField,   planningSectionChiefRadioField,  planningSectionChiefPhoneField);
        installPersonAutocomplete(logisticsSectionChiefNameField,  logisticsSectionChiefRadioField, logisticsSectionChiefPhoneField);
        installPersonAutocomplete(financeAdminSectionChiefNameField, financeAdminSectionChiefRadioField, financeAdminSectionChiefPhoneField);
        installPersonAutocomplete(documentationUnitLeaderNameField, documentationUnitLeaderRadioField, documentationUnitLeaderPhoneField);
        installPersonAutocomplete(resourcesUnitLeaderNameField,    resourcesUnitLeaderRadioField,   resourcesUnitLeaderPhoneField);
        installPersonAutocomplete(situationUnitLeaderNameField,    situationUnitLeaderRadioField,   situationUnitLeaderPhoneField);
        installPersonAutocomplete(demobUnitLeaderNameField,        demobUnitLeaderRadioField,       demobUnitLeaderPhoneField);
        installPersonAutocomplete(stagingAreaManagerNameField,     stagingAreaManagerRadioField,    stagingAreaManagerPhoneField);
        installPersonAutocomplete(commUnitLeaderNameField,         commUnitLeaderRadioField,        commUnitLeaderPhoneField);
        installPersonAutocomplete(commTechNameField,               commTechRadioField,              commTechPhoneField);
        installPersonAutocomplete(supplyUnitLeaderNameField,       supplyUnitLeaderRadioField,      supplyUnitLeaderPhoneField);
        installPersonAutocomplete(facilitiesUnitLeaderNameField,   facilitiesUnitLeaderRadioField,  facilitiesUnitLeaderPhoneField);
        installPersonAutocomplete(groundSupportUnitLeaderNameField, groundSupportUnitLeaderRadioField, groundSupportUnitLeaderPhoneField);
        installPersonAutocomplete(foodUnitLeaderNameField,         foodUnitLeaderRadioField,        foodUnitLeaderPhoneField);
        installPersonAutocomplete(timeUnitLeaderNameField,         timeUnitLeaderRadioField,        timeUnitLeaderPhoneField);
        installPersonAutocomplete(procurementUnitLeaderNameField,  procurementUnitLeaderRadioField, procurementUnitLeaderPhoneField);
        installPersonAutocomplete(compClaimsUnitLeaderNameField,   compClaimsUnitLeaderRadioField,  compClaimsUnitLeaderPhoneField);
        installPersonAutocomplete(costUnitLeaderNameField,         costUnitLeaderRadioField,        costUnitLeaderPhoneField);
        addIncidentCommanderRow("", "", "", false);
        rebuildIncidentCommanderRowsUi();

        // Custom / additional positions section
        JPanel customSection = buildCustomPositionsPanel();

        // ICS 207 Preparer section
        JPanel preparerSection = UiSupport.formPanel();
        preparerSection.setBorder(BorderFactory.createTitledBorder("ICS 207 Preparer"));
        UiSupport.addRow(preparerSection, 0, "Name", ics207PreparedByNameField);
        UiSupport.addRow(preparerSection, 1, "Position / Title", ics207PreparedByPositionTitleField);
        UiSupport.addRow(preparerSection, 2, "Date / Time", ics207PreparedDateTimeSpinner);

        JPanel all = new JPanel();
        all.setLayout(new BoxLayout(all, BoxLayout.Y_AXIS));
        all.setOpaque(false);
        all.add(icSection);
        all.add(Box.createVerticalStrut(8));
        all.add(commandSection);
        all.add(Box.createVerticalStrut(8));
        all.add(generalSection);
        all.add(Box.createVerticalStrut(8));
        all.add(opsSection);
        all.add(Box.createVerticalStrut(8));
        all.add(planningSection);
        all.add(Box.createVerticalStrut(8));
        all.add(logisticsSection);
        all.add(Box.createVerticalStrut(8));
        all.add(financeSection);
        all.add(Box.createVerticalStrut(8));
        all.add(customSection);
        all.add(Box.createVerticalStrut(8));
        all.add(preparerSection);

        JPanel topAligned = new JPanel(new BorderLayout());
        topAligned.setOpaque(false);
        topAligned.add(all, BorderLayout.NORTH);

        JScrollPane scrollPane = new JScrollPane(topAligned);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        add(scrollPane, BorderLayout.CENTER);
    }

    /** Builds the custom positions section with an editable table and Add/Remove buttons. */
    private JPanel buildCustomPositionsPanel() {
        // Section combo-box in the "Section" column
        JComboBox<String> sectionCombo = new JComboBox<>(new String[]{
                "Command Staff", "Operations", "Planning", "Logistics", "Finance/Admin"
        });
        customPositionsTable.getColumnModel().getColumn(0).setCellEditor(new DefaultCellEditor(sectionCombo));
        customPositionsTable.setFillsViewportHeight(true);
        customPositionsTable.getTableHeader().setReorderingAllowed(false);

        JButton addBtn = new JButton("Add…");
        addBtn.addActionListener(e -> addCustomPosition());

        JButton removeBtn = new JButton("Remove");
        removeBtn.addActionListener(e -> {
            int row = customPositionsTable.getSelectedRow();
            if (row >= 0) {
                String title = safe((String) customPositionsModel.getValueAt(row, 1));
                String name = safe((String) customPositionsModel.getValueAt(row, 2));
                String label = title.isBlank() ? name : title + (name.isBlank() ? "" : " (" + name + ")");
                int confirm = JOptionPane.showConfirmDialog(this,
                        "Remove custom position " + (label.isBlank() ? "at row " + (row + 1) : "'" + label + "'") + "?",
                        "Remove Custom Position", JOptionPane.YES_NO_OPTION);
                if (confirm == JOptionPane.YES_OPTION) {
                    customPositionsModel.removeRow(row);
                }
            }
        });

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        buttons.setOpaque(false);
        buttons.add(addBtn);
        buttons.add(removeBtn);

        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setBorder(BorderFactory.createTitledBorder("Additional Positions"));
        panel.setOpaque(false);
        panel.add(new JScrollPane(customPositionsTable), BorderLayout.CENTER);
        panel.add(buttons, BorderLayout.SOUTH);
        return panel;
    }

    /** Opens a dialog to add a new custom position entry. */
    private void addCustomPosition() {
        JTextField titleField = UiSupport.textField();
        JTextField nameField  = UiSupport.textField();
        JTextField radioField = UiSupport.textField();
        JTextField phoneField = UiSupport.textField();
        JComboBox<String> sectionBox = new JComboBox<>(new String[]{
                "Command Staff", "Operations", "Planning", "Logistics", "Finance/Admin"
        });

        JPanel form = UiSupport.formPanel();
        UiSupport.addRow(form, 0, "Section",        sectionBox);
        UiSupport.addRow(form, 1, "Position Title", titleField);
        UiSupport.addRow(form, 2, "Name",            nameField);
        UiSupport.addRow(form, 3, "Radio",           radioField);
        UiSupport.addRow(form, 4, "Phone",           phoneField);

        int result = JOptionPane.showConfirmDialog(this, form, "Add Custom Position",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result == JOptionPane.OK_OPTION) {
            customPositionsModel.addRow(new Object[]{
                    sectionBox.getSelectedItem(),
                    titleField.getText().trim(),
                    nameField.getText().trim(),
                    radioField.getText().trim(),
                    phoneField.getText().trim()
            });
        }
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
        OrganizationalChart chart = controller.getData().getOrganizationalChart();
        incidentCommanderRows.clear();
        if (chart.getIncidentCommanderEntries() != null && !chart.getIncidentCommanderEntries().isEmpty()) {
            for (OrgChartEntry entry : chart.getIncidentCommanderEntries()) {
                addIncidentCommanderRow(safe(entry.getName()), safe(entry.getRadio()),
                        safe(entry.getPhone()), chart.isPropagateIncidentCommanderContacts());
            }
        } else if (chart.getIncidentCommanders() != null && !chart.getIncidentCommanders().isEmpty()) {
            List<String> names = chart.getIncidentCommanders();
            for (int i = 0; i < names.size(); i++) {
                addIncidentCommanderRow(safe(names.get(i)),
                        i == 0 ? safe(chart.getIncidentCommanderRadio()) : "",
                        i == 0 ? safe(chart.getIncidentCommanderPhone()) : "",
                        chart.isPropagateIncidentCommanderContacts());
            }
        } else {
            addIncidentCommanderRow("", "", "", false);
        }
        rebuildIncidentCommanderRowsUi();

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

        stagingAreaManagerNameField.setText(safe(chart.getStagingAreaManagerName()));
        stagingAreaManagerRadioField.setText(safe(chart.getStagingAreaManagerRadio()));
        stagingAreaManagerPhoneField.setText(safe(chart.getStagingAreaManagerPhone()));

        resourcesUnitLeaderNameField.setText(safe(chart.getResourcesUnitLeaderName()));
        resourcesUnitLeaderRadioField.setText(safe(chart.getResourcesUnitLeaderRadio()));
        resourcesUnitLeaderPhoneField.setText(safe(chart.getResourcesUnitLeaderPhone()));

        situationUnitLeaderNameField.setText(safe(chart.getSituationUnitLeaderName()));
        situationUnitLeaderRadioField.setText(safe(chart.getSituationUnitLeaderRadio()));
        situationUnitLeaderPhoneField.setText(safe(chart.getSituationUnitLeaderPhone()));

        demobUnitLeaderNameField.setText(safe(chart.getDemobilizationUnitLeaderName()));
        demobUnitLeaderRadioField.setText(safe(chart.getDemobilizationUnitLeaderRadio()));
        demobUnitLeaderPhoneField.setText(safe(chart.getDemobilizationUnitLeaderPhone()));

        supplyUnitLeaderNameField.setText(safe(chart.getSupplyUnitLeaderName()));
        supplyUnitLeaderRadioField.setText(safe(chart.getSupplyUnitLeaderRadio()));
        supplyUnitLeaderPhoneField.setText(safe(chart.getSupplyUnitLeaderPhone()));

        facilitiesUnitLeaderNameField.setText(safe(chart.getFacilitiesUnitLeaderName()));
        facilitiesUnitLeaderRadioField.setText(safe(chart.getFacilitiesUnitLeaderRadio()));
        facilitiesUnitLeaderPhoneField.setText(safe(chart.getFacilitiesUnitLeaderPhone()));

        groundSupportUnitLeaderNameField.setText(safe(chart.getGroundSupportUnitLeaderName()));
        groundSupportUnitLeaderRadioField.setText(safe(chart.getGroundSupportUnitLeaderRadio()));
        groundSupportUnitLeaderPhoneField.setText(safe(chart.getGroundSupportUnitLeaderPhone()));

        foodUnitLeaderNameField.setText(safe(chart.getFoodUnitLeaderName()));
        foodUnitLeaderRadioField.setText(safe(chart.getFoodUnitLeaderRadio()));
        foodUnitLeaderPhoneField.setText(safe(chart.getFoodUnitLeaderPhone()));

        timeUnitLeaderNameField.setText(safe(chart.getTimeUnitLeaderName()));
        timeUnitLeaderRadioField.setText(safe(chart.getTimeUnitLeaderRadio()));
        timeUnitLeaderPhoneField.setText(safe(chart.getTimeUnitLeaderPhone()));

        procurementUnitLeaderNameField.setText(safe(chart.getProcurementUnitLeaderName()));
        procurementUnitLeaderRadioField.setText(safe(chart.getProcurementUnitLeaderRadio()));
        procurementUnitLeaderPhoneField.setText(safe(chart.getProcurementUnitLeaderPhone()));

        compClaimsUnitLeaderNameField.setText(safe(chart.getCompClaimsUnitLeaderName()));
        compClaimsUnitLeaderRadioField.setText(safe(chart.getCompClaimsUnitLeaderRadio()));
        compClaimsUnitLeaderPhoneField.setText(safe(chart.getCompClaimsUnitLeaderPhone()));

        costUnitLeaderNameField.setText(safe(chart.getCostUnitLeaderName()));
        costUnitLeaderRadioField.setText(safe(chart.getCostUnitLeaderRadio()));
        costUnitLeaderPhoneField.setText(safe(chart.getCostUnitLeaderPhone()));

        // Custom positions table
        while (customPositionsModel.getRowCount() > 0) customPositionsModel.removeRow(0);
        for (OrgChartEntry entry : chart.getAdditionalPositions()) {
            customPositionsModel.addRow(new Object[]{
                    sectionLabel(entry.getSection()),
                    safe(entry.getTitle()),
                    safe(entry.getName()),
                    safe(entry.getRadio()),
                    safe(entry.getPhone())
            });
        }

        // ICS 207 preparer
        Ics207Form form207 = controller.getData().getForm207();
        ics207PreparedByNameField.setText(safe(form207.getPreparedByName()));
        ics207PreparedByPositionTitleField.setText(safe(form207.getPreparedByPositionTitle()));
        ics207PreparedDateTimeSpinner.setValue(AppController.toDate(form207.getPreparedDateTime()));
    }

    /**
     * Applies field values to the model.
     */
    public void pushToModel() {
        OrganizationalChart chart = controller.getData().getOrganizationalChart();
        List<String> icNames = new ArrayList<>();
        List<OrgChartEntry> icEntries = new ArrayList<>();
        boolean propagate = false;
        String propagatedRadio = "";
        String propagatedPhone = "";
        for (IncidentCommanderRow row : incidentCommanderRows) {
            String name = row.nameField().getText().trim();
            String radio = row.radioField().getText().trim();
            String phone = row.phoneField().getText().trim();
            if (name.isBlank() && radio.isBlank() && phone.isBlank()) {
                continue;
            }
            if (!name.isBlank()) {
                icNames.add(name);
            }
            OrgChartEntry entry = new OrgChartEntry();
            entry.setSection(OrgChartEntry.Section.COMMAND_STAFF);
            entry.setTitle("Incident Commander");
            entry.setName(name);
            entry.setRadio(radio);
            entry.setPhone(phone);
            icEntries.add(entry);
            boolean rowPropagate = row.propagateCheckbox().isSelected();
            propagate |= rowPropagate;
            if (rowPropagate) {
                if (propagatedRadio.isBlank()) {
                    propagatedRadio = radio;
                }
                if (propagatedPhone.isBlank()) {
                    propagatedPhone = phone;
                }
            }
        }
        chart.setIncidentCommanders(icNames);
        chart.setIncidentCommanderEntries(icEntries);
        chart.setPropagateIncidentCommanderContacts(propagate);
        chart.setIncidentCommanderRadio(propagatedRadio);
        chart.setIncidentCommanderPhone(propagatedPhone);

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

        chart.setStagingAreaManagerName(stagingAreaManagerNameField.getText().trim());
        chart.setStagingAreaManagerRadio(stagingAreaManagerRadioField.getText().trim());
        chart.setStagingAreaManagerPhone(stagingAreaManagerPhoneField.getText().trim());

        chart.setResourcesUnitLeaderName(resourcesUnitLeaderNameField.getText().trim());
        chart.setResourcesUnitLeaderRadio(resourcesUnitLeaderRadioField.getText().trim());
        chart.setResourcesUnitLeaderPhone(resourcesUnitLeaderPhoneField.getText().trim());

        chart.setSituationUnitLeaderName(situationUnitLeaderNameField.getText().trim());
        chart.setSituationUnitLeaderRadio(situationUnitLeaderRadioField.getText().trim());
        chart.setSituationUnitLeaderPhone(situationUnitLeaderPhoneField.getText().trim());

        chart.setDemobilizationUnitLeaderName(demobUnitLeaderNameField.getText().trim());
        chart.setDemobilizationUnitLeaderRadio(demobUnitLeaderRadioField.getText().trim());
        chart.setDemobilizationUnitLeaderPhone(demobUnitLeaderPhoneField.getText().trim());

        chart.setSupplyUnitLeaderName(supplyUnitLeaderNameField.getText().trim());
        chart.setSupplyUnitLeaderRadio(supplyUnitLeaderRadioField.getText().trim());
        chart.setSupplyUnitLeaderPhone(supplyUnitLeaderPhoneField.getText().trim());

        chart.setFacilitiesUnitLeaderName(facilitiesUnitLeaderNameField.getText().trim());
        chart.setFacilitiesUnitLeaderRadio(facilitiesUnitLeaderRadioField.getText().trim());
        chart.setFacilitiesUnitLeaderPhone(facilitiesUnitLeaderPhoneField.getText().trim());

        chart.setGroundSupportUnitLeaderName(groundSupportUnitLeaderNameField.getText().trim());
        chart.setGroundSupportUnitLeaderRadio(groundSupportUnitLeaderRadioField.getText().trim());
        chart.setGroundSupportUnitLeaderPhone(groundSupportUnitLeaderPhoneField.getText().trim());

        chart.setFoodUnitLeaderName(foodUnitLeaderNameField.getText().trim());
        chart.setFoodUnitLeaderRadio(foodUnitLeaderRadioField.getText().trim());
        chart.setFoodUnitLeaderPhone(foodUnitLeaderPhoneField.getText().trim());

        chart.setTimeUnitLeaderName(timeUnitLeaderNameField.getText().trim());
        chart.setTimeUnitLeaderRadio(timeUnitLeaderRadioField.getText().trim());
        chart.setTimeUnitLeaderPhone(timeUnitLeaderPhoneField.getText().trim());

        chart.setProcurementUnitLeaderName(procurementUnitLeaderNameField.getText().trim());
        chart.setProcurementUnitLeaderRadio(procurementUnitLeaderRadioField.getText().trim());
        chart.setProcurementUnitLeaderPhone(procurementUnitLeaderPhoneField.getText().trim());

        chart.setCompClaimsUnitLeaderName(compClaimsUnitLeaderNameField.getText().trim());
        chart.setCompClaimsUnitLeaderRadio(compClaimsUnitLeaderRadioField.getText().trim());
        chart.setCompClaimsUnitLeaderPhone(compClaimsUnitLeaderPhoneField.getText().trim());

        chart.setCostUnitLeaderName(costUnitLeaderNameField.getText().trim());
        chart.setCostUnitLeaderRadio(costUnitLeaderRadioField.getText().trim());
        chart.setCostUnitLeaderPhone(costUnitLeaderPhoneField.getText().trim());

        // Custom positions
        List<OrgChartEntry> additional = new ArrayList<>();
        if (customPositionsTable.isEditing()) customPositionsTable.getCellEditor().stopCellEditing();
        for (int row = 0; row < customPositionsModel.getRowCount(); row++) {
            OrgChartEntry entry = new OrgChartEntry();
            entry.setSection(sectionFromLabel((String) customPositionsModel.getValueAt(row, 0)));
            entry.setTitle(safe((String) customPositionsModel.getValueAt(row, 1)));
            entry.setName(safe((String) customPositionsModel.getValueAt(row, 2)));
            entry.setRadio(safe((String) customPositionsModel.getValueAt(row, 3)));
            entry.setPhone(safe((String) customPositionsModel.getValueAt(row, 4)));
            additional.add(entry);
        }
        chart.setAdditionalPositions(additional);

        // ICS 207 preparer
        Ics207Form form207 = controller.getData().getForm207();
        form207.setPreparedByName(ics207PreparedByNameField.getText().trim());
        form207.setPreparedByPositionTitle(ics207PreparedByPositionTitleField.getText().trim());
        form207.setPreparedDateTime(AppController.toLocalDateTime((java.util.Date) ics207PreparedDateTimeSpinner.getValue()));
    }

    private void addIncidentCommanderRow(String name, String radio, String phone, boolean propagate) {
        JTextField nameField = UiSupport.textField();
        JTextField radioField = UiSupport.textField();
        JTextField phoneField = UiSupport.textField();
        JCheckBox propagateCheckbox = new JCheckBox("Propagate contact to other forms");
        propagateCheckbox.setOpaque(false);
        nameField.setText(name);
        radioField.setText(radio);
        phoneField.setText(phone);
        propagateCheckbox.setSelected(propagate);
        installPersonAutocomplete(nameField, radioField, phoneField);
        incidentCommanderRows.add(new IncidentCommanderRow(nameField, radioField, phoneField, propagateCheckbox));
    }

    private void rebuildIncidentCommanderRowsUi() {
        incidentCommanderRowsPanel.removeAll();
        if (incidentCommanderRows.isEmpty()) {
            addIncidentCommanderRow("", "", "", false);
        }
        boolean unifiedCommand = incidentCommanderRows.size() > 1;
        incidentCommanderModeLabel.setText(unifiedCommand ? "Unified Command" : "Incident Commander");
        for (int i = 0; i < incidentCommanderRows.size(); i++) {
            IncidentCommanderRow row = incidentCommanderRows.get(i);
            GridBagConstraints c = new GridBagConstraints();
            c.gridy = i;
            c.insets = new Insets(2, 0, 2, 4);
            c.anchor = GridBagConstraints.WEST;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.weightx = 0.28;
            c.gridx = 0;
            incidentCommanderRowsPanel.add(row.nameField(), c);
            c.weightx = 0.18;
            c.gridx = 1;
            incidentCommanderRowsPanel.add(row.radioField(), c);
            c.gridx = 2;
            incidentCommanderRowsPanel.add(row.phoneField(), c);
            c.weightx = 0;
            c.gridx = 3;
            JButton pickButton = new JButton("Pick…");
            pickButton.addActionListener(e -> pickIncidentCommanderResource(row));
            incidentCommanderRowsPanel.add(pickButton, c);
            c.gridx = 4;
            JButton removeButton = new JButton("Remove");
            removeButton.setEnabled(incidentCommanderRows.size() > 1);
            removeButton.addActionListener(e -> {
                String who = row.nameField().getText().trim();
                int confirm = JOptionPane.showConfirmDialog(this,
                        "Remove incident commander " + (who.isBlank() ? "entry?" : "'" + who + "'?"),
                        "Remove Incident Commander", JOptionPane.YES_NO_OPTION);
                if (confirm == JOptionPane.YES_OPTION) {
                    incidentCommanderRows.remove(row);
                    rebuildIncidentCommanderRowsUi();
                }
            });
            incidentCommanderRowsPanel.add(removeButton, c);
            c.gridx = 5;
            row.propagateCheckbox().setVisible(unifiedCommand);
            incidentCommanderRowsPanel.add(row.propagateCheckbox(), c);
        }
        incidentCommanderRowsPanel.revalidate();
        incidentCommanderRowsPanel.repaint();
    }

    private void pickIncidentCommanderResource(IncidentCommanderRow row) {
        List<String> names = new ArrayList<>(controller.getPersonnelNames());
        names.add("<Add new resource…>");
        JComboBox<String> combo = new JComboBox<>(names.toArray(new String[0]));
        int choice = JOptionPane.showConfirmDialog(this, combo,
                "Select Incident Commander", JOptionPane.OK_CANCEL_OPTION);
        if (choice != JOptionPane.OK_OPTION || combo.getSelectedItem() == null) {
            return;
        }
        String selected = combo.getSelectedItem().toString();
        if ("<Add new resource…>".equals(selected)) {
            JTextField nameField = UiSupport.textField();
            JTextField radioField = UiSupport.textField();
            JTextField phoneField = UiSupport.textField();
            JPanel form = UiSupport.formPanel();
            UiSupport.addRequiredRow(form, 0, "Name", nameField);
            UiSupport.addRow(form, 1, "Radio", radioField);
            UiSupport.addRow(form, 2, "Phone", phoneField);
            JScrollPane pane = new JScrollPane(form);
            pane.setBorder(BorderFactory.createEmptyBorder());
            if (!UiSupport.showResizableConfirmDialog(this, "Add Incident Commander Resource",
                    pane, new Dimension(540, 240))) {
                return;
            }
            row.nameField().setText(nameField.getText().trim());
            row.radioField().setText(radioField.getText().trim());
            row.phoneField().setText(phoneField.getText().trim());
            return;
        }
        row.nameField().setText(selected);
        var card = controller.findPersonCard(selected);
        if (card != null) {
            if (row.radioField().getText().isBlank()) {
                row.radioField().setText(safe(card.getRadioChannel()));
            }
            if (row.phoneField().getText().isBlank()) {
                row.phoneField().setText(safe(card.getPhoneNumber()));
            }
        }
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

    private static String sectionLabel(OrgChartEntry.Section section) {
        if (section == null) return "Operations";
        return switch (section) {
            case COMMAND_STAFF -> "Command Staff";
            case OPERATIONS    -> "Operations";
            case PLANNING      -> "Planning";
            case LOGISTICS     -> "Logistics";
            case FINANCE_ADMIN -> "Finance/Admin";
        };
    }

    private static OrgChartEntry.Section sectionFromLabel(String label) {
        if (label == null) return OrgChartEntry.Section.OPERATIONS;
        return switch (label) {
            case "Command Staff" -> OrgChartEntry.Section.COMMAND_STAFF;
            case "Planning"      -> OrgChartEntry.Section.PLANNING;
            case "Logistics"     -> OrgChartEntry.Section.LOGISTICS;
            case "Finance/Admin" -> OrgChartEntry.Section.FINANCE_ADMIN;
            default              -> OrgChartEntry.Section.OPERATIONS;
        };
    }

    private record IncidentCommanderRow(JTextField nameField, JTextField radioField,
                                        JTextField phoneField, JCheckBox propagateCheckbox) {
    }
}
