/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.pdf;

import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.MedicalPlan;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders the standard human ICS 206 Medical Plan using numbered form blocks.
 */
public class Ics206PdfRenderer extends AbstractPdfRenderer implements PdfFormRenderer {
	private static final String FORM_KEY = "ICS 206";
	private static final String TITLE = "MEDICAL PLAN";

	/** {@inheritDoc} */
	@Override
	public String getFormKey() {
		return FORM_KEY;
	}

	/**
	 * Counts pages using the same table layout as the exported form.
	 *
	 * @param data incident data.
	 * @return page count, at least one.
	 */
	public static int pageCount(AppData data) {
		AppData safeData = data == null ? new AppData() : data;
		Ics206PdfRenderer renderer = new Ics206PdfRenderer();
		MedicalPlan plan = safeData.getMedicalPlan();
		return Ics206FormLayout.pageCount(renderer, safeData, FORM_KEY, TITLE, plan.getIapPage(),
				plan.getPreparedAt(), renderer.sections(safeData), renderer.footerMetadata(safeData));
	}

	/** {@inheritDoc} */
	@Override
	public void render(AppData data, Path outputFile) throws IOException {
		AppData safeData = data == null ? new AppData() : data;
		MedicalPlan plan = safeData.getMedicalPlan();
		Ics206FormLayout.render(this, safeData, outputFile, FORM_KEY, TITLE, plan.getIapPage(), plan.getPreparedAt(),
				sections(safeData), footerMetadata(safeData));
	}

	/**
	 * Builds the numbered human ICS 206 content and table columns.
	 *
	 * @param data incident document.
	 * @return ordered printable sections.
	 */
	private List<Ics206FormLayout.Section> sections(AppData data) {
		MedicalPlan plan = data.getMedicalPlan();
		List<Ics206FormLayout.Section> sections = new ArrayList<>();
		sections.add(aidStations(plan));
		sections.add(transportation(plan));
		sections.add(hospitals(plan));
		sections.add(procedures(plan));
		return sections;
	}

	/**
	 * Builds preparer and reviewer values for the fixed bottom metadata block.
	 *
	 * @param data incident document.
	 * @return bottom metadata values.
	 */
	private Ics206FormLayout.FooterMetadata footerMetadata(AppData data) {
		return new Ics206FormLayout.FooterMetadata(data.getMedicalUnitLeaderAssignment().getName(),
				data.getMedicalPlan().getReviewedBySafetyOfficer());
	}

	/**
	 * Creates the aid-station grid for block 3.
	 *
	 * @param plan human medical plan.
	 * @return numbered aid-station section.
	 */
	private Ics206FormLayout.Section aidStations(MedicalPlan plan) {
		List<List<String>> rows = new ArrayList<>();
		if (plan.isSimpleMode() || plan.getAidStations().isEmpty()) {
			rows.add(List.of(plan.isSimpleMode() ? "Detailed aid station listings not entered."
					: "No medical aid stations entered.", "", "", ""));
		} else {
			for (MedicalPlan.AidStation station : plan.getAidStations()) {
				if (station != null) {
					rows.add(List.of(safe(station.getName()), safe(station.getLocation()),
							join(safe(station.getContactNumbers()), safe(station.getFrequency())),
							yesNoUnknown(station.getParamedicsOnSite())));
				}
			}
		}
		return new Ics206FormLayout.Section("3. MEDICAL AID STATIONS",
				new float[] {0.20f, 0.24f, 0.34f, 0.22f},
				List.of("Aid Station", "Location", "Contact Number(s) / Frequency", "Paramedics On Site"), rows);
	}

	/**
	 * Creates the air and ground transportation grid for block 4.
	 *
	 * @param plan human medical plan.
	 * @return numbered transportation section.
	 */
	private Ics206FormLayout.Section transportation(MedicalPlan plan) {
		List<List<String>> rows = new ArrayList<>();
		if (plan.isSimpleMode() || plan.getTransportServices().isEmpty()) {
			rows.add(List.of(plan.isSimpleMode() ? "Detailed transport listings not entered."
					: "No transport services entered.", "", "", ""));
		} else {
			for (MedicalPlan.TransportService service : plan.getTransportServices()) {
				if (service != null) {
					rows.add(List.of(safe(service.getService()), safe(service.getLocation()),
							join(safe(service.getContactNumbers()), safe(service.getFrequency())),
							join(safe(service.getAirOrGround()), safe(service.getLevelOfService()))));
				}
			}
		}
		return new Ics206FormLayout.Section("4. TRANSPORTATION (AIR OR GROUND)",
				new float[] {0.23f, 0.22f, 0.29f, 0.26f},
				List.of("Service", "Location", "Contact Number(s) / Frequency", "Type / Level of Service"), rows);
	}

	/**
	 * Creates the hospital grid for block 5.
	 *
	 * @param plan human medical plan.
	 * @return numbered hospital section.
	 */
	private Ics206FormLayout.Section hospitals(MedicalPlan plan) {
		List<List<String>> rows = new ArrayList<>();
		if (plan.isSimpleMode() || plan.getHospitals().isEmpty()) {
			rows.add(List.of(plan.isSimpleMode() ? "Detailed hospital listings not entered."
					: "No hospitals entered.", "", "", "", ""));
		} else {
			for (MedicalPlan.Hospital hospital : plan.getHospitals()) {
				if (hospital != null) {
					rows.add(List.of(safe(hospital.getName()), safe(hospital.getAddress()),
							join(safe(hospital.getContactNumbers()), safe(hospital.getFrequency())),
							travelTimes(hospital), capabilities(hospital)));
				}
			}
		}
		return new Ics206FormLayout.Section("5. HOSPITALS",
				new float[] {0.16f, 0.23f, 0.20f, 0.20f, 0.21f},
				List.of("Hospital", "Address", "Contact / Frequency", "Travel Time (Air / Ground)",
						"Services / Capabilities"), rows);
	}

	/**
	 * Creates block 6 with emergency instructions, procedures, and aviation guidance.
	 *
	 * @param plan human medical plan.
	 * @return numbered special-procedures section.
	 */
	private Ics206FormLayout.Section procedures(MedicalPlan plan) {
		List<List<String>> rows = new ArrayList<>();
		if (plan.isSimpleMode()) {
			rows.add(List.of("Emergency instruction: " + safe(plan.getCall911Procedure())));
			addIfPresent(rows, "Designated medical unit role: ", plan.getDesignatedMedicalUnitRole());
			addIfPresent(rows, "Designated person: ", plan.getDesignatedMedicalUnitName());
			addIfPresent(rows, "Contact: ", plan.getDesignatedMedicalUnitContact());
		}
		addParagraphs(rows, plan.getEmergencyProcedures());
		rows.add(List.of(plan.isAviationAssetsUsed()
				? "[X] Aviation assets are used for rescue; coordinate with Air Operations Branch."
				: "[ ] Aviation assets are used for rescue; coordinate with Air Operations Branch."));
		addIfPresent(rows, "Air Operations: ", plan.getAirOperationsReminder());
		if (rows.isEmpty()) {
			rows.add(List.of("No special medical emergency procedures entered."));
		}
		return new Ics206FormLayout.Section("6. SPECIAL MEDICAL EMERGENCY PROCEDURES", new float[] {1f},
				List.of(), rows);
	}

	/**
	 * Combines travel time values with their recorded provenance.
	 *
	 * @param hospital hospital record.
	 * @return air and ground travel time.
	 */
	private String travelTimes(MedicalPlan.Hospital hospital) {
		return "Air: " + join(safe(hospital.getAirTravelTime()), safe(hospital.getAirTravelProvenance()))
				+ " | Ground: " + join(safe(hospital.getGroundTravelTime()),
						safe(hospital.getGroundTravelProvenance()));
	}

	/**
	 * Combines hospital coordinates and medical capabilities without dropping unknowns.
	 *
	 * @param hospital hospital record.
	 * @return printable capability details.
	 */
	private String capabilities(MedicalPlan.Hospital hospital) {
		String coordinates = safe(hospital.getHelipadCoordinates());
		if (coordinates.isBlank() && hospital.getLatitude() != null && hospital.getLongitude() != null) {
			coordinates = hospital.getLatitude() + ", " + hospital.getLongitude();
		}
		return "Trauma: " + yesNoUnknown(hospital.getTraumaCenter())
				+ (hospital.getTraumaLevel().isBlank() ? "" : " (" + hospital.getTraumaLevel() + ")")
				+ " | Burn: " + yesNoUnknown(hospital.getBurnCenter())
				+ " | Helipad: " + yesNoUnknown(hospital.getHelipad())
				+ (coordinates.isBlank() ? "" : " | Coordinates: " + coordinates);
	}

	/**
	 * Adds nonblank, independently printable procedure paragraphs.
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
	 * Joins two optional values without leaving a stray delimiter.
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
	 * Formats a nullable capability answer distinctly from yes or no.
	 *
	 * @param value optional yes/no value.
	 * @return readable capability status.
	 */
	private String yesNoUnknown(Boolean value) {
		return value == null ? "UNKNOWN" : value ? "YES" : "NO";
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
