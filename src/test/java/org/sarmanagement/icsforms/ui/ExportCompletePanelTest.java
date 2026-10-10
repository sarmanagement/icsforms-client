/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import java.awt.BorderLayout;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.nio.file.Path;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** Regression tests for selectable PDF export results and filename copying. */
class ExportCompletePanelTest {
	@Test
	void displaysActualExportedPathsAsReadOnlySelectableText() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			for (List<Path> files : List.of(List.of(Path.of("/tmp/incident IAP.pdf")),
					List.of(Path.of("/tmp/ics-203.pdf"), Path.of("/tmp/ics-207.pdf")))) {
				JPanel panel = MainFrame.createExportCompletePanel(files);
				JTextArea paths = paths(panel);
				assertEquals(String.join("\n", files.stream().map(Path::toString).toList()), paths.getText());
				assertFalse(paths.isEditable());
				paths.selectAll();
				assertEquals(paths.getText(), paths.getSelectedText());
				assertTrue(copyButton(panel).isEnabled());
			}
		});
	}

	@Test
	void emptyFileListDisablesCopy() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			JPanel panel = MainFrame.createExportCompletePanel(List.of());
			assertEquals("", paths(panel).getText());
			assertFalse(copyButton(panel).isEnabled());
		});
	}

	@Test
	void copiesSingleAndMultipleFilenamesToSystemClipboard() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless(), "System clipboard requires a display");
		for (List<Path> files : List.of(List.of(Path.of("/tmp/incident IAP.pdf")),
				List.of(Path.of("/tmp/ics-203.pdf"), Path.of("/tmp/ics-207.pdf")))) {
			SwingUtilities.invokeAndWait(() -> copyButton(MainFrame.createExportCompletePanel(files)).doClick());
			assertEquals(String.join("\n", files.stream().map(path -> path.getFileName().toString()).toList()),
					Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor));
		}
	}

	/**
	 * Finds the selectable file list in the completion panel.
	 *
	 * @param panel completion panel.
	 * @return read-only text area.
	 */
	private static JTextArea paths(JPanel panel) {
		JScrollPane scroll = (JScrollPane) ((BorderLayout) panel.getLayout()).getLayoutComponent(BorderLayout.CENTER);
		return (JTextArea) scroll.getViewport().getView();
	}

	/**
	 * Finds the clipboard action in the completion panel.
	 *
	 * @param panel completion panel.
	 * @return filename-copy button.
	 */
	private static JButton copyButton(JPanel panel) {
		JPanel actions = (JPanel) ((BorderLayout) panel.getLayout()).getLayoutComponent(BorderLayout.SOUTH);
		return (JButton) actions.getComponent(0);
	}
}
