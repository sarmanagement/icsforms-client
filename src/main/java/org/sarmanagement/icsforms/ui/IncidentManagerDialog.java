/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.sarmanagement.icsforms.persistence.IncidentId;
import org.sarmanagement.icsforms.persistence.IncidentStore;
import org.sarmanagement.icsforms.persistence.IncidentSummary;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.nio.file.Path;
import java.util.List;

/** Manages saved incidents without opening or changing the active editor. */
public class IncidentManagerDialog extends JDialog {
	private final IncidentStore store;
	private final IncidentId activeId;
	private final Dialogs dialogs;
	private final DefaultTableModel model = new DefaultTableModel(
			new String[]{"Incident", "ID", "Operational period", "Updated", "Store file"}, 0);
	private final JTable table = new JTable(model) {
		/**
		 * Keeps saved summary cells read-only.
		 *
		 * @param row row index.
		 * @param column column index.
		 * @return false.
		 */
		@Override
		public boolean isCellEditable(int row, int column) {
			return false;
		}
	};
	private final JButton backupButton = new JButton("Backup JSON…");
	private final JButton deleteButton = new JButton("Delete…");
	private final JButton openButton = new JButton("Open");
	private final JLabel hint = new JLabel();
	private List<IncidentSummary> summaries = List.of();
	private StartupDialog.StartupAction chosenAction;
	private IncidentId chosenIncidentId;
	private org.sarmanagement.icsforms.model.IncidentMode chosenMode;

	/**
	 * Creates a modal manager using desktop prompts.
	 *
	 * @param owner owning window.
	 * @param store configured persistence store.
	 * @param activeId active incident, or null before opening an editor.
	 */
	public IncidentManagerDialog(Window owner, IncidentStore store, IncidentId activeId) {
		this(owner, store, activeId, new NativeDialogs());
	}

	/**
	 * Creates a manager with injectable prompts for deterministic UI tests.
	 *
	 * @param owner owning window.
	 * @param store configured persistence store.
	 * @param activeId active incident, or null.
	 * @param dialogs chooser, confirmation and feedback implementation.
	 */
	IncidentManagerDialog(Window owner, IncidentStore store, IncidentId activeId, Dialogs dialogs) {
		super(owner, "Manage Incidents", ModalityType.APPLICATION_MODAL);
		this.store = store;
		this.activeId = activeId;
		this.dialogs = dialogs;
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		table.getSelectionModel().addListSelectionListener(event -> updateButtons());
		backupButton.addActionListener(event -> backupSelected());
		deleteButton.addActionListener(event -> deleteSelected());
		openButton.addActionListener(event -> openSelected());
		JButton refreshButton = new JButton("Refresh");
		refreshButton.addActionListener(event -> refresh());
		JButton importButton = new JButton("Load JSON…");
		importButton.addActionListener(event -> importIncident());
		JButton newButton = new JButton("New…");
		newButton.addActionListener(event -> newIncident());
		JButton closeButton = new JButton("Close");
		closeButton.addActionListener(event -> dispose());
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		buttons.add(openButton);
		buttons.add(newButton);
		buttons.add(importButton);
		buttons.add(backupButton);
		buttons.add(deleteButton);
		buttons.add(refreshButton);
		buttons.add(closeButton);
		JPanel content = new JPanel(new BorderLayout(8, 8));
		content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
		var descriptor = store.describe();
		JPanel header = new JPanel(new BorderLayout(0, 6));
		header.add(new JLabel(descriptor.type() + ": " + descriptor.location()), BorderLayout.NORTH);
		header.add(hint, BorderLayout.SOUTH);
		content.add(header, BorderLayout.NORTH);
		content.add(new JScrollPane(table), BorderLayout.CENTER);
		content.add(buttons, BorderLayout.SOUTH);
		setContentPane(content);
		setPreferredSize(new Dimension(1000, 360));
		refresh();
		pack();
		setLocationRelativeTo(owner);
	}

	/**
	 * Returns the selected summary without relying on an outdated row index.
	 *
	 * @return selected incident, or null.
	 */
	private IncidentSummary selected() {
		int row = table.getSelectedRow();
		return row < 0 ? null : summaries.get(row);
	}

	/** Enables actions only for a selection and a supported backup backend. */
	private void updateButtons() {
		boolean selected = table.getSelectedRow() >= 0;
		IncidentSummary summary = selected();
		openButton.setEnabled(selected && !summary.incidentId().equals(activeId));
		backupButton.setEnabled(selected && store.supportsBackup());
		backupButton.setToolTipText(store.supportsBackup()
				? "Create a new JSON file outside the live store; existing files are never overwritten."
				: "This store does not support safe incident backups.");
		deleteButton.setEnabled(selected && !summary.incidentId().equals(activeId));
	}

	/** Refreshes atomically, retaining the selection and previous rows on failure. */
	private void refresh() {
		IncidentSummary previous = selected();
		try {
			List<IncidentSummary> updated = List.copyOf(store.list());
			Object[][] rows = new Object[updated.size()][];
			for (int row = 0; row < updated.size(); row++) {
				IncidentSummary summary = updated.get(row);
				var context = store.load(summary.incidentId()).getIncidentContext();
				String period = context.getOperationalPeriodStart() == null ? "not set"
						: UiSupport.formatIsoDateTime(context.getOperationalPeriodStart());
				period += " — " + (context.getOperationalPeriodEnd() == null ? "not set"
						: UiSupport.formatIsoDateTime(context.getOperationalPeriodEnd()));
				rows[row] = new Object[]{summary.displayLabel(), summary.incidentId().value(), period,
						summary.lastModified(), summary.path()};
			}
			table.clearSelection();
			summaries = updated;
			model.setDataVector(rows, new String[]{"Incident", "ID", "Operational period", "Updated", "Store file"});
			for (int column = 0; column < table.getColumnCount(); column++) {
				table.getColumnModel().getColumn(column).setPreferredWidth(column == 4 ? 350 : 230);
			}
			if (previous != null) {
				for (int row = 0; row < summaries.size(); row++) {
					if (previous.incidentId().equals(summaries.get(row).incidentId())) {
						table.setRowSelectionInterval(row, row);
						break;
					}
				}
			}
			hint.setText(summaries.isEmpty() ? "No saved incidents. Create one or load JSON."
					: "Open, back up, or delete a saved incident. The active incident cannot be deleted.");
		} catch (RuntimeException failure) {
			dialogs.message(this, "Unable to refresh incidents: " + failure.getMessage(), true);
		}
		updateButtons();
	}

	/** Backs up the selected saved document without changing selection or identity. */
	private void backupSelected() {
		IncidentSummary summary = selected();
		if (summary == null) {
			return;
		}
		try {
			Path target = dialogs.chooseBackup(this, summary);
			if (target != null) {
				store.backupFile(summary.incidentId(), target);
				dialogs.message(this, "Backup saved to " + target, false);
			}
		} catch (RuntimeException failure) {
			dialogs.message(this, "Unable to back up incident: " + failure.getMessage(), true);
		}
	}

	/**
	 * Opens the selected saved incident and returns its identity to the caller.
	 */
	private void openSelected() {
		IncidentSummary summary = selected();
		if (summary != null && !summary.incidentId().equals(activeId)) {
			chosenIncidentId = summary.incidentId();
			chosenAction = StartupDialog.StartupAction.OPEN_EXISTING;
			dispose();
		}
	}

	/**
	 * Opens a new-incident workflow and returns its selected phase and mode.
	 */
	private void newIncident() {
		StartupDialog startup = new StartupDialog(this, store, false, false);
		startup.setVisible(true);
		StartupDialog.StartupAction action = startup.getChosenAction();
		if (action != null) {
			chosenAction = action;
			chosenMode = startup.getChosenMode();
			dispose();
		}
	}

	/**
	 * Imports a strictly validated external incident and selects it for opening.
	 */
	private void importIncident() {
		try {
			Path source = dialogs.chooseImport(this);
			if (source == null) {
				return;
			}
			IncidentId imported = store.importFile(source).orElse(null);
			if (imported != null) {
				ImportReportDialog.show(this, source, true, store.getLastImportProblems());
				chosenIncidentId = imported;
				chosenAction = StartupDialog.StartupAction.OPEN_EXISTING;
				dispose();
			}
		} catch (org.sarmanagement.icsforms.persistence.ImportValidationException exception) {
			ImportReportDialog.show(this, exception.getSourceFile(), false, exception.getProblems());
		} catch (RuntimeException failure) {
			dialogs.message(this, "Unable to import incident: " + failure.getMessage(), true);
		}
	}

	/**
	 * Returns the action chosen in this dialog.
	 *
	 * @return selected startup action, or null when dismissed.
	 */
	public StartupDialog.StartupAction getChosenAction() {
		return chosenAction;
	}

	/**
	 * Returns the selected incident identity.
	 *
	 * @return selected incident, or null when the operator chose to create a new incident.
	 */
	public IncidentId getChosenIncidentId() {
		return chosenIncidentId;
	}

	/**
	 * Returns the mode selected for a new incident.
	 *
	 * @return selected mode, or null when no new incident was chosen.
	 */
	public org.sarmanagement.icsforms.model.IncidentMode getChosenMode() {
		return chosenMode;
	}

	/** Confirms a soft deletion, forbidding deletion of the active editor. */
	private void deleteSelected() {
		IncidentSummary summary = selected();
		if (summary == null) {
			return;
		}
		if (summary.incidentId().equals(activeId)) {
			dialogs.message(this, "This incident is active. Switch to another incident before deleting it.", true);
			return;
		}
		String question = "Delete " + summary.displayLabel() + "\nID: " + summary.incidentId().value()
				+ "\nPlease back up first. The incident will be removed from the list and tombstoned"
				+ "\n(.json.deleted); its automatic .bak backup is retained. This is not secure erasure.";
		if (!dialogs.confirmDelete(this, question)) {
			return;
		}
		try {
			store.delete(summary.incidentId());
			refresh();
		} catch (RuntimeException failure) {
			dialogs.message(this, "Unable to delete incident: " + failure.getMessage(), true);
		}
	}

	/** Desktop interactions isolated from management behavior for automated tests. */
	interface Dialogs {
		/**
		 * Chooses an external JSON document to import.
		 *
		 * @param owner parent window.
		 * @return source file, or null on cancellation.
		 */
		Path chooseImport(Window owner);

		/**
		 * Chooses a new external backup filename.
		 *
		 * @param owner parent window.
		 * @param summary selected incident.
		 * @return target, or null on cancellation.
		 */
		Path chooseBackup(Window owner, IncidentSummary summary);

		/**
		 * Confirms soft deletion.
		 *
		 * @param owner parent window.
		 * @param question consequences and incident identity.
		 * @return true only on explicit approval.
		 */
		boolean confirmDelete(Window owner, String question);

		/**
		 * Shows outcome feedback.
		 *
		 * @param owner parent window.
		 * @param message feedback text.
		 * @param error whether the operation failed.
		 */
		void message(Window owner, String message, boolean error);
	}

	/** Native chooser and confirmation implementation. */
	private static final class NativeDialogs implements Dialogs {
		/** Creates the desktop prompt implementation. */
		private NativeDialogs() {
		}

		/** {@inheritDoc} */
		@Override
		public Path chooseImport(Window owner) {
			JFileChooser chooser = new JFileChooser();
			chooser.setDialogTitle("Load incident JSON");
			chooser.setFileFilter(new FileNameExtensionFilter("Incident JSON (*.json)", "json"));
			return chooser.showOpenDialog(owner) == JFileChooser.APPROVE_OPTION
					? chooser.getSelectedFile().toPath() : null;
		}

		/** {@inheritDoc} */
		@Override
		public Path chooseBackup(Window owner, IncidentSummary summary) {
			JFileChooser chooser = new JFileChooser();
			chooser.setDialogTitle("Backup JSON — new filename outside the store (no overwriting)");
			chooser.setFileFilter(new FileNameExtensionFilter("Incident JSON (*.json)", "json"));
			chooser.setSelectedFile(new java.io.File(summary.incidentId().value() + "-backup.json"));
			return chooser.showSaveDialog(owner) == JFileChooser.APPROVE_OPTION
					? chooser.getSelectedFile().toPath() : null;
		}

		/** {@inheritDoc} */
		@Override
		public boolean confirmDelete(Window owner, String question) {
			return JOptionPane.showConfirmDialog(owner, question, "Delete saved incident",
					JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION;
		}

		/** {@inheritDoc} */
		@Override
		public void message(Window owner, String message, boolean error) {
			JOptionPane.showMessageDialog(owner, message, "Manage Incidents",
					error ? JOptionPane.ERROR_MESSAGE : JOptionPane.INFORMATION_MESSAGE);
		}
	}
}
