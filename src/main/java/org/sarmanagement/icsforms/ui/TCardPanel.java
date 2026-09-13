package org.sarmanagement.icsforms.ui;

import org.sarmanagement.icsforms.model.SarTaskAssignment;
import org.sarmanagement.icsforms.model.SarTaskResource;
import org.sarmanagement.icsforms.model.ResourceDirectoryEntry;
import org.sarmanagement.icsforms.model.ResourceDirectorySource;
import org.sarmanagement.icsforms.model.TCard;
import org.sarmanagement.icsforms.model.TCardType;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButton;
import javax.swing.RowFilter;
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
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Panel displaying and editing ICS 219 T-Card (Resource Status Card) records.
 *
 * <p>Cards may be displayed in a sortable table, a colour-coded rack view that mimics a
 * physical T-card rack, or a personnel directory view. Personnel cards linked from the org
 * chart or SAR tasks are created automatically by {@link AppController#syncTCards()};
 * manual add and CSV import are also available.</p>
 */
public class TCardPanel extends JPanel {
    private static final String[] STATUS_OPTIONS = {
            "", "Requested", "Enroute", "At Staging", "Assigned", "Out of Service"
    };
    private static final String VIEW_TABLE = "table";
    private static final String VIEW_RACK  = "rack";
    private static final String VIEW_DIRECTORY = "directory";
    private static final int DIRECTORY_LARGE_FONT_THRESHOLD = 8;
    private static final float DIRECTORY_DEFAULT_FONT_SIZE = 12f;
    private static final float DIRECTORY_LARGE_FONT_SIZE = 15f;

    private final AppController controller;
    private final TCardTableModel tableModel;
    private final JTable table;
    private final DirectoryTableModel directoryTableModel;
    private final JTable directoryTable;
    private final TableRowSorter<DirectoryTableModel> directorySorter;
    private final CardLayout viewLayout = new CardLayout();
    private final JPanel viewContainer = new JPanel(viewLayout);
    private final JScrollPane rackScroll = new JScrollPane();
    private final JComboBox<String> viewSelector = new JComboBox<>(new String[]{
            "Table View", "Rack View", "Directory View"
    });
    private final JTextField directoryNameFilterField = UiSupport.textField();
    private final JComboBox<String> directoryPositionFilter = new JComboBox<>(new String[]{"All"});
    private final JComboBox<String> directoryStateFilter = new JComboBox<>(new String[]{"All"});
    private final JComboBox<String> directoryUnitFilter = new JComboBox<>(new String[]{"All"});
    private final JComboBox<String> directoryAssignmentFilter = new JComboBox<>(new String[]{"All"});
    private final Font directoryBaseFont;
    private String currentView = VIEW_TABLE;
    /** Currently selected card in rack view; {@code null} when nothing is selected. */
    private TCard selectedRackCard = null;
    /** Assignment ID → team number, used to label task groups in table and rack views. */
    private final Map<String, String> assignmentTeamByRef = new LinkedHashMap<>();
    /** Assignment ID → resource identifier, used in rack view task banners. */
    private final Map<String, String> assignmentResIdByRef = new LinkedHashMap<>();
    /** Set of assignment task keys (from sourceRef) whose rack-view group is collapsed. */
    private final java.util.Set<String> collapsedTaskKeys = new java.util.HashSet<>();
    /** Maps each T-card to its rack-view widget panel for in-place border updates without full rebuild. */
    private final Map<TCard, JPanel> rackCardWidgets = new IdentityHashMap<>();
    /**
     * Snapshot of all SAR task assignments keyed by assignment ID, refreshed on each
     * {@link #refreshFromModel()} call.  Used in the rack to detect when a resource
     * appears in multiple task assignments and render busy-indicator cards.
     */
    private final Map<String, SarTaskAssignment> sarTaskByAssignmentId = new LinkedHashMap<>();
    private java.util.function.Consumer<String> onEditSarAssignmentRequest;

    /** Returns {@code true} when the incident is running in SAR mode. */
    private boolean isSarMode() {
        return controller.getIncidentMode() == org.sarmanagement.icsforms.model.IncidentMode.SAR;
    }

    public void setOnEditSarAssignmentRequest(java.util.function.Consumer<String> handler) {
        this.onEditSarAssignmentRequest = handler;
    }

    /**
     * Returns a {@link javax.swing.ListCellRenderer} for {@link TCardType} combo boxes that
     * displays the SAR-mode label when the incident is in SAR mode.
     */
    private javax.swing.DefaultListCellRenderer tCardTypeRenderer() {
        return new javax.swing.DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(
                    javax.swing.JList<?> list, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof TCardType t) {
                    setText(t.getLabel(isSarMode()));
                }
                return this;
            }
        };
    }

    /**
     * Creates the T-card panel.
     *
     * @param controller application controller.
     */
    public TCardPanel(AppController controller) {
        super(new BorderLayout());
        this.controller = controller;
        this.tableModel = new TCardTableModel(this::isSarMode);
        this.table = new JTable(tableModel);
        this.directoryTableModel = new DirectoryTableModel();
        this.directoryTable = new JTable(directoryTableModel);
        this.directorySorter = new TableRowSorter<>(directoryTableModel);
        this.directoryBaseFont = directoryTable.getFont();
        setBorder(BorderFactory.createTitledBorder("T-Cards (ICS 219 Resource Status)"));

        table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        table.setFillsViewportHeight(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(22);
        table.setDefaultRenderer(Object.class, new TCardCellRenderer());
        configureDirectoryTable();

        viewContainer.add(new JScrollPane(table), VIEW_TABLE);
        viewContainer.add(rackScroll, VIEW_RACK);
        viewContainer.add(buildDirectoryPanel(), VIEW_DIRECTORY);

        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        JButton addCardBtn       = new JButton("Add Card…");
        JButton editBtn          = new JButton("Edit Selected…");
        JButton removeBtn        = new JButton("Remove Selected");
        JButton mergeDupBtn      = new JButton("Merge Duplicates…");
        JButton importCsvBtn     = new JButton("Import from CSV…");

        addCardBtn.addActionListener(e -> addCardWithTypeChoice());
        editBtn.addActionListener(e -> editSelectedCard());
        removeBtn.addActionListener(e -> removeSelectedCard());
        mergeDupBtn.addActionListener(e -> mergeDuplicates());
        importCsvBtn.addActionListener(e -> importFromCsv());
        viewSelector.addActionListener(e -> switchView(selectedViewKey()));

        buttonRow.add(new JLabel("View:"));
        buttonRow.add(viewSelector);
        buttonRow.add(addCardBtn);
        buttonRow.add(editBtn);
        buttonRow.add(removeBtn);
        buttonRow.add(mergeDupBtn);
        buttonRow.add(importCsvBtn);

        installTablePopupMenu();

        add(viewContainer, BorderLayout.CENTER);
        add(buttonRow, BorderLayout.SOUTH);
    }

    private void configureDirectoryTable() {
        directoryTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        directoryTable.setFillsViewportHeight(true);
        directoryTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        directoryTable.setRowHeight(24);
        directoryTable.setRowSorter(directorySorter);
        directorySorter.setComparator(0, ResourceDirectorySource.displayNameComparator());
        directorySorter.setSortsOnUpdates(true);
        directorySorter.setSortKeys(List.of(new javax.swing.RowSorter.SortKey(0, javax.swing.SortOrder.ASCENDING)));
        directoryTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = directoryTable.rowAtPoint(e.getPoint());
                if (row >= 0) {
                    directoryTable.setRowSelectionInterval(row, row);
                }
                if (e.getClickCount() == 2 && javax.swing.SwingUtilities.isLeftMouseButton(e)) {
                    editSelectedCard();
                }
            }
        });
    }

    private JPanel buildDirectoryPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.add(buildDirectoryFilters(), BorderLayout.NORTH);
        panel.add(new JScrollPane(directoryTable), BorderLayout.CENTER);
        installDirectoryFilterListeners();
        return panel;
    }

    private JPanel buildDirectoryFilters() {
        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        directoryNameFilterField.setColumns(14);
        filters.add(new JLabel("Name:"));
        filters.add(directoryNameFilterField);
        filters.add(new JLabel("Assigned position:"));
        filters.add(directoryPositionFilter);
        filters.add(new JLabel("State:"));
        filters.add(directoryStateFilter);
        filters.add(new JLabel("Unit:"));
        filters.add(directoryUnitFilter);
        filters.add(new JLabel("Assignment:"));
        filters.add(directoryAssignmentFilter);
        return filters;
    }

    private void installDirectoryFilterListeners() {
        javax.swing.event.DocumentListener docListener = new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { applyDirectoryFilters(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { applyDirectoryFilters(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { applyDirectoryFilters(); }
        };
        directoryNameFilterField.getDocument().addDocumentListener(docListener);
        directoryPositionFilter.addActionListener(e -> applyDirectoryFilters());
        directoryStateFilter.addActionListener(e -> applyDirectoryFilters());
        directoryUnitFilter.addActionListener(e -> applyDirectoryFilters());
        directoryAssignmentFilter.addActionListener(e -> applyDirectoryFilters());
    }

    /** Installs a right-click context menu on the T-card table mirroring the bottom buttons. */
    private void installTablePopupMenu() {
        JPopupMenu popup = new JPopupMenu();
        JMenuItem addItem    = new JMenuItem("Add Card…");
        JMenuItem editItem   = new JMenuItem("Edit Selected…");
        JMenuItem removeItem = new JMenuItem("Remove Selected");
        addItem.addActionListener(e -> addCardWithTypeChoice());
        editItem.addActionListener(e -> editSelectedCard());
        removeItem.addActionListener(e -> removeSelectedCard());
        popup.add(addItem);
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
        assignmentResIdByRef.clear();
        sarTaskByAssignmentId.clear();
        for (SarTaskAssignment task : controller.getData().getSarTaskAssignments()) {
            if (!task.getAssignmentId().isBlank()) {
                assignmentTeamByRef.put(task.getAssignmentId(), task.getAssignmentTeamNumber());
                assignmentResIdByRef.put(task.getAssignmentId(), task.getResourceIdentifier());
                sarTaskByAssignmentId.put(task.getAssignmentId(), task);
            }
        }
        tableModel.setAssignmentTeamByRef(new LinkedHashMap<>(assignmentTeamByRef));
        tableModel.setCards(new ArrayList<>(controller.getData().getTCards()));
        refreshDerivedViewsFromTableModel(null);
    }

    /**
     * Applies table edits back to the model.
     */
    public void pushToModel() {
        controller.getData().setTCards(new ArrayList<>(tableModel.getCards()));
    }

    // -------------------------------------------------------------------------
    // View switching
    // -------------------------------------------------------------------------

    private void switchView(String viewKey) {
        currentView = viewKey;
        selectedRackCard = null;
        if (VIEW_RACK.equals(currentView)) {
            rebuildRackView();
        }
        viewLayout.show(viewContainer, currentView);
    }

    private String selectedViewKey() {
        Object selection = viewSelector.getSelectedItem();
        if ("Rack View".equals(selection)) {
            return VIEW_RACK;
        }
        if ("Directory View".equals(selection)) {
            return VIEW_DIRECTORY;
        }
        return VIEW_TABLE;
    }

    private void refreshDerivedViewsFromTableModel() {
        refreshDerivedViewsFromTableModel(null);
    }

    private void refreshDerivedViewsFromTableModel(TCard preferredDirectorySelection) {
        TCard directorySelection = VIEW_DIRECTORY.equals(currentView)
                ? (preferredDirectorySelection != null ? preferredDirectorySelection : selectedCard())
                : null;
        directoryTableModel.setEntries(ResourceDirectorySource.build(controller.getData(), tableModel.getCards()));
        refreshDirectoryFilterChoices();
        updateDirectoryColumnSizing();
        applyDirectoryFilters();
        restoreDirectorySelection(directorySelection);
        if (VIEW_RACK.equals(currentView)) {
            rebuildRackView();
        }
    }

    private void restoreDirectorySelection(TCard selectedCard) {
        if (!VIEW_DIRECTORY.equals(currentView) || selectedCard == null) {
            directoryTable.clearSelection();
            return;
        }
        for (int viewRow = 0; viewRow < directoryTable.getRowCount(); viewRow++) {
            int modelRow = directoryTable.convertRowIndexToModel(viewRow);
            if (directoryTableModel.getEntry(modelRow).card() == selectedCard) {
                directoryTable.setRowSelectionInterval(viewRow, viewRow);
                return;
            }
        }
        directoryTable.clearSelection();
    }

    private void refreshDirectoryFilterChoices() {
        refreshFilterCombo(directoryPositionFilter, directoryTableModel.distinctAssignedPositions());
        refreshFilterCombo(directoryStateFilter, directoryTableModel.distinctStates());
        refreshFilterCombo(directoryUnitFilter, directoryTableModel.distinctUnits());
        refreshFilterCombo(directoryAssignmentFilter, directoryTableModel.distinctAssignments());
    }

    private void refreshFilterCombo(JComboBox<String> combo, List<String> values) {
        Object previous = combo.getSelectedItem();
        combo.removeAllItems();
        combo.addItem("All");
        for (String value : values) {
            combo.addItem(value);
        }
        if (previous != null && hasComboValue(combo, previous.toString())) {
            combo.setSelectedItem(previous);
        } else {
            combo.setSelectedIndex(0);
        }
    }

    private boolean hasComboValue(JComboBox<String> combo, String value) {
        for (int i = 0; i < combo.getItemCount(); i++) {
            if (value.equals(combo.getItemAt(i))) {
                return true;
            }
        }
        return false;
    }

    private void applyDirectoryFilters() {
        List<RowFilter<DirectoryTableModel, Integer>> filters = new ArrayList<>();
        String nameFilter = directoryNameFilterField.getText() == null
                ? "" : directoryNameFilterField.getText().trim().toLowerCase(Locale.ROOT);
        if (!nameFilter.isBlank()) {
            filters.add(new RowFilter<>() {
                @Override
                public boolean include(Entry<? extends DirectoryTableModel, ? extends Integer> entry) {
                    ResourceDirectoryEntry row = directoryTableModel.getEntry(entry.getIdentifier());
                    return row.name().toLowerCase(Locale.ROOT).contains(nameFilter);
                }
            });
        }
        addExactFilter(filters, directoryStateFilter, ResourceDirectoryEntry::state);
        addExactFilter(filters, directoryPositionFilter, ResourceDirectoryEntry::assignedPosition);
        addExactFilter(filters, directoryUnitFilter, ResourceDirectoryEntry::unit);
        addExactFilter(filters, directoryAssignmentFilter, ResourceDirectoryEntry::assignment);
        directorySorter.setRowFilter(filters.isEmpty() ? null : RowFilter.andFilter(filters));
        updateDirectoryReadability(directoryTable.getRowCount());
    }

    private void addExactFilter(List<RowFilter<DirectoryTableModel, Integer>> filters,
                                JComboBox<String> combo,
                                java.util.function.Function<ResourceDirectoryEntry, String> extractor) {
        Object selected = combo.getSelectedItem();
        if (!(selected instanceof String text) || text.isBlank() || "All".equals(text)) {
            return;
        }
        filters.add(new RowFilter<>() {
            @Override
            public boolean include(Entry<? extends DirectoryTableModel, ? extends Integer> entry) {
                return text.equalsIgnoreCase(extractor.apply(directoryTableModel.getEntry(entry.getIdentifier())));
            }
        });
    }

    private void updateDirectoryReadability(int resultCount) {
        boolean large = useLargeDirectoryFont(resultCount);
        directoryTable.setFont(directoryBaseFont.deriveFont(large
                ? DIRECTORY_LARGE_FONT_SIZE : DIRECTORY_DEFAULT_FONT_SIZE));
        directoryTable.setRowHeight(large ? 30 : 24);
        updateDirectoryColumnSizing();
    }

    private void updateDirectoryColumnSizing() {
        if (directoryTable.getColumnModel().getColumnCount() != directoryTableModel.getColumnCount()) {
            return;
        }
        java.awt.FontMetrics metrics = directoryTable.getFontMetrics(directoryTable.getFont());
        for (int columnIndex = 0; columnIndex < directoryTableModel.getColumnCount(); columnIndex++) {
            int width = metrics.stringWidth(directoryTableModel.getColumnName(columnIndex)) + 24;
            for (int rowIndex = 0; rowIndex < directoryTableModel.getRowCount(); rowIndex++) {
                Object value = directoryTableModel.getValueAt(rowIndex, columnIndex);
                width = Math.max(width, metrics.stringWidth(value == null ? "" : value.toString()) + 24);
            }
            if (columnIndex == 1) {
                width = Math.max(width, 220);
            }
            directoryTable.getColumnModel().getColumn(columnIndex).setPreferredWidth(width);
        }
    }

    static boolean useLargeDirectoryFont(int resultCount) {
        return resultCount > 0 && resultCount <= DIRECTORY_LARGE_FONT_THRESHOLD;
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
        rackCardWidgets.clear();
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

            // Group Available and Assigned columns by task; other columns stay handler-grouped.
            String colLabel = header.getDisplayLabel();
            if ("Assigned".equalsIgnoreCase(colLabel) || "Available".equalsIgnoreCase(colLabel)) {
                addTaskGroupedCards(col, children, "Assigned".equalsIgnoreCase(colLabel));
            } else {
                addHandlerGroupedCards(col, children, false);
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
     * are clustered under a small task-label banner showing the resource count.
     * Clicking the banner collapses or expands the group.  Cards linked to the same
     * handler are kept together within each group.</p>
     *
     * @param isAssignedColumn {@code true} when rendering the "Assigned" column — only then
     *                         are phantom busy-indicator groups added for tasks whose resources
     *                         are assigned to a different column.
     */
    private void addTaskGroupedCards(JPanel col, List<TCard> children, boolean isAssignedColumn) {
        // Index all TCards by their stable UUID for fast lookups.
        Map<String, TCard> tCardByResourceId = new LinkedHashMap<>();
        for (TCard c : controller.getData().getTCards()) {
            if (!c.getResourceId().isBlank()) {
                tCardByResourceId.put(c.getResourceId(), c);
            }
        }

        // For the Assigned column: build a lookup from resourceId → active ("On Task") assignment
        // so that each TCard is grouped under the task where it is genuinely deployed, regardless
        // of what the card's sourceRef says.  A card whose sourceRef points to a Planned task but
        // whose resource is on an active task must appear under the active task, not the Planned one.
        Map<String, String> activeTaskByResourceId = new java.util.HashMap<>();
        if (isAssignedColumn) {
            for (Map.Entry<String, SarTaskAssignment> e : sarTaskByAssignmentId.entrySet()) {
                SarTaskAssignment t = e.getValue();
                String lifecycle = t.getTaskLifecycleStatus() == null
                        ? ""
                        : t.getTaskLifecycleStatus().trim().toLowerCase(java.util.Locale.ROOT);
                if (!(lifecycle.startsWith("assigned -") || "on task".equals(lifecycle))) continue;
                List<SarTaskResource> res = t.getResourcesAssigned();
                if (res == null) continue;
                for (SarTaskResource r : res) {
                    if (!r.getResourceId().isBlank()) {
                        activeTaskByResourceId.put(r.getResourceId(), e.getKey());
                    }
                }
            }
        }

        // Group children by assignment ID.
        // • Returned tasks → ungrouped (resources flow freely under their status header).
        // • Assigned column: group each card under the active "On Task" assignment that owns
        //   its resource (via activeTaskByResourceId), falling back to sourceRef only when no
        //   active assignment claims the resource.  Cards whose sourceRef references a Planned
        //   (non-On-Task) assignment are placed ungrouped so the Planned task never appears in
        //   the Assigned column.
        // • Available column: use sourceRef as-is (Planned tasks render here).
        Map<String, List<TCard>> byTask = new LinkedHashMap<>();
        byTask.put("", new ArrayList<>()); // unnamed / non-task cards
        for (TCard card : children) {
            String taskKey = "";

            if (isAssignedColumn && !card.getResourceId().isBlank()) {
                // Prefer the active-task lookup over sourceRef for the Assigned column.
                String activeTask = activeTaskByResourceId.get(card.getResourceId());
                if (activeTask != null) {
                    taskKey = activeTask;
                }
                // If no active task claims this resource, leave taskKey="" (ungrouped).
            } else {
                // Available (and other) columns: derive group key from sourceRef.
                String ref = card.getSourceRef();
                if (ref.startsWith("sar:")) {
                    String[] parts = ref.split(":", 3);
                    if (parts.length >= 2 && !parts[1].isBlank()) {
                        String candidate = parts[1];
                        SarTaskAssignment candidateTask = sarTaskByAssignmentId.get(candidate);
                        // Group only under known, non-Returned tasks; stale/unknown refs → ungrouped.
                        String lifecycle = candidateTask == null ? ""
                                : candidateTask.getTaskLifecycleStatus().trim().toLowerCase(java.util.Locale.ROOT);
                        if (candidateTask != null && !"returned".equals(lifecycle)) {
                            taskKey = candidate;
                        }
                    }
                }
            }
            byTask.computeIfAbsent(taskKey, k -> new ArrayList<>()).add(card);
        }

        // In the "Assigned" column only: add a phantom group for active ("On Task") tasks that
        // have resources "Assigned" on a *different* group so busy-indicator cards can be rendered.
        // Only "On Task" tasks qualify — Planned and Returned tasks never appear in the Assigned
        // column.
        if (isAssignedColumn) {
            for (Map.Entry<String, SarTaskAssignment> taskEntry : sarTaskByAssignmentId.entrySet()) {
                String taskId = taskEntry.getKey();
                if (byTask.containsKey(taskId)) continue; // already present
                SarTaskAssignment task = taskEntry.getValue();
                if (task == null) continue;
                // Only active assignments can produce phantom groups.
                String lifecycle = task.getTaskLifecycleStatus() == null
                        ? ""
                        : task.getTaskLifecycleStatus().trim().toLowerCase(java.util.Locale.ROOT);
                if (!(lifecycle.startsWith("assigned -") || "on task".equals(lifecycle))) continue;
                List<SarTaskResource> res = task.getResourcesAssigned();
                if (res == null || res.isEmpty()) continue;
                // Add phantom only when at least one resource is "Assigned" (busy on another task).
                boolean hasBusyCard = res.stream()
                        .anyMatch(r -> !r.getResourceId().isBlank()
                                       && tCardByResourceId.containsKey(r.getResourceId())
                                       && "Assigned".equalsIgnoreCase(
                                               tCardByResourceId.get(r.getResourceId()).getStatus()));
                if (hasBusyCard) {
                    byTask.computeIfAbsent(taskId, k -> new ArrayList<>());
                }
            }
        }

        // Render non-task cards first, then each task group.
        List<TCard> unassigned = byTask.remove("");
        if (unassigned != null && !unassigned.isEmpty()) {
            addHandlerGroupedCards(col, unassigned, false);
        }
        for (Map.Entry<String, List<TCard>> entry : byTask.entrySet()) {
            String taskKey   = entry.getKey();
            String teamLabel = assignmentTeamByRef.getOrDefault(taskKey, taskKey);
            String resId     = assignmentResIdByRef.getOrDefault(taskKey, "");
            List<TCard> groupCards = new ArrayList<>(entry.getValue());

            // Find busy-indicator resources: resources assigned to this task whose
            // TCard is primarily rendered in a different task group (because they are
            // currently "Assigned" to another task).
            List<TCard> busyCards = new ArrayList<>();
            SarTaskAssignment task = sarTaskByAssignmentId.get(taskKey);
            if (task != null) {
                java.util.Set<TCard> alreadyInGroup = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
                alreadyInGroup.addAll(groupCards);
                for (SarTaskResource res : task.getResourcesAssigned()) {
                    if (!res.getResourceId().isBlank()) {
                        TCard candidate = tCardByResourceId.get(res.getResourceId());
                        if (candidate != null && !alreadyInGroup.contains(candidate)
                                && "Assigned".equalsIgnoreCase(candidate.getStatus())) {
                            busyCards.add(candidate);
                        }
                    }
                }
            }

            if (!teamLabel.isBlank()) {
                boolean collapsed = collapsedTaskKeys.contains(taskKey);
                int people = (int) groupCards.stream().filter(c -> c.getCardType() == TCardType.PERSONNEL).count();
                int other  = groupCards.size() - people;
                col.add(buildTaskBannerWidget(teamLabel, resId, people, other, collapsed, taskKey));
                if (!collapsed) {
                    addHandlerGroupedCards(col, groupCards, true);
                    // Append busy-indicator reference cards below the group's own cards.
                    for (TCard busyCard : busyCards) {
                        String primaryTeam = assignmentTeamByRef.getOrDefault(
                                busyCard.getSourceRef().startsWith("sar:")
                                        ? busyCard.getSourceRef().split(":", 3)[1] : "",
                                "another task");
                        col.add(buildBusyReferenceCard(busyCard, primaryTeam));
                        col.add(javax.swing.Box.createVerticalStrut(1));
                    }
                    col.add(javax.swing.Box.createVerticalStrut(4));
                } else {
                    // Show only the leader card when the group is collapsed (with paperclip icon).
                    groupCards.stream()
                            .filter(c -> c.getSourceRef().endsWith(":leader"))
                            .findFirst()
                            .ifPresent(leader -> {
                                col.add(buildRackCard(leader, true));
                                col.add(javax.swing.Box.createVerticalStrut(3));
                            });
                }
            } else if (!groupCards.isEmpty()) {
                addHandlerGroupedCards(col, groupCards, false);
                col.add(javax.swing.Box.createVerticalStrut(4));
            }
        }
    }

    /**
     * Builds a compact, visually-distinct "busy" reference card for a resource that is
     * currently assigned to a different task.  The card shows the resource name and a
     * ⚠ indicator with the team label of the task they are currently on.
     *
     * @param card           the resource T-card.
     * @param primaryTeamLabel the team label of the task the resource is currently on.
     * @return a read-only reference panel.
     */
    private JPanel buildBusyReferenceCard(TCard card, String primaryTeamLabel) {
        JPanel p = new JPanel(new BorderLayout(2, 0));
        p.setBackground(new Color(220, 220, 220)); // neutral grey
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(160, 160, 160)),
                BorderFactory.createEmptyBorder(2, 4, 2, 4)));
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel nameLabel = new JLabel("⚠ " + card.getDisplayLabel());
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.ITALIC, 10.5f));
        nameLabel.setForeground(new Color(80, 80, 80));

        String assignedTo = primaryTeamLabel.isBlank() ? "another task" : primaryTeamLabel;
        JLabel detailLabel = new JLabel("Assigned: " + assignedTo);
        detailLabel.setFont(detailLabel.getFont().deriveFont(9.5f));
        detailLabel.setForeground(new Color(120, 60, 0));

        p.add(nameLabel, BorderLayout.CENTER);
        p.add(detailLabel, BorderLayout.SOUTH);
        return p;
    }

    /**
     * Adds resource cards to a column panel, with canine/equipment cards grouped
     * immediately below their handler's personnel card.
     */
    private void addHandlerGroupedCards(JPanel col, List<TCard> cards, boolean inTaskGroup) {
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
            col.add(javax.swing.Box.createVerticalStrut(inTaskGroup ? 0 : 3));
            // Append any linked canines directly below.
            String personName = card.getPersonName();
            List<TCard> linked = caninesByHandler.get(personName);
            if (linked != null) {
                for (TCard canine : linked) {
                    col.add(buildCanineRackCard(canine));
                    col.add(javax.swing.Box.createVerticalStrut(inTaskGroup ? 0 : 2));
                }
                caninesByHandler.remove(personName);
            }
        }

        // Any canines whose handler card isn't in this column — render standalone.
        for (List<TCard> orphaned : caninesByHandler.values()) {
            for (TCard card : orphaned) {
                col.add(buildRackCard(card));
                col.add(javax.swing.Box.createVerticalStrut(inTaskGroup ? 0 : 3));
            }
        }
    }

    /**
     * Builds a narrow task-label banner for sub-grouping within the Assigned column.
     *
     * <p>The banner shows the assignment number, optional resource identifier, and the
     * people/resource breakdown as {@code (n)} for n people, or {@code (n, m)} when
     * m non-personnel resources are also present.
     * Clicking the banner toggles the collapsed/expanded state of the group.</p>
     */
    private JPanel buildTaskBannerWidget(String teamLabel, String resId, int people, int other,
                                         boolean collapsed, String taskKey) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(new Color(200, 220, 255));
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(100, 140, 220), 1),
                BorderFactory.createEmptyBorder(2, 6, 2, 6)));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));

        String arrow = collapsed ? "▶" : "▼";
        StringBuilder bannerText = new StringBuilder(arrow).append(" ").append(teamLabel);
        if (!resId.isBlank()) {
            bannerText.append(":").append(resId);
        }
        bannerText.append(" (").append(people);
        if (other > 0) {
            bannerText.append(", ").append(other);
        }
        bannerText.append(")");

        JLabel lbl = new JLabel(bannerText.toString());
        lbl.setFont(lbl.getFont().deriveFont(Font.BOLD, 10.5f));
        lbl.setForeground(new Color(30, 60, 130));
        p.add(lbl, BorderLayout.CENTER);
        if (isSarMode() && onEditSarAssignmentRequest != null && !taskKey.isBlank()) {
            JButton editTaskBtn = new JButton("✎");
            editTaskBtn.setMargin(new Insets(1, 4, 1, 4));
            editTaskBtn.setFont(editTaskBtn.getFont().deriveFont(10f));
            editTaskBtn.setToolTipText("Edit SAR assignment");
            editTaskBtn.addActionListener(e -> onEditSarAssignmentRequest.accept(taskKey));
            p.add(editTaskBtn, BorderLayout.EAST);
        }

        p.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        p.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (javax.swing.SwingUtilities.isLeftMouseButton(e)) {
                    if (collapsedTaskKeys.contains(taskKey)) {
                        collapsedTaskKeys.remove(taskKey);
                    } else {
                        collapsedTaskKeys.add(taskKey);
                    }
                    rebuildRackView();
                }
            }
        });
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
            col.setBorder(BorderFactory.createTitledBorder(type.getLabel(isSarMode())));

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
        return buildRackCardWidget(card, false, false);
    }

    /**
     * Builds a rack card optionally displaying a paperclip icon in the upper-right corner
     * to indicate the task group is collapsed and more resources are hidden.
     */
    private JPanel buildRackCard(TCard card, boolean showPaperclip) {
        return buildRackCardWidget(card, false, showPaperclip);
    }

    /** Builds a slightly narrower canine/equipment card indented below a handler card. */
    private JPanel buildCanineRackCard(TCard card) {
        return buildRackCardWidget(card, true, false);
    }

    private JPanel buildRackCardWidget(TCard card, boolean indented, boolean showPaperclip) {
        JPanel p = new JPanel(new BorderLayout(2, 2));
        p.setBackground(cardColor(card.getCardType()));
        boolean isSelected = card == selectedRackCard;
        p.setBorder(rackCardBorder(isSelected, indented));
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 55));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        // Store card reference so click handler can retrieve it.
        p.putClientProperty("tcard", card);
        // Register in widget map for in-place border updates.
        rackCardWidgets.put(card, p);

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

        JPanel northRow = new JPanel(new BorderLayout());
        northRow.setOpaque(false);
        northRow.add(nameLabel, BorderLayout.CENTER);
        if (showPaperclip) {
            JLabel paperclip = new JLabel("📎");
            paperclip.setFont(paperclip.getFont().deriveFont(11f));
            northRow.add(paperclip, BorderLayout.EAST);
        }
        p.add(northRow, BorderLayout.NORTH);
        if (!detail.isEmpty()) {
            JLabel detailLabel = new JLabel(detail.toString());
            detailLabel.setFont(detailLabel.getFont().deriveFont(10.5f));
            p.add(detailLabel, BorderLayout.CENTER);
        }

        // Right-click popup for rack cards.
        JPopupMenu rackPopup = new JPopupMenu();
        JMenuItem rackEditItem   = new JMenuItem("Edit…");
        JMenuItem rackRemoveItem = new JMenuItem("Remove");
        rackEditItem.addActionListener(e -> {
            selectedRackCard = card;
            editSelectedCard();
        });
        rackRemoveItem.addActionListener(e -> {
            selectedRackCard = card;
            removeSelectedCard();
        });
        rackPopup.add(rackEditItem);
        rackPopup.add(rackRemoveItem);
        p.setComponentPopupMenu(rackPopup);

        p.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                // Selection update without full rack rebuild so that double-click events
                // are still delivered to the same panel instance.
                if (javax.swing.SwingUtilities.isLeftMouseButton(e)) {
                    updateRackSelection(card, indented);
                }
            }
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && javax.swing.SwingUtilities.isLeftMouseButton(e)) {
                    selectedRackCard = card;
                    editSelectedCard();
                }
            }
        });
        return p;
    }

    /** Builds the compound border for a rack card. */
    private static javax.swing.border.Border rackCardBorder(boolean selected, boolean indented) {
        return BorderFactory.createCompoundBorder(
                selected
                        ? BorderFactory.createLineBorder(new Color(0, 100, 200), 2)
                        : BorderFactory.createLineBorder(Color.DARK_GRAY),
                BorderFactory.createEmptyBorder(3, indented ? 12 : 5, 3, 5));
    }

    /**
     * Selects a rack card, updating just the borders of the old and new selection panels
     * without a full rack rebuild (which would break double-click detection).
     */
    private void updateRackSelection(TCard card, boolean indented) {
        if (selectedRackCard != null && selectedRackCard != card) {
            JPanel old = rackCardWidgets.get(selectedRackCard);
            if (old != null) {
                old.setBorder(rackCardBorder(false, Boolean.TRUE.equals(old.getClientProperty("indented"))));
                old.repaint();
            }
        }
        selectedRackCard = card;
        JPanel p = rackCardWidgets.get(card);
        if (p != null) {
            p.putClientProperty("indented", indented);
            p.setBorder(rackCardBorder(true, indented));
            p.repaint();
        }
    }



    private void addCardWithTypeChoice() {
        JComboBox<TCardType> typeChooser = new JComboBox<>(TCardType.values());
        typeChooser.setRenderer(tCardTypeRenderer());
        typeChooser.setSelectedItem(TCardType.PERSONNEL);
        int result = JOptionPane.showConfirmDialog(this,
                new Object[]{"Select card type:", typeChooser},
                "Add T-Card", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) {
            return;
        }
        TCard card = new TCard();
        card.setCardType((TCardType) typeChooser.getSelectedItem());
        if (openEditDialog(card)) {
            tableModel.addCard(card);
            refreshDerivedViewsFromTableModel();
            controller.markDirty();
        }
    }

    private void addPersonnelCard() {
        TCard card = new TCard();
        card.setCardType(TCardType.PERSONNEL);
        if (openEditDialog(card)) {
            tableModel.addCard(card);
            refreshDerivedViewsFromTableModel();
            controller.markDirty();
        }
    }

    public TCard createPersonnelCardFromPicker(String proposedName, String radioChannel, String phoneNumber) {
        String name = proposedName == null ? "" : proposedName.trim();
        if (name.isBlank()) {
            return null;
        }
        TCard existing = controller.findPersonCard(name);
        if (existing != null) {
            if (existing.getRadioChannel().isBlank() && radioChannel != null && !radioChannel.trim().isBlank()) {
                existing.setRadioChannel(radioChannel.trim());
            }
            if (existing.getPhoneNumber().isBlank() && phoneNumber != null && !phoneNumber.trim().isBlank()) {
                existing.setPhoneNumber(phoneNumber.trim());
            }
            controller.markDirty();
            return existing;
        }
        TCard card = new TCard();
        card.setCardType(TCardType.PERSONNEL);
        card.setPersonName(name);
        card.setResourceIdentifier(name);
        card.setLocation("ICP");
        card.setStatus("ordered");
        if (radioChannel != null && !radioChannel.trim().isBlank()) {
            card.setRadioChannel(radioChannel.trim());
        }
        if (phoneNumber != null && !phoneNumber.trim().isBlank()) {
            card.setPhoneNumber(phoneNumber.trim());
        }
        if (!openEditDialog(card)) {
            return null;
        }
        tableModel.addCard(card);
        refreshDerivedViewsFromTableModel();
        controller.markDirty();
        return card;
    }

    private void addHeaderCard() {
        TCard card = new TCard();
        card.setCardType(TCardType.HEADER);
        if (openEditDialog(card)) {
            tableModel.addCard(card);
            refreshDerivedViewsFromTableModel();
            controller.markDirty();
        }
    }

    private void editSelectedCard() {
        TCard selected = selectedCard();
        if (selected == null) {
            return;
        }
        TCard copy = copyCard(selected);
        if (!openEditDialog(copy)) {
            return;
        }
        int row = tableModel.indexOfIdentity(selected);
        if (row < 0) {
            return;
        }
        tableModel.replaceCard(row, copy);
        if (selectedRackCard == selected) {
            selectedRackCard = copy;
        }
        refreshDerivedViewsFromTableModel(copy);
        controller.markDirty();
    }

    private void removeSelectedCard() {
        TCard selected = selectedCard();
        if (selected == null) {
            return;
        }
        int row = tableModel.indexOfIdentity(selected);
        if (row < 0) {
            return;
        }
        String label = effectiveName(selected);
        int confirm = JOptionPane.showConfirmDialog(this,
                "Remove resource '" + (label.isBlank() ? ("row " + (row + 1)) : label) + "'?",
                "Remove Resource", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            tableModel.removeCard(row);
            if (selectedRackCard == selected) {
                selectedRackCard = null;
            }
            refreshDerivedViewsFromTableModel();
            controller.markDirty();
        }
    }

    private TCard selectedCard() {
        if (VIEW_RACK.equals(currentView)) {
            return selectedRackCard;
        }
        if (VIEW_DIRECTORY.equals(currentView)) {
            int viewRow = directoryTable.getSelectedRow();
            if (viewRow < 0) {
                return null;
            }
            int modelRow = directoryTable.convertRowIndexToModel(viewRow);
            return directoryTableModel.getEntry(modelRow).card();
        }
        int row = table.getSelectedRow();
        return row < 0 ? null : tableModel.getCard(table.convertRowIndexToModel(row));
    }

    // -------------------------------------------------------------------------
    // Merge duplicates
    // -------------------------------------------------------------------------

    /**
     * Finds T-cards that share the same name (case-insensitive), presents a merge
     * dialog for each pair, and removes the card that was merged into the other.
     *
     * <p>Merge rules applied field by field:
     * <ul>
     *   <li>If one value is blank and the other is not, the non-blank value wins.</li>
     *   <li>If both values are identical (after trimming), they are kept as-is.</li>
     *   <li>If both values are non-blank and differ, a selection dialog lets the
     *       operator choose or edit the value to retain.</li>
     * </ul>
     * The first card (lower index) is the target that receives the merged data;
     * the second (duplicate) is removed once the merge is confirmed.</p>
     */
    private void mergeDuplicates() {
        int mergedCount = 0;

        // Walk through the list looking for cards with the same effective name.
        // Iterate from the beginning; after each merge restart the scan because
        // indices have changed.
        outer:
        while (true) {
            List<TCard> current = tableModel.getCards();
            for (int i = 0; i < current.size(); i++) {
                TCard a = current.get(i);
                String nameA = effectiveName(a).toLowerCase(Locale.ROOT);
                if (nameA.isBlank()) {
                    continue;
                }
                for (int j = i + 1; j < current.size(); j++) {
                    TCard b = current.get(j);
                    if (!nameA.equals(effectiveName(b).toLowerCase(Locale.ROOT))) {
                        continue;
                    }
                    // Found a duplicate pair (a, b). Show the merge dialog.
                    int[] outcome = new int[1]; // 0=merged, 1=skip, 2=cancel
                    TCard merged = showMergeDialog(a, b, outcome);
                    if (outcome[0] == 2) {
                        break outer; // user cancelled all
                    }
                    if (outcome[0] == 1 || merged == null) {
                        continue; // skip this pair; continue scanning
                    }
                    // Replace a with the merged card and remove b.
                    tableModel.replaceCard(i, merged);
                    tableModel.removeCard(j);
                    mergedCount++;
                    continue outer; // restart after structural change
                }
            }
            break; // no more pairs found
        }

        if (mergedCount == 0) {
            JOptionPane.showMessageDialog(this,
                    "No duplicate T-cards (by name) found.",
                    "Merge Duplicates", JOptionPane.INFORMATION_MESSAGE);
        } else {
            pushToModel();
            controller.deduplicateSarTaskResources();
            controller.markDirty();
            refreshFromModel();
            JOptionPane.showMessageDialog(this,
                    mergedCount + " duplicate pair(s) merged.",
                    "Merge Duplicates", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    /** Returns the effective name used for duplicate detection. */
    private static String effectiveName(TCard card) {
        String name = card.getPersonName();
        if (!name.isBlank()) return name.trim();
        name = card.getResourceIdentifier();
        return name == null ? "" : name.trim();
    }

    /**
     * Opens a field-by-field merge dialog for two T-cards with the same name.
     *
     * <p>Each field is shown as a row with "Card A" and "Card B" values. Where
     * both values are non-blank and differ, the operator can select which to keep
     * (or edit the value directly). Where values are the same or one is blank the
     * row is pre-resolved automatically and displayed read-only.</p>
     *
     * @param a       target card (will receive merged data).
     * @param b       duplicate card (will be removed on confirmation).
     * @param outcome single-element array set to: 0=merge confirmed, 1=skip, 2=cancel all.
     * @return a new merged card when outcome is 0, {@code null} otherwise.
     */
    private TCard showMergeDialog(TCard a, TCard b, int[] outcome) {
        // Define the fields to compare.
        String[] fieldLabels = {
            "Card type", "Person name", "Home agency", "Home state",
            "Phone", "Radio channel", "Resource identifier", "Location",
            "Status", "Notes", "Handler/operator"
        };
        String[] valuesA = {
            a.getCardType().getLabel(isSarMode()),
            a.getPersonName(), a.getHomeAgency(), a.getHomeState(),
            a.getPhoneNumber(), a.getRadioChannel(), a.getResourceIdentifier(),
            a.getLocation(), a.getStatus(), a.getNotes(), a.getHandlerName()
        };
        String[] valuesB = {
            b.getCardType().getLabel(isSarMode()),
            b.getPersonName(), b.getHomeAgency(), b.getHomeState(),
            b.getPhoneNumber(), b.getRadioChannel(), b.getResourceIdentifier(),
            b.getLocation(), b.getStatus(), b.getNotes(), b.getHandlerName()
        };

        int fieldCount = fieldLabels.length;
        // For each field: auto-resolved value (null when conflict requires user choice).
        String[] autoResolved = new String[fieldCount];
        // True when the field is a conflict requiring user input.
        boolean[] isConflict = new boolean[fieldCount];
        JTextField[] editFields = new JTextField[fieldCount];
        // Radio buttons: 0 = A, 1 = B; only created for conflict rows.
        javax.swing.ButtonGroup[] groups = new javax.swing.ButtonGroup[fieldCount];
        javax.swing.JRadioButton[] radioA = new javax.swing.JRadioButton[fieldCount];
        javax.swing.JRadioButton[] radioB = new javax.swing.JRadioButton[fieldCount];

        for (int i = 0; i < fieldCount; i++) {
            String va = valuesA[i] == null ? "" : valuesA[i].trim();
            String vb = valuesB[i] == null ? "" : valuesB[i].trim();
            if (va.equalsIgnoreCase(vb)) {
                autoResolved[i] = va;
            } else if (va.isBlank()) {
                autoResolved[i] = vb;
            } else if (vb.isBlank()) {
                autoResolved[i] = va;
            } else {
                isConflict[i] = true;
            }
        }

        // Build the dialog panel.
        JPanel panel = new JPanel(new java.awt.GridBagLayout());
        java.awt.GridBagConstraints gbc = new java.awt.GridBagConstraints();
        gbc.insets = new java.awt.Insets(2, 4, 2, 4);
        gbc.anchor = java.awt.GridBagConstraints.WEST;

        // Header row.
        gbc.gridy = 0; gbc.gridx = 0; gbc.weightx = 0;
        panel.add(boldLabel("Field"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.4;
        panel.add(boldLabel("Card A — " + effectiveName(a)), gbc);
        gbc.gridx = 2; gbc.weightx = 0.4;
        panel.add(boldLabel("Card B — " + effectiveName(b)), gbc);
        gbc.gridx = 3; gbc.weightx = 0.2;
        panel.add(boldLabel("Keep"), gbc);

        for (int i = 0; i < fieldCount; i++) {
            int row = i + 1;
            gbc.gridy = row; gbc.gridx = 0; gbc.weightx = 0;
            panel.add(new JLabel(fieldLabels[i] + ":"), gbc);

            gbc.gridx = 1; gbc.weightx = 0.4;
            JLabel lblA = new JLabel("<html>" + htmlEscape(valuesA[i]) + "</html>");
            panel.add(lblA, gbc);

            gbc.gridx = 2; gbc.weightx = 0.4;
            JLabel lblB = new JLabel("<html>" + htmlEscape(valuesB[i]) + "</html>");
            panel.add(lblB, gbc);

            gbc.gridx = 3; gbc.weightx = 0.2;
            if (isConflict[i]) {
                // Show radio buttons A / B plus an editable override field.
                groups[i] = new javax.swing.ButtonGroup();
                radioA[i] = new javax.swing.JRadioButton("A");
                radioB[i] = new javax.swing.JRadioButton("B");
                radioA[i].setSelected(true);
                groups[i].add(radioA[i]);
                groups[i].add(radioB[i]);
                editFields[i] = new JTextField(valuesA[i], 12);
                // Capture effectively-final references for use in lambdas.
                final JTextField editField = editFields[i];
                final String va = valuesA[i];
                final String vb = valuesB[i];
                // Selecting a radio button copies its value into the edit field.
                radioA[i].addActionListener(e -> editField.setText(va));
                radioB[i].addActionListener(e -> editField.setText(vb));
                JPanel conflictPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
                conflictPanel.add(radioA[i]);
                conflictPanel.add(radioB[i]);
                conflictPanel.add(editFields[i]);
                panel.add(conflictPanel, gbc);
            } else {
                panel.add(new JLabel(autoResolved[i].isBlank() ? "—" : autoResolved[i]), gbc);
            }
        }

        JScrollPane scroll = new JScrollPane(panel);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setPreferredSize(new Dimension(720, 360));

        String[] options = {"Merge", "Skip this pair", "Cancel all"};
        int choice = JOptionPane.showOptionDialog(
                javax.swing.SwingUtilities.getWindowAncestor(this),
                scroll,
                "Merge duplicate T-cards: " + effectiveName(a),
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE,
                null, options, options[0]);

        if (choice == 2) {
            outcome[0] = 2; // cancel all
            return null;
        }
        if (choice == 1 || choice == JOptionPane.CLOSED_OPTION) {
            outcome[0] = 1; // skip this pair (dismissing the window = skip, not cancel all)
            return null;
        }
        outcome[0] = 0; // merge confirmed

        // Build the merged card: start from a copy of card A.
        TCard merged = copyCard(a);
        for (int i = 0; i < fieldCount; i++) {
            String resolved = isConflict[i] ? editFields[i].getText().trim() : autoResolved[i];
            applyMergedField(merged, i, resolved);
        }
        // Preserve check-in date from whichever card has one; prefer A.
        if (merged.getCheckInDateTime() == null && b.getCheckInDateTime() != null) {
            merged.setCheckInDateTime(b.getCheckInDateTime());
        }
        // Preserve sourceRef: keep the non-blank one, preferring A.
        if (merged.getSourceRef().isBlank() && !b.getSourceRef().isBlank()) {
            merged.setSourceRef(b.getSourceRef());
        }
        return merged;
    }

    /**
     * Applies a resolved field value back to the merged card.
     *
     * @param merged   card being built.
     * @param fieldIdx index matching the {@code fieldLabels} array in {@link #showMergeDialog}.
     * @param value    resolved value to apply.
     */
    private static void applyMergedField(TCard merged, int fieldIdx, String value) {
        switch (fieldIdx) {
            case 0 -> {
                // Card type: match against any label variant (standard or SAR) so that
                // a label stored in one mode is still recognised after a mode switch.
                for (TCardType t : TCardType.values()) {
                    if (t.matchesLabel(value)) {
                        merged.setCardType(t);
                        break;
                    }
                }
            }
            case 1  -> merged.setPersonName(value);
            case 2  -> merged.setHomeAgency(value);
            case 3  -> merged.setHomeState(value);
            case 4  -> merged.setPhoneNumber(value);
            case 5  -> merged.setRadioChannel(value);
            case 6  -> merged.setResourceIdentifier(value);
            case 7  -> merged.setLocation(value);
            case 8  -> merged.setStatus(value);
            case 9  -> merged.setNotes(value);
            case 10 -> merged.setHandlerName(value);
            default -> { /* no-op */ }
        }
    }

    private static JLabel boldLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(lbl.getFont().deriveFont(Font.BOLD));
        return lbl;
    }

    private static String htmlEscape(String s) {
        if (s == null || s.isBlank()) return "<i>(blank)</i>";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // -------------------------------------------------------------------------
    // CSV import/export
    // -------------------------------------------------------------------------

    public void exportToCsv() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("CSV files (*.csv)", "csv"));
        chooser.setDialogTitle("Export resources as CSV");
        chooser.setSelectedFile(new File("resources.csv"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        if (file != null && !file.getName().toLowerCase(Locale.ROOT).endsWith(".csv")) {
            file = new File(file.getParentFile(), file.getName() + ".csv");
        }
        try {
            exportToCsv(file);
            JOptionPane.showMessageDialog(this, "Exported resources to " + file.getName() + ".",
                    "Export Complete", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                    "Could not export CSV: " + ex.getMessage(),
                    "Export Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public void exportToCsv(File outputFile) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile))) {
            writer.println("Name, Agency, State, Phone, Type, Handler");
            for (TCard card : tableModel.getCards()) {
                String name = card.getPersonName().isBlank() ? card.getResourceIdentifier() : card.getPersonName();
                writer.println(csvValue(name) + ","
                        + csvValue(card.getHomeAgency()) + ","
                        + csvValue(card.getHomeState()) + ","
                        + csvValue(card.getPhoneNumber()) + ","
                        + csvValue(card.getCardType().name()) + ","
                        + csvValue(card.getHandlerName()));
            }
        }
    }

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
    public void importFromCsv() {
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
        String[] colNames = {"Import?", "Name", "Home Agency", "Home State", "Phone", "Type", "Handler"};
        Object[][] previewData = new Object[dataRows][colNames.length];
        for (int i = 0; i < dataRows; i++) {
            String[] row = rows.get(dataStart + i);
            previewData[i][0] = Boolean.TRUE;
            previewData[i][1] = cell(row, 0);
            previewData[i][2] = cell(row, 1);
            previewData[i][3] = cell(row, 2);
            previewData[i][4] = cell(row, 3);
            previewData[i][5] = cell(row, 4);
            previewData[i][6] = cell(row, 5); // handler/operator name (may be blank)
        }

        CsvPreviewTableModel previewModel = new CsvPreviewTableModel(previewData, colNames);
        JTable previewTable = new JTable(previewModel);
        previewTable.setRowHeight(22);
        previewTable.getColumnModel().getColumn(0).setMaxWidth(65);
        JScrollPane scroll = new JScrollPane(previewTable);
        scroll.setPreferredSize(new Dimension(720, Math.min(380, dataRows * 25 + 60)));

        // --- Selection buttons (Select All / Deselect All / by agency) ---
        JButton selectAllBtn   = new JButton("Select All");
        JButton deselectAllBtn = new JButton("Deselect All");
        selectAllBtn.addActionListener(e -> {
            for (int i = 0; i < dataRows; i++) previewModel.setValueAt(Boolean.TRUE,  i, 0);
        });
        deselectAllBtn.addActionListener(e -> {
            for (int i = 0; i < dataRows; i++) previewModel.setValueAt(Boolean.FALSE, i, 0);
        });

        JPanel selPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        selPanel.add(selectAllBtn);
        selPanel.add(deselectAllBtn);

        // Collect distinct non-blank agencies for the per-agency filter.
        java.util.LinkedHashSet<String> agencySet = new java.util.LinkedHashSet<>();
        for (int i = 0; i < dataRows; i++) {
            String ag = (String) previewModel.getValueAt(i, 2);
            if (ag != null && !ag.isBlank()) agencySet.add(ag.trim());
        }
        if (!agencySet.isEmpty()) {
            JComboBox<String> agencyCombo = new JComboBox<>(agencySet.toArray(new String[0]));
            JButton selectAgBtn   = new JButton("Select Agency");
            JButton deselectAgBtn = new JButton("Deselect Agency");
            selectAgBtn.addActionListener(e -> {
                String ag = (String) agencyCombo.getSelectedItem();
                if (ag == null) return;
                for (int i = 0; i < dataRows; i++) {
                    if (ag.equalsIgnoreCase((String) previewModel.getValueAt(i, 2)))
                        previewModel.setValueAt(Boolean.TRUE, i, 0);
                }
            });
            deselectAgBtn.addActionListener(e -> {
                String ag = (String) agencyCombo.getSelectedItem();
                if (ag == null) return;
                for (int i = 0; i < dataRows; i++) {
                    if (ag.equalsIgnoreCase((String) previewModel.getValueAt(i, 2)))
                        previewModel.setValueAt(Boolean.FALSE, i, 0);
                }
            });
            selPanel.add(new JLabel("Agency:"));
            selPanel.add(agencyCombo);
            selPanel.add(selectAgBtn);
            selPanel.add(deselectAgBtn);
        }

        // --- Initial status/location preset radio buttons ---
        // Option 0: No change — leave status/location blank (default).
        // Option 1: Requested — resource ordered but not yet en route.
        // Option 2: Enroute   — resource is travelling to the incident.
        // Option 3: Available, Location=At Staging — resource has arrived at staging.
        JRadioButton radioNoChange  = new JRadioButton("No change (leave blank)", true);
        JRadioButton radioRequested = new JRadioButton("Requested (ordered, not yet en route)");
        JRadioButton radioEnroute   = new JRadioButton("Enroute");
        JRadioButton radioAvailable = new JRadioButton("Available (at Staging)");
        ButtonGroup presetGroup = new ButtonGroup();
        presetGroup.add(radioNoChange);
        presetGroup.add(radioRequested);
        presetGroup.add(radioEnroute);
        presetGroup.add(radioAvailable);
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        statusPanel.setBorder(BorderFactory.createTitledBorder("Initial Status"));
        statusPanel.add(radioNoChange);
        statusPanel.add(radioRequested);
        statusPanel.add(radioEnroute);
        statusPanel.add(radioAvailable);

        // --- Assemble dialog content ---
        JPanel content = new JPanel(new BorderLayout(4, 4));
        content.add(selPanel,    BorderLayout.NORTH);
        content.add(scroll,      BorderLayout.CENTER);
        content.add(statusPanel, BorderLayout.SOUTH);

        int choice = JOptionPane.showConfirmDialog(this, content,
                "Select resources to import", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) {
            return;
        }

        // Resolve the chosen preset (null means "no change — leave blank").
        String presetStatus   = radioRequested.isSelected() ? "Requested"
                              : radioEnroute.isSelected()   ? "Enroute"
                              : radioAvailable.isSelected() ? "Available"
                              : null;
        String presetLocation = radioAvailable.isSelected() ? "At Staging" : null;

        int imported = 0;
        for (int i = 0; i < dataRows; i++) {
            if (Boolean.TRUE.equals(previewModel.getValueAt(i, 0))) {
                TCard card = csvRowToCard(
                        (String) previewModel.getValueAt(i, 1),
                        (String) previewModel.getValueAt(i, 2),
                        (String) previewModel.getValueAt(i, 3),
                        (String) previewModel.getValueAt(i, 4),
                        (String) previewModel.getValueAt(i, 5),
                        (String) previewModel.getValueAt(i, 6));
                if (presetStatus   != null) card.setStatus(presetStatus);
                if (presetLocation != null) card.setLocation(presetLocation);
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
        return csvRowToCard(name, agency, state, phone, typeStr, null);
    }

    private TCard csvRowToCard(String name, String agency, String state, String phone, String typeStr, String handler) {
        TCard card = new TCard();
        TCardType type = inferCardType(typeStr);
        card.setCardType(type);
        String safeName = name == null ? "" : name.trim();
        // For PERSONNEL cards, the name is the person's name; for all other card types
        // (equipment, canines, aircraft, etc.) the name is the resource identifier.
        if (type == TCardType.PERSONNEL) {
            card.setPersonName(safeName);
        } else {
            card.setResourceIdentifier(safeName);
        }
        card.setHomeAgency(agency == null ? "" : agency.trim());
        card.setHomeState(state == null ? "" : state.trim());
        card.setPhoneNumber(phone == null ? "" : phone.trim());
        if (handler != null && !handler.isBlank()) {
            card.setHandlerName(handler.trim());
        }
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
        // 219-7 Equipment: canines are working assets/equipment in ICS.
        if (v.contains("canine") || v.contains("handler") || v.contains("k9")
                || v.contains("dozer") || v.contains("equipment") || v.contains("219-7")) {
            return TCardType.EQUIPMENT;
        }
        // 219-8 Misc. Equipment / Task Force
        if (v.contains("219-8") || v.contains("misc") || v.contains("task force")) {
            return TCardType.MISC_EQUIPMENT;
        }
        // 219-6 Fixed-Wing (drones / UAS also fall here)
        if (v.contains("fixed") || v.contains("drone") || v.contains("uas") || v.contains("uav")
                || v.contains("aircraft") || v.contains("219-6")) {
            return TCardType.FIXED_WING;
        }
        if (v.contains("helicopter") || v.contains("219-4")) {
            return TCardType.HELICOPTER;
        }
        if (v.contains("engine") || v.contains("219-3")) {
            return TCardType.ENGINE;
        }
        if (v.contains("crew") || v.contains("team") || v.contains("219-2")) {
            return TCardType.CREW;
        }
        // Check GENERIC before HEADER: "219-1" is a prefix of "219-10", so checking GENERIC
        // first prevents "219-10" from matching the HEADER branch's v.contains("219-1") test.
        if (v.contains("generic") || v.contains("219-10")) {
            return TCardType.GENERIC;
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

    private static String csvValue(String value) {
        String safe = value == null ? "" : value;
        if (safe.contains(",") || safe.contains("\"") || safe.contains("\n") || safe.contains("\r")) {
            return "\"" + safe.replace("\"", "\"\"") + "\"";
        }
        return safe;
    }

    // -------------------------------------------------------------------------
    // Card edit dialog
    // -------------------------------------------------------------------------

    /**
     * Opens an edit dialog for the given card.  A "Card type" combo is shown at the top;
     * changing it switches the visible set of type-specific fields.  The card is updated
     * in-place on confirmation.
     *
     * @param card card to edit in-place.
     * @return {@code true} when the user confirmed.
     */
    private boolean openEditDialog(TCard card) {
        // ── shared fields ──────────────────────────────────────────────────
        JComboBox<TCardType> typeCombo = new JComboBox<>(TCardType.values());
        typeCombo.setRenderer(tCardTypeRenderer());
        typeCombo.setSelectedItem(card.getCardType());

        // ── HEADER sub-form fields (separate instances) ────────────────────
        JTextField headerResourceIdField = UiSupport.textField();
        JTextField headerLocationField   = UiSupport.textField();
        JTextField headerNotesField      = UiSupport.textField();

        headerResourceIdField.setText(card.getResourceIdentifier());
        headerLocationField.setText(card.getLocation());
        headerNotesField.setText(card.getNotes());

        // ── PERSONNEL sub-form fields ──────────────────────────────────────
        JTextField personNameField    = UiSupport.textField();
        JTextField homeAgencyField    = UiSupport.textField();
        JTextField homeStateField     = UiSupport.textField();
        homeStateField.setPreferredSize(new Dimension(48, homeStateField.getPreferredSize().height));
        JTextField phoneField         = UiSupport.textField();
        JTextField radioChannelField  = UiSupport.textField();
        JTextField personnelResIdField = UiSupport.textField();
        JTextField personnelLocField  = UiSupport.textField();
        JComboBox<String> personnelStatusCombo = new JComboBox<>(STATUS_OPTIONS);
        JTextField personnelNotesField = UiSupport.textField();

        SpinnerDateModel checkInModel = new SpinnerDateModel();
        JSpinner checkInSpinner = new JSpinner(checkInModel);
        checkInSpinner.setEditor(new JSpinner.DateEditor(checkInSpinner, "yyyy-MM-dd HH:mm"));

        personNameField.setText(card.getPersonName());
        homeAgencyField.setText(card.getHomeAgency());
        homeStateField.setText(card.getHomeState());
        phoneField.setText(card.getPhoneNumber());
        radioChannelField.setText(card.getRadioChannel());
        personnelResIdField.setText(card.getResourceIdentifier());
        personnelLocField.setText(card.getLocation());
        personnelStatusCombo.setSelectedItem(card.getStatus());
        personnelNotesField.setText(card.getNotes());
        if (card.getCheckInDateTime() != null) {
            checkInModel.setValue(Date.from(card.getCheckInDateTime().atZone(ZoneId.systemDefault()).toInstant()));
        }

        // ── OTHER resource sub-form fields ────────────────────────────────
        JTextField handlerNameField      = UiSupport.textField();
        JTextField resourceResIdField    = UiSupport.textField();
        JTextField resourceLocField      = UiSupport.textField();
        JComboBox<String> resourceStatusCombo = new JComboBox<>(STATUS_OPTIONS);
        JTextField resourceNotesField    = UiSupport.textField();
        JTextField crewSizeField         = UiSupport.textField();
        crewSizeField.setPreferredSize(new Dimension(60, crewSizeField.getPreferredSize().height));

        handlerNameField.setText(card.getHandlerName());
        resourceResIdField.setText(card.getResourceIdentifier());
        resourceLocField.setText(card.getLocation());
        resourceStatusCombo.setSelectedItem(card.getStatus());
        resourceNotesField.setText(card.getNotes());
        if (card.getNumberOfPersons() > 0) {
            crewSizeField.setText(String.valueOf(card.getNumberOfPersons()));
        }

        // ── build three sub-forms (HEADER / PERSONNEL / other) ──────────
        CardLayout subLayout = new CardLayout();
        JPanel subContainer = new JPanel(subLayout);

        JPanel headerForm = UiSupport.formPanel();
        int r = 0;
        UiSupport.addRow(headerForm, r++, "Heading text (column label)", headerResourceIdField);
        UiSupport.addRow(headerForm, r++, "Location note",               headerLocationField);
        UiSupport.addRow(headerForm, r,   "Notes",                       headerNotesField);

        JPanel personnelForm = UiSupport.formPanel();
        r = 0;
        UiSupport.addRow(personnelForm, r++, "Person name",             personNameField);
        UiSupport.addRow(personnelForm, r++, "Home agency",             homeAgencyField);
        UiSupport.addRow(personnelForm, r++, "Home state (2-letter)",   homeStateField);
        UiSupport.addRow(personnelForm, r++, "Phone number",            phoneField);
        UiSupport.addRow(personnelForm, r++, "Radio channel/talkgroup", radioChannelField);
        UiSupport.addRow(personnelForm, r++, "Check-in date/time",      checkInSpinner);
        UiSupport.addRow(personnelForm, r++, "Resource identifier",     personnelResIdField);
        UiSupport.addRow(personnelForm, r++, "Location (e.g. ICP)",     personnelLocField);
        UiSupport.addRow(personnelForm, r++, "Status",                  personnelStatusCombo);
        UiSupport.addRow(personnelForm, r,   "Notes",                   personnelNotesField);

        JPanel resourceForm = UiSupport.formPanel();
        r = 0;
        UiSupport.addRow(resourceForm, r++, "Handler/operator name", handlerNameField);
        UiSupport.addRow(resourceForm, r++, "Resource identifier",   resourceResIdField);
        UiSupport.addRow(resourceForm, r++, "Number of persons",     crewSizeField);
        UiSupport.addRow(resourceForm, r++, "Location (e.g. ICP)",   resourceLocField);
        UiSupport.addRow(resourceForm, r++, "Status",                resourceStatusCombo);
        UiSupport.addRow(resourceForm, r,   "Notes",                 resourceNotesField);

        subContainer.add(headerForm,    "HEADER");
        subContainer.add(personnelForm, "PERSONNEL");
        subContainer.add(resourceForm,  "OTHER");

        Runnable showSubForm = () -> {
            TCardType t = (TCardType) typeCombo.getSelectedItem();
            if (t == TCardType.HEADER) {
                subLayout.show(subContainer, "HEADER");
            } else if (t == TCardType.PERSONNEL) {
                subLayout.show(subContainer, "PERSONNEL");
            } else {
                subLayout.show(subContainer, "OTHER");
            }
        };
        showSubForm.run();
        typeCombo.addActionListener(e -> showSubForm.run());

        // ── outer container: type combo on top, sub-form below ───────────
        JPanel typeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        typeRow.add(new javax.swing.JLabel("Card type:"));
        typeRow.add(typeCombo);

        JPanel outerPanel = new JPanel(new BorderLayout(0, 4));
        outerPanel.add(typeRow, BorderLayout.NORTH);
        outerPanel.add(subContainer, BorderLayout.CENTER);

        // ── related resources + Add-to-Assignment section ─────────────────
        JPanel relatedPanel = buildRelatedResourcesPanel(card);

        // Button that reads the current name from the active sub-form and opens
        // an assignment picker dialog without closing this edit dialog.
        JButton addToAssignBtn = new JButton("Add to Assignment…");
        addToAssignBtn.addActionListener(e -> {
            TCardType currentType = (TCardType) typeCombo.getSelectedItem();
            String currentName;
            if (currentType == TCardType.PERSONNEL) {
                currentName = personNameField.getText().trim();
            } else if (currentType == TCardType.HEADER) {
                currentName = headerResourceIdField.getText().trim();
            } else {
                currentName = resourceResIdField.getText().trim();
            }
            if (currentName.isBlank()) {
                JOptionPane.showMessageDialog(
                        javax.swing.SwingUtilities.getWindowAncestor(this),
                        "Please enter a name or identifier before adding to an assignment.",
                        "Add to Assignment", JOptionPane.WARNING_MESSAGE);
                return;
            }
            showAddToAssignmentDialog(
                    javax.swing.SwingUtilities.getWindowAncestor(this),
                    currentName, currentType, card.getResourceId());
        });
        JPanel addToAssignRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 2));
        addToAssignRow.add(addToAssignBtn);

        JPanel southPanel = new JPanel();
        southPanel.setLayout(new BoxLayout(southPanel, BoxLayout.Y_AXIS));
        if (relatedPanel != null) {
            southPanel.add(relatedPanel);
        }
        southPanel.add(addToAssignRow);
        outerPanel.add(southPanel, BorderLayout.SOUTH);

        JScrollPane scroll = new JScrollPane(outerPanel);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        if (!UiSupport.showResizableConfirmDialog(
                javax.swing.SwingUtilities.getWindowAncestor(this), "Edit T-Card", scroll,
                new Dimension(520, relatedPanel != null ? 540 : 480))) {
            return false;
        }

        TCardType chosenType = (TCardType) typeCombo.getSelectedItem();
        card.setCardType(chosenType);
        if (chosenType == TCardType.HEADER) {
            card.setResourceIdentifier(headerResourceIdField.getText().trim());
            card.setLocation(headerLocationField.getText().trim());
            card.setNotes(headerNotesField.getText().trim());
        } else {
            if (chosenType == TCardType.PERSONNEL) {
                card.setPersonName(personNameField.getText().trim());
                card.setHomeAgency(homeAgencyField.getText().trim());
                card.setHomeState(homeStateField.getText().trim());
                card.setPhoneNumber(phoneField.getText().trim());
                card.setRadioChannel(radioChannelField.getText().trim());
                Object spinnerVal = checkInSpinner.getValue();
                if (spinnerVal instanceof Date d) {
                    card.setCheckInDateTime(LocalDateTime.ofInstant(d.toInstant(), ZoneId.systemDefault()));
                }
                card.setResourceIdentifier(personnelResIdField.getText().trim());
                card.setLocation(personnelLocField.getText().trim());
                card.setStatus((String) personnelStatusCombo.getSelectedItem());
                card.setNotes(personnelNotesField.getText().trim());
            } else {
                card.setHandlerName(handlerNameField.getText().trim());
                card.setResourceIdentifier(resourceResIdField.getText().trim());
                card.setLocation(resourceLocField.getText().trim());
                card.setStatus((String) resourceStatusCombo.getSelectedItem());
                card.setNotes(resourceNotesField.getText().trim());
                String crewSizeText = crewSizeField.getText().trim();
                card.setNumberOfPersons(crewSizeText.isEmpty() ? 0 : parseIntOrZero(crewSizeText));
            }
        }
        return true;
    }

    /**
     * Opens a modal dialog allowing the user to add the T-card resource to a SAR task
     * assignment or (for PERSONNEL cards) to an ICS org chart role.
     *
     * <p>Changes are applied immediately and {@link AppController#markDirty()} is called.</p>
     *
     * @param parent      parent window for the dialog.
     * @param resourceName the effective name of the resource (from the currently open edit form).
     * @param cardType    the card type currently selected in the edit form.
     * @param cardResourceId stable UUID of the T-card being edited, used to link the
     *                       SarTaskResource by UUID rather than by name.
     */
    private void showAddToAssignmentDialog(java.awt.Window parent, String resourceName,
                                           TCardType cardType, String cardResourceId) {
        List<SarTaskAssignment> tasks = controller.getData().getSarTaskAssignments();

        // ── SAR Task tab ──────────────────────────────────────────────────
        String[] sarCols = {"Team #", "Type", "Leader", "Assignment"};
        javax.swing.table.DefaultTableModel sarModel = new javax.swing.table.DefaultTableModel(sarCols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        for (SarTaskAssignment t : tasks) {
            sarModel.addRow(new Object[]{
                    t.getAssignmentTeamNumber(),
                    t.getResourceType(),
                    t.getLeader(),
                    t.getAssignment()
            });
        }
        JTable sarTable = new JTable(sarModel);
        sarTable.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        sarTable.getTableHeader().setReorderingAllowed(false);
        JScrollPane sarScroll = new JScrollPane(sarTable);
        sarScroll.setPreferredSize(new Dimension(480, 160));

        JButton addAsResBtn  = new JButton("Add as Resource");
        JButton setAsLeaderBtn = new JButton("Set as Leader");
        JPanel sarBtnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        sarBtnRow.add(addAsResBtn);
        sarBtnRow.add(setAsLeaderBtn);

        JPanel sarTab = new JPanel(new BorderLayout(0, 4));
        sarTab.add(sarScroll, BorderLayout.CENTER);
        sarTab.add(sarBtnRow, BorderLayout.SOUTH);

        // Wire SAR buttons — they apply to the selected task and close the dialog.
        javax.swing.JDialog[] dlgRef = new javax.swing.JDialog[1];

        addAsResBtn.addActionListener(e -> {
            int row = sarTable.getSelectedRow();
            if (row < 0 || row >= tasks.size()) {
                JOptionPane.showMessageDialog(parent,
                        "Please select a task.", "Add to Assignment", JOptionPane.WARNING_MESSAGE);
                return;
            }
            // Build a temporary TCard carrying the name/type from the edit form.
            TCard stub = new TCard();
            stub.setCardType(cardType);
            stub.setResourceId(cardResourceId);
            if (cardType == TCardType.PERSONNEL) {
                stub.setPersonName(resourceName);
            } else {
                stub.setResourceIdentifier(resourceName);
            }
            controller.addTCardToSarTask(stub, tasks.get(row), false);
            controller.markDirty();
            if (dlgRef[0] != null) dlgRef[0].dispose();
        });

        setAsLeaderBtn.addActionListener(e -> {
            int row = sarTable.getSelectedRow();
            if (row < 0 || row >= tasks.size()) {
                JOptionPane.showMessageDialog(parent,
                        "Please select a task.", "Add to Assignment", JOptionPane.WARNING_MESSAGE);
                return;
            }
            TCard stub = new TCard();
            stub.setCardType(cardType);
            stub.setResourceId(cardResourceId);
            if (cardType == TCardType.PERSONNEL) {
                stub.setPersonName(resourceName);
            } else {
                stub.setResourceIdentifier(resourceName);
            }
            controller.addTCardToSarTask(stub, tasks.get(row), true);
            controller.markDirty();
            if (dlgRef[0] != null) dlgRef[0].dispose();
        });

        // ── Org Chart Role tab (PERSONNEL only) ───────────────────────────
        javax.swing.JTabbedPane tabs = new javax.swing.JTabbedPane();
        tabs.addTab("SAR Task", sarTab);

        if (cardType == TCardType.PERSONNEL) {
            List<String> roles = AppController.getOrgChartRoleLabels();
            javax.swing.DefaultListModel<String> roleListModel = new javax.swing.DefaultListModel<>();
            roles.forEach(roleListModel::addElement);
            javax.swing.JList<String> roleList = new javax.swing.JList<>(roleListModel);
            roleList.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
            JScrollPane roleScroll = new JScrollPane(roleList);
            roleScroll.setPreferredSize(new Dimension(480, 160));

            JButton assignRoleBtn = new JButton("Assign to Role");
            assignRoleBtn.addActionListener(e -> {
                String selectedRole = roleList.getSelectedValue();
                if (selectedRole == null) {
                    JOptionPane.showMessageDialog(parent,
                            "Please select a role.", "Add to Assignment", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                controller.setOrgChartRoleName(selectedRole, resourceName);
                controller.markDirty();
                if (dlgRef[0] != null) dlgRef[0].dispose();
            });

            JPanel orgTab = new JPanel(new BorderLayout(0, 4));
            orgTab.add(roleScroll, BorderLayout.CENTER);
            JPanel orgBtnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
            orgBtnRow.add(assignRoleBtn);
            orgTab.add(orgBtnRow, BorderLayout.SOUTH);
            tabs.addTab("Org Chart Role", orgTab);
        }

        tabs.setPreferredSize(new Dimension(500, 230));

        if (tasks.isEmpty()) {
            sarTable.setEnabled(false);
            addAsResBtn.setEnabled(false);
            setAsLeaderBtn.setEnabled(false);
        }

        javax.swing.JDialog dlg = new javax.swing.JDialog(parent, "Add to Assignment",
                java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        dlgRef[0] = dlg;
        dlg.setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        JPanel content = new JPanel(new BorderLayout(6, 6));
        content.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        content.add(new javax.swing.JLabel("Add \"" + resourceName + "\" to:"), BorderLayout.NORTH);
        content.add(tabs, BorderLayout.CENTER);
        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> dlg.dispose());
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        btnRow.add(cancelBtn);
        content.add(btnRow, BorderLayout.SOUTH);
        dlg.setContentPane(content);
        dlg.pack();
        dlg.setLocationRelativeTo(parent);
        dlg.setVisible(true);
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
    // Related resources panel (handler ↔ equipment navigation)
    // -------------------------------------------------------------------------

    /**
     * Builds a panel listing resources related to the given card via handler/equipment links.
     * For a PERSONNEL card, lists EQUIPMENT cards whose handlerName matches this card's person name.
     * For an EQUIPMENT card, lists the PERSONNEL card whose personName matches this card's handlerName.
     * Returns {@code null} when no related resources are found.
     */
    private JPanel buildRelatedResourcesPanel(TCard card) {
        List<TCard> allCards = tableModel.getCards();
        List<TCard> related = new ArrayList<>();

        if (card.getCardType() == TCardType.PERSONNEL) {
            String name = card.getPersonName().trim();
            if (!name.isBlank()) {
                for (TCard c : allCards) {
                    if (c != card
                            && (c.getCardType() == TCardType.EQUIPMENT || c.getCardType() == TCardType.MISC_EQUIPMENT)
                            && name.equalsIgnoreCase(c.getHandlerName().trim())) {
                        related.add(c);
                    }
                }
            }
        } else if (card.getCardType() == TCardType.EQUIPMENT || card.getCardType() == TCardType.MISC_EQUIPMENT) {
            String handlerName = card.getHandlerName().trim();
            if (!handlerName.isBlank()) {
                for (TCard c : allCards) {
                    if (c != card
                            && c.getCardType() == TCardType.PERSONNEL
                            && handlerName.equalsIgnoreCase(c.getPersonName().trim())) {
                        related.add(c);
                    }
                }
            }
        }

        if (related.isEmpty()) {
            return null;
        }

        String sectionTitle = card.getCardType() == TCardType.PERSONNEL
                ? "Linked equipment / canines"
                : "Handler / operator";

        JPanel panel = new JPanel(new BorderLayout(0, 2));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createTitledBorder(sectionTitle));

        JPanel rows = new JPanel(new GridBagLayout());
        rows.setOpaque(false);
        int rowIdx = 0;
        for (TCard rel : related) {
            GridBagConstraints labelConstraints = new GridBagConstraints();
            labelConstraints.gridx = 0;
            labelConstraints.gridy = rowIdx;
            labelConstraints.weightx = 1.0;
            labelConstraints.fill = GridBagConstraints.HORIZONTAL;
            labelConstraints.anchor = GridBagConstraints.WEST;
            labelConstraints.insets = new Insets(2, 4, 2, 8);
            String labelText = rel.getCardType().toString()
                    + ": " + effectiveName(rel)
                    + (rel.getResourceIdentifier().isBlank() ? "" : " (" + rel.getResourceIdentifier() + ")");
            rows.add(new JLabel(labelText), labelConstraints);

            GridBagConstraints btnConstraints = new GridBagConstraints();
            btnConstraints.gridx = 1;
            btnConstraints.gridy = rowIdx;
            btnConstraints.anchor = GridBagConstraints.EAST;
            btnConstraints.insets = new Insets(2, 0, 2, 4);
            JButton openBtn = new JButton("Open…");
            final TCard relCard = rel;
            openBtn.addActionListener(ev -> {
                // Re-resolve the index at click time to handle any intervening model changes.
                int currentIdx = tableModel.getCards().indexOf(relCard);
                if (currentIdx >= 0) {
                    TCard copy = copyCard(tableModel.getCard(currentIdx));
                    if (openEditDialog(copy)) {
                        tableModel.replaceCard(currentIdx, copy);
                        refreshDerivedViewsFromTableModel();
                        controller.markDirty();
                    }
                }
            });
            rows.add(openBtn, btnConstraints);
            rowIdx++;
        }
        panel.add(rows, BorderLayout.CENTER);
        return panel;
    }

    // -------------------------------------------------------------------------
    // Table model
    // -------------------------------------------------------------------------

    private static final class TCardTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = {
                "Type", "Name / Resource", "Agency", "State", "Phone",
                "Check-In", "Location", "Status", "Task"
        };

        private final java.util.function.BooleanSupplier sarModeSupplier;
        private final List<TCard> cards = new ArrayList<>();
        private Map<String, String> assignmentTeamByRef = new LinkedHashMap<>();

        TCardTableModel(java.util.function.BooleanSupplier sarModeSupplier) {
            this.sarModeSupplier = sarModeSupplier;
        }

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

        int indexOfIdentity(TCard card) {
            for (int i = 0; i < cards.size(); i++) {
                if (cards.get(i) == card) {
                    return i;
                }
            }
            return -1;
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
                case 0 -> card.getCardType().getLabel(sarModeSupplier.getAsBoolean());
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

    private static final class DirectoryTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = {
                "Name", "Contact", "Assigned Position", "Unit", "State", "Assignment", "Status"
        };

        private final List<ResourceDirectoryEntry> entries = new ArrayList<>();

        void setEntries(List<ResourceDirectoryEntry> newEntries) {
            entries.clear();
            if (newEntries != null) {
                entries.addAll(newEntries);
            }
            fireTableDataChanged();
        }

        ResourceDirectoryEntry getEntry(int row) {
            return entries.get(row);
        }

        List<String> distinctStates() {
            return distinct(ResourceDirectoryEntry::state);
        }

        List<String> distinctAssignedPositions() {
            return distinct(ResourceDirectoryEntry::assignedPosition);
        }

        List<String> distinctUnits() {
            return distinct(ResourceDirectoryEntry::unit);
        }

        List<String> distinctAssignments() {
            return distinct(ResourceDirectoryEntry::assignment);
        }

        private List<String> distinct(java.util.function.Function<ResourceDirectoryEntry, String> extractor) {
            return entries.stream()
                    .map(extractor)
                    .filter(value -> value != null && !value.isBlank())
                    .distinct()
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
        }

        @Override public int getRowCount() { return entries.size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int column) { return COLUMNS[column]; }
        @Override public boolean isCellEditable(int rowIndex, int columnIndex) { return false; }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            ResourceDirectoryEntry entry = entries.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> entry.name();
                case 1 -> entry.contactMethods();
                case 2 -> entry.assignedPosition();
                case 3 -> entry.unit();
                case 4 -> entry.state();
                case 5 -> entry.assignment();
                case 6 -> entry.status();
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
            case FIXED_WING    -> new Color(255, 200, 100);
            case EQUIPMENT     -> new Color(255, 255, 153);
            case MISC_EQUIPMENT -> new Color(240, 220, 180);
            case GENERIC       -> new Color(210, 180, 240);
        };
    }

    private static int parseIntOrZero(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
