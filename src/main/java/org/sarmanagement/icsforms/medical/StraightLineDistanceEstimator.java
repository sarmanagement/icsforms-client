/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.medical;

/**
 * Optional offline Haversine fallback, explicitly labeled as an estimate.
 */
public class StraightLineDistanceEstimator {
	private static final double EARTH_RADIUS_METERS = 6_371_000;

	/**
	 * Calculates straight-line distance without estimating travel time.
	 *
	 * @param source staging/base coordinate.
	 * @param destination facility coordinate.
	 * @return straight-line distance in meters.
	 */
	public double estimateMeters(GeoPoint source, GeoPoint destination) {
		double sourceLatitude = Math.toRadians(source.latitude());
		double destinationLatitude = Math.toRadians(destination.latitude());
		double latitudeDelta = destinationLatitude - sourceLatitude;
		double longitudeDelta = Math.toRadians(destination.longitude() - source.longitude());
		double a = Math.sin(latitudeDelta / 2) * Math.sin(latitudeDelta / 2)
				+ Math.cos(sourceLatitude) * Math.cos(destinationLatitude) * Math.sin(longitudeDelta / 2)
						* Math.sin(longitudeDelta / 2);
		double centralAngle = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
		return EARTH_RADIUS_METERS * centralAngle;
	}
}
