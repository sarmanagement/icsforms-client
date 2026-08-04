package org.sarmanagement.icsforms.ui;

import javax.swing.JComponent;
import javax.swing.JSpinner;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SpinnerDateModel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Date;

/**
 * Shared Swing layout helpers for compact form editing panels.
 */
final class UiSupport {
    private UiSupport() {
    }

    /**
     * Creates a standard form panel with grid bag layout.
     *
     * @return configured panel.
     */
    static JPanel formPanel() {
        return new JPanel(new GridBagLayout());
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
        GridBagConstraints left = new GridBagConstraints();
        left.gridx = 0;
        left.gridy = row;
        left.anchor = GridBagConstraints.NORTHWEST;
        left.insets = new Insets(4, 4, 4, 4);
        panel.add(new JLabel(label), left);

        GridBagConstraints right = new GridBagConstraints();
        right.gridx = 1;
        right.gridy = row;
        right.weightx = 1.0;
        right.fill = GridBagConstraints.HORIZONTAL;
        right.insets = new Insets(4, 4, 4, 4);
        panel.add(component, right);
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
        return area;
    }

    /**
     * Creates a standard text field.
     *
     * @return configured text field.
     */
    static JTextField textField() {
        return new JTextField(30);
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
        return spinner;
    }
}
