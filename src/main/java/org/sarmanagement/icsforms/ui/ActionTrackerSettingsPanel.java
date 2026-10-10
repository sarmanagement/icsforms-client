/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.sarmanagement.icsforms.model.AppData;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;

/** Incident-level availability controls for the independent open action tracker. */
public class ActionTrackerSettingsPanel extends JPanel {
	private final JCheckBox enabled = new JCheckBox("Enable independent Open Action Tracker");

	/**
	 * Creates settings controls from an incident.
	 * @param data incident preferences
	 */
	public ActionTrackerSettingsPanel(AppData data) {
		super(new BorderLayout());
		JPanel form = UiSupport.formPanel();
		enabled.setSelected(data.isOpenActionTrackerEnabled());
		UiSupport.addRow(form, 0, "", enabled);
		UiSupport.addRow(form, 1, "", new JLabel(
				"<html>Tracker availability is independent of SAR / Generic mode.<br>"
						+ "Disabling it hides the tab without deleting actions.</html>"));
		add(form, BorderLayout.CENTER);
	}

	/**
	 * Saves tracker availability without modifying records or legacy print preferences.
	 * @param data incident to update
	 */
	public void applyTo(AppData data) {
		data.setOpenActionTrackerEnabled(enabled.isSelected());
	}
}
