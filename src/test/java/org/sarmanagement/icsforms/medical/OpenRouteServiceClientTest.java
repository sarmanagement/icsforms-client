/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.medical;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.model.StagingLocation;
import org.sarmanagement.icsforms.model.VetDirectory;
import org.sarmanagement.icsforms.model.VetFacility;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies ORS request mapping and local-only route/geocoding behavior with stub HTTP.
 */
class OpenRouteServiceClientTest {
	@TempDir
	Path tempDirectory;

	@Test
	void matrixUsesLongitudeLatitudeAndCalculatesEveryDestinationInInputOrder() throws Exception {
		AtomicReference<String> firstBody = new AtomicReference<>();
		AtomicInteger requests = new AtomicInteger();
		HttpServer server = server("/v2/matrix/driving-car", exchange -> {
			firstBody.compareAndSet(null, new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			requests.incrementAndGet();
			assertEquals("test-key", exchange.getRequestHeaders().getFirst("Authorization"));
			reply(exchange, 200, "{\"distances\":[[1200.0]],\"durations\":[[240.0]]}");
		});
		try {
			OpenRouteServiceDrivingRouter router = router(server, () -> "test-key");
			List<DrivingRouteProvider.RouteResult> results = router.calculate(new GeoPoint(42.0, -71.0),
					List.of(new GeoPoint(43.0, -72.0), new GeoPoint(44.0, -73.0)));

			assertEquals(2, requests.get());
			assertTrue(firstBody.get().contains("\"locations\":[[-71.0,42.0],[-72.0,43.0]]"));
			assertTrue(firstBody.get().contains("\"sources\":[\"0\"]"));
			assertTrue(firstBody.get().contains("\"destinations\":[\"1\"]"));
			assertTrue(firstBody.get().contains("\"metrics\":[\"distance\",\"duration\"]"));
			assertTrue(firstBody.get().contains("\"units\":\"m\""));
			assertEquals(1200.0, results.get(0).distanceMeters());
			assertEquals(240.0, results.get(1).durationSeconds());
			assertEquals("openrouteservice/driving-car", results.get(0).method());
		} finally {
			server.stop(0);
		}
	}

	@Test
	void geocoderReturnsCandidatesForExplicitOperatorConfirmation() throws Exception {
		HttpServer server = server("/geocode/search", exchange -> {
			assertEquals("text=100+Fictional+Road", exchange.getRequestURI().getRawQuery());
			assertEquals("test-key", exchange.getRequestHeaders().getFirst("Authorization"));
			reply(exchange, 200, "{\"features\":[{\"geometry\":{\"coordinates\":[-71.5,42.5]},"
					+ "\"properties\":{\"label\":\"100 Fictional Road, Test Town\",\"confidence\":0.89}}]}");
		});
		try {
			OpenRouteServiceGeocoder geocoder = new OpenRouteServiceGeocoder(HttpClient.newHttpClient(),
					endpoint(server, "/geocode/search"), () -> "test-key", Duration.ofSeconds(2));
			List<AddressGeocoder.Candidate> candidates = geocoder.geocode("100 Fictional Road");
			assertEquals(1, candidates.size());
			assertEquals("100 Fictional Road, Test Town", candidates.get(0).label());
			assertEquals(-71.5, candidates.get(0).point().longitude());
			assertEquals(42.5, candidates.get(0).point().latitude());
			assertEquals(0.89, candidates.get(0).confidence());
		} finally {
			server.stop(0);
		}
	}

	@Test
	void routerRetriesThrottlingWithABoundAndRedactsAuthorizationFailures() throws Exception {
		AtomicInteger requests = new AtomicInteger();
		HttpServer throttled = server("/v2/matrix/driving-car", exchange -> {
			if (requests.getAndIncrement() == 0) {
				exchange.getResponseHeaders().add("Retry-After", "0");
				reply(exchange, 429, "sensitive server detail");
			} else {
				reply(exchange, 200, "{\"distances\":[[10.0]],\"durations\":[[4.0]]}");
			}
		});
		try {
			assertTrue(router(throttled, () -> "private-api-key")
					.calculate(new GeoPoint(0, 0), List.of(new GeoPoint(0, 1))).get(0).available());
			assertEquals(2, requests.get());
		} finally {
			throttled.stop(0);
		}

		HttpServer unauthorized = server("/v2/matrix/driving-car",
				exchange -> reply(exchange, 401, "private-api-key must not appear"));
		try {
			OrsServiceException error = assertThrows(OrsServiceException.class,
					() -> router(unauthorized, () -> "private-api-key")
							.calculate(new GeoPoint(0, 0), List.of(new GeoPoint(0, 1))));
			assertEquals(401, error.getStatusCode());
			assertFalse(error.getMessage().contains("private-api-key"));
			assertFalse(error.getMessage().contains("sensitive server detail"));
		} finally {
			unauthorized.stop(0);
		}
	}

	@Test
	void unresolvedStatusesAndNonRoadEstimatesCannotEnterTheAutomaticShortlist() throws Exception {
		StagingLocation staging = new StagingLocation();
		staging.setAddress("Base");
		staging.setResolution("Confirmed base", 42, -71, "manual", "confirmed", null);
		VetDirectory directory = new VetDirectory();
		directory.getFacilities().add(routedFacility("road-1", "Nearest", true, 1000,
				"openrouteservice/driving-car", staging.getGeneration()));
		directory.getFacilities().add(routedFacility("road-2", "Second", true, 2000,
				"openrouteservice/driving-car", staging.getGeneration()));
		directory.getFacilities().add(routedFacility("estimated", "Estimate", true, 10,
				"straight-line estimate", staging.getGeneration()));
		directory.getFacilities().add(routedFacility("unknown", "Unconfirmed", null, 1,
				"openrouteservice/driving-car", staging.getGeneration()));

		List<VetFacility> selected = new VetShortlistSelector().propose(directory.getFacilities(), 1,
				staging.getGeneration());

		assertEquals(1, selected.size());
		assertEquals("Nearest", selected.get(0).getName());
		selected.get(0).setName("Plan-only edit");
		assertEquals("Nearest", directory.getFacilities().get(0).getName());

		VetFacility offline = directory.getFacilities().get(2);
		new VetRoutingService((source, destinations) -> List.of()).estimateStraightLine(staging, offline);
		assertEquals("straight-line estimate", offline.getDistanceMethod());
		assertTrue(Double.isNaN(offline.getDurationSeconds()));
	}

	@Test
	void routingProcessesAllConfirmedFacilitiesAndLeavesUnknownOutByDefault() throws Exception {
		StagingLocation staging = new StagingLocation();
		staging.setAddress("Base");
		staging.setResolution("Confirmed base", 42, -71, "manual", "", null);
		VetDirectory directory = new VetDirectory();
		directory.getFacilities().add(coordinateFacility("Confirmed A", true, -72));
		directory.getFacilities().add(coordinateFacility("Confirmed B", true, -73));
		directory.getFacilities().add(coordinateFacility("Unknown", null, -74));
		AtomicInteger routed = new AtomicInteger();
		VetRoutingService routing = new VetRoutingService((source, destinations) -> {
			routed.incrementAndGet();
			return List.of(new DrivingRouteProvider.RouteResult(5000, 600, "openrouteservice/driving-car",
					DrivingRouteProvider.Status.AVAILABLE, ""));
		});

		VetRoutingService.CalculationReport report = routing.calculate(staging, directory, false, true);

		assertEquals(2, routed.get());
		assertEquals(2, report.calculated());
		assertEquals(1, report.skipped());
		assertEquals(3, directory.getFacilities().size());
		assertFalse(directory.getFacilities().get(2).hasCurrentDistance(staging.getGeneration()));
	}

	@Test
	void localKeyFileIsSeparateFromIncidentData() throws Exception {
		Path keyPath = tempDirectory.resolve("private").resolve("ors-api-key");
		OrsApiKeyStore store = new OrsApiKeyStore(keyPath);
		store.saveApiKey("example-local-key");
		assertEquals("example-local-key", store.getApiKey());
		assertTrue(Files.isRegularFile(keyPath));
		assertThrows(IllegalArgumentException.class, () -> store.saveApiKey(" "));
	}

	/**
	 * Creates a router aimed at a local HTTP server.
	 *
	 * @param server local response stub.
	 * @param apiKey test key supplier.
	 * @return configured router.
	 */
	private OpenRouteServiceDrivingRouter router(HttpServer server, java.util.function.Supplier<String> apiKey) {
		return new OpenRouteServiceDrivingRouter(HttpClient.newHttpClient(),
				endpoint(server, "/v2/matrix/driving-car"), apiKey, Duration.ofSeconds(2));
	}

	/**
	 * Starts a local-only HTTP server with one endpoint handler.
	 *
	 * @param path endpoint path.
	 * @param handler stub handler.
	 * @return started server.
	 * @throws IOException when server setup fails.
	 */
	private HttpServer server(String path, ExchangeHandler handler) throws IOException {
		HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext(path, exchange -> {
			try {
				handler.handle(exchange);
			} catch (Exception exception) {
				reply(exchange, 500, "test server failure");
			}
		});
		server.start();
		return server;
	}

	/**
	 * Returns a local endpoint URI for the server.
	 *
	 * @param server local server.
	 * @param path endpoint path.
	 * @return server URI.
	 */
	private URI endpoint(HttpServer server, String path) {
		return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path);
	}

	/**
	 * Sends a controlled HTTP response.
	 *
	 * @param exchange active request.
	 * @param status response status.
	 * @param body response body.
	 * @throws IOException when response writing fails.
	 */
	private void reply(HttpExchange exchange, int status, String body) throws IOException {
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		exchange.sendResponseHeaders(status, bytes.length);
		try (var output = exchange.getResponseBody()) {
			output.write(bytes);
		}
	}

	/**
	 * Creates a test facility with an explicit route result.
	 *
	 * @param id facility identifier.
	 * @param name facility name.
	 * @param available nullable 24-hour status.
	 * @param distance road or estimate distance.
	 * @param method distance method.
	 * @param generation staging generation.
	 * @return test facility.
	 */
	private VetFacility routedFacility(String id, String name, Boolean available, double distance, String method,
			long generation) {
		VetFacility facility = new VetFacility();
		facility.setId(id);
		facility.setName(name);
		facility.setAvailable24Hours(available);
		facility.setCoordinates(42, -72, "manual", "test");
		facility.setDistance(distance, 100, method, "test", null, generation);
		return facility;
	}

	/**
	 * Creates a test directory entry with valid coordinates.
	 *
	 * @param name facility name.
	 * @param available nullable availability.
	 * @param longitude longitude.
	 * @return coordinate-ready facility.
	 */
	private VetFacility coordinateFacility(String name, Boolean available, double longitude) {
		VetFacility facility = new VetFacility();
		facility.setName(name);
		facility.setAvailable24Hours(available);
		facility.setCoordinates(42, longitude, "manual", "test");
		return facility;
	}

	/**
	 * HTTP-server callback used to stub one provider request.
	 */
	@FunctionalInterface
	private interface ExchangeHandler {
		/**
		 * Handles one local HTTP request.
		 *
		 * @param exchange request and response.
		 * @throws Exception when the test stub fails.
		 */
		void handle(HttpExchange exchange) throws Exception;
	}
}
