/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.IncidentMode;
import org.sarmanagement.icsforms.model.ResourceAssignment;
import org.sarmanagement.icsforms.model.SarTaskAssignment;
import org.sarmanagement.icsforms.model.SarTaskSupport;
import org.sarmanagement.icsforms.model.TCard;
import org.sarmanagement.icsforms.model.TCardType;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests facts-only animal classification and objective lifecycle aggregation. */
class IncidentCommandSummaryTest {
	@Test
	void sarModeAndOrdinaryEquipmentDoNotRequireAnimalMedicalPlan() {
		AppData data = new AppData();
		data.setIncidentMode(IncidentMode.SAR);
		TCard equipment = new TCard();
		equipment.setCardType(TCardType.EQUIPMENT);
		equipment.setResourceIdentifier("Generator");
		data.getTCards().add(equipment);
		assertFalse(IncidentCommandSummary.requiresAnimalMedicalPlan(data));
		assertEquals("219-7 Equipment", IncidentCommandSummary.resourceGroup(equipment));
	}

	@Test
	void knownCanineEquipmentHasSeparateGroupRegardlessOfStatus() {
		AppData data = new AppData();
		TCard canine = new TCard();
		canine.setCardType(TCardType.EQUIPMENT);
		canine.setCanineResource(true);
		canine.setStatus("Out of service");
		data.getTCards().add(canine);
		assertTrue(IncidentCommandSummary.requiresAnimalMedicalPlan(data));
		assertEquals("219-7 Equipment — Canine", IncidentCommandSummary.resourceGroup(canine));
		canine.setCanineResource(false);
		canine.setResourceIdentifier("K9 Echo");
		assertTrue(IncidentCommandSummary.requiresAnimalMedicalPlan(data));
		assertEquals("219-7 Equipment — Canine", IncidentCommandSummary.resourceGroup(canine));
	}

	@Test
	void canineAndEquineAssignmentAndTaskTypesRequireVeterinaryPlanning() {
		for (String type : List.of(SarTaskSupport.RESOURCE_TYPE_CANINE, SarTaskSupport.RESOURCE_TYPE_EQUINE)) {
			AppData data = new AppData();
			ResourceAssignment assignment = new ResourceAssignment();
			assignment.setResourceType(type);
			data.getForm204().getResourcesAssigned().add(assignment);
			assertTrue(IncidentCommandSummary.requiresAnimalMedicalPlan(data));
			data.getForm204().getResourcesAssigned().clear();
			SarTaskAssignment task = new SarTaskAssignment();
			task.setResourceType(type);
			data.getSarTaskAssignments().add(task);
			assertTrue(IncidentCommandSummary.requiresAnimalMedicalPlan(data));
		}
	}

	@Test
	void exactEquineCardTypeLabelRequiresPlanningButHeaderDoesNot() {
		AppData data = new AppData();
		TCard card = new TCard();
		card.setCardType(TCardType.EQUIPMENT);
		card.setResourceIdentifier("Equine");
		data.getTCards().add(card);
		assertTrue(IncidentCommandSummary.requiresAnimalMedicalPlan(data));
		card.setCardType(TCardType.HEADER);
		assertFalse(IncidentCommandSummary.requiresAnimalMedicalPlan(data));
	}

	@Test
	void genericAndEquipmentNamesRecognizeExplicitAnimalLabelsWithoutHumanOrSubstringGuesses() {
		for (TCardType type : List.of(TCardType.GENERIC, TCardType.MISC_EQUIPMENT, TCardType.EQUIPMENT)) {
			for (String name : List.of("Canine Echo", "K-9 Echo", "Equine Scout", "Horse Scout")) {
				AppData data = new AppData();
				TCard card = new TCard();
				card.setCardType(type);
				card.setPersonName(name);
				data.getTCards().add(card);
				assertTrue(IncidentCommandSummary.requiresAnimalMedicalPlan(data), type + ": " + name);
			}
		}
		AppData data = new AppData();
		TCard card = new TCard();
		card.setCardType(TCardType.GENERIC);
		card.setPersonName("Hotdog cart");
		data.getTCards().add(card);
		assertFalse(IncidentCommandSummary.requiresAnimalMedicalPlan(data));
		card.setPersonName("Canine Handler");
		card.setCardType(TCardType.PERSONNEL);
		assertFalse(IncidentCommandSummary.requiresAnimalMedicalPlan(data));
		card.setCardType(TCardType.EQUIPMENT);
		card.setPersonName("Canine Echo");
		assertEquals("219-7 Equipment — Canine", IncidentCommandSummary.resourceGroup(card));
	}

	@Test
	void equineAssetIdentifiersUseExplicitWordBoundariesRatherThanExactLabelsOrNarratives() {
		for (TCardType type : List.of(TCardType.GENERIC, TCardType.MISC_EQUIPMENT, TCardType.EQUIPMENT)) {
			for (String identifier : List.of("Equine Team A", "Horse X")) {
				AppData data = new AppData();
				TCard card = new TCard();
				card.setCardType(type);
				card.setResourceIdentifier(identifier);
				data.getTCards().add(card);
				assertTrue(IncidentCommandSummary.requiresAnimalMedicalPlan(data), type + ": " + identifier);
				card.setResourceIdentifier("Horsepower meter");
				card.setNotes("Equine team may be requested later");
				assertFalse(IncidentCommandSummary.requiresAnimalMedicalPlan(data));
			}
		}
	}

	@Test
	void linkedAssignmentStatesRetainEveryRecordedLifecycleAndUnknownState() {
		AppData data = new AppData();
		for (String state : List.of("planned", "assigned - enroute to assignment", "assigned - on task",
				"assigned - returning from assignment", "returned", "custom recorded status")) {
			ResourceAssignment assignment = assignment(data, state);
			task(data, assignment.getAssignmentId(), state);
		}
		ResourceAssignment missing = assignment(data, "missing");
		ResourceAssignment unrelated = new ResourceAssignment();
		unrelated.setAssignmentId("unrelated");
		data.getForm204().getResourcesAssigned().add(unrelated);
		task(data, "unrelated", "unrelated-state");
		task(data, "orphan-task", "orphan-state");
		String states = IncidentCommandSummary.assignmentStates(data, "objective");
		for (String state : List.of("planned", "assigned - enroute to assignment", "assigned - on task",
				"assigned - returning from assignment", "returned", "custom recorded status", "No lifecycle recorded")) {
			assertTrue(states.contains(state + ": 1"), states);
		}
		assertFalse(states.contains("unrelated-state"));
		assertFalse(states.contains("orphan-state"));
		assertEquals("No linked assignments", IncidentCommandSummary.assignmentStates(data, "other-objective"));
		assertTrue(missing.getObjectiveIds().contains("objective"));
	}

	@Test
	void duplicateLinksAndTaskRecordsDoNotInflateCountsAndConflictsAreExplicit() {
		AppData data = new AppData();
		ResourceAssignment assignment = assignment(data, "stable-assignment");
		assignment.setObjectiveIds(List.of("objective", "objective"));
		data.getForm204().getResourcesAssigned().add(assignment);
		task(data, assignment.getAssignmentId(), "returned");
		task(data, assignment.getAssignmentId(), "returned");
		assertEquals("returned: 1", IncidentCommandSummary.assignmentStates(data, "objective"));
		task(data, assignment.getAssignmentId(), "assigned - on task");
		assertEquals("Conflicting lifecycle records: 1",
				IncidentCommandSummary.assignmentStates(data, "objective"));
	}

	@Test
	void blankAssignmentIdentifiersNeverLinkToUnlinkedTaskDefaults() {
		AppData data = new AppData();
		assignment(data, "");
		task(data, "", "returned");
		assertEquals("No lifecycle recorded: 1", IncidentCommandSummary.assignmentStates(data, "objective"));
	}

	@Test
	void populatedAssignmentCountExcludesDefaultRowsAndLinkageMetadata() {
		AppData data = new AppData();
		ResourceAssignment blank = assignment(data, "generated-id");
		blank.setObjectiveIds(List.of("objective"));
		assertEquals(0, IncidentCommandSummary.populatedAssignments(data));
		blank.setAssignment("Search north");
		assertEquals(1, IncidentCommandSummary.populatedAssignments(data));
		ResourceAssignment resource = assignment(data, "resource");
		resource.setResourceIdentifier("Ground Team 1");
		assertEquals(2, IncidentCommandSummary.populatedAssignments(data));
		ResourceAssignment staffed = assignment(data, "staffed");
		staffed.setNumberOfPersons(3);
		assertEquals(3, IncidentCommandSummary.populatedAssignments(data));
	}

	/**
	 * Adds a stable objective-linked assignment fixture.
	 *
	 * @param data incident fixture.
	 * @param id stable assignment identifier.
	 * @return added assignment.
	 */
	private ResourceAssignment assignment(AppData data, String id) {
		ResourceAssignment assignment = new ResourceAssignment();
		assignment.setAssignmentId(id);
		assignment.setObjectiveIds(List.of("objective"));
		data.getForm204().getResourcesAssigned().add(assignment);
		return assignment;
	}

	/**
	 * Adds an explicitly linked task fixture.
	 *
	 * @param data incident fixture.
	 * @param assignmentId stable linked assignment identifier.
	 * @param state recorded lifecycle.
	 */
	private void task(AppData data, String assignmentId, String state) {
		SarTaskAssignment task = new SarTaskAssignment();
		task.setAssignmentId(assignmentId);
		task.setTaskLifecycleStatus(state);
		data.getSarTaskAssignments().add(task);
	}
}
