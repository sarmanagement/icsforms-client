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
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.util.function.Consumer;

/**
 * Live Medical Unit Leader preparation and review-handoff work panel.
 */
public class MedicalUnitLeaderRolePanel extends JPanel {
	private static final String HUMAN_PLAN = "Human medical plan preparation";
	private static final String CANINE_PLAN = "Canine medical plan preparation";
	private final AppController controller;
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
	private final JPanel previewCards = new JPanel(new CardLayout());
	private final AsyncFormPreviewPanel humanPreview = new AsyncFormPreviewPanel(new Ics206PdfRenderer());
	private final AsyncFormPreviewPanel caninePreview = new AsyncFormPreviewPanel(new Ics206CaninePdfRenderer());
	private final PlanReviewStrip humanReview;
	private final PlanReviewStrip canineReview;
	private final AppController.IncidentChangeSubscription subscription;

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
		humanReview = new PlanReviewStrip(controller, PlanReviewArea.HUMAN_MEDICAL, this::applyFocusedEdits);
		canineReview = new PlanReviewStrip(controller, PlanReviewArea.CANINE_MEDICAL, this::applyFocusedEdits);
		add(new JLabel("MEDICAL UNIT LEADER | Human and canine plans remain separate"), BorderLayout.NORTH);
		add(buildBody(), BorderLayout.CENTER);
		showAll.addActionListener(event -> rebuildWorklist());
		worklist.addListSelectionListener(event -> {
			if (!event.getValueIsAdjusting()) {
				refreshSelectedItem();
			}
		});
		refreshFromModel();
		subscription = controller.addIncidentChangeListener(change -> {
			if (controller.isCurrent(binding)) {
				refreshFromModel();
				humanPreview.markOutOfDate();
				caninePreview.markOutOfDate();
			}
		});
	}

	/** Releases listener and preview workers. */
	public void dispose() {
		subscription.close();
		humanPreview.cancel();
		caninePreview.cancel();
	}

	/** Refreshes shared plan facts while preserving selection. */
	public void refreshFromModel() {
		String selected = worklist.getSelectedValue();
		AppData data = controller.getData();
		leaderName.setText(data.getMedicalUnitLeaderAssignment().getName());
		leaderRadio.setText(data.getMedicalUnitLeaderAssignment().getRadioChannel());
		leaderPhone.setText(data.getMedicalUnitLeaderAssignment().getPhoneNumber());
		humanInstruction.setText(data.getMedicalPlan().getCall911Procedure());
		canineInstruction.setText(data.getCanineMedicalPlan().getEmergencyProcedures());
		rebuildWorklist();
		if (selected != null) {
			worklist.setSelectedValue(selected, true);
		}
		if (worklist.getSelectedIndex() < 0 && !workModel.isEmpty()) {
			worklist.setSelectedIndex(0);
		}
		humanReview.refreshFromModel();
		canineReview.refreshFromModel();
		refreshSelectedItem();
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
		JPanel focused = UiSupport.formPanel();
		UiSupport.addRow(focused, 0, "Medical Unit Leader", leaderName);
		UiSupport.addRow(focused, 1, "Radio channel", leaderRadio);
		UiSupport.addRow(focused, 2, "Phone", leaderPhone);
		UiSupport.addRow(focused, 3, "Human emergency instruction", new JScrollPane(humanInstruction));
		UiSupport.addRow(focused, 4, "Canine emergency procedure", new JScrollPane(canineInstruction));
		JPanel actions = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEADING));
		JButton apply = new JButton("Apply focused edits");
		apply.addActionListener(event -> applyFocusedEdits());
		JButton preview = new JButton("Preview selected plan");
		preview.addActionListener(event -> previewSelected());
		JButton source = new JButton("Open selected full editor");
		source.addActionListener(event -> openSelectedSource());
		actions.add(apply);
		actions.add(preview);
		actions.add(source);
		JPanel reviews = new JPanel(new java.awt.GridLayout(0, 1));
		reviews.add(humanReview);
		reviews.add(canineReview);
		previewCards.add(humanPreview, HUMAN_PLAN);
		previewCards.add(caninePreview, CANINE_PLAN);
		JPanel right = new JPanel(new BorderLayout(4, 4));
		detail.setEditable(false);
		detail.setLineWrap(true);
		detail.setWrapStyleWord(true);
		JPanel upper = new JPanel(new BorderLayout());
		upper.add(new JScrollPane(detail), BorderLayout.CENTER);
		upper.add(focused, BorderLayout.SOUTH);
		right.add(upper, BorderLayout.NORTH);
		right.add(previewCards, BorderLayout.CENTER);
		JPanel bottom = new JPanel(new BorderLayout());
		bottom.add(reviews, BorderLayout.CENTER);
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
		workModel.clear();
		workModel.addElement(HUMAN_PLAN);
		if (controller.getData().isCanineMedicalWorkspaceEligible()) {
			workModel.addElement(CANINE_PLAN);
		}
		workModel.addElement("Personnel and aid stations");
		workModel.addElement("Transport and facilities");
		workModel.addElement("Review handoff");
		if (showAll.isSelected()) {
			workModel.addElement("Safety plan status");
			workModel.addElement("Communications plan status");
		}
	}

	/** Refreshes selected details without judging medical sufficiency. */
	private void refreshSelectedItem() {
		String selected = worklist.getSelectedValue();
		if (selected == null) {
			return;
		}
		AppData data = controller.getData();
		if (HUMAN_PLAN.equals(selected)) {
			detail.setText("Human plan: " + data.getPlanReviewStatus(PlanReviewArea.HUMAN_MEDICAL).displayLabel()
					+ "\nAid stations: " + data.getMedicalPlan().getAidStations().size() + "\nTransport services: "
					+ data.getMedicalPlan().getTransportServices().size() + "\nHospitals: "
					+ data.getMedicalPlan().getHospitals().size());
			((CardLayout) previewCards.getLayout()).show(previewCards, HUMAN_PLAN);
		} else if (CANINE_PLAN.equals(selected)) {
			detail.setText("Canine plan: " + data.getPlanReviewStatus(PlanReviewArea.CANINE_MEDICAL).displayLabel()
					+ "\nLinked veterinary personnel: "
					+ data.getCanineMedicalPlan().getVeterinaryPersonnelResourceIds().size()
					+ "\nAccepted veterinary facilities: "
					+ data.getCanineMedicalPlan().getSelectedFacilities().size());
			((CardLayout) previewCards.getLayout()).show(previewCards, CANINE_PLAN);
		} else {
			detail.setText("Recorded plan information is displayed without an automatic sufficiency judgment.\n"
					+ "Use the focused fields or open the comprehensive editor for extensive changes.");
		}
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
		controller.markDirty();
	}

	/** Requests the standard selected medical PDF. */
	private void previewSelected() {
		if (CANINE_PLAN.equals(worklist.getSelectedValue())) {
			caninePreview.requestPreview(controller.getData(),
					PlanReviewSupport.contentDigest(controller.getData(), PlanReviewArea.CANINE_MEDICAL));
		} else {
			humanPreview.requestPreview(controller.getData(),
					PlanReviewSupport.contentDigest(controller.getData(), PlanReviewArea.HUMAN_MEDICAL));
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
