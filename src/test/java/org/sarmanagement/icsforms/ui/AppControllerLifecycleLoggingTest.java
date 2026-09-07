package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.model.ActivityLogScope;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.Ics214Form;
import org.sarmanagement.icsforms.model.SarTaskAssignment;
import org.sarmanagement.icsforms.persistence.LocalRepository;
import org.sarmanagement.icsforms.pdf.PdfExportService;
import org.sarmanagement.icsforms.validation.IncidentValidator;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppControllerLifecycleLoggingTest {

    @Test
    void taskStatusTransitionAddsIcpCommunicationsLogEntry() throws Exception {
        Path tempFile = Files.createTempDirectory("icsforms-lifecycle").resolve("incident.json");
        AppController controller = new AppController(
                new AppData(),
                new LocalRepository(tempFile),
                new PdfExportService(),
                new IncidentValidator());

        SarTaskAssignment task = new SarTaskAssignment();
        task.setAssignmentId("a-1");
        task.setAssignmentTeamNumber("T-1");
        task.setResourceIdentifier("RES-1");

        controller.recordTaskLifecycleTransition(task, "assigned - enroute to assignment");

        assertEquals(1, controller.getData().getForm204().getCommunications().size());
        Ics214Form icpLog = controller.getData().getActivityLogs().stream()
                .filter(log -> log.getLogScope() == ActivityLogScope.ICP)
                .findFirst()
                .orElse(null);
        assertTrue(icpLog != null && !icpLog.getActivityLog().isEmpty());
        assertTrue(icpLog.getActivityLog().get(0).getNotableActivity().contains("assigned - enroute to assignment"));
    }
}

