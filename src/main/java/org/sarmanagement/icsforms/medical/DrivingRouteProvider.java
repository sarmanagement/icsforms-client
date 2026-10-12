/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.medical;

import java.io.IOException;
import java.util.List;

/**
 * Ground-driving route matrix provider.
 */
public interface DrivingRouteProvider {
	/**
	 * Calculates driving routes from one source to destinations in input order.
	 *
	 * @param source staging/base coordinate.
	 * @param destinations eligible resolved facility coordinates.
	 * @return one result per destination, including unreachable/error states.
	 * @throws IOException when the provider is unavailable.
	 * @throws InterruptedException when the request is cancelled.
	 */
	List<RouteResult> calculate(GeoPoint source, List<GeoPoint> destinations)
			throws IOException, InterruptedException;

	/**
	 * One road-route result.
	 *
	 * @param distanceMeters road distance in meters, or NaN if unavailable.
	 * @param durationSeconds route duration in seconds, or NaN if unavailable.
	 * @param method provider/profile label.
	 * @param status result classification.
	 * @param message non-sensitive status message.
	 */
	record RouteResult(double distanceMeters, double durationSeconds, String method, Status status, String message) {
		/**
		 * Returns whether a finite driving distance is available.
		 *
		 * @return true when a road distance is available.
		 */
		public boolean available() {
			return status == Status.AVAILABLE && Double.isFinite(distanceMeters) && distanceMeters >= 0;
		}
	}

	/**
	 * Route-result condition.
	 */
	enum Status {
		AVAILABLE,
		UNREACHABLE,
		ERROR
	}
}
