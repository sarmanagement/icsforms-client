package org.sarmanagement.icsforms.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceDirectorySourceTest {

    @Test
    void buildProjectsPersonnelDirectoryRowsFromCanonicalTCardAndTaskData() {
        AppData data = new AppData();

        SarTaskAssignment task = new SarTaskAssignment();
        task.setAssignmentId("A1");
        task.setAssignmentTeamNumber("A-1");
        task.setResourceIdentifier("Ground-1");
        task.setLeader("Alex Leader");
        task.setLeaderRole("Task Leader");
        task.setAssignment("Search creek drainage to the north ridge");

        TCard resourceCard = new TCard();
        resourceCard.setCardType(TCardType.PERSONNEL);
        resourceCard.setPersonName("Kim Rescuer");
        resourceCard.setHomeAgency("Unit 7");
        resourceCard.setHomeState("wa");
        resourceCard.setPhoneNumber("555-2000");
        resourceCard.setSourceRef("sar:A1:r:0");

        SarTaskResource resource = new SarTaskResource();
        resource.setName("Kim Rescuer");
        resource.setIcsPosition("Searcher");
        resource.setFunction("Medic");
        resource.setResourceId(resourceCard.getResourceId());
        task.setResourcesAssigned(List.of(resource));

        TCard leaderCard = new TCard();
        leaderCard.setCardType(TCardType.PERSONNEL);
        leaderCard.setPersonName("Alex Leader");
        leaderCard.setHomeAgency("SAR");
        leaderCard.setHomeState("or");
        leaderCard.setRadioChannel("Tac 1");
        leaderCard.setPhoneNumber("555-1000");
        leaderCard.setStatus("Assigned");
        leaderCard.setSourceRef("sar:A1:leader");

        TCard safetyCard = new TCard();
        safetyCard.setCardType(TCardType.PERSONNEL);
        safetyCard.setPersonName("Sam Safety");
        safetyCard.setHomeAgency("Planning");
        safetyCard.setHomeState("ca");
        safetyCard.setPhoneNumber("555-3000");
        safetyCard.setLocation("ICP");
        safetyCard.setSourceRef("org:safetyOfficer");

        TCard equipment = new TCard();
        equipment.setCardType(TCardType.EQUIPMENT);
        equipment.setResourceIdentifier("K9-1");

        data.setSarTaskAssignments(List.of(task));
        data.setTCards(List.of(leaderCard, resourceCard, safetyCard, equipment));

        List<ResourceDirectoryEntry> rows = ResourceDirectorySource.build(data);

        assertEquals(3, rows.size());
        ResourceDirectoryEntry leaderRow = rows.stream().filter(row -> row.name().equals("Alex Leader")).findFirst().orElseThrow();
        assertEquals("Task Leader", leaderRow.assignedPosition());
        assertEquals("OR", leaderRow.state());
        assertTrue(leaderRow.assignment().contains("A-1"));
        assertTrue(leaderRow.assignment().contains("Ground-1"));
        assertTrue(leaderRow.contactMethods().contains("Radio: Tac 1"));
        assertTrue(leaderRow.contactMethods().contains("Phone: 555-1000"));

        ResourceDirectoryEntry resourceRow = rows.stream().filter(row -> row.name().equals("Kim Rescuer")).findFirst().orElseThrow();
        assertEquals("Searcher", resourceRow.assignedPosition());
        assertEquals("WA", resourceRow.state());
        assertEquals("Unit 7", resourceRow.unit());
        assertTrue(resourceRow.assignment().contains("Search creek drainage"));

        ResourceDirectoryEntry safetyRow = rows.stream().filter(row -> row.name().equals("Sam Safety")).findFirst().orElseThrow();
        assertEquals("Safety Officer", safetyRow.assignedPosition());
        assertEquals("ICP", safetyRow.assignment());
    }

    @Test
    void byLastNameSortsRowsAlphabeticallyBySurname() {
        TCard alpha = new TCard();
        alpha.setCardType(TCardType.PERSONNEL);
        alpha.setPersonName("Zoe Alpha");

        TCard bravo = new TCard();
        bravo.setCardType(TCardType.PERSONNEL);
        bravo.setPersonName("Amy Bravo");

        TCard charlie = new TCard();
        charlie.setCardType(TCardType.PERSONNEL);
        charlie.setPersonName("Chris Charlie");

        AppData data = new AppData();
        data.setTCards(List.of(charlie, alpha, bravo));

        List<String> names = ResourceDirectorySource.build(data).stream()
                .sorted(ResourceDirectorySource.byLastName())
                .map(ResourceDirectoryEntry::name)
                .toList();

        assertEquals(List.of("Zoe Alpha", "Amy Bravo", "Chris Charlie"), names);
    }

    @Test
    void byLastNameIgnoresCommonSuffixes() {
        TCard doe = new TCard();
        doe.setCardType(TCardType.PERSONNEL);
        doe.setPersonName("Pat Doe III");

        TCard smith = new TCard();
        smith.setCardType(TCardType.PERSONNEL);
        smith.setPersonName("Jane Smith Jr.");

        TCard adams = new TCard();
        adams.setCardType(TCardType.PERSONNEL);
        adams.setPersonName("Alex Adams");

        AppData data = new AppData();
        data.setTCards(List.of(smith, doe, adams));

        List<String> names = ResourceDirectorySource.build(data).stream()
                .sorted(ResourceDirectorySource.byLastName())
                .map(ResourceDirectoryEntry::name)
                .toList();

        assertEquals(List.of("Alex Adams", "Pat Doe III", "Jane Smith Jr."), names);
    }
}
