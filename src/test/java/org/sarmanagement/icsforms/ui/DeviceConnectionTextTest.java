/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.host.CredentialState;
import org.sarmanagement.icsforms.host.DeviceInfo;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Verifies the host device table's connection column, including the lost-contact marker. */
class DeviceConnectionTextTest {
	private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

	@Test
	void connectedDevicesShowYes() {
		assertEquals("yes", HostSharingDialog.connectionText(device(CredentialState.ACTIVE, NOW, true)));
	}

	@Test
	void anActiveDeviceThatWasInContactAndIsNotNowIsMarkedLostContact() {
		assertEquals("no - lost contact", HostSharingDialog.connectionText(device(CredentialState.ACTIVE, NOW, false)));
	}

	@Test
	void neverSeenRevokedAndExpiredDevicesAreNotMarkedLostContact() {
		assertEquals("no", HostSharingDialog.connectionText(device(CredentialState.ACTIVE, null, false)));
		assertEquals("no", HostSharingDialog.connectionText(device(CredentialState.REVOKED, NOW, false)));
		assertEquals("no", HostSharingDialog.connectionText(device(CredentialState.EXPIRED, NOW, false)));
	}

	/**
	 * Builds a device row.
	 *
	 * @param state credential state
	 * @param contact last contact or null
	 * @param connected whether currently connected
	 * @return the device
	 */
	private static DeviceInfo device(CredentialState state, Instant contact, boolean connected) {
		return new DeviceInfo("id", "name", NOW, NOW.plusSeconds(60), state, contact, connected);
	}
}
