package org.sarmanagement.icsforms.pdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.IncidentContext;
import org.sarmanagement.icsforms.model.SarTaskAssignment;
import org.sarmanagement.icsforms.model.SarTaskResource;
import org.sarmanagement.icsforms.model.TCard;
import org.sarmanagement.icsforms.model.TCardType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class Ics205aPdfRendererTest {

    @Test
    void renderProducesCommunicationsListSortedByLastName() throws Exception {
        AppData data = new AppData();
        IncidentContext context = data.getIncidentContext();
        context.setIncidentName("River Search");
        context.setOperationalPeriodStart(LocalDateTime.of(2026, 9, 13, 8, 0));
        context.setOperationalPeriodEnd(LocalDateTime.of(2026, 9, 13, 20, 0));
        context.setCurrentUser("Pat Planner");
        context.setCurrentUserPositionTitle("Communications Unit Leader");

        SarTaskAssignment task = new SarTaskAssignment();
        task.setAssignmentId("A1");
        task.setAssignmentTeamNumber("A-1");
        task.setResourceIdentifier("Ground-1");
        task.setLeader("Zoey Zulu");
        task.setLeaderRole("Task Leader");
        task.setAssignment("Search shoreline");

        TCard zulu = new TCard();
        zulu.setCardType(TCardType.PERSONNEL);
        zulu.setPersonName("Zoey Zulu");
        zulu.setRadioChannel("Tac 4");
        zulu.setPhoneNumber("555-4000");
        zulu.setSourceRef("sar:A1:leader");

        TCard alpha = new TCard();
        alpha.setCardType(TCardType.PERSONNEL);
        alpha.setPersonName("Amy Alpha");
        alpha.setPhoneNumber("555-1000");
        alpha.setSourceRef("org:safetyOfficer");

        SarTaskResource leader = new SarTaskResource();
        leader.setName("Zoey Zulu");
        leader.setResourceId(zulu.getResourceId());
        task.setResourcesAssigned(List.of(leader));

        data.setSarTaskAssignments(List.of(task));
        data.setTCards(List.of(zulu, alpha));

        Path output = Files.createTempFile("ics-205a-", ".pdf");
        new Ics205aPdfRenderer().render(data, output);

        try (PDDocument pdf = Loader.loadPDF(output.toFile())) {
            String text = new PDFTextStripper().getText(pdf);
            assertTrue(text.contains("ICS 205A COMMUNICATIONS LIST"));
            assertTrue(text.contains("1. Incident Name"));
            assertTrue(text.contains("2. Operational Period"));
            assertTrue(text.contains("3. Basic Local Communications Information"));
            assertTrue(text.contains("4. Prepared By"));
            assertTrue(text.contains("River Search"));
            assertTrue(text.contains("Pat Planner"));
            assertTrue(text.contains("Communications Unit Leader"));
            assertTrue(text.contains("Amy Alpha"));
            assertTrue(text.contains("Zoey Zulu"));
            assertTrue(text.indexOf("Amy Alpha") < text.indexOf("Zoey Zulu"));
            assertTrue(text.contains("Tac 4"));
            assertTrue(text.contains("555-4000"));
        }
    }
}
