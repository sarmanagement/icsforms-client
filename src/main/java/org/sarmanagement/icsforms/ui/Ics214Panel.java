// SPDX-License-Identifier: GPL-3.0-or-later
package org.sarmanagement.icsforms.ui;

import org.sarmanagement.icsforms.model.ActivityEventType;
import org.sarmanagement.icsforms.model.ActivityLogEntry;
import org.sarmanagement.icsforms.model.ActivityLogScope;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.ClueLogEntry;
import org.sarmanagement.icsforms.model.Ics214Form;
import org.sarmanagement.icsforms.model.ResourceAssignment;
import org.sarmanagement.icsforms.model.SarTaskAssignment;
import org.sarmanagement.icsforms.model.SarTaskResource;
import org.sarmanagement.icsforms.model.SarTaskSupport;
import org.sarmanagement.icsforms.model.TCard;
import org.sarmanagement.icsforms.model.TCardType;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * First-cut editor for ICS 214 activity log data.
 *
 * <p>
 * Event types shown in the entry dialog are read from
 * {@link AppData#getActivityEventTypes()}, which can be managed via the
 * Configuration menu.
 * </p>
 */
public class Ics214Panel extends JPanel {
	private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
	private static final Map<Ics214Form, JFrame> POP_OUT_WINDOWS = new IdentityHashMap<>();

	private final AppController controller;
	private final JTextField nameField = UiSupport.textField();
	private final JTextField icsPositionField = UiSupport.textField();
	private final JTextField homeAgencyField = UiSupport.textField();
	private final JTextField preparedByNameField = UiSupport.textField();
	private final JTextField preparedByPositionField = UiSupport.textField();
	private final JSpinner preparedDateTimeField = UiSupport.dateTimeSpinner();
	private final JButton pickPreparerButton = new JButton("Pick preparer from task…");
	private final JButton pickResourceButton = new JButton("Pick…");
	private final JButton syncResourcesBtn = new JButton("Sync resources…");
	private final ResourcesTableModel resourcesTableModel = new ResourcesTableModel();
	private final ActivityLogTableModel activityLogTableModel = new ActivityLogTableModel();
	private final JTable resourcesTable = new JTable(resourcesTableModel);
	private final JTable activityLogTable = new JTable(activityLogTableModel);
	private final JScrollPane activityLogScrollPane = new JScrollPane(activityLogTable);
	private AppData currentData;
	private Ics214Form currentForm;
	private HeaderState loadedHeader;
	private List<ResourceState> loadedResources = List.of();

	/**
	 * Creates the ICS 214 editor panel.
	 *
	 * @param controller
	 *            application controller.
	 */
	public Ics214Panel(AppController controller) {
		super(new BorderLayout(8, 8));
		this.controller = controller;

		JPanel form = UiSupport.formPanel();
		form.setBorder(BorderFactory.createTitledBorder("ICS 214 Activity Log"));
		JPanel nameRow = new JPanel(new BorderLayout(4, 0));
		nameRow.add(nameField, BorderLayout.CENTER);
		pickResourceButton.setToolTipText("Link section 3 to a known resource or location");
		pickResourceButton.addActionListener(e -> pickResourceForSection3());
		nameRow.add(pickResourceButton, BorderLayout.EAST);
		UiSupport.addRow(form, 0, "Name", nameRow);
		UiSupport.addRow(form, 1, "ICS position", icsPositionField);
		UiSupport.addRow(form, 2, "Home agency", homeAgencyField);
		JPanel preparedByRow = new JPanel(new BorderLayout(4, 0));
		preparedByRow.setOpaque(false);
		preparedByRow.add(preparedByNameField, BorderLayout.CENTER);
		JButton pickPreparedByButton = new JButton("Pick…");
		pickPreparedByButton.addActionListener(e -> UiSupport.openInstalledNamePicker(preparedByNameField));
		preparedByRow.add(pickPreparedByButton, BorderLayout.EAST);
		UiSupport.addRow(form, 3, "Prepared by name", preparedByRow);
		UiSupport.addRow(form, 4, "Prepared by position/title", preparedByPositionField);
		UiSupport.addRow(form, 5, "Prepared date/time", preparedDateTimeField);

		UiSupport.installNameAutocomplete(preparedByNameField, controller::getPersonnelNames,
				selectedName -> populatePreparedByFromPersonCard(selectedName, true),
				proposedName -> createPersonnelFromPicker(proposedName, preparedByPositionField, homeAgencyField));

		pickPreparerButton.setEnabled(false);
		pickPreparerButton.addActionListener(event -> pickPreparerFromTask());
		JPanel pickPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		pickPanel.add(pickPreparerButton);

		resourcesTable.setFillsViewportHeight(true);
		activityLogTable.setFillsViewportHeight(true);
		activityLogTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
			@Override
			public java.awt.Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
					boolean hasFocus, int row, int column) {
				super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
				int modelRow = table.convertRowIndexToModel(row);
				ActivityLogEntry entry = activityLogTableModel.entryAt(modelRow);
				setText(entry != null && entry.isStruckOut() ? strikeHtml(value) : value == null ? "" : value.toString());
				return this;
			}
		});

		// Item 6: Double-click on a resource row shows its linked T-card (read-only).
		resourcesTable.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if (e.getClickCount() == 2 && javax.swing.SwingUtilities.isLeftMouseButton(e)) {
					showTCardForSelectedResource();
				}
			}
		});

		JPanel resourcesPanel = new JPanel(new BorderLayout());
		resourcesPanel.setBorder(BorderFactory.createTitledBorder("Section 6 - Resources Assigned"));
		syncResourcesBtn.addActionListener(e -> syncResources());
		JPanel resourcesButtonRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
		resourcesButtonRow.add(syncResourcesBtn);
		resourcesPanel.add(new JScrollPane(resourcesTable), BorderLayout.CENTER);
		resourcesPanel.add(resourcesButtonRow, BorderLayout.SOUTH);

		JPanel activityPanel = new JPanel(new BorderLayout());
		activityPanel.setBorder(BorderFactory.createTitledBorder("Section 7 - Activity Log"));
		activityPanel.add(activityLogScrollPane, BorderLayout.CENTER);
		activityPanel.add(activityButtonsPanel(), BorderLayout.SOUTH);
		installActivityContextMenu();

		JPanel tablesPanel = new JPanel(new GridLayout(2, 1, 8, 8));
		tablesPanel.add(resourcesPanel);
		tablesPanel.add(activityPanel);

		JPanel northPanel = new JPanel(new BorderLayout());

		JScrollPane formScrollPane = new JScrollPane(form);
		formScrollPane.setBorder(BorderFactory.createEmptyBorder());
		formScrollPane.setPreferredSize(new Dimension(0, 240));

		JButton popOutBtn = new JButton("Open in New Window");
		popOutBtn.setToolTipText("Open this ICS 214 form in a separate window so it can be used alongside other tabs");
		popOutBtn.addActionListener(e -> popOutInNewWindow());
		pickPanel.add(popOutBtn);

		northPanel.add(formScrollPane, BorderLayout.CENTER);
		northPanel.add(pickPanel, BorderLayout.SOUTH);

		add(northPanel, BorderLayout.NORTH);
		add(tablesPanel, BorderLayout.CENTER);
	}

	/** Adds row-specific details and follow-up actions to the activity table. */
	private void installActivityContextMenu() {
		javax.swing.JPopupMenu popup = new javax.swing.JPopupMenu();
		javax.swing.JMenuItem details = new javax.swing.JMenuItem("View details");
		javax.swing.JMenuItem another = new javax.swing.JMenuItem("Add another entry");
		details.addActionListener(event -> {
			ActivityLogEntry entry = selectedActivityEntry();
			if (entry != null && ActivityEntryDialog.showDetails(activityLogTable, controller, currentForm, entry)) {
				refreshActivityEntries();
			}
		});
		another.addActionListener(event -> {
			ActivityLogEntry entry = selectedActivityEntry();
			if (entry != null && new ActivityEntryDialog(controller,
					ActivityEntryDialog.followUpContext(activityLogTable, currentForm, entry), currentForm)
							.show(activityLogTable)) {
				refreshActivityEntries();
			}
		});
		popup.add(details);
		popup.add(another);
		activityLogTable.setComponentPopupMenu(popup);
		activityLogTable.addMouseListener(new MouseAdapter() {
			/** {@inheritDoc} */
			@Override
			public void mousePressed(MouseEvent event) {
				selectTarget(event);
			}

			/** {@inheritDoc} */
			@Override
			public void mouseReleased(MouseEvent event) {
				selectTarget(event);
			}

			/**
			 * Selects the clicked row, clearing stale selections outside rows.
			 *
			 * @param event initiating mouse event
			 */
			private void selectTarget(MouseEvent event) {
				int row = activityLogTable.rowAtPoint(event.getPoint());
				if (row < 0) {
					activityLogTable.clearSelection();
				} else {
					activityLogTable.setRowSelectionInterval(row, row);
				}
			}
		});
	}

	/**
	 * Resolves a sorted activity selection to its model entry.
	 *
	 * @return selected entry, or null when no row is selected
	 */
	private ActivityLogEntry selectedActivityEntry() {
		int row = activityLogTable.getSelectedRow();
		return row < 0 || currentForm == null || currentData != controller.getData() ? null
				: currentForm.getActivityLog().get(activityLogTable.convertRowIndexToModel(row));
	}

	/**
	 * Loads values from the supplied model.
	 *
	 * @param form
	 *            activity log form.
	 * @param data
	 *            source document.
	 */
	public void loadFromModel(Ics214Form form, AppData data) {
		currentForm = form;
		currentData = data == null ? new AppData() : data;
		if (currentForm == null) {
			loadedHeader = null;
			loadedResources = List.of();
			clearFields();
			pickPreparerButton.setEnabled(false);
			return;
		}
		nameField.setText(nullSafe(currentForm.getName()));
		icsPositionField.setText(nullSafe(currentForm.getIcsPosition()));
		homeAgencyField.setText(nullSafe(currentForm.getHomeAgency()));
		preparedByNameField.setText(nullSafe(currentForm.getPreparedByName()));
		preparedByPositionField.setText(nullSafe(currentForm.getPreparedByPositionTitle()));
		preparedDateTimeField.setValue(AppController.toDate(currentForm.getPreparedDateTime()));
		resourcesTableModel.setRows(copyResources(currentForm.getResourcesAssigned()));
		activityLogTableModel.setRows(currentForm.getActivityLog(), resolvedEventTypes());
		scrollActivityLogToLatestEntry();
		boolean isTask = currentForm.getLogScope() == ActivityLogScope.TASK_ASSIGNMENT;
		pickPreparerButton.setEnabled(isTask && !linkedTaskResources().isEmpty());
		if (isTask) {
			syncResourcesBtn.setText("Sync from Task Assignment…");
			syncResourcesBtn.setToolTipText(
					"Replace the resource list with the leader and resources from the linked task assignment");
		} else {
			syncResourcesBtn.setText("Refresh from ICP & 204…");
			syncResourcesBtn.setToolTipText("Add resources from T-cards at ICP and from 204 assignment forms");
		}
		loadedHeader = headerState();
		loadedResources = resourceStates(resourcesTableModel.getRows());
	}

	/**
	 * Merges locally edited fields against their loaded baselines and current
	 * canonical values. Untouched fields adopt external changes; deliberate local
	 * edits win same-field conflicts. Authoritative activity entries are never replaced.
	 */
	public void saveToModel() {
		if (currentForm == null || currentData != controller.getData() || loadedHeader == null) {
			return;
		}
		mergeTextField(nameField, loadedHeader.name(), currentForm.getName(), currentForm::setName);
		mergeTextField(icsPositionField, loadedHeader.icsPosition(), currentForm.getIcsPosition(), currentForm::setIcsPosition);
		mergeTextField(homeAgencyField, loadedHeader.homeAgency(), currentForm.getHomeAgency(), currentForm::setHomeAgency);
		mergeTextField(preparedByNameField, loadedHeader.preparedByName(), currentForm.getPreparedByName(),
				currentForm::setPreparedByName);
		mergeTextField(preparedByPositionField, loadedHeader.preparedByPosition(), currentForm.getPreparedByPositionTitle(),
				currentForm::setPreparedByPositionTitle);
		LocalDateTime localDateTime = AppController.toLocalDateTime((Date) preparedDateTimeField.getValue());
		if (!java.util.Objects.equals(localDateTime, loadedHeader.preparedDateTime())) {
			currentForm.setPreparedDateTime(localDateTime);
		} else {
			preparedDateTimeField.setValue(AppController.toDate(currentForm.getPreparedDateTime()));
		}
		if (!resourceStates(resourcesTableModel.getRows()).equals(loadedResources)) {
			currentForm.setResourcesAssigned(copyResources(resourcesTableModel.getRows()));
		} else {
			resourcesTableModel.setRows(copyResources(currentForm.getResourcesAssigned()));
		}
		loadedHeader = headerState();
		loadedResources = resourceStates(resourcesTableModel.getRows());
	}

	/**
	 * Applies a local text edit, or refreshes an untouched field from canonical state.
	 *
	 * @param field editable local field
	 * @param baseline field value last loaded or saved
	 * @param canonical currently stored field value
	 * @param apply setter for deliberate local changes
	 */
	private static void mergeTextField(JTextField field, String baseline, String canonical,
			java.util.function.Consumer<String> apply) {
		String local = field.getText().trim();
		if (!java.util.Objects.equals(local, baseline)) {
			apply.accept(local);
		} else {
			field.setText(canonical == null ? "" : canonical);
		}
	}

	/**
	 * Snapshots displayed header values without retaining mutable Swing state.
	 *
	 * @return immutable local header baseline
	 */
	private HeaderState headerState() {
		return new HeaderState(nameField.getText().trim(), icsPositionField.getText().trim(), homeAgencyField.getText().trim(),
				preparedByNameField.getText().trim(), preparedByPositionField.getText().trim(),
				AppController.toLocalDateTime((Date) preparedDateTimeField.getValue()));
	}

	/**
	 * Snapshots resource values to distinguish local edits from stale resource rows.
	 *
	 * @param resources displayed resource rows
	 * @return immutable resource baseline values
	 */
	private static List<ResourceState> resourceStates(List<SarTaskResource> resources) {
		return resources.stream().map(resource -> new ResourceState(resource.getFunction(), resource.getIcsPosition(),
				resource.getHomeAgency(), resource.getName(), resource.getCardType(), resource.getResourceId())).toList();
	}

	/**
	 * Copies resource rows so external model edits cannot become apparent local edits.
	 *
	 * @param resources source resources
	 * @return detached resource rows preserving stable identities
	 */
	private static List<SarTaskResource> copyResources(List<SarTaskResource> resources) {
		List<SarTaskResource> copies = new ArrayList<>();
		for (SarTaskResource resource : resources) {
			SarTaskResource copy = new SarTaskResource();
			copy.setFunction(resource.getFunction());
			copy.setIcsPosition(resource.getIcsPosition());
			copy.setHomeAgency(resource.getHomeAgency());
			copy.setName(resource.getName());
			copy.setCardType(resource.getCardType());
			copy.setResourceId(resource.getResourceId());
			copies.add(copy);
		}
		return copies;
	}

	/**
	 * Immutable baseline of editable header fields.
	 *
	 * @param name form name
	 * @param icsPosition ICS position
	 * @param homeAgency home agency
	 * @param preparedByName preparer name
	 * @param preparedByPosition preparer position
	 * @param preparedDateTime displayed preparation time
	 */
	private record HeaderState(String name, String icsPosition, String homeAgency, String preparedByName,
			String preparedByPosition, LocalDateTime preparedDateTime) {
	}

	/**
	 * Immutable baseline of one resource row.
	 *
	 * @param function resource function
	 * @param icsPosition resource position
	 * @param homeAgency resource agency
	 * @param name resource name
	 * @param cardType resource type
	 * @param resourceId stable resource identity
	 */
	private record ResourceState(String function, String icsPosition, String homeAgency, String name,
			TCardType cardType, String resourceId) {
	}

	/** Reloads event type labels after the event type list has been modified. */
	public void refreshEventTypes() {
		if (currentForm != null) {
			activityLogTableModel.setRows(currentForm.getActivityLog(), resolvedEventTypes());
		}
	}

	/** Returns the configured event types, falling back to defaults when empty. */
	private List<ActivityEventType> resolvedEventTypes() {
		if (currentData == null || currentData.getActivityEventTypes().isEmpty()) {
			return ActivityEventType.defaultTypes();
		}
		return currentData.getActivityEventTypes();
	}

	/** Returns the resources assigned to the linked SAR task, or an empty list. */
	private List<SarTaskResource> linkedTaskResources() {
		if (currentForm == null || currentData == null) {
			return List.of();
		}
		String taskId = currentForm.getLinkedSarTaskAssignmentId();
		if (taskId == null || taskId.isBlank()) {
			return List.of();
		}
		return currentData.getSarTaskAssignments().stream().filter(t -> taskId.equals(t.getAssignmentId())).findFirst()
				.map(SarTaskAssignment::getResourcesAssigned).orElse(List.of());
	}

	/** Chooses a linked-task preparer within the initiating document root. */
	private void pickPreparerFromTask() {
		List<SarTaskResource> resources = linkedTaskResources();
		if (resources.isEmpty()) {
			return;
		}
		SarTaskResource[] resourceArray = resources.toArray(new SarTaskResource[0]);
		JComboBox<SarTaskResource> combo = new JComboBox<>(resourceArray);
		UiSupport.configureDialogComboBox(combo, 280);
		combo.setRenderer((list, value, index, isSelected, cellHasFocus) -> {
			JLabel label = new JLabel(value == null ? "" : value.getName() + " (" + value.getIcsPosition() + ")");
			if (isSelected) {
				label.setBackground(list.getSelectionBackground());
				label.setForeground(list.getSelectionForeground());
				label.setOpaque(true);
			}
			return label;
		});
		int result = ActivityEntryDialog.showDocumentConfirm(this, combo, "Pick preparer", JOptionPane.OK_CANCEL_OPTION);
		if (result != JOptionPane.OK_OPTION) {
			return;
		}
		SarTaskResource selected = (SarTaskResource) combo.getSelectedItem();
		if (selected == null) {
			return;
		}
		preparedByNameField.setText(nullSafe(selected.getName()));
		preparedByPositionField.setText(nullSafe(selected.getIcsPosition()));
		homeAgencyField.setText(nullSafe(selected.getHomeAgency()));
		controller.markDirty();
	}

	/**
	 * Shows a picker dialog populated with all known T-card resources and links the
	 * selection to ICS 214 section 3 (Name / ICS Position / Home Agency), resolving
	 * canonical identity from the resource record.
	 */
	private void pickResourceForSection3() {
		if (currentData == null) {
			return;
		}
		List<Section3Choice> choices = section3Choices();
		if (choices.isEmpty()) {
			UiSupport.showDocumentMessage(this, "No T-card resources found.", "Pick Resource",
					JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		JComboBox<Section3Choice> combo = new JComboBox<>(choices.toArray(new Section3Choice[0]));
		UiSupport.configureDialogComboBox(combo, 360);
		combo.setRenderer((list, value, index, isSelected, cellHasFocus) -> {
			String display = value == null ? "" : value.displayLabel();
			JLabel label = new JLabel(display);
			if (isSelected) {
				label.setBackground(list.getSelectionBackground());
				label.setForeground(list.getSelectionForeground());
				label.setOpaque(true);
			}
			return label;
		});
		// Pre-select currently linked resource if any.
		if (currentForm != null && currentForm.getLinkedResourceId() != null) {
			choices.stream().filter(c -> currentForm.getLinkedResourceId().equals(c.linkedResourceId())).findFirst()
					.ifPresent(combo::setSelectedItem);
		}
		int result = ActivityEntryDialog.showDocumentConfirm(this, combo, "Link section 3 to resource/location",
				JOptionPane.OK_CANCEL_OPTION);
		if (result != JOptionPane.OK_OPTION) {
			return;
		}
		Section3Choice selected = (Section3Choice) combo.getSelectedItem();
		if (selected == null) {
			return;
		}
		if (currentForm != null) {
			currentForm.setLinkedResourceId(nullSafe(selected.linkedResourceId()));
		}
		nameField.setText(nullSafe(selected.name()));
		icsPositionField.setText(nullSafe(selected.icsPosition()));
		homeAgencyField.setText(nullSafe(selected.homeAgency()));
		controller.markDirty();
	}

	private static String displayNameForCard(TCard card) {
		if (card == null)
			return "";
		String name = card.getPersonName();
		if (name == null || name.isBlank()) {
			name = card.getResourceIdentifier();
		}
		String agency = card.getHomeAgency();
		if (agency != null && !agency.isBlank()) {
			return name + " (" + agency + ")";
		}
		return name == null ? "" : name;
	}

	/**
	 * Opens this ICS 214 form in a separate modeless window so the operator can log
	 * events while navigating other tabs. The window shares the same
	 * {@link Ics214Form} model object. One independent window is permitted per
	 * form; closing it saves only active-incident edits and refreshes activity rows
	 * without overwriting pending fields in the embedded editor.
	 */
	private void popOutInNewWindow() {
		if (currentForm == null || currentData != controller.getData()) {
			return;
		}
		JFrame existing = POP_OUT_WINDOWS.get(currentForm);
		if (existing != null && existing.isDisplayable()) {
			existing.toFront();
			existing.requestFocus();
			return;
		}
		saveToModel();
		Ics214Form form = currentForm;
		AppData data = currentData;
		String title = "ICS 214 – " + (currentForm.getName().isBlank() ? "Activity Log" : currentForm.getName());
		JFrame frame = new JFrame(title);
		frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		Ics214Panel popOut = new Ics214Panel(controller);
		popOut.loadFromModel(form, data);

		// Single Close button — saves changes and closes the window.
		JButton closeBtn = new JButton("Close");
		closeBtn.addActionListener(e -> closePopOut(frame, popOut, form, data));

		// Also save when the user dismisses with the OS window-close button.
		frame.addWindowListener(new WindowAdapter() {
			/** {@inheritDoc} */
			@Override
			public void windowClosing(WindowEvent e) {
				closePopOut(frame, popOut, form, data);
			}

			/** {@inheritDoc} */
			@Override
			public void windowClosed(WindowEvent e) {
				POP_OUT_WINDOWS.remove(form, frame);
			}
		});

		JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		btnRow.add(closeBtn);
		frame.getContentPane().setLayout(new BorderLayout());
		frame.getContentPane().add(popOut, BorderLayout.CENTER);
		frame.getContentPane().add(btnRow, BorderLayout.SOUTH);
		frame.setSize(900, 700);
		frame.setLocationByPlatform(true);
		POP_OUT_WINDOWS.put(form, frame);
		UiSupport.registerIncidentWindow(frame, data);
		frame.setVisible(true);
	}

	/**
	 * Closes a detached editor without saving into a switched incident.
	 *
	 * @param frame detached window
	 * @param popOut detached editor
	 * @param form originally edited form
	 * @param data originally edited incident
	 */
	private void closePopOut(JFrame frame, Ics214Panel popOut, Ics214Form form, AppData data) {
		if (controller.getData() == data) {
			popOut.saveToModel();
			controller.markDirty();
			if (currentData == data && currentForm == form) {
				refreshActivityEntries();
			}
		}
		frame.dispose();
	}

	/** Opens the shared entry workflow without saving other pending log edits. */
	private void addActivityEntry() {
		if (currentForm == null || currentData != controller.getData()) {
			return;
		}
		if (new ActivityEntryDialog(controller,
				new LogEntryContext(this, "", currentForm.getLinkedSarTaskAssignmentId(), ""), currentForm).show(this)) {
			refreshActivityEntries();
		}
	}

	/** Refreshes only activity rows, preserving all other pending form edits. */
	public void refreshActivityEntries() {
		if (currentForm != null) {
			activityLogTableModel.setRows(currentForm.getActivityLog(), resolvedEventTypes());
			scrollActivityLogToLatestEntry();
		}
	}

	/**
	 * Shows the clue capture dialog pre-populated from the given activity log entry
	 * and adds the result to the shared clue log.
	 *
	 * <p>
	 * Behaviour differs by log type and event type:
	 * </p>
	 * <ul>
	 * <li>Resource 214 (task-linked): detecting resource is taken from the entry;
	 * no possible-duplicate checkbox.</li>
	 * <li>Management 214 (ICP / assignment-list): detecting resource is chosen from
	 * a picklist of task-linked 214 forms; possible-duplicate checkbox is
	 * shown.</li>
	 * </ul>
	 * <p>
	 * Follow-up is not captured in this dialog; it is entered directly in the clue
	 * log grid.
	 * </p>
	 *
	 * @param sourceEntry
	 *            the activity log entry that triggered clue capture.
	 * @param eventTypeId
	 *            the event type identifier ({@code CLUE_DETECTED} or
	 *            {@code CLUE_REPORTED}).
	 * @param owner initiating editor component
	 * @param assignmentId explicitly selected task assignment
	 * @return true when clue capture was accepted; false on cancellation
	 */
	boolean captureClue(java.awt.Component owner, ActivityLogEntry sourceEntry, String eventTypeId, String assignmentId) {
		if (currentForm == null || currentData != controller.getData()) {
			return false;
		}
		boolean isResourceLog = currentForm.getLogScope() == ActivityLogScope.TASK_ASSIGNMENT;

		JPanel form = UiSupport.formPanel();
		JTextField locationField = UiSupport.textField();
		JTextArea descriptionArea = UiSupport.textArea(3);
		JTextArea immediateActionArea = UiSupport.textArea(2);

		// Detecting resource: fixed for resource 214, picklist for management 214.
		JComboBox<String> detectingResourceCombo = null;
		JLabel detectingResourceLabel = null;
		if (isResourceLog) {
			detectingResourceLabel = new JLabel(sourceEntry.getResourceIdentifier().isBlank()
					? nullSafe(currentForm.getName()) : sourceEntry.getResourceIdentifier());
		} else {
			List<String> resourceNames = taskLinkedFormNames();
			String[] items = resourceNames.isEmpty() ? new String[]{""} : resourceNames.toArray(new String[0]);
			detectingResourceCombo = new JComboBox<>(items);
			detectingResourceCombo.setEditable(true);
			detectingResourceCombo.setSelectedItem(sourceEntry.getResourceIdentifier());
		}

		// Possible duplicate: shown only for management 214.
		JCheckBox possibleDuplicateCheck = null;
		if (!isResourceLog) {
			possibleDuplicateCheck = new JCheckBox(
					"Possible duplicate (detecting resource may have already logged this clue)");
			boolean isClueReported = ActivityEventType.ID_CLUE_REPORTED.equals(eventTypeId);
			possibleDuplicateCheck.setSelected(isClueReported);
		}

		int row = 0;
		if (detectingResourceCombo != null) {
			UiSupport.addRow(form, row++, "Detecting resource", detectingResourceCombo);
		} else {
			UiSupport.addRow(form, row++, "Detecting resource", detectingResourceLabel);
		}
		UiSupport.addRow(form, row++, "Location / position", locationField);
		UiSupport.addRow(form, row++, "Description", new JScrollPane(descriptionArea));
		UiSupport.addRow(form, row++, "Immediate action taken", new JScrollPane(immediateActionArea));
		if (possibleDuplicateCheck != null) {
			UiSupport.addRow(form, row++, "", possibleDuplicateCheck);
		}

		JScrollPane scrollPane = new JScrollPane(form);
		scrollPane.setBorder(BorderFactory.createEmptyBorder());
		int dialogHeight = 310 + (possibleDuplicateCheck != null ? 30 : 0);
		if (!UiSupport.showResizableConfirmDialog(owner, "Capture clue details", scrollPane,
				new Dimension(640, dialogHeight))) {
			return false;
		}
		if (currentData != controller.getData()) {
			return false;
		}

		// Resolve detecting-task link and detected-by resource.
		// The task relationship is stored as assignmentId (the hidden link);
		// detectingTask is
		// kept for backward compatibility with serialised data that predates this
		// redesign.
		// detectedBy captures the specific person or resource that found the clue.
		String detectedBy;
		if (isResourceLog) {
			detectedBy = detectingResourceLabel.getText();
		} else if (detectingResourceCombo != null && detectingResourceCombo.getSelectedItem() != null) {
			detectedBy = detectingResourceCombo.getSelectedItem().toString();
		} else {
			detectedBy = "";
		}

		ClueLogEntry clue = new ClueLogEntry();
		clue.setDateTimeCollected(sourceEntry.getTimestamp());
		clue.setDetectedBy(detectedBy);
		clue.setLocation(locationField.getText().trim());
		clue.setDescription(descriptionArea.getText().trim());
		clue.setImmediateAction(immediateActionArea.getText().trim());
		clue.setPossibleDuplicate(possibleDuplicateCheck != null && possibleDuplicateCheck.isSelected());
		String taskId = assignmentId == null || assignmentId.isBlank()
				? currentForm.getLinkedSarTaskAssignmentId() : assignmentId;
		if (taskId != null && !taskId.isBlank()) {
			clue.setAssignmentId(taskId);
		}
		currentData.getClueLogEntries().add(clue);
		return true;
	}

	/**
	 * Returns detecting-task labels for all ICS 214 forms in the document that are
	 * linked to a SAR task assignment (resource-level 214 forms). Each label
	 * combines the task team number and the form's resource name so the
	 * management-214 clue-capture picklist shows both.
	 */
	private List<String> taskLinkedFormNames() {
		if (currentData == null) {
			return List.of();
		}
		// Build a map from assignmentId → team number for quick lookup.
		Map<String, SarTaskAssignment> taskById = new HashMap<>();
		if (currentData.getSarTaskAssignments() != null) {
			for (SarTaskAssignment t : currentData.getSarTaskAssignments()) {
				taskById.put(t.getAssignmentId(), t);
			}
		}
		return currentData.getActivityLogs().stream().filter(f -> f.getLogScope() == ActivityLogScope.TASK_ASSIGNMENT)
				.filter(f -> f.getName() != null && !f.getName().isBlank()).map(f -> {
					SarTaskAssignment task = taskById.get(f.getLinkedSarTaskAssignmentId());
					return UiSupport.detectingTaskLabel(task, f.getName());
				}).filter(label -> !label.isBlank()).distinct().collect(Collectors.toList());
	}

	/**
	 * Confirms strike-out within the initiating document root.
	 *
	 * @param row activity row in model coordinates
	 */
	private void removeSelectedActivityEntry(int row) {
		if (currentForm == null || row < 0 || row >= currentForm.getActivityLog().size()) {
			return;
		}
		ActivityLogEntry entry = currentForm.getActivityLog().get(row);
		String label = (entry.getTimestamp() == null
				? ""
				: SarTaskPanel.formatDateTimeValue(entry.getTimestamp()) + " ")
				+ resolvedEventTypeLabel(entry.getEventTypeId());
		int confirm = ActivityEntryDialog.showDocumentConfirm(this, "Strike out activity entry '" + label.trim() + "'?",
				"Strike Out Activity Entry", JOptionPane.YES_NO_OPTION);
		if (confirm != JOptionPane.YES_OPTION) {
			return;
		}
		strikeOutActivityEntry(row);
	}

	/**
	 * Marks an activity log entry as struck out while retaining it in the log.
	 *
	 * @param row
	 *            selected row index in model coordinates.
	 */
	private void strikeOutActivityEntry(int row) {
		if (currentForm == null || row < 0 || row >= currentForm.getActivityLog().size()) {
			return;
		}
		currentForm.getActivityLog().get(row).setStruckOut(true);
		activityLogTableModel.setRows(currentForm.getActivityLog(), resolvedEventTypes());
		scrollActivityLogToLatestEntry();
		controller.markDirty();
	}

	private String resolvedEventTypeLabel(String eventTypeId) {
		for (ActivityEventType type : resolvedEventTypes()) {
			if (type.getId().equals(eventTypeId)) {
				return type.getLabel();
			}
		}
		return eventTypeId == null ? "" : eventTypeId;
	}

	/**
	 * Creates entry actions using view-to-model conversion for selected rows.
	 *
	 * @return activity action buttons
	 */
	private JPanel activityButtonsPanel() {
		JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		JButton add = new JButton("Add Entry");
		JButton remove = new JButton("Strike Out Entry");
		add.addActionListener(event -> addActivityEntry());
		remove.addActionListener(event -> {
			int row = activityLogTable.getSelectedRow();
			removeSelectedActivityEntry(row < 0 ? -1 : activityLogTable.convertRowIndexToModel(row));
		});
		panel.add(add);
		panel.add(remove);
		return panel;
	}

	/**
	 * Item 6: Shows a non-editable view of the T-card linked to the selected
	 * resource row. Looks up the T-card by person name from the controller's
	 * current T-card list.
	 */
	private void showTCardForSelectedResource() {
		int row = resourcesTable.getSelectedRow();
		if (row < 0 || currentData == null) {
			return;
		}
		int modelRow = resourcesTable.convertRowIndexToModel(row);
		SarTaskResource resource = resourcesTableModel.getRows().get(modelRow);
		String name = resource.getName().isBlank() ? resource.getIcsPosition() : resource.getName();
		TCard tcard = currentData.getTCards().stream()
				.filter(c -> !c.getPersonName().isBlank() && c.getPersonName().equalsIgnoreCase(name)).findFirst()
				.orElse(null);
		if (tcard == null) {
			UiSupport.showDocumentMessage(this, "No T-card record found for: " + name, "T-Card",
					JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		JPanel form = UiSupport.formPanel();
		int r = 0;
		UiSupport.addRow(form, r++, "Card type", new JLabel(tcard.getCardType().getLabel()));
		UiSupport.addRow(form, r++, "Name", new JLabel(tcard.getPersonName()));
		UiSupport.addRow(form, r++, "Home agency", new JLabel(tcard.getHomeAgency()));
		UiSupport.addRow(form, r++, "Home state", new JLabel(tcard.getHomeState()));
		UiSupport.addRow(form, r++, "Phone", new JLabel(tcard.getPhoneNumber()));
		UiSupport.addRow(form, r++, "Radio channel", new JLabel(tcard.getRadioChannel()));
		UiSupport.addRow(form, r++, "Resource identifier", new JLabel(tcard.getResourceIdentifier()));
		UiSupport.addRow(form, r++, "Location", new JLabel(tcard.getLocation()));
		UiSupport.addRow(form, r++, "Status", new JLabel(tcard.getStatus()));
		UiSupport.addRow(form, r, "Notes", new JLabel(tcard.getNotes()));
		JScrollPane scroll = new JScrollPane(form);
		scroll.setBorder(BorderFactory.createEmptyBorder());
		UiSupport.showDocumentMessage(this, scroll, "T-Card: " + tcard.getPersonName(), JOptionPane.PLAIN_MESSAGE);
	}

	/**
	 * Dispatches to the appropriate sync strategy based on the current form's
	 * scope: TASK_ASSIGNMENT replaces the list from the linked task;
	 * ICP/ASSIGNMENT_LIST adds missing resources from ICP T-cards and 204
	 * assignment leaders.
	 */
	private void syncResources() {
		if (currentForm == null || currentData == null) {
			return;
		}
		if (currentForm.getLogScope() == ActivityLogScope.TASK_ASSIGNMENT) {
			syncResourcesFromTask();
		} else {
			refreshResourcesFromIcp();
		}
	}

	/**
	 * Replaces the resource list with the leader and resources from the linked SAR
	 * task assignment only, so the 214 matches exactly the people on that task.
	 */
	private void syncResourcesFromTask() {
		String taskId = currentForm.getLinkedSarTaskAssignmentId();
		if (taskId == null || taskId.isBlank()) {
			UiSupport.showDocumentMessage(this, "This form is not linked to a task assignment.", "Sync from Task",
					JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		SarTaskAssignment task = currentData.getSarTaskAssignments().stream()
				.filter(t -> taskId.equals(t.getAssignmentId())).findFirst().orElse(null);
		if (task == null) {
			UiSupport.showDocumentMessage(this, "Linked task assignment not found.", "Sync from Task",
					JOptionPane.WARNING_MESSAGE);
			return;
		}
		List<SarTaskResource> fromTask = new ArrayList<>(task.getResourcesAssigned());
		if (fromTask.isEmpty()) {
			UiSupport.showDocumentMessage(this, "The linked task assignment has no resources.", "Sync from Task",
					JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		String summary = fromTask.stream()
				.map(r -> "  \u2022 " + (r.getName().isBlank() ? r.getFunction() : r.getName())
						+ (r.getIcsPosition().isBlank() ? "" : " (" + r.getIcsPosition() + ")"))
				.collect(Collectors.joining("\n"));
		int choice = ActivityEntryDialog.showDocumentConfirm(this,
				"Replace the current resource list with the following " + fromTask.size()
						+ " resource(s) from the task assignment?\n" + summary,
				"Sync from Task Assignment", JOptionPane.OK_CANCEL_OPTION);
		if (choice != JOptionPane.OK_OPTION) {
			return;
		}
		resourcesTableModel.setRows(copyResources(fromTask));
		currentForm.setResourcesAssigned(new ArrayList<>(fromTask));
		loadedResources = resourceStates(resourcesTableModel.getRows());
		controller.markDirty();
	}

	/**
	 * Adds missing resources from ICP T-cards and 204 assignment leaders. Uses
	 * T-card notes as the ICS position for org-chart-sourced personnel so their
	 * role (e.g. "Safety Officer") appears in the table.
	 */
	private void refreshResourcesFromIcp() {
		List<SarTaskResource> toAdd = new ArrayList<>();
		// T-cards at ICP.
		for (TCard card : currentData.getTCards()) {
			if (card.getCardType() == org.sarmanagement.icsforms.model.TCardType.HEADER) {
				continue;
			}
			String loc = card.getLocation() == null ? "" : card.getLocation();
			if ("ICP".equalsIgnoreCase(loc) && !card.getPersonName().isBlank()) {
				SarTaskResource r = new SarTaskResource();
				r.setName(card.getPersonName());
				r.setHomeAgency(card.getHomeAgency());
				// Notes on org-sourced cards carry the role label (e.g. "Safety Officer").
				if (!card.getNotes().isBlank()) {
					r.setIcsPosition(card.getNotes());
				}
				toAdd.add(r);
			}
		}
		// Leaders from 204 assignment forms.
		for (ResourceAssignment ra : currentData.getForm204().getResourcesAssigned()) {
			if (!ra.getLeader().isBlank()) {
				SarTaskResource r = new SarTaskResource();
				r.setName(ra.getLeader());
				r.setIcsPosition(ra.getLeaderRole());
				toAdd.add(r);
			}
		}
		if (toAdd.isEmpty()) {
			UiSupport.showDocumentMessage(this, "No ICP resources or 204 assignments found to add.",
					"Refresh Resources", JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		// Remove duplicates against existing list.
		java.util.Set<String> existing = new java.util.HashSet<>();
		for (SarTaskResource r : resourcesTableModel.getRows()) {
			String n = r.getName().isBlank() ? r.getFunction() : r.getName();
			existing.add(n.trim().toLowerCase());
		}
		toAdd.removeIf(r -> {
			String n = r.getName().isBlank() ? r.getFunction() : r.getName();
			return existing.contains(n.trim().toLowerCase());
		});
		if (toAdd.isEmpty()) {
			UiSupport.showDocumentMessage(this, "All ICP resources are already in the list.", "Refresh Resources",
					JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		String summary = toAdd.stream()
				.map(r -> "  \u2022 " + (r.getName().isBlank() ? r.getFunction() : r.getName())
						+ (r.getIcsPosition().isBlank() ? "" : " (" + r.getIcsPosition() + ")"))
				.collect(Collectors.joining("\n"));
		int choice = ActivityEntryDialog.showDocumentConfirm(this, "Add the following resources to this form?\n" + summary,
				"Refresh from ICP & 204", JOptionPane.OK_CANCEL_OPTION);
		if (choice != JOptionPane.OK_OPTION) {
			return;
		}
		List<SarTaskResource> merged = new ArrayList<>(resourcesTableModel.getRows());
		merged.addAll(toAdd);
		resourcesTableModel.setRows(copyResources(merged));
		currentForm.setResourcesAssigned(merged);
		loadedResources = resourceStates(resourcesTableModel.getRows());
		controller.markDirty();
	}

	private List<Section3Choice> section3Choices() {
		if (currentData == null || currentData.getTCards() == null) {
			return List.of();
		}
		List<Section3Choice> choices = new ArrayList<>();
		Set<String> seen = new LinkedHashSet<>();
		for (TCard card : currentData.getTCards()) {
			if (card == null || card.getCardType() == TCardType.HEADER) {
				continue;
			}
			String name = resourceNameForCard(card);
			if (name.isBlank()) {
				continue;
			}
			String key = "resource:" + nullSafe(card.getResourceId());
			if (seen.add(key)) {
				choices.add(new Section3Choice(displayNameForCard(card), name, nullSafe(card.getNotes()),
						nullSafe(card.getHomeAgency()), nullSafe(card.getResourceId())));
			}
		}
		for (String location : locationChoices()) {
			if (location.isBlank()) {
				continue;
			}
			String key = "location:" + location.toLowerCase();
			if (seen.add(key)) {
				choices.add(new Section3Choice(location + " (Location)", location, "", "", ""));
			}
		}
		return choices;
	}

	private List<String> locationChoices() {
		if (currentData == null || currentData.getTCards() == null) {
			return List.of();
		}
		Set<String> locations = new LinkedHashSet<>();
		for (TCard card : currentData.getTCards()) {
			if (card == null) {
				continue;
			}
			if (card.getLocation() != null && !card.getLocation().isBlank()) {
				locations.add(card.getLocation().trim());
			}
			if (card.getCardType() == TCardType.HEADER) {
				String headerLabel = displayNameForCard(card).trim();
				String normalized = headerLabel.toLowerCase();
				if (!headerLabel.isBlank() && !normalized.equals("ordered") && !normalized.equals("available")
						&& !normalized.equals("assigned") && !normalized.equals("out of service")) {
					locations.add(headerLabel);
				}
			}
		}
		return new ArrayList<>(locations);
	}

	private void populatePreparedByFromPersonCard(String selectedName, boolean markDirty) {
		var card = controller.findPersonCard(selectedName);
		if (card == null) {
			return;
		}
		if (preparedByPositionField.getText().isBlank() && card.getNotes() != null && !card.getNotes().isBlank()) {
			preparedByPositionField.setText(card.getNotes().trim());
		}
		if (homeAgencyField.getText().isBlank()) {
			homeAgencyField.setText(nullSafe(card.getHomeAgency()));
		}
		if (markDirty) {
			controller.markDirty();
		}
	}

	private String createPersonnelFromPicker(String proposedName, JTextField positionField, JTextField agencyField) {
		TCard created = controller.createPersonnelCardViaDialog(proposedName, "", "");
		if (created == null) {
			return "";
		}
		if (agencyField.getText().isBlank()) {
			agencyField.setText(nullSafe(created.getHomeAgency()));
		}
		if (positionField.getText().isBlank() && created.getNotes() != null && !created.getNotes().isBlank()) {
			positionField.setText(created.getNotes().trim());
		}
		return nullSafe(created.getPersonName());
	}

	private void clearFields() {
		nameField.setText("");
		icsPositionField.setText("");
		homeAgencyField.setText("");
		preparedByNameField.setText("");
		preparedByPositionField.setText("");
		preparedDateTimeField.setValue(AppController.toDate(null));
		resourcesTableModel.setRows(List.of());
		activityLogTableModel.setRows(List.of(), List.of());
	}

	private String nullSafe(String value) {
		return value == null ? "" : value;
	}

	/**
	 * Scrolls the section 7 activity log to its newest entry.
	 */
	private void scrollActivityLogToLatestEntry() {
		UiSupport.scrollTableToLastRow(activityLogTable);
	}

	/**
	 * Returns the linked SAR task assignment, or {@code null} when this form is not
	 * task-linked.
	 *
	 * @return linked task assignment.
	 */
	private SarTaskAssignment linkedTask() {
		if (currentForm == null || currentData == null) {
			return null;
		}
		String taskId = currentForm.getLinkedSarTaskAssignmentId();
		if (taskId == null || taskId.isBlank()) {
			return null;
		}
		return currentData.getSarTaskAssignments().stream().filter(task -> taskId.equals(task.getAssignmentId())).findFirst()
				.orElse(null);
	}

	/**
	 * Normalizes the entry resource label so task-linked logs consistently show the
	 * assignment/team number plus resource identifier.
	 *
	 * @param entry
	 *            entry to normalize.
	 */
	private void normalizeEntryResourceIdentifier(ActivityLogEntry entry) {
		if (entry == null || currentForm == null) {
			return;
		}
		if (currentForm.getLogScope() == ActivityLogScope.TASK_ASSIGNMENT) {
			String label = SarTaskSupport.taskResourceDisplayLabel(linkedTask());
			if (!label.isBlank()) {
				entry.setResourceIdentifier(label);
			}
		}
	}

	/**
	 * Returns the resource label to show for an activity log entry.
	 *
	 * @param entry
	 *            entry being shown.
	 * @return resource label for the table.
	 */
	private String displayedResourceIdentifier(ActivityLogEntry entry) {
		if (entry == null) {
			return "";
		}
		SarTaskAssignment task = linkedTask();
		if (task != null) {
			String label = SarTaskSupport.taskResourceDisplayLabel(task);
			if (!label.isBlank()) {
				return label;
			}
		}
		String raw = nullSafe(entry.getResourceIdentifier()).trim();
		if (raw.isBlank() || currentData == null) {
			return raw;
		}
		for (SarTaskAssignment candidate : currentData.getSarTaskAssignments()) {
			String label = SarTaskSupport.taskResourceDisplayLabel(candidate);
			if (raw.equalsIgnoreCase(nullSafe(candidate.getAssignmentTeamNumber()).trim())
					|| raw.equalsIgnoreCase(nullSafe(candidate.getResourceIdentifier()).trim())
					|| raw.equalsIgnoreCase(label)) {
				return label;
			}
		}
		return raw;
	}

	/**
	 * Wraps a table cell value in simple strike-through HTML.
	 *
	 * @param value
	 *            cell value.
	 * @return HTML strike-through rendering text.
	 */
	private static String strikeHtml(Object value) {
		String text = value == null ? "" : value.toString();
		String escaped = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
		return "<html><strike>" + escaped + "</strike></html>";
	}

	private static String resourceNameForCard(TCard card) {
		if (card == null) {
			return "";
		}
		String name = card.getPersonName();
		if (name == null || name.isBlank()) {
			name = card.getResourceIdentifier();
		}
		return name == null ? "" : name.trim();
	}

	private record Section3Choice(String displayLabel, String name, String icsPosition, String homeAgency,
			String linkedResourceId) {
	}

	// -------------------------------------------------------------------------
	// Activity entry editor dialog
	// -------------------------------------------------------------------------

	/** Detached fields shared by contextual entry dialogs. */
	static class ActivityEntryEditor {
		final JPanel panel = UiSupport.formPanel();
		private final JSpinner timestampField = UiSupport.dateTimeSpinner();
		private final JComboBox<ActivityEventType> eventTypeField;
		final JComboBox<String> resourceIdentifierField;
		private final JTextArea notableActivityField = UiSupport.textArea(4);

		/**
		 * Builds editable activity fields.
		 *
		 * @param eventTypes available event types
		 * @param defaultResourceIdentifier initial resource
		 * @param resourcePicklist available resource labels
		 */
		ActivityEntryEditor(List<ActivityEventType> eventTypes, String defaultResourceIdentifier,
				List<String> resourcePicklist) {
			ActivityEventType[] typeArray = eventTypes.toArray(new ActivityEventType[0]);
			eventTypeField = new JComboBox<>(typeArray);
			eventTypeField.setRenderer(new javax.swing.DefaultListCellRenderer() {
				@Override
				public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list, Object value,
						int index, boolean isSelected, boolean cellHasFocus) {
					super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
					if (value instanceof ActivityEventType t) {
						if (ActivityEventType.ID_CLUE_DETECTED.equals(t.getId())) {
							setText("Clue detected by my assignment");
						} else if (ActivityEventType.ID_CLUE_REPORTED.equals(t.getId())) {
							setText("Clue reported by another assignment");
						} else {
							setText(t.getLabel());
						}
					}
					return this;
				}
			});
			// Select the free-text / Note type by default.
			for (ActivityEventType t : typeArray) {
				if (ActivityEventType.ID_FREE_TEXT.equals(t.getId())) {
					eventTypeField.setSelectedItem(t);
					break;
				}
			}
			// Build an editable combobox for the resource identifier. The picklist makes it
			// easy to select a team member; free text is still allowed.
			List<String> items = new ArrayList<>();
			if (resourcePicklist != null) {
				items.addAll(resourcePicklist);
			}
			resourceIdentifierField = new JComboBox<>(items.toArray(new String[0]));
			resourceIdentifierField.setEditable(true);
			UiSupport.configureDialogComboBox(resourceIdentifierField, 260);
			if (defaultResourceIdentifier != null && !defaultResourceIdentifier.isBlank()) {
				resourceIdentifierField.setSelectedItem(defaultResourceIdentifier);
			} else {
				resourceIdentifierField.setSelectedItem("");
			}
			UiSupport.configureDialogComboBox(eventTypeField, 220);
			UiSupport.addRow(panel, 0, "Date/time", timestampField);
			UiSupport.addRow(panel, 1, "Event type", eventTypeField);
			UiSupport.addRow(panel, 2, "Resource identifier", resourceIdentifierField);
			UiSupport.addRow(panel, 3, "Notable activity", new JScrollPane(notableActivityField));
		}

		/**
		 * Creates a detached entry from the fields.
		 *
		 * @return new entry, not yet added to any log
		 */
		ActivityLogEntry toEntry() {
			ActivityLogEntry entry = new ActivityLogEntry();
			entry.setTimestamp(AppController.toLocalDateTime((Date) timestampField.getValue()));
			ActivityEventType selected = (ActivityEventType) eventTypeField.getSelectedItem();
			entry.setEventTypeId(selected != null ? selected.getId() : ActivityEventType.ID_FREE_TEXT);
			Object resourceSelection = resourceIdentifierField.getSelectedItem();
			entry.setResourceIdentifier(resourceSelection == null ? "" : resourceSelection.toString().trim());
			entry.setNotableActivity(notableActivityField.getText().trim());
			return entry;
		}
	}

	// -------------------------------------------------------------------------
	// Event type manager dialog (invoked from Configuration menu via MainFrame)
	// -------------------------------------------------------------------------

	/** Manages configurable activity types within the owning document's dialog chain. */
	static class EventTypeManagerDialog {
		final JPanel panel = new JPanel(new BorderLayout(4, 4));
		private final EventTypesTableModel tableModel;
		private final JTable table;

		EventTypeManagerDialog(List<ActivityEventType> initial) {
			tableModel = new EventTypesTableModel(initial);
			table = new JTable(tableModel);
			table.setFillsViewportHeight(true);

			JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
			JButton add = new JButton("Add Custom Type");
			JButton remove = new JButton("Remove Selected");
			add.addActionListener(e -> addCustomType());
			remove.addActionListener(e -> removeSelected());
			buttons.add(add);
			buttons.add(remove);

			panel.add(new JLabel("Built-in types cannot be removed. Add custom types below."), BorderLayout.NORTH);
			panel.add(new JScrollPane(table), BorderLayout.CENTER);
			panel.add(buttons, BorderLayout.SOUTH);
		}

		/** Adds a validated custom event type using a document-modal picker. */
		private void addCustomType() {
			JTextField idField = new JTextField(12);
			JTextField labelField = new JTextField(20);
			JPanel input = new JPanel(new FlowLayout(FlowLayout.LEFT));
			input.add(new JLabel("ID (letters, digits, underscores):"));
			input.add(idField);
			input.add(new JLabel("Label:"));
			input.add(labelField);
			int result = ActivityEntryDialog.showDocumentConfirm(panel, input, "New Event Type", JOptionPane.OK_CANCEL_OPTION);
			if (result != JOptionPane.OK_OPTION) {
				return;
			}
			String id = idField.getText().trim().toUpperCase().replace(' ', '_');
			String label = labelField.getText().trim();
			if (id.isEmpty() || label.isEmpty()) {
				UiSupport.showDocumentMessage(panel, "Both ID and Label are required.", "Invalid Event Type",
						JOptionPane.ERROR_MESSAGE);
				return;
			}
			if (!id.matches("[A-Z0-9_]+")) {
				UiSupport.showDocumentMessage(panel,
						"ID may only contain letters (A-Z), digits (0-9), and underscores.", "Invalid Event Type ID",
						JOptionPane.ERROR_MESSAGE);
				return;
			}
			tableModel.addType(new ActivityEventType(id, label, false));
		}

		/** Confirms removal of a configurable event type within its owner document. */
		private void removeSelected() {
			int row = table.getSelectedRow();
			if (row >= 0) {
				ActivityEventType type = tableModel.getTypes().get(row);
				int confirm = ActivityEntryDialog.showDocumentConfirm(panel,
						"Remove event type '" + type.getLabel() + "' (" + type.getId() + ")?", "Remove Event Type",
						JOptionPane.YES_NO_OPTION);
				if (confirm == JOptionPane.YES_OPTION) {
					tableModel.removeRow(row);
				}
			}
		}

		List<ActivityEventType> getEventTypes() {
			return tableModel.getTypes();
		}
	}

	private static class EventTypesTableModel extends AbstractTableModel {
		private final String[] columns = {"ID", "Label", "Built-in"};
		private final List<ActivityEventType> types;

		private EventTypesTableModel(List<ActivityEventType> types) {
			this.types = new ArrayList<>(types);
		}

		void addType(ActivityEventType type) {
			types.add(type);
			fireTableRowsInserted(types.size() - 1, types.size() - 1);
		}

		void removeRow(int row) {
			if (row >= 0 && row < types.size() && !types.get(row).isBuiltIn()) {
				types.remove(row);
				fireTableRowsDeleted(row, row);
			}
		}

		List<ActivityEventType> getTypes() {
			return types;
		}

		@Override
		public int getRowCount() {
			return types.size();
		}
		@Override
		public int getColumnCount() {
			return columns.length;
		}
		@Override
		public String getColumnName(int col) {
			return columns[col];
		}
		@Override
		public boolean isCellEditable(int row, int col) {
			return false;
		}

		@Override
		public Object getValueAt(int row, int col) {
			ActivityEventType t = types.get(row);
			return switch (col) {
				case 0 -> t.getId();
				case 1 -> t.getLabel();
				default -> t.isBuiltIn() ? "Yes" : "No";
			};
		}
	}

	// -------------------------------------------------------------------------
	// Table models for resources and activity log
	// -------------------------------------------------------------------------

	private static class ResourcesTableModel extends AbstractTableModel {
		private final String[] columns = {"Name", "ICS Position", "Home Agency"};
		private List<SarTaskResource> rows = new ArrayList<>();

		void setRows(List<SarTaskResource> rows) {
			this.rows = rows == null ? new ArrayList<>() : rows;
			fireTableDataChanged();
		}

		List<SarTaskResource> getRows() {
			return rows;
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
		public String getColumnName(int col) {
			return columns[col];
		}
		@Override
		public boolean isCellEditable(int row, int col) {
			return false;
		}

		@Override
		public Object getValueAt(int row, int col) {
			SarTaskResource r = rows.get(row);
			return switch (col) {
				// Show resource identifier (function) when name is blank (e.g. canine
				// resources).
				case 0 -> r.getName().isBlank() ? r.getFunction() : r.getName();
				case 1 -> r.getIcsPosition();
				default -> r.getHomeAgency();
			};
		}
	}

	private class ActivityLogTableModel extends AbstractTableModel {
		private final String[] columns = {"Date/Time", "Event Type", "Resource", "Notable Activity"};
		private List<ActivityLogEntry> rows = new ArrayList<>();
		private List<ActivityEventType> eventTypes = new ArrayList<>();

		void setRows(List<ActivityLogEntry> rows, List<ActivityEventType> eventTypes) {
			this.rows = rows == null ? new ArrayList<>() : rows;
			this.eventTypes = eventTypes == null ? ActivityEventType.defaultTypes() : eventTypes;
			fireTableDataChanged();
		}

		List<ActivityLogEntry> getRows() {
			return rows;
		}

		ActivityLogEntry entryAt(int row) {
			return row < 0 || row >= rows.size() ? null : rows.get(row);
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
		public String getColumnName(int col) {
			return columns[col];
		}
		@Override
		public boolean isCellEditable(int row, int col) {
			return false;
		}

		@Override
		public Object getValueAt(int row, int col) {
			ActivityLogEntry entry = rows.get(row);
			return switch (col) {
				case 0 -> entry.getTimestamp() == null ? "" : DATE_TIME_FORMATTER.format(entry.getTimestamp());
				case 1 -> resolveLabel(entry.getEventTypeId());
				case 2 -> displayedResourceIdentifier(entry);
				default -> entry.getNotableActivity();
			};
		}

		private String resolveLabel(String id) {
			if (id == null) {
				return "Note";
			}
			return eventTypes.stream().filter(t -> id.equals(t.getId())).map(ActivityEventType::getLabel).findFirst()
					.orElse(id);
		}
	}
}
