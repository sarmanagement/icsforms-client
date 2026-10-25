/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.sarmanagement.icsforms.pdf.PdfOrientation;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.util.List;

/** Per-export radio-plan and ICS 233 layout choices that never change incident preferences. */
class PdfPrintOptionsPanel extends JPanel {
	static final String ADAPTED_CLUE_LOG = "Clue Log (ICS 233-CG adapted)";
	private static final String[] ORIENTATIONS = {"Portrait", "Landscape", "Portrait-fit"};
	private final JComboBox<String> orientation = new JComboBox<>(ORIENTATIONS);
	private final JComboBox<String> radioOrientation = new JComboBox<>(ORIENTATIONS);
	private final JCheckBox adaptedClues = new JCheckBox("Export SAR clue log as adapted ICS 233");

	/**
	 * Creates standard clue-log and portrait defaults for each export dialog.
	 *
	 * @param formKeys supported renderer keys
	 */
	PdfPrintOptionsPanel(List<String> formKeys) {
		super(new BorderLayout());
		JPanel form = UiSupport.formPanel();
		radioOrientation.setSelectedItem("Portrait-fit");
		adaptedClues.setEnabled(formKeys.contains("Clue Log") && formKeys.contains(ADAPTED_CLUE_LOG));
		radioOrientation.setEnabled(formKeys.contains("ICS 205"));
		String fitDescription = "Portrait-fit: landscape form rotated 90° counterclockwise on portrait paper.";
		orientation.setToolTipText(fitDescription);
		radioOrientation.setToolTipText(fitDescription);
		UiSupport.addRow(form, 0, "ICS 205 orientation", radioOrientation);
		UiSupport.addRow(form, 1, "ICS 233 orientation", orientation);
		UiSupport.addRow(form, 2, "", adaptedClues);
		add(form, BorderLayout.CENTER);
	}

	/**
	 * Returns the orientation chosen for this export.
	 *
	 * @return true for portrait layout, false otherwise
	 */
	boolean isPortrait() {
		return ics233Orientation() == PdfOrientation.PORTRAIT;
	}

	/**
	 * Returns whether this export uses native landscape ICS 205 pages.
	 *
	 * @return true for native landscape sheets, false for portrait-fit sheets
	 */
	boolean isIcs205NativeLandscape() {
		return ics205Orientation() == PdfOrientation.LANDSCAPE;
	}

	/**
	 * Returns the ICS 205 layout for this export only.
	 *
	 * @return selected layout, defaulting to portrait-fit when unavailable.
	 */
	PdfOrientation ics205Orientation() {
		return radioOrientation.isEnabled() ? PdfOrientation.values()[radioOrientation.getSelectedIndex()]
				: PdfOrientation.PORTRAIT_FIT;
	}

	/**
	 * Returns the ICS 233 layout for this export only.
	 *
	 * @return selected layout for the tracker and adapted clue log.
	 */
	PdfOrientation ics233Orientation() {
		return PdfOrientation.values()[orientation.getSelectedIndex()];
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
