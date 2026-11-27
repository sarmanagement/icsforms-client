/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;

import com.fasterxml.jackson.databind.JsonNode;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.RecordDiff;
import org.sarmanagement.icsforms.model.RecordIndex;
import org.sarmanagement.icsforms.model.RootSettings;
import org.sarmanagement.icsforms.persistence.FullDocumentSnapshot;
import org.sarmanagement.icsforms.persistence.IncidentService.FieldConflict;
import org.sarmanagement.icsforms.persistence.IncidentService.RecordBatch;

/**
 * Displays durable outbox snapshots and collects explicit recovery decisions.
 *
 * <p>The host supplies snapshots and handles callbacks without blocking the event dispatch thread.
 * This component never submits, retries, exports, or removes an operation itself.
 */
public final class OutboxReviewPanel extends JPanel {
	/** States of an operation awaiting recovery or submission. */
	public enum Status {
		/** Waiting for submission. */
		PENDING,
		/** Rejected due to field conflicts. */
		CONFLICT,
		/** Rejected for another reason. */
		REJECTED,
		/** A rebased draft awaits explicit review before it can be queued. */
		REVIEW_REQUIRED
	}

	/** An explicit choice for a conflicting field. */
	public enum Choice {
		/** No decision has been made. */
		UNDECIDED("Choose…"),
		/** Retain the submitted value. */
		MINE("Keep my edit"),
		/** Use the host value. */
		THEIRS("Keep the stored copy");

		private final String label;

		/**
		 * Creates a choice with its display label.
		 *
		 * @param label text shown to the operator
		 */
		Choice(String label) {
			this.label = label;
		}

		/** @return the operator-facing label */
		@Override
		public String toString() {
			return label;
		}
	}

	/**
	 * One durable operation to display.
	 *
	 * @param operationId stable operation identity
	 * @param before document immediately preceding this operation
	 * @param draft complete edited document, including operations not yet materialized as a batch
	 * @param batch exact submitted batch, or null until predecessors are acknowledged
	 * @param status current durable state
	 * @param conflicts field conflicts, in host order
	 * @param rejection rejection explanation, if available
	 */
	public record Entry(String operationId, AppData before, AppData draft, RecordBatch batch, Status status,
			List<FieldConflict> conflicts, String rejection) {
		/** Validates the snapshot and copies the conflict list. */
		public Entry {
			Objects.requireNonNull(operationId, "operationId");
			before = FullDocumentSnapshot.detach(Objects.requireNonNull(before, "before"));
			draft = FullDocumentSnapshot.detach(Objects.requireNonNull(draft, "draft"));
			if (batch != null && !operationId.equals(batch.operationId())) {
				throw new IllegalArgumentException("Batch operation ID does not match");
			}
			Objects.requireNonNull(status, "status");
			conflicts = List.copyOf(conflicts);
		}

		/** @return a detached copy of the preceding document */
		@Override
		public AppData before() {
			return FullDocumentSnapshot.detach(before);
		}

		/** @return a detached copy of the complete edited document */
		@Override
		public AppData draft() {
			return FullDocumentSnapshot.detach(draft);
		}

		/** @return a concise list label without exposing draft contents */
		@Override
		public String toString() {
			String shortId = operationId.startsWith("offline-") ? operationId.substring("offline-".length())
					: operationId;
			return status + " — edit " + shortId.substring(0, Math.min(8, shortId.length()));
		}
	}

	/**
	 * A decision about a specific host-reported conflict.
	 *
	 * @param conflict original conflict and its compared values
	 * @param choice explicitly selected value
	 */
	public record Decision(FieldConflict conflict, Choice choice) {
		/** Requires a real decision and its original conflict. */
		public Decision {
			Objects.requireNonNull(conflict, "conflict");
			if (choice == null || choice == Choice.UNDECIDED) {
				throw new IllegalArgumentException("A field choice is required");
			}
		}
	}

	/**
	 * Integration callbacks. Implementations should schedule I/O off the event dispatch thread.
	 *
	 * <p>Resolution must check the latest host values against the reviewed conflict, preserve
	 * non-conflicting edits and dependencies, and construct a fresh operation identity. A
	 * conflicting operation ID must not be blindly retried. If the host or draft changed, or
	 * recovery fails, the original draft must remain intact for another review.
	 */
	public interface Actions {
		/**
		 * Requests export or other recovery of the complete draft and available submitted batch.
		 *
		 * @param entry selected durable operation
		 */
		void exportDraft(Entry entry);

		/**
		 * Requests resolution of all reported fields.
		 *
		 * @param entry selected durable operation
		 * @param decisions one explicit choice per conflict, in host order
		 */
		void resolve(Entry entry, List<Decision> decisions);

		/**
		 * Requests updated host values for another review without submitting the draft.
		 *
		 * @param entry selected conflicting operation
		 */
		void refreshConflict(Entry entry);

		/**
		 * Requests explicit approval of the rebased draft for later synchronization.
		 *
		 * @param entry reviewed operation
		 */
		void confirm(Entry entry);

		/**
		 * Requests replacing a rejected queue by one edit built from the current corrected local draft.
		 *
		 * @param entry rejected head operation
		 */
		default void replaceRejected(Entry entry) {
		}
	}

	private final Actions actions;
	private final DefaultListModel<Entry> entries = new DefaultListModel<>();
	private final JList<Entry> list = new JList<>(entries);
	private final DefaultTableModel fields = new DefaultTableModel(
			new Object[] {"Record", "Field", "Original", "My edit", "Stored on host", "Choice"}, 0) {
		/** {@inheritDoc} */
		@Override
		public boolean isCellEditable(int row, int column) {
			Entry entry = list.getSelectedValue();
			return entry != null && entry.status() == Status.CONFLICT && column == 5;
		}
	};
	private final JTable table = new JTable(fields);
	private final JTextArea details = new JTextArea(3, 30);
	private final JButton export = new JButton("Export / recover draft");
	private final JButton keepMine = new JButton("Keep all my edits");
	private final JButton keepShared = new JButton("Keep all stored copies");
	private final JButton resolve = new JButton("Apply my choices");
	private final JButton replace = new JButton("Replace rejected edit with corrected draft");
	private final JButton refresh = new JButton("Refresh conflict values");
	private final JCheckBox reviewed = new JCheckBox("I reviewed the rebased changes");
	private final JButton confirm = new JButton("Approve reviewed changes for sync");
	private final Map<String, SavedChoices> choices = new HashMap<>();
	private boolean updating;

	/**
	 * Creates a review panel without initiating any persistence or network work.
	 *
	 * @param actions integration callbacks for explicit operator requests
	 */
	public OutboxReviewPanel(Actions actions) {
		super(new BorderLayout(6, 6));
		this.actions = Objects.requireNonNull(actions, "actions");
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list.getAccessibleContext().setAccessibleName("Outbox operations");
		table.getAccessibleContext().setAccessibleName("Conflicting fields and choices");
		table.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
		table.setRowHeight(Math.max(table.getRowHeight(), 24));
		table.getColumnModel().getColumn(5).setCellEditor(new javax.swing.DefaultCellEditor(
				new JComboBox<>(new DefaultComboBoxModel<>(Choice.values()))));
		details.setEditable(false);
		details.setLineWrap(true);
		details.setWrapStyleWord(true);
		details.getAccessibleContext().setAccessibleName("Outbox operation details");
		JPanel center = new JPanel(new BorderLayout(4, 4));
		center.add(new JLabel("<html>An edit could not be applied because the copy stored on the host differs from what your edit "
				+ "started from. This is not always another person's change; it can be an older stored copy. For each "
				+ "row, keep your edit or the stored copy (click the Choice cell, or use the buttons below), then "
				+ "apply. Nothing is lost until you apply.</html>"),
				BorderLayout.NORTH);
		center.add(new JScrollPane(table), BorderLayout.CENTER);
		center.add(new JScrollPane(details), BorderLayout.SOUTH);
		JPanel buttons = new JPanel(new GridLayout(0, 1, 2, 2));
		buttons.add(export);
		buttons.add(keepMine);
		buttons.add(keepShared);
		buttons.add(resolve);
		buttons.add(replace);
		buttons.add(refresh);
		buttons.add(reviewed);
		buttons.add(confirm);
		add(new JScrollPane(list), BorderLayout.WEST);
		add(center, BorderLayout.CENTER);
		add(buttons, BorderLayout.SOUTH);
		list.addListSelectionListener(event -> {
			if (!event.getValueIsAdjusting() && !updating) {
				showSelection();
			}
		});
		fields.addTableModelListener(event -> {
			if (!updating) {
				saveChoices();
				updateButtons();
			}
		});
		table.getSelectionModel().addListSelectionListener(event -> {
			if (!event.getValueIsAdjusting()) {
				showRowDetails();
			}
		});
		reviewed.addActionListener(event -> updateButtons());
		export.addActionListener(event -> actions.exportDraft(list.getSelectedValue()));
		keepMine.addActionListener(event -> chooseAll(Choice.MINE));
		keepShared.addActionListener(event -> chooseAll(Choice.THEIRS));
		resolve.addActionListener(event -> requestResolution());
		replace.addActionListener(event -> actions.replaceRejected(list.getSelectedValue()));
		refresh.addActionListener(event -> actions.refreshConflict(list.getSelectedValue()));
		confirm.addActionListener(event -> {
			Entry selected = list.getSelectedValue();
			reviewed.setSelected(false);
			updateButtons();
			actions.confirm(selected);
		});
		updateButtons();
	}

	/**
	 * Replaces the displayed durable snapshot on the Swing event dispatch thread.
	 * Existing field choices survive refresh only while the corresponding conflict list is unchanged.
	 *
	 * @param snapshot latest operations, in display order
	 */
	public void setEntries(List<Entry> snapshot) {
		List<Entry> copy = List.copyOf(snapshot);
		var ids = new HashSet<String>();
		for (Entry entry : copy) {
			if (!ids.add(entry.operationId())) {
				throw new IllegalArgumentException("Duplicate outbox operation ID: " + entry.operationId());
			}
			if (table.isEditing() && !table.getCellEditor().stopCellEditing()) {
				return;
			}
		}
		Entry selected = list.getSelectedValue();
		String selectedId = selected == null ? null : selected.operationId();
		updating = true;
		entries.clear();
		int selectedIndex = -1;
		Map<String, SavedChoices> retained = new HashMap<>();
		for (Entry entry : copy) {
			String id = entry.operationId();
			SavedChoices saved = choices.get(id);
			if (saved != null && Objects.equals(saved.batch(), entry.batch())
					&& saved.conflicts().equals(entry.conflicts())) {
				retained.put(id, saved);
			}
			entries.addElement(entry);
			if (id.equals(selectedId)) {
				selectedIndex = entries.size() - 1;
			}
		}
		choices.clear();
		choices.putAll(retained);
		if (selectedIndex >= 0) {
			list.setSelectedIndex(selectedIndex);
		}
		updating = false;
		showSelection();
	}

	/** Updates the visible conflict details after selection or snapshot changes. */
	private void showSelection() {
		updating = true;
		reviewed.setSelected(false);
		fields.setRowCount(0);
		Entry entry = list.getSelectedValue();
		if (entry == null) {
			details.setText("Select an operation to review.");
		} else {
			details.setText(entry.rejection() == null ? entry.status().toString() : entry.rejection());
			SavedChoices saved = choices.get(entry.operationId());
			for (int i = 0; i < entry.conflicts().size(); i++) {
				FieldConflict conflict = entry.conflicts().get(i);
				Choice choice = saved == null ? Choice.UNDECIDED : saved.values().get(i);
				fields.addRow(new Object[] {conflict.key().type() + " / " + conflict.key().id(), conflict.field(),
						display(conflict.base()), display(conflict.mine()), display(conflict.theirs()), choice});
			}
			if (entry.status() == Status.REVIEW_REQUIRED) {
				showChanges(entry.before(), entry.draft());
			}
		}
		updating = false;
		updateButtons();
	}

	/**
	 * Displays net record and incident-wide settings changes against the rebased host document.
	 *
	 * @param before document preceding the operation
	 * @param draft rebased draft to be approved
	 */
	private void showChanges(AppData before, AppData draft) {
		RecordDiff diff = RecordDiff.between(before, draft);
		RecordIndex oldRecords = RecordIndex.of(before);
		RecordIndex newRecords = RecordIndex.of(draft);
		diff.created().forEach(key -> fields.addRow(new Object[] {key.type() + " / " + key.id(), "(created)",
				"(missing)", display(newRecords.record(key)), "", ""}));
		diff.changed().forEach((key, names) -> names.forEach(name -> fields.addRow(new Object[] {
				key.type() + " / " + key.id(), name, display(oldRecords.record(key).get(name)),
				display(newRecords.record(key).get(name)), "", ""})));
		diff.removed().forEach(key -> fields.addRow(new Object[] {key.type() + " / " + key.id(), "(removed)",
				display(oldRecords.record(key)), "(missing)", "", ""}));
		JsonNode oldSettings = RootSettings.snapshot(before);
		JsonNode newSettings = RootSettings.snapshot(draft);
		for (String name : RootSettings.propertyNames()) {
			JsonNode oldValue = oldSettings.get(name);
			JsonNode newValue = newSettings.get(name);
			if (!Objects.equals(oldValue, newValue)) {
				fields.addRow(new Object[] {"Incident-wide settings", name,
						display(oldValue), display(newValue), "", ""});
			}
		}
		details.setText("Select each row to inspect the full original and rebased values.");
	}

	/** Shows the full values for the selected field or record, including multiline text. */
	private void showRowDetails() {
		int row = table.getSelectedRow();
		if (row < 0 || row >= fields.getRowCount()) {
			return;
		}
		details.setText("Original:\n" + fields.getValueAt(row, 2) + "\n\nMy edit:\n" + fields.getValueAt(row, 3)
				+ "\n\nStored on host:\n" + fields.getValueAt(row, 4));
		details.setCaretPosition(0);
	}

	/**
	 * Formats a JSON value without confusing a missing value with JSON null.
	 *
	 * @param value value reported by the host
	 * @return display representation
	 */
	private static String display(JsonNode value) {
		return value == null ? "(missing)" : value.toPrettyString();
	}

	/** Retains choices without altering the durable operation. */
	private void saveChoices() {
		Entry entry = list.getSelectedValue();
		if (entry == null || entry.status() != Status.CONFLICT) {
			return;
		}
		List<Choice> values = new ArrayList<>();
		for (int i = 0; i < fields.getRowCount(); i++) {
			Object value = fields.getValueAt(i, 5);
			values.add(value instanceof Choice ? (Choice) value : Choice.UNDECIDED);
		}
		choices.put(entry.operationId(), new SavedChoices(entry.batch(), entry.conflicts(), List.copyOf(values)));
	}

	/** Enables actions only for the selected operation and complete, explicit decisions. */
	private void updateButtons() {
		Entry entry = list.getSelectedValue();
		export.setEnabled(entry != null);
		refresh.setEnabled(entry != null && entry.status() == Status.CONFLICT);
		boolean deciding = entry != null && entry.status() == Status.CONFLICT && fields.getRowCount() > 0;
		keepMine.setEnabled(deciding);
		keepShared.setEnabled(deciding);
		replace.setEnabled(entry != null && entry.status() == Status.REJECTED && entries.size() > 1
				&& entries.getElementAt(0).operationId().equals(entry.operationId()));
		reviewed.setEnabled(entry != null && entry.status() == Status.REVIEW_REQUIRED);
		confirm.setEnabled(reviewed.isEnabled() && reviewed.isSelected());
		boolean complete = entry != null && entry.batch() != null
				&& entry.status() == Status.CONFLICT && fields.getRowCount() > 0;
		for (int i = 0; complete && i < fields.getRowCount(); i++) {
			complete = fields.getValueAt(i, 5) instanceof Choice choice && choice != Choice.UNDECIDED;
		}
		resolve.setEnabled(complete);
	}

	/**
	 * Sets the same choice on every conflicting field of the selected operation.
	 *
	 * @param choice choice to apply to all rows
	 */
	private void chooseAll(Choice choice) {
		if (table.isEditing() && !table.getCellEditor().stopCellEditing()) {
			return;
		}
		for (int i = 0; i < fields.getRowCount(); i++) {
			fields.setValueAt(choice, i, 5);
		}
	}

	/** Delivers explicit choices without changing or resubmitting the draft. */
	private void requestResolution() {
		if (table.isEditing() && !table.getCellEditor().stopCellEditing()) {
			return;
		}
		updateButtons();
		if (!resolve.isEnabled()) {
			return;
		}
		Entry entry = list.getSelectedValue();
		List<Decision> decisions = new ArrayList<>();
		for (int i = 0; i < entry.conflicts().size(); i++) {
			decisions.add(new Decision(entry.conflicts().get(i), (Choice) fields.getValueAt(i, 5)));
		}
		actions.resolve(entry, List.copyOf(decisions));
	}

	/**
	 * Local, non-durable choices associated with an unchanged conflict list.
	 *
	 * @param batch submitted batch
	 * @param conflicts original conflicts
	 * @param values corresponding selections
	 */
	private record SavedChoices(RecordBatch batch, List<FieldConflict> conflicts, List<Choice> values) {
	}
}
