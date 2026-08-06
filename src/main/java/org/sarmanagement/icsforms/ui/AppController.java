package org.sarmanagement.icsforms.ui;

import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.Ics204Form;
import org.sarmanagement.icsforms.model.IncidentContext;
import org.sarmanagement.icsforms.model.OrganizationalChart;
import org.sarmanagement.icsforms.model.ResourceAssignment;
import org.sarmanagement.icsforms.model.SarTaskAssignment;
import org.sarmanagement.icsforms.persistence.LocalRepository;
import org.sarmanagement.icsforms.pdf.PdfExportService;
import org.sarmanagement.icsforms.validation.IncidentValidator;
import org.sarmanagement.icsforms.validation.ValidationMessage;

import javax.swing.Timer;
import java.io.IOException;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Coordinates UI edits, autosave, validation, and linked SAR task synchronization.
 */
public class AppController {
    private static final int AUTOSAVE_DELAY_MS = 750;

    private final LocalRepository repository;
    private final PdfExportService exportService;
    private final IncidentValidator validator;
    private final Timer autosaveTimer;
    private AppData data;
    private boolean dirty;
    private LinkSource activeLinkSource = LinkSource.NONE;

    /**
     * Identifies which tab last edited a shared linked role field.
     */
    public enum LinkSource {
        NONE,
        SHARED,
        ORG_CHART,
        ICS202,
        ICS204
    }

    /**
     * Creates a controller for the desktop application.
     *
     * @param data initial incident document.
     * @param repository local repository.
     * @param exportService PDF export service.
     * @param validator validation service.
     */
    public AppController(AppData data, LocalRepository repository, PdfExportService exportService, IncidentValidator validator) {
        this.data = data == null ? createDefaultData() : data;
        ensureDefaults(this.data);
        this.repository = repository;
        this.exportService = exportService;
        this.validator = validator;
        this.autosaveTimer = new Timer(AUTOSAVE_DELAY_MS, event -> save());
        this.autosaveTimer.setRepeats(false);
        syncSarTasks();
    }

    /**
     * Returns the active incident document.
     *
     * @return active document.
     */
    public AppData getData() {
        return data;
    }

    /**
     * Replaces the active document and synchronizes linked defaults.
     *
     * @param data new active document.
     */
    public void setData(AppData data) {
        this.data = data == null ? createDefaultData() : data;
        ensureDefaults(this.data);
        syncSarTasks();
        dirty = false;
        activeLinkSource = LinkSource.NONE;
    }

    /**
     * Marks the document dirty and schedules autosave.
     */
    public void markDirty() {
        markDirty(LinkSource.NONE);
    }

    /**
     * Marks the document dirty, synchronizes linked fields, and schedules autosave.
     *
     * @param source source tab for linked role values.
     */
    public void markDirty(LinkSource source) {
        dirty = true;
        synchronizeLinkedFields(source);
        syncSarTasks();
        autosaveTimer.restart();
    }

    /**
     * Saves the active document immediately.
     */
    public void save() {
        save(activeLinkSource);
    }

    /**
     * Saves the active document immediately using the current authoritative linked-field source.
     *
     * @param source source tab for linked role values.
     */
    public void save(LinkSource source) {
        synchronizeLinkedFields(source);
        syncSarTasks();
        repository.save(data);
        dirty = false;
    }

    /**
     * Saves the active document to a different location.
     *
     * @param path destination file path.
     */
    public void saveAs(Path path) {
        saveAs(path, activeLinkSource);
    }

    /**
     * Saves the active document to a different location using the current authoritative linked-field source.
     *
     * @param path destination file path.
     * @param source source tab for linked role values.
     */
    public void saveAs(Path path, LinkSource source) {
        synchronizeLinkedFields(source);
        syncSarTasks();
        new LocalRepository(path).save(data);
        dirty = false;
    }

    /**
     * Loads a document from a specific path.
     *
     * @param path source file path.
     */
    public void open(Path path) {
        setData(new LocalRepository(path).loadOrDefault());
    }

    /**
     * Resets the editor to a fresh incident document.
     */
    public void newDocument() {
        setData(createDefaultData());
        markDirty();
    }

    /**
     * Validates the active document.
     *
     * @return validation messages.
     */
    public List<ValidationMessage> validate() {
        return validate(activeLinkSource);
    }

    /**
     * Validates the active document using the current authoritative linked-field source.
     *
     * @param source source tab for linked role values.
     * @return validation messages.
     */
    public List<ValidationMessage> validate(LinkSource source) {
        synchronizeLinkedFields(source);
        syncSarTasks();
        return validator.validate(data);
    }

    /**
     * Exports one supported form to PDF after validation.
     *
     * @param formKey form identifier.
     * @param outputDirectory destination directory.
     * @return created file path.
     * @throws IOException when export fails.
     */
    public Path exportSelected(String formKey, Path outputDirectory) throws IOException {
        return exportSelected(formKey, outputDirectory, activeLinkSource);
    }

    /**
     * Exports one supported form to PDF after validation.
     *
     * @param formKey form identifier.
     * @param outputDirectory destination directory.
     * @param source source tab for linked role values.
     * @return created file path.
     * @throws IOException when export fails.
     */
    public Path exportSelected(String formKey, Path outputDirectory, LinkSource source) throws IOException {
        synchronizeLinkedFields(source);
        syncSarTasks();
        return exportService.exportSelected(formKey, data, outputDirectory);
    }

    /**
     * Exports all supported forms to PDF after validation.
     *
     * @param outputDirectory destination directory.
     * @return created file paths.
     * @throws IOException when export fails.
     */
    public java.util.Map<String, Path> exportAll(Path outputDirectory) throws IOException {
        return exportAll(outputDirectory, activeLinkSource);
    }

    /**
     * Exports all supported forms to PDF after validation.
     *
     * @param outputDirectory destination directory.
     * @param source source tab for linked role values.
     * @return created file paths.
     * @throws IOException when export fails.
     */
    public java.util.Map<String, Path> exportAll(Path outputDirectory, LinkSource source) throws IOException {
        synchronizeLinkedFields(source);
        syncSarTasks();
        return exportService.exportAll(data, outputDirectory);
    }

    /**
     * Synchronizes shared org-chart-linked fields across the shared tab, org chart, ICS 202, and ICS 204.
     *
     * @param source source tab for linked role values.
     */
    public void synchronizeLinkedFields(LinkSource source) {
        if (source != LinkSource.NONE) {
            activeLinkSource = source;
        }
        IncidentContext context = data.getIncidentContext();
        org.sarmanagement.icsforms.model.Ics202Form form202 = data.getForm202();
        Ics204Form form204 = data.getForm204();
        OrganizationalChart organizationalChart = data.getOrganizationalChart();
        if (context == null || form202 == null || form204 == null || organizationalChart == null) {
            return;
        }

        String preparerName = safe(context.getCurrentUser());
        String preparerTitle = safe(context.getCurrentUserPositionTitle());

        List<String> incidentCommanders = linkedIncidentCommanders(source, form202, organizationalChart);
        if (matchesIncidentCommanderRole(preparerTitle) && !preparerName.isBlank()) {
            incidentCommanders = source == LinkSource.SHARED
                    ? new ArrayList<>(List.of(preparerName))
                    : withAddedUnique(incidentCommanders, preparerName);
        }
        organizationalChart.setIncidentCommanders(incidentCommanders);
        form202.setApprovedByIncidentCommanderName(joinNames(incidentCommanders));

        String operationsSectionChiefName = linkedOperationsSectionChief(source, form204, organizationalChart);
        if (matchesOperationsSectionChiefRole(preparerTitle) && !preparerName.isBlank()) {
            operationsSectionChiefName = preparerName;
        }
        operationsSectionChiefName = safe(operationsSectionChiefName);
        organizationalChart.setOperationsSectionChiefName(operationsSectionChiefName);
        form204.setOperationsSectionChiefName(operationsSectionChiefName);

        form202.setPreparedByName(preparerName);
        form202.setPreparedByPositionTitle(preparerTitle);
        form204.setPreparedByName(preparerName);
        form204.setPreparedByPositionTitle(preparerTitle);
    }

    /**
     * Converts a date to local date/time in the system zone.
     *
     * @param value source date.
     * @return converted local date/time.
     */
    public static LocalDateTime toLocalDateTime(Date value) {
        return value == null ? null : LocalDateTime.ofInstant(value.toInstant(), ZoneId.systemDefault());
    }

    /**
     * Converts a local date/time to a date in the system zone.
     *
     * @param value source local date/time.
     * @return converted date.
     */
    public static Date toDate(LocalDateTime value) {
        return value == null ? new Date() : Date.from(value.atZone(ZoneId.systemDefault()).toInstant());
    }

    /**
     * Returns whether the document has unsaved edits.
     *
     * @return {@code true} when unsaved edits exist.
     */
    public boolean isDirty() {
        return dirty;
    }

    /**
     * Synchronizes SAR task scaffold records from ICS 204 resources using stable assignment IDs.
     */
    public void syncSarTasks() {
        IncidentContext context = data.getIncidentContext();
        Ics204Form form = data.getForm204();
        List<SarTaskAssignment> synced = new ArrayList<>();
        for (ResourceAssignment resource : form.getResourcesAssigned()) {
            synced.add(SarTaskAssignment.fromResourceAssignment(resource, context, form));
        }
        data.setSarTaskAssignments(synced);
    }

    /**
     * Ensures required nested models are present after load and before editing.
     *
     * @param data document to normalize.
     */
    private void ensureDefaults(AppData data) {
        if (data.getIncidentContext() == null) {
            data.setIncidentContext(new IncidentContext());
        }
        if (data.getOrganizationalChart() == null) {
            data.setOrganizationalChart(new OrganizationalChart());
        }
        if (data.getForm202() == null) {
            data.setForm202(new org.sarmanagement.icsforms.model.Ics202Form());
        }
        if (data.getForm204() == null) {
            data.setForm204(new Ics204Form());
        }
        if (data.getSarTaskAssignments() == null) {
            data.setSarTaskAssignments(new ArrayList<>());
        }
        if (data.getSchemaVersion() < AppData.CURRENT_SCHEMA_VERSION) {
            data.setSchemaVersion(AppData.CURRENT_SCHEMA_VERSION);
        }
        activeLinkSource = LinkSource.NONE;
        synchronizeLinkedFields(LinkSource.NONE);
    }

    /**
     * Creates a fresh document with user-friendly defaults for the first launch.
     *
     * @return new incident document.
     */
    private AppData createDefaultData() {
        AppData document = new AppData();
        IncidentContext context = document.getIncidentContext();
        context.setOperationalPeriodStart(LocalDateTime.now().withSecond(0).withNano(0));
        context.setOperationalPeriodEnd(context.getOperationalPeriodStart().plusHours(12));
        return document;
    }

    private List<String> linkedIncidentCommanders(LinkSource source, org.sarmanagement.icsforms.model.Ics202Form form202,
                                                  OrganizationalChart organizationalChart) {
        List<String> from202 = parseNames(form202.getApprovedByIncidentCommanderName());
        List<String> fromChart = new ArrayList<>(organizationalChart.getIncidentCommanders());
        return switch (source) {
            case ICS202 -> from202;
            case ORG_CHART -> fromChart;
            default -> !from202.isEmpty() ? from202 : fromChart;
        };
    }

    private String linkedOperationsSectionChief(LinkSource source, Ics204Form form204, OrganizationalChart organizationalChart) {
        return switch (source) {
            case ICS204 -> form204.getOperationsSectionChiefName();
            case ORG_CHART -> organizationalChart.getOperationsSectionChiefName();
            default -> !safe(form204.getOperationsSectionChiefName()).isBlank()
                    ? form204.getOperationsSectionChiefName() : organizationalChart.getOperationsSectionChiefName();
        };
    }

    private List<String> parseNames(String value) {
        List<String> names = new ArrayList<>();
        if (value == null || value.isBlank()) {
            return names;
        }
        for (String name : value.split("[\\r\\n;]+")) {
            String trimmed = safe(name);
            if (!trimmed.isBlank()) {
                names = withAddedUnique(names, trimmed);
            }
        }
        return names;
    }

    private String joinNames(List<String> names) {
        return String.join("; ", names);
    }

    private List<String> withAddedUnique(List<String> values, String value) {
        List<String> updated = new ArrayList<>(values);
        String normalizedValue = normalizeRole(value);
        for (String existing : updated) {
            if (normalizeRole(existing).equals(normalizedValue)) {
                return updated;
            }
        }
        updated.add(value);
        return updated;
    }

    private boolean matchesIncidentCommanderRole(String value) {
        String normalized = normalizeRole(value);
        return normalized.equals("incident commander") || normalized.equals("unified command");
    }

    private boolean matchesOperationsSectionChiefRole(String value) {
        return normalizeRole(value).equals("operations section chief");
    }

    private String normalizeRole(String value) {
        return safe(value).toLowerCase().replaceAll("[^a-z0-9]+", " ").trim().replaceAll("\\s+", " ");
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
