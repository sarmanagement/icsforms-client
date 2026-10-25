/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.MedicalPlan;
import org.sarmanagement.icsforms.model.VetFacility;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
	void summaryIncludesSharedMedicalRolesAndAidStationDetails() {
		AppData data = new AppData();
		data.getOrganizationalChart().setSafetyOfficerName("Fictional Safety Officer");
		data.getOrganizationalChart().setMedicalUnitLeaderName("Fictional Medical Leader");
		MedicalPlan.AidStation station = new MedicalPlan.AidStation();
		station.setName("Fictional Aid Station");
		station.setLocation("Fictional Base");
		data.getMedicalPlan().getAidStations().add(station);
		data.getCanineMedicalPlan().setAidStationInformation("Fictional canine aid station");

		String summary = SafetyDashboardPanel.summaryText(data);

		assertTrue(summary.contains("Safety Officer: Fictional Safety Officer"));
		assertTrue(summary.contains("Medical Unit Leader: Fictional Medical Leader"));
		assertTrue(summary.contains("Medical aid stations: Fictional Aid Station — Fictional Base"));
		assertTrue(summary.contains("Canine medical aid stations: Fictional canine aid station"));
	}

	@Test
	void overviewContainsPreviewTabsForTheThreeRequestedForms() throws Exception {
		SafetyDashboardPanel[] panel = new SafetyDashboardPanel[1];
		SwingUtilities.invokeAndWait(() -> {
			panel[0] = new SafetyDashboardPanel(ignored -> { });
			panel[0].refreshFromModel(new AppData());
		});

		assertEquals("Readiness Summary", panel[0].overviewTabs().getTitleAt(0));
		assertEquals("ICS 208 Safety Message", panel[0].overviewTabs().getTitleAt(1));
		assertEquals("ICS 206 Medical Plan", panel[0].overviewTabs().getTitleAt(2));
		assertEquals("ICS 206CANINE (adapted)", panel[0].overviewTabs().getTitleAt(3));
		SwingUtilities.invokeAndWait(() -> {
			for (int index = 1; index < panel[0].overviewTabs().getTabCount(); index++) {
				panel[0].overviewTabs().setSelectedIndex(index);
				assertTrue(containsText(panel[0].overviewTabs().getComponentAt(index), "page(s) — preview only"));
			}
		});
	}

	@Test
	void safetyOverviewVisibilitySurvivesManualOpeningOutsideItsRoleDashboard() {
		assertFalse(MainFrame.safetyDashboardVisible(MainFrame.Dashboard.IC_ICP, false));
		assertTrue(MainFrame.safetyDashboardVisible(MainFrame.Dashboard.IC_ICP, true));
		assertTrue(MainFrame.safetyDashboardVisible(MainFrame.Dashboard.SAFETY_OFFICER, false));
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
		List<WorkspaceDestination> opened = new ArrayList<>();
		SafetyDashboardPanel[] panel = new SafetyDashboardPanel[1];
		SwingUtilities.invokeAndWait(() -> {
			panel[0] = new SafetyDashboardPanel(opened::add);
			for (JButton button : buttons(panel[0])) {
				button.doClick();
			}
		});

		assertTrue(opened.contains(WorkspaceDestination.of(WorkspaceId.SAFETY)));
		assertTrue(opened.contains(WorkspaceDestination.humanMedicalPlan()));
		assertTrue(opened.contains(WorkspaceDestination.canineMedicalPlan()));
		assertTrue(opened.contains(WorkspaceDestination.of(WorkspaceId.ORG_CHART)));
		assertTrue(opened.contains(WorkspaceDestination.of(WorkspaceId.TCARDS)));
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

	/**
	 * Checks whether a nested component tree contains a label with the given text.
	 *
	 * @param component component tree root.
	 * @param text expected text fragment.
	 * @return true when a label includes the text fragment.
	 */
	private static boolean containsText(Component component, String text) {
		if (component instanceof javax.swing.JLabel label && label.getText() != null
				&& label.getText().contains(text)) {
			return true;
		}
		if (component instanceof java.awt.Container container) {
			for (Component child : container.getComponents()) {
				if (containsText(child, text)) {
					return true;
				}
			}
		}
		return false;
	}
}
