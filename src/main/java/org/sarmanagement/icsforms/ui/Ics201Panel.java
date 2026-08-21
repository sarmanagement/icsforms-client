package org.sarmanagement.icsforms.ui;

import org.sarmanagement.icsforms.model.IapPhase;
import org.sarmanagement.icsforms.model.Ics201Form;
import org.sarmanagement.icsforms.model.IncidentContext;
import org.sarmanagement.icsforms.model.OrganizationalChart;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Multi-page editor for the Incident Briefing (ICS 201) form.
 */
public class Ics201Panel extends JPanel {
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final AppController controller;
    private final JTabbedPane pages = new JTabbedPane();
    private final JLabel phaseNoteLabel = new JLabel();
    private final JTextField incidentNameField = UiSupport.textField();
    private final JTextField incidentNumberField = UiSupport.textField();
    private final JSpinner dateTimeInitiatedSpinner = UiSupport.dateTimeSpinner();
    private final JTextArea mapSketchArea = UiSupport.textArea(8);
    private final JTextArea situationSummaryArea = UiSupport.textArea(8);
    private final JTextArea objectivesArea = UiSupport.textArea(8);
    private final ActionTableModel actionTableModel = new ActionTableModel();
    private final JTable actionTable = new JTable(actionTableModel);
    private final ResourceSummaryTableModel resourceSummaryTableModel = new ResourceSummaryTableModel();
    private final JTable resourceSummaryTable = new JTable(resourceSummaryTableModel);
    private final JTextArea incidentCommandersArea = readOnlyTextArea(4);
    private final JLabel safetyOfficerLabel = new JLabel();
    private final JLabel publicInformationOfficerLabel = new JLabel();
    private final JLabel liaisonOfficerLabel = new JLabel();
    private final JLabel operationsSectionChiefLabel = new JLabel();
    private final JLabel planningSectionChiefLabel = new JLabel();
    private final JLabel logisticsSectionChiefLabel = new JLabel();
    private final JLabel financeAdminSectionChiefLabel = new JLabel();
    private final JTextField preparedByNameField = UiSupport.textField();
    private final JTextField preparedByPositionField = UiSupport.textField();
    private final JSpinner preparedDateTimeSpinner = UiSupport.dateTimeSpinner();
    private final JTextField preparedSignatureField = UiSupport.textField();

    /**
     * Creates the ICS 201 editor panel.
     *
     * @param controller application controller.
     */
    public Ics201Panel(AppController controller) {
        super(new BorderLayout(8, 8));
        this.controller = controller;

        incidentNameField.setEditable(false);
        configureNoteLabel();
        buildPages();

        add(phaseNoteLabel, BorderLayout.NORTH);
        add(pages, BorderLayout.CENTER);
    }

    /** Loads values from the model. */
    public void refreshFromModel() {
        Ics201Form form = controller.getData201();
        IncidentContext context = controller.getData().getIncidentContext();
        incidentNameField.setText(resolveIncidentName(form, context));
        incidentNumberField.setText(safe(form.getIncidentNumber()));
        dateTimeInitiatedSpinner.setValue(
                AppController.toDate(toDateTime(form.getDateInitiated(), form.getTimeInitiated())));
        mapSketchArea.setText(safe(form.getMapSketch()));
        situationSummaryArea.setText(safe(form.getSituationSummary()));
        objectivesArea.setText(String.join("\n", form.getCurrentObjectives()));
        actionTableModel.setRows(form.getCurrentActions());
        resourceSummaryTableModel.setRows(form.getResources());
        setPreparerValues(form, context);
        refreshOrgChart();
        refreshPhaseNote();
    }

    /** Applies field values to the model. */
    public void pushToModel() {
        Ics201Form form = controller.getData201();
        IncidentContext context = controller.getData().getIncidentContext();
        form.setIncidentName(resolveIncidentName(form, context));
        form.setIncidentNumber(incidentNumberField.getText().trim());
        LocalDateTime dt = AppController.toLocalDateTime((java.util.Date) dateTimeInitiatedSpinner.getValue());
        form.setDateInitiated(dt == null ? null : dt.toLocalDate());
        form.setTimeInitiated(dt == null ? null : dt.toLocalTime());
        form.setMapSketch(mapSketchArea.getText().trim());
        form.setSituationSummary(situationSummaryArea.getText().trim());
        form.setCurrentObjectives(lines(objectivesArea.getText()));
        form.setCurrentActions(actionTableModel.getRows());
        form.setResources(resourceSummaryTableModel.getRows());
        form.setPreparedByName(preparedByNameField.getText().trim());
        form.setPreparedByPositionTitle(preparedByPositionField.getText().trim());
        form.setPreparedDateTime(AppController.toLocalDateTime((java.util.Date) preparedDateTimeSpinner.getValue()));
        form.setPreparedBySignature(preparedSignatureField.getText().trim());
    }

    private void configureNoteLabel() {
        phaseNoteLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("ICS 201 Operational Use"),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)));
        phaseNoteLabel.setVerticalAlignment(JLabel.TOP);
    }

    private void buildPages() {
        pages.addTab("Page 1", new JScrollPane(buildPageOne()));
        pages.addTab("Page 2", new JScrollPane(buildPageTwo()));
        pages.addTab("Page 3", new JScrollPane(buildPageThree()));
        pages.addTab("Page 4", new JScrollPane(buildPageFour()));
    }

    private JPanel buildPageOne() {
        JPanel form = UiSupport.formPanel();
        form.setBorder(BorderFactory.createTitledBorder("ICS 201 - Page 1"));
        UiSupport.addRow(form, 0, "1. Incident Name", incidentNameField);
        UiSupport.addRow(form, 1, "2. Incident Number", incidentNumberField);
        UiSupport.addRow(form, 2, "3. Date/Time Initiated", dateTimeInitiatedSpinner);
        UiSupport.addRow(form, 3, "4. Map/Sketch (description/reference)", new JScrollPane(mapSketchArea));
        UiSupport.addRow(form, 4, "5. Situation Summary and Health and Safety Briefing", new JScrollPane(situationSummaryArea));
        UiSupport.addWideRow(form, 5, buildPreparerPanel());
        return form;
    }

    private JPanel buildPageTwo() {
        JPanel page = new JPanel(new BorderLayout(8, 8));
        page.setBorder(BorderFactory.createTitledBorder("ICS 201 - Page 2"));

        JPanel objectivesPanel = new JPanel(new BorderLayout());
        objectivesPanel.setOpaque(false);
        objectivesPanel.setBorder(BorderFactory.createTitledBorder("7. Current and Planned Objectives"));
        objectivesPanel.add(new JScrollPane(objectivesArea), BorderLayout.CENTER);

        actionTable.setFillsViewportHeight(true);
        JPanel actionsPanel = new JPanel(new BorderLayout());
        actionsPanel.setOpaque(false);
        actionsPanel.setBorder(BorderFactory.createTitledBorder("8. Current and Planned Actions"));
        actionsPanel.add(new JScrollPane(actionTable), BorderLayout.CENTER);
        actionsPanel.add(buttonsPanel(
                () -> { actionTableModel.addRow(); controller.markDirty(); },
                () -> { actionTableModel.removeRow(actionTable.getSelectedRow()); controller.markDirty(); }
        ), BorderLayout.SOUTH);

        JPanel center = new JPanel(new BorderLayout(8, 8));
        center.setOpaque(false);
        center.add(objectivesPanel, BorderLayout.NORTH);
        center.add(actionsPanel, BorderLayout.CENTER);
        page.add(center, BorderLayout.CENTER);
        return page;
    }

    private JPanel buildPageThree() {
        JPanel page = new JPanel(new BorderLayout(8, 8));
        page.setBorder(BorderFactory.createTitledBorder("ICS 201 - Page 3"));

        JPanel orgPanel = UiSupport.formPanel();
        orgPanel.setBorder(BorderFactory.createTitledBorder("9. Current Organization"));
        UiSupport.addRow(orgPanel, 0, "Incident Commander(s)", new JScrollPane(incidentCommandersArea));
        UiSupport.addRow(orgPanel, 1, "Safety Officer", safetyOfficerLabel);
        UiSupport.addRow(orgPanel, 2, "Public Information Officer", publicInformationOfficerLabel);
        UiSupport.addRow(orgPanel, 3, "Liaison Officer", liaisonOfficerLabel);
        UiSupport.addRow(orgPanel, 4, "Operations Section Chief", operationsSectionChiefLabel);
        UiSupport.addRow(orgPanel, 5, "Planning Section Chief", planningSectionChiefLabel);
        UiSupport.addRow(orgPanel, 6, "Logistics Section Chief", logisticsSectionChiefLabel);
        UiSupport.addRow(orgPanel, 7, "Finance/Admin Section Chief", financeAdminSectionChiefLabel);
        UiSupport.addWideRow(orgPanel, 8, new JLabel("Edit organizational chart on the 'Org Chart' tab"));

        page.add(orgPanel, BorderLayout.CENTER);
        return page;
    }

    private JPanel buildPageFour() {
        JPanel page = new JPanel(new BorderLayout(8, 8));
        page.setBorder(BorderFactory.createTitledBorder("ICS 201 - Page 4"));

        resourceSummaryTable.setFillsViewportHeight(true);
        JPanel resourcesPanel = new JPanel(new BorderLayout());
        resourcesPanel.setOpaque(false);
        resourcesPanel.setBorder(BorderFactory.createTitledBorder("10. Resource Summary"));
        resourcesPanel.add(new JScrollPane(resourceSummaryTable), BorderLayout.CENTER);
        resourcesPanel.add(buttonsPanel(
                () -> { resourceSummaryTableModel.addRow(); controller.markDirty(); },
                () -> { resourceSummaryTableModel.removeRow(resourceSummaryTable.getSelectedRow()); controller.markDirty(); }
        ), BorderLayout.SOUTH);

        page.add(resourcesPanel, BorderLayout.CENTER);
        return page;
    }

    private JPanel buildPreparerPanel() {
        JPanel panel = UiSupport.formPanel();
        panel.setBorder(BorderFactory.createTitledBorder("6. Prepared by"));
        UiSupport.addRow(panel, 0, "Name", preparedByNameField);
        UiSupport.addRow(panel, 1, "Position/Title", preparedByPositionField);
        UiSupport.addRow(panel, 2, "Date/Time", preparedDateTimeSpinner);
        UiSupport.addRow(panel, 3, "Signature", preparedSignatureField);
        return panel;
    }

    private JPanel buttonsPanel(Runnable addAction, Runnable removeAction) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        JButton addButton = new JButton("Add Row");
        JButton removeButton = new JButton("Remove");
        addButton.addActionListener(event -> addAction.run());
        removeButton.addActionListener(event -> removeAction.run());
        panel.add(addButton);
        panel.add(removeButton);
        return panel;
    }

    private void refreshOrgChart() {
        OrganizationalChart chart = controller.getData().getOrganizationalChart();
        incidentCommandersArea.setText(String.join("\n", chart.getIncidentCommanders()));
        safetyOfficerLabel.setText(blankToPlaceholder(chart.getSafetyOfficerName()));
        publicInformationOfficerLabel.setText(blankToPlaceholder(chart.getPublicInformationOfficerName()));
        liaisonOfficerLabel.setText(blankToPlaceholder(chart.getLiaisonOfficerName()));
        operationsSectionChiefLabel.setText(blankToPlaceholder(chart.getOperationsSectionChiefName()));
        planningSectionChiefLabel.setText(blankToPlaceholder(chart.getPlanningSectionChiefName()));
        logisticsSectionChiefLabel.setText(blankToPlaceholder(chart.getLogisticsSectionChiefName()));
        financeAdminSectionChiefLabel.setText(blankToPlaceholder(chart.getFinanceAdminSectionChiefName()));
    }

    private void refreshPhaseNote() {
        IapPhase phase = controller.getIapPhase();
        String phaseText;
        if (phase == IapPhase.PRE_OP) {
            phaseText = "Pre-Operational (planning) phase: no incident response has started yet. "
                    + "ICS 201 is available to pre-stage information but is not yet in active use.";
        } else if (phase == IapPhase.INITIAL_RESPONSE) {
            phaseText = "Initial Incident Response phase: ICS 201 is the primary capture tool. "
                    + "All fields are editable. Transition to a subsequent operational period when moving to ICS 202/204 as primary forms.";
        } else {
            phaseText = "Subsequent Operational Period phase: ICS 201 is locked as the historical initial-response record attached to the IAP. "
                    + "Use ICS 202 and ICS 204 as the primary forms for this period.";
        }
        phaseNoteLabel.setText("<html><body style='width: 900px'>" + phaseText + "</body></html>");
        boolean editable = phase != IapPhase.DURING_OP;
        setFormEditable(editable);
    }

    private void setFormEditable(boolean editable) {
        incidentNumberField.setEditable(editable);
        dateTimeInitiatedSpinner.setEnabled(editable);
        mapSketchArea.setEditable(editable);
        situationSummaryArea.setEditable(editable);
        objectivesArea.setEditable(editable);
        actionTable.setEnabled(editable);
        resourceSummaryTable.setEnabled(editable);
        preparedByNameField.setEditable(editable);
        preparedByPositionField.setEditable(editable);
        preparedDateTimeSpinner.setEnabled(editable);
        preparedSignatureField.setEditable(editable);
    }

    private void setPreparerValues(Ics201Form form, IncidentContext context) {
        String preparedByName = safe(form.getPreparedByName());
        String preparedByPosition = safe(form.getPreparedByPositionTitle());
        if (preparedByName.isBlank()) {
            preparedByName = safe(context == null ? "" : context.getCurrentUser());
        }
        if (preparedByPosition.isBlank()) {
            preparedByPosition = safe(context == null ? "" : context.getCurrentUserPositionTitle());
        }
        preparedByNameField.setText(preparedByName);
        preparedByPositionField.setText(preparedByPosition);
        preparedDateTimeSpinner.setValue(AppController.toDate(form.getPreparedDateTime()));
        preparedSignatureField.setText(safe(form.getPreparedBySignature()));
    }

    private String resolveIncidentName(Ics201Form form, IncidentContext context) {
        String contextName = context == null ? "" : safe(context.getIncidentName());
        return contextName.isBlank() ? safe(form.getIncidentName()) : contextName;
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

    private static LocalDateTime toDateTime(LocalDate date, LocalTime time) {
        if (date == null && time == null) {
            return null;
        }
        LocalDate d = date != null ? date : LocalDate.of(1970, 1, 1);
        LocalTime t = time != null ? time : LocalTime.MIDNIGHT;
        return LocalDateTime.of(d, t);
    }

    private static JTextArea readOnlyTextArea(int rows) {
        JTextArea area = UiSupport.textArea(rows);
        area.setEditable(false);
        return area;
    }

    private String blankToPlaceholder(String value) {
        return safe(value).isBlank() ? "—" : safe(value);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private static class ActionTableModel extends AbstractTableModel {
        private final String[] columns = {"Time", "Actions"};
        private final List<Ics201Form.ActionEntry> rows = new ArrayList<>();

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            Ics201Form.ActionEntry entry = rows.get(rowIndex);
            return columnIndex == 0 ? entry.getTime() : entry.getActions();
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return true;
        }

        @Override
        public void setValueAt(Object value, int rowIndex, int columnIndex) {
            Ics201Form.ActionEntry entry = rows.get(rowIndex);
            if (columnIndex == 0) {
                entry.setTime(value == null ? "" : value.toString());
            } else {
                entry.setActions(value == null ? "" : value.toString());
            }
            fireTableCellUpdated(rowIndex, columnIndex);
        }

        void addRow() {
            rows.add(new Ics201Form.ActionEntry());
            fireTableRowsInserted(rows.size() - 1, rows.size() - 1);
        }

        void removeRow(int index) {
            if (index < 0 || index >= rows.size()) {
                return;
            }
            rows.remove(index);
            fireTableRowsDeleted(index, index);
        }

        void setRows(List<Ics201Form.ActionEntry> entries) {
            rows.clear();
            if (entries != null) {
                for (Ics201Form.ActionEntry entry : entries) {
                    Ics201Form.ActionEntry copy = new Ics201Form.ActionEntry();
                    copy.setTime(entry == null ? "" : entry.getTime());
                    copy.setActions(entry == null ? "" : entry.getActions());
                    rows.add(copy);
                }
            }
            fireTableDataChanged();
        }

        List<Ics201Form.ActionEntry> getRows() {
            List<Ics201Form.ActionEntry> copy = new ArrayList<>();
            for (Ics201Form.ActionEntry entry : rows) {
                Ics201Form.ActionEntry row = new Ics201Form.ActionEntry();
                row.setTime(entry.getTime());
                row.setActions(entry.getActions());
                copy.add(row);
            }
            return copy;
        }
    }

    private static class ResourceSummaryTableModel extends AbstractTableModel {
        private final String[] columns = {"Resource", "Resource Identifier", "Date/Time Ordered", "ETA", "Arrived", "Notes"};
        private final List<Ics201Form.ResourceSummaryEntry> rows = new ArrayList<>();

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return columnIndex == 4 ? Boolean.class : String.class;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            Ics201Form.ResourceSummaryEntry entry = rows.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> entry.getResource();
                case 1 -> entry.getResourceIdentifier();
                case 2 -> formatDateTime(entry.getDateTimeOrdered());
                case 3 -> formatDateTime(entry.getEta());
                case 4 -> entry.isArrived();
                default -> entry.getNotes();
            };
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return true;
        }

        @Override
        public void setValueAt(Object value, int rowIndex, int columnIndex) {
            Ics201Form.ResourceSummaryEntry entry = rows.get(rowIndex);
            switch (columnIndex) {
                case 0 -> entry.setResource(value == null ? "" : value.toString());
                case 1 -> entry.setResourceIdentifier(value == null ? "" : value.toString());
                case 2 -> entry.setDateTimeOrdered(parseDateTime(value == null ? "" : value.toString()));
                case 3 -> entry.setEta(parseDateTime(value == null ? "" : value.toString()));
                case 4 -> entry.setArrived(Boolean.TRUE.equals(value));
                default -> entry.setNotes(value == null ? "" : value.toString());
            }
            fireTableCellUpdated(rowIndex, columnIndex);
        }

        void addRow() {
            rows.add(new Ics201Form.ResourceSummaryEntry());
            fireTableRowsInserted(rows.size() - 1, rows.size() - 1);
        }

        void removeRow(int index) {
            if (index < 0 || index >= rows.size()) {
                return;
            }
            rows.remove(index);
            fireTableRowsDeleted(index, index);
        }

        void setRows(List<Ics201Form.ResourceSummaryEntry> entries) {
            rows.clear();
            if (entries != null) {
                for (Ics201Form.ResourceSummaryEntry entry : entries) {
                    Ics201Form.ResourceSummaryEntry copy = new Ics201Form.ResourceSummaryEntry();
                    if (entry != null) {
                        copy.setResource(entry.getResource());
                        copy.setResourceIdentifier(entry.getResourceIdentifier());
                        copy.setDateTimeOrdered(entry.getDateTimeOrdered());
                        copy.setEta(entry.getEta());
                        copy.setArrived(entry.isArrived());
                        copy.setNotes(entry.getNotes());
                    }
                    rows.add(copy);
                }
            }
            fireTableDataChanged();
        }

        List<Ics201Form.ResourceSummaryEntry> getRows() {
            List<Ics201Form.ResourceSummaryEntry> copy = new ArrayList<>();
            for (Ics201Form.ResourceSummaryEntry entry : rows) {
                Ics201Form.ResourceSummaryEntry row = new Ics201Form.ResourceSummaryEntry();
                row.setResource(entry.getResource());
                row.setResourceIdentifier(entry.getResourceIdentifier());
                row.setDateTimeOrdered(entry.getDateTimeOrdered());
                row.setEta(entry.getEta());
                row.setArrived(entry.isArrived());
                row.setNotes(entry.getNotes());
                copy.add(row);
            }
            return copy;
        }

        private static String formatDateTime(LocalDateTime value) {
            return value == null ? "" : DATE_TIME_FORMATTER.format(value);
        }

        private static LocalDateTime parseDateTime(String value) {
            if (value == null || value.isBlank()) {
                return null;
            }
            try {
                return LocalDateTime.parse(value.trim(), DATE_TIME_FORMATTER);
            } catch (DateTimeParseException exception) {
                return null;
            }
        }
    }
}
