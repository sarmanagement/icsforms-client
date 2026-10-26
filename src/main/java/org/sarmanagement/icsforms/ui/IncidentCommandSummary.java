/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.MedicalPlan;
import org.sarmanagement.icsforms.model.PlanReviewArea;
import org.sarmanagement.icsforms.model.RadioChannel;
import org.sarmanagement.icsforms.model.ResourceAssignment;
import org.sarmanagement.icsforms.model.SarTaskAssignment;
import org.sarmanagement.icsforms.model.SarTaskSupport;
import org.sarmanagement.icsforms.model.TCard;
import org.sarmanagement.icsforms.model.TCardType;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Derives command summary facts without assigning readiness or approval. */
final class IncidentCommandSummary {
	private static final Pattern CANINE_LABEL = Pattern.compile("(?i)\\b(?:canine|k-?9|dog)\\b");
	private static final Pattern EQUINE_LABEL = Pattern.compile("(?i)\\b(?:equine|horse)\\b");

	/** Prevents instantiation of the summary utility. */
	private IncidentCommandSummary() {
	}

	/**
	 * Tests whether veterinary planning is required by recorded animal resources.
	 * SAR mode alone does not establish the presence of animal resources.
	 *
	 * @param data current incident.
	 * @return whether canine or equine resources are recorded.
	 */
	static boolean requiresAnimalMedicalPlan(AppData data) {
		return data.getTCards().stream().filter(card -> card != null && card.getCardType() != TCardType.HEADER)
				.anyMatch(card -> card.isCanineResourceCard()
						|| isAssetCard(card) && (hasAnimalLabel(card, CANINE_LABEL) || hasAnimalLabel(card, EQUINE_LABEL)))
				|| data.getAllAssignmentResources().stream().filter(assignment -> assignment != null)
						.anyMatch(assignment -> isAnimalType(assignment.getResourceType()))
				|| data.getSarTaskAssignments().stream().filter(task -> task != null)
						.anyMatch(task -> isAnimalType(task.getResourceType()));
	}

	/**
	 * Recognizes explicit animal resource-type labels, not arbitrary narrative.
	 *
	 * @param type recorded resource-type label.
	 * @return whether this is a canine or equine type.
	 */
	private static boolean isAnimalType(String type) {
		return SarTaskSupport.RESOURCE_TYPE_CANINE.equalsIgnoreCase(type)
				|| SarTaskSupport.RESOURCE_TYPE_EQUINE.equalsIgnoreCase(type);
	}

	/**
	 * Restricts name-based legacy animal recognition to non-personnel assets.
	 *
	 * @param card recorded resource card.
	 * @return whether a generic or equipment card can represent an animal.
	 */
	private static boolean isAssetCard(TCard card) {
		return card.getCardType() == TCardType.EQUIPMENT || card.getCardType() == TCardType.MISC_EQUIPMENT
				|| card.getCardType() == TCardType.GENERIC;
	}

	/**
	 * Recognizes explicit animal words in asset labels without substring guesses
	 * such as interpreting a hotdog cart as a canine.
	 *
	 * @param card recorded resource card.
	 * @param label recognized animal-label pattern.
	 * @return whether an identifier or resource name contains a known animal label.
	 */
	private static boolean hasAnimalLabel(TCard card, Pattern label) {
		return label.matcher(card.getResourceIdentifier()).find() || label.matcher(card.getPersonName()).find();
	}

	/**
	 * Distinguishes known canine equipment from other ICS 219-7 equipment.
	 *
	 * @param card recorded resource card.
	 * @return precise recorded resource group.
	 */
	static String resourceGroup(TCard card) {
		if (card.getCardType() == TCardType.EQUIPMENT
				&& (card.isCanineResourceCard() || hasAnimalLabel(card, CANINE_LABEL))) {
			return "219-7 Equipment — Canine";
		}
		return card.getCardType() == null ? "Unspecified resource type" : card.getCardType().getLabel();
	}

	/**
	 * Counts ICS 204 assignment rows with substantive entered content, excluding
	 * generated identities, objective links, and the default leader-role label.
	 *
	 * @param data current incident.
	 * @return populated assignment row count, not a completeness assessment.
	 */
	static long populatedAssignments(AppData data) {
		return data.getAllAssignmentResources().stream().filter(assignment -> assignment != null)
				.filter(assignment -> assignment.getNumberOfPersons() > 0
						|| java.util.stream.Stream.of(assignment.getAssignmentTeamNumber(), assignment.getResourceType(),
								assignment.getTaskType(), assignment.getResourceIdentifier(), assignment.getLeader(),
								assignment.getContact(), assignment.getReportingLocation(), assignment.getSpecialEquipment(),
								assignment.getSupplies(), assignment.getRemarks(), assignment.getNotes(), assignment.getAssignment())
								.anyMatch(value -> value != null && !value.isBlank()))
				.count();
	}

	/**
	 * Counts substantive rows and narrative sections using the same presence
	 * criteria for command summaries and reference windows. Presence is not a
	 * validated completeness or readiness assessment.
	 *
	 * @param data active incident.
	 * @param area plan area.
	 * @return populated content-item count, excluding defaults and review metadata.
	 */
	static long recordedContent(AppData data, PlanReviewArea area) {
		return switch (area) {
			case SAFETY -> data.getForm215a().getEntries().stream().filter(Objects::nonNull)
					.filter(row -> hasText(row.getHazardsRisks(), row.getMitigations(), row.getEquipmentPpe())).count()
					+ data.getForm208().getHazardBlocks().stream().filter(Objects::nonNull)
							.filter(row -> hasText(row.getHazard(), row.getPrecautions())).count()
					+ countText(data.getForm208().getFreeText(), data.getForm208().getGeneralRequirements())
					+ countText(data.getForm208().getSiteSafetyPlan().getPlanName(),
							data.getForm208().getSiteSafetyPlan().getLocatedAt(),
							data.getForm208().getSiteSafetyPlan().getRevision())
					+ data.getForm208().getSiteSafetyPlan().getTextSections().values().stream()
							.filter(value -> hasText(value)).count()
					+ data.getSarTaskAssignments().stream().filter(task -> hasText(task.getHazardsObserved())).count();
			case HUMAN_MEDICAL -> humanMedicalContent(data.getMedicalPlan());
			case CANINE_MEDICAL -> countText(data.getCanineMedicalPlan().getAidStationInformation(),
					data.getCanineMedicalPlan().getTransportServices(), data.getCanineMedicalPlan().getPoisonControlContact(),
					data.getCanineMedicalPlan().getOnsiteVeterinarian(), data.getCanineMedicalPlan().getVeterinarianDvm(),
					data.getCanineMedicalPlan().getVeterinaryCapabilityNotes(),
					data.getCanineMedicalPlan().getEmergencyProcedures())
					+ transportCount(data.getCanineMedicalPlan().getTransportServiceEntries())
					+ data.getCanineMedicalPlan().getSelectedFacilities().stream().filter(Objects::nonNull)
							.filter(facility -> hasText(facility.getName(), facility.getAddress())).count()
					+ data.getCanineMedicalPlan().getVeterinaryPersonnelResourceIds().stream()
							.filter(value -> hasText(value)).distinct().count();
			case COMMUNICATIONS -> countText(data.getForm205().getSpecialInstructions())
					+ data.getForm205().getChannels().stream().filter(Objects::nonNull)
							.filter(channel -> hasText(channel.getZoneGroup(), channel.getChannelNumber(),
									channel.getFunction(), channel.getChannelName(), channel.getAssignment(),
									channel.getRxFrequency(), channel.getRxBandwidth(), channel.getRxToneNac(),
									channel.getTxFrequency(), channel.getTxBandwidth(), channel.getTxToneNac(),
									channel.getMode(), channel.getDigitalProtocol(), channel.getSystem(),
									channel.getRemarks(), channel.getCoverageGuidance(), channel.getRelayGuidance())
									|| !RadioChannel.TX_POLICY_UNKNOWN.equals(channel.getTxPolicy())
									|| channel.getProgrammingMetadata().values().stream().anyMatch(value -> hasText(value)))
							.count();
		};
	}

	/**
	 * Counts recorded human medical rows and text, not the built-in Call 911 default.
	 *
	 * @param plan human medical plan.
	 * @return populated content items.
	 */
	private static long humanMedicalContent(MedicalPlan plan) {
		return countText(plan.getDesignatedMedicalUnitRole(), plan.getDesignatedMedicalUnitName(),
				plan.getDesignatedMedicalUnitContact(), plan.getEmergencyProcedures(), plan.getAirOperationsReminder())
				+ (hasText(plan.getCall911Procedure()) && !"Call 911".equals(plan.getCall911Procedure().trim()) ? 1 : 0)
				+ plan.getAidStations().stream().filter(Objects::nonNull)
						.filter(row -> hasText(row.getName(), row.getLocation(), row.getContactNumbers(),
								row.getFrequency()) || row.getParamedicsOnSite() != null).count()
				+ transportCount(plan.getTransportServices())
				+ plan.getHospitals().stream().filter(Objects::nonNull)
						.filter(row -> hasText(row.getName(), row.getAddress(),
						row.getContactNumbers(), row.getFrequency(), row.getHelipadCoordinates(), row.getAirTravelTime(),
						row.getGroundTravelTime(), row.getTraumaLevel()) || row.getLatitude() != null
						|| row.getLongitude() != null || row.getTraumaCenter() != null || row.getBurnCenter() != null
						|| row.getHelipad() != null).count();
	}

	/**
	 * Counts transport rows with entered service information.
	 *
	 * @param services recorded transport rows.
	 * @return populated row count.
	 */
	private static long transportCount(List<MedicalPlan.TransportService> services) {
		return services.stream().filter(Objects::nonNull)
				.filter(row -> hasText(row.getService(), row.getLocation(), row.getContactNumbers(),
						row.getFrequency(), row.getAirOrGround(), row.getLevelOfService())).count();
	}

	/**
	 * Counts nonblank narrative sections.
	 *
	 * @param values section text.
	 * @return populated section count.
	 */
	private static long countText(String... values) {
		return java.util.Arrays.stream(values).filter(value -> value != null && !value.isBlank()).count();
	}

	/**
	 * Tests whether a row has any entered text.
	 *
	 * @param values row fields.
	 * @return whether at least one field is populated.
	 */
	private static boolean hasText(String... values) {
		return countText(values) > 0;
	}

	/**
	 * Counts linked assignments by recorded task lifecycle using stable assignment
	 * identifiers. Unlinked tasks never contribute; absent or conflicting records
	 * remain explicit rather than being inferred as planned or complete.
	 *
	 * @param data current incident.
	 * @param objectiveId stable objective identifier.
	 * @return lifecycle counts suitable for display below an objective.
	 */
	static String assignmentStates(AppData data, String objectiveId) {
		Map<String, Integer> counts = new LinkedHashMap<>();
		Set<String> seen = new LinkedHashSet<>();
		for (ResourceAssignment assignment : data.getAllAssignmentResources()) {
			if (assignment == null || !assignment.getObjectiveIds().contains(objectiveId)) {
				continue;
			}
			String id = assignment.getAssignmentId();
			if (id != null && !id.isBlank() && !seen.add(id)) {
				continue;
			}
			Set<String> states = new LinkedHashSet<>();
			if (id != null && !id.isBlank()) {
				for (SarTaskAssignment task : data.getSarTaskAssignments()) {
					if (task != null && id.equals(task.getAssignmentId())) {
						states.add(task.getTaskLifecycleStatus());
					}
				}
			}
			String state = states.isEmpty() ? "No lifecycle recorded"
					: states.size() == 1 ? states.iterator().next() : "Conflicting lifecycle records";
			counts.merge(state, 1, Integer::sum);
		}
		return counts.isEmpty() ? "No linked assignments" : counts.entrySet().stream()
				.map(entry -> entry.getKey() + ": " + entry.getValue()).collect(Collectors.joining("; "));
	}
}
