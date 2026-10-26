/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Computes deterministic substantive-content boundaries for plan review.
 */
public final class PlanReviewSupport {
	private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule())
			.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
	private static final Set<String> PRESENTATION_FIELDS = Set.of("iapPage", "form205aIapPage", "formNumber");

	/** Prevents utility construction. */
	private PlanReviewSupport() {
	}

	/**
	 * Computes the current substantive digest for one plan area.
	 *
	 * @param data active incident.
	 * @param area review area.
	 * @return lowercase hexadecimal SHA-256 digest.
	 */
	public static String contentDigest(AppData data, PlanReviewArea area) {
		Map<String, Object> content = new LinkedHashMap<>();
		content.put("incidentName", data.getIncidentContext().getIncidentName());
		content.put("operationalPeriodStart", data.getIncidentContext().getOperationalPeriodStart());
		content.put("operationalPeriodEnd", data.getIncidentContext().getOperationalPeriodEnd());
		switch (area) {
			case SAFETY -> {
				content.put("analysis", data.getForm215a());
				content.put("message", data.getForm208());
				content.put("reportedHazards", data.getSarTaskAssignments().stream()
						.map(task -> ListValue.of(task.getAssignmentId(), task.getHazardsObserved(),
								task.getSafetyHazardAnalysisEntryId(), task.getSafetyHazardNotedBy()))
						.toList());
			}
			case HUMAN_MEDICAL -> {
				content.put("medicalUnitLeader", data.getMedicalUnitLeaderAssignment());
				content.put("plan", data.getMedicalPlan());
			}
			case CANINE_MEDICAL -> {
				content.put("medicalUnitLeader", data.getMedicalUnitLeaderAssignment());
				content.put("plan", data.getCanineMedicalPlan());
			}
			case COMMUNICATIONS -> content.put("plan", data.getForm205());
			default -> throw new IllegalArgumentException("Unsupported review area: " + area);
		}
		JsonNode tree = MAPPER.valueToTree(content);
		removePresentationFields(tree);
		try {
			byte[] canonical = MAPPER.writeValueAsBytes(tree);
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical);
			return java.util.HexFormat.of().formatHex(digest);
		} catch (java.io.IOException | NoSuchAlgorithmException exception) {
			throw new IllegalStateException("Unable to calculate plan review boundary", exception);
		}
	}

	/**
	 * Removes generated page metadata recursively while preserving substantive plan
	 * values.
	 *
	 * @param node tree to normalize.
	 */
	private static void removePresentationFields(JsonNode node) {
		if (node instanceof ObjectNode object) {
			for (String field : PRESENTATION_FIELDS) {
				object.remove(field);
			}
			Iterator<JsonNode> children = object.elements();
			while (children.hasNext()) {
				removePresentationFields(children.next());
			}
		} else if (node != null && node.isArray()) {
			node.elements().forEachRemaining(PlanReviewSupport::removePresentationFields);
		}
	}

	/**
	 * Small stable value used to avoid serializing whole SAR task objects into the
	 * safety boundary.
	 *
	 * @param assignmentId assignment identity.
	 * @param hazards reported hazards.
	 * @param analysisId promoted analysis row identity.
	 * @param notedBy person recording review.
	 */
	private record ListValue(String assignmentId, String hazards, String analysisId, String notedBy) {
		/**
		 * Creates a null-safe boundary value.
		 *
		 * @param assignmentId assignment identity.
		 * @param hazards reported hazards.
		 * @param analysisId promoted analysis row identity.
		 * @param notedBy person recording review.
		 * @return stable value.
		 */
		private static ListValue of(String assignmentId, String hazards, String analysisId, String notedBy) {
			return new ListValue(safe(assignmentId), safe(hazards), safe(analysisId), safe(notedBy));
		}
	}

	/**
	 * Converts null strings to empty values.
	 *
	 * @param value input.
	 * @return non-null value.
	 */
	private static String safe(String value) {
		return value == null ? "" : value;
	}
}
