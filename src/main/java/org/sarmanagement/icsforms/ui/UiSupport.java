package org.sarmanagement.icsforms.ui;

import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JSpinner;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SpinnerDateModel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JViewport;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Dimension;
import java.awt.Color;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Shared Swing layout helpers for compact form editing panels.
 */
final class UiSupport {
    static final Color REQUIRED_FIELD_BACKGROUND = new Color(255, 248, 225);
    private static final String FORM_SPACER_PROPERTY = "uiSupport.formSpacer";

    /**
     * When set to a future timestamp, all autocomplete suggestion popups are suppressed until
     * that time has passed.  This covers both the {@code DocumentListener} path (which fires
     * when {@code setText()} is called during a model refresh) and the {@code focusGained}
     * path (which fires when the tab panel programmatically moves focus to the first field).
     * See {@link #suppressSuggestionsFor(long)}.
     */
    private static volatile long suppressSuggestionsUntil = 0;

    /**
     * Suppresses all autocomplete suggestion popups for the given duration.  Call this
     * immediately before any batch of programmatic {@code setText()} calls on fields that
     * have autocomplete installed (e.g. at the start of {@code refreshFromModel()}), so
     * that neither the document-change trigger nor the focus-gained trigger opens the popup
     * during a tab switch.  Normal user interaction resumes once the window expires.
     *
     * @param durationMs how long (in ms from now) to suppress suggestions.
     */
    static void suppressSuggestionsFor(long durationMs) {
        suppressSuggestionsUntil = System.currentTimeMillis() + durationMs;
    }

    private static boolean isSuggestionsSuppressed() {
        return System.currentTimeMillis() < suppressSuggestionsUntil;
    }

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
     * Installs a drop-down name autocomplete on a text field.
     *
     * <p>When the user types at least one character a popup menu appears below the field
     * showing matching suggestions (prefix match, case-insensitive, up to 10 results).
     * Selecting a suggestion fills the field and, if {@code onSelected} is non-null,
     * invokes the callback so callers can auto-fill adjacent contact fields.</p>
     *
     * @param nameField  target text field.
     * @param suggestions lazy supplier of the current suggestion list.
     * @param onSelected optional callback fired when a suggestion is chosen; receives the
     *                   selected name string.  May be {@code null}.
     */
    static void installNameAutocomplete(JTextField nameField,
                                        Supplier<List<String>> suggestions,
                                        Consumer<String> onSelected) {
        JPopupMenu popup = new JPopupMenu();
        boolean[] updating = {false};

        // Actual popup-rebuild logic, always runs on the EDT (called from Timer or focusGained).
        Runnable rebuildPopup = () -> {
            String text = nameField.getText().trim().toLowerCase(Locale.ROOT);
            popup.removeAll();
            List<String> matched;
            if (text.isEmpty()) {
                // With a blank field show all available names (up to 10).
                matched = suggestions.get().stream().limit(10).toList();
            } else {
                matched = suggestions.get().stream()
                        .filter(s -> s.trim().toLowerCase(Locale.ROOT).startsWith(text))
                        .limit(10)
                        .toList();
            }
            if (matched.isEmpty()) {
                popup.setVisible(false);
                return;
            }
            for (String s : matched) {
                JMenuItem item = new JMenuItem(s);
                item.addActionListener(ev -> {
                    updating[0] = true;
                    nameField.setText(s);
                    updating[0] = false;
                    popup.setVisible(false);
                    if (onSelected != null) {
                        onSelected.accept(s);
                    }
                });
                popup.add(item);
            }
            if (nameField.isShowing()) {
                if (popup.isVisible()) {
                    // Popup is already open — refresh items in-place to avoid the
                    // hide/reshow cycle that causes cursor flicker on every keystroke.
                    popup.revalidate();
                    popup.repaint();
                } else {
                    popup.show(nameField, 0, nameField.getHeight());
                }
            }
        };

        // Debounce timer: fires once after the user pauses typing, so the suggestion list is
        // rebuilt at most once per burst of keystrokes rather than on every character change.
        Timer debounce = new Timer(150, ev -> rebuildPopup.run());
        debounce.setRepeats(false);

        DocumentListener listener = new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { update(); }
            @Override public void removeUpdate(DocumentEvent e) { update(); }
            @Override public void changedUpdate(DocumentEvent e) {}

            private void update() {
                if (updating[0]) return;
                if (isSuggestionsSuppressed()) return;
                // Only show while typing when text is non-empty.
                String text = nameField.getText().trim();
                if (text.isEmpty()) {
                    debounce.stop();
                    popup.setVisible(false);
                    return;
                }
                // Restart the debounce timer so we only compute suggestions after the user pauses.
                debounce.restart();
            }
        };
        nameField.getDocument().addDocumentListener(listener);
        nameField.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) {
                // Show suggestions when the user navigates to the field (mouse click or keyboard
                // Tab traversal), but not when the tab panel moves focus programmatically during
                // a model refresh.  The suppression flag is set by refreshFromModel() callers
                // before setText() calls, so it covers both the DocumentListener and this handler.
                if (!isSuggestionsSuppressed() && !suggestions.get().isEmpty()) {
                    rebuildPopup.run();
                }
            }
            @Override public void focusLost(FocusEvent e) {
                debounce.stop();
                popup.setVisible(false);
            }
        });
    }
}
