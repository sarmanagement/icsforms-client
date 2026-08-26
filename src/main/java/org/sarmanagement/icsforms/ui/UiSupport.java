package org.sarmanagement.icsforms.ui;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JList;
import javax.swing.JSpinner;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerDateModel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JViewport;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Dimension;
import java.awt.Color;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.sarmanagement.icsforms.model.SarTaskAssignment;

/**
 * Shared Swing layout helpers for compact form editing panels.
 */
final class UiSupport {
    static final Color REQUIRED_FIELD_BACKGROUND = new Color(255, 248, 225);
    private static final String FORM_SPACER_PROPERTY = "uiSupport.formSpacer";

    private UiSupport() {
    }

    /**
     * Creates a standard form panel with grid bag layout.
     *
     * @return configured panel.
     */
    static JPanel formPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        return panel;
    }

    /**
     * Adds a labeled component to a form panel.
     *
     * @param panel target panel.
     * @param row row index.
     * @param label label text.
     * @param component field component.
     */
    static void addRow(JPanel panel, int row, String label, JComponent component) {
        addRow(panel, row, label, component, false);
    }

    /**
     * Adds a labeled required component to a form panel.
     *
     * @param panel target panel.
     * @param row row index.
     * @param label label text.
     * @param component field component.
     */
    static void addRequiredRow(JPanel panel, int row, String label, JComponent component) {
        addRow(panel, row, label, component, true);
    }

    /**
     * Adds a full-width component row to a form panel.
     *
     * @param panel target panel.
     * @param row row index.
     * @param component row component.
     */
    static void addWideRow(JPanel panel, int row, JComponent component) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = row;
        constraints.gridwidth = 2;
        constraints.weightx = 1.0;
        constraints.anchor = GridBagConstraints.NORTHWEST;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = new Insets(3, 3, 3, 3);
        panel.add(component, constraints);
        updateBottomSpacer(panel, row + 1);
    }

    private static void addRow(JPanel panel, int row, String label, JComponent component, boolean required) {
        JLabel fieldLabel = new JLabel(required ? label + " (required)" : label);
        fieldLabel.setLabelFor(labelTarget(component));
        if (required) {
            markRequired(component, label);
        }

        GridBagConstraints left = new GridBagConstraints();
        left.gridx = 0;
        left.gridy = row;
        left.anchor = GridBagConstraints.NORTHWEST;
        left.insets = new Insets(3, 3, 3, 8);
        panel.add(fieldLabel, left);

        GridBagConstraints right = new GridBagConstraints();
        right.gridx = 1;
        right.gridy = row;
        right.weightx = 1.0;
        right.anchor = GridBagConstraints.NORTHWEST;
        right.fill = GridBagConstraints.HORIZONTAL;
        right.insets = new Insets(3, 0, 3, 3);
        panel.add(component, right);
        updateBottomSpacer(panel, row + 1);
    }

    private static void updateBottomSpacer(JPanel panel, int row) {
        Object existing = panel.getClientProperty(FORM_SPACER_PROPERTY);
        if (existing instanceof JPanel spacer) {
            panel.remove(spacer);
        }
        JPanel spacer = new JPanel();
        spacer.setOpaque(false);
        GridBagConstraints filler = new GridBagConstraints();
        filler.gridx = 0;
        filler.gridy = row;
        filler.gridwidth = 2;
        filler.weightx = 1.0;
        filler.weighty = 1.0;
        filler.fill = GridBagConstraints.BOTH;
        panel.add(spacer, filler);
        panel.putClientProperty(FORM_SPACER_PROPERTY, spacer);
    }

    private static JComponent labelTarget(JComponent component) {
        if (component instanceof JScrollPane scrollPane && scrollPane.getViewport().getView() instanceof JComponent child) {
            return child;
        }
        return component;
    }

    private static void markRequired(JComponent component, String label) {
        applyRequiredBackground(component);
        JComponent accessibleTarget = labelTarget(component);
        String existingDescription = accessibleTarget.getAccessibleContext().getAccessibleDescription();
        String requiredDescription = label + " is a required field.";
        accessibleTarget.getAccessibleContext().setAccessibleDescription(
                existingDescription == null || existingDescription.isBlank()
                        ? requiredDescription
                        : existingDescription + " " + requiredDescription);
    }

    private static void applyRequiredBackground(JComponent component) {
        if (component instanceof JScrollPane scrollPane) {
            JViewport viewport = scrollPane.getViewport();
            viewport.setOpaque(true);
            viewport.setBackground(REQUIRED_FIELD_BACKGROUND);
            if (viewport.getView() instanceof JComponent child) {
                child.setOpaque(true);
                child.setBackground(REQUIRED_FIELD_BACKGROUND);
            }
            return;
        }
        component.setOpaque(true);
        component.setBackground(REQUIRED_FIELD_BACKGROUND);
    }

    /**
     * Creates a multiline text area with line wrapping.
     *
     * @param rows preferred row count.
     * @return configured text area.
     */
    static JTextArea textArea(int rows) {
        JTextArea area = new JTextArea(rows, 40);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setTabSize(4);
        return area;
    }

    /**
     * Creates a standard text field.
     *
     * @return configured text field.
     */
    static JTextField textField() {
        return new JTextField(24);
    }

    /**
     * Creates a standard date/time spinner.
     *
     * @return configured date/time spinner.
     */
    static JSpinner dateTimeSpinner() {
        JSpinner spinner = new JSpinner(new SpinnerDateModel());
        spinner.setEditor(new JSpinner.DateEditor(spinner, "yyyy-MM-dd HH:mm"));
        spinner.setValue(new Date());
        spinner.setPreferredSize(new Dimension(180, spinner.getPreferredSize().height));
        return spinner;
    }

    /**
     * Shows a resizable OK/cancel dialog for richer editors.
     *
     * @param parent parent component.
     * @param title dialog title.
     * @param component dialog content.
     * @param preferredSize preferred minimum content size.
     * @return {@code true} when OK was selected.
     */
    static boolean showResizableConfirmDialog(Component parent, String title, JComponent component, Dimension preferredSize) {
        if (preferredSize != null) {
            component.setPreferredSize(preferredSize);
        }
        JOptionPane optionPane = new JOptionPane(component, JOptionPane.PLAIN_MESSAGE, JOptionPane.OK_CANCEL_OPTION);
        JDialog dialog = optionPane.createDialog(parent, title);
        dialog.setResizable(true);
        dialog.pack();
        if (preferredSize != null) {
            dialog.setSize(new Dimension(
                    Math.max(dialog.getWidth(), preferredSize.width),
                    Math.max(dialog.getHeight(), preferredSize.height)));
        }
        dialog.setVisible(true);
        Object value = optionPane.getValue();
        dialog.dispose();
        return Integer.valueOf(JOptionPane.OK_OPTION).equals(value);
    }

    /**
     * Installs a name picker on a text field.
     *
     * <p>The field remains fully editable for free-form input.  Clicking the field when the
     * suggestion list is non-empty opens a modal pick-list dialog: a filter text box narrows
     * the list in real time, and double-clicking (or pressing Enter on) an entry fills the
     * field and fires the optional {@code onSelected} callback to auto-fill adjacent fields.
     * This avoids focus-stealing and flicker problems associated with pop-up menu approaches.</p>
     *
     * @param nameField   target text field.
     * @param suggestions lazy supplier of the current suggestion list (queried at dialog-open time).
     * @param onSelected  optional callback fired when a suggestion is chosen; receives the
     *                    selected name string.  May be {@code null}.
     */
    static void installNameAutocomplete(JTextField nameField,
                                        Supplier<List<String>> suggestions,
                                        Consumer<String> onSelected) {
        nameField.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getButton() != MouseEvent.BUTTON1) return;
                List<String> all = suggestions.get();
                if (all.isEmpty()) return;
                openPickerDialog(nameField, all, onSelected);
            }
        });
    }

    /**
     * Opens the modal name-picker dialog anchored to {@code anchor}.
     *
     * <p>The dialog contains a filter field and a scrollable list.  Typing in the filter field
     * narrows the list to prefix-matching entries.  Double-clicking an item (or pressing Enter
     * when one is selected) fills {@code nameField} and fires {@code onSelected}.</p>
     */
    private static void openPickerDialog(JTextField nameField,
                                         List<String> allNames,
                                         Consumer<String> onSelected) {
        Window owner = nameField.isShowing()
                ? (Window) javax.swing.SwingUtilities.getWindowAncestor(nameField) : null;
        JDialog dialog = new JDialog(owner, "Select person", java.awt.Dialog.ModalityType.APPLICATION_MODAL);

        // Filter field
        JTextField filterField = new JTextField(nameField.getText().trim(), 20);

        // List model + list
        DefaultListModel<String> listModel = new DefaultListModel<>();
        JList<String> list = new JList<>(listModel);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setVisibleRowCount(10);

        // Populate list according to current filter text
        Runnable applyFilter = () -> {
            String filter = filterField.getText().trim().toLowerCase(Locale.ROOT);
            listModel.clear();
            allNames.stream()
                    .filter(s -> filter.isEmpty() || s.trim().toLowerCase(Locale.ROOT).contains(filter))
                    .forEach(listModel::addElement);
            if (!listModel.isEmpty()) {
                list.setSelectedIndex(0);
            }
        };
        applyFilter.run();

        filterField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e)  { applyFilter.run(); }
            @Override public void removeUpdate(DocumentEvent e)  { applyFilter.run(); }
            @Override public void changedUpdate(DocumentEvent e) {}
        });

        // Accept selection and close
        Runnable accept = () -> {
            String selected = list.getSelectedValue();
            if (selected != null) {
                nameField.setText(selected);
                dialog.dispose();
                if (onSelected != null) {
                    onSelected.accept(selected);
                }
            }
        };

        // Double-click on list item
        list.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() >= 2) accept.run();
            }
        });

        // Enter key in filter field or list triggers acceptance
        filterField.addActionListener(ev -> accept.run());
        list.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override public void keyPressed(java.awt.event.KeyEvent e) {
                if (e.getKeyCode() == java.awt.event.KeyEvent.VK_ENTER) accept.run();
            }
        });

        // Buttons
        JButton okButton = new JButton("Select");
        okButton.addActionListener(ev -> accept.run());
        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(ev -> dialog.dispose());

        JPanel buttonPanel = new JPanel();
        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);

        JPanel top = new JPanel(new BorderLayout(4, 4));
        top.setBorder(BorderFactory.createEmptyBorder(4, 4, 0, 4));
        top.add(new JLabel("Filter:"), BorderLayout.WEST);
        top.add(filterField, BorderLayout.CENTER);

        JPanel center = new JPanel(new BorderLayout());
        center.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        center.add(new JScrollPane(list), BorderLayout.CENTER);

        dialog.getContentPane().setLayout(new BorderLayout());
        dialog.getContentPane().add(top, BorderLayout.NORTH);
        dialog.getContentPane().add(center, BorderLayout.CENTER);
        dialog.getContentPane().add(buttonPanel, BorderLayout.SOUTH);
        dialog.pack();
        dialog.setMinimumSize(new Dimension(260, 280));
        dialog.setLocationRelativeTo(nameField);
        filterField.requestFocusInWindow();
        dialog.setVisible(true);
    }

    /**
     * Returns a human-readable label for a SAR task assignment, combining the team number and
     * assignment name when both are available.  Used in picklists and dialogs.
     *
     * @param task the assignment to label.
     * @return a non-null label string (may be empty if neither field is set).
     */
    static String taskLabel(SarTaskAssignment task) {
        String number = task.getAssignmentTeamNumber() != null ? task.getAssignmentTeamNumber().trim() : "";
        String name = task.getAssignment() != null ? task.getAssignment().trim() : "";
        if (!number.isBlank() && !name.isBlank()) {
            return number + " – " + name;
        }
        return !number.isBlank() ? number : name;
    }

    /**
     * Returns a detecting-task label combining the task team number and the resource identifier
     * (ICS 214 form name) of the resource reporting the clue.  Format: {@code "<teamNumber> – <resourceName>"}.
     *
     * <p>Used when recording and displaying the detecting task in the clue log so the entry
     * shows both which task and which specific resource observed the clue.</p>
     *
     * @param task         the SAR task assignment (provides the team number).
     * @param resourceName the ICS 214 form name / resource identifier for the detecting resource.
     * @return a combined label; falls back to just the resource name if the team number is blank.
     */
    static String detectingTaskLabel(SarTaskAssignment task, String resourceName) {
        String number = task != null && task.getAssignmentTeamNumber() != null
                ? task.getAssignmentTeamNumber().trim() : "";
        String name = resourceName != null ? resourceName.trim() : "";
        if (!number.isBlank() && !name.isBlank()) {
            return number + " – " + name;
        }
        return !number.isBlank() ? number : name;
    }
}
