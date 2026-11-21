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
	private final String host;
	private final int port;
	private final int enrollmentPort;
	private final HttpClient http;
	private final String hostFingerprint;

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
		try {
			X509Certificate certificate = HostCrypto.parseCertificate(response.path("certificate").asText(""));
			X509Certificate authority = HostCrypto.parseCertificate(response.path("caCertificate").asText(""));
			if (!invitation.hostFingerprint().equalsIgnoreCase(HostCrypto.fingerprint(authority))) {
				throw new HostClientException(HostClientException.Kind.IDENTITY_CHANGED, 0, null,
						"The host identity does not match the invitation.", null);
			}
			char[] password = HostCrypto.sha256(Instant.now() + HostCrypto.fingerprint(certificate)).toCharArray();
			KeyStore store = KeyStore.getInstance("PKCS12");
			store.load(null, null);
			store.setKeyEntry(DeviceCredential.KEY_ALIAS, keys.getPrivate(), password,
					new X509Certificate[] {certificate, authority});
			store.setCertificateEntry(DeviceCredential.CA_ALIAS, authority);
			return new DeviceCredential(store, password, invitation.hostFingerprint(),
					response.path("deviceId").asText(), Instant.parse(response.path("expiresAt").asText()));
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
		return host.contains(":") && !host.startsWith("[") ? "[" + host + "]" : host;
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
