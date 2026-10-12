/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.api.Test;

import javax.swing.JMenu;
import javax.swing.JRadioButtonMenuItem;
import java.util.List;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainFrameDashboardTest {
	@Test
	void defaultDashboardPreservesLegacyWorkspaceOrder() {
		List<String> titles = MainFrame.Dashboard.IC_ICP.workspaceTitles();

		assertEquals("Shared", titles.get(0));
		assertEquals("Org Chart", titles.get(1));
		assertEquals("ICS 201", titles.get(2));
		assertEquals("ICS 202", titles.get(3));
		assertEquals("ICS 204", titles.get(4));
		assertTrue(titles.contains("Medical"));
	}

	@Test
	void dashboardChoicesAreViewMenuRadioItemsWithIcIcpSelectedByDefault() {
		List<MainFrame.Dashboard> selected = new ArrayList<>();

		JMenu dashboardMenu = MainFrame.createDashboardMenu(MainFrame.Dashboard.IC_ICP, selected::add);

		assertEquals("Dashboard", dashboardMenu.getText());
		assertEquals(4, dashboardMenu.getItemCount());
		assertTrue(((JRadioButtonMenuItem) dashboardMenu.getItem(0)).isSelected());
		dashboardMenu.getItem(2).doClick();
		assertEquals(List.of(MainFrame.Dashboard.OPERATIONS), selected);
	}

	@Test
	void operationsAndCommunicationsExposeSharedSafetyOrgAndMedicalEditorsWithoutIcs205() {
		for (MainFrame.Dashboard dashboard :
				List.of(MainFrame.Dashboard.OPERATIONS, MainFrame.Dashboard.COMMUNICATIONS)) {
			List<String> titles = dashboard.workspaceTitles();
			assertTrue(titles.contains("Safety"));
			assertTrue(titles.contains("Org Chart"));
			assertTrue(titles.contains("Medical"));
			assertEquals(1, titles.stream().filter("Medical"::equals).count());
			assertFalseContainsIcs205(titles);
		}
	}

	@Test
	void safetyDashboardIncludesReadinessOverviewAndLinksToCommonSafetyWorkspaces() {
		List<String> titles = MainFrame.Dashboard.SAFETY_OFFICER.workspaceTitles();

		assertEquals("Safety Overview", titles.get(0));
		assertTrue(titles.contains("Safety"));
		assertTrue(titles.contains("Org Chart"));
		assertTrue(titles.contains("Medical"));
	}

	/**
	 * Confirms that the Communications dashboard does not invent ICS 205 workspaces.
	 *
	 * @param titles dashboard workspace titles.
	 */
	private void assertFalseContainsIcs205(List<String> titles) {
		assertTrue(titles.stream().noneMatch(title -> title.equals("ICS 205") || title.equals("ICS 205A")));
	}
}
