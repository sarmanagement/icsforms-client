package org.sarmanagement.icsforms;

import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.CommunicationEntry;
import org.sarmanagement.icsforms.model.Ics202Form;
import org.sarmanagement.icsforms.model.Ics204Form;
import org.sarmanagement.icsforms.model.IncidentContext;
import org.sarmanagement.icsforms.model.ResourceAssignment;
import org.sarmanagement.icsforms.model.SarTaskAssignment;
import org.sarmanagement.icsforms.persistence.LocalRepository;
import org.sarmanagement.icsforms.pdf.Ics202PdfRenderer;
import org.sarmanagement.icsforms.pdf.Ics204PdfRenderer;
import org.sarmanagement.icsforms.pdf.PdfExportService;
import org.sarmanagement.icsforms.validation.IncidentValidator;
import org.sarmanagement.icsforms.validation.ValidationMessage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Focused tests for persistence, validation, SAR linkage, and PDF smoke exports.
 */
class LocalRepositoryTest {

    /**
     * Verifies that workspace JSON preserves schema version, shared context, and linked assignment data.
     *
     * @throws Exception when the temp file cannot be used.
     */
    @Test
    void roundTripPersistsAndLoads() throws Exception {
        Path tempDir = Files.createTempDirectory("icsforms");
        Path tempFile = tempDir.resolve("incident.json");
        LocalRepository repository = new LocalRepository(tempFile);
        AppData input = sampleData();

        repository.save(input);
        AppData loaded = repository.loadOrDefault();

        assertNotNull(loaded.getIncidentContext());
        assertEquals(AppData.CURRENT_SCHEMA_VERSION, loaded.getSchemaVersion());
        assertEquals("Test Incident", loaded.getIncidentContext().getIncidentName());
        assertEquals(1, loaded.getForm204().getResourcesAssigned().size());
        assertEquals("assign-1", loaded.getForm204().getResourcesAssigned().get(0).getAssignmentId());
    }

    /**
     * Verifies corrupt primary files recover gracefully from the last known-good backup.
     *
     * @throws Exception when temp file setup fails.
     */
    @Test
    void corruptPrimaryFallsBackToBackup() throws Exception {
        Path tempDir = Files.createTempDirectory("icsforms");
        Path tempFile = tempDir.resolve("incident.json");
        LocalRepository repository = new LocalRepository(tempFile);
        repository.save(sampleData());
        Files.writeString(tempFile, "not-json");

        AppData loaded = repository.loadOrDefault();

        assertEquals("Test Incident", loaded.getIncidentContext().getIncidentName());
    }

    /**
     * Verifies conditional supervisor validation and date ordering checks.
     */
    @Test
    void validatorReportsConditionalAndOrderingFailures() {
        AppData data = sampleData();
        data.getIncidentContext().setOperationalPeriodStart(LocalDateTime.parse("2026-01-02T12:00:00"));
        data.getIncidentContext().setOperationalPeriodEnd(LocalDateTime.parse("2026-01-01T12:00:00"));
        data.getForm204().setDivision("Alpha");
        data.getForm204().setDivisionGroupSupervisorName("");
        data.getForm204().setDivisionGroupSupervisorContact("");

        List<ValidationMessage> messages = new IncidentValidator().validate(data);

        assertTrue(messages.stream().anyMatch(message -> message.field().equals("operationalPeriod")));
        assertTrue(messages.stream().anyMatch(message -> message.field().equals("ics204.divisionGroupSupervisorName")));
        assertTrue(messages.stream().anyMatch(message -> message.field().equals("ics204.divisionGroupSupervisorContact")));
    }

    /**
     * Verifies ICS 204 resource linkage creates a matching SAR scaffold entry with carried-over context.
     */
    @Test
    void resourceAssignmentMapsToSarScaffold() {
        AppData data = sampleData();
        ResourceAssignment resource = data.getForm204().getResourcesAssigned().get(0);

        SarTaskAssignment task = SarTaskAssignment.fromResourceAssignment(resource, data.getIncidentContext(), data.getForm204());

        assertEquals(resource.getAssignmentId(), task.getAssignmentId());
        assertEquals("Search trail segment", task.getAssignment());
        assertEquals("Division A", task.getDivision());
        assertEquals("Tac 1", task.getCommunications().get(0).getPrimaryContact());
    }

    /**
     * Verifies structured PDF exports are generated and non-empty.
     *
     * @throws Exception when temp file setup fails.
     */
    @Test
    void pdfExportsAreGenerated() throws Exception {
        AppData data = sampleData();
        PdfExportService exportService = new PdfExportService(new Ics202PdfRenderer(), new Ics204PdfRenderer());
        Path outputDir = Files.createTempDirectory("icsforms-pdf");

        Path pdf202 = exportService.exportSelected("ICS 202", data, outputDir);
        Path pdf204 = exportService.exportSelected("ICS 204", data, outputDir);

        assertTrue(Files.exists(pdf202));
        assertTrue(Files.size(pdf202) > 0);
        assertTrue(Files.exists(pdf204));
        assertTrue(Files.size(pdf204) > 0);
    }

    /**
     * Creates a deterministic sample document for tests.
     *
     * @return sample incident document.
     */
    private AppData sampleData() {
        IncidentContext context = new IncidentContext("Test Incident", LocalDateTime.parse("2026-01-01T00:00:00"), LocalDateTime.parse("2026-01-01T12:00:00"), "Planner");

        Ics202Form form202 = new Ics202Form();
        form202.setObjectives(List.of("Protect life", "Stabilize scene"));
        form202.setCommandEmphasis("Responder accountability");
        form202.setSituationalAwareness("Wind shift expected.");
        form202.setSiteSafetyPlanRequired(true);
        form202.setIncidentActionPlanAttachments(List.of("ICS 203", "Map packet"));
        form202.setPreparedByName("Planner");
        form202.setPreparedByPositionTitle("Planning Section Chief");
        form202.setPreparedBySignature("Planner Sig");
        form202.setApprovedByIncidentCommanderName("IC Name");
        form202.setApprovedBySignature("IC Sig");
        form202.setApprovedDateTime(LocalDateTime.parse("2026-01-01T01:00:00"));
        form202.setIapPage("1");

        ResourceAssignment resource = new ResourceAssignment();
        resource.setAssignmentId("assign-1");
        resource.setResourceIdentifier("Team 1");
        resource.setLeader("Leader A");
        resource.setNumberOfPersons(4);
        resource.setContact("555-0101");
        resource.setReportingLocation("ICP");
        resource.setSpecialEquipment("ATV");
        resource.setSupplies("Medical kit");
        resource.setRemarks("Check in hourly");
        resource.setNotes("Bring maps");
        resource.setAssignment("Search trail segment");

        CommunicationEntry communicationEntry = new CommunicationEntry();
        communicationEntry.setNameOrFunction("Team 1 Lead");
        communicationEntry.setPrimaryContact("Tac 1");

        Ics204Form form204 = new Ics204Form();
        form204.setDivision("Division A");
        form204.setOperationsSectionChiefName("Ops Chief");
        form204.setOperationsSectionChiefContact("555-0199");
        form204.setDivisionGroupSupervisorName("Supervisor");
        form204.setDivisionGroupSupervisorContact("Tac 2");
        form204.setResourcesAssigned(List.of(resource));
        form204.setCommunications(List.of(communicationEntry));
        form204.setSharedWorkAssignment("Shared assignment");
        form204.setSpecialInstructions("Maintain radio discipline");
        form204.setPreparedByName("Ops Planner");
        form204.setPreparedByPositionTitle("Operations");
        form204.setPreparedBySignature("Ops Sig");
        form204.setPreparedDateTime(LocalDateTime.parse("2026-01-01T02:00:00"));
        form204.setIapPage("2");

        AppData data = new AppData(context, form202, form204, List.of());
        data.setSchemaVersion(AppData.CURRENT_SCHEMA_VERSION);
        return data;
    }
}
