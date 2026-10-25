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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainFrameDashboardTest {
	@Test
	void defaultDashboardPreservesLegacyWorkspaceOrder() {
		List<WorkspaceId> workspaces = MainFrame.Dashboard.IC_ICP.workspaceIds();

		assertEquals(WorkspaceId.SHARED, workspaces.get(0));
		assertEquals(WorkspaceId.ORG_CHART, workspaces.get(1));
		assertEquals(WorkspaceId.ICS_201, workspaces.get(2));
		assertEquals(WorkspaceId.ICS_202, workspaces.get(3));
		assertEquals(WorkspaceId.ICS_204, workspaces.get(4));
		assertTrue(workspaces.contains(WorkspaceId.MEDICAL));
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
	void operationsAndCommunicationsExposeSharedSafetyOrgMedicalAndOptionalRadioEditors() {
		for (MainFrame.Dashboard dashboard :
				List.of(MainFrame.Dashboard.OPERATIONS, MainFrame.Dashboard.COMMUNICATIONS)) {
			List<WorkspaceId> workspaces = dashboard.workspaceIds();
			assertTrue(workspaces.contains(WorkspaceId.SAFETY));
			assertTrue(workspaces.contains(WorkspaceId.ORG_CHART));
			assertTrue(workspaces.contains(WorkspaceId.MEDICAL));
			assertEquals(1, workspaces.stream().filter(WorkspaceId.MEDICAL::equals).count());
			assertTrue(workspaces.contains(WorkspaceId.ICS_205));
		}
	}

	@Test
	void safetyDashboardIncludesReadinessOverviewAndLinksToCommonSafetyWorkspaces() {
		List<WorkspaceId> workspaces = MainFrame.Dashboard.SAFETY_OFFICER.workspaceIds();

		assertEquals(WorkspaceId.SAFETY_OVERVIEW, workspaces.get(0));
		assertTrue(workspaces.contains(WorkspaceId.SAFETY));
		assertTrue(workspaces.contains(WorkspaceId.ORG_CHART));
		assertTrue(workspaces.contains(WorkspaceId.MEDICAL));
	}

	@Test
	void workspaceDestinationsSeparateStableIdsFromDisplayLabelsAndTargetNestedPlans() {
		assertEquals(WorkspaceId.HUMAN_MEDICAL_PLAN, WorkspaceDestination.humanMedicalPlan().workspaceId());
		assertEquals(WorkspaceId.CANINE_MEDICAL_PLAN, WorkspaceDestination.canineMedicalPlan().workspaceId());
		assertEquals(WorkspaceId.SAFETY_OVERVIEW,
				WorkspaceDestination.of(WorkspaceId.SAFETY_OVERVIEW).workspaceId());
		assertEquals("Safety Overview", WorkspaceId.SAFETY_OVERVIEW.displayLabel());
		assertThrows(IllegalArgumentException.class, () -> WorkspaceDestination.of(WorkspaceId.ACTIVITY_LOG));
		assertThrows(IllegalArgumentException.class, () -> WorkspaceDestination.activityLog(""));
	}

	@Test
	void activityLogDestinationsRequireAnInstanceIdentifier() {
		WorkspaceDestination first = WorkspaceDestination.activityLog("log-instance-one");
		WorkspaceDestination second = WorkspaceDestination.activityLog("log-instance-two");

		assertEquals(WorkspaceId.ACTIVITY_LOG, first.workspaceId());
		assertTrue(!first.equals(second));
		assertEquals("log-instance-one", first.instanceId());
	}

}
