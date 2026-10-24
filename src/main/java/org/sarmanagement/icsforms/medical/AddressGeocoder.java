/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.medical;

import java.io.IOException;
import java.util.List;

/**
 * Address-to-coordinate lookup service, separate from routing.
 */
public interface AddressGeocoder {
	/**
	 * Looks up candidate coordinates for an address.
	 *
	 * @param address entered address.
	 * @return ordered geocoding candidates; an empty list means no match.
	 * @throws IOException when the lookup fails.
	 * @throws InterruptedException when the request is cancelled.
	 */
	List<Candidate> geocode(String address) throws IOException, InterruptedException;

	/**
	 * One operator-confirmable geocoding candidate.
	 *
	 * @param label resolved place/address label.
	 * @param point resolved coordinates.
	 * @param confidence provider confidence, or NaN if unavailable.
	 */
	record Candidate(String label, GeoPoint point, double confidence) {
	}
}
