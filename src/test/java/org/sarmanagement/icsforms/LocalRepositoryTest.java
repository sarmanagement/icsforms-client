package org.sarmanagement.icsforms;

import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.CommunicationEntry;
import org.sarmanagement.icsforms.model.Ics202Form;
import org.sarmanagement.icsforms.model.Ics204Form;
import org.sarmanagement.icsforms.model.IncidentContext;
import org.sarmanagement.icsforms.model.OrganizationalChart;
import org.sarmanagement.icsforms.model.ResourceAssignment;
import org.sarmanagement.icsforms.model.SarTaskAssignment;
import org.sarmanagement.icsforms.persistence.LocalRepository;
import org.sarmanagement.icsforms.pdf.Ics202PdfRenderer;
import org.sarmanagement.icsforms.pdf.Ics204PdfRenderer;
import org.sarmanagement.icsforms.pdf.PdfExportService;
import org.sarmanagement.icsforms.pdf.SarTaskAssignmentPdfRenderer;
import org.sarmanagement.icsforms.ui.AppController;
import org.sarmanagement.icsforms.validation.IncidentValidator;
import org.sarmanagement.icsforms.validation.ValidationMessage;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

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
        assertEquals(List.of("IC One", "IC Two"), loaded.getOrganizationalChart().getIncidentCommanders());
        assertEquals("Ops Chief", loaded.getOrganizationalChart().getOperationsSectionChiefName());
        assertEquals(1, loaded.getForm204().getResourcesAssigned().size());
        assertEquals("assign-1", loaded.getForm204().getResourcesAssigned().get(0).getAssignmentId());
        assertEquals("A-1", loaded.getForm204().getResourcesAssigned().get(0).getAssignmentTeamNumber());
        assertEquals(Ics204Form.MANAGEMENT_DIVISION, loaded.getForm204().getManagementContext());
        assertEquals("Map-42", loaded.getIncidentContext().getTaskMap());
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
        data.getForm204().setManagementContext(Ics204Form.MANAGEMENT_DIVISION);
        data.getForm204().setDivision("Alpha");
        data.getForm204().setDivisionGroupSupervisorName("");
        data.getForm204().setDivisionGroupSupervisorContact("");

        List<ValidationMessage> messages = new IncidentValidator().validate(data);

        assertTrue(messages.stream().anyMatch(message -> message.field().equals("operationalPeriod")));
        assertTrue(messages.stream().anyMatch(message -> message.field().equals("ics204.divisionGroupSupervisorName")));
        assertTrue(messages.stream().anyMatch(message -> message.field().equals("ics204.divisionGroupSupervisorContact")));
    }

    /**
     * Verifies branch selection requires branch director data instead of division/group supervisor data.
     */
    @Test
    void validatorUsesBranchSpecificSupervisorFields() {
        AppData data = sampleData();
        data.getForm204().setManagementContext(Ics204Form.MANAGEMENT_BRANCH);
        data.getForm204().setBranch("Branch 1");
        data.getForm204().setDivision("");
        data.getForm204().setBranchDirectorName("");
        data.getForm204().setBranchDirectorContact("");

        List<ValidationMessage> messages = new IncidentValidator().validate(data);

        assertTrue(messages.stream().anyMatch(message -> message.field().equals("ics204.branchDirectorName")));
        assertTrue(messages.stream().anyMatch(message -> message.field().equals("ics204.branchDirectorContact")));
        assertFalse(messages.stream().anyMatch(message -> message.field().equals("ics204.divisionGroupSupervisorName")));
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
        assertEquals("A-1", task.getAssignmentTeamNumber());
        assertEquals("Leader/Handler", task.getLeaderRole());
        assertEquals("Map-42", task.getTaskMap());
        assertEquals("Tac 1", task.getCommunications().get(0).getPrimaryContact());
        assertEquals("Team 1 Lead", task.getCommunications().get(0).getName());
        assertEquals("Medical", task.getCommunications().get(0).getFunction());
    }

    /**
     * Verifies syncing SAR tasks preserves debrief details while refreshing shared assignment data.
     *
     * @throws Exception when temp file setup fails.
     */
    @Test
    void syncSarTasksPreservesDebriefData() throws Exception {
        Path tempDir = Files.createTempDirectory("icsforms");
        AppController controller = new AppController(sampleData(), new LocalRepository(tempDir.resolve("incident.json")),
                new PdfExportService(new Ics202PdfRenderer(), new Ics204PdfRenderer(), new SarTaskAssignmentPdfRenderer()),
                new IncidentValidator());

        SarTaskAssignment task = controller.getData().getSarTaskAssignments().get(0);
        task.setDebriefNotes("Completed assignment and located clues.");
        task.setAssignment("Detailed segment instructions");
        task.setVehicleMiles("14");
        controller.getData().getIncidentContext().setTaskMap("Map-99");
        controller.syncSarTasks();

        SarTaskAssignment synced = controller.getData().getSarTaskAssignments().get(0);
        assertEquals("Completed assignment and located clues.", synced.getDebriefNotes());
        assertEquals("Detailed segment instructions", synced.getAssignment());
        assertEquals("14", synced.getVehicleMiles());
        assertEquals("Map-99", synced.getTaskMap());
        assertEquals("A-1", synced.getAssignmentTeamNumber());
    }

    /**
     * Verifies the org chart tab data links bidirectionally with ICS 202/204 and shared preparer roles.
     *
     * @throws Exception when temp file setup fails.
     */
    @Test
    void organizationalChartLinksAcrossTabs() throws Exception {
        Path tempDir = Files.createTempDirectory("icsforms");
        AppController controller = new AppController(sampleData(), new LocalRepository(tempDir.resolve("incident.json")),
                new PdfExportService(new Ics202PdfRenderer(), new Ics204PdfRenderer(), new SarTaskAssignmentPdfRenderer()), new IncidentValidator());

        controller.getData().getForm202().setApprovedByIncidentCommanderName("IC Alpha; IC Bravo");
        controller.synchronizeLinkedFields(AppController.LinkSource.ICS202);
        assertEquals(List.of("IC Alpha", "IC Bravo"), controller.getData().getOrganizationalChart().getIncidentCommanders());

        controller.getData().getIncidentContext().setCurrentUser("Ops Prep");
        controller.getData().getIncidentContext().setCurrentUserPositionTitle("Operations Section Chief");
        controller.synchronizeLinkedFields(AppController.LinkSource.SHARED);
        assertEquals("Ops Prep", controller.getData().getOrganizationalChart().getOperationsSectionChiefName());
        assertEquals("Ops Prep", controller.getData().getForm204().getOperationsSectionChiefName());
    }

    /**
     * Verifies structured PDF exports are generated and non-empty.
     *
     * @throws Exception when temp file setup fails.
     */
    @Test
    void pdfExportsAreGenerated() throws Exception {
        AppData data = sampleData();
        PdfExportService exportService = new PdfExportService(new Ics202PdfRenderer(), new Ics204PdfRenderer(), new SarTaskAssignmentPdfRenderer());
        Path outputDir = Files.createTempDirectory("icsforms-pdf");

        Path pdf202 = exportService.exportSelected("ICS 202", data, outputDir);
        Path pdf204 = exportService.exportSelected("ICS 204", data, outputDir);
        Path sarPdf = exportService.exportSelected("SAR Task Assignment", data, outputDir);

        assertTrue(Files.exists(pdf202));
        assertTrue(Files.size(pdf202) > 0);
        assertTrue(Files.exists(pdf204));
        assertTrue(Files.size(pdf204) > 0);
        assertTrue(Files.readAllBytes(pdf202).length > 0);
        assertTrue(Files.exists(sarPdf));
        assertTrue(Files.size(sarPdf) > 0);

        try (PDDocument pdf = Loader.loadPDF(pdf202.toFile())) {
            String text = new PDFTextStripper().getText(pdf);
            assertTrue(text.contains("ICS 202 INCIDENT OBJECTIVES"));
            assertTrue(text.contains("1. Incident Name"));
            assertTrue(text.contains("2. Operational Period"));
            assertTrue(text.contains("8. Approved By Incident Commander"));
            assertTrue(text.contains("IAP Page: 1"));
            assertTrue(text.contains("Date/Time:"));
        }

        try (PDDocument pdf = Loader.loadPDF(pdf204.toFile())) {
            String text = new PDFTextStripper().getText(pdf);
            assertTrue(text.contains("ICS 204 ASSIGNMENT LIST"));
            assertTrue(text.contains("4. Operations Personnel"));
            assertTrue(text.contains("3. Division"));
            assertTrue(text.contains("Division Supervisor"));
            assertTrue(text.contains("5. Resources Assigned"));
            assertTrue(text.contains("Resource"));
            assertTrue(text.contains("Leader"));
            assertTrue(text.contains("Contact"));
            assertTrue(text.contains("Reporting Location"));
            assertTrue(text.contains("9. Prepared By"));
            assertTrue(text.contains("IAP Page: 2"));
        }

        try (PDDocument pdf = Loader.loadPDF(sarPdf.toFile())) {
            String text = new PDFTextStripper().getText(pdf);
            assertEquals(2, pdf.getNumberOfPages());
            assertTrue(text.contains("SAR TASK ASSIGNMENT FORM"));
            assertTrue(text.contains("1. Incident/Mission Name"));
            assertTrue(text.contains("3. Assignment/Team Number"));
            assertTrue(text.contains("A-1"));
            assertTrue(text.contains("Leader/Handler"));
            assertTrue(text.contains("8. Task Map"));
            assertTrue(text.contains("Map-42"));
            assertTrue(text.contains("15. Debriefing"));
        }
    }

    /**
     * Creates a deterministic sample document for tests.
     *
     * @return sample incident document.
     */
    private AppData sampleData() {
        IncidentContext context = new IncidentContext("Test Incident", LocalDateTime.parse("2026-01-01T00:00:00"), LocalDateTime.parse("2026-01-01T12:00:00"), "Planner", "Planning Section Chief");
        context.setTaskMap("Map-42");

        Ics202Form form202 = new Ics202Form();
        form202.setObjectives(List.of("Protect life", "Stabilize scene"));
        form202.setCommandEmphasis("Responder accountability");
        form202.setSituationalAwareness("Wind shift expected.");
        form202.setSiteSafetyPlanRequired(true);
        form202.setIncidentActionPlanAttachments(List.of("ICS 203", "Map packet"));
        form202.setApprovedByIncidentCommanderName("IC Name");
        form202.setApprovedDateTime(LocalDateTime.parse("2026-01-01T01:00:00"));
        form202.setIapPage("1");

        ResourceAssignment resource = new ResourceAssignment();
        resource.setAssignmentId("assign-1");
        resource.setAssignmentTeamNumber("A-1");
        resource.setResourceIdentifier("Team 1");
        resource.setLeaderRole("Leader/Handler");
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
        communicationEntry.setName("Team 1 Lead");
        communicationEntry.setFunction("Medical");
        communicationEntry.setPrimaryContact("Tac 1");

        Ics204Form form204 = new Ics204Form();
        form204.setManagementContext(Ics204Form.MANAGEMENT_DIVISION);
        form204.setDivision("Division A");
        form204.setOperationsSectionChiefName("Ops Chief");
        form204.setOperationsSectionChiefContact("555-0199");
        form204.setDivisionGroupSupervisorName("Supervisor");
        form204.setDivisionGroupSupervisorContact("Tac 2");
        form204.setResourcesAssigned(List.of(resource));
        form204.setCommunications(List.of(communicationEntry));
        form204.setSharedWorkAssignment("Shared assignment");
        form204.setSpecialInstructions("Maintain radio discipline");
        form204.setPreparedByName("Planner");
        form204.setPreparedByPositionTitle("Planning Section Chief");
        form204.setPreparedDateTime(LocalDateTime.parse("2026-01-01T02:00:00"));
        form204.setIapPage("2");

        SarTaskAssignment task = SarTaskAssignment.fromResourceAssignment(resource, context, form204);
        task.setPreparedDateTime(form204.getPreparedDateTime());
        task.setDebriefNotes("Initial debrief notes");
        AppData data = new AppData(context, form202, form204, List.of(task));
        OrganizationalChart organizationalChart = new OrganizationalChart();
        organizationalChart.setIncidentCommanders(List.of("IC One", "IC Two"));
        organizationalChart.setOperationsSectionChiefName("Ops Chief");
        data.setOrganizationalChart(organizationalChart);
        data.setSchemaVersion(AppData.CURRENT_SCHEMA_VERSION);
        return data;
    }
}
