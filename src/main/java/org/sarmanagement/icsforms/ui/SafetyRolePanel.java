/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.PlanReviewArea;
import org.sarmanagement.icsforms.model.PlanReviewSupport;
import org.sarmanagement.icsforms.model.SarTaskAssignment;
import org.sarmanagement.icsforms.pdf.Ics206CaninePdfRenderer;
import org.sarmanagement.icsforms.pdf.Ics206PdfRenderer;
import org.sarmanagement.icsforms.pdf.Ics208PdfRenderer;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.util.function.Consumer;

/**
 * Live Safety Officer worklist with focused edits, review actions, and previews.
 */
public class SafetyRolePanel extends JPanel {
	private static final String SAFETY_PLAN = "Safety planning and reported hazards";
	private static final String HUMAN_REVIEW = "Human medical plan review";
	private static final String CANINE_REVIEW = "Canine medical plan review";
	private final AppController controller;
	private final UiRefreshState refreshState = new UiRefreshState();
	private boolean refreshingWorklist;
	private final Consumer<WorkspaceDestination> navigator;
	private final AppController.IncidentBinding binding;
	private final DefaultListModel<String> workModel = new DefaultListModel<>();
	private final JList<String> worklist = new JList<>(workModel);
	private final JCheckBox showAll = new JCheckBox("Show all incident items");
	private final JTextArea detail = new JTextArea();
	private final JTextField safetyOfficer = new JTextField();
	private final JTextArea safetyMessage = new JTextArea(4, 40);
	private final RolePlanStatusBadges planBadges;
	private final JPanel focusedSafetyFields = UiSupport.formPanel();
	private final JPanel previewCards = new JPanel(new CardLayout());
	private final JPanel reviewCards = new JPanel(new CardLayout());
	private final JButton applyFocusedEditsButton = new JButton("Apply focused edits");
	private final AsyncFormPreviewPanel safetyPreview = new AsyncFormPreviewPanel(new Ics208PdfRenderer());
	private final AsyncFormPreviewPanel humanPreview = new AsyncFormPreviewPanel(new Ics206PdfRenderer());
	private final AsyncFormPreviewPanel caninePreview = new AsyncFormPreviewPanel(new Ics206CaninePdfRenderer());
	private final PlanReviewStrip safetyReview;
	private final PlanReviewStrip humanReview;
	private final PlanReviewStrip canineReview;
	private final AppController.IncidentChangeSubscription subscription;
	private boolean loadingFocusedEdits;
	private boolean focusedEditsDirty;
	private boolean disposed;

	/**
	 * Creates the Safety Officer panel.
	 *
	 * @param controller incident controller.
	 * @param navigator typed full-workspace navigator.
	 */
	public SafetyRolePanel(AppController controller, Consumer<WorkspaceDestination> navigator) {
		super(new BorderLayout(8, 8));
		this.controller = controller;
		this.navigator = navigator;
		binding = controller.captureIncidentBinding();
		planBadges = new RolePlanStatusBadges(controller.getData(), "Safety", "Human medical", "Canine medical");
		safetyReview = new PlanReviewStrip(controller, PlanReviewArea.SAFETY, this::applyFocusedEdits);
		humanReview = new PlanReviewStrip(controller, PlanReviewArea.HUMAN_MEDICAL, () -> {
		});
		canineReview = new PlanReviewStrip(controller, PlanReviewArea.CANINE_MEDICAL, () -> {
		});
		JPanel header = new JPanel(new BorderLayout(6, 4));
		header.add(new JLabel("SAFETY OFFICER | Focused live view — not an authorization boundary"),
				BorderLayout.CENTER);
		header.add(planBadges, BorderLayout.EAST);
		add(header, BorderLayout.NORTH);
		add(buildBody(), BorderLayout.CENTER);
		installFocusedDraftListeners();
		showAll.addActionListener(event -> refreshFromModel());
		worklist.addListSelectionListener(event -> {
			if (!event.getValueIsAdjusting() && !refreshingWorklist) {
				refreshSelectedItem();
			}
		});
		refreshFromModel();
		subscription = controller.addIncidentChangeListener(change -> {
			refreshFromModel();
		});
	}

	/** Releases listener and preview resources. */
	public void dispose() {
		disposed = true;
		subscription.close();
		cancelPreviews();
	}

	/** Cancels outstanding PDF work when the panel closes or its incident changes. */
	private void cancelPreviews() {
		safetyPreview.cancel();
		humanPreview.cancel();
		caninePreview.cancel();
	}

	/** Refreshes shared facts while preserving worklist selection. */
	public void refreshFromModel() {
		if (disposed || !controller.isCurrent(binding)) {
			cancelPreviews();
			return;
		}
		AppData data = controller.getData();
		if (!refreshState.roleChanged(data, data.getForm202(), data.getForm205(), data.getForm215a(),
				data.getForm208(), data.getMedicalPlan(), data.getCanineMedicalPlan(),
				data.getSarTaskAssignments(), data.getStagingLocation(), data.getIncidentLocations(),
				showAll.isSelected(), focusedEditsDirty)) {
			return;
		}
		String selected = worklist.getSelectedValue();
		if (!focusedEditsDirty) {
			loadingFocusedEdits = true;
			safetyOfficer.setText(MedicalRoleSupport.safetyOfficerName(controller.getData()));
			safetyMessage.setText(controller.getData().getForm208().getFreeText());
			loadingFocusedEdits = false;
		}
		planBadges.refreshFromModel(controller.getData());
		refreshingWorklist = true;
		rebuildWorklist();
		if (selected != null) {
			worklist.setSelectedValue(selected, true);
		}
		if (worklist.getSelectedIndex() < 0 && !workModel.isEmpty()) {
			worklist.setSelectedIndex(0);
		}
		refreshingWorklist = false;
		safetyReview.refreshFromModel();
		humanReview.refreshFromModel();
		canineReview.refreshFromModel();
		refreshSelectedItem();
	}

	/** Tracks unsaved focused edits so unrelated live updates cannot replace them. */
	private void installFocusedDraftListeners() {
		DocumentListener listener = new DocumentListener() {
			/** {@inheritDoc} */
			@Override
			public void insertUpdate(DocumentEvent event) {
				markFocusedEditsDirty();
			}

			/** {@inheritDoc} */
			@Override
			public void removeUpdate(DocumentEvent event) {
				markFocusedEditsDirty();
			}

			/** {@inheritDoc} */
			@Override
			public void changedUpdate(DocumentEvent event) {
				markFocusedEditsDirty();
			}
		};
		for (JTextComponent field : new JTextComponent[] { safetyOfficer, safetyMessage }) {
			field.getDocument().addDocumentListener(listener);
		}
	}

	/** Marks focused fields as containing a user draft unless they are loading. */
	private void markFocusedEditsDirty() {
		if (!loadingFocusedEdits) {
			focusedEditsDirty = true;
		}
	}

	/**
	 * Returns visible work item count for focused responsibility-filter tests.
	 *
	 * @return work item count.
	 */
	int workItemCount() {
		return workModel.size();
	}

	/**
	 * Builds worklist and selected-detail layout.
	 *
	 * @return split work panel.
	 */
	private JSplitPane buildBody() {
		JPanel left = new JPanel(new BorderLayout(4, 4));
		left.setBorder(javax.swing.BorderFactory.createTitledBorder("Responsible work"));
		left.add(showAll, BorderLayout.NORTH);
		left.add(new JScrollPane(worklist), BorderLayout.CENTER);
		UiSupport.addRow(focusedSafetyFields, 0, "Safety Officer", safetyOfficer);
		safetyMessage.setLineWrap(true);
		safetyMessage.setWrapStyleWord(true);
		UiSupport.addRow(focusedSafetyFields, 1, "Focused safety message", new JScrollPane(safetyMessage));
		JPanel actions = new JPanel(new java.awt.GridLayout(0, 2, 4, 4));
		applyFocusedEditsButton.addActionListener(event -> applyFocusedEdits());
		JButton source = new JButton("Open selected source");
		source.addActionListener(event -> openSelectedSource());
		actions.add(applyFocusedEditsButton);
		actions.add(source);
		reviewCards.add(safetyReview, SAFETY_PLAN);
		reviewCards.add(humanReview, HUMAN_REVIEW);
		reviewCards.add(canineReview, CANINE_REVIEW);
		reviewCards.add(new JPanel(), "No plan review selected");
		previewCards.add(safetyPreview, SAFETY_PLAN);
		previewCards.add(humanPreview, HUMAN_REVIEW);
		previewCards.add(caninePreview, CANINE_REVIEW);
		JPanel right = new JPanel(new BorderLayout(4, 4));
		detail.setEditable(false);
		detail.setLineWrap(true);
		detail.setWrapStyleWord(true);
		JPanel upper = new JPanel(new BorderLayout());
		upper.add(new JScrollPane(detail), BorderLayout.CENTER);
		upper.add(focusedSafetyFields, BorderLayout.SOUTH);
		right.add(upper, BorderLayout.NORTH);
		right.add(previewCards, BorderLayout.CENTER);
		JPanel bottom = new JPanel(new BorderLayout());
		bottom.add(reviewCards, BorderLayout.CENTER);
		bottom.add(actions, BorderLayout.SOUTH);
		right.add(bottom, BorderLayout.SOUTH);
		JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
		split.setResizeWeight(0.28);
		split.setDividerLocation(300);
		split.setPreferredSize(new Dimension(1180, 760));
		return split;
	}

	/** Rebuilds default-responsibility items and optional observational context. */
	private void rebuildWorklist() {
		workModel.clear();
		workModel.addElement(SAFETY_PLAN);
		workModel.addElement(HUMAN_REVIEW);
		if (controller.getData().isCanineMedicalWorkspaceEligible()) {
			workModel.addElement(CANINE_REVIEW);
		}
		if (showAll.isSelected()) {
			workModel.addElement("Communications plan status");
			workModel.addElement("Incident objectives context");
		}
	}

	/** Refreshes selected work detail and preview card. */
	private void refreshSelectedItem() {
		if (disposed || !controller.isCurrent(binding)) {
			cancelPreviews();
			return;
		}
		String selected = worklist.getSelectedValue();
		if (selected == null) {
			focusedSafetyFields.setVisible(false);
			applyFocusedEditsButton.setEnabled(false);
			reviewCards.setVisible(false);
			previewCards.setVisible(false);
			cancelPreviews();
			return;
		}
		AppData data = controller.getData();
		String reviewCard = "No plan review selected";
		if (SAFETY_PLAN.equals(selected)) {
			long hazards = data.getSarTaskAssignments().stream().map(SarTaskAssignment::getHazardsObserved)
					.filter(value -> value != null && !value.isBlank()).count();
			detail.setText("Safety review: " + data.getPlanReviewStatus(PlanReviewArea.SAFETY).displayLabel()
					+ "\nReported task hazards: " + hazards
					+ "\nMissing fields are shown as recorded and are not a sufficiency judgment.");
			((CardLayout) previewCards.getLayout()).show(previewCards, SAFETY_PLAN);
			reviewCard = SAFETY_PLAN;
		} else if (HUMAN_REVIEW.equals(selected)) {
			detail.setText("Human medical: " + data.getPlanReviewStatus(PlanReviewArea.HUMAN_MEDICAL).displayLabel()
					+ "\nEmergency instruction: " + data.getMedicalPlan().getCall911Procedure());
			((CardLayout) previewCards.getLayout()).show(previewCards, HUMAN_REVIEW);
			reviewCard = HUMAN_REVIEW;
		} else if (CANINE_REVIEW.equals(selected)) {
			detail.setText("Canine medical: " + data.getPlanReviewStatus(PlanReviewArea.CANINE_MEDICAL).displayLabel()
					+ "\nEmergency procedure: " + data.getCanineMedicalPlan().getEmergencyProcedures());
			((CardLayout) previewCards.getLayout()).show(previewCards, CANINE_REVIEW);
			reviewCard = CANINE_REVIEW;
		} else if (selected.startsWith("Communications")) {
			detail.setText("Communications: " + data.getPlanReviewStatus(PlanReviewArea.COMMUNICATIONS).displayLabel());
		} else {
			detail.setText(data.getForm202().getStructuredObjectives().size() + " objective(s) recorded.");
		}
		boolean planSelected = SAFETY_PLAN.equals(selected) || HUMAN_REVIEW.equals(selected) || CANINE_REVIEW.equals(selected);
		previewCards.setVisible(planSelected);
		focusedSafetyFields.setVisible(SAFETY_PLAN.equals(selected));
		applyFocusedEditsButton.setEnabled(SAFETY_PLAN.equals(selected));
		((CardLayout) reviewCards.getLayout()).show(reviewCards, reviewCard);
		reviewCards.setVisible(planSelected);
		if (planSelected) {
			previewSelected();
		} else {
			cancelPreviews();
		}
	}

	/** Applies focused safety values to their shared authoritative models. */
	private void applyFocusedEdits() {
		if (!controller.isCurrent(binding)) {
			return;
		}
		MedicalRoleSupport.setSafetyOfficerName(controller.getData(), safetyOfficer.getText());
		controller.getData().getForm208().setFreeText(safetyMessage.getText());
		focusedEditsDirty = false;
		controller.markDirty();
	}

	/** Automatically requests the latest PDF for the selected responsible plan. */
	private void previewSelected() {
		if (disposed || !controller.isCurrent(binding)) {
			cancelPreviews();
			return;
		}
		String selected = worklist.getSelectedValue();
		if (HUMAN_REVIEW.equals(selected)) {
			humanPreview.requestPreview(controller.getData(),
					PlanReviewSupport.contentDigest(controller.getData(), PlanReviewArea.HUMAN_MEDICAL));
		} else if (CANINE_REVIEW.equals(selected)) {
			caninePreview.requestPreview(controller.getData(),
					PlanReviewSupport.contentDigest(controller.getData(), PlanReviewArea.CANINE_MEDICAL));
		} else {
			safetyPreview.requestPreview(controller.getData(),
					PlanReviewSupport.contentDigest(controller.getData(), PlanReviewArea.SAFETY));
		}
	}

	/** Opens the comprehensive editor for the selected item. */
	private void openSelectedSource() {
		String selected = worklist.getSelectedValue();
		if (HUMAN_REVIEW.equals(selected)) {
			navigator.accept(WorkspaceDestination.humanMedicalPlan());
		} else if (CANINE_REVIEW.equals(selected)) {
			navigator.accept(WorkspaceDestination.canineMedicalPlan());
		} else {
			navigator.accept(WorkspaceDestination.of(WorkspaceId.SAFETY));
		}
	}
}
