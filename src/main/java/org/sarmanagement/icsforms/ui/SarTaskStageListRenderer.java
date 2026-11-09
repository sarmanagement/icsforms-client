/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JList;
import javax.swing.border.Border;
import java.awt.Color;
import java.awt.Component;
import java.util.function.Function;

/**
 * List renderer that colours each row by its SAR lifecycle stage and marks the selected row with
 * a border so the stage colour stays visible.
 */
final class SarTaskStageListRenderer extends DefaultListCellRenderer {
	private static final long serialVersionUID = 1L;
	private static final Border SELECTED = BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(new Color(0, 0, 160), 2), BorderFactory.createEmptyBorder(0, 2, 0, 2));
	private static final Border UNSELECTED = BorderFactory.createEmptyBorder(2, 4, 2, 4);

	private final transient Function<Object, SarTaskStage> stageOf;

	/**
	 * Creates a renderer.
	 *
	 * @param stageOf maps a list element to its stage, or null when the row has no SAR task.
	 */
	SarTaskStageListRenderer(Function<Object, SarTaskStage> stageOf) {
		this.stageOf = stageOf;
	}

	/**
	 * Prepares a row, colour-coding it and bordering it when selected.
	 *
	 * @param list list being rendered.
	 * @param value list element.
	 * @param index row index.
	 * @param isSelected whether the row is selected.
	 * @param cellHasFocus whether the row has focus.
	 * @return configured row component.
	 */
	@Override
	public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,
			boolean cellHasFocus) {
		Component row = super.getListCellRendererComponent(list, value, index, false, false);
		putClientProperty("html.disable", Boolean.TRUE);
		SarTaskStage stage = stageOf.apply(value);
		if (stage != null) {
			row.setBackground(stage.color());
			row.setForeground(Color.BLACK);
			setBorder(isSelected ? SELECTED : UNSELECTED);
		} else if (isSelected) {
			row.setBackground(list.getSelectionBackground());
			row.setForeground(list.getSelectionForeground());
		}
		return row;
	}
}
