package org.sarmanagement.icsforms.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.persistence.LocalRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for incident mode, T-Card model, and expanded organizational chart serialization.
 */
class IncidentModeAndTCardTest {

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    // -----------------------------------------------------------------------
    // IncidentMode serialization
    // -----------------------------------------------------------------------

    @Test
    void incidentModeDefaultsToSar() {
        AppData data = new AppData();
        assertEquals(IncidentMode.SAR, data.getIncidentMode());
    }

    @Test
    void incidentModeRoundTrips() throws IOException {
        AppData data = new AppData();
        data.setIncidentMode(IncidentMode.GENERIC);
        String json = mapper.writeValueAsString(data);
        AppData loaded = mapper.readValue(json, AppData.class);
        assertEquals(IncidentMode.GENERIC, loaded.getIncidentMode());
    }

    @Test
    void incidentModeSarRoundTrips() throws IOException {
        AppData data = new AppData();
        data.setIncidentMode(IncidentMode.SAR);
        String json = mapper.writeValueAsString(data);
        AppData loaded = mapper.readValue(json, AppData.class);
        assertEquals(IncidentMode.SAR, loaded.getIncidentMode());
    }

    /**
     * Files written before this feature was added should default to SAR mode for backward compat.
     */
    @Test
    void missingIncidentModeFieldDefaultsToSarOnDeserialization() throws IOException {
        String json = "{\"schemaVersion\":2,\"incidentContext\":{\"incidentName\":\"Test\"}}";
        AppData loaded = mapper.readValue(json, AppData.class);
        assertEquals(IncidentMode.SAR, loaded.getIncidentMode());
    }

    // -----------------------------------------------------------------------
    // T-Card model
    // -----------------------------------------------------------------------

    @Test
    void tCardDefaultsToPersonnelType() {
        TCard card = new TCard();
        assertEquals(TCardType.PERSONNEL, card.getCardType());
    }

    @Test
    void tCardHomeStateNormalisedToUpperCase() {
        TCard card = new TCard();
        card.setHomeState("ca");
        assertEquals("CA", card.getHomeState());
    }

    @Test
    void tCardRoundTrips() throws IOException {
        TCard card = new TCard();
        card.setCardType(TCardType.PERSONNEL);
        card.setPersonName("Jane Smith");
        card.setHomeAgency("Marin SAR");
        card.setHomeState("CA");
        card.setPhoneNumber("415-555-1234");
        card.setCheckInDateTime(LocalDateTime.of(2026, 6, 15, 8, 0));
        card.setLocation("ICP");
        card.setStatus("Assigned");

        String json = mapper.writeValueAsString(card);
        TCard loaded = mapper.readValue(json, TCard.class);

        assertEquals(TCardType.PERSONNEL, loaded.getCardType());
        assertEquals("Jane Smith", loaded.getPersonName());
        assertEquals("Marin SAR", loaded.getHomeAgency());
        assertEquals("CA", loaded.getHomeState());
        assertEquals("415-555-1234", loaded.getPhoneNumber());
        assertEquals(LocalDateTime.of(2026, 6, 15, 8, 0), loaded.getCheckInDateTime());
        assertEquals("ICP", loaded.getLocation());
        assertEquals("Assigned", loaded.getStatus());
    }

    @Test
    void tCardCanineHandlerMapsToMiscEquipment() throws IOException {
        TCard card = new TCard();
        card.setCardType(TCardType.MISC_EQUIPMENT);
        card.setResourceIdentifier("Canine K9-1 / Handler Smith");
        String json = mapper.writeValueAsString(card);
        TCard loaded = mapper.readValue(json, TCard.class);
        assertEquals(TCardType.MISC_EQUIPMENT, loaded.getCardType());
    }

    @Test
    void tCardDroneMapsToAircraft() throws IOException {
        TCard card = new TCard();
        card.setCardType(TCardType.AIRCRAFT);
        card.setResourceIdentifier("Drone Unit-3");
        String json = mapper.writeValueAsString(card);
        TCard loaded = mapper.readValue(json, TCard.class);
        assertEquals(TCardType.AIRCRAFT, loaded.getCardType());
    }

    @Test
    void appDataTCardsRoundTrip() throws IOException {
        AppData data = new AppData();
        TCard card = new TCard();
        card.setPersonName("Alice");
        card.setHomeState("OR");
        data.setTCards(List.of(card));

        String json = mapper.writeValueAsString(data);
        AppData loaded = mapper.readValue(json, AppData.class);

        assertEquals(1, loaded.getTCards().size());
        assertEquals("Alice", loaded.getTCards().get(0).getPersonName());
        assertEquals("OR", loaded.getTCards().get(0).getHomeState());
    }

    @Test
    void appDataTCardsDefaultsToEmptyListOnLoad() throws IOException {
        // Simulate old file format without tCards field.
        String json = "{\"schemaVersion\":2,\"incidentContext\":{}}";
        AppData loaded = mapper.readValue(json, AppData.class);
        assertNotNull(loaded.getTCards());
        assertTrue(loaded.getTCards().isEmpty());
    }

    // -----------------------------------------------------------------------
    // Expanded OrganizationalChart
    // -----------------------------------------------------------------------

    @Test
    void orgChartNewPositionsRoundTrip() throws IOException {
        OrganizationalChart chart = new OrganizationalChart();
        chart.setSafetyOfficerName("Safety Sam");
        chart.setSafetyOfficerRadio("Tac-1");
        chart.setPublicInformationOfficerName("PIO Pat");
        chart.setPublicInformationOfficerPhone("555-1111");
        chart.setLiaisonOfficerName("Liaison Lee");
        chart.setLiaisonOfficerPhone("555-2222");
        chart.setPlanningSectionChiefName("Planning Pete");
        chart.setPlanningSectionChiefRadio("Tac-2");
        chart.setLogisticsSectionChiefName("Logistics Lou");
        chart.setLogisticsSectionChiefRadio("Tac-3");
        chart.setFinanceAdminSectionChiefName("Finance Fran");
        chart.setFinanceAdminSectionChiefPhone("555-3333");
        chart.setDocumentationUnitLeaderName("Doc Dan");
        chart.setDocumentationUnitLeaderPhone("555-4444");

        String json = mapper.writeValueAsString(chart);
        OrganizationalChart loaded = mapper.readValue(json, OrganizationalChart.class);

        assertEquals("Safety Sam", loaded.getSafetyOfficerName());
        assertEquals("Tac-1", loaded.getSafetyOfficerRadio());
        assertEquals("PIO Pat", loaded.getPublicInformationOfficerName());
        assertEquals("555-1111", loaded.getPublicInformationOfficerPhone());
        assertEquals("Liaison Lee", loaded.getLiaisonOfficerName());
        assertEquals("Planning Pete", loaded.getPlanningSectionChiefName());
        assertEquals("Logistics Lou", loaded.getLogisticsSectionChiefName());
        assertEquals("Finance Fran", loaded.getFinanceAdminSectionChiefName());
        assertEquals("Doc Dan", loaded.getDocumentationUnitLeaderName());
        assertEquals("555-4444", loaded.getDocumentationUnitLeaderPhone());
    }

    @Test
    void orgChartNewPositionsDefaultToEmpty() {
        OrganizationalChart chart = new OrganizationalChart();
        assertEquals("", chart.getSafetyOfficerName());
        assertEquals("", chart.getSafetyOfficerRadio());
        assertEquals("", chart.getSafetyOfficerPhone());
        assertEquals("", chart.getPublicInformationOfficerName());
        assertEquals("", chart.getLiaisonOfficerName());
        assertEquals("", chart.getPlanningSectionChiefName());
        assertEquals("", chart.getLogisticsSectionChiefName());
        assertEquals("", chart.getFinanceAdminSectionChiefName());
        assertEquals("", chart.getDocumentationUnitLeaderName());
    }

    // -----------------------------------------------------------------------
    // Persistence round-trip including new fields
    // -----------------------------------------------------------------------

    @Test
    void fullAppDataWithNewFieldsRoundTripsViaLocalRepository() throws Exception {
        Path tmpDir = Files.createTempDirectory("icsforms-mode-test");
        LocalRepository repo = new LocalRepository(tmpDir.resolve("incident.json"));

        AppData data = new AppData();
        data.setIncidentMode(IncidentMode.GENERIC);

        TCard card = new TCard();
        card.setCardType(TCardType.PERSONNEL);
        card.setPersonName("Bob");
        card.setHomeState("WA");
        data.setTCards(List.of(card));

        OrganizationalChart chart = data.getOrganizationalChart();
        chart.setSafetyOfficerName("Officer Olivia");
        chart.setDocumentationUnitLeaderName("Doc Dylan");

        repo.save(data);
        AppData loaded = repo.loadOrDefault();

        assertEquals(IncidentMode.GENERIC, loaded.getIncidentMode());
        assertEquals(1, loaded.getTCards().size());
        assertEquals("Bob", loaded.getTCards().get(0).getPersonName());
        assertEquals("WA", loaded.getTCards().get(0).getHomeState());
        assertEquals("Officer Olivia", loaded.getOrganizationalChart().getSafetyOfficerName());
        assertEquals("Doc Dylan", loaded.getOrganizationalChart().getDocumentationUnitLeaderName());
    }

    @Test
    void orgChartLegacyContactFieldDeserializesToPhone() throws IOException {
        // Old JSON written before the radio/phone split used a single "contact" field per position.
        // @JsonAlias annotations must migrate those values into the new *Phone fields.
        String legacyJson = """
                {
                  "safetyOfficerName": "Sam Safety",
                  "safetyOfficerContact": "555-9999",
                  "publicInformationOfficerName": "PIO Pat",
                  "publicInformationOfficerContact": "555-8888"
                }""";
        OrganizationalChart chart = mapper.readValue(legacyJson, OrganizationalChart.class);
        assertEquals("Sam Safety", chart.getSafetyOfficerName());
        assertEquals("555-9999", chart.getSafetyOfficerPhone());
        assertEquals("", chart.getSafetyOfficerRadio());
        assertEquals("PIO Pat", chart.getPublicInformationOfficerName());
        assertEquals("555-8888", chart.getPublicInformationOfficerPhone());
    }

    @Test
    void iapPhaseRoundTrips() throws IOException {
        AppData data = new AppData();
        data.setIapPhase(org.sarmanagement.icsforms.model.IapPhase.DURING_OP);
        String json = mapper.writeValueAsString(data);
        AppData loaded = mapper.readValue(json, AppData.class);
        assertEquals(org.sarmanagement.icsforms.model.IapPhase.DURING_OP, loaded.getIapPhase());
    }

    @Test
    void iapPhaseDefaultsToPreOpForNewData() {
        AppData data = new AppData();
        assertEquals(org.sarmanagement.icsforms.model.IapPhase.PRE_OP, data.getIapPhase());
    }
}

