/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.host;

import java.io.Closeable;
import java.time.Duration;
import java.util.List;

/**
 * Optional local-network discovery of hosts that have a pairing window open. Discovery is a convenience only: it
 * conveys no secret and no trust, because pairing authenticates the host from the code regardless of how the
 * address was found.
 */
public interface HostDiscovery {
	/**
	 * A host found on the local network.
	 *
	 * @param name host display name
	 * @param address host name or address to connect to
	 * @param enrollmentPort enrollment port
	 */
	record Found(String name, String address, int enrollmentPort) {
	}

	/**
	 * Starts advertising a pairing window. The advertisement carries only the host name and enrollment port.
	 *
	 * @param name display name
	 * @param enrollmentPort enrollment port
	 * @return handle that stops the advertisement when closed
	 */
	Closeable advertise(String name, int enrollmentPort);

	/**
	 * Browses for hosts advertising a pairing window.
	 *
	 * @param wait how long to listen
	 * @return hosts found, possibly empty
	 */
	List<Found> browse(Duration wait);

	/**
	 * Returns the discovery implementation for this platform, falling back to one that finds nothing.
	 *
	 * @return discovery implementation
	 */
	static HostDiscovery create() {
		try {
			return new JmdnsDiscovery();
		} catch (LinkageError | RuntimeException failure) {
			return new HostDiscovery() {
				/** {@inheritDoc} */
				@Override
				public Closeable advertise(String name, int enrollmentPort) {
					return () -> { };
				}

				/** {@inheritDoc} */
				@Override
				public List<Found> browse(Duration wait) {
					return List.of();
				}
			};
		}
	}
}
