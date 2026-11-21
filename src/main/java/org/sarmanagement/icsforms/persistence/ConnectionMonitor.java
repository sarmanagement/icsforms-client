/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.persistence;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Polls a host's committed cursor on a daemon thread and reports connection state. It never runs on the Swing
 * event-dispatch thread and never compares device clocks with incident data: the cursor is an opaque committed
 * position and "last confirmed" is the local time of the last successful poll, used for display and staleness only.
 *
 * <p><b>Semantics.</b> A poll is a conditional read of the changes after the last cursor the handler accepted. A
 * successful poll confirms contact. A poll that observes a newer cursor calls the {@link ChangeHandler}; the
 * cursor only advances when the handler accepts, so a view that kept an operator's draft is offered the change
 * again on every poll until it can be applied. The state is {@link State#STALE} when the last confirmation is older
 * than the stale threshold even if the next poll is still waiting on a slow network.
 *
 * <p><b>Detection delay.</b> Lost contact is detected at the first poll that fails, which is at most one poll
 * interval plus the transport's request timeout after contact is lost. Staleness is raised by a separate check at
 * most {@link #maximumStaleDetectionDelay()} after the last confirmation, independent of how long a request hangs.
 * A client cannot tell a network failure from a host crash, so both are {@link State#LOST_CONTACT}; only a host
 * that announced it is stopping yields {@link State#HOST_STOPPED}, and that state is retained after the host's
 * listeners close.
 */
public final class ConnectionMonitor implements AutoCloseable {
	/** System property overriding the default poll interval, in milliseconds. */
	public static final String POLL_INTERVAL_PROPERTY = "icsforms.remote.pollIntervalMillis";
	/** System property overriding the default stale threshold, in milliseconds. */
	public static final String STALE_AFTER_PROPERTY = "icsforms.remote.staleAfterMillis";
	/** Default interval between polls. */
	public static final Duration DEFAULT_POLL_INTERVAL = Duration.ofSeconds(5);
	private static final Logger LOGGER = Logger.getLogger(ConnectionMonitor.class.getName());

	/**
	 * Connection state of a connected view.
	 */
	public enum State {
		/** No poll has completed yet. */
		CONNECTING,
		/** The last poll succeeded recently. */
		CONNECTED,
		/** No poll has succeeded within the stale threshold. */
		STALE,
		/** The last poll failed for an unexplained reason: network failure or host crash. */
		LOST_CONTACT,
		/** The host announced that it is stopping. */
		HOST_STOPPED,
		/** The host rejected the credential or its identity changed. */
		ACCESS_LOST,
		/** The monitor was closed. */
		CLOSED
	}

	/**
	 * Immutable connection status.
	 *
	 * @param state connection state
	 * @param lastConfirmed local time of the last successful poll, or null when none succeeded
	 * @param detail operator-facing explanation, never containing secrets or file paths
	 * @param reason access failure reason when {@code state} is {@link State#ACCESS_LOST}, otherwise null
	 */
	public record Status(State state, Instant lastConfirmed, String detail,
			RemoteAccessException.Reason reason) {
	}

	/**
	 * Receives status changes on the monitor thread.
	 */
	@FunctionalInterface
	public interface Listener {
		/**
		 * Reports the current status after each poll and whenever staleness changes.
		 *
		 * @param status current status
		 */
		void statusChanged(Status status);
	}

	/**
	 * Applies newer remote state on the monitor thread, which may block on network I/O.
	 */
	@FunctionalInterface
	public interface ChangeHandler {
		/**
		 * Reacts to a newer committed cursor.
		 *
		 * @param cursor newer committed cursor
		 * @return true when the change was applied or acknowledged; false to be offered it again at the next poll
		 */
		boolean remoteChanged(long cursor);
	}

	private final RemoteIncidentGateway gateway;
	private final IncidentId incident;
	private final Duration pollInterval;
	private final Duration staleAfter;
	private final Clock clock;
	private final ChangeHandler handler;
	private final Listener listener;
	private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, task -> {
		Thread thread = new Thread(task, "remote-connection-monitor");
		thread.setDaemon(true);
		return thread;
	});
	private long cursor;
	private volatile State state = State.CONNECTING;
	private volatile Instant lastConfirmed;
	private volatile String detail = "Connecting";
	private volatile RemoteAccessException.Reason reason;
	private volatile boolean stopNotified;
	private volatile boolean closed;
	private volatile State lastPublished;

	/**
	 * Creates a monitor with the default interval and the interval-derived threshold, honouring the system
	 * properties {@value #POLL_INTERVAL_PROPERTY} and {@value #STALE_AFTER_PROPERTY}.
	 *
	 * @param gateway transport to the host
	 * @param incident shared incident
	 * @param initialCursor committed cursor of the snapshot already held
	 * @param handler change handler
	 * @param listener status listener
	 * @return an unstarted monitor
	 */
	public static ConnectionMonitor withDefaults(RemoteIncidentGateway gateway, IncidentId incident,
			long initialCursor, ChangeHandler handler, Listener listener) {
		Duration interval = millisProperty(POLL_INTERVAL_PROPERTY, DEFAULT_POLL_INTERVAL);
		Duration stale = millisProperty(STALE_AFTER_PROPERTY, interval.multipliedBy(3));
		return new ConnectionMonitor(gateway, incident, initialCursor, interval, stale, Clock.systemUTC(), handler,
				listener);
	}

	/**
	 * Reads a positive millisecond system property.
	 *
	 * @param name property name
	 * @param fallback value when unset or invalid
	 * @return configured duration
	 */
	private static Duration millisProperty(String name, Duration fallback) {
		try {
			long millis = Long.parseLong(System.getProperty(name, ""));
			return millis > 0 ? Duration.ofMillis(millis) : fallback;
		} catch (NumberFormatException failure) {
			return fallback;
		}
	}

	/**
	 * Creates a monitor.
	 *
	 * @param gateway transport to the host
	 * @param incident shared incident
	 * @param initialCursor committed cursor of the snapshot already held
	 * @param pollInterval delay between polls
	 * @param staleAfter age of the last confirmation after which the view is stale
	 * @param clock local clock used only for display and staleness
	 * @param handler change handler
	 * @param listener status listener
	 */
	public ConnectionMonitor(RemoteIncidentGateway gateway, IncidentId incident, long initialCursor,
			Duration pollInterval, Duration staleAfter, Clock clock, ChangeHandler handler, Listener listener) {
		this.gateway = Objects.requireNonNull(gateway, "gateway");
		this.incident = Objects.requireNonNull(incident, "incident");
		this.cursor = initialCursor;
		this.pollInterval = Objects.requireNonNull(pollInterval, "pollInterval");
		this.staleAfter = Objects.requireNonNull(staleAfter, "staleAfter");
		this.clock = Objects.requireNonNull(clock, "clock");
		this.handler = Objects.requireNonNull(handler, "handler");
		this.listener = Objects.requireNonNull(listener, "listener");
		if (pollInterval.isZero() || pollInterval.isNegative() || staleAfter.isZero() || staleAfter.isNegative()) {
			throw new IllegalArgumentException("Polling intervals must be positive");
		}
	}

	/**
	 * Starts polling immediately and then at the configured interval.
	 */
	public void start() {
		scheduler.scheduleWithFixedDelay(this::pollOnce, 0, pollInterval.toMillis(), TimeUnit.MILLISECONDS);
		long check = Math.max(1, Math.min(pollInterval.toMillis(), staleAfter.toMillis()) / 2);
		scheduler.scheduleWithFixedDelay(this::checkStale, check, check, TimeUnit.MILLISECONDS);
	}

	/**
	 * Returns the configured poll interval.
	 *
	 * @return interval between polls
	 */
	public Duration pollInterval() {
		return pollInterval;
	}

	/**
	 * Returns the longest time after the last confirmed contact before the view is reported stale, regardless of
	 * how long an individual request hangs.
	 *
	 * @return stale threshold plus the staleness check period
	 */
	public Duration maximumStaleDetectionDelay() {
		long check = Math.max(1, Math.min(pollInterval.toMillis(), staleAfter.toMillis()) / 2);
		return staleAfter.plusMillis(check);
	}

	/**
	 * Returns the current status, applying the stale threshold.
	 *
	 * @return status snapshot
	 */
	public Status status() {
		State current = state;
		Instant confirmed = lastConfirmed;
		if (current == State.CONNECTED && confirmed != null
				&& Duration.between(confirmed, clock.instant()).compareTo(staleAfter) > 0) {
			return new Status(State.STALE, confirmed, "No confirmation from the host recently", null);
		}
		return new Status(current, confirmed, detail, reason);
	}

	/**
	 * Polls once. Public so a view can refresh on demand and tests can drive it deterministically; calls are
	 * serialized.
	 */
	public synchronized void pollOnce() {
		if (closed) {
			return;
		}
		try {
			IncidentService.ChangeResult result = gateway.changesSince(incident, cursor);
			lastConfirmed = clock.instant();
			state = State.CONNECTED;
			detail = "Connected";
			reason = null;
			stopNotified = false;
			publish();
			if (result.cursor() != cursor && handler.remoteChanged(result.cursor())) {
				cursor = result.cursor();
			}
		} catch (RemoteAccessException failure) {
			failed(failure.reason() == RemoteAccessException.Reason.HOST_STOPPING ? State.HOST_STOPPED
					: State.ACCESS_LOST, failure);
		} catch (StoreUnavailableException failure) {
			failed(classifyLoss(), failure);
		} catch (RuntimeException failure) {
			failed(State.LOST_CONTACT, failure);
		}
	}

	/**
	 * Separates a notified host stop from an unexplained loss of contact.
	 *
	 * @return resulting state
	 */
	private State classifyLoss() {
		try {
			if (gateway.probe() == RemoteIncidentGateway.HostProbe.STOPPING) {
				stopNotified = true;
			}
		} catch (RemoteAccessException access) {
			if (access.reason() == RemoteAccessException.Reason.HOST_STOPPING) {
				stopNotified = true;
			} else {
				return State.ACCESS_LOST;
			}
		} catch (RuntimeException unreachable) {
			LOGGER.log(Level.FINE, "Host probe failed: {0}", unreachable.getClass().getSimpleName());
		}
		return stopNotified ? State.HOST_STOPPED : State.LOST_CONTACT;
	}

	/**
	 * Records a failed poll and publishes it.
	 *
	 * @param next resulting state
	 * @param failure failure observed
	 */
	private void failed(State next, RuntimeException failure) {
		state = next;
		if (next == State.HOST_STOPPED) {
			stopNotified = true;
		}
		reason = failure instanceof RemoteAccessException access ? access.reason() : null;
		detail = switch (next) {
			case HOST_STOPPED -> "The host announced that it stopped sharing";
			case ACCESS_LOST -> "The host no longer accepts this device";
			default -> "Contact with the host was lost for an unknown reason";
		};
		LOGGER.log(Level.FINE, "Poll failed: {0}", failure.getClass().getSimpleName());
		publish();
	}

	/**
	 * Raises staleness without waiting for a hanging poll.
	 */
	private void checkStale() {
		if (!closed && status().state() == State.STALE && lastPublished != State.STALE) {
			publish();
		}
	}

	/**
	 * Delivers the current status to the listener, containing listener failures.
	 */
	private void publish() {
		Status current = status();
		lastPublished = current.state();
		try {
			listener.statusChanged(current);
		} catch (RuntimeException failure) {
			LOGGER.log(Level.WARNING, "Connection listener failed: {0}", failure.getClass().getSimpleName());
		}
	}

	/**
	 * Stops polling. Idempotent.
	 */
	@Override
	public void close() {
		closed = true;
		state = State.CLOSED;
		detail = "Disconnected";
		scheduler.shutdownNow();
	}
}
