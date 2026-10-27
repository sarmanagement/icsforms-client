/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.model.ActivityEventType;
import org.sarmanagement.icsforms.model.ActivityLogEntry;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.Ics214Form;
import org.sarmanagement.icsforms.model.SarTaskAssignment;
import org.sarmanagement.icsforms.pdf.PdfExportService;
import org.sarmanagement.icsforms.persistence.FileIncidentStore;
import org.sarmanagement.icsforms.validation.IncidentValidator;

import javax.swing.JButton;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.event.MouseEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** Covers assignment grouping and visual event categories for the ICP timeline. */
class IcpActivityTimelineDialogTest {
	@TempDir
	Path directory;

	@Test
	void groupsIcpEventsByAssignmentAndKeepsUnassignedEventsInTheirOwnLane() {
		AppData data = new AppData();
		SarTaskAssignment task = new SarTaskAssignment();
		task.setAssignmentId("assignment-1");
		task.setTaskId("task-1");
		task.setAssignmentTeamNumber("T1");
		task.setResourceIdentifier("Trackers");
		data.getSarTaskAssignments().add(task);

		Ics214Form icpLog = new Ics214Form();
		icpLog.getActivityLog().add(entry("assignment-1", ActivityEventType.ID_RESOURCE_DEPARTED_STAGING));
		icpLog.getActivityLog().add(entry("assignment-1", ActivityEventType.ID_CLUE_DETECTED));
		icpLog.getActivityLog().add(entry("", ActivityEventType.ID_SUBJECT_FOUND));
		data.getActivityLogs().add(icpLog);
		Ics214Form taskLog = new Ics214Form();
		taskLog.setLinkedSarTaskAssignmentId("assignment-1");
		taskLog.getActivityLog().add(entry("assignment-1", ActivityEventType.ID_RESOURCE_ON_TASK));
		data.getActivityLogs().add(taskLog);

		List<IcpActivityTimelineDialog.TimelineLane> lanes = IcpActivityTimelineDialog.buildLanes(data);

		var assignmentLane = lanes.stream().filter(lane -> lane.assignmentId().equals("assignment-1"))
				.findFirst().orElseThrow();
		assertEquals("task-1", assignmentLane.taskId());
		assertEquals(List.of(ActivityEventType.ID_RESOURCE_DEPARTED_STAGING, ActivityEventType.ID_CLUE_DETECTED),
				assignmentLane.markers().stream().map(IcpActivityTimelineDialog.TimelineMarker::eventTypeId).toList());
		assertEquals(List.of(ActivityEventType.ID_SUBJECT_FOUND), lanes.stream()
				.filter(lane -> lane.assignmentId().isBlank()).flatMap(lane -> lane.markers().stream())
				.map(IcpActivityTimelineDialog.TimelineMarker::eventTypeId).toList());
	}

	@Test
	void lifecycleAndClueEventsHaveDistinctReadableMarkers() {
		assertEquals("D", IcpActivityTimelineDialog.markerFor(ActivityEventType.ID_RESOURCE_DEPARTED_STAGING));
		assertEquals("T", IcpActivityTimelineDialog.markerFor(ActivityEventType.ID_RESOURCE_ON_TASK));
		assertEquals("C", IcpActivityTimelineDialog.markerFor(ActivityEventType.ID_CLUE_REPORTED));
		assertEquals("S", IcpActivityTimelineDialog.markerFor(ActivityEventType.ID_SUBJECT_FOUND));
		assertEquals("✓", IcpActivityTimelineDialog.markerFor(ActivityEventType.ID_TASK_COMPLETED));
		assertEquals("R", IcpActivityTimelineDialog.markerFor(ActivityEventType.ID_RESOURCE_RETURNED_STAGING));
		assertTrue(IcpActivityTimelineDialog.markerFor(ActivityEventType.ID_FREE_TEXT).length() > 0);
	}

	@Test
	void addActivityActionUsesSelectedAssignmentLane() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		try (FileIncidentStore store = new FileIncidentStore(directory.resolve("store"))) {
			AppData data = new AppData();
			SarTaskAssignment task = new SarTaskAssignment();
			task.setAssignmentId("assignment-1");
			task.setTaskId("task-1");
			task.setAssignmentTeamNumber("T1");
			task.setResourceIdentifier("Trackers");
			data.getSarTaskAssignments().add(task);
			var incidentId = store.create(data);
			AppController controller = new AppController(data, store, incidentId,
					new PdfExportService(), new IncidentValidator());
			controller.getData().getSarTaskAssignments().add(task);
			AtomicReference<LogEntryContext> context = new AtomicReference<>();
			SwingUtilities.invokeAndWait(() -> {
				IcpActivityTimelineDialog dialog = new IcpActivityTimelineDialog(null, controller, context::set);
				try {
					JScrollPane scroll = descendants(dialog).stream().filter(JScrollPane.class::isInstance)
							.map(JScrollPane.class::cast).findFirst().orElseThrow();
					Component canvas = scroll.getViewport().getView();
					MouseEvent click = new MouseEvent(canvas, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(),
							0, 20, 68, 1, false);
					for (var listener : canvas.getMouseListeners()) {
						listener.mouseClicked(click);
					}
					button(dialog, "Add ICP Activity…").doClick();
					assertEquals("assignment-1", context.get().assignmentId());
					assertEquals("task-1", context.get().taskId());
					assertEquals("Trackers", context.get().resourceId());
				} finally {
					dialog.dispose();
				}
			});
		}
	}

	/**
	 * Creates a timestamped marker entry for one test.
	 *
	 * @param assignmentId assignment identity.
	 * @param eventTypeId event category.
	 * @return configured activity row.
	 */
	private static ActivityLogEntry entry(String assignmentId, String eventTypeId) {
		ActivityLogEntry entry = new ActivityLogEntry();
		entry.setAssignmentId(assignmentId);
		entry.setEventTypeId(eventTypeId);
		entry.setTimestamp(LocalDateTime.of(2026, 10, 27, 8, 0));
		entry.setNotableActivity("Test event");
		return entry;
	}

	/**
	 * Finds a labeled button in a nested window.
	 *
	 * @param parent parent container.
	 * @param title button title.
	 * @return matching button.
	 */
	private static JButton button(Container parent, String title) {
		return descendants(parent).stream().filter(JButton.class::isInstance).map(JButton.class::cast)
				.filter(candidate -> title.equals(candidate.getText())).findFirst().orElseThrow();
	}

	/**
	 * Traverses nested Swing components.
	 *
	 * @param parent component container.
	 * @return all descendants.
	 */
	private static List<Component> descendants(Container parent) {
		List<Component> result = new java.util.ArrayList<>();
		for (Component child : parent.getComponents()) {
			result.add(child);
			if (child instanceof Container container) {
				result.addAll(descendants(container));
			}
		}
		return result;
	}
}
