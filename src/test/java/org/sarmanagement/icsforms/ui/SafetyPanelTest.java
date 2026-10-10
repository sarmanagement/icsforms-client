/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.Ics208Form;
import org.sarmanagement.icsforms.model.IncidentMode;
import org.sarmanagement.icsforms.model.SafetyAnalysisEntry;
import org.sarmanagement.icsforms.model.SafetyHazardReport;
import org.sarmanagement.icsforms.model.SarTaskAssignment;
import org.sarmanagement.icsforms.pdf.PdfExportService;
import org.sarmanagement.icsforms.persistence.LocalRepository;
import org.sarmanagement.icsforms.validation.IncidentValidator;

import javax.swing.SwingUtilities;
import javax.swing.JTabbedPane;
import java.time.LocalDateTime;
import java.util.Map;
import javax.swing.JTable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests basic safety tab persistence and optional site-plan sections.
 */
class SafetyPanelTest {
	@Test
	void refreshAndPushPreserveAnalysisAndSitePlanFields() throws Exception {
		AppData data = new AppData();
		SafetyAnalysisEntry entry = new SafetyAnalysisEntry();
		entry.setHazardsRisks("Flooding");
		entry.setMitigations("Maintain an escape route.");
		data.getForm215a().getEntries().add(entry);
		data.getForm215a().setPreparedDateTime(LocalDateTime.of(2026, 10, 10, 7, 30));
		data.getForm208().setSiteSafetyPlanRequired(Boolean.FALSE);
		data.getForm208().getSiteSafetyPlan().setTextSections(Map.of("Red zone controls", "Access monitored."));
		AppController controller = new AppController(data, (LocalRepository) null, new PdfExportService(),
				new IncidentValidator());
		SafetyPanel[] panel = new SafetyPanel[1];

		SwingUtilities.invokeAndWait(() -> {
			panel[0] = new SafetyPanel(controller);
			panel[0].refreshFromModel();
			panel[0].pushToModel();
		});

		assertEquals("Flooding", data.getForm215a().getEntries().get(0).getHazardsRisks());
		assertEquals(LocalDateTime.of(2026, 10, 10, 7, 30), data.getForm215a().getPreparedDateTime());
		assertEquals(Boolean.FALSE, data.getForm208().getSiteSafetyPlanRequired());
		assertEquals("Access monitored.", data.getForm208().getSiteSafetyPlan().getTextSections().get("Red zone controls"));
		assertNull(data.getForm208().getPreparedDateTime());
	}

	@Test
	void sarHazardReportsRemainReadOnlySourceDataWhenPanelRefreshes() throws Exception {
		AppData data = new AppData();
		data.setIncidentMode(IncidentMode.SAR);
		org.sarmanagement.icsforms.model.SarTaskAssignment task = new org.sarmanagement.icsforms.model.SarTaskAssignment();
		task.setAssignmentTeamNumber("K9-1");
		task.setHazardsObserved("Loose rocks near ridge.");
		task.setDebriefingCompleted(false);
		data.getSarTaskAssignments().add(task);
		AppController controller = new AppController(data, (LocalRepository) null, new PdfExportService(),
				new IncidentValidator());

		SwingUtilities.invokeAndWait(() -> {
			SafetyPanel panel = new SafetyPanel(controller);
			panel.refreshFromModel();
		});

		assertEquals("Loose rocks near ridge.", task.getHazardsObserved());
		assertEquals(Ics208Form.MessageMode.HAZARD_PRECAUTIONS, data.getForm208().getMessageMode());
	}

	@Test
	void safetyTabsFollowAnalysisReviewMessageOrderAndHideReportsOutsideSar() throws Exception {
		AppData data = new AppData();
		data.setIncidentMode(IncidentMode.GENERIC);
		AppController controller = new AppController(data, (LocalRepository) null, new PdfExportService(),
				new IncidentValidator());
		SafetyPanel[] panel = new SafetyPanel[1];
		SwingUtilities.invokeAndWait(() -> {
			panel[0] = new SafetyPanel(controller);
			panel[0].refreshFromModel();
		});
		JTabbedPane tabs = (JTabbedPane) field(panel[0], "sections");
		assertEquals(2, tabs.getTabCount());
		assertEquals("ICS 215A Analysis", tabs.getTitleAt(0));
		assertEquals("ICS 208 Message / Plan", tabs.getTitleAt(1));

		data.setIncidentMode(IncidentMode.SAR);
		SarTaskAssignment task = new SarTaskAssignment();
		task.setHazardsObserved("Flooded trail");
		data.getSarTaskAssignments().add(task);
		SwingUtilities.invokeAndWait(() -> panel[0].refreshFromModel());
		assertEquals(3, tabs.getTabCount());
		assertEquals("ICS 215A Analysis", tabs.getTitleAt(0));
		assertEquals("Reported SAR Hazards", tabs.getTitleAt(1));
		assertEquals("ICS 208 Message / Plan", tabs.getTitleAt(2));

		SafetyAnalysisEntry entry = new SafetyAnalysisEntry();
		entry.setHazardsRisks("Flooded trail");
		data.getForm215a().getEntries().add(entry);
		task.setSafetyHazardAnalysisEntryId(entry.getEntryId());
		SwingUtilities.invokeAndWait(() -> panel[0].refreshFromModel());
		assertTrue(((javax.swing.JLabel) field(panel[0], "hazardReportCoverage")).getText()
				.startsWith("All SAR-reported hazards accounted for"));
	}

	@Test
	void safetyPanelShowsArchivedAndCurrentReportsWithCoverageStatus() throws Exception {
		AppData data = new AppData();
		data.setIncidentMode(IncidentMode.SAR);
		data.getForm215a().getEntries().add(new SafetyAnalysisEntry());
		SafetyHazardReport archived = new SafetyHazardReport();
		archived.setTaskLabel("K9-old");
		archived.setIncidentArea("Division A");
		archived.setHazards("Loose rocks");
		archived.setPeriodStart(LocalDateTime.of(2026, 10, 1, 8, 0));
		archived.setPeriodEnd(LocalDateTime.of(2026, 10, 1, 20, 0));
		archived.setNotedBySafetyOfficer("A. Safety");
		data.getArchivedSafetyHazardReports().add(archived);
		SarTaskAssignment current = new SarTaskAssignment();
		current.setAssignmentTeamNumber("K9-current");
		current.setHazardsObserved("Falling branches");
		AppController controller = new AppController(data, (LocalRepository) null, new PdfExportService(),
				new IncidentValidator());
		data.getSarTaskAssignments().add(current);
		SafetyPanel[] panel = new SafetyPanel[1];

		SwingUtilities.invokeAndWait(() -> {
			panel[0] = new SafetyPanel(controller);
			panel[0].refreshFromModel();
		});

		var reportField = SafetyPanel.class.getDeclaredField("reportTable");
		reportField.setAccessible(true);
		JTable table = (JTable) reportField.get(panel[0]);
		assertEquals(2, table.getRowCount());
		assertEquals("2026-10-01T08:00 – 2026-10-01T20:00", table.getValueAt(0, 0));
		assertEquals("Noted by A. Safety", table.getValueAt(0, 4));
		assertEquals("Not yet reviewed", table.getValueAt(1, 4));

		SwingUtilities.invokeAndWait(() -> {
			table.setRowSelectionInterval(0, 0);
			try {
				var promote = SafetyPanel.class.getDeclaredMethod("promoteHazardReport");
				promote.setAccessible(true);
				promote.invoke(panel[0]);
			} catch (ReflectiveOperationException exception) {
				throw new AssertionError(exception);
			}
		});

		assertEquals("Loose rocks", data.getForm215a().getEntries().get(1).getHazardsRisks());
		assertEquals(data.getForm215a().getEntries().get(1).getEntryId(), archived.getAnalysisEntryId());
		SwingUtilities.invokeAndWait(() -> {
			try {
				var timerField = AppController.class.getDeclaredField("autosaveTimer");
				timerField.setAccessible(true);
				((javax.swing.Timer) timerField.get(controller)).stop();
			} catch (ReflectiveOperationException exception) {
				throw new AssertionError(exception);
			}
		});
	}

	@Test
	void operationalPeriodAdvanceArchivesNonblankHazardsAndReviewState() {
		AppData data = new AppData();
		SarTaskAssignment task = new SarTaskAssignment();
		task.setAssignmentId("task-1");
		task.setAssignmentTeamNumber("K9-1");
		task.setHazardsObserved("Steep terrain");
		task.setDebriefingCompleted(true);
		task.setSafetyHazardAnalysisEntryId("analysis-1");
		task.setSafetyHazardNotedBy("A. Safety");
		task.setSafetyHazardNotedDateTime(LocalDateTime.of(2026, 10, 1, 19, 0));
		data.getSarTaskAssignments().add(task);
		data.getIncidentContext().setOperationalPeriodStart(LocalDateTime.of(2026, 10, 1, 8, 0));
		data.getIncidentContext().setOperationalPeriodEnd(LocalDateTime.of(2026, 10, 1, 20, 0));

		data.advanceToNewOperationalPeriod();

		assertEquals(1, data.getArchivedSafetyHazardReports().size());
		SafetyHazardReport report = data.getArchivedSafetyHazardReports().get(0);
		assertEquals("Steep terrain", report.getHazards());
		assertEquals("task-1", report.getTaskId());
		assertEquals(LocalDateTime.of(2026, 10, 1, 8, 0), report.getPeriodStart());
		assertEquals("analysis-1", report.getAnalysisEntryId());
		assertEquals("A. Safety", report.getNotedBySafetyOfficer());
		assertTrue(report.isDebriefingCompleted());
		assertEquals(0, data.getSarTaskAssignments().size());
	}

	private static Object field(Object target, String name) throws ReflectiveOperationException {
		var field = target.getClass().getDeclaredField(name);
		field.setAccessible(true);
		return field.get(target);
	}
}
