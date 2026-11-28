/*
 * Copyright (C) 2026 SAR Management
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or any later version.
 * This program is distributed WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See <https://www.gnu.org/licenses/>.
 */

package org.sarmanagement.icsforms.host;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HostReachabilityTest {
	@TempDir
	Path directory;

	@Test
	void anOpenPortPassesAndAClosedPortIsReportedAsRefused() throws IOException {
		int closed;
		try (ServerSocket probe = new ServerSocket(0)) {
			closed = probe.getLocalPort();
		}
		try (ServerSocket open = new ServerSocket(0)) {
			List<HostReachability.Step> steps = HostReachability.check("127.0.0.1", open.getLocalPort(), closed, 500);
			assertTrue(steps.stream().filter(step -> step.name().startsWith("Pairing port")).findFirst().get()
					.passed());
			HostReachability.Step data = steps.stream().filter(step -> step.name().startsWith("Data port"))
					.findFirst().get();
			assertFalse(data.passed());
			assertTrue(data.detail().startsWith("Refused"));
			assertFalse(HostReachability.allPassed(steps));
			assertTrue(HostReachability.describe(steps).contains("FAILED  Data port " + closed));
		}
	}

	@Test
	void anUnresolvableNameStopsAtTheFirstStep() {
		List<HostReachability.Step> steps = HostReachability.check("no-such-host.invalid", 8444, 8443, 500);
		assertEquals(1, steps.size());
		assertFalse(steps.get(0).passed());
	}

	@Test
	void entriesAreParsedIntoHostAndPorts() {
		assertEquals(new HostReachability.Target("192.168.1.5", 8444, 0), HostReachability.parse("192.168.1.5"));
		assertEquals(new HostReachability.Target("host.local", 9000, 0), HostReachability.parse(" host.local:9000 "));
		assertEquals(new HostReachability.Target("::1", 9001, 0), HostReachability.parse("[::1]:9001"));
		assertEquals(new HostReachability.Target("192.168.1.5", 8444, 8443), HostReachability.parse(
				new org.sarmanagement.icsforms.host.pairing.PairingPayload("192.168.1.5", 8443, 8444, "a".repeat(64))
						.toText().toLowerCase()));
		assertThrows(IllegalArgumentException.class, () -> HostReachability.parse(""));
		assertThrows(IllegalArgumentException.class, () -> HostReachability.parse("host:abc"));
	}

	@Test
	void interfaceKindComesFromSysfsOrTheDisplayName() throws IOException {
		Files.createDirectories(directory.resolve("wlan0/wireless"));
		Files.createDirectories(directory.resolve("eth0/device"));
		Files.createDirectories(directory.resolve("docker0"));
		assertEquals("wireless", NetworkInfo.kindOf("wlan0", null, directory));
		assertEquals("wired", NetworkInfo.kindOf("eth0", null, directory));
		assertEquals("", NetworkInfo.kindOf("docker0", null, directory));
		assertEquals("wireless", NetworkInfo.kindOf("net1", "Intel Wi-Fi 6", directory));
		assertEquals("wired", NetworkInfo.kindOf("net2", "Realtek Gigabit Ethernet", directory));
		assertEquals("", NetworkInfo.kindOf("net3", "Something", directory));
		NetworkInfo.Network network = new NetworkInfo.Network("wlan0", java.net.InetAddress.getLoopbackAddress(), 8,
				"wireless");
		assertTrue(network.describe().startsWith("wlan0 (wireless): "));
	}

	@Test
	void busyConflictIsExplainedAsNotANetworkProblem() {
		String text = HostReachability.explainFailure("Another device is pairing with the host right now.");
		assertTrue(text.contains("busy"));
		assertTrue(text.contains("network is fine"));
		assertEquals("", HostReachability.explainFailure(null));
	}
}
