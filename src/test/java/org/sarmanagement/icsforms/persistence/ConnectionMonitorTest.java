/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.persistence;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies connection states, last-confirmed semantics, stale detection and change offering. */
class ConnectionMonitorTest {
	private static final IncidentId INCIDENT = IncidentId.newId();

	@Test
	void pollingConfirmsAndOffersNewCursorsUntilHandled() {
		FakeGateway gateway = new FakeGateway();
		MutableClock clock = new MutableClock();
		List<Long> offered = new ArrayList<>();
		AtomicBoolean accept = new AtomicBoolean();
		try (ConnectionMonitor monitor = monitor(gateway, clock, cursor -> {
			offered.add(cursor);
			return accept.get();
		}, new ArrayList<>())) {
			assertEquals(ConnectionMonitor.State.CONNECTING, monitor.status().state());
			assertNull(monitor.status().lastConfirmed());
			gateway.cursor = 3;
			monitor.pollOnce();
			assertEquals(ConnectionMonitor.State.CONNECTED, monitor.status().state());
			assertEquals(clock.instant(), monitor.status().lastConfirmed());
			monitor.pollOnce();
			accept.set(true);
			monitor.pollOnce();
			monitor.pollOnce();
			assertEquals(List.of(3L, 3L, 3L), offered);
		}
	}

	@Test
	void staleAfterThresholdWithoutAnyConfirmationOfFreshness() {
		FakeGateway gateway = new FakeGateway();
		MutableClock clock = new MutableClock();
		try (ConnectionMonitor monitor = monitor(gateway, clock, cursor -> true, new ArrayList<>())) {
			monitor.pollOnce();
			clock.advance(Duration.ofSeconds(29));
			assertEquals(ConnectionMonitor.State.CONNECTED, monitor.status().state());
			clock.advance(Duration.ofSeconds(2));
			assertEquals(ConnectionMonitor.State.STALE, monitor.status().state());
			assertTrue(monitor.maximumStaleDetectionDelay().compareTo(Duration.ofSeconds(30)) >= 0);
			monitor.pollOnce();
			assertEquals(ConnectionMonitor.State.CONNECTED, monitor.status().state());
		}
	}

	@Test
	void unexplainedLossIsDistinguishedFromNotifiedStop() {
		FakeGateway gateway = new FakeGateway();
		MutableClock clock = new MutableClock();
		List<ConnectionMonitor.Status> seen = new ArrayList<>();
		try (ConnectionMonitor monitor = monitor(gateway, clock, cursor -> true, seen)) {
			monitor.pollOnce();
			Instant confirmed = monitor.status().lastConfirmed();
			clock.advance(Duration.ofSeconds(1));
			gateway.failure = new StoreUnavailableException("down", null);
			gateway.probeFails = true;
			monitor.pollOnce();
			assertEquals(ConnectionMonitor.State.LOST_CONTACT, monitor.status().state());
			assertEquals(confirmed, monitor.status().lastConfirmed());

			gateway.probeFails = false;
			gateway.probe = RemoteIncidentGateway.HostProbe.STOPPING;
			monitor.pollOnce();
			assertEquals(ConnectionMonitor.State.HOST_STOPPED, monitor.status().state());

			gateway.failure = new RemoteAccessException(RemoteAccessException.Reason.CREDENTIAL_REVOKED, "revoked",
					null);
			monitor.pollOnce();
			assertEquals(ConnectionMonitor.State.ACCESS_LOST, monitor.status().state());
			assertEquals(RemoteAccessException.Reason.CREDENTIAL_REVOKED, monitor.status().reason());

			gateway.failure = null;
			monitor.pollOnce();
			assertEquals(ConnectionMonitor.State.CONNECTED, monitor.status().state());
			assertTrue(seen.size() >= 4);
		}
	}

	@Test
	void closedMonitorStopsPollingAndConfigurationIsValidated() {
		FakeGateway gateway = new FakeGateway();
		ConnectionMonitor monitor = monitor(gateway, new MutableClock(), cursor -> true, new ArrayList<>());
		monitor.close();
		monitor.pollOnce();
		assertEquals(0, gateway.polls.get());
		assertEquals(ConnectionMonitor.State.CLOSED, monitor.status().state());
		assertThrows(IllegalArgumentException.class, () -> new ConnectionMonitor(gateway, INCIDENT, 0,
				Duration.ZERO, Duration.ofSeconds(1), Clock.systemUTC(), cursor -> true, status -> { }));
	}

	@Test
	void scheduledPollingDetectsChangeWithinConfiguredInterval() throws Exception {
		FakeGateway gateway = new FakeGateway();
		gateway.cursor = 9;
		CountDownLatch changed = new CountDownLatch(1);
		try (ConnectionMonitor monitor = new ConnectionMonitor(gateway, INCIDENT, 0, Duration.ofMillis(20),
				Duration.ofMillis(200), Clock.systemUTC(), cursor -> {
					changed.countDown();
					return true;
				}, status -> { })) {
			monitor.start();
			assertTrue(changed.await(5, TimeUnit.SECONDS));
			assertEquals(Duration.ofMillis(20), monitor.pollInterval());
		}
	}

	/**
	 * Builds a monitor with a 5 second interval and 30 second stale threshold.
	 *
	 * @param gateway fake transport
	 * @param clock mutable clock
	 * @param handler change handler
	 * @param seen sink for published statuses
	 * @return monitor
	 */
	private static ConnectionMonitor monitor(FakeGateway gateway, MutableClock clock,
			ConnectionMonitor.ChangeHandler handler, List<ConnectionMonitor.Status> seen) {
		return new ConnectionMonitor(gateway, INCIDENT, 0, Duration.ofSeconds(5), Duration.ofSeconds(30), clock,
				handler, seen::add);
	}

	/** Adjustable clock. */
	private static final class MutableClock extends Clock {
		private Instant now = Instant.parse("2026-01-01T00:00:00Z");

		/**
		 * Moves time forward.
		 *
		 * @param by amount to add
		 */
		void advance(Duration by) {
			now = now.plus(by);
		}

		/** {@inheritDoc} */
		@Override
		public java.time.ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		/** {@inheritDoc} */
		@Override
		public Clock withZone(java.time.ZoneId zone) {
			return this;
		}

		/** {@inheritDoc} */
		@Override
		public Instant instant() {
			return now;
		}
	}

	/** Scriptable gateway. */
	private static final class FakeGateway implements RemoteIncidentGateway {
		private final AtomicLong polls = new AtomicLong();
		private volatile long cursor;
		private volatile RuntimeException failure;
		private volatile boolean probeFails;
		private volatile HostProbe probe = HostProbe.RUNNING;

		/** {@inheritDoc} */
		@Override
		public String location() {
			return "fake";
		}

		/** {@inheritDoc} */
		@Override
		public IncidentService.ReadResult read(IncidentId incident) {
			throw new UnsupportedOperationException();
		}

		/** {@inheritDoc} */
		@Override
		public IncidentService.ChangeResult changesSince(IncidentId incident, long after) {
			polls.incrementAndGet();
			if (failure != null) {
				throw failure;
			}
			return new IncidentService.ChangeResult(cursor, List.of());
		}

		/** {@inheritDoc} */
		@Override
		public IncidentService.SubmissionResult submit(IncidentService.RecordBatch batch) {
			throw new UnsupportedOperationException();
		}

		/** {@inheritDoc} */
		@Override
		public HostProbe probe() {
			if (probeFails) {
				throw new StoreUnavailableException("unreachable", null);
			}
			return probe;
		}

		/** {@inheritDoc} */
		@Override
		public void close() {
			// nothing to release
		}
	}
}
