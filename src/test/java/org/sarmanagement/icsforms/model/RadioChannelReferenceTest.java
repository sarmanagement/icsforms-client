/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** Covers durable radio references alongside backward-compatible contact labels. */
class RadioChannelReferenceTest {
	private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

	@Test
	void legacyAndNullReferencesDefaultToEmptyWithoutLosingFallbacks() throws Exception {
		assertEquals("", new TCard().getRadioChannelId());
		assertEquals("", new CommunicationEntry().getRadioChannelId());
		for (String json : new String[] {
				"{\"radioChannel\":\"Command\"}",
				"{\"radioChannelId\":null,\"radioChannel\":\"Command\"}"}) {
			TCard card = mapper.readValue(json, TCard.class);
			assertEquals("Command", card.getRadioChannel());
			assertEquals("", card.getRadioChannelId());
		}
		for (String json : new String[] {
				"{\"primaryContact\":\"Command\"}",
				"{\"radioChannelId\":null,\"primaryContact\":\"Command\"}"}) {
			CommunicationEntry entry = mapper.readValue(json, CommunicationEntry.class);
			assertEquals("Command", entry.getPrimaryContact());
			assertEquals("", entry.getRadioChannelId());
		}
	}

	@Test
	void jsonRoundTripRetainsIdsRegardlessOfInputPropertyOrder() throws Exception {
		for (String json : new String[] {
				"{\"radioChannelId\":\"stable\",\"radioChannel\":\"Command\"}",
				"{\"radioChannel\":\"Command\",\"radioChannelId\":\"stable\"}"}) {
			TCard card = mapper.readValue(json, TCard.class);
			TCard restored = mapper.readValue(mapper.writeValueAsBytes(card), TCard.class);
			assertEquals("stable", restored.getRadioChannelId());
			assertEquals("Command", restored.getRadioChannel());
		}
		for (String json : new String[] {
				"{\"radioChannelId\":\"stable\",\"primaryContact\":\"Command\"}",
				"{\"primaryContact\":\"Command\",\"radioChannelId\":\"stable\"}"}) {
			CommunicationEntry entry = mapper.readValue(json, CommunicationEntry.class);
			CommunicationEntry restored = mapper.readValue(mapper.writeValueAsBytes(entry), CommunicationEntry.class);
			assertEquals("stable", restored.getRadioChannelId());
			assertEquals("Command", restored.getPrimaryContact());
		}
	}

	@Test
	void editingTCardTextClearsReferenceOnlyWhenExactLabelChanges() {
		TCard card = new TCard();
		card.setRadioChannel("Command");
		card.setRadioChannelId("stable");
		card.setRadioChannel("Command");
		assertEquals("stable", card.getRadioChannelId());
		card.setRadioChannel(" Command ");
		assertEquals("", card.getRadioChannelId());
		assertEquals(" Command ", card.getRadioChannel());
		card.setRadioChannelId("second");
		card.setRadioChannel(null);
		assertEquals("", card.getRadioChannel());
		assertEquals("", card.getRadioChannelId());
		card.setRadioChannelId("empty-label");
		card.setRadioChannel(null);
		assertEquals("empty-label", card.getRadioChannelId());
		card.setRadioChannelId(null);
		assertEquals("", card.getRadioChannelId());
	}

	@Test
	void editingCommunicationsTextClearsReferenceOnlyWhenExactLabelChanges() {
		CommunicationEntry entry = new CommunicationEntry();
		entry.setPrimaryContact("Command");
		entry.setRadioChannelId("stable");
		entry.setPrimaryContact("Command");
		assertEquals("stable", entry.getRadioChannelId());
		entry.setName("Changed person");
		entry.setFunction("Changed role");
		assertEquals("stable", entry.getRadioChannelId());
		entry.setPrimaryContact("Command ");
		assertEquals("", entry.getRadioChannelId());
		assertEquals("Command ", entry.getPrimaryContact());
		entry.setRadioChannelId("second");
		entry.setPrimaryContact(null);
		assertEquals("", entry.getPrimaryContact());
		assertEquals("", entry.getRadioChannelId());
		entry.setRadioChannelId("empty-label");
		entry.setPrimaryContact(null);
		assertEquals("empty-label", entry.getRadioChannelId());
		entry.setRadioChannelId(null);
		assertEquals("", entry.getRadioChannelId());
	}

	@Test
	void sarScaffoldCopiesStableReferenceAndFallbackWithoutMutableSharing() {
		Ics204Form form = new Ics204Form();
		CommunicationEntry entry = new CommunicationEntry();
		entry.setPrimaryContact("Command");
		entry.setRadioChannelId("stable");
		form.getCommunications().add(entry);
		SarTaskAssignment task = SarTaskAssignment.fromResourceAssignment(null, null, form);
		CommunicationEntry copied = task.getCommunications().get(0);
		assertNotSame(entry, copied);
		assertEquals("stable", copied.getRadioChannelId());
		assertEquals("Command", copied.getPrimaryContact());
		copied.setPrimaryContact("Tactical");
		assertEquals("", copied.getRadioChannelId());
		assertEquals("stable", entry.getRadioChannelId());
		assertEquals("Command", entry.getPrimaryContact());
	}

	@Test
	void carriedChannelIdentityStillResolvesForPersistedExternalReferences() throws Exception {
		AppData data = new AppData();
		RadioChannel channel = new RadioChannel();
		channel.setChannelName("Command");
		data.getForm205().getChannels().add(channel);
		CommunicationEntry entry = new CommunicationEntry();
		entry.setPrimaryContact("Command");
		entry.setRadioChannelId(channel.getChannelId());
		data.getForm204().getCommunications().add(entry);
		TCard card = new TCard();
		card.setRadioChannel("Command");
		card.setRadioChannelId(channel.getChannelId());
		data.getTCards().add(card);

		AppData restored = mapper.readValue(mapper.writeValueAsBytes(data), AppData.class);
		String stableId = restored.getForm205().getChannels().get(0).getChannelId();
		assertEquals(stableId, restored.getForm204().getCommunications().get(0).getRadioChannelId());
		assertEquals(stableId, restored.getTCards().get(0).getRadioChannelId());
		restored.advanceToNewOperationalPeriod(true);
		assertEquals(stableId, restored.getForm205().getChannels().get(0).getChannelId());
		assertEquals(stableId, restored.getOperationalPeriodHistory().get(0).getForm205()
				.getChannels().get(0).getChannelId());
		assertEquals(stableId, entry.getRadioChannelId());
		assertEquals(stableId, card.getRadioChannelId());
	}

	@Test
	void projectionPeeksNeverInitializeLegacyIdentitiesOrReconcileAssignments() throws Exception {
		AppData data = new AppData();
		TCard card = new TCard();
		SarTaskAssignment task = new SarTaskAssignment();
		data.getOrganizationalChart().setMedicalUnitLeaderName("Legacy medical leader");
		for (int i = 0; i < 2; i++) {
			assertNull(data.peekMedicalUnitLeaderAssignment());
			assertNull(card.peekResourceId());
			assertNull(task.peekTaskId());
		}
		assertSame(data.getMedicalPlan(), data.peekMedicalPlan());
		assertSame(data.getCanineMedicalPlan(), data.peekCanineMedicalPlan());
		MedicalUnitLeaderAssignment assignment = data.getMedicalUnitLeaderAssignment();
		assertEquals("Legacy medical leader", assignment.getName());
		assertSame(assignment, data.peekMedicalUnitLeaderAssignment());
		card.setResourceId("existing-card");
		task.setTaskId("existing-task");
		assertEquals("existing-card", card.peekResourceId());
		assertEquals("existing-task", task.peekTaskId());
		var properties = mapper.valueToTree(data);
		assertFalse(properties.has("peekMedicalUnitLeaderAssignment"));
		assertFalse(properties.has("peekMedicalPlan"));
		assertFalse(properties.has("peekCanineMedicalPlan"));
		assertFalse(mapper.valueToTree(card).has("peekResourceId"));
		assertFalse(mapper.valueToTree(task).has("peekTaskId"));
	}
}
