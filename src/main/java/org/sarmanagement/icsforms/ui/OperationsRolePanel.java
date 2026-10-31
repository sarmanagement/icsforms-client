/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.sarmanagement.icsforms.comms.ChannelLinkSource;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.IncidentObjective;
import org.sarmanagement.icsforms.model.ResourceAssignment;
import org.sarmanagement.icsforms.model.SarTaskAssignment;
import org.sarmanagement.icsforms.model.SarTaskResource;
import org.sarmanagement.icsforms.model.SarTaskSupport;
import org.sarmanagement.icsforms.model.TCard;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Live Operations task board that delegates all direct actions to established
 * assignment, task, clue, log, and debrief workflows.
 */
public class OperationsRolePanel extends JPanel {
	private static final DateTimeFormatter PERIOD_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
	private final AppController controller;
	private final Supplier<ResourceAssignment> newAssignment;
	private final Consumer<String> editTask;
	private final Consumer<String> changeStatus;
	private final Consumer<SarTaskAssignment> addLog;
	private final Runnable recordClue;
	private final Consumer<String> debrief;
	private final Runnable humanReference;
	private final Runnable canineReference;
	private final Runnable openOperationalPlanning;
	private final AppController.IncidentBinding binding;
	private final JLabel banner = new JLabel();
	private final DefaultListModel<TaskChoice> taskModel = new DefaultListModel<>();
	private final JList<TaskChoice> taskBoard = new JList<>(taskModel);
	private final JTextArea detail = new JTextArea();
	private final DefaultListModel<ResourceChoice> resourceModel = new DefaultListModel<>();
	private final JList<ResourceChoice> availableResources = new JList<>(resourceModel);
	private final AppController.IncidentChangeSubscription subscription;

	/**
	 * Creates the Operations panel with callbacks into existing editor workflows.
	 *
	 * @param controller incident controller.
	 * @param newAssignment established normal assignment workflow.
	 * @param editTask established task editor by assignment ID.
	 * @param changeStatus established task lifecycle dialog by assignment ID.
	 * @param addLog established contextual activity-entry workflow.
	 * @param recordClue established clue-entry workflow.
	 * @param debrief established task debrief workflow by assignment ID.
	 * @param humanReference opens the human medical reference.
	 * @param canineReference opens the canine medical reference.
	 */
	public OperationsRolePanel(AppController controller, Supplier<ResourceAssignment> newAssignment,
			Consumer<String> editTask, Consumer<String> changeStatus, Consumer<SarTaskAssignment> addLog,
			Runnable recordClue, Consumer<String> debrief, Runnable humanReference, Runnable canineReference) {
		this(controller, newAssignment, editTask, changeStatus, addLog, recordClue, debrief, humanReference,
				canineReference, () -> {
				});
	}

	/**
	 * Creates the Operations panel and links its operational planning action to the shared editor.
	 *
	 * @param controller incident controller.
	 * @param newAssignment established normal assignment workflow.
	 * @param editTask established task editor by assignment ID.
	 * @param changeStatus established task lifecycle dialog by assignment ID.
	 * @param addLog established contextual activity-entry workflow.
	 * @param recordClue established clue-entry workflow.
	 * @param debrief established task debrief workflow by assignment ID.
	 * @param humanReference opens the human medical reference.
	 * @param canineReference opens the canine medical reference.
	 * @param openOperationalPlanning opens the shared ICS 215 editor.
	 */
	public OperationsRolePanel(AppController controller, Supplier<ResourceAssignment> newAssignment,
			Consumer<String> editTask, Consumer<String> changeStatus, Consumer<SarTaskAssignment> addLog,
			Runnable recordClue, Consumer<String> debrief, Runnable humanReference, Runnable canineReference,
			Runnable openOperationalPlanning) {
		super(new BorderLayout(8, 8));
		this.controller = controller;
		this.newAssignment = newAssignment;
		this.editTask = editTask;
		this.changeStatus = changeStatus;
		this.addLog = addLog;
		this.recordClue = recordClue;
		this.debrief = debrief;
		this.humanReference = humanReference;
		this.canineReference = canineReference;
		this.openOperationalPlanning = openOperationalPlanning;
		binding = controller.captureIncidentBinding();
		add(buildHeader(), BorderLayout.NORTH);
		add(buildBody(), BorderLayout.CENTER);
		taskBoard.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		taskBoard.addListSelectionListener(event -> {
			if (!event.getValueIsAdjusting()) {
				refreshDetail();
			}
		});
		availableResources.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		availableResources.getAccessibleContext()
				.setAccessibleName("Recorded available resources for a new assignment");
		refreshFromModel();
		subscription = controller.addIncidentChangeListener(change -> {
			if (controller.isCurrent(binding)) {
				refreshFromModel();
			}
		});
	}

	/** Releases the live incident listener. */
	public void dispose() {
		subscription.close();
	}

	/** Refreshes the board while retaining task and resource selection identities. */
	public void refreshFromModel() {
		String selectedAssignment = selectedTask() == null ? "" : selectedTask().getAssignmentId();
		List<String> selectedResources = availableResources.getSelectedValuesList().stream()
				.map(ResourceChoice::resourceId).toList();
		AppData data = controller.getData();
		String start = data.getIncidentContext().getOperationalPeriodStart() == null
				? "unspecified"
				: PERIOD_TIME.format(data.getIncidentContext().getOperationalPeriodStart());
		String end = data.getIncidentContext().getOperationalPeriodEnd() == null
				? "unspecified"
				: PERIOD_TIME.format(data.getIncidentContext().getOperationalPeriodEnd());
		RolePanelSupport.banner(banner, "OPERATIONS", data.getIncidentContext().getIncidentName(),
				"Operational period: " + start + " — " + end
						+ " | Recorded status/location, not readiness confirmation");
		taskModel.clear();
		for (SarTaskAssignment task : data.getSarTaskAssignments()) {
			taskModel.addElement(new TaskChoice(task));
		}
		for (int index = 0; index < taskModel.size(); index++) {
			if (selectedAssignment.equals(taskModel.get(index).task().getAssignmentId())) {
				taskBoard.setSelectedIndex(index);
				break;
			}
		}
		if (taskBoard.getSelectedIndex() < 0 && !taskModel.isEmpty()) {
			taskBoard.setSelectedIndex(0);
		}
		resourceModel.clear();
		for (TCard card : data.getTCards()) {
			if ("available".equalsIgnoreCase(card.getStatus())) {
				resourceModel.addElement(new ResourceChoice(card));
			}
		}
		for (int index = 0; index < resourceModel.size(); index++) {
			if (selectedResources.contains(resourceModel.get(index).resourceId())) {
				availableResources.addSelectionInterval(index, index);
			}
		}
		refreshDetail();
	}

	/**
	 * Returns selected assignment identity for focused live-selection tests.
	 *
	 * @return assignment identity, or blank.
	 */
	String selectedAssignmentId() {
		SarTaskAssignment task = selectedTask();
		return task == null ? "" : task.getAssignmentId();
	}

	/**
	 * Builds persistent incident header and actions.
	 *
	 * @return header panel.
	 */
	private JPanel buildHeader() {
		JPanel header = new JPanel(new BorderLayout());
		header.add(banner, BorderLayout.CENTER);
		JPanel actions = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.TRAILING));
		JButton create = new JButton("New assignment…");
		create.addActionListener(event -> createAssignment());
		JButton human = new JButton("Human Medical Reference");
		human.addActionListener(event -> humanReference.run());
		JButton canine = new JButton("Canine Medical Reference");
		canine.addActionListener(event -> canineReference.run());
		JButton planning = new JButton("Operational planning / ICS 215");
		planning.addActionListener(event -> openOperationalPlanning.run());
		actions.add(create);
		actions.add(human);
		actions.add(canine);
		actions.add(planning);
		header.add(actions, BorderLayout.SOUTH);
		return header;
	}

	/**
	 * Builds task board, selected detail/actions, and recorded resource list.
	 *
	 * @return split body.
	 */
	private JSplitPane buildBody() {
		JPanel left = new JPanel(new BorderLayout());
		left.setBorder(javax.swing.BorderFactory.createTitledBorder("Task board"));
		left.add(new JScrollPane(taskBoard), BorderLayout.CENTER);
		JPanel right = new JPanel(new BorderLayout(4, 4));
		right.setBorder(javax.swing.BorderFactory.createTitledBorder("Selected task and direct actions"));
		detail.setEditable(false);
		detail.setLineWrap(true);
		detail.setWrapStyleWord(true);
		right.add(new JScrollPane(detail), BorderLayout.CENTER);
		JPanel actions = new JPanel(new java.awt.GridLayout(0, 2, 4, 4));
		button(actions, "Edit assignment…", () -> withTask(task -> editTask.accept(task.getAssignmentId())));
		button(actions, "Change status…", () -> withTask(task -> changeStatus.accept(task.getAssignmentId())));
		button(actions, "Add log entry…", () -> withTask(addLog));
		button(actions, "Record clue…", recordClue);
		button(actions, "Debrief task…", () -> withTask(task -> debrief.accept(task.getAssignmentId())));
		right.add(actions, BorderLayout.SOUTH);
		JSplitPane tasks = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
		tasks.setResizeWeight(0.35);
		tasks.setDividerLocation(380);
		JPanel resources = new JPanel(new BorderLayout(4, 4));
		resources.setBorder(javax.swing.BorderFactory.createTitledBorder("Recorded available resources"));
		resources.add(new JScrollPane(availableResources), BorderLayout.CENTER);
		resources.add(new JLabel("Select for New assignment; operator must confirm readiness."), BorderLayout.SOUTH);
		JSplitPane body = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tasks, resources);
		body.setResizeWeight(0.72);
		body.setDividerLocation(540);
		body.setPreferredSize(new Dimension(1160, 760));
		return body;
	}

	/**
	 * Adds an action button.
	 *
	 * @param panel target panel.
	 * @param label button label.
	 * @param action action callback.
	 */
	private void button(JPanel panel, String label, Runnable action) {
		JButton button = new JButton(label);
		button.addActionListener(event -> action.run());
		panel.add(button);
	}

	/** Creates a normal assignment and applies explicitly selected recorded resources. */
	private void createAssignment() {
		List<String> resourceIds = availableResources.getSelectedValuesList().stream()
				.map(ResourceChoice::resourceId).toList();
		ResourceAssignment assignment = newAssignment.get();
		if (assignment != null) {
			controller.assignRecordedResources(assignment.getAssignmentId(), resourceIds);
			refreshFromModel();
			taskBoard.setSelectedValue(new TaskChoice(controller.getData().getSarTaskAssignments().stream()
					.filter(task -> assignment.getAssignmentId().equals(task.getAssignmentId())).findFirst()
					.orElse(null)), true);
		}
	}

	/** Refreshes objective, members, communications, hazards, results, and debrief context. */
	private void refreshDetail() {
		SarTaskAssignment task = selectedTask();
		if (task == null) {
			detail.setText("No task selected.");
			return;
		}
		ResourceAssignment assignment = controller.getData().getAllAssignmentResources().stream()
				.filter(row -> task.getAssignmentId().equals(row.getAssignmentId())).findFirst().orElse(null);
		List<String> objectives = new ArrayList<>();
		if (assignment != null) {
			for (String id : assignment.getObjectiveIds()) {
				String text = controller.getData().getForm202().getStructuredObjectives().stream()
						.filter(objective -> id.equals(objective.getObjectiveId())).map(IncidentObjective::getText)
						.findFirst().orElse("[Missing objective " + id + "]");
				objectives.add(text);
			}
		}
		String members = task.getResourcesAssigned().stream().map(SarTaskResource::getName)
				.filter(name -> name != null && !name.isBlank()).collect(java.util.stream.Collectors.joining(", "));
		String communications = ChannelLinkSource.links(controller.getData()).stream()
				.filter(link -> task.getAssignmentId().equals(link.sourceId()))
				.map(link -> link.sourceLabel() + ": "
						+ (link.channel() == null ? link.legacyFallback() + " [" + link.status() + "]"
								: link.channel().label()))
				.collect(java.util.stream.Collectors.joining(", "));
		if (communications.isBlank()) {
			communications = task.getCommunications().stream()
					.map(entry -> entry.getName() + ": " + entry.getPrimaryContact())
					.collect(java.util.stream.Collectors.joining(", "));
		}
		detail.setText("Assignment: " + task.getAssignment() + "\nStatus: " + task.getTaskLifecycleStatus()
				+ "\nObjectives: " + (objectives.isEmpty() ? "[No objective linked]" : String.join("; ", objectives))
				+ "\nLeader/team: " + SarTaskSupport.taskResourceDisplayLabel(task) + "\nMembers/resources: " + members
				+ "\nCommunications: " + communications + "\nHazards: " + task.getHazardsObserved()
				+ "\nReported result: " + task.getDebriefNotes() + "\nDebrief completed: "
				+ task.isDebriefingCompleted());
	}

	/**
	 * Runs an action for the current task.
	 *
	 * @param action task action.
	 */
	private void withTask(Consumer<SarTaskAssignment> action) {
		SarTaskAssignment task = selectedTask();
		if (task != null) {
			action.accept(task);
		}
	}

	/**
	 * Returns selected canonical task.
	 *
	 * @return selected task or null.
	 */
	private SarTaskAssignment selectedTask() {
		TaskChoice choice = taskBoard.getSelectedValue();
		return choice == null ? null : choice.task();
	}

	/**
	 * Task-board label.
	 *
	 * @param task canonical task, or null.
	 */
	private record TaskChoice(SarTaskAssignment task) {
		/** @return board label. */
		@Override
		public String toString() {
			return task == null
					? ""
					: task.getAssignmentTeamNumber() + " | " + task.getTaskLifecycleStatus() + " | "
							+ task.getAssignment();
		}
	}

	/**
	 * Recorded available-resource label.
	 *
	 * @param card canonical T-card.
	 */
	private record ResourceChoice(TCard card) {
		/** @return stable resource identity. */
		private String resourceId() {
			return card.getResourceId();
		}

		/** @return recorded resource label. */
		@Override
		public String toString() {
			return card.getDisplayLabel() + " | status: " + card.getStatus() + " | location: " + card.getLocation();
		}
	}
}
