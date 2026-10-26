/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import java.awt.Color;

/** Compact, accessible presentation shared by focused role and reference panels. */
final class RolePanelSupport {
	static final Color MISSING = new Color(255, 170, 170);
	static final Color PENDING = new Color(255, 230, 140);
	static final Color CURRENT = new Color(165, 225, 165);
	static final Color OPTIONAL = new Color(225, 230, 235);

	/** Prevents utility construction. */
	private RolePanelSupport() {
	}

	/**
	 * Separates banner context into readable lines without allowing an unbounded
	 * incident name to displace controls. Full incident text remains accessible.
	 *
	 * @param label banner label.
	 * @param title role or reference title.
	 * @param incident full incident name.
	 * @param context contextual caveat or operational period.
	 */
	static void banner(JLabel label, String title, String incident, String context) {
		String name = incident == null ? "" : incident;
		String compact = name.length() > 72 ? name.substring(0, 69) + "…" : name;
		label.setText("<html><b>" + html(title) + "</b><br>Incident: " + html(compact)
				+ "<br>" + html(context).replace(" | ", "<br>") + "</html>");
		label.setToolTipText(name);
		label.getAccessibleContext().setAccessibleDescription(title + "; Incident: " + name + "; " + context);
	}

	/**
	 * Gives a textual status an opaque, high-contrast color badge.
	 *
	 * @param label status label retaining its textual meaning.
	 * @param color background state color.
	 */
	static void color(JLabel label, Color color) {
		label.setOpaque(true);
		label.setForeground(Color.BLACK);
		label.setBackground(color);
		label.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
	}

	/**
	 * Escapes plain text for compact HTML labels.
	 *
	 * @param text plain text.
	 * @return escaped text.
	 */
	private static String html(String text) {
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
