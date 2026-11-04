/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.Ics202ReportedResult;
import org.sarmanagement.icsforms.model.IapPlanningStage;
import org.sarmanagement.icsforms.model.IncidentObjective;
import org.sarmanagement.icsforms.model.ObjectiveAssessment;
import org.sarmanagement.icsforms.model.ObjectiveApprovalStatus;
import org.sarmanagement.icsforms.model.PlanReviewArea;
import org.sarmanagement.icsforms.model.PlanReviewStatus;
import org.sarmanagement.icsforms.model.ResourceAssignment;
import org.sarmanagement.icsforms.model.SarTaskAssignment;
import org.sarmanagement.icsforms.model.TCard;
import org.sarmanagement.icsforms.model.TCardType;
import org.sarmanagement.icsforms.model.UtcTimestamp;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTabbedPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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
	interface AssignmentActions {
		/** Opens the matching ICS 204 resource detail. */
		void openIcs204(String assignmentId);

		/** Opens the matching SAR task editor. */
		void openSarTask(String assignmentId);

		/** Opens the shared task-linked activity entry workflow. */
		void addLogEntry(String assignmentId);

		/** Opens the established SAR task status workflow. */
		void changeStatus(String assignmentId);

		/** Opens the established SAR task debrief workflow. */
		void debrief(String assignmentId);

		/** Creates and saves a normal ICS 204 assignment prelinked to an objective. */
		default ResourceAssignment createSupportingAssignment(String objectiveId) {
			return null;
		}
	}

	private static final DateTimeFormatter APPROVAL_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
	private final AppController controller;
	private final Consumer<WorkspaceDestination> navigator;
	private final AssignmentActions assignmentActions;
	private final AppController.IncidentBinding binding;
	private final JLabel banner = new JLabel();
	private final JComboBox<IapPlanningStage> planningStage = new JComboBox<>(IapPlanningStage.values());
	private final JPanel planProgress = new JPanel(new java.awt.GridBagLayout());
	private final JLabel overallProgress = new JLabel();
	private final Map<String, JLabel> progressBadges = new LinkedHashMap<>();
	private final DefaultTableModel objectiveModel = readOnlyModel("Objective", "n", "Linked assignments", "Assessment");
	private final JTable objectives = new JTable(objectiveModel);
	private TableColumn objectiveAssessmentColumn;
	private boolean showObjectiveAssessments;
	private JSplitPane objectiveSplit;
	private final JTextArea commandEmphasis = contextDisplay();
	private final JTextArea situationalAwareness = contextDisplay();
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
		this(controller, navigator, null);
	}

	/**
	 * Creates the Incident Command panel with linked-assignment operations.
	 *
	 * @param controller incident controller.
	 * @param navigator typed full-workspace navigator.
	 * @param assignmentActions callbacks for existing assignment workflows.
	 */
	IncidentCommandRolePanel(AppController controller, Consumer<WorkspaceDestination> navigator,
			AssignmentActions assignmentActions) {
		super(new BorderLayout(8, 8));
		this.controller = controller;
		this.navigator = navigator;
		this.assignmentActions = assignmentActions;
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
		commandEmphasis.setText(data.getForm202().getCommandEmphasis());
		situationalAwareness.setText(data.getForm202().getSituationalAwareness());
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
		JPanel header = new JPanel(new BorderLayout(8, 0));
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
		stage.add(new JLabel("Planning stage"));
		resourceGrouping.setSelectedIndex(1);
		planningStage.getAccessibleContext().setAccessibleName("IAP planning-cycle stage");
		planningStage.setPreferredSize(new Dimension(145, planningStage.getPreferredSize().height));
		stage.add(planningStage);
		objectives.getAccessibleContext().setAccessibleName("Incident objectives and recorded assessments");
		objectives.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
		objectives.getColumnModel().getColumn(0).setPreferredWidth(640);
		objectives.getColumnModel().getColumn(0).setMinWidth(240);
		objectives.getColumnModel().getColumn(1).setPreferredWidth(34);
		objectives.getColumnModel().getColumn(1).setMinWidth(30);
		objectives.getColumnModel().getColumn(1).setMaxWidth(42);
		objectives.getColumnModel().getColumn(2).setPreferredWidth(250);
		objectives.getColumnModel().getColumn(2).setMinWidth(160);
		objectives.getColumnModel().getColumn(3).setPreferredWidth(120);
		objectives.getColumnModel().getColumn(3).setMaxWidth(140);
		objectives.setRowHeight(42);
		DefaultTableCellRenderer objectiveRenderer = new DefaultTableCellRenderer();
		objectiveRenderer.setVerticalAlignment(SwingConstants.TOP);
		objectives.getColumnModel().getColumn(0).setCellRenderer(objectiveRenderer);
		objectives.getColumnModel().getColumn(2).setCellRenderer(new DefaultTableCellRenderer() {
			/** {@inheritDoc} */
			@Override
			public java.awt.Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
					boolean focused, int row, int column) {
				java.awt.Component component = super.getTableCellRendererComponent(table, value, selected, focused,
						row, column);
				if (value != null && value.toString().contains("<b>None</b>")) {
					component.setBackground(new Color(255, 232, 175));
					component.setForeground(Color.BLACK);
					((JLabel) component).setToolTipText("No linked assignments");
				} else {
					component.setBackground(selected ? table.getSelectionBackground() : table.getBackground());
					component.setForeground(selected ? table.getSelectionForeground() : table.getForeground());
					((JLabel) component).setToolTipText(value == null ? null : value.toString());
				}
				return component;
			}
		});
		objectiveAssessmentColumn = objectives.getColumnModel().getColumn(3);
		setObjectiveAssessmentColumnVisible(false);
		objectives.addComponentListener(new java.awt.event.ComponentAdapter() {
			/** {@inheritDoc} */
			@Override
			public void componentResized(java.awt.event.ComponentEvent event) {
				updateObjectiveRowHeights();
			}
		});
		objectives.setToolTipText("Assignment lifecycle counts are recorded task states, not objective achievement "
				+ "or resource readiness. Missing and conflicting lifecycle records remain explicit.");
		JPopupMenu objectiveMenu = new JPopupMenu();
		JMenuItem addObjectiveItem = new JMenuItem("Add objective");
		addObjectiveItem.addActionListener(event -> editObjective(null));
		objectiveMenu.add(addObjectiveItem);
		JMenuItem editObjectiveItem = new JMenuItem("Edit objective");
		editObjectiveItem.addActionListener(event -> editSelectedObjective());
		objectiveMenu.add(editObjectiveItem);
		JMenuItem assignmentsItem = new JMenuItem("Linked work");
		assignmentsItem.addActionListener(event -> showSelectedObjectiveAssignments());
		objectiveMenu.add(assignmentsItem);
		objectiveMenu.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
			/** {@inheritDoc} */
			@Override
			public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent event) {
				boolean selected = objectives.getSelectedRow() >= 0;
				editObjectiveItem.setEnabled(selected);
				assignmentsItem.setEnabled(selected);
			}

			/** {@inheritDoc} */
			@Override
			public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent event) {
			}

			/** {@inheritDoc} */
			@Override
			public void popupMenuCanceled(javax.swing.event.PopupMenuEvent event) {
			}
		});
		objectives.setComponentPopupMenu(objectiveMenu);
		objectives.addMouseListener(new MouseAdapter() {
			/** {@inheritDoc} */
			@Override
			public void mousePressed(MouseEvent event) {
				selectObjectiveAtEvent(event);
			}

			/** {@inheritDoc} */
			@Override
			public void mouseReleased(MouseEvent event) {
				selectObjectiveAtEvent(event);
			}

			/** {@inheritDoc} */
			@Override
			public void mouseClicked(MouseEvent event) {
				if (event.getClickCount() == 2 && javax.swing.SwingUtilities.isLeftMouseButton(event)) {
					selectObjectiveAtEvent(event);
					editSelectedObjective();
				}
			}
		});
		JButton editObjective = new JButton("Edit objective");
		editObjective.addActionListener(event -> editSelectedObjective());
		JButton linkedWork = new JButton("Linked work");
		linkedWork.addActionListener(event -> showSelectedObjectiveAssignments());
		JButton addObjective = new JButton("Add objective");
		addObjective.addActionListener(event -> editObjective(null));
		java.awt.Insets actionButtonInsets = new java.awt.Insets(2, 4, 2, 4);
		editObjective.setMargin(actionButtonInsets);
		linkedWork.setMargin(actionButtonInsets);
		addObjective.setMargin(actionButtonInsets);
		editObjective.setEnabled(false);
		linkedWork.setEnabled(false);
		stage.add(addObjective);
		stage.add(editObjective);
		stage.add(linkedWork);
		objectives.getSelectionModel().addListSelectionListener(event -> {
			if (!event.getValueIsAdjusting()) {
				boolean selected = objectives.getSelectedRow() >= 0;
				editObjective.setEnabled(selected);
				linkedWork.setEnabled(selected);
			}
		});
		planning.add(stage, BorderLayout.NORTH);
		JButton editContext = new JButton("Edit command emphasis and situational awareness");
		editContext.addActionListener(event -> editIcs202Context());
		JPanel objectiveTablePanel = new JPanel(new BorderLayout(0, 4));
		objectiveTablePanel.add(new JScrollPane(objectives), BorderLayout.CENTER);
		planProgress.getAccessibleContext().setAccessibleName("Recorded IAP progress summary");
		planProgress.setBorder(javax.swing.BorderFactory.createEmptyBorder(2, 4, 2, 4));
		for (String area : List.of("Objectives", "Assignment coverage", "Communications", "Safety", "Human medical",
				"Canine medical")) {
			JLabel badge = new JLabel();
			badge.setBorder(javax.swing.BorderFactory.createCompoundBorder(
					javax.swing.BorderFactory.createEtchedBorder(),
					javax.swing.BorderFactory.createEmptyBorder(2, 4, 2, 4)));
			progressBadges.put(area, badge);
			java.awt.GridBagConstraints constraints = new java.awt.GridBagConstraints();
			constraints.gridx = progressBadges.size() % 2;
			constraints.gridy = (progressBadges.size() - 1) / 2;
			constraints.weightx = 1;
			constraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
			constraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
			constraints.insets = new java.awt.Insets(1, 2, 1, 2);
			planProgress.add(badge, constraints);
		}
		planProgress.add(new JPanel(), progressSpacerConstraints(0, 3));
		planProgress.add(new JPanel(), progressSpacerConstraints(1, 3));
		JPanel progress = new JPanel(new BorderLayout(4, 4));
		progress.add(planProgress, BorderLayout.CENTER);
		objectiveSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, objectiveTablePanel, progress);
		objectiveSplit.setResizeWeight(1.0);
		objectiveSplit.addComponentListener(new java.awt.event.ComponentAdapter() {
			/** {@inheritDoc} */
			@Override
			public void componentResized(java.awt.event.ComponentEvent event) {
				fitProgressSection();
			}
		});
		objectiveSplit.setDividerLocation(0.62);
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
		JScrollPane resourceScroll = new JScrollPane(resources);
		resourceScroll.setPreferredSize(new Dimension(resourceScroll.getPreferredSize().width,
				resources.getTableHeader().getPreferredSize().height + 10 * resources.getRowHeight()));
		resourcePanel.add(resourceScroll, BorderLayout.CENTER);
		resourcePanel.add(new JLabel("<html>Unspecified is not Available.<br>No radio check or readiness is inferred.</html>"),
				BorderLayout.SOUTH);

		JPanel contextPanel = new JPanel(new BorderLayout(4, 4));
		contextPanel.getAccessibleContext().setAccessibleName("ICS 202 command emphasis and situational awareness");
		JPanel contextActions = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.TRAILING, 0, 0));
		contextActions.add(editContext);
		contextPanel.add(contextActions, BorderLayout.NORTH);
		JPanel contextDetails = new JPanel(new GridLayout(0, 1, 4, 4));
		contextDetails.add(contextSection("Command emphasis", commandEmphasis));
		contextDetails.add(contextSection("General situational awareness", situationalAwareness));
		contextPanel.add(contextDetails, BorderLayout.CENTER);
		JSplitPane resourceColumn = new JSplitPane(JSplitPane.VERTICAL_SPLIT, contextPanel, resourcePanel);
		resourceColumn.setResizeWeight(0.72);
		resourceColumn.setDividerLocation(0.72);

		JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, planning, resourceColumn);
		split.setResizeWeight(0.7);
		split.setDividerLocation(760);
		split.setPreferredSize(new Dimension(1120, 680));
		return split;
	}

	/**
	 * Creates a read-only, wrapped display for current ICS 202 context.
	 *
	 * @return text area for one ICS 202 context value.
	 */
	private static JTextArea contextDisplay() {
		JTextArea display = new JTextArea(2, 24);
		display.setEditable(false);
		display.setLineWrap(true);
		display.setWrapStyleWord(true);
		display.setFocusable(true);
		return display;
	}

	/**
	 * Builds a titled read-only context section.
	 *
	 * @param title section title.
	 * @param content displayed context text.
	 * @return labeled context section.
	 */
	private JPanel contextSection(String title, JTextArea content) {
		JPanel section = new JPanel(new BorderLayout());
		section.setBorder(javax.swing.BorderFactory.createTitledBorder(title));
		content.getAccessibleContext().setAccessibleName(title);
		section.add(new JScrollPane(content), BorderLayout.CENTER);
		return section;
	}

	/**
	 * Builds explicit objective approval controls.
	 *
	 * @return approval panel.
	 */
	private JPanel buildApprovalPanel() {
		JPanel approval = new JPanel(new java.awt.GridBagLayout());
		approval.setOpaque(false);
		JLabel approvalLabel = new JLabel("Objective approval");
		approvalLabel.setLabelFor(approvalStatus);
		java.awt.GridBagConstraints constraints = new java.awt.GridBagConstraints();
		constraints.insets = new java.awt.Insets(1, 3, 1, 8);
		constraints.anchor = java.awt.GridBagConstraints.WEST;
		constraints.gridx = 0;
		constraints.gridy = 0;
		approval.add(approvalLabel, constraints);
		constraints.gridx = 1;
		constraints.weightx = 0.5;
		constraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
		approval.add(approvalStatus, constraints);

		approver.getAccessibleContext().setAccessibleName("Approving Incident Commander");
		JLabel approverLabel = new JLabel("Incident Commander");
		approverLabel.setLabelFor(approver);
		constraints.gridx = 2;
		constraints.weightx = 0;
		constraints.fill = java.awt.GridBagConstraints.NONE;
		approval.add(approverLabel, constraints);
		constraints.gridx = 3;
		constraints.weightx = 0.5;
		constraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
		approval.add(approver, constraints);

		signature.getAccessibleContext().setAccessibleName("Incident Commander typed signature");
		JLabel signatureLabel = new JLabel("Typed signature");
		signatureLabel.setLabelFor(signature);
		constraints.gridx = 0;
		constraints.gridy = 1;
		constraints.weightx = 0;
		constraints.fill = java.awt.GridBagConstraints.NONE;
		approval.add(signatureLabel, constraints);
		constraints.gridx = 1;
		constraints.weightx = 0.5;
		constraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
		approval.add(signature, constraints);

		JButton approve = new JButton("Approve current objectives");
		approve.addActionListener(event -> approveObjectives());
		constraints.gridx = 2;
		constraints.gridwidth = 2;
		constraints.weightx = 0.5;
		approval.add(approve, constraints);
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
			String assignmentSummary = links == 0 ? "<html><b>None</b></html>"
					: states.replace("; ", " · ");
			objectiveModel.addRow(new Object[]{"<html>" + html(objective.toString()) + "</html>", links,
					assignmentSummary, objective.getAssessment().displayLabel()});
		}
		updateObjectiveRowHeights();
	}

	/**
	 * Keeps objective wording fully visible while using compact rows for short text.
	 */
	private void updateObjectiveRowHeights() {
		int width = Math.max(240, objectives.getColumnModel().getColumn(0).getWidth());
		java.awt.FontMetrics metrics = objectives.getFontMetrics(objectives.getFont());
		for (int row = 0; row < objectiveModel.getRowCount(); row++) {
			String text = String.valueOf(objectiveModel.getValueAt(row, 0));
			text = text.replaceAll("<[^>]+>", "").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&");
			int lineCount = wrappedLineCount(text, width - 10, metrics);
			objectives.setRowHeight(row, Math.max(42, lineCount * metrics.getHeight() + 6));
		}
	}

	/**
	 * Estimates wrapped lines using the table font and objective-column width.
	 *
	 * @param text objective wording.
	 * @param availableWidth width available for the text.
	 * @param metrics table font metrics.
	 * @return minimum number of wrapped lines.
	 */
	private static int wrappedLineCount(String text, int availableWidth, java.awt.FontMetrics metrics) {
		int lines = 1;
		int lineWidth = 0;
		for (String word : text.split("\\s+")) {
			int wordWidth = metrics.stringWidth(word);
			if (lineWidth > 0 && lineWidth + metrics.stringWidth(" ") + wordWidth > availableWidth) {
				lines++;
				lineWidth = wordWidth;
			} else {
				lineWidth += (lineWidth == 0 ? 0 : metrics.stringWidth(" ")) + wordWidth;
			}
		}
		return lines;
	}

	/**
	 * Shows or hides the objective assessment column without changing recorded data.
	 *
	 * @param visible whether the assessment column should be visible.
	 */
	private void setObjectiveAssessmentColumnVisible(boolean visible) {
		boolean currentlyVisible = false;
		for (int index = 0; index < objectives.getColumnModel().getColumnCount(); index++) {
			if (objectives.getColumnModel().getColumn(index) == objectiveAssessmentColumn) {
				currentlyVisible = true;
				break;
			}
		}
		if (visible && !currentlyVisible) {
			objectives.addColumn(objectiveAssessmentColumn);
			objectives.moveColumn(objectives.getColumnCount() - 1, objectives.getColumnCount() - 1);
		} else if (!visible && currentlyVisible) {
			objectives.removeColumn(objectiveAssessmentColumn);
		}
		objectives.doLayout();
		updateObjectiveRowHeights();
		objectives.getTableHeader().repaint();
	}

	/**
	 * Returns the selected objective's stable identifier.
	 *
	 * @return selected objective identifier, or blank.
	 */
	private String selectedObjectiveId() {
		int selected = objectives.getSelectedRow();
		int row = selected < 0 ? -1 : objectives.convertRowIndexToModel(selected);
		List<IncidentObjective> current = controller.getData().getForm202().getStructuredObjectives();
		return row < 0 || row >= current.size() ? "" : current.get(row).getObjectiveId();
	}

	/** Opens the focused editor for the selected objective. */
	private void editSelectedObjective() {
		int selected = objectives.getSelectedRow();
		int row = selected < 0 ? -1 : objectives.convertRowIndexToModel(selected);
		List<IncidentObjective> current = controller.getData().getForm202().getStructuredObjectives();
		if (row >= 0 && row < current.size()) {
			editObjective(current.get(row));
		}
	}

	/**
	 * Opens a focused editor for adding or editing objective wording, assessment,
	 * and assessment note.
	 *
	 * @param existing objective being edited, or null to create one.
	 */
	private void editObjective(IncidentObjective existing) {
		AppController.IncidentBinding editBinding = controller.captureIncidentBinding();
		JTextArea wording = new JTextArea(existing == null ? "" : existing.getText(), 9, 48);
		wording.setLineWrap(true);
		wording.setWrapStyleWord(true);
		wording.getAccessibleContext().setAccessibleName("Full objective wording");
		ObjectiveAssessment recordedAssessment = existing == null ? ObjectiveAssessment.NOT_ASSESSED
				: existing.getAssessment();
		String recordedNote = existing == null ? "" : existing.getAssessmentNote();
		JComboBox<ObjectiveAssessment> assessment = null;
		JTextArea note = null;
		JPanel form = new JPanel(new GridLayout(0, 1, 4, 4));
		form.add(new JLabel("Objective wording"));
		form.add(new JScrollPane(wording));
		if (showObjectiveAssessments) {
			assessment = new JComboBox<>(ObjectiveAssessment.values());
			assessment.setSelectedItem(recordedAssessment);
			assessment.getAccessibleContext().setAccessibleName("Objective assessment");
			note = new JTextArea(recordedNote, 1, 48);
			note.setLineWrap(true);
			note.setWrapStyleWord(true);
			note.getAccessibleContext().setAccessibleName("Objective assessment note");
			form.add(new JLabel("Assessment"));
			form.add(assessment);
			form.add(new JLabel("Assessment note"));
			form.add(new JScrollPane(note));
		}
		String title = existing == null ? "Add incident objective" : "Edit incident objective";
		int choice = UiSupport.showResizableOptionDialog(this, title, form, new Dimension(620, 480),
				new Object[]{"Save", "Cancel"}, "Save");
		if (choice == 0) {
			saveObjective(editBinding, existing == null ? "" : existing.getObjectiveId(), wording.getText(),
					assessment == null ? recordedAssessment : (ObjectiveAssessment) assessment.getSelectedItem(),
					note == null ? recordedNote : note.getText());
		}
	}

	/**
	 * Sets whether objective assessments are shown in the table and editor.
	 *
	 * @param visible true to show assessment details.
	 */
	void setShowObjectiveAssessments(boolean visible) {
		showObjectiveAssessments = visible;
		setObjectiveAssessmentColumnVisible(visible);
	}

	/**
	 * Keeps the review badges at their preferred height beneath the objective table.
	 */
	private void fitProgressSection() {
		if (objectiveSplit == null || objectiveSplit.getHeight() <= 0 || planProgress.getParent() == null) {
			return;
		}
		planProgress.revalidate();
		planProgress.getParent().revalidate();
		int reviewHeight = planProgress.getParent().getPreferredSize().height;
		planProgress.getParent().setMinimumSize(new Dimension(0, reviewHeight));
		int availableHeight = objectiveSplit.getHeight() - objectiveSplit.getDividerSize();
		int minimumReviewHeight = Math.min(reviewHeight, Math.max(48, availableHeight / 3));
		int dividerLocation = Math.min(Math.max(80, availableHeight - reviewHeight),
				availableHeight - minimumReviewHeight);
		dividerLocation = Math.max(0, dividerLocation);
		objectiveSplit.setDividerLocation(dividerLocation);
	}

	/**
	 * Provides a flexible final row so extra space is not inserted around badges.
	 *
	 * @param column grid column.
	 * @param row grid row.
	 * @return constraints for a vertical spacer.
	 */
	private static java.awt.GridBagConstraints progressSpacerConstraints(int column, int row) {
		java.awt.GridBagConstraints constraints = new java.awt.GridBagConstraints();
		constraints.gridx = column;
		constraints.gridy = row;
		constraints.weightx = 1;
		constraints.weighty = 1;
		constraints.fill = java.awt.GridBagConstraints.BOTH;
		return constraints;
	}

	/**
	 * Applies an objective edit only to the incident binding from which it began.
	 *
	 * @param editBinding originating incident and period.
	 * @param objectiveId stable objective identifier, or blank to add.
	 * @param wording complete objective text.
	 * @param assessment explicit human assessment.
	 * @param note assessment note.
	 * @return true when the objective was added or updated.
	 */
	boolean saveObjective(AppController.IncidentBinding editBinding, String objectiveId, String wording,
			ObjectiveAssessment assessment, String note) {
		if (!controller.isCurrent(editBinding)) {
			return false;
		}
		List<IncidentObjective> ordered = new ArrayList<>(controller.getData().getForm202().getStructuredObjectives());
		IncidentObjective objective = null;
		if (objectiveId != null && !objectiveId.isBlank()) {
			objective = ordered.stream().filter(candidate -> objectiveId.equals(candidate.getObjectiveId()))
					.findFirst().orElse(null);
			if (objective == null) {
				return false;
			}
		} else {
			objective = new IncidentObjective();
			ordered.add(objective);
		}
		ObjectiveAssessment previousAssessment = objective.getAssessment();
		String previousNote = objective.getAssessmentNote();
		objective.setText(wording);
		objective.setAssessment(assessment);
		objective.setAssessmentNote(note);
		if (previousAssessment != objective.getAssessment()
				|| !java.util.Objects.equals(previousNote, objective.getAssessmentNote())) {
			objective.setAssessedBy(controller.getData().getIncidentContext().getCurrentUser());
			objective.setAssessedAt(UtcTimestamp.now().withSecond(0).withNano(0));
		}
		controller.getData().getForm202().setStructuredObjectives(ordered);
		controller.markDirty();
		refreshFromModel();
		return true;
	}

	/** Opens editors for command emphasis and general situational awareness. */
	private void editIcs202Context() {
		AppController.IncidentBinding editBinding = controller.captureIncidentBinding();
		JTextArea emphasis = new JTextArea(controller.getData().getForm202().getCommandEmphasis(), 8, 48);
		emphasis.setLineWrap(true);
		emphasis.setWrapStyleWord(true);
		emphasis.getAccessibleContext().setAccessibleName("Command emphasis");
		JTextArea awareness = new JTextArea(controller.getData().getForm202().getSituationalAwareness(), 8, 48);
		awareness.setLineWrap(true);
		awareness.setWrapStyleWord(true);
		awareness.getAccessibleContext().setAccessibleName("General situational awareness");
		JLabel emphasisLabel = new JLabel("Command emphasis");
		emphasisLabel.setLabelFor(emphasis);
		JLabel awarenessLabel = new JLabel("General situational awareness");
		awarenessLabel.setLabelFor(awareness);
		JPanel form = new JPanel();
		form.setLayout(new javax.swing.BoxLayout(form, javax.swing.BoxLayout.Y_AXIS));
		form.add(contextEditorSection(emphasisLabel, emphasis));
		form.add(javax.swing.Box.createVerticalStrut(10));
		form.add(contextEditorSection(awarenessLabel, awareness));
		int choice = UiSupport.showResizableOptionDialog(this, "Edit ICS 202 context", form,
				new Dimension(760, 520), new Object[]{"Save", "Cancel"}, "Save");
		if (choice == 0) {
			saveIcs202Context(editBinding, emphasis.getText(), awareness.getText());
		}
	}

	/**
	 * Creates a labeled ICS 202 context editor section with compact label spacing.
	 *
	 * @param label section label connected to its editor.
	 * @param editor text editor for the section.
	 * @return vertically arranged labeled editor section.
	 */
	private JPanel contextEditorSection(JLabel label, JTextArea editor) {
		JPanel section = new JPanel(new BorderLayout(0, 2));
		section.setAlignmentX(LEFT_ALIGNMENT);
		JScrollPane scroll = new JScrollPane(editor);
		scroll.setPreferredSize(new Dimension(700, 190));
		section.add(label, BorderLayout.NORTH);
		section.add(scroll, BorderLayout.CENTER);
		return section;
	}

	/**
	 * Saves ICS 202 command emphasis and situational awareness against the originating incident.
	 *
	 * @param editBinding originating incident and period.
	 * @param emphasis command emphasis wording.
	 * @param awareness general situational awareness wording.
	 * @return true when the values were saved.
	 */
	boolean saveIcs202Context(AppController.IncidentBinding editBinding, String emphasis, String awareness) {
		if (!controller.isCurrent(editBinding)) {
			return false;
		}
		controller.getData().getForm202().setCommandEmphasis(emphasis);
		controller.getData().getForm202().setSituationalAwareness(awareness);
		controller.markDirty();
		return true;
	}

	/**
	 * Creates a table model containing reported results for one objective.
	 *
	 * @param objectiveId objective whose results are displayed.
	 * @return read-only result table model.
	 */
	DefaultTableModel resultsModelFor(String objectiveId) {
		DefaultTableModel model = readOnlyModel("Reported result", "Source", "Recorded");
		for (Ics202ReportedResult result : controller.getData().getForm202().getReportedResults()) {
			if (objectiveId.equals(result.getObjectiveId())) {
				model.addRow(new Object[]{result.getResult(), result.getSource(),
						result.getReportedAt() == null ? "Not recorded"
								: UiSupport.formatDateTime(result.getReportedAt(), APPROVAL_TIME)});
			}
		}
		return model;
	}

	/**
	 * Refreshes an existing reported-results table model.
	 *
	 * @param model table model to refresh.
	 * @param objectiveId objective whose results are displayed.
	 */
	private void refreshResults(DefaultTableModel model, String objectiveId) {
		model.setRowCount(0);
		DefaultTableModel latest = resultsModelFor(objectiveId);
		for (int row = 0; row < latest.getRowCount(); row++) {
			model.addRow(new Object[]{latest.getValueAt(row, 0), latest.getValueAt(row, 1),
					latest.getValueAt(row, 2)});
		}
	}

	/**
	 * Opens a picker for existing assignments not already linked to this objective.
	 *
	 * @param objective objective receiving selected links.
	 * @param assignmentsModel linked-assignment table model to refresh.
	 * @param linkedAssignments current linked-assignment rows.
	 */
	private void linkExistingAssignments(IncidentObjective objective, DefaultTableModel assignmentsModel,
			List<ResourceAssignment> linkedAssignments, Runnable refreshActionAvailability) {
		AppController.IncidentBinding editBinding = controller.captureIncidentBinding();
		List<ResourceAssignment> available = controller.getAssignmentResources().stream()
				.filter(assignment -> !assignment.getObjectiveIds().contains(objective.getObjectiveId())).toList();
		if (available.isEmpty()) {
			UiSupport.showDocumentMessage(this, "There are no unlinked assignments to add.",
					"Link existing assignments", javax.swing.JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		JList<String> picker = new JList<>(available.stream().map(this::assignmentLabel)
				.toArray(String[]::new));
		picker.setSelectionMode(javax.swing.ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		picker.getAccessibleContext().setAccessibleName("Existing assignments to link");
		int choice = UiSupport.showResizableOptionDialog(this, "Link existing assignments",
				new JScrollPane(picker), new Dimension(620, 320), new Object[]{"Link", "Cancel"}, "Link");
		if (choice == 0) {
			List<String> assignmentIds = picker.getSelectedIndices().length == 0 ? List.of()
					: java.util.Arrays.stream(picker.getSelectedIndices()).mapToObj(index -> available.get(index))
							.map(ResourceAssignment::getAssignmentId).toList();
			if (linkAssignmentsToObjective(editBinding, objective.getObjectiveId(), assignmentIds)) {
				refreshLinkedAssignments(objective, assignmentsModel, linkedAssignments);
				refreshActionAvailability.run();
			}
		}
	}

	/**
	 * Creates supporting work through the normal assignment workflow and refreshes the dialog.
	 *
	 * @param objective objective receiving the assignment.
	 * @param assignmentsModel linked-assignment table model.
	 * @param linkedAssignments current linked rows.
	 * @param refreshActionAvailability updates direct-action enablement.
	 */
	private void createSupportingAssignment(IncidentObjective objective, DefaultTableModel assignmentsModel,
			List<ResourceAssignment> linkedAssignments, Runnable refreshActionAvailability) {
		if (createSupportingAssignment(objective.getObjectiveId()) != null) {
			refreshLinkedAssignments(objective, assignmentsModel, linkedAssignments);
			refreshActionAvailability.run();
		}
	}

	/**
	 * Adds existing assignment links without disturbing any other objective links.
	 *
	 * @param editBinding originating incident and period.
	 * @param objectiveId stable objective identifier.
	 * @param assignmentIds existing assignments selected for this objective.
	 * @return true when at least one valid link was added.
	 */
	boolean linkAssignmentsToObjective(AppController.IncidentBinding editBinding, String objectiveId,
			List<String> assignmentIds) {
		if (!controller.isCurrent(editBinding) || selectedObjective(objectiveId) == null || assignmentIds == null) {
			return false;
		}
		List<ResourceAssignment> assignments = controller.getData().getAllAssignmentResources();
		boolean changed = false;
		for (String assignmentId : assignmentIds) {
			ResourceAssignment assignment = assignments.stream()
					.filter(candidate -> assignmentId != null && assignmentId.equals(candidate.getAssignmentId()))
					.findFirst().orElse(null);
			if (assignment != null && !assignment.getObjectiveIds().contains(objectiveId)) {
				List<String> links = new ArrayList<>(assignment.getObjectiveIds());
				links.add(objectiveId);
				assignment.setObjectiveIds(links);
				changed = true;
			}
		}
		if (changed) {
			controller.markDirty();
			refreshFromModel();
		}
		return changed;
	}

	/**
	 * Creates a normal assignment with the selected objective prelinked.
	 *
	 * @param objectiveId stable objective identifier.
	 * @return saved assignment, or null when cancelled or stale.
	 */
	ResourceAssignment createSupportingAssignment(String objectiveId) {
		AppController.IncidentBinding editBinding = controller.captureIncidentBinding();
		if (selectedObjective(objectiveId) == null || assignmentActions == null) {
			return null;
		}
		ResourceAssignment assignment = assignmentActions.createSupportingAssignment(objectiveId);
		if (assignment == null || !controller.isCurrent(editBinding)
				|| selectedObjective(objectiveId) == null) {
			return null;
		}
		List<ResourceAssignment> existing = controller.getData().getAllAssignmentResources();
		boolean changed = false;
		if (existing.stream().noneMatch(row -> assignment.getAssignmentId().equals(row.getAssignmentId()))) {
			controller.getData().getForm204().getResourcesAssigned().add(assignment);
			changed = true;
		}
		if (!assignment.getObjectiveIds().contains(objectiveId)) {
			List<String> links = new ArrayList<>(assignment.getObjectiveIds());
			links.add(objectiveId);
			assignment.setObjectiveIds(links);
			changed = true;
		}
		if (changed) {
			controller.markDirty();
		}
		refreshFromModel();
		return assignment;
	}

	/**
	 * Creates a readable assignment label for the existing-link picker.
	 *
	 * @param assignment ICS 204 assignment.
	 * @return team number, resource identifier, and description.
	 */
	String assignmentLabel(ResourceAssignment assignment) {
		Set<String> objectiveIds = controller.getData().getForm202().getStructuredObjectives().stream()
				.filter(objective -> !objective.getText().isBlank())
				.map(IncidentObjective::getObjectiveId).collect(java.util.stream.Collectors.toSet());
		boolean linked = assignment.getObjectiveIds().stream().anyMatch(objectiveIds::contains);
		return (linked ? "" : "[No objective link] ")
				+ nonblank(assignment.getAssignmentTeamNumber(), "No team number") + " — "
				+ nonblank(assignment.getResourceIdentifier(), "No resource identifier") + " — "
				+ nonblank(assignment.getAssignment(), "No assignment text");
	}

	/**
	 * Rebuilds the linked assignment table after adding an assignment.
	 *
	 * @param objective objective whose assignment links are shown.
	 * @param model displayed assignment table.
	 * @param linkedAssignments backing table rows.
	 */
	private void refreshLinkedAssignments(IncidentObjective objective, DefaultTableModel model,
			List<ResourceAssignment> linkedAssignments) {
		linkedAssignments.clear();
		linkedAssignments.addAll(controller.getData().getAssignmentsForObjective(objective.getObjectiveId()));
		model.setRowCount(0);
		if (linkedAssignments.isEmpty()) {
			model.addRow(new Object[]{"", "No assignments linked", "", ""});
			return;
		}
		for (ResourceAssignment assignment : linkedAssignments) {
			SarTaskAssignment task = findSarTask(assignment.getAssignmentId());
			model.addRow(new Object[]{nonblank(assignment.getAssignmentTeamNumber(), "Not recorded"),
					nonblank(assignment.getResourceIdentifier(), "Not recorded"),
					task == null ? "Not recorded" : nonblank(task.getTaskLifecycleStatus(), "Not recorded"),
					assignment.getAssignment()});
		}
	}

	/**
	 * Opens an operator input dialog for a reported result and its source.
	 *
	 * @param objectiveId stable objective identifier.
	 */
	private void openReportedResultDialog(String objectiveId) {
		AppController.IncidentBinding editBinding = controller.captureIncidentBinding();
		JTextArea result = new JTextArea(4, 42);
		result.setLineWrap(true);
		result.setWrapStyleWord(true);
		result.getAccessibleContext().setAccessibleName("Reported result");
		JTextField source = new JTextField(42);
		source.getAccessibleContext().setAccessibleName("Reported result source");
		JPanel form = new JPanel(new GridLayout(0, 1, 4, 4));
		form.add(new JLabel("Reported result"));
		form.add(new JScrollPane(result));
		form.add(new JLabel("Source (for example: team report, log entry, or observed fact)"));
		form.add(source);
		while (controller.isCurrent(editBinding)) {
			int choice = UiSupport.showResizableOptionDialog(this, "Record reported result", form,
					new Dimension(560, 300), new Object[]{"Record", "Cancel"}, "Record");
			if (choice != 0) {
				return;
			}
			if (recordReportedResult(editBinding, objectiveId, result.getText(), source.getText())) {
				return;
			}
			UiSupport.showDocumentMessage(this,
					"A result and its source are required, and the incident must be unchanged.",
					"Unable to record result", javax.swing.JOptionPane.WARNING_MESSAGE);
		}
	}

	/**
	 * Records an operator-reported result and its source without calculating objective achievement.
	 *
	 * @param editBinding originating incident and period.
	 * @param objectiveId stable objective identifier.
	 * @param result reported result wording.
	 * @param source stated source of the result.
	 * @return true when the result was recorded.
	 */
	boolean recordReportedResult(AppController.IncidentBinding editBinding, String objectiveId, String result,
			String source) {
		if (!controller.isCurrent(editBinding) || selectedObjective(objectiveId) == null
				|| result == null || result.isBlank() || source == null || source.isBlank()) {
			return false;
		}
		Ics202ReportedResult recorded = new Ics202ReportedResult();
		recorded.setObjectiveId(objectiveId);
		recorded.setResult(result.trim());
		recorded.setSource(source.trim());
		recorded.setReportedBy(controller.getData().getIncidentContext().getCurrentUser());
		recorded.setReportedAt(UtcTimestamp.now().withSecond(0).withNano(0));
		controller.getData().getForm202().getReportedResults().add(recorded);
		controller.markDirty();
		return true;
	}

	/**
	 * Finds an objective by its stable identity.
	 *
	 * @param objectiveId objective identifier.
	 * @return objective, or null if absent.
	 */
	private IncidentObjective selectedObjective(String objectiveId) {
		return objectiveId == null || objectiveId.isBlank() ? null
				: controller.getData().getForm202().getStructuredObjectives().stream()
				.filter(objective -> objectiveId.equals(objective.getObjectiveId())).findFirst().orElse(null);
	}
	/**
	 * Selects the row beneath a context-menu click.
	 *
	 * @param event mouse event.
	 */
	private void selectObjectiveAtEvent(MouseEvent event) {
		if (event.isPopupTrigger()) {
			int row = objectives.rowAtPoint(event.getPoint());
			if (row >= 0) {
				objectives.setRowSelectionInterval(row, row);
			}
		}
	}

	/** Displays linked assignments and delegates selected-row actions to existing workflows. */
	private void showSelectedObjectiveAssignments() {
		int selectedRow = objectives.getSelectedRow();
		int row = selectedRow < 0 ? -1 : objectives.convertRowIndexToModel(selectedRow);
		if (row < 0 || row >= controller.getData().getForm202().getStructuredObjectives().size()) {
			return;
		}
		IncidentObjective objective = controller.getData().getForm202().getStructuredObjectives().get(row);
		List<ResourceAssignment> linkedAssignments = new ArrayList<>();
		for (ResourceAssignment assignment : controller.getAssignmentResources()) {
			if (!assignment.getObjectiveIds().contains(objective.getObjectiveId())) {
				continue;
			}
			linkedAssignments.add(assignment);
		}
		String title = "Linked assignments for objective " + (objective.getDisplayOrder() + 1);
		JDialog dialog = new JDialog(javax.swing.SwingUtilities.getWindowAncestor(this), title,
				java.awt.Dialog.ModalityType.MODELESS);
		dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
		JPanel content = new JPanel(new BorderLayout(6, 6));
		JTextArea objectiveText = new JTextArea(objective.getText());
		objectiveText.setEditable(false);
		objectiveText.setLineWrap(true);
		objectiveText.setWrapStyleWord(true);
		objectiveText.setRows(3);
		objectiveText.getAccessibleContext().setAccessibleName("Objective wording");
		content.add(new JScrollPane(objectiveText), BorderLayout.NORTH);

		DefaultTableModel assignmentsModel = readOnlyModel("Task Number", "Task Name", "Status", "Assignment");
		for (ResourceAssignment assignment : linkedAssignments) {
			SarTaskAssignment task = findSarTask(assignment.getAssignmentId());
			String number = nonblank(assignment.getAssignmentTeamNumber(), "Not recorded");
			String name = nonblank(assignment.getResourceIdentifier(), "Not recorded");
			String status = task == null ? "Not recorded" : nonblank(task.getTaskLifecycleStatus(), "Not recorded");
			assignmentsModel.addRow(new Object[]{number, name, status, assignment.getAssignment()});
		}
		JTable assignmentsTable = new JTable(assignmentsModel);
		assignmentsTable.getAccessibleContext().setAccessibleName("Tasks linked to objective");
		assignmentsTable.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
		assignmentsTable.setAutoCreateRowSorter(true);
		assignmentsTable.setFillsViewportHeight(true);
		if (linkedAssignments.isEmpty()) {
			assignmentsModel.addRow(new Object[]{"", "No assignments linked", "", ""});
		} else {
			assignmentsTable.setRowSelectionInterval(0, 0);
		}
		JPanel assignmentTab = new JPanel(new BorderLayout());
		assignmentTab.add(new JScrollPane(assignmentsTable), BorderLayout.CENTER);
		DefaultTableModel objectiveResultsModel = resultsModelFor(objective.getObjectiveId());
		JTable resultsTable = new JTable(objectiveResultsModel);
		resultsTable.getAccessibleContext().setAccessibleName("Reported results and sources for objective");
		resultsTable.setFillsViewportHeight(true);
		JPanel resultsTab = new JPanel(new BorderLayout(4, 4));
		resultsTab.add(new JScrollPane(resultsTable), BorderLayout.CENTER);
		JButton recordResult = new JButton("Record reported result…");
		recordResult.addActionListener(event -> {
			openReportedResultDialog(objective.getObjectiveId());
			refreshResults(objectiveResultsModel, objective.getObjectiveId());
		});
		JPanel resultActions = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEADING));
		resultActions.add(recordResult);
		resultsTab.add(resultActions, BorderLayout.SOUTH);
		JTabbedPane detailTabs = new JTabbedPane();
		detailTabs.addTab("Linked assignments", assignmentTab);
		detailTabs.addTab("Reported results", resultsTab);
		content.add(detailTabs, BorderLayout.CENTER);

		JPanel actions = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.TRAILING));
		JButton open204 = new JButton("Open ICS 204 details");
		JButton openSar = new JButton("Open SAR task");
		JButton addLog = new JButton("Make a log entry");
		JButton changeStatus = new JButton("Change status");
		JButton debrief = new JButton("Debrief");
		JButton linkExisting = new JButton("Link existing…");
		JButton createSupporting = new JButton("Create supporting assignment…");
		JButton close = new JButton("Close");
		boolean sarMode = controller.getIncidentMode() == org.sarmanagement.icsforms.model.IncidentMode.SAR;
		openSar.setVisible(sarMode);
		debrief.setVisible(sarMode);
		createSupporting.setEnabled(assignmentActions != null);
		open204.setEnabled(!linkedAssignments.isEmpty());
		openSar.setEnabled(!linkedAssignments.isEmpty());
		addLog.setEnabled(!linkedAssignments.isEmpty());
		changeStatus.setEnabled(false);
		Runnable refreshActionAvailability = () -> {
			int viewRow = assignmentsTable.getSelectedRow();
			int modelRow = viewRow < 0 ? -1 : assignmentsTable.convertRowIndexToModel(viewRow);
			boolean hasAssignment = modelRow >= 0 && modelRow < linkedAssignments.size();
			SarTaskAssignment task = hasAssignment
					? findSarTask(linkedAssignments.get(modelRow).getAssignmentId()) : null;
			boolean hasSarTask = task != null;
			open204.setEnabled(hasAssignment);
			openSar.setEnabled(sarMode && hasAssignment && hasSarTask);
			addLog.setEnabled(hasAssignment);
			changeStatus.setEnabled(sarMode && hasSarTask
					&& SarTaskPanel.isPlannedOrAssigned(task.getTaskLifecycleStatus()));
			debrief.setEnabled(sarMode && SarTaskPanel.isReadyForDebrief(task));
		};
		assignmentsTable.getSelectionModel().addListSelectionListener(event -> {
			if (!event.getValueIsAdjusting()) {
				refreshActionAvailability.run();
			}
		});
		refreshActionAvailability.run();
		linkExisting.addActionListener(event -> linkExistingAssignments(objective, assignmentsModel,
				linkedAssignments, refreshActionAvailability));
		createSupporting.addActionListener(event -> createSupportingAssignment(objective, assignmentsModel,
				linkedAssignments, refreshActionAvailability));
		open204.addActionListener(event -> runSelectedAssignmentAction(assignmentsTable, linkedAssignments,
				assignmentActions == null ? null : assignmentActions::openIcs204));
		openSar.addActionListener(event -> runSelectedAssignmentAction(assignmentsTable, linkedAssignments,
				assignmentActions == null ? null : assignmentActions::openSarTask));
		addLog.addActionListener(event -> runSelectedAssignmentAction(assignmentsTable, linkedAssignments,
				assignmentActions == null ? null : assignmentActions::addLogEntry));
		changeStatus.addActionListener(event -> runSelectedAssignmentAction(assignmentsTable, linkedAssignments,
				assignmentActions == null ? null : assignmentActions::changeStatus));
		debrief.addActionListener(event -> runSelectedAssignmentAction(assignmentsTable, linkedAssignments,
				assignmentActions == null ? null : assignmentActions::debrief));
		close.addActionListener(event -> dialog.dispose());
		JPopupMenu assignmentMenu = new JPopupMenu();
		addAssignmentMenuItem(assignmentMenu, "Open ICS 204 details", open204);
		if (sarMode) {
			addAssignmentMenuItem(assignmentMenu, "Open SAR task", openSar);
		}
		assignmentMenu.addSeparator();
		addAssignmentMenuItem(assignmentMenu, "Make a log entry", addLog);
		addAssignmentMenuItem(assignmentMenu, "Change status", changeStatus);
		if (sarMode) {
			addAssignmentMenuItem(assignmentMenu, "Debrief", debrief);
		}
		assignmentsTable.setComponentPopupMenu(assignmentMenu);
		assignmentsTable.addMouseListener(new java.awt.event.MouseAdapter() {
			/** Selects the row under a context-menu invocation. */
			@Override
			public void mousePressed(java.awt.event.MouseEvent event) {
				selectAssignmentRow(assignmentsTable, event);
			}

			/** Selects the row under a context-menu invocation. */
			@Override
			public void mouseReleased(java.awt.event.MouseEvent event) {
				selectAssignmentRow(assignmentsTable, event);
			}

			/** Opens the ICS 204 details for a double-clicked assignment. */
			@Override
			public void mouseClicked(java.awt.event.MouseEvent event) {
				if (event.getClickCount() == 2 && javax.swing.SwingUtilities.isLeftMouseButton(event)) {
					int viewRow = assignmentsTable.rowAtPoint(event.getPoint());
					if (viewRow >= 0) {
						assignmentsTable.setRowSelectionInterval(viewRow, viewRow);
						open204.doClick();
					}
				}
			}
		});
		actions.add(open204);
		actions.add(openSar);
		actions.add(addLog);
		actions.add(changeStatus);
		actions.add(debrief);
		actions.add(linkExisting);
		actions.add(createSupporting);
		actions.add(close);
		content.add(actions, BorderLayout.SOUTH);
		dialog.setContentPane(content);
		dialog.setSize(new Dimension(920, 420));
		dialog.setLocationRelativeTo(this);
		dialog.setVisible(true);
	}

	/**
	 * Finds a SAR task linked to one ICS 204 assignment.
	 *
	 * @param assignmentId stable assignment identifier.
	 * @return linked task, or null when the assignment has no SAR task.
	 */
	private SarTaskAssignment findSarTask(String assignmentId) {
		return controller.getData().getSarTaskAssignments().stream()
				.filter(task -> assignmentId.equals(task.getAssignmentId())).findFirst().orElse(null);
	}

	/**
	 * Runs an assignment action for the currently selected table row.
	 *
	 * @param table task table.
	 * @param assignments linked ICS 204 assignments in model order.
	 * @param action callback for the selected assignment.
	 */
	private static void runSelectedAssignmentAction(JTable table, List<ResourceAssignment> assignments,
			Consumer<String> action) {
		int viewRow = table.getSelectedRow();
		int modelRow = viewRow < 0 ? -1 : table.convertRowIndexToModel(viewRow);
		if (action == null || modelRow < 0 || modelRow >= assignments.size()) {
			return;
		}
		String assignmentId = assignments.get(modelRow).getAssignmentId();
		action.accept(assignmentId);
	}

	/**
	 * Adds one context-menu item that delegates to an existing assignment action button.
	 *
	 * @param menu assignment row menu.
	 * @param label visible menu label.
	 * @param actionButton existing workflow action button.
	 */
	private static void addAssignmentMenuItem(JPopupMenu menu, String label, JButton actionButton) {
		JMenuItem item = new JMenuItem(label);
		item.setEnabled(actionButton.isEnabled());
		actionButton.addPropertyChangeListener("enabled",
				event -> item.setEnabled(actionButton.isEnabled()));
		item.addActionListener(event -> actionButton.doClick());
		menu.add(item);
	}

	/**
	 * Selects the row beneath a context-menu trigger.
	 *
	 * @param table linked-assignment table.
	 * @param event mouse event.
	 */
	private static void selectAssignmentRow(JTable table, java.awt.event.MouseEvent event) {
		if (event.isPopupTrigger()) {
			int row = table.rowAtPoint(event.getPoint());
			if (row >= 0) {
				table.setRowSelectionInterval(row, row);
			}
		}
	}

	/**
	 * Returns the first nonblank display value.
	 *
	 * @param value preferred display value.
	 * @param fallback alternate value.
	 * @return preferred nonblank value or fallback.
	 */
	private static String nonblank(String value, String fallback) {
		return value == null || value.isBlank() ? fallback : value;
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
		List<ResourceAssignment> assignments = controller.getAssignmentResources();
		long objectivesWithoutAssignments = data.getForm202().getStructuredObjectives().stream()
				.filter(objective -> objectiveIds.contains(objective.getObjectiveId()))
				.filter(objective -> assignments.stream()
						.noneMatch(assignment -> assignment.getObjectiveIds().contains(objective.getObjectiveId())))
				.count();
		long assignmentsWithoutObjectives = assignments.stream()
				.filter(assignment -> assignment.getObjectiveIds().stream().noneMatch(objectiveIds::contains)).count();
		boolean objectivesRecorded = !objectiveIds.isEmpty();
		boolean objectivesApproved = data.getForm202().getObjectiveApprovalStatus()
				== ObjectiveApprovalStatus.APPROVED_CURRENT;
		String compactApproval = switch (data.getForm202().getObjectiveApprovalStatus()) {
			case NOT_APPROVED -> "Not approved";
			case APPROVED_CURRENT -> "Approved";
			case CONTENT_CHANGED -> "Approval stale";
			case LEGACY_UNVERIFIED -> "Legacy approval";
		};
		boolean animalRequired = IncidentCommandSummary.requiresAnimalMedicalPlan(data);
		String objectiveCoverage = objectivesWithoutAssignments + " objectives without linked assignments; "
				+ assignmentsWithoutObjectives + " assignments without a current objective link";
		String compactObjectiveCoverage = "No obj links: " + objectivesWithoutAssignments + "\n"
				+ "No assignment links: " + assignmentsWithoutObjectives;
		long populatedAssignments = IncidentCommandSummary.populatedAssignments(data);
		boolean missing = !objectivesRecorded || populatedAssignments == 0;
		boolean approved = objectivesApproved;
		updateBadge("Objectives", mark(objectivesRecorded) + objectiveIds.size() + " recorded; " + objectiveCoverage,
				mark(objectivesApproved) + data.getForm202().getObjectiveApprovalStatus().displayLabel()
						+ "; " + objectiveCoverage,
				mark(objectivesRecorded) + objectiveIds.size() + " recorded\n" + compactObjectiveCoverage,
				mark(objectivesApproved) + compactApproval,
				!objectivesRecorded ? RolePanelSupport.MISSING
						: objectivesApproved ? RolePanelSupport.CURRENT : RolePanelSupport.PENDING);
		int assignmentCount = assignments.size();
		long linkedAssignments = assignmentCount - assignmentsWithoutObjectives;
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
			String compactReview = switch (data.getPlanReviewStatus(area)) {
				case REVIEWED_APPROVED -> "Reviewed · current";
				case CHANGED_SINCE_REVIEW -> "Stale since review";
				case NOT_YET_REVIEWED -> "Not reviewed";
			};
			long contentItems = IncidentCommandSummary.recordedContent(data, area);
			boolean current = data.getPlanReviewStatus(area) == PlanReviewStatus.REVIEWED_APPROVED;
			boolean required = area != PlanReviewArea.CANINE_MEDICAL || animalRequired;
			if (required) {
				missing |= contentItems == 0;
				approved &= current;
			}
			String compactContent = contentItems == 0 ? "No content"
					: contentItems + (contentItems == 1 ? " item" : " items") + " recorded";
			updateBadge(title, mark(contentItems > 0) + (contentItems == 0 ? "No content recorded"
					: contentItems + " content items recorded"),
					mark(current) + review + (area == PlanReviewArea.CANINE_MEDICAL
							? required ? " (required — animal resources recorded)" : " (optional — no animal resources recorded)"
							: ""),
					compactContent
							+ (area == PlanReviewArea.CANINE_MEDICAL
							? required ? " · required" : " · optional" : ""),
					mark(current) + compactReview,
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
		badge.setText("<html><b>" + html(title) + "</b><br>" + html(compactContent).replace("\n", "<br>")
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
						: "Objectives".equals(title)
								? " Counts identify objectives without linked assignments and assignments without a link "
										+ "to a currently recorded objective; they are not achievement or readiness judgments."
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
