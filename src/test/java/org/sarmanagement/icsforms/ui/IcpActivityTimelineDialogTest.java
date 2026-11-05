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
import org.sarmanagement.icsforms.model.RadioChannel;
import org.sarmanagement.icsforms.model.RadioCheckStatus;
import org.sarmanagement.icsforms.model.ResourceAssignment;
import org.sarmanagement.icsforms.model.SarTaskAssignment;
import org.sarmanagement.icsforms.model.SarTaskResource;
import org.sarmanagement.icsforms.pdf.PdfExportService;
import org.sarmanagement.icsforms.persistence.FileIncidentStore;
import org.sarmanagement.icsforms.validation.IncidentValidator;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.time.LocalDateTime;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
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
	void digitalClockUsesSelectedZoneAndTwentyFourHourFormat() {
		assertEquals("04:34:56",
				IcpActivityTimelineDialog.formatClockTime(Instant.parse("2026-11-02T12:34:56Z"),
						ZoneId.of("America/Los_Angeles")));
	}

	@Test
	void committedCommunicationsActivityRefreshesOpenTimelineAndPreservesAssignmentSelection() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		AppData data = new AppData();
		SarTaskAssignment selectedTask = new SarTaskAssignment();
		selectedTask.setAssignmentId("selected-assignment");
		selectedTask.setResourceIdentifier("Selected team");
		data.getSarTaskAssignments().add(selectedTask);
		ResourceAssignment selectedAssignment = new ResourceAssignment();
		selectedAssignment.setAssignmentId(selectedTask.getAssignmentId());
		selectedAssignment.setResourceIdentifier("Selected team");
		data.getForm204().getResourcesAssigned().add(selectedAssignment);
		RadioChannel channel = new RadioChannel();
		channel.setChannelName("Command");
		data.getForm205().getChannels().add(channel);
		AppController controller = IncidentStoreTestFixtures.controller(data);
		AtomicReference<IcpActivityTimelineDialog> reference = new AtomicReference<>();
		try {
			SwingUtilities.invokeAndWait(() -> {
				IcpActivityTimelineDialog timeline = new IcpActivityTimelineDialog(null, controller, ignored -> { });
				reference.set(timeline);
				timeline.setVisible(true);
				selectLane(timeline, 0);
				SarTaskAssignment inserted = new SarTaskAssignment();
				inserted.setAssignmentId("inserted-assignment");
				data.getSarTaskAssignments().add(0, inserted);
				ResourceAssignment insertedAssignment = new ResourceAssignment();
				insertedAssignment.setAssignmentId(inserted.getAssignmentId());
				data.getForm204().getResourcesAssigned().add(0, insertedAssignment);
				ActivityLogEntry committed = entry(selectedTask.getAssignmentId(),
						ActivityEventType.ID_COMMUNICATIONS_CHECK);
				controller.commitRadioCheckWithActivity(controller.captureIncidentBinding(), channel.getChannelId(),
						channel.deepCopy(), RadioCheckStatus.SUCCESSFUL, "COML", committed.getTimestamp(), "Clear",
						new Ics214Form(), committed);
			});
			SwingUtilities.invokeAndWait(() -> {
				IcpActivityTimelineDialog timeline = reference.get();
				List<?> lanes = field(timeline, "lanes", List.class);
				int selected = field(timeline, "selectedLane", Integer.class);
				assertEquals(1, selected);
				var selectedLane = (IcpActivityTimelineDialog.TimelineLane) lanes.get(selected);
				assertEquals(selectedTask.getAssignmentId(), selectedLane.assignmentId());
				assertEquals(List.of(ActivityEventType.ID_COMMUNICATIONS_CHECK),
						selectedLane.markers().stream().map(IcpActivityTimelineDialog.TimelineMarker::eventTypeId).toList());
				assertEquals(2, field(controller, "incidentChangeListeners", Collection.class).size());
				timeline.dispose();
				assertEquals(0, field(controller, "incidentChangeListeners", Collection.class).size());
				data.getSarTaskAssignments().clear();
				controller.markDirty();
			});
			SwingUtilities.invokeAndWait(() -> assertEquals(3,
					field(reference.get(), "lanes", List.class).size()));
		} finally {
			SwingUtilities.invokeAndWait(() -> {
				if (reference.get() != null) {
					reference.get().dispose();
				}
				controller.close();
			});
		}
	}

	@Test
	void replacedIncidentCannotRefreshOrApplyStaleTimelineActions() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		AppController controller = IncidentStoreTestFixtures.controller(new AppData());
		AtomicReference<LogEntryContext> added = new AtomicReference<>();
		AtomicReference<IcpActivityTimelineDialog> reference = new AtomicReference<>();
		AtomicReference<List<?>> originalLanes = new AtomicReference<>();
		try {
			SwingUtilities.invokeAndWait(() -> {
				IcpActivityTimelineDialog timeline = new IcpActivityTimelineDialog(null, controller, added::set);
				reference.set(timeline);
				timeline.setVisible(true);
				selectLane(timeline, 0);
				originalLanes.set(field(timeline, "lanes", List.class));
				controller.newDocument();
			});
			SwingUtilities.invokeAndWait(() -> {
				IcpActivityTimelineDialog timeline = reference.get();
				assertFalse(timeline.hasSelectedLane());
				button(timeline, "Refresh").doClick();
				button(timeline, "Add Log Entry…").doClick();
				assertNull(added.get());
				assertSame(originalLanes.get(), field(timeline, "lanes", List.class));
			});
		} finally {
			SwingUtilities.invokeAndWait(() -> {
				if (reference.get() != null) {
					reference.get().dispose();
				}
				controller.close();
			});
		}
	}

	@Test
	void onTaskSegmentsConnectOnTaskToCompletionAndIgnoreStruckOutEvents() {
		LocalDateTime start = LocalDateTime.of(2026, 10, 27, 8, 0);
		var onTask = marker(start, ActivityEventType.ID_RESOURCE_ON_TASK, false);
		var completion = marker(start.plusMinutes(40), ActivityEventType.ID_TASK_COMPLETED, false);
		var activeOnTask = marker(start.plusHours(2).plusMinutes(10), ActivityEventType.ID_RESOURCE_ON_TASK, false);
		List<IcpActivityTimelineDialog.TimelineMarker> markers = new ArrayList<>(List.of(
				onTask,
				marker(start.plusMinutes(20), ActivityEventType.ID_CLUE_DETECTED, false),
				completion,
				marker(start.plusMinutes(50), ActivityEventType.ID_RESOURCE_RETURNED_STAGING, false),
				marker(start.plusHours(1), ActivityEventType.ID_RESOURCE_ON_TASK, true),
				marker(start.plusHours(2), ActivityEventType.ID_RESOURCE_RETURNED_STAGING, false),
				activeOnTask));

		assertEquals(List.of(new IcpActivityTimelineDialog.TimelineSegment(start, start.plusMinutes(40), onTask,
				completion), new IcpActivityTimelineDialog.TimelineSegment(start.plusHours(2).plusMinutes(10),
						start.plusHours(2).plusMinutes(30), activeOnTask, null)),
				IcpActivityTimelineDialog.onTaskSegments(markers, start.plusHours(2).plusMinutes(30)));
	}

	@Test
	void connectorEndpointsUseTheSameVerticalOffsetsAsMarkersAtOverlappingTimes() {
		LocalDateTime start = LocalDateTime.of(2026, 10, 27, 8, 0);
		var first = marker(start, ActivityEventType.ID_RESOURCE_ON_TASK, false);
		var second = marker(start, ActivityEventType.ID_CLUE_DETECTED, false);
		var third = marker(start.plusSeconds(1), ActivityEventType.ID_TASK_COMPLETED, false);
		List<IcpActivityTimelineDialog.TimelineMarker> markers = List.of(first, second, third);

		assertEquals(0, IcpActivityTimelineDialog.markerVerticalOffset(markers, first, start));
		assertEquals(0, IcpActivityTimelineDialog.markerVerticalOffset(markers, second, start));
		assertEquals(0, IcpActivityTimelineDialog.markerVerticalOffset(markers, third, start));
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
			task.setTaskLifecycleStatus("assigned - on task");
			SarTaskResource resource = new SarTaskResource();
			resource.setName("Tracker One");
			resource.setFunction("Team Leader");
			task.getResourcesAssigned().add(resource);
			data.getSarTaskAssignments().add(task);
			Ics214Form log = new Ics214Form();
			ActivityLogEntry event = entry("assignment-1", ActivityEventType.ID_RESOURCE_ON_TASK);
			event.setResourceIdentifier("T1: Trackers");
			event.setNotableActivity("Tracker One is on task");
			log.getActivityLog().add(event);
			data.getActivityLogs().add(log);
			data.setActivityEventTypes(new ArrayList<>(ActivityEventType.defaultTypes()));
			data.getActivityEventTypes().add(new ActivityEventType("CANINE_ALERT", "Canine Alert", false, true));
			var incidentId = store.create(data);
			AppController controller = new AppController(data, store, incidentId,
					new PdfExportService(), new IncidentValidator());
			controller.getData().getSarTaskAssignments().add(task);
			AtomicReference<LogEntryContext> context = new AtomicReference<>();
			AtomicReference<String> openedAssignment = new AtomicReference<>();
			AtomicReference<IcpActivityTimelineDialog.QuickLogRequest> quickLog = new AtomicReference<>();
			SwingUtilities.invokeAndWait(() -> {
				IcpActivityTimelineDialog dialog = new IcpActivityTimelineDialog(null, controller, context::set,
						openedAssignment::set, quickLog::set);
				try {
					IncidentClockPanel clock = field(dialog, "clockPanel", IncidentClockPanel.class);
					JLabel digitalTime = field(clock, "currentTime", JLabel.class);
					JLabel timeZone = field(clock, "currentZone", JLabel.class);
					assertTrue(digitalTime.getFont().getSize2D() >= 18f);
					assertTrue(digitalTime.getText().matches("\\d{2}:\\d{2}:\\d{2}"));
					assertFalse(digitalTime.getText().contains(timeZone.getText()));
					assertEquals(digitalTime.getParent(), timeZone.getParent());
					assertEquals(new java.awt.Dimension(270, 72),
							field(dialog, "clockPanel", JPanel.class).getPreferredSize());
					assertTrue(((java.awt.FlowLayout) field(dialog, "clockPanel", JPanel.class).getLayout())
							.getAlignment() == java.awt.FlowLayout.CENTER);
					assertTrue(((javax.swing.border.CompoundBorder) field(dialog, "clockPanel", JPanel.class)
							.getBorder()).getInsideBorder().getBorderInsets(field(dialog, "clockPanel", JPanel.class))
							.bottom >= 7);
					assertTrue(descendants(dialog).stream().filter(JLabel.class::isInstance)
							.map(JLabel.class::cast).anyMatch(label -> "ICP activity by assignment.".equals(label.getText())));
					assertTrue(descendants(dialog).stream().filter(JLabel.class::isInstance)
							.map(JLabel.class::cast).anyMatch(label -> label.getText()
									.startsWith("Markers show staging, task, clue")));
					assertFalse(button(dialog, "Event Details…").isEnabled());
					assertFalse(button(dialog, "Open Task / Assignment…").isEnabled());
					JScrollPane scroll = descendants(dialog).stream().filter(JScrollPane.class::isInstance)
							.map(JScrollPane.class::cast).findFirst().orElseThrow();
					Component canvas = scroll.getViewport().getView();
					MouseEvent click = new MouseEvent(canvas, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(),
							0, 20, 68, 1, false);
					for (var listener : canvas.getMouseListeners()) {
						listener.mouseClicked(click);
					}
					assertTrue(button(dialog, "Event Details…").isEnabled());
					assertTrue(button(dialog, "Open Task / Assignment…").isEnabled());
					JPopupMenu popup;
					try {
						var popupMethod = IcpActivityTimelineDialog.class.getDeclaredMethod("createRowPopup");
						popupMethod.setAccessible(true);
						popup = (JPopupMenu) popupMethod.invoke(dialog);
					} catch (ReflectiveOperationException exception) {
						throw new AssertionError(exception);
					}
					assertEquals(List.of("Event Details…", "Add ICP Log Entry…", "Open Task / Assignment…",
							"Log T1 task completed", "Log T1 returned to staging", "Log T1 Canine Alert"),
							java.util.Arrays.stream(popup.getComponents()).filter(JMenuItem.class::isInstance)
									.map(JMenuItem.class::cast).map(JMenuItem::getText).toList());
					assertTrue(((JMenuItem) popup.getComponent(2)).isEnabled());
					assertFalse(popup.getComponentCount() > 0
							&& java.util.Arrays.stream(popup.getComponents()).filter(JMenuItem.class::isInstance)
									.map(JMenuItem.class::cast).anyMatch(item -> item.getText().contains("departed")));
					((JMenuItem) popup.getComponent(4)).doClick();
					assertEquals(ActivityEventType.ID_TASK_COMPLETED, quickLog.get().eventTypeId());
					assertEquals("T1 task completed", quickLog.get().activity());
					button(dialog, "Open Task / Assignment…").doClick();
					assertEquals("assignment-1", openedAssignment.get());

					canvas.setSize(canvas.getPreferredSize());
					BufferedImage image = new BufferedImage(canvas.getWidth(), canvas.getHeight(),
							BufferedImage.TYPE_INT_ARGB);
					canvas.paint(image.getGraphics());
					MouseEvent hover = new MouseEvent(canvas, MouseEvent.MOUSE_MOVED, System.currentTimeMillis(),
							0, 240, 68, 0, false);
					String tooltip = ((javax.swing.JComponent) canvas).getToolTipText(hover);
					assertTrue(tooltip.contains("T1: Trackers"));
					assertTrue(tooltip.contains("Tracker One is on task"));

					Timer acceptDetails = new Timer(100, eventTimer -> {
						for (java.awt.Window window : java.awt.Window.getWindows()) {
							if (window instanceof JDialog detail && "Timeline Details".equals(detail.getTitle())) {
								List<String> text = descendants(detail).stream().filter(JTextArea.class::isInstance)
										.map(JTextArea.class::cast).map(JTextArea::getText).toList();
								assertTrue(text.stream().anyMatch(value -> value.contains("Tracker One — Team Leader")));
								assertTrue(text.stream().anyMatch(value -> value.contains("T1: Trackers")
										&& value.contains("Tracker One is on task")));
								assertTrue(descendants(detail).stream().filter(JButton.class::isInstance)
										.map(JButton.class::cast).anyMatch(candidate ->
												"Log T1 task completed".equals(candidate.getText())));
								button(detail, "Add ICP Log Entry…").doClick();
							}
						}
					});
					acceptDetails.setRepeats(false);
					acceptDetails.start();
					button(dialog, "Event Details…").doClick();
					assertEquals("assignment-1", context.get().assignmentId());
					assertEquals("task-1", context.get().taskId());
					button(dialog, "Add Log Entry…").doClick();
					assertEquals("assignment-1", context.get().assignmentId());
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
	 * Creates a timeline marker for one test.
	 *
	 * @param timestamp event time.
	 * @param eventTypeId event category.
	 * @param struckOut whether the source entry is struck out.
	 * @return configured marker.
	 */
	private static IcpActivityTimelineDialog.TimelineMarker marker(LocalDateTime timestamp, String eventTypeId,
			boolean struckOut) {
		return new IcpActivityTimelineDialog.TimelineMarker(timestamp, eventTypeId, eventTypeId, "", "", struckOut);
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

	/**
	 * Reads a private timeline or shared clock component for focused visual assertions.
	 *
	 * @param dialog inspected timeline or clock.
	 * @param name component field name.
	 * @param type expected component type.
	 * @param <T> component type.
	 * @return the named component.
	 */
	private static <T> T field(Object dialog, String name, Class<T> type) {
		try {
			var field = dialog.getClass().getDeclaredField(name);
			field.setAccessible(true);
			return type.cast(field.get(dialog));
		} catch (ReflectiveOperationException exception) {
			throw new AssertionError(exception);
		}

	}

	/**
	 * Selects a lane before testing refresh-driven reordering.
	 *
	 * @param timeline inspected timeline.
	 * @param index selected lane index.
	 */
	private static void selectLane(IcpActivityTimelineDialog timeline, int index) {
		try {
			var field = IcpActivityTimelineDialog.class.getDeclaredField("selectedLane");
			field.setAccessible(true);
			field.setInt(timeline, index);
		} catch (ReflectiveOperationException exception) {
			throw new AssertionError(exception);
		}
	}
}
