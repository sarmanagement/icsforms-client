/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.host;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;

/** Multicast DNS discovery of pairing windows using JmDNS. Failures degrade to no discovery. */
final class JmdnsDiscovery implements HostDiscovery {
	private static final Logger LOGGER = Logger.getLogger(JmdnsDiscovery.class.getName());
	private static final String TYPE = "_icsforms-pair._tcp.local.";

	/** {@inheritDoc} */
	@Override
	public Closeable advertise(String name, int enrollmentPort) {
		try {
			JmDNS dns = JmDNS.create();
			dns.registerService(ServiceInfo.create(TYPE, name, enrollmentPort, 0, 0,
					Map.of("v", "1", "name", name)));
			return () -> {
				dns.unregisterAllServices();
				dns.close();
			};
		} catch (IOException | RuntimeException failure) {
			LOGGER.log(Level.INFO, "Pairing advertisement unavailable: {0}", failure.getMessage());
			return () -> { };
		}
	}

	/** {@inheritDoc} */
	@Override
	public List<Found> browse(Duration wait) {
		List<Found> found = new ArrayList<>();
		try (JmDNS dns = JmDNS.create()) {
			for (ServiceInfo info : dns.list(TYPE, Math.max(1, wait.toMillis()))) {
				InetAddress[] addresses = info.getInetAddresses();
				if (addresses.length > 0) {
					found.add(new Found(info.getName(), addresses[0].getHostAddress(), info.getPort()));
				}
			}
		} catch (IOException | RuntimeException failure) {
			LOGGER.log(Level.INFO, "Host discovery unavailable: {0}", failure.getMessage());
		}
		return found;
	}
}
