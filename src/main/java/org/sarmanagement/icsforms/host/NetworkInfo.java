/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.host;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Describes the networks this device is attached to, so operators can see which addresses a host or client is
 * actually reachable on and whether both devices share a network.
 */
public final class NetworkInfo {
	/**
	 * One address on one network interface.
	 *
	 * @param interfaceName operating system name of the interface, for example {@code wlan0}
	 * @param address address of this device on that interface
	 * @param prefixLength length of the network prefix in bits
	 */
	public record Network(String interfaceName, InetAddress address, int prefixLength) {
		/**
		 * Returns the address in textual form.
		 *
		 * @return host address text
		 */
		public String hostAddress() {
			return address.getHostAddress();
		}

		/**
		 * Returns the network in CIDR form for IPv4 addresses, or the prefix length for IPv6.
		 *
		 * @return network text such as {@code 192.168.1.0/24}
		 */
		public String networkText() {
			if (address instanceof Inet4Address) {
				int mask = prefixLength == 0 ? 0 : -1 << (32 - prefixLength);
				byte[] bytes = address.getAddress();
				int value = ((bytes[0] & 0xff) << 24) | ((bytes[1] & 0xff) << 16) | ((bytes[2] & 0xff) << 8)
						| (bytes[3] & 0xff);
				int network = value & mask;
				return ((network >>> 24) & 0xff) + "." + ((network >>> 16) & 0xff) + "." + ((network >>> 8) & 0xff)
						+ "." + (network & 0xff) + "/" + prefixLength;
			}
			return "prefix /" + prefixLength;
		}

		/**
		 * Returns a one-line description.
		 *
		 * @return text such as {@code wlan0: 192.168.1.32 on network 192.168.1.0/24}
		 */
		public String describe() {
			return interfaceName + ": " + hostAddress() + " on network " + networkText();
		}

		/**
		 * Tells whether another IPv4 address lies on this network.
		 *
		 * @param other IPv4 address to test
		 * @return true when both are IPv4 and share the network prefix
		 */
		boolean contains(InetAddress other) {
			if (!(address instanceof Inet4Address) || !(other instanceof Inet4Address)) {
				return false;
			}
			int mask = prefixLength == 0 ? 0 : -1 << (32 - prefixLength);
			return (toInt(address) & mask) == (toInt(other) & mask);
		}

		/**
		 * Packs an IPv4 address into an integer.
		 *
		 * @param value IPv4 address
		 * @return the address bits
		 */
		private static int toInt(InetAddress value) {
			byte[] bytes = value.getAddress();
			return ((bytes[0] & 0xff) << 24) | ((bytes[1] & 0xff) << 16) | ((bytes[2] & 0xff) << 8)
					| (bytes[3] & 0xff);
		}
	}

	/** Prevents instantiation. */
	private NetworkInfo() {
	}

	/**
	 * Lists the usable addresses of all running, non-loopback interfaces. Link-local IPv6 addresses are omitted.
	 *
	 * @return networks this device is on, possibly empty when none can be determined
	 */
	public static List<Network> local() {
		List<Network> result = new ArrayList<>();
		try {
			var interfaces = NetworkInterface.networkInterfaces().toList();
			for (NetworkInterface candidate : interfaces) {
				if (!candidate.isUp() || candidate.isLoopback()) {
					continue;
				}
				for (InterfaceAddress entry : candidate.getInterfaceAddresses()) {
					InetAddress address = entry.getAddress();
					if (address.isLinkLocalAddress() && !(address instanceof Inet4Address)) {
						continue;
					}
					result.add(new Network(candidate.getName(), address, entry.getNetworkPrefixLength()));
				}
			}
		} catch (SocketException failure) {
			return List.of();
		}
		return List.copyOf(result);
	}

	/**
	 * Reduces a list to the addresses worth showing an operator: every IPv4 address, and for IPv6 only one
	 * stable global address per interface (no link-local, temporary-looking duplicates or zone suffixes).
	 *
	 * @param networks networks to reduce
	 * @return distinct networks in the original order
	 */
	public static List<Network> distinct(List<Network> networks) {
		List<Network> result = new ArrayList<>();
		java.util.Set<String> ipv6Interfaces = new java.util.HashSet<>();
		java.util.Set<String> seen = new java.util.HashSet<>();
		for (Network network : networks) {
			String text = network.hostAddress().replaceAll("%.*$", "");
			if (!seen.add(text)) {
				continue;
			}
			if (!(network.address() instanceof Inet4Address)) {
				if (network.address().isLinkLocalAddress() || !ipv6Interfaces.add(network.interfaceName())) {
					continue;
				}
			}
			result.add(network);
		}
		return List.copyOf(result);
	}

	/**
	 * Formats networks as lines of text for display.
	 *
	 * @param networks networks to describe
	 * @return one line per network, or a sentence saying none were found
	 */
	public static String summary(List<Network> networks) {
		if (networks.isEmpty()) {
			return "No active network connection was found.";
		}
		StringBuilder text = new StringBuilder();
		for (Network network : networks) {
			if (text.length() > 0) {
				text.append('\n');
			}
			text.append(network.describe());
		}
		return text.toString();
	}

	/**
	 * Finds which local network a literal IPv4 host address belongs to.
	 *
	 * @param hostText host text, optionally with a trailing {@code :port}
	 * @param networks networks to test against
	 * @return the matching network, or empty when the text is not an IPv4 literal on any listed network
	 */
	public static Optional<Network> networkContaining(String hostText, List<Network> networks) {
		String host = hostText == null ? "" : hostText.strip();
		int colon = host.lastIndexOf(':');
		if (colon > 0 && host.indexOf(':') == colon) {
			host = host.substring(0, colon);
		}
		if (!host.matches("\\d{1,3}(\\.\\d{1,3}){3}")) {
			return Optional.empty();
		}
		try {
			InetAddress parsed = InetAddress.getByName(host);
			return networks.stream().filter(network -> network.contains(parsed)).findFirst();
		} catch (java.net.UnknownHostException failure) {
			return Optional.empty();
		}
	}
}
