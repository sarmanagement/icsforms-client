/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.pdf.PdfExportService;
import org.sarmanagement.icsforms.persistence.ConnectionMonitor;
import org.sarmanagement.icsforms.persistence.RemoteIncidentStore;
import org.sarmanagement.icsforms.persistence.StoredIncident;
import org.sarmanagement.icsforms.validation.IncidentValidator;

import javax.swing.SwingUtilities;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves that network I/O never runs on, or blocks, the event-dispatch thread. A real host is reached through a
 * gateway that blocks on a latch, wrapped by the real {@link RemoteIncidentStore}, {@link AppController} and
 * {@link ConnectionMonitor}; while each call is blocked the event thread must keep servicing
 * {@code invokeAndWait} pings and the gateway must record no call on it.
 */
class SlowGatewayEventThreadTest {
	@TempDir
	Path directory;

	@Test
	void saveRefreshSwitchStoreAndMonitorPollingNeverBlockOrRunOnTheEventThread() throws Exception {
		String previous = System.getProperty(ConnectionMonitor.POLL_INTERVAL_PROPERTY);
		System.setProperty(ConnectionMonitor.POLL_INTERVAL_PROPERTY, "50");
		ExecutorService worker = Executors.newCachedThreadPool();
		try (HostedIncidentFixture host = new HostedIncidentFixture(directory, "Slow transport")) {
			HostedIncidentFixture.SlowGateway[] slowRef = new HostedIncidentFixture.SlowGateway[1];
			RemoteIncidentStore store = host.pairThrough(real -> slowRef[0] = new HostedIncidentFixture.SlowGateway(
					real));
			HostedIncidentFixture.SlowGateway slow = slowRef[0];
			StoredIncident first = store.loadSnapshot(host.incident());
			AppController controller = SwingTestLifecycleExtension.track(new AppController(first.data(), store,
					host.incident(), new PdfExportService(), new IncidentValidator()));

			controller.getData().getIncidentContext().setIncidentName("Saved slowly");
			controller.markDirty();
			whileBlocked(slow, worker, true, controller::save);
			assertFalse(controller.isDirty());
			assertEquals("Saved slowly", host.backing().load(host.incident()).getIncidentContext().getIncidentName());

			RemoteIncidentStore other = host.pair();
			StoredIncident theirs = other.loadSnapshot(host.incident());
			theirs.data().getIncidentContext().setIncidentName("Changed by another device");
			other.save(host.incident(), theirs.data(), theirs.revision());
			whileBlocked(slow, worker, true, controller::refreshFromRemote);
			assertEquals("Changed by another device", controller.getData().getIncidentContext().getIncidentName());

			RemoteIncidentStore switched = host.pairThrough(real -> slowRef[0] = new HostedIncidentFixture.SlowGateway(
					real));
			HostedIncidentFixture.SlowGateway slowSwitch = slowRef[0];
			whileBlocked(slowSwitch, worker, true, () -> {
				try {
					SwingUtilities.invokeAndWait(() -> controller.switchStore(switched, host.incident()));
				} catch (Exception failure) {
					throw new IllegalStateException(failure);
				}
			});
			assertEquals(switched, controller.getStore());

			HostedIncidentFixture.SlowGateway slowPoll = new HostedIncidentFixture.SlowGateway(host.pair().gateway());
			try (ConnectionMonitor monitor = ConnectionMonitor.withDefaults(slowPoll, host.incident(), 0,
					cursor -> true, status -> { })) {
				whileBlocked(slowPoll, worker, false, monitor::start);
				waitFor(() -> monitor.status().state() == ConnectionMonitor.State.CONNECTED);
			}

			assertEquals(0, slow.callsOnEventThread(), "save/refresh gateway calls on the event thread");
			assertEquals(0, slowSwitch.callsOnEventThread(), "switch gateway calls on the event thread");
			assertEquals(0, slowPoll.callsOnEventThread(), "poll gateway calls on the event thread");
			assertTrue(slow.calls() > 0 && slowSwitch.calls() > 0 && slowPoll.calls() > 0);
		} finally {
			worker.shutdownNow();
			if (previous == null) {
				System.clearProperty(ConnectionMonitor.POLL_INTERVAL_PROPERTY);
			} else {
				System.setProperty(ConnectionMonitor.POLL_INTERVAL_PROPERTY, previous);
			}
		}
	}

	/**
	 * Runs an action on a worker while the gateway blocks, asserting that the event thread answers pings
	 * throughout, then releases the gateway and waits for the action to finish.
	 *
	 * @param slow gateway to block
	 * @param worker executor for the action
	 * @param actionBlocks whether the action itself waits for the gateway (false for a background poller)
	 * @param action action that talks to the gateway
	 * @throws Exception when the action fails or the event thread stops responding
	 */
	private static void whileBlocked(HostedIncidentFixture.SlowGateway slow, ExecutorService worker, boolean actionBlocks,
			Runnable action)
			throws Exception {
		int before = slow.calls();
		slow.arm();
		Future<?> running = worker.submit(action);
		waitFor(() -> slow.calls() > before);
		for (int ping = 0; ping < 5; ping++) {
			assertTimeoutPreemptively(Duration.ofSeconds(5), () -> SwingUtilities.invokeAndWait(() -> { }),
					"event thread stopped servicing events while the host call was blocked");
			if (actionBlocks) {
				assertFalse(running.isDone(), "the action finished although the host call was blocked");
			}
			Thread.sleep(20);
		}
		slow.release();
		running.get(30, TimeUnit.SECONDS);
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
			Thread.sleep(10);
		}
	}
}
