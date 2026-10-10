/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.sarmanagement.icsforms.model.ClueLogEntry;
import org.sarmanagement.icsforms.model.PocAssignment;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.text.ParseException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** Edits clue follow-up responsibility while preserving POC assignment history. */
class ClueFollowUpPanel extends JPanel {
	private final JTextField poc = UiSupport.textField();
	private final JSpinner assignedAt = UiSupport.dateTimeSpinner();
	private final JCheckBox briefed = new JCheckBox("Current POC briefed");
	private final ActionDateField target;
	private final ActionDateField completed;
	private final JTextArea status = UiSupport.textArea(2);

	/**
	 * Creates follow-up controls from a clue without modifying it.
	 * @param clue clue whose follow-up is edited
	 */
	ClueFollowUpPanel(ClueLogEntry clue) {
		super(new BorderLayout());
		JPanel form = UiSupport.formPanel();
		poc.setText(clue.getFollowUpAssignedTo());
		poc.setToolTipText("POC (point of contact): the person or resource responsible for this follow-up.");
		briefed.setToolTipText("Select after the current POC has been informed of and briefed on the follow-up action.");
		briefed.setSelected(clue.isFollowUpBriefed());
		poc.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
			/** {@inheritDoc} */
			@Override
			public void insertUpdate(javax.swing.event.DocumentEvent event) {
				briefed.setSelected(false);
			}

			/** {@inheritDoc} */
			@Override
			public void removeUpdate(javax.swing.event.DocumentEvent event) {
				briefed.setSelected(false);
			}

			/** {@inheritDoc} */
			@Override
			public void changedUpdate(javax.swing.event.DocumentEvent event) {
				briefed.setSelected(false);
			}
		});
		target = new ActionDateField(clue.getFollowUpTargetDate());
		completed = new ActionDateField(clue.getFollowUpCompletedDate());
		status.setText(clue.getFollowUpStatus());
		JTextArea history = UiSupport.textArea(3);
		history.setEditable(false);
		StringBuilder text = new StringBuilder();
		for (PocAssignment assignment : clue.getFollowUpAssignments()) {
			if (assignment != null) {
				text.append(ActionDateField.format(assignment.getAssignedAt())).append(" — ")
						.append(assignment.getAssignee().isBlank() ? "(unassigned)" : assignment.getAssignee())
						.append('\n');
			}
		}
		history.setText(text.toString());
		UiSupport.addRow(form, 0, "", new JLabel(
				"<html>POC = point of contact responsible for follow-up.<br>"
						+ "Changing the POC appends an assignment; earlier assignments remain in history.</html>"));
		UiSupport.addRow(form, 1, "Current POC", poc);
		UiSupport.addRow(form, 2, "New assignment / start", assignedAt);
		UiSupport.addRow(form, 3, "", briefed);
		UiSupport.addRow(form, 4, "Target completion", target);
		UiSupport.addRow(form, 5, "Actual completion", completed);
		UiSupport.addRow(form, 6, "Status / progress", new JScrollPane(status));
		UiSupport.addRow(form, 7, "POC history (oldest first)", new JScrollPane(history));
		add(form, BorderLayout.CENTER);
	}

	/**
	 * Saves validated follow-up controls, appending only changed POC assignments.
	 * @param clue clue to update
	 * @throws ParseException when any date editor contains invalid text
	 */
	void saveTo(ClueLogEntry clue) throws ParseException {
		LocalDateTime targetValue = target.getValue();
		LocalDateTime completedValue = completed.getValue();
		assignedAt.commitEdit();
		LocalDateTime assignmentValue = AppController.toLocalDateTime((Date) assignedAt.getValue());
		String name = poc.getText().trim();
		if (!name.equals(clue.getFollowUpAssignedTo())) {
			List<PocAssignment> history = new ArrayList<>(clue.getFollowUpAssignments());
			history.add(new PocAssignment(name, assignmentValue));
			clue.setFollowUpAssignments(history);
		}
		clue.setFollowUpBriefed(briefed.isSelected());
		clue.setFollowUpTargetDate(targetValue);
		clue.setFollowUpCompletedDate(completedValue);
		clue.setFollowUpStatus(status.getText().trim());
	}
}
