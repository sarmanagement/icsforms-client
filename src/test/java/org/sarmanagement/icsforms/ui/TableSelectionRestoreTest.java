/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.util.List;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import org.junit.jupiter.api.Test;

class TableSelectionRestoreTest {
	@Test
	void reselectsRowsByContentAfterTheModelIsReloaded() {
		JTable table = new JTable(new DefaultTableModel(new Object[][] {{"a", "1"}, {"b", "2"}}, new Object[] {"x", "y"}));
		table.setRowSelectionInterval(1, 1);
		List<List<String>> signatures = List.of(MainFrame.rowSignature(table, 1));
		table.setModel(new DefaultTableModel(new Object[][] {{"c", "3"}, {"a", "1"}, {"b", "2"}}, new Object[] {"x", "y"}));
		table.clearSelection();
		MainFrame.reselectMatchingRows(table, signatures);
		assertArrayEquals(new int[] {2}, table.getSelectedRows());
	}

	@Test
	void doesNotGuessWhenSeveralRowsMatch() {
		JTable table = new JTable(new DefaultTableModel(new Object[][] {{"a"}, {"a"}}, new Object[] {"x"}));
		MainFrame.reselectMatchingRows(table, List.of(List.of("a")));
		assertArrayEquals(new int[] {}, table.getSelectedRows());
	}
}
