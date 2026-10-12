/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.VetFacility;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafetyDashboardPanelTest {
	@Test
	void readinessSummaryDistinguishesInactiveDraftUnknownAndStaleRouteStates() {
		AppData data = new AppData();
		data.setIncidentMode(org.sarmanagement.icsforms.model.IncidentMode.GENERIC);
		data.getMedicalPlan().setActive(true);
		data.getCanineMedicalPlan().setActive(true);
		data.getStagingLocation().setAddress("1 Fictional Road");
		VetFacility unknownVet = new VetFacility();
		unknownVet.setName("Fictional Veterinary Hospital");
		unknownVet.setAvailable24Hours(null);
		unknownVet.setDistance(1250, 500, "straight-line estimate", "test estimate",
				java.time.Instant.now(), data.getStagingLocation().getGeneration() - 1);
		data.getCanineMedicalPlan().getSelectedFacilities().add(unknownVet);

		String summary = SafetyDashboardPanel.summaryText(data);

		assertTrue(summary.contains("Standard ICS 206: active; recorded review status: Not reviewed"));
		assertTrue(summary.contains("Adapted canine ICS 206CANINE: active; recorded review status: Not reviewed"));
		assertTrue(summary.contains("1 unknown"));
		assertTrue(summary.contains("1 without a current-base distance"));
		assertTrue(summary.contains("Routing does not verify either"));
		assertFalse(summary.toLowerCase().contains("clinically sufficient"));
	}

	@Test
	void inactivePlansAreNotDescribedAsReviewedOrApproved() {
		String summary = SafetyDashboardPanel.summaryText(new AppData());

		assertTrue(summary.contains("Standard ICS 206: inactive"));
		assertTrue(summary.contains("Adapted canine ICS 206CANINE: inactive"));
		assertFalse(summary.contains("recorded review status: Approved"));
	}

	@Test
	void readinessShortcutsNavigateToSharedEditors() throws Exception {
		List<String> opened = new ArrayList<>();
		SafetyDashboardPanel[] panel = new SafetyDashboardPanel[1];
		SwingUtilities.invokeAndWait(() -> {
			panel[0] = new SafetyDashboardPanel(opened::add);
			for (JButton button : buttons(panel[0])) {
				button.doClick();
			}
		});

		assertTrue(opened.contains("Safety"));
		assertTrue(opened.contains("Medical Plan (ICS 206)"));
		assertTrue(opened.contains("Canine Medical Plan (ICS 206CANINE, adapted)"));
		assertTrue(opened.contains("Org Chart"));
		assertTrue(opened.contains("T-Cards"));
	}

	/**
	 * Collects nested Swing buttons for interaction tests.
	 *
	 * @param panel parent component.
	 * @return buttons in component order.
	 */
	private List<JButton> buttons(JPanel panel) {
		List<JButton> result = new ArrayList<>();
		for (Component child : panel.getComponents()) {
			if (child instanceof JButton button) {
				result.add(button);
			} else if (child instanceof JPanel nested) {
				result.addAll(buttons(nested));
			} else if (child instanceof javax.swing.JScrollPane scroll
					&& scroll.getViewport().getView() instanceof JPanel nested) {
				result.addAll(buttons(nested));
			}
		}
		return result;
	}
}
