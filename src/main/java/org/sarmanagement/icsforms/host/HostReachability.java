/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.host;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

/**
 * Checks, step by step, whether a host can be reached from this device, so an operator who cannot connect can see
 * which step fails: the name resolving, the host answering a ping, and each of the host's ports accepting a
 * connection. A failed ping alone does not mean the host is unreachable, because many firewalls drop pings.
 */
public final class HostReachability {
	/** Default timeout for each step, in milliseconds. */
	public static final int DEFAULT_TIMEOUT_MILLIS = 2000;
	/** Pairing port a typed address uses when it names none. */
	public static final int DEFAULT_PAIRING_PORT = 8444;

	/**
	 * Outcome of one step.
	 *
	 * @param name what was checked
	 * @param passed whether the step succeeded
	 * @param detail what was found and what it means
	 */
	public record Step(String name, boolean passed, String detail) {
	}

	/**
	 * Where to look for a host.
	 *
	 * @param host host name or address
	 * @param pairingPort port devices use to pair
	 * @param dataPort port devices use to exchange incident data, or 0 when not known
	 */
	public record Target(String host, int pairingPort, int dataPort) {
	}

	/**
	 * Works out the host to check from what the operator entered: pairing QR text, an invitation, or a typed
	 * address such as {@code host}, {@code host:8444} or {@code [::1]:8444}.
	 *
	 * @param text entered text
	 * @return the target
	 * @throws IllegalArgumentException when the text is blank or malformed
	 */
	public static Target parse(String text) {
		String trimmed = text == null ? "" : text.strip();
		if (org.sarmanagement.icsforms.host.pairing.PairingPayload.isPayload(trimmed)) {
			var payload = org.sarmanagement.icsforms.host.pairing.PairingPayload.parse(trimmed);
			return new Target(payload.host(), payload.enrollmentPort(), payload.port());
		}
		if (trimmed.startsWith("ICSFORMS-INVITE-")) {
			HostInvitation invitation = HostInvitation.parse(trimmed);
			return new Target(invitation.host(), invitation.enrollmentPort(), invitation.port());
		}
		String address = trimmed;
		int port = DEFAULT_PAIRING_PORT;
		boolean bracketed = address.startsWith("[");
		int colon = bracketed ? address.indexOf("]:") + 1 : address.indexOf(':');
		boolean hasPort = bracketed ? colon > 0 : colon > 0 && colon == address.lastIndexOf(':');
		if (hasPort) {
			try {
				port = Integer.parseInt(address.substring(colon + 1));
			} catch (NumberFormatException failure) {
				throw new IllegalArgumentException("The port is not a number.", failure);
			}
			address = address.substring(0, colon);
		}
		if (address.startsWith("[") && address.endsWith("]")) {
			address = address.substring(1, address.length() - 1);
		}
		if (address.isBlank()) {
			throw new IllegalArgumentException("Enter the host address.");
		}
		return new Target(address, port, 0);
	}

	/** Prevents instantiation. */
	private HostReachability() {
	}

	/**
	 * Runs all checks. This blocks for up to a few timeouts, so call it off the event thread.
	 *
	 * @param host host name or address
	 * @param pairingPort port devices use to pair
	 * @param dataPort port devices use to exchange incident data, or 0 when not known
	 * @param timeoutMillis timeout of each step
	 * @return one outcome per step, in order
	 */
	public static List<Step> check(String host, int pairingPort, int dataPort, int timeoutMillis) {
		List<Step> steps = new ArrayList<>();
		InetAddress address;
		try {
			address = InetAddress.getByName(host);
			steps.add(new Step("Find the host", true, host + " is " + address.getHostAddress() + "."));
		} catch (UnknownHostException failure) {
			steps.add(new Step("Find the host", false, "The name or address \"" + host
					+ "\" could not be resolved. Check for a typing mistake, or enter the host's IP address."));
			return steps;
		}
		steps.add(ping(address, timeoutMillis));
		steps.add(port(address, pairingPort, "Pairing port", "used to add this device", timeoutMillis));
		if (dataPort > 0) {
			steps.add(port(address, dataPort, "Data port", "used to share incident data", timeoutMillis));
		} else {
			steps.add(new Step("Data port", true, "The data port is not known until this device has paired with the "
					+ "host; it is checked when an invitation or QR text is used."));
		}
		return steps;
	}

	/**
	 * Explains what a failed connection attempt means and what to do, in terms an operator can act on, because
	 * the network checks cannot see problems that happen after the host has been reached.
	 *
	 * @param message the failure shown for the last attempt, possibly null
	 * @return an explanation, or an empty string when there is no failure to explain
	 */
	public static String explainFailure(String message) {
		if (message == null || message.isBlank()) {
			return "";
		}
		String text = message.toLowerCase(java.util.Locale.ROOT);
		String meaning;
		if (text.contains("another device is pairing") || text.contains("conflict")) {
			meaning = "The host was reached, so the network is fine. It is busy: only one device can pair at a "
					+ "time, and either another device is pairing now or an earlier attempt from this or another "
					+ "device has not timed out. Wait a minute and try again, and ask the host operator whether "
					+ "another device is waiting for its number to be entered.";
		} else if (text.contains("not accept this device") || text.contains("revoked")) {
			meaning = "The host was reached but does not accept this device. Ask the host operator to open "
					+ "\"Devices can join\" again, or to check that this device was not revoked.";
		} else if (text.contains("expired")) {
			meaning = "The host was reached but this device's access has expired. Pair the device again.";
		} else if (text.contains("identity changed")) {
			meaning = "The host's certificate is not the one this device remembers. Confirm with the host operator "
					+ "that this is the right host before pairing again.";
		} else if (text.contains("incompatible")) {
			meaning = "This device and the host run incompatible versions. Update the older one.";
		} else if (text.contains("not serving") || text.contains("not share")) {
			meaning = "The host was reached but is not sharing an incident right now. Ask the host operator to "
					+ "start sharing.";
		} else if (text.contains("cannot be reached") || text.contains("unreachable")) {
			meaning = "The host could not be reached; the checks below show where it stops.";
		} else {
			meaning = "The host was reached, so this is not a network problem. Read the message above, and check the "
					+ "host's status line.";
		}
		return "Last attempt: " + message + "\nWhat it means: " + meaning + "\n\n";
	}

	/**
	 * Tells whether every checked step passed.
	 *
	 * @param steps outcomes from {@link #check}
	 * @return true when all steps passed
	 */
	public static boolean allPassed(List<Step> steps) {
		return steps.stream().allMatch(Step::passed);
	}

	/**
	 * Formats outcomes as lines of text for display.
	 *
	 * @param steps outcomes from {@link #check}
	 * @return one line per step
	 */
	public static String describe(List<Step> steps) {
		StringBuilder text = new StringBuilder();
		for (Step step : steps) {
			text.append(step.passed() ? "OK      " : "FAILED  ").append(step.name()).append(": ").append(step.detail())
					.append('\n');
		}
		return text.toString();
	}

	/**
	 * Pings the host. A failed ping is reported as not conclusive, since many hosts and firewalls do not answer.
	 *
	 * @param address host address
	 * @param timeoutMillis timeout
	 * @return the outcome
	 */
	private static Step ping(InetAddress address, int timeoutMillis) {
		try {
			long started = System.nanoTime();
			if (address.isReachable(timeoutMillis)) {
				long millis = (System.nanoTime() - started) / 1_000_000;
				return new Step("Ping", true, "The host answered in about " + millis + " ms.");
			}
			return new Step("Ping", true, "No answer. Many firewalls drop pings, so this alone does not mean the "
					+ "host is unreachable; the port checks below decide.");
		} catch (IOException failure) {
			return new Step("Ping", false, "This device has no route to the host: " + failure.getMessage()
					+ ". Check that both devices are on the same network.");
		}
	}

	/**
	 * Tries a TCP connection to one port.
	 *
	 * @param address host address
	 * @param port port to try
	 * @param name step name
	 * @param purpose what the port is for
	 * @param timeoutMillis timeout
	 * @return the outcome
	 */
	private static Step port(InetAddress address, int port, String name, String purpose, int timeoutMillis) {
		try (Socket socket = new Socket()) {
			socket.connect(new InetSocketAddress(address, port), timeoutMillis);
			return new Step(name + " " + port, true, "Connected (" + purpose + ").");
		} catch (java.net.SocketTimeoutException failure) {
			return new Step(name + " " + port, false, "No reply (" + purpose + "). A firewall on the host or on "
					+ "the network is probably dropping it; open this port on the host's firewall.");
		} catch (java.net.ConnectException failure) {
			return new Step(name + " " + port, false, "Refused (" + purpose + "). The host is reachable but nothing "
					+ "is listening on this port: check the port number, and that the host is sharing and, for "
					+ "pairing, that devices can join.");
		} catch (IOException failure) {
			return new Step(name + " " + port, false, "Could not connect (" + purpose + "): " + failure.getMessage());
		}
	}
}
