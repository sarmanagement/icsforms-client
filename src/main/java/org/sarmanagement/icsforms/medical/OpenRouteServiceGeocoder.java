/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.medical;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * ORS public Pelias forward geocoder, independent of the routing provider.
 */
public class OpenRouteServiceGeocoder implements AddressGeocoder {
	private static final URI DEFAULT_ENDPOINT = URI.create("https://api.openrouteservice.org/geocode/search");
	private static final ObjectMapper MAPPER = new ObjectMapper();
	private final HttpClient client;
	private final URI endpoint;
	private final Supplier<String> apiKey;
	private final Duration requestTimeout;

	/**
	 * Creates a geocoder using the public ORS endpoint and local key store.
	 */
	public OpenRouteServiceGeocoder() {
		this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), DEFAULT_ENDPOINT,
				new OrsApiKeyStore()::getApiKey, Duration.ofSeconds(20));
	}

	/**
	 * Creates a geocoder with injectable HTTP configuration for controlled testing.
	 *
	 * @param client HTTP client.
	 * @param endpoint forward-geocoding endpoint.
	 * @param apiKey supplier for local credential.
	 * @param requestTimeout request timeout.
	 */
	public OpenRouteServiceGeocoder(HttpClient client, URI endpoint, Supplier<String> apiKey,
			Duration requestTimeout) {
		this.client = client;
		this.endpoint = endpoint;
		this.apiKey = apiKey;
		this.requestTimeout = requestTimeout;
	}

	/** {@inheritDoc} */
	@Override
	public List<Candidate> geocode(String address) throws IOException, InterruptedException {
		if (address == null || address.isBlank()) {
			throw new IllegalArgumentException("Enter an address to look up");
		}
		String key;
		try {
			key = apiKey.get();
		} catch (RuntimeException exception) {
			throw new OrsServiceException(401);
		}
		String separator = endpoint.toString().contains("?") ? "&" : "?";
		URI uri = URI.create(endpoint + separator + "text=" + URLEncoder.encode(address, StandardCharsets.UTF_8));
		HttpRequest request = HttpRequest.newBuilder(uri).timeout(requestTimeout)
				.header("Authorization", key).header("Accept", "application/geo+json")
				.GET().build();
		HttpResponse<String> response = send(request);
		if (response.statusCode() < 200 || response.statusCode() >= 300) {
			throw new OrsServiceException(response.statusCode());
		}
		return candidates(response.body());
	}

	/**
	 * Sends the geocoding request and sanitizes transport-timeout errors.
	 *
	 * @param request prepared HTTP request.
	 * @return provider response.
	 * @throws IOException when the request fails.
	 * @throws InterruptedException when cancelled.
	 */
	private HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
		try {
			return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		} catch (java.net.http.HttpTimeoutException exception) {
			throw new OrsServiceException(408);
		}
	}

	/**
	 * Parses feature candidates without silently choosing an ambiguous result.
	 *
	 * @param body JSON body.
	 * @return provider-ordered candidates.
	 * @throws IOException when the response is malformed.
	 */
	private List<Candidate> candidates(String body) throws IOException {
		JsonNode features = MAPPER.readTree(body).path("features");
		if (!features.isArray()) {
			throw new IOException("OpenRouteService returned malformed geocoding results");
		}
		List<Candidate> result = new ArrayList<>();
		for (JsonNode feature : features) {
			JsonNode coordinates = feature.path("geometry").path("coordinates");
			if (!coordinates.isArray() || coordinates.size() < 2 || !coordinates.get(0).isNumber()
					|| !coordinates.get(1).isNumber()) {
				continue;
			}
			double longitude = coordinates.get(0).asDouble();
			double latitude = coordinates.get(1).asDouble();
			try {
				GeoPoint point = new GeoPoint(latitude, longitude);
				JsonNode properties = feature.path("properties");
				String label = properties.path("label").asText(properties.path("name").asText(""));
				double confidence = properties.path("confidence").isNumber()
						? properties.path("confidence").asDouble()
						: Double.NaN;
				result.add(new Candidate(label, point, confidence));
			} catch (IllegalArgumentException ignored) {
				// Ignore only malformed coordinates; retain all valid candidates for confirmation.
			}
		}
		return result;
	}
}
