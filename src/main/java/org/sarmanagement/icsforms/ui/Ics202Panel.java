/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.Ics202Form;
import org.sarmanagement.icsforms.model.IncidentObjective;
import org.sarmanagement.icsforms.model.ObjectiveAssessment;
import org.sarmanagement.icsforms.model.Ics204Form;
import org.sarmanagement.icsforms.model.ResourceAssignment;
import org.sarmanagement.icsforms.pdf.PdfText;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JLabel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;

/**
 * First-cut editor for ICS 202 Incident Objectives content.
 */
public class Ics202Panel extends JPanel {
	private final AppController controller;
	private final List<IncidentObjective> objectives = new ArrayList<>();
	private final ObjectivesTableModel objectivesModel = new ObjectivesTableModel();
	private final JTable objectivesTable = new JTable(objectivesModel);
	private final JTextField objectiveCellField = UiSupport.textField();
	private final JTextArea objectiveTextArea = UiSupport.textArea(4);
	private final JComboBox<ObjectiveAssessment> objectiveAssessment = new JComboBox<>(ObjectiveAssessment.values());
	private final JTextArea objectiveAssessmentNote = UiSupport.textArea(2);
	private final JLabel objectiveWarning = new JLabel(" ");
	private final JLabel objectiveDialogWarning = new JLabel(" ");
	private final Color objectiveBackground = objectiveTextArea.getBackground();
	private final JTextArea commandEmphasisArea = UiSupport.textArea(3);
	private final JTextArea situationalAwarenessArea = UiSupport.textArea(3);
	private final JCheckBox siteSafetyPlanRequired = new JCheckBox("Site safety plan required");
	private final JCheckBox includeIcs202 = new JCheckBox("ICS 202");
	private final JCheckBox includeIcs204 = new JCheckBox("ICS 204");
	private final JCheckBox includeIcs205 = new JCheckBox("ICS 205");
	private final JCheckBox includeIcs208 = new JCheckBox("ICS 208");
	private final JCheckBox includeIcs215a = new JCheckBox("ICS 215A");
	private final JCheckBox includeIcs206 = new JCheckBox("ICS 206");
	private final JCheckBox includeCanine206 = new JCheckBox("ICS 206CANINE (adapted)");
	private final JCheckBox includeSarTaskAssignment = new JCheckBox("SAR Task Assignment");
	private final JCheckBox includeMapPacket = new JCheckBox("Map packet");
	private final JTextArea additionalFormsArea = UiSupport.textArea(2);
	private final JTextField preparedByNameField = UiSupport.textField();
	private final JTextField preparedByPositionField = UiSupport.textField();
	private final JTextField approvedByNameField = UiSupport.textField();
	private final javax.swing.JSpinner approvedDateTimeField = UiSupport.dateTimeSpinner();
	private final JTextField iapPageField = UiSupport.textField();
	private AppController.IncidentBinding objectiveBinding;
	private boolean objectiveDialogOpen;

	/**
	 * Creates the ICS 202 editor panel.
	 *
	 * @param controller
	 *            application controller.
	 */
	public Ics202Panel(AppController controller) {
		super(new BorderLayout());
		this.controller = controller;
		objectiveBinding = controller.captureIncidentBinding();
		JPanel form = UiSupport.formPanel();
		form.setBorder(BorderFactory.createTitledBorder("ICS 202 Incident Objectives"));
		UiSupport.addRow(form, 0, "Ordered objectives", objectiveEditor());
		UiSupport.addRow(form, 1, "Command emphasis", new JScrollPane(commandEmphasisArea));
		UiSupport.addRow(form, 2, "General situational awareness", new JScrollPane(situationalAwarenessArea));
		UiSupport.addRow(form, 3, "Safety plan", siteSafetyPlanRequired);
		UiSupport.addRow(form, 4, "Included forms / attachments", includedFormsPanel());
		UiSupport.addRow(form, 5, "Prepared by name", preparedByNameField);
		UiSupport.addRow(form, 6, "Prepared by position/title", preparedByPositionField);
		preparedByNameField.setEditable(false);
		preparedByPositionField.setEditable(false);
		UiSupport.addRow(form, 7, "Incident commander", approvedByNameField);
		UiSupport.addRow(form, 8, "IC Approval date/time", approvedDateTimeField);
		iapPageField.setEditable(false);
		iapPageField.setToolTipText("Assigned automatically when the IAP bundle PDF is exported");
		UiSupport.addRow(form, 9, "IAP page (auto)", iapPageField);
		JScrollPane scrollPane = new JScrollPane(form);
		scrollPane.setBorder(BorderFactory.createEmptyBorder());
		add(scrollPane, BorderLayout.CENTER);
	}

	/**
	 * Creates the objective editor with a nonblocking, accessible PDF warning.
	 *
	 * @return objective text editor and inline warning.
	 */
	private JPanel objectiveEditor() {
		JPanel panel = new JPanel(new BorderLayout(0, 4));
		panel.setOpaque(false);
		objectiveWarning.setForeground(new Color(160, 40, 40));
		objectiveWarning.getAccessibleContext().setAccessibleName("Objective PDF export warning");
		objectivesTable.getAccessibleContext().setAccessibleName("Incident objectives");
		objectivesTable.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
		objectivesTable.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
		objectivesTable.setPreferredScrollableViewportSize(new java.awt.Dimension(480, 100));
		objectivesTable.getColumnModel().getColumn(0).setMaxWidth(45);
		objectivesTable.getColumnModel().getColumn(0).setPreferredWidth(45);
		objectivesTable.getColumnModel().getColumn(1).setPreferredWidth(435);
		objectiveCellField.getDocument().putProperty("filterNewlines", Boolean.FALSE);
		objectivesTable.getColumnModel().getColumn(1).setCellEditor(new javax.swing.DefaultCellEditor(objectiveCellField));
		objectivesTable.getColumnModel().getColumn(1).getCellEditor()
				.addCellEditorListener(new javax.swing.event.CellEditorListener() {
					/** {@inheritDoc} */
					@Override
					public void editingStopped(javax.swing.event.ChangeEvent event) {
						javax.swing.SwingUtilities.invokeLater(() -> updateObjectiveWarning());
					}

					/** {@inheritDoc} */
					@Override
					public void editingCanceled(javax.swing.event.ChangeEvent event) {
						javax.swing.SwingUtilities.invokeLater(() -> updateObjectiveWarning());
					}
				});
		objectivesTable.setFillsViewportHeight(true);
		JPanel actions = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEADING));
		JButton add = new JButton("Add objective");
		JButton apply = new JButton("Apply to assignments…");
		JButton edit = new JButton("Edit objective…");
		JButton up = new JButton("Move up");
		JButton down = new JButton("Move down");
		JButton remove = new JButton("Remove…");
		add.addActionListener(event -> addObjective());
		apply.addActionListener(event -> applySelectedObjective());
		edit.addActionListener(event -> editSelectedObjective());
		up.addActionListener(event -> moveSelectedObjective(-1));
		down.addActionListener(event -> moveSelectedObjective(1));
		remove.addActionListener(event -> removeSelectedObjective());
		actions.add(add);
		actions.add(edit);
		actions.add(apply);
		actions.add(up);
		actions.add(down);
		actions.add(remove);
		panel.add(new JScrollPane(objectivesTable), BorderLayout.CENTER);
		panel.add(actions, BorderLayout.NORTH);
		panel.add(objectiveWarning, BorderLayout.SOUTH);
		DocumentListener warningListener = new DocumentListener() {
			/** {@inheritDoc} */
			@Override
			public void insertUpdate(DocumentEvent event) {
				updateObjectiveWarning();
			}

			/** {@inheritDoc} */
			@Override
			public void removeUpdate(DocumentEvent event) {
				updateObjectiveWarning();
			}

			/** {@inheritDoc} */
			@Override
			public void changedUpdate(DocumentEvent event) {
				updateObjectiveWarning();
			}
		};
		objectiveTextArea.getDocument().addDocumentListener(warningListener);
		objectiveCellField.getDocument().addDocumentListener(warningListener);
		updateObjectiveWarning();
		return panel;
	}

	/**
	 * Updates export guidance without rejecting edits, paste, tab switches, or saves.
	 */
	private void updateObjectiveWarning() {
		try {
			if (objectiveDialogOpen) {
				PdfText.validate(objectiveTextArea.getText(), "ICS 202", "Objectives");
			}
			if (objectivesTable.isEditing()) {
				PdfText.validate(objectiveCellField.getText(), "ICS 202", "Objectives");
			}
			for (int index = 0; index < objectives.size(); index++) {
				if (!(objectiveDialogOpen && objectives.get(index) == selectedObjective())
						&& !(objectivesTable.isEditing()
								&& index == objectivesTable.convertRowIndexToModel(objectivesTable.getEditingRow()))) {
					PdfText.validate(objectives.get(index).getText(), "ICS 202", "Objectives");
				}
			}
			objectiveWarning.setText(" ");
			objectiveDialogWarning.setText(" ");
			objectiveWarning.setToolTipText(null);
			objectiveTextArea.setToolTipText(null);
			objectiveTextArea.setBackground(objectiveBackground);
			objectiveCellField.setToolTipText(null);
			objectiveCellField.setBackground(objectiveBackground);
		} catch (IllegalArgumentException exception) {
			String message = exception.getMessage();
			int advice = message.indexOf(". Edit this field");
			objectiveWarning.setText("PDF export warning: " + (advice < 0 ? message : message.substring(0, advice + 1)));
			objectiveDialogWarning.setText(objectiveWarning.getText());
			objectiveDialogWarning.setForeground(objectiveWarning.getForeground());
			objectiveDialogWarning.setToolTipText(message);
			objectiveWarning.setToolTipText(message);
			objectiveTextArea.setToolTipText(message);
			objectiveTextArea.setBackground(new Color(255, 242, 224));
			objectiveCellField.setToolTipText(message);
			objectiveCellField.setBackground(new Color(255, 242, 224));
		}
	}

	/**
	 * Commits the active cell, retaining a rejected edit for correction.
	 *
	 * @return whether editing completed in the originating incident.
	 */
	private boolean finishObjectiveEdit() {
		return controller.isCurrent(objectiveBinding)
				&& (!objectivesTable.isEditing() || objectivesTable.getCellEditor().stopCellEditing());
	}

	/**
	 * Returns the selected stable row.
	 *
	 * @return selected objective, or null.
	 */
	private IncidentObjective selectedObjective() {
		int row = objectivesTable.getSelectedRow();
		return row < 0 ? null : objectives.get(objectivesTable.convertRowIndexToModel(row));
	}

	/** Adds and selects a normal stable objective. */
	private void addObjective() {
		if (!finishObjectiveEdit()) {
			return;
		}
		IncidentObjective objective = new IncidentObjective("", objectives.size());
		objectives.add(objective);
		controller.getData().getForm202().setStructuredObjectives(modelObjectives());
		objectivesModel.fireTableDataChanged();
		updateObjectiveWarning();
		int viewRow = objectivesTable.convertRowIndexToView(objectives.size() - 1);
		objectivesTable.setRowSelectionInterval(viewRow, viewRow);
		objectivesTable.editCellAt(viewRow, 1);
		objectivesTable.getEditorComponent().requestFocusInWindow();
		controller.markDirty();
	}

	/** Opens an explicit text and assessment draft, saving only on confirmation. */
	private void editSelectedObjective() {
		if (!finishObjectiveEdit()) {
			return;
		}
		IncidentObjective objective = selectedObjective();
		if (objective == null) {
			return;
		}
		AppController.IncidentBinding binding = controller.captureIncidentBinding();
		objectiveDialogOpen = true;
		objectiveTextArea.setText(objective.getText());
		objectiveAssessment.setSelectedItem(objective.getAssessment());
		objectiveAssessmentNote.setText(objective.getAssessmentNote());
		JPanel detail = UiSupport.formPanel();
		UiSupport.addRow(detail, 0, "Objective text", new JScrollPane(objectiveTextArea));
		UiSupport.addRow(detail, 1, "Assessment", objectiveAssessment);
		UiSupport.addRow(detail, 2, "Assessment note", new JScrollPane(objectiveAssessmentNote));
		UiSupport.addWideRow(detail, 3, objectiveDialogWarning);
		int choice;
		try {
			choice = UiSupport.showResizableOptionDialog(this, "Edit incident objective", detail,
					new java.awt.Dimension(640, 300), new Object[]{"Save", "Cancel"}, "Save");
		} finally {
			objectiveDialogOpen = false;
		}
		if (choice != 0
				|| !controller.isCurrent(binding)
				|| !controller.getData().getForm202().getStructuredObjectives().contains(objective)) {
			objectiveTextArea.setText("");
			updateObjectiveWarning();
			return;
		}
		ObjectiveAssessment previous = objective.getAssessment();
		String previousNote = objective.getAssessmentNote();
		objective.setText(objectiveTextArea.getText());
		objective.setAssessment((ObjectiveAssessment) objectiveAssessment.getSelectedItem());
		objective.setAssessmentNote(objectiveAssessmentNote.getText().trim());
		if (previous != objective.getAssessment()
				|| !java.util.Objects.equals(previousNote, objective.getAssessmentNote())) {
			objective.setAssessedBy(controller.getData().getIncidentContext().getCurrentUser());
			objective.setAssessedAt(java.time.LocalDateTime.now().withSecond(0).withNano(0));
		}
		controller.getData().getForm202().setStructuredObjectives(modelObjectives());
		int row = objectives.indexOf(objective);
		objectivesModel.fireTableRowsUpdated(row, row);
		int viewRow = objectivesTable.convertRowIndexToView(row);
		if (viewRow >= 0) {
			objectivesTable.setRowSelectionInterval(viewRow, viewRow);
		}
		objectiveTextArea.setText("");
		updateObjectiveWarning();
		controller.markDirty();
	}

	/** Selects existing assignments grouped by branch, division and group. */
	private void applySelectedObjective() {
		if (!finishObjectiveEdit() || selectedObjective() == null) {
			return;
		}
		AppController.IncidentBinding binding = controller.captureIncidentBinding();
		IncidentObjective objective = selectedObjective();
		AssignmentSelection selection = new AssignmentSelection(controller.getData(), objective.getObjectiveId());
		while (controller.isCurrent(binding)
				&& controller.getData().getForm202().getStructuredObjectives().contains(objective)) {
			int choice = UiSupport.showResizableOptionDialog(this, "Apply objective to assignments", selection,
					new java.awt.Dimension(720, 360), new Object[]{"Apply", "Cancel"}, "Apply");
			if (choice != 0 || !controller.isCurrent(binding)) {
				return;
			}
			if (selection.finishEditing()) {
				if (controller.getData().getForm202().getStructuredObjectives().contains(objective)) {
					selection.apply(objective.getObjectiveId());
					controller.markDirty();
				}
				return;
			}
		}
	}

	/**
	 * Moves a selected stable objective without changing its identity.
	 *
	 * @param offset negative to move up, positive to move down.
	 */
	private void moveSelectedObjective(int offset) {
		if (!finishObjectiveEdit()) {
			return;
		}
		int viewSource = objectivesTable.getSelectedRow();
		int source = viewSource < 0 ? -1 : objectivesTable.convertRowIndexToModel(viewSource);
		int target = source + offset;
		if (source < 0 || target < 0 || target >= objectives.size()) {
			return;
		}
		IncidentObjective objective = objectives.remove(source);
		objectives.add(target, objective);
		controller.getData().getForm202().setStructuredObjectives(modelObjectives());
		objectivesModel.fireTableDataChanged();
		updateObjectiveWarning();
		int viewTarget = objectivesTable.convertRowIndexToView(target);
		objectivesTable.setRowSelectionInterval(viewTarget, viewTarget);
		controller.markDirty();
	}

	/** Removes an objective after explicit resolution of assignment links. */
	private void removeSelectedObjective() {
		if (!finishObjectiveEdit()) {
			return;
		}
		AppController.IncidentBinding binding = controller.captureIncidentBinding();
		IncidentObjective objective = selectedObjective();
		if (objective == null) {
			return;
		}
		boolean unlink = false;
		if (!controller.getData().getAssignmentsForObjective(objective.getObjectiveId()).isEmpty()) {
			int choice = JOptionPane.showConfirmDialog(this,
					"This objective has linked assignments. Explicitly unlink them and remove the objective?",
					"Resolve objective links", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
			if (choice != JOptionPane.OK_OPTION) {
				return;
			}
			unlink = true;
		}
		if (!controller.isCurrent(binding)) {
			return;
		}
		controller.getData().removeObjective(objective.getObjectiveId(), "", unlink);
		refreshObjectives(controller.getData().getForm202());
		controller.markDirty();
	}

	/**
	 * Returns stable objectives currently displayed by the ordered editor.
	 *
	 * @return ordered objective list.
	 */
	private List<IncidentObjective> modelObjectives() {
		return new ArrayList<>(objectives);
	}

	/**
	 * Reloads stable objective rows without creating new identities.
	 *
	 * @param form active ICS 202 form.
	 * @throws IllegalStateException if an active edit in the current incident is rejected.
	 */
	private void refreshObjectives(Ics202Form form) {
		if (objectivesTable.isEditing()) {
			if (controller.isCurrent(objectiveBinding)) {
				UiSupport.commitTableEdits(objectivesTable);
			} else {
				objectivesTable.getCellEditor().cancelCellEditing();
			}
		}
		String selectedId = selectedObjective() == null
				? ""
				: selectedObjective().getObjectiveId();
		objectives.clear();
		objectives.addAll(form.getStructuredObjectives());
		objectiveBinding = controller.captureIncidentBinding();
		objectivesModel.fireTableDataChanged();
		updateObjectiveWarning();
		for (int index = 0; index < objectives.size(); index++) {
			if (selectedId.equals(objectives.get(index).getObjectiveId())) {
				int viewRow = objectivesTable.convertRowIndexToView(index);
				objectivesTable.setRowSelectionInterval(viewRow, viewRow);
				return;
			}
		}
		if (!objectives.isEmpty()) {
			int viewRow = objectivesTable.convertRowIndexToView(0);
			objectivesTable.setRowSelectionInterval(viewRow, viewRow);
		}
	}

	/**
	 * Loads values after committing current-incident table edits.
	 *
	 * @throws IllegalStateException if the current incident's active edit is rejected.
	 */
	public void refreshFromModel() {
		Ics202Form form = controller.getData().getForm202();
		refreshObjectives(form);
		commandEmphasisArea.setText(nullSafe(form.getCommandEmphasis()));
		situationalAwarenessArea.setText(nullSafe(form.getSituationalAwareness()));
		siteSafetyPlanRequired.setSelected(form.isSiteSafetyPlanRequired());
		setIncludedForms(form.getIncidentActionPlanAttachments());
		preparedByNameField.setText(nullSafe(form.getPreparedByName()));
		preparedByPositionField.setText(nullSafe(form.getPreparedByPositionTitle()));
		approvedByNameField.setText(nullSafe(form.getApprovedByIncidentCommanderName()));
		approvedDateTimeField.setValue(AppController.toDate(form.getApprovedDateTime()));
		iapPageField.setText(nullSafe(form.getIapPage()));
	}

	/**
	 * Applies values without discarding objective text or blocking incident saves.
	 * Unsupported PDF characters remain in the model for export-time diagnostics.
	 */
	public void pushToModel() {
		if (!finishObjectiveEdit()) {
			if (!controller.isCurrent(objectiveBinding)) {
				return;
			}
			throw new IllegalStateException("Finish or cancel the incident objective edit before saving.");
		}
		Ics202Form form = controller.getData().getForm202();
		form.setStructuredObjectives(modelObjectives());
		form.setCommandEmphasis(commandEmphasisArea.getText().trim());
		form.setSituationalAwareness(situationalAwarenessArea.getText().trim());
		form.setSiteSafetyPlanRequired(siteSafetyPlanRequired.isSelected());
		List<String> includedForms = getIncludedForms();
		form.setIncidentActionPlanAttachments(includedForms);
		synchronizeMedicalPlanInclusion(controller.getData(), includedForms);
		form.setApprovedByIncidentCommanderName(approvedByNameField.getText().trim());
		form.setPreparedByName(preparedByNameField.getText().trim());
		form.setPreparedByPositionTitle(preparedByPositionField.getText().trim());
		form.setApprovedDateTime(AppController.toLocalDateTime((java.util.Date) approvedDateTimeField.getValue()));
		form.setIapPage(iapPageField.getText().trim());
	}

	/** Compact ordered grid; only objective text is editable inline. */
	private class ObjectivesTableModel extends AbstractTableModel {
		/** {@inheritDoc} */
		@Override
		public int getRowCount() {
			return objectives.size();
		}

		/** {@inheritDoc} */
		@Override
		public int getColumnCount() {
			return 2;
		}

		/** {@inheritDoc} */
		@Override
		public String getColumnName(int column) {
			return column == 0 ? "#" : "Objective";
		}

		/** {@inheritDoc} */
		@Override
		public Object getValueAt(int row, int column) {
			return column == 0 ? row + 1 : objectives.get(row).getText();
		}

		/** {@inheritDoc} */
		@Override
		public boolean isCellEditable(int row, int column) {
			return column == 1;
		}

		/** {@inheritDoc} */
		@Override
		public void setValueAt(Object value, int row, int column) {
			if (column != 1 || !controller.isCurrent(objectiveBinding)) {
				return;
			}
			objectives.get(row).setText(value == null ? "" : value.toString());
			controller.getData().getForm202().setStructuredObjectives(modelObjectives());
			fireTableCellUpdated(row, column);
			updateObjectiveWarning();
			controller.markDirty();
		}
	}

	/** Detached checkbox selection of existing assignments, ordered by their context. */
	static class AssignmentSelection extends JPanel {
		private final List<ResourceAssignment> assignments = new ArrayList<>();
		private final List<String> contexts = new ArrayList<>();
		private final List<Boolean> selected = new ArrayList<>();
		private final JTable table;

		/**
		 * Creates an assignment selection without modifying any model links.
		 *
		 * @param data originating incident.
		 * @param objectiveId objective whose links are being edited.
		 */
		AssignmentSelection(AppData data, String objectiveId) {
			super(new BorderLayout());
			List<Ics204Form> forms = new ArrayList<>();
			forms.add(data.getForm204());
			forms.addAll(data.getAdditionalForms204());
			forms.sort(java.util.Comparator.comparing(AssignmentSelection::context));
			java.util.Set<String> seen = new java.util.HashSet<>();
			for (Ics204Form form : forms) {
				for (ResourceAssignment assignment : form.getResourcesAssigned()) {
					if (seen.add(assignment.getAssignmentId())) {
						assignments.add(assignment);
						contexts.add(context(form));
						selected.add(assignment.getObjectiveIds().contains(objectiveId));
					}
				}
			}
			table = new JTable(new AbstractTableModel() {
				/** {@inheritDoc} */
				@Override
				public int getRowCount() {
					return assignments.size();
				}

				/** {@inheritDoc} */
				@Override
				public int getColumnCount() {
					return 4;
				}

				/** {@inheritDoc} */
				@Override
				public String getColumnName(int column) {
					return new String[]{"Linked", "Branch / Division / Group", "Assignment/Team #", "Resource"}[column];
				}

				/** {@inheritDoc} */
				@Override
				public Class<?> getColumnClass(int column) {
					return column == 0 ? Boolean.class : String.class;
				}

				/** {@inheritDoc} */
				@Override
				public Object getValueAt(int row, int column) {
					return switch (column) {
						case 0 -> selected.get(row);
						case 1 -> contexts.get(row);
						case 2 -> assignments.get(row).getAssignmentTeamNumber();
						default -> assignments.get(row).getResourceIdentifier();
					};
				}

				/** {@inheritDoc} */
				@Override
				public boolean isCellEditable(int row, int column) {
					return column == 0;
				}

				/** {@inheritDoc} */
				@Override
				public void setValueAt(Object value, int row, int column) {
					if (column == 0) {
						selected.set(row, Boolean.TRUE.equals(value));
						fireTableCellUpdated(row, column);
					}
				}
			});
			table.getColumnModel().getColumn(0).setMaxWidth(60);
			table.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
			add(new JLabel("Select zero or more existing assignments. Other objective links are preserved."),
					BorderLayout.NORTH);
			add(new JScrollPane(table), BorderLayout.CENTER);
		}

		/**
		 * Labels a form's branch, division and group for contiguous grouping.
		 *
		 * @param form assignment context.
		 * @return readable context, including an incident-level fallback.
		 */
		private static String context(Ics204Form form) {
			List<String> parts = new ArrayList<>();
			if (!form.getBranch().isBlank()) {
				parts.add("Branch: " + form.getBranch());
			}
			if (!form.getDivision().isBlank()) {
				parts.add("Division: " + form.getDivision());
			}
			if (!form.getGroup().isBlank()) {
				parts.add("Group: " + form.getGroup());
			}
			return parts.isEmpty() ? "Incident" : String.join(" / ", parts);
		}

		/**
		 * Commits any pending checkbox change.
		 *
		 * @return whether the selection editor accepted its value.
		 */
		boolean finishEditing() {
			return !table.isEditing() || table.getCellEditor().stopCellEditing();
		}

		/**
		 * Applies only this objective's links, preserving all other links.
		 *
		 * @param objectiveId objective being applied.
		 */
		void apply(String objectiveId) {
			for (int index = 0; index < assignments.size(); index++) {
				ResourceAssignment assignment = assignments.get(index);
				List<String> links = new ArrayList<>(assignment.getObjectiveIds());
				if (selected.get(index)) {
					if (!links.contains(objectiveId)) {
						links.add(objectiveId);
					}
				} else {
					links.remove(objectiveId);
				}
				assignment.setObjectiveIds(links);
			}
		}
	}

	/**
	 * Creates the attachment choices.
	 *
	 * @return attachment controls.
	 */
	private JPanel includedFormsPanel() {
		JPanel panel = new JPanel(new BorderLayout(0, 4));
		panel.setOpaque(false);
		JPanel checks = new JPanel(new GridLayout(0, 2, 4, 4));
		checks.setOpaque(false);
		checks.add(includeIcs202);
		checks.add(includeIcs204);
		checks.add(includeIcs205);
		checks.add(includeIcs208);
		checks.add(includeIcs215a);
		checks.add(includeIcs206);
		checks.add(includeCanine206);
		checks.add(includeSarTaskAssignment);
		checks.add(includeMapPacket);
		panel.add(checks, BorderLayout.NORTH);
		JScrollPane scrollPane = new JScrollPane(additionalFormsArea);
		scrollPane.setPreferredSize(new java.awt.Dimension(100, 60));
		panel.add(scrollPane, BorderLayout.CENTER);
		return panel;
	}

	/**
	 * Loads selected attachments.
	 *
	 * @param items selected form names.
	 */
	private void setIncludedForms(List<String> items) {
		includeIcs202.setSelected(false);
		includeIcs204.setSelected(false);
		includeIcs205.setSelected(false);
		includeIcs208.setSelected(false);
		includeIcs215a.setSelected(false);
		includeIcs206.setSelected(false);
		includeCanine206.setSelected(false);
		includeSarTaskAssignment.setSelected(false);
		includeMapPacket.setSelected(false);
		List<String> additional = new ArrayList<>();
		for (String item : items) {
			if ("ICS 202".equalsIgnoreCase(item)) {
				includeIcs202.setSelected(true);
			} else if ("ICS 204".equalsIgnoreCase(item)) {
				includeIcs204.setSelected(true);
			} else if ("ICS 205".equalsIgnoreCase(item)) {
				includeIcs205.setSelected(true);
			} else if ("ICS 208".equalsIgnoreCase(item)) {
				includeIcs208.setSelected(true);
			} else if ("ICS 215A".equalsIgnoreCase(item)) {
				includeIcs215a.setSelected(true);
			} else if ("ICS 206".equalsIgnoreCase(item)) {
				includeIcs206.setSelected(true);
			} else if ("ICS 206CANINE".equalsIgnoreCase(item)) {
				includeCanine206.setSelected(true);
			} else if ("SAR Task Assignment".equalsIgnoreCase(item)) {
				includeSarTaskAssignment.setSelected(true);
			} else if ("Map packet".equalsIgnoreCase(item)) {
				includeMapPacket.setSelected(true);
			} else {
				additional.add(item);
			}
		}
		additionalFormsArea.setText(String.join("\n", additional));
	}

	/**
	 * Collects selected attachments.
	 *
	 * @return selected form names.
	 */
	private List<String> getIncludedForms() {
		List<String> items = new ArrayList<>();
		if (includeIcs202.isSelected()) {
			items.add("ICS 202");
		}
		if (includeIcs204.isSelected()) {
			items.add("ICS 204");
		}
		if (includeIcs205.isSelected()) {
			items.add("ICS 205");
		}
		if (includeIcs208.isSelected()) {
			items.add("ICS 208");
		}
		if (includeIcs215a.isSelected()) {
			items.add("ICS 215A");
		}
		if (includeIcs206.isSelected()) {
			items.add("ICS 206");
		}
		if (includeCanine206.isSelected()) {
			items.add("ICS 206CANINE");
		}
		if (includeSarTaskAssignment.isSelected()) {
			items.add("SAR Task Assignment");
		}
		if (includeMapPacket.isSelected()) {
			items.add("Map packet");
		}
		items.addAll(lines(additionalFormsArea.getText()));
		return items;
	}

	/**
	 * Keeps legacy active flags aligned with the ICS 202 attachment selections.
	 *
	 * @param data incident whose medical plans are being configured.
	 * @param includedForms selected ICS 202 attachments.
	 */
	static void synchronizeMedicalPlanInclusion(AppData data, List<String> includedForms) {
		data.getMedicalPlan().setActive(includedForms.stream().anyMatch(item -> "ICS 206".equalsIgnoreCase(item)));
		data.getCanineMedicalPlan().setActive(
				includedForms.stream().anyMatch(item -> "ICS 206CANINE".equalsIgnoreCase(item)));
	}

	/**
	 * Splits textarea input into non-empty lines.
	 *
	 * @param value
	 *            textarea content.
	 * @return non-empty lines.
	 */
	private List<String> lines(String value) {
		List<String> items = new ArrayList<>();
		for (String line : PdfText.normalize(value).split("\n")) {
			if (!line.isBlank()) {
				items.add(line.trim());
			}
		}
		return items;
	}

	/**
	 * Converts null text to empty text.
	 *
	 * @param value
	 *            input value.
	 * @return safe text.
	 */
	private String nullSafe(String value) {
		return value == null ? "" : value;
	}
}
