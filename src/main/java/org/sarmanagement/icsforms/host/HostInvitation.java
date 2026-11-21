/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.host;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/**
 * A pairing invitation as shown to the operator and typed or pasted on the device. It carries the host address and
 * the host authority fingerprint so the device pins the host, plus the single-use secret. Treat the text as a
 * secret until it expires or is redeemed.
 *
 * @param host host name or IP address
 * @param port mutual-TLS port
 * @param enrollmentPort enrollment port
 * @param hostFingerprint SHA-256 fingerprint of the host authority certificate
 * @param incidentId incident the invitation is scoped to
 * @param secret single-use invitation secret
 * @param expiresAt invitation expiry
 */
public record HostInvitation(String host, int port, int enrollmentPort, String hostFingerprint, String incidentId,
		String secret, Instant expiresAt) {
	private static final String PREFIX = "ICSFORMS-INVITE-1:";
	private static final ObjectMapper MAPPER = new ObjectMapper();

	/**
	 * Redacts the secret from diagnostic output.
	 *
	 * @return text that omits the secret
	 */
	@Override
	public String toString() {
		return "HostInvitation[" + host + ":" + port + ", incident=" + incidentId + "]";
	}

	/**
	 * Encodes the invitation as copyable text.
	 *
	 * @return invitation text
	 */
	public String toText() {
		ObjectNode node = MAPPER.createObjectNode();
		node.put("host", host);
		node.put("port", port);
		node.put("enrollmentPort", enrollmentPort);
		node.put("hostFingerprint", hostFingerprint);
		node.put("incidentId", incidentId);
		node.put("secret", secret);
		node.put("expiresAt", expiresAt.toString());
		return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(node.toString().getBytes(StandardCharsets.UTF_8));
	}

	/**
	 * Parses invitation text.
	 *
	 * @param text invitation text, surrounding whitespace is ignored
	 * @return parsed invitation
	 * @throws IllegalArgumentException when the text is not a valid invitation
	 */
	public static HostInvitation parse(String text) {
		String trimmed = text == null ? "" : text.strip();
		if (!trimmed.startsWith(PREFIX)) {
			throw new IllegalArgumentException("Not an ICS Forms invitation");
		}
		try {
			byte[] json = Base64.getUrlDecoder().decode(trimmed.substring(PREFIX.length()));
			JsonNode node = MAPPER.readTree(json);
			return new HostInvitation(required(node, "host"), node.path("port").asInt(-1),
					node.path("enrollmentPort").asInt(-1), required(node, "hostFingerprint"),
					required(node, "incidentId"), required(node, "secret"), Instant.parse(required(node, "expiresAt")));
		} catch (IOException | RuntimeException exception) {
			throw new IllegalArgumentException("Invitation text is malformed", exception);
		}
	}

	/**
	 * Reads a required text property.
	 *
	 * @param node JSON object
	 * @param name property name
	 * @return non-blank value
	 */
	private static String required(JsonNode node, String name) {
		String value = node.path(name).asText("");
		if (value.isBlank()) {
			throw new IllegalArgumentException("Missing " + name);
		}
		return value;
	}
}
