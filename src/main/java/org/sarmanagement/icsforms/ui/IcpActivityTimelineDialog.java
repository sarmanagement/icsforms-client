/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.sarmanagement.icsforms.model.ActivityEventType;
import org.sarmanagement.icsforms.model.ActivityLogEntry;
import org.sarmanagement.icsforms.model.ActivityLogScope;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.Ics214Form;
import org.sarmanagement.icsforms.model.SarTaskAssignment;
import org.sarmanagement.icsforms.model.SarTaskSupport;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Proof-of-concept timeline for ICP activity, grouped by assignment.
 */
public final class IcpActivityTimelineDialog extends JDialog {
	private final AppController controller;
	private final Consumer<LogEntryContext> addEntry;
	private final TimelineCanvas canvas = new TimelineCanvas();
	private final JLabel selectionLabel = new JLabel("Select an assignment lane or add an ICP entry.");
	private List<TimelineLane> lanes = List.of();
	private int selectedLane = -1;

	/**
	 * Creates a modeless ICP timeline with the shared activity-entry workflow.
	 *
	 * @param owner parent window.
	 * @param controller active incident controller.
	 * @param addEntry shared activity entry action.
	 */
	public IcpActivityTimelineDialog(Window owner, AppController controller, Consumer<LogEntryContext> addEntry) {
		super(owner, "ICP Activity Timeline", ModalityType.MODELESS);
		this.controller = Objects.requireNonNull(controller);
		this.addEntry = Objects.requireNonNull(addEntry);
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		JPanel content = new JPanel(new BorderLayout(8, 8));
		content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		content.add(new JLabel("ICP activity by assignment. Markers show staging, task, clue, subject, completion, and return events."),
				BorderLayout.NORTH);
		JScrollPane scroll = new JScrollPane(canvas);
		scroll.setPreferredSize(new Dimension(1000, 540));
		JPanel timelineView = new JPanel(new BorderLayout(0, 4));
		timelineView.add(buildLegend(), BorderLayout.NORTH);
		timelineView.add(scroll, BorderLayout.CENTER);
		content.add(timelineView, BorderLayout.CENTER);
		JPanel controls = new JPanel(new BorderLayout());
		controls.add(selectionLabel, BorderLayout.CENTER);
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		JButton addButton = new JButton("Add ICP Activity…");
		addButton.addActionListener(event -> addSelectedEntry());
		JButton refreshButton = new JButton("Refresh");
		refreshButton.addActionListener(event -> refresh());
		JButton closeButton = new JButton("Close");
		closeButton.addActionListener(event -> dispose());
		buttons.add(addButton);
		buttons.add(refreshButton);
		buttons.add(closeButton);
		controls.add(buttons, BorderLayout.EAST);
		content.add(controls, BorderLayout.SOUTH);
		setContentPane(content);
		refresh();
		pack();
		setMinimumSize(new Dimension(760, 420));
		setLocationRelativeTo(owner);
	}

	/**
	 * Builds a legend for the timeline's compact event symbols.
	 *
	 * @return legend row.
	 */
	private JPanel buildLegend() {
		JPanel legend = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
		addLegendItem(legend, "D", "Depart", markerColor(ActivityEventType.ID_RESOURCE_DEPARTED_STAGING));
		addLegendItem(legend, "T", "On task", markerColor(ActivityEventType.ID_RESOURCE_ON_TASK));
		addLegendItem(legend, "C", "Clue", markerColor(ActivityEventType.ID_CLUE_DETECTED));
		addLegendItem(legend, "S", "Subject located", markerColor(ActivityEventType.ID_SUBJECT_FOUND));
		addLegendItem(legend, "✓", "Complete", markerColor(ActivityEventType.ID_TASK_COMPLETED));
		addLegendItem(legend, "R", "Return to staging/base",
				markerColor(ActivityEventType.ID_RESOURCE_RETURNED_STAGING));
		addLegendItem(legend, "•", "Note", markerColor(ActivityEventType.ID_FREE_TEXT));
		return legend;
	}

	/**
	 * Adds one colored marker description to the timeline legend.
	 *
	 * @param legend containing panel.
	 * @param marker compact symbol.
	 * @param description event category.
	 * @param color marker color.
	 */
	private void addLegendItem(JPanel legend, String marker, String description, Color color) {
		JLabel label = new JLabel(marker + " " + description);
		label.setForeground(color);
		label.setFont(label.getFont().deriveFont(Font.BOLD, 11f));
		legend.add(label);
	}

	/**
	 * Reloads assignment lanes and event markers from the active incident.
	 */
	private void refresh() {
		lanes = buildLanes(controller.getData());
		if (selectedLane >= lanes.size()) {
			selectedLane = -1;
		}
		canvas.revalidate();
		canvas.repaint();
		updateSelectionLabel();
	}

	/**
	 * Adds an activity entry associated with the selected assignment, when any.
	 */
	private void addSelectedEntry() {
		TimelineLane lane = selectedLane < 0 ? null : lanes.get(selectedLane);
		addEntry.accept(new LogEntryContext(this, lane == null ? "" : lane.taskId(),
				lane == null ? "" : lane.assignmentId(), lane == null ? "" : lane.resourceIdentifier()));
		refresh();
	}

	/**
	 * Updates the selected-lane prompt.
	 */
	private void updateSelectionLabel() {
		if (selectedLane < 0) {
			selectionLabel.setText("Select an assignment lane or add an ICP entry.");
			return;
		}
		selectionLabel.setText("Selected: " + lanes.get(selectedLane).label());
	}

	/**
	 * Builds ICP lanes, retaining assignments that do not yet have logged events.
	 *
	 * @param data active incident snapshot.
	 * @return ordered lanes with their timestamped markers.
	 */
	static List<TimelineLane> buildLanes(AppData data) {
		Map<String, MutableLane> grouped = new LinkedHashMap<>();
		if (data == null) {
			return List.of();
		}
		for (SarTaskAssignment task : data.getSarTaskAssignments()) {
			if (task == null) {
				continue;
			}
			String assignmentId = safe(task.getAssignmentId());
			grouped.putIfAbsent(assignmentId, new MutableLane(assignmentId,
					task.getTaskId(), SarTaskSupport.taskResourceDisplayLabel(task), task.getResourceIdentifier()));
		}
		grouped.put("", new MutableLane("", "", "ICP / Unassigned", ""));
		Map<String, String> eventLabels = new LinkedHashMap<>();
		List<ActivityEventType> eventTypes = data.getActivityEventTypes().isEmpty()
				? ActivityEventType.defaultTypes() : data.getActivityEventTypes();
		for (ActivityEventType eventType : eventTypes) {
			eventLabels.put(eventType.getId(), eventType.getLabel());
		}
		for (Ics214Form log : data.getActivityLogs()) {
			if (log == null || log.getLogScope() != ActivityLogScope.ICP || log.getActivityLog() == null) {
				continue;
			}
			for (ActivityLogEntry entry : log.getActivityLog()) {
				if (entry == null || entry.getTimestamp() == null) {
					continue;
				}
				String assignmentId = safe(entry.getAssignmentId());
				if (assignmentId.isBlank()) {
					assignmentId = assignmentIdForResource(data, entry.getResourceIdentifier());
				}
				MutableLane lane = grouped.get(assignmentId);
				if (lane == null) {
					String resource = safe(entry.getResourceIdentifier());
					String label = resource.isBlank() ? "Unassigned" : resource;
					lane = new MutableLane(assignmentId, "", label, resource);
					grouped.put(assignmentId, lane);
				}
				String typeId = entry.getEventTypeId();
				lane.markers.add(new TimelineMarker(entry.getTimestamp(), typeId,
						eventLabels.getOrDefault(typeId, "Activity"), entry.getNotableActivity(),
						entry.isStruckOut()));
			}
		}
		List<TimelineLane> result = new ArrayList<>();
		for (MutableLane lane : grouped.values()) {
			lane.markers.sort(Comparator.comparing(TimelineMarker::timestamp));
			result.add(new TimelineLane(lane.assignmentId, lane.taskId, lane.label, lane.resourceIdentifier,
					List.copyOf(lane.markers)));
		}
		return List.copyOf(result);
	}

	/**
	 * Resolves a task's stable ID from a legacy activity resource label.
	 *
	 * @param data incident containing task assignments.
	 * @param resourceIdentifier log resource label.
	 * @return matching assignment ID, or empty when no task matches.
	 */
	private static String assignmentIdForResource(AppData data, String resourceIdentifier) {
		String resource = safe(resourceIdentifier);
		if (resource.isBlank()) {
			return "";
		}
		for (SarTaskAssignment task : data.getSarTaskAssignments()) {
			if (resource.equals(task.getResourceIdentifier())
					|| resource.equals(SarTaskSupport.taskResourceDisplayLabel(task))) {
				return task.getAssignmentId();
			}
		}
		return "";
	}

	/**
	 * Returns the compact visual symbol for a built-in lifecycle or clue event.
	 *
	 * @param eventTypeId stable event type ID.
	 * @return short marker symbol.
	 */
	static String markerFor(String eventTypeId) {
		return switch (safe(eventTypeId)) {
			case ActivityEventType.ID_RESOURCE_DEPARTED_STAGING -> "D";
			case ActivityEventType.ID_RESOURCE_ON_TASK -> "T";
			case ActivityEventType.ID_CLUE_DETECTED, ActivityEventType.ID_CLUE_REPORTED -> "C";
			case ActivityEventType.ID_SUBJECT_FOUND -> "S";
			case ActivityEventType.ID_TASK_COMPLETED -> "✓";
			case ActivityEventType.ID_RESOURCE_RETURNED_STAGING -> "R";
			default -> "•";
		};
	}

	/**
	 * Returns the marker color for a built-in activity type.
	 *
	 * @param eventTypeId stable event type ID.
	 * @return marker color.
	 */
	private static Color markerColor(String eventTypeId) {
		return switch (safe(eventTypeId)) {
			case ActivityEventType.ID_RESOURCE_DEPARTED_STAGING -> new Color(178, 92, 0);
			case ActivityEventType.ID_RESOURCE_ON_TASK -> new Color(29, 95, 155);
			case ActivityEventType.ID_CLUE_DETECTED, ActivityEventType.ID_CLUE_REPORTED ->
				new Color(118, 71, 160);
			case ActivityEventType.ID_SUBJECT_FOUND -> new Color(33, 125, 70);
			case ActivityEventType.ID_TASK_COMPLETED -> new Color(74, 74, 74);
			case ActivityEventType.ID_RESOURCE_RETURNED_STAGING -> new Color(0, 121, 128);
			default -> new Color(105, 105, 105);
		};
	}

	/**
	 * Returns an empty string for null display values.
	 *
	 * @param value source text.
	 * @return non-null text.
	 */
	private static String safe(String value) {
		return value == null ? "" : value;
	}

	/**
	 * Assignment lane and its event markers.
	 *
	 * @param assignmentId stable assignment identity.
	 * @param taskId task identity.
	 * @param label visible lane label.
	 * @param resourceIdentifier resource display name.
	 * @param markers chronologically ordered activity markers.
	 */
	record TimelineLane(String assignmentId, String taskId, String label, String resourceIdentifier,
			List<TimelineMarker> markers) {
	}

	/**
	 * A timestamped marker shown on one assignment lane.
	 *
	 * @param timestamp event time.
	 * @param eventTypeId stable event type identity.
	 * @param eventLabel configured event label.
	 * @param activity notable activity text.
	 * @param struckOut whether the source entry is struck out.
	 */
	record TimelineMarker(LocalDateTime timestamp, String eventTypeId, String eventLabel, String activity,
			boolean struckOut) {
	}

	/**
	 * Mutable lane used only while constructing the timeline model.
	 */
	private static final class MutableLane {
		private final String assignmentId;
		private final String taskId;
		private final String label;
		private final String resourceIdentifier;
		private final List<TimelineMarker> markers = new ArrayList<>();

		/**
		 * Creates a lane builder.
		 *
		 * @param assignmentId assignment identity.
		 * @param taskId task identity.
		 * @param label visible lane label.
		 * @param resourceIdentifier resource display label.
		 */
		private MutableLane(String assignmentId, String taskId, String label, String resourceIdentifier) {
			this.assignmentId = assignmentId;
			this.taskId = taskId;
			this.label = label;
			this.resourceIdentifier = resourceIdentifier;
		}
	}

	/**
	 * Scrollable chart that paints lanes, hourly guides, and event symbols.
	 */
	private final class TimelineCanvas extends JPanel {
		private static final int LEFT = 240;
		private static final int TOP = 42;
		private static final int ROW_HEIGHT = 52;
		private static final int HOUR_WIDTH = 100;
		private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
		private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM d");
		private final List<MarkerHit> hits = new ArrayList<>();

		/**
		 * Creates a white, tooltip-enabled timeline canvas.
		 */
		private TimelineCanvas() {
			setBackground(Color.WHITE);
			setOpaque(true);
			javax.swing.ToolTipManager.sharedInstance().registerComponent(this);
			addMouseListener(new MouseAdapter() {
				/**
				 * Selects the assignment lane clicked by the operator.
				 *
				 * @param event pointer event.
				 */
				@Override
				public void mouseClicked(MouseEvent event) {
					int laneIndex = event.getY() < TOP ? -1 : (event.getY() - TOP) / ROW_HEIGHT;
					if (laneIndex >= 0 && laneIndex < lanes.size()) {
						selectedLane = laneIndex;
						updateSelectionLabel();
						repaint();
					}
				}
			});
		}

		/** {@inheritDoc} */
		@Override
		public Dimension getPreferredSize() {
			List<TimelineMarker> markers = allMarkers();
			long hours = timelineHours(markers);
			return new Dimension(Math.max(960, LEFT + (int) (hours * HOUR_WIDTH) + 60),
					TOP + Math.max(1, lanes.size()) * ROW_HEIGHT + 22);
		}

		/** {@inheritDoc} */
		@Override
		protected void paintComponent(Graphics graphics) {
			super.paintComponent(graphics);
			Graphics2D g = (Graphics2D) graphics.create();
			try {
				g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				drawTimeline(g);
			} finally {
				g.dispose();
			}
		}

		/**
		 * Returns the marker tooltip under the pointer.
		 *
		 * @param event pointer location.
		 * @return event time, category, and activity text.
		 */
		@Override
		public String getToolTipText(MouseEvent event) {
			for (MarkerHit hit : hits) {
				if (hit.contains(event.getX(), event.getY())) {
					return "<html>" + hit.tooltip() + "</html>";
				}
			}
			return null;
		}

		/**
		 * Draws the axis, lanes, and event symbols.
		 *
		 * @param g drawing context.
		 */
		private void drawTimeline(Graphics2D g) {
			List<TimelineMarker> markers = allMarkers();
			LocalDateTime start = timelineStart(markers);
			long hours = timelineHours(markers);
			g.setColor(new Color(80, 80, 80));
			g.setFont(getFont().deriveFont(Font.BOLD, 12f));
			g.drawString("Assignment", 8, 24);
			for (long hour = 0; hour <= hours; hour++) {
				int x = LEFT + (int) (hour * HOUR_WIDTH);
				LocalDateTime tick = start.plusHours(hour);
				g.setColor(new Color(215, 220, 225));
				g.setStroke(new BasicStroke(1f));
				g.drawLine(x, TOP - 10, x, TOP + lanes.size() * ROW_HEIGHT);
				g.setColor(new Color(70, 70, 70));
				g.setFont(getFont().deriveFont(Font.PLAIN, 11f));
				g.drawString(tick.getHour() == 0 ? tick.format(DATE) : tick.format(TIME), x + 3, 24);
			}
			hits.clear();
			for (int laneIndex = 0; laneIndex < lanes.size(); laneIndex++) {
				TimelineLane lane = lanes.get(laneIndex);
				int top = TOP + laneIndex * ROW_HEIGHT;
				if (laneIndex == selectedLane) {
					g.setColor(new Color(230, 241, 252));
					g.fillRect(0, top, getWidth(), ROW_HEIGHT);
				}
				g.setColor(new Color(222, 226, 230));
				g.drawLine(0, top + ROW_HEIGHT, getWidth(), top + ROW_HEIGHT);
				g.setColor(new Color(45, 50, 55));
				g.setFont(getFont().deriveFont(Font.PLAIN, 12f));
				g.drawString(trim(lane.label(), 32), 8, top + ROW_HEIGHT / 2 + 4);
				drawMarkers(g, lane, laneIndex, start);
			}
			if (lanes.isEmpty()) {
				g.setColor(Color.DARK_GRAY);
				g.drawString("No ICP activity or assignments yet.", 12, TOP + 20);
			}
		}

		/**
		 * Draws all events for a lane and records their tooltip bounds.
		 *
		 * @param g drawing context.
		 * @param lane assignment row.
		 * @param laneIndex row number.
		 * @param start horizontal-axis origin.
		 */
		private void drawMarkers(Graphics2D g, TimelineLane lane, int laneIndex, LocalDateTime start) {
			Map<Integer, Integer> offsets = new LinkedHashMap<>();
			for (TimelineMarker marker : lane.markers()) {
				long minutes = Duration.between(start, marker.timestamp()).toMinutes();
				int x = LEFT + (int) (minutes * HOUR_WIDTH / 60f);
				int centerY = TOP + laneIndex * ROW_HEIGHT + ROW_HEIGHT / 2;
				int overlap = offsets.merge(x, 1, Integer::sum) - 1;
				int y = centerY + (overlap % 3 - 1) * 10;
				Color color = markerColor(marker.eventTypeId());
				g.setColor(color);
				g.fillOval(x - 8, y - 8, 16, 16);
				g.setColor(Color.WHITE);
				g.setFont(getFont().deriveFont(Font.BOLD, 9f));
				g.drawString(markerFor(marker.eventTypeId()), x - 4, y + 3);
				if (marker.struckOut()) {
					g.setColor(Color.BLACK);
					g.drawLine(x - 8, y, x + 8, y);
				}
				String text = marker.timestamp().format(TIME) + " — " + marker.eventLabel() + ": "
						+ safe(marker.activity()).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
				hits.add(new MarkerHit(x - 10, y - 10, x + 10, y + 10, text));
			}
		}

		/**
		 * Collects all plotted events.
		 *
		 * @return timeline markers in all assignment lanes.
		 */
		private List<TimelineMarker> allMarkers() {
			return lanes.stream().flatMap(lane -> lane.markers().stream()).toList();
		}

		/**
		 * Returns the chart's rounded hourly range.
		 *
		 * @param markers all plotted markers.
		 * @return at least one hour.
		 */
		private long timelineHours(List<TimelineMarker> markers) {
			if (markers.isEmpty()) {
				return 1;
			}
			LocalDateTime start = timelineStart(markers);
			LocalDateTime end = markers.stream().map(TimelineMarker::timestamp).max(LocalDateTime::compareTo).orElse(start);
			return Math.max(1, (long) Math.ceil(Duration.between(start, end).toMinutes() / 60.0));
		}

		/**
		 * Returns the start of the first plotted hour.
		 *
		 * @param markers all plotted markers.
		 * @return rounded axis start.
		 */
		private LocalDateTime timelineStart(List<TimelineMarker> markers) {
			return markers.stream().map(TimelineMarker::timestamp).min(LocalDateTime::compareTo)
					.map(value -> value.withMinute(0).withSecond(0).withNano(0)).orElse(LocalDateTime.now().withMinute(0));
		}

		/**
		 * Truncates a long lane label for its fixed-width column.
		 *
		 * @param text source label.
		 * @param maxLength visible character limit.
		 * @return display label.
		 */
		private String trim(String text, int maxLength) {
			return text.length() <= maxLength ? text : text.substring(0, maxLength - 1) + "…";
		}
	}

	/**
	 * One marker's clickable/hoverable canvas bounds.
	 *
	 * @param left left edge.
	 * @param top top edge.
	 * @param right right edge.
	 * @param bottom bottom edge.
	 * @param tooltip hover text.
	 */
	private record MarkerHit(int left, int top, int right, int bottom, String tooltip) {
		/**
		 * Tests whether the pointer is inside this marker.
		 *
		 * @param x pointer horizontal coordinate.
		 * @param y pointer vertical coordinate.
		 * @return true when contained.
		 */
		private boolean contains(int x, int y) {
			return x >= left && x <= right && y >= top && y <= bottom;
		}
	}
}
