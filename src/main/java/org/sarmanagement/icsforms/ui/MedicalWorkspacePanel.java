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
import java.awt.RenderingHints;
import java.awt.geom.Path2D;

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
			return  canine ? 27 : WIDTH;
		}

		/** {@inheritDoc} */
		@Override
		public int getIconHeight() {
			return canine ? 24 : HEIGHT;
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
		 * Paints a generic canine silhouette with head, body, legs, and tail.
		 *
		 * @param drawing icon graphics.
		 * @param x icon horizontal origin.
		 * @param y icon vertical origin.
		 */
		private void paintCanine(Graphics2D drawing, int x, int y) {
			Graphics2D g = (Graphics2D) drawing.create();
			try {
				g.translate(x, y);

				// Far-side legs (slightly offset) to suggest depth.
				Path2D farLegs = new Path2D.Double();
				farLegs.moveTo(8, 13);
				farLegs.lineTo(11, 14);
				farLegs.lineTo(10, 17);
				farLegs.lineTo(11, 20);
				farLegs.lineTo(12, 21);
				farLegs.lineTo(9.5, 21);
				farLegs.lineTo(8, 17);
				farLegs.closePath();

				farLegs.moveTo(14, 13);
				farLegs.lineTo(16, 13);
				farLegs.lineTo(15, 20);
				farLegs.lineTo(16, 21);
				farLegs.lineTo(13.5, 21);
				farLegs.closePath();
				g.fill(farLegs);

				Path2D dog = new Path2D.Double();

				// Back and shoulder.
				dog.moveTo(5, 10);
				dog.curveTo(7, 9, 10, 9.5, 13, 9.5);

				// ---- Head/neck tuned: shorter, thicker muzzle ----
				dog.curveTo(14, 9.6, 14.8, 9.0, 15.8, 8.2);

				// Ear.
				dog.lineTo(16.0, 6.4);
				dog.lineTo(16.8, 4.2);
				dog.lineTo(18.1, 6.2);

				// Crown + forehead stop.
				dog.lineTo(19.0, 6.7);
				dog.lineTo(19.4, 7.8);

				// Muzzle top (shorter than prior version).
				dog.lineTo(22.8, 7.7);

				// Nose point.
				dog.lineTo(23.2, 8.7);
				dog.lineTo(22.6, 9.8);

				// Lower muzzle/jaw return (lower for thicker muzzle).
				dog.lineTo(20.2, 10.1);
				dog.lineTo(19.1, 9.3);

				// Short throat into chest.
				dog.curveTo(19.0, 10.9, 18.8, 12.4, 18.0, 14.0);
				dog.lineTo(17.5, 15.0);

				// Near foreleg + paw.
				dog.lineTo(17.5, 21);
				dog.lineTo(19, 22);
				dog.lineTo(16, 22);
				dog.lineTo(15.5, 14.5);

				// Belly.
				dog.curveTo(14, 14.5, 12, 14.5, 10, 14);

				// Hind leg + paw.
				dog.lineTo(9, 17);
				dog.lineTo(7.5, 20.5);
				dog.lineTo(9, 22);
				dog.lineTo(6, 22);
				dog.lineTo(6, 19.5);
				dog.lineTo(7, 16);
				dog.curveTo(5.5, 15, 5, 13.5, 5, 12);

				// Lowered feathered tail.
				dog.curveTo(4.5, 14, 4, 16, 2, 18);
				dog.lineTo(1, 17);
				dog.curveTo(2.5, 14.5, 2.5, 11, 5, 10);

				dog.closePath();
				g.fill(dog);
			} finally {
				g.dispose();
			}
		}
	}
}
