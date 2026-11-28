/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.host;

import com.fasterxml.jackson.databind.JsonNode;
import org.sarmanagement.icsforms.persistence.ReauthorizationOutbox;
import org.sarmanagement.icsforms.persistence.RemoteIncidentStore;

import java.nio.file.Path;
import java.time.Duration;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Client side of administrator-approved re-authorization for one cached incident.
 *
 * <p>The flow never changes the durable outbox while waiting or after a denial. Revoked-device work is exported
 * to protected recovery storage before requesting repair and before replay. Pending work is replayed only after
 * the host approves a credential and the replacement gateway has been attached.
 */
public final class ReauthorizationFlow {
	/** Final result of one request. */
	public enum Outcome {
		/** The administrator approved the request and pending work was replayed. */
		APPROVED,
		/** The administrator denied the request; the outbox is unchanged. */
		DENIED,
		/** The caller stopped waiting; the request stays pending on the host and the outbox is unchanged. */
		CANCELLED
	}

	/** Prevents instantiation of this utility. */
	private ReauthorizationFlow() {
	}

	/**
	 * Requests approval, waits for the decision and replays the outbox after approval. Revoked-device work is
	 * exported beside the protected journal before requesting repair and again before replay; an export failure stops
	 * the flow without removing queued work.
	 *
	 * @param remote cached store holding the durable outbox
	 * @param gateway current, possibly expired or revoked, device transport
	 * @param invitationText invitation from the same host; required only when the credential was revoked
	 * @param deviceName operator-visible device name
	 * @param cancelled polled between status checks; true stops waiting without losing anything
	 * @param pollInterval delay between status checks
	 * @param status receives short progress messages, may be null
	 * @return how the request ended
	 * @throws InterruptedException when the waiting thread is interrupted
	 * @throws IllegalStateException when the host does not report an expired or revoked credential
	 * @throws IllegalArgumentException when a revoked credential has no usable invitation for this incident
	 */
	public static Outcome run(RemoteIncidentStore remote, HostGateway gateway, String invitationText,
			String deviceName, BooleanSupplier cancelled, Duration pollInterval, Consumer<String> status)
			throws InterruptedException {
		String state = gateway.credentialState();
		JsonNode outbox;
		synchronized (remote) {
			if ("revoked".equals(state)) {
				Path exported = remote.exportCutoffOutbox();
				if (exported != null && status != null) {
					status.accept("Outbox recovery export retained at " + exported);
				}
			}
			outbox = ReauthorizationOutbox.describe(remote.pendingOperations());
		}
		HostClient.ReauthorizationTicket ticket;
		if ("expired".equals(state)) {
			ticket = gateway.requestRenewal(deviceName, outbox);
		} else if ("revoked".equals(state)) {
			HostInvitation parsed = HostInvitation.parse(invitationText);
			if (!remote.incident().value().equals(parsed.incidentId())) {
				throw new IllegalArgumentException("The invitation is for a different incident");
			}
			ticket = gateway.requestRepair(invitationText, deviceName, outbox);
		} else {
			throw new IllegalStateException("The host does not report an expired or revoked credential");
		}
		if (status != null) {
			status.accept("Waiting for administrator approval; outbox retained");
		}
		while (!cancelled.getAsBoolean()) {
			HostClient.ReauthorizationResult result = gateway.pollReauthorization(ticket);
			if ("approved".equals(result.state())) {
				synchronized (remote) {
					if ("revoked".equals(state)) {
						Path exported = remote.exportCutoffOutbox();
						if (exported != null && status != null) {
							status.accept("Outbox recovery export retained at " + exported);
						}
					}
					remote.attachGateway(gateway.withCredential(result.credential()));
					remote.replayPending();
				}
				return Outcome.APPROVED;
			}
			if ("denied".equals(result.state())) {
				return Outcome.DENIED;
			}
			Thread.sleep(pollInterval.toMillis());
		}
		return Outcome.CANCELLED;
	}
}
