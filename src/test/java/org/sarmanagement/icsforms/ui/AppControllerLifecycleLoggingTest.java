package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.model.ActivityLogScope;
import org.sarmanagement.icsforms.model.ActivityEventType;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.ClueLogEntry;
import org.sarmanagement.icsforms.model.ActivityLogEntry;
import org.sarmanagement.icsforms.model.Ics214Form;
import org.sarmanagement.icsforms.model.SarTaskAssignment;
import org.sarmanagement.icsforms.model.TCard;
import org.sarmanagement.icsforms.model.TCardType;
import org.sarmanagement.icsforms.persistence.LocalRepository;
import org.sarmanagement.icsforms.pdf.PdfExportService;
import org.sarmanagement.icsforms.validation.IncidentValidator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppControllerLifecycleLoggingTest {

	@Test
	void taskStatusTransitionAddsIcpAndTaskEntriesAndCopiesOnTaskTime() throws Exception {
		AppController controller = sampleControllerWithTaskLog();
		SarTaskAssignment task = controller.getData().getSarTaskAssignments().get(0);
		LocalDateTime effectiveTime = LocalDateTime.parse("2026-01-01T16:15:00");

		AppController.TaskLifecycleChangeResult result = controller.recordTaskLifecycleTransition(task, "planned",
				"assigned - on task", effectiveTime);

		assertFalse(result.hasWarning());
		assertEquals("assigned - on task", task.getTaskLifecycleStatus());
		assertEquals(effectiveTime, task.getAssignmentStart());
		assertEquals(0, controller.getData().getForm204().getCommunications().size());
		Ics214Form icpLog = controller.getData().getActivityLogs().stream()
				.filter(log -> log.getLogScope() == ActivityLogScope.ICP).findFirst().orElse(null);
		assertNotNull(icpLog);
		assertEquals("Task status changed to assigned - on task", icpLog.getActivityLog().get(0).getNotableActivity());
		assertEquals(effectiveTime, icpLog.getActivityLog().get(0).getTimestamp());
		Ics214Form taskLog = controller.getData().getActivityLogs().stream()
				.filter(log -> "a-1".equals(log.getLinkedSarTaskAssignmentId())).findFirst().orElse(null);
		assertNotNull(taskLog);
		assertEquals(ActivityEventType.ID_RESOURCE_ON_TASK, taskLog.getActivityLog().get(0).getEventTypeId());
		assertEquals(effectiveTime, taskLog.getActivityLog().get(0).getTimestamp());
	}

	@Test
	void taskStatusLogEntryUpdatesTaskStateUsingLogTimestamp() throws Exception {
		AppController controller = sampleControllerWithTaskLog();
		SarTaskAssignment task = controller.getData().getSarTaskAssignments().get(0);
		task.setTaskLifecycleStatus("assigned - on task");
		Ics214Form taskLog = controller.getData().getActivityLogs().stream()
				.filter(log -> "a-1".equals(log.getLinkedSarTaskAssignmentId())).findFirst().orElseThrow();
		ActivityLogEntry entry = new ActivityLogEntry();
		LocalDateTime effectiveTime = LocalDateTime.parse("2026-01-01T18:45:00");
		entry.setTimestamp(effectiveTime);
		entry.setEventTypeId(ActivityEventType.ID_TASK_COMPLETED);
		entry.setResourceIdentifier("T-1");
		entry.setNotableActivity("Task completed");

		AppController.TaskLifecycleChangeResult result = controller.applyTaskLifecycleFromLogEntry(taskLog, entry);

		assertFalse(result.hasWarning());
		assertEquals("assigned - returning from assignment", task.getTaskLifecycleStatus());
		assertEquals(effectiveTime, task.getAssignmentEnd());
		assertEquals(0, taskLog.getActivityLog().size(), "controller should not duplicate the operator-entered log row");
	}

	@Test
	void outOfSequenceTaskStatusLogEntryWarnsInsteadOfSilentlyFailing() throws Exception {
		AppController controller = sampleControllerWithTaskLog();
		SarTaskAssignment task = controller.getData().getSarTaskAssignments().get(0);
		task.setTaskLifecycleStatus("returned");
		Ics214Form taskLog = controller.getData().getActivityLogs().stream()
				.filter(log -> "a-1".equals(log.getLinkedSarTaskAssignmentId())).findFirst().orElseThrow();
		ActivityLogEntry entry = new ActivityLogEntry();
		entry.setTimestamp(LocalDateTime.parse("2026-01-01T19:15:00"));
		entry.setEventTypeId(ActivityEventType.ID_RESOURCE_ON_TASK);
		entry.setResourceIdentifier("T-1");

		AppController.TaskLifecycleChangeResult result = controller.applyTaskLifecycleFromLogEntry(taskLog, entry);

		assertTrue(result.hasWarning());
		assertEquals("returned", task.getTaskLifecycleStatus());
	}

	@Test
	void outOfSequenceTaskStatusChangeWarnsWithoutAddingMirroredLifecycleRows() throws Exception {
		AppController controller = sampleControllerWithTaskLog();
		SarTaskAssignment task = controller.getData().getSarTaskAssignments().get(0);
		task.setTaskLifecycleStatus("returned");

		AppController.TaskLifecycleChangeResult result = controller.recordTaskLifecycleTransition(task, "returned",
				"assigned - on task", LocalDateTime.parse("2026-01-01T20:00:00"));

		assertTrue(result.hasWarning());
		assertEquals("returned", task.getTaskLifecycleStatus());
		assertEquals(0, controller.getData().getActivityLogs().stream()
				.filter(log -> log.getLogScope() == ActivityLogScope.ICP)
				.mapToInt(log -> log.getActivityLog() == null ? 0 : log.getActivityLog().size()).sum());
		Ics214Form taskLog = controller.getData().getActivityLogs().stream()
				.filter(log -> "a-1".equals(log.getLinkedSarTaskAssignmentId())).findFirst().orElseThrow();
		assertEquals(0, taskLog.getActivityLog().size());
	}

	@Test
	void plannedToReturnedStatusChangeWarnsAndLeavesTaskUnchanged() throws Exception {
		AppController controller = sampleControllerWithTaskLog();
		SarTaskAssignment task = controller.getData().getSarTaskAssignments().get(0);
		task.setTaskLifecycleStatus("planned");

		AppController.TaskLifecycleChangeResult result = controller.recordTaskLifecycleTransition(task, "planned",
				"returned", LocalDateTime.parse("2026-01-01T20:05:00"));

		assertTrue(result.hasWarning());
		assertEquals("planned", task.getTaskLifecycleStatus());
	}

	@Test
	void clueLoggingAddsIcpActivityEntry() throws Exception {
		Path tempFile = Files.createTempDirectory("icsforms-clue").resolve("incident.json");
		AppController controller = new AppController(new AppData(), new LocalRepository(tempFile),
				new PdfExportService(), new IncidentValidator());

		ClueLogEntry clue = new ClueLogEntry();
		clue.setDetectingTask("T-1");
		clue.setLocation("Ridge spur");
		clue.setDescription("Footprint");
		clue.setAssignmentId("");

		controller.recordClueInIcpActivityLog(clue);

		Ics214Form icpLog = controller.getData().getActivityLogs().stream()
				.filter(log -> log.getLogScope() == ActivityLogScope.ICP).findFirst().orElse(null);
		assertTrue(icpLog != null);
		assertFalse(icpLog.getActivityLog().isEmpty());
		assertEquals("T-1", icpLog.getActivityLog().get(0).getResourceIdentifier());
		assertTrue(icpLog.getActivityLog().get(0).getNotableActivity().contains("Clue logged"));
	}

	@Test
	void ensurePersonnelCardCreatesPersonnelCard() throws Exception {
		Path tempFile = Files.createTempDirectory("icsforms-personnel").resolve("incident.json");
		AppController controller = new AppController(new AppData(), new LocalRepository(tempFile),
				new PdfExportService(), new IncidentValidator());

		TCard created = controller.ensurePersonnelCard("Alex Searcher", "SAR-3", "555-1000");

		assertTrue(created != null);
		assertEquals(TCardType.PERSONNEL, created.getCardType());
		assertEquals("Alex Searcher", created.getPersonName());
		assertEquals("SAR-3", created.getRadioChannel());
		assertEquals("555-1000", created.getPhoneNumber());
		assertTrue(controller.getData().getTCards().stream().anyMatch(
				card -> "Alex Searcher".equals(card.getPersonName()) && card.getCardType() == TCardType.PERSONNEL));
	}

	private AppController sampleControllerWithTaskLog() throws Exception {
		Path tempFile = Files.createTempDirectory("icsforms-lifecycle").resolve("incident.json");
		AppController controller = new AppController(new AppData(), new LocalRepository(tempFile), new PdfExportService(),
				new IncidentValidator());
		SarTaskAssignment task = new SarTaskAssignment();
		task.setAssignmentId("a-1");
		task.setAssignmentTeamNumber("T-1");
		task.setResourceIdentifier("RES-1");
		controller.getData().setSarTaskAssignments(List.of(task));
		Ics214Form taskLog = new Ics214Form();
		taskLog.setLinkedSarTaskAssignmentId("a-1");
		taskLog.setName("Task Log");
		controller.getData().setActivityLogs(new java.util.ArrayList<>(List.of(taskLog)));
		return controller;
	}
}
