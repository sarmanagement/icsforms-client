/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.IapPlanningStage;
import org.sarmanagement.icsforms.model.IncidentObjective;
import org.sarmanagement.icsforms.model.ObjectiveApprovalStatus;
import org.sarmanagement.icsforms.model.PlanReviewArea;
import org.sarmanagement.icsforms.model.PlanReviewStatus;
import org.sarmanagement.icsforms.model.TCard;
import org.sarmanagement.icsforms.model.TCardType;
import org.sarmanagement.icsforms.model.UtcTimestamp;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Live Incident Command overview for explicit planning progress, objectives,
 * approval, reviews, and recorded resource states.
 */
public class IncidentCommandRolePanel extends JPanel {
	private static final DateTimeFormatter APPROVAL_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
	private final AppController controller;
	private final Consumer<WorkspaceDestination> navigator;
	private final AppController.IncidentBinding binding;
	private final JLabel banner = new JLabel();
	private final JComboBox<IapPlanningStage> planningStage = new JComboBox<>(IapPlanningStage.values());
	private final JPanel planProgress = new JPanel(new GridLayout(0, 2, 6, 6));
	private final JLabel overallProgress = new JLabel();
	private final Map<String, JLabel> progressBadges = new LinkedHashMap<>();
	private final DefaultTableModel objectiveModel = readOnlyModel("Objective", "Assessment", "Linked assignments");
	private final JTable objectives = new JTable(objectiveModel);
	private final JComboBox<String> resourceGrouping = new JComboBox<>(new String[]{"All resources", "By resource type"});
	private final DefaultTableModel resourceModel = readOnlyModel("Resource group", "Total");
	private final JTable resources = new JTable(resourceModel);
	private final JLabel approvalStatus = new JLabel();
	private final JTextField approver = new JTextField(20);
	private final JTextField signature = new JTextField(20);
	private final AppController.IncidentChangeSubscription subscription;
	private boolean refreshing;
	private boolean loadingApprovalDraft;
	private boolean approvalDraftDirty;

	/**
	 * Creates the Incident Command role panel.
	 *
	 * @param controller incident controller.
	 * @param navigator typed full-workspace navigator.
	 */
	public IncidentCommandRolePanel(AppController controller, Consumer<WorkspaceDestination> navigator) {
		super(new BorderLayout(8, 8));
		this.controller = controller;
		this.navigator = navigator;
		binding = controller.captureIncidentBinding();
		add(buildHeader(), BorderLayout.NORTH);
		add(buildBody(), BorderLayout.CENTER);
		installApprovalDraftListeners();
		planningStage.addActionListener(event -> applyPlanningStage());
		resourceGrouping.addActionListener(event -> rebuildResources(controller.getData()));
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

	/**
	 * Refreshes recorded command facts while retaining approval drafts being typed.
	 */
	public void refreshFromModel() {
		refreshing = true;
		AppData data = controller.getData();
		RolePanelSupport.banner(banner, "INCIDENT COMMAND", data.getIncidentContext().getIncidentName(),
				"Operational period: " + periodLabel(data)
						+ " | Recorded resource states, not readiness certification");
		planningStage.setSelectedItem(data.getIapPlanningStage());
		rebuildObjectives(data);
		rebuildResources(data);
		rebuildProgress(data);
		String approval = data.getForm202().getObjectiveApprovalStatus().displayLabel();
		if (data.getForm202().getApprovedDateTime() != null) {
			approval += " by " + data.getForm202().getApprovedByIncidentCommanderName() + " at "
					+ UiSupport.formatDateTime(data.getForm202().getApprovedDateTime(), APPROVAL_TIME);
		}
		approvalStatus.setText(data.getForm202().getObjectiveApprovalStatus().displayLabel());
		approvalStatus.setToolTipText(approval);
		approvalStatus.getAccessibleContext().setAccessibleDescription(approval);
		approvalStatus.getAccessibleContext().setAccessibleName("Objective approval status");
		if (!approvalDraftDirty) {
			loadingApprovalDraft = true;
			String current = data.getForm202().getApprovedByIncidentCommanderName();
			approver.setText(current.isBlank() ? data.getIncidentContext().getCurrentUser() : current);
			signature.setText(data.getForm202().getApprovedBySignature());
			loadingApprovalDraft = false;
		}
		refreshing = false;
	}

	/** Tracks approval drafts so live changes cannot replace partially typed values. */
	private void installApprovalDraftListeners() {
		DocumentListener listener = new DocumentListener() {
			/** {@inheritDoc} */
			@Override
			public void insertUpdate(DocumentEvent event) {
				markApprovalDraftDirty();
			}

			/** {@inheritDoc} */
			@Override
			public void removeUpdate(DocumentEvent event) {
				markApprovalDraftDirty();
			}

			/** {@inheritDoc} */
			@Override
			public void changedUpdate(DocumentEvent event) {
				markApprovalDraftDirty();
			}
		};
		for (JTextComponent field : new JTextComponent[] { approver, signature }) {
			field.getDocument().addDocumentListener(listener);
		}
	}

	/** Marks approval fields as a user draft unless model values are loading. */
	private void markApprovalDraftDirty() {
		if (!loadingApprovalDraft) {
			approvalDraftDirty = true;
		}
	}

	/**
	 * Returns the displayed resource count for one exact recorded status.
	 *
	 * @param status exact display status.
	 * @return displayed count, or zero.
	 */
	int displayedResourceCount(String status) {
		for (int column = 1; column < resourceModel.getColumnCount() - 1; column++) {
			if (status.equals(resourceModel.getColumnName(column))) {
				int total = 0;
				for (int row = 0; row < resourceModel.getRowCount(); row++) {
					total += (Integer) resourceModel.getValueAt(row, column);
				}
				return total;
			}
		}
		return 0;
	}

	/**
	 * Builds the persistent incident banner and full-editor navigation.
	 *
	 * @return header panel.
	 */
	private JPanel buildHeader() {
		JPanel header = new JPanel(new BorderLayout());
		header.add(banner, BorderLayout.CENTER);
		JPanel actions = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.TRAILING));
		JButton objectivesButton = new JButton("Open full ICS 202");
		objectivesButton.addActionListener(
				event -> navigator.accept(WorkspaceDestination.of(WorkspaceId.ICS_202)));
		JButton resourcesButton = new JButton("Open T-Cards");
		resourcesButton.addActionListener(event -> navigator.accept(WorkspaceDestination.of(WorkspaceId.TCARDS)));
		actions.add(objectivesButton);
		actions.add(resourcesButton);
		header.add(actions, BorderLayout.SOUTH);
		return header;
	}

	/**
	 * Builds planning, objective, approval, and resource sections.
	 *
	 * @return command panel layout.
	 */
	private JSplitPane buildBody() {
		JPanel planning = new JPanel(new BorderLayout(6, 6));
		planning.setBorder(javax.swing.BorderFactory.createTitledBorder("IAP planning cycle and objectives"));
		JPanel stage = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEADING));
		stage.add(new JLabel("Explicit planning stage"));
		planningStage.getAccessibleContext().setAccessibleName("IAP planning-cycle stage");
		stage.add(planningStage);
		planning.add(stage, BorderLayout.NORTH);
		objectives.getAccessibleContext().setAccessibleName("Incident objectives and recorded assessments");
		objectives.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
		objectives.getColumnModel().getColumn(0).setPreferredWidth(640);
		objectives.getColumnModel().getColumn(0).setMinWidth(240);
		objectives.getColumnModel().getColumn(1).setPreferredWidth(120);
		objectives.getColumnModel().getColumn(1).setMaxWidth(150);
		objectives.getColumnModel().getColumn(2).setPreferredWidth(120);
		objectives.getColumnModel().getColumn(2).setMaxWidth(140);
		objectives.setRowHeight(52);
		objectives.setToolTipText("Assignment lifecycle counts are recorded task states, not objective achievement "
				+ "or resource readiness. Missing and conflicting lifecycle records remain explicit.");
		planProgress.getAccessibleContext().setAccessibleName("Recorded IAP progress summary");
		planProgress.setBorder(javax.swing.BorderFactory.createEmptyBorder(6, 6, 6, 6));
		for (String area : List.of("Objectives", "Assignment coverage", "Safety", "Human medical", "Canine medical",
				"Communications")) {
			JLabel badge = new JLabel();
			badge.setBorder(javax.swing.BorderFactory.createCompoundBorder(
					javax.swing.BorderFactory.createEtchedBorder(),
					javax.swing.BorderFactory.createEmptyBorder(4, 6, 4, 6)));
			progressBadges.put(area, badge);
			planProgress.add(badge);
		}
		JPanel progress = new JPanel(new BorderLayout(4, 4));
		overallProgress.setOpaque(true);
		overallProgress.setForeground(Color.BLACK);
		overallProgress.setBorder(javax.swing.BorderFactory.createEmptyBorder(6, 6, 6, 6));
		progress.add(overallProgress, BorderLayout.NORTH);
		progress.add(planProgress, BorderLayout.CENTER);
		JSplitPane objectiveSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(objectives),
				new JScrollPane(progress));
		objectiveSplit.setResizeWeight(0.5);
		objectiveSplit.setDividerLocation(210);
		planning.add(objectiveSplit, BorderLayout.CENTER);
		planning.add(buildApprovalPanel(), BorderLayout.SOUTH);

		JPanel resourcePanel = new JPanel(new BorderLayout());
		resourcePanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Recorded resource overview"));
		resources.getAccessibleContext().setAccessibleName("Unique resources by type and exact recorded status");
		resources.setToolTipText("Each stable resource identity is counted once using its first recorded card; "
				+ "header cards are excluded. Grouping does not change status totals.");
		resources.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		resourceGrouping.getAccessibleContext().setAccessibleName("Resource overview grouping");
		JPanel grouping = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEADING));
		grouping.add(new JLabel("Group by"));
		grouping.add(resourceGrouping);
		resourcePanel.add(grouping, BorderLayout.NORTH);
		resourcePanel.add(new JScrollPane(resources), BorderLayout.CENTER);
		resourcePanel.add(new JLabel("<html>Unspecified is not Available.<br>No radio check or readiness is inferred.</html>"),
				BorderLayout.SOUTH);

		JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, planning, resourcePanel);
		split.setResizeWeight(0.7);
		split.setDividerLocation(760);
		split.setPreferredSize(new Dimension(1120, 680));
		return split;
	}

	/**
	 * Builds explicit objective approval controls.
	 *
	 * @return approval panel.
	 */
	private JPanel buildApprovalPanel() {
		JPanel approval = UiSupport.formPanel();
		UiSupport.addRow(approval, 0, "Objective approval", approvalStatus);
		approver.getAccessibleContext().setAccessibleName("Approving Incident Commander");
		UiSupport.addRow(approval, 1, "Incident Commander", approver);
		signature.getAccessibleContext().setAccessibleName("Incident Commander typed signature");
		UiSupport.addRow(approval, 2, "Typed signature", signature);
		JButton approve = new JButton("Approve current objectives");
		approve.addActionListener(event -> approveObjectives());
		UiSupport.addRow(approval, 3, "", approve);
		return approval;
	}

	/** Applies an operator-selected planning stage without inferring progress. */
	private void applyPlanningStage() {
		if (refreshing) {
			return;
		}
		controller.setIapPlanningStage((IapPlanningStage) planningStage.getSelectedItem());
	}

	/** Records approval against the exact current ordered objective content. */
	private void approveObjectives() {
		try {
			controller.approveCurrentObjectives(approver.getText(), signature.getText(),
					UtcTimestamp.now().withSecond(0).withNano(0));
			approvalDraftDirty = false;
			refreshFromModel();
		} catch (IllegalArgumentException exception) {
			UiSupport.showDocumentMessage(this, exception.getMessage(), "Unable to approve objectives",
					javax.swing.JOptionPane.ERROR_MESSAGE);
		}
	}

	/**
	 * Rebuilds the objective summary using stable assignment links.
	 *
	 * @param data active incident.
	 */
	private void rebuildObjectives(AppData data) {
		objectiveModel.setRowCount(0);
		for (IncidentObjective objective : data.getForm202().getStructuredObjectives()) {
			long links = controller.getAssignmentResources().stream()
					.filter(assignment -> assignment.getObjectiveIds().contains(objective.getObjectiveId())).count();
			String states = IncidentCommandSummary.assignmentStates(data, objective.getObjectiveId());
			String text = "<html>" + html(objective.toString()) + "<br><small>Assignments: "
					+ html(states).replace("; ", "<br>") + "</small></html>";
			objectiveModel.addRow(new Object[]{text, objective.getAssessment().displayLabel(), links});
			objectives.setRowHeight(objectiveModel.getRowCount() - 1,
					Math.max(52, 24 + states.split("; ", -1).length * 18));
		}
	}

	/**
	 * Rebuilds status totals or type breakdowns from unique stable resource
	 * identities, retaining the first recorded card and excluding header cards.
	 *
	 * @param data active incident.
	 */
	private void rebuildResources(AppData data) {
		Map<String, TCard> unique = new LinkedHashMap<>();
		for (TCard card : data.getTCards()) {
			if (card != null && card.getCardType() != TCardType.HEADER) {
				unique.putIfAbsent(card.getResourceId(), card);
			}
		}
		Set<String> statuses = new LinkedHashSet<>();
		Map<String, Map<String, Integer>> groups = new LinkedHashMap<>();
		for (TCard card : unique.values()) {
			String status = card.getStatus().trim();
			String label = status.isBlank() ? "Unspecified" : status;
			statuses.add(label);
			String group = resourceGrouping.getSelectedIndex() == 0 ? "All resources"
					: IncidentCommandSummary.resourceGroup(card);
			groups.computeIfAbsent(group, ignored -> new LinkedHashMap<>()).merge(label, 1, Integer::sum);
		}
		List<String> columns = new java.util.ArrayList<>();
		columns.add("Resource type");
		columns.addAll(statuses);
		columns.add("Total");
		resourceModel.setColumnIdentifiers(columns.toArray());
		resourceModel.setRowCount(0);
		groups.forEach((group, counts) -> {
			List<Object> row = new java.util.ArrayList<>();
			row.add(group);
			statuses.forEach(status -> row.add(counts.getOrDefault(status, 0)));
			row.add(counts.values().stream().mapToInt(Integer::intValue).sum());
			resourceModel.addRow(row.toArray());
		});
		resources.getColumnModel().getColumn(0).setPreferredWidth(185);
		for (int column = 1; column < resources.getColumnCount(); column++) {
			resources.getColumnModel().getColumn(column).setPreferredWidth(110);
		}
	}

	/**
	 * Rebuilds a facts-only planning summary.
	 *
	 * @param data active incident.
	 */
	private void rebuildProgress(AppData data) {
		Set<String> objectiveIds = new LinkedHashSet<>();
		data.getForm202().getStructuredObjectives().stream().filter(objective -> !objective.getText().isBlank())
				.forEach(objective -> objectiveIds.add(objective.getObjectiveId()));
		long linkedAssignments = controller.getAssignmentResources().stream()
				.filter(assignment -> assignment.getObjectiveIds().stream().anyMatch(objectiveIds::contains)).count();
		boolean objectivesRecorded = !objectiveIds.isEmpty();
		boolean objectivesApproved = data.getForm202().getObjectiveApprovalStatus()
				== ObjectiveApprovalStatus.APPROVED_CURRENT;
		long populatedAssignments = IncidentCommandSummary.populatedAssignments(data);
		boolean missing = !objectivesRecorded || populatedAssignments == 0;
		boolean approved = objectivesApproved;
		boolean animalRequired = IncidentCommandSummary.requiresAnimalMedicalPlan(data);
		updateBadge("Objectives", mark(objectivesRecorded) + objectiveIds.size() + " recorded",
				mark(objectivesApproved) + data.getForm202().getObjectiveApprovalStatus().displayLabel(),
				mark(objectivesRecorded) + objectiveIds.size() + " recorded",
				mark(objectivesApproved) + data.getForm202().getObjectiveApprovalStatus().displayLabel(),
				!objectivesRecorded ? RolePanelSupport.MISSING
						: objectivesApproved ? RolePanelSupport.CURRENT : RolePanelSupport.PENDING);
		int assignmentCount = controller.getAssignmentResources().size();
		updateBadge("Assignment coverage", mark(populatedAssignments > 0) + populatedAssignments
				+ " populated assignment rows; " + mark(assignmentCount > 0 && linkedAssignments == assignmentCount)
				+ linkedAssignments + "/" + assignmentCount + " assignments linked to recorded objectives",
				"Links only — not an assessment or approval",
				mark(populatedAssignments > 0) + populatedAssignments + " populated rows",
				mark(assignmentCount > 0 && linkedAssignments == assignmentCount)
						+ linkedAssignments + "/" + assignmentCount + " linked · not approval",
				populatedAssignments == 0 ? RolePanelSupport.MISSING
						: assignmentCount > 0 && linkedAssignments == assignmentCount
								? RolePanelSupport.CURRENT : RolePanelSupport.PENDING);
		for (PlanReviewArea area : PlanReviewArea.values()) {
			String title = switch (area) {
				case SAFETY -> "Safety";
				case HUMAN_MEDICAL -> "Human medical";
				case CANINE_MEDICAL -> "Canine medical";
				case COMMUNICATIONS -> "Communications";
			};
			String review = switch (data.getPlanReviewStatus(area)) {
				case REVIEWED_APPROVED -> "Reviewed-approved — current";
				case CHANGED_SINCE_REVIEW -> "Stale — changed since review";
				case NOT_YET_REVIEWED -> "Not yet reviewed";
			};
			long contentItems = IncidentCommandSummary.recordedContent(data, area);
			boolean current = data.getPlanReviewStatus(area) == PlanReviewStatus.REVIEWED_APPROVED;
			boolean required = area != PlanReviewArea.CANINE_MEDICAL || animalRequired;
			if (required) {
				missing |= contentItems == 0;
				approved &= current;
			}
			updateBadge(title, mark(contentItems > 0) + (contentItems == 0 ? "No content recorded"
					: contentItems + " content items recorded"),
					mark(current) + review + (area == PlanReviewArea.CANINE_MEDICAL
							? required ? " (required — animal resources recorded)" : " (optional — no animal resources recorded)"
							: ""),
					(contentItems == 0 ? "No content recorded" : contentItems + " content items recorded")
							+ (area == PlanReviewArea.CANINE_MEDICAL
							? required ? " · required" : " · optional" : ""),
					mark(current) + review,
					contentItems == 0 ? required ? RolePanelSupport.MISSING : RolePanelSupport.OPTIONAL
							: current ? RolePanelSupport.CURRENT : RolePanelSupport.PENDING);
		}
		String state = missing ? "RED — Required planning content missing"
				: !approved ? "YELLOW — Required content recorded; approval pending or stale"
				: "GREEN — Required content recorded and current approvals";
		overallProgress.setText("<html><b>" + (missing ? "RED — Required content missing"
				: !approved ? "YELLOW — Approval pending or stale" : "GREEN — Content recorded / approvals current")
				+ "</b><br>Recorded content and approvals only —<br>"
				+ "not plan sufficiency or operational readiness.</html>");
		overallProgress.setBackground(missing ? RolePanelSupport.MISSING
				: !approved ? RolePanelSupport.PENDING : RolePanelSupport.CURRENT);
		overallProgress.getAccessibleContext().setAccessibleName("Overall planning status: " + state);
		overallProgress.setToolTipText("Required: objectives, ICS 204 assignment plan, safety, human medical, communications; canine/equine "
				+ "medical only when animal resources are recorded. Approvals must match current content. "
				+ "Populated means at least one substantive row or narrative section (or a nonblank objective), "
				+ "not a validated complete plan. Red: missing required content; yellow: content present but "
				+ "current approval missing; green: required content present with current approvals. "
				+ "Assignments require a substantive populated row; objective links alone do not count as assignment content. "
				+ "No separate assignment approval is inferred. Assignment links and the manually selected planning stage "
				+ "do not certify approval or readiness.");
	}

	/**
	 * Marks an explicitly established fact without inferring a broader status.
	 *
	 * @param recorded whether the fact is established.
	 * @return checkmark or open-circle prefix.
	 */
	private static String mark(boolean recorded) {
		return recorded ? "✓ " : "○ ";
	}

	/**
	 * Updates one compact fact badge without implying plan completeness.
	 *
	 * @param title badge heading.
	 * @param content recorded quantity.
	 * @param state explicit review or approval state.
	 * @param compactContent short recorded quantity.
	 * @param compactState short textual state.
	 * @param color presence and explicit review color, not readiness.
	 */
	private void updateBadge(String title, String content, String state, String compactContent,
			String compactState, Color color) {
		JLabel badge = progressBadges.get(title);
		badge.setText("<html><b>" + html(title) + "</b><br>" + html(compactContent)
				+ "<br>" + html(compactState) + "</html>");
		badge.setOpaque(true);
		badge.setForeground(Color.BLACK);
		badge.setBackground(color);
		badge.getAccessibleContext().setAccessibleName(title + ": " + content + "; " + state);
		badge.setToolTipText(title + ": " + content + "; " + state
				+ ". Recorded content and explicit review only; not sufficiency or operational readiness. "
				+ "A content checkmark means presence, not validated plan completeness. "
				+ "Blank rows, default settings, signatures and page metadata do not count as plan content. "
				+ "Content items are populated plan rows or narrative sections."
				+ ("Assignment coverage".equals(title)
						? " Required ICS 204 assignment presence is separate from objective-link coverage. "
								+ "At least one substantive assignment row is required; generated IDs and objective links alone "
								+ "do not count. Link coverage is checked only when every assignment links to a recorded objective; "
								+ "it is not assignment approval, objective achievement, or operational readiness."
						: ""));
	}

	/**
	 * Creates a noneditable table model.
	 *
	 * @param columns column labels.
	 * @return noneditable model.
	 */
	private static DefaultTableModel readOnlyModel(String... columns) {
		return new DefaultTableModel(columns, 0) {
			private static final long serialVersionUID = 1L;

			/**
			 * Prevents editing summary values.
			 *
			 * @param row row index.
			 * @param column column index.
			 * @return always false.
			 */
			@Override
			public boolean isCellEditable(int row, int column) {
				return false;
			}
		};
	}

	/**
	 * Formats the current operational period.
	 *
	 * @param data active incident.
	 * @return period label.
	 */
	private static String periodLabel(AppData data) {
		LocalDateTime start = data.getIncidentContext().getOperationalPeriodStart();
		LocalDateTime end = data.getIncidentContext().getOperationalPeriodEnd();
		return (start == null ? "unspecified" : UiSupport.formatDateTime(start, APPROVAL_TIME)) + " — "
				+ (end == null ? "unspecified" : UiSupport.formatDateTime(end, APPROVAL_TIME));
	}

	/**
	 * Escapes text embedded in an HTML Swing label.
	 *
	 * @param value plain text.
	 * @return escaped text.
	 */
	private static String html(String value) {
		return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
