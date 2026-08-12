package org.sarmanagement.icsforms.ui;

import org.sarmanagement.icsforms.model.TCard;
import org.sarmanagement.icsforms.model.TCardType;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerDateModel;
import javax.swing.SwingConstants;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * Panel displaying and editing ICS 219 T-Card (Resource Status Card) records.
 *
 * <p>Cards may be displayed in a sortable table or in a colour-coded rack view that
 * mimics a physical T-card rack.  Personnel cards linked from the org chart or SAR tasks
 * are created automatically by {@link AppController#syncTCards()}; manual add and CSV
 * import are also available.</p>
 */
public class TCardPanel extends JPanel {
    private static final String[] STATUS_OPTIONS = {
            "", "Enroute", "At Staging", "Assigned", "Out of Service"
    };
    private static final String VIEW_TABLE = "table";
    private static final String VIEW_RACK  = "rack";

    private final AppController controller;
    private final TCardTableModel tableModel = new TCardTableModel();
    private final JTable table = new JTable(tableModel);
    private final CardLayout viewLayout = new CardLayout();
    private final JPanel viewContainer = new JPanel(viewLayout);
    private final JScrollPane rackScroll = new JScrollPane();
    private final JButton toggleViewBtn = new JButton("Rack View");
    private String currentView = VIEW_TABLE;

    /**
     * Creates the T-card panel.
     *
     * @param controller application controller.
     */
    public TCardPanel(AppController controller) {
        super(new BorderLayout());
        this.controller = controller;
        setBorder(BorderFactory.createTitledBorder("T-Cards (ICS 219 Resource Status)"));

        table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        table.setFillsViewportHeight(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(22);
        table.setDefaultRenderer(Object.class, new TCardCellRenderer());

        viewContainer.add(new JScrollPane(table), VIEW_TABLE);
        viewContainer.add(rackScroll, VIEW_RACK);

        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        JButton addPersonnelBtn = new JButton("Add Personnel Card");
        JButton editBtn        = new JButton("Edit Selected…");
        JButton removeBtn      = new JButton("Remove Selected");
        JButton importCsvBtn   = new JButton("Import from CSV…");

        addPersonnelBtn.addActionListener(e -> addPersonnelCard());
        editBtn.addActionListener(e -> editSelectedCard());
        removeBtn.addActionListener(e -> removeSelectedCard());
        importCsvBtn.addActionListener(e -> importFromCsv());
        toggleViewBtn.addActionListener(e -> toggleView());

        buttonRow.add(addPersonnelBtn);
        buttonRow.add(editBtn);
        buttonRow.add(removeBtn);
        buttonRow.add(importCsvBtn);
        buttonRow.add(toggleViewBtn);

        add(viewContainer, BorderLayout.CENTER);
        add(buttonRow, BorderLayout.SOUTH);
    }

    /**
     * Reloads table rows from the model.
     */
    public void refreshFromModel() {
        tableModel.setCards(new ArrayList<>(controller.getData().getTCards()));
        if (currentView.equals(VIEW_RACK)) {
            rebuildRackView();
        }
    }

    /**
     * Applies table edits back to the model.
     */
    public void pushToModel() {
        controller.getData().setTCards(new ArrayList<>(tableModel.getCards()));
    }

    // -------------------------------------------------------------------------
    // View toggle
    // -------------------------------------------------------------------------

    private void toggleView() {
        if (currentView.equals(VIEW_TABLE)) {
            currentView = VIEW_RACK;
            toggleViewBtn.setText("Table View");
            rebuildRackView();
            viewLayout.show(viewContainer, VIEW_RACK);
        } else {
            currentView = VIEW_TABLE;
            toggleViewBtn.setText("Rack View");
            viewLayout.show(viewContainer, VIEW_TABLE);
        }
    }

    /**
     * Rebuilds the visual rack panel from the current table model rows.
     *
     * <p>If any HEADER (grey, 219-1) cards are present the rack is arranged as a physical
     * T-card rack: each HEADER card becomes a column heading and the resource cards that
     * follow it (up to the next HEADER) are stacked below it as narrow coloured cards.
     * When no HEADER cards exist the rack falls back to grouping by card type.</p>
     */
    private void rebuildRackView() {
        List<TCard> allCards = tableModel.getCards();

        // Determine if there are any HEADER cards; if so use header-based layout.
        boolean hasHeaders = allCards.stream().anyMatch(c -> c.getCardType() == TCardType.HEADER);

        JPanel rack;
        if (hasHeaders) {
            rack = buildHeaderBasedRack(allCards);
        } else {
            rack = buildTypeBasedRack(allCards);
        }

        rackScroll.setViewportView(rack);
        rackScroll.revalidate();
        rackScroll.repaint();
    }

    /**
     * Builds a rack panel where each HEADER card is a column heading and the
     * resource cards that follow it are stacked below as narrow coloured widgets.
     * Resource cards before the first HEADER are collected into an "Unassigned" column.
     */
    private JPanel buildHeaderBasedRack(List<TCard> cards) {
        // Partition cards into sections: (headerCard-or-null, list-of-resource-cards).
        List<TCard> sectionHeaders = new ArrayList<>();
        List<List<TCard>> sectionCards = new ArrayList<>();

        TCard currentHeader = null;
        List<TCard> currentCards = new ArrayList<>();
        for (TCard card : cards) {
            if (card.getCardType() == TCardType.HEADER) {
                sectionHeaders.add(currentHeader);
                sectionCards.add(currentCards);
                currentHeader = card;
                currentCards = new ArrayList<>();
            } else {
                currentCards.add(card);
            }
        }
        sectionHeaders.add(currentHeader);
        sectionCards.add(currentCards);

        // Remove leading null-header section if it has no cards.
        if (!sectionHeaders.isEmpty() && sectionHeaders.get(0) == null && sectionCards.get(0).isEmpty()) {
            sectionHeaders.remove(0);
            sectionCards.remove(0);
        }

        int cols = Math.max(1, sectionHeaders.size());
        JPanel rack = new JPanel(new GridLayout(1, cols, 6, 0));
        rack.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        for (int i = 0; i < sectionHeaders.size(); i++) {
            TCard header = sectionHeaders.get(i);
            List<TCard> children = sectionCards.get(i);

            JPanel col = new JPanel();
            col.setLayout(new javax.swing.BoxLayout(col, javax.swing.BoxLayout.Y_AXIS));

            // Grey header card at top of column.
            if (header != null) {
                col.add(buildHeaderCardWidget(header));
                col.add(javax.swing.Box.createVerticalStrut(4));
            } else {
                // Unassigned column heading.
                JLabel lbl = new JLabel("Unassigned");
                lbl.setFont(lbl.getFont().deriveFont(Font.BOLD, 12f));
                lbl.setOpaque(true);
                lbl.setBackground(cardColor(TCardType.HEADER));
                lbl.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(Color.DARK_GRAY),
                        BorderFactory.createEmptyBorder(4, 6, 4, 6)));
                lbl.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
                col.add(lbl);
                col.add(javax.swing.Box.createVerticalStrut(4));
            }

            for (TCard card : children) {
                col.add(buildRackCard(card));
                col.add(javax.swing.Box.createVerticalStrut(3));
            }
            col.add(javax.swing.Box.createVerticalGlue());
            rack.add(col);
        }
        return rack;
    }

    /**
     * Builds a rack panel grouped by card type — used when no HEADER cards are present.
     */
    private JPanel buildTypeBasedRack(List<TCard> allCards) {
        List<TCardType> typeOrder = Arrays.asList(TCardType.values());
        java.util.Map<TCardType, List<TCard>> byType = new java.util.LinkedHashMap<>();
        for (TCardType t : typeOrder) {
            byType.put(t, new ArrayList<>());
        }
        for (TCard card : allCards) {
            byType.get(card.getCardType()).add(card);
        }

        List<TCardType> usedTypes = new ArrayList<>();
        for (TCardType t : typeOrder) {
            if (!byType.get(t).isEmpty()) {
                usedTypes.add(t);
            }
        }
        int cols = Math.max(1, usedTypes.size());

        JPanel rack = new JPanel(new GridLayout(1, cols, 6, 0));
        rack.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        for (TCardType type : usedTypes) {
            JPanel col = new JPanel();
            col.setLayout(new javax.swing.BoxLayout(col, javax.swing.BoxLayout.Y_AXIS));
            col.setBorder(BorderFactory.createTitledBorder(type.getLabel()));

            for (TCard card : byType.get(type)) {
                col.add(buildRackCard(card));
                col.add(javax.swing.Box.createVerticalStrut(3));
            }
            col.add(javax.swing.Box.createVerticalGlue());
            rack.add(col);
        }
        return rack;
    }

    /** Builds the grey column-heading widget for a HEADER card in the rack view. */
    private JPanel buildHeaderCardWidget(TCard header) {
        JPanel p = new JPanel(new BorderLayout(2, 2));
        p.setBackground(cardColor(TCardType.HEADER));
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.DARK_GRAY, 2),
                BorderFactory.createEmptyBorder(4, 6, 4, 6)));
        p.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);

        JLabel titleLabel = new JLabel(header.getDisplayLabel());
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 13f));
        p.add(titleLabel, BorderLayout.NORTH);

        StringBuilder sub = new StringBuilder();
        if (!header.getLocation().isBlank()) {
            sub.append(header.getLocation());
        }
        if (!header.getNotes().isBlank()) {
            if (!sub.isEmpty()) sub.append(" · ");
            sub.append(header.getNotes());
        }
        if (!sub.isEmpty()) {
            JLabel subLabel = new JLabel(sub.toString());
            subLabel.setFont(subLabel.getFont().deriveFont(11f));
            p.add(subLabel, BorderLayout.CENTER);
        }
        return p;
    }

    /**
     * Builds a single narrow resource card widget for the rack view.
     *
     * <p>Location and status are omitted here because they are conveyed by the HEADER
     * card above the column.  Only name, agency and phone are shown.</p>
     */
    private JPanel buildRackCard(TCard card) {
        JPanel p = new JPanel(new BorderLayout(2, 2));
        p.setBackground(cardColor(card.getCardType()));
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.DARK_GRAY),
                BorderFactory.createEmptyBorder(3, 5, 3, 5)));
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 55));
        p.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);

        JLabel nameLabel = new JLabel(card.getDisplayLabel());
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD));

        StringBuilder detail = new StringBuilder();
        if (!card.getHomeAgency().isBlank()) {
            detail.append(card.getHomeAgency());
        }
        if (!card.getPhoneNumber().isBlank()) {
            if (!detail.isEmpty()) detail.append(" · ");
            detail.append(card.getPhoneNumber());
        }

        p.add(nameLabel, BorderLayout.NORTH);
        if (!detail.isEmpty()) {
            JLabel detailLabel = new JLabel(detail.toString());
            detailLabel.setFont(detailLabel.getFont().deriveFont(10.5f));
            p.add(detailLabel, BorderLayout.CENTER);
        }
        return p;
    }

    // -------------------------------------------------------------------------
    // Card add / edit / remove
    // -------------------------------------------------------------------------

    private void addPersonnelCard() {
        TCard card = new TCard();
        card.setCardType(TCardType.PERSONNEL);
        if (openEditDialog(card)) {
            tableModel.addCard(card);
            controller.markDirty();
        }
    }

    private void editSelectedCard() {
        int row = table.getSelectedRow();
        if (row < 0) {
            return;
        }
        TCard card = tableModel.getCard(row);
        if (card.getCardType() == TCardType.HEADER) {
            return;
        }
        TCard copy = copyCard(card);
        if (openEditDialog(copy)) {
            tableModel.replaceCard(row, copy);
            controller.markDirty();
        }
    }

    private void removeSelectedCard() {
        int row = table.getSelectedRow();
        if (row < 0) {
            return;
        }
        tableModel.removeCard(row);
        controller.markDirty();
    }

    // -------------------------------------------------------------------------
    // CSV import
    // -------------------------------------------------------------------------

    /**
     * Opens a file chooser, reads a CSV file, and shows a preview dialog so the operator
     * can select which rows to import as T-cards.
     *
     * <p>Expected CSV column order (header row optional):
     * {@code name, home agency, home state, phone, type}.<br>
     * If the first row contains text matching column names it is treated as a header and
     * skipped.  The {@code type} column accepts the full card-type label (e.g.
     * {@code "219-5 Personnel"}) or a keyword such as {@code "person"}, {@code "canine"},
     * {@code "drone"}, {@code "crew"}, {@code "engine"}, {@code "helicopter"},
     * {@code "dozer"}.  Unrecognised values default to {@link TCardType#PERSONNEL}.</p>
     */
    private void importFromCsv() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("CSV files (*.csv)", "csv"));
        chooser.setDialogTitle("Select CSV resource file");
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        List<String[]> rows;
        try {
            rows = parseCsv(file);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                    "Could not read file: " + ex.getMessage(),
                    "Import Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (rows.isEmpty()) {
            JOptionPane.showMessageDialog(this, "CSV file is empty.", "Import", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // Detect and skip a header row.
        int dataStart = 0;
        String[] first = rows.get(0);
        if (first.length > 0 && looksLikeHeader(first[0])) {
            dataStart = 1;
        }

        if (dataStart >= rows.size()) {
            JOptionPane.showMessageDialog(this, "No data rows found after the header.", "Import", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // Build preview with checkboxes.
        int dataRows = rows.size() - dataStart;
        String[] colNames = {"Import?", "Name", "Home Agency", "Home State", "Phone", "Type"};
        Object[][] previewData = new Object[dataRows][colNames.length];
        for (int i = 0; i < dataRows; i++) {
            String[] row = rows.get(dataStart + i);
            previewData[i][0] = Boolean.TRUE;
            previewData[i][1] = cell(row, 0);
            previewData[i][2] = cell(row, 1);
            previewData[i][3] = cell(row, 2);
            previewData[i][4] = cell(row, 3);
            previewData[i][5] = cell(row, 4);
        }

        CsvPreviewTableModel previewModel = new CsvPreviewTableModel(previewData, colNames);
        JTable previewTable = new JTable(previewModel);
        previewTable.setRowHeight(22);
        previewTable.getColumnModel().getColumn(0).setMaxWidth(65);
        JScrollPane scroll = new JScrollPane(previewTable);
        scroll.setPreferredSize(new Dimension(620, Math.min(400, dataRows * 25 + 60)));

        int choice = JOptionPane.showConfirmDialog(this, scroll,
                "Select resources to import", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) {
            return;
        }

        int imported = 0;
        for (int i = 0; i < dataRows; i++) {
            if (Boolean.TRUE.equals(previewModel.getValueAt(i, 0))) {
                TCard card = csvRowToCard(
                        (String) previewModel.getValueAt(i, 1),
                        (String) previewModel.getValueAt(i, 2),
                        (String) previewModel.getValueAt(i, 3),
                        (String) previewModel.getValueAt(i, 4),
                        (String) previewModel.getValueAt(i, 5));
                tableModel.addCard(card);
                imported++;
            }
        }
        if (imported > 0) {
            controller.markDirty();
        }
        JOptionPane.showMessageDialog(this, "Imported " + imported + " T-card(s).",
                "Import Complete", JOptionPane.INFORMATION_MESSAGE);
    }

    /** Converts a CSV row's fields into a new T-card. */
    private TCard csvRowToCard(String name, String agency, String state, String phone, String typeStr) {
        TCard card = new TCard();
        card.setPersonName(name == null ? "" : name.trim());
        card.setHomeAgency(agency == null ? "" : agency.trim());
        card.setHomeState(state == null ? "" : state.trim());
        card.setPhoneNumber(phone == null ? "" : phone.trim());
        card.setCardType(inferCardType(typeStr));
        card.setLocation("ICP");
        return card;
    }

    /**
     * Infers a {@link TCardType} from a free-form string.
     *
     * <p>Checks for keyword substrings (case-insensitive) before falling back to
     * {@link TCardType#PERSONNEL}.</p>
     */
    static TCardType inferCardType(String value) {
        if (value == null || value.isBlank()) {
            return TCardType.PERSONNEL;
        }
        String v = value.trim().toLowerCase();
        if (v.contains("canine") || v.contains("handler") || v.contains("219-8") || v.contains("misc")) {
            return TCardType.MISC_EQUIPMENT;
        }
        if (v.contains("drone") || v.contains("uas") || v.contains("uav") || v.contains("aircraft") || v.contains("219-6")) {
            return TCardType.AIRCRAFT;
        }
        if (v.contains("helicopter") || v.contains("219-4")) {
            return TCardType.HELICOPTER;
        }
        if (v.contains("dozer") || v.contains("219-7")) {
            return TCardType.DOZER;
        }
        if (v.contains("engine") || v.contains("219-3")) {
            return TCardType.ENGINE;
        }
        if (v.contains("crew") || v.contains("219-2")) {
            return TCardType.CREW;
        }
        if (v.contains("header") || v.contains("219-1")) {
            return TCardType.HEADER;
        }
        return TCardType.PERSONNEL;
    }

    /** Reads a CSV file and returns rows as string arrays. */
    static List<String[]> parseCsv(File file) throws IOException {
        List<String[]> rows = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (!line.isBlank()) {
                    rows.add(splitCsvLine(line));
                }
            }
        }
        return rows;
    }

    /** Splits a single CSV line respecting double-quoted fields. */
    static String[] splitCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());
        return fields.toArray(new String[0]);
    }

    private static String cell(String[] row, int index) {
        return (row != null && index < row.length) ? row[index].trim() : "";
    }

    private static boolean looksLikeHeader(String value) {
        if (value == null) return false;
        String v = value.trim().toLowerCase();
        return v.contains("name") || v.contains("agency") || v.contains("person");
    }

    // -------------------------------------------------------------------------
    // Card edit dialog
    // -------------------------------------------------------------------------

    /**
     * Opens an edit dialog for the given card.
     *
     * @param card card to edit in-place.
     * @return {@code true} when the user confirmed.
     */
    private boolean openEditDialog(TCard card) {
        JTextField personNameField   = UiSupport.textField();
        JTextField homeAgencyField   = UiSupport.textField();
        JTextField homeStateField    = UiSupport.textField();
        homeStateField.setPreferredSize(new Dimension(48, homeStateField.getPreferredSize().height));
        JTextField phoneField        = UiSupport.textField();
        JTextField resourceIdField   = UiSupport.textField();
        JTextField locationField     = UiSupport.textField();
        JComboBox<String> statusCombo = new JComboBox<>(STATUS_OPTIONS);
        JTextField notesField        = UiSupport.textField();

        SpinnerDateModel checkInModel = new SpinnerDateModel();
        JSpinner checkInSpinner = new JSpinner(checkInModel);
        checkInSpinner.setEditor(new JSpinner.DateEditor(checkInSpinner, "yyyy-MM-dd HH:mm"));

        personNameField.setText(card.getPersonName());
        homeAgencyField.setText(card.getHomeAgency());
        homeStateField.setText(card.getHomeState());
        phoneField.setText(card.getPhoneNumber());
        resourceIdField.setText(card.getResourceIdentifier());
        locationField.setText(card.getLocation());
        statusCombo.setSelectedItem(card.getStatus());
        notesField.setText(card.getNotes());
        if (card.getCheckInDateTime() != null) {
            checkInModel.setValue(Date.from(card.getCheckInDateTime().atZone(ZoneId.systemDefault()).toInstant()));
        }

        JPanel form = UiSupport.formPanel();
        int row = 0;
        if (card.getCardType() == TCardType.PERSONNEL) {
            UiSupport.addRow(form, row++, "Person name",           personNameField);
            UiSupport.addRow(form, row++, "Home agency",           homeAgencyField);
            UiSupport.addRow(form, row++, "Home state (2-letter)", homeStateField);
            UiSupport.addRow(form, row++, "Phone number",          phoneField);
            UiSupport.addRow(form, row++, "Check-in date/time",    checkInSpinner);
        }
        UiSupport.addRow(form, row++, "Resource identifier", resourceIdField);
        UiSupport.addRow(form, row++, "Location (e.g. ICP)", locationField);
        UiSupport.addRow(form, row++, "Status",              statusCombo);
        UiSupport.addRow(form, row,   "Notes",               notesField);

        JScrollPane scroll = new JScrollPane(form);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        String title = "Edit " + card.getCardType().getLabel();
        if (!UiSupport.showResizableConfirmDialog(
                javax.swing.SwingUtilities.getWindowAncestor(this), title, scroll,
                new Dimension(520, 400))) {
            return false;
        }

        card.setPersonName(personNameField.getText().trim());
        card.setHomeAgency(homeAgencyField.getText().trim());
        card.setHomeState(homeStateField.getText().trim());
        card.setPhoneNumber(phoneField.getText().trim());
        Object spinnerVal = checkInSpinner.getValue();
        if (spinnerVal instanceof Date d) {
            card.setCheckInDateTime(LocalDateTime.ofInstant(d.toInstant(), ZoneId.systemDefault()));
        }
        card.setResourceIdentifier(resourceIdField.getText().trim());
        card.setLocation(locationField.getText().trim());
        card.setStatus((String) statusCombo.getSelectedItem());
        card.setNotes(notesField.getText().trim());
        return true;
    }

    private static TCard copyCard(TCard src) {
        TCard copy = new TCard();
        copy.setCardType(src.getCardType());
        copy.setPersonName(src.getPersonName());
        copy.setHomeAgency(src.getHomeAgency());
        copy.setHomeState(src.getHomeState());
        copy.setPhoneNumber(src.getPhoneNumber());
        copy.setCheckInDateTime(src.getCheckInDateTime());
        copy.setResourceIdentifier(src.getResourceIdentifier());
        copy.setLocation(src.getLocation());
        copy.setStatus(src.getStatus());
        copy.setNotes(src.getNotes());
        copy.setSourceRef(src.getSourceRef());
        return copy;
    }

    // -------------------------------------------------------------------------
    // Table model
    // -------------------------------------------------------------------------

    private static final class TCardTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = {
                "Type", "Name / Resource", "Agency", "State", "Phone",
                "Check-In", "Location", "Status"
        };

        private final List<TCard> cards = new ArrayList<>();

        void setCards(List<TCard> newCards) {
            cards.clear();
            cards.addAll(newCards);
            fireTableDataChanged();
        }

        List<TCard> getCards() {
            return new ArrayList<>(cards);
        }

        TCard getCard(int row) {
            return cards.get(row);
        }

        void addCard(TCard card) {
            cards.add(card);
            fireTableRowsInserted(cards.size() - 1, cards.size() - 1);
        }

        void replaceCard(int row, TCard card) {
            cards.set(row, card);
            fireTableRowsUpdated(row, row);
        }

        void removeCard(int row) {
            cards.remove(row);
            fireTableRowsDeleted(row, row);
        }

        @Override public int getRowCount()    { return cards.size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int col) { return COLUMNS[col]; }
        @Override public boolean isCellEditable(int row, int col) { return false; }

        @Override
        public Object getValueAt(int row, int col) {
            TCard card = cards.get(row);
            return switch (col) {
                case 0 -> card.getCardType().getLabel();
                case 1 -> card.getDisplayLabel();
                case 2 -> card.getHomeAgency();
                case 3 -> card.getHomeState();
                case 4 -> card.getPhoneNumber();
                case 5 -> card.getCheckInDateTime() != null
                        ? card.getCheckInDateTime().toString().replace('T', ' ') : "";
                case 6 -> card.getLocation();
                case 7 -> card.getStatus();
                default -> "";
            };
        }
    }

    // -------------------------------------------------------------------------
    // CSV preview table model
    // -------------------------------------------------------------------------

    private static final class CsvPreviewTableModel extends AbstractTableModel {
        private final Object[][] data;
        private final String[] columns;

        CsvPreviewTableModel(Object[][] data, String[] columns) {
            this.data = data;
            this.columns = columns;
        }

        @Override public int getRowCount()    { return data.length; }
        @Override public int getColumnCount() { return columns.length; }
        @Override public String getColumnName(int col) { return columns[col]; }
        @Override public Class<?> getColumnClass(int col) { return col == 0 ? Boolean.class : String.class; }
        @Override public boolean isCellEditable(int row, int col) { return col == 0; }
        @Override public Object getValueAt(int row, int col) { return data[row][col]; }

        @Override
        public void setValueAt(Object value, int row, int col) {
            data[row][col] = value;
            fireTableCellUpdated(row, col);
        }
    }

    // -------------------------------------------------------------------------
    // Cell renderer (table view)
    // -------------------------------------------------------------------------

    private static final class TCardCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int col) {
            Component c = super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, col);
            if (!isSelected && t.getModel() instanceof TCardTableModel model) {
                TCardType type = model.getCard(row).getCardType();
                c.setBackground(cardColor(type));
                c.setForeground(Color.BLACK);
            }
            if (c instanceof JLabel lbl) {
                lbl.setHorizontalAlignment(SwingConstants.LEFT);
            }
            return c;
        }
    }

    static Color cardColor(TCardType type) {
        return switch (type) {
            case HEADER        -> new Color(180, 180, 180);
            case CREW          -> new Color(144, 238, 144);
            case ENGINE        -> new Color(255, 182, 193);
            case HELICOPTER    -> new Color(173, 216, 230);
            case PERSONNEL     -> Color.WHITE;
            case AIRCRAFT      -> new Color(255, 200, 100);
            case DOZER         -> new Color(255, 255, 153);
            case MISC_EQUIPMENT -> new Color(240, 220, 180);
        };
    }
}
