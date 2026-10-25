/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.persistence.LocalRepository;
import org.sarmanagement.icsforms.pdf.PdfExportService;
import org.sarmanagement.icsforms.validation.IncidentValidator;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.GraphicsEnvironment;
import java.nio.file.Files;
import java.util.List;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

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
		assertEquals("IC/ICP (default)", dashboardMenu.getItem(0).getText());
		assertEquals("IC/ICP", MainFrame.Dashboard.IC_ICP.label());
		assertTrue(((JRadioButtonMenuItem) dashboardMenu.getItem(0)).isSelected());
		dashboardMenu.getItem(2).doClick();
		assertEquals(List.of(MainFrame.Dashboard.OPERATIONS), selected);
	}

	@Test
	void dashboardMenuImmediatelyFollowsCanineReferenceWithoutChangingSavedDefault() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		var directory = Files.createTempDirectory("icsforms-dashboard-menu");
		SwingUtilities.invokeAndWait(() -> {
			MainFrame frame = new MainFrame(new AppData(), new LocalRepository(directory.resolve("incident.json")),
					new PdfExportService(), new IncidentValidator(), directory);
			try {
				JMenu view = frame.getJMenuBar().getMenu(1);
				List<String> labels = new ArrayList<>();
				for (int index = 0; index < view.getItemCount(); index++) {
					JMenuItem item = view.getItem(index);
					labels.add(item == null ? "" : item.getText());
				}
				int reference = labels.indexOf("Canine Medical Reference — adapted ICS 206CANINE");
				assertTrue(reference >= 0);
				assertEquals("Dashboard", labels.get(reference + 1));
			} finally {
				AppController controller = (AppController) Ics205PanelTest.field(frame, "controller");
				((Timer) Ics205PanelTest.field(controller, "autosaveTimer")).stop();
				frame.dispose();
			}
		});
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
			assertTrue(workspaces.stream().noneMatch(id -> "ICS 205A".equals(id.displayLabel())));
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
