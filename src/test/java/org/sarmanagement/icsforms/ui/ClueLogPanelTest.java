/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.ClueLogEntry;
import org.sarmanagement.icsforms.model.Ics202Form;
import org.sarmanagement.icsforms.model.Ics204Form;
import org.sarmanagement.icsforms.model.IncidentContext;
import org.sarmanagement.icsforms.persistence.LocalRepository;
import org.sarmanagement.icsforms.pdf.Ics202PdfRenderer;
import org.sarmanagement.icsforms.pdf.Ics204PdfRenderer;
import org.sarmanagement.icsforms.pdf.PdfExportService;
import org.sarmanagement.icsforms.pdf.SarTaskAssignmentPdfRenderer;
import org.sarmanagement.icsforms.validation.IncidentValidator;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClueLogPanelTest {

	@Test
	void stalePushRetainsCanonicalDebriefAndExternalClues() throws Exception {
		AppController controller = sampleController();
		ClueLogEntry old = new ClueLogEntry();
		old.setDescription("Before debrief");
		controller.getData().setClueLogEntries(new java.util.ArrayList<>(List.of(old)));
		ClueLogPanel panel = new ClueLogPanel(controller);
		panel.refreshFromModel();
		ClueLogEntry debrief = new ClueLogEntry();
		debrief.setEntryId(old.getEntryId());
		debrief.setDescription("Updated in debrief");
		debrief.setFollowUp("Retain follow-up history");
		debrief.setFollowUpCompletedDate(LocalDateTime.parse("2026-01-01T03:00:00"));
		ClueLogEntry external = new ClueLogEntry();
		external.setDescription("Logged from detached window");
		List<ClueLogEntry> canonical = List.of(debrief, external);
		controller.getData().setClueLogEntries(canonical);
		panel.pushToModel();
		assertSame(canonical, controller.getData().getClueLogEntries());
		assertEquals("Updated in debrief", clueTable(panel).getValueAt(0, 4));
		assertEquals("Retain follow-up history", debrief.getFollowUp());
		assertEquals(LocalDateTime.parse("2026-01-01T03:00:00"), debrief.getFollowUpCompletedDate());
		assertSame(external, controller.getData().getClueLogEntries().get(1));
	}

	@Test
	void pendingInlineCellMergesOnlyThatFieldAndNeverResurrectsDeletedClues() throws Exception {
		AppController controller = sampleController();
		ClueLogEntry old = new ClueLogEntry();
		old.setDescription("Before debrief");
		controller.getData().setClueLogEntries(new java.util.ArrayList<>(List.of(old)));
		ClueLogPanel panel = new ClueLogPanel(controller);
		panel.refreshFromModel();
		JTable table = clueTable(panel);
		assertTrue(table.editCellAt(0, 3));
		((JTextField) table.getEditorComponent()).setText("Pending location");
		ClueLogEntry debrief = new ClueLogEntry();
		debrief.setEntryId(old.getEntryId());
		debrief.setDescription("Updated in debrief");
		debrief.setFollowUp("New follow-up history");
		ClueLogEntry external = new ClueLogEntry();
		controller.getData().setClueLogEntries(new java.util.ArrayList<>(List.of(debrief, external)));
		panel.pushToModel();
		assertEquals("Pending location", debrief.getLocation());
		assertEquals("Updated in debrief", debrief.getDescription());
		assertEquals("New follow-up history", debrief.getFollowUp());
		assertSame(debrief, controller.getData().getClueLogEntries().get(0));
		assertTrue(table.editCellAt(0, 3));
		((JTextField) table.getEditorComponent()).setText("Deleted clue must stay deleted");
		controller.getData().setClueLogEntries(List.of(external));
		panel.pushToModel();
		assertEquals(1, controller.getData().getClueLogEntries().size());
		assertSame(external, controller.getData().getClueLogEntries().get(0));
	}

	@Test
	void sharedCanonicalListReceivesActualInlineEditBeforePush() throws Exception {
		AppController controller = sampleController();
		ClueLogEntry clue = new ClueLogEntry();
		List<ClueLogEntry> canonical = new java.util.ArrayList<>(List.of(clue));
		controller.getData().setClueLogEntries(canonical);
		ClueLogPanel panel = new ClueLogPanel(controller);
		panel.refreshFromModel();
		JTable table = clueTable(panel);
		assertTrue(table.editCellAt(0, 4));
		((JTextField) table.getEditorComponent()).setText("Entered inline");
		panel.pushToModel();
		assertSame(canonical, controller.getData().getClueLogEntries());
		assertEquals("Entered inline", clue.getDescription());
	}

	@Test
	void clueLogShowsAddAndFindDuplicatesButtons() throws Exception {
		ClueLogPanel panel = new ClueLogPanel(sampleController());
		JPanel buttons = (JPanel) panel.getComponent(1);

		assertEquals(3, buttons.getComponentCount());
		assertEquals("Add", ((JButton) buttons.getComponent(0)).getText());
		assertEquals("Find Duplicates\u2026", ((JButton) buttons.getComponent(1)).getText());
		assertEquals("Sync to Activity Logs", ((JButton) buttons.getComponent(2)).getText());
	}

	/**
	 * Returns the visible clue table used by inline-edit regressions.
	 *
	 * @param panel clue editor.
	 * @return clue table.
	 */
	private static JTable clueTable(ClueLogPanel panel) {
		return (JTable) ((JScrollPane) panel.getComponent(0)).getViewport().getView();
	}

	/**
	 * Creates a controller backed by a disposable incident repository.
	 *
	 * @return sample controller.
	 * @throws Exception if its repository directory cannot be created.
	 */
	private static AppController sampleController() throws Exception {
		IncidentContext context = new IncidentContext("Test Incident", LocalDateTime.parse("2026-01-01T00:00:00"),
				LocalDateTime.parse("2026-01-01T12:00:00"), "Planner", "Planning Section Chief");
		AppData data = new AppData(context, new Ics202Form(), new Ics204Form(), List.of());
		return new AppController(data,
				new LocalRepository(Files.createTempDirectory("icsforms-ui").resolve("incident.json")),
				new PdfExportService(new Ics202PdfRenderer(), new Ics204PdfRenderer(),
						new SarTaskAssignmentPdfRenderer()),
				new IncidentValidator());
	}
}
