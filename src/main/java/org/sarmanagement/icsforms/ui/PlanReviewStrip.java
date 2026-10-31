/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.sarmanagement.icsforms.model.PlanReviewArea;
import org.sarmanagement.icsforms.model.PlanReviewAssertion;
import org.sarmanagement.icsforms.model.PlanReviewStatus;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.time.format.DateTimeFormatter;

/**
 * Reusable focused control for viewing and recording plan review assertions.
 */
public class PlanReviewStrip extends JPanel {
	private static final DateTimeFormatter REVIEW_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
	private final AppController controller;
	private final PlanReviewArea area;
	private final Runnable beforeReview;
	private final JLabel status = new JLabel();
	private final JTextField reviewer = new JTextField(16);

	/**
	 * Creates a review strip for one independent plan area.
	 *
	 * @param controller incident controller.
	 * @param area plan area.
	 * @param beforeReview callback that commits pending editor input before review.
	 */
	public PlanReviewStrip(AppController controller, PlanReviewArea area, Runnable beforeReview) {
		super(new BorderLayout(6, 4));
		this.controller = controller;
		this.area = area;
		this.beforeReview = beforeReview == null ? () -> {
		} : beforeReview;
		JPanel heading = new JPanel(new BorderLayout(6, 0));
		heading.add(new JLabel(area.displayLabel() + " review:"), BorderLayout.WEST);
		heading.add(status, BorderLayout.CENTER);
		add(heading, BorderLayout.NORTH);
		JPanel controls = new JPanel(new BorderLayout(6, 0));
		controls.add(new JLabel("Reviewer"), BorderLayout.WEST);
		reviewer.getAccessibleContext().setAccessibleName(area.displayLabel() + " plan reviewer");
		controls.add(reviewer, BorderLayout.CENTER);
		JButton review = new JButton("Review current content");
		review.addActionListener(event -> recordReview());
		controls.add(review, BorderLayout.EAST);
		add(controls, BorderLayout.CENTER);
		refreshFromModel();
	}

	/** Refreshes status while retaining a reviewer draft currently being typed. */
	public void refreshFromModel() {
		PlanReviewStatus reviewStatus = controller.getData().getPlanReviewStatus(area);
		PlanReviewAssertion assertion = controller.getData().getReviewAssertion(area);
		String detail = "";
		if (assertion.isRecorded()) {
			detail = " by " + assertion.getReviewedBy() + " at "
					+ UiSupport.formatDateTime(assertion.getReviewedAt(), REVIEW_TIME);
		}
		status.setText(reviewStatus.displayLabel());
		status.setToolTipText(reviewStatus.displayLabel() + detail);
		status.getAccessibleContext().setAccessibleDescription(reviewStatus.displayLabel() + detail);
		RolePanelSupport.color(status, switch (reviewStatus) {
			case REVIEWED_APPROVED -> RolePanelSupport.CURRENT;
			case CHANGED_SINCE_REVIEW -> RolePanelSupport.PENDING;
			case NOT_YET_REVIEWED -> RolePanelSupport.OPTIONAL;
		});
		status.getAccessibleContext().setAccessibleName(area.displayLabel() + " review status");
		if (reviewer.getText().isBlank()) {
			String currentUser = controller.getData().getIncidentContext().getCurrentUser();
			reviewer.setText(currentUser == null ? "" : currentUser);
		}
	}

	/** Commits pending plan edits and records the operator's explicit assertion. */
	private void recordReview() {
		try {
			beforeReview.run();
			controller.getData().recordPlanReview(area, reviewer.getText(), null);
			controller.markDirty();
			refreshFromModel();
		} catch (IllegalArgumentException | IllegalStateException exception) {
			JOptionPane.showMessageDialog(this, exception.getMessage(), "Unable to record review",
					JOptionPane.ERROR_MESSAGE);
		}
	}
}
