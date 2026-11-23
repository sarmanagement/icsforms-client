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
import java.time.Duration;
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
			} else if ("/v1/pairing/short/begin".equals(path) || "/v1/pairing/short/finish".equals(path)) {
				if (!"POST".equals(method)) {
					HostHttp.error(exchange, 405, "method_not_allowed", "Use POST.", false);
				} else if ("/v1/pairing/short/begin".equals(path)) {
					shortBegin(exchange);
				} else {
					shortFinish(exchange);
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
		ObjectNode suites = body.putObject("pairingSuites");
		suites.set("pake", HostHttp.MAPPER.valueToTree(context.suites.pakeIds()));
		suites.set("channel", HostHttp.MAPPER.valueToTree(context.suites.channelIds()));
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
	 * Starts a short-code exchange: negotiates suites, runs the host's first two PAKE rounds against the
	 * device's first message, and returns the host's messages. Nothing about the code is revealed.
	 *
	 * @param exchange exchange to answer
	 * @throws IOException when the response cannot be written
	 */
	private void shortBegin(HttpExchange exchange) throws IOException {
		if (!context.pairingGlobal.tryAcquire("global")
				|| !context.pairingPerAddress.tryAcquire(HostHttp.remoteKey(exchange))) {
			rateLimited(exchange);
			return;
		}
		JsonNode request = readShortRequest(exchange);
		if (request == null) {
			return;
		}
		String pakeId = supported(request.path("suite").asText(""), context.suites.pakeIds());
		String channelId = supported(request.path("channel").asText(""), context.suites.channelIds());
		byte[] clientRound1 = decode(request.path("round1").asText(""));
		char[] code = context.shortPairing.codeChars();
		if (pakeId == null || channelId == null || clientRound1 == null || code == null) {
			HostHttp.error(exchange, 403, "invitation_invalid", INVITATION_INVALID, false);
			return;
		}
		try {
			org.sarmanagement.icsforms.host.pairing.PakeExchange host = context.suites.pake(pakeId).orElseThrow()
					.start(org.sarmanagement.icsforms.host.pairing.PakeSuite.Role.SERVER, code);
			byte[] hostRound1 = host.round1();
			byte[] hostRound2 = host.round2(clientRound1);
			byte[][] transcript = {pakeId.getBytes(java.nio.charset.StandardCharsets.UTF_8),
					channelId.getBytes(java.nio.charset.StandardCharsets.UTF_8), clientRound1, hostRound1, hostRound2};
			ShortPairing.Session session = context.shortPairing.begin(host,
					context.suites.channel(channelId).orElseThrow(), pakeId, transcript);
			if (session == null) {
				HostHttp.error(exchange, 403, "invitation_invalid", INVITATION_INVALID, false);
				return;
			}
			ObjectNode body = HostHttp.MAPPER.createObjectNode();
			body.put("sessionId", session.id());
			body.put("suite", pakeId);
			body.put("channel", channelId);
			body.put("round1", java.util.Base64.getEncoder().encodeToString(hostRound1));
			body.put("round2", java.util.Base64.getEncoder().encodeToString(hostRound2));
			HostHttp.send(exchange, 200, body);
		} catch (GeneralSecurityException malformed) {
			HostHttp.error(exchange, 403, "invitation_invalid", INVITATION_INVALID, false);
		} finally {
			java.util.Arrays.fill(code, '\0');
		}
	}

	/**
	 * Finishes a short-code exchange: verifies the device's key confirmation, which binds the host identity the
	 * device observed, then admits the device whose certificate request arrived sealed under the exchange key. A
	 * failed confirmation counts against the window.
	 *
	 * @param exchange exchange to answer
	 * @throws IOException when the response cannot be written
	 */
	private void shortFinish(HttpExchange exchange) throws IOException {
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
		JsonNode request = readShortRequest(exchange);
		if (request == null) {
			return;
		}
		ShortPairing.Session session = context.shortPairing.take(request.path("sessionId").asText(""));
		byte[] clientRound2 = decode(request.path("round2").asText(""));
		byte[] confirmation = decode(request.path("confirmation").asText(""));
		byte[] sealed = decode(request.path("sealed").asText(""));
		if (session == null || clientRound2 == null || confirmation == null || sealed == null) {
			HostHttp.error(exchange, 403, "invitation_invalid", INVITATION_INVALID, false);
			return;
		}
		byte[] hostIdentity = context.authority.fingerprint().getBytes(java.nio.charset.StandardCharsets.UTF_8);
		org.sarmanagement.icsforms.host.pairing.ChannelSuite.PairingChannel channel;
		JsonNode payload;
		try {
			byte[] key = session.exchange().complete(clientRound2);
			byte[][] parts = java.util.Arrays.copyOf(session.transcript(), session.transcript().length + 1);
			parts[parts.length - 1] = clientRound2;
			channel = session.channelSuite().open(key, org.sarmanagement.icsforms.host.pairing.PairingTranscript.of(parts));
			if (!channel.verifyConfirmation("client", hostIdentity, confirmation)) {
				context.shortPairing.failed();
				LOGGER.log(Level.INFO, "Short-code pairing rejected: confirmation failed");
				HostHttp.error(exchange, 403, "invitation_invalid", INVITATION_INVALID, false);
				return;
			}
			payload = HostHttp.MAPPER.readTree(channel.unseal("request", sealed));
		} catch (GeneralSecurityException | IOException failure) {
			context.shortPairing.failed();
			HostHttp.error(exchange, 403, "invitation_invalid", INVITATION_INVALID, false);
			return;
		}
		PublicKey deviceKey;
		try {
			deviceKey = HostCrypto.verifyRequest(payload.path("certificateRequest").asText(""));
		} catch (GeneralSecurityException | IllegalArgumentException exception) {
			HostHttp.error(exchange, 400, "invalid_request", "The pairing request is malformed.", false);
			return;
		}
		DeviceRegistry.Admission admission;
		try {
			DeviceRegistry.IssuedInvitation internal = context.registry.issueInvitation(context.incident,
					Duration.ofMinutes(1));
			admission = context.registry.redeem(internal.secret(), context.incident, context.incident,
					sanitizeName(payload.path("deviceName").asText("")), context.config.credentialLifetime(),
					context.periodClose(), (deviceId, now, notAfter) -> context.authority.issueDevice(deviceKey,
							deviceId, now, notAfter));
		} catch (InvitationRejectedException rejected) {
			LOGGER.log(Level.INFO, "Short-code pairing rejected: {0}", rejected.getMessage());
			HostHttp.error(exchange, 403, "invitation_invalid", INVITATION_INVALID, false);
			return;
		} catch (HostStorageException failure) {
			context.fail("Device registry storage failed");
			LOGGER.log(Level.SEVERE, "Device registry storage failed during pairing", failure);
			HostHttp.error(exchange, 503, "service_unavailable", "The host cannot admit devices right now.", true);
			return;
		}
		context.shortPairing.close();
		ObjectNode result = HostHttp.MAPPER.createObjectNode();
		result.put("deviceId", admission.device().deviceId());
		result.put("incidentId", context.incident.value());
		result.put("certificate", HostCrypto.pem(admission.certificate()));
		result.put("caCertificate", context.authority.certificatePem());
		result.put("expiresAt", admission.device().expiresAt().toString());
		result.put("port", context.port());
		ObjectNode body = HostHttp.MAPPER.createObjectNode();
		try {
			body.put("confirmation", java.util.Base64.getEncoder()
					.encodeToString(channel.confirmation("server", hostIdentity)));
			body.put("sealed", java.util.Base64.getEncoder()
					.encodeToString(channel.seal("result", HostHttp.MAPPER.writeValueAsBytes(result))));
		} catch (GeneralSecurityException failure) {
			HostHttp.error(exchange, 500, "internal_error", "The host could not process the request.", true);
			return;
		}
		LOGGER.log(Level.INFO, "Device admitted by short code: {0}", admission.device().deviceId());
		HostHttp.send(exchange, 200, body);
	}

	/**
	 * Reads a bounded JSON object for the short-code exchange.
	 *
	 * @param exchange exchange to answer on failure
	 * @return the object, or null after an error response
	 * @throws IOException when the body cannot be read
	 */
	private JsonNode readShortRequest(HttpExchange exchange) throws IOException {
		byte[] raw = HostHttp.readBody(exchange, HostProtocol.MAX_PAIRING_BYTES);
		return raw == null ? null : HostHttp.parseObject(exchange, raw);
	}

	/**
	 * Accepts a suite identifier only when this host supports it.
	 *
	 * @param requested identifier the device chose
	 * @param supported identifiers this host supports
	 * @return the identifier, or null when unsupported
	 */
	private static String supported(String requested, java.util.List<String> supported) {
		return supported.contains(requested) ? requested : null;
	}

	/**
	 * Decodes Base64 text.
	 *
	 * @param text Base64 text
	 * @return bytes, or null when blank or malformed
	 */
	private static byte[] decode(String text) {
		try {
			return text.isBlank() ? null : java.util.Base64.getDecoder().decode(text);
		} catch (IllegalArgumentException malformed) {
			return null;
		}
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
