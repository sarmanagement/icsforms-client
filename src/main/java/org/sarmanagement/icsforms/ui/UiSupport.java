package org.sarmanagement.icsforms.ui;

import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JSpinner;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SpinnerDateModel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JViewport;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Dimension;
import java.awt.Color;
import java.util.Date;

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
}
