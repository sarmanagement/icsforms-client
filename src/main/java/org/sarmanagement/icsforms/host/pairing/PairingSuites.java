/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.host.pairing;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The set of PAKE and channel suites a party supports, in order of preference. The pairing protocol negotiates
 * through this registry only, so adding or replacing a suite is a change here and in the suite class.
 */
public final class PairingSuites {
	private final Map<String, PakeSuite> pakes = new LinkedHashMap<>();
	private final Map<String, ChannelSuite> channels = new LinkedHashMap<>();

	/**
	 * Creates an empty registry; use {@link #defaults()} for the shipped suites.
	 */
	public PairingSuites() {
		// Suites are added with the register methods.
	}

	/**
	 * Returns the shipped suites: finite-field J-PAKE and the HKDF/AES-GCM channel.
	 *
	 * @return registry of default suites
	 */
	public static PairingSuites defaults() {
		return new PairingSuites().register(new JpakeSuite()).register(new HkdfAesGcmChannelSuite());
	}

	/**
	 * Adds a PAKE suite after those already registered, so it is less preferred.
	 *
	 * @param suite suite to add
	 * @return this registry
	 */
	public PairingSuites register(PakeSuite suite) {
		pakes.put(suite.id(), suite);
		return this;
	}

	/**
	 * Adds a channel suite after those already registered.
	 *
	 * @param suite suite to add
	 * @return this registry
	 */
	public PairingSuites register(ChannelSuite suite) {
		channels.put(suite.id(), suite);
		return this;
	}

	/**
	 * Lists supported PAKE suite identifiers in order of preference.
	 *
	 * @return identifiers
	 */
	public List<String> pakeIds() {
		return new ArrayList<>(pakes.keySet());
	}

	/**
	 * Lists supported channel suite identifiers in order of preference.
	 *
	 * @return identifiers
	 */
	public List<String> channelIds() {
		return new ArrayList<>(channels.keySet());
	}

	/**
	 * Looks up a PAKE suite.
	 *
	 * @param id wire identifier
	 * @return the suite, or empty when unsupported
	 */
	public Optional<PakeSuite> pake(String id) {
		return Optional.ofNullable(pakes.get(id));
	}

	/**
	 * Looks up a channel suite.
	 *
	 * @param id wire identifier
	 * @return the suite, or empty when unsupported
	 */
	public Optional<ChannelSuite> channel(String id) {
		return Optional.ofNullable(channels.get(id));
	}

	/**
	 * Chooses the first offered identifier this registry supports, honouring the offerer's preference order.
	 *
	 * @param offered identifiers offered by the peer, most preferred first
	 * @param supported identifiers this side supports
	 * @return the chosen identifier, or empty when none match
	 */
	public static Optional<String> choose(List<String> offered, List<String> supported) {
		return offered.stream().filter(supported::contains).findFirst();
	}
}
