/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.host;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.util.List;
import org.junit.jupiter.api.Test;

class NetworkInfoTest {
	private static NetworkInfo.Network wifi() throws Exception {
		return new NetworkInfo.Network("wlan0", InetAddress.getByName("192.168.1.32"), 24);
	}

	@Test
	void describesIpv4NetworkInCidrForm() throws Exception {
		assertEquals("192.168.1.0/24", wifi().networkText());
		assertEquals("wlan0: 192.168.1.32 on network 192.168.1.0/24", wifi().describe());
	}

	@Test
	void findsTheNetworkContainingALiteralAddress() throws Exception {
		List<NetworkInfo.Network> networks = List.of(wifi());
		assertTrue(NetworkInfo.networkContaining("192.168.1.77:8444", networks).isPresent());
		assertTrue(NetworkInfo.networkContaining("192.168.2.5", networks).isEmpty());
		assertTrue(NetworkInfo.networkContaining("host.local", networks).isEmpty());
	}

	@Test
	void summaryReportsWhenNoNetworkIsFound() {
		assertEquals("No active network connection was found.", NetworkInfo.summary(List.of()));
	}
}
