/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.CanineMedicalPlan;
import org.sarmanagement.icsforms.model.UtcTimestamp;
import org.sarmanagement.icsforms.model.VetFacility;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Shared canine veterinary-facility presentation operations for editable and reference surfaces.
 */
final class CanineMedicalFacilityTools {
	private CanineMedicalFacilityTools() {
	}

	/**
	 * Builds a sorted view of accepted canine-plan facilities followed by unselected directory candidates.
	 *
	 * @param data incident containing the canine plan and veterinary directory.
	 * @return detached display records in route-distance order within each source group.
	 */
	static List<FacilityDisplay> referenceFacilities(AppData data) {
		CanineMedicalPlan plan = data.getCanineMedicalPlan();
		List<VetFacility> accepted = new ArrayList<>(plan.getSelectedFacilities());
		accepted.sort(Comparator.comparingDouble(CanineMedicalFacilityTools::sortableDistance));
		List<FacilityDisplay> displays = new ArrayList<>();
		for (VetFacility facility : accepted) {
			displays.add(facilityDisplay(facility, "[Accepted plan]", data));
		}
		List<String> acceptedIds = accepted.stream().map(VetFacility::getId).toList();
		data.getVetDirectory().getFacilities().stream()
				.filter(facility -> !acceptedIds.contains(facility.getId()))
				.sorted(Comparator.comparingDouble(CanineMedicalFacilityTools::sortableDistance))
				.map(facility -> facilityDisplay(facility, "[Directory candidate]", data))
				.forEach(displays::add);
		return List.copyOf(displays);
	}

	/**
	 * Formats a facility's recorded road distance.
	 *
	 * @param facility veterinary facility.
	 * @return distance in miles, or {@code unrouted}.
	 */
	static String distanceLabel(VetFacility facility) {
		return distanceValue(facility).isBlank() ? "unrouted" : distanceValue(facility) + " mi";
	}

	/**
	 * Formats a road distance as a table-friendly numeric value.
	 *
	 * @param facility veterinary facility.
	 * @return miles to one decimal place, or blank when unavailable.
	 */
	static String distanceValue(VetFacility facility) {
		return Double.isFinite(facility.getDistanceMeters())
				? String.format(java.util.Locale.ROOT, "%.1f", facility.getDistanceMeters() / 1609.344)
				: "";
	}

	/**
	 * Formats a facility's recorded route duration.
	 *
	 * @param facility veterinary facility.
	 * @return duration in minutes, or {@code duration unknown}.
	 */
	static String durationLabel(VetFacility facility) {
		return durationValue(facility).isBlank()
				? "duration unknown"
				: Math.round(facility.getDurationSeconds() / 60d) + " min";
	}

	/**
	 * Formats a route duration as a table-friendly numeric value.
	 *
	 * @param facility veterinary facility.
	 * @return minutes to one decimal place, or blank when unavailable.
	 */
	static String durationValue(VetFacility facility) {
		return Double.isFinite(facility.getDurationSeconds())
				? String.format(java.util.Locale.ROOT, "%.1f", facility.getDurationSeconds() / 60)
				: "";
	}

	/**
	 * Formats the recorded availability state for a veterinary facility.
	 *
	 * @param available whether the facility is available around the clock.
	 * @return availability label.
	 */
	static String availabilityLabel(Boolean available) {
		return available == null ? "Unknown" : available ? "Yes" : "No";
	}

	/**
	 * Sorts facilities with finite routes first, ordered by distance.
	 *
	 * @param facility veterinary facility.
	 * @return route distance or positive infinity when unrouted.
	 */
	private static double sortableDistance(VetFacility facility) {
		return Double.isFinite(facility.getDistanceMeters())
				? facility.getDistanceMeters()
				: Double.POSITIVE_INFINITY;
	}

	/**
	 * Builds a detached display record from the shared canine facility model.
	 *
	 * @param facility source facility.
	 * @param source plan or directory source label.
	 * @param data active incident for origin freshness details.
	 * @return formatted facility display.
	 */
	private static FacilityDisplay facilityDisplay(VetFacility facility, String source, AppData data) {
		String age = facility.getCalculatedAt() == null
				? "age unknown"
				: Duration.between(facility.getCalculatedAt(), UtcTimestamp.nowInstant()).toHours() + " h old";
		String current = facility.hasCurrentDistance(data.getStagingLocation().getGeneration())
				? "current origin generation"
				: "stale/other origin";
		String origin = data.getStagingLocation().getLabel() + " — " + data.getStagingLocation().getAddress();
		String detail = source + "\n" + facility.getName() + "\n" + facility.getAddress() + ", "
				+ facility.getTown() + ", " + facility.getState() + " " + facility.getPostalCode()
				+ "\nContacts: " + String.join(", ", facility.getContactNumbers()) + "\nAvailability: "
				+ availabilityLabel(facility.getAvailable24Hours()) + " 24-hour; " + facility.getAvailabilityNotes()
				+ "\nCapabilities: " + facility.getCapabilities() + "\nVerified: "
				+ UiSupport.formatInstant(facility.getVerifiedAt())
				+ "\nOrigin: " + origin + "\nRoute: " + distanceLabel(facility) + ", " + durationLabel(facility)
				+ " | " + facility.getDistanceMethod() + " | " + facility.getDistanceProvenance() + " | " + age
				+ " | " + current;
		return new FacilityDisplay(source, source + " " + facility.getName() + " — " + distanceLabel(facility),
				detail);
	}

	/**
	 * Read-only facility presentation detached from incident-owned facility instances.
	 *
	 * @param source plan or directory source label.
	 * @param label visible list label.
	 * @param detail visible facility details.
	 */
	record FacilityDisplay(String source, String label, String detail) {
	}
}
