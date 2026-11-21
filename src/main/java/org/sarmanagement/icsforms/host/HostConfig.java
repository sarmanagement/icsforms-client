/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.host;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;

/**
 * Host configuration.
 *
 * @param bindAddress local address to listen on, for example {@code 0.0.0.0} or {@code 127.0.0.1}
 * @param port mutual-TLS port, or 0 for an ephemeral port
 * @param enrollmentPort pre-authentication pairing and status port, or 0 for an ephemeral port
 * @param advertisedHost host name or IP address that devices use; carried in invitations and certificates
 * @param credentialLifetime default device credential lifetime
 * @param invitationLifetime invitation lifetime
 * @param connectedWindow how recently a device must have made contact to count as connected
 * @param stopGrace how long to keep reporting the stopping state to reachable clients before closing
 * @param drainTimeout how long to wait for in-flight requests before they are explicitly failed
 * @param minimumClientVersion lowest supported client version
 * @param pairingAttemptsPerAddress pairing attempts allowed per remote address per minute
 * @param pairingAttemptsGlobal pairing attempts allowed overall per minute
 * @param statusRequestsPerAddress status requests allowed per remote address per minute
 * @param clock time source
 */
public record HostConfig(String bindAddress, int port, int enrollmentPort, String advertisedHost,
		Duration credentialLifetime, Duration invitationLifetime, Duration connectedWindow, Duration stopGrace,
		Duration drainTimeout, String minimumClientVersion, int pairingAttemptsPerAddress, int pairingAttemptsGlobal,
		int statusRequestsPerAddress, Clock clock) {
	/** Default credential lifetime of 72 hours. */
	public static final Duration DEFAULT_CREDENTIAL_LIFETIME = Duration.ofHours(72);
	/** Largest accepted port number. */
	private static final int MAX_PORT = 65535;

	/**
	 * Validates the configuration.
	 */
	public HostConfig {
		Objects.requireNonNull(bindAddress, "bindAddress");
		Objects.requireNonNull(advertisedHost, "advertisedHost");
		Objects.requireNonNull(clock, "clock");
		if (port < 0 || port > MAX_PORT || enrollmentPort < 0 || enrollmentPort > MAX_PORT) {
			throw new IllegalArgumentException("Port is out of range");
		}
		if (port != 0 && port == enrollmentPort) {
			throw new IllegalArgumentException("The mutual-TLS and enrollment ports must differ");
		}
		if (advertisedHost.isBlank()) {
			throw new IllegalArgumentException("Advertised host is required");
		}
		if (credentialLifetime.isNegative() || credentialLifetime.isZero() || invitationLifetime.isNegative()
				|| invitationLifetime.isZero()) {
			throw new IllegalArgumentException("Lifetimes must be positive");
		}
	}

	/**
	 * Creates the default configuration.
	 *
	 * @param advertisedHost host name or IP address devices will use
	 * @param port mutual-TLS port, or 0 for ephemeral
	 * @param enrollmentPort enrollment port, or 0 for ephemeral
	 * @return configuration with 72-hour credentials, 15-minute invitations and conservative rate limits
	 */
	public static HostConfig defaults(String advertisedHost, int port, int enrollmentPort) {
		return new HostConfig("0.0.0.0", port, enrollmentPort, advertisedHost, DEFAULT_CREDENTIAL_LIFETIME,
				Duration.ofMinutes(15), Duration.ofMinutes(2), Duration.ofSeconds(3), Duration.ofSeconds(10),
				HostProtocol.DEFAULT_MINIMUM_CLIENT_VERSION, 10, 60, 120, Clock.systemUTC());
	}

	/**
	 * Returns a copy bound to a different address.
	 *
	 * @param address local bind address
	 * @return updated configuration
	 */
	public HostConfig withBindAddress(String address) {
		return new HostConfig(address, port, enrollmentPort, advertisedHost, credentialLifetime, invitationLifetime,
				connectedWindow, stopGrace, drainTimeout, minimumClientVersion, pairingAttemptsPerAddress,
				pairingAttemptsGlobal, statusRequestsPerAddress, clock);
	}

	/**
	 * Returns a copy using a different clock, for tests.
	 *
	 * @param newClock time source
	 * @return updated configuration
	 */
	public HostConfig withClock(Clock newClock) {
		return new HostConfig(bindAddress, port, enrollmentPort, advertisedHost, credentialLifetime,
				invitationLifetime, connectedWindow, stopGrace, drainTimeout, minimumClientVersion,
				pairingAttemptsPerAddress, pairingAttemptsGlobal, statusRequestsPerAddress, newClock);
	}

	/**
	 * Returns a copy with different timing for stopping.
	 *
	 * @param grace notification grace period
	 * @param drain in-flight drain timeout
	 * @return updated configuration
	 */
	public HostConfig withStopTiming(Duration grace, Duration drain) {
		return new HostConfig(bindAddress, port, enrollmentPort, advertisedHost, credentialLifetime,
				invitationLifetime, connectedWindow, grace, drain, minimumClientVersion, pairingAttemptsPerAddress,
				pairingAttemptsGlobal, statusRequestsPerAddress, clock);
	}

	/**
	 * Returns a copy with different pairing rate limits.
	 *
	 * @param perAddress pairing attempts per address per minute
	 * @param global pairing attempts overall per minute
	 * @return updated configuration
	 */
	public HostConfig withPairingLimits(int perAddress, int global) {
		return new HostConfig(bindAddress, port, enrollmentPort, advertisedHost, credentialLifetime,
				invitationLifetime, connectedWindow, stopGrace, drainTimeout, minimumClientVersion, perAddress, global,
				statusRequestsPerAddress, clock);
	}

	/**
	 * Returns a copy with different credential and invitation lifetimes.
	 *
	 * @param credential device credential lifetime
	 * @param invitation invitation lifetime
	 * @return updated configuration
	 */
	public HostConfig withLifetimes(Duration credential, Duration invitation) {
		return new HostConfig(bindAddress, port, enrollmentPort, advertisedHost, credential, invitation,
				connectedWindow, stopGrace, drainTimeout, minimumClientVersion, pairingAttemptsPerAddress,
				pairingAttemptsGlobal, statusRequestsPerAddress, clock);
	}
}
