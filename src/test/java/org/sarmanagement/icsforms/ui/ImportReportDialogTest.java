/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.persistence.ImportValidationException;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** Headless regression coverage for the report shared by menu and startup imports. */
class ImportReportDialogTest {
	@Test
	void failureIncludesFilePathsAndCorrectiveGuidance() {
		Path file = Path.of("wrong-incident.json");
		String report = ImportReportDialog.formatReport(file, false, List.of(
				new ImportValidationException.Problem(ImportValidationException.Severity.ERROR,
						"$.openActionItems[0].title", "Unknown property 'title'.", "Use 'item' instead."),
				new ImportValidationException.Problem(ImportValidationException.Severity.WARNING,
						"$.schemaVersion", "Missing schema version; treating as legacy.", null)));
		assertTrue(report.contains(file.toAbsolutePath().toString()));
		assertTrue(report.contains("FAILED"));
		assertTrue(report.contains("active incident is unchanged"));
		assertTrue(report.contains("ERROR $.openActionItems[0].title"));
		assertTrue(report.contains("Suggested fix: Use 'item' instead."));
		assertTrue(report.contains("WARNING $.schemaVersion"));
	}

	@Test
	void successfulReportRetainsWarnings() {
		String report = ImportReportDialog.formatReport(Path.of("legacy.json"), true, List.of(
				new ImportValidationException.Problem(ImportValidationException.Severity.WARNING,
						"$.schemaVersion", "Missing schema version; treating as legacy.", null)));
		assertTrue(report.contains("PASSED"));
		assertTrue(report.contains("WARNING $.schemaVersion"));
		assertFalse(report.contains("FAILED"));
	}

	@Test
	void reportIsScrollableSelectableAndHasCopyButton() {
		JPanel panel = ImportReportDialog.createReportPanel(Path.of("export.json"), true, List.of());
		BorderLayout layout = (BorderLayout) panel.getLayout();
		JScrollPane scroll = (JScrollPane) layout.getLayoutComponent(BorderLayout.CENTER);
		JTextArea text = (JTextArea) scroll.getViewport().getView();
		assertFalse(text.isEditable());
		assertTrue(text.isEnabled());
		assertTrue(text.getText().contains("No validation problems."));
		text.selectAll();
		assertEquals(text.getText(), text.getSelectedText());
		JPanel footer = (JPanel) layout.getLayoutComponent(BorderLayout.SOUTH);
		JButton copy = (JButton) ((BorderLayout) footer.getLayout()).getLayoutComponent(BorderLayout.EAST);
		assertEquals("Copy report", copy.getText());
	}

	@Test
	void reportOpensAsVisibleDialog() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		AtomicBoolean visible = new AtomicBoolean();
		SwingUtilities.invokeAndWait(() -> {
			Timer close = new Timer(150, event -> {
				for (Window window : Window.getWindows()) {
					if (window instanceof JDialog dialog && "Incident import report".equals(dialog.getTitle())
							&& dialog.isShowing()) {
						visible.set(true);
						dialog.dispose();
					}
				}
			});
			close.start();
			try {
				ImportReportDialog.show(null, Path.of("fictional.json"), true, List.of());
			} finally {
				close.stop();
			}
		});
		assertTrue(visible.get());
	}
}
