/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.host;

import java.time.Instant;

/**
 * Operator-visible description of an admitted device.
 *
 * @param deviceId device identifier
 * @param name device name chosen when pairing
 * @param issuedAt credential issue time
 * @param expiresAt credential expiry
 * @param state credential state
 * @param lastContact last authenticated contact since the host started, or null
 * @param connected whether the device made contact recently
 * @param lastAddress network address the last authenticated request came from since the host started, or null
 */
public record DeviceInfo(String deviceId, String name, Instant issuedAt, Instant expiresAt, CredentialState state,
		Instant lastContact, boolean connected, String lastAddress) {
	/**
	 * Creates a description without a known network address.
	 *
	 * @param deviceId device identifier
	 * @param name device name chosen when pairing
	 * @param issuedAt credential issue time
	 * @param expiresAt credential expiry
	 * @param state credential state
	 * @param lastContact last authenticated contact since the host started, or null
	 * @param connected whether the device made contact recently
	 */
	public DeviceInfo(String deviceId, String name, Instant issuedAt, Instant expiresAt, CredentialState state,
			Instant lastContact, boolean connected) {
		this(deviceId, name, issuedAt, expiresAt, state, lastContact, connected, null);
	}
}
