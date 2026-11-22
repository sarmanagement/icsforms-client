/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.host;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpsExchange;
import org.sarmanagement.icsforms.persistence.IncidentId;
import org.sarmanagement.icsforms.persistence.IncidentNotFoundException;
import org.sarmanagement.icsforms.persistence.IncidentService;
import org.sarmanagement.icsforms.persistence.StoreConflictException;
import org.sarmanagement.icsforms.persistence.StoreLockConflictException;
import org.sarmanagement.icsforms.persistence.StoreCommittedException;
import org.sarmanagement.icsforms.persistence.StorePersistenceException;
import org.sarmanagement.icsforms.persistence.StoreUnavailableException;
import org.sarmanagement.icsforms.persistence.StoreValidationException;

import java.io.IOException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLPeerUnverifiedException;

/**
 * Certificate-authenticated endpoints: the part-2 incident contract plus the host administrator API, which only
 * the host's own loopback credential may use.
 */
final class HostApiHandler implements HttpHandler {
	private static final Logger LOGGER = Logger.getLogger(HostApiHandler.class.getName());
	private static final String INCIDENTS = "/v1/incidents/";
	private static final String DEVICES = "/v1/admin/devices";
	private final HostContext context;

	/**
	 * Creates the handler.
	 *
	 * @param context shared session state
	 */
	HostApiHandler(HostContext context) {
		this.context = context;
	}

	/** {@inheritDoc} */
	@Override
	public void handle(HttpExchange exchange) throws IOException {
		context.inFlight.incrementAndGet();
		try {
			route(exchange);
		} catch (RuntimeException failure) {
			failure(exchange, failure);
		} catch (Error failure) {
			context.fail("Unexpected host error: " + failure.getClass().getSimpleName());
			LOGGER.log(Level.SEVERE, "Unexpected host error", failure);
			HostHttp.error(exchange, 500, "internal_error", "The host could not process the request.", true);
		} finally {
			context.inFlight.decrementAndGet();
			exchange.close();
		}
	}

	/**
	 * Applies state, version and credential checks and dispatches the request.
	 *
	 * @param exchange exchange to answer
	 * @throws IOException when the response cannot be written
	 */
	private void route(HttpExchange exchange) throws IOException {
		HostState state = context.state.get();
		if (state != HostState.RUNNING) {
			HostHttp.error(exchange, 503, "service_unavailable", "The incident service is temporarily unavailable.", true,
					Map.of("hostState", state.name().toLowerCase(Locale.ROOT)));
			return;
		}
		if (!versionAccepted(exchange)) {
			return;
		}
		DeviceRegistry.DeviceRecord device = authenticate(exchange);
		if (device == null) {
			return;
		}
		String path = exchange.getRequestURI().getPath();
		if (path.startsWith("/v1/admin/")) {
			admin(exchange, device, path);
		} else if (path.startsWith(INCIDENTS)) {
			incident(exchange, device, path.substring(INCIDENTS.length()));
		} else {
			HostHttp.error(exchange, 404, "not_found", "No such resource.", false);
		}
	}

	/**
	 * Enforces API and client version negotiation without silently downgrading.
	 *
	 * @param exchange exchange to answer on failure
	 * @return true when the request may proceed
	 * @throws IOException when the response cannot be written
	 */
	private boolean versionAccepted(HttpExchange exchange) throws IOException {
		String api = exchange.getRequestHeaders().getFirst(HostProtocol.API_VERSION_HEADER);
		String client = exchange.getRequestHeaders().getFirst(HostProtocol.CLIENT_VERSION_HEADER);
		boolean apiOk = api != null && api.strip().matches("[0-9]{1,3}")
				&& HostProtocol.SUPPORTED_API_VERSIONS.contains(Integer.parseInt(api.strip()));
		if (apiOk && HostProtocol.atLeast(client, context.config.minimumClientVersion())) {
			return true;
		}
		HostHttp.error(exchange, 426, "incompatible_client",
				"This client version is not supported by the host.", false,
				Map.of("minimumClientVersion", context.config.minimumClientVersion(), "supportedApiVersions",
						HostProtocol.SUPPORTED_API_VERSIONS));
		return false;
	}

	/**
	 * Resolves the TLS client certificate to an admitted, unexpired, unrevoked device.
	 *
	 * @param exchange exchange to answer on failure
	 * @return the device, or null after an error response was sent
	 * @throws IOException when the response cannot be written
	 */
	private DeviceRegistry.DeviceRecord authenticate(HttpExchange exchange) throws IOException {
		Optional<X509Certificate> peer = peerCertificate(exchange);
		Optional<DeviceRegistry.DeviceRecord> found = peer.flatMap(
				certificate -> context.registry.byFingerprint(HostCrypto.fingerprint(certificate)));
		if (found.isEmpty()) {
			HostHttp.error(exchange, 401, "unauthorized", "A valid device credential is required.", false);
			return null;
		}
		DeviceRegistry.DeviceRecord device = found.get();
		switch (context.effectiveState(device)) {
			case REVOKED -> {
				HostHttp.error(exchange, 403, "credential_revoked", "The device credential was revoked.", false);
				return null;
			}
			case EXPIRED -> {
				HostHttp.error(exchange, 401, "credential_expired", "The device credential has expired.", false);
				return null;
			}
			default -> {
				if (!device.permissions().contains(HostPermission.CONNECT)) {
					HostHttp.error(exchange, 403, "forbidden", "This device may not connect.", false);
					return null;
				}
			}
		}
		context.registry.recordContact(device.deviceId());
		return device;
	}

	/**
	 * Extracts the peer certificate validated by the TLS handshake.
	 *
	 * @param exchange exchange
	 * @return device certificate, if the handshake produced one
	 */
	private Optional<X509Certificate> peerCertificate(HttpExchange exchange) {
		if (!(exchange instanceof HttpsExchange https)) {
			return Optional.empty();
		}
		try {
			Certificate[] chain = https.getSSLSession().getPeerCertificates();
			if (chain.length > 0 && chain[0] instanceof X509Certificate certificate) {
				return Optional.of(certificate);
			}
		} catch (SSLPeerUnverifiedException exception) {
			return Optional.empty();
		}
		return Optional.empty();
	}

	/**
	 * Serves the incident contract: read, changes since a cursor, and atomic submit.
	 *
	 * @param exchange exchange to answer
	 * @param device authenticated device
	 * @param rest request path after {@code /v1/incidents/}
	 * @throws IOException when the response cannot be written
	 */
	private void incident(HttpExchange exchange, DeviceRegistry.DeviceRecord device, String rest) throws IOException {
		String[] segments = rest.split("/", -1);
		IncidentId requested;
		try {
			requested = IncidentId.of(segments[0]);
		} catch (IllegalArgumentException exception) {
			HostHttp.error(exchange, 404, "not_found", "The incident was not found.", false);
			return;
		}
		if (!requested.equals(context.incident) || !context.incident.value().equals(device.incidentId())) {
			HostHttp.error(exchange, 404, "not_found", "The incident was not found.", false);
			return;
		}
		String method = exchange.getRequestMethod();
		if (segments.length == 1) {
			if (!"GET".equals(method)) {
				HostHttp.error(exchange, 405, "method_not_allowed", "Use GET.", false);
			} else if (require(exchange, device, HostPermission.READ)) {
				read(exchange);
			}
		} else if (segments.length == 2 && "changes".equals(segments[1])) {
			if (!"GET".equals(method)) {
				HostHttp.error(exchange, 405, "method_not_allowed", "Use GET.", false);
			} else if (require(exchange, device, HostPermission.READ)) {
				changes(exchange);
			}
		} else if (segments.length == 2 && "records:submit".equals(segments[1])) {
			if (!"POST".equals(method)) {
				HostHttp.error(exchange, 405, "method_not_allowed", "Use POST.", false);
			} else {
				submit(exchange, device);
			}
		} else {
			HostHttp.error(exchange, 404, "not_found", "No such resource.", false);
		}
	}

	/**
	 * Checks that the device holds a permission.
	 *
	 * @param exchange exchange to answer on failure
	 * @param device authenticated device
	 * @param permission required permission
	 * @return true when granted
	 * @throws IOException when the response cannot be written
	 */
	private boolean require(HttpExchange exchange, DeviceRegistry.DeviceRecord device, HostPermission permission)
			throws IOException {
		if (device.permissions().contains(permission)) {
			return true;
		}
		HostHttp.error(exchange, 403, "forbidden", "The device credential does not allow this operation.", false);
		return false;
	}

	/**
	 * Returns current records and versions.
	 *
	 * @param exchange exchange to answer
	 * @throws IOException when the response cannot be written
	 */
	private void read(HttpExchange exchange) throws IOException {
		IncidentService.ReadResult result = context.service.read(context.incident);
		System.err.println("READ " + result.records().stream()
				.filter(record -> record.content().path("provenance").has("acceptedAt"))
				.map(record -> record.content().path("provenance").path("acceptedAt")).toList());
		ObjectNode body = HostHttp.MAPPER.createObjectNode();
		body.put("incidentId", result.incident().value());
		body.put("cursor", result.cursor());
		body.set("records", HostHttp.MAPPER.valueToTree(result.records()));
		HostHttp.send(exchange, 200, body);
	}

	/**
	 * Returns ordered changes strictly after a cursor.
	 *
	 * @param exchange exchange to answer
	 * @throws IOException when the response cannot be written
	 */
	private void changes(HttpExchange exchange) throws IOException {
		String after = HostHttp.query(exchange.getRequestURI().getRawQuery()).getOrDefault("after", "0");
		long cursor;
		try {
			cursor = Long.parseLong(after.strip());
		} catch (NumberFormatException exception) {
			HostHttp.error(exchange, 400, "invalid_request", "The after cursor must be an integer.", false);
			return;
		}
		HostHttp.send(exchange, 200, HostHttp.MAPPER.valueToTree(context.service.changesSince(context.incident, cursor)));
	}

	/**
	 * Applies an atomic submission, returning {@code 409} with field conflicts when the merge cannot proceed.
	 *
	 * @param exchange exchange to answer
	 * @param device authenticated device
	 * @throws IOException when the response cannot be written
	 */
	private void submit(HttpExchange exchange, DeviceRegistry.DeviceRecord device) throws IOException {
		byte[] raw = HostHttp.readBody(exchange, HostProtocol.MAX_REQUEST_BYTES);
		if (raw == null) {
			return;
		}
		JsonNode request = HostHttp.parseObject(exchange, raw);
		if (request == null) {
			return;
		}
		JsonNode records = request.path("records");
		if (!records.isArray()) {
			HostHttp.error(exchange, 400, "invalid_request", "records must be an array.", false);
			return;
		}
		if (records.size() > HostProtocol.MAX_BATCH_RECORDS) {
			HostHttp.error(exchange, 413, "payload_too_large",
					"A batch may contain at most " + HostProtocol.MAX_BATCH_RECORDS + " records.", false,
					Map.of("maximumRecords", HostProtocol.MAX_BATCH_RECORDS));
			return;
		}
		IncidentService.RecordBatch batch;
		try {
			List<IncidentService.RecordSubmission> submissions = new ArrayList<>();
			Instant acceptedAt = context.config.clock().instant();
			for (JsonNode record : records) {
				IncidentService.RecordSubmission submission =
						HostHttp.MAPPER.treeToValue(record, IncidentService.RecordSubmission.class);
				IncidentService.RecordSubmission attributed = withDeviceAttribution(submission, device, acceptedAt);
				System.err.println("ATTRIBUTED " + attributed.content());
				submissions.add(attributed);
			}
			String operationId = request.path("operationId").asText("");
			if (operationId.isBlank()) {
				throw new IllegalArgumentException("operationId is required");
			}
			batch = new IncidentService.RecordBatch(context.incident, operationId, submissions);
		} catch (IOException | RuntimeException exception) {
			HostHttp.error(exchange, 400, "invalid_request", "The submitted records are malformed.", false);
			return;
		}
		for (IncidentService.RecordSubmission submission : batch.records()) {
			HostPermission needed = switch (submission.operation()) {
				case CREATE -> HostPermission.WRITE;
				case UPDATE -> HostPermission.UPDATE;
				case STRIKE_OUT -> HostPermission.DELETE;
			};
			if (!require(exchange, device, needed)) {
				return;
			}
		}
		IncidentService.SubmissionResult result = context.service.submit(batch);
		HostHttp.send(exchange, result.applied() ? 200 : 409, HostHttp.MAPPER.valueToTree(result));
	}

	/**
	 * Adds host-verified identity and acceptance time to all submitted provenance.
	 *
	 * @param submission client record submission
	 * @param device device authenticated by the TLS certificate
	 * @param acceptedAt host acceptance time
	 * @return submission with host-authoritative provenance
	 */
	private IncidentService.RecordSubmission withDeviceAttribution(
			IncidentService.RecordSubmission submission, DeviceRegistry.DeviceRecord device, Instant acceptedAt) {
		JsonNode content = submission.content();
		JsonNode attributed = normalizeProvenance(content, device, acceptedAt, false);
		return new IncidentService.RecordSubmission(submission.key(), submission.parent(),
				submission.parentField(), submission.baseVersion(), submission.operation(), attributed);
	}

	/**
	 * Recursively replaces client-controlled metadata inside provenance objects.
	 *
	 * @param value submitted JSON value
	 * @param device authenticated device
	 * @param acceptedAt host acceptance time
	 * @param provenance true when the current object is provenance
	 * @return detached value with normalized provenance
	 */
	private JsonNode normalizeProvenance(JsonNode value, DeviceRegistry.DeviceRecord device, Instant acceptedAt,
			boolean provenance) {
		if (value == null) {
			return null;
		}
		if (value.isObject()) {
			ObjectNode copy = ((ObjectNode) value).deepCopy();
			List<String> fields = new ArrayList<>();
			copy.fieldNames().forEachRemaining(fields::add);
			for (String field : fields) {
				copy.set(field, normalizeProvenance(copy.get(field), device, acceptedAt, "provenance".equals(field)));
			}
			if (provenance) {
				copy.put("acceptedAt", acceptedAt.toString());
				copy.put("deviceId", device.deviceId());
				if (device.originalDeviceId() == null) {
					copy.remove("originalDeviceId");
				} else {
					copy.put("originalDeviceId", device.originalDeviceId());
				}
			}
			return copy;
		}
		if (value.isArray()) {
			com.fasterxml.jackson.databind.node.ArrayNode copy =
					(com.fasterxml.jackson.databind.node.ArrayNode) value.deepCopy();
			for (int index = 0; index < copy.size(); index++) {
				copy.set(index, normalizeProvenance(copy.get(index), device, acceptedAt, false));
			}
			return copy;
		}
		return value.deepCopy();
	}

	/**
	 * Serves administrator operations, which are limited to the host's own loopback credential.
	 *
	 * @param exchange exchange to answer
	 * @param device authenticated device
	 * @param path request path
	 * @throws IOException when the response cannot be written
	 */
	private void admin(HttpExchange exchange, DeviceRegistry.DeviceRecord device, String path) throws IOException {
		if (!device.admin() || !HostHttp.isLoopback(exchange)) {
			HostHttp.error(exchange, 403, "forbidden", "Administrator access is not available to this device.", false);
			return;
		}
		String method = exchange.getRequestMethod();
		if ("/v1/admin/invitations".equals(path) && "POST".equals(method)) {
			invitation(exchange);
		} else if (DEVICES.equals(path) && "GET".equals(method)) {
			devices(exchange);
		} else if ("/v1/admin/reauthorizations".equals(path) && "GET".equals(method)) {
			reauthorizations(exchange);
		} else if ("/v1/admin/reauthorizations/audit".equals(path) && "GET".equals(method)) {
			reauthorizationAudit(exchange);
		} else if (path.startsWith("/v1/admin/reauthorizations/") && path.endsWith(":approve")
				&& "POST".equals(method)) {
			decideReauthorization(exchange, path, true);
		} else if (path.startsWith("/v1/admin/reauthorizations/") && path.endsWith(":deny")
				&& "POST".equals(method)) {
			decideReauthorization(exchange, path, false);
		} else if (path.startsWith(DEVICES + "/") && path.endsWith(":revoke") && "POST".equals(method)) {
			String id = path.substring(DEVICES.length() + 1, path.length() - ":revoke".length());
			if (context.registry.revoke(id)) {
				ObjectNode body = HostHttp.MAPPER.createObjectNode();
				body.put("revoked", true);
				HostHttp.send(exchange, 200, body);
			} else {
				HostHttp.error(exchange, 404, "not_found", "No such device.", false);
			}
		} else {
			HostHttp.error(exchange, 404, "not_found", "No such resource.", false);
		}
	}

	/**
	 * Lists pending requests, including their queued work for administrator review.
	 *
	 * @param exchange exchange to answer
	 * @throws IOException when the response cannot be written
	 */
	private void reauthorizations(HttpExchange exchange) throws IOException {
		ArrayNode requests = HostHttp.MAPPER.createArrayNode();
		for (DeviceRegistry.ReauthorizationRequest request : context.registry.pendingRequests()) {
			ObjectNode item = HostHttp.MAPPER.createObjectNode();
			item.put("requestId", request.requestId());
			item.put("kind", request.kind().name().toLowerCase(Locale.ROOT));
			item.put("originalDeviceId", request.originalDeviceId());
			item.put("deviceName", request.deviceName());
			item.put("createdAt", request.createdAt().toString());
			item.set("outbox", HostHttp.MAPPER.readTree(request.outboxJson()));
			requests.add(item);
		}
		ObjectNode body = HostHttp.MAPPER.createObjectNode();
		body.set("requests", requests);
		HostHttp.send(exchange, 200, body);
	}

	/**
	 * Lists administrator decisions without exposing request tokens or device credentials.
	 *
	 * @param exchange exchange to answer
	 * @throws IOException when the response cannot be written
	 */
	private void reauthorizationAudit(HttpExchange exchange) throws IOException {
		ArrayNode audit = HostHttp.MAPPER.createArrayNode();
		for (DeviceRegistry.ApprovalAudit entry : context.registry.approvalAudit()) {
			ObjectNode item = HostHttp.MAPPER.createObjectNode();
			item.put("requestId", entry.requestId());
			item.put("decision", entry.decision().name().toLowerCase(Locale.ROOT));
			item.put("decidedAt", entry.decidedAt().toString());
			item.put("originalDeviceId", entry.originalDeviceId());
			if (entry.deviceId() != null) {
				item.put("deviceId", entry.deviceId());
			}
			audit.add(item);
		}
		ObjectNode body = HostHttp.MAPPER.createObjectNode();
		body.set("audit", audit);
		HostHttp.send(exchange, 200, body);
	}

	/**
	 * Approves or denies a pending request.
	 *
	 * @param exchange exchange to answer
	 * @param path request path
	 * @param approve true to approve, false to deny
	 * @throws IOException when the response cannot be written
	 */
	private void decideReauthorization(HttpExchange exchange, String path, boolean approve) throws IOException {
		String suffix = approve ? ":approve" : ":deny";
		String requestId = path.substring("/v1/admin/reauthorizations/".length(), path.length() - suffix.length());
		try {
			DeviceRegistry.ReauthorizationRequest request;
			if (approve) {
				request = context.registry.approveRequest(requestId, context.config.credentialLifetime(),
						context.periodClose(), (publicKey, deviceId, now, notAfter) ->
								context.authority.issueDevice(publicKey, deviceId, now, notAfter));
			} else {
				request = context.registry.denyRequest(requestId);
			}
			ObjectNode body = HostHttp.MAPPER.createObjectNode();
			body.put("state", request.state().name().toLowerCase(Locale.ROOT));
			if (approve) {
				body.put("deviceId", request.deviceId());
			}
			HostHttp.send(exchange, 200, body);
		} catch (InvitationRejectedException rejected) {
			HostHttp.error(exchange, 409, "request_not_pending", "The request is no longer pending.", false);
		} catch (HostStorageException failure) {
			context.fail("Device registry storage failed");
			HostHttp.error(exchange, 503, "service_unavailable", "The host cannot update approvals right now.", true);
		}
	}

	/**
	 * Issues an invitation for the shared incident.
	 *
	 * @param exchange exchange to answer
	 * @throws IOException when the response cannot be written
	 */
	private void invitation(HttpExchange exchange) throws IOException {
		Instant close = context.periodClose();
		if (close != null && !context.config.clock().instant().isBefore(close)) {
			HostHttp.error(exchange, 409, "period_closed",
					"The operational period has closed; credentials cannot be issued.", false);
			return;
		}
		DeviceRegistry.IssuedInvitation issued = context.registry.issueInvitation(context.incident,
				context.config.invitationLifetime());
		HostInvitation invitation = new HostInvitation(context.config.advertisedHost(), context.port(),
				context.enrollmentPort(), context.authority.fingerprint(), context.incident.value(), issued.secret(),
				issued.expiresAt());
		ObjectNode body = HostHttp.MAPPER.createObjectNode();
		body.put("invitationId", issued.invitationId());
		body.put("expiresAt", issued.expiresAt().toString());
		body.put("invitation", invitation.toText());
		HostHttp.send(exchange, 201, body);
	}

	/**
	 * Lists admitted devices with credential state and last contact.
	 *
	 * @param exchange exchange to answer
	 * @throws IOException when the response cannot be written
	 */
	private void devices(HttpExchange exchange) throws IOException {
		Instant now = context.config.clock().instant();
		Instant cutoff = now.minus(context.config.connectedWindow());
		ArrayNode array = HostHttp.MAPPER.createArrayNode();
		for (DeviceRegistry.DeviceRecord record : context.registry.devices()) {
			Instant contact = context.registry.lastContact(record.deviceId()).orElse(null);
			CredentialState state = context.effectiveState(record);
			array.add(HostHttp.MAPPER.valueToTree(new DeviceInfo(record.deviceId(), record.name(), record.issuedAt(),
					record.expiresAt(), state, contact,
					state == CredentialState.ACTIVE && contact != null && contact.isAfter(cutoff))));
		}
		ObjectNode body = HostHttp.MAPPER.createObjectNode();
		body.set("devices", array);
		HostHttp.send(exchange, 200, body);
	}

	/**
	 * Maps a service or storage failure to the structured error defined by the contract. Messages for server-side
	 * failures are generic so no internal detail or incident content reaches a remote caller.
	 *
	 * @param exchange exchange to answer
	 * @param failure failure to map
	 * @throws IOException when the response cannot be written
	 */
	private void failure(HttpExchange exchange, RuntimeException failure) throws IOException {
		if (failure instanceof StoreLockConflictException) {
			HostHttp.error(exchange, 423, "locked", "The incident is locked by another editor.", true);
		} else if (failure instanceof StoreConflictException) {
			HostHttp.error(exchange, 409, "conflict", "The submission conflicts with the current incident state.",
					false);
		} else if (failure instanceof IncidentNotFoundException) {
			HostHttp.error(exchange, 404, "not_found", "The incident was not found.", false);
		} else if (failure instanceof StoreValidationException || failure instanceof IllegalArgumentException) {
			HostHttp.error(exchange, 400, "invalid_request", String.valueOf(failure.getMessage()), false);
		} else if (failure instanceof StoreCommittedException) {
			context.noteFailure("Incident file projection failed after a durable commit");
			LOGGER.log(Level.WARNING, "Projection failed after commit", failure);
			HostHttp.error(exchange, 500, "committed_not_projected", StoreCommittedException.MESSAGE, true);
		} else if (failure instanceof StoreUnavailableException) {
			context.noteFailure("Incident storage is unavailable");
			HostHttp.error(exchange, 503, "service_unavailable", "The incident service is temporarily unavailable.",
					true);
		} else if (failure instanceof HostStorageException) {
			context.fail("Host-owned storage failed");
			LOGGER.log(Level.SEVERE, "Host-owned storage failed", failure);
			HostHttp.error(exchange, 503, "service_unavailable", "The incident service is temporarily unavailable.",
					true);
		} else {
			context.noteFailure("Incident storage failed: " + failure.getClass().getSimpleName());
			LOGGER.log(Level.WARNING, "Host request failed", failure);
			HostHttp.error(exchange, 500, failure instanceof StorePersistenceException ? "persistence_failure"
					: "internal_error", "The host could not process the request.", true);
		}
	}
}
