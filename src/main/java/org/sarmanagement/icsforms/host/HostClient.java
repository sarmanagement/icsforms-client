/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.host;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletionException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManager;

/**
 * Minimal HTTPS client for the host API. It pins the host authority, presents the device certificate when it has
 * one, and classifies failures so callers can distinguish an expired or revoked credential, an unreachable host and
 * a changed host identity. Used for pairing, the host's own loopback administration, and diagnostics; the full
 * remote store arrives in a later part of the series.
 */
public final class HostClient {
	private static final Duration TIMEOUT = Duration.ofSeconds(10);
	private static final Duration CONFIRMATION_WAIT = Duration.ofSeconds(120);
	private static final Duration POLL_INTERVAL = Duration.ofSeconds(1);
	private final String host;
	private final int port;
	private final int enrollmentPort;
	private final HttpClient http;
	private final String hostFingerprint;

	/**
	 * In-memory proof and key material for a pending administrator decision.
	 */
	public static final class ReauthorizationTicket {
		private final String host;
		private final int enrollmentPort;
		private final String hostFingerprint;
		private final String token;
		private final String requestId;
		private final KeyPair keys;
		private final String deviceName;

		/**
		 * Creates a polling ticket.
		 *
		 * @param host host name
		 * @param enrollmentPort host enrollment port
		 * @param hostFingerprint pinned host identity
		 * @param token one-time status token
		 * @param keys device key pair
		 * @param deviceName device name
		 */
		private ReauthorizationTicket(String host, int enrollmentPort, String hostFingerprint, String token,
				String requestId, KeyPair keys, String deviceName) {
			this.host = host;
			this.enrollmentPort = enrollmentPort;
			this.hostFingerprint = hostFingerprint;
			this.token = token;
			this.requestId = requestId;
			this.keys = keys;
			this.deviceName = deviceName;
		}

		/**
		 * Redacts the bearer token and private key from diagnostic output.
		 *
		 * @return text that omits secrets
		 */
		@Override
		public String toString() {
			return "ReauthorizationTicket[waiting for administrator approval]";
		}

		/**
		 * Returns the public request identifier.
		 *
		 * @return request identifier
		 */
		public String requestId() {
			return requestId;
		}
	}

	/**
	 * Result of checking a re-authorization request.
	 *
	 * @param state waiting_for_approval, denied or approved
	 * @param credential approved device credential, or null until approved
	 */
	public record ReauthorizationResult(String state, DeviceCredential credential) {
	}

	/**
	 * Creates a client.
	 *
	 * @param host host name or address
	 * @param port mutual-TLS port
	 * @param enrollmentPort enrollment port
	 * @param hostFingerprint pinned host authority fingerprint
	 * @param context TLS context that pins the host
	 */
	private HostClient(String host, int port, int enrollmentPort, String hostFingerprint, SSLContext context) {
		this.host = host;
		this.port = port;
		this.enrollmentPort = enrollmentPort;
		this.hostFingerprint = hostFingerprint;
		this.http = HttpClient.newBuilder().sslContext(context).connectTimeout(TIMEOUT)
				.version(HttpClient.Version.HTTP_1_1).build();
	}

	/**
	 * Creates a client for a paired device.
	 *
	 * @param host host name or address
	 * @param port mutual-TLS port
	 * @param enrollmentPort enrollment port
	 * @param credential device credential
	 * @return client
	 */
	public static HostClient forDevice(String host, int port, int enrollmentPort, DeviceCredential credential) {
		return new HostClient(host, port, enrollmentPort, credential.hostFingerprint(), credential.sslContext());
	}

	/**
	 * Creates a client that has no device certificate, for pairing and status checks only.
	 *
	 * @param invitation invitation that carries the host address and pinned fingerprint
	 * @return client
	 */
	public static HostClient anonymous(HostInvitation invitation) {
		return anonymous(invitation.host(), invitation.port(), invitation.enrollmentPort(),
				invitation.hostFingerprint());
	}

	/**
	 * Creates a client that has no device certificate, for status checks only.
	 *
	 * @param host host name or address
	 * @param port mutual-TLS port
	 * @param enrollmentPort enrollment port
	 * @param hostFingerprint pinned host authority fingerprint
	 * @return client
	 */
	public static HostClient anonymous(String host, int port, int enrollmentPort, String hostFingerprint) {
		try {
			SSLContext context = SSLContext.getInstance("TLSv1.3");
			context.init(null, new TrustManager[] {new PinnedHostTrust(hostFingerprint)}, null);
			return new HostClient(host, port, enrollmentPort, hostFingerprint, context);
		} catch (GeneralSecurityException exception) {
			throw new IllegalStateException("Cannot create TLS context", exception);
		}
	}

	/**
	 * Redeems an invitation: generates a key pair on this device, has the host sign a certificate for it, and
	 * returns the resulting credential.
	 *
	 * @param invitation invitation text from the host operator
	 * @param deviceName name shown to the host operator
	 * @return the device credential
	 * @throws HostClientException when pairing fails
	 */
	public static DeviceCredential pair(HostInvitation invitation, String deviceName) {
		HostClient client = anonymous(invitation);
		KeyPair keys = HostCrypto.generateKeyPair();
		ObjectNode body = HostHttp.MAPPER.createObjectNode();
		body.put("invitation", invitation.secret());
		body.put("incidentId", invitation.incidentId());
		body.put("deviceName", deviceName);
		body.put("certificateRequest", HostCrypto.createRequest(keys, deviceName));
		JsonNode response = client.post(client.enrollmentUri("/v1/pairing/redeem"), body.toString());
		return buildCredential(keys, response, invitation.hostFingerprint());
	}

	/**
	 * Builds the device credential from an admission response, requiring the returned authority to be the pinned
	 * one.
	 *
	 * @param keys the device key pair the certificate was issued for
	 * @param response admission response with certificate, authority certificate, device id and expiry
	 * @param fingerprint host authority fingerprint the device pins
	 * @return the device credential
	 * @throws HostClientException when the response is unusable or names a different authority
	 */
	private static DeviceCredential buildCredential(KeyPair keys, JsonNode response, String fingerprint) {
		try {
			X509Certificate certificate = HostCrypto.parseCertificate(response.path("certificate").asText(""));
			X509Certificate authority = HostCrypto.parseCertificate(response.path("caCertificate").asText(""));
			if (!fingerprint.equalsIgnoreCase(HostCrypto.fingerprint(authority))) {
				throw new HostClientException(HostClientException.Kind.IDENTITY_CHANGED, 0, null,
						"The host identity does not match the invitation.", null);
			}
			char[] password = HostCrypto.sha256(Instant.now() + HostCrypto.fingerprint(certificate)).toCharArray();
			KeyStore store = KeyStore.getInstance("PKCS12");
			store.load(null, null);
			store.setKeyEntry(DeviceCredential.KEY_ALIAS, keys.getPrivate(), password,
					new X509Certificate[] {certificate, authority});
			store.setCertificateEntry(DeviceCredential.CA_ALIAS, authority);
			return new DeviceCredential(store, password, fingerprint, response.path("deviceId").asText(),
					Instant.parse(response.path("expiresAt").asText()));
		} catch (GeneralSecurityException | IOException | RuntimeException exception) {
			if (exception instanceof HostClientException hostFailure) {
				throw hostFailure;
			}
			throw new HostClientException(HostClientException.Kind.REJECTED, 200, null,
					"The host returned an unusable certificate.", exception);
		}
	}

	/**
	 * Fetches the pre-authentication status signal.
	 *
	 * @return status document
	 * @throws HostClientException when the host cannot be reached or its identity differs
	 */
	public JsonNode status() {
		return get(enrollmentUri("/v1/status"));
	}

	/**
	 * Asks the host whether a credential is active, expired, revoked or unknown.
	 *
	 * @param credential credential to check
	 * @return state name in lower case
	 * @throws HostClientException when the host cannot be reached or its identity differs
	 */
	public String credentialState(DeviceCredential credential) {
		ObjectNode body = HostHttp.MAPPER.createObjectNode();
		body.put("certificateFingerprint", credential.certificateFingerprint());
		return post(enrollmentUri("/v1/status/credential"), body.toString()).path("state").asText("unknown");
	}

	/**
	 * Requests approval to renew an expired credential with its existing key pair.
	 *
	 * @param credential expired device credential
	 * @param deviceName device name shown to the administrator
	 * @param pendingOutbox full pending operation array for administrator review
	 * @return ticket to poll while retaining the local outbox
	 */
	public ReauthorizationTicket requestRenewal(DeviceCredential credential, String deviceName,
			JsonNode pendingOutbox) {
		if (!hostFingerprint.equals(credential.hostFingerprint())) {
			throw new IllegalArgumentException("The credential belongs to a different host");
		}
		if (pendingOutbox == null || !pendingOutbox.isArray()) {
			throw new IllegalArgumentException("Pending outbox must be an array");
		}
		KeyPair keys = credential.keyPair();
		ObjectNode body = HostHttp.MAPPER.createObjectNode();
		body.put("deviceId", credential.deviceId());
		body.put("certificateFingerprint", credential.certificateFingerprint());
		body.put("certificate", HostCrypto.pem(credential.certificate()));
		body.put("certificateRequest", HostCrypto.createRequest(keys, deviceName));
		body.put("deviceName", deviceName);
		body.set("pendingOutbox", pendingOutbox);
		JsonNode response = post(enrollmentUri("/v1/reauthorization/renew"), body.toString());
		return ticket(response, keys, deviceName);
	}

	/**
	 * Requests approval to replace a revoked identity using a fresh key pair and invitation.
	 *
	 * @param invitation single-use host invitation
	 * @param originalDeviceId revoked device identity
	 * @param deviceName replacement device name
	 * @param pendingOutbox full pending operation array for administrator review
	 * @return ticket to poll while retaining the local outbox
	 */
	public static ReauthorizationTicket requestRepair(HostInvitation invitation, String originalDeviceId,
			String deviceName, JsonNode pendingOutbox) {
		if (pendingOutbox == null || !pendingOutbox.isArray()) {
			throw new IllegalArgumentException("Pending outbox must be an array");
		}
		HostClient client = anonymous(invitation);
		KeyPair keys = HostCrypto.generateKeyPair();
		ObjectNode body = HostHttp.MAPPER.createObjectNode();
		body.put("invitation", invitation.secret());
		body.put("incidentId", invitation.incidentId());
		body.put("originalDeviceId", originalDeviceId);
		body.put("deviceName", deviceName);
		body.put("certificateRequest", HostCrypto.createRequest(keys, deviceName));
		body.set("pendingOutbox", pendingOutbox);
		JsonNode response = client.post(client.enrollmentUri("/v1/reauthorization/repair"), body.toString());
		return client.ticket(response, keys, deviceName);
	}

	/**
	 * Checks whether a pending request has been approved or denied.
	 *
	 * @param ticket request proof and key material
	 * @return current decision and a credential after approval
	 */
	public ReauthorizationResult pollReauthorization(ReauthorizationTicket ticket) {
		if (!hostFingerprint.equals(ticket.hostFingerprint)) {
			throw new IllegalArgumentException("The request belongs to a different host");
		}
		ObjectNode body = HostHttp.MAPPER.createObjectNode();
		body.put("statusToken", ticket.token);
		JsonNode response = post(URI.create("https://" + formatAuthority(ticket.host) + ":"
				+ ticket.enrollmentPort + "/v1/reauthorization/status"), body.toString());
		String state = response.path("state").asText("unknown");
		if (!"approved".equals(state)) {
			return new ReauthorizationResult(state, null);
		}
		try {
			X509Certificate certificate = HostCrypto.parseCertificate(response.path("certificate").asText(""));
			X509Certificate authority = HostCrypto.parseCertificate(response.path("caCertificate").asText(""));
			if (!ticket.hostFingerprint.equalsIgnoreCase(HostCrypto.fingerprint(authority))) {
				throw new HostClientException(HostClientException.Kind.IDENTITY_CHANGED, 0, null,
						"The host identity does not match the request.", null);
			}
			return new ReauthorizationResult(state, createCredential(ticket.keys, ticket.deviceName, certificate,
					authority, ticket.hostFingerprint, response.path("deviceId").asText(),
					Instant.parse(response.path("expiresAt").asText())));
		} catch (GeneralSecurityException | IOException | RuntimeException exception) {
			if (exception instanceof HostClientException hostFailure) {
				throw hostFailure;
			}
			throw new HostClientException(HostClientException.Kind.REJECTED, 200, null,
					"The host returned an unusable credential.", exception);
		}
	}

	/**
	 * Creates a polling ticket from the initial request response.
	 *
	 * @param response accepted request response
	 * @param keys device keys
	 * @param deviceName operator-visible name
	 * @return ticket
	 */
	private ReauthorizationTicket ticket(JsonNode response, KeyPair keys, String deviceName) {
		return new ReauthorizationTicket(host, enrollmentPort, hostFingerprint,
				response.path("statusToken").asText(), response.path("requestId").asText(), keys, deviceName);
	}

	/**
	 * Creates a PKCS12 credential from an approved certificate.
	 *
	 * @param keys device key pair
	 * @param deviceName device name
	 * @param certificate issued certificate
	 * @param authority host certificate authority
	 * @param fingerprint pinned authority fingerprint
	 * @param deviceId host-assigned device identity
	 * @param expiresAt certificate expiry
	 * @return in-memory credential
	 * @throws GeneralSecurityException when the credential cannot be assembled
	 * @throws IOException when the key store cannot be initialized
	 */
	private static DeviceCredential createCredential(KeyPair keys, String deviceName, X509Certificate certificate,
			X509Certificate authority, String fingerprint, String deviceId, Instant expiresAt)
			throws GeneralSecurityException, IOException {
		char[] password = HostCrypto.sha256(Instant.now() + HostCrypto.fingerprint(certificate)).toCharArray();
		KeyStore store = KeyStore.getInstance("PKCS12");
		store.load(null, null);
		store.setKeyEntry(DeviceCredential.KEY_ALIAS, keys.getPrivate(), password,
				new X509Certificate[] {certificate, authority});
		store.setCertificateEntry(DeviceCredential.CA_ALIAS, authority);
		return new DeviceCredential(store, password, fingerprint, deviceId, expiresAt);
	}

	/**
	 * Diagnoses why the host is or is not usable, using only the pre-authentication endpoints.
	 *
	 * @param credential device credential
	 * @return a connection state
	 */
	public HostConnectionState diagnose(DeviceCredential credential) {
		try {
			JsonNode status = status();
			if ("stopping".equals(status.path("hostState").asText()) || !"running".equals(status.path("hostState").asText())) {
				return HostConnectionState.HOST_UNAVAILABLE;
			}
			return switch (credentialState(credential).toLowerCase(Locale.ROOT)) {
				case "active" -> HostConnectionState.CONNECTED;
				case "expired" -> HostConnectionState.CREDENTIAL_EXPIRED;
				case "revoked" -> HostConnectionState.CREDENTIAL_REVOKED;
				default -> HostConnectionState.CREDENTIAL_UNKNOWN;
			};
		} catch (HostClientException failure) {
			return switch (failure.kind()) {
				case IDENTITY_CHANGED -> HostConnectionState.HOST_IDENTITY_CHANGED;
				case HOST_UNAVAILABLE -> HostConnectionState.HOST_UNAVAILABLE;
				default -> HostConnectionState.HOST_UNREACHABLE;
			};
		}
	}

	/**
	 * Reads the shared incident.
	 *
	 * @param incidentId incident identifier
	 * @return read document
	 */
	public JsonNode readIncident(String incidentId) {
		return get(dataUri("/v1/incidents/" + incidentId));
	}

	/**
	 * Reads changes after a cursor.
	 *
	 * @param incidentId incident identifier
	 * @param after cursor
	 * @return changes document
	 */
	public JsonNode changesSince(String incidentId, long after) {
		return get(dataUri("/v1/incidents/" + incidentId + "/changes?after=" + after));
	}

	/**
	 * Submits a batch.
	 *
	 * @param incidentId incident identifier
	 * @param json batch JSON
	 * @return the submission result; field conflicts are returned rather than thrown
	 */
	public JsonNode submit(String incidentId, String json) {
		return exchange(HttpRequest.newBuilder(dataUri("/v1/incidents/" + incidentId + "/records:submit"))
				.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(json)), true);
	}

	/**
	 * The in-person pairing window as opened by the host.
	 *
	 * @param expiresAt when the window closes
	 * @param host host name or address devices should use
	 * @param port mutual-TLS port
	 * @param enrollmentPort enrollment port
	 * @param fingerprint host authority fingerprint
	 */
	public record PairingOffer(Instant expiresAt, String host, int port, int enrollmentPort, String fingerprint) {
		/**
		 * Returns the QR and clipboard payload, which carries the fingerprint but no secret.
		 *
		 * @return pairing payload
		 */
		public org.sarmanagement.icsforms.host.pairing.PairingPayload payload() {
			return new org.sarmanagement.icsforms.host.pairing.PairingPayload(host, port, enrollmentPort,
					fingerprint);
		}
	}

	/**
	 * The state of the pairing window.
	 *
	 * @param open whether the window is open
	 * @param expiresAt expiry, or null when closed
	 * @param awaitingDevice label of the device waiting for its number to be entered, or null
	 */
	public record PairingState(boolean open, Instant expiresAt, String awaitingDevice) {
	}

	/**
	 * The outcome of an in-person pairing.
	 *
	 * @param credential device credential, pinned to the verified host authority
	 * @param port mutual-TLS port reported by the host
	 * @param incidentId incident the host shares
	 */
	public record ShortPairingResult(DeviceCredential credential, int port, String incidentId) {
	}

	/**
	 * Pairs with a host in person. The device commits to a key share, completes a key agreement with the host
	 * and shows a short number derived from it, which the host operator types in. The number binds the host
	 * identity this device observed, so a man in the middle presenting a different authority produces a number
	 * that does not match. When {@code pinnedFingerprint} is known (read from a QR code) the TLS connection is
	 * pinned to it from the start. This blocks until the operator confirms, the exchange is rejected, or the
	 * wait times out.
	 *
	 * @param host host name or address
	 * @param enrollmentPort enrollment port
	 * @param pinnedFingerprint fingerprint obtained out of band, or null when the host was typed
	 * @param deviceName name shown to the host operator
	 * @param suites suites this device supports, in order of preference
	 * @param listener shows the number to this device's operator
	 * @return the credential and where to connect
	 * @throws HostClientException when pairing fails, is refused or times out
	 */
	public static ShortPairingResult pairInPerson(String host, int enrollmentPort, String pinnedFingerprint,
			String deviceName, org.sarmanagement.icsforms.host.pairing.PairingSuites suites,
			PairingListener listener) {
		CapturingHostTrust capture = null;
		HostClient client;
		if (pinnedFingerprint != null) {
			client = anonymous(host, 0, enrollmentPort, pinnedFingerprint.toLowerCase(Locale.ROOT));
		} else {
			capture = new CapturingHostTrust();
			try {
				SSLContext context = SSLContext.getInstance("TLS");
				context.init(null, new TrustManager[] {capture}, null);
				client = new HostClient(host, 0, enrollmentPort, "", context);
			} catch (GeneralSecurityException exception) {
				throw new IllegalStateException("Cannot create TLS context", exception);
			}
		}
		JsonNode status = client.get(client.enrollmentUri("/v1/status"));
		String sasId = chooseSuite(status.path("pairingSuites").path("sas"), suites.sasIds());
		String channelId = chooseSuite(status.path("pairingSuites").path("channel"), suites.channelIds());
		if (sasId == null || channelId == null) {
			throw new HostClientException(HostClientException.Kind.INCOMPATIBLE, 0, null,
					"The host does not support a pairing method this device supports.", null);
		}
		Base64.Encoder encoder = Base64.getEncoder();
		try {
			org.sarmanagement.icsforms.host.pairing.SasSuite.Initiator initiator = suites.sas(sasId).orElseThrow()
					.initiator();
			ObjectNode begin = HostHttp.MAPPER.createObjectNode();
			begin.put("suite", sasId);
			begin.put("channel", channelId);
			begin.put("commitment", encoder.encodeToString(initiator.commitment()));
			begin.put("deviceName", deviceName);
			JsonNode started = client.post(client.enrollmentUri("/v1/pairing/short/begin"), begin.toString());
			String sessionId = started.path("sessionId").asText();
			byte[] opening = initiator.accept(Base64.getDecoder().decode(started.path("share").asText("")));
			ObjectNode open = HostHttp.MAPPER.createObjectNode();
			open.put("sessionId", sessionId);
			open.put("opening", encoder.encodeToString(opening));
			client.post(client.enrollmentUri("/v1/pairing/short/open"), open.toString());
			String observed = pinnedFingerprint != null ? pinnedFingerprint.toLowerCase(Locale.ROOT)
					: capture.observedFingerprint();
			byte[] identity = observed.getBytes(StandardCharsets.UTF_8);
			org.sarmanagement.icsforms.host.pairing.SasSuite.Outcome outcome = initiator.outcome(identity);
			try (java.io.Closeable shown = listener.showCode(
					org.sarmanagement.icsforms.host.pairing.PairingCode.display(outcome.code()))) {
				awaitConfirmation(client, sessionId);
			}
			org.sarmanagement.icsforms.host.pairing.ChannelSuite.PairingChannel channel = suites.channel(channelId)
					.orElseThrow().open(outcome.secret(), outcome.transcript());
			KeyPair keys = HostCrypto.generateKeyPair();
			ObjectNode request = HostHttp.MAPPER.createObjectNode();
			request.put("deviceName", deviceName);
			request.put("certificateRequest", HostCrypto.createRequest(keys, deviceName));
			ObjectNode finish = HostHttp.MAPPER.createObjectNode();
			finish.put("sessionId", sessionId);
			finish.put("confirmation", encoder.encodeToString(channel.confirmation("client", identity)));
			finish.put("sealed",
					encoder.encodeToString(channel.seal("request", HostHttp.MAPPER.writeValueAsBytes(request))));
			JsonNode finished = client.post(client.enrollmentUri("/v1/pairing/short/finish"), finish.toString());
			if (!channel.verifyConfirmation("server", identity,
					Base64.getDecoder().decode(finished.path("confirmation").asText("")))) {
				throw new HostClientException(HostClientException.Kind.REJECTED, 200, null,
						"The host did not prove that it shares the agreed key.", null);
			}
			JsonNode result = HostHttp.MAPPER.readTree(
					channel.unseal("result", Base64.getDecoder().decode(finished.path("sealed").asText(""))));
			DeviceCredential credential = buildCredential(keys, result, observed);
			return new ShortPairingResult(credential, result.path("port").asInt(), result.path("incidentId").asText());
		} catch (GeneralSecurityException | IOException | IllegalArgumentException failure) {
			throw new HostClientException(HostClientException.Kind.REJECTED, 200, null,
					"The pairing exchange with the host failed.", failure);
		}
	}

	/**
	 * Polls until the host operator has confirmed this device's number.
	 *
	 * @param client client for the enrollment port
	 * @param sessionId exchange identifier
	 * @throws HostClientException when the host drops the exchange or the wait times out
	 */
	private static void awaitConfirmation(HostClient client, String sessionId) {
		ObjectNode body = HostHttp.MAPPER.createObjectNode().put("sessionId", sessionId);
		long deadline = System.nanoTime() + CONFIRMATION_WAIT.toNanos();
		while (System.nanoTime() < deadline) {
			JsonNode state = client.post(client.enrollmentUri("/v1/pairing/short/status"), body.toString());
			if ("confirmed".equals(state.path("state").asText())) {
				return;
			}
			try {
				Thread.sleep(POLL_INTERVAL.toMillis());
			} catch (InterruptedException interrupted) {
				Thread.currentThread().interrupt();
				throw new HostClientException(HostClientException.Kind.REJECTED, 0, null, "Pairing was cancelled.",
						interrupted);
			}
		}
		throw new HostClientException(HostClientException.Kind.REJECTED, 0, null,
				"The host did not confirm the number in time.", null);
	}

	/**
	 * Picks the first identifier this device prefers that the host also supports.
	 *
	 * @param hostSupported identifiers the host reports
	 * @param preferred identifiers this device supports, most preferred first
	 * @return the identifier, or null when none match
	 */
	private static String chooseSuite(JsonNode hostSupported, List<String> preferred) {
		List<String> offered = new ArrayList<>();
		for (JsonNode node : hostSupported) {
			offered.add(node.asText(""));
		}
		return preferred.stream().filter(offered::contains).findFirst().orElse(null);
	}

	/**
	 * Opens the in-person pairing window (administrator only, loopback only).
	 *
	 * @return where devices connect and the host identity
	 */
	public PairingOffer openPairing() {
		JsonNode body = post(dataUri("/v1/admin/pairing"), "{}");
		return new PairingOffer(Instant.parse(body.path("expiresAt").asText()), body.path("host").asText(),
				body.path("port").asInt(), body.path("enrollmentPort").asInt(), body.path("fingerprint").asText());
	}

	/**
	 * Reads the state of the pairing window (administrator only, loopback only).
	 *
	 * @return window state
	 */
	public PairingState pairingState() {
		JsonNode body = get(dataUri("/v1/admin/pairing"));
		String device = body.path("awaitingDevice").asText(null);
		return new PairingState(body.path("open").asBoolean(),
				body.hasNonNull("expiresAt") ? Instant.parse(body.path("expiresAt").asText()) : null, device);
	}

	/**
	 * Submits the number the operator typed (administrator only, loopback only).
	 *
	 * @param code the typed number
	 * @return {@code confirmed}, {@code mismatch}, {@code burned} or {@code none}
	 */
	public String enterPairingCode(String code) {
		return post(dataUri("/v1/admin/pairing:enter"), HostHttp.MAPPER.createObjectNode().put("code", code).toString())
				.path("result").asText();
	}

	/** Drops the exchange in progress without closing the window (administrator only, loopback only). */
	public void cancelPairing() {
		post(dataUri("/v1/admin/pairing:cancel"), "{}");
	}

	/**
	 * Closes the pairing window (administrator only, loopback only).
	 */
	public void closePairing() {
		post(dataUri("/v1/admin/pairing:close"), "{}");
	}

	/**
	 * Issues an invitation (administrator only, loopback only).
	 *
	 * @return invitation text
	 */
	public String issueInvitation() {
		return post(dataUri("/v1/admin/invitations"), "{}").path("invitation").asText();
	}

	/**
	 * Lists devices (administrator only, loopback only).
	 *
	 * @return devices
	 */
	public List<DeviceInfo> listDevices() {
		List<DeviceInfo> devices = new ArrayList<>();
		for (JsonNode node : get(dataUri("/v1/admin/devices")).path("devices")) {
			try {
				devices.add(HostHttp.MAPPER.treeToValue(node, DeviceInfo.class));
			} catch (IOException exception) {
				throw new HostClientException(HostClientException.Kind.REJECTED, 200, null,
						"The host returned an unreadable device list.", exception);
			}
		}
		return devices;
	}

	/**
	 * Lists pending re-authorization requests for the host administrator.
	 *
	 * @return request objects with queued outbox contents
	 */
	public List<JsonNode> listReauthorizationRequests() {
		List<JsonNode> requests = new ArrayList<>();
		for (JsonNode node : get(dataUri("/v1/admin/reauthorizations")).path("requests")) {
			requests.add(node.deepCopy());
		}
		return List.copyOf(requests);
	}

	/**
	 * Approves a pending request.
	 *
	 * @param requestId request to approve
	 */
	public void approveReauthorization(String requestId) {
		post(dataUri("/v1/admin/reauthorizations/" + requestId + ":approve"), "{}");
	}

	/**
	 * Denies a pending request.
	 *
	 * @param requestId request to deny
	 */
	public void denyReauthorization(String requestId) {
		post(dataUri("/v1/admin/reauthorizations/" + requestId + ":deny"), "{}");
	}

	/**
	 * Revokes a device (administrator only, loopback only).
	 *
	 * @param deviceId device identifier
	 */
	public void revoke(String deviceId) {
		post(dataUri("/v1/admin/devices/" + deviceId + ":revoke"), "{}");
	}

	/**
	 * Returns the pinned host fingerprint.
	 *
	 * @return hex fingerprint
	 */
	public String hostFingerprint() {
		return hostFingerprint;
	}

	/**
	 * Creates a client for the same host with a newly approved device credential.
	 *
	 * @param credential approved credential
	 * @return client authenticated as the approved identity
	 */
	HostClient withCredential(DeviceCredential credential) {
		return forDevice(host, port, enrollmentPort, credential);
	}

	/**
	 * Builds a mutual-TLS URI.
	 *
	 * @param path request path and query
	 * @return URI
	 */
	private URI dataUri(String path) {
		return URI.create("https://" + authority() + ":" + port + path);
	}

	/**
	 * Builds an enrollment URI.
	 *
	 * @param path request path
	 * @return URI
	 */
	private URI enrollmentUri(String path) {
		return URI.create("https://" + authority() + ":" + enrollmentPort + path);
	}

	/**
	 * Formats the host for a URI, bracketing IPv6 literals.
	 *
	 * @return URI host part
	 */
	private String authority() {
		return formatAuthority(host);
	}

	/**
	 * Formats a host for a URI.
	 *
	 * @param value host name or address
	 * @return URI host part
	 */
	private static String formatAuthority(String value) {
		return value.contains(":") && !value.startsWith("[") ? "[" + value + "]" : value;
	}

	/**
	 * Sends a GET.
	 *
	 * @param uri target
	 * @return response JSON
	 */
	private JsonNode get(URI uri) {
		return exchange(HttpRequest.newBuilder(uri).GET(), false);
	}

	/**
	 * Sends a JSON POST.
	 *
	 * @param uri target
	 * @param body JSON body
	 * @return response JSON
	 */
	private JsonNode post(URI uri, String body) {
		return exchange(HttpRequest.newBuilder(uri).header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)), false);
	}

	/**
	 * Executes a request, classifying failures.
	 *
	 * @param builder request builder
	 * @param allowConflict whether a 409 body is returned instead of thrown
	 * @return response JSON
	 */
	private JsonNode exchange(HttpRequest.Builder builder, boolean allowConflict) {
		HttpRequest request = builder.timeout(TIMEOUT).header(HostProtocol.API_VERSION_HEADER,
				Integer.toString(HostProtocol.API_VERSION)).header(HostProtocol.CLIENT_VERSION_HEADER,
						HostProtocol.clientVersion()).header("Accept", "application/json").build();
		HttpResponse<String> response;
		try {
			response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			throw new HostClientException(HostClientException.Kind.UNREACHABLE, 0, null, "Interrupted.", interrupted);
		} catch (IOException | CompletionException failure) {
			throw classify(failure);
		}
		JsonNode body;
		try {
			body = response.body().isBlank() ? HostHttp.MAPPER.createObjectNode()
					: HostHttp.MAPPER.readTree(response.body());
		} catch (IOException exception) {
			throw new HostClientException(HostClientException.Kind.REJECTED, response.statusCode(), null,
					"The host returned an unreadable response.", exception);
		}
		int status = response.statusCode();
		if (status / 100 == 2 || allowConflict && status == 409) {
			return body;
		}
		throw fromError(status, body);
	}

	/**
	 * Converts a transport failure into a classified exception.
	 *
	 * @param failure transport failure
	 * @return classified exception
	 */
	private static HostClientException classify(Throwable failure) {
		for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
			if (cause instanceof PinnedHostTrust.IdentityMismatchException) {
				return new HostClientException(HostClientException.Kind.IDENTITY_CHANGED, 0, null,
						"The host identity has changed and is not trusted.", failure);
			}
		}
		for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
			if (cause instanceof SSLException) {
				return new HostClientException(HostClientException.Kind.UNREACHABLE, 0, "tls_rejected",
						"The secure connection was rejected.", failure);
			}
		}
		return new HostClientException(HostClientException.Kind.UNREACHABLE, 0, null, "The host cannot be reached.",
				failure);
	}

	/**
	 * Converts a structured error response into an exception.
	 *
	 * @param status HTTP status
	 * @param body error body
	 * @return classified exception
	 */
	private static HostClientException fromError(int status, JsonNode body) {
		String code = body.path("error").path("code").asText(body.path("code").asText(""));
		String message = body.path("error").path("message").asText(body.path("message").asText("Request failed."));
		HostClientException.Kind kind = switch (code) {
			case "credential_expired" -> HostClientException.Kind.CREDENTIAL_EXPIRED;
			case "credential_revoked" -> HostClientException.Kind.CREDENTIAL_REVOKED;
			case "unauthorized" -> HostClientException.Kind.UNAUTHORIZED;
			case "service_unavailable" -> HostClientException.Kind.HOST_UNAVAILABLE;
			case "incompatible_client" -> HostClientException.Kind.INCOMPATIBLE;
			case "invitation_invalid" -> HostClientException.Kind.INVITATION_REJECTED;
			case "rate_limited" -> HostClientException.Kind.RATE_LIMITED;
			default -> HostClientException.Kind.REJECTED;
		};
		return new HostClientException(kind, status, code, message, null);
	}
}
