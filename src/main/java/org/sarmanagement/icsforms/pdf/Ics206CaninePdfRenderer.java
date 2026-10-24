/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.pdf;

import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.CanineMedicalPlan;
import org.sarmanagement.icsforms.model.StagingLocation;
import org.sarmanagement.icsforms.model.VetFacility;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Renders ICS 206CANINE as an adapted canine medical plan, not an official FEMA variant.
 */
public class Ics206CaninePdfRenderer extends AbstractPdfRenderer implements PdfFormRenderer {
	private static final String FORM_KEY = "ICS 206CANINE";
	private static final String TITLE = "CANINE MEDICAL PLAN (ADAPTED)";

	/** {@inheritDoc} */
	@Override
	public String getFormKey() {
		return FORM_KEY;
	}

	/**
	 * Counts pages using the same table layout as the exported adapted form.
	 *
	 * @param data incident data.
	 * @return page count, at least one.
	 */
	public static int pageCount(AppData data) {
		AppData safeData = data == null ? new AppData() : data;
		Ics206CaninePdfRenderer renderer = new Ics206CaninePdfRenderer();
		CanineMedicalPlan plan = safeData.getCanineMedicalPlan();
		return Ics206FormLayout.pageCount(renderer, safeData, FORM_KEY, TITLE, plan.getIapPage(), plan.getPreparedAt(),
				renderer.sections(safeData));
	}

	/** {@inheritDoc} */
	@Override
	public void render(AppData data, Path outputFile) throws IOException {
		AppData safeData = data == null ? new AppData() : data;
		CanineMedicalPlan plan = safeData.getCanineMedicalPlan();
		Ics206FormLayout.render(this, safeData, outputFile, FORM_KEY, TITLE, plan.getIapPage(), plan.getPreparedAt(),
				sections(safeData));
	}

	/**
	 * Builds the adapted canine medical plan blocks.
	 *
	 * @param data incident document.
	 * @return ordered printable sections.
	 */
	private List<Ics206FormLayout.Section> sections(AppData data) {
		CanineMedicalPlan plan = data.getCanineMedicalPlan();
		return List.of(aidStations(plan), transportation(plan), veterinaryHospitals(data),
				procedures(plan), preparedBy(plan, data.getMedicalUnitLeaderAssignment().getName()), reviewedBy(plan));
	}

	/**
	 * Creates the aid-station text block for section 3.
	 *
	 * @param plan canine medical plan.
	 * @return numbered aid-station section.
	 */
	private Ics206FormLayout.Section aidStations(CanineMedicalPlan plan) {
		return textSection("3. MEDICAL AID STATIONS", plan.getAidStationInformation(),
				"No canine aid station information entered.");
	}

	/**
	 * Creates the ground and air transportation text block for section 4.
	 *
	 * @param plan canine medical plan.
	 * @return numbered transportation section.
	 */
	private Ics206FormLayout.Section transportation(CanineMedicalPlan plan) {
		return textSection("4. TRANSPORTATION (AIR OR GROUND)", plan.getTransportServices(),
				"No canine transport information entered.");
	}

	/**
	 * Creates the veterinary hospital grid with route and availability notes.
	 *
	 * @param data incident document containing the selected plan snapshots and base.
	 * @return numbered veterinary hospital section.
	 */
	private Ics206FormLayout.Section veterinaryHospitals(AppData data) {
		CanineMedicalPlan plan = data.getCanineMedicalPlan();
		StagingLocation base = data.getStagingLocation();
		List<List<String>> rows = new ArrayList<>();
		if (plan.getSelectedFacilities().isEmpty()) {
			rows.add(List.of("No veterinary facilities selected.", "", "", "", "", ""));
		} else {
			for (VetFacility facility : plan.getSelectedFacilities()) {
				if (facility != null) {
					String routeOrigin = base.getLabel().isBlank() ? "staging/base" : base.getLabel();
					routeOrigin = join(routeOrigin, safe(base.getAddress()));
					String route = facility.hasCurrentDistance(base.getGeneration())
							? formatDistance(facility.getDistanceMeters()) + " from " + routeOrigin
									+ formatDuration(facility.getDurationSeconds())
									+ (facility.getDistanceMethod().isBlank() ? ""
											: " (" + facility.getDistanceMethod() + ")")
							: "Distance not calculated from " + routeOrigin;
					String notes = join(safe(facility.getCapabilities()), safe(facility.getAvailabilityNotes()));
					notes = join(notes, route);
					rows.add(List.of(safe(facility.getName()), join(safe(facility.getTown()), safe(facility.getState())),
							join(safe(facility.getAddress()), safe(facility.getPostalCode())),
							join(facility.getContactNumbers() == null ? "" : String.join("; ",
									facility.getContactNumbers()),
									safe(facility.getFax()).isBlank() ? "" : "Fax: " + safe(facility.getFax())),
							availability(facility.getAvailable24Hours()),
							notes));
				}
			}
		}
		return new Ics206FormLayout.Section("5. VETERINARY HOSPITALS",
				new float[] {0.16f, 0.12f, 0.20f, 0.17f, 0.09f, 0.26f},
				List.of("Hospital Name", "Town", "Address", "Contact Number(s)", "24 Hours", "Notes"), rows);
	}

	/**
	 * Creates block 6 with veterinary contacts, procedures, and aviation guidance.
	 *
	 * @param plan canine medical plan.
	 * @return numbered special-procedures section.
	 */
	private Ics206FormLayout.Section procedures(CanineMedicalPlan plan) {
		List<List<String>> rows = new ArrayList<>();
		addIfPresent(rows, "Poison control: ", plan.getPoisonControlContact());
		addIfPresent(rows, "Onsite veterinarian: ", plan.getOnsiteVeterinarian());
		addIfPresent(rows, "Veterinary capabilities: ", plan.getVeterinaryCapabilityNotes());
		addParagraphs(rows, plan.getEmergencyProcedures());
		rows.add(List.of(plan.isAviationAssetsUsed()
				? "[X] Aviation assets are used for rescue; coordinate with Air Operations Branch."
				: "[ ] Aviation assets are used for rescue; coordinate with Air Operations Branch."));
		if (rows.isEmpty()) {
			rows.add(List.of("No special medical emergency procedures entered."));
		}
		return new Ics206FormLayout.Section("6. SPECIAL MEDICAL EMERGENCY PROCEDURES", new float[] {1f},
				List.of(), rows);
	}

	/**
	 * Creates block 7 with a printed signature line and preparation date.
	 *
	 * @param plan canine medical plan.
	 * @param data incident data for timezone formatting.
	 * @param medicalUnitLeaderName authoritative shared role name.
	 * @return numbered prepared-by section.
	 */
	private Ics206FormLayout.Section preparedBy(CanineMedicalPlan plan, String medicalUnitLeaderName) {
		return new Ics206FormLayout.Section("7. PREPARED BY (MEDICAL UNIT LEADER)",
				new float[] {0.53f, 0.47f}, List.of(),
				List.of(List.of("Name: " + safe(medicalUnitLeaderName),
						"Signature: ______________________________________________")));
	}

	/**
	 * Creates block 8 with review information and a printed signature line.
	 *
	 * @param plan canine medical plan.
	 * @return numbered reviewed-by section.
	 */
	private Ics206FormLayout.Section reviewedBy(CanineMedicalPlan plan) {
		return new Ics206FormLayout.Section("8. APPROVED BY (SAFETY OFFICER)",
				new float[] {0.53f, 0.47f}, List.of(),
				List.of(List.of("Name: " + safe(plan.getReviewedBySafetyOfficer()),
						"Signature: ______________________________________________"),
						List.of("Review status: " + safe(plan.getReviewStatus()), "")));
	}

	/**
	 * Creates a full-width paragraph block, retaining entered line breaks.
	 *
	 * @param title numbered block title.
	 * @param value source text.
	 * @param emptyMessage neutral message when no text exists.
	 * @return printable one-column section.
	 */
	private Ics206FormLayout.Section textSection(String title, String value, String emptyMessage) {
		List<List<String>> rows = new ArrayList<>();
		if (value == null || value.isBlank()) {
			rows.add(List.of(emptyMessage));
		} else {
			addParagraphs(rows, value);
		}
		return new Ics206FormLayout.Section(title, new float[] {1f}, List.of(), rows);
	}

	/**
	 * Adds nonblank procedure paragraphs as independently wrappable rows.
	 *
	 * @param rows target rows.
	 * @param value source text.
	 */
	private void addParagraphs(List<List<String>> rows, String value) {
		if (value != null && !value.isBlank()) {
			for (String line : value.split("\\R")) {
				rows.add(List.of(line));
			}
		}
	}

	/**
	 * Adds an optional labeled field when its value is present.
	 *
	 * @param rows target rows.
	 * @param label field label.
	 * @param value field value.
	 */
	private void addIfPresent(List<List<String>> rows, String label, String value) {
		if (value != null && !value.isBlank()) {
			rows.add(List.of(label + value));
		}
	}

	/**
	 * Formats a route distance in miles.
	 *
	 * @param meters route distance in meters.
	 * @return distance display.
	 */
	private String formatDistance(double meters) {
		return String.format(Locale.ROOT, "%.1f mi", meters / 1609.344d);
	}

	/**
	 * Displays available route duration only when the provider returned it.
	 *
	 * @param seconds route duration in seconds.
	 * @return duration display or empty text when unavailable.
	 */
	private String formatDuration(double seconds) {
		return Double.isFinite(seconds) ? " | " + Math.round(seconds / 60d) + " min" : "";
	}

	/**
	 * Displays unknown 24-hour availability distinctly from a confirmed answer.
	 *
	 * @param available optional availability answer.
	 * @return readable status.
	 */
	private String availability(Boolean available) {
		return available == null ? "Unknown (not verified)" : available ? "Yes" : "No";
	}

	/**
	 * Joins optional values without leaving a separator when either is blank.
	 *
	 * @param first first value.
	 * @param second second value.
	 * @return combined value.
	 */
	private String join(String first, String second) {
		if (first.isBlank()) {
			return second;
		}
		return second.isBlank() ? first : first + " | " + second;
	}

	/**
	 * Normalizes nullable printable text.
	 *
	 * @param value source value.
	 * @return non-null text.
	 */
	private String safe(String value) {
		return value == null ? "" : value;
	}
}
