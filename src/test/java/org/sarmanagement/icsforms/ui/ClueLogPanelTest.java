/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.ClueLogEntry;
import org.sarmanagement.icsforms.model.Ics202Form;
import org.sarmanagement.icsforms.model.Ics204Form;
import org.sarmanagement.icsforms.model.IncidentContext;
import org.sarmanagement.icsforms.model.OperatorContext;
import org.sarmanagement.icsforms.model.Provenance;
import org.sarmanagement.icsforms.model.RecordDiff;
import org.sarmanagement.icsforms.model.RecordIndex;
import org.sarmanagement.icsforms.model.SarTaskAssignment;
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
import java.time.LocalDateTime;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
		org.sarmanagement.icsforms.TestIdentitySupport.assign(debrief, old.getEntryId());
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
		org.sarmanagement.icsforms.TestIdentitySupport.assign(debrief, old.getEntryId());
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

	@Test
	void duplicateTaskLabelsRemainBoundToDistinctAssignmentIds() {
		SarTaskAssignment first = task("assignment-1");
		SarTaskAssignment second = task("assignment-2");

		List<ClueLogPanel.TaskChoice> choices = ClueLogPanel.taskChoices(List.of(first, second));

		assertEquals("assignment-1", choices.get(0).assignmentId());
		assertEquals("assignment-2", choices.get(1).assignmentId());
		assertNotEquals(choices.get(0).label(), choices.get(1).label());
	}

	@Test
	void duplicateCandidatesRequireMatchingNonblankLocationAndDescriptionAcrossSources() {
		ClueLogEntry first = clue(" Ridge  North ", " Blue   pack ");
		first.setDetectingTask("Team A");
		ClueLogEntry second = clue("ridge north", "blue pack");
		second.setDetectingTask("Team B");
		second.setDateTimeCollected(LocalDateTime.parse("2026-01-02T03:00:00"));
		ClueLogEntry different = clue("ridge north", "red pack");
		ClueLogEntry blank = clue("", "");
		List<ClueLogEntry> entries = List.of(first, second, different, blank, clue("", ""));

		assertEquals(List.of(new ClueDuplicateReview.Candidate(first, second)), ClueDuplicateReview.candidates(entries));
		assertFalse(first.isStruckOut());
		assertFalse(second.isStruckOut());
		assertNull(second.getDuplicateConfirmation());
		second.setStruckOut(true);
		assertTrue(ClueDuplicateReview.candidates(entries).isEmpty());
	}

	@Test
	void confirmationRetainsEvidenceProvenanceAndProducesStrikeOutDiff() throws Exception {
		AppController controller = sampleController();
		controller.setOperatorContext(new OperatorContext("Reviewer", "station-3"));
		ClueLogEntry first = clue("Ridge", "Pack");
		ClueLogEntry second = clue("ridge", "pack");
		Provenance original = Provenance.local("Reporter", "station-1", Instant.parse("2026-01-01T00:00:00Z"));
		second.setProvenance(original);
		second.setFollowUp("Check with team");
		controller.getData().setClueLogEntries(new java.util.ArrayList<>(List.of(first, second)));
		ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
		AppData before = mapper.readValue(mapper.writeValueAsBytes(controller.getData()), AppData.class);
		ClueLogPanel panel = new ClueLogPanel(controller);
		panel.refreshFromModel();

		assertTrue(panel.confirmDuplicate(second.getEntryId(), first.getEntryId()));
		assertEquals(2, controller.getData().getClueLogEntries().size());
		assertFalse(first.isStruckOut());
		assertTrue(second.isStruckOut());
		assertEquals(original, second.getProvenance());
		assertEquals("Check with team", second.getFollowUp());
		assertEquals(first.getEntryId(), second.getDuplicateConfirmation().duplicateOfId());
		assertEquals("Reviewer", second.getDuplicateConfirmation().provenance().operatorName());
		assertEquals("station-3", second.getDuplicateConfirmation().provenance().stationId());
		assertTrue(second.getDuplicateConfirmation().confirmedAt() != null);
		assertFalse(clueTable(panel).isCellEditable(1, 4));
		assertEquals("Struck out: duplicate", clueTable(panel).getValueAt(1, 8));
		RecordDiff diff = RecordDiff.between(before, controller.getData());
		assertTrue(diff.struckOut().contains(new RecordIndex.RecordKey(ClueLogEntry.class.getName(), second.getEntryId())));
		assertTrue(diff.removed().isEmpty());
		ClueLogEntry restored = mapper.readValue(mapper.writeValueAsBytes(second), ClueLogEntry.class);
		assertEquals(second.getDuplicateConfirmation(), restored.getDuplicateConfirmation());
		assertEquals(original, restored.getProvenance());
		assertTrue(restored.isStruckOut());
		assertFalse(panel.confirmDuplicate(second.getEntryId(), first.getEntryId()));
	}

	@Test
	void staleOrCancelledReviewDoesNotStrikeOutClues() throws Exception {
		AppController controller = sampleController();
		ClueLogEntry first = clue("Ridge", "Pack");
		ClueLogEntry second = clue("Ridge", "Pack");
		controller.getData().setClueLogEntries(new java.util.ArrayList<>(List.of(first, second)));
		ClueLogPanel panel = new ClueLogPanel(controller);
		panel.refreshFromModel();
		ClueDuplicateReview.candidates(controller.getData().getClueLogEntries());
		assertFalse(first.isStruckOut());
		assertFalse(second.isStruckOut());
		second.setDescription("Different clue");
		assertFalse(panel.confirmDuplicate(second.getEntryId(), first.getEntryId()));
		assertNull(second.getDuplicateConfirmation());
		controller.getData().setClueLogEntries(List.of(first));
		assertFalse(panel.confirmDuplicate(second.getEntryId(), first.getEntryId()));
		assertFalse(first.isStruckOut());
	}

	@Test
	void pendingEditCannotChangeAnExternallyStruckOutClue() throws Exception {
		AppController controller = sampleController();
		ClueLogEntry clue = clue("Ridge", "Pack");
		controller.getData().setClueLogEntries(new java.util.ArrayList<>(List.of(clue)));
		ClueLogPanel panel = new ClueLogPanel(controller);
		panel.refreshFromModel();
		JTable table = clueTable(panel);
		assertTrue(table.editCellAt(0, 4));
		((JTextField) table.getEditorComponent()).setText("Stale change");
		clue.confirmDuplicate("another-clue", Instant.parse("2026-01-02T03:04:05Z"), null);
		panel.pushToModel();
		assertEquals("Pack", clue.getDescription());
		assertNull(clue.getDuplicateConfirmation().provenance());
	}

	/**
	 * Creates a clue with evidence suitable for candidate matching.
	 * @param location reported location
	 * @param description reported description
	 * @return configured clue
	 */
	private static ClueLogEntry clue(String location, String description) {
		ClueLogEntry clue = new ClueLogEntry();
		clue.setLocation(location);
		clue.setDescription(description);
		return clue;
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
		return IncidentStoreTestFixtures.controller(data,
				new PdfExportService(new Ics202PdfRenderer(), new Ics204PdfRenderer(),
						new SarTaskAssignmentPdfRenderer()),
				new IncidentValidator());
	}

	/**
	 * Creates a task with a duplicate operator-facing label and a distinct link identity.
	 *
	 * @param assignmentId task assignment UUID
	 * @return configured SAR task
	 */
	private static SarTaskAssignment task(String assignmentId) {
		SarTaskAssignment task = new SarTaskAssignment();
		task.setAssignmentId(assignmentId);
		task.setAssignmentTeamNumber("Team 1");
		task.setResourceIdentifier("Search dog");
		return task;
	}
}
