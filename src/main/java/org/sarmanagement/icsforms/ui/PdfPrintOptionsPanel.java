/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.util.List;

/** Per-export ICS 233 layout choices that never change incident preferences. */
class PdfPrintOptionsPanel extends JPanel {
	static final String ADAPTED_CLUE_LOG = "Clue Log (ICS 233-CG adapted)";
	private final JComboBox<String> orientation = new JComboBox<>(new String[]{"Landscape", "Portrait"});
	private final JCheckBox adaptedClues = new JCheckBox("Export SAR clue log as adapted ICS 233");

	/**
	 * Creates standard clue-log and portrait defaults for each export dialog.
	 *
	 * @param formKeys supported renderer keys
	 */
	PdfPrintOptionsPanel(List<String> formKeys) {
		super(new BorderLayout());
		JPanel form = UiSupport.formPanel();
		orientation.setSelectedItem("Portrait");
		adaptedClues.setEnabled(formKeys.contains("Clue Log") && formKeys.contains(ADAPTED_CLUE_LOG));
		UiSupport.addRow(form, 0, "ICS 233 orientation", orientation);
		UiSupport.addRow(form, 1, "", adaptedClues);
		add(form, BorderLayout.CENTER);
	}

	/**
	 * Returns the orientation chosen for this export.
	 *
	 * @return true for portrait, false for landscape
	 */
	boolean isPortrait() {
		return orientation.getSelectedIndex() == 1;
	}

	/**
	 * Replaces only a selected clue log with its adapted renderer, without duplicates.
	 *
	 * @param selectedKeys selected form renderer keys
	 * @return distinct selected keys with this export's clue layout applied
	 */
	List<String> selectedFormKeys(List<String> selectedKeys) {
		boolean adapt = adaptedClues.isEnabled() && adaptedClues.isSelected();
		return selectedKeys.stream()
				.map(key -> adapt && "Clue Log".equals(key) ? ADAPTED_CLUE_LOG : key)
				.distinct().toList();
	}
}
