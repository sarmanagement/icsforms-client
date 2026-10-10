/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.Ics208Form;
import org.sarmanagement.icsforms.model.IncidentMode;
import org.sarmanagement.icsforms.model.SafetyAnalysisEntry;
import org.sarmanagement.icsforms.pdf.PdfExportService;
import org.sarmanagement.icsforms.persistence.LocalRepository;
import org.sarmanagement.icsforms.validation.IncidentValidator;

import javax.swing.SwingUtilities;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
}
