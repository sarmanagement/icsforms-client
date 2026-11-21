/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.host;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sun.net.httpserver.HttpExchange;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * JSON request and response helpers shared by the host's HTTP handlers.
 */
final class HostHttp {
	/** Mapper configured for ISO-8601 instants. */
	static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule())
			.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

	/**
	 * Prevents construction of this utility class.
	 */
	private HostHttp() {
	}

	/**
	 * Sends a JSON response and never caches it.
	 *
	 * @param exchange exchange to answer
	 * @param status HTTP status
	 * @param body JSON body, or null for no content
	 * @throws IOException when the response cannot be written
	 */
	static void send(HttpExchange exchange, int status, JsonNode body) throws IOException {
		exchange.getResponseHeaders().set("Cache-Control", "no-store");
		if (body == null) {
			exchange.sendResponseHeaders(status, -1);
			return;
		}
		byte[] bytes = MAPPER.writeValueAsBytes(body);
		exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
		exchange.sendResponseHeaders(status, bytes.length);
		try (OutputStream output = exchange.getResponseBody()) {
			output.write(bytes);
		}
	}

	/**
	 * Sends a structured error as defined in the host API contract.
	 *
	 * @param exchange exchange to answer
	 * @param status HTTP status
	 * @param code stable machine-readable error code
	 * @param message operator-readable message that reveals nothing about incident contents
	 * @param retryable whether retrying can succeed
	 * @param details additional properties, possibly empty
	 * @throws IOException when the response cannot be written
	 */
	static void error(HttpExchange exchange, int status, String code, String message, boolean retryable,
			Map<String, ?> details) throws IOException {
		ObjectNode error = MAPPER.createObjectNode();
		error.put("code", code);
		error.put("message", message);
		error.put("retryable", retryable);
		error.set("details", MAPPER.valueToTree(details));
		ObjectNode body = MAPPER.createObjectNode();
		body.set("error", error);
		send(exchange, status, body);
	}

	/**
	 * Sends a structured error without details.
	 *
	 * @param exchange exchange to answer
	 * @param status HTTP status
	 * @param code stable machine-readable error code
	 * @param message operator-readable message
	 * @param retryable whether retrying can succeed
	 * @throws IOException when the response cannot be written
	 */
	static void error(HttpExchange exchange, int status, String code, String message, boolean retryable)
			throws IOException {
		error(exchange, status, code, message, retryable, Map.of());
	}

	/**
	 * Reads a request body, enforcing a size limit before any deserialization.
	 *
	 * @param exchange exchange to read
	 * @param limit maximum body size in bytes
	 * @return the body, or null after a {@code 413} response was sent because the limit was exceeded
	 * @throws IOException when reading or responding fails
	 */
	static byte[] readBody(HttpExchange exchange, int limit) throws IOException {
		String declared = exchange.getRequestHeaders().getFirst("Content-Length");
		if (declared != null) {
			try {
				if (Long.parseLong(declared.strip()) > limit) {
					tooLarge(exchange, limit);
					return null;
				}
			} catch (NumberFormatException exception) {
				error(exchange, 400, "invalid_request", "Content-Length is not a number.", false);
				return null;
			}
		}
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		byte[] chunk = new byte[8192];
		try (InputStream input = exchange.getRequestBody()) {
			int read;
			while ((read = input.read(chunk)) >= 0) {
				buffer.write(chunk, 0, read);
				if (buffer.size() > limit) {
					tooLarge(exchange, limit);
					return null;
				}
			}
		}
		return buffer.toByteArray();
	}

	/**
	 * Discards up to a bounded number of unread request bytes so the client can read the error response.
	 *
	 * @param input request body stream
	 * @param maximum maximum number of bytes to discard
	 */
	private static void drain(InputStream input, long maximum) {
		byte[] chunk = new byte[8192];
		long total = 0;
		try {
			int read;
			while (total < maximum && (read = input.read(chunk)) >= 0) {
				total += read;
			}
		} catch (IOException exception) {
			return;
		}
	}

	/**
	 * Sends the request-too-large error.
	 *
	 * @param exchange exchange to answer
	 * @param limit the limit that was exceeded
	 * @throws IOException when the response cannot be written
	 */
	private static void tooLarge(HttpExchange exchange, int limit) throws IOException {
		drain(exchange.getRequestBody(), 2L * limit);
		error(exchange, 413, "payload_too_large", "The request body exceeds the limit of " + limit + " bytes.", false,
				Map.of("maximumBytes", limit));
	}

	/**
	 * Parses a request body as a JSON object, answering {@code 400} when it is not one.
	 *
	 * @param exchange exchange to answer on failure
	 * @param body raw body
	 * @return parsed object, or null after a {@code 400} response was sent
	 * @throws IOException when responding fails
	 */
	static JsonNode parseObject(HttpExchange exchange, byte[] body) throws IOException {
		try {
			JsonNode node = MAPPER.readTree(body);
			if (node != null && node.isObject()) {
				return node;
			}
		} catch (IOException | RuntimeException exception) {
			// Reported uniformly below without echoing the malformed content.
		}
		error(exchange, 400, "invalid_request", "The request body must be a JSON object.", false);
		return null;
	}

	/**
	 * Returns a key identifying the remote peer for rate limiting.
	 *
	 * @param exchange exchange
	 * @return remote IP address text
	 */
	static String remoteKey(HttpExchange exchange) {
		InetSocketAddress address = exchange.getRemoteAddress();
		return address == null || address.getAddress() == null ? "unknown" : address.getAddress().getHostAddress();
	}

	/**
	 * Reports whether the peer is on the loopback interface.
	 *
	 * @param exchange exchange
	 * @return true for loopback peers
	 */
	static boolean isLoopback(HttpExchange exchange) {
		InetSocketAddress address = exchange.getRemoteAddress();
		return address != null && address.getAddress() != null && address.getAddress().isLoopbackAddress();
	}

	/**
	 * Parses a query string into the first value of each parameter.
	 *
	 * @param query raw query, possibly null
	 * @return parameter map
	 */
	static Map<String, String> query(String query) {
		java.util.Map<String, String> result = new java.util.HashMap<>();
		if (query == null || query.isEmpty()) {
			return result;
		}
		for (String pair : query.split("&")) {
			int separator = pair.indexOf('=');
			String key = separator < 0 ? pair : pair.substring(0, separator);
			String value = separator < 0 ? "" : pair.substring(separator + 1);
			result.putIfAbsent(java.net.URLDecoder.decode(key, StandardCharsets.UTF_8),
					java.net.URLDecoder.decode(value, StandardCharsets.UTF_8));
		}
		return result;
	}

	/**
	 * Builds a JSON array from nodes.
	 *
	 * @param nodes elements
	 * @return array node
	 */
	static ArrayNode array(List<? extends JsonNode> nodes) {
		ArrayNode array = MAPPER.createArrayNode();
		nodes.forEach(array::add);
		return array;
	}
}
