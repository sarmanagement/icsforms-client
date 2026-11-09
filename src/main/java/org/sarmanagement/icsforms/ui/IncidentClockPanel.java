/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.sarmanagement.icsforms.model.UtcTimestamp;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Reusable incident management clock panel combining analog and digital displays.
 * Provides 12-hour analog clock face with synchronized 24-hour digital display and
 * timezone label. Updates at 1-second intervals only while showing, using the
 * incident timezone settings.
 *
 * @since 0.1.2
 */
public final class IncidentClockPanel extends JPanel {
	private final AppController controller;
	private final JLabel currentTime = new JLabel();
	private final JLabel currentZone = new JLabel();
	private final ClockFace clockFace = new ClockFace();
	private final Timer clockTimer;
	private final HierarchyListener hierarchyListener;
	private AppController.IncidentChangeSubscription subscription;
	private boolean disposed;

	/**
	 * Creates an incident clock panel with automatic timezone and time updates.
	 * Starts refresh work only while showing in a visible hierarchy. Call
	 * {@link #dispose()} when the panel is no longer needed.
	 *
	 * @param controller active incident controller for incident-specific timezone config.
	 * @throws NullPointerException if controller is null.
	 */
	public IncidentClockPanel(AppController controller) {
		this.controller = Objects.requireNonNull(controller, "controller must not be null");
		setLayout(new FlowLayout(FlowLayout.CENTER, 4, -2));
		setBorder(BorderFactory.createCompoundBorder(BorderFactory.createTitledBorder("Current time"),
				BorderFactory.createEmptyBorder(0, 0, 7, 0)));
		setPreferredSize(new Dimension(270, 72));
		getAccessibleContext().setAccessibleName("Incident clock");

		clockFace.setPreferredSize(new Dimension(48, 48));
		JPanel digitalClock = new JPanel(new GridLayout(2, 1, 0, 0));
		currentTime.setFont(currentTime.getFont().deriveFont(Font.BOLD, 18f));
		currentZone.getAccessibleContext().setAccessibleName("Current time zone");
		digitalClock.add(currentTime);
		digitalClock.add(currentZone);
		add(clockFace);
		add(digitalClock);

		refreshCurrentTime();
		clockTimer = new Timer(1000, event -> refreshCurrentTime());
		hierarchyListener = event -> {
			if ((event.getChangeFlags() & (HierarchyEvent.SHOWING_CHANGED
					| HierarchyEvent.DISPLAYABILITY_CHANGED)) != 0) {
				updateRefreshLifecycle();
			}
		};
		addHierarchyListener(hierarchyListener);
	}

	/**
	 * Permanently stops refresh work and releases any incident listener.
	 */
	public void dispose() {
		disposed = true;
		clockTimer.stop();
		closeSubscription();
		removeHierarchyListener(hierarchyListener);
	}

	/** Starts or suspends refresh work as the clock's visible hierarchy changes. */
	private void updateRefreshLifecycle() {
		if (!disposed && isShowing()) {
			refreshCurrentTime();
			if (subscription == null) {
				subscription = controller.addIncidentChangeListener(change -> refreshCurrentTime());
			}
			clockTimer.start();
		} else {
			clockTimer.stop();
			closeSubscription();
		}
	}

	/** Releases the current live registration, if any. */
	private void closeSubscription() {
		if (subscription != null) {
			subscription.close();
			subscription = null;
		}
	}

	/**
	 * Updates the analog clock and digital display to the current time in the
	 * configured incident timezone.
	 */
	private void refreshCurrentTime() {
		if (disposed) {
			return;
		}
		ZoneId zone = MainFrame.configuredZoneId(controller.getData());
		ZonedDateTime now = UtcTimestamp.nowInstant().atZone(zone);
		currentTime.setText(formatClockTime(now.toInstant(), zone));
		currentZone.setText(zone.getId());
		currentZone.setToolTipText(zone.getId());
		currentTime.getAccessibleContext().setAccessibleName("Current time, 24-hour " + currentTime.getText()
				+ " " + zone.getId());
		clockFace.setTime(now);
	}

	/**
	 * Formats a digital clock value in 24-hour time for a selected zone.
	 * Produces output in "HH:mm:ss" format.
	 *
	 * @param instant current UTC instant.
	 * @param zoneId selected display timezone.
	 * @return 24-hour digital time in "HH:mm:ss" format.
	 */
	static String formatClockTime(Instant instant, ZoneId zoneId) {
		return DateTimeFormatter.ofPattern("HH:mm:ss").withZone(zoneId).format(instant);
	}

	/**
	 * 12-hour analog clock face with hour, minute, and second hands.
	 * Renders with antialiased graphics and supports timezone-aware time display.
	 */
	private static final class ClockFace extends JPanel {
		private ZonedDateTime time = ZonedDateTime.now();

		/** Creates an accessible analog clock face. */
		private ClockFace() {
			setOpaque(false);
			getAccessibleContext().setAccessibleName("Current time, analog clock");
		}

		/**
		 * Updates the analog clock and its accessible description.
		 *
		 * @param current current zoned time.
		 */
		private void setTime(ZonedDateTime current) {
			time = current;
			getAccessibleContext().setAccessibleDescription(
					"Current time " + formatClockTime(current.toInstant(), current.getZone()) + " "
							+ current.getZone().getId());
			repaint();
		}

		/** {@inheritDoc} */
		@Override
		protected void paintComponent(Graphics graphics) {
			super.paintComponent(graphics);
			Graphics2D g = (Graphics2D) graphics.create();
			try {
				g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				int size = Math.min(getWidth(), getHeight()) - 4;
				int centerX = getWidth() / 2;
				int centerY = getHeight() / 2;
				int radius = size / 2;
				g.setColor(Color.WHITE);
				g.fillOval(centerX - radius, centerY - radius, size, size);
				g.setColor(new Color(65, 75, 85));
				g.setStroke(new BasicStroke(1.5f));
				g.drawOval(centerX - radius, centerY - radius, size, size);
				for (int tick = 0; tick < 12; tick++) {
					double angle = Math.PI * tick / 6 - Math.PI / 2;
					int outerX = centerX + (int) (Math.cos(angle) * (radius - 3));
					int outerY = centerY + (int) (Math.sin(angle) * (radius - 3));
					int inset = tick % 3 == 0 ? 8 : 5;
					int innerX = centerX + (int) (Math.cos(angle) * (radius - inset));
					int innerY = centerY + (int) (Math.sin(angle) * (radius - inset));
					g.drawLine(innerX, innerY, outerX, outerY);
				}
				double seconds = time.getSecond() + time.getNano() / 1_000_000_000d;
				double minutes = time.getMinute() + seconds / 60d;
				double hours = time.getHour() % 12 + minutes / 60d;
				drawHand(g, centerX, centerY, radius * 0.48, hours / 12d, 2.5f);
				drawHand(g, centerX, centerY, radius * 0.7, minutes / 60d, 1.8f);
				drawHand(g, centerX, centerY, radius * 0.76, seconds / 60d, 1f);
				g.setColor(new Color(45, 50, 55));
				g.fillOval(centerX - 2, centerY - 2, 4, 4);
			} finally {
				g.dispose();
			}
		}

		/**
		 * Draws one clock hand.
		 *
		 * @param graphics drawing context.
		 * @param centerX clock center x.
		 * @param centerY clock center y.
		 * @param length hand length.
		 * @param fraction position around the dial (0-1).
		 * @param stroke hand width in pixels.
		 */
		private void drawHand(Graphics2D graphics, int centerX, int centerY, double length, double fraction,
				float stroke) {
			double angle = fraction * Math.PI * 2 - Math.PI / 2;
			graphics.setColor(new Color(45, 50, 55));
			graphics.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			graphics.drawLine(centerX, centerY, centerX + (int) (Math.cos(angle) * length),
					centerY + (int) (Math.sin(angle) * length));
		}
	}
}
