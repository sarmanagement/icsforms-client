/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.PlanReviewArea;
import org.sarmanagement.icsforms.model.PlanReviewSupport;
import org.sarmanagement.icsforms.pdf.Ics206CaninePdfRenderer;
import org.sarmanagement.icsforms.pdf.Ics206PdfRenderer;

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
 * Live Medical Unit Leader preparation and review-handoff work panel.
 */
public class MedicalUnitLeaderRolePanel extends JPanel {
	private String displayedReviewSelection;
	private static final String HUMAN_PLAN = "Human medical plan preparation";
	private static final String CANINE_PLAN = "Canine medical plan preparation";
	private final AppController controller;
	private final UiRefreshState refreshState = new UiRefreshState();
	private boolean refreshingWorklist;
	private final Consumer<WorkspaceDestination> navigator;
	private final AppController.IncidentBinding binding;
	private final DefaultListModel<String> workModel = new DefaultListModel<>();
	private final JList<String> worklist = new JList<>(workModel);
	private final JCheckBox showAll = new JCheckBox("Show all incident items");
	private final JTextArea detail = new JTextArea();
	private final JTextField leaderName = new JTextField();
	private final JTextField leaderRadio = new JTextField();
	private final JTextField leaderPhone = new JTextField();
	private final JTextArea humanInstruction = new JTextArea(3, 40);
	private final JTextArea canineInstruction = new JTextArea(3, 40);
	private final RolePlanStatusBadges planBadges;
	private final JPanel previewCards = new JPanel(new CardLayout());
	private final JPanel reviewCards = new JPanel(new CardLayout());
	private final AsyncFormPreviewPanel humanPreview = new AsyncFormPreviewPanel(new Ics206PdfRenderer());
	private final AsyncFormPreviewPanel caninePreview = new AsyncFormPreviewPanel(new Ics206CaninePdfRenderer());
	private final JPanel focusedCards = new JPanel(new CardLayout());
	private final PlanReviewStrip humanReview;
	private final PlanReviewStrip canineReview;
	private final AppController.IncidentChangeSubscription subscription;
	private boolean loadingFocusedEdits;
	private boolean focusedEditsDirty;
	private boolean disposed;

	/**
	 * Creates the Medical Unit Leader panel.
	 *
	 * @param controller incident controller.
	 * @param navigator typed full-workspace navigator.
	 */
	public MedicalUnitLeaderRolePanel(AppController controller, Consumer<WorkspaceDestination> navigator) {
		super(new BorderLayout(8, 8));
		this.controller = controller;
		this.navigator = navigator;
		binding = controller.captureIncidentBinding();
		planBadges = new RolePlanStatusBadges(controller.getData(), "Human medical", "Canine medical");
		humanReview = new PlanReviewStrip(controller, PlanReviewArea.HUMAN_MEDICAL, this::applyFocusedEdits);
		canineReview = new PlanReviewStrip(controller, PlanReviewArea.CANINE_MEDICAL, this::applyFocusedEdits);
		JPanel header = new JPanel(new BorderLayout(6, 4));
		header.add(new JLabel("MEDICAL UNIT LEADER | Human and canine plans remain separate"),
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

	/** Releases listener and preview workers. */
	public void dispose() {
		disposed = true;
		subscription.close();
		cancelPreviews();
	}

	/** Cancels outstanding PDF work when the panel closes or its incident changes. */
	private void cancelPreviews() {
		humanPreview.cancel();
		caninePreview.cancel();
	}

	/** Refreshes shared plan facts while preserving selection. */
	public void refreshFromModel() {
		if (disposed || !controller.isCurrent(binding)) {
			cancelPreviews();
			return;
		}
		AppData data = controller.getData();
		if (!refreshState.roleChanged(data, planBadges.displayInputs(data), data.getMedicalUnitLeaderAssignment(), data.getMedicalPlan(),
				data.getCanineMedicalPlan(), data.getForm202(), data.getForm205(), data.getForm215a(),
				data.getForm208(), data.getStagingLocation(), data.getIncidentLocations(),
				data.getPdfLayoutSettings(), data.isCanineMedicalWorkspaceEligible(),
				IncidentCommandSummary.requiresAnimalMedicalPlan(data),
				data.getHumanMedicalReview(), data.getCanineMedicalReview(),
				java.util.Arrays.stream(PlanReviewArea.values()).map(data::getPlanReviewStatus).toList(),
				showAll.isSelected(), focusedEditsDirty)) {
			previewSelected();
			return;
		}
		try {
		String selected = worklist.getSelectedValue();
		if (!focusedEditsDirty) {
			loadingFocusedEdits = true;
			UiSupport.setTextIfChanged(leaderName, data.getMedicalUnitLeaderAssignment().getName());
			UiSupport.setTextIfChanged(leaderRadio, data.getMedicalUnitLeaderAssignment().getRadioChannel());
			UiSupport.setTextIfChanged(leaderPhone, data.getMedicalUnitLeaderAssignment().getPhoneNumber());
			UiSupport.setTextIfChanged(humanInstruction, data.getMedicalPlan().getCall911Procedure());
			UiSupport.setTextIfChanged(canineInstruction, data.getCanineMedicalPlan().getEmergencyProcedures());
			loadingFocusedEdits = false;
		}
		planBadges.refreshFromModel(data);
		refreshingWorklist = true;
		rebuildWorklist();
		if (selected != null) {
			worklist.setSelectedValue(selected, true);
		}
		if (worklist.getSelectedIndex() < 0 && !workModel.isEmpty()) {
			worklist.setSelectedIndex(0);
		}
		refreshingWorklist = false;
		humanReview.refreshFromModel();
		canineReview.refreshFromModel();
		refreshSelectedItem();
		refreshState.complete();
		} finally {
			loadingFocusedEdits = false;
			refreshingWorklist = false;
		}
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
		for (JTextComponent field : new JTextComponent[] { leaderName, leaderRadio, leaderPhone, humanInstruction,
				canineInstruction }) {
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
	 * Returns visible work count for responsibility-filter tests.
	 *
	 * @return work item count.
	 */
	int workItemCount() {
		return workModel.size();
	}

	/**
	 * Builds worklist and selected preparation details.
	 *
	 * @return split panel.
	 */
	private JSplitPane buildBody() {
		JPanel left = new JPanel(new BorderLayout(4, 4));
		left.setBorder(javax.swing.BorderFactory.createTitledBorder("Responsible work"));
		left.add(showAll, BorderLayout.NORTH);
		left.add(new JScrollPane(worklist), BorderLayout.CENTER);
		JPanel personnelFields = UiSupport.formPanel();
		UiSupport.addRow(personnelFields, 0, "Medical Unit Leader", leaderName);
		UiSupport.addRow(personnelFields, 1, "Radio channel", leaderRadio);
		UiSupport.addRow(personnelFields, 2, "Phone", leaderPhone);
		JPanel humanFields = UiSupport.formPanel();
		UiSupport.addRow(humanFields, 0, "Human emergency instruction", new JScrollPane(humanInstruction));
		JPanel canineFields = UiSupport.formPanel();
		UiSupport.addRow(canineFields, 0, "Canine emergency procedure", new JScrollPane(canineInstruction));
		focusedCards.add(personnelFields, "Personnel and aid stations");
		focusedCards.add(humanFields, HUMAN_PLAN);
		focusedCards.add(canineFields, CANINE_PLAN);
		humanInstruction.setLineWrap(true);
		humanInstruction.setWrapStyleWord(true);
		canineInstruction.setLineWrap(true);
		canineInstruction.setWrapStyleWord(true);
		JPanel actions = new JPanel(new java.awt.GridLayout(0, 2, 4, 4));
		JButton apply = new JButton("Apply focused edits");
		apply.addActionListener(event -> applyFocusedEdits());
		JButton source = new JButton("Open selected full editor");
		source.addActionListener(event -> openSelectedSource());
		actions.add(apply);
		actions.add(source);
		previewCards.add(humanPreview, HUMAN_PLAN);
		previewCards.add(caninePreview, CANINE_PLAN);
		JPanel right = new JPanel(new BorderLayout(4, 4));
		detail.setEditable(false);
		detail.setLineWrap(true);
		detail.setWrapStyleWord(true);
		JPanel upper = new JPanel(new BorderLayout());
		upper.add(new JScrollPane(detail), BorderLayout.CENTER);
		upper.add(focusedCards, BorderLayout.SOUTH);
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

	/** Rebuilds MUL-responsibility items and optional broader context. */
	private void rebuildWorklist() {
		java.util.List<String> rows = new java.util.ArrayList<>();
		rows.add(HUMAN_PLAN);
		if (controller.getData().isCanineMedicalWorkspaceEligible()) {
			rows.add(CANINE_PLAN);
		}
		rows.add("Personnel and aid stations");
		rows.add("Transport and facilities");
		rows.add("Review handoff");
		if (showAll.isSelected()) {
			rows.add("Safety plan status");
			rows.add("Communications plan status");
		}
		UiSupport.reconcileList(workModel, rows);
	}

	/** Refreshes selected details without judging medical sufficiency. */
	private void refreshSelectedItem() {
		if (disposed || !controller.isCurrent(binding)) {
			cancelPreviews();
			return;
		}
		String selected = worklist.getSelectedValue();
		if (selected == null) {
			return;
		}
		AppData data = controller.getData();
		if (HUMAN_PLAN.equals(selected)) {
			UiSupport.setTextIfChanged(detail, "Human plan: " + data.getPlanReviewStatus(PlanReviewArea.HUMAN_MEDICAL).displayLabel()
					+ "\nAid stations: " + data.getMedicalPlan().getAidStations().size() + "\nTransport services: "
					+ data.getMedicalPlan().getTransportServices().size() + "\nHospitals: "
					+ data.getMedicalPlan().getHospitals().size());
			((CardLayout) previewCards.getLayout()).show(previewCards, HUMAN_PLAN);
			((CardLayout) focusedCards.getLayout()).show(focusedCards, HUMAN_PLAN);
		} else if (CANINE_PLAN.equals(selected)) {
			UiSupport.setTextIfChanged(detail, "Canine plan: " + data.getPlanReviewStatus(PlanReviewArea.CANINE_MEDICAL).displayLabel()
					+ "\nLinked veterinary personnel: "
					+ data.getCanineMedicalPlan().getVeterinaryPersonnelResourceIds().size()
					+ "\nAccepted veterinary facilities: "
					+ data.getCanineMedicalPlan().getSelectedFacilities().size());
			((CardLayout) previewCards.getLayout()).show(previewCards, CANINE_PLAN);
			((CardLayout) focusedCards.getLayout()).show(focusedCards, CANINE_PLAN);
		} else if ("Personnel and aid stations".equals(selected)) {
			UiSupport.setTextIfChanged(detail, personnelAndAidStationDetails(data));
			((CardLayout) focusedCards.getLayout()).show(focusedCards, "Personnel and aid stations");
		} else if ("Transport and facilities".equals(selected)) {
			UiSupport.setTextIfChanged(detail, transportAndFacilityDetails(data));
		} else if ("Review handoff".equals(selected)) {
			UiSupport.setTextIfChanged(detail, "Human medical: " + data.getPlanReviewStatus(PlanReviewArea.HUMAN_MEDICAL).displayLabel()
					+ "\nCanine medical: " + data.getPlanReviewStatus(PlanReviewArea.CANINE_MEDICAL).displayLabel()
					+ "\nEach review is a separate, content-bound operator assertion.");
		} else {
			UiSupport.setTextIfChanged(detail, selected.startsWith("Safety")
					? "Safety plan review: " + data.getPlanReviewStatus(PlanReviewArea.SAFETY).displayLabel()
					: "Communications plan review: " + data.getPlanReviewStatus(PlanReviewArea.COMMUNICATIONS).displayLabel());
		}
		boolean planSelected = HUMAN_PLAN.equals(selected) || CANINE_PLAN.equals(selected);
		previewCards.setVisible(planSelected);
		focusedCards.setVisible(HUMAN_PLAN.equals(selected) || CANINE_PLAN.equals(selected)
				|| "Personnel and aid stations".equals(selected));
		boolean reviewsSelected = planSelected || "Review handoff".equals(selected);
		reviewCards.setVisible(reviewsSelected);
		showReviewCards(selected);
		if (planSelected) {
			previewSelected();
		} else {
			cancelPreviews();
		}
	}

	/**
	 * Displays only the review control associated with the selected work item.
	 *
	 * @param selected selected responsible-work item.
	 */
	private void showReviewCards(String selected) {
		if (java.util.Objects.equals(displayedReviewSelection, selected)) {
			return;
		}
		reviewCards.removeAll();
		reviewCards.setLayout(HUMAN_PLAN.equals(selected) || CANINE_PLAN.equals(selected)
				? new BorderLayout() : new java.awt.GridLayout(0, 1, 0, 4));
		humanReview.setVisible(HUMAN_PLAN.equals(selected) || "Review handoff".equals(selected));
		canineReview.setVisible(CANINE_PLAN.equals(selected) || "Review handoff".equals(selected));
		if (HUMAN_PLAN.equals(selected)) {
			reviewCards.add(humanReview, BorderLayout.CENTER);
		} else if (CANINE_PLAN.equals(selected)) {
			reviewCards.add(canineReview, BorderLayout.CENTER);
		} else if ("Review handoff".equals(selected)) {
			reviewCards.add(humanReview);
			reviewCards.add(canineReview);
		}
		reviewCards.revalidate();
		reviewCards.repaint();
		displayedReviewSelection = selected;
	}

	/**
	 * Summarizes the recorded medical leadership and aid-station details.
	 *
	 * @param data active incident.
	 * @return recorded personnel and aid-station details.
	 */
	private static String personnelAndAidStationDetails(AppData data) {
		var leader = data.getMedicalUnitLeaderAssignment();
		StringBuilder details = new StringBuilder("Medical Unit Leader: ").append(leader.getName())
				.append("\nRadio: ").append(leader.getRadioChannel()).append("\nPhone: ").append(leader.getPhoneNumber());
		var plan = data.getMedicalPlan();
		details.append("\nDesignated medical role: ").append(plan.getDesignatedMedicalUnitRole()).append(" — ")
				.append(plan.getDesignatedMedicalUnitName()).append("\nDesignated contact: ")
				.append(plan.getDesignatedMedicalUnitContact()).append("\nAid stations: ")
				.append(plan.getAidStations().size());
		for (var station : plan.getAidStations()) {
			details.append("\n• ").append(station.getName()).append(" — ").append(station.getLocation())
					.append(" | ").append(station.getContactNumbers()).append(" | ").append(station.getFrequency());
		}
		return details.toString();
	}

	/**
	 * Summarizes recorded transport services and receiving facilities.
	 *
	 * @param data active incident.
	 * @return recorded transport and facility details.
	 */
	private static String transportAndFacilityDetails(AppData data) {
		var plan = data.getMedicalPlan();
		StringBuilder details = new StringBuilder("Transport services: ").append(plan.getTransportServices().size());
		for (var service : plan.getTransportServices()) {
			details.append("\n• ").append(service.getService()).append(" — ").append(service.getLocation())
					.append(" | ").append(service.getContactNumbers()).append(" | ").append(service.getAirOrGround())
					.append(" | ").append(service.getLevelOfService());
		}
		details.append("\nHospitals / facilities: ").append(plan.getHospitals().size());
		for (var facility : plan.getHospitals()) {
			details.append("\n• ").append(facility.getName()).append(" — ").append(facility.getAddress())
					.append(" | Ground: ").append(facility.getGroundTravelTime()).append(" | Air: ")
					.append(facility.getAirTravelTime());
		}
		return details.toString();
	}

	/** Applies shared role contacts and separate human/canine instructions. */
	private void applyFocusedEdits() {
		if (!controller.isCurrent(binding)) {
			return;
		}
		MedicalRoleSupport.setMedicalUnitLeader(controller.getData(), leaderName.getText(), leaderRadio.getText(),
				leaderPhone.getText());
		controller.getData().getMedicalPlan().setCall911Procedure(humanInstruction.getText());
		controller.getData().getCanineMedicalPlan().setEmergencyProcedures(canineInstruction.getText());
		focusedEditsDirty = false;
		controller.markDirty();
	}

	/** Automatically requests the latest PDF for the selected medical plan. */
	private void previewSelected() {
		if (disposed || !controller.isCurrent(binding)) {
			cancelPreviews();
			return;
		}
		if (CANINE_PLAN.equals(worklist.getSelectedValue())) {
			AppData data = controller.getData();
			caninePreview.requestPreview(data, PlanReviewSupport.contentDigest(data, PlanReviewArea.CANINE_MEDICAL),
					data.getIncidentContext(), data.getCanineMedicalPlan(), data.getMedicalUnitLeaderAssignment(),
					data.getStagingLocation(), data.getPdfLayoutSettings(), MainFrame.configuredZoneId(data).getId());
		} else if (HUMAN_PLAN.equals(worklist.getSelectedValue())) {
			AppData data = controller.getData();
			humanPreview.requestPreview(data, PlanReviewSupport.contentDigest(data, PlanReviewArea.HUMAN_MEDICAL),
					data.getIncidentContext(), data.getMedicalPlan(), data.getMedicalUnitLeaderAssignment(),
					data.getPdfLayoutSettings(), MainFrame.configuredZoneId(data).getId());
		}
	}

	/** Opens the selected comprehensive medical editor. */
	private void openSelectedSource() {
		if (CANINE_PLAN.equals(worklist.getSelectedValue())) {
			navigator.accept(WorkspaceDestination.canineMedicalPlan());
		} else {
			navigator.accept(WorkspaceDestination.humanMedicalPlan());
		}
	}
}
