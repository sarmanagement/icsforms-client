/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.medical;

import org.sarmanagement.icsforms.model.StagingLocation;

/**
 * Geographic point represented as latitude and longitude in decimal degrees.
 *
 * @param latitude latitude in decimal degrees.
 * @param longitude longitude in decimal degrees.
 */
public record GeoPoint(double latitude, double longitude) {
	/**
	 * Validates the coordinate pair.
	 */
	public GeoPoint {
		StagingLocation.validateCoordinates(latitude, longitude);
	}

	/**
	 * Returns the ORS/GeoJSON coordinate order required at HTTP boundaries.
	 *
	 * @return longitude then latitude.
	 */
	public double[] asLongitudeLatitude() {
		return new double[] {longitude, latitude};
	}
}
