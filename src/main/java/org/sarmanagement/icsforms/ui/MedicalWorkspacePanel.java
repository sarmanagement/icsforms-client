/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import javax.swing.JTabbedPane;
import javax.swing.JPanel;
import javax.swing.Icon;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;

/**
 * Shared Medical workspace containing the human and adapted canine medical plans.
 */
public class MedicalWorkspacePanel extends JPanel {
	private static final Color PLAN_ICON_COLOR = new Color(65, 75, 85);
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
		plans.addTab(WorkspaceId.HUMAN_MEDICAL_PLAN.displayLabel(), new MedicalPlanIcon(false), humanPanel);
		plans.setToolTipTextAt(0, "Human medical plan");
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
			plans.addTab(WorkspaceId.CANINE_MEDICAL_PLAN.displayLabel(), new MedicalPlanIcon(true), caninePanel);
			plans.setToolTipTextAt(plans.indexOfComponent(caninePanel), "Canine medical plan");
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

	/**
	 * Paints a small generic person or canine silhouette for its plan tab.
	 */
	private static final class MedicalPlanIcon implements Icon {
		private static final int WIDTH = 20;
		private static final int HEIGHT = 20;
		private final boolean canine;

		/**
		 * Creates an icon for the selected plan type.
		 *
		 * @param canine true to draw a canine icon, false to draw a human icon.
		 */
		private MedicalPlanIcon(boolean canine) {
			this.canine = canine;
		}

		/** {@inheritDoc} */
		@Override
		public int getIconWidth() {
			return WIDTH;
		}

		/** {@inheritDoc} */
		@Override
		public int getIconHeight() {
			return HEIGHT;
		}

		/** {@inheritDoc} */
		@Override
		public void paintIcon(Component component, Graphics graphics, int x, int y) {
			Graphics2D drawing = (Graphics2D) graphics.create();
			try {
				drawing.setColor(PLAN_ICON_COLOR);
				drawing.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				if (canine) {
					paintCanine(drawing, x, y);
				} else {
					paintHuman(drawing, x, y);
				}
			} finally {
				drawing.dispose();
			}
		}

		/**
		 * Paints a human head and shoulders.
		 *
		 * @param drawing icon graphics.
		 * @param x icon horizontal origin.
		 * @param y icon vertical origin.
		 */
		private void paintHuman(Graphics2D drawing, int x, int y) {
			drawing.fillOval(x + 7, y + 2, 7, 7);
			drawing.fillRoundRect(x + 3, y + 10, 15, 9, 7, 7);
		}

		/**
		 * Paints a side-profile canine head with a projecting muzzle and nose.
		 *
		 * @param drawing icon graphics.
		 * @param x icon horizontal origin.
		 * @param y icon vertical origin.
		 */
		private void paintCanine(Graphics2D drawing, int x, int y) {
			drawing.fillOval(x + 4, y + 4, 11, 12);
			drawing.fillPolygon(new Polygon(new int[] {x + 6, x + 2, x + 4, x + 9},
					new int[] {y + 5, y + 7, y + 13, y + 10}, 4));
			drawing.fillPolygon(new Polygon(new int[] {x + 7, x + 14, x + 13, x + 7},
					new int[] {y + 13, y + 13, y + 19, y + 19}, 4));
			drawing.fillRoundRect(x + 11, y + 9, 7, 5, 3, 3);
			drawing.setColor(PLAN_ICON_COLOR);
			drawing.fillOval(x + 17, y + 9, 3, 3);
			drawing.setColor(Color.WHITE);
			drawing.fillOval(x + 11, y + 7, 2, 2);
		}
	}
}
