package org.sarmanagement.icsforms.ui;

import org.sarmanagement.icsforms.model.SarTaskAssignment;
import org.sarmanagement.icsforms.model.TCard;
import org.sarmanagement.icsforms.model.TCardType;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    /** Currently selected card in rack view; {@code null} when nothing is selected. */
    private TCard selectedRackCard = null;
    /** Assignment ID → team number, used to label task groups in table and rack views. */
    private final Map<String, String> assignmentTeamByRef = new LinkedHashMap<>();

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
        JButton addHeaderBtn    = new JButton("Add Header Card");
        JButton editBtn        = new JButton("Edit Selected…");
        JButton removeBtn      = new JButton("Remove Selected");
        JButton importCsvBtn   = new JButton("Import from CSV…");

        addPersonnelBtn.addActionListener(e -> addPersonnelCard());
        addHeaderBtn.addActionListener(e -> addHeaderCard());
        editBtn.addActionListener(e -> editSelectedCard());
        removeBtn.addActionListener(e -> removeSelectedCard());
        importCsvBtn.addActionListener(e -> importFromCsv());
        toggleViewBtn.addActionListener(e -> toggleView());

        buttonRow.add(addPersonnelBtn);
        buttonRow.add(addHeaderBtn);
        buttonRow.add(editBtn);
        buttonRow.add(removeBtn);
        buttonRow.add(importCsvBtn);
        buttonRow.add(toggleViewBtn);

        installTablePopupMenu();

        add(viewContainer, BorderLayout.CENTER);
        add(buttonRow, BorderLayout.SOUTH);
    }

    /** Installs a right-click context menu on the T-card table mirroring the bottom buttons. */
    private void installTablePopupMenu() {
        JPopupMenu popup = new JPopupMenu();
        JMenuItem editItem   = new JMenuItem("Edit Selected…");
        JMenuItem removeItem = new JMenuItem("Remove Selected");
        editItem.addActionListener(e -> editSelectedCard());
        removeItem.addActionListener(e -> removeSelectedCard());
        popup.add(editItem);
        popup.add(removeItem);
        table.setComponentPopupMenu(popup);
        table.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { selectRowAt(e); }
            @Override public void mouseReleased(MouseEvent e) { selectRowAt(e); }
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && javax.swing.SwingUtilities.isLeftMouseButton(e)) {
                    selectRowAt(e);
                    editSelectedCard();
                }
            }
            private void selectRowAt(MouseEvent e) {
                int row = table.rowAtPoint(e.getPoint());
                if (row >= 0) table.setRowSelectionInterval(row, row);
            }
        });
    }

    /**
     * Reloads table rows from the model.
     */
    public void refreshFromModel() {
        // Build assignment ID → team number map for task grouping.
        assignmentTeamByRef.clear();
        for (SarTaskAssignment task : controller.getData().getSarTaskAssignments()) {
            if (!task.getAssignmentId().isBlank()) {
                assignmentTeamByRef.put(task.getAssignmentId(), task.getAssignmentTeamNumber());
            }
        }
        tableModel.setAssignmentTeamByRef(new LinkedHashMap<>(assignmentTeamByRef));
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
            selectedRackCard = null;
            rebuildRackView();
            viewLayout.show(viewContainer, VIEW_RACK);
        } else {
            currentView = VIEW_TABLE;
            toggleViewBtn.setText("Rack View");
            selectedRackCard = null;
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
     * Builds a rack panel where each HEADER card is a column heading and resource cards are
     * placed into the column whose label best matches the card's status or location.
     *
     * <p>Within the "Assigned" column, cards are further sub-grouped by SAR task assignment.
     * Canine/equipment cards with a {@code handlerName} are rendered immediately below
     * their handler's personnel card in every column.</p>
     */
    private JPanel buildHeaderBasedRack(List<TCard> cards) {
        // Collect HEADER cards in order.
        List<TCard> sectionHeaders = new ArrayList<>();
        for (TCard card : cards) {
            if (card.getCardType() == TCardType.HEADER) {
                sectionHeaders.add(card);
            }
        }

        // For each HEADER build a mutable child list.
        List<List<TCard>> sectionCards = new ArrayList<>();
        for (int i = 0; i < sectionHeaders.size(); i++) {
            sectionCards.add(new ArrayList<>());
        }

        // Determine the default column index ("Available" header, or 0 if absent).
        int defaultColIndex = 0;
        for (int i = 0; i < sectionHeaders.size(); i++) {
            if ("Available".equalsIgnoreCase(sectionHeaders.get(i).getDisplayLabel())) {
                defaultColIndex = i;
                break;
            }
        }

        // Place each non-HEADER card into the best-matching column.
        for (TCard card : cards) {
            if (card.getCardType() == TCardType.HEADER) {
                continue;
            }
            int colIndex = defaultColIndex;
            String matchStatus   = card.getStatus()   == null ? "" : card.getStatus();
            String matchLocation = card.getLocation() == null ? "" : card.getLocation();
            boolean matched = false;
            for (int i = 0; i < sectionHeaders.size(); i++) {
                String headerLabel = sectionHeaders.get(i).getDisplayLabel();
                if (!matchStatus.isBlank() && headerLabel.equalsIgnoreCase(matchStatus)) {
                    colIndex = i;
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                for (int i = 0; i < sectionHeaders.size(); i++) {
                    String headerLabel = sectionHeaders.get(i).getDisplayLabel();
                    if (!matchLocation.isBlank() && headerLabel.equalsIgnoreCase(matchLocation)) {
                        colIndex = i;
                        break;
                    }
                }
            }
            sectionCards.get(colIndex).add(card);
        }

        // Build list of visible columns: filter out "Enroute" and "Ordered" headers
        // when they have no resource cards assigned to them.
        List<TCard> visibleHeaders = new ArrayList<>();
        List<List<TCard>> visibleCards = new ArrayList<>();
        for (int i = 0; i < sectionHeaders.size(); i++) {
            String label = sectionHeaders.get(i).getDisplayLabel();
            boolean hiddenWhenEmpty = "Enroute".equalsIgnoreCase(label)
                    || "Ordered".equalsIgnoreCase(label);
            if (hiddenWhenEmpty && sectionCards.get(i).isEmpty()) {
                continue;
            }
            visibleHeaders.add(sectionHeaders.get(i));
            visibleCards.add(sectionCards.get(i));
        }

        int cols = Math.max(1, visibleHeaders.size());
        JPanel rack = new JPanel(new GridLayout(1, cols, 6, 0));
        rack.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        for (int i = 0; i < visibleHeaders.size(); i++) {
            TCard header = visibleHeaders.get(i);
            List<TCard> children = visibleCards.get(i);

            JPanel col = new JPanel();
            col.setLayout(new javax.swing.BoxLayout(col, javax.swing.BoxLayout.Y_AXIS));

            col.add(buildHeaderCardWidget(header));
            col.add(javax.swing.Box.createVerticalStrut(4));

            // Check if this is the "Assigned" column — if so, sub-group by task.
            String colLabel = header.getDisplayLabel();
            if ("Assigned".equalsIgnoreCase(colLabel)) {
                addTaskGroupedCards(col, children);
            } else {
                addHandlerGroupedCards(col, children);
            }
            col.add(javax.swing.Box.createVerticalGlue());
            rack.add(col);
        }
        return rack;
    }

    /**
     * Adds resource cards to a column panel, grouped by SAR task assignment.
     *
     * <p>Cards with a {@code sourceRef} beginning with {@code "sar:<assignmentId>:"}
     * are clustered under a small task-label banner.  Cards linked to the same
     * handler are kept together within each group.</p>
     */
    private void addTaskGroupedCards(JPanel col, List<TCard> children) {
        // Group children by assignment ID (prefix of sourceRef "sar:<id>:...").
        Map<String, List<TCard>> byTask = new LinkedHashMap<>();
        byTask.put("", new ArrayList<>()); // unnamed / non-task cards
        for (TCard card : children) {
            String ref = card.getSourceRef();
            String taskKey = "";
            if (ref.startsWith("sar:")) {
                String[] parts = ref.split(":", 3);
                // parts[1] is the assignment ID; guard against "sar:" with no ID.
                if (parts.length >= 2 && !parts[1].isBlank()) {
                    taskKey = parts[1];
                }
            }
            byTask.computeIfAbsent(taskKey, k -> new ArrayList<>()).add(card);
        }

        // Render non-task cards first, then each task group.
        List<TCard> unassigned = byTask.remove("");
        if (unassigned != null && !unassigned.isEmpty()) {
            addHandlerGroupedCards(col, unassigned);
        }
        for (Map.Entry<String, List<TCard>> entry : byTask.entrySet()) {
            String teamLabel = assignmentTeamByRef.getOrDefault(entry.getKey(), entry.getKey());
            if (!teamLabel.isBlank()) {
                col.add(buildTaskBannerWidget(teamLabel));
                col.add(javax.swing.Box.createVerticalStrut(2));
            }
            addHandlerGroupedCards(col, entry.getValue());
            col.add(javax.swing.Box.createVerticalStrut(4));
        }
    }

    /**
     * Adds resource cards to a column panel, with canine/equipment cards grouped
     * immediately below their handler's personnel card.
     */
    private void addHandlerGroupedCards(JPanel col, List<TCard> cards) {
        // Separate handler-linked (canine) cards from the rest.
        Map<String, List<TCard>> caninesByHandler = new LinkedHashMap<>();
        List<TCard> topLevel = new ArrayList<>();
        for (TCard card : cards) {
            String handler = card.getHandlerName();
            if (!handler.isBlank()) {
                caninesByHandler.computeIfAbsent(handler, k -> new ArrayList<>()).add(card);
            } else {
                topLevel.add(card);
            }
        }

        for (TCard card : topLevel) {
            col.add(buildRackCard(card));
            col.add(javax.swing.Box.createVerticalStrut(3));
            // Append any linked canines directly below.
            String personName = card.getPersonName();
            List<TCard> linked = caninesByHandler.get(personName);
            if (linked != null) {
                for (TCard canine : linked) {
                    col.add(buildCanineRackCard(canine));
                    col.add(javax.swing.Box.createVerticalStrut(2));
                }
                caninesByHandler.remove(personName);
            }
        }

        // Any canines whose handler card isn't in this column — render standalone.
        for (List<TCard> orphaned : caninesByHandler.values()) {
            for (TCard card : orphaned) {
                col.add(buildRackCard(card));
                col.add(javax.swing.Box.createVerticalStrut(3));
            }
        }
    }

    /** Builds a narrow task-label banner for sub-grouping within the Assigned column. */
    private JPanel buildTaskBannerWidget(String teamLabel) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(new Color(200, 220, 255));
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(100, 140, 220), 1),
                BorderFactory.createEmptyBorder(2, 6, 2, 6)));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
        JLabel lbl = new JLabel("⊳ " + teamLabel);
        lbl.setFont(lbl.getFont().deriveFont(Font.BOLD, 10.5f));
        lbl.setForeground(new Color(30, 60, 130));
        p.add(lbl, BorderLayout.CENTER);
        return p;
    }

    /**
     * Builds a rack panel grouped by card type — used when no HEADER cards are present.
     */
    private JPanel buildTypeBasedRack(List<TCard> allCards) {
        List<TCardType> typeOrder = Arrays.asList(TCardType.values());
        Map<TCardType, List<TCard>> byType = new LinkedHashMap<>();
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

    /** Builds the compact grey column-heading widget for a HEADER card in the rack view. */
    private JPanel buildHeaderCardWidget(TCard header) {
        JPanel p = new JPanel(new BorderLayout(2, 0));
        p.setBackground(cardColor(TCardType.HEADER));
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.DARK_GRAY, 2),
                BorderFactory.createEmptyBorder(3, 6, 3, 6)));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

        JLabel titleLabel = new JLabel(header.getDisplayLabel());
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 12f));
        p.add(titleLabel, BorderLayout.CENTER);
        return p;
    }

    /**
     * Builds a single narrow resource card widget for the rack view, with a mouse listener
     * that marks the card as selected and enables "Edit Selected…".
     */
    private JPanel buildRackCard(TCard card) {
        return buildRackCardWidget(card, false);
    }

    /** Builds a slightly narrower canine/equipment card indented below a handler card. */
    private JPanel buildCanineRackCard(TCard card) {
        return buildRackCardWidget(card, true);
    }

    private JPanel buildRackCardWidget(TCard card, boolean indented) {
        JPanel p = new JPanel(new BorderLayout(2, 2));
        p.setBackground(cardColor(card.getCardType()));
        boolean isSelected = card == selectedRackCard;
        p.setBorder(BorderFactory.createCompoundBorder(
                isSelected
                        ? BorderFactory.createLineBorder(new Color(0, 100, 200), 2)
                        : BorderFactory.createLineBorder(Color.DARK_GRAY),
                BorderFactory.createEmptyBorder(3, indented ? 12 : 5, 3, 5)));
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 55));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        // Store card reference so click handler can retrieve it.
        p.putClientProperty("tcard", card);

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

        p.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                selectRackCard(card);
            }
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && javax.swing.SwingUtilities.isLeftMouseButton(e)) {
                    editSelectedCard();
                }
            }
        });
        return p;
    }

    /**
     * Selects a card in the rack view and repaints the rack to show the new selection.
     */
    private void selectRackCard(TCard card) {
        selectedRackCard = card;
        rebuildRackView();
    }



    private void addPersonnelCard() {
        TCard card = new TCard();
        card.setCardType(TCardType.PERSONNEL);
        if (openEditDialog(card)) {
            tableModel.addCard(card);
            controller.markDirty();
        }
    }

    private void addHeaderCard() {
        TCard card = new TCard();
        card.setCardType(TCardType.HEADER);
        if (openEditDialog(card)) {
            tableModel.addCard(card);
            controller.markDirty();
        }
    }

    private void editSelectedCard() {
        if (currentView.equals(VIEW_RACK)) {
            if (selectedRackCard == null) {
                return;
            }
            TCard copy = copyCard(selectedRackCard);
            if (openEditDialog(copy)) {
                List<TCard> cards = tableModel.getCards();
                for (int i = 0; i < cards.size(); i++) {
                    if (cards.get(i) == selectedRackCard) {
                        tableModel.replaceCard(i, copy);
                        break;
                    }
                }
                selectedRackCard = copy;
                controller.markDirty();
                rebuildRackView();
            }
            return;
        }
        int row = table.getSelectedRow();
        if (row < 0) {
            return;
        }
        TCard card = tableModel.getCard(row);
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
        JTextField personNameField    = UiSupport.textField();
        JTextField homeAgencyField    = UiSupport.textField();
        JTextField homeStateField     = UiSupport.textField();
        homeStateField.setPreferredSize(new Dimension(48, homeStateField.getPreferredSize().height));
        JTextField phoneField         = UiSupport.textField();
        JTextField radioChannelField  = UiSupport.textField();
        JTextField handlerNameField   = UiSupport.textField();
        JTextField resourceIdField    = UiSupport.textField();
        JTextField locationField      = UiSupport.textField();
        JComboBox<String> statusCombo = new JComboBox<>(STATUS_OPTIONS);
        JTextField notesField         = UiSupport.textField();

        SpinnerDateModel checkInModel = new SpinnerDateModel();
        JSpinner checkInSpinner = new JSpinner(checkInModel);
        checkInSpinner.setEditor(new JSpinner.DateEditor(checkInSpinner, "yyyy-MM-dd HH:mm"));

        personNameField.setText(card.getPersonName());
        homeAgencyField.setText(card.getHomeAgency());
        homeStateField.setText(card.getHomeState());
        phoneField.setText(card.getPhoneNumber());
        radioChannelField.setText(card.getRadioChannel());
        handlerNameField.setText(card.getHandlerName());
        resourceIdField.setText(card.getResourceIdentifier());
        locationField.setText(card.getLocation());
        statusCombo.setSelectedItem(card.getStatus());
        notesField.setText(card.getNotes());
        if (card.getCheckInDateTime() != null) {
            checkInModel.setValue(Date.from(card.getCheckInDateTime().atZone(ZoneId.systemDefault()).toInstant()));
        }

        JPanel form = UiSupport.formPanel();
        int row = 0;
        if (card.getCardType() == TCardType.HEADER) {
            UiSupport.addRow(form, row++, "Heading text (column label)", resourceIdField);
            UiSupport.addRow(form, row++, "Location note",               locationField);
            UiSupport.addRow(form, row,   "Notes",                       notesField);
        } else {
            if (card.getCardType() == TCardType.PERSONNEL) {
                UiSupport.addRow(form, row++, "Person name",              personNameField);
                UiSupport.addRow(form, row++, "Home agency",              homeAgencyField);
                UiSupport.addRow(form, row++, "Home state (2-letter)",    homeStateField);
                UiSupport.addRow(form, row++, "Phone number",             phoneField);
                UiSupport.addRow(form, row++, "Radio channel/talkgroup",  radioChannelField);
                UiSupport.addRow(form, row++, "Check-in date/time",       checkInSpinner);
            } else {
                // Non-personnel resource cards (canine, drone, etc.) may name their handler.
                UiSupport.addRow(form, row++, "Handler/operator name",    handlerNameField);
            }
            UiSupport.addRow(form, row++, "Resource identifier", resourceIdField);
            UiSupport.addRow(form, row++, "Location (e.g. ICP)", locationField);
            UiSupport.addRow(form, row++, "Status",              statusCombo);
            UiSupport.addRow(form, row,   "Notes",               notesField);
        }

        JScrollPane scroll = new JScrollPane(form);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        String title = "Edit " + card.getCardType().getLabel();
        if (!UiSupport.showResizableConfirmDialog(
                javax.swing.SwingUtilities.getWindowAncestor(this), title, scroll,
                new Dimension(520, 420))) {
            return false;
        }

        if (card.getCardType() == TCardType.HEADER) {
            card.setResourceIdentifier(resourceIdField.getText().trim());
            card.setLocation(locationField.getText().trim());
            card.setNotes(notesField.getText().trim());
        } else {
            if (card.getCardType() == TCardType.PERSONNEL) {
                card.setPersonName(personNameField.getText().trim());
                card.setHomeAgency(homeAgencyField.getText().trim());
                card.setHomeState(homeStateField.getText().trim());
                card.setPhoneNumber(phoneField.getText().trim());
                card.setRadioChannel(radioChannelField.getText().trim());
                Object spinnerVal = checkInSpinner.getValue();
                if (spinnerVal instanceof Date d) {
                    card.setCheckInDateTime(LocalDateTime.ofInstant(d.toInstant(), ZoneId.systemDefault()));
                }
            } else {
                card.setHandlerName(handlerNameField.getText().trim());
            }
            card.setResourceIdentifier(resourceIdField.getText().trim());
            card.setLocation(locationField.getText().trim());
            card.setStatus((String) statusCombo.getSelectedItem());
            card.setNotes(notesField.getText().trim());
        }
        return true;
    }

    private static TCard copyCard(TCard src) {
        TCard copy = new TCard();
        copy.setCardType(src.getCardType());
        copy.setPersonName(src.getPersonName());
        copy.setHomeAgency(src.getHomeAgency());
        copy.setHomeState(src.getHomeState());
        copy.setPhoneNumber(src.getPhoneNumber());
        copy.setRadioChannel(src.getRadioChannel());
        copy.setCheckInDateTime(src.getCheckInDateTime());
        copy.setResourceIdentifier(src.getResourceIdentifier());
        copy.setLocation(src.getLocation());
        copy.setStatus(src.getStatus());
        copy.setNotes(src.getNotes());
        copy.setSourceRef(src.getSourceRef());
        copy.setHandlerName(src.getHandlerName());
        return copy;
    }

    // -------------------------------------------------------------------------
    // Table model
    // -------------------------------------------------------------------------

    private static final class TCardTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = {
                "Type", "Name / Resource", "Agency", "State", "Phone",
                "Check-In", "Location", "Status", "Task"
        };

        private final List<TCard> cards = new ArrayList<>();
        private Map<String, String> assignmentTeamByRef = new LinkedHashMap<>();

        void setAssignmentTeamByRef(Map<String, String> map) {
            this.assignmentTeamByRef = map == null ? new LinkedHashMap<>() : map;
            fireTableDataChanged();
        }

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
                case 8 -> {
                    String ref = card.getSourceRef();
                    if (ref.startsWith("sar:")) {
                        String[] parts = ref.split(":", 3);
                        if (parts.length >= 2 && !parts[1].isBlank()) {
                            String teamNum = assignmentTeamByRef.get(parts[1]);
                            yield teamNum != null ? teamNum : "";
                        }
                    }
                    yield "";
                }
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
