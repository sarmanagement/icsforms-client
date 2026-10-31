/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import java.awt.FlowLayout;
import java.text.ParseException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/** Optional UTC timestamp editor displayed in the configured incident timezone. */
class ActionDateField extends JPanel {
	private final JCheckBox specified = new JCheckBox("Set");
	private final JSpinner spinner = UiSupport.dateTimeSpinner();

	/**
	 * Creates an optional timestamp editor.
	 * @param value UTC timestamp, or null when unset
	 */
	ActionDateField(LocalDateTime value) {
		super(new FlowLayout(FlowLayout.LEFT, 0, 0));
		specified.setSelected(value != null);
		if (value != null) {
			spinner.setValue(AppController.toDate(value));
		}
		spinner.setEnabled(value != null);
		specified.addActionListener(event -> spinner.setEnabled(specified.isSelected()));
		add(specified);
		add(spinner);
	}

	/**
	 * Commits the editor text and returns its UTC value.
	 * @return UTC timestamp, or null when unset
	 * @throws ParseException when the entered date is invalid
	 */
	LocalDateTime getValue() throws ParseException {
		if (!specified.isSelected()) {
			return null;
		}
		spinner.commitEdit();
		return AppController.toLocalDateTime((Date) spinner.getValue());
	}

	/**
	 * Formats a UTC timestamp in the incident display timezone.
	 * @param value UTC timestamp, or null
	 * @return display text, or empty when unset
	 */
	static String format(LocalDateTime value) {
		return UiSupport.formatDateTime(value, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
	}
}
