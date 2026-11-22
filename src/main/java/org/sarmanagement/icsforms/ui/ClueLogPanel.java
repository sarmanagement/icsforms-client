/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.sarmanagement.icsforms.model.ActivityLogScope;
import org.sarmanagement.icsforms.model.ClueLogEntry;
import org.sarmanagement.icsforms.model.Ics214Form;
import org.sarmanagement.icsforms.model.PocAssignment;
import org.sarmanagement.icsforms.model.Provenance;
import org.sarmanagement.icsforms.model.SarTaskAssignment;
import org.sarmanagement.icsforms.model.UtcTimestamp;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Shared clue log editor showing clues captured across SAR task debriefings.
 */
public class ClueLogPanel extends JPanel {
	/**
	 * Sentinel value used in the detecting-task picklist for clues not linked to a
	 * known task.
	 */
	private static final String DETECTING_TASK_OTHER = "Other";
	private final AppController controller;
	private final ClueLogTableModel tableModel = new ClueLogTableModel();
	private final JTable table = new JTable(tableModel) {
		/**
		 * Highlights outstanding follow-up targets in the shared clue log.
		 * @param renderer cell renderer
		 * @param row view row
		 * @param column view column
		 * @return rendered component
		 */
		@Override
		public java.awt.Component prepareRenderer(javax.swing.table.TableCellRenderer renderer, int row, int column) {
			java.awt.Component component = super.prepareRenderer(renderer, row, column);
			if (!isRowSelected(row)) {
				component.setBackground(followUpTargetColor(tableModel.getRows().get(convertRowIndexToModel(row)),
						getBackground()));
			}
			ClueLogEntry clue = tableModel.getRows().get(convertRowIndexToModel(row));
			if (clue.isStruckOut() && component instanceof JLabel label) {
				Object value = getValueAt(row, column);
				label.setText("<html><strike>" + escape(value == null ? "" : value.toString()) + "</strike></html>");
			}
			return component;
		}
	};

	/**
	 * Returns overdue red or due-within-24-hours yellow for outstanding follow-up.
	 * @param clue clue to inspect
	 * @param normal normal table background
	 * @return highlight color, or normal when not due
	 */
	static java.awt.Color followUpTargetColor(ClueLogEntry clue, java.awt.Color normal) {
		String status = clue.getFollowUpStatus().trim();
		if (clue.getFollowUpCompletedDate() != null || "completed".equalsIgnoreCase(status)
				|| "followed up".equalsIgnoreCase(status) || clue.getFollowUpTargetDate() == null) {
			return normal;
		}
		LocalDateTime now = UtcTimestamp.now();
		if (clue.getFollowUpTargetDate().isBefore(now)) {
			return new java.awt.Color(255, 225, 210);
		}
		return clue.getFollowUpTargetDate().isAfter(now.plusHours(24))
				? normal : new java.awt.Color(255, 245, 180);
	}

	/**
	 * Creates the shared clue editor.
	 * @param controller incident controller
	 */
	public ClueLogPanel(AppController controller) {
		super(new BorderLayout());
		this.controller = controller;

		table.setFillsViewportHeight(true);
		table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		tableModel.addTableModelListener(event -> {
			if (event.getColumn() != javax.swing.event.TableModelEvent.ALL_COLUMNS) {
				controller.markDirty();
			}
		});
		setBorder(BorderFactory.createTitledBorder("Clue Log"));
		add(new JScrollPane(table), BorderLayout.CENTER);

		// Right-click context menu — opened anywhere on a row.
		JPopupMenu rowMenu = new JPopupMenu();
		JMenuItem detailsItem = new JMenuItem("View Details…");
		detailsItem.addActionListener(e -> {
			int row = table.getSelectedRow();
			if (row >= 0)
				showClueDetails(row);
		});
		rowMenu.add(detailsItem);
		table.addMouseListener(new MouseAdapter() {
			/**
			 * Opens the context menu for a clue row.
			 * @param e mouse event
			 */
			private void maybeShow(MouseEvent e) {
				if (e.isPopupTrigger()) {
					int row = table.rowAtPoint(e.getPoint());
					if (row >= 0) {
						table.setRowSelectionInterval(row, row);
						rowMenu.show(table, e.getX(), e.getY());
					}
				}
			}
			@Override
			/** {@inheritDoc} */
			public void mousePressed(MouseEvent e) {
				maybeShow(e);
			}
			@Override
			/** {@inheritDoc} */
			public void mouseReleased(MouseEvent e) {
				maybeShow(e);
			}
			@Override
			/** {@inheritDoc} */
			public void mouseClicked(MouseEvent e) {
				if (e.getClickCount() == 2 && e.getButton() == MouseEvent.BUTTON1) {
					int row = table.rowAtPoint(e.getPoint());
					if (row >= 0)
						showClueDetails(row);
				}
			}
		});

		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
		JButton add = new JButton("Add");
		JButton findDuplicates = new JButton("Find Duplicates…");
		JButton syncToLogs = new JButton("Sync to Activity Logs");
		syncToLogs.setToolTipText("Propagate clues with an assigned task to their linked ICS 214 activity logs");
		add.addActionListener(event -> addClueDialog());
		findDuplicates.addActionListener(event -> findAndResolveDuplicates());
		syncToLogs.addActionListener(event -> syncClueLogToActivityLogs());
		buttons.add(add);
		buttons.add(findDuplicates);
		buttons.add(syncToLogs);
		add(buttons, BorderLayout.SOUTH);
	}

	/** Opens a dialog to enter clue details and adds the new entry to the log. */
	private void addClueDialog() {
		List<TaskChoice> taskItems = taskChoices(controller.getData().getSarTaskAssignments());

		JPanel form = UiSupport.formPanel();
		JComboBox<TaskChoice> taskCombo = new JComboBox<>(taskItems.toArray(new TaskChoice[0]));
		taskCombo.setSelectedItem(null); // blank by default
		taskCombo.setEditable(false);
		JTextField detectedByField = UiSupport.textField();
		// "Detected by" is only relevant when "Other" is chosen; keep it enabled only
		// then.
		detectedByField.setEnabled(false);
		taskCombo.addActionListener(e -> {
			TaskChoice selected = (TaskChoice) taskCombo.getSelectedItem();
			detectedByField.setEnabled(selected != null && selected.other());
		});

		JSpinner dateTimeSpinner = UiSupport.dateTimeSpinner();
		JTextField locationField = UiSupport.textField();
		JTextArea descriptionArea = UiSupport.textArea(3);
		JTextArea immediateActionArea = UiSupport.textArea(2);
		JCheckBox possibleDuplicateCheck = new JCheckBox("Possible duplicate");

		int row = 0;
		UiSupport.addRow(form, row++, "Detecting task", taskCombo);
		UiSupport.addRow(form, row++, "Detected by", detectedByField);
		UiSupport.addRow(form, row++, "Date/time collected", dateTimeSpinner);
		UiSupport.addRow(form, row++, "Location / position", locationField);
		UiSupport.addRow(form, row++, "Description", new JScrollPane(descriptionArea));
		UiSupport.addRow(form, row++, "Immediate action taken", new JScrollPane(immediateActionArea));
		UiSupport.addRow(form, row, "", possibleDuplicateCheck);

		JScrollPane scrollPane = new JScrollPane(form);
		scrollPane.setBorder(BorderFactory.createEmptyBorder());
		if (!UiSupport.showResizableConfirmDialog(this, "Add Clue", scrollPane, new Dimension(640, 400))) {
			return;
		}

		ClueLogEntry clue = new ClueLogEntry();
		clue.setProvenance(controller.createProvenance());
		Object spinnerValue = dateTimeSpinner.getValue();
		if (spinnerValue instanceof Date) {
			clue.setDateTimeCollected(AppController.toLocalDateTime((Date) spinnerValue));
		} else {
			clue.setDateTimeCollected(UtcTimestamp.now().withSecond(0).withNano(0));
		}

		TaskChoice selectedTask = (TaskChoice) taskCombo.getSelectedItem();
		String taskLabel = selectedTask == null ? "" : selectedTask.label().trim();
		if (selectedTask != null && selectedTask.other()) {
			// No task link; store free-text detected-by only.
			clue.setDetectedBy(detectedByField.getText().trim());
		} else {
			// Explicit clear: detectedBy is not applicable when a specific task is chosen.
			clue.setDetectedBy("");
			if (selectedTask != null && !selectedTask.assignmentId().isBlank()) {
				clue.setAssignmentId(selectedTask.assignmentId());
			}
			clue.setDetectingTask(taskLabel);
		}
		clue.setLocation(locationField.getText().trim());
		clue.setDescription(descriptionArea.getText().trim());
		clue.setImmediateAction(immediateActionArea.getText().trim());
		clue.setPossibleDuplicate(possibleDuplicateCheck.isSelected());

		tableModel.addRow(clue);
		controller.recordClueInIcpActivityLog(clue);
		controller.propagateClueToActivityLog(clue);
		controller.markDirty();
	}

	/**
	 * Builds task choices carrying stable assignment identities separately from labels.
	 *
	 * @param tasks current SAR task assignments
	 * @return ordered task, resource, and unlinked choices
	 */
	static List<TaskChoice> taskChoices(List<SarTaskAssignment> tasks) {
		List<TaskChoice> choices = new ArrayList<>();
		Set<String> resources = new HashSet<>();
		Map<String, Integer> labelCounts = new HashMap<>();
		if (tasks != null) {
			for (SarTaskAssignment task : tasks) {
				if (task != null) {
					String label = UiSupport.taskLabel(task);
					if (!label.isBlank()) {
						labelCounts.merge(label, 1, Integer::sum);
					}
				}
			}
			for (SarTaskAssignment task : tasks) {
				if (task == null) {
					continue;
				}
				String label = UiSupport.taskLabel(task);
				if (!label.isBlank()) {
					if (labelCounts.getOrDefault(label, 0) > 1) {
						String suffix = task.getAssignmentId().isBlank()
								? task.getTaskId() : task.getAssignmentId();
						label += " [" + suffix + "]";
					}
					choices.add(new TaskChoice(task.getAssignmentId(), label, false));
				}
				if (task.getResourcesAssigned() != null) {
					for (var resource : task.getResourcesAssigned()) {
						String name = resource.getName() == null ? "" : resource.getName().trim();
						if (!name.isBlank() && resources.add(name)) {
							choices.add(new TaskChoice("", name, false));
						}
					}
				}
			}
		}
		choices.add(new TaskChoice("", DETECTING_TASK_OTHER, true));
		return choices;
	}

	/**
	 * Separates a displayed task or resource label from its optional link identity.
	 *
	 * @param assignmentId stable task assignment ID, or blank for an unlinked choice
	 * @param label visible choice label
	 * @param other whether this is the unlinked "Other" choice
	 */
	record TaskChoice(String assignmentId, String label, boolean other) {
		/**
		 * Returns the operator-facing choice label.
		 *
		 * @return visible choice label
		 */
		@Override
		public String toString() {
			return label;
		}
	}

	/**
	 * Opens the established clue-entry workflow from another incident view.
	 */
	public void openAddClueDialog() {
		addClueDialog();
	}

	/**
	 * Shows an editable details dialog for the clue at the given table row.
	 * @param row clue row index
	 */
	private void showClueDetails(int row) {
		ClueLogEntry clue = tableModel.getRows().get(row);
		if (clue.isStruckOut()) {
			JTextArea details = new JTextArea(describeClue(clue));
			details.setEditable(false);
			JOptionPane.showMessageDialog(this, new JScrollPane(details), "Struck-out clue history",
					JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		String taskLabel = (String) tableModel.getValueAt(row, 0);

		JPanel form = UiSupport.formPanel();

		JTextField taskField = UiSupport.textField();
		taskField.setText(taskLabel);
		taskField.setEditable(false);

		JTextField detectedByField = UiSupport.textField();
		detectedByField.setText(clue.getDetectedBy());
		JSpinner dateField = UiSupport.dateTimeSpinner();
		dateField.setValue(
				clue.getDateTimeCollected() == null ? Date.from(UtcTimestamp.nowInstant())
						: AppController.toDate(clue.getDateTimeCollected()));

		JTextField locationField = UiSupport.textField();
		locationField.setText(clue.getLocation());

		JTextArea descArea = UiSupport.textArea(3);
		descArea.setText(clue.getDescription());

		JTextArea actionArea = UiSupport.textArea(2);
		actionArea.setText(clue.getImmediateAction());

		JCheckBox dupCheck = new JCheckBox("Possible duplicate", clue.isPossibleDuplicate());
		JTextArea followUpArea = UiSupport.textArea(2);
		followUpArea.setText(clue.getFollowUp());
		ClueFollowUpPanel followUpDetails = new ClueFollowUpPanel(clue);

		int r = 0;
		UiSupport.addRow(form, r++, "Task", taskField);
		UiSupport.addRow(form, r++, "Detected by", detectedByField);
		UiSupport.addRow(form, r++, "Date/time collected", dateField);
		UiSupport.addRow(form, r++, "Location / position", locationField);
		UiSupport.addRow(form, r++, "Description", new JScrollPane(descArea));
		UiSupport.addRow(form, r++, "Immediate action taken", new JScrollPane(actionArea));
		UiSupport.addRow(form, r++, "", dupCheck);
		UiSupport.addRow(form, r++, "Follow up", new JScrollPane(followUpArea));
		UiSupport.addRow(form, r, "Follow-up tracking", followUpDetails);

		JScrollPane scrollPane = new JScrollPane(form);
		scrollPane.setBorder(BorderFactory.createEmptyBorder());
		while (UiSupport.showResizableConfirmDialog(this, "Clue Details", scrollPane, new Dimension(740, 700))) {
			try {
				dateField.commitEdit();
				followUpDetails.saveTo(clue);
			} catch (java.text.ParseException exception) {
				JOptionPane.showMessageDialog(this, "Enter valid dates or clear their Set checkboxes.",
						"Invalid Date", JOptionPane.ERROR_MESSAGE);
				continue;
			}
			clue.setDateTimeCollected(AppController.toLocalDateTime((Date) dateField.getValue()));
			clue.setDetectedBy(detectedByField.getText().trim());
			clue.setLocation(locationField.getText().trim());
			clue.setDescription(descArea.getText().trim());
			clue.setImmediateAction(actionArea.getText().trim());
			clue.setPossibleDuplicate(dupCheck.isSelected());
			clue.setFollowUp(followUpArea.getText().trim());
			tableModel.fireTableRowsUpdated(row, row);
			controller.markDirty();
			return;
		}
	}

	/**
	 * Builds a map from assignment ID to a display label for the "Task" column. The
	 * label format is {@code "<teamNumber> – <resourceName>"} derived from the
	 * linked ICS 214 form names (i.e. the resource identifier), not the assignment
	 * description text. Falls back to {@link UiSupport#taskLabel} when no linked
	 * 214 forms exist.
	 * @return assignment labels
	 */
	private Map<String, String> buildTaskLabelMap() {
		Map<String, String> map = new HashMap<>();
		if (controller.getData().getSarTaskAssignments() != null && controller.getData().getActivityLogs() != null) {
			Map<String, SarTaskAssignment> taskById = new HashMap<>();
			for (SarTaskAssignment t : controller.getData().getSarTaskAssignments()) {
				taskById.put(t.getAssignmentId(), t);
			}
			for (Ics214Form form : controller.getData().getActivityLogs()) {
				if (form.getLogScope() == ActivityLogScope.TASK_ASSIGNMENT
						&& form.getLinkedSarTaskAssignmentId() != null && !form.getLinkedSarTaskAssignmentId().isBlank()
						&& form.getName() != null && !form.getName().isBlank()) {
					SarTaskAssignment task = taskById.get(form.getLinkedSarTaskAssignmentId());
					if (task != null) {
						// One entry per assignment (first 214 form wins; team# + resource name).
						map.putIfAbsent(form.getLinkedSarTaskAssignmentId(),
								UiSupport.detectingTaskLabel(task, form.getName()));
					}
				}
			}
		}
		// Fall back to task label (team# + assignment text) for assignments without a
		// linked 214.
		if (controller.getData().getSarTaskAssignments() != null) {
			for (SarTaskAssignment t : controller.getData().getSarTaskAssignments()) {
				if (t.getAssignmentId() != null && !t.getAssignmentId().isBlank()) {
					map.putIfAbsent(t.getAssignmentId(), UiSupport.taskLabel(t));
				}
			}
		}
		return map;
	}

	/** Refreshes shared clue rows and linked task labels. */
	public void refreshFromModel() {
		tableModel.setRows(controller.getData().getClueLogEntries(), buildTaskLabelMap());
	}

	/**
	 * Commits the active inline cell while preserving newer canonical debrief clues.
	 * When rows were replaced externally, only the actual edited cell is merged by
	 * stable clue identity; stale rows and deleted clues are never reinstated.
	 *
	 * @throws IllegalStateException when the active cell cannot be committed.
	 */
	public void pushToModel() {
		String editedId = null;
		int editedColumn = -1;
		Object editedValue = null;
		if (table.isEditing() && table.getCellEditor() != null) {
			int editedRow = table.convertRowIndexToModel(table.getEditingRow());
			editedColumn = table.convertColumnIndexToModel(table.getEditingColumn());
			editedId = tableModel.getRows().get(editedRow).getEntryId();
			if (!table.getCellEditor().stopCellEditing()) {
				throw new IllegalStateException("Finish editing the selected clue before saving.");
			}
			editedValue = tableModel.getValueAt(editedRow, editedColumn);
		}
		List<ClueLogEntry> canonical = controller.getData().getClueLogEntries();
		if (tableModel.getRows() != canonical) {
			tableModel.setRows(canonical, buildTaskLabelMap());
			if (editedId != null) {
				for (int row = 0; row < canonical.size(); row++) {
					if (editedId.equals(canonical.get(row).getEntryId())) {
						tableModel.setValueAt(editedValue, row, editedColumn);
						break;
					}
				}
			}
		}
	}

	/**
	 * Propagates all clues with an assigned task ID to their linked ICS 214
	 * activity logs.
	 */
	private void syncClueLogToActivityLogs() {
		pushToModel();
		int count = 0;
		for (ClueLogEntry clue : tableModel.getRows()) {
			if (!clue.isStruckOut() && clue.getAssignmentId() != null && !clue.getAssignmentId().isBlank()) {
				if (controller.propagateClueToActivityLog(clue)) {
					count++;
				}
			}
		}
		controller.markDirty();
		JOptionPane.showMessageDialog(this, count + " clue(s) propagated to linked activity logs.", "Sync Complete",
				JOptionPane.INFORMATION_MESSAGE);
	}

	/**
	 * Presents matching pairs for explicit, individual review.
	 */
	private void findAndResolveDuplicates() {
		pushToModel();
		List<ClueDuplicateReview.Candidate> candidates = ClueDuplicateReview.candidates(tableModel.getRows());
		if (candidates.isEmpty()) {
			JOptionPane.showMessageDialog(this,
					"No candidates found. Nonblank location and description must match, ignoring case and whitespace.",
					"Find Duplicates",
					JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		JComboBox<ClueDuplicateReview.Candidate> pairs = new JComboBox<>(
				candidates.toArray(new ClueDuplicateReview.Candidate[0]));
		JTextArea details = new JTextArea(18, 70);
		details.setEditable(false);
		details.setCaretPosition(0);
		String[] displayed = new String[2];
		Runnable updateDetails = () -> {
			ClueDuplicateReview.Candidate pair = (ClueDuplicateReview.Candidate) pairs.getSelectedItem();
			displayed[0] = describeClue(pair.first());
			displayed[1] = describeClue(pair.second());
			details.setText("FIRST\n" + displayed[0] + "\n\nSECOND\n" + displayed[1]);
			details.setCaretPosition(0);
		};
		pairs.addActionListener(event -> updateDetails.run());
		updateDetails.run();
		JPanel review = new JPanel(new BorderLayout(5, 5));
		review.add(new JLabel("Candidates: matching nonblank location and description, ignoring case and whitespace."),
				BorderLayout.NORTH);
		review.add(new JScrollPane(details), BorderLayout.CENTER);
		review.add(pairs, BorderLayout.SOUTH);
		Object[] options = {"Strike out first", "Strike out second", "Cancel"};
		int choice = JOptionPane.showOptionDialog(this, review, "Review suspected duplicates",
				JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[2]);
		if (choice != 0 && choice != 1) {
			return;
		}
		ClueDuplicateReview.Candidate pair = (ClueDuplicateReview.Candidate) pairs.getSelectedItem();
		ClueLogEntry struck = choice == 0 ? pair.first() : pair.second();
		ClueLogEntry retained = choice == 0 ? pair.second() : pair.first();
		Provenance reviewer = controller.createProvenance();
		if (JOptionPane.showConfirmDialog(this, "Confirm that clue " + struck.getEntryId()
				+ " duplicates clue " + retained.getEntryId()
				+ " and strike it out? Both records will remain in the log.\nReviewer: "
				+ (reviewer == null ? "unavailable" : reviewer.operatorName()),
				"Confirm duplicate",
				JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
			return;
		}
		pushToModel();
		if (!tableModel.getRows().contains(pair.first()) || !tableModel.getRows().contains(pair.second())
				|| !displayed[0].equals(describeClue(pair.first())) || !displayed[1].equals(describeClue(pair.second()))) {
			JOptionPane.showMessageDialog(this, "The clues changed during review. Refresh and review again.");
			return;
		}
		if (!confirmDuplicate(struck.getEntryId(), retained.getEntryId())) {
			JOptionPane.showMessageDialog(this, "The clues changed during review. Refresh and review again.");
		}
	}

	/**
	 * Confirms a still-current candidate, retaining both records and their attribution.
	 * @param struckId identity of the clue to strike out
	 * @param retainedId identity of the reference clue
	 * @return true if the decision was recorded
	 */
	boolean confirmDuplicate(String struckId, String retainedId) {
		pushToModel();
		for (ClueDuplicateReview.Candidate pair : ClueDuplicateReview.candidates(tableModel.getRows())) {
			ClueLogEntry a = pair.first();
			ClueLogEntry b = pair.second();
			if (a.getEntryId().equals(struckId) && b.getEntryId().equals(retainedId)
					|| b.getEntryId().equals(struckId) && a.getEntryId().equals(retainedId)) {
				ClueLogEntry struck = a.getEntryId().equals(struckId) ? a : b;
				struck.confirmDuplicate(retainedId, UtcTimestamp.nowInstant(), controller.createProvenance());
				tableModel.fireTableDataChanged();
				controller.markDirty();
				return true;
			}
		}
		return false;
	}

	/**
	 * Formats clue evidence and provenance for review and history.
	 * @param clue clue to display
	 * @return text describing the recorded entry
	 */
	private static String describeClue(ClueLogEntry clue) {
		List<String> assignments = new ArrayList<>();
		for (PocAssignment assignment : clue.getFollowUpAssignments()) {
			if (assignment != null) {
				assignments.add(assignment.getId() + ": " + assignment.getAssignee() + " at " + assignment.getAssignedAt());
			}
		}
		return "ID: " + clue.getEntryId() + "\nTask: " + clue.getDetectingTask()
				+ "\nAssignment ID: " + clue.getAssignmentId() + "\nDetected by: " + clue.getDetectedBy()
				+ "\nCollected: " + clue.getDateTimeCollected() + "\nLocation: " + clue.getLocation()
				+ "\nDescription: " + clue.getDescription() + "\nImmediate action: " + clue.getImmediateAction()
				+ "\nPossible duplicate: " + clue.isPossibleDuplicate()
				+ "\nFollow up: " + clue.getFollowUp() + "\nFollow-up assignments: " + String.join("; ", assignments)
				+ "\nFollow-up briefed: " + clue.isFollowUpBriefed()
				+ "\nFollow-up target: " + clue.getFollowUpTargetDate()
				+ "\nFollow-up completed: " + clue.getFollowUpCompletedDate()
				+ "\nFollow-up status: " + clue.getFollowUpStatus() + "\nEntry provenance: " + clue.getProvenance()
				+ "\nDuplicate confirmation: " + clue.getDuplicateConfirmation();
	}

	/**
	 * Escapes text used in duplicate-resolution HTML.
	 * @param s text, or null
	 * @return escaped text
	 */
	private static String escape(String s) {
		return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	/** Shared clue columns retaining the original in-table editing workflow. */
	private static class ClueLogTableModel extends AbstractTableModel {
		private final String[] columns = {"Task", "Detected By", "Date/Time Collected", "Location", "Description",
				"Immediate Action", "Poss. Dup", "Follow Up", "Review Status"};
		private List<ClueLogEntry> rows = new ArrayList<>();
		/**
		 * Map from assignmentId → computed task label (e.g. "T3 – Dog X"), refreshed on
		 * setRows.
		 */
		private Map<String, String> taskLabelById = new HashMap<>();

		/**
		 * Sets rows and their task display labels.
		 * @param rows clue rows
		 * @param taskLabelById task labels
		 */
		void setRows(List<ClueLogEntry> rows, Map<String, String> taskLabelById) {
			this.rows = rows == null ? new ArrayList<>() : rows;
			this.taskLabelById = taskLabelById == null ? new HashMap<>() : taskLabelById;
			fireTableDataChanged();
		}

		/**
		 * Sets rows without task context.
		 * @param rows clue rows
		 * @deprecated kept for callers that don't have task context
		 */
		void setRows(List<ClueLogEntry> rows) {
			setRows(rows, null);
		}

		/**
		 * Returns all clue rows.
		 * @return clue rows
		 */
		List<ClueLogEntry> getRows() {
			return rows;
		}

		/**
		 * Adds a clue.
		 * @param entry clue to add
		 */
		void addRow(ClueLogEntry entry) {
			rows.add(entry);
			fireTableRowsInserted(rows.size() - 1, rows.size() - 1);
		}

		/** Adds an empty clue. */
		void addRow() {
			rows.add(new ClueLogEntry());
			fireTableRowsInserted(rows.size() - 1, rows.size() - 1);
		}

		@Override
		/** {@inheritDoc} */
		public int getRowCount() {
			return rows.size();
		}
		@Override
		/** {@inheritDoc} */
		public int getColumnCount() {
			return columns.length;
		}
		@Override
		/** {@inheritDoc} */
		public String getColumnName(int column) {
			return columns[column];
		}

		@Override
		/** {@inheritDoc} */
		public boolean isCellEditable(int rowIndex, int columnIndex) {
			// Col 0 (Task) is read-only — derived from assignmentId.
			return columnIndex != 0 && columnIndex != 8 && !rows.get(rowIndex).isStruckOut();
		}

		@Override
		/** {@inheritDoc} */
		public Class<?> getColumnClass(int columnIndex) {
			return columnIndex == 6 ? Boolean.class : String.class;
		}

		@Override
		/** {@inheritDoc} */
		public Object getValueAt(int rowIndex, int columnIndex) {
			ClueLogEntry row = rows.get(rowIndex);
			return switch (columnIndex) {
				case 0 -> {
					// Derive the task label from assignmentId; fall back to stored detectingTask.
					String id = row.getAssignmentId();
					if (id != null && !id.isBlank() && taskLabelById.containsKey(id)) {
						yield taskLabelById.get(id);
					}
					yield row.getDetectingTask();
				}
				case 1 -> row.getDetectedBy();
				case 2 -> SarTaskPanel.formatDateTimeValue(row.getDateTimeCollected());
				case 3 -> row.getLocation();
				case 4 -> row.getDescription();
				case 5 -> row.getImmediateAction();
				case 6 -> row.isPossibleDuplicate();
				case 7 -> row.getFollowUp();
				default -> row.isStruckOut()
						? (row.getDuplicateConfirmation() == null ? "Struck out" : "Struck out: duplicate") : "";
			};
		}

		@Override
		/** {@inheritDoc} */
		public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
			ClueLogEntry row = rows.get(rowIndex);
			if (row.isStruckOut()) {
				return;
			}
			switch (columnIndex) {
				case 0 -> {
				} // read-only, derived from assignmentId
				case 1 -> row.setDetectedBy(aValue == null ? "" : aValue.toString());
				case 2 ->
					row.setDateTimeCollected(SarTaskPanel.parseDateTimeValue(aValue == null ? "" : aValue.toString()));
				case 3 -> row.setLocation(aValue == null ? "" : aValue.toString());
				case 4 -> row.setDescription(aValue == null ? "" : aValue.toString());
				case 5 -> row.setImmediateAction(aValue == null ? "" : aValue.toString());
				case 6 -> row.setPossibleDuplicate(Boolean.TRUE.equals(aValue));
				default -> row.setFollowUp(aValue == null ? "" : aValue.toString());
			}
			fireTableCellUpdated(rowIndex, columnIndex);
		}
	}
}
