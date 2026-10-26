/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.sarmanagement.icsforms.persistence.ImportValidationException;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.nio.file.Path;
import java.util.List;

/** Presents external incident import results as scrollable, copyable plain text. */
final class ImportReportDialog {
	/** Prevents construction of this dialog utility. */
	private ImportReportDialog() {
	}

	/**
	 * Shows the report without exposing incident content.
	 *
	 * @param owner owning component, or null during startup.
	 * @param source imported file.
	 * @param success whether validation and import succeeded.
	 * @param problems validation errors and warnings.
	 */
	static void show(Component owner, Path source, boolean success,
			List<ImportValidationException.Problem> problems) {
		JOptionPane.showMessageDialog(owner, createReportPanel(source, success, problems),
				"Incident import report", success ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE);
	}

	/**
	 * Builds a report view whose text can be selected or copied in full.
	 *
	 * @param source imported file.
	 * @param success whether the import succeeded.
	 * @param problems validation errors and warnings.
	 * @return scrollable report with a copy button.
	 */
	static JPanel createReportPanel(Path source, boolean success,
			List<ImportValidationException.Problem> problems) {
		JTextArea text = new JTextArea(formatReport(source, success, problems));
		text.setEditable(false);
		text.setLineWrap(true);
		text.setWrapStyleWord(true);
		text.setCaretPosition(0);
		JScrollPane scroll = new JScrollPane(text);
		scroll.setPreferredSize(new Dimension(680, 360));
		JButton copy = new JButton("Copy report");
		copy.addActionListener(event -> {
			text.selectAll();
			text.copy();
			text.setCaretPosition(0);
		});
		JPanel footer = new JPanel(new BorderLayout(8, 0));
		footer.add(new JLabel("Report text can also be selected and copied."), BorderLayout.CENTER);
		footer.add(copy, BorderLayout.EAST);
		JPanel panel = new JPanel(new BorderLayout(8, 8));
		panel.add(scroll, BorderLayout.CENTER);
		panel.add(footer, BorderLayout.SOUTH);
		return panel;
	}

	/**
	 * Formats all problems, including paths and corrective guidance, as plain text.
	 *
	 * @param source imported file.
	 * @param success whether the import succeeded.
	 * @param problems validation errors and warnings.
	 * @return report suitable for display and clipboard copying.
	 */
	static String formatReport(Path source, boolean success,
			List<ImportValidationException.Problem> problems) {
		StringBuilder report = new StringBuilder("File: ").append(source.toAbsolutePath())
				.append("\nImport: ").append(success ? "PASSED" : "FAILED").append("\n");
		if (!success) {
			report.append("The file was not imported. The active incident is unchanged.\n");
		}
		if (problems.isEmpty()) {
			report.append("\nNo validation problems.");
		}
		for (ImportValidationException.Problem problem : problems) {
			report.append("\n").append(problem.severity()).append(" ").append(problem.path())
					.append("\n").append(problem.message()).append("\n");
			if (problem.suggestedFix() != null && !problem.suggestedFix().isBlank()) {
				report.append("Suggested fix: ").append(problem.suggestedFix()).append("\n");
			}
		}
		return report.toString();
	}
}
