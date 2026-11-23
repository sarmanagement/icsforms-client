/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.host;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.sarmanagement.icsforms.persistence.IncidentId;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The only unauthenticated surface: a status signal and the pairing endpoint. Status reveals nothing about incident
 * contents, and pairing only exchanges a valid invitation for a device certificate; it never grants incident access.
 */
final class EnrollmentHandler implements HttpHandler {
	private static final Logger LOGGER = Logger.getLogger(EnrollmentHandler.class.getName());
	private static final int MAX_NAME_LENGTH = 64;
	private static final String INVITATION_INVALID = "The invitation is invalid, expired or already used.";
	private final HostContext context;

	/**
	 * Creates the handler.
	 *
	 * @param context shared session state
	 */
	EnrollmentHandler(HostContext context) {
		this.context = context;
	}

	/** {@inheritDoc} */
	@Override
	public void handle(HttpExchange exchange) throws IOException {
		try {
			String path = exchange.getRequestURI().getPath();
			String method = exchange.getRequestMethod();
			if ("/v1/status".equals(path)) {
				if (!"GET".equals(method)) {
					HostHttp.error(exchange, 405, "method_not_allowed", "Use GET.", false);
				} else if (!context.statusPerAddress.tryAcquire(HostHttp.remoteKey(exchange))) {
					rateLimited(exchange);
				} else {
					status(exchange);
				}
			} else if ("/v1/status/credential".equals(path)) {
				if (!"POST".equals(method)) {
					HostHttp.error(exchange, 405, "method_not_allowed", "Use POST.", false);
				} else if (!context.statusPerAddress.tryAcquire(HostHttp.remoteKey(exchange))) {
					rateLimited(exchange);
				} else {
					credentialStatus(exchange);
				}
			} else if ("/v1/pairing/redeem".equals(path)) {
				if (!"POST".equals(method)) {
					HostHttp.error(exchange, 405, "method_not_allowed", "Use POST.", false);
				} else {
					redeem(exchange);
				}
			} else if ("/v1/reauthorization/renew".equals(path)) {
				if (!"POST".equals(method)) {
					HostHttp.error(exchange, 405, "method_not_allowed", "Use POST.", false);
				} else {
					requestRenewal(exchange);
				}
			} else if ("/v1/reauthorization/repair".equals(path)) {
				if (!"POST".equals(method)) {
					HostHttp.error(exchange, 405, "method_not_allowed", "Use POST.", false);
				} else {
					requestRepair(exchange);
				}
			} else if ("/v1/reauthorization/status".equals(path)) {
				if (!"POST".equals(method)) {
					HostHttp.error(exchange, 405, "method_not_allowed", "Use POST.", false);
				} else if (!context.statusPerAddress.tryAcquire(HostHttp.remoteKey(exchange))) {
					rateLimited(exchange);
				} else {
					reauthorizationStatus(exchange);
				}
			} else {
				HostHttp.error(exchange, 404, "not_found", "No such resource.", false);
			}
		} catch (RuntimeException failure) {
			LOGGER.log(Level.WARNING, "Enrollment request failed: {0}", failure.getClass().getSimpleName());
			context.noteFailure("Enrollment request failed: " + failure.getClass().getSimpleName());
			HostHttp.error(exchange, 500, "internal_error", "The host could not process the request.", true);
		} finally {
			exchange.close();
		}
	}

	/**
	 * Answers the deliberate pre-authentication status signal.
	 *
	 * @param exchange exchange to answer
	 * @throws IOException when the response cannot be written
	 */
	private void status(HttpExchange exchange) throws IOException {
		ObjectNode body = HostHttp.MAPPER.createObjectNode();
		body.put("apiVersion", HostProtocol.API_VERSION);
		body.set("supportedApiVersions", HostHttp.MAPPER.valueToTree(HostProtocol.SUPPORTED_API_VERSIONS));
		body.put("minimumClientVersion", context.config.minimumClientVersion());
		body.put("hostState", context.state.get().name().toLowerCase(Locale.ROOT));
		body.put("hostFingerprint", context.authority.fingerprint());
		HostHttp.send(exchange, 200, body);
	}

	/**
	 * Reports whether a credential, identified by its certificate fingerprint, is active, expired, revoked or unknown.
	 *
	 * @param exchange exchange to answer
	 * @throws IOException when the response cannot be written
	 */
	private void credentialStatus(HttpExchange exchange) throws IOException {
		byte[] raw = HostHttp.readBody(exchange, HostProtocol.MAX_PAIRING_BYTES);
		if (raw == null) {
			return;
		}
		JsonNode request = HostHttp.parseObject(exchange, raw);
		if (request == null) {
			return;
		}
		String fingerprint = request.path("certificateFingerprint").asText("");
		CredentialState state = fingerprint.matches("[0-9a-f]{64}")
				? context.registry.byFingerprint(fingerprint).map(context::effectiveState)
						.orElse(CredentialState.UNKNOWN)
				: CredentialState.UNKNOWN;
		ObjectNode body = HostHttp.MAPPER.createObjectNode();
		body.put("state", state.name().toLowerCase(Locale.ROOT));
		HostHttp.send(exchange, 200, body);
	}

	/**
	 * Redeems an invitation for a device certificate.
	 *
	 * @param exchange exchange to answer
	 * @throws IOException when the response cannot be written
	 */
	private void redeem(HttpExchange exchange) throws IOException {
		if (!context.pairingGlobal.tryAcquire("global")
				|| !context.pairingPerAddress.tryAcquire(HostHttp.remoteKey(exchange))) {
			rateLimited(exchange);
			return;
		}
		if (context.state.get() != HostState.RUNNING) {
			HostHttp.error(exchange, 503, "service_unavailable", "The host is not accepting new devices.", true,
					Map.of("hostState", context.state.get().name().toLowerCase(Locale.ROOT)));
			return;
		}
		byte[] raw = HostHttp.readBody(exchange, HostProtocol.MAX_PAIRING_BYTES);
		if (raw == null) {
			return;
		}
		JsonNode request = HostHttp.parseObject(exchange, raw);
		if (request == null) {
			return;
		}
		PublicKey deviceKey;
		IncidentId requested;
		try {
			deviceKey = HostCrypto.verifyRequest(request.path("certificateRequest").asText(""));
			requested = IncidentId.of(request.path("incidentId").asText(""));
		} catch (GeneralSecurityException | IllegalArgumentException exception) {
			HostHttp.error(exchange, 400, "invalid_request", "The pairing request is malformed.", false);
			return;
		}
		String name = sanitizeName(request.path("deviceName").asText(""));
		DeviceRegistry.Admission admission;
		try {
			admission = context.registry.redeem(request.path("invitation").asText(""), requested, context.incident, name,
					context.config.credentialLifetime(), context.periodClose(), (deviceId, now, notAfter) -> context.authority.issueDevice(deviceKey, deviceId, now, notAfter));
		} catch (InvitationRejectedException rejected) {
			LOGGER.log(Level.INFO, "Pairing rejected: {0}", rejected.getMessage());
			HostHttp.error(exchange, 403, "invitation_invalid", INVITATION_INVALID, false);
			return;
		} catch (HostStorageException failure) {
			context.fail("Device registry storage failed");
			LOGGER.log(Level.SEVERE, "Device registry storage failed during pairing", failure);
			HostHttp.error(exchange, 503, "service_unavailable", "The host cannot admit devices right now.", true);
			return;
		}
		ObjectNode body = HostHttp.MAPPER.createObjectNode();
		body.put("deviceId", admission.device().deviceId());
		body.put("incidentId", context.incident.value());
		body.put("certificate", HostCrypto.pem(admission.certificate()));
		body.put("caCertificate", context.authority.certificatePem());
		body.put("expiresAt", admission.device().expiresAt().toString());
		LOGGER.log(Level.INFO, "Device admitted: {0}", admission.device().deviceId());
		HostHttp.send(exchange, 200, body);
	}

	/**
	 * Queues same-key renewal of an expired device for administrator approval.
	 *
	 * @param exchange exchange to answer
	 * @throws IOException when the response cannot be written
	 */
	private void requestRenewal(HttpExchange exchange) throws IOException {
		if (!allowReauthorization(exchange)) {
			return;
		}
		JsonNode request = readReauthorizationRequest(exchange);
		if (request == null) {
			return;
		}
		try {
			X509Certificate certificate = HostCrypto.parseCertificate(request.path("certificate").asText(""));
			java.security.PublicKey key = HostCrypto.verifyRequest(request.path("certificateRequest").asText(""));
			String fingerprint = request.path("certificateFingerprint").asText("");
			String deviceId = request.path("deviceId").asText("");
			DeviceRegistry.DeviceRecord device = context.registry.byFingerprint(fingerprint).orElse(null);
			if (device == null || !device.deviceId().equals(deviceId)
					|| context.effectiveState(device) != CredentialState.EXPIRED) {
				throw new InvitationRejectedException("renewal not eligible");
			}
			DeviceRegistry.SubmittedRequest submitted = context.registry.requestRenewal(deviceId, fingerprint,
					certificate, key, sanitizeName(request.path("deviceName").asText("")),
					HostHttp.MAPPER.writeValueAsString(request.path("pendingOutbox")), context.periodClose());
			sendSubmitted(exchange, submitted);
		} catch (GeneralSecurityException | IOException | InvitationRejectedException failure) {
			HostHttp.error(exchange, 403, "reauthorization_unavailable",
					"The credential cannot request renewal.", false);
		}
	}

	/**
	 * Queues a new invitation-backed identity for a revoked device's retained outbox.
	 *
	 * @param exchange exchange to answer
	 * @throws IOException when the response cannot be written
	 */
	private void requestRepair(HttpExchange exchange) throws IOException {
		if (!allowReauthorization(exchange)) {
			return;
		}
		JsonNode request = readReauthorizationRequest(exchange);
		if (request == null) {
			return;
		}
		try {
			java.security.PublicKey key = HostCrypto.verifyRequest(request.path("certificateRequest").asText(""));
			IncidentId incident = IncidentId.of(request.path("incidentId").asText(""));
			DeviceRegistry.SubmittedRequest submitted = context.registry.requestRepair(
					request.path("invitation").asText(""), incident, context.incident,
					request.path("originalDeviceId").asText(""),
					sanitizeName(request.path("deviceName").asText("")),
					key, HostHttp.MAPPER.writeValueAsString(request.path("pendingOutbox")));
			sendSubmitted(exchange, submitted);
		} catch (GeneralSecurityException | IllegalArgumentException | IOException
				| InvitationRejectedException failure) {
			HostHttp.error(exchange, 403, "reauthorization_unavailable",
					"The request cannot be approved with these credentials.", false);
		}
	}

	/**
	 * Reads a bounded re-authorization body.
	 *
	 * @param exchange exchange to read
	 * @return request object, or null after sending an error
	 * @throws IOException when the body cannot be read
	 */
	private JsonNode readReauthorizationRequest(HttpExchange exchange) throws IOException {
		byte[] raw = HostHttp.readBody(exchange, HostProtocol.MAX_REAUTHORIZATION_BYTES);
		return raw == null ? null : HostHttp.parseObject(exchange, raw);
	}

	/**
	 * Applies global and per-address limits to queue submissions.
	 *
	 * @param exchange exchange to answer
	 * @return true when the request may proceed
	 * @throws IOException when the response cannot be written
	 */
	private boolean allowReauthorization(HttpExchange exchange) throws IOException {
		if (!context.reauthorizationGlobal.tryAcquire("global")
				|| !context.reauthorizationPerAddress.tryAcquire(HostHttp.remoteKey(exchange))) {
			rateLimited(exchange);
			return false;
		}
		if (context.state.get() != HostState.RUNNING) {
			HostHttp.error(exchange, 503, "service_unavailable", "The host is not accepting requests.", true);
			return false;
		}
		return true;
	}

	/**
	 * Sends the request identifier and its one-time polling token.
	 *
	 * @param exchange exchange to answer
	 * @param submitted accepted request
	 * @throws IOException when the response cannot be written
	 */
	private void sendSubmitted(HttpExchange exchange, DeviceRegistry.SubmittedRequest submitted) throws IOException {
		ObjectNode body = HostHttp.MAPPER.createObjectNode();
		body.put("requestId", submitted.request().requestId());
		body.put("statusToken", submitted.token());
		body.put("state", "waiting_for_approval");
		HostHttp.send(exchange, 202, body);
	}

	/**
	 * Returns only the decision for the request identified by a bearer token.
	 *
	 * @param exchange exchange to answer
	 * @throws IOException when the response cannot be written
	 */
	private void reauthorizationStatus(HttpExchange exchange) throws IOException {
		byte[] raw = HostHttp.readBody(exchange, HostProtocol.MAX_PAIRING_BYTES);
		if (raw == null) {
			return;
		}
		JsonNode body = HostHttp.parseObject(exchange, raw);
		if (body == null) {
			return;
		}
		DeviceRegistry.ReauthorizationRequest request = context.registry
				.requestForToken(body.path("statusToken").asText("")).orElse(null);
		if (request == null) {
			HostHttp.error(exchange, 404, "not_found", "No such request.", false);
			return;
		}
		ObjectNode response = HostHttp.MAPPER.createObjectNode();
		response.put("state", switch (request.state()) {
			case PENDING -> "waiting_for_approval";
			case DENIED -> "denied";
			case APPROVED -> "approved";
		});
		if (request.state() == DeviceRegistry.ApprovalState.APPROVED) {
			response.put("deviceId", request.deviceId());
			response.put("certificate", request.certificate());
			response.put("caCertificate", context.authority.certificatePem());
			response.put("expiresAt", context.registry.devices().stream()
					.filter(device -> device.deviceId().equals(request.deviceId())).findFirst()
					.map(device -> device.expiresAt().toString()).orElse(""));
		}
		HostHttp.send(exchange, 200, response);
	}

	/**
	 * Sends the rate-limit error.
	 *
	 * @param exchange exchange to answer
	 * @throws IOException when the response cannot be written
	 */
	private void rateLimited(HttpExchange exchange) throws IOException {
		exchange.getResponseHeaders().set("Retry-After", "60");
		HostHttp.error(exchange, 429, "rate_limited", "Too many requests; try again later.", true);
	}

	/**
	 * Normalizes an operator-visible device name.
	 *
	 * @param raw name supplied by the device
	 * @return printable name of limited length
	 */
	private static String sanitizeName(String raw) {
		String cleaned = raw.replaceAll("\\p{Cntrl}", " ").strip();
		if (cleaned.isEmpty()) {
			cleaned = "Unnamed device";
		}
		return cleaned.length() > MAX_NAME_LENGTH ? cleaned.substring(0, MAX_NAME_LENGTH) : cleaned;
	}
}
