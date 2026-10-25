/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.comms;

import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.comms.ChannelLinkSource.ChannelInfo;
import org.sarmanagement.icsforms.comms.ChannelLinkSource.ChannelLink;
import org.sarmanagement.icsforms.comms.ChannelLinkSource.LinkStatus;
import org.sarmanagement.icsforms.comms.ChannelLinkSource.SourceKind;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.CommunicationEntry;
import org.sarmanagement.icsforms.model.Ics204Form;
import org.sarmanagement.icsforms.model.Ics203Form;
import org.sarmanagement.icsforms.model.MedicalPlan;
import org.sarmanagement.icsforms.model.MedicalUnitLeaderAssignment;
import org.sarmanagement.icsforms.model.OrgChartEntry;
import org.sarmanagement.icsforms.model.RadioChannel;
import org.sarmanagement.icsforms.model.ResourceAssignment;
import org.sarmanagement.icsforms.model.SarTaskAssignment;
import org.sarmanagement.icsforms.model.SarTaskResource;
import org.sarmanagement.icsforms.model.TCard;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises immutable, identity-only channel projections without Swing dependencies. */
class ChannelLinkSourceTest {
	@Test
	void nullDocumentAndNullTextAreSafe() {
		assertEquals(List.of(), ChannelLinkSource.channels(null));
		assertEquals(List.of(), ChannelLinkSource.links(null));
		assertEquals(List.of(), ChannelLinkSource.legacyChannelNames(null));
		ChannelLink unlinked = ChannelLinkSource.resolve(null, null, null);
		assertEquals(LinkStatus.UNLINKED, unlinked.status());
		assertEquals("", unlinked.channelId());
		assertEquals("", unlinked.displayLabel());
		assertNull(unlinked.channel());
		ChannelLink missing = ChannelLinkSource.resolve(null, "unknown", "  legacy  ");
		assertEquals(LinkStatus.MISSING, missing.status());
		assertEquals("  legacy  ", missing.displayLabel());
		assertEquals(LinkStatus.UNLINKED, ChannelLinkSource.resolve(null, " \t", "text").status());
	}

	@Test
	void exactUniqueIdWinsOverDuplicateNamesAndFrequencies() {
		AppData data = data(channel("one", "Command"), channel("two", "Command"));
		data.getForm205().getChannels().forEach(channel -> channel.setRxFrequency("155.5000"));
		ChannelLink selected = ChannelLinkSource.resolve(data, "two", "Old label");
		assertEquals(LinkStatus.LINKED, selected.status());
		assertEquals("two", selected.channel().id());
		assertEquals("Command", selected.displayLabel());
		assertEquals("Old label", selected.legacyFallback());
		for (String fallback : List.of("Command", "155.5000")) {
			assertEquals(LinkStatus.UNLINKED, ChannelLinkSource.resolve(data, "", fallback).status());
			assertEquals(fallback, ChannelLinkSource.resolve(data, "", fallback).displayLabel());
		}
		assertEquals(LinkStatus.MISSING, ChannelLinkSource.resolve(data, "Command", "legacy").status());
		assertEquals(LinkStatus.MISSING, ChannelLinkSource.resolve(data, " two ", "legacy").status());
	}

	@Test
	void duplicateIdNeverChoosesFirstTargetAndDeletedTargetKeepsFallback() {
		AppData data = data(channel("duplicate", "First"), channel("duplicate", "Second"));
		assertEquals(2, ChannelLinkSource.channels(data).size());
		ChannelLink ambiguous = ChannelLinkSource.resolve(data, "duplicate", "Keep me");
		assertEquals(LinkStatus.AMBIGUOUS, ambiguous.status());
		assertEquals("Keep me", ambiguous.displayLabel());
		assertNull(ambiguous.channel());
		data.getForm205().getChannels().clear();
		ChannelLink dangling = ChannelLinkSource.resolve(data, "duplicate", "Keep me");
		assertEquals(LinkStatus.MISSING, dangling.status());
		assertEquals("Keep me", dangling.displayLabel());
		assertNull(dangling.channel());
	}

	@Test
	void channelLabelsFallBackToNumberThenIdentityAndIncludeNonprintedChannels() {
		RadioChannel number = channel("number-id", "");
		number.setChannelNumber("007");
		number.setIncludeOnForm(false);
		RadioChannel identity = channel("identity-id", " \t");
		AppData data = data(number, identity);
		assertEquals(List.of(new ChannelInfo("number-id", "007"), new ChannelInfo("identity-id", "identity-id")),
				ChannelLinkSource.channels(data));
		assertEquals("007", ChannelLinkSource.resolve(data, "number-id", "old").displayLabel());
		assertEquals(new ChannelInfo("", ""), new ChannelInfo(null, null));
	}

	@Test
	void centralChannelLabelHandlesNullAndPreservesNameNumberIdentityPrecedence() {
		assertEquals("", ChannelLinkSource.channelLabel(null));
		RadioChannel channel = channel("stable-id", " Name ");
		channel.setChannelNumber("007");
		assertEquals(" Name ", ChannelLinkSource.channelLabel(channel));
		channel.setChannelName(" \t");
		assertEquals("007", ChannelLinkSource.channelLabel(channel));
		channel.setChannelNumber(null);
		assertEquals("stable-id", ChannelLinkSource.channelLabel(channel));
		assertEquals("stable-id", channel.getChannelId());
	}

	@Test
	void snapshotsCannotExposeMutableListsChannelsOrMetadata() {
		RadioChannel channel = channel("stable", "Original");
		channel.setProgrammingMetadata(Map.of("raw", "protected"));
		AppData data = data(channel);
		CommunicationEntry entry = communication("stable", "fallback");
		data.getForm204().getCommunications().add(entry);
		List<ChannelInfo> channels = ChannelLinkSource.channels(data);
		List<ChannelLink> links = ChannelLinkSource.links(data);
		List<String> names = ChannelLinkSource.legacyChannelNames(data);
		assertThrows(UnsupportedOperationException.class, () -> channels.clear());
		assertThrows(UnsupportedOperationException.class, () -> links.clear());
		assertThrows(UnsupportedOperationException.class, () -> names.clear());
		channel.setChannelName("Changed");
		channel.setChannelId("new-id");
		entry.setPrimaryContact("changed fallback");
		channel.getProgrammingMetadata().put("raw", "changed");
		data.getForm205().getChannels().clear();
		assertEquals("Original", channels.get(0).label());
		assertEquals("stable", channels.get(0).id());
		assertEquals("Original", links.get(0).displayLabel());
		assertEquals("fallback", links.get(0).legacyFallback());
		assertEquals(List.of("Original", "fallback"), names);
	}

	@Test
	void nonIcs205ConsumerReadsLinked204AndTcardWithoutMappingStrings() {
		AppData data = data(channel("stable", "Renamed command"));
		data.getForm204().setFormId("form-id");
		data.getForm204().getCommunications().add(communication("stable", "Old command"));
		TCard card = card("resource-id", "stable", "Old card channel");
		data.getTCards().add(card);
		assertEquals(List.of("Renamed command", "Renamed command"), assignmentDisplay(data));
		List<ChannelLink> links = ChannelLinkSource.links(data);
		ChannelLink assignment = first(links, SourceKind.ICS204_COMMUNICATION);
		assertEquals("form-id/communication:0", assignment.sourceId());
		assertEquals("resource-id", first(links, SourceKind.TCARD).sourceId());
		assertEquals("Old command", data.getForm204().getCommunications().get(0).getPrimaryContact());
		assertEquals("Old card channel", card.getRadioChannel());
		assertEquals("stable", card.getRadioChannelId());
	}

	@Test
	void projectsAdditional204ResourcesTaskContactsAndTaskResourcesByExplicitIdentity() {
		AppData data = data(channel("stable", "Current"));
		Ics204Form additional = new Ics204Form();
		additional.setFormId("additional-form");
		additional.getCommunications().add(communication("", "legacy 204"));
		ResourceAssignment assignment = new ResourceAssignment();
		assignment.setAssignmentId("assignment-id");
		assignment.setContact("legacy assignment");
		additional.getResourcesAssigned().add(assignment);
		additional.setOperationsSectionChiefContact("ops 204");
		data.getAdditionalForms204().add(additional);
		TCard card = card("resource-id", "stable", "legacy card");
		data.getTCards().add(card);
		SarTaskAssignment task = new SarTaskAssignment();
		task.setTaskId("task-id");
		task.setContact("legacy task");
		task.setOperationsSectionChiefContact("ops task");
		task.setSecondaryManagementContact("secondary task");
		task.getCommunications().add(communication("stable", "legacy task channel"));
		SarTaskResource resource = new SarTaskResource();
		resource.setResourceId("resource-id");
		resource.setName("Presentation only");
		task.getResourcesAssigned().add(resource);
		data.getSarTaskAssignments().add(task);
		List<ChannelLink> links = ChannelLinkSource.links(data);
		assertEquals("additional-form/communication:0", first(links, SourceKind.ICS204_COMMUNICATION).sourceId());
		assertEquals("assignment-id", first(links, SourceKind.ICS204_RESOURCE).sourceId());
		assertEquals("legacy assignment", first(links, SourceKind.ICS204_RESOURCE).displayLabel());
		assertEquals("legacy task", first(links, SourceKind.SAR_ASSIGNMENT).displayLabel());
		assertEquals("task-id/communication:0", first(links, SourceKind.SAR_COMMUNICATION).sourceId());
		assertEquals("Current", first(links, SourceKind.SAR_COMMUNICATION).displayLabel());
		ChannelLink taskResource = first(links, SourceKind.SAR_RESOURCE);
		assertEquals("task-id/resource:resource-id", taskResource.sourceId());
		assertEquals("stable", taskResource.channel().id());
		assertEquals("legacy card", taskResource.legacyFallback());
		assertEquals(2, links.stream().filter(link -> link.sourceKind() == SourceKind.SAR_MANAGEMENT).count());
	}

	@Test
	void taskResourceNeverJoinsByNameOrAmbiguousResourceId() {
		AppData data = data(channel("stable", "Channel"));
		TCard first = card("resource-id", "stable", "legacy card");
		first.setPersonName("Same name");
		data.getTCards().add(first);
		SarTaskAssignment task = new SarTaskAssignment();
		SarTaskResource resource = new SarTaskResource();
		resource.setName("Same name");
		task.getResourcesAssigned().add(resource);
		data.getSarTaskAssignments().add(task);
		assertFalse(ChannelLinkSource.links(data).stream().anyMatch(link -> link.sourceKind() == SourceKind.SAR_RESOURCE));
		resource.setResourceId("resource-id");
		assertEquals("Channel", first(ChannelLinkSource.links(data), SourceKind.SAR_RESOURCE).displayLabel());
		data.getTCards().add(card("resource-id", "stable", "different legacy"));
		assertFalse(ChannelLinkSource.links(data).stream().anyMatch(link -> link.sourceKind() == SourceKind.SAR_RESOURCE));
	}

	@Test
	void medicalSafetyAndOrganizationKeepLegacyTextEvenWhenChannelNameMatches() {
		AppData data = data(channel("stable", "155.5000"));
		MedicalPlan plan = data.getMedicalPlan();
		plan.setDesignatedMedicalUnitContact("medical contact");
		MedicalPlan.AidStation station = new MedicalPlan.AidStation();
		station.setFrequency("155.5000");
		plan.getAidStations().add(station);
		MedicalPlan.TransportService transport = new MedicalPlan.TransportService();
		transport.setFrequency("155.500000");
		plan.getTransportServices().add(transport);
		MedicalPlan.Hospital hospital = new MedicalPlan.Hospital();
		hospital.setFrequency("hospital raw");
		plan.getHospitals().add(hospital);
		data.getCanineMedicalPlan().getTransportServiceEntries().add(transport);
		MedicalUnitLeaderAssignment leader = new MedicalUnitLeaderAssignment();
		leader.setRadioChannel("leader legacy");
		data.setMedicalUnitLeaderAssignment(leader);
		data.getOrganizationalChart().setSafetyOfficerRadio("155.5000");
		data.getOrganizationalChart().setOperationsSectionChiefRadio("ops legacy");
		OrgChartEntry extra = new OrgChartEntry();
		extra.setRadio("extra legacy");
		data.getOrganizationalChart().getAdditionalPositions().add(extra);
		data.getOrganizationalChart().getIncidentCommanderEntries().add(extra);
		data.getOrganizationalChart().getAssignmentList().getAgencyRepresentatives().add(extra);
		Ics203Form.Branch branch = new Ics203Form.Branch();
		branch.getDivisions().add(extra);
		data.getOrganizationalChart().getAssignmentList().getBranches().add(branch);
		List<ChannelLink> links = ChannelLinkSource.links(data);
		assertEquals("medical contact", first(links, SourceKind.MEDICAL_CONTACT).displayLabel());
		assertEquals("155.5000", first(links, SourceKind.MEDICAL_AID_STATION).displayLabel());
		assertEquals("155.500000", first(links, SourceKind.MEDICAL_TRANSPORT).displayLabel());
		assertEquals("hospital raw", first(links, SourceKind.MEDICAL_HOSPITAL).displayLabel());
		assertEquals("155.500000", first(links, SourceKind.CANINE_MEDICAL_TRANSPORT).displayLabel());
		assertEquals("leader legacy", first(links, SourceKind.MEDICAL_UNIT_LEADER).displayLabel());
		assertEquals("155.5000", first(links, SourceKind.SAFETY_OFFICER).displayLabel());
		assertEquals("extra legacy", first(links, SourceKind.ORG_ADDITIONAL).displayLabel());
		assertEquals("extra legacy", first(links, SourceKind.ORG_INCIDENT_COMMANDER).displayLabel());
		assertEquals("extra legacy", first(links, SourceKind.ORG_AGENCY_REPRESENTATIVE).displayLabel());
		assertEquals("organization/branch:0/entry:0", first(links, SourceKind.ORG_DIVISION).sourceId());
		assertTrue(links.stream().allMatch(link -> link.status() == LinkStatus.UNLINKED));
		assertTrue(links.stream().anyMatch(link -> link.sourceKind() == SourceKind.ORG_ROLE
				&& link.displayLabel().equals("ops legacy")));
	}

	@Test
	void readsDoNotInitializeLegacyIdsReconcileLeaderOrRepairMissingPlans() throws Exception {
		AppData data = data(channel("stable", "Channel"));
		TCard card = new TCard();
		card.setRadioChannel("legacy");
		data.getTCards().add(card);
		SarTaskAssignment task = new SarTaskAssignment();
		task.setTaskId(null);
		task.setContact("legacy task");
		data.getSarTaskAssignments().add(task);
		data.getOrganizationalChart().setMedicalUnitLeaderName("legacy leader");
		data.getOrganizationalChart().setMedicalUnitLeaderRadio("legacy leader radio");
		setField(data, "medicalPlan", null);
		setField(data, "canineMedicalPlan", null);
		assertNull(data.peekMedicalUnitLeaderAssignment());
		assertNull(card.peekResourceId());
		assertNull(task.peekTaskId());
		long revision = data.getRevision();
		List<ChannelLink> first = ChannelLinkSource.links(data);
		assertEquals(first, ChannelLinkSource.links(data));
		ChannelLinkSource.legacyChannelNames(data);
		ChannelLinkSource.resolve(data, "stable", "legacy");
		assertNull(data.peekMedicalUnitLeaderAssignment());
		assertNull(data.peekMedicalPlan());
		assertNull(data.peekCanineMedicalPlan());
		assertNull(card.peekResourceId());
		assertNull(task.peekTaskId());
		assertEquals(revision, data.getRevision());
		assertEquals("tcard:0", first(first, SourceKind.TCARD).sourceId());
		assertEquals("task:0", first(first, SourceKind.SAR_ASSIGNMENT).sourceId());
		assertEquals("legacy leader radio", first(first, SourceKind.MEDICAL_UNIT_LEADER).displayLabel());
	}

	@Test
	void nullRowsAndAbsentPlanAreSkippedWithoutRepair() throws Exception {
		AppData data = data(channel("stable", "Channel"));
		data.getForm205().getChannels().add(null);
		data.getAdditionalForms204().add(null);
		data.getForm204().getCommunications().add(null);
		data.getForm204().getResourcesAssigned().add(null);
		data.getTCards().add(null);
		data.getSarTaskAssignments().add(null);
		data.getMedicalPlan().getAidStations().add(null);
		data.getMedicalPlan().getTransportServices().add(null);
		data.getMedicalPlan().getHospitals().add(null);
		data.getCanineMedicalPlan().getTransportServiceEntries().add(null);
		data.getOrganizationalChart().getIncidentCommanderEntries().add(null);
		data.getOrganizationalChart().getAdditionalPositions().add(null);
		assertEquals(1, ChannelLinkSource.channels(data).size());
		assertEquals(List.of(), ChannelLinkSource.links(data));
		setField(data, "form205", null);
		assertEquals(List.of(), ChannelLinkSource.channels(data));
		assertEquals(LinkStatus.MISSING, ChannelLinkSource.resolve(data, "stable", "fallback").status());
		assertNull(data.getForm205());
		for (String field : List.of("form204", "additionalForms204", "sarTaskAssignments", "tCards",
				"organizationalChart", "medicalPlan", "canineMedicalPlan")) {
			setField(data, field, null);
		}
		assertEquals(List.of(), ChannelLinkSource.links(data));
		assertEquals(List.of(), ChannelLinkSource.legacyChannelNames(data));
	}

	@Test
	void planAssignmentsAndCarriedIdsRemainResolvableWithoutArchivedTargets() {
		RadioChannel channel = channel("stable", "Command");
		channel.setAssignment("Search teams");
		AppData data = data(channel);
		TCard card = card("resource-id", "stable", "Old command");
		data.getTCards().add(card);
		ChannelLink assignment = first(ChannelLinkSource.links(data), SourceKind.ICS205_ASSIGNMENT);
		assertEquals("stable", assignment.sourceId());
		assertEquals("Search teams", assignment.sourceLabel());
		assertEquals(LinkStatus.LINKED, assignment.status());
		data.advanceToNewOperationalPeriod(true);
		assertEquals("stable", ChannelLinkSource.resolve(data, card.getRadioChannelId(), card.getRadioChannel()).channel().id());
		assertEquals("Command", first(ChannelLinkSource.links(data), SourceKind.ICS205_ASSIGNMENT).displayLabel());
		data.getForm205().getChannels().clear();
		assertEquals(LinkStatus.MISSING, ChannelLinkSource.resolve(data, "stable", "Old command").status());
	}

	@Test
	void legacySuggestionsAreDistinctOrderedAndDoNotCreateLinks() {
		AppData data = data(channel("one", "Same"), channel("two", "Same"));
		data.getForm204().getCommunications().add(communication("", " Original legacy "));
		data.getForm204().getCommunications().add(communication("", "Same"));
		assertEquals(List.of("Same", " Original legacy "), ChannelLinkSource.legacyChannelNames(data));
		assertTrue(ChannelLinkSource.links(data).stream().allMatch(link -> link.status() == LinkStatus.UNLINKED));
	}

	/**
	 * Example non-ICS-205 consumer using typed origins and resolved presentation.
	 * @param data current incident
	 * @return displayed assignment and T-card channels, with no local mapping
	 */
	private static List<String> assignmentDisplay(AppData data) {
		return ChannelLinkSource.links(data).stream()
				.filter(link -> link.sourceKind() == SourceKind.ICS204_COMMUNICATION || link.sourceKind() == SourceKind.TCARD)
				.map(ChannelLink::displayLabel).toList();
	}

	/**
	 * Finds the first projection with a typed origin.
	 * @param links detached projections
	 * @param kind required origin
	 * @return matching projection
	 */
	private static ChannelLink first(List<ChannelLink> links, SourceKind kind) {
		return links.stream().filter(link -> link.sourceKind() == kind).findFirst().orElseThrow();
	}

	/**
	 * Creates an incident with a mutable inventory for deletion/ambiguity tests.
	 * @param channels initial inventory
	 * @return incident document
	 */
	private static AppData data(RadioChannel... channels) {
		AppData data = new AppData();
		data.getForm205().getChannels().addAll(Arrays.asList(channels));
		return data;
	}

	/**
	 * Creates a channel with explicit identity and label.
	 * @param id stable identity
	 * @param name presentation label
	 * @return test channel
	 */
	private static RadioChannel channel(String id, String name) {
		RadioChannel channel = new RadioChannel();
		channel.setChannelId(id);
		channel.setChannelName(name);
		return channel;
	}

	/**
	 * Creates a persisted communication reference.
	 * @param id explicit channel identity
	 * @param fallback original free text
	 * @return communication row
	 */
	private static CommunicationEntry communication(String id, String fallback) {
		CommunicationEntry entry = new CommunicationEntry();
		entry.setPrimaryContact(fallback);
		entry.setRadioChannelId(id);
		return entry;
	}

	/**
	 * Creates a T-card with explicit resource and channel identities.
	 * @param resourceId resource identity
	 * @param channelId channel identity
	 * @param fallback original radio text
	 * @return test T-card
	 */
	private static TCard card(String resourceId, String channelId, String fallback) {
		TCard card = new TCard();
		card.setResourceId(resourceId);
		card.setRadioChannel(fallback);
		card.setRadioChannelId(channelId);
		return card;
	}

	/**
	 * Models pre-migration null fields without invoking repairing setters.
	 * @param data incident document
	 * @param name declared field name
	 * @param value raw persisted value
	 * @throws ReflectiveOperationException if the model no longer declares the field
	 */
	private static void setField(AppData data, String name, Object value) throws ReflectiveOperationException {
		Field field = AppData.class.getDeclaredField(name);
		field.setAccessible(true);
		field.set(data, value);
	}
}
