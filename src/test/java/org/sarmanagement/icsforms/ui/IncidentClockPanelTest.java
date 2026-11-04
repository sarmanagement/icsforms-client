/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.pdf.PdfExportService;
import org.sarmanagement.icsforms.persistence.LocalRepository;
import org.sarmanagement.icsforms.validation.IncidentValidator;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** Covers shared clock formatting, accessibility, timezone refresh, and lifecycle. */
class IncidentClockPanelTest {
	@TempDir
	Path directory;

	@Test
	void formatsTwentyFourHourTimeAcrossZonesAndDaylightSavingTransitions() {
		assertEquals("23:34:56", IncidentClockPanel.formatClockTime(Instant.parse("2026-11-02T23:34:56Z"),
				ZoneId.of("UTC")));
		assertEquals("01:59:59", IncidentClockPanel.formatClockTime(Instant.parse("2026-11-01T08:59:59Z"),
				ZoneId.of("America/Los_Angeles")));
		assertEquals("01:00:00", IncidentClockPanel.formatClockTime(Instant.parse("2026-11-01T09:00:00Z"),
				ZoneId.of("America/Los_Angeles")));
	}

	@Test
	void initializesAccessibleClockWithoutStartingHiddenWork() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			AppController controller = controller();
			AppData data = controller.getData();
			data.setUseSystemTimeZone(false);
			data.setConfiguredTimeZoneId("America/Los_Angeles");
			IncidentClockPanel clock = new IncidentClockPanel(controller);
			try {
				JLabel digital = field(clock, "currentTime", JLabel.class);
				JLabel zone = field(clock, "currentZone", JLabel.class);
				JPanel analog = field(clock, "clockFace", JPanel.class);
				assertEquals("America/Los_Angeles", zone.getText());
				assertEquals(zone.getText(), zone.getToolTipText());
				assertTrue(digital.getText().matches("\\d{2}:\\d{2}:\\d{2}"));
				assertTrue(digital.getFont().getSize2D() >= 18f);
				assertTrue(digital.getAccessibleContext().getAccessibleName().startsWith("Current time, 24-hour "));
				assertEquals("Current time, analog clock", analog.getAccessibleContext().getAccessibleName());
				assertTrue(analog.getAccessibleContext().getAccessibleDescription().contains(zone.getText()));
				assertFalse(timer(clock).isRunning());
				assertEquals(0, listeners(controller));
				analog.setSize(48, 48);
				var graphics = new BufferedImage(48, 48, BufferedImage.TYPE_INT_ARGB).createGraphics();
				try {
					analog.paint(graphics);
				} finally {
					graphics.dispose();
				}
			} finally {
				clock.dispose();
			}
		});
	}

	@Test
	void refreshesTimeZoneAndStopsOnHideRemovalAndDisposalWithoutLeakingListeners() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		SwingUtilities.invokeAndWait(() -> {
			AppController controller = controller();
			IncidentClockPanel clock = new IncidentClockPanel(controller);
			JFrame frame = new JFrame();
			try {
				frame.add(clock);
				frame.pack();
				frame.setVisible(true);
				assertTrue(timer(clock).isRunning());
				assertEquals(1, listeners(controller));
				controller.getData().setUseSystemTimeZone(false);
				controller.getData().setConfiguredTimeZoneId("Pacific/Honolulu");
				for (var listener : timer(clock).getActionListeners()) {
					listener.actionPerformed(null);
				}
				assertEquals("Pacific/Honolulu", field(clock, "currentZone", JLabel.class).getText());
				controller.getData().setConfiguredTimeZoneId("invalid zone");
				for (var listener : timer(clock).getActionListeners()) {
					listener.actionPerformed(null);
				}
				assertEquals(ZoneId.systemDefault().getId(), field(clock, "currentZone", JLabel.class).getText());
				frame.setVisible(false);
				assertFalse(timer(clock).isRunning());
				assertEquals(0, listeners(controller));
				frame.setVisible(true);
				assertTrue(timer(clock).isRunning());
				assertEquals(1, listeners(controller));
				frame.remove(clock);
				assertFalse(timer(clock).isRunning());
				assertEquals(0, listeners(controller));
				frame.add(clock);
				frame.validate();
				assertTrue(timer(clock).isRunning());
				assertEquals(1, listeners(controller));
				clock.dispose();
				clock.dispose();
				assertFalse(timer(clock).isRunning());
				assertEquals(0, listeners(controller));
				frame.setVisible(false);
				frame.setVisible(true);
				assertFalse(timer(clock).isRunning());
				assertEquals(0, listeners(controller));
			} finally {
				clock.dispose();
				frame.dispose();
			}
		});
	}

	@Test
	void roleHeadersShareTheClockAndDisposeItsRefreshWork() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			AppController controller = controller();
			IncidentCommandRolePanel command = new IncidentCommandRolePanel(controller, ignored -> { });
			OperationsRolePanel operations = new OperationsRolePanel(controller, () -> null, ignored -> { },
					ignored -> { }, ignored -> { }, () -> { }, ignored -> { }, () -> { }, () -> { });
			JFrame frame = GraphicsEnvironment.isHeadless() ? null : new JFrame();
			try {
				IncidentClockPanel commandClock = field(command, "clockPanel", IncidentClockPanel.class);
				IncidentClockPanel operationsClock = field(operations, "clockPanel", IncidentClockPanel.class);
				JPanel commandHeader = (JPanel) ((BorderLayout) command.getLayout())
						.getLayoutComponent(BorderLayout.NORTH);
				JPanel operationsHeader = (JPanel) ((BorderLayout) operations.getLayout())
						.getLayoutComponent(BorderLayout.NORTH);
				assertEquals(commandHeader, commandClock.getParent());
				assertEquals(operationsHeader, operationsClock.getParent().getParent());
				if (frame != null) {
					JPanel roles = new JPanel(new java.awt.GridLayout(2, 1));
					roles.add(command);
					roles.add(operations);
					frame.add(roles);
					frame.pack();
					frame.setVisible(true);
					assertTrue(timer(commandClock).isRunning());
					assertTrue(timer(operationsClock).isRunning());
				}
				command.dispose();
				operations.dispose();
				assertFalse(timer(commandClock).isRunning());
				assertFalse(timer(operationsClock).isRunning());
				assertTrue(field(commandClock, "disposed", Boolean.class));
				assertTrue(field(operationsClock, "disposed", Boolean.class));
				assertEquals(0, listeners(controller));
			} finally {
				command.dispose();
				operations.dispose();
				if (frame != null) {
					frame.dispose();
				}
			}
		});
	}

	@Test
	void timelineDisposesItsSharedClock() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		SwingUtilities.invokeAndWait(() -> {
			AppController controller = controller();
			IcpActivityTimelineDialog timeline = new IcpActivityTimelineDialog(null, controller, ignored -> { });
			IncidentClockPanel clock = field(timeline, "clockPanel", IncidentClockPanel.class);
			try {
				assertFalse(timer(clock).isRunning());
				timeline.setVisible(true);
				assertTrue(timer(clock).isRunning());
				assertEquals(2, listeners(controller));
				timeline.dispose();
				assertFalse(timer(clock).isRunning());
				assertEquals(0, listeners(controller));
				assertTrue(field(clock, "disposed", Boolean.class));
			} finally {
				timeline.dispose();
			}
		});
	}

	/**
	 * Creates a controller backed by this test's local repository.
	 *
	 * @return incident controller.
	 */
	private AppController controller() {
		return new AppController(new AppData(), new LocalRepository(directory.resolve("incident.json")),
				new PdfExportService(), new IncidentValidator());
	}

	/**
	 * Reads the shared clock's refresh timer.
	 *
	 * @param clock clock under test.
	 * @return refresh timer.
	 */
	private static Timer timer(IncidentClockPanel clock) {
		return field(clock, "clockTimer", Timer.class);
	}

	/**
	 * Counts live incident listener registrations.
	 *
	 * @param controller incident controller.
	 * @return registered listener count.
	 */
	private static int listeners(AppController controller) {
		return field(controller, "incidentChangeListeners", Collection.class).size();
	}

	/**
	 * Reads private state for lifecycle and accessible-component assertions.
	 *
	 * @param target inspected object.
	 * @param name field name.
	 * @param type expected field type.
	 * @param <T> field type.
	 * @return field value.
	 */
	private static <T> T field(Object target, String name, Class<T> type) {
		try {
			var field = target.getClass().getDeclaredField(name);
			field.setAccessible(true);
			return type.cast(field.get(target));
		} catch (ReflectiveOperationException exception) {
			throw new AssertionError(exception);
		}
	}
}
