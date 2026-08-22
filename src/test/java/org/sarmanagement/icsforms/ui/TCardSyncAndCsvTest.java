package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.OrganizationalChart;
import org.sarmanagement.icsforms.model.SarTaskAssignment;
import org.sarmanagement.icsforms.model.SarTaskResource;
import org.sarmanagement.icsforms.model.TCard;
import org.sarmanagement.icsforms.model.TCardType;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for T-card cross-linking (org chart and SAR tasks) and CSV import helpers.
 */
class TCardSyncAndCsvTest {

    // -----------------------------------------------------------------------
    // syncTCards — org chart cross-linking
    // -----------------------------------------------------------------------

    @Test
    void syncTCardsCreatesCardForSafetyOfficer() {
        AppData data = appDataWithSafetyOfficer("Sam Safety", "555-0100");

        AppController ctrl = TestAppController.create(data);
        ctrl.syncTCards();

        List<TCard> cards = data.getTCards();
        TCard safetyCard = findByRef(cards, "org:safetyOfficer");
        assertNotNull(safetyCard, "Expected a T-card for the safety officer");
        assertEquals("Sam Safety", safetyCard.getPersonName());
        assertEquals("555-0100", safetyCard.getPhoneNumber());
    }

    @Test
    void syncTCardsUpdatesExistingCardFromOrgChart() {
        AppData data = appDataWithSafetyOfficer("Sam Safety", "555-0100");

        // Pre-seed a card with the correct sourceRef but old name.
        TCard existing = new TCard();
        existing.setSourceRef("org:safetyOfficer");
        existing.setPersonName("Old Name");
        existing.setStatus("Assigned");
        data.getTCards().add(existing);

        AppController ctrl = TestAppController.create(data);
        ctrl.syncTCards();

        TCard safetyCard = findByRef(data.getTCards(), "org:safetyOfficer");
        assertNotNull(safetyCard);
        assertEquals("Sam Safety", safetyCard.getPersonName(), "Name should be updated from org chart");
        assertEquals("Assigned", safetyCard.getStatus(), "Operator-set status must be preserved");
    }

    @Test
    void syncTCardsRemovesCardWhenOrgChartPositionCleared() {
        AppData data = appDataWithSafetyOfficer("Sam Safety", "555-0100");
        TCard existing = new TCard();
        existing.setSourceRef("org:safetyOfficer");
        existing.setPersonName("Sam Safety");
        data.getTCards().add(existing);

        // Now clear the safety officer name.
        data.getOrganizationalChart().setSafetyOfficerName("");

        AppController ctrl = TestAppController.create(data);
        ctrl.syncTCards();

        assertNull(findByRef(data.getTCards(), "org:safetyOfficer"),
                "Card should be removed when position is cleared");
    }

    @Test
    void syncTCardsPreservesManualCards() {
        AppData data = new AppData();
        TCard manual = new TCard();
        manual.setPersonName("Manual Person");
        // sourceRef is blank → manual card
        data.getTCards().add(manual);

        AppController ctrl = TestAppController.create(data);
        ctrl.syncTCards();

        boolean found = data.getTCards().stream()
                .anyMatch(c -> "Manual Person".equals(c.getPersonName()) && c.getSourceRef().isBlank());
        assertTrue(found, "Manual card (blank sourceRef) must be preserved");
    }

    @Test
    void syncTCardsCreatesCardsForAllNamedOrgChartPositions() {
        AppData data = new AppData();
        OrganizationalChart chart = data.getOrganizationalChart();
        chart.setSafetyOfficerName("Safety Sam");
        chart.setPublicInformationOfficerName("PIO Pat");
        chart.setLiaisonOfficerName("Liaison Lee");
        chart.setOperationsSectionChiefName("Ops Oscar");
        chart.setPlanningSectionChiefName("Plan Pete");
        chart.setLogisticsSectionChiefName("Log Larry");
        chart.setFinanceAdminSectionChiefName("Finance Fran");
        chart.setDocumentationUnitLeaderName("Doc Dan");

        AppController ctrl = TestAppController.create(data);
        ctrl.syncTCards();

        String[] refs = {
                "org:safetyOfficer", "org:pio", "org:liaisonOfficer",
                "org:operationsChief", "org:planningChief", "org:logisticsChief",
                "org:financeAdminChief", "org:documentationUnitLeader"
        };
        for (String ref : refs) {
            assertNotNull(findByRef(data.getTCards(), ref), "Missing card for " + ref);
        }
    }

    // -----------------------------------------------------------------------
    // syncTCards — SAR task resource cross-linking
    // -----------------------------------------------------------------------

    @Test
    void syncTCardsCreatesCardForSarTaskLeader() {
        AppData data = new AppData();
        AppController ctrl = TestAppController.create(data);

        // Add SAR task directly after construction (bypasses syncSarTasks cleanup).
        SarTaskAssignment task = new SarTaskAssignment();
        task.setAssignmentId("T001");
        task.setAssignmentTeamNumber("Team-1");
        task.setLeader("Alice Leader");
        task.setLeaderRole("Task Leader");
        task.setContact("555-1234");
        data.getSarTaskAssignments().add(task);

        ctrl.syncTCards();

        TCard leaderCard = findByRef(data.getTCards(), "sar:T001:leader");
        assertNotNull(leaderCard, "Expected card for SAR task leader");
        assertEquals("Alice Leader", leaderCard.getPersonName());
        assertEquals("555-1234", leaderCard.getPhoneNumber());
    }

    @Test
    void syncTCardsCreatesCardForEachSarTaskResource() {
        AppData data = new AppData();
        AppController ctrl = TestAppController.create(data);

        // Add SAR task directly after construction.
        SarTaskAssignment task = new SarTaskAssignment();
        task.setAssignmentId("T002");
        task.setAssignmentTeamNumber("Team-2");

        SarTaskResource r0 = new SarTaskResource();
        r0.setName("Bob Rescuer");
        r0.setHomeAgency("Agency B");
        SarTaskResource r1 = new SarTaskResource();
        r1.setName("Carol Medic");
        r1.setHomeAgency("Agency C");

        task.setResourcesAssigned(new ArrayList<>(List.of(r0, r1)));
        data.getSarTaskAssignments().add(task);

        ctrl.syncTCards();

        assertNotNull(findByRef(data.getTCards(), "sar:T002:r:0"), "Missing card for resource 0");
        assertNotNull(findByRef(data.getTCards(), "sar:T002:r:1"), "Missing card for resource 1");

        TCard r0Card = findByRef(data.getTCards(), "sar:T002:r:0");
        assertEquals("Bob Rescuer", r0Card.getPersonName());
        assertEquals("Agency B",   r0Card.getHomeAgency());
    }

    // -----------------------------------------------------------------------
    // syncTCards — canine/handler deduplication
    // -----------------------------------------------------------------------

    /**
     * Regression: a stale EQUIPMENT card whose {@code resourceIdentifier} matches the
     * handler's name (created by an older sync version) must not produce a duplicate.
     * After sync the handler should appear exactly once as PERSONNEL, and the stale
     * EQUIPMENT card (which is no longer referenced) must be removed.
     */
    @Test
    void syncTCardsDoesNotDuplicateHandlerWhenStaleEquipmentCardExists() {
        AppData data = new AppData();

        // Stale EQUIPMENT card: resourceIdentifier = handler name (old bug artefact).
        TCard staleEquipCard = new TCard();
        staleEquipCard.setCardType(TCardType.EQUIPMENT);
        staleEquipCard.setResourceIdentifier("John Smith");
        staleEquipCard.setSourceRef("sar:T100:r:0"); // non-blank → not a manual card
        data.getTCards().add(staleEquipCard);

        // Correct PERSONNEL card for the handler.
        TCard handlerPersonnelCard = new TCard();
        handlerPersonnelCard.setCardType(TCardType.PERSONNEL);
        handlerPersonnelCard.setPersonName("John Smith");
        handlerPersonnelCard.setSourceRef("sar:T100:leader");
        data.getTCards().add(handlerPersonnelCard);

        AppController ctrl = TestAppController.create(data);

        // Add SAR task after construction so that syncSarTasks() in the constructor
        // does not clear it (the task list is derived from the ICS 204 form on sync).
        SarTaskAssignment task = new SarTaskAssignment();
        task.setAssignmentId("T100");
        task.setResourceType("Canine");
        task.setLeader("John Smith");
        task.setLeaderRole("Leader/Handler");

        SarTaskResource handlerRes = new SarTaskResource();
        handlerRes.setName("John Smith");
        handlerRes.setFunction("Leader/Handler");
        task.setResourcesAssigned(new ArrayList<>(List.of(handlerRes)));
        data.getSarTaskAssignments().add(task);
        ctrl.syncTCards();

        List<TCard> cards = data.getTCards();

        long personnelCount = cards.stream()
                .filter(c -> c.getCardType() == TCardType.PERSONNEL
                        && "John Smith".equals(c.getPersonName()))
                .count();
        assertEquals(1, personnelCount,
                "Handler must appear exactly once as PERSONNEL; found " + personnelCount);

        long equipCount = cards.stream()
                .filter(c -> (c.getCardType() == TCardType.EQUIPMENT
                        || c.getCardType() == TCardType.MISC_EQUIPMENT)
                        && "John Smith".equals(c.getResourceIdentifier()))
                .count();
        assertEquals(0, equipCount,
                "Stale EQUIPMENT card with handler name must be removed after sync");
    }

    /**
     * Regression: a canine T-card that stores the dog's name in {@code resourceIdentifier}
     * (the generalised identifier for all non-personnel resources) must remain as EQUIPMENT
     * after {@code syncTCards()} and must not flip to PERSONNEL or display the handler's name.
     * Legacy data that had the name in {@code personName} with a blank {@code resourceIdentifier}
     * is automatically migrated by {@code syncTCards()} to use {@code resourceIdentifier}.
     */
    @Test
    void syncTCardsPreservesCanineCardTypeWhenNameStoredInPersonName() {
        AppData data = new AppData();

        // Canine T-card: legacy format — name stored in personName, resourceIdentifier blank.
        TCard canineCard = new TCard();
        canineCard.setCardType(TCardType.EQUIPMENT);
        canineCard.setPersonName("Rex");
        canineCard.setHandlerName("John Smith");
        data.getTCards().add(canineCard);

        // Handler T-card.
        TCard handlerCard = new TCard();
        handlerCard.setCardType(TCardType.PERSONNEL);
        handlerCard.setPersonName("John Smith");
        data.getTCards().add(handlerCard);

        AppController ctrl = TestAppController.create(data);

        SarTaskAssignment task = new SarTaskAssignment();
        task.setAssignmentId("T200");
        task.setResourceType("Canine");
        task.setLeader("John Smith");
        task.setLeaderRole("Handler");

        // The canine appears in resourcesAssigned with the dog's name and EQUIPMENT type.
        SarTaskResource canineRes = new SarTaskResource();
        canineRes.setName("Rex");
        canineRes.setCardType(TCardType.EQUIPMENT);
        canineRes.setFunction("Canine");
        task.setResourcesAssigned(new ArrayList<>(List.of(canineRes)));
        data.getSarTaskAssignments().add(task);
        ctrl.syncTCards();

        List<TCard> cards = data.getTCards();

        // After sync, the name must have been migrated to resourceIdentifier.
        Optional<TCard> rex = cards.stream()
                .filter(c -> "Rex".equalsIgnoreCase(c.getResourceIdentifier()))
                .findFirst();
        assertTrue(rex.isPresent(), "Canine card 'Rex' must still exist after sync (via resourceIdentifier)");
        assertEquals(TCardType.EQUIPMENT, rex.get().getCardType(),
                "Canine card must remain EQUIPMENT, not flip to PERSONNEL");
        assertNotEquals("John Smith", rex.get().getResourceIdentifier(),
                "Canine card resourceIdentifier must not be overwritten with handler name");

        // The resource entry must also keep the EQUIPMENT type.
        assertEquals(TCardType.EQUIPMENT, canineRes.getCardType(),
                "SarTaskResource cardType must remain EQUIPMENT after sync");
    }

    /**
     * Regression: when a canine is entered manually on the SAR task resource list with
     * cardType=EQUIPMENT but no existing TCard exists for it yet (cold-start / post-restart),
     * syncTCards must create an EQUIPMENT card, not a PERSONNEL card.
     * Previously it fell through to findOrCreatePersonCard, which created a PERSONNEL card
     * bearing the canine's name and then set res.cardType to PERSONNEL — causing handler
     * name bleed-through on the next sync.
     */
    @Test
    void syncTCardsCreatesEquipmentCardForManuallyTypedEquipmentResource() {
        AppData data = new AppData();

        // Handler T-card only — no canine TCard in the list yet.
        TCard handlerCard = new TCard();
        handlerCard.setCardType(TCardType.PERSONNEL);
        handlerCard.setPersonName("Jane Doe");
        data.getTCards().add(handlerCard);

        AppController ctrl = TestAppController.create(data);

        SarTaskAssignment task = new SarTaskAssignment();
        task.setAssignmentId("T300");
        task.setResourceType("Canine");
        task.setLeader("Jane Doe");
        task.setLeaderRole("Handler");

        // Canine added manually with EQUIPMENT type — no pre-existing TCard.
        SarTaskResource canineRes = new SarTaskResource();
        canineRes.setName("Buddy");
        canineRes.setCardType(TCardType.EQUIPMENT);
        canineRes.setFunction("Canine");
        task.setResourcesAssigned(new ArrayList<>(List.of(canineRes)));
        data.getSarTaskAssignments().add(task);

        ctrl.syncTCards();

        List<TCard> cards = data.getTCards();

        // A new EQUIPMENT card should have been created for "Buddy".
        Optional<TCard> buddy = cards.stream()
                .filter(c -> "Buddy".equalsIgnoreCase(c.getResourceIdentifier()))
                .findFirst();
        assertTrue(buddy.isPresent(), "EQUIPMENT card 'Buddy' must be created by syncTCards");
        assertEquals(TCardType.EQUIPMENT, buddy.get().getCardType(),
                "Card for canine 'Buddy' must be EQUIPMENT, not PERSONNEL");

        // The SarTaskResource cardType must not have been overwritten to PERSONNEL.
        assertEquals(TCardType.EQUIPMENT, canineRes.getCardType(),
                "SarTaskResource.cardType must remain EQUIPMENT after sync");

        // The handler card must not have changed its name to "Buddy".
        assertEquals("Jane Doe", handlerCard.getPersonName(),
                "Handler card personName must not be overwritten by canine name");

        // The handler must appear exactly once.
        long handlerCount = cards.stream()
                .filter(c -> c.getCardType() == TCardType.PERSONNEL
                        && "Jane Doe".equalsIgnoreCase(c.getPersonName()))
                .count();
        assertEquals(1, handlerCount, "Handler must not be duplicated");
    }



    // -----------------------------------------------------------------------
    // syncTCards — task assignment overrides org chart sourceRef
    // -----------------------------------------------------------------------

    /**
     * A person in both an org chart role and a task assignment must appear in the
     * task group (sourceRef starts with "sar:"), not under ICP (sourceRef "org:...").
     */
    @Test
    void syncTCardsTaskRefOverridesOrgChartRef() {
        AppData data = new AppData();
        // Person is Operations Section Chief in the org chart.
        data.getOrganizationalChart().setOperationsSectionChiefName("Alice Johnson");

        AppController ctrl = TestAppController.create(data);

        // Add SAR task after construction (bypasses syncSarTasks cleanup).
        SarTaskAssignment task = new SarTaskAssignment();
        task.setAssignmentId("task-1");
        SarTaskResource res = new SarTaskResource();
        res.setName("Alice Johnson");
        res.setCardType(TCardType.PERSONNEL);
        task.setResourcesAssigned(new ArrayList<>(List.of(res)));
        data.getSarTaskAssignments().add(task);

        ctrl.syncTCards();

        List<TCard> cards = data.getTCards();
        // Alice should have exactly one T-card.
        long aliceCount = cards.stream()
                .filter(c -> "Alice Johnson".equalsIgnoreCase(c.getPersonName()))
                .count();
        assertEquals(1, aliceCount, "Alice must have exactly one T-card");

        TCard alice = cards.stream()
                .filter(c -> "Alice Johnson".equalsIgnoreCase(c.getPersonName()))
                .findFirst().get();
        // The sourceRef must be a task ref so she appears in the task group, not ICP.
        assertTrue(alice.getSourceRef().startsWith("sar:"),
                "Task assignment must override org chart sourceRef; was: " + alice.getSourceRef());
    }

    /**
     * A resource entry with a stale non-PERSONNEL cardType whose name matches a known
     * person must be corrected to PERSONNEL rather than creating a phantom equipment card.
     */
    @Test
    void syncTCardsCorrectedStaleEquipmentCardTypeForKnownPerson() {
        AppData data = new AppData();
        // Pre-existing PERSONNEL card for "John Doe".
        TCard handler = new TCard();
        handler.setCardType(TCardType.PERSONNEL);
        handler.setPersonName("John Doe");
        data.getTCards().add(handler);

        AppController ctrl = TestAppController.create(data);

        // resourcesAssigned has two stale entries: one EQUIPMENT and one PERSONNEL for same name.
        SarTaskAssignment task = new SarTaskAssignment();
        task.setAssignmentId("task-dup");
        SarTaskResource eqRes = new SarTaskResource();
        eqRes.setName("John Doe");
        eqRes.setCardType(TCardType.EQUIPMENT);   // stale type from previous bug
        SarTaskResource perRes = new SarTaskResource();
        perRes.setName("John Doe");
        perRes.setCardType(TCardType.PERSONNEL);
        task.setResourcesAssigned(new ArrayList<>(List.of(eqRes, perRes)));
        data.getSarTaskAssignments().add(task);

        ctrl.syncTCards();

        List<TCard> cards = data.getTCards();
        // There must be exactly one card for "John Doe" (no phantom EQUIPMENT card).
        long handlerCardCount = cards.stream()
                .filter(c -> "John Doe".equalsIgnoreCase(c.getPersonName()))
                .count();
        long phantomEquipCount = cards.stream()
                .filter(c -> c.getCardType() != TCardType.PERSONNEL
                        && "John Doe".equalsIgnoreCase(c.getResourceIdentifier()))
                .count();
        assertEquals(1, handlerCardCount, "John Doe must appear exactly once as PERSONNEL");
        assertEquals(0, phantomEquipCount, "No phantom EQUIPMENT card must be created for a known person");
    }

    /**
     * A newly created PERSONNEL card must have numberOfPersons == 1 (not 0).
     */
    @Test
    void syncTCardsPersonnelCardHasDefaultNumberOfPersonsOne() {
        AppData data = new AppData();
        data.getOrganizationalChart().setSafetyOfficerName("Bob Smith");
        AppController ctrl = TestAppController.create(data);
        ctrl.syncTCards();

        TCard card = data.getTCards().stream()
                .filter(c -> "Bob Smith".equalsIgnoreCase(c.getPersonName()))
                .findFirst().orElse(null);
        assertNotNull(card);
        assertEquals(1, card.getNumberOfPersons(),
                "PERSONNEL card must default to numberOfPersons = 1");
    }

    /**
     * Existing PERSONNEL cards with numberOfPersons == 0 (legacy data) must be migrated to 1.
     */
    @Test
    void syncTCardsMigratesLegacyPersonnelCardsWithZeroPersonCount() {
        AppData data = new AppData();
        TCard legacy = new TCard();
        legacy.setCardType(TCardType.PERSONNEL);
        legacy.setPersonName("Carol White");
        legacy.setNumberOfPersons(0);
        data.getTCards().add(legacy);

        AppController ctrl = TestAppController.create(data);
        ctrl.syncTCards();

        assertEquals(1, legacy.getNumberOfPersons(),
                "Legacy PERSONNEL card with numberOfPersons=0 must be migrated to 1");
    }


    @Test
    void parseCsvSplitsSimpleLine() {
        String[] fields = TCardPanel.splitCsvLine("Alice,Marin SAR,CA,415-555-1111,person");
        assertArrayEquals(new String[]{"Alice", "Marin SAR", "CA", "415-555-1111", "person"}, fields);
    }

    @Test
    void parseCsvHandlesQuotedFieldWithComma() {
        String[] fields = TCardPanel.splitCsvLine("\"Smith, Alice\",Marin SAR,CA,415-555-1111,person");
        assertEquals("Smith, Alice", fields[0]);
        assertEquals("Marin SAR",    fields[1]);
    }

    @Test
    void parseCsvHandlesEscapedDoubleQuote() {
        String[] fields = TCardPanel.splitCsvLine("\"O\"\"Brien\",Agency,CA,,person");
        assertEquals("O\"Brien", fields[0]);
    }

    @Test
    void parseCsvFileSkipsBlankLines() throws IOException {
        File tmp = Files.createTempFile("tcard_test", ".csv").toFile();
        Files.writeString(tmp.toPath(), "Alice,Agency,CA,555,person\n\nBob,Agency2,OR,556,canine\n");
        List<String[]> rows = TCardPanel.parseCsv(tmp);
        assertEquals(2, rows.size());
        tmp.deleteOnExit();
    }

    @Test
    void inferCardTypeRecognisesKeywords() {
        // Canine and handler → 219-7 Equipment (canines are working assets/equipment in ICS)
        assertEquals(TCardType.EQUIPMENT,      TCardPanel.inferCardType("canine handler"));
        assertEquals(TCardType.EQUIPMENT,      TCardPanel.inferCardType("k9"));
        // Fixed-wing / drone
        assertEquals(TCardType.FIXED_WING,     TCardPanel.inferCardType("drone"));
        assertEquals(TCardType.FIXED_WING,     TCardPanel.inferCardType("219-6 Fixed-Wing"));
        assertEquals(TCardType.HELICOPTER,     TCardPanel.inferCardType("Helicopter"));
        // Equipment (219-7) also matches dozer
        assertEquals(TCardType.EQUIPMENT,      TCardPanel.inferCardType("dozer"));
        assertEquals(TCardType.ENGINE,         TCardPanel.inferCardType("engine 219-3"));
        assertEquals(TCardType.CREW,           TCardPanel.inferCardType("Crew"));
        assertEquals(TCardType.GENERIC,        TCardPanel.inferCardType("219-10 generic"));
        assertEquals(TCardType.PERSONNEL,      TCardPanel.inferCardType("person"));
        assertEquals(TCardType.PERSONNEL,      TCardPanel.inferCardType(null));
        assertEquals(TCardType.PERSONNEL,      TCardPanel.inferCardType(""));
    }

    /**
     * On a canine task the resource list may contain the handler's own name typed as EQUIPMENT
     * (a legacy UI artefact).  That entry must be treated as PERSONNEL — not used to create a
     * canine T-card — and the canine card must not have the handler's name as its identifier.
     */
    @Test
    void syncTCardsDoesNotConflateHandlerNameWithCanineResource() {
        AppData data = new AppData();
        AppController ctrl = TestAppController.create(data);

        SarTaskAssignment task = new SarTaskAssignment();
        task.setAssignmentId("task-k9");
        task.setResourceType("Canine");
        task.setLeader("Jane Doe");
        task.setLeaderRole("Handler");

        // Resource list has the handler's own name typed as EQUIPMENT (old bug artefact)
        // plus a real canine entry.
        SarTaskResource handlerAsEquip = new SarTaskResource();
        handlerAsEquip.setName("Jane Doe");
        handlerAsEquip.setCardType(TCardType.EQUIPMENT);

        SarTaskResource canineRes = new SarTaskResource();
        canineRes.setName("K9 Rex");
        canineRes.setCardType(TCardType.EQUIPMENT);

        task.setResourcesAssigned(new ArrayList<>(List.of(handlerAsEquip, canineRes)));
        data.getSarTaskAssignments().add(task);

        ctrl.syncTCards();

        List<TCard> cards = data.getTCards();

        // Handler must have exactly one PERSONNEL card.
        long handlerPersonnel = cards.stream()
                .filter(c -> c.getCardType() == TCardType.PERSONNEL
                        && "Jane Doe".equalsIgnoreCase(c.getPersonName()))
                .count();
        assertEquals(1, handlerPersonnel, "Handler must have exactly one PERSONNEL card");

        // No phantom equipment card with the handler's name as resourceIdentifier.
        long phantomEquip = cards.stream()
                .filter(c -> c.getCardType() != TCardType.PERSONNEL
                        && "Jane Doe".equalsIgnoreCase(c.getResourceIdentifier()))
                .count();
        assertEquals(0, phantomEquip,
                "No equipment card may have the handler's name as resourceIdentifier");

        // Canine card must exist with correct name and handler link.
        TCard canineCard = cards.stream()
                .filter(c -> "K9 Rex".equalsIgnoreCase(c.getResourceIdentifier()))
                .findFirst().orElse(null);
        assertNotNull(canineCard, "Canine T-card must exist");
        assertEquals(TCardType.EQUIPMENT, canineCard.getCardType());
        assertEquals("Jane Doe", canineCard.getHandlerName(),
                "Canine card must reference the handler's name");
    }

    /**
     * A resource assigned to a second task must appear under that task in the rack view
     * (last-assignment-wins), not remain locked to the first task's ref.
     */
    @Test
    void syncTCardsUpdatesSourceRefWhenResourceReassignedToAnotherTask() {
        AppData data = new AppData();
        AppController ctrl = TestAppController.create(data);

        SarTaskAssignment task1 = new SarTaskAssignment();
        task1.setAssignmentId("task-alpha");
        task1.setLeader("Bob Jones");

        SarTaskAssignment task2 = new SarTaskAssignment();
        task2.setAssignmentId("task-bravo");
        SarTaskResource bobRes = new SarTaskResource();
        bobRes.setName("Bob Jones");
        bobRes.setCardType(TCardType.PERSONNEL);
        task2.setResourcesAssigned(new ArrayList<>(List.of(bobRes)));

        data.getSarTaskAssignments().add(task1);
        data.getSarTaskAssignments().add(task2);

        ctrl.syncTCards();

        List<TCard> cards = data.getTCards();
        TCard bobCard = cards.stream()
                .filter(c -> "Bob Jones".equalsIgnoreCase(c.getPersonName()))
                .findFirst().orElse(null);
        assertNotNull(bobCard, "T-card for Bob Jones must exist");
        // Bob is the leader of task-alpha AND appears in task-bravo resources.
        // The last task processed (task-bravo resource) must win.
        assertTrue(bobCard.getSourceRef().startsWith("sar:task-bravo"),
                "Bob's sourceRef must reflect the last task that claimed him, got: "
                        + bobCard.getSourceRef());
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static AppData appDataWithSafetyOfficer(String name, String contact) {
        AppData data = new AppData();
        data.getOrganizationalChart().setSafetyOfficerName(name);
        data.getOrganizationalChart().setSafetyOfficerPhone(contact);
        return data;
    }

    private static TCard findByRef(List<TCard> cards, String ref) {
        return cards.stream().filter(c -> ref.equals(c.getSourceRef())).findFirst().orElse(null);
    }

    // -----------------------------------------------------------------------
    // Minimal AppController factory for unit tests (no Swing, no persistence)
    // -----------------------------------------------------------------------

    /**
     * Creates a minimal {@link AppController} backed by in-memory stubs so that
     * sync methods can be exercised without a real repository or export service.
     */
    private static final class TestAppController {
        static AppController create(AppData data) {
            org.sarmanagement.icsforms.persistence.LocalRepository repo =
                    new org.sarmanagement.icsforms.persistence.LocalRepository(
                            java.nio.file.Path.of(System.getProperty("java.io.tmpdir"), "tcard_test.json"));
            org.sarmanagement.icsforms.pdf.PdfExportService exportService =
                    new org.sarmanagement.icsforms.pdf.PdfExportService();
            org.sarmanagement.icsforms.validation.IncidentValidator validator =
                    new org.sarmanagement.icsforms.validation.IncidentValidator();
            return new AppController(data, repo, exportService, validator);
        }
    }
}
