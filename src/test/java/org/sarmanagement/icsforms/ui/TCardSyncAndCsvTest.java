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

    // -----------------------------------------------------------------------
    // CSV helper unit tests
    // -----------------------------------------------------------------------

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
