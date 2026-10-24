/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import javax.swing.JTabbedPane;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Component;

/**
 * Shared Medical workspace containing the human and adapted canine medical plans.
 */
public class MedicalWorkspacePanel extends JPanel {
	private final Ics206Panel humanPanel;
	private final CanineMedicalPanel caninePanel;
	private final JTabbedPane plans = new JTabbedPane();
	private boolean canineVisible;
	private Component lastSelectedPlan;

	/**
	 * Creates the shared medical workspace around the two independent plan editors.
	 *
	 * @param humanPanel standard human ICS 206 editor.
	 * @param caninePanel adapted canine ICS 206CANINE editor.
	 */
	public MedicalWorkspacePanel(Ics206Panel humanPanel, CanineMedicalPanel caninePanel) {
		super(new BorderLayout());
		this.humanPanel = humanPanel;
		this.caninePanel = caninePanel;
		plans.addTab(WorkspaceId.HUMAN_MEDICAL_PLAN.displayLabel(), humanPanel);
		lastSelectedPlan = humanPanel;
		plans.addChangeListener(event -> flushPreviousPlan());
		add(plans, BorderLayout.CENTER);
	}

	/**
	 * Updates canine-plan visibility without discarding either editor's data.
	 *
	 * @param visible true when canine mode/card eligibility or explicit access allows it.
	 */
	public void setCanineVisible(boolean visible) {
		if (canineVisible == visible) {
			return;
		}
		canineVisible = visible;
		int canineIndex = plans.indexOfComponent(caninePanel);
		if (visible && canineIndex < 0) {
			plans.addTab(WorkspaceId.CANINE_MEDICAL_PLAN.displayLabel(), caninePanel);
		} else if (!visible && canineIndex >= 0) {
			boolean selectedCanine = plans.getSelectedComponent() == caninePanel;
			plans.removeTabAt(canineIndex);
			if (selectedCanine) {
				plans.setSelectedComponent(humanPanel);
			}
		}
	}

	/**
	 * Selects the standard human plan.
	 */
	public void selectHumanPlan() {
		plans.setSelectedComponent(humanPanel);
	}

	/**
	 * Makes the canine editor visible and selects it for an explicit saved-plan request.
	 */
	public void openCaninePlan() {
		setCanineVisible(true);
		plans.setSelectedComponent(caninePanel);
	}

	/**
	 * Returns the nested tabbed pane for focused UI tests.
	 *
	 * @return medical plan tabs.
	 */
	JTabbedPane planTabs() {
		return plans;
	}

	/**
	 * Flushes table-cell edits from the previous editor when switching nested medical tabs.
	 */
	private void flushPreviousPlan() {
		Component selectedPlan = plans.getSelectedComponent();
		if (selectedPlan == null || selectedPlan == lastSelectedPlan) {
			return;
		}
		if (lastSelectedPlan == humanPanel) {
			humanPanel.pushToModel();
		} else if (lastSelectedPlan == caninePanel) {
			caninePanel.pushToModel();
		}
		lastSelectedPlan = selectedPlan;
	}
}
