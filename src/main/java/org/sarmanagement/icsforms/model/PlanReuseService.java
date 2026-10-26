/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Set;

/**
 * Copies selected plan content without replacing incident context or carrying approvals.
 */
public final class PlanReuseService {
	private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());
	private static final Set<String> ASSERTION_FIELDS = Set.of("preparedBy", "preparedAt",
			"preparedDateTime", "preparedByMedicalUnitLeader", "preparerSignature", "reviewedBySafetyOfficer",
			"safetyOfficerSignature", "reviewStatus", "operationsSectionChiefSignature", "reviewedDateTime",
			"preparedByName", "preparedByPositionTitle", "preparedBySignature", "iapPage", "formId", "datePrepared",
			"safetyOfficerName", "operationsSectionChiefName", "incidentNumber", "entryId", "channelId", "id");

	/** Prevents utility construction. */
	private PlanReuseService() {
	}

	/**
	 * Captures an independent current-period source for cross-incident imports.
	 *
	 * @param data source incident.
	 * @return detached snapshot containing only reusable plans and period dates.
	 */
	public static AppData.OperationalPeriodRecord snapshot(AppData data) {
		AppData.OperationalPeriodRecord record = new AppData.OperationalPeriodRecord();
		record.setPeriodStart(data.getIncidentContext().getOperationalPeriodStart());
		record.setPeriodEnd(data.getIncidentContext().getOperationalPeriodEnd());
		record.setForm205(copy(data.getForm205(), Ics205Form.class));
		record.setForm215a(copy(data.getForm215a(), Ics215aForm.class));
		record.setForm208(copy(data.getForm208(), Ics208Form.class));
		record.setMedicalPlan(copy(data.getMedicalPlan(), MedicalPlan.class));
		record.setCanineMedicalPlan(copy(data.getCanineMedicalPlan(), CanineMedicalPlan.class));
		return record;
	}

	/**
	 * Identifies plan areas containing content rather than empty default scaffolding.
	 *
	 * @param source archived or imported source.
	 * @return available plan areas; legacy absent snapshots remain unavailable.
	 */
	public static Set<PlanReviewArea> availablePlans(AppData.OperationalPeriodRecord source) {
		EnumSet<PlanReviewArea> result = EnumSet.noneOf(PlanReviewArea.class);
		if (source == null) {
			return result;
		}
		if (hasContent(source.getForm205(), new Ics205Form())) {
			result.add(PlanReviewArea.COMMUNICATIONS);
		}
		if (hasContent(source.getForm215a(), new Ics215aForm())
				|| hasContent(source.getForm208(), new Ics208Form())) {
			result.add(PlanReviewArea.SAFETY);
		}
		if (hasContent(source.getMedicalPlan(), new MedicalPlan())) {
			result.add(PlanReviewArea.HUMAN_MEDICAL);
		}
		if (hasContent(source.getCanineMedicalPlan(), new CanineMedicalPlan())) {
			result.add(PlanReviewArea.CANINE_MEDICAL);
		}
		return result;
	}

	/**
	 * Replaces only selected plan areas using detached copies; leaves other plans,
	 * incident identity, period, organizational roles, resources and directories intact.
	 *
	 * @param target active incident.
	 * @param source source period snapshot.
	 * @param areas selected plan areas.
	 * @param crossIncident true to discard source-incident resource links and routing estimates.
	 * @throws IllegalArgumentException when a selected plan is unavailable.
	 */
	public static void apply(AppData target, AppData.OperationalPeriodRecord source,
			Set<PlanReviewArea> areas, boolean crossIncident) {
		if (areas.isEmpty() || !availablePlans(source).containsAll(areas)) {
			throw new IllegalArgumentException("Select at least one available plan.");
		}
		// Prepare every copy before changing the target so a malformed source cannot partially import.
		AppData.OperationalPeriodRecord detached = copy(source, AppData.OperationalPeriodRecord.class);
		for (PlanReviewArea area : areas) {
			switch (area) {
				case COMMUNICATIONS -> target.setForm205(detached.getForm205().copyForNewPeriod());
				case SAFETY -> {
					Ics215aForm analysis = detached.getForm215a() == null
							? new Ics215aForm() : detached.getForm215a();
					analysis.setIncidentNumber(target.getForm215a().getIncidentNumber());
					analysis.setSafetyOfficerName("");
					analysis.setOperationsSectionChiefName("");
					analysis.setSafetyOfficerSignature("");
					analysis.setOperationsSectionChiefSignature("");
					analysis.setPreparedDateTime(null);
					analysis.setReviewedDateTime(null);
					analysis.setIapPage("");
					Ics208Form message = detached.getForm208() == null ? new Ics208Form() : detached.getForm208();
					message.setPreparedByName("");
					message.setPreparedBySignature("");
					message.setPreparedDateTime(null);
					message.setIapPage("");
					message.getHazardBlocks().forEach(block -> block.setNeedsReview(true));
					message.getSiteSafetyPlan().setPreparedByName("");
					message.getSiteSafetyPlan().setPreparedBySignature("");
					message.getSiteSafetyPlan().setPreparedDateTime(null);
					target.setForm215a(analysis);
					target.setForm208(message);
				}
				case HUMAN_MEDICAL -> {
					MedicalPlan plan = detached.getMedicalPlan();
					plan.resetReviewState();
					plan.setPreparedByMedicalUnitLeader("");
					plan.setPreparerSignature("");
					plan.setPreparedAt(null);
					plan.setIapPage("");
					if (crossIncident) {
						for (MedicalPlan.Hospital hospital : plan.getHospitals()) {
							hospital.setAirTravelTime("");
							hospital.setGroundTravelTime("");
							hospital.setAirTravelProvenance("");
							hospital.setGroundTravelProvenance("");
						}
					}
					target.setMedicalPlan(plan);
				}
				case CANINE_MEDICAL -> {
					CanineMedicalPlan plan = detached.getCanineMedicalPlan();
					plan.resetReviewState();
					plan.setPreparedByMedicalUnitLeader("");
					plan.setPreparerSignature("");
					plan.setPreparedAt(null);
					plan.setIapPage("");
					// Resource cards are period-owned; never turn source identities into current assignments.
					plan.setVeterinaryPersonnelResourceIds(new ArrayList<>());
					if (crossIncident) {
						plan.getSelectedFacilities().forEach(VetFacility::clearDistance);
					}
					target.setCanineMedicalPlan(plan);
				}
				default -> throw new IllegalArgumentException("Unsupported plan area: " + area);
			}
			target.setReviewAssertion(area, new PlanReviewAssertion());
		}
	}

	/**
	 * Compares content while ignoring preparation and approval metadata.
	 *
	 * @param value persisted plan, possibly absent in older archives.
	 * @param empty empty plan of the same type.
	 * @return true when substantive content differs from empty scaffolding.
	 */
	private static boolean hasContent(Object value, Object empty) {
		if (value == null) {
			return false;
		}
		ObjectNode actual = MAPPER.valueToTree(value);
		ObjectNode baseline = MAPPER.valueToTree(empty);
		removeAssertions(actual);
		removeAssertions(baseline);
		return !actual.equals(baseline);
	}

	/**
	 * Removes preparation metadata from nested content before testing availability.
	 *
	 * @param node persisted content tree.
	 */
	private static void removeAssertions(JsonNode node) {
		if (node instanceof ObjectNode object) {
			object.remove(ASSERTION_FIELDS);
			object.fields().forEachRemaining(field -> {
				if (field.getValue() instanceof ArrayNode rows) {
					Object empty = emptyRow(field.getKey());
					if (empty != null) {
						JsonNode baseline = MAPPER.valueToTree(empty);
						removeAssertions(baseline);
						for (int index = rows.size() - 1; index >= 0; index--) {
							removeAssertions(rows.get(index));
							if (rows.get(index).isNull() || baseline.equals(rows.get(index))) {
								rows.remove(index);
							}
						}
					}
				}
			});
		}
		node.elements().forEachRemaining(PlanReuseService::removeAssertions);
	}

	/**
	 * Supplies blank row scaffolding for availability comparisons, not persisted copies.
	 *
	 * @param field collection property name.
	 * @return empty row of the collection's type, or null for non-row collections.
	 */
	private static Object emptyRow(String field) {
		return switch (field) {
			case "entries" -> new SafetyAnalysisEntry();
			case "hazardBlocks" -> new SafetyMessageBlock();
			case "channels" -> new RadioChannel();
			case "aidStations" -> new MedicalPlan.AidStation();
			case "transportServices", "transportServiceEntries" -> new MedicalPlan.TransportService();
			case "hospitals" -> new MedicalPlan.Hospital();
			case "selectedFacilities" -> new VetFacility();
			default -> null;
		};
	}

	/**
	 * Makes an independent persisted-value copy.
	 *
	 * @param value source value.
	 * @param type persisted type.
	 * @param <T> copied type.
	 * @return detached copy, or null when absent.
	 */
	private static <T> T copy(T value, Class<T> type) {
		return value == null ? null : MAPPER.convertValue(value, type);
	}
}
