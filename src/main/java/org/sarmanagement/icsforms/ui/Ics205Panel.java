/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.sarmanagement.icsforms.model.Ics205Form;
import org.sarmanagement.icsforms.model.RadioChannel;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.table.AbstractTableModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.text.ParseException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Optional ICS 205 workspace. Hiding it never deletes channel or preparation data.
 */
public final class Ics205Panel extends JPanel {
	private final AppController controller;
	private final ChannelTableModel tableModel = new ChannelTableModel();
	private final JTable table = new JTable(tableModel);
	private final JTextArea instructions = new JTextArea(4, 45);
	private final JTextField preparedBy = new JTextField(24);
	private final JTextField signature = new JTextField(24);
	private final JCheckBox preparedDatePresent = new JCheckBox("Preparation date/time recorded");
	private final JSpinner preparedDate = UiSupport.dateTimeSpinner();
	private final JCheckBox datePreparedPresent = new JCheckBox("Form date prepared recorded");
	private final JSpinner datePrepared = UiSupport.dateTimeSpinner();
	private final JTextField iapPage = new JTextField(8);
	private final JLabel validation = new JLabel(" ");
	private Consumer<String> exportRequest = ignored -> { };
	private boolean loading;

	/**
	 * Creates the communications workspace.
	 *
	 * @param controller active incident controller.
	 */
	public Ics205Panel(AppController controller) {
		super(new BorderLayout(6, 6));
		this.controller = controller;
		table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		table.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
		table.getAccessibleContext().setAccessibleName("Communications plan channels");
		for (int index = 0; index < table.getColumnCount(); index++) {
			table.getColumnModel().getColumn(index).setPreferredWidth(145);
		}
		table.addMouseListener(new java.awt.event.MouseAdapter() {
			/** {@inheritDoc} */
			@Override
			public void mouseClicked(java.awt.event.MouseEvent event) {
				if (event.getClickCount() == 2 && table.getSelectedRow() >= 0) {
					editChannel(false);
				}
			}
		});
		add(buildToolbar(), BorderLayout.NORTH);
		add(new JScrollPane(table), BorderLayout.CENTER);
		JPanel footer = new JPanel(new BorderLayout(4, 4));
		footer.add(buildPreparationEditor(), BorderLayout.CENTER);
		footer.add(validation, BorderLayout.SOUTH);
		add(footer, BorderLayout.SOUTH);
		refreshFromModel();
		DocumentListener listener = new DocumentListener() {
			/** {@inheritDoc} */
			@Override
			public void insertUpdate(DocumentEvent event) {
				changed();
			}

			/** {@inheritDoc} */
			@Override
			public void removeUpdate(DocumentEvent event) {
				changed();
			}

			/** {@inheritDoc} */
			@Override
			public void changedUpdate(DocumentEvent event) {
				changed();
			}
		};
		for (javax.swing.text.JTextComponent field : new javax.swing.text.JTextComponent[]{
				instructions, preparedBy, signature, iapPage}) {
			field.getDocument().addDocumentListener(listener);
		}
		preparedDatePresent.addActionListener(event -> changed());
		preparedDate.addChangeListener(event -> changed());
		datePreparedPresent.addActionListener(event -> changed());
		datePrepared.addChangeListener(event -> changed());
		UiSupport.installNameAutocomplete(preparedBy, controller::getPersonnelNames,
				name -> preparedBy.setText(name));
	}

	/**
	 * Sets the PDF export shortcut, also available when the workspace is hidden.
	 *
	 * @param request explicit form export action.
	 */
	public void setExportRequest(Consumer<String> request) {
		exportRequest = request == null ? ignored -> { } : request;
	}

	/** Reloads the active document without creating preparation details or dirtying it. */
	public void refreshFromModel() {
		loading = true;
		try {
			Ics205Form plan = controller.getData().getForm205();
			instructions.setText(plan.getSpecialInstructions());
			preparedBy.setText(plan.getPreparedByName());
			preparedBy.setToolTipText("Suggested Communications Unit Leader: "
					+ controller.getData().getOrganizationalChart().getCommunicationsUnitLeaderName());
			signature.setText(plan.getPreparedBySignature());
			preparedDatePresent.setSelected(plan.getPreparedDateTime() != null);
			if (plan.getPreparedDateTime() != null) {
				preparedDate.setValue(AppController.toDate(plan.getPreparedDateTime()));
			}
			preparedDate.setEnabled(preparedDatePresent.isSelected());
			datePreparedPresent.setSelected(plan.getDatePrepared() != null);
			if (plan.getDatePrepared() != null) {
				datePrepared.setValue(AppController.toDate(plan.getDatePrepared()));
			}
			datePrepared.setEnabled(datePreparedPresent.isSelected());
			iapPage.setText(plan.getIapPage());
			tableModel.fireTableDataChanged();
			updateWarnings();
		} finally {
			loading = false;
		}
	}

	/** Flushes preparation fields, leaving an unrecorded date and signature blank. */
	public void pushToModel() {
		if (loading) {
			return;
		}
		applyPreparationFields();
	}

	/**
	 * Validates both optional dates before applying any metadata from this edit.
	 *
	 * @return whether all preparation input was applied.
	 */
	private boolean applyPreparationFields() {
		LocalDateTime preparation;
		LocalDateTime formDate;
		try {
			preparation = enteredDate(preparedDate, preparedDatePresent);
			formDate = enteredDate(datePrepared, datePreparedPresent);
		} catch (ParseException exception) {
			validation.setText("Invalid preparation date/time. Metadata was not saved; use yyyy-MM-dd HH:mm.");
			return false;
		}
		Ics205Form plan = controller.getData().getForm205();
		plan.setSpecialInstructions(instructions.getText());
		plan.setPreparedByName(preparedBy.getText());
		plan.setPreparedBySignature(signature.getText());
		plan.setPreparedDateTime(preparation);
		plan.setDatePrepared(formDate);
		plan.setIapPage(iapPage.getText());
		updateWarnings();
		return true;
	}

	/**
	 * Parses visible spinner text in its configured display timezone without committing either editor.
	 * An unchanged display retains stored seconds rather than silently truncating them.
	 *
	 * @param spinner date editor.
	 * @param present whether the operator recorded this date.
	 * @return entered UTC date/time, or null when not recorded.
	 * @throws ParseException when entered date text is invalid.
	 */
	private static LocalDateTime enteredDate(JSpinner spinner, JCheckBox present) throws ParseException {
		if (!present.isSelected()) {
			return null;
		}
		JSpinner.DateEditor editor = (JSpinner.DateEditor) spinner.getEditor();
		var format = (java.text.SimpleDateFormat) editor.getFormat().clone();
		format.setLenient(false);
		String text = editor.getTextField().getText();
		if (format.format((java.util.Date) spinner.getValue()).equals(text)) {
			return AppController.toLocalDateTime((java.util.Date) spinner.getValue());
		}
		java.text.ParsePosition position = new java.text.ParsePosition(0);
		java.util.Date value = format.parse(text, position);
		if (value == null || position.getIndex() != text.length()) {
			throw new ParseException("Invalid date/time", Math.max(0, position.getErrorIndex()));
		}
		return AppController.toLocalDateTime(value);
	}

	/**
	 * Adds or replaces an explicitly accepted channel; callers can test transactions without dialogs.
	 *
	 * @param index existing row, or -1 for append.
	 * @param channel detached edited channel.
	 */
	void applyChannel(int index, RadioChannel channel) {
		UUID.fromString(channel.getChannelId());
		var channels = controller.getData().getForm205().getChannels();
		for (int row = 0; row < channels.size(); row++) {
			if (row != index && channel.getChannelId().equals(channels.get(row).getChannelId())) {
				throw new IllegalArgumentException("Channel ID already belongs to another row.");
			}
		}
		if (index < 0) {
			channels.add(channel);
		} else {
			channels.set(index, channel);
		}
		controller.markDirty();
		tableModel.fireTableDataChanged();
		updateWarnings();
	}

	/**
	 * Duplicates an existing channel with a new stable identifier.
	 *
	 * @param index source row.
	 */
	void duplicateChannel(int index) {
		if (index < 0) {
			return;
		}
		RadioChannel copy = controller.getData().getForm205().getChannels().get(index).deepCopy();
		copy.setChannelId(UUID.randomUUID().toString());
		applyChannel(-1, copy);
	}

	/**
	 * Moves a channel one place while preserving its identity and metadata.
	 *
	 * @param index source row.
	 * @param delta signed movement.
	 */
	void moveChannel(int index, int delta) {
		int destination = index + delta;
		var channels = controller.getData().getForm205().getChannels();
		if (index < 0 || destination < 0 || destination >= channels.size()) {
			return;
		}
		Collections.swap(channels, index, destination);
		controller.markDirty();
		tableModel.fireTableDataChanged();
		table.setRowSelectionInterval(destination, destination);
	}

	/**
	 * Removes an explicitly selected row.
	 *
	 * @param index row to remove.
	 */
	void removeChannel(int index) {
		if (index >= 0) {
			controller.getData().getForm205().getChannels().remove(index);
			controller.markDirty();
			tableModel.fireTableDataChanged();
			updateWarnings();
		}
	}

	/**
	 * Builds channel actions and explicit file interchange controls.
	 *
	 * @return toolbar.
	 */
	private JPanel buildToolbar() {
		JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		addButton(panel, "Add…", () -> editChannel(true));
		addButton(panel, "Edit…", () -> editChannel(false));
		addButton(panel, "Duplicate", () -> duplicateChannel(table.getSelectedRow()));
		addButton(panel, "Remove", () -> removeChannel(table.getSelectedRow()));
		addButton(panel, "Up", () -> moveChannel(table.getSelectedRow(), -1));
		addButton(panel, "Down", () -> moveChannel(table.getSelectedRow(), 1));
		addButton(panel, "Import CSV…", () -> new RadioChannelCsvDialogs(this, controller).importCsv());
		addButton(panel, "Export CSV…", () -> new RadioChannelCsvDialogs(this, controller).exportCsv(false));
		addButton(panel, "Export CHIRP…", () -> new RadioChannelCsvDialogs(this, controller).exportCsv(true));
		addButton(panel, "Export ICS 205 PDF", () -> {
			pushToModel();
			exportRequest.accept("ICS 205");
		});
		return panel;
	}

	/**
	 * Builds optional preparation and multiline special-instruction fields.
	 *
	 * @return preparation editor.
	 */
	private JPanel buildPreparationEditor() {
		JPanel form = UiSupport.formPanel();
		instructions.setLineWrap(true);
		instructions.setWrapStyleWord(true);
		instructions.getAccessibleContext().setAccessibleName("Special instructions");
		preparedBy.getAccessibleContext().setAccessibleName("Prepared by name");
		signature.getAccessibleContext().setAccessibleName("Prepared by signature");
		UiSupport.addRow(form, 0, "Special instructions", new JScrollPane(instructions));
		JPanel preparer = new JPanel(new FlowLayout(FlowLayout.LEFT));
		preparer.add(preparedBy);
		addButton(preparer, "Use Communications Unit Leader", () -> preparedBy.setText(
				controller.getData().getOrganizationalChart().getCommunicationsUnitLeaderName()));
		UiSupport.addRow(form, 1, "Prepared by (override allowed)", preparer);
		UiSupport.addRow(form, 2, "Signature (only if supplied)", signature);
		JPanel date = new JPanel(new FlowLayout(FlowLayout.LEFT));
		date.add(preparedDatePresent);
		date.add(preparedDate);
		UiSupport.addRow(form, 3, "Preparation date/time", date);
		JPanel formDate = new JPanel(new FlowLayout(FlowLayout.LEFT));
		formDate.add(datePreparedPresent);
		formDate.add(datePrepared);
		UiSupport.addRow(form, 4, "2. Date Prepared", formDate);
		UiSupport.addRow(form, 5, "IAP page", iapPage);
		return form;
	}

	/**
	 * Adds a named button.
	 *
	 * @param panel containing toolbar.
	 * @param label button label.
	 * @param action click action.
	 */
	private static void addButton(JPanel panel, String label, Runnable action) {
		JButton button = new JButton(label);
		button.addActionListener(event -> action.run());
		panel.add(button);
	}

	/**
	 * Opens a detached detailed editor and applies only confirmed valid input.
	 *
	 * @param create whether to create a new channel.
	 */
	private void editChannel(boolean create) {
		int index = create ? -1 : table.getSelectedRow();
		if (!create && index < 0) {
			return;
		}
		RadioChannel source = create ? new RadioChannel()
				: controller.getData().getForm205().getChannels().get(index);
		if (create) {
			source.setChannelId(UUID.randomUUID().toString());
		}
		RadioChannelEditorPanel editor = new RadioChannelEditorPanel(source, RadioChannelPicker.legacyLabels(controller));
		while (UiSupport.showResizableConfirmDialog(this, create ? "Add radio channel" : "Edit radio channel",
				editor, new Dimension(820, 700))) {
			try {
				applyChannel(index, editor.editedChannel());
				return;
			} catch (IllegalArgumentException exception) {
				RadioChannelPicker.showError(this, exception.getMessage());
			}
		}
	}

	/** Saves a user edit and schedules existing autosave behavior. */
	private void changed() {
		if (!loading) {
			preparedDate.setEnabled(preparedDatePresent.isSelected());
			datePrepared.setEnabled(datePreparedPresent.isSelected());
			if (applyPreparationFields()) {
				controller.markDirty();
			}
		}
	}

	/** Shows non-blocking programming warnings; incomplete drafts remain editable and exportable. */
	private void updateWarnings() {
		long incomplete = controller.getData().getForm205().getChannels().stream()
				.filter(channel -> channel.getChannelName().isBlank() || !validFrequency(channel.getRxFrequency())
						|| channel.getMode().isBlank()
						|| RadioChannel.TX_POLICY_UNKNOWN.equals(channel.getTxPolicy())
						|| RadioChannel.TX_POLICY_TRANSMIT.equals(channel.getTxPolicy())
						&& !validFrequency(channel.getTxFrequency()))
				.count();
		validation.setText(incomplete == 0 ? "Verify radio programming with the Communications Unit Leader."
				: incomplete + " channel(s) need programming review (missing or unrecognized details). Values are retained.");
	}

	/**
	 * Checks only positive decimal frequency syntax without assuming a band, units or transmit permission.
	 *
	 * @param frequency raw operator-entered frequency.
	 * @return whether the value is a positive decimal number.
	 */
	private static boolean validFrequency(String frequency) {
		try {
			return !frequency.isBlank() && new java.math.BigDecimal(frequency).signum() > 0;
		} catch (NumberFormatException exception) {
			return false;
		}
	}

	/** Read-only overview; the detailed editor exposes long values without truncation. */
	private final class ChannelTableModel extends AbstractTableModel {
		/** {@inheritDoc} */
		@Override
		public int getRowCount() {
			return controller == null ? 0 : controller.getData().getForm205().getChannels().size();
		}

		/** {@inheritDoc} */
		@Override
		public int getColumnCount() {
			return RadioChannelEditorPanel.Field.values().length + 3;
		}

		/** {@inheritDoc} */
		@Override
		public String getColumnName(int column) {
			int fields = RadioChannelEditorPanel.Field.values().length;
			return column < fields ? RadioChannelEditorPanel.Field.values()[column].label
					: column == fields ? "Transmit policy" : column == fields + 1 ? "Include on form" : "Programming metadata";
		}

		/** {@inheritDoc} */
		@Override
		public Object getValueAt(int row, int column) {
			RadioChannel channel = controller.getData().getForm205().getChannels().get(row);
			int fields = RadioChannelEditorPanel.Field.values().length;
			return column < fields ? RadioChannelEditorPanel.Field.values()[column].getter.apply(channel)
					: column == fields ? channel.getTxPolicy()
					: column == fields + 1 ? channel.isIncludeOnForm() : channel.getProgrammingMetadata();
		}
	}
}
