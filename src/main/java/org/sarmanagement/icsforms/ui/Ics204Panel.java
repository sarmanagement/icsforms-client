/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.sarmanagement.icsforms.model.CommunicationEntry;
import org.sarmanagement.icsforms.model.Ics204Form;
import org.sarmanagement.icsforms.model.IncidentObjective;
import org.sarmanagement.icsforms.model.ResourceAssignment;
import org.sarmanagement.icsforms.model.SarTaskSupport;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * First-cut editor for ICS 204 assignment list data, resources, and
 * communications.
 */
public class Ics204Panel extends JPanel {
	/** Distinguishes an ordinary save from a completed editor handoff or cancel. */
	private enum AssignmentEditorOutcome {
		SAVED, NAVIGATED, CANCELLED
	}

	private final AppController controller;
	private java.util.function.Consumer<ResourceAssignment> on214Request;
	private java.util.function.Consumer<String> onEditSarTaskRequest;
	private java.util.function.Consumer<String> onChangeSarTaskStatusRequest;
	private java.util.function.BiConsumer<java.awt.Component, ResourceAssignment> onAddLogEntryRequest;
	private final JComboBox<String> managementContextSelector = new JComboBox<>(
			new String[]{"Incident", "Branch", "Division", "Group"});
	private final JLabel selectedContextLabel = new JLabel("Staging area name");
	private final JTextField selectedContextValueField = UiSupport.textField();
	private final JTextField branchField = UiSupport.textField();
	private final JTextField divisionField = UiSupport.textField();
	private final JTextField groupField = UiSupport.textField();
	private final JTextField stagingAreaField = UiSupport.textField();
	private final JTextField operationsChiefNameField = UiSupport.textField();
	private final JTextField operationsChiefContactField = UiSupport.textField();
	private final JTextField branchDirectorNameField = UiSupport.textField();
	private final JTextField branchDirectorContactField = UiSupport.textField();
	private final JTextField supervisorNameField = UiSupport.textField();
	private final JTextField supervisorContactField = UiSupport.textField();
	private final JTextArea sharedAssignmentArea = UiSupport.textArea(3);
	private final JTextArea specialInstructionsArea = UiSupport.textArea(3);
	private final JTextField preparedByNameField = UiSupport.textField();
	private final JTextField preparedByPositionField = UiSupport.textField();
	private final JSpinner preparedDateTimeField = UiSupport.dateTimeSpinner();
	private final JTextField iapPageField = UiSupport.textField();
	private final JPanel managementContactsPanel = new JPanel(new GridBagLayout());
	private final ResourceTableModel resourceTableModel = new ResourceTableModel();
	private final CommunicationsTableModel communicationsTableModel = new CommunicationsTableModel();
	private final JTable communicationsTable = new JTable(communicationsTableModel);
	private final JTable resourceTable = new JTable(resourceTableModel);
	private String activeManagementContext = Ics204Form.MANAGEMENT_STAGING_AREA;
	private boolean updatingContextSelection;

	/**
	 * Creates the ICS 204 editor panel.
	 *
	 * @param controller
	 *            application controller.
	 */
	public Ics204Panel(AppController controller) {
		super(new BorderLayout(8, 8));
		this.controller = controller;
		RadioChannelPicker.install(operationsChiefContactField, controller);
		RadioChannelPicker.install(branchDirectorContactField, controller);
		RadioChannelPicker.install(supervisorContactField, controller);

		JPanel form = UiSupport.formPanel();
		selectedContextValueField.setColumns(16);
		operationsChiefNameField.setColumns(16);
		operationsChiefContactField.setColumns(12);
		branchDirectorNameField.setColumns(16);
		branchDirectorContactField.setColumns(12);
		supervisorNameField.setColumns(16);
		supervisorContactField.setColumns(12);
		preparedByNameField.setColumns(18);
		preparedByPositionField.setColumns(18);
		iapPageField.setColumns(8);
		iapPageField.setEditable(false);
		iapPageField.setToolTipText("Assigned automatically when the IAP bundle PDF is exported");
		JPanel selectedContextPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		selectedContextPanel.setOpaque(false);
		selectedContextPanel.add(selectedContextLabel);
		selectedContextPanel.add(selectedContextValueField);

		// Management level + selected context on a single row
		JPanel managementLevelRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		managementLevelRow.setOpaque(false);
		managementLevelRow.add(managementContextSelector);
		managementLevelRow.add(selectedContextPanel);

		// Prepared by (name, position, date/time) on a single row
		JPanel preparedByRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		preparedByRow.setOpaque(false);
		JLabel nameLabel = new JLabel("Name:");
		JLabel posLabel = new JLabel("  Position/title:");
		JLabel dtLabel = new JLabel("  Date/time:");
		preparedByRow.add(nameLabel);
		preparedByRow.add(preparedByNameField);
		preparedByRow.add(posLabel);
		preparedByRow.add(preparedByPositionField);
		preparedByRow.add(dtLabel);
		preparedByRow.add(preparedDateTimeField);

		form.setBorder(BorderFactory.createTitledBorder("ICS 204 Assignment Context"));
		UiSupport.addRow(form, 0, "Management level / context", managementLevelRow);
		UiSupport.addRow(form, 1, "Management contacts", managementContactsPanel);
		UiSupport.addRow(form, 2, "Shared work assignment", new JScrollPane(sharedAssignmentArea));
		UiSupport.addRow(form, 3, "Special instructions", new JScrollPane(specialInstructionsArea));
		UiSupport.addRow(form, 4, "Prepared by", preparedByRow);
		UiSupport.addRow(form, 5, "IAP page (auto)", iapPageField);
		preparedByNameField.setEditable(false);
		preparedByPositionField.setEditable(false);
		managementContextSelector.addActionListener(event -> {
			if (updatingContextSelection) {
				return;
			}
			storeSelectedContextValue(activeManagementContext);
			activeManagementContext = selectedManagementContext();
			updateSelectedContextLabel();
			loadSelectedContextValue(activeManagementContext);
			rebuildManagementContacts();
		});
		updateSelectedContextLabel();
		rebuildManagementContacts();

		RadioChannelPicker.install(communicationsTable, 2, controller,
				row -> communicationsTableModel.getRows().get(row).getRadioChannelId(),
				(row, id) -> communicationsTableModel.getRows().get(row).setRadioChannelId(id));
		communicationsTable.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
		resourceTable.setFillsViewportHeight(true);
		communicationsTable.setFillsViewportHeight(true);
		installResourceRowEditor();

		JPanel resourcesPanel = new JPanel(new BorderLayout());
		resourcesPanel.setBorder(BorderFactory.createTitledBorder("Resources Assigned"));
		resourcesPanel.add(new JScrollPane(resourceTable), BorderLayout.CENTER);
		resourcesPanel.add(resourcesButtonsPanel(), BorderLayout.SOUTH);

		JPanel communicationsPanel = new JPanel(new BorderLayout());
		communicationsPanel.setBorder(BorderFactory.createTitledBorder("Communications"));
		communicationsPanel.add(new JScrollPane(communicationsTable), BorderLayout.CENTER);
		JPanel commButtons = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 4, 2));
		JButton addCommRowBtn = new JButton("Add Row");
		JButton addStaffBtn = new JButton("Add Staff…");
		JButton removeCommBtn = new JButton("Remove");
		JButton planChannelBtn = new JButton("Add plan channel…");
		planChannelBtn.addActionListener(event -> {
			org.sarmanagement.icsforms.model.RadioChannel channel = RadioChannelPicker.choose(this, controller);
			if (channel != null) {
				addPlanChannel(channel);
			}
		});
		addCommRowBtn.addActionListener(e -> communicationsTableModel.addRow());
		addStaffBtn.addActionListener(e -> addStaffToComms());
		removeCommBtn.addActionListener(e -> removeSelectedCommunicationRow(communicationsTable));
		commButtons.add(addCommRowBtn);
		commButtons.add(addStaffBtn);
		commButtons.add(planChannelBtn);
		commButtons.add(removeCommBtn);
		communicationsPanel.add(commButtons, BorderLayout.SOUTH);

		JPanel tablesPanel = new JPanel(new GridLayout(2, 1, 8, 8));
		tablesPanel.add(resourcesPanel);
		tablesPanel.add(communicationsPanel);

		JScrollPane formScrollPane = new JScrollPane(form);
		formScrollPane.setBorder(BorderFactory.createEmptyBorder());
		formScrollPane.setPreferredSize(new Dimension(0, 260));
		add(formScrollPane, BorderLayout.NORTH);
		add(tablesPanel, BorderLayout.CENTER);
	}

	/**
	 * Appends an explicitly selected communications suggestion without overwriting existing rows.
	 *
	 * @param channel selected ICS 205 channel.
	 */
	void addPlanChannel(org.sarmanagement.icsforms.model.RadioChannel channel) {
		if (communicationsTable.isEditing()) {
			communicationsTable.getCellEditor().stopCellEditing();
		}
		CommunicationEntry entry = new CommunicationEntry();
		entry.setName(RadioChannelPicker.displayName(channel));
		entry.setFunction(channel.getFunction());
		entry.setPrimaryContact(RadioChannelPicker.displayName(channel));
		entry.setRadioChannelId(channel.getChannelId());
		communicationsTableModel.addEntry(entry);
		pushToModel();
		controller.markDirty();
	}

	/**
	 * Sets the callback invoked when the operator requests to add or open an ICS
	 * 214 log for a resource.
	 *
	 * @param handler
	 *            callback receiving the selected {@link ResourceAssignment}.
	 */
	public void setOn214Request(java.util.function.Consumer<ResourceAssignment> handler) {
		this.on214Request = handler;
	}

	public void setOnEditSarTaskRequest(java.util.function.Consumer<String> handler) {
		this.onEditSarTaskRequest = handler;
	}

	public void setOnChangeSarTaskStatusRequest(java.util.function.Consumer<String> handler) {
		this.onChangeSarTaskStatusRequest = handler;
	}

	/**
	 * Opens the "Create assignment" dialog (same editor used for editing existing
	 * assignments), adds the new record to the ICS 204 form if the user confirms,
	 * and returns it.
	 *
	 * <p>
	 * Call this from the SAR Tasks panel so that a new SAR task always has a
	 * backing ICS 204 resource assignment created first.
	 * </p>
	 *
	 * @return the saved assignment, or {@code null} if cancelled or already handed
	 *         off to another editor.
	 */
	public ResourceAssignment openNewAssignmentEditor() {
		return openNewAssignmentEditorForObjective("");
	}

	/**
	 * Opens a normal new-assignment editor with one objective preselected.
	 *
	 * @param objectiveId stable objective identifier, or blank for no preselection.
	 * @return saved assignment, or null when cancelled or handed off.
	 */
	public ResourceAssignment openNewAssignmentEditorForObjective(String objectiveId) {
		ResourceAssignment row = new ResourceAssignment();
		if (objectiveId != null && !objectiveId.isBlank()) {
			row.setObjectiveIds(List.of(objectiveId));
		}
		ResourceAssignmentEditor editor = new ResourceAssignmentEditor(row, controller);
		JPanel content = resourceAssignmentEditorDialogContent(row, editor);
		if (confirmResourceAssignmentDialog("Create ICS 204 Assignment", row, editor, content,
				new Dimension(920, 560)) != AssignmentEditorOutcome.SAVED) {
			return null;
		}
		return row;
	}

	/**
	 * Installs contextual log entry creation for assignment editors.
	 *
	 * @param handler callback receiving the launching editor and assignment.
	 */
	public void setOnAddLogEntryRequest(
			java.util.function.BiConsumer<java.awt.Component, ResourceAssignment> handler) {
		onAddLogEntryRequest = handler;
	}

	/** Loads values from the model. */
	public void refreshFromModel() {
		Ics204Form form = controller.getData().getForm204();
		branchField.setText(nullSafe(form.getBranch()));
		divisionField.setText(nullSafe(form.getDivision()));
		groupField.setText(nullSafe(form.getGroup()));
		stagingAreaField.setText(nullSafe(form.getStagingArea()));
		activeManagementContext = form.getManagementContext();
		setSelectedManagementContext(activeManagementContext);
		updateSelectedContextLabel();
		loadSelectedContextValue(activeManagementContext);
		operationsChiefNameField.setText(nullSafe(form.getOperationsSectionChiefName()));
		operationsChiefContactField.setText(nullSafe(form.getOperationsSectionChiefContact()));
		branchDirectorNameField.setText(nullSafe(form.getBranchDirectorName()));
		branchDirectorContactField.setText(nullSafe(form.getBranchDirectorContact()));
		supervisorNameField.setText(nullSafe(form.getDivisionGroupSupervisorName()));
		supervisorContactField.setText(nullSafe(form.getDivisionGroupSupervisorContact()));
		sharedAssignmentArea.setText(nullSafe(form.getSharedWorkAssignment()));
		specialInstructionsArea.setText(nullSafe(form.getSpecialInstructions()));
		preparedByNameField.setText(nullSafe(form.getPreparedByName()));
		preparedByPositionField.setText(nullSafe(form.getPreparedByPositionTitle()));
		preparedDateTimeField.setValue(AppController.toDate(form.getPreparedDateTime()));
		iapPageField.setText(nullSafe(form.getIapPage()));
		rebuildManagementContacts();
		resourceTableModel.setRows(form.getResourcesAssigned());
		communicationsTableModel.setRows(form.getCommunications());
	}

	/** Applies field values to the model. */
	public void pushToModel() {
		UiSupport.commitTableEdits(communicationsTable);
		Ics204Form form = controller.getData().getForm204();
		storeSelectedContextValue(activeManagementContext);
		form.setManagementContext(activeManagementContext);
		form.setBranch("");
		form.setDivision("");
		form.setGroup("");
		form.setStagingArea("");
		String selectedContextValue = selectedContextValueField.getText().trim();
		if (Ics204Form.MANAGEMENT_BRANCH.equals(activeManagementContext)) {
			form.setBranch(selectedContextValue);
		} else if (Ics204Form.MANAGEMENT_DIVISION.equals(activeManagementContext)) {
			form.setDivision(selectedContextValue);
		} else if (Ics204Form.MANAGEMENT_GROUP.equals(activeManagementContext)) {
			form.setGroup(selectedContextValue);
		} else {
			form.setStagingArea(selectedContextValue);
		}
		form.setOperationsSectionChiefName(operationsChiefNameField.getText().trim());
		form.setOperationsSectionChiefContact(operationsChiefContactField.getText().trim());
		form.setBranchDirectorName(branchDirectorNameField.getText().trim());
		form.setBranchDirectorContact(branchDirectorContactField.getText().trim());
		form.setDivisionGroupSupervisorName(supervisorNameField.getText().trim());
		form.setDivisionGroupSupervisorContact(supervisorContactField.getText().trim());
		form.setSharedWorkAssignment(sharedAssignmentArea.getText().trim());
		form.setSpecialInstructions(specialInstructionsArea.getText().trim());
		form.setPreparedByName(preparedByNameField.getText().trim());
		form.setPreparedByPositionTitle(preparedByPositionField.getText().trim());
		form.setPreparedDateTime(AppController.toLocalDateTime((java.util.Date) preparedDateTimeField.getValue()));
		form.setIapPage(iapPageField.getText().trim());
		form.setResourcesAssigned(resourceTableModel.getRows());
		form.setCommunications(communicationsTableModel.getRows());
		rebuildManagementContacts();
	}

	private void rebuildManagementContacts() {
		managementContactsPanel.removeAll();
		int rowIndex = 0;
		for (ManagementContactRow row : visibleManagementRows()) {
			GridBagConstraints label = new GridBagConstraints();
			label.gridx = 0;
			label.gridy = rowIndex;
			label.anchor = GridBagConstraints.NORTHWEST;
			label.insets = new Insets(2, 0, 2, 8);
			managementContactsPanel.add(new JLabel(row.label()), label);

			GridBagConstraints name = new GridBagConstraints();
			name.gridx = 1;
			name.gridy = rowIndex;
			name.weightx = 1.0;
			name.fill = GridBagConstraints.HORIZONTAL;
			name.insets = new Insets(2, 0, 2, 6);
			managementContactsPanel.add(row.nameField(), name);

			GridBagConstraints contactLabel = new GridBagConstraints();
			contactLabel.gridx = 2;
			contactLabel.gridy = rowIndex;
			contactLabel.anchor = GridBagConstraints.NORTHWEST;
			contactLabel.insets = new Insets(2, 0, 2, 6);
			managementContactsPanel.add(new JLabel("Contact"), contactLabel);

			GridBagConstraints contact = new GridBagConstraints();
			contact.gridx = 3;
			contact.gridy = rowIndex;
			contact.weightx = 0.7;
			contact.fill = GridBagConstraints.HORIZONTAL;
			contact.insets = new Insets(2, 0, 2, 0);
			managementContactsPanel.add(row.contactField(), contact);
			rowIndex++;
		}
		managementContactsPanel.revalidate();
		managementContactsPanel.repaint();
	}

	private List<ManagementContactRow> visibleManagementRows() {
		List<ManagementContactRow> rows = new ArrayList<>();
		rows.add(new ManagementContactRow("Operations section chief", operationsChiefNameField,
				operationsChiefContactField));
		if (Ics204Form.MANAGEMENT_BRANCH.equals(activeManagementContext)) {
			rows.add(new ManagementContactRow("Branch director", branchDirectorNameField, branchDirectorContactField));
		}
		if (Ics204Form.MANAGEMENT_DIVISION.equals(activeManagementContext)
				|| Ics204Form.MANAGEMENT_GROUP.equals(activeManagementContext)) {
			rows.add(new ManagementContactRow(Ics204Form.MANAGEMENT_GROUP.equals(activeManagementContext)
					? "Group supervisor"
					: "Division supervisor", supervisorNameField, supervisorContactField));
		}
		return rows;
	}

	private void updateSelectedContextLabel() {
		selectedContextLabel.setText(switch (activeManagementContext) {
			case Ics204Form.MANAGEMENT_BRANCH -> "Branch name";
			case Ics204Form.MANAGEMENT_DIVISION -> "Division name";
			case Ics204Form.MANAGEMENT_GROUP -> "Group name";
			default -> "Staging area name";
		});
	}

	private void storeSelectedContextValue(String managementContext) {
		String value = selectedContextValueField.getText().trim();
		switch (managementContext) {
			case Ics204Form.MANAGEMENT_BRANCH -> branchField.setText(value);
			case Ics204Form.MANAGEMENT_DIVISION -> divisionField.setText(value);
			case Ics204Form.MANAGEMENT_GROUP -> groupField.setText(value);
			default -> stagingAreaField.setText(value);
		}
	}

	private void loadSelectedContextValue(String managementContext) {
		selectedContextValueField.setText(switch (managementContext) {
			case Ics204Form.MANAGEMENT_BRANCH -> nullSafe(branchField.getText());
			case Ics204Form.MANAGEMENT_DIVISION -> nullSafe(divisionField.getText());
			case Ics204Form.MANAGEMENT_GROUP -> nullSafe(groupField.getText());
			default -> nullSafe(stagingAreaField.getText());
		});
	}

	private String selectedManagementContext() {
		Object selected = managementContextSelector.getSelectedItem();
		if ("Branch".equals(selected)) {
			return Ics204Form.MANAGEMENT_BRANCH;
		}
		if ("Division".equals(selected)) {
			return Ics204Form.MANAGEMENT_DIVISION;
		}
		if ("Group".equals(selected)) {
			return Ics204Form.MANAGEMENT_GROUP;
		}
		return Ics204Form.MANAGEMENT_STAGING_AREA;
	}

	private void setSelectedManagementContext(String managementContext) {
		updatingContextSelection = true;
		managementContextSelector.setSelectedItem(switch (managementContext) {
			case Ics204Form.MANAGEMENT_BRANCH -> "Branch";
			case Ics204Form.MANAGEMENT_DIVISION -> "Division";
			case Ics204Form.MANAGEMENT_GROUP -> "Group";
			default -> "Incident";
		});
		updatingContextSelection = false;
		activeManagementContext = managementContext;
	}

	/**
	 * Builds assignment creation, edit, removal and navigation controls.
	 *
	 * @return resource action controls.
	 */
	private JPanel resourcesButtonsPanel() {
		JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
		JButton add = new JButton("Add");
		JButton edit = new JButton("Edit…");
		JButton remove = new JButton("Remove");
		JButton editSarTask = new JButton("Edit linked SAR task…");
		edit.setEnabled(false);
		remove.setEnabled(false);
		editSarTask.setEnabled(false);
		resourceTable.getSelectionModel().addListSelectionListener(e -> {
			boolean selected = resourceTable.getSelectedRow() >= 0;
			edit.setEnabled(selected);
			remove.setEnabled(selected);
			editSarTask.setEnabled(selected && onEditSarTaskRequest != null);
		});
		add.addActionListener(event -> openNewAssignmentEditor());
		edit.addActionListener(event -> openSelectedResourceEditor());
		remove.addActionListener(event -> removeSelectedResourceRow());
		editSarTask.addActionListener(event -> openLinkedSarTaskForSelection());
		panel.add(add);
		panel.add(edit);
		panel.add(remove);
		panel.add(editSarTask);
		return panel;
	}

	/**
	 * Selects org-chart staff for communications using a document-scoped picker.
	 */
	private void addStaffToComms() {
		org.sarmanagement.icsforms.model.OrganizationalChart chart = controller.getData().getOrganizationalChart();
		// Collect all named staff positions as selectable entries.
		java.util.List<String[]> entries = new java.util.ArrayList<>();
		for (String ic : chart.getIncidentCommanders()) {
			if (!ic.isBlank())
				entries.add(new String[]{ic, "Incident Commander", ""});
		}
		addStaffEntry(entries, chart.getSafetyOfficerName(), "Safety Officer",
				chart.getSafetyOfficerRadio().isBlank()
						? chart.getSafetyOfficerPhone()
						: chart.getSafetyOfficerRadio());
		addStaffEntry(entries, chart.getPublicInformationOfficerName(), "PIO",
				chart.getPublicInformationOfficerRadio().isBlank()
						? chart.getPublicInformationOfficerPhone()
						: chart.getPublicInformationOfficerRadio());
		addStaffEntry(entries, chart.getLiaisonOfficerName(), "Liaison Officer",
				chart.getLiaisonOfficerRadio().isBlank()
						? chart.getLiaisonOfficerPhone()
						: chart.getLiaisonOfficerRadio());
		addStaffEntry(entries, chart.getOperationsSectionChiefName(), "Operations Section Chief",
				chart.getOperationsSectionChiefRadio().isBlank()
						? chart.getOperationsSectionChiefPhone()
						: chart.getOperationsSectionChiefRadio());
		addStaffEntry(entries, chart.getPlanningSectionChiefName(), "Planning Section Chief",
				chart.getPlanningSectionChiefRadio().isBlank()
						? chart.getPlanningSectionChiefPhone()
						: chart.getPlanningSectionChiefRadio());
		addStaffEntry(entries, chart.getLogisticsSectionChiefName(), "Logistics Section Chief",
				chart.getLogisticsSectionChiefRadio().isBlank()
						? chart.getLogisticsSectionChiefPhone()
						: chart.getLogisticsSectionChiefRadio());
		addStaffEntry(entries, chart.getFinanceAdminSectionChiefName(), "Finance/Admin Section Chief",
				chart.getFinanceAdminSectionChiefRadio().isBlank()
						? chart.getFinanceAdminSectionChiefPhone()
						: chart.getFinanceAdminSectionChiefRadio());
		addStaffEntry(entries, chart.getCommunicationsUnitLeaderName(), "Communications Unit Leader",
				chart.getCommunicationsUnitLeaderRadio().isBlank()
						? chart.getCommunicationsUnitLeaderPhone()
						: chart.getCommunicationsUnitLeaderRadio());
		addStaffEntry(entries, chart.getCommunicationsTechnicianName(), "Communications Technician",
				chart.getCommunicationsTechnicianRadio().isBlank()
						? chart.getCommunicationsTechnicianPhone()
						: chart.getCommunicationsTechnicianRadio());

		if (entries.isEmpty()) {
			UiSupport.showDocumentMessage(this, "No named staff positions found on the Org Chart.",
					"Add Staff", javax.swing.JOptionPane.INFORMATION_MESSAGE);
			return;
		}

		// Build a selection table.
		String[] colNames = {"Name", "Role", "Contact"};
		Object[][] data = entries.stream().map(e -> new Object[]{e[0], e[1], e[2]}).toArray(Object[][]::new);
		javax.swing.JTable selTable = new javax.swing.JTable(data, colNames) {
			@Override
			public boolean isCellEditable(int r, int c) {
				return false;
			}
		};
		selTable.setSelectionMode(javax.swing.ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		selTable.setFillsViewportHeight(true);
		javax.swing.JScrollPane scroll = new javax.swing.JScrollPane(selTable);
		scroll.setPreferredSize(new java.awt.Dimension(500, 180));

		if (!UiSupport.showResizableConfirmDialog(this, "Select staff to add to Communications", scroll,
				new Dimension(500, 240))) {
			return;
		}
		int[] selected = selTable.getSelectedRows();
		for (int row : selected) {
			org.sarmanagement.icsforms.model.CommunicationEntry entry = new org.sarmanagement.icsforms.model.CommunicationEntry();
			entry.setName(entries.get(row)[0]);
			entry.setFunction(entries.get(row)[1]);
			entry.setPrimaryContact(entries.get(row)[2]);
			communicationsTableModel.addEntry(entry);
		}
	}

	private static void addStaffEntry(java.util.List<String[]> list, String name, String role, String contact) {
		if (name != null && !name.isBlank()) {
			list.add(new String[]{name, role, contact == null ? "" : contact});
		}
	}

	/** Installs creation, editing and linked-navigation actions on resource rows. */
	private void installResourceRowEditor() {
		JPopupMenu menu = new JPopupMenu();
		JMenuItem addItem = new JMenuItem("Add assignment…");
		JMenuItem editItem = new JMenuItem("Edit assignment…");
		JMenuItem removeItem = new JMenuItem("Remove assignment");
		JMenuItem editSarTaskItem = new JMenuItem("Edit linked SAR task…");
		JMenuItem changeStatusItem = new JMenuItem("Change linked task status…");
		JMenuItem log214Item = new JMenuItem("Add ICS 214 Log…");
		JMenuItem addLogEntryItem = new JMenuItem("Add log entry…");
		addLogEntryItem.addActionListener(event -> addLogEntryForSelectedResource());
		addItem.addActionListener(event -> openNewAssignmentEditor());
		editItem.addActionListener(event -> openSelectedResourceEditor());
		removeItem.addActionListener(event -> removeSelectedResourceRow());
		editSarTaskItem.addActionListener(event -> openLinkedSarTaskForSelection());
		changeStatusItem.addActionListener(event -> openStatusDialogForSelectedLinkedTask());
		log214Item.addActionListener(event -> open214ForSelectedResource());
		menu.add(addItem);
		menu.add(editItem);
		menu.add(removeItem);
		menu.add(editSarTaskItem);
		menu.add(changeStatusItem);
		menu.addSeparator();
		menu.add(log214Item);
		menu.add(addLogEntryItem);
		menu.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
			@Override
			public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent event) {
				int viewRow = resourceTable.getSelectedRow();
				if (viewRow >= 0) {
					int modelRow = resourceTable.convertRowIndexToModel(viewRow);
					ResourceAssignment ra = resourceTableModel.getRows().get(modelRow);
					boolean exists = controller.getData().getActivityLogs().stream()
							.anyMatch(f -> ra.getAssignmentId().equals(f.getLinkedSarTaskAssignmentId()));
					log214Item.setText(exists ? "Open ICS 214 Log" : "Add ICS 214 Log…");
				}
				editSarTaskItem.setVisible(onEditSarTaskRequest != null);
				changeStatusItem.setVisible(onChangeSarTaskStatusRequest != null);
				log214Item.setVisible(on214Request != null);
				addLogEntryItem.setEnabled(onAddLogEntryRequest != null && viewRow >= 0);
				changeStatusItem.setEnabled(canChangeSelectedLinkedTaskStatus());
			}
			@Override
			public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent event) {
			}
			@Override
			public void popupMenuCanceled(javax.swing.event.PopupMenuEvent event) {
			}
		});
		resourceTable.setComponentPopupMenu(menu);
		resourceTable.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent event) {
				selectResourceRow(event);
			}

			@Override
			public void mouseReleased(MouseEvent event) {
				if (event.isPopupTrigger()) {
					selectResourceRow(event);
				}
			}

			@Override
			public void mouseClicked(MouseEvent event) {
				if (event.getClickCount() == 2 && event.getButton() == MouseEvent.BUTTON1) {
					selectResourceRow(event);
					openSelectedResourceEditor();
				}
			}
		});
	}

	/** Launches contextual logging for the selected resource, respecting sorting. */
	private void addLogEntryForSelectedResource() {
		int viewRow = resourceTable.getSelectedRow();
		if (onAddLogEntryRequest != null && viewRow >= 0) {
			ResourceAssignment assignment = resourceTableModel.getRows()
					.get(resourceTable.convertRowIndexToModel(viewRow));
			onAddLogEntryRequest.accept(resourceTable, assignment);
		}
	}

	private void selectResourceRow(MouseEvent event) {
		int viewRow = resourceTable.rowAtPoint(event.getPoint());
		if (viewRow >= 0) {
			resourceTable.setRowSelectionInterval(viewRow, viewRow);
		}
	}

	/** Opens the selected resource with save-before-navigation semantics. */
	private void openSelectedResourceEditor() {
		int viewRow = resourceTable.getSelectedRow();
		if (viewRow < 0) {
			return;
		}
		int modelRow = resourceTable.convertRowIndexToModel(viewRow);
		ResourceAssignment row = resourceTableModel.getRows().get(modelRow);
		ResourceAssignmentEditor editor = new ResourceAssignmentEditor(row, controller);
		JPanel content = resourceAssignmentEditorDialogContent(row, editor);
		if (confirmResourceAssignmentDialog(editor.dialogTitle(), row, editor, content, new Dimension(920, 560))
				== AssignmentEditorOutcome.CANCELLED) {
			return;
		}
		resourceTableModel.fireTableRowsUpdated(modelRow, modelRow);
	}

	private JPanel resourceAssignmentEditorDialogContent(ResourceAssignment assignment,
			ResourceAssignmentEditor editor) {
		JScrollPane scrollPane = new JScrollPane(editor.panel);
		scrollPane.setBorder(BorderFactory.createEmptyBorder());
		JPanel content = new JPanel(new BorderLayout(0, 6));
		content.setOpaque(false);
		content.add(scrollPane, BorderLayout.CENTER);
		return content;
	}

	/**
	 * Keeps entered fields available until validation and persistence succeed.
	 *
	 * @param title dialog title.
	 * @param assignment assignment being edited or created.
	 * @param editor field editor.
	 * @param content dialog content.
	 * @param preferredSize dialog size.
	 * @return explicit save, completed navigation, or cancellation outcome.
	 */
	private AssignmentEditorOutcome confirmResourceAssignmentDialog(String title, ResourceAssignment assignment,
			ResourceAssignmentEditor editor, JPanel content,
			Dimension preferredSize) {
		boolean existing = controller.getAssignmentResources().stream()
				.anyMatch(row -> assignment.getAssignmentId().equals(row.getAssignmentId()));
		while (true) {
			java.util.List<Object> options = new ArrayList<>();
			if (onEditSarTaskRequest != null && assignment.getAssignmentId() != null
					&& !assignment.getAssignmentId().isBlank()) {
				options.add("Save and open linked SAR task…");
			}
			if (!existing && onAddLogEntryRequest != null) {
				options.add("Save assignment and add log entry…");
			} else if (existing && onAddLogEntryRequest != null) {
				JButton logButton = new JButton("Add log entry…");
				logButton.addActionListener(event -> onAddLogEntryRequest.accept(content, assignment));
				options.add(logButton);
			}
			int okIndex = options.size();
			options.add("Save");
			int cancelIndex = options.size();
			options.add("Cancel");
			int choice = UiSupport.showResizableOptionDialog(this, title, content, preferredSize, options.toArray(),
					"Save");
			if (choice < 0 || choice == cancelIndex) {
				return AssignmentEditorOutcome.CANCELLED;
			}
			try {
				saveAssignmentEditor(assignment, editor);
			} catch (RuntimeException exception) {
				UiSupport.showDocumentMessage(content, exception.getMessage(), "Unable to save assignment",
						JOptionPane.ERROR_MESSAGE);
				continue;
			}
			if (choice == okIndex) {
				return AssignmentEditorOutcome.SAVED;
			}
			if ("Save assignment and add log entry…".equals(options.get(choice))) {
				onAddLogEntryRequest.accept(content, assignment);
				return AssignmentEditorOutcome.NAVIGATED;
			}
			if ("Save and open linked SAR task…".equals(options.get(choice))) {
				onEditSarTaskRequest.accept(assignment.getAssignmentId());
				return AssignmentEditorOutcome.NAVIGATED;
			}
		}
	}

	/**
	 * Validates, inserts once by identity, synchronizes and persists an assignment.
	 *
	 * @param assignment backing assignment.
	 * @param editor field values to save.
	 * @throws IllegalArgumentException if the assignment number is missing.
	 */
	private void saveAssignmentEditor(ResourceAssignment assignment, ResourceAssignmentEditor editor) {
		if (editor.assignmentTeamNumberField.getText().isBlank()) {
			throw new IllegalArgumentException("Assignment/Team # is required.");
		}
		try {
			controller.persistEditorChanges(() -> {
				editor.applyTo(assignment);
				if (controller.getAssignmentResources().stream()
						.noneMatch(row -> assignment.getAssignmentId().equals(row.getAssignmentId()))) {
					resourceTableModel.setRows(new ArrayList<>(controller.getData().getForm204().getResourcesAssigned()));
					resourceTableModel.addRow(assignment);
					controller.getData().getForm204().setResourcesAssigned(resourceTableModel.getRows());
				}
				controller.syncSarTasks();
				for (org.sarmanagement.icsforms.model.SarTaskAssignment task : controller.getData().getSarTaskAssignments()) {
					if (assignment.getAssignmentId().equals(task.getAssignmentId())) {
						if (!java.util.Objects.equals(editor.initialAssignment, assignment.getAssignment())) {
							task.setAssignment(assignment.getAssignment());
						}
						if (!java.util.Objects.equals(editor.initialEquipment, assignment.getSpecialEquipment())) {
							task.setSpecialEquipment(assignment.getSpecialEquipment());
						}
					}
				}
			});
		} catch (RuntimeException exception) {
			resourceTableModel.setRows(controller.getData().getForm204().getResourcesAssigned());
			throw exception;
		}
	}

	private void open214ForSelectedResource() {
		if (on214Request == null) {
			return;
		}
		int viewRow = resourceTable.getSelectedRow();
		if (viewRow < 0) {
			return;
		}
		int modelRow = resourceTable.convertRowIndexToModel(viewRow);
		ResourceAssignment row = resourceTableModel.getRows().get(modelRow);
		on214Request.accept(row);
	}

	/**
	 * Opens an assignment in any primary or additional form by stable identity.
	 *
	 * @param assignmentId stable assignment identity.
	 * @return whether an assignment was found.
	 */
	public boolean openEditorForAssignmentId(String assignmentId) {
		if (assignmentId == null || assignmentId.isBlank()) {
			return false;
		}
		for (int i = 0; i < resourceTableModel.getRows().size(); i++) {
			ResourceAssignment row = resourceTableModel.getRows().get(i);
			if (assignmentId.equals(row.getAssignmentId())) {
				int viewRow = resourceTable.convertRowIndexToView(i);
				if (viewRow >= 0) {
					resourceTable.setRowSelectionInterval(viewRow, viewRow);
				}
				openSelectedResourceEditor();
				return true;
			}
		}
		for (ResourceAssignment row : controller.getAssignmentResources()) {
			if (assignmentId.equals(row.getAssignmentId())) {
				ResourceAssignmentEditor editor = new ResourceAssignmentEditor(row, controller);
				confirmResourceAssignmentDialog(editor.dialogTitle(), row, editor,
						resourceAssignmentEditorDialogContent(row, editor), new Dimension(920, 560));
				return true;
			}
		}
		return false;
	}

	/** Opens the selected resource's linked task without application-modal feedback. */
	private void openLinkedSarTaskForSelection() {
		if (onEditSarTaskRequest == null) {
			return;
		}
		int viewRow = resourceTable.getSelectedRow();
		if (viewRow < 0) {
			return;
		}
		int modelRow = resourceTable.convertRowIndexToModel(viewRow);
		ResourceAssignment row = resourceTableModel.getRows().get(modelRow);
		if (row.getAssignmentId() == null || row.getAssignmentId().isBlank()) {
			UiSupport.showDocumentMessage(this, "Selected assignment has no assignment ID to link to a SAR task.",
					"Open SAR Task", JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		onEditSarTaskRequest.accept(row.getAssignmentId());
	}

	private void openStatusDialogForSelectedLinkedTask() {
		if (onChangeSarTaskStatusRequest == null) {
			return;
		}
		int viewRow = resourceTable.getSelectedRow();
		if (viewRow < 0) {
			return;
		}
		int modelRow = resourceTable.convertRowIndexToModel(viewRow);
		ResourceAssignment row = resourceTableModel.getRows().get(modelRow);
		if (row.getAssignmentId() == null || row.getAssignmentId().isBlank()) {
			return;
		}
		String linkedStatus = linkedTaskLifecycleStatus(row.getAssignmentId());
		if (!isPlannedOrAssigned(linkedStatus)) {
			return;
		}
		onChangeSarTaskStatusRequest.accept(row.getAssignmentId());
	}

	private boolean canChangeSelectedLinkedTaskStatus() {
		int viewRow = resourceTable.getSelectedRow();
		if (viewRow < 0) {
			return false;
		}
		ResourceAssignment row = resourceTableModel.getRows().get(resourceTable.convertRowIndexToModel(viewRow));
		if (row.getAssignmentId() == null || row.getAssignmentId().isBlank()) {
			return false;
		}
		return isPlannedOrAssigned(linkedTaskLifecycleStatus(row.getAssignmentId()));
	}

	private String linkedTaskLifecycleStatus(String assignmentId) {
		if (assignmentId == null || assignmentId.isBlank()) {
			return "";
		}
		return controller.getData().getSarTaskAssignments().stream()
				.filter(task -> assignmentId.equals(task.getAssignmentId()))
				.map(task -> task.getTaskLifecycleStatus() == null ? "" : task.getTaskLifecycleStatus()).findFirst()
				.orElse("");
	}

	private static boolean isPlannedOrAssigned(String lifecycle) {
		if (lifecycle == null) {
			return false;
		}
		String value = lifecycle.trim().toLowerCase(java.util.Locale.ROOT);
		return "planned".equals(value) || value.startsWith("assigned -");
	}

	/** Removes the selected assignment after document-scoped confirmation. */
	private void removeSelectedResourceRow() {
		int viewRow = resourceTable.getSelectedRow();
		if (viewRow < 0) {
			return;
		}
		int modelRow = resourceTable.convertRowIndexToModel(viewRow);
		ResourceAssignment row = resourceTableModel.getRows().get(modelRow);
		String label = row.getAssignmentTeamNumber().isBlank()
				? row.getResourceIdentifier()
				: row.getAssignmentTeamNumber();
		String detail = row.getLeader().isBlank() ? "" : " (Leader: " + row.getLeader() + ")";
		int confirm = UiSupport.showResizableOptionDialog(this, "Remove Assignment",
				new JLabel("Remove assignment '" + (label.isBlank() ? ("row " + (viewRow + 1)) : label)
						+ "'" + detail + "?"),
				null, new Object[]{"Remove", "Cancel"}, "Cancel");
		if (confirm == 0) {
			resourceTableModel.removeRow(modelRow);
			controller.markDirty();
		}
	}

	/**
	 * Removes a communication row after document-scoped confirmation.
	 *
	 * @param communicationsTable table containing the selected communication row.
	 */
	private void removeSelectedCommunicationRow(JTable communicationsTable) {
		int row = communicationsTable.getSelectedRow();
		if (row < 0) {
			return;
		}
		CommunicationEntry entry = communicationsTableModel.getRows().get(row);
		String who = entry.getName().isBlank() ? entry.getFunction() : entry.getName();
		String contact = entry.getPrimaryContact() == null ? "" : entry.getPrimaryContact().trim();
		int confirm = UiSupport.showResizableOptionDialog(this, "Remove Communication Entry",
				new JLabel("Remove communication entry for '" + (who.isBlank() ? ("row " + (row + 1)) : who)
						+ "'" + (contact.isBlank() ? "" : " (" + contact + ")") + "?"),
				null, new Object[]{"Remove", "Cancel"}, "Cancel");
		if (confirm == 0) {
			communicationsTableModel.removeRow(row);
			controller.markDirty();
		}
	}

	private static JPanel inlineFieldPanel(LabeledComponent... components) {
		JPanel panel = new JPanel(new GridLayout(1, components.length, 8, 0));
		panel.setOpaque(false);
		for (LabeledComponent component : components) {
			JPanel cell = new JPanel(new BorderLayout(0, 2));
			cell.setOpaque(false);
			cell.add(new JLabel(component.label()), BorderLayout.NORTH);
			cell.add(component.component(), BorderLayout.CENTER);
			panel.add(cell);
		}
		return panel;
	}

	private static JScrollPane textArea(String value, int rows) {
		JTextArea area = UiSupport.textArea(rows);
		area.setText(value == null ? "" : value);
		return new JScrollPane(area);
	}

	private static JTextArea textAreaFrom(JScrollPane scrollPane) {
		return (JTextArea) scrollPane.getViewport().getView();
	}

	private static int parseInt(String value) {
		try {
			return Integer.parseInt(value.trim());
		} catch (Exception ex) {
			return 0;
		}
	}

	/**
	 * Converts null strings to empty strings.
	 *
	 * @param value
	 *            input text.
	 * @return safe text.
	 */
	private String nullSafe(String value) {
		return value == null ? "" : value;
	}

	private record ManagementContactRow(String label, JTextField nameField, JTextField contactField) {
	}

	private record LabeledComponent(String label, java.awt.Component component) {
	}

	private static class ResourceAssignmentEditor {
		private final JPanel panel = UiSupport.formPanel();
		private final JTextField assignmentTeamNumberField = UiSupport.textField();
		private final JComboBox<String> resourceTypeField = new JComboBox<>(
				SarTaskSupport.resourceTypes().toArray(String[]::new));
		private final JComboBox<String> taskTypeField = new JComboBox<>(
				SarTaskSupport.taskTypes().toArray(String[]::new));
		private final JTextField resourceField = UiSupport.textField();
		private final JTextField leaderRoleField = UiSupport.textField();
		private final JTextField leaderField = UiSupport.textField();
		private final JTextField personsField = UiSupport.textField();
		private final JTextField contactField = UiSupport.textField();
		private final JTextField reportingField = UiSupport.textField();
		private final JScrollPane equipmentField;
		private final JScrollPane suppliesField;
		private final JScrollPane remarksField;
		private final JScrollPane notesField;
		private final JScrollPane assignmentField;
		private final JList<IncidentObjective> objectivesField;
		private final String initialAssignment;
		private final String initialEquipment;

		/**
		 * Creates assignment fields and remembers shared-text values for merging.
		 *
		 * @param row backing resource assignment.
		 * @param controller resource lookup controller.
		 */
		private ResourceAssignmentEditor(ResourceAssignment row, AppController controller) {
			initialAssignment = row.getAssignment();
			initialEquipment = row.getSpecialEquipment();
			resourceTypeField.setEditable(true);
			taskTypeField.setEditable(true);
			UiSupport.configureDialogComboBox(resourceTypeField, 170);
			UiSupport.configureDialogComboBox(taskTypeField, 170);
			assignmentTeamNumberField.setText(row.getAssignmentTeamNumber());
			resourceTypeField.setSelectedItem(row.getResourceType());
			taskTypeField.setSelectedItem(row.getTaskType());
			resourceField.setText(row.getResourceIdentifier());
			leaderRoleField.setText(row.getLeaderRole());
			leaderField.setText(row.getLeader());
			personsField.setColumns(4);
			personsField.setText(row.getNumberOfPersons() <= 0 ? "" : String.valueOf(row.getNumberOfPersons()));
			contactField.setText(row.getContact());
			RadioChannelPicker.install(contactField, controller);
			contactField.setToolTipText("Radio channel or phone number for contacting the team leader");
			// Auto-fill contact from the leader's T-card radio/phone when the field is
			// blank on open.
			if (contactField.getText().isBlank() && !leaderField.getText().isBlank()) {
				var card = controller.findPersonCard(leaderField.getText());
				if (card != null) {
					String autoContact = card.getRadioChannel().isBlank()
							? card.getPhoneNumber()
							: card.getRadioChannel();
					if (!autoContact.isBlank()) {
						contactField.setText(autoContact);
					}
				}
			}
			reportingField.setText(row.getReportingLocation());
			equipmentField = textArea(row.getSpecialEquipment(), 2);
			suppliesField = textArea(row.getSupplies(), 2);
			remarksField = textArea(row.getRemarks(), 2);
			notesField = textArea(row.getNotes(), 2);
			assignmentField = textArea(row.getAssignment(), 4);
			objectivesField = new JList<>(
					controller.getData().getForm202().getStructuredObjectives().toArray(IncidentObjective[]::new));
			List<IncidentObjective> objectiveRows = controller.getData().getForm202().getStructuredObjectives();
			int[] selectedObjectives = java.util.stream.IntStream.range(0, objectiveRows.size())
					.filter(index -> row.getObjectiveIds().contains(objectiveRows.get(index).getObjectiveId())).toArray();
			objectivesField.setSelectedIndices(selectedObjectives);
			objectivesField.setVisibleRowCount(Math.min(4, Math.max(1, objectivesField.getModel().getSize())));
			objectivesField.getAccessibleContext().setAccessibleName("Linked incident objectives");

			// Autocomplete on leader field: selecting a known name auto-fills contact.
			UiSupport.installNameAutocomplete(leaderField, controller::getPersonnelNames, selectedName -> {
				var card = controller.findPersonCard(selectedName);
				if (card != null && contactField.getText().isBlank()) {
					String contact = card.getRadioChannel().isBlank() ? card.getPhoneNumber() : card.getRadioChannel();
					if (!contact.isBlank()) {
						contactField.setText(contact);
					}
				}
			}, proposedName -> {
				var card = controller.createPersonnelCardViaDialog(proposedName, "", "");
				if (card == null) {
					return "";
				}
				if (contactField.getText().isBlank()) {
					String contact = card.getRadioChannel().isBlank() ? card.getPhoneNumber() : card.getRadioChannel();
					if (!contact.isBlank()) {
						contactField.setText(contact);
					}
				}
				return card.getPersonName();
			});

			int rowIndex = 0;
			UiSupport.addRequiredRow(panel, rowIndex++, "Assignment/Team #", assignmentTeamNumberField);
			UiSupport.addRow(panel, rowIndex++, "Task setup",
					inlineFieldPanel(new LabeledComponent("Resource type", resourceTypeField),
							new LabeledComponent("Task Geometry", taskTypeField),
							new LabeledComponent("Persons", personsField)));
			UiSupport.addRow(panel, rowIndex++, "Resource",
					inlineFieldPanel(new LabeledComponent("Identifier", resourceField),
							new LabeledComponent("Radio/Phone (contact)", contactField)));
			UiSupport.addRow(panel, rowIndex++, "Leadership", inlineFieldPanel(
					new LabeledComponent("Leader role", leaderRoleField), new LabeledComponent("Leader", leaderField)));
			UiSupport.addRow(panel, rowIndex++, "Reporting location", reportingField);
			UiSupport.addRow(panel, rowIndex++, "Objectives (zero or more)", new JScrollPane(objectivesField));
			UiSupport.addRow(panel, rowIndex++, "Special equipment", equipmentField);
			UiSupport.addRow(panel, rowIndex++, "Supplies", suppliesField);
			UiSupport.addRow(panel, rowIndex++, "Remarks", remarksField);
			UiSupport.addRow(panel, rowIndex++, "Notes", notesField);
			UiSupport.addRow(panel, rowIndex, "Assignment", assignmentField);
		}

		private String dialogTitle() {
			String teamNumber = assignmentTeamNumberField.getText().trim();
			return teamNumber.isBlank() ? "Edit assignment" : "Edit assignment " + teamNumber;
		}

		/**
		 * Applies entered values to the backing assignment.
		 *
		 * @param row assignment to update.
		 */
		private void applyTo(ResourceAssignment row) {
			row.setAssignmentTeamNumber(assignmentTeamNumberField.getText().trim());
			row.setResourceType(selectedComboValue(resourceTypeField));
			row.setTaskType(selectedComboValue(taskTypeField));
			row.setResourceIdentifier(resourceField.getText().trim());
			row.setLeaderRole(leaderRoleField.getText().trim());
			row.setLeader(leaderField.getText().trim());
			row.setNumberOfPersons(parseInt(personsField.getText()));
			row.setContact(contactField.getText().trim());
			row.setReportingLocation(reportingField.getText().trim());
			row.setSpecialEquipment(textAreaFrom(equipmentField).getText().trim());
			row.setSupplies(textAreaFrom(suppliesField).getText().trim());
			row.setRemarks(textAreaFrom(remarksField).getText().trim());
			row.setNotes(textAreaFrom(notesField).getText().trim());
			row.setAssignment(textAreaFrom(assignmentField).getText().trim());
			row.setObjectiveIds(objectivesField.getSelectedValuesList().stream().map(IncidentObjective::getObjectiveId)
					.toList());
		}

		private static String selectedComboValue(JComboBox<String> comboBox) {
			Object selected = comboBox.getEditor().getItem();
			return selected == null ? "" : selected.toString().trim();
		}
	}

	/**
	 * Table model for editable resource assignment rows.
	 */
	private static class ResourceTableModel extends AbstractTableModel {
		private final String[] columns = {"Assignment/Team #", "Resource Type", "Task Geometry", "Resource",
				"Leader Role", "Leader", "Persons", "Contact", "Reporting", "Equipment", "Supplies", "Remarks", "Notes",
				"Assignment"};
		private java.util.List<ResourceAssignment> rows = new java.util.ArrayList<>();

		/**
		 * @param rows
		 *            replacement rows.
		 */
		void setRows(java.util.List<ResourceAssignment> rows) {
			this.rows = rows == null ? new java.util.ArrayList<>() : rows;
			fireTableDataChanged();
		}
		/** @return editable rows. */
		java.util.List<ResourceAssignment> getRows() {
			return rows;
		}
		/** Adds a blank row. */
		void addRow() {
			rows.add(new ResourceAssignment());
			fireTableRowsInserted(rows.size() - 1, rows.size() - 1);
		}
		/** Adds a specific row. */
		void addRow(ResourceAssignment assignment) {
			rows.add(assignment);
			fireTableRowsInserted(rows.size() - 1, rows.size() - 1);
		}
		/**
		 * @param row
		 *            row index to remove.
		 */
		void removeRow(int row) {
			if (row >= 0 && row < rows.size()) {
				rows.remove(row);
				fireTableRowsDeleted(row, row);
			}
		}
		@Override
		public int getRowCount() {
			return rows.size();
		}
		@Override
		public int getColumnCount() {
			return columns.length;
		}
		@Override
		public String getColumnName(int column) {
			return columns[column];
		}
		@Override
		public boolean isCellEditable(int rowIndex, int columnIndex) {
			return false;
		}
		@Override
		public Object getValueAt(int rowIndex, int columnIndex) {
			ResourceAssignment row = rows.get(rowIndex);
			return switch (columnIndex) {
				case 0 -> row.getAssignmentTeamNumber();
				case 1 -> row.getResourceType();
				case 2 -> row.getTaskType();
				case 3 -> row.getResourceIdentifier();
				case 4 -> row.getLeaderRole();
				case 5 -> row.getLeader();
				case 6 -> row.getNumberOfPersons();
				case 7 -> row.getContact();
				case 8 -> row.getReportingLocation();
				case 9 -> row.getSpecialEquipment();
				case 10 -> row.getSupplies();
				case 11 -> row.getRemarks();
				case 12 -> row.getNotes();
				default -> row.getAssignment();
			};
		}
		@Override
		public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
			ResourceAssignment row = rows.get(rowIndex);
			String value = aValue == null ? "" : aValue.toString();
			switch (columnIndex) {
				case 0 -> row.setAssignmentTeamNumber(value);
				case 1 -> row.setResourceType(value);
				case 2 -> row.setTaskType(value);
				case 3 -> row.setResourceIdentifier(value);
				case 4 -> row.setLeaderRole(value);
				case 5 -> row.setLeader(value);
				case 6 -> row.setNumberOfPersons(parseInt(value));
				case 7 -> row.setContact(value);
				case 8 -> row.setReportingLocation(value);
				case 9 -> row.setSpecialEquipment(value);
				case 10 -> row.setSupplies(value);
				case 11 -> row.setRemarks(value);
				case 12 -> row.setNotes(value);
				default -> row.setAssignment(value);
			}
			fireTableCellUpdated(rowIndex, columnIndex);
		}
		/**
		 * @param value
		 *            string value. @return parsed integer or zero.
		 */
	}

	/**
	 * Table model for communications rows.
	 */
	private static class CommunicationsTableModel extends AbstractTableModel {
		private final String[] columns = {"Name", "Function", "Primary Contact (Phone/Radio)"};
		private java.util.List<CommunicationEntry> rows = new java.util.ArrayList<>();

		/**
		 * @param rows
		 *            replacement rows.
		 */
		void setRows(java.util.List<CommunicationEntry> rows) {
			this.rows = rows == null ? new java.util.ArrayList<>() : rows;
			fireTableDataChanged();
		}
		/** @return editable rows. */
		java.util.List<CommunicationEntry> getRows() {
			return rows;
		}
		/** Adds a blank row. */
		void addRow() {
			rows.add(new CommunicationEntry());
			fireTableRowsInserted(rows.size() - 1, rows.size() - 1);
		}
		/** Adds a pre-populated entry. */
		void addEntry(CommunicationEntry entry) {
			rows.add(entry);
			fireTableRowsInserted(rows.size() - 1, rows.size() - 1);
		}
		/**
		 * @param row
		 *            row index to remove.
		 */
		void removeRow(int row) {
			if (row >= 0 && row < rows.size()) {
				rows.remove(row);
				fireTableRowsDeleted(row, row);
			}
		}
		@Override
		public int getRowCount() {
			return rows.size();
		}
		@Override
		public int getColumnCount() {
			return columns.length;
		}
		@Override
		public String getColumnName(int column) {
			return columns[column];
		}
		@Override
		public boolean isCellEditable(int rowIndex, int columnIndex) {
			return true;
		}
		@Override
		public Object getValueAt(int rowIndex, int columnIndex) {
			return switch (columnIndex) {
				case 0 -> rows.get(rowIndex).getName();
				case 1 -> rows.get(rowIndex).getFunction();
				default -> rows.get(rowIndex).getPrimaryContact();
			};
		}
		@Override
		public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
			String value = aValue == null ? "" : aValue.toString();
			if (columnIndex == 0) {
				rows.get(rowIndex).setName(value);
			} else if (columnIndex == 1) {
				rows.get(rowIndex).setFunction(value);
			} else {
				rows.get(rowIndex).setPrimaryContact(value);
			}
			fireTableCellUpdated(rowIndex, columnIndex);
		}
	}
}
