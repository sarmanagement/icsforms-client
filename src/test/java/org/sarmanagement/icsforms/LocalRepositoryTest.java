package org.sarmanagement.icsforms;

import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.Ics204Form;
import org.sarmanagement.icsforms.model.IncidentContext;
import org.sarmanagement.icsforms.persistence.LocalRepository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class LocalRepositoryTest {

    @Test
    void roundTripPersistsAndLoads() throws Exception {
        Path tempFile = Files.createTempFile("icsforms", ".json");
        LocalRepository repository = new LocalRepository(tempFile);
        AppData input = new AppData();
        input.setIncidentContext(new IncidentContext("Test", LocalDateTime.parse("2026-01-01T00:00:00"), LocalDateTime.parse("2026-01-01T12:00:00"), "user"));

        repository.save(input);
        AppData loaded = repository.loadOrDefault();

        assertNotNull(loaded.getIncidentContext());
        assertEquals("Test", loaded.getIncidentContext().getIncidentName());
    }

    @Test
    void requiresSupervisorWhenBranchDivisionOrGroupSet() {
        Ics204Form form = new Ics204Form();
        form.setBranch("A");
        assertTrue(form.requiresBranchDivisionGroupSupervisor());
        assertFalse(form.hasRequiredSupervisorWhenNeeded());
        form.setDivisionGroupSupervisorName("Supervisor");
        assertTrue(form.hasRequiredSupervisorWhenNeeded());
    }
}
