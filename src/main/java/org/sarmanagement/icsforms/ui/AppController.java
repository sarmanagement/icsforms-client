package org.sarmanagement.icsforms.ui;

import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.Ics204Form;
import org.sarmanagement.icsforms.model.IncidentContext;
import org.sarmanagement.icsforms.model.ResourceAssignment;
import org.sarmanagement.icsforms.model.SarTaskAssignment;
import org.sarmanagement.icsforms.persistence.LocalRepository;
import org.sarmanagement.icsforms.pdf.PdfExportService;
import org.sarmanagement.icsforms.validation.IncidentValidator;
import org.sarmanagement.icsforms.validation.ValidationMessage;

import javax.swing.Timer;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
    }

    /**
     * Marks the document dirty and schedules autosave.
     */
    public void markDirty() {
        dirty = true;
        syncSarTasks();
        autosaveTimer.restart();
    }

    /**
     * Saves the active document immediately.
     */
    public void save() {
        repository.save(data);
        dirty = false;
    }

    /**
     * Saves the active document to a different location.
     *
     * @param path destination file path.
     */
    public void saveAs(Path path) {
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
        syncSarTasks();
        return exportService.exportAll(data, outputDirectory);
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
        if (data.getForm202() == null) {
            data.setForm202(new org.sarmanagement.icsforms.model.Ics202Form());
        }
        if (data.getForm204() == null) {
            data.setForm204(new Ics204Form());
        }
        if (data.getSarTaskAssignments() == null) {
            data.setSarTaskAssignments(new ArrayList<>());
        }
        if (data.getSchemaVersion() == 0) {
            data.setSchemaVersion(AppData.CURRENT_SCHEMA_VERSION);
        }
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
}
