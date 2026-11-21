/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.pdf.PdfExportService;
import org.sarmanagement.icsforms.persistence.ConnectionMonitor;
import org.sarmanagement.icsforms.persistence.FileIncidentStore;
import org.sarmanagement.icsforms.persistence.IncidentId;
import org.sarmanagement.icsforms.persistence.RemoteIncidentGateway;
import org.sarmanagement.icsforms.persistence.RemoteIncidentStore;
import org.sarmanagement.icsforms.persistence.StoreUnavailableException;
import org.sarmanagement.icsforms.persistence.StoredIncident;
import org.sarmanagement.icsforms.validation.IncidentValidator;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * Displayed-lane tests for connected operation in {@link MainFrame}: capability-driven menu state, the connect
 * flow, the status line and the host/standalone store switches.
 */
class MainFrameConnectedModeTest {
	@TempDir
	Path directory;
	private final List<MainFrame> frames = new ArrayList<>();
	private String previousPoll;

	/**
	 * Skips headless runs and makes polling fast.
	 */
	@BeforeEach
	void setUp() {
		assumeFalse(GraphicsEnvironment.isHeadless());
		previousPoll = System.getProperty(ConnectionMonitor.POLL_INTERVAL_PROPERTY);
		System.setProperty(ConnectionMonitor.POLL_INTERVAL_PROPERTY, "50");
	}

	/**
	 * Disposes every window and restores the polling property.
	 *
	 * @throws Exception when the event thread cannot be reached
	 */
	@AfterEach
	void tearDown() throws Exception {
		if (previousPoll == null) {
			System.clearProperty(ConnectionMonitor.POLL_INTERVAL_PROPERTY);
		} else {
			System.setProperty(ConnectionMonitor.POLL_INTERVAL_PROPERTY, previousPoll);
		}
		SwingUtilities.invokeAndWait(() -> {
			for (Window window : Window.getWindows()) {
				if (window instanceof MainFrame frame && frame.isDisplayable()) {
					((Timer) field(field(frame, "controller"), "autosaveTimer")).stop();
					frame.dispose();
				}
			}
		});
	}

	@Test
	void connectedAndSharingWindowsDisableCreateDuplicateAndImportFromStoreCapabilities() throws Exception {
		try (HostedIncidentFixture host = new HostedIncidentFixture(directory.resolve("host"), "Shared");
				FileIncidentStore local = new FileIncidentStore(directory.resolve("local"))) {
			MainFrame standalone = frame(local, 'S');
			assertTrue(enabled(standalone, "New…"));
			assertTrue(enabled(standalone, "Duplicate Incident"));
			assertTrue(enabled(standalone, "Import Incident JSON…"));

			RemoteIncidentStore remote = host.pair();
			MainFrame connected = connected(host, remote);
			for (String text : List.of("New…", "Duplicate Incident", "Import Incident JSON…")) {
				assertFalse(enabled(connected, text), text + " must be disabled for a store that cannot do it");
				assertNotNull(tooltip(connected, text), text + " must explain why it is disabled");
			}
		}
	}

	@Test
	void connectingThroughASlowInvitationRedeemerNeverBlocksTheEventThread() throws Exception {
		try (HostedIncidentFixture host = new HostedIncidentFixture(directory.resolve("host"), "Shared");
				FileIncidentStore local = new FileIncidentStore(directory.resolve("local"))) {
			MainFrame owner = frame(local, 'S');
			HostedIncidentFixture.SlowGateway[] slow = new HostedIncidentFixture.SlowGateway[1];
			AtomicBoolean redeemedOnEventThread = new AtomicBoolean();
			SwingUtilities.invokeAndWait(() -> owner.setInvitationRedeemer((invitation, name) -> {
				redeemedOnEventThread.set(SwingUtilities.isEventDispatchThread());
				return host.pairThrough(real -> slow[0] = new HostedIncidentFixture.SlowGateway(real));
			}));
			SwingUtilities.invokeAndWait(() -> owner.connectWithInvitation("ignored", "device"));
			waitFor(() -> slow[0] != null);
			slow[0].arm();
			for (int ping = 0; ping < 5; ping++) {
				assertTimeoutPreemptively(java.time.Duration.ofSeconds(5), () -> SwingUtilities.invokeAndWait(() -> { }));
			}
			slow[0].release();
			waitFor(() -> connectedWindow(owner) != null);
			assertFalse(redeemedOnEventThread.get());
			assertEquals(0, slow[0].callsOnEventThread());
			MainFrame connected = connectedWindow(owner);
			assertFalse(enabled(connected, "New…"));
		}
	}

	@Test
	void statusLineShowsAuthorityConnectionLastConfirmedAndANotifiedStop() throws Exception {
		try (HostedIncidentFixture host = new HostedIncidentFixture(directory.resolve("host"), "Shared")) {
			MainFrame connected = connected(host, host.pair());
			waitFor(() -> text(connected).contains("Connected to") && text(connected).contains("last confirmed")
					&& text(connected).contains("CONNECTED"));
			host.session().stop();
			waitFor(() -> text(connected).contains("HOST_STOPPED"));
			assertTrue(text(connected).contains("announced that it stopped sharing"), text(connected));
			assertFalse(text(connected).contains("LOST_CONTACT"));
		}
	}

	@Test
	void anUnexplainedLossOfContactIsNotReportedAsAHostStop() throws Exception {
		try (HostedIncidentFixture host = new HostedIncidentFixture(directory.resolve("host"), "Shared")) {
			AtomicBoolean down = new AtomicBoolean();
			RemoteIncidentStore remote = host.pairThrough(real -> (RemoteIncidentGateway) Proxy.newProxyInstance(
					RemoteIncidentGateway.class.getClassLoader(), new Class<?>[] {RemoteIncidentGateway.class},
					(proxy, method, args) -> {
						if (down.get() && !method.getName().equals("close") && !method.getName().equals("location")) {
							throw new StoreUnavailableException("The network is down", null);
						}
						try {
							return method.invoke(real, args);
						} catch (InvocationTargetException failure) {
							throw failure.getCause();
						}
					}));
			MainFrame connected = connected(host, remote);
			waitFor(() -> text(connected).contains("CONNECTED"));
			down.set(true);
			waitFor(() -> text(connected).contains("LOST_CONTACT"));
			assertFalse(text(connected).contains("HOST_STOPPED"), text(connected));
			assertTrue(text(connected).contains("last confirmed"), text(connected));
		}
	}

	@Test
	void newerHostChangesAreAnnouncedWhileTheOperatorsDraftIsKept() throws Exception {
		try (HostedIncidentFixture host = new HostedIncidentFixture(directory.resolve("host"), "Shared")) {
			MainFrame connected = connected(host, host.pair());
			AppController controller = (AppController) field(connected, "controller");
			waitFor(() -> text(connected).contains("CONNECTED"));
			SwingUtilities.invokeAndWait(() -> {
				controller.getData().getIncidentContext().setIncidentName("Operator draft");
				controller.markDirty();
			});
			RemoteIncidentStore other = host.pair();
			StoredIncident theirs = other.loadSnapshot(host.incident());
			theirs.data().getIncidentContext().setIncidentName("Their change");
			other.save(host.incident(), theirs.data(), theirs.revision());

			waitFor(() -> text(connected).contains("newer changes on the host; your draft is kept"));
			assertEquals("Operator draft", controller.getData().getIncidentContext().getIncidentName());
			assertTrue(controller.isDirty());
		}
	}

	@Test
	void hostWindowSwitchesToTheLoopbackStoreWhileSharingAndBackWhenSharingStops() throws Exception {
		AtomicBoolean delayLocalLoad = new AtomicBoolean();
		CountDownLatch loadStarted = new CountDownLatch(1);
		CountDownLatch releaseLoad = new CountDownLatch(1);
		try (FileIncidentStore local = new FileIncidentStore(directory.resolve("local")) {
			/**
			 * Delays the local snapshot load when the test is checking the stop transition.
			 *
			 * @param id incident to load
			 * @return the local snapshot
			 */
			@Override
			public StoredIncident loadSnapshot(IncidentId id) {
				if (delayLocalLoad.get()) {
					loadStarted.countDown();
					try {
						if (!releaseLoad.await(30, TimeUnit.SECONDS)) {
							throw new IllegalStateException("Timed out waiting to restore the local store");
						}
					} catch (InterruptedException interrupted) {
						Thread.currentThread().interrupt();
						throw new IllegalStateException(interrupted);
					}
				}
				return super.loadSnapshot(id);
			}
		}) {
			MainFrame frame = frame(local, 'H');
			AppController controller = (AppController) field(frame, "controller");
			assertEquals("Standalone", text(frame));
			AtomicReference<HostSharingDialog> dialogHolder = new AtomicReference<>();
			SwingUtilities.invokeAndWait(() -> {
				JMenu sharing = menu(frame, "Sharing");
				sharing.getItem(1).doClick();
				for (Window window : Window.getWindows()) {
					if (window instanceof HostSharingDialog found && found.isVisible()) {
						dialogHolder.set(found);
					}
				}
				dialogHolder.get().configure("127.0.0.1", 0, 0);
				dialogHolder.get().startSharing();
			});
			HostSharingDialog dialog = dialogHolder.get();
			waitFor(() -> dialog.isSharing() && text(frame).startsWith("Host"));
			assertTrue(controller.getStore() instanceof RemoteIncidentStore);
			assertFalse(enabled(frame, "New…"));
			assertFalse(enabled(frame, "Duplicate Incident"));
			assertFalse(enabled(frame, "Import Incident JSON…"));

			CountDownLatch stopped = new CountDownLatch(1);
			delayLocalLoad.set(true);
			try {
				SwingUtilities.invokeAndWait(() -> dialog.stopAsync(stopped::countDown));
				assertTrue(loadStarted.await(30, TimeUnit.SECONDS), "local store load did not start");
				assertFalse("Standalone".equals(text(frame)));
			} finally {
				releaseLoad.countDown();
			}
			assertTrue(stopped.await(30, TimeUnit.SECONDS), "sharing did not stop");
			SwingUtilities.invokeAndWait(() -> {
				assertFalse(dialog.isSharing());
				assertEquals(local, controller.getStore());
				assertEquals("Standalone", frame.connectionStatusText());
			});
			assertTrue(enabled(frame, "New…"));
			assertTrue(enabled(frame, "Duplicate Incident"));
			assertTrue(enabled(frame, "Import Incident JSON…"));
			SwingUtilities.invokeAndWait(dialog::dispose);
		}
	}

	@Test
	void aRemoteEditRefreshesACleanConnectedViewWithoutLosingTheSelectedTab() throws Exception {
		try (HostedIncidentFixture host = new HostedIncidentFixture(directory.resolve("host"), "Shared")) {
			MainFrame connected = connected(host, host.pair());
			javax.swing.JTabbedPane tabs = (javax.swing.JTabbedPane) field(connected, "tabs");
			AtomicInteger selected = new AtomicInteger();
			SwingUtilities.invokeAndWait(() -> {
				tabs.setSelectedIndex(tabs.getTabCount() - 1);
				selected.set(tabs.getSelectedIndex());
			});
			AppController controller = (AppController) field(connected, "controller");
			RemoteIncidentStore other = host.pair();
			StoredIncident theirs = other.loadSnapshot(host.incident());
			theirs.data().getIncidentContext().setIncidentName("Changed remotely");
			other.save(host.incident(), theirs.data(), theirs.revision());

			waitFor(() -> "Changed remotely".equals(controller.getData().getIncidentContext().getIncidentName()));
			AtomicInteger after = new AtomicInteger();
			SwingUtilities.invokeAndWait(() -> after.set(tabs.getSelectedIndex()));
			assertEquals(selected.get(), after.get());
			assertFalse(controller.isDirty());
			assertFalse(text(connected).contains("your draft is kept"));
		}
	}

	/**
	 * Opens a standalone window.
	 *
	 * @param store file store holding the incident
	 * @param marker character making the incident name distinct
	 * @return the window
	 * @throws Exception when creation fails
	 */
	private MainFrame frame(FileIncidentStore store, char marker) throws Exception {
		AppData data = new AppData();
		data.getIncidentContext().setIncidentName("Local " + marker);
		IncidentId id = store.create(data);
		AppData loaded = store.load(id);
		AtomicReference<MainFrame> holder = new AtomicReference<>();
		SwingUtilities.invokeAndWait(() -> {
			MainFrame frame = new MainFrame(loaded, store, id, new PdfExportService(), new IncidentValidator(),
					directory);
			holder.set(frame);
		});
		frames.add(holder.get());
		return holder.get();
	}

	/**
	 * Opens a connected window the way the connect flow does.
	 *
	 * @param host fixture sharing the incident
	 * @param remote paired remote store
	 * @return the window with its monitor attached
	 * @throws Exception when creation fails
	 */
	private MainFrame connected(HostedIncidentFixture host, RemoteIncidentStore remote) throws Exception {
		StoredIncident first = remote.loadSnapshot(host.incident());
		AtomicReference<MainFrame> holder = new AtomicReference<>();
		SwingUtilities.invokeAndWait(() -> {
			MainFrame frame = new MainFrame(first.data(), remote, host.incident(), new PdfExportService(),
					new IncidentValidator(), directory);
			frame.attachMonitor(remote, "Connected to " + remote.describe().location());
			holder.set(frame);
		});
		frames.add(holder.get());
		return holder.get();
	}

	/**
	 * Finds a connected window opened by the connect flow.
	 *
	 * @param owner the window that started the flow
	 * @return the new window or null
	 */
	private static MainFrame connectedWindow(MainFrame owner) {
		for (Window window : Window.getWindows()) {
			if (window instanceof MainFrame frame && frame != owner && frame.isDisplayable()) {
				return frame;
			}
		}
		return null;
	}

	/**
	 * Reads the status line on the event thread.
	 *
	 * @param frame window
	 * @return status text
	 */
	private static String text(MainFrame frame) {
		AtomicReference<String> text = new AtomicReference<>();
		try {
			SwingUtilities.invokeAndWait(() -> text.set(frame.connectionStatusText()));
		} catch (InterruptedException | InvocationTargetException failure) {
			throw new AssertionError(failure);
		}
		return text.get();
	}

	/**
	 * Finds a menu by title.
	 *
	 * @param frame window
	 * @param title menu title
	 * @return menu
	 */
	private static JMenu menu(MainFrame frame, String title) {
		for (int i = 0; i < frame.getJMenuBar().getMenuCount(); i++) {
			if (title.equals(frame.getJMenuBar().getMenu(i).getText())) {
				return frame.getJMenuBar().getMenu(i);
			}
		}
		throw new AssertionError("No menu " + title);
	}

	/**
	 * Finds a menu item by its text in any menu.
	 *
	 * @param frame window
	 * @param text item text
	 * @return the item
	 */
	private static JMenuItem item(MainFrame frame, String text) {
		for (int i = 0; i < frame.getJMenuBar().getMenuCount(); i++) {
			JMenu menu = frame.getJMenuBar().getMenu(i);
			for (int j = 0; j < menu.getItemCount(); j++) {
				JMenuItem candidate = menu.getItem(j);
				if (candidate != null && text.equals(candidate.getText())) {
					return candidate;
				}
			}
		}
		throw new AssertionError("No menu item " + text);
	}

	/**
	 * Reads whether a menu item is enabled.
	 *
	 * @param frame window
	 * @param text item text
	 * @return whether it is enabled
	 * @throws Exception when the event thread cannot be reached
	 */
	private static boolean enabled(MainFrame frame, String text) throws Exception {
		AtomicBoolean enabled = new AtomicBoolean();
		SwingUtilities.invokeAndWait(() -> enabled.set(item(frame, text).isEnabled()));
		return enabled.get();
	}

	/**
	 * Reads a menu item's tooltip.
	 *
	 * @param frame window
	 * @param text item text
	 * @return the tooltip or null
	 * @throws Exception when the event thread cannot be reached
	 */
	private static String tooltip(MainFrame frame, String text) throws Exception {
		AtomicReference<String> tip = new AtomicReference<>();
		SwingUtilities.invokeAndWait(() -> tip.set(item(frame, text).getToolTipText()));
		return tip.get();
	}

	/**
	 * Waits for a condition.
	 *
	 * @param condition condition to await
	 * @throws InterruptedException when interrupted
	 */
	private static void waitFor(BooleanSupplier condition) throws InterruptedException {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
		while (!condition.getAsBoolean()) {
			assertTrue(System.nanoTime() < deadline, "condition not reached");
			Thread.sleep(25);
		}
	}

	/**
	 * Reads a private field reflectively.
	 *
	 * @param target object to read
	 * @param name field name
	 * @return field value
	 */
	private static Object field(Object target, String name) {
		try {
			Field field = target.getClass().getDeclaredField(name);
			field.setAccessible(true);
			return field.get(target);
		} catch (ReflectiveOperationException failure) {
			throw new AssertionError(failure);
		}
	}
}
