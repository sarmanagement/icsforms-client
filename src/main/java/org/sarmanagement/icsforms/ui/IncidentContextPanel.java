/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.IncidentContext;
import org.sarmanagement.icsforms.model.IncidentLocation;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.DefaultCellEditor;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.ArrayList;

/**
 * Global editor for incident metadata shared by ICS 202, ICS 204, and SAR
 * scaffolding.
 *
 * <p>
 * In addition to the editable fields, this panel shows read-only status
 * information: the incident store location, the current IAP phase, and a table of
 * past operational periods (each with start/end date-times and the incident
 * commander names recorded at that time).
 * </p>
 */
public class IncidentContextPanel extends JPanel {
	private static final String[] POSITION_PRESETS = {"", "Incident Commander", "Unified Command", "Safety Officer",
			"Operations Section Chief", "Planning Section Chief", "Logistics Section Chief",
			"Finance / Admin Section Chief", "Documentation Unit Leader"};
	private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

	private final AppController controller;

	// Read-only status labels (refreshed from model)
	private final JLabel filePathLabel = new JLabel(" ");
	private final JLabel phaseLabel = new JLabel(" ");

	// Editable fields
	private final JTextField incidentNameField = UiSupport.textField();
	private final JSpinner startField = UiSupport.dateTimeSpinner();
	private final JSpinner endField = UiSupport.dateTimeSpinner();
	private final JTextField taskMapField = UiSupport.textField();
	private final JTextField currentUserField = UiSupport.textField();
	private final JComboBox<String> currentUserPositionCombo = new JComboBox<>(POSITION_PRESETS);
	private final DefaultTableModel locationsModel = new DefaultTableModel(
			new String[]{"Type", "Name", "Address", "ID"}, 0) {
		@Override
		public boolean isCellEditable(int row, int column) {
			return column < 3;
		}
	};
	private final JTable locationsTable = new JTable(locationsModel);
	private boolean loadingLocations;
	private boolean savingLocations;

	// Operational period history table
	private final DefaultTableModel historyTableModel = new DefaultTableModel(
			new String[]{"#", "Period Start", "Period End", "Incident Commander(s)"}, 0) {
		@Override
		public boolean isCellEditable(int row, int column) {
			return false;
		}
	};
	private final JTable historyTable = new JTable(historyTableModel);

	/**
	 * Creates the shared incident context editor.
	 *
	 * @param controller
	 *            application controller.
	 */
	public IncidentContextPanel(AppController controller) {
		super(new BorderLayout());
		this.controller = controller;
		currentUserPositionCombo.setEditable(true);

		// --- Status section (read-only) ---
		JPanel statusForm = UiSupport.formPanel();
		statusForm.setBorder(BorderFactory.createTitledBorder("Incident Status (read-only)"));
		UiSupport.addRow(statusForm, 0, "Incident store", filePathLabel);
		UiSupport.addRow(statusForm, 1, "Current phase", phaseLabel);

		// --- Editable context section ---
		JPanel editForm = UiSupport.formPanel();
		editForm.setBorder(BorderFactory.createTitledBorder("Shared Incident Context"));
		UiSupport.addRow(editForm, 0, "Incident name", incidentNameField);
		UiSupport.addRow(editForm, 1, "Operational period start", startField);
		UiSupport.addRow(editForm, 2, "Operational period end", endField);
		UiSupport.addRow(editForm, 3, "Task map / CalTopo id", taskMapField);

		// Preparer / current user — plain text entry with an optional person picker.
		JPanel userRow = new JPanel(new BorderLayout(4, 0));
		userRow.setOpaque(false);
		userRow.add(currentUserField, BorderLayout.CENTER);
		JButton pickUserButton = new JButton("Pick…");
		pickUserButton.setToolTipText("Select from known personnel (T-cards)");
		pickUserButton.addActionListener(e -> UiSupport.openInstalledNamePicker(currentUserField));
		userRow.add(pickUserButton, BorderLayout.EAST);
		UiSupport.addRow(editForm, 4, "Preparer / current user", userRow);
		UiSupport.addRow(editForm, 5, "Preparer position/title", currentUserPositionCombo);
		UiSupport.installNameAutocomplete(currentUserField, controller::getPersonnelNames,
				selectedName -> controller.markDirty(), this::createCurrentUserPersonnelCard);

		// --- Operational period history table ---
		JPanel historyPanel = new JPanel(new BorderLayout());
		historyPanel.setBorder(BorderFactory.createTitledBorder("Operational Period History"));
		historyTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
		historyTable.setFillsViewportHeight(false);
		historyPanel.add(new JScrollPane(historyTable), BorderLayout.CENTER);

		JPanel locationsPanel = new JPanel(new BorderLayout(4, 4));
		locationsPanel.setBorder(BorderFactory.createTitledBorder("Incident Locations"));
		locationsTable.setFillsViewportHeight(true);
		locationsTable.getColumnModel().getColumn(0).setCellEditor(new DefaultCellEditor(
				new JComboBox<>(new String[]{"ICP", "Staging Area", "Base", "Other"})));
		locationsTable.getColumnModel().getColumn(3).setMinWidth(0);
		locationsTable.getColumnModel().getColumn(3).setMaxWidth(0);
		locationsTable.getColumnModel().getColumn(3).setWidth(0);
		locationsPanel.add(new JScrollPane(locationsTable), BorderLayout.CENTER);
		JPanel locationControls = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));
		addLocationButton(locationControls, "Add ICP", "ICP");
		addLocationButton(locationControls, "Add staging area", "Staging Area");
		addLocationButton(locationControls, "Add base", "Base");
		addLocationButton(locationControls, "Add other", "Other");
		JButton removeLocation = new JButton("Remove selected");
		removeLocation.addActionListener(event -> {
			int row = locationsTable.getSelectedRow();
			if (row >= 0) {
				locationsModel.removeRow(locationsTable.convertRowIndexToModel(row));
			}
		});
		locationControls.add(removeLocation);
		locationsPanel.add(locationControls, BorderLayout.SOUTH);
		locationsModel.addTableModelListener(event -> {
			if (!loadingLocations && !savingLocations) {
				saveIncidentLocations();
				controller.markDirty();
			}
		});

		JPanel content = new JPanel();
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setOpaque(false);
		content.add(statusForm);
		content.add(Box.createVerticalStrut(6));
		content.add(editForm);
		content.add(Box.createVerticalStrut(6));
		content.add(locationsPanel);
		content.add(Box.createVerticalStrut(6));
		content.add(historyPanel);

		JPanel topAligned = new JPanel(new BorderLayout());
		topAligned.setOpaque(false);
		topAligned.add(content, BorderLayout.NORTH);
		JScrollPane scrollPane = new JScrollPane(topAligned);
		scrollPane.setBorder(BorderFactory.createEmptyBorder());
		add(scrollPane, BorderLayout.CENTER);
	}

	/**
	 * Loads field values from the model.
	 */
	public void refreshFromModel() {
		IncidentContext context = controller.getData().getIncidentContext();
		incidentNameField.setText(nullSafe(context.getIncidentName()));
		startField.setValue(toDate(context.getOperationalPeriodStart()));
		endField.setValue(toDate(context.getOperationalPeriodEnd()));
		taskMapField.setText(nullSafe(context.getTaskMap()));
		currentUserField.setText(nullSafe(context.getCurrentUser()));
		currentUserPositionCombo.setSelectedItem(nullSafe(context.getCurrentUserPositionTitle()));
		loadIncidentLocations(controller.getData().getIncidentLocations());

		// Refresh read-only status labels.
		filePathLabel.setText(controller.getStoreDisplayLabel());
		phaseLabel.setText(phaseDisplayName(controller.getData().getIapPhase()));

		// Rebuild the period history table.
		historyTableModel.setRowCount(0);
		List<AppData.OperationalPeriodRecord> history = controller.getData().getOperationalPeriodHistory();
		for (int i = 0; i < history.size(); i++) {
			AppData.OperationalPeriodRecord rec = history.get(i);
			historyTableModel.addRow(new Object[]{i + 1, formatDateTime(rec.getPeriodStart()),
					formatDateTime(rec.getPeriodEnd()), String.join("; ", rec.getIncidentCommanders())});
		}
		// The "current" period always appears as the last row (in progress).
		List<String> currentIcs = controller.getData().getOrganizationalChart().getIncidentCommanders();
		if (currentIcs == null) {
			currentIcs = List.of();
		}
		historyTableModel.addRow(new Object[]{history.size() + 1, formatDateTime(context.getOperationalPeriodStart()),
				formatDateTime(context.getOperationalPeriodEnd()),
				currentIcs.isEmpty() ? "(current)" : String.join("; ", currentIcs) + " (current)"});
	}

	/**
	 * Creates a personnel card from a name entered in the shared context.
	 *
	 * @param proposedName suggested person name.
	 * @return selected personnel name, or blank when creation is cancelled.
	 */
	private String createCurrentUserPersonnelCard(String proposedName) {
		var card = controller.createPersonnelCardViaDialog(proposedName, "", "");
		if (card == null) {
			return "";
		}
		return card.getPersonName();
	}

	/**
	 * Applies field values to the model.
	 */
	public void pushToModel() {
		IncidentContext context = controller.getData().getIncidentContext();
		context.setIncidentName(incidentNameField.getText().trim());
		context.setOperationalPeriodStart(AppController.toLocalDateTime((Date) startField.getValue()));
		context.setOperationalPeriodEnd(AppController.toLocalDateTime((Date) endField.getValue()));
		context.setTaskMap(taskMapField.getText().trim());
		context.setCurrentUser(currentUserField.getText().trim());
		Object posVal = currentUserPositionCombo.getSelectedItem();
		context.setCurrentUserPositionTitle(posVal == null ? "" : posVal.toString().trim());
		saveIncidentLocations();
	}

	/**
	 * Adds a quick-create button for a common incident location type.
	 *
	 * @param controls location action bar.
	 * @param label button label.
	 * @param type location category.
	 */
	private void addLocationButton(JPanel controls, String label, String type) {
		JButton button = new JButton(label);
		button.addActionListener(event -> locationsModel.addRow(new Object[]{type, "", "", ""}));
		controls.add(button);
	}

	/**
	 * Rebuilds the editable location table from the active incident.
	 *
	 * @param locations incident-owned locations.
	 */
	private void loadIncidentLocations(List<IncidentLocation> locations) {
		loadingLocations = true;
		try {
			locationsModel.setRowCount(0);
			for (IncidentLocation location : locations) {
				if (location != null) {
					locationsModel.addRow(new Object[]{location.getType(), location.getName(), location.getAddress(),
							location.getId()});
				}
			}
		} finally {
			loadingLocations = false;
		}
	}

	/**
	 * Applies edited table rows to the active incident while retaining location identifiers.
	 */
	private void saveIncidentLocations() {
		savingLocations = true;
		try {
			UiSupport.commitTableEdits(locationsTable);
			List<IncidentLocation> locations = new ArrayList<>();
			for (int row = 0; row < locationsModel.getRowCount(); row++) {
				IncidentLocation location = new IncidentLocation();
				location.setType(String.valueOf(locationsModel.getValueAt(row, 0)));
				location.setName(String.valueOf(locationsModel.getValueAt(row, 1)));
				location.setAddress(String.valueOf(locationsModel.getValueAt(row, 2)));
				location.setId(String.valueOf(locationsModel.getValueAt(row, 3)));
				locations.add(location);
			}
			String selectedBaseId = controller.getData().getCanineRoutingBaseLocationId();
			if (!selectedBaseId.isBlank() && locations.stream().noneMatch(location ->
					selectedBaseId.equals(location.getId()))) {
				controller.getData().setCanineRoutingBaseLocationId("");
			}
			controller.getData().setIncidentLocations(locations);
		} finally {
			savingLocations = false;
		}
	}

	/**
	 * Converts an incident-local date/time to the Swing spinner representation.
	 *
	 * @param value incident date/time.
	 * @return matching date value.
	 */
	private Date toDate(java.time.LocalDateTime value) {
		return AppController.toDate(value);
	}

	/**
	 * Formats an operational-period date/time for the history table.
	 *
	 * @param value incident date/time.
	 * @return formatted timestamp or blank when absent.
	 */
	private String formatDateTime(LocalDateTime value) {
		return value == null ? "" : value.format(DT_FMT);
	}

	/**
	 * Returns an operator-facing label for the current IAP phase.
	 *
	 * @param phase current incident phase.
	 * @return phase description.
	 */
	private String phaseDisplayName(org.sarmanagement.icsforms.model.IapPhase phase) {
		if (phase == null)
			return "Pre-Operational";
		return switch (phase) {
			case PRE_OP -> "Pre-Operational (planning)";
			case INITIAL_RESPONSE -> "Initial Incident Response (ICS 201 active)";
			case DURING_OP -> "Subsequent Operational Period (ICS 201 read-only)";
		};
	}

	/**
	 * Converts null strings to empty text for display.
	 *
	 * @param value
	 *            input value.
	 * @return display-safe text.
	 */
	private String nullSafe(String value) {
		return value == null ? "" : value;
	}
}
