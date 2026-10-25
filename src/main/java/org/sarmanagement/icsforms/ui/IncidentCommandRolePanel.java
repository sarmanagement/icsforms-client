/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.IapPlanningStage;
import org.sarmanagement.icsforms.model.IncidentObjective;
import org.sarmanagement.icsforms.model.PlanReviewArea;
import org.sarmanagement.icsforms.model.TCard;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
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
	private final JTextArea planProgress = new JTextArea();
	private final DefaultTableModel objectiveModel = readOnlyModel("Objective", "Assessment", "Linked assignments");
	private final JTable objectives = new JTable(objectiveModel);
	private final DefaultTableModel resourceModel = readOnlyModel("Recorded T-card status", "Count");
	private final JTable resources = new JTable(resourceModel);
	private final JLabel approvalStatus = new JLabel();
	private final JTextField approver = new JTextField(20);
	private final JTextField signature = new JTextField(20);
	private final AppController.IncidentChangeSubscription subscription;
	private boolean refreshing;

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
		planningStage.addActionListener(event -> applyPlanningStage());
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
		banner.setText("<html><b>INCIDENT COMMAND</b> | Incident: " + html(data.getIncidentContext().getIncidentName())
				+ " | Operational period: " + html(periodLabel(data))
				+ " | resource counts are recorded states, not readiness certification</html>");
		planningStage.setSelectedItem(data.getIapPlanningStage());
		rebuildObjectives(data);
		rebuildResources(data);
		rebuildProgress(data);
		String approval = data.getForm202().getObjectiveApprovalStatus().displayLabel();
		if (data.getForm202().getApprovedDateTime() != null) {
			approval += " by " + data.getForm202().getApprovedByIncidentCommanderName() + " at "
					+ APPROVAL_TIME.format(data.getForm202().getApprovedDateTime());
		}
		approvalStatus.setText(approval);
		approvalStatus.getAccessibleContext().setAccessibleName("Objective approval status");
		if (approver.getText().isBlank()) {
			String current = data.getForm202().getApprovedByIncidentCommanderName();
			approver.setText(current.isBlank() ? data.getIncidentContext().getCurrentUser() : current);
		}
		if (signature.getText().isBlank()) {
			signature.setText(data.getForm202().getApprovedBySignature());
		}
		refreshing = false;
	}

	/**
	 * Returns the displayed resource count for one exact recorded status.
	 *
	 * @param status exact display status.
	 * @return displayed count, or zero.
	 */
	int displayedResourceCount(String status) {
		for (int row = 0; row < resourceModel.getRowCount(); row++) {
			if (status.equals(resourceModel.getValueAt(row, 0))) {
				return (Integer) resourceModel.getValueAt(row, 1);
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
		header.add(actions, BorderLayout.EAST);
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
		planProgress.setEditable(false);
		planProgress.setLineWrap(true);
		planProgress.setWrapStyleWord(true);
		planProgress.getAccessibleContext().setAccessibleName("Recorded IAP progress summary");
		JSplitPane objectiveSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(objectives),
				new JScrollPane(planProgress));
		objectiveSplit.setResizeWeight(0.6);
		planning.add(objectiveSplit, BorderLayout.CENTER);
		planning.add(buildApprovalPanel(), BorderLayout.SOUTH);

		JPanel resourcePanel = new JPanel(new BorderLayout());
		resourcePanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Recorded resource overview"));
		resources.getAccessibleContext().setAccessibleName("Counts by exact recorded T-card status");
		resourcePanel.add(new JScrollPane(resources), BorderLayout.CENTER);
		resourcePanel.add(new JLabel("Unspecified is not treated as Available; no radio check or readiness is inferred."),
				BorderLayout.SOUTH);

		JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, planning, resourcePanel);
		split.setResizeWeight(0.7);
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
					LocalDateTime.now().withSecond(0).withNano(0));
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
			objectiveModel.addRow(new Object[]{objective.toString(), objective.getAssessment().displayLabel(), links});
		}
	}

	/**
	 * Rebuilds exact recorded T-card status counts without treating presence as
	 * readiness.
	 *
	 * @param data active incident.
	 */
	private void rebuildResources(AppData data) {
		Map<String, Integer> counts = new LinkedHashMap<>();
		for (TCard card : data.getTCards()) {
			if (card.getCardType() == org.sarmanagement.icsforms.model.TCardType.HEADER) {
				continue;
			}
			String status = card.getStatus().trim();
			String label = status.isBlank() ? "Unspecified" : status;
			counts.merge(label, 1, Integer::sum);
		}
		resourceModel.setRowCount(0);
		counts.forEach((status, count) -> resourceModel.addRow(new Object[]{status, count}));
	}

	/**
	 * Rebuilds a facts-only planning summary.
	 *
	 * @param data active incident.
	 */
	private void rebuildProgress(AppData data) {
		int objectiveCount = data.getForm202().getStructuredObjectives().size();
		long linkedAssignments = controller.getAssignmentResources().stream()
				.filter(assignment -> !assignment.getObjectiveIds().isEmpty()).count();
		StringBuilder summary = new StringBuilder();
		summary.append("Recorded planning stage: ").append(data.getIapPlanningStage().displayLabel()).append('\n');
		summary.append("Objectives: ").append(objectiveCount).append("; assignments with objective links: ")
				.append(linkedAssignments).append('/').append(controller.getAssignmentResources().size()).append('\n');
		summary.append("Objective approval: ").append(data.getForm202().getObjectiveApprovalStatus().displayLabel())
				.append('\n');
		summary.append("Independent reviews — Safety: ")
				.append(data.getPlanReviewStatus(PlanReviewArea.SAFETY).displayLabel()).append("; Human Medical: ")
				.append(data.getPlanReviewStatus(PlanReviewArea.HUMAN_MEDICAL).displayLabel())
				.append("; Canine Medical: ")
				.append(data.getPlanReviewStatus(PlanReviewArea.CANINE_MEDICAL).displayLabel())
				.append("; Communications: ")
				.append(data.getPlanReviewStatus(PlanReviewArea.COMMUNICATIONS).displayLabel()).append('\n');
		summary.append("Communications channels recorded: ").append(data.getForm205().getChannels().size())
				.append(". These facts do not assert operational readiness.");
		planProgress.setText(summary.toString());
		planProgress.setCaretPosition(0);
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
		return (start == null ? "unspecified" : APPROVAL_TIME.format(start)) + " — "
				+ (end == null ? "unspecified" : APPROVAL_TIME.format(end));
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
