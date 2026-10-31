/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.Ics215Form;

import javax.swing.JOptionPane;
import java.awt.Component;
import java.time.format.DateTimeFormatter;

/**
 * Validates and previews planned ICS 215 dates before an explicit period promotion.
 */
public final class Ics215PromotionDialog {
	private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

	/** Prevents utility construction. */
	private Ics215PromotionDialog() {
	}

	/**
	 * Displays a confirmation containing the exact planned target period.
	 *
	 * @param owner parent component.
	 * @param data incident containing the planned worksheet.
	 * @return true only when the worksheet is valid and the operator confirms.
	 */
	public static boolean confirm(Component owner, AppData data) {
		Ics215Form form = data.getNextPeriodForm215();
		try {
			data.validateIcs215Promotion();
		} catch (IllegalArgumentException exception) {
			JOptionPane.showMessageDialog(owner, exception.getMessage(), "Cannot promote ICS 215",
					JOptionPane.WARNING_MESSAGE);
			return false;
		}
		String preview = "Promote the planned ICS 215 worksheet and apply these dates to Shared?\n\n"
				+ "From: " + DATE_TIME.format(form.getTargetPeriodStart()) + "\n"
				+ "To: " + DATE_TIME.format(form.getTargetPeriodEnd()) + "\n\n"
				+ "Accepted Have snapshots will be retained and linked T-cards flagged for review.";
		return JOptionPane.showConfirmDialog(owner, preview, "Confirm ICS 215 period promotion",
				JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION;
	}
}
