/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.comms;

import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.CommunicationEntry;
import org.sarmanagement.icsforms.model.Ics204Form;
import org.sarmanagement.icsforms.model.Ics203Form;
import org.sarmanagement.icsforms.model.MedicalPlan;
import org.sarmanagement.icsforms.model.MedicalUnitLeaderAssignment;
import org.sarmanagement.icsforms.model.OrgChartEntry;
import org.sarmanagement.icsforms.model.OrganizationalChart;
import org.sarmanagement.icsforms.model.RadioChannel;
import org.sarmanagement.icsforms.model.ResourceAssignment;
import org.sarmanagement.icsforms.model.SarTaskAssignment;
import org.sarmanagement.icsforms.model.SarTaskResource;
import org.sarmanagement.icsforms.model.TCard;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * Read-only, UI-independent projections of current-period channel references.
 * Only an explicit, unique channel ID establishes a link: matching names or
 * frequencies never do. Results are immutable snapshots, not model objects.
 * Reading does not initialize missing identities or reconcile legacy fields.
 */
public final class ChannelLinkSource {
	/** Typed origins; labels are presentation text, never lookup keys. */
	public enum SourceKind {
		DIRECT, ICS204_COMMUNICATION, ICS204_RESOURCE, ICS204_MANAGEMENT,
		SAR_COMMUNICATION, SAR_ASSIGNMENT, SAR_RESOURCE, SAR_MANAGEMENT, TCARD,
		MEDICAL_UNIT_LEADER, MEDICAL_CONTACT, MEDICAL_AID_STATION,
		MEDICAL_TRANSPORT, MEDICAL_HOSPITAL, CANINE_MEDICAL_TRANSPORT,
		SAFETY_OFFICER, ORG_ROLE, ORG_ADDITIONAL, ORG_INCIDENT_COMMANDER,
		ORG_AGENCY_REPRESENTATIVE, ORG_DIVISION,
		ICS205_ASSIGNMENT
	}

	/** Resolution outcome, independent of whether legacy text is present. */
	public enum LinkStatus {
		/** Exactly one current channel has the explicit ID. */
		LINKED,
		/** Null, empty, or whitespace-only ID; legacy text is not matched. */
		UNLINKED,
		/** A nonblank ID has no target, including a null document or absent plan. */
		MISSING,
		/** Multiple channels have the explicit ID; none is chosen. */
		AMBIGUOUS
	}

	/**
	 * Detached channel identity and presentation label.
	 *
	 * @param id stable channel identity, never null
	 * @param label name, otherwise number, otherwise ID; never null
	 */
	public record ChannelInfo(String id, String label) {
		/**
		 * Normalizes null text without modifying the model.
		 * @param id channel identity
		 * @param label presentation label
		 */
		public ChannelInfo(String id, String label) {
			this.id = safe(id);
			this.label = safe(label);
		}
	}

	/**
	 * Detached reference snapshot. Unlinked, missing, and ambiguous references keep
	 * exact legacy text (null becomes empty); linked references display the channel
	 * label even when names are duplicated. Only LINKED results contain a channel.
	 * Source IDs use model IDs where available, with structural row suffixes for
	 * rows without IDs; these suffixes are locations, not persisted identities.
	 *
	 * @param sourceKind typed origin
	 * @param sourceId model identity/structural location, or empty for direct resolution
	 * @param sourceLabel presentation-only source description
	 * @param channelId explicit channel identity, or empty when absent
	 * @param legacyFallback original text, never inferred or overwritten
	 * @param displayLabel linked label or unchanged fallback
	 * @param status resolution outcome
	 * @param channel immutable target snapshot, or null unless linked
	 */
	public record ChannelLink(SourceKind sourceKind, String sourceId, String sourceLabel,
			String channelId, String legacyFallback, String displayLabel, LinkStatus status,
			ChannelInfo channel) {
	}

	/** Prevents instantiation of this read-only utility. */
	private ChannelLinkSource() {
	}

	/**
	 * Returns a channel's presentation label without changing its identity.
	 * Uses the nonblank name, otherwise number, otherwise existing ID; null
	 * channels and null final IDs produce empty text. Values are never trimmed.
	 *
	 * @param channel current channel, possibly null
	 * @return name, number, existing ID, or empty text
	 */
	public static String channelLabel(RadioChannel channel) {
		if (channel == null) {
			return "";
		}
		if (!safe(channel.getChannelName()).isBlank()) {
			return channel.getChannelName();
		}
		if (!safe(channel.getChannelNumber()).isBlank()) {
			return channel.getChannelNumber();
		}
		return safe(channel.getChannelId());
	}

	/**
	 * Returns all current channels in plan order, including excluded print rows.
	 * Null documents, plans, channel lists, and channel rows contribute nothing.
	 * Duplicate identities and labels remain visible so callers cannot silently
	 * turn an ambiguous ID into a link.
	 *
	 * @param data current document, possibly null
	 * @return immutable detached channel snapshots
	 */
	public static List<ChannelInfo> channels(AppData data) {
		List<ChannelInfo> result = new ArrayList<>();
		if (data != null && data.getForm205() != null) {
			for (RadioChannel channel : rows(data.getForm205().getChannels())) {
				if (channel != null) {
					result.add(new ChannelInfo(channel.getChannelId(), channelLabel(channel)));
				}
			}
		}
		return List.copyOf(result);
	}

	/**
	 * Resolves by exact, unique stable ID only, never by label or frequency.
	 * A null/blank ID is UNLINKED; an unknown ID (including with null data) is
	 * MISSING; duplicate IDs are AMBIGUOUS. All three retain the exact fallback,
	 * with null fallback normalized to empty. Nonblank IDs are not trimmed.
	 *
	 * @param data current document, possibly null
	 * @param channelId explicit identity, possibly null
	 * @param legacyFallback original free text, possibly null
	 * @return immutable direct-reference snapshot
	 */
	public static ChannelLink resolve(AppData data, String channelId, String legacyFallback) {
		return resolve(channels(data), SourceKind.DIRECT, "", "", channelId, legacyFallback);
	}

	/**
	 * Projects populated current-period references in deterministic model order.
	 * Includes ICS 204, SAR assignments and explicitly identified resources, T-cards,
	 * medical frequencies/shared leader, organization/safety roles, and ICS 205
	 * assignments. Legacy-only fields remain UNLINKED even when their text matches
	 * a channel. Empty rows and null rows are skipped. Archived plans are excluded.
	 * Resource-to-T-card joins also require a unique explicit resource ID.
	 *
	 * @param data current document, possibly null
	 * @return immutable snapshots with no live channel, list, or map exposure
	 */
	public static List<ChannelLink> links(AppData data) {
		if (data == null) {
			return List.of();
		}
		List<ChannelInfo> channels = channels(data);
		List<ChannelLink> result = new ArrayList<>();
		addForm(result, channels, data.getForm204(), "primary");
		int index = 0;
		for (Ics204Form form : rows(data.getAdditionalForms204())) {
			addForm(result, channels, form, "additional:" + index++);
		}
		index = 0;
		for (SarTaskAssignment task : rows(data.getSarTaskAssignments())) {
			if (task != null) {
				addTask(result, channels, data, task, "task:" + index);
			}
			index++;
		}
		index = 0;
		for (TCard card : rows(data.getTCards())) {
			if (card != null) {
				add(result, channels, SourceKind.TCARD, identity(card.peekResourceId(), "tcard:" + index),
						card.getDisplayLabel(), card.getRadioChannelId(), card.getRadioChannel());
			}
			index++;
		}
		addMedical(result, channels, data);
		addOrganization(result, channels, data.getOrganizationalChart());
		if (data.getForm205() != null) {
			for (RadioChannel channel : rows(data.getForm205().getChannels())) {
				if (channel != null && !safe(channel.getAssignment()).isBlank()) {
					add(result, channels, SourceKind.ICS205_ASSIGNMENT, channel.getChannelId(),
							channel.getAssignment(), channel.getChannelId(), channel.getChannelName());
				}
			}
		}
		return List.copyOf(result);
	}

	/**
	 * Returns distinct nonblank channel labels followed by original legacy text,
	 * preserving first occurrence order and spelling. This is a presentation-only
	 * suggestion list: choosing a string does not establish an identity link.
	 *
	 * @param data current document, possibly null
	 * @return immutable name/text suggestions
	 */
	public static List<String> legacyChannelNames(AppData data) {
		LinkedHashSet<String> result = new LinkedHashSet<>();
		for (ChannelInfo channel : channels(data)) {
			if (!channel.label().isBlank()) {
				result.add(channel.label());
			}
		}
		for (ChannelLink link : links(data)) {
			if (!link.legacyFallback().isBlank()) {
				result.add(link.legacyFallback());
			}
		}
		return List.copyOf(result);
	}

	/**
	 * Resolves against an already detached channel snapshot.
	 * @param channels candidate targets
	 * @param kind origin type
	 * @param sourceId model identity/location
	 * @param label presentation-only description
	 * @param channelId explicit target identity
	 * @param fallback legacy text
	 * @return detached resolution
	 */
	private static ChannelLink resolve(List<ChannelInfo> channels, SourceKind kind, String sourceId,
			String label, String channelId, String fallback) {
		String id = safe(channelId);
		String legacy = safe(fallback);
		ChannelInfo target = null;
		int count = 0;
		if (!id.isBlank()) {
			for (ChannelInfo channel : channels) {
				if (id.equals(channel.id())) {
					target = channel;
					count++;
				}
			}
		}
		LinkStatus status = id.isBlank() ? LinkStatus.UNLINKED
				: count == 0 ? LinkStatus.MISSING : count == 1 ? LinkStatus.LINKED : LinkStatus.AMBIGUOUS;
		if (status != LinkStatus.LINKED) {
			target = null;
		}
		return new ChannelLink(kind, safe(sourceId), safe(label), id, legacy,
				target == null ? legacy : target.label(), status, target);
	}

	/**
	 * Adds a populated reference without changing either model or source lists.
	 * @param result output snapshots
	 * @param channels candidate targets
	 * @param kind origin type
	 * @param sourceId model identity/location
	 * @param label presentation-only description
	 * @param channelId explicit identity
	 * @param fallback legacy text
	 */
	private static void add(List<ChannelLink> result, List<ChannelInfo> channels, SourceKind kind,
			String sourceId, String label, String channelId, String fallback) {
		if (!safe(channelId).isBlank() || !safe(fallback).isBlank()) {
			result.add(resolve(channels, kind, sourceId, label, channelId, fallback));
		}
	}

	/**
	 * Projects assignment-form references.
	 * @param result output snapshots
	 * @param channels candidate targets
	 * @param form source form, possibly null
	 * @param location structural fallback when the form lacks an identity
	 */
	private static void addForm(List<ChannelLink> result, List<ChannelInfo> channels,
			Ics204Form form, String location) {
		if (form == null) {
			return;
		}
		String id = identity(form.getFormId(), location);
		addCommunications(result, channels, SourceKind.ICS204_COMMUNICATION, id, form.getCommunications());
		int index = 0;
		for (ResourceAssignment resource : rows(form.getResourcesAssigned())) {
			if (resource != null) {
				add(result, channels, SourceKind.ICS204_RESOURCE,
						identity(resource.getAssignmentId(), id + "/resource:" + index),
						resource.getResourceIdentifier(), "", resource.getContact());
			}
			index++;
		}
		add(result, channels, SourceKind.ICS204_MANAGEMENT, id + "/operations",
				form.getOperationsSectionChiefName(), "", form.getOperationsSectionChiefContact());
		add(result, channels, SourceKind.ICS204_MANAGEMENT, id + "/branch",
				form.getBranchDirectorName(), "", form.getBranchDirectorContact());
		add(result, channels, SourceKind.ICS204_MANAGEMENT, id + "/supervisor",
				form.getDivisionGroupSupervisorName(), "", form.getDivisionGroupSupervisorContact());
	}

	/**
	 * Projects communication rows, whose identities are parent ID plus row location.
	 * @param result output snapshots
	 * @param channels candidate targets
	 * @param kind typed parent context
	 * @param parentId stable parent identity or structural location
	 * @param entries communication rows, possibly null
	 */
	private static void addCommunications(List<ChannelLink> result, List<ChannelInfo> channels,
			SourceKind kind, String parentId, List<CommunicationEntry> entries) {
		int index = 0;
		for (CommunicationEntry entry : rows(entries)) {
			if (entry != null) {
				add(result, channels, kind, parentId + "/communication:" + index,
						entry.getNameOrFunction(), entry.getRadioChannelId(), entry.getPrimaryContact());
			}
			index++;
		}
	}

	/**
	 * Projects SAR communication and resource references without name matching.
	 * @param result output snapshots
	 * @param channels candidate targets
	 * @param data current document
	 * @param task source task
	 * @param location structural fallback for legacy tasks lacking IDs
	 */
	private static void addTask(List<ChannelLink> result, List<ChannelInfo> channels, AppData data,
			SarTaskAssignment task, String location) {
		String id = identity(task.peekTaskId(), location);
		addCommunications(result, channels, SourceKind.SAR_COMMUNICATION, id, task.getCommunications());
		add(result, channels, SourceKind.SAR_ASSIGNMENT, id, task.getResourceIdentifier(), "", task.getContact());
		add(result, channels, SourceKind.SAR_MANAGEMENT, id + "/operations",
				task.getOperationsSectionChiefName(), "", task.getOperationsSectionChiefContact());
		add(result, channels, SourceKind.SAR_MANAGEMENT, id + "/secondary",
				task.getSecondaryManagementName(), "", task.getSecondaryManagementContact());
		int index = 0;
		for (SarTaskResource resource : rows(task.getResourcesAssigned())) {
			if (resource != null) {
				TCard card = uniqueCard(data, resource.getResourceId());
				if (card != null) {
					add(result, channels, SourceKind.SAR_RESOURCE,
							id + "/resource:" + identity(resource.getResourceId(), Integer.toString(index)),
							resource.getName(), card.getRadioChannelId(), card.getRadioChannel());
				}
			}
			index++;
		}
	}

	/**
	 * Finds a T-card only by unique, nonblank persisted resource identity.
	 * @param data current document
	 * @param resourceId explicit resource identity
	 * @return unique card, or null for missing, blank, or duplicate identities
	 */
	private static TCard uniqueCard(AppData data, String resourceId) {
		if (safe(resourceId).isBlank()) {
			return null;
		}
		TCard found = null;
		for (TCard card : rows(data.getTCards())) {
			if (card != null && Objects.equals(resourceId, card.peekResourceId())) {
				if (found != null) {
					return null;
				}
				found = card;
			}
		}
		return found;
	}

	/**
	 * Projects legacy medical frequencies and the already-existing shared leader.
	 * @param result output snapshots
	 * @param channels candidate targets
	 * @param data current document
	 */
	private static void addMedical(List<ChannelLink> result, List<ChannelInfo> channels, AppData data) {
		MedicalUnitLeaderAssignment leader = data.peekMedicalUnitLeaderAssignment();
		if (leader != null) {
			add(result, channels, SourceKind.MEDICAL_UNIT_LEADER, "medical-unit-leader",
					leader.getName(), "", leader.getRadioChannel());
		} else if (data.getOrganizationalChart() != null) {
			add(result, channels, SourceKind.MEDICAL_UNIT_LEADER, "medical-unit-leader",
					data.getOrganizationalChart().getMedicalUnitLeaderName(), "",
					data.getOrganizationalChart().getMedicalUnitLeaderRadio());
		}
		MedicalPlan plan = data.peekMedicalPlan();
		if (plan != null) {
			add(result, channels, SourceKind.MEDICAL_CONTACT, "medical/contact",
					plan.getDesignatedMedicalUnitName(), "", plan.getDesignatedMedicalUnitContact());
			int index = 0;
			for (MedicalPlan.AidStation station : rows(plan.getAidStations())) {
				if (station != null) {
					add(result, channels, SourceKind.MEDICAL_AID_STATION, "medical/aid:" + index,
							station.getName(), "", station.getFrequency());
				}
				index++;
			}
			addTransports(result, channels, SourceKind.MEDICAL_TRANSPORT, plan.getTransportServices());
			index = 0;
			for (MedicalPlan.Hospital hospital : rows(plan.getHospitals())) {
				if (hospital != null) {
					add(result, channels, SourceKind.MEDICAL_HOSPITAL, "medical/hospital:" + index,
							hospital.getName(), "", hospital.getFrequency());
				}
				index++;
			}
		}
		if (data.peekCanineMedicalPlan() != null) {
			addTransports(result, channels, SourceKind.CANINE_MEDICAL_TRANSPORT,
					data.peekCanineMedicalPlan().getTransportServiceEntries());
		}
	}

	/**
	 * Projects human or canine transport frequencies without numeric conversion.
	 * @param result output snapshots
	 * @param channels candidate targets
	 * @param kind transport origin
	 * @param services transport rows, possibly null
	 */
	private static void addTransports(List<ChannelLink> result, List<ChannelInfo> channels,
			SourceKind kind, List<MedicalPlan.TransportService> services) {
		int index = 0;
		for (MedicalPlan.TransportService service : rows(services)) {
			if (service != null) {
				add(result, channels, kind, kind.name() + ":" + index,
						service.getService(), "", service.getFrequency());
			}
			index++;
		}
	}

	/**
	 * Centralizes named organizational radio fields and safety-officer references.
	 * @param result output snapshots
	 * @param channels candidate targets
	 * @param chart organization, possibly null
	 */
	private static void addOrganization(List<ChannelLink> result, List<ChannelInfo> channels,
			OrganizationalChart chart) {
		if (chart == null) {
			return;
		}
		add(result, channels, SourceKind.SAFETY_OFFICER, "safety-officer",
				chart.getSafetyOfficerName(), "", chart.getSafetyOfficerRadio());
		addRole(result, channels, "incident-commander", "Incident Commander", chart.getIncidentCommanderRadio());
		addRole(result, channels, "public-information", "Public Information Officer", chart.getPublicInformationOfficerRadio());
		addRole(result, channels, "liaison", "Liaison Officer", chart.getLiaisonOfficerRadio());
		addRole(result, channels, "operations", "Operations Section Chief", chart.getOperationsSectionChiefRadio());
		addRole(result, channels, "planning", "Planning Section Chief", chart.getPlanningSectionChiefRadio());
		addRole(result, channels, "logistics", "Logistics Section Chief", chart.getLogisticsSectionChiefRadio());
		addRole(result, channels, "finance", "Finance/Admin Section Chief", chart.getFinanceAdminSectionChiefRadio());
		addRole(result, channels, "documentation", "Documentation Unit Leader", chart.getDocumentationUnitLeaderRadio());
		addRole(result, channels, "resources", "Resources Unit Leader", chart.getResourcesUnitLeaderRadio());
		addRole(result, channels, "situation", "Situation Unit Leader", chart.getSituationUnitLeaderRadio());
		addRole(result, channels, "demobilization", "Demobilization Unit Leader", chart.getDemobilizationUnitLeaderRadio());
		addRole(result, channels, "staging", "Staging Area Manager", chart.getStagingAreaManagerRadio());
		addRole(result, channels, "communications", "Communications Unit Leader", chart.getCommunicationsUnitLeaderRadio());
		addRole(result, channels, "communications-tech", "Communications Technician", chart.getCommunicationsTechnicianRadio());
		addRole(result, channels, "supply", "Supply Unit Leader", chart.getSupplyUnitLeaderRadio());
		addRole(result, channels, "facilities", "Facilities Unit Leader", chart.getFacilitiesUnitLeaderRadio());
		addRole(result, channels, "ground-support", "Ground Support Unit Leader", chart.getGroundSupportUnitLeaderRadio());
		addRole(result, channels, "food", "Food Unit Leader", chart.getFoodUnitLeaderRadio());
		addRole(result, channels, "medical", "Medical Unit Leader", chart.getMedicalUnitLeaderRadio());
		addRole(result, channels, "time", "Time Unit Leader", chart.getTimeUnitLeaderRadio());
		addRole(result, channels, "procurement", "Procurement Unit Leader", chart.getProcurementUnitLeaderRadio());
		addRole(result, channels, "compensation", "Compensation/Claims Unit Leader", chart.getCompClaimsUnitLeaderRadio());
		addRole(result, channels, "cost", "Cost Unit Leader", chart.getCostUnitLeaderRadio());
		addEntries(result, channels, SourceKind.ORG_INCIDENT_COMMANDER, "organization/commander", chart.getIncidentCommanderEntries());
		addEntries(result, channels, SourceKind.ORG_ADDITIONAL, "organization/additional", chart.getAdditionalPositions());
		Ics203Form assignmentList = chart.getAssignmentList();
		if (assignmentList != null) {
			addEntries(result, channels, SourceKind.ORG_AGENCY_REPRESENTATIVE, "organization/agency",
					assignmentList.getAgencyRepresentatives());
			int index = 0;
			for (Ics203Form.Branch branch : rows(assignmentList.getBranches())) {
				if (branch != null) {
					addEntries(result, channels, SourceKind.ORG_DIVISION, "organization/branch:" + index,
							branch.getDivisions());
				}
				index++;
			}
		}
	}

	/**
	 * Adds a legacy organizational role with a fixed structural identity.
	 * @param result output snapshots
	 * @param channels candidate targets
	 * @param role fixed structural role identity
	 * @param label presentation-only role title
	 * @param radio original free text
	 */
	private static void addRole(List<ChannelLink> result, List<ChannelInfo> channels,
			String role, String label, String radio) {
		add(result, channels, SourceKind.ORG_ROLE, "organization/" + role, label, "", radio);
	}

	/**
	 * Projects organization rows that do not have persistent row identities.
	 * @param result output snapshots
	 * @param channels candidate targets
	 * @param kind typed organization origin
	 * @param parentId structural parent identity
	 * @param entries source rows, possibly null
	 */
	private static void addEntries(List<ChannelLink> result, List<ChannelInfo> channels,
			SourceKind kind, String parentId, List<OrgChartEntry> entries) {
		int index = 0;
		for (OrgChartEntry entry : rows(entries)) {
			if (entry != null) {
				add(result, channels, kind, parentId + "/entry:" + index,
						entry.getTitle(), "", entry.getRadio());
			}
			index++;
		}
	}

	/**
	 * Returns existing identity without generating one for legacy records.
	 * @param id persisted identity, possibly null
	 * @param location structural fallback
	 * @return existing identity or fallback
	 */
	private static String identity(String id, String location) {
		return safe(id).isBlank() ? location : id;
	}

	/**
	 * Supplies an empty view for a null source list without filtering row positions.
	 * @param values source list
	 * @param <T> row type
	 * @return original list for iteration only, or immutable empty list
	 */
	private static <T> List<T> rows(List<T> values) {
		return values == null ? List.of() : values;
	}

	/**
	 * Normalizes null text without trimming legacy content.
	 * @param value original text
	 * @return original text or empty string
	 */
	private static String safe(String value) {
		return value == null ? "" : value;
	}
}
