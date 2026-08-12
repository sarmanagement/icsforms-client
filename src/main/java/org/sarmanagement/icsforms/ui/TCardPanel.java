package org.sarmanagement.icsforms.ui;

import org.sarmanagement.icsforms.model.TCard;
import org.sarmanagement.icsforms.model.TCardType;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerDateModel;
import javax.swing.SwingConstants;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Panel displaying and editing ICS 219 T-Card (Resource Status Card) records.
 *
 * <p>Cards are displayed in a table grouped under 219-1 header rows by location/status.
 * Only 219-5 Personnel cards are fully editable in this implementation.</p>
 */
public class TCardPanel extends JPanel {
    private static final String[] STATUS_OPTIONS = {
            "", "Enroute", "At Staging", "Assigned", "Out of Service"
    };

    private final AppController controller;
    private final TCardTableModel tableModel = new TCardTableModel();
    private final JTable table = new JTable(tableModel);

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

        // Colour-code rows by card type.
        table.setDefaultRenderer(Object.class, new TCardCellRenderer());

        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        JButton addPersonnelBtn = new JButton("Add Personnel Card");
        JButton editBtn = new JButton("Edit Selected…");
        JButton removeBtn = new JButton("Remove Selected");

        addPersonnelBtn.addActionListener(e -> addPersonnelCard());
        editBtn.addActionListener(e -> editSelectedCard());
        removeBtn.addActionListener(e -> removeSelectedCard());

        buttonRow.add(addPersonnelBtn);
        buttonRow.add(editBtn);
        buttonRow.add(removeBtn);

        add(new JScrollPane(table), BorderLayout.CENTER);
        add(buttonRow, BorderLayout.SOUTH);
    }

    /**
     * Reloads table rows from the model.
     */
    public void refreshFromModel() {
        tableModel.setCards(new ArrayList<>(controller.getData().getTCards()));
    }

    /**
     * Applies table edits back to the model.
     */
    public void pushToModel() {
        controller.getData().setTCards(new ArrayList<>(tableModel.getCards()));
    }

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
            // Header cards don't have a full edit form; skip.
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

    /**
     * Opens an edit dialog for the given card.
     *
     * @param card card to edit in-place.
     * @return {@code true} when the user confirmed.
     */
    private boolean openEditDialog(TCard card) {
        JTextField personNameField = UiSupport.textField();
        JTextField homeAgencyField = UiSupport.textField();
        JTextField homeStateField = UiSupport.textField();
        homeStateField.setPreferredSize(new Dimension(48, homeStateField.getPreferredSize().height));
        JTextField phoneField = UiSupport.textField();
        JTextField resourceIdField = UiSupport.textField();
        JTextField locationField = UiSupport.textField();
        JComboBox<String> statusCombo = new JComboBox<>(STATUS_OPTIONS);
        JTextField notesField = UiSupport.textField();

        SpinnerDateModel checkInModel = new SpinnerDateModel();
        JSpinner checkInSpinner = new JSpinner(checkInModel);
        checkInSpinner.setEditor(new JSpinner.DateEditor(checkInSpinner, "yyyy-MM-dd HH:mm"));

        // Populate with existing values.
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
            UiSupport.addRow(form, row++, "Person name", personNameField);
            UiSupport.addRow(form, row++, "Home agency", homeAgencyField);
            UiSupport.addRow(form, row++, "Home state (2-letter)", homeStateField);
            UiSupport.addRow(form, row++, "Phone number", phoneField);
            UiSupport.addRow(form, row++, "Check-in date/time", checkInSpinner);
        }
        UiSupport.addRow(form, row++, "Resource identifier", resourceIdField);
        UiSupport.addRow(form, row++, "Location (e.g. ICP)", locationField);
        UiSupport.addRow(form, row++, "Status", statusCombo);
        UiSupport.addRow(form, row, "Notes", notesField);

        JScrollPane scroll = new JScrollPane(form);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        String title = "Edit " + card.getCardType().getLabel();
        if (!UiSupport.showResizableConfirmDialog(
                javax.swing.SwingUtilities.getWindowAncestor(this), title, scroll,
                new Dimension(520, 400))) {
            return false;
        }

        // Write back.
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

        @Override
        public int getRowCount() {
            return cards.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int col) {
            return COLUMNS[col];
        }

        @Override
        public boolean isCellEditable(int row, int col) {
            return false;
        }

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
                        ? card.getCheckInDateTime().toString().replace('T', ' ')
                        : "";
                case 6 -> card.getLocation();
                case 7 -> card.getStatus();
                default -> "";
            };
        }
    }

    // -------------------------------------------------------------------------
    // Cell renderer
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

        private static Color cardColor(TCardType type) {
            return switch (type) {
                case HEADER -> new Color(180, 180, 180);        // grey
                case CREW -> new Color(144, 238, 144);           // green
                case ENGINE -> new Color(255, 182, 193);         // rose
                case HELICOPTER -> new Color(173, 216, 230);     // blue
                case PERSONNEL -> Color.WHITE;                   // white
                case AIRCRAFT -> new Color(255, 200, 100);       // orange
                case DOZER -> new Color(255, 255, 153);          // yellow
                case MISC_EQUIPMENT -> new Color(240, 220, 180); // buff/tan
            };
        }
    }
}
