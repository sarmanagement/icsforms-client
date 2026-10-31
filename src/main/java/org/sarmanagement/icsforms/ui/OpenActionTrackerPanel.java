/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.sarmanagement.icsforms.model.OpenActionItem;
import org.sarmanagement.icsforms.model.UtcTimestamp;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.text.ParseException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Independent optional ICS 233 editor for regular, non-clue action items. */
public class OpenActionTrackerPanel extends JPanel {
	private final AppController controller;
	private final ActionTableModel tableModel = new ActionTableModel();
	private final JComboBox<String> filter = new JComboBox<>(new String[]{"Open", "Completed", "All"});
	private final JTable table = new JTable(tableModel) {
		/**
		 * Highlights overdue open actions without obscuring selection.
		 * @param renderer cell renderer
		 * @param row view row
		 * @param column view column
		 * @return rendered component
		 */
		@Override
		public Component prepareRenderer(javax.swing.table.TableCellRenderer renderer, int row, int column) {
			Component component = super.prepareRenderer(renderer, row, column);
			if (!isRowSelected(row)) {
				OpenActionItem item = tableModel.rows.get(convertRowIndexToModel(row));
				component.setBackground(isOverdue(item) ? new Color(255, 225, 210)
						: isDueSoon(item) ? new Color(255, 245, 180) : getBackground());
			}
			return component;
		}
	};

	/**
	 * Creates a regular action tracker.
	 * @param controller incident controller
	 */
	public OpenActionTrackerPanel(AppController controller) {
		super(new BorderLayout(8, 8));
		this.controller = controller;
		setBorder(BorderFactory.createTitledBorder("Open Action Tracker — ICS 233"));
		table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		table.setFillsViewportHeight(true);
		JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT));
		header.add(new JLabel("Show"));
		header.add(filter);
		header.add(new JLabel("POC = responsible point of contact. Only the current POC is retained."));
		filter.addActionListener(event -> refreshFromModel());
		add(header, BorderLayout.NORTH);
		add(new JScrollPane(table), BorderLayout.CENTER);
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
		JButton add = new JButton("Add");
		JButton edit = new JButton("Edit");
		JButton remove = new JButton("Remove");
		add.addActionListener(event -> editItem(null));
		edit.addActionListener(event -> {
			if (table.getSelectedRow() >= 0) {
				editItem(tableModel.rows.get(table.convertRowIndexToModel(table.getSelectedRow())));
			}
		});
		remove.addActionListener(event -> {
			if (table.getSelectedRow() >= 0 && JOptionPane.showConfirmDialog(this, "Remove selected action?",
					"Remove Action", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
				removeItem(tableModel.rows.get(table.convertRowIndexToModel(table.getSelectedRow())));
			}
		});
		buttons.add(add);
		buttons.add(edit);
		buttons.add(remove);
		add(buttons, BorderLayout.SOUTH);
		refreshFromModel();
	}

	/**
	 * Opens a validated action editor, applying changes only on confirmation.
	 * @param existing action to edit, or null to create a new action
	 */
	private void editItem(OpenActionItem existing) {
		OpenActionItem item = existing == null ? new OpenActionItem() : existing;
		JPanel form = UiSupport.formPanel();
		JTextArea description = UiSupport.textArea(3);
		description.setText(item.getItem());
		JTextField poc = UiSupport.textField();
		poc.setText(item.getPoc());
		poc.setToolTipText("POC (point of contact): the person or resource responsible for this action.");
		JCheckBox briefed = new JCheckBox("Current POC briefed", item.isBriefedToPoc());
		briefed.setToolTipText("Select after the current POC has been informed of and briefed on this action.");
		resetBriefingOnPocEdit(poc, briefed);
		ActionDateField start = new ActionDateField(item.getStartDate());
		ActionDateField target = new ActionDateField(item.getTargetDate());
		ActionDateField actual = new ActionDateField(item.getActualDate());
		JTextArea status = UiSupport.textArea(2);
		status.setText(item.getStatus());
		UiSupport.addRow(form, 0, "Item (number assigned automatically)", new JScrollPane(description));
		UiSupport.addRow(form, 1, "Current POC", poc);
		UiSupport.addRow(form, 2, "", briefed);
		UiSupport.addRow(form, 3, "Start", start);
		UiSupport.addRow(form, 4, "Target", target);
		UiSupport.addRow(form, 5, "Actual completion", actual);
		UiSupport.addRow(form, 6, "Status / progress", new JScrollPane(status));
		while (UiSupport.showResizableConfirmDialog(this, existing == null ? "Add Action" : "Edit Action",
				new JScrollPane(form), new Dimension(680, 460))) {
			try {
				LocalDateTime startValue = start.getValue();
				LocalDateTime targetValue = target.getValue();
				LocalDateTime actualValue = actual.getValue();
				item.setItem(description.getText().trim());
				item.setPoc(poc.getText().trim());
				item.setBriefedToPoc(briefed.isSelected());
				item.setStartDate(startValue);
				item.setTargetDate(targetValue);
				item.setActualDate(actualValue);
				item.setStatus(status.getText().trim());
				if (existing == null) {
					addItem(item);
				} else {
					controller.markDirty();
					refreshFromModel();
				}
				return;
			} catch (ParseException exception) {
				JOptionPane.showMessageDialog(this, "Enter a valid date/time or clear its Set checkbox.",
						"Invalid Date", JOptionPane.ERROR_MESSAGE);
			}
		}
	}

	/**
	 * Clears briefing on POC text edits while allowing explicit rebriefing.
	 * @param poc point-of-contact editor
	 * @param briefed briefing checkbox
	 */
	static void resetBriefingOnPocEdit(JTextField poc, JCheckBox briefed) {
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
	}

	/**
	 * Adds an action and assigns sequential numbers to the full list.
	 * @param item action to add
	 */
	void addItem(OpenActionItem item) {
		controller.getData().getOpenActionItems().add(item);
		renumber();
		controller.markDirty();
		refreshFromModel();
	}

	/**
	 * Removes an action and closes gaps in sequence numbers.
	 * @param item action to remove
	 */
	void removeItem(OpenActionItem item) {
		controller.getData().getOpenActionItems().remove(item);
		renumber();
		controller.markDirty();
		refreshFromModel();
	}

	/** Assigns numbers in stored order, independent of the displayed filter. */
	private void renumber() {
		List<OpenActionItem> items = controller.getData().getOpenActionItems();
		for (int i = 0; i < items.size(); i++) {
			items.get(i).setNumber(i + 1);
		}
	}

	/** Refreshes the view without dropping hidden or completed records. */
	public void refreshFromModel() {
		tableModel.rows = new ArrayList<>();
		for (OpenActionItem item : controller.getData().getOpenActionItems()) {
			if ("All".equals(filter.getSelectedItem())
					|| item.isCompleted() == "Completed".equals(filter.getSelectedItem())) {
				tableModel.rows.add(item);
			}
		}
		tableModel.fireTableDataChanged();
	}

	/**
	 * Returns whether an open action is past its UTC target.
	 * @param item action to inspect
	 * @return true when overdue and not completed
	 */
	static boolean isOverdue(OpenActionItem item) {
		return !item.isCompleted() && item.getTargetDate() != null
				&& item.getTargetDate().isBefore(UtcTimestamp.now());
	}

	/**
	 * Returns whether an open action is due within the next 24 hours.
	 * @param item action to inspect
	 * @return true when due soon, excluding overdue and completed actions
	 */
	static boolean isDueSoon(OpenActionItem item) {
		LocalDateTime now = UtcTimestamp.now();
		return !item.isCompleted() && item.getTargetDate() != null
				&& !item.getTargetDate().isBefore(now) && !item.getTargetDate().isAfter(now.plusHours(24));
	}

	/** Read-only table; edits use the complete action dialog. */
	private static class ActionTableModel extends AbstractTableModel {
		private final String[] columns = {"No.", "Item", "Current POC", "Briefed", "Start", "Target",
				"Actual completion", "Status"};
		private List<OpenActionItem> rows = new ArrayList<>();

		/**
		 * Returns the visible row count.
		 * @return visible count
		 */
		@Override
		public int getRowCount() {
			return rows.size();
		}

		/**
		 * Returns the action column count.
		 * @return column count
		 */
		@Override
		public int getColumnCount() {
			return columns.length;
		}

		/**
		 * Returns a column heading.
		 * @param column column index
		 * @return heading
		 */
		@Override
		public String getColumnName(int column) {
			return columns[column];
		}

		/**
		 * Returns the displayed action value.
		 * @param rowIndex visible row index
		 * @param columnIndex column index
		 * @return formatted value
		 */
		@Override
		public Object getValueAt(int rowIndex, int columnIndex) {
			OpenActionItem item = rows.get(rowIndex);
			return switch (columnIndex) {
				case 0 -> item.getNumber();
				case 1 -> item.getItem();
				case 2 -> item.getPoc();
				case 3 -> item.isBriefedToPoc() ? "Yes" : "No";
				case 4 -> ActionDateField.format(item.getStartDate());
				case 5 -> ActionDateField.format(item.getTargetDate());
				case 6 -> ActionDateField.format(item.getActualDate());
				default -> item.getStatus();
			};
		}
	}
}
