/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.medical;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * HeiGIT-hosted openrouteservice driving-car matrix client. It sends one
 * destination per request to avoid relying on inaccessible public-service
 * matrix-size quotas. OpenStreetMap data attribution is © openrouteservice by HeiGIT.
 */
public class OpenRouteServiceDrivingRouter implements DrivingRouteProvider {
	static final URI DEFAULT_ENDPOINT = URI.create(
			"https://api.heigit.org/openrouteservice/v2/matrix/driving-car");
	private static final String METHOD = "openrouteservice/driving-car";
	private static final int MAX_RETRIES = 2;
	private static final long MAX_RETRY_DELAY_MILLIS = 1500;
	private static final ObjectMapper MAPPER = new ObjectMapper();
	private final HttpClient client;
	private final URI endpoint;
	private final Supplier<String> apiKey;
	private final Duration requestTimeout;

	/**
	 * Creates a driving router using the public ORS endpoint and local key store.
	 */
	public OpenRouteServiceDrivingRouter() {
		this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), DEFAULT_ENDPOINT,
				new OrsApiKeyStore()::getApiKey, Duration.ofSeconds(30));
	}

	/**
	 * Creates a router with injectable HTTP configuration for controlled tests.
	 *
	 * @param client HTTP client.
	 * @param endpoint matrix endpoint.
	 * @param apiKey supplier for local credential.
	 * @param requestTimeout request timeout.
	 */
	public OpenRouteServiceDrivingRouter(HttpClient client, URI endpoint, Supplier<String> apiKey,
			Duration requestTimeout) {
		this.client = client;
		this.endpoint = endpoint;
		this.apiKey = apiKey;
		this.requestTimeout = requestTimeout;
	}

	/** {@inheritDoc} */
	@Override
	public List<RouteResult> calculate(GeoPoint source, List<GeoPoint> destinations)
			throws IOException, InterruptedException {
		List<RouteResult> results = new ArrayList<>();
		for (GeoPoint destination : destinations) {
			if (Thread.currentThread().isInterrupted()) {
				throw new InterruptedException("Route calculation cancelled");
			}
			results.add(routeOne(source, destination));
		}
		return results;
	}

	/**
	 * Routes one source/destination pair without exceeding multi-location quotas.
	 *
	 * @param source staging/base coordinate.
	 * @param destination facility coordinate.
	 * @return route distance and duration or a sanitized error.
	 * @throws IOException when a non-retriable service error occurs.
	 * @throws InterruptedException when cancelled.
	 */
	private RouteResult routeOne(GeoPoint source, GeoPoint destination) throws IOException, InterruptedException {
		String key;
		try {
			key = apiKey.get();
		} catch (RuntimeException exception) {
			throw new OrsServiceException(401);
		}
		HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(requestTimeout)
				.header("Authorization", key).header("Content-Type", "application/json")
				.header("Accept", "application/json").POST(HttpRequest.BodyPublishers.ofString(requestBody(source,
						destination)))
				.build();
		HttpResponse<String> response = sendWithRetry(request);
		if (response.statusCode() < 200 || response.statusCode() >= 300) {
			throw new OrsServiceException(response.statusCode());
		}
		return parseRoute(response.body());
	}

	/**
	 * Sends an HTTP request with bounded retries for throttling/transient server errors.
	 *
	 * @param request prepared request.
	 * @return service response.
	 * @throws IOException when the request fails.
	 * @throws InterruptedException when cancelled during network wait or retry delay.
	 */
	private HttpResponse<String> sendWithRetry(HttpRequest request) throws IOException, InterruptedException {
		for (int attempt = 0; ; attempt++) {
			HttpResponse<String> response;
			try {
				response = client.send(request, HttpResponse.BodyHandlers.ofString(java.nio.charset.StandardCharsets.UTF_8));
			} catch (java.net.http.HttpTimeoutException exception) {
				throw new OrsServiceException(408);
			}
			if ((response.statusCode() == 429 || response.statusCode() == 503) && attempt < MAX_RETRIES) {
				Thread.sleep(retryDelay(response));
				continue;
			}
			return response;
		}
	}

	/**
	 * Calculates a capped retry delay using Retry-After when it is expressed in seconds.
	 *
	 * @param response throttled response.
	 * @return delay in milliseconds.
	 */
	private long retryDelay(HttpResponse<String> response) {
		try {
			long delay = Long.parseLong(response.headers().firstValue("Retry-After").orElse("1")) * 1000L;
			return Math.max(0, Math.min(MAX_RETRY_DELAY_MILLIS, delay));
		} catch (NumberFormatException exception) {
			return 1000;
		}
	}

	/**
	 * Creates one-source, one-destination matrix JSON in ORS longitude/latitude order.
	 *
	 * @param source staging/base.
	 * @param destination facility.
	 * @return request JSON.
	 * @throws IOException when serialization fails.
	 */
	private String requestBody(GeoPoint source, GeoPoint destination) throws IOException {
		ObjectNode root = MAPPER.createObjectNode();
		ArrayNode locations = root.putArray("locations");
		addCoordinates(locations.addArray(), source);
		addCoordinates(locations.addArray(), destination);
		root.putArray("sources").add("0");
		root.putArray("destinations").add("1");
		root.putArray("metrics").add("distance").add("duration");
		root.put("units", "m");
		return MAPPER.writeValueAsString(root);
	}

	/**
	 * Writes one ORS coordinate tuple.
	 *
	 * @param tuple target JSON array.
	 * @param point point in application latitude/longitude order.
	 */
	private void addCoordinates(ArrayNode tuple, GeoPoint point) {
		tuple.add(point.longitude());
		tuple.add(point.latitude());
	}

	/**
	 * Parses the source row while distinguishing null/unreachable routes.
	 *
	 * @param body JSON response.
	 * @return one road route.
	 * @throws IOException when the result shape is invalid.
	 */
	private RouteResult parseRoute(String body) throws IOException {
		JsonNode root = MAPPER.readTree(body);
		JsonNode distances = root.path("distances");
		JsonNode durations = root.path("durations");
		if (!distances.isArray() || distances.isEmpty() || !distances.get(0).isArray()
				|| distances.get(0).isEmpty() || !durations.isArray() || durations.isEmpty()
				|| !durations.get(0).isArray() || durations.get(0).isEmpty()) {
			throw new IOException("OpenRouteService returned a malformed matrix response");
		}
		JsonNode distance = distances.get(0).get(0);
		JsonNode duration = durations.get(0).get(0);
		if (distance.isNull() || !distance.isNumber()) {
			return new RouteResult(Double.NaN, duration.isNumber() ? duration.asDouble() : Double.NaN, METHOD,
					Status.UNREACHABLE, "No driving route returned");
		}
		double distanceMeters = distance.asDouble();
		double durationSeconds = duration.isNumber() ? duration.asDouble() : Double.NaN;
		if (!Double.isFinite(distanceMeters) || distanceMeters < 0) {
			throw new IOException("OpenRouteService returned an invalid distance");
		}
		return new RouteResult(distanceMeters, durationSeconds, METHOD, Status.AVAILABLE, "");
	}
}
