/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.host;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.sarmanagement.icsforms.persistence.IncidentId;
import org.sarmanagement.icsforms.persistence.IncidentNotFoundException;
import org.sarmanagement.icsforms.persistence.IncidentService;
import org.sarmanagement.icsforms.persistence.RemoteAccessException;
import org.sarmanagement.icsforms.persistence.RemoteIncidentGateway;
import org.sarmanagement.icsforms.persistence.RemoteIncidentStore;
import org.sarmanagement.icsforms.persistence.StoreConflictException;
import org.sarmanagement.icsforms.persistence.StoreLockConflictException;
import org.sarmanagement.icsforms.persistence.StorePersistenceException;
import org.sarmanagement.icsforms.persistence.StoreUnavailableException;
import org.sarmanagement.icsforms.persistence.StoreValidationException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * {@link RemoteIncidentGateway} over the host's HTTPS/mutual-TLS API. It maps every protocol outcome onto the
 * existing store exceptions so conflicts, missing incidents, validation errors, unreachable hosts, rejected
 * credentials and server failures stay distinguishable. All calls block on network I/O.
 */
public final class HostGateway implements RemoteIncidentGateway {
	private final HostClient client;
	private final DeviceCredential credential;
	private final String location;

	/**
	 * Creates a gateway.
	 *
	 * @param client pinned client for the host
	 * @param credential device credential presented to the host
	 * @param location display address of the host
	 */
	public HostGateway(HostClient client, DeviceCredential credential, String location) {
		this.client = Objects.requireNonNull(client, "client");
		this.credential = Objects.requireNonNull(credential, "credential");
		this.location = Objects.requireNonNull(location, "location");
	}

	/**
	 * Redeems an invitation, pins the host named in it and returns the production remote store for the invited
	 * incident. This performs network I/O and must not run on the event-dispatch thread. The credential lives only
	 * in memory for this session.
	 *
	 * @param invitationText invitation text from the host operator
	 * @param deviceName name shown to the host operator
	 * @return remote store for the shared incident
	 * @throws IllegalArgumentException when the invitation is malformed
	 * @throws RemoteAccessException when the host rejects the invitation or its identity differs
	 * @throws StoreUnavailableException when the host cannot be reached
	 */
	public static RemoteIncidentStore pair(String invitationText, String deviceName) {
		HostInvitation invitation = HostInvitation.parse(invitationText);
		try {
			DeviceCredential credential = HostClient.pair(invitation, deviceName);
			HostClient client = HostClient.forDevice(invitation.host(), invitation.port(),
					invitation.enrollmentPort(), credential);
			return new RemoteIncidentStore(
					new HostGateway(client, credential, invitation.host() + ":" + invitation.port()),
					new IncidentId(invitation.incidentId()));
		} catch (HostClientException failure) {
			throw map(failure);
		}
	}

	/** {@inheritDoc} */
	@Override
	public String location() {
		return location;
	}

	/** {@inheritDoc} */
	@Override
	public IncidentService.ReadResult read(IncidentId incident) {
		try {
			JsonNode body = client.readIncident(incident.value());
			List<IncidentService.VersionedRecord> records = new ArrayList<>();
			for (JsonNode node : body.path("records")) {
				records.add(HostHttp.MAPPER.treeToValue(node, IncidentService.VersionedRecord.class));
			}
			return new IncidentService.ReadResult(incident, body.path("cursor").asLong(), records);
		} catch (HostClientException failure) {
			throw map(failure);
		} catch (JsonProcessingException | RuntimeException failure) {
			throw unreadable(failure);
		}
	}

	/** {@inheritDoc} */
	@Override
	public IncidentService.ChangeResult changesSince(IncidentId incident, long after) {
		try {
			return HostHttp.MAPPER.treeToValue(client.changesSince(incident.value(), after),
					IncidentService.ChangeResult.class);
		} catch (HostClientException failure) {
			throw map(failure);
		} catch (JsonProcessingException | RuntimeException failure) {
			throw unreadable(failure);
		}
	}

	/** {@inheritDoc} */
	@Override
	public IncidentService.SubmissionResult submit(IncidentService.RecordBatch batch) {
		try {
			ObjectNode request = HostHttp.MAPPER.createObjectNode();
			request.put("operationId", batch.operationId());
			ArrayNode records = request.putArray("records");
			for (IncidentService.RecordSubmission submission : batch.records()) {
				records.add(HostHttp.MAPPER.valueToTree(submission));
			}
			JsonNode body = client.submit(batch.incident().value(), request.toString());
			if (body.has("error")) {
				throw map(new HostClientException(HostClientException.Kind.REJECTED, 409,
						body.path("error").path("code").asText(""), body.path("error").path("message")
								.asText("Conflict"), null));
			}
			return HostHttp.MAPPER.treeToValue(body, IncidentService.SubmissionResult.class);
		} catch (HostClientException failure) {
			throw map(failure);
		} catch (JsonProcessingException failure) {
			throw unreadable(failure);
		}
	}

	/** {@inheritDoc} */
	@Override
	public HostProbe probe() {
		return switch (client.diagnose(credential)) {
			case CONNECTED -> HostProbe.RUNNING;
			case HOST_UNAVAILABLE -> HostProbe.STOPPING;
			case CREDENTIAL_EXPIRED -> throw new RemoteAccessException(RemoteAccessException.Reason.CREDENTIAL_EXPIRED,
					"The device credential has expired", null);
			case CREDENTIAL_REVOKED -> throw new RemoteAccessException(RemoteAccessException.Reason.CREDENTIAL_REVOKED,
					"The host revoked this device", null);
			case CREDENTIAL_UNKNOWN -> throw new RemoteAccessException(RemoteAccessException.Reason.CREDENTIAL_UNKNOWN,
					"The host does not recognise this device", null);
			case HOST_IDENTITY_CHANGED -> throw new RemoteAccessException(
					RemoteAccessException.Reason.IDENTITY_CHANGED, "The host identity changed", null);
			case HOST_UNREACHABLE -> throw new StoreUnavailableException("The host cannot be reached");
		};
	}

	/**
	 * Nothing to release: the HTTP client keeps no resources that outlive its requests.
	 */
	@Override
	public void close() {
		// intentionally empty
	}

	/**
	 * Wraps an unreadable response without exposing its content.
	 *
	 * @param failure parse failure
	 * @return exception to throw
	 */
	private static StorePersistenceException unreadable(Exception failure) {
		return new StorePersistenceException("The host returned a response this client cannot read", failure);
	}

	/**
	 * Maps a transport or protocol failure onto the store exception hierarchy.
	 *
	 * @param failure classified client failure
	 * @return exception to throw
	 */
	static RuntimeException map(HostClientException failure) {
		return switch (failure.kind()) {
			case UNREACHABLE -> new StoreUnavailableException("The host cannot be reached", failure);
			case HOST_UNAVAILABLE, RATE_LIMITED -> new StoreUnavailableException(
					"The host is not serving the incident right now", failure);
			case CREDENTIAL_EXPIRED -> new RemoteAccessException(RemoteAccessException.Reason.CREDENTIAL_EXPIRED,
					"The device credential has expired", failure);
			case CREDENTIAL_REVOKED -> new RemoteAccessException(RemoteAccessException.Reason.CREDENTIAL_REVOKED,
					"The host revoked this device", failure);
			case UNAUTHORIZED, INVITATION_REJECTED -> new RemoteAccessException(
					RemoteAccessException.Reason.CREDENTIAL_UNKNOWN, "The host does not accept this device", failure);
			case IDENTITY_CHANGED -> new RemoteAccessException(RemoteAccessException.Reason.IDENTITY_CHANGED,
					"The host identity changed", failure);
			case INCOMPATIBLE -> new RemoteAccessException(RemoteAccessException.Reason.INCOMPATIBLE,
					"This client and the host are incompatible", failure);
			case REJECTED -> rejected(failure);
		};
	}

	/**
	 * Maps a structured host rejection by status.
	 *
	 * @param failure rejection
	 * @return exception to throw
	 */
	private static RuntimeException rejected(HostClientException failure) {
		int status = failure.status();
		if (status == 404) {
			return new IncidentNotFoundException("The host does not share this incident");
		}
		if (status == 423) {
			return new StoreLockConflictException("The incident is locked by another editor on the host");
		}
		if (status == 409) {
			return new StoreConflictException("The host reports a conflict", null, null);
		}
		if (status == 400 || status == 413) {
			return new StoreValidationException("The host rejected the request: " + failure.getMessage());
		}
		return new StorePersistenceException("The host could not complete the request (HTTP " + status + ")",
				failure);
	}
}
