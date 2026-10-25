/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.sarmanagement.icsforms.model.Ics205Form;
import org.sarmanagement.icsforms.model.RadioChannel;

import javax.swing.JButton;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JComboBox;
import javax.swing.RowFilter;
import javax.swing.table.TableRowSorter;
import javax.swing.table.DefaultTableCellRenderer;
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
import java.util.Objects;
import java.util.UUID;

/**
 * Optional ICS 205 workspace. Hiding it never deletes channel or preparation data.
 */
public final class Ics205Panel extends JPanel {
	private final AppController controller;
	private final ChannelTableModel tableModel = new ChannelTableModel();
	private final JTable table = new JTable(tableModel);
	private final TableRowSorter<ChannelTableModel> channelSorter = new TableRowSorter<>(tableModel);
	private final JComboBox<String> channelVisibility = new JComboBox<>(new String[]{"Show all channels", "Included in plan only"});
	private final JTextArea instructions = new JTextArea(4, 45);
	private final JTextField preparedBy = new JTextField(24);
	private final JCheckBox preparedDatePresent = new JCheckBox("Preparation date/time recorded");
	private final JSpinner preparedDate = UiSupport.dateTimeSpinner();
	private final JLabel validation = new JLabel(" ");
	private final JLabel preparerRole = new JLabel();
	private final JButton useLeader = new JButton("Use Communications Unit Leader");
	private final Action edit = channelAction("Edit…", () -> editChannel(false));
	private final Action duplicate = channelAction("Duplicate", () -> duplicateChannel(selectedChannelIndex()));
	private final Action remove = channelAction("Remove", () -> removeChannel(selectedChannelIndex()));
	private final Action up = channelAction("Up", () -> moveSelectedChannel(-1));
	private final Action down = channelAction("Down", () -> moveSelectedChannel(1));
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
		table.setRowSorter(channelSorter);
		for (int index = 0; index < table.getColumnCount(); index++) {
			channelSorter.setSortable(index, false);
		}
		table.setDefaultRenderer(Object.class, new ChannelCellRenderer());
		table.setToolTipText("Included channels are green; channels outside the plan are gray. Double-click to edit.");
		table.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
		table.getAccessibleContext().setAccessibleName("Communications plan channels");
		int[] widths = {105, 70, 55, 90, 130, 100, 100, 65, 135, 65, 65, 90, 90, 90, 110, 150};
		for (int index = 0; index < table.getColumnCount(); index++) {
			table.getColumnModel().getColumn(index).setPreferredWidth(widths[index]);
		}
		table.addMouseListener(new java.awt.event.MouseAdapter() {
			/** {@inheritDoc} */
			@Override
			public void mousePressed(java.awt.event.MouseEvent event) {
				selectPopupRow(event);
			}

			/** {@inheritDoc} */
			@Override
			public void mouseReleased(java.awt.event.MouseEvent event) {
				selectPopupRow(event);
			}

			/** {@inheritDoc} */
			@Override
			public void mouseClicked(java.awt.event.MouseEvent event) {
				if (event.getClickCount() == 2 && javax.swing.SwingUtilities.isLeftMouseButton(event)) {
					int row = table.rowAtPoint(event.getPoint());
					if (row >= 0) {
						table.setRowSelectionInterval(row, row);
						editChannel(false);
					}
				}
			}
		});
		table.getSelectionModel().addListSelectionListener(event -> updateChannelActions());
		JPopupMenu menu = new JPopupMenu();
		for (Action action : new Action[]{edit, duplicate, remove, up, down}) {
			menu.add(action);
		}
		table.setComponentPopupMenu(menu);
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
				instructions, preparedBy}) {
			field.getDocument().addDocumentListener(listener);
		}
		preparedDatePresent.addActionListener(event -> changed());
		preparedDate.addChangeListener(event -> {
			((JSpinner.DateEditor) preparedDate.getEditor()).getTextField().setValue(preparedDate.getValue());
			changed();
		});
		UiSupport.installNameAutocomplete(preparedBy, controller::getPersonnelNames,
				name -> preparedBy.setText(name));
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
			LocalDateTime preparation = preparationDate(plan);
			preparedDatePresent.setSelected(preparation != null);
			if (preparation != null) {
				preparedDate.setValue(AppController.toDate(preparation));
			}
			preparedDate.setEnabled(preparedDatePresent.isSelected());
			tableModel.fireTableDataChanged();
			updateChannelActions();
			updatePreparerRole();
			updateWarnings();
		} finally {
			loading = false;
		}
	}

	/** Flushes preparation fields without modifying saved signature or pagination data. */
	public void pushToModel() {
		if (loading) {
			return;
		}
		applyPreparationFields();
	}

	/**
	 * Validates the optional date before applying metadata. An explicit date change updates
	 * both printed date blocks; unrelated edits preserve distinct legacy dates.
	 *
	 * @return whether all preparation input was applied.
	 */
	private boolean applyPreparationFields() {
		LocalDateTime preparation;
		try {
			preparation = enteredDate(preparedDate, preparedDatePresent);
		} catch (ParseException exception) {
			validation.setText("Invalid preparation date/time. Metadata was not saved; use yyyy-MM-dd HH:mm.");
			return false;
		}
		Ics205Form plan = controller.getData().getForm205();
		plan.setSpecialInstructions(instructions.getText());
		plan.setPreparedByName(preparedBy.getText());
		updatePreparerRole();
		if (!Objects.equals(preparation, preparationDate(plan))) {
			plan.setPreparedDateTime(preparation);
			plan.setDatePrepared(preparation);
		}
		updateWarnings();
		return true;
	}

	/**
	 * Selects the preparation timestamp, falling back to the legacy form date.
	 *
	 * @param plan saved communications plan.
	 * @return preparation timestamp, or null when neither date was recorded.
	 */
	private static LocalDateTime preparationDate(Ics205Form plan) {
		return plan.getPreparedDateTime() != null ? plan.getPreparedDateTime() : plan.getDatePrepared();
	}

	/**
	 * Parses visible spinner text in its configured display timezone without committing the editor.
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
		int view = table.convertRowIndexToView(destination);
		if (view >= 0) {
			table.setRowSelectionInterval(view, view);
		}
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
		for (Action action : new Action[]{edit, duplicate, remove, up, down}) {
			panel.add(new JButton(action));
		}
		addButton(panel, "Import CSV…", () -> new RadioChannelCsvDialogs(this, controller).importCsv());
		addButton(panel, "Import CHIRP CSV…", () -> new RadioChannelCsvDialogs(this, controller).importCsv(true));
		addButton(panel, "Export CSV…", () -> new RadioChannelCsvDialogs(this, controller).exportCsv(false));
		addButton(panel, "Export CHIRP…", () -> new RadioChannelCsvDialogs(this, controller).exportCsv(true));
		channelVisibility.addActionListener(event -> {
			channelSorter.setRowFilter(channelVisibility.getSelectedIndex() == 0 ? null : new RowFilter<>() {
				/** {@inheritDoc} */
				@Override
				public boolean include(Entry<? extends ChannelTableModel, ? extends Integer> entry) {
					return controller.getData().getForm205().getChannels().get(entry.getIdentifier()).isIncludeOnForm();
				}
			});
			updateChannelActions();
		});
		panel.add(channelVisibility);
		return panel;
	}

	/**
	 * Resolves the selected visible row to the persisted channel order.
	 *
	 * @return model index, or -1 when no row is selected.
	 */
	private int selectedChannelIndex() {
		return table.getSelectedRow() < 0 ? -1 : table.convertRowIndexToModel(table.getSelectedRow());
	}

	/**
	 * Reorders adjacent visible channels without targeting a hidden row by mistake.
	 *
	 * @param delta signed movement in the visible list.
	 */
	private void moveSelectedChannel(int delta) {
		int view = table.getSelectedRow();
		int destination = view + delta;
		if (view < 0 || destination < 0 || destination >= table.getRowCount()) {
			return;
		}
		int sourceIndex = table.convertRowIndexToModel(view);
		int targetIndex = table.convertRowIndexToModel(destination);
		Collections.swap(controller.getData().getForm205().getChannels(), sourceIndex, targetIndex);
		controller.markDirty();
		tableModel.fireTableDataChanged();
		int selected = table.convertRowIndexToView(targetIndex);
		table.setRowSelectionInterval(selected, selected);
	}

	/**
	 * Creates an action shared by the toolbar and channel context menu.
	 *
	 * @param label action label.
	 * @param operation channel operation.
	 * @return initially disabled selection-dependent action.
	 */
	private static Action channelAction(String label, Runnable operation) {
		Action action = new AbstractAction(label) {
			/** {@inheritDoc} */
			@Override
			public void actionPerformed(java.awt.event.ActionEvent event) {
				operation.run();
			}
		};
		action.setEnabled(false);
		return action;
	}

	/**
	 * Selects the context-menu target, clearing stale selection on empty space.
	 *
	 * @param event platform-specific popup mouse event.
	 */
	private void selectPopupRow(java.awt.event.MouseEvent event) {
		if (event.isPopupTrigger() || javax.swing.SwingUtilities.isRightMouseButton(event)) {
			int row = table.rowAtPoint(event.getPoint());
			if (row >= 0) {
				table.setRowSelectionInterval(row, row);
			} else {
				table.clearSelection();
			}
		}
	}

	/** Keeps toolbar and popup actions aligned with selection and ordering bounds. */
	private void updateChannelActions() {
		int row = table.getSelectedRow();
		boolean selected = row >= 0 && row < table.getRowCount();
		edit.setEnabled(selected);
		duplicate.setEnabled(selected);
		remove.setEnabled(selected);
		up.setEnabled(selected && row > 0);
		down.setEnabled(selected && row < table.getRowCount() - 1);
	}

	/** Displays the selected preparer's known role without assigning one or creating a person. */
	private void updatePreparerRole() {
		String leader = controller.getData().getOrganizationalChart().getCommunicationsUnitLeaderName();
		useLeader.setEnabled(!leader.isBlank());
		String name = preparedBy.getText().trim();
		String role = "";
		if (!name.isBlank()) {
			if (name.equalsIgnoreCase(leader.trim())) {
				role = "Communications Unit Leader";
			} else {
				var chart = controller.getData().getOrganizationalChart();
				String[][] positions = {
						{"Safety Officer", chart.getSafetyOfficerName()},
						{"Public Information Officer", chart.getPublicInformationOfficerName()},
						{"Liaison Officer", chart.getLiaisonOfficerName()},
						{"Operations Section Chief", chart.getOperationsSectionChiefName()},
						{"Planning Section Chief", chart.getPlanningSectionChiefName()},
						{"Logistics Section Chief", chart.getLogisticsSectionChiefName()},
						{"Finance/Admin Section Chief", chart.getFinanceAdminSectionChiefName()},
						{"Documentation Unit Leader", chart.getDocumentationUnitLeaderName()},
						{"Communications Technician", chart.getCommunicationsTechnicianName()},
						{"Staging Area Manager", chart.getStagingAreaManagerName()},
						{"Resources Unit Leader", chart.getResourcesUnitLeaderName()},
						{"Situation Unit Leader", chart.getSituationUnitLeaderName()},
						{"Demobilization Unit Leader", chart.getDemobilizationUnitLeaderName()},
						{"Supply Unit Leader", chart.getSupplyUnitLeaderName()},
						{"Facilities Unit Leader", chart.getFacilitiesUnitLeaderName()},
						{"Ground Support Unit Leader", chart.getGroundSupportUnitLeaderName()},
						{"Food Unit Leader", chart.getFoodUnitLeaderName()},
						{"Medical Unit Leader", chart.getMedicalUnitLeaderName()},
						{"Time Unit Leader", chart.getTimeUnitLeaderName()},
						{"Procurement Unit Leader", chart.getProcurementUnitLeaderName()},
						{"Comp/Claims Unit Leader", chart.getCompClaimsUnitLeaderName()},
						{"Cost Unit Leader", chart.getCostUnitLeaderName()}
				};
				java.util.Set<String> roles = new java.util.LinkedHashSet<>();
				if (chart.getIncidentCommanders().stream().anyMatch(person -> name.equalsIgnoreCase(person.trim()))) {
					roles.add("Incident Commander");
				}
				for (String[] position : positions) {
					if (name.equalsIgnoreCase(position[1].trim())) {
						roles.add(position[0]);
					}
				}
				chart.getAdditionalPositions().stream()
						.filter(position -> name.equalsIgnoreCase(position.getName().trim()))
						.map(org.sarmanagement.icsforms.model.OrgChartEntry::getTitle)
						.filter(title -> !title.isBlank()).forEach(roles::add);
				role = String.join(", ", roles);
			}
			var context = controller.getData().getIncidentContext();
			if (role.isBlank() && name.equalsIgnoreCase(context.getCurrentUser().trim())) {
				role = context.getCurrentUserPositionTitle();
			}
		}
		preparerRole.setText("Role: " + (role.isBlank() ? "Not specified" : role));
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
		UiSupport.addRow(form, 0, "Special instructions", new JScrollPane(instructions));
		JPanel preparer = new JPanel(new FlowLayout(FlowLayout.LEFT));
		preparer.add(preparedBy);
		useLeader.addActionListener(event -> preparedBy.setText(
				controller.getData().getOrganizationalChart().getCommunicationsUnitLeaderName()));
		preparer.add(useLeader);
		preparer.add(preparerRole);
		UiSupport.addRow(form, 1, "Prepared by (override allowed)", preparer);
		JPanel date = new JPanel(new FlowLayout(FlowLayout.LEFT));
		date.add(preparedDatePresent);
		date.add(preparedDate);
		UiSupport.addRow(form, 2, "Preparation date/time", date);
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
		int index = create ? -1 : selectedChannelIndex();
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
				editor, new Dimension(960, 620))) {
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
			updatePreparerRole();
			preparedDate.setEnabled(preparedDatePresent.isSelected());
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
		private final RadioChannelEditorPanel.Field[] columns = {
				RadioChannelEditorPanel.Field.ZONE, RadioChannelEditorPanel.Field.NUMBER,
				RadioChannelEditorPanel.Field.FUNCTION, RadioChannelEditorPanel.Field.NAME,
				RadioChannelEditorPanel.Field.RX_FREQUENCY, RadioChannelEditorPanel.Field.TX_FREQUENCY,
				RadioChannelEditorPanel.Field.MODE, RadioChannelEditorPanel.Field.ASSIGNMENT,
				RadioChannelEditorPanel.Field.RX_BANDWIDTH, RadioChannelEditorPanel.Field.TX_BANDWIDTH,
				RadioChannelEditorPanel.Field.RX_TONE, RadioChannelEditorPanel.Field.TX_TONE,
				RadioChannelEditorPanel.Field.PROTOCOL, RadioChannelEditorPanel.Field.SYSTEM,
				RadioChannelEditorPanel.Field.REMARKS
		};
		/** {@inheritDoc} */
		@Override
		public int getRowCount() {
			return controller == null ? 0 : controller.getData().getForm205().getChannels().size();
		}

		/** {@inheritDoc} */
		@Override
		public int getColumnCount() {
			return columns.length + 1;
		}

		/** {@inheritDoc} */
		@Override
		public String getColumnName(int column) {
			return column == 0 ? "Included in plan" : columns[column - 1].label;
		}

		/** {@inheritDoc} */
		@Override
		public Object getValueAt(int row, int column) {
			RadioChannel channel = controller.getData().getForm205().getChannels().get(row);
			if (column == 0) {
				return channel.isIncludeOnForm() ? "Yes" : "No";
			}
			RadioChannelEditorPanel.Field field = columns[column - 1];
			String value = field.getter.apply(channel);
			return field == RadioChannelEditorPanel.Field.MODE ? switch (value) {
				case "A" -> "Analog";
				case "D" -> "Digital";
				case "M" -> "Mixed";
				default -> value;
			} : value;
		}
	}

	/** Distinguishes plan inclusion without overriding the look-and-feel selection colors. */
	private final class ChannelCellRenderer extends DefaultTableCellRenderer {
		/** {@inheritDoc} */
		@Override
		public java.awt.Component getTableCellRendererComponent(JTable source, Object value, boolean selected,
				boolean focused, int row, int column) {
			super.getTableCellRendererComponent(source, value, selected, focused, row, column);
			boolean included = controller.getData().getForm205().getChannels()
					.get(source.convertRowIndexToModel(row)).isIncludeOnForm();
			if (!selected) {
				setBackground(included ? new java.awt.Color(232, 245, 233) : new java.awt.Color(240, 240, 240));
				setForeground(java.awt.Color.BLACK);
			}
			setToolTipText((included ? "Included in plan" : "Not included in plan") + ": " + Objects.toString(value, ""));
			return this;
		}
	}
}
