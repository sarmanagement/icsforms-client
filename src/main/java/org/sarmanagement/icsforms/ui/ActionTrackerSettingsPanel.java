/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.sarmanagement.icsforms.model.AppData;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;

/** Incident-level regular action tracker and ICS 233 export preferences. */
public class ActionTrackerSettingsPanel extends JPanel {
	private final JCheckBox enabled = new JCheckBox("Enable independent Open Action Tracker");
	private final JCheckBox adaptedClues = new JCheckBox("Export SAR clue log as adapted ICS 233");
	private final JComboBox<String> orientation = new JComboBox<>(new String[]{"Landscape", "Portrait"});

	/**
	 * Creates settings controls from an incident.
	 * @param data incident preferences
	 */
	public ActionTrackerSettingsPanel(AppData data) {
		super(new BorderLayout());
		JPanel form = UiSupport.formPanel();
		enabled.setSelected(data.isOpenActionTrackerEnabled());
		adaptedClues.setSelected(data.isClueLogIcs233Enabled());
		orientation.setSelectedIndex(data.isIcs233Portrait() ? 1 : 0);
		UiSupport.addRow(form, 0, "", enabled);
		UiSupport.addRow(form, 1, "", adaptedClues);
		UiSupport.addRow(form, 2, "ICS 233 orientation", orientation);
		UiSupport.addRow(form, 3, "", new JLabel(
				"<html>Tracker availability is independent of SAR / Generic mode.<br>"
						+ "Disabling it hides the tab without deleting actions.<br>"
						+ "The standard clue log remains the default output.</html>"));
		add(form, BorderLayout.CENTER);
	}

	/**
	 * Saves selected preferences without modifying action or clue records.
	 * @param data incident to update
	 */
	public void applyTo(AppData data) {
		data.setOpenActionTrackerEnabled(enabled.isSelected());
		data.setClueLogIcs233Enabled(adaptedClues.isSelected());
		data.setIcs233Portrait(orientation.getSelectedIndex() == 1);
	}
}
