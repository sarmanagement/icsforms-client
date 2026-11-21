/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.host;

import org.sarmanagement.icsforms.persistence.IncidentId;
import org.sarmanagement.icsforms.persistence.IncidentService;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * State shared by a session's HTTP handlers.
 */
final class HostContext {
	private static final Duration RATE_WINDOW = Duration.ofMinutes(1);
	final HostConfig config;
	final HostAuthority authority;
	final DeviceRegistry registry;
	final IncidentService service;
	final IncidentId incident;
	final AtomicReference<HostState> state;
	final AtomicReference<String> failure = new AtomicReference<>();
	final AtomicInteger inFlight = new AtomicInteger();
	final RateLimiter pairingPerAddress;
	final RateLimiter pairingGlobal;
	final RateLimiter statusPerAddress;
	private volatile Instant periodClose;
	private volatile int port;
	private volatile int enrollmentPort;

	/**
	 * Creates the shared context.
	 *
	 * @param config host configuration
	 * @param authority host authority
	 * @param registry device registry
	 * @param service incident service exposed to devices
	 * @param incident the one shared incident
	 * @param state lifecycle state holder owned by the session
	 */
	HostContext(HostConfig config, HostAuthority authority, DeviceRegistry registry, IncidentService service,
			IncidentId incident, AtomicReference<HostState> state) {
		this.config = config;
		this.authority = authority;
		this.registry = registry;
		this.service = service;
		this.incident = incident;
		this.state = state;
		this.pairingPerAddress = new RateLimiter(config.pairingAttemptsPerAddress(), RATE_WINDOW, config.clock());
		this.pairingGlobal = new RateLimiter(config.pairingAttemptsGlobal(), RATE_WINDOW, config.clock());
		this.statusPerAddress = new RateLimiter(config.statusRequestsPerAddress(), RATE_WINDOW, config.clock());
	}

	/**
	 * Returns the time at which the operational period closes, which caps new credentials.
	 *
	 * @return cutoff, or null when none is set
	 */
	Instant periodClose() {
		return periodClose;
	}

	/**
	 * Sets the period close cutoff for credentials issued from now on.
	 *
	 * @param cutoff cutoff time, or null for none
	 */
	void setPeriodClose(Instant cutoff) {
		this.periodClose = cutoff;
	}

	/**
	 * Returns the bound mutual-TLS port.
	 *
	 * @return port
	 */
	int port() {
		return port;
	}

	/**
	 * Returns the bound enrollment port.
	 *
	 * @return port
	 */
	int enrollmentPort() {
		return enrollmentPort;
	}

	/**
	 * Records the ports that were actually bound.
	 *
	 * @param mtls mutual-TLS port
	 * @param enrollment enrollment port
	 */
	void bound(int mtls, int enrollment) {
		this.port = mtls;
		this.enrollmentPort = enrollment;
	}

	/**
	 * Notes a failure for display without revealing it to remote callers.
	 *
	 * @param message operator-readable failure text
	 */
	void noteFailure(String message) {
		failure.set(message);
	}

	/**
	 * Marks the whole service as failed after an unrecoverable error.
	 *
	 * @param message operator-readable failure text
	 */
	void fail(String message) {
		failure.set(message);
		state.updateAndGet(current -> current == HostState.RUNNING ? HostState.FAILED : current);
	}
}
