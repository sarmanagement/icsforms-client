/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.medical;

import org.sarmanagement.icsforms.model.StagingLocation;
import org.sarmanagement.icsforms.model.VetDirectory;
import org.sarmanagement.icsforms.model.VetFacility;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Calculates road routes for every directory facility without availability filtering.
 */
public class VetRoutingService {
	private final DrivingRouteProvider routeProvider;
	private final StraightLineDistanceEstimator straightLineEstimator = new StraightLineDistanceEstimator();

	/**
	 * Creates the service with a configurable provider; ORS is the default caller choice.
	 *
	 * @param routeProvider ground route provider.
	 */
	public VetRoutingService(DrivingRouteProvider routeProvider) {
		this.routeProvider = routeProvider;
	}

	/**
	 * Calculates or reuses road routes for every facility, regardless of availability status.
	 *
	 * <p>
	 * It sends no clinical information. Existing cached road results for the current
	 * staging generation are retained unless refresh is requested. Unresolved,
	 * unreachable, and errored facilities remain in the directory.
	 * </p>
	 *
	 * @param staging incident staging/base location.
	 * @param directory incident-owned regional directory.
	 * @param refreshCached whether to force a network refresh of existing route results.
	 * @return route summary and per-record warnings.
	 * @throws InterruptedException when cancelled.
	 */
	public CalculationReport calculate(StagingLocation staging, VetDirectory directory, boolean refreshCached)
			throws InterruptedException {
		if (staging == null || !staging.hasValidCoordinates()) {
			return new CalculationReport(0, 0, 0, List.of("Staging/base coordinates are unresolved."));
		}
		GeoPoint source = new GeoPoint(staging.getLatitude(), staging.getLongitude());
		int calculated = 0;
		int cached = 0;
		int skipped = 0;
		List<String> warnings = new ArrayList<>();
		for (VetFacility facility : directory.getFacilities()) {
			if (!refreshCached && facility.hasCurrentDistance(staging.getGeneration())
					&& "openrouteservice/driving-car".equals(facility.getDistanceMethod())) {
				cached++;
				continue;
			}
			if (facility.getLatitude() == null || facility.getLongitude() == null) {
				warnings.add(facility.getName() + ": address/coordinates unresolved.");
				continue;
			}
			GeoPoint destination;
			try {
				destination = new GeoPoint(facility.getLatitude(), facility.getLongitude());
			} catch (IllegalArgumentException exception) {
				warnings.add(facility.getName() + ": coordinates are outside valid ranges.");
				continue;
			}
			try {
				List<DrivingRouteProvider.RouteResult> results = routeProvider.calculate(source, List.of(destination));
				if (results.size() != 1) {
					warnings.add(facility.getName() + ": route provider returned no result.");
					continue;
				}
				DrivingRouteProvider.RouteResult result = results.get(0);
				if (!result.available()) {
					warnings.add(facility.getName() + ": " + result.message());
					continue;
				}
				facility.setDistance(result.distanceMeters(), result.durationSeconds(), result.method(),
						"ground driving route from staging/base", Instant.now(), staging.getGeneration());
				calculated++;
			} catch (IOException exception) {
				warnings.add(facility.getName() + ": " + exception.getMessage());
			}
			if (Thread.currentThread().isInterrupted()) {
				throw new InterruptedException("Route calculation cancelled");
			}
		}
		return new CalculationReport(calculated, cached, skipped, warnings);
	}

	/**
	 * Retains the earlier availability-policy signature while routing every record.
	 *
	 * @param staging incident staging/base location.
	 * @param directory incident-owned regional directory.
	 * @param includeUnknown ignored; all availability states are routed.
	 * @param refreshCached whether to force a network refresh of existing route results.
	 * @return route summary and per-record warnings.
	 * @throws InterruptedException when cancelled.
	 */
	public CalculationReport calculate(StagingLocation staging, VetDirectory directory, boolean includeUnknown,
			boolean refreshCached) throws InterruptedException {
		return calculate(staging, directory, refreshCached);
	}

	/**
	 * Stores a clearly labeled straight-line estimate as a fallback only.
	 *
	 * @param staging incident staging/base location.
	 * @param facility facility to estimate.
	 * @throws IllegalArgumentException when coordinates are unresolved.
	 */
	public void estimateStraightLine(StagingLocation staging, VetFacility facility) {
		if (staging == null || !staging.hasValidCoordinates() || facility.getLatitude() == null
				|| facility.getLongitude() == null) {
			throw new IllegalArgumentException("Resolve both staging and facility coordinates first");
		}
		double meters = straightLineEstimator.estimateMeters(new GeoPoint(staging.getLatitude(), staging.getLongitude()),
				new GeoPoint(facility.getLatitude(), facility.getLongitude()));
		facility.setDistance(meters, Double.NaN, "straight-line estimate", "Haversine; not a driving route",
				Instant.now(), staging.getGeneration());
	}

	/**
	 * Route-calc summary that does not discard any directory records.
	 *
	 * @param calculated number of new road results.
	 * @param cached number of reused current-base road results.
	 * @param skipped number excluded by the status policy.
	 * @param warnings unresolved/unreachable/error messages.
	 */
	public record CalculationReport(int calculated, int cached, int skipped, List<String> warnings) {
	}
}
