/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.CanineMedicalPlan;
import org.sarmanagement.icsforms.model.MedicalPlan;
import org.sarmanagement.icsforms.model.StagingLocation;
import org.sarmanagement.icsforms.model.VetFacility;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Readiness overview for the Safety Officer dashboard; links open shared editors.
 */
public class SafetyDashboardPanel extends JPanel {
	private final JTextArea summary = new JTextArea();
	private final Consumer<String> workspaceOpener;

	/**
	 * Creates a read-only medical/safety summary with shortcuts to shared workspaces.
	 *
	 * @param workspaceOpener callback receiving a shared workspace title.
	 */
	public SafetyDashboardPanel(Consumer<String> workspaceOpener) {
		super(new BorderLayout(8, 8));
		this.workspaceOpener = workspaceOpener;
		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		add(new JLabel("Readiness indicators reflect saved plan fields, not medical sufficiency or operational verification."),
				BorderLayout.NORTH);
		summary.setEditable(false);
		summary.setLineWrap(true);
		summary.setWrapStyleWord(true);
		add(new JScrollPane(summary), BorderLayout.CENTER);
		JPanel shortcuts = new JPanel(new FlowLayout(FlowLayout.LEFT));
		addShortcut(shortcuts, "Safety message / hazards", "Safety");
		addShortcut(shortcuts, "Medical Plan (ICS 206)", "Medical Plan (ICS 206)");
		addShortcut(shortcuts, "Canine Medical Plan (adapted)", "Canine Medical Plan (ICS 206CANINE, adapted)");
		addShortcut(shortcuts, "Org Chart", "Org Chart");
		addShortcut(shortcuts, "Resources / T-Cards", "T-Cards");
		add(shortcuts, BorderLayout.SOUTH);
	}

	/**
	 * Refreshes readiness text from shared incident state without changing it.
	 *
	 * @param data current incident.
	 */
	public void refreshFromModel(AppData data) {
		summary.setText(summaryText(data));
		summary.setCaretPosition(0);
	}

	/**
	 * Formats a cautious summary of plan state, location resolution and route provenance.
	 *
	 * @param data current incident.
	 * @return display-only readiness summary.
	 */
	static String summaryText(AppData data) {
		List<String> lines = new ArrayList<>();
		lines.add("Incident: " + data.getIncidentContext().getIncidentName());
		lines.add("Standard ICS 206: " + planStatus(data.getMedicalPlan().isActive(),
				data.getMedicalPlan().getReviewStatus()));
		MedicalPlan medical = data.getMedicalPlan();
		if (medical.isSimpleMode()) {
			lines.add("Emergency instruction: " + text(medical.getCall911Procedure()));
		} else if (!medical.getEmergencyProcedures().isBlank()) {
			lines.add("Emergency procedures: " + text(medical.getEmergencyProcedures()));
		}
		CanineMedicalPlan canine = data.getCanineMedicalPlan();
		lines.add("Adapted canine ICS 206CANINE: "
				+ planStatus(canine.isActive(), canine.getReviewStatus()));
		StagingLocation base = data.getStagingLocation();
		lines.add("Staging/base: " + text(base.getLabel()) + " — " + text(base.getAddress())
				+ (base.hasValidCoordinates() ? " (coordinates available)" : " (coordinates unresolved)"));
		int confirmed = 0;
		int unknown = 0;
		int unresolved = 0;
		int roadRoutes = 0;
		int estimates = 0;
		for (VetFacility facility : data.getCanineMedicalPlan().getSelectedFacilities()) {
			if (Boolean.TRUE.equals(facility.getAvailable24Hours())) {
				confirmed++;
			} else if (facility.getAvailable24Hours() == null) {
				unknown++;
			}
			if (!facility.hasCurrentDistance(base.getGeneration())) {
				unresolved++;
			} else if (facility.getDistanceMethod().contains("straight-line")) {
				estimates++;
			} else {
				roadRoutes++;
			}
		}
		lines.add("Canine plan facilities: " + confirmed + " marked 24-hour yes, " + unknown
				+ " unknown, " + roadRoutes + " current non-estimate routes, " + estimates
				+ " straight-line estimates, " + unresolved + " without a current-base distance.");
		lines.add("Verify facility availability and capability directly. Routing does not verify either.");
		return String.join("\n", lines);
	}

	/**
	 * Adds one deep-link button to a shared editor.
	 *
	 * @param panel button container.
	 * @param label visible button text.
	 * @param workspace shared editor title.
	 */
	private void addShortcut(JPanel panel, String label, String workspace) {
		JButton button = new JButton(label);
		button.addActionListener(event -> workspaceOpener.accept(workspace));
		panel.add(button);
	}

	/**
	 * Summarizes explicit plan activation and review metadata.
	 *
	 * @param active whether the plan is active.
	 * @param reviewStatus recorded review status.
	 * @return cautious plan state label.
	 */
	private static String planStatus(boolean active, String reviewStatus) {
		if (!active) {
			return "inactive";
		}
		String status = text(reviewStatus);
		return "active; recorded review status: " + (status.isBlank() ? "Not reviewed" : status);
	}

	/**
	 * Converts null values to empty display text.
	 *
	 * @param value source value.
	 * @return non-null display text.
	 */
	private static String text(String value) {
		return value == null ? "" : value;
	}
}
