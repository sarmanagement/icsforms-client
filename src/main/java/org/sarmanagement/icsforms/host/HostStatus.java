/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.host;

/**
 * Snapshot of a host session for display.
 *
 * @param state lifecycle state
 * @param port mutual-TLS port, or 0 when not listening
 * @param enrollmentPort enrollment port, or 0 when not listening
 * @param hostFingerprint host authority fingerprint, or null when not started
 * @param connectedDevices devices with recent contact
 * @param failure failure description when {@code state} is {@link HostState#FAILED}, otherwise null
 */
public record HostStatus(HostState state, int port, int enrollmentPort, String hostFingerprint, int connectedDevices,
		String failure) {
}
