/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.ObjectiveApprovalStatus;
import org.sarmanagement.icsforms.model.PlanReviewArea;
import org.sarmanagement.icsforms.model.PlanReviewStatus;

import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Compact role-specific indicators of content and review work owed to command.
 */
final class RolePlanStatusBadges extends JPanel {
	private static final String OBJECTIVES = "Objectives";
	private static final Map<String, PlanReviewArea> REVIEW_AREAS = Map.of(
			"Safety", PlanReviewArea.SAFETY,
			"Human medical", PlanReviewArea.HUMAN_MEDICAL,
			"Canine medical", PlanReviewArea.CANINE_MEDICAL,
			"Communications", PlanReviewArea.COMMUNICATIONS);

	private final Map<String, JLabel> badges = new LinkedHashMap<>();

	/**
	 * Creates compact indicators for the named role-plan status blocks.
	 *
	 * @param data current incident.
	 * @param sections labels to display, in order.
	 */
	RolePlanStatusBadges(AppData data, String... sections) {
		super(new GridLayout(1, Math.max(1, sections.length), 3, 0));
		setPreferredSize(new Dimension(Math.max(140, sections.length * 142), 60));
		getAccessibleContext().setAccessibleName("Plan work status for this role");
		for (String section : sections) {
			JLabel badge = new JLabel();
			badge.setOpaque(true);
			badge.setForeground(Color.BLACK);
			badge.setBorder(javax.swing.BorderFactory.createCompoundBorder(
					javax.swing.BorderFactory.createEtchedBorder(),
					javax.swing.BorderFactory.createEmptyBorder(3, 5, 3, 5)));
			badge.setFont(badge.getFont().deriveFont(10f));
			badge.getAccessibleContext().setAccessibleName(section + " plan status");
			badges.put(section, badge);
			add(badge);
		}
		refreshFromModel(data);
	}

	/**
	 * Refreshes the compact status from the current incident facts.
	 *
	 * @param data current incident.
	 */
	void refreshFromModel(AppData data) {
		for (Map.Entry<String, JLabel> entry : badges.entrySet()) {
			String section = entry.getKey();
			JLabel badge = entry.getValue();
			if (OBJECTIVES.equals(section)) {
				updateObjectives(data, badge);
			} else {
				PlanReviewArea area = REVIEW_AREAS.get(section);
				if (area != null) {
					updateReviewArea(data, area, section, badge);
				}
			}
		}
	}

	/**
	 * Updates the objective-writing and approval status.
	 *
	 * @param data current incident.
	 * @param badge rendered status.
	 */
	private static void updateObjectives(AppData data, JLabel badge) {
		long count = data.getForm202().getStructuredObjectives().stream()
				.filter(objective -> objective.getText() != null && !objective.getText().isBlank()).count();
		ObjectiveApprovalStatus approval = data.getForm202().getObjectiveApprovalStatus();
		Color color = count == 0 ? RolePanelSupport.MISSING
				: approval == ObjectiveApprovalStatus.APPROVED_CURRENT ? RolePanelSupport.CURRENT
						: RolePanelSupport.PENDING;
		String summary = count + (count == 1 ? " objective" : " objectives");
		String status = approval.displayLabel();
		updateBadge(badge, OBJECTIVES, summary, status, color,
				"Recorded objectives and content-bound approval status; not objective achievement or readiness.");
	}

	/**
	 * Updates one plan-review and content-presence status.
	 *
	 * @param data current incident.
	 * @param area plan area.
	 * @param label visible section label.
	 * @param badge rendered status.
	 */
	private static void updateReviewArea(AppData data, PlanReviewArea area, String label, JLabel badge) {
		long count = IncidentCommandSummary.recordedContent(data, area);
		boolean required = area != PlanReviewArea.CANINE_MEDICAL
				|| IncidentCommandSummary.requiresAnimalMedicalPlan(data);
		PlanReviewStatus review = data.getPlanReviewStatus(area);
		String state = switch (review) {
			case REVIEWED_APPROVED -> "Reviewed — current";
			case CHANGED_SINCE_REVIEW -> "Changed since review";
			case NOT_YET_REVIEWED -> "Not yet reviewed";
		};
		Color color = count == 0 ? required ? RolePanelSupport.MISSING : RolePanelSupport.OPTIONAL
				: review == PlanReviewStatus.REVIEWED_APPROVED ? RolePanelSupport.CURRENT : RolePanelSupport.PENDING;
		String content = count == 0 ? "No content recorded" : count + " content items";
		if (!required) {
			state += " · optional";
		}
		updateBadge(badge, label, content, state, color,
				"Recorded plan content and explicit review only; not completeness or operational readiness.");
	}

	/**
	 * Applies concise visible, tooltip, and accessible status text.
	 *
	 * @param badge status label.
	 * @param title section name.
	 * @param content recorded content summary.
	 * @param status review or approval summary.
	 * @param color status color.
	 * @param caveat limitation of the recorded status.
	 */
	private static void updateBadge(JLabel badge, String title, String content, String status, Color color,
			String caveat) {
		String text = title + ": " + content + "; " + status;
		badge.setText("<html><b>" + RolePanelSupport.htmlText(title) + "</b><br>"
				+ RolePanelSupport.htmlText(content) + "<br>" + RolePanelSupport.htmlText(status) + "</html>");
		badge.setBackground(color);
		badge.setToolTipText(text + ". " + caveat);
		badge.getAccessibleContext().setAccessibleDescription(text + ". " + caveat);
	}
}
