/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.CanineMedicalPlan;
import org.sarmanagement.icsforms.model.StagingLocation;
import org.sarmanagement.icsforms.model.UtcTimestamp;
import org.sarmanagement.icsforms.model.VetDirectory;
import org.sarmanagement.icsforms.model.VetFacility;
import org.sarmanagement.icsforms.medical.DrivingRouteProvider;
import org.sarmanagement.icsforms.medical.VetRoutingService;
import org.sarmanagement.icsforms.medical.VetShortlistSelector;

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
		directoryFacilities(data).stream()
				.filter(facility -> !acceptedIds.contains(facility.getId()))
				.sorted(Comparator.comparingDouble(CanineMedicalFacilityTools::sortableDistance))
				.map(facility -> facilityDisplay(facility, "[Directory candidate]", data))
				.forEach(displays::add);
		return List.copyOf(displays);
	}

	/**
	 * Returns an ordered list view of the incident's veterinary directory entries.
	 *
	 * @param data incident containing the veterinary directory.
	 * @return directory records in their recorded order.
	 */
	static List<VetFacility> directoryFacilities(AppData data) {
		return List.copyOf(data.getVetDirectory().getFacilities());
	}

	/**
	 * Finds a directory facility by its stable identifier.
	 *
	 * @param data incident containing the veterinary directory.
	 * @param facilityId stable facility identifier.
	 * @return matching directory record, or null when absent.
	 */
	static VetFacility findDirectoryFacility(AppData data, String facilityId) {
		return data.getVetDirectory().getFacilities().stream()
				.filter(facility -> facilityId != null && facilityId.equals(facility.getId()))
				.findFirst().orElse(null);
	}

	/**
	 * Resolves the selected accepted-plan or directory facility for reference display.
	 *
	 * @param data incident containing the canine plan and veterinary directory.
	 * @param facilityId stable facility identifier selected in the reference.
	 * @return current read-only facility display, or null when the facility was removed.
	 */
	static FacilityDisplay selectedFacility(AppData data, String facilityId) {
		CanineMedicalPlan plan = data.getCanineMedicalPlan();
		VetFacility accepted = plan.getSelectedFacilities().stream()
				.filter(facility -> facilityId != null && facilityId.equals(facility.getId()))
				.findFirst().orElse(null);
		if (accepted != null) {
			return facilityDisplay(accepted, "[Accepted plan]", data);
		}
		VetFacility candidate = findDirectoryFacility(data, facilityId);
		return candidate == null ? null : facilityDisplay(candidate, "[Directory candidate]", data);
	}

	/**
	 * Proposes current route-confirmed facilities using the shared shortlist policy.
	 *
	 * @param data incident containing the veterinary directory and staging generation.
	 * @param limit maximum number of proposals.
	 * @return detached candidate snapshots in route order.
	 */
	static List<VetFacility> proposeFacilities(AppData data, int limit) {
		return new VetShortlistSelector().propose(directoryFacilities(data), limit,
				data.getStagingLocation().getGeneration());
	}

	/**
	 * Calculates routes through the existing provider service for an explicit workspace action.
	 *
	 * @param provider ground route provider.
	 * @param staging incident staging/base location.
	 * @param directory incident-owned directory snapshot.
	 * @param refreshCached whether current cached routes should be refreshed.
	 * @return route summary and warnings.
	 * @throws InterruptedException when the calculation is cancelled.
	 */
	static VetRoutingService.CalculationReport calculateRoutes(DrivingRouteProvider provider,
			StagingLocation staging, VetDirectory directory, boolean refreshCached) throws InterruptedException {
		return new VetRoutingService(provider).calculate(staging, directory, refreshCached);
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
		return new FacilityDisplay(facility.getId(), source,
				source + " " + facility.getName() + " — " + distanceLabel(facility),
				detail);
	}

	/**
	 * Read-only facility presentation detached from incident-owned facility instances.
	 *
	 * @param facilityId stable facility identifier.
	 * @param source plan or directory source label.
	 * @param label visible list label.
	 * @param detail visible facility details.
	 */
	record FacilityDisplay(String facilityId, String source, String label, String detail) {
	}
}
