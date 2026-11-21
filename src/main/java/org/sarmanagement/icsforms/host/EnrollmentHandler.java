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
