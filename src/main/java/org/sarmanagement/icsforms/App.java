package org.sarmanagement.icsforms;

import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.Ics202Form;
import org.sarmanagement.icsforms.model.Ics204Form;
import org.sarmanagement.icsforms.model.IncidentContext;
import org.sarmanagement.icsforms.model.SarTaskAssignment;
import org.sarmanagement.icsforms.persistence.LocalRepository;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

public class App {
    public static void main(String[] args) {
        IncidentContext context = new IncidentContext(
                "Sample Incident",
                LocalDateTime.now(),
                LocalDateTime.now().plusHours(12),
                "Operator"
        );

        Ics202Form form202 = new Ics202Form();
        form202.setPreparedByName("Operator");

        Ics204Form form204 = new Ics204Form();
        form204.setOperationsSectionChiefName("Operator");

        SarTaskAssignment sarTask = SarTaskAssignment.fromResourceAssignment(null, context.getIncidentName());

        AppData data = new AppData(context, form202, form204, List.of(sarTask));
        LocalRepository repository = new LocalRepository(Path.of("icsforms-local.json"));
        repository.save(data);

        System.out.println("ICS forms scaffold initialized. Data persisted to icsforms-local.json");
    }
}
