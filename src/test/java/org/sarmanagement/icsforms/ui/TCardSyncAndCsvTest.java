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
        assertEquals(TCardType.MISC_EQUIPMENT, TCardPanel.inferCardType("canine handler"));
        assertEquals(TCardType.AIRCRAFT,       TCardPanel.inferCardType("drone"));
        assertEquals(TCardType.AIRCRAFT,       TCardPanel.inferCardType("219-6 Aircraft"));
        assertEquals(TCardType.HELICOPTER,     TCardPanel.inferCardType("Helicopter"));
        assertEquals(TCardType.DOZER,          TCardPanel.inferCardType("dozer"));
        assertEquals(TCardType.ENGINE,         TCardPanel.inferCardType("engine 219-3"));
        assertEquals(TCardType.CREW,           TCardPanel.inferCardType("Crew"));
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
