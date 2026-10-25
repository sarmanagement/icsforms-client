/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.medical;

import org.sarmanagement.icsforms.model.VetFacility;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Proposes the nearest confirmed 24-hour facilities using road distance only.
 */
public class VetShortlistSelector {
	/** Default closest-facility count for a new proposal. */
	public static final int DEFAULT_LIMIT = 8;

	/**
	 * Creates a shortlist selector.
	 */
	public VetShortlistSelector() {
	}

	/**
	 * Returns a stable-order proposal without changing the source directory or plan.
	 *
	 * @param facilities regional directory records.
	 * @param limit requested maximum count; nonpositive values use the default.
	 * @param stagingGeneration currently selected base generation.
	 * @return independent snapshots ranked by ground driving distance.
	 */
	public List<VetFacility> propose(List<VetFacility> facilities, int limit, long stagingGeneration) {
		int count = limit <= 0 ? DEFAULT_LIMIT : limit;
		List<IndexedFacility> eligible = new ArrayList<>();
		for (int index = 0; index < facilities.size(); index++) {
			VetFacility facility = facilities.get(index);
			if (Boolean.TRUE.equals(facility.getAvailable24Hours())
					&& facility.hasCurrentDistance(stagingGeneration)
					&& "openrouteservice/driving-car".equals(facility.getDistanceMethod())) {
				eligible.add(new IndexedFacility(index, facility));
			}
		}
		eligible.sort(Comparator.comparingDouble((IndexedFacility item) -> item.facility().getDistanceMeters())
				.thenComparingInt(IndexedFacility::index));
		return eligible.stream().limit(count).map(item -> item.facility().copy()).toList();
	}

	/**
	 * Preserves stable tie ordering for equal road distances.
	 *
	 * @param index source order.
	 * @param facility facility record.
	 */
	private record IndexedFacility(int index, VetFacility facility) {
	}
}
