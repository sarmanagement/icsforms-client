// SPDX-License-Identifier: GPL-3.0-or-later
package org.sarmanagement.icsforms.ui;

import org.sarmanagement.icsforms.model.ActivityEventType;
import org.sarmanagement.icsforms.model.ActivityLogEntry;
import org.sarmanagement.icsforms.model.ActivityLogScope;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.Ics214Form;
import org.sarmanagement.icsforms.model.IncidentMode;
import org.sarmanagement.icsforms.model.SarTaskAssignment;
import org.sarmanagement.icsforms.model.SarTaskSupport;
import org.sarmanagement.icsforms.model.ResourceAssignment;
import org.sarmanagement.icsforms.model.TCard;
import org.sarmanagement.icsforms.model.TCardType;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared contextual activity-entry workflow that does not save or close its initiating editor.
 */
final class ActivityEntryDialog {
	private final AppController controller;
	private final AppData incident;
	private final JComboBox<LogChoice> destination = new JComboBox<>();
	private final JComboBox<TaskChoice> task = new JComboBox<>();
	private final Ics214Panel.ActivityEntryEditor editor;
	private final JPanel panel = new JPanel(new BorderLayout(8, 8));

	/**
	 * Builds a detached editor with mutable destination, task, and resource choices.
	 *
	 * @param controller incident controller
	 * @param context initiating context
	 * @param preferredLog preferred destination, or null to select ICP
	 */
	ActivityEntryDialog(AppController controller, LogEntryContext context, Ics214Form preferredLog) {
		this.controller = controller;
		AppData data = controller.getData();
		incident = data;
		LogChoice preferred = null;
		for (Ics214Form log : data.getActivityLogs()) {
			LogChoice choice = new LogChoice(log);
			destination.addItem(choice);
			if (log == preferredLog || (preferredLog == null && preferred == null
					&& log.getLogScope() == ActivityLogScope.ICP)) {
				preferred = choice;
			}
		}
		if (preferred == null && preferredLog != null) {
			preferred = new LogChoice(preferredLog);
			destination.addItem(preferred);
		}
		if (preferred == null && preferredLog == null) {
			Ics214Form log = new Ics214Form();
			log.setName("ICP");
			preferred = new LogChoice(log);
			destination.addItem(preferred);
		}
		if (preferred != null) {
			destination.setSelectedItem(preferred);
		}
		List<String> resources = new ArrayList<>();
		task.addItem(new TaskChoice(null, null));
		java.util.Set<String> representedAssignments = new java.util.HashSet<>();
		String assignmentId = context.assignmentId();
		if ((assignmentId == null || assignmentId.isBlank()) && preferredLog != null) {
			assignmentId = preferredLog.getLinkedSarTaskAssignmentId();
		}
		for (SarTaskAssignment assignment : data.getSarTaskAssignments()) {
			TaskChoice choice = new TaskChoice(assignment, null);
			task.addItem(choice);
			representedAssignments.add(assignment.getAssignmentId());
			resources.add(SarTaskSupport.taskResourceDisplayLabel(assignment));
			if (assignment.getAssignmentId().equals(assignmentId)
					|| (assignmentId == null || assignmentId.isBlank())
					&& (assignment.getTaskId().equals(context.taskId())
					|| SarTaskSupport.taskResourceDisplayLabel(assignment).equals(context.resourceId())
					|| !assignment.getResourceIdentifier().isBlank()
					&& assignment.getResourceIdentifier().equals(context.resourceId()))) {
				task.setSelectedItem(choice);
			}
		}
		for (ResourceAssignment assignment : controller.getAssignmentResources()) {
			if (!representedAssignments.add(assignment.getAssignmentId())) {
				continue;
			}
			TaskChoice choice = new TaskChoice(null, assignment);
			task.addItem(choice);
			if (!assignment.getResourceIdentifier().isBlank()
					&& !resources.contains(assignment.getResourceIdentifier())) {
				resources.add(assignment.getResourceIdentifier());
			}
			if (assignment.getAssignmentId().equals(assignmentId)
					|| (assignmentId == null || assignmentId.isBlank())
					&& !assignment.getResourceIdentifier().isBlank()
					&& assignment.getResourceIdentifier().equals(context.resourceId())) {
				task.setSelectedItem(choice);
			}
		}
		for (TCard card : data.getTCards()) {
			if (card.getCardType() != TCardType.HEADER) {
				String name = card.getPersonName().isBlank() ? card.getResourceIdentifier() : card.getPersonName();
				if (!name.isBlank() && !resources.contains(name)) {
					resources.add(name);
				}
			}
		}
		String resource = context.resourceId();
		if (resource == null || resource.isBlank()) {
			resource = ((TaskChoice) task.getSelectedItem()).resourceIdentifier();
		}
		List<ActivityEventType> types = new ArrayList<>(data.getActivityEventTypes());
		if (types.isEmpty()) {
			types.addAll(ActivityEventType.defaultTypes());
		}
		if (controller.getIncidentMode() != IncidentMode.SAR) {
			types.removeIf(type -> isClueType(type.getId()));
		}
		editor = new Ics214Panel.ActivityEntryEditor(types, resource, resources, this::isIcpDestination);
		JPanel selectors = UiSupport.formPanel();
		UiSupport.addRow(selectors, 0, "Destination log", destination);
		UiSupport.addRow(selectors, 1, "Task / assignment", task);
		UiSupport.configureDialogComboBox(destination, 300);
		UiSupport.configureDialogComboBox(task, 300);
		panel.add(selectors, BorderLayout.NORTH);
		panel.add(editor.panel, BorderLayout.CENTER);
		destination.addActionListener(event -> editor.refreshEventTypeDisplay());
		task.addActionListener(event -> {
			TaskChoice choice = (TaskChoice) task.getSelectedItem();
			if (choice.assignment() != null || choice.resourceAssignment() != null) {
				editor.resourceIdentifierField.setSelectedItem(choice.resourceIdentifier());
			}
		});
	}

	/**
	 * Determines whether the currently selected destination log is scoped to the ICP.
	 *
	 * @return true when the selected destination log is an ICP log
	 */
	private boolean isIcpDestination() {
		LogChoice choice = (LogChoice) destination.getSelectedItem();
		return choice != null && choice.log() != null && choice.log().getLogScope() == ActivityLogScope.ICP;
	}

	/**
	 * Runs the workflow, committing only after all required dialogs are accepted.
	 *
	 * @param owner initiating editor or panel
	 * @return true when a complete entry was added
	 */
	boolean show(Component owner) {
		if (controller.getData() != incident) {
			return false;
		}
		JScrollPane scroll = new JScrollPane(panel);
		scroll.setBorder(BorderFactory.createEmptyBorder());
		if (!UiSupport.showResizableConfirmDialog(owner, "Add log entry", scroll, new Dimension(680, 460))) {
			return false;
		}
		if (controller.getData() != incident) {
			return false;
		}
		ActivityLogEntry entry = editor.toEntry();
		return saveEntry(owner, entry, candidate -> captureClue(owner, candidate));
	}

	/**
	 * Captures required clue details using the initiating editor as dialog owner.
	 *
	 * @param owner initiating component
	 * @param entry detached activity entry
	 * @return whether the clue was accepted
	 */
	private boolean captureClue(Component owner, ActivityLogEntry entry) {
		Ics214Panel clueEditor = new Ics214Panel(controller);
		clueEditor.loadFromModel(selectedLog(), incident);
		return clueEditor.captureClue(owner, entry, entry.getEventTypeId(),
				selectedAssignmentId());
	}

	/**
	 * Commits an accepted entry only after any required clue workflow succeeds.
	 *
	 * @param owner initiating component
	 * @param entry detached entry
	 * @param clueCapture required clue capture operation
	 * @return true when the activity was committed
	 */
	boolean saveEntry(Component owner, ActivityLogEntry entry,
			java.util.function.Predicate<ActivityLogEntry> clueCapture) {
		if (controller.getData() != incident) {
			return false;
		}
		Ics214Form log = selectedLog();
		SarTaskAssignment assignment = selectedTask();
		if (controller.getIncidentMode() == IncidentMode.SAR && isClueType(entry.getEventTypeId())) {
			if (!clueCapture.test(entry)) {
				return false;
			}
		}
		if (controller.getData() != incident) {
			return false;
		}
		entry.setAssignmentId(selectedAssignmentId());
		Ics214Form lifecycleContext = log;
		if (assignment != null) {
			lifecycleContext = new Ics214Form();
			lifecycleContext.setLinkedSarTaskAssignmentId(assignment.getAssignmentId());
		}
		log.getActivityLog().add(entry);
		if (!controller.getData().getActivityLogs().contains(log)) {
			controller.getData().getActivityLogs().add(log);
		}
		AppController.TaskLifecycleChangeResult result = controller.getIncidentMode() == IncidentMode.SAR
				? controller.applyTaskLifecycleFromLogEntry(lifecycleContext, entry)
				: new AppController.TaskLifecycleChangeResult(false, "");
		controller.markDirty();
		if (result.hasWarning()) {
			UiSupport.showDocumentMessage(owner, result.warningMessage(), "SAR Task Status", JOptionPane.WARNING_MESSAGE);
		}
		return true;
	}

	/**
	 * Returns the selected log without mutating the incident.
	 *
	 * @return selected destination
	 */
	Ics214Form selectedLog() {
		return ((LogChoice) destination.getSelectedItem()).log();
	}

	/**
	 * Returns the task explicitly selected for this entry.
	 *
	 * @return selected task, or null for no task
	 */
	SarTaskAssignment selectedTask() {
		return ((TaskChoice) task.getSelectedItem()).assignment();
	}

	/**
	 * Returns the selected assignment identity in either SAR or Generic mode.
	 *
	 * @return assignment identifier, or an empty string when none is selected
	 */
	String selectedAssignmentId() {
		return ((TaskChoice) task.getSelectedItem()).assignmentId();
	}

	/**
	 * Resolves ownership after a save-and-log initiating editor has closed.
	 *
	 * @param source initiating editor content
	 * @param fallback main incident window when no document root remains visible
	 * @return visible source, its independent document root, or the fallback
	 */
	static Component resolveOwner(Component source, Component fallback) {
		if (source != null && source.isShowing()) {
			return source;
		}
		java.awt.Window window = source instanceof java.awt.Window owner ? owner
				: source == null ? null : javax.swing.SwingUtilities.getWindowAncestor(source);
		java.awt.Window root = UiSupport.documentRoot(window);
		return root != null && root.isShowing() ? root : fallback;
	}

	/**
	 * Identifies event types that require SAR clue capture.
	 *
	 * @param id event identifier
	 * @return whether the event is clue-related
	 */
	private static boolean isClueType(String id) {
		return ActivityEventType.ID_CLUE_DETECTED.equals(id) || ActivityEventType.ID_CLUE_REPORTED.equals(id);
	}

	/**
	 * Resolves the display label for an event type identifier.
	 *
	 * @param data incident data containing configured event types
	 * @param eventTypeId event type identifier
	 * @return human-readable event type label
	 */
	static String resolvedEventTypeLabel(AppData data, String eventTypeId) {
		String id = eventTypeId == null ? ActivityEventType.ID_FREE_TEXT : eventTypeId;
		List<ActivityEventType> types = data != null && data.getActivityEventTypes() != null
				&& !data.getActivityEventTypes().isEmpty()
				? data.getActivityEventTypes()
				: ActivityEventType.defaultTypes();
		for (ActivityEventType type : types) {
			if (type.getId().equals(id)) {
				return type.getLabel();
			}
		}
		return id;
	}

	/**
	 * Builds the details panel displaying the fields of an activity log entry.
	 *
	 * @param incident incident data containing configured event types
	 * @param entry activity log entry to display
	 * @return panel containing read-only entry details
	 */
	static JPanel buildDetailsPanel(AppData incident, ActivityLogEntry entry) {
		JPanel details = UiSupport.formPanel();
		UiSupport.addRow(details, 0, "Date/time", new JLabel(String.valueOf(entry.getTimestamp())));
		UiSupport.addRow(details, 1, "Event type",
				new JLabel(resolvedEventTypeLabel(incident, entry.getEventTypeId())));
		UiSupport.addRow(details, 2, "Resource", new JLabel(entry.getResourceIdentifier()));
		javax.swing.JTextArea text = UiSupport.textArea(6);
		text.setText(entry.getNotableActivity());
		text.setEditable(false);
		UiSupport.addRow(details, 3, "Notable activity", new JScrollPane(text));
		return details;
	}

	/**
	 * Presents an immutable, read-only activity entry with a same-context follow-up action.
	 *
	 * @param owner initiating component
	 * @param controller incident controller
	 * @param log source log
	 * @param entry existing entry
	 * @return whether a follow-up entry was added
	 */
	static boolean showDetails(Component owner, AppController controller, Ics214Form log, ActivityLogEntry entry) {
		AppData incident = controller.getData();
		JPanel details = buildDetailsPanel(incident, entry);
		int choice = UiSupport.showResizableOptionDialog(owner, "Activity entry details", new JScrollPane(details),
				new Dimension(640, 340), new String[]{"Add another entry", "Close"}, "Close");
		if (choice != 0 || controller.getData() != incident) {
			return false;
		}
		return new ActivityEntryDialog(controller, followUpContext(owner, log, entry), log)
				.show(owner);
	}

	/**
	 * Resolves persisted assignment context before legacy log/resource heuristics.
	 *
	 * @param owner initiating component
	 * @param log existing entry's destination
	 * @param entry activity supplying task and resource context
	 * @return follow-up context preserving the entry's stable assignment identity
	 */
	static LogEntryContext followUpContext(Component owner, Ics214Form log, ActivityLogEntry entry) {
		String assignmentId = entry.getAssignmentId();
		if (assignmentId.isBlank()) {
			assignmentId = log.getLinkedSarTaskAssignmentId();
		}
		return new LogEntryContext(owner, "", assignmentId, entry.getResourceIdentifier());
	}

	/**
	 * Presents an operational confirmation using shared document-modal support.
	 *
	 * @param owner initiating component
	 * @param message confirmation content
	 * @param title dialog title
	 * @param optionType standard JOptionPane confirmation option type
	 * @return selected option index, or -1 when dismissed
	 */
	static int showDocumentConfirm(Component owner, Object message, String title, int optionType) {
		return showDocumentConfirm(owner, message, title, optionType, JOptionPane.PLAIN_MESSAGE);
	}

	/**
	 * Presents operational content without blocking independent document roots.
	 *
	 * @param owner initiating component
	 * @param message confirmation content
	 * @param title dialog title
	 * @param optionType standard JOptionPane confirmation option type
	 * @param messageType standard JOptionPane message severity
	 * @return selected option index, or -1 when dismissed
	 */
	static int showDocumentConfirm(Component owner, Object message, String title, int optionType, int messageType) {
		Object[] options = optionType == JOptionPane.YES_NO_OPTION
				? new String[]{"Yes", "No"} : new String[]{"OK", "Cancel"};
		JOptionPane content = new JOptionPane(message, messageType, JOptionPane.DEFAULT_OPTION, null, new Object[0]);
		return UiSupport.showResizableOptionDialog(owner, title, content, null, options, options[0]);
	}

	/**
	 * Display wrapper for a destination log.
	 *
	 * @param log destination
	 */
	private record LogChoice(Ics214Form log) {
		/** {@inheritDoc} */
		@Override
		public String toString() {
			return log.getLogScope() + ": " + log.getName();
		}
	}

	/**
	 * Display wrapper for an optional task assignment.
	 *
	 * @param assignment SAR task, when available
	 * @param resourceAssignment ICS 204 assignment without a corresponding SAR task
	 */
	private record TaskChoice(SarTaskAssignment assignment, ResourceAssignment resourceAssignment) {
		/**
		 * Returns the stable selected assignment identity.
		 *
		 * @return assignment identity or an empty string for no selection
		 */
		String assignmentId() {
			return assignment != null ? assignment.getAssignmentId()
					: resourceAssignment != null ? resourceAssignment.getAssignmentId() : "";
		}

		/**
		 * Returns the resource label for the selected assignment.
		 *
		 * @return canonical SAR label or Generic resource identifier
		 */
		String resourceIdentifier() {
			return assignment != null ? SarTaskSupport.taskResourceDisplayLabel(assignment)
					: resourceAssignment != null ? resourceAssignment.getResourceIdentifier() : "";
		}

		/** {@inheritDoc} */
		@Override
		public String toString() {
			if (assignment != null) {
				return SarTaskSupport.taskResourceDisplayLabel(assignment);
			}
			if (resourceAssignment != null) {
				String team = resourceAssignment.getAssignmentTeamNumber();
				return team.isBlank() ? resourceIdentifier() : team + ": " + resourceIdentifier();
			}
			return "(No task / assignment)";
		}
	}
}
