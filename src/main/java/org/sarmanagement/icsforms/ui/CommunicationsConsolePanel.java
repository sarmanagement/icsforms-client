/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.sarmanagement.icsforms.comms.ChannelLinkSource;
import org.sarmanagement.icsforms.model.ActivityEventType;
import org.sarmanagement.icsforms.model.ActivityLogEntry;
import org.sarmanagement.icsforms.model.ActivityLogScope;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.Ics214Form;
import org.sarmanagement.icsforms.model.Ics204Form;
import org.sarmanagement.icsforms.model.RadioChannel;
import org.sarmanagement.icsforms.model.RadioCheckStatus;
import org.sarmanagement.icsforms.model.ResourceAssignment;
import org.sarmanagement.icsforms.model.SarTaskAssignment;
import org.sarmanagement.icsforms.model.SarTaskResource;
import org.sarmanagement.icsforms.model.SarTaskSupport;
import org.sarmanagement.icsforms.model.TCard;
import org.sarmanagement.icsforms.model.TCardType;
import org.sarmanagement.icsforms.model.UtcTimestamp;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Live communications operating console over the shared task, contact and ICP log models.
 * Check drafts and detached shared editors survive cancelled and rejected submissions.
 */
public final class CommunicationsConsolePanel extends JPanel {
	private static final DateTimeFormatter CHECK_TIME = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm")
			.withResolverStyle(java.time.format.ResolverStyle.STRICT);
	private final AppController controller;
	private final UiRefreshState logRefreshState = new UiRefreshState();
	private final AppController.IncidentBinding binding;
	private final AppController.IncidentChangeSubscription subscription;
	private final DefaultListModel<TaskChoice> taskModel = new DefaultListModel<>();
	private final JList<TaskChoice> tasks = new JList<>(taskModel);
	private final JTextArea taskDetails = area("Selected task communications", false);
	private final JTextField lookup = new JTextField(22);
	private final JTextArea contacts = area("Resource and contact lookup results", false);
	private final DefaultListModel<ContactChoice> contactModel = new DefaultListModel<>();
	private final JList<ContactChoice> contactChoices = new JList<>(contactModel);
	private final JComboBox<ChannelChoice> channels = new JComboBox<>();
	private final JTextArea channelDetails = area("Selected channel details", false);
	private final JComboBox<RadioCheckStatus> outcome = new JComboBox<>(new RadioCheckStatus[] {
			RadioCheckStatus.SUCCESSFUL, RadioCheckStatus.PARTIAL, RadioCheckStatus.FAILED});
	private final JTextField checkedBy = new JTextField(22);
	private final JTextField checkedAt = new JTextField(22);
	private final JTextArea notes = area("Communications check notes", true);
	private final JLabel feedback = new JLabel("Checks are explicit assertions, not inferred readiness.");
	private final JCheckBox allIncident = new JCheckBox("Show all log entries");
	private final JLabel logScope = new JLabel(" ");
	private final DefaultListModel<LogChoice> logModel = new DefaultListModel<>();
	private final JList<LogChoice> logs = new JList<>(logModel);
	private final JButton details = new JButton("View full entry details…");
	private final Map<String, CheckDraft> checkDrafts = new LinkedHashMap<>();
	private final Map<String, ActivityEntryDialog> entryDrafts = new LinkedHashMap<>();
	private String loadedChannelId = "";
	private boolean refreshing;
	private boolean disposed;
	private boolean initialSelection = true;

	/**
	 * Creates a console bound to the current incident without copying persistent state.
	 *
	 * @param controller incident controller.
	 */
	public CommunicationsConsolePanel(AppController controller) {
		super(new BorderLayout(6, 6));
		this.controller = controller;
		binding = controller.captureIncidentBinding();
		javax.swing.DefaultListCellRenderer plainText = new javax.swing.DefaultListCellRenderer();
		plainText.putClientProperty("html.disable", Boolean.TRUE);
		tasks.setCellRenderer(plainText);
		contactChoices.setCellRenderer(plainText);
		logs.setCellRenderer(plainText);
		channels.setRenderer(plainText);
		JPanel board = new JPanel(new BorderLayout(4, 4));
		board.setBorder(BorderFactory.createTitledBorder("Task assignment board — recorded status"));
		board.add(buildActions(), BorderLayout.SOUTH);
		tasks.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		tasks.getAccessibleContext().setAccessibleName("Communications task assignment board");
		installTaskMenu();
		JSplitPane taskSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(tasks),
				new JScrollPane(taskDetails));
		taskSplit.setResizeWeight(0.5);
		board.add(taskSplit, BorderLayout.CENTER);
		JTabbedPane tools = new JTabbedPane();
		tools.addTab("Recent ICP log", buildLogs());
		tools.addTab("Resource / contact lookup", buildContacts());
		tools.addTab("Record check + log", buildCheck());
		JSplitPane body = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, board, tools);
		body.setResizeWeight(0.4);
		body.setPreferredSize(new Dimension(1120, 680));
		add(body, BorderLayout.CENTER);
		tasks.addListSelectionListener(event -> {
			if (!event.getValueIsAdjusting() && !refreshing) {
				if (tasks.getSelectedValue() != null) {
					allIncident.setSelected(false);
				}
				refreshTaskDetails();
				refreshContacts();
				refreshLogs();
			}
		});
		channels.addActionListener(event -> {
			if (!refreshing) {
				rememberCheckDraft();
				loadCheckDraft();
			}
		});
		allIncident.addActionListener(event -> refreshLogs());
		logs.addListSelectionListener(event -> details.setEnabled(logs.getSelectedValue() != null));
		lookup.getDocument().addDocumentListener(new DocumentListener() {
			/** {@inheritDoc} */
			@Override
			public void insertUpdate(DocumentEvent event) {
				refreshContacts();
			}

			/** {@inheritDoc} */
			@Override
			public void removeUpdate(DocumentEvent event) {
				refreshContacts();
			}

			/** {@inheritDoc} */
			@Override
			public void changedUpdate(DocumentEvent event) {
				refreshContacts();
			}
		});
		refreshFromModel();
		subscription = controller.addIncidentChangeListener(change -> {
			if (!disposed && controller.isCurrent(binding)) {
				refreshFromModel();
			}
		});
	}

	/** Releases the incident subscription and prevents actions from a closed console. */
	public void dispose() {
		disposed = true;
		subscription.close();
	}

	/** Refreshes projections while retaining stable selections, filters and entered drafts. */
	public void refreshFromModel() {
		if (disposed || !controller.isCurrent(binding)) {
			return;
		}
		AppData data = controller.getData();
		rememberCheckDraft();
		String taskId = selectedAssignmentId();
		String channelId = selectedChannelId();
		refreshing = true;
		try {
			List<TaskChoice> taskRows = new ArrayList<>();
			for (SarTaskAssignment task : data.getSarTaskAssignments()) {
				taskRows.add(new TaskChoice(task.getAssignmentId(), task.getAssignmentTeamNumber() + " | "
						+ task.getTaskLifecycleStatus() + " | " + task.getAssignment()));
			}
			java.util.Set<String> represented = new java.util.HashSet<>();
			for (TaskChoice row : taskRows) {
				represented.add(row.assignmentId());
			}
			for (ResourceAssignment assignment : data.getAllAssignmentResources()) {
				if (represented.add(assignment.getAssignmentId())) {
					taskRows.add(new TaskChoice(assignment.getAssignmentId(),
							assignment.getAssignmentTeamNumber() + " | ICS 204 | " + assignment.getResourceIdentifier()
									+ " | " + assignment.getAssignment()));
				}
			}
			UiSupport.reconcileList(taskModel, taskRows);
			int selectedTaskIndex = -1;
			for (int index = 0; index < taskModel.size(); index++) {
				if (taskId.equals(taskModel.get(index).assignmentId())) {
					selectedTaskIndex = index;
					break;
				}
			}
			if (initialSelection && selectedTaskIndex < 0 && !taskModel.isEmpty()) {
				selectedTaskIndex = 0;
			}
			if (tasks.getSelectedIndex() != selectedTaskIndex) {
				tasks.setSelectedIndex(selectedTaskIndex);
			}
			initialSelection = false;
			List<ChannelChoice> channelRows = data.getForm205().getChannels().stream()
					.map(channel -> new ChannelChoice(channel.getChannelId(), ChannelLinkSource.channelLabel(channel))).toList();
			List<ChannelChoice> displayed = java.util.stream.IntStream.range(0, channels.getItemCount())
					.mapToObj(channels::getItemAt).toList();
			if (!displayed.equals(channelRows)) {
				channels.removeAllItems();
				channelRows.forEach(channels::addItem);
				for (int index = 0; index < channels.getItemCount(); index++) {
					if (channelId.equals(channels.getItemAt(index).channelId())) {
						channels.setSelectedIndex(index);
						break;
					}
				}
				if (!channelId.isBlank() && !channelId.equals(selectedChannelId())) {
					channels.setSelectedIndex(-1);
				}
			}
		} finally {
			refreshing = false;
		}
		loadCheckDraft();
		refreshTaskDetails();
		refreshContacts();
		refreshLogs();
	}

	/**
	 * Returns the selected task identity without retaining an editable task snapshot.
	 *
	 * @return stable assignment identity or blank.
	 */
	String selectedAssignmentId() {
		TaskChoice choice = tasks.getSelectedValue();
		return choice == null ? "" : choice.assignmentId();
	}

	/**
	 * Returns the selected channel identity.
	 *
	 * @return stable channel identity or blank.
	 */
	String selectedChannelId() {
		ChannelChoice choice = (ChannelChoice) channels.getSelectedItem();
		return choice == null ? "" : choice.channelId();
	}

	/**
	 * Builds task-board shared-editor actions, none of which mutate a channel.
	 *
	 * @return action toolbar.
	 */
	private JPanel buildActions() {
		JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEADING));
		JButton add = new JButton("Add Log Entry…");
		add.addActionListener(event -> openEntry(ActivityEventType.ID_FREE_TEXT));
		JButton problem = new JButton("Communications Problem…");
		problem.addActionListener(event -> openEntry(ActivityEventType.ID_COMMUNICATIONS_PROBLEM));
		actions.add(add);
		actions.add(problem);
		return actions;
	}

	/** Installs shared log actions that use the task beneath the context-menu click. */
	private void installTaskMenu() {
		JPopupMenu menu = new JPopupMenu() {
			/**
			 * Selects an actual task row before displaying its contextual actions.
			 *
			 * @param invoker component requesting the menu.
			 * @param x horizontal click position.
			 * @param y vertical click position.
			 */
			@Override
			public void show(java.awt.Component invoker, int x, int y) {
				java.awt.Point point = new java.awt.Point(x, y);
				int row = tasks.locationToIndex(point);
				if (!isActive() || row < 0 || !tasks.getCellBounds(row, row).contains(point)) {
					return;
				}
				tasks.setSelectedIndex(row);
				super.show(invoker, x, y);
			}
		};
		JMenuItem add = new JMenuItem("Add Log Entry…");
		add.addActionListener(event -> openEntry(ActivityEventType.ID_FREE_TEXT));
		JMenuItem problem = new JMenuItem("Communications Problem…");
		problem.addActionListener(event -> openEntry(ActivityEventType.ID_COMMUNICATIONS_PROBLEM));
		menu.add(add);
		menu.add(problem);
		tasks.setComponentPopupMenu(menu);
	}

	/**
	 * Builds incident-wide contact search using explicit stable channel resolution.
	 *
	 * @return contact lookup panel.
	 */
	private JPanel buildContacts() {
		JPanel panel = new JPanel(new BorderLayout(4, 4));
		JPanel search = new JPanel(new FlowLayout(FlowLayout.LEADING));
		search.add(new JLabel("Find resource / contact"));
		lookup.getAccessibleContext().setAccessibleName("Find communications resource or contact");
		search.add(lookup);
		panel.add(search, BorderLayout.NORTH);
		contactChoices.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		contactChoices.getAccessibleContext().setAccessibleName("Selectable communications resources and contacts");
		JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(contactChoices),
				new JScrollPane(contacts));
		split.setResizeWeight(0.5);
		panel.add(split, BorderLayout.CENTER);
		return panel;
	}

	/**
	 * Builds the recent shared ICP activity projection and full-details action.
	 *
	 * @return activity panel.
	 */
	private JPanel buildLogs() {
		JPanel panel = new JPanel(new BorderLayout(4, 4));
		logScope.putClientProperty("html.disable", Boolean.TRUE);
		logScope.getAccessibleContext().setAccessibleName("Recent ICP log scope");
		logScope.setMinimumSize(new Dimension(0, logScope.getPreferredSize().height));
		JPanel scopeBar = new JPanel(new BorderLayout(8, 0));
		scopeBar.add(logScope, BorderLayout.CENTER);
		scopeBar.add(allIncident, BorderLayout.EAST);
		panel.add(scopeBar, BorderLayout.NORTH);
		logs.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		logs.getAccessibleContext().setAccessibleName("Recent ICP log entries");
		panel.add(new JScrollPane(logs), BorderLayout.CENTER);
		details.setEnabled(false);
		details.addActionListener(event -> {
			LogChoice choice = logs.getSelectedValue();
			if (choice != null && isActive()) {
				ActivityEntryDialog.showDetails(this, controller, choice.log(), choice.entry());
			}
		});
		panel.add(details, BorderLayout.SOUTH);
		return panel;
	}

	/**
	 * Builds explicit selected-channel outcome, operator, time and notes inputs.
	 *
	 * @return check editor panel.
	 */
	private JPanel buildCheck() {
		JPanel form = UiSupport.formPanel();
		channels.getAccessibleContext().setAccessibleName("Channel for communications check");
		checkedBy.getAccessibleContext().setAccessibleName("Communications check recorded by");
		checkedAt.getAccessibleContext().setAccessibleName("Communications check UTC time yyyy-MM-dd HH:mm");
		outcome.getAccessibleContext().setAccessibleName("Communications check outcome");
		UiSupport.addRow(form, 0, "Selected channel", channels);
		UiSupport.addRow(form, 1, "Recorded channel details", new JScrollPane(channelDetails));
		UiSupport.addRow(form, 2, "Explicit outcome", outcome);
		UiSupport.addRow(form, 3, "Checked by", checkedBy);
		UiSupport.addRow(form, 4, "Checked at (UTC, yyyy-MM-dd HH:mm)", checkedAt);
		UiSupport.addRow(form, 5, "Notes", new JScrollPane(notes));
		JButton record = new JButton("Review check + log entry…");
		record.addActionListener(event -> recordCheck());
		JButton reload = new JButton("Reload current channel baseline");
		reload.addActionListener(event -> reloadCheckBaseline());
		JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEADING));
		actions.add(record);
		actions.add(reload);
		UiSupport.addRow(form, 6, "", actions);
		UiSupport.addRow(form, 7, "", feedback);
		JPanel panel = new JPanel(new BorderLayout());
		panel.add(new JScrollPane(form), BorderLayout.CENTER);
		return panel;
	}

	/**
	 * Resolves the selected task against the current model.
	 *
	 * @return live selected task or null.
	 */
	private SarTaskAssignment selectedTask() {
		String id = selectedAssignmentId();
		return controller.getData().getSarTaskAssignments().stream()
				.filter(task -> id.equals(task.getAssignmentId())).findFirst().orElse(null);
	}

	/** Refreshes the selected task's recorded assignment, contacts and stable link statuses. */
	private void refreshTaskDetails() {
		SarTaskAssignment task = selectedTask();
		if (task == null) {
			ResourceAssignment assignment = selectedResourceAssignment();
			String summary = assignment == null
					? "No task filter. Showing all incident contacts; choose Show all log entries to browse activity."
					: "ICS 204 assignment: " + assignment.getAssignment() + "\nTeam: "
							+ assignment.getAssignmentTeamNumber() + "\nResource: " + assignment.getResourceIdentifier()
							+ "\nLeader: " + assignment.getLeader() + "\nContact: " + assignment.getContact()
							+ "\nReporting location: " + assignment.getReportingLocation();
			if (assignment != null) {
				for (ChannelLinkSource.ChannelLink link : ChannelLinkSource.links(controller.getData())) {
					if (isTaskLink(link)) {
						summary += "\n" + link.sourceLabel() + ": " + linkText(link);
					}
				}
			}
			UiSupport.setTextIfChanged(taskDetails, summary);
			return;
		}
		StringBuilder text = new StringBuilder("Assignment: ").append(task.getAssignment())
				.append("\nStatus: ").append(task.getTaskLifecycleStatus())
				.append("\nLeader / team: ").append(SarTaskSupport.taskResourceDisplayLabel(task))
				.append("\nLeader: ").append(task.getLeader()).append("\nContact: ").append(task.getContact())
				.append("\nLocation: ").append(task.getBranch()).append(" / ").append(task.getDivision())
				.append(" / ").append(task.getGroup()).append("\nStaging: ").append(task.getStagingArea());
		for (SarTaskResource resource : task.getResourcesAssigned()) {
			text.append("\nResource: ").append(resource.getName()).append(" — ").append(resource.getFunction());
		}
		for (ChannelLinkSource.ChannelLink link : ChannelLinkSource.links(controller.getData())) {
			if (isTaskLink(link)) {
				text.append("\n").append(link.sourceLabel()).append(": ").append(linkText(link));
			}
		}
		UiSupport.setTextIfChanged(taskDetails, text.toString());
	}

	/** Refreshes contact search without inferring identity from legacy channel text. */
	private void refreshContacts() {
		AppData data = controller.getData();
		String selectedContact = contactChoices.getSelectedValue() == null ? ""
				: contactChoices.getSelectedValue().identity();
		List<ContactChoice> rows = new ArrayList<>();
		String assignmentId = selectedAssignmentId();
		SarTaskAssignment task = selectedTask();
		for (ResourceAssignment assignment : data.getAllAssignmentResources()) {
			if (assignmentId.isBlank() || assignmentId.equals(assignment.getAssignmentId())) {
				rows.add(new ContactChoice("assignment:" + assignment.getAssignmentId(),
						assignment.getResourceIdentifier(), "Assignment resource: " + assignment.getResourceIdentifier()
								+ " | Team: " + assignment.getAssignmentTeamNumber() + " | Leader: "
								+ assignment.getLeader() + " | Contact: " + assignment.getContact()
								+ " | Reporting location: " + assignment.getReportingLocation()));
			}
		}
		java.util.Set<String> scopedResources = new java.util.HashSet<>();
		if (task != null) {
			for (SarTaskResource resource : task.getResourcesAssigned()) {
				if (!resource.getResourceId().isBlank()) {
					scopedResources.add(resource.getResourceId());
				}
				rows.add(new ContactChoice("task:" + assignmentId + "/resource:" + resource.getId(),
						resource.getName(), "Assigned resource: " + resource.getName() + " | Function: "
								+ resource.getFunction()));
			}
		}
		for (TCard card : data.getTCards()) {
			String resourceId = card.peekResourceId();
			boolean uniqueId = resourceId != null && !resourceId.isBlank()
					&& data.getTCards().stream()
							.filter(candidate -> resourceId.equals(candidate.peekResourceId())).count() == 1;
			String sourceRef = card.getSourceRef();
			boolean uniqueSource = !sourceRef.isBlank() && data.getTCards().stream()
					.filter(candidate -> sourceRef.equals(candidate.getSourceRef())).count() == 1;
			boolean inScope = assignmentId.isBlank() || uniqueId && scopedResources.contains(resourceId)
					|| uniqueSource && sourceRef.startsWith("sar:" + assignmentId + ":");
			if (card.getCardType() != TCardType.HEADER && inScope && (uniqueId || uniqueSource)) {
				String prefill = card.getPersonName().isBlank() ? card.getResourceIdentifier() : card.getPersonName();
				String identity = uniqueId ? "tcard:" + resourceId : "tcard-source:" + sourceRef;
				rows.add(new ContactChoice(identity, prefill,
						"T-card " + card.getDisplayLabel() + " | " + card.getResourceIdentifier()
						+ " | Agency: " + card.getHomeAgency() + " | Phone: " + card.getPhoneNumber()
						+ " | Status: " + card.getStatus() + " | Location: " + card.getLocation()
						+ " | Radio: " + linkText(ChannelLinkSource.resolve(data, card.getRadioChannelId(),
								card.getRadioChannel()))));
			}
		}
		for (ChannelLinkSource.ChannelLink link : ChannelLinkSource.links(data)) {
			if (assignmentId.isBlank() || isTaskLink(link)) {
				rows.add(new ContactChoice("link:" + link.sourceKind() + ":" + link.sourceId(), link.sourceLabel(),
						link.sourceKind() + " | " + link.sourceLabel() + " | " + linkText(link)));
			}
		}
		String query = lookup.getText().strip().toLowerCase(Locale.ROOT);
		List<ContactChoice> found = rows.stream()
				.filter(row -> row.label().toLowerCase(Locale.ROOT).contains(query)).toList();
		UiSupport.reconcileList(contactModel, found);
		if (!selectedContact.isBlank()) {
			int selectedIndex = -1;
			for (int index = 0; index < found.size(); index++) {
				if (found.get(index).identity().equals(selectedContact)) {
					selectedIndex = index;
					break;
				}
			}
			if (contactChoices.getSelectedIndex() != selectedIndex) {
				contactChoices.setSelectedIndex(selectedIndex);
			}
		}
		UiSupport.setTextIfChanged(contacts, found.isEmpty() ? "No recorded resource or contact matches."
				: found.stream().map(ContactChoice::label).collect(java.util.stream.Collectors.joining("\n\n")));
	}

	/**
	 * Matches projected sources by their real parent identities, never by presentation labels.
	 * SAR link origins use task IDs; shared ICS 204 communications use their owning form ID.
	 *
	 * @param link detached channel reference.
	 * @return true when the source belongs to the selected task or assignment.
	 */
	private boolean isTaskLink(ChannelLinkSource.ChannelLink link) {
		String assignmentId = selectedAssignmentId();
		if (assignmentId.isBlank()) {
			return false;
		}
		SarTaskAssignment task = selectedTask();
		if (link.sourceKind() == ChannelLinkSource.SourceKind.ICS204_RESOURCE) {
			return assignmentId.equals(link.sourceId());
		}
		if (List.of(ChannelLinkSource.SourceKind.SAR_COMMUNICATION, ChannelLinkSource.SourceKind.SAR_ASSIGNMENT,
				ChannelLinkSource.SourceKind.SAR_RESOURCE, ChannelLinkSource.SourceKind.SAR_MANAGEMENT)
				.contains(link.sourceKind()) && task != null) {
			return hasOrigin(link.sourceId(), task.getTaskId());
		}
		if (link.sourceKind() != ChannelLinkSource.SourceKind.ICS204_COMMUNICATION
				&& link.sourceKind() != ChannelLinkSource.SourceKind.ICS204_MANAGEMENT) {
			return false;
		}
		List<Ics204Form> forms = new ArrayList<>();
		forms.add(controller.getData().getForm204());
		forms.addAll(controller.getData().getAdditionalForms204());
		for (Ics204Form form : forms) {
			if (form.getResourcesAssigned().stream()
					.anyMatch(assignment -> assignmentId.equals(assignment.getAssignmentId()))) {
				if (hasOrigin(link.sourceId(), form.getFormId())) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Matches a projected source against one typed parent identity and its structural children.
	 *
	 * @param sourceId projected source location.
	 * @param parentId stable or explicit legacy structural parent.
	 * @return whether the source has this exact parent.
	 */
	private static boolean hasOrigin(String sourceId, String parentId) {
		return parentId != null && !parentId.isBlank()
				&& (parentId.equals(sourceId) || sourceId.startsWith(parentId + "/"));
	}

	/**
	 * Formats explicit channel resolution and preserves unresolved fallback text.
	 *
	 * @param link detached stable resolution.
	 * @return label with explicit resolution status and identity.
	 */
	private String linkText(ChannelLinkSource.ChannelLink link) {
		String text = link.displayLabel() + " [" + link.status() + "]"
				+ (link.channelId().isBlank() ? "" : " ID: " + link.channelId())
				+ (link.channel() == null || link.legacyFallback().isBlank()
						? "" : " | Recorded contact: " + link.legacyFallback());
		RadioChannel channel = link.channel() == null ? null : uniqueChannel(link.channelId());
		return channel == null ? text : text + " | Check: " + channel.getRadioCheckStatus().displayLabel()
				+ " | Checked by: " + channel.getRadioCheckedBy() + " | Checked at: "
				+ channel.getRadioCheckedAt() + " UTC | Check notes: " + channel.getRadioCheckNotes();
	}

	/** Refreshes recent entries by stable task identity, using the timeline's legacy label fallback. */
	private void refreshLogs() {
		if (selectedAssignmentId().isBlank()) {
			allIncident.setSelected(true);
		}
		TaskChoice scopeTask = tasks.getSelectedValue();
		String scope = allIncident.isSelected() || scopeTask == null ? "All log entries from the ICP log"
				: scopeTask.label();
		if (!scope.equals(logScope.getText())) {
			logScope.setText(scope);
			logScope.setToolTipText(scope);
		}
		ActivityLogEntry selected = logs.getSelectedValue() == null ? null : logs.getSelectedValue().entry();
		List<LogChoice> rows = recentEntries(controller.getData(), allIncident.isSelected() ? null
				: selectedAssignmentId());
		logRefreshState.refresh(controller.getData(), () -> {
			UiSupport.reconcileList(logModel, rows);
			for (int index = 0; index < rows.size(); index++) {
				LogChoice row = rows.get(index);
				if (selected != null && row.entry().getEntryId().equals(selected.getEntryId())) {
					logs.setSelectedIndex(index);
				}
			}
		}, rows.stream().map(row -> List.of(row.entry().getEntryId(), row.toString(),
				System.identityHashCode(row.entry()), System.identityHashCode(row.log()))).toList());
		details.setEnabled(logs.getSelectedValue() != null);
	}

	/**
	 * Projects shared ICP rows in reverse chronological order without changing entries.
	 *
	 * @param data current incident.
	 * @param assignmentId task filter; null means all incident, blank means no selected task.
	 * @return immutable reference projection, not a second activity-log store.
	 */
	static List<LogChoice> recentEntries(AppData data, String assignmentId) {
		List<LogChoice> rows = new ArrayList<>();
		for (Ics214Form log : data.getActivityLogs()) {
			if (log == null || log.getLogScope() != ActivityLogScope.ICP) {
				continue;
			}
			for (ActivityLogEntry entry : log.getActivityLog()) {
				if (entry == null) {
					continue;
				}
				if (assignmentId == null || !assignmentId.isBlank()
						&& assignmentId.equals(associatedAssignmentId(entry))) {
					rows.add(new LogChoice(log, entry, ActivityEntryDialog.resolvedEventTypeLabel(data,
							entry.getEventTypeId())));
				}
			}
		}
		rows.sort(Comparator.comparing((LogChoice choice) -> choice.entry().getTimestamp(),
				Comparator.nullsLast(Comparator.reverseOrder())));
		return List.copyOf(rows);
	}

	/**
	 * Returns the stable assignment link migrated onto an activity entry.
	 *
	 * @param entry shared activity entry.
	 * @return assignment identity or blank.
	 */
	private static String associatedAssignmentId(ActivityLogEntry entry) {
		return entry.getAssignmentId();
	}

	/**
	 * Creates shared-editor context from the selected task.
	 *
	 * @return contextual entry request.
	 */
	private LogEntryContext entryContext() {
		SarTaskAssignment task = selectedTask();
		ResourceAssignment assignment = selectedResourceAssignment();
		ContactChoice contact = contactChoices.getSelectedValue();
		String resource = contact != null ? contact.resource()
				: task != null ? SarTaskSupport.taskResourceDisplayLabel(task)
						: assignment == null ? "" : assignment.getResourceIdentifier();
		return new LogEntryContext(this, task == null ? "" : task.peekTaskId(), selectedAssignmentId(), resource);
	}

	/**
	 * Opens a retained shared editor for a normal log or problem quick action.
	 *
	 * @param eventTypeId initial shared event type.
	 */
	private void openEntry(String eventTypeId) {
		if (!isActive()) {
			return;
		}
		String key = selectedAssignmentId() + "/" + eventTypeId + "/"
				+ (contactChoices.getSelectedValue() == null ? "" : contactChoices.getSelectedValue().identity());
		ActivityEntryDialog editor = entryDrafts.computeIfAbsent(key, ignored -> new ActivityEntryDialog(controller,
				entryContext(), null, eventTypeId, ""));
		if (editor.show(this)) {
			entryDrafts.remove(key);
		}
	}

	/** Retains the current channel's entered fields before selection or live refresh. */
	private void rememberCheckDraft() {
		CheckDraft draft = checkDrafts.get(loadedChannelId);
		if (draft != null) {
			draft.outcome = (RadioCheckStatus) outcome.getSelectedItem();
			draft.by = checkedBy.getText();
			draft.time = checkedAt.getText();
			draft.notes = notes.getText();
		}
	}

	/** Loads a channel-specific draft and a read-only current channel summary. */
	private void loadCheckDraft() {
		loadedChannelId = selectedChannelId();
		RadioChannel channel = uniqueChannel(loadedChannelId);
		if (channel == null) {
			UiSupport.setTextIfChanged(channelDetails,
					"Select a uniquely resolved current channel. Missing / ambiguous IDs cannot be checked.");
			return;
		}
		CheckDraft draft = checkDrafts.computeIfAbsent(loadedChannelId, ignored -> new CheckDraft(channel.deepCopy()));
		if (outcome.getSelectedItem() != draft.outcome) {
			outcome.setSelectedItem(draft.outcome);
		}
		UiSupport.setTextIfChanged(checkedBy, draft.by);
		UiSupport.setTextIfChanged(checkedAt, draft.time);
		UiSupport.setTextIfChanged(notes, draft.notes);
		UiSupport.setTextIfChanged(channelDetails, ChannelLinkSource.channelLabel(channel) + "\nAssignment: " + channel.getAssignment()
				+ "\nRX: " + channel.getRxFrequency() + " | TX: " + channel.getTxFrequency()
				+ "\nCoverage: " + channel.getCoverageGuidance() + "\nRelay: " + channel.getRelayGuidance()
				+ "\nLast check: " + channel.getRadioCheckStatus().displayLabel() + " | "
				+ channel.getRadioCheckedBy() + " | " + channel.getRadioCheckedAt()
				+ "\nNotes: " + channel.getRadioCheckNotes());
	}

	/**
	 * Resolves exactly one channel by stable identity without choosing an ambiguous row.
	 *
	 * @param channelId stable channel identity.
	 * @return unique current channel or null.
	 */
	private RadioChannel uniqueChannel(String channelId) {
		List<RadioChannel> matches = controller.getData().getForm205().getChannels().stream()
				.filter(channel -> !channelId.isBlank() && channelId.equals(channel.getChannelId())).toList();
		return matches.size() == 1 ? matches.get(0) : null;
	}

	/** Opens the shared editor and delegates one accepted check-plus-log transaction. */
	private void recordCheck() {
		if (!isActive()) {
			return;
		}
		rememberCheckDraft();
		CheckDraft draft = checkDrafts.get(loadedChannelId);
		if (draft == null || uniqueChannel(loadedChannelId) == null) {
			feedback.setText("Select a unique current channel before recording a check.");
			return;
		}
		try {
			LocalDateTime time = LocalDateTime.parse(draft.time, CHECK_TIME);
			if (draft.outcome == null || draft.by.isBlank()) {
				throw new IllegalArgumentException("Explicit outcome and checked by are required.");
			}
			String editorKey = selectedAssignmentId() + "/"
					+ (contactChoices.getSelectedValue() == null ? "" : contactChoices.getSelectedValue().identity());
			ActivityEntryDialog editor = draft.editors.get(editorKey);
			if (editor == null) {
				String channelId = loadedChannelId;
				editor = new ActivityEntryDialog(controller, entryContext(), null,
						ActivityEventType.ID_COMMUNICATIONS_CHECK,
						"Communications check for channel " + ChannelLinkSource.channelLabel(draft.baseline)
								+ " [" + loadedChannelId + "].",
						(log, entry) -> {
							CheckAssertion assertion = draft.reviewed;
							entry.setNotableActivity(assertion.summary() + "\n\n"
									+ entry.getNotableActivity());
							controller.commitRadioCheckWithActivity(binding, channelId, assertion.baseline(),
									assertion.outcome(), assertion.by(), assertion.time(), assertion.notes(), log, entry);
							return true;
						});
				draft.editors.put(editorKey, editor);
			}
			String channelId = loadedChannelId;
			draft.reviewed = new CheckAssertion(draft.baseline.deepCopy(), draft.outcome, draft.by, time,
					draft.notes, checkSummary(channelId, draft));
			editor.setCommitSummary(draft.reviewed.summary());
			editor.setDefaultTimestamp(time);
			if (editor.show(this)) {
				checkDrafts.remove(channelId);
				loadCheckDraft();
				feedback.setText("Check and shared ICP activity entry recorded together.");
			} else {
				feedback.setText("Not recorded; entered check and log drafts retained.");
			}
		} catch (RuntimeException exception) {
			feedback.setText("Not recorded: " + exception.getMessage() + " Draft retained.");
		}
	}

	/**
	 * Builds the exact check assertion shown before confirmation and retained in shared log history.
	 *
	 * @param channelId stable checked channel.
	 * @param draft entered check assertion.
	 * @return visible, persisted assertion summary.
	 */
	private static String checkSummary(String channelId, CheckDraft draft) {
		return "Communications check — record channel check and ICP log together\nChannel: "
				+ ChannelLinkSource.channelLabel(draft.baseline) + " [" + channelId + "]\nOutcome: "
				+ draft.outcome.displayLabel() + "\nChecked by: " + draft.by + "\nChecked at: "
				+ draft.time + " UTC\nNotes: " + draft.notes;
	}

	/** Explicitly accepts a current channel baseline without discarding entered check or log drafts. */
	private void reloadCheckBaseline() {
		rememberCheckDraft();
		RadioChannel current = uniqueChannel(loadedChannelId);
		CheckDraft draft = checkDrafts.get(loadedChannelId);
		if (current != null && draft != null && isActive()) {
			draft.baseline = current.deepCopy();
			loadCheckDraft();
			feedback.setText("Current channel baseline loaded. Check and log drafts retained; review before confirming.");
		}
	}

	/**
	 * Prevents writes from disposed or replaced incident views.
	 *
	 * @return true when this console still belongs to the active incident.
	 */
	private boolean isActive() {
		return !disposed && controller.isCurrent(binding);
	}

	/**
	 * Creates a wrapped, accessible text area.
	 *
	 * @param name accessible field name.
	 * @param editable whether operators can enter text.
	 * @return configured text area.
	 */
	private static JTextArea area(String name, boolean editable) {
		JTextArea area = new JTextArea(5, 28);
		area.setEditable(editable);
		area.setLineWrap(true);
		area.setWrapStyleWord(true);
		area.getAccessibleContext().setAccessibleName(name);
		return area;
	}

	/**
	 * Resolves the selected generic assignment independently of SAR scaffold availability.
	 *
	 * @return canonical ICS 204 assignment, or null.
	 */
	private ResourceAssignment selectedResourceAssignment() {
		String id = selectedAssignmentId();
		return controller.getData().getAllAssignmentResources().stream()
				.filter(assignment -> id.equals(assignment.getAssignmentId())).findFirst().orElse(null);
	}

	/**
	 * Selectable recorded contact without inferred channel or assignment identity.
	 *
	 * @param identity stable resource or structural source identity.
	 * @param resource recorded resource prefill for the shared activity editor.
	 * @param label full recorded contact and channel details.
	 */
	private record ContactChoice(String identity, String resource, String label) {
		/** @return visible contact summary. */
		@Override
		public String toString() {
			return label;
		}
	}

	/**
	 * Immutable structured assertion captured before the shared confirmation dialog opens.
	 *
	 * @param baseline detached focused channel baseline.
	 * @param outcome reviewed explicit outcome.
	 * @param by reviewed operator.
	 * @param time reviewed UTC check time.
	 * @param notes reviewed check notes.
	 * @param summary exact visible and persisted assertion.
	 */
	private record CheckAssertion(RadioChannel baseline, RadioCheckStatus outcome, String by,
			LocalDateTime time, String notes, String summary) {
	}

	/**
	 * Detached task-board label keyed by stable identity.
	 *
	 * @param assignmentId stable task identity.
	 * @param label recorded task summary.
	 */
	private record TaskChoice(String assignmentId, String label) {
		/** @return visible board summary. */
		@Override
		public String toString() {
			return label;
		}
	}

	/**
	 * Detached channel choice keyed by stable identity rather than name.
	 *
	 * @param channelId stable channel identity.
	 * @param label channel presentation label.
	 */
	private record ChannelChoice(String channelId, String label) {
		/** @return visible channel label and identity. */
		@Override
		public String toString() {
			return label + " [" + channelId + "]";
		}
	}

	/**
	 * Reference projection into the shared activity log; no log data is copied.
	 *
	 * @param log owning shared log.
	 * @param entry shared entry.
	 * @param eventLabel resolved shared event type label.
	 * @param label detached display text.
	 */
	record LogChoice(Ics214Form log, ActivityLogEntry entry, String eventLabel, String label) {
		/**
		 * Captures a label while retaining the authoritative entry for actions.
		 *
		 * @param log owning log.
		 * @param entry authoritative activity.
		 * @param eventLabel resolved classification.
		 */
		LogChoice(Ics214Form log, ActivityLogEntry entry, String eventLabel) {
			this(log, entry, eventLabel, (entry.isStruckOut() ? "[STRUCK OUT] " : "")
					+ (entry.getTimestamp() == null ? "Undated" : UiSupport.formatDateTime(entry.getTimestamp(), CHECK_TIME))
					+ " | " + eventLabel + " | " + entry.getResourceIdentifier() + " | " + entry.getNotableActivity());
		}

		/** @return timestamp, classification, resource and original narrative. */
		@Override
		public String toString() {
			return label;
		}
	}

	/** Entered, uncommitted check fields and retained detached shared activity editor. */
	private static final class CheckDraft {
		private RadioChannel baseline;
		private RadioCheckStatus outcome;
		private String by = "";
		private String time = UtcTimestamp.now().withSecond(0).withNano(0).format(CHECK_TIME);
		private String notes = "";
		private final Map<String, ActivityEntryDialog> editors = new LinkedHashMap<>();
		private CheckAssertion reviewed;

		/**
		 * Captures the optimistic baseline without modifying the current channel.
		 *
		 * @param baseline detached current channel.
		 */
		private CheckDraft(RadioChannel baseline) {
			this.baseline = baseline;
		}
	}
}
