package org.sarmanagement.icsforms.ui;

import org.sarmanagement.icsforms.model.ActivityEventType;
import org.sarmanagement.icsforms.model.ActivityLogEntry;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.ClueLogEntry;
import org.sarmanagement.icsforms.model.CommunicationEntry;
import org.sarmanagement.icsforms.model.Ics201Form;
import org.sarmanagement.icsforms.model.Ics204Form;
import org.sarmanagement.icsforms.model.Ics214Form;
import org.sarmanagement.icsforms.model.IncidentContext;
import org.sarmanagement.icsforms.model.OrganizationalChart;
import org.sarmanagement.icsforms.model.OrgChartEntry;
import org.sarmanagement.icsforms.model.ActivityLogScope;
import org.sarmanagement.icsforms.model.ResourceAssignment;
import org.sarmanagement.icsforms.model.SarTaskAssignment;
import org.sarmanagement.icsforms.model.SarTaskResource;
import org.sarmanagement.icsforms.model.SarTaskSupport;
import org.sarmanagement.icsforms.model.TCard;
import org.sarmanagement.icsforms.model.TCardType;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
        ICS204,
        ICS214
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
     * Returns the file path where the active document is persisted.
     *
     * @return workspace file path.
     */
    public java.nio.file.Path getFilePath() {
        return repository.getFilePath();
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
     * Returns the ICS 201 incident briefing data.
     *
     * @return ICS 201 form data.
     */
    public Ics201Form getData201() {
        return data.getForm201();
    }

    /**
     * Replaces the ICS 201 incident briefing data and marks the document dirty.
     *
     * @param form ICS 201 form data.
     */
    public void setData201(Ics201Form form) {
        data.setForm201(form);
        markDirty();
    }

    /**
     * Returns a sorted list of distinct personnel names from all T-cards with a non-blank name.
     *
     * @return sorted list of personnel names.
     */
    public List<String> getPersonnelNames() {
        return data.getTCards().stream()
                .filter(c -> !c.getPersonName().isBlank())
                .map(TCard::getPersonName)
                .distinct()
                .sorted()
                .toList();
    }

    /**
     * Finds the first T-card whose {@code personName} matches {@code name} (case-insensitive).
     *
     * @param name person name to look up; {@code null} or blank returns {@code null}.
     * @return matching T-card, or {@code null} if not found.
     */
    public TCard findPersonCard(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String key = name.trim().toLowerCase();
        return data.getTCards().stream()
                .filter(c -> key.equals(c.getPersonName().trim().toLowerCase()))
                .findFirst()
                .orElse(null);
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
        if (source != LinkSource.NONE) {
            activeLinkSource = source;
        }
        synchronizeLinkedFields(source);
        syncSarTasks();
        syncTCards();
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
     * Advances the current incident workspace to a new subsequent operational period.
     *
     * <p>The organizational chart and ICS 201 initial-response record are preserved as
     * historical context.  Operational-period forms are cleared so the new period starts fresh.
     * See {@link org.sarmanagement.icsforms.model.AppData#advanceToNewOperationalPeriod()} for
     * the full list of fields that are reset.</p>
     */
    public void newOperationalPeriod() {
        data.advanceToNewOperationalPeriod();
        ensureDefaults(data);
        syncSarTasks();
        dirty = true;
        activeLinkSource = LinkSource.NONE;
        autosaveTimer.restart();
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
     * Exports all supported forms merged into a single IAP bundle PDF.
     *
     * @param outputDirectory destination directory.
     * @param source source tab for linked role values.
     * @return path of the merged IAP bundle PDF.
     * @throws IOException when export or merge fails.
     */
    public Path exportIapBundle(Path outputDirectory, LinkSource source) throws IOException {
        synchronizeLinkedFields(source);
        syncSarTasks();
        return exportService.exportIapBundle(data, outputDirectory);
    }

    public List<String> exportableFormKeys() {
        return exportService.formKeys();
    }

    public java.util.Map<String, Path> exportSelectedForms(List<String> formKeys, Path outputDirectory, LinkSource source) throws IOException {
        synchronizeLinkedFields(source);
        syncSarTasks();
        java.util.Map<String, Path> exported = new LinkedHashMap<>();
        if (formKeys == null) {
            return exported;
        }
        for (String formKey : formKeys) {
            if (exportService.supports(formKey)) {
                exported.put(formKey, exportService.exportSelected(formKey, data, outputDirectory));
            }
        }
        return exported;
    }

    public Path exportSelectedIapBundle(List<String> formKeys, Path outputDirectory, LinkSource source) throws IOException {
        synchronizeLinkedFields(source);
        syncSarTasks();
        return exportService.exportIapBundle(data, outputDirectory, formKeys);
    }

    /**
     * Synchronizes shared org-chart-linked fields across the shared tab, org chart, ICS 202, and ICS 204.
     *
     * @param source source tab for linked role values.
     */
    public void synchronizeLinkedFields(LinkSource source) {
        if (source == LinkSource.NONE && activeLinkSource != LinkSource.NONE) {
            source = activeLinkSource;
        }
        IncidentContext context = data.getIncidentContext();
        Ics201Form form201 = data.getForm201();
        org.sarmanagement.icsforms.model.Ics202Form form202 = data.getForm202();
        Ics204Form form204 = data.getForm204();
        OrganizationalChart organizationalChart = data.getOrganizationalChart();
        if (context == null || form201 == null || form202 == null || form204 == null || organizationalChart == null) {
            return;
        }

        String preparerName = safe(context.getCurrentUser());
        String preparerTitle = safe(context.getCurrentUserPositionTitle());
        form201.setIncidentName(safe(context.getIncidentName()));

        List<String> incidentCommanders = linkedIncidentCommanders(source, form202, organizationalChart);
        if (source == LinkSource.SHARED && matchesIncidentCommanderRole(preparerTitle) && !preparerName.isBlank()) {
            incidentCommanders = incidentCommanders.isEmpty()
                    ? new ArrayList<>(List.of(preparerName))
                    : withAddedUnique(incidentCommanders, preparerName);
        }
        organizationalChart.setIncidentCommanders(incidentCommanders);
        form202.setApprovedByIncidentCommanderName(joinNames(incidentCommanders));

        String operationsSectionChiefName = linkedOperationsSectionChief(source, form204, organizationalChart);
        if (source == LinkSource.SHARED && matchesOperationsSectionChiefRole(preparerTitle) && !preparerName.isBlank()) {
            operationsSectionChiefName = preparerName;
        }
        operationsSectionChiefName = safe(operationsSectionChiefName);
        organizationalChart.setOperationsSectionChiefName(operationsSectionChiefName);
        form204.setOperationsSectionChiefName(operationsSectionChiefName);

        // Sync ops section chief contact from org chart → form 204 when org chart is authoritative.
        String opsContact = safe(organizationalChart.getOperationsSectionChiefRadio()).isBlank()
                ? safe(organizationalChart.getOperationsSectionChiefPhone())
                : safe(organizationalChart.getOperationsSectionChiefRadio());
        if (!opsContact.isBlank()
                && (safe(form204.getOperationsSectionChiefContact()).isBlank()
                || source == LinkSource.ORG_CHART)) {
            form204.setOperationsSectionChiefContact(opsContact);
        }

        if (source == LinkSource.SHARED && matchesPlanningSectionChiefRole(preparerTitle) && !preparerName.isBlank()) {
            organizationalChart.setPlanningSectionChiefName(preparerName);
        }
        if (source == LinkSource.SHARED && matchesLogisticsSectionChiefRole(preparerTitle) && !preparerName.isBlank()) {
            organizationalChart.setLogisticsSectionChiefName(preparerName);
        }
        if (source == LinkSource.SHARED && matchesFinanceAdminSectionChiefRole(preparerTitle) && !preparerName.isBlank()) {
            organizationalChart.setFinanceAdminSectionChiefName(preparerName);
        }
        if (source == LinkSource.SHARED && matchesDocumentationUnitLeaderRole(preparerTitle) && !preparerName.isBlank()) {
            organizationalChart.setDocumentationUnitLeaderName(preparerName);
        }
        if (source == LinkSource.SHARED && matchesSafetyOfficerRole(preparerTitle) && !preparerName.isBlank()) {
            organizationalChart.setSafetyOfficerName(preparerName);
        }

        if (!preparerName.isBlank() || !preparerTitle.isBlank() || source == LinkSource.SHARED) {
            form202.setPreparedByName(preparerName);
            form202.setPreparedByPositionTitle(preparerTitle);
            form204.setPreparedByName(preparerName);
            form204.setPreparedByPositionTitle(preparerTitle);
        }
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
     * Returns the current incident mode (SAR or Generic).
     *
     * @return incident mode.
     */
    public org.sarmanagement.icsforms.model.IncidentMode getIncidentMode() {
        return data.getIncidentMode();
    }

    /**
     * Sets the incident mode and marks the document dirty.
     *
     * @param mode incident mode.
     */
    public void setIncidentMode(org.sarmanagement.icsforms.model.IncidentMode mode) {
        data.setIncidentMode(mode);
        markDirty();
    }

    /**
     * Returns the current IAP preparation phase.
     *
     * @return IAP phase.
     */
    public org.sarmanagement.icsforms.model.IapPhase getIapPhase() {
        return data.getIapPhase();
    }

    /**
     * Sets the IAP preparation phase and marks the document dirty.
     *
     * @param phase IAP phase.
     */
    public void setIapPhase(org.sarmanagement.icsforms.model.IapPhase phase) {
        data.setIapPhase(phase);
        markDirty();
    }

    /**
     * Synchronizes SAR task scaffold records from ICS 204 resources using stable assignment IDs.
     */
    public void syncSarTasks() {
        IncidentContext context = data.getIncidentContext();
        Ics204Form form = data.getForm204();
        Map<String, SarTaskAssignment> existingById = new LinkedHashMap<>();
        for (SarTaskAssignment existing : data.getSarTaskAssignments()) {
            existingById.put(existing.getAssignmentId(), existing);
        }
        List<SarTaskAssignment> synced = new ArrayList<>();
        for (ResourceAssignment resource : form.getResourcesAssigned()) {
            SarTaskAssignment scaffold = SarTaskAssignment.fromResourceAssignment(resource, context, form);
            scaffold.setPreparedDateTime(form.getPreparedDateTime());
            SarTaskAssignment existing = existingById.get(resource.getAssignmentId());
            synced.add(existing == null ? scaffold : mergeSarTask(existing, scaffold));
        }
        data.setSarTaskAssignments(synced);
    }

    /**
     * Synchronizes editable SAR task linkage fields back into the matching ICS 204 resource rows.
     */
    public void syncIcs204ResourcesFromSarTasks() {
        Map<String, SarTaskAssignment> tasksById = new LinkedHashMap<>();
        for (SarTaskAssignment task : data.getSarTaskAssignments()) {
            tasksById.put(task.getAssignmentId(), task);
        }
        for (ResourceAssignment resource : data.getForm204().getResourcesAssigned()) {
            SarTaskAssignment task = tasksById.get(resource.getAssignmentId());
            if (task == null) {
                continue;
            }
            resource.setAssignmentTeamNumber(task.getAssignmentTeamNumber());
            resource.setResourceType(task.getResourceType());
            resource.setTaskType(task.getTaskType());
            // Auto-fill contact from the leader's T-card radio/phone when the field is blank.
            if (safe(resource.getContact()).isBlank() && !safe(resource.getLeader()).isBlank()) {
                TCard leaderCard = findPersonCard(resource.getLeader());
                if (leaderCard != null) {
                    String radioPhone = safe(leaderCard.getRadioChannel()).isBlank()
                            ? safe(leaderCard.getPhoneNumber())
                            : safe(leaderCard.getRadioChannel());
                    if (!radioPhone.isBlank()) {
                        resource.setContact(radioPhone);
                    }
                }
            }
        }
        updateClueTaskLabels(tasksById);
    }

    /**
     * Synchronizes T-card records from org-chart staff positions and SAR task resources.
     *
     * <p>Each named staff position on the org chart and each named resource on a SAR task
     * automatically maintains a linked T-card.  A card's {@code sourceRef} field is the
     * stable key; only the name and contact fields are updated here so that operators may
     * freely edit status, location, and notes without losing their changes.</p>
     *
     * <p>Cards whose source position is later cleared (name set to blank) are removed from
     * the list.  Manually created cards (blank sourceRef) are never touched.</p>
     */
    public void syncTCards() {
        List<TCard> cards = data.getTCards();
        if (cards == null) {
            cards = new ArrayList<>();
            data.setTCards(cards);
        }

        // Build a mutable map of sourceRef → card for fast lookup; preserve order.
        Map<String, TCard> byRef = new LinkedHashMap<>();
        for (TCard card : cards) {
            String ref = card.getSourceRef();
            if (!ref.isBlank()) {
                byRef.put(ref, card);
            }
        }

        // Build a name → card map for cross-source deduplication.  The same physical
        // person may appear as a leader on one task and as a resource on another; they
        // should share a single T-card record rather than receiving duplicates.
        Map<String, TCard> byName = new LinkedHashMap<>();
        for (TCard card : cards) {
            String n = card.getPersonName().trim().toLowerCase();
            if (!n.isBlank()) {
                byName.putIfAbsent(n, card);
            }
        }

        // Build a resourceIdentifier → card map for equipment/canine cards so that
        // SAR task resources can be matched back to existing EQUIPMENT T-cards.
        // Legacy data: old CSV imports stored the canine name in personName with a blank
        // resourceIdentifier.  Migrate those records now so all downstream logic can rely
        // on resourceIdentifier as the authoritative display name for non-personnel cards.
        // This migration is idempotent: if the migrated data is not immediately saved, the
        // same transformation will re-apply on the next sync with the same result.
        Map<String, TCard> byEquipmentId = new LinkedHashMap<>();
        // UUID → card index — the authoritative lookup used when a SarTaskResource carries
        // a resourceId reference, ensuring we find the exact card regardless of name.
        Map<String, TCard> byCardId = new LinkedHashMap<>();
        for (TCard card : cards) {
            if (card.getCardType() != TCardType.PERSONNEL) {
                if (card.getResourceIdentifier().isBlank() && !card.getPersonName().isBlank()) {
                    card.setResourceIdentifier(card.getPersonName());
                    card.setPersonName("");
                }
            }
            // Migrate legacy PERSONNEL cards that have numberOfPersons == 0 (pre-default era).
            if (card.getCardType() == TCardType.PERSONNEL && card.getNumberOfPersons() == 0) {
                card.setNumberOfPersons(1);
            }
            // Index all non-PERSONNEL, non-HEADER cards by resourceIdentifier so that
            // canines or other resources stored with an unexpected card type (e.g. due to a
            // previous sync bug) are still found by name and not silently recreated.
            if (card.getCardType() != TCardType.PERSONNEL && card.getCardType() != TCardType.HEADER) {
                String rid = card.getResourceIdentifier().trim().toLowerCase();
                if (!rid.isBlank()) {
                    byEquipmentId.putIfAbsent(rid, card);
                }
            }
            // Always index every card by its stable UUID.
            byCardId.putIfAbsent(card.getResourceId(), card);
        }

        // Collect the set of sourceRefs that should exist after this sync.
        Map<String, TCard> wanted = new LinkedHashMap<>();

        // --- Org chart positions ---
        OrganizationalChart chart = data.getOrganizationalChart();
        if (chart != null) {
            // Incident commanders (one card per named commander)
            List<String> ics = chart.getIncidentCommanders();
            if (ics != null) {
                for (int i = 0; i < ics.size(); i++) {
                    String name = safe(ics.get(i));
                    if (!name.isBlank()) {
                        String ref = "org:ic:" + i;
                        TCard card;
                        if (byRef.containsKey(ref)) {
                            card = byRef.get(ref);
                        } else {
                            String nameKey = name.trim().toLowerCase();
                            card = byName.containsKey(nameKey) ? byName.get(nameKey) : newOrgCard(name, "", "");
                        }
                        card.setPersonName(name);
                        String icRadio = safe(chart.getIncidentCommanderRadio());
                        String icPhone = safe(chart.getIncidentCommanderPhone());
                        if (chart.getIncidentCommanderEntries() != null && i < chart.getIncidentCommanderEntries().size()) {
                            OrgChartEntry icEntry = chart.getIncidentCommanderEntries().get(i);
                            if (icEntry != null) {
                                if (!safe(icEntry.getRadio()).isBlank()) {
                                    icRadio = safe(icEntry.getRadio());
                                }
                                if (!safe(icEntry.getPhone()).isBlank()) {
                                    icPhone = safe(icEntry.getPhone());
                                }
                            }
                        }
                        if (!icRadio.isBlank()) card.setRadioChannel(icRadio);
                        if (!icPhone.isBlank()) card.setPhoneNumber(icPhone);
                        card.setSourceRef(card.getSourceRef().isBlank() ? ref : card.getSourceRef());
                        card.setNotes(notePreserving(card.getNotes(), "Incident Commander"));
                        wanted.put(ref, card);
                        byName.putIfAbsent(name.trim().toLowerCase(), card);
                    }
                }
            }
            addOrgCard(wanted, byRef, byName, "org:safetyOfficer",
                    chart.getSafetyOfficerName(), chart.getSafetyOfficerRadio(),
                    chart.getSafetyOfficerPhone(), "Safety Officer");
            addOrgCard(wanted, byRef, byName, "org:pio",
                    chart.getPublicInformationOfficerName(), chart.getPublicInformationOfficerRadio(),
                    chart.getPublicInformationOfficerPhone(), "Public Information Officer");
            addOrgCard(wanted, byRef, byName, "org:liaisonOfficer",
                    chart.getLiaisonOfficerName(), chart.getLiaisonOfficerRadio(),
                    chart.getLiaisonOfficerPhone(), "Liaison Officer");
            addOrgCard(wanted, byRef, byName, "org:operationsChief",
                    chart.getOperationsSectionChiefName(), chart.getOperationsSectionChiefRadio(),
                    chart.getOperationsSectionChiefPhone(), "Operations Section Chief");
            addOrgCard(wanted, byRef, byName, "org:planningChief",
                    chart.getPlanningSectionChiefName(), chart.getPlanningSectionChiefRadio(),
                    chart.getPlanningSectionChiefPhone(), "Planning Section Chief");
            addOrgCard(wanted, byRef, byName, "org:logisticsChief",
                    chart.getLogisticsSectionChiefName(), chart.getLogisticsSectionChiefRadio(),
                    chart.getLogisticsSectionChiefPhone(), "Logistics Section Chief");
            addOrgCard(wanted, byRef, byName, "org:financeAdminChief",
                    chart.getFinanceAdminSectionChiefName(), chart.getFinanceAdminSectionChiefRadio(),
                    chart.getFinanceAdminSectionChiefPhone(), "Finance/Admin Section Chief");
            addOrgCard(wanted, byRef, byName, "org:documentationUnitLeader",
                    chart.getDocumentationUnitLeaderName(), chart.getDocumentationUnitLeaderRadio(),
                    chart.getDocumentationUnitLeaderPhone(), "Documentation Unit Leader");
            addOrgCard(wanted, byRef, byName, "org:commUnitLeader",
                    chart.getCommunicationsUnitLeaderName(), chart.getCommunicationsUnitLeaderRadio(),
                    chart.getCommunicationsUnitLeaderPhone(), "Communications Unit Leader");
            addOrgCard(wanted, byRef, byName, "org:commTechnician",
                    chart.getCommunicationsTechnicianName(), chart.getCommunicationsTechnicianRadio(),
                    chart.getCommunicationsTechnicianPhone(), "Communications Technician");
        }

        // --- Context preparer ---
        // Always maintain a T-card for the named preparer/current user, regardless of whether
        // their title maps to a specific org chart slot.  This is keyed by a fixed sourceRef so
        // it survives sync cycles even when the person's name changes.
        IncidentContext ctx = data.getIncidentContext();
        if (ctx != null && !safe(ctx.getCurrentUser()).isBlank()) {
            String preparerCardName = safe(ctx.getCurrentUser());
            String preparerCardTitle = safe(ctx.getCurrentUserPositionTitle());
            String preparerRef = "context:preparer";
            TCard preparerCard;
            if (byRef.containsKey(preparerRef)) {
                preparerCard = byRef.get(preparerRef);
            } else {
                String nameKey = preparerCardName.trim().toLowerCase();
                preparerCard = byName.containsKey(nameKey) ? byName.get(nameKey) : newPersonnelCard(preparerCardName);
            }
            preparerCard.setPersonName(preparerCardName);
            preparerCard.setLocation(coalesce(preparerCard.getLocation(), "ICP"));
            preparerCard.setSourceRef(preparerCard.getSourceRef().isBlank() ? preparerRef : preparerCard.getSourceRef());
            preparerCard.setNotes(notePreserving(preparerCard.getNotes(),
                    preparerCardTitle.isBlank() ? "Preparer" : preparerCardTitle));
            wanted.put(preparerRef, preparerCard);
            byName.putIfAbsent(preparerCardName.trim().toLowerCase(), preparerCard);
        }

        // --- SAR task resources ---
        // Track desired T-card status from task lifecycle (identity-keyed for dedup safety).
        Map<TCard, String> taskDrivenStatus = new java.util.IdentityHashMap<>();
        for (SarTaskAssignment task : data.getSarTaskAssignments()) {
            String assignmentId = task.getAssignmentId();
            if (assignmentId == null || assignmentId.isBlank()) {
                continue;
            }
            String lifecycleCardStatus = lifecycleToCardStatus(task.getTaskLifecycleStatus());
            // Task leader
            String leaderName = safe(task.getLeader());
            boolean isCanineTask = SarTaskSupport.usesCanineFactors(task.getResourceType());
            if (!leaderName.isBlank()) {
                String ref = "sar:" + assignmentId + ":leader";
                TCard card = findOrCreatePersonCard(ref, leaderName, byRef, byName, wanted);
                card.setPersonName(leaderName);
                String taskContact = safe(task.getContact());
                if (!taskContact.isBlank()) {
                    // Route the contact value to the correct field: phone numbers (≥7 digits
                    // after stripping formatting) go to phoneNumber; anything else (radio
                    // channel names, talkgroup IDs, VHF/UHF frequencies) goes to radioChannel.
                    if (looksLikePhoneNumber(taskContact)) {
                        card.setPhoneNumber(coalesce(card.getPhoneNumber(), taskContact));
                    } else {
                        card.setRadioChannel(coalesce(card.getRadioChannel(), taskContact));
                    }
                }
                // A task assignment overrides an org chart slot so the card appears in the
                // correct task group in the rack view.  A sar: ref is never overridden by
                // another sar: ref — the first task to claim a person wins.
                setTaskSourceRef(card, ref);
                card.setNotes(notePreserving(card.getNotes(),
                        safe(task.getLeaderRole()) + " — " + safe(task.getAssignmentTeamNumber())));
                wanted.put(ref, card);
                byName.putIfAbsent(leaderName.trim().toLowerCase(), card);
                applyHigherPriorityStatus(taskDrivenStatus, card, lifecycleCardStatus);

                // For canine tasks, the canine T-card is added via the resources-assigned
                // list (picked by the operator), not synthesised from the task identifier.
                // Handler linking is performed when iterating resourcesAssigned below.
            }
            // Assigned resources — deduplicate by name first to avoid phantom cards.
            List<SarTaskResource> resources = task.getResourcesAssigned();
            if (resources != null) {
                resources = deduplicateResources(resources);
                for (int i = 0; i < resources.size(); i++) {
                    SarTaskResource res = resources.get(i);
                    String resName = safe(res.getName());
                    if (resName.isBlank()) {
                        continue;
                    }
                    String ref = "sar:" + assignmentId + ":r:" + i;
                    String resNameKey = resName.trim().toLowerCase();
                    boolean resNameIsLeader = !leaderName.isBlank()
                            && resNameKey.equals(leaderName.trim().toLowerCase());

                    // --- UUID-based lookup (preferred) -----------------------------------------
                    // When the SarTaskResource carries a resourceId, find the exact TCard by UUID.
                    // This is the authoritative path: the TCard's type is never mutated from
                    // SarTaskResource.cardType — the stored card type wins unconditionally.
                    TCard card;
                    String linkedId = res.getResourceId();
                    if (!linkedId.isBlank() && byCardId.containsKey(linkedId)) {
                        card = byCardId.get(linkedId);
                        // Synchronise the SarTaskResource's display-helper fields from the card.
                        res.setCardType(card.getCardType());
                        res.setName(card.getDisplayLabel());
                        setTaskSourceRef(card, ref);
                        if (card.getCardType() != TCardType.PERSONNEL
                                && isCanineTask && !leaderName.isBlank() && !resNameIsLeader
                                && card.getHandlerName().isBlank()) {
                            card.setHandlerName(leaderName);
                        }
                        card.setNotes(notePreserving(card.getNotes(),
                                safe(res.getFunction()) + " — " + safe(task.getAssignmentTeamNumber())));
                        wanted.put(ref, card);
                        applyHigherPriorityStatus(taskDrivenStatus, card, lifecycleCardStatus);
                        continue;
                    }

                    // --- Legacy name-based lookup (fallback for entries without a resourceId) ---
                    // On a canine task the leader is the handler (a person).  If a resource
                    // entry has the same name as the leader it IS that person — never treat it
                    // as a canine/equipment card regardless of the stored cardType.
                    TCard equipCard = resNameIsLeader ? null : byEquipmentId.get(resNameKey);
                    // Guard against a stale EQUIPMENT card whose resourceIdentifier happens to
                    // match a known person name (can arise from old sync logic that incorrectly
                    // used the task's resource-identifier field as the canine T-card key).
                    // When a proper PERSONNEL card already exists for the same name, prefer it.
                    if (equipCard != null) {
                        TCard existingPersonCard = byName.get(resNameKey);
                        if (existingPersonCard != null
                                && existingPersonCard.getCardType() == TCardType.PERSONNEL) {
                            equipCard = null;
                        }
                    }
                    if (equipCard != null) {
                        card = equipCard;
                        res.setCardType(card.getCardType());
                        // Back-fill resourceId so future syncs use the fast UUID path.
                        res.setResourceId(card.getResourceId());
                        setTaskSourceRef(card, ref);
                        if (isCanineTask && !leaderName.isBlank() && !resNameIsLeader
                                && card.getHandlerName().isBlank()) {
                            card.setHandlerName(leaderName);
                        }
                        card.setNotes(notePreserving(card.getNotes(),
                                safe(res.getFunction()) + " — " + safe(task.getAssignmentTeamNumber())));
                        wanted.put(ref, card);
                        applyHigherPriorityStatus(taskDrivenStatus, card, lifecycleCardStatus);
                    } else if (!resNameIsLeader && res.getCardType() != null
                            && res.getCardType() != TCardType.PERSONNEL) {
                        // Resource was explicitly typed as non-PERSONNEL (e.g. EQUIPMENT for a canine)
                        // when it was added to the task.  But if the name matches a known person,
                        // the stored cardType is a stale artefact — correct it to PERSONNEL.
                        TCard existingPersonCard = byName.get(resNameKey);
                        if (existingPersonCard != null
                                && existingPersonCard.getCardType() == TCardType.PERSONNEL) {
                            res.setCardType(TCardType.PERSONNEL);
                            card = findOrCreatePersonCard(ref, resName, byRef, byName, wanted);
                            card.setPersonName(resName);
                            card.setHomeAgency(coalesce(card.getHomeAgency(), res.getHomeAgency()));
                            res.setResourceId(card.getResourceId());
                            setTaskSourceRef(card, ref);
                            card.setNotes(notePreserving(card.getNotes(),
                                    safe(res.getFunction()) + " — " + safe(task.getAssignmentTeamNumber())));
                            wanted.put(ref, card);
                            byName.putIfAbsent(resName.trim().toLowerCase(), card);
                            applyHigherPriorityStatus(taskDrivenStatus, card, lifecycleCardStatus);
                        } else {
                            // Honour the stored non-PERSONNEL type rather than falling through
                            // to findOrCreatePersonCard, which would create a PERSONNEL card.
                            card = findOrCreateEquipmentCard(ref, resName, byRef, byEquipmentId);
                            // Never overwrite the card's existing type: if the card was already
                            // created with a specific type, preserve it.
                            if (card.getCardType() == TCardType.PERSONNEL) {
                                card.setCardType(res.getCardType());
                            }
                            res.setCardType(card.getCardType());
                            card.setResourceIdentifier(coalesce(card.getResourceIdentifier(), resName));
                            card.setHomeAgency(coalesce(card.getHomeAgency(), res.getHomeAgency()));
                            res.setResourceId(card.getResourceId());
                            setTaskSourceRef(card, ref);
                            if (isCanineTask && !leaderName.isBlank() && !resNameIsLeader
                                    && card.getHandlerName().isBlank()) {
                                card.setHandlerName(leaderName);
                            }
                            card.setNotes(notePreserving(card.getNotes(),
                                    safe(res.getFunction()) + " — " + safe(task.getAssignmentTeamNumber())));
                            wanted.put(ref, card);
                            byEquipmentId.putIfAbsent(resName.trim().toLowerCase(), card);
                            applyHigherPriorityStatus(taskDrivenStatus, card, lifecycleCardStatus);
                        }
                    } else {
                        card = findOrCreatePersonCard(ref, resName, byRef, byName, wanted);
                        res.setCardType(card.getCardType());
                        card.setPersonName(resName);
                        card.setHomeAgency(coalesce(card.getHomeAgency(), res.getHomeAgency()));
                        res.setResourceId(card.getResourceId());
                        setTaskSourceRef(card, ref);
                        card.setNotes(notePreserving(card.getNotes(),
                                safe(res.getFunction()) + " — " + safe(task.getAssignmentTeamNumber())));
                        wanted.put(ref, card);
                        byName.putIfAbsent(resName.trim().toLowerCase(), card);
                        applyHigherPriorityStatus(taskDrivenStatus, card, lifecycleCardStatus);
                    }
                }
            }
        }

        // Apply lifecycle-driven statuses to task resource cards.
        for (Map.Entry<TCard, String> entry : taskDrivenStatus.entrySet()) {
            entry.getKey().setStatus(entry.getValue());
        }

        // Rebuild the card list: keep manually created cards first, then source-linked cards
        // in stable order, dropping any whose source was cleared.  Use identity-based
        // deduplication so that a card reused for multiple refs is only emitted once.
        List<TCard> result = new ArrayList<>();
        Set<TCard> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (TCard card : cards) {
            if (card.getSourceRef().isBlank()) {
                result.add(card);  // manual card — keep as-is
                seen.add(card);
            }
        }
        for (TCard card : wanted.values()) {
            if (!seen.contains(card)) {
                result.add(card);
                seen.add(card);
            }
        }
        ensureDefaultHeaderCards(result, data.getIapPhase());
        data.setTCards(result);
    }

    /** Default HEADER card labels used when no HEADER cards exist yet. */
    private static final String[] DEFAULT_HEADER_LABELS = {
            "ICP", "Available", "Assigned", "Out of Service", "Enroute"
    };
    /** Extra HEADER label added in pre-operational-period mode. */
    private static final String ORDERED_HEADER_LABEL = "Ordered";

    /**
     * Ensures the default HEADER (219-1) rack columns exist in the card list.
     *
     * <p>If no HEADER cards are present the default labels (ICP, Available, Assigned,
     * Out of Service, Enroute) are prepended; in {@link org.sarmanagement.icsforms.model.IapPhase#PRE_OP}
     * mode an additional "Ordered" column is inserted after "ICP".
     * Once any HEADER card is present no defaults are added so as not to override operator
     * customisation.</p>
     *
     * @param cards   mutable card list to modify in-place.
     * @param phase   current IAP phase (used to include/exclude the Ordered column).
     */
    static void ensureDefaultHeaderCards(List<TCard> cards,
                                         org.sarmanagement.icsforms.model.IapPhase phase) {
        boolean hasHeader = cards.stream().anyMatch(c -> c.getCardType() == TCardType.HEADER);
        if (hasHeader) {
            return;
        }
        List<TCard> headers = new ArrayList<>();
        for (String label : DEFAULT_HEADER_LABELS) {
            TCard h = new TCard();
            h.setCardType(TCardType.HEADER);
            h.setResourceIdentifier(label);
            headers.add(h);
            // Insert "Ordered" after "ICP" in pre-op mode.
            if ("ICP".equals(label) && phase == org.sarmanagement.icsforms.model.IapPhase.PRE_OP) {
                TCard ordered = new TCard();
                ordered.setCardType(TCardType.HEADER);
                ordered.setResourceIdentifier(ORDERED_HEADER_LABEL);
                headers.add(ordered);
            }
        }
        cards.addAll(0, headers);
    }

    /**
     * Converts a task lifecycle status string to the equivalent T-card status.
     *
     * @param lifecycle lifecycle value from SAR task editor.
     * @return T-card status string ("Assigned", blank, or {@code null} for Returned).
     */
    static String lifecycleToCardStatus(String lifecycle) {
        if (lifecycle == null) return "";
        String normalized = lifecycle.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (normalized) {
            case "assigned - enroute to assignment",
                 "assigned - on task",
                 "assigned - returning from assignment",
                 "on task" -> "Assigned";
            case "returned" -> null;  // Returned tasks do not drive resource status —
                                      // operators manage the card manually after return.
            default         -> "";    // Planned/Planning or unknown → blank (available)
        };
    }

    /**
     * Records a lifecycle-driven status for {@code card}, keeping the highest-priority
     * value when the same card is referenced from multiple tasks.
     *
     * <p>A {@code null} status means "do not touch this card's status" (used for
     * Returned tasks so that manually-set statuses such as "At Staging" are preserved).
     * Blank statuses are recorded as the baseline so resources can move back to
     * available when a task is reverted to Planned. Higher-priority non-blank statuses
     * ("Assigned") still win when the same card appears on multiple tasks.</p>
     *
     * <p>Priority: "Assigned" &gt; blank (available).</p>
     */
    private static void applyHigherPriorityStatus(Map<TCard, String> map, TCard card, String status) {
        if (status == null) {
            return; // Returned-task sentinel: leave the card's current status untouched.
        }
        if (status.isBlank()) {
            map.putIfAbsent(card, "");
            return;
        }
        String current = map.getOrDefault(card, "");
        if (statusPriority(status) > statusPriority(current)) {
            map.put(card, status);
        }
    }

    private static int statusPriority(String status) {
        if (status == null) return 0;
        return switch (status) {
            case "Assigned"       -> 2;
            case "Out of Service" -> 1;
            default               -> 0;
        };
    }

    /**
     * Finds an existing T-card for {@code personName} or creates a new one.
     *
     * <p>Lookup priority:
     * <ol>
     *   <li>Card already registered under {@code ref} in {@code byRef} (same position, update).</li>
     *   <li>Card already emitted into {@code wanted} for the same name (cross-ref dedup).</li>
     *   <li>Card existing in {@code byName} from a previous sync cycle (persist old card).</li>
     *   <li>New blank card.</li>
     * </ol>
     */
    private TCard findOrCreatePersonCard(String ref, String personName,
                                          Map<String, TCard> byRef,
                                          Map<String, TCard> byName,
                                          Map<String, TCard> wanted) {
        if (byRef.containsKey(ref)) {
            TCard candidate = byRef.get(ref);
            // A stale EQUIPMENT card may have been stored under this ref by an older sync
            // cycle.  Do not reuse it for a PERSONNEL entry — fall through to name lookup.
            if (candidate.getCardType() == TCardType.PERSONNEL) {
                return candidate;
            }
        }
        String nameKey = personName.trim().toLowerCase();
        // Check if we already placed this person's card into wanted under a different ref.
        for (TCard c : wanted.values()) {
            if (nameKey.equals(c.getPersonName().trim().toLowerCase())) {
                return c;
            }
        }
        if (byName.containsKey(nameKey)) {
            return byName.get(nameKey);
        }
        return newPersonnelCard(personName);
    }

    /**
     * Finds an existing equipment T-card for {@code resourceId} or creates a new one.
     *
     * <p>Lookup priority:
     * <ol>
     *   <li>Card already registered under {@code ref} in {@code byRef}.</li>
     *   <li>Card in {@code byEquipmentId} matched by resource identifier.</li>
     *   <li>New blank EQUIPMENT card.</li>
     * </ol>
     */
    private TCard findOrCreateEquipmentCard(String ref, String resourceId,
                                             Map<String, TCard> byRef,
                                             Map<String, TCard> byEquipmentId) {
        if (byRef.containsKey(ref)) {
            return byRef.get(ref);
        }
        String idKey = resourceId.trim().toLowerCase();
        if (byEquipmentId.containsKey(idKey)) {
            return byEquipmentId.get(idKey);
        }
        TCard card = new TCard();
        card.setCardType(TCardType.EQUIPMENT);
        card.setResourceIdentifier(resourceId);
        return card;
    }

    /**
     * Returns the resource identifiers of all EQUIPMENT T-cards whose {@code handlerName}
     * matches {@code handlerName} (case-insensitive).  Used by the SAR task editor to
     * suggest the handler's linked canine when creating a Canine task.
     *
     * @param handlerName handler name to look up.
     * @return list of matching resource identifiers, never {@code null}.
     */
    public List<String> findEquipmentForHandler(String handlerName) {
        if (handlerName == null || handlerName.isBlank()) {
            return List.of();
        }
        String key = handlerName.trim().toLowerCase();
        List<String> result = new ArrayList<>();
        for (TCard card : data.getTCards()) {
            if ((card.getCardType() == TCardType.EQUIPMENT || card.getCardType() == TCardType.MISC_EQUIPMENT)
                    && key.equals(card.getHandlerName().trim().toLowerCase())) {
                String name = effectiveTCardName(card);
                if (!name.isBlank()) {
                    result.add(name);
                }
            }
        }
        return result;
    }

    /**
     * Removes duplicate {@link SarTaskResource} entries from every SAR task assignment
     * by comparing effective names (case-insensitive).  When the same resource name appears
     * more than once in a task's resources list (which can happen after T-card deduplication
     * merges two cards that were separately referenced), all occurrences beyond the first
     * are dropped.
     *
     * <p>Call this after merging duplicate T-cards so that the SAR task display does not
     * show the same resource twice.</p>
     */
    public void deduplicateSarTaskResources() {
        for (SarTaskAssignment task : data.getSarTaskAssignments()) {
            List<SarTaskResource> resources = task.getResourcesAssigned();
            if (resources == null || resources.size() <= 1) {
                continue;
            }
            Set<String> seen = new java.util.HashSet<>();
            List<SarTaskResource> deduped = new ArrayList<>();
            for (SarTaskResource res : resources) {
                String key = res.getName() == null ? "" : res.getName().trim().toLowerCase();
                if (seen.add(key)) {
                    deduped.add(res);
                }
            }
            if (deduped.size() < resources.size()) {
                task.setResourcesAssigned(deduped);
            }
        }
    }

    /**
     * Returns a sorted list of all known resource names (personnel names and equipment
     * identifiers) from the current T-card rack.  Used to populate pick lists in the
     * SAR task editor's Resources Assigned table.
     *
     * @return sorted list of non-blank resource names/identifiers.
     */
    public List<String> getAvailableResourceNames() {
        List<String> names = new ArrayList<>();
        for (TCard card : data.getTCards()) {
            String n = effectiveTCardName(card).trim();
            if (!n.isBlank()) {
                names.add(n);
            }
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    /**
     * Returns T-cards suitable for picking as SAR task resources (excludes header cards).
     *
     * @return list of non-header T-cards sorted by effective name.
     */
    public List<TCard> getAvailableTCards() {
        List<TCard> result = new ArrayList<>();
        for (TCard card : data.getTCards()) {
            if (card.getCardType() == TCardType.HEADER) {
                continue;
            }
            String effName = effectiveTCardName(card);
            if (!effName.isBlank()) {
                result.add(card);
            }
        }
        result.sort((a, b) -> {
            String nameA = effectiveTCardName(a);
            String nameB = effectiveTCardName(b);
            return String.CASE_INSENSITIVE_ORDER.compare(nameA, nameB);
        });
        return result;
    }

    /**
     * Returns the set of T-card resource IDs that are currently on an active ("On Task")
     * SAR task assignment.  Used by the resource picker dialog to determine which cards to
     * hide by default (cards whose resources are already committed to a live task).
     *
     * @return unmodifiable set of UUID resource IDs; never {@code null}.
     */
    public Set<String> getOnTaskResourceIds() {
        Set<String> ids = new HashSet<>();
        List<SarTaskAssignment> tasks = data.getSarTaskAssignments();
        if (tasks == null) return Collections.unmodifiableSet(ids);
        for (SarTaskAssignment task : tasks) {
            String lifecycle = task.getTaskLifecycleStatus() == null
                    ? ""
                    : task.getTaskLifecycleStatus().trim().toLowerCase(java.util.Locale.ROOT);
            if (!(lifecycle.startsWith("assigned -") || "on task".equals(lifecycle))) continue;
            List<SarTaskResource> resources = task.getResourcesAssigned();
            if (resources == null) continue;
            for (SarTaskResource res : resources) {
                String rid = res.getResourceId();
                if (rid != null && !rid.isBlank()) {
                    ids.add(rid);
                }
            }
        }
        return Collections.unmodifiableSet(ids);
    }

    public void applyReturnedResourceStatuses(SarTaskAssignment task, Map<String, String> statusesByResourceId) {
        if (task == null || statusesByResourceId == null || statusesByResourceId.isEmpty()) {
            return;
        }
        Map<String, TCard> cardsById = new HashMap<>();
        for (TCard card : data.getTCards()) {
            if (card.getResourceId() != null && !card.getResourceId().isBlank()) {
                cardsById.put(card.getResourceId(), card);
            }
        }
        for (SarTaskResource resource : task.getResourcesAssigned()) {
            if (resource.getResourceId() == null || resource.getResourceId().isBlank()) {
                continue;
            }
            String status = statusesByResourceId.get(resource.getResourceId());
            if (status == null) {
                continue;
            }
            TCard card = cardsById.get(resource.getResourceId());
            if (card != null) {
                card.setStatus(status);
            }
        }
    }

    /**
     * Records a task lifecycle status change as an activity log entry in the linked ICS 214 form.
     *
     * <p>When a SAR task transitions between Planned → On Task → Returned, this method
     * appends a timestamped entry to the task's linked 214 (if any) so that the activity
     * log and T-card rack status stay synchronized.  The event type used is:
     * <ul>
     *   <li>{@link ActivityEventType#ID_RESOURCE_ON_TASK} when the new status is
     *       {@code "On Task"}.</li>
     *   <li>{@link ActivityEventType#ID_TASK_COMPLETED} when the new status is
     *       {@code "Returned"}.</li>
     * </ul>
     * Transitions to {@code "Planned"} do not generate an entry.</p>
     *
     * @param task      the task whose lifecycle status changed.
     * @param newStatus the new lifecycle status value.
     */
    public void recordTaskLifecycleTransition(SarTaskAssignment task, String newStatus) {
        if (task == null || newStatus == null) {
            return;
        }
        String normalized = newStatus.trim().toLowerCase(java.util.Locale.ROOT);
        String eventTypeId = switch (normalized) {
            case "assigned - enroute to assignment",
                 "assigned - on task",
                 "assigned - returning from assignment" -> ActivityEventType.ID_RESOURCE_ON_TASK;
            case "returned" -> ActivityEventType.ID_TASK_COMPLETED;
            default -> ActivityEventType.ID_FREE_TEXT;
        };
        String description = "Task status changed to " + normalized;

        // Also append to ICP communications log.
        CommunicationEntry comm = new CommunicationEntry();
        comm.setName(safe(task.getAssignmentTeamNumber()).isBlank()
                ? safe(task.getResourceIdentifier()) : safe(task.getAssignmentTeamNumber()));
        comm.setFunction("Task status update");
        comm.setPrimaryContact(LocalDateTime.now().withSecond(0).withNano(0) + " — " + normalized);
        data.getForm204().getCommunications().add(comm);

        // Append the same transition into the ICP-level ICS 214 communications/activity log.
        Ics214Form icpLog = data.getActivityLogs().stream()
                .filter(log -> log.getLogScope() == ActivityLogScope.ICP)
                .findFirst()
                .orElseGet(() -> {
                    Ics214Form created = new Ics214Form();
                    created.setName("ICP Communications Log");
                    data.getActivityLogs().add(created);
                    return created;
                });
        ActivityLogEntry icpEntry = new ActivityLogEntry();
        icpEntry.setTimestamp(LocalDateTime.now().withSecond(0).withNano(0));
        icpEntry.setEventTypeId(ActivityEventType.ID_FREE_TEXT);
        icpEntry.setResourceIdentifier(safe(task.getAssignmentTeamNumber()).isBlank()
                ? safe(task.getResourceIdentifier()) : safe(task.getAssignmentTeamNumber()));
        icpEntry.setNotableActivity(description);
        icpLog.getActivityLog().add(icpEntry);

        if (ActivityEventType.ID_FREE_TEXT.equals(eventTypeId)) {
            return;
        }
        // Find the linked ICS 214 form for this task.
        String assignmentId = task.getAssignmentId();
        for (Ics214Form log : data.getActivityLogs()) {
            if (assignmentId.equals(log.getLinkedSarTaskAssignmentId())) {
                ActivityLogEntry entry = new ActivityLogEntry();
                entry.setTimestamp(LocalDateTime.now().withSecond(0).withNano(0));
                entry.setEventTypeId(eventTypeId);
                entry.setResourceIdentifier(safe(task.getResourceIdentifier()).isBlank()
                        ? safe(task.getLeader()) : safe(task.getResourceIdentifier()));
                entry.setNotableActivity(description);
                log.getActivityLog().add(entry);
                break;
            }
        }
    }

    /**
     * Validates whether a task debriefing can be marked as completed and marks it if valid.
     *
     * <p>A debriefing is considered complete when all of the following are satisfied:</p>
     * <ul>
     *   <li>The task's lifecycle status is {@code "Returned"}.</li>
     *   <li>A debriefing supervisor name is non-blank.</li>
     *   <li>Debrief notes are non-blank.</li>
     *   <li>Areas not covered is non-blank.</li>
     *   <li>Hazards observed is non-blank.</li>
     * </ul>
     *
     * @param task the task assignment to validate and mark.
     * @return a list of human-readable validation errors, or an empty list when the
     *         debriefing is complete and the flag has been set.
     */
    public List<String> markDebriefingComplete(SarTaskAssignment task) {
        List<String> errors = new ArrayList<>();
        if (task == null) {
            errors.add("No task provided.");
            return errors;
        }
        if (!"returned".equalsIgnoreCase(task.getTaskLifecycleStatus())) {
            errors.add("Task must be in 'returned' status before debriefing can be completed.");
        }
        if (safe(task.getDebriefingSupervisor()).isBlank()) {
            errors.add("Debriefing supervisor name is required.");
        }
        if (safe(task.getDebriefNotes()).isBlank()) {
            errors.add("Debrief notes are required.");
        }
        if (safe(task.getAreasNotCovered()).isBlank()) {
            errors.add("Areas not covered is required.");
        }
        if (safe(task.getHazardsObserved()).isBlank()) {
            errors.add("Hazards observed is required.");
        }
        if (errors.isEmpty()) {
            task.setDebriefingCompleted(true);
            markDirty();
        }
        return errors;
    }


    private void addOrgCard(Map<String, TCard> wanted, Map<String, TCard> existing,
                             Map<String, TCard> byName,
                             String ref, String name, String radio, String phone, String roleLabel) {
        String safeName = safe(name);
        if (safeName.isBlank()) {
            return;
        }
        TCard card;
        if (existing.containsKey(ref)) {
            card = existing.get(ref);
        } else {
            String nameKey = safeName.trim().toLowerCase();
            card = byName.containsKey(nameKey) ? byName.get(nameKey) : newOrgCard(safeName, radio, phone);
        }
        card.setPersonName(safeName);
        card.setLocation(coalesce(card.getLocation(), "ICP"));
        card.setRadioChannel(coalesce(card.getRadioChannel(), radio));
        card.setPhoneNumber(coalesce(card.getPhoneNumber(), phone));
        card.setSourceRef(card.getSourceRef().isBlank() ? ref : card.getSourceRef());
        card.setNotes(notePreserving(card.getNotes(), roleLabel));
        wanted.put(ref, card);
        byName.putIfAbsent(safeName.trim().toLowerCase(), card);
    }

    /**
     * Sets the {@code sourceRef} of a T-card to {@code ref} for a task assignment.
     *
     * <p>A task assignment ({@code "sar:..."}) always takes priority over an org chart slot
     * ({@code "org:..."}).  Within a sync pass, the last task to claim a card wins so that
     * a resource reassigned from one task to another moves to the new task group in the rack
     * view.  A person may be on multiple tasks across an operational period (sequentially,
     * not simultaneously), and the rack should reflect their current assignment.</p>
     */
    private static void setTaskSourceRef(TCard card, String ref) {
        String current = card.getSourceRef();
        if (current.isBlank() || current.startsWith("org:") || current.startsWith("sar:")) {
            card.setSourceRef(ref);
        }
    }

    /**
     * Returns a deduplicated view of {@code resources} where entries for the same
     * resource (same UUID or same name case-insensitive) are collapsed to one entry.
     *
     * <p>UUID-based deduplication (when {@code resourceId} is set) takes priority over
     * name-based deduplication.  Among duplicates the entry with a non-blank
     * {@code resourceId} is preferred; otherwise the most specific card type wins.</p>
     */
    private static List<SarTaskResource> deduplicateResources(List<SarTaskResource> resources) {
        // First pass: index by resourceId for entries that carry one.
        Map<String, SarTaskResource> byId = new LinkedHashMap<>();
        for (SarTaskResource res : resources) {
            String id = res.getResourceId();
            if (!id.isBlank()) {
                byId.putIfAbsent(id, res);
            }
        }
        // Second pass: deduplicate by name, preferring entries already seen by UUID.
        Map<String, SarTaskResource> seen = new LinkedHashMap<>();
        for (SarTaskResource res : resources) {
            String name = res.getName();
            String key = (name == null ? "" : name).trim().toLowerCase();
            if (key.isBlank()) {
                continue;
            }
            // If this entry's UUID matches one already indexed, it is the authoritative
            // entry — place it under the current name key unconditionally (covers rename).
            String id = res.getResourceId();
            if (!id.isBlank() && byId.containsKey(id)) {
                seen.put(key, byId.get(id));
                continue;
            }
            if (!seen.containsKey(key)) {
                seen.put(key, res);
            } else {
                // Prefer the entry with a resourceId; if neither has one, prefer PERSONNEL
                // over non-PERSONNEL so that stale equipment entries do not survive.
                SarTaskResource existing = seen.get(key);
                if (!id.isBlank() && existing.getResourceId().isBlank()) {
                    seen.put(key, res);
                } else if (existing.getCardType() != null
                        && existing.getCardType() != TCardType.PERSONNEL
                        && (res.getCardType() == null || res.getCardType() == TCardType.PERSONNEL)) {
                    seen.put(key, res);
                }
            }
        }
        return new ArrayList<>(seen.values());
    }

    private TCard newPersonnelCard(String name) {
        TCard card = new TCard();
        card.setCardType(TCardType.PERSONNEL);
        card.setPersonName(name);
        card.setNumberOfPersons(1);
        return card;
    }

    private TCard newOrgCard(String name, String radio, String phone) {
        TCard card = newPersonnelCard(name);
        card.setLocation("ICP");
        if (radio != null && !radio.isBlank()) {
            card.setRadioChannel(radio);
        }
        if (phone != null && !phone.isBlank()) {
            card.setPhoneNumber(phone);
        }
        return card;
    }

    /**
     * Returns the effective display name for a T-card.
     * Returns the display name to use when matching or listing a T-card.
     *
     * <p>For PERSONNEL cards the person's name ({@code personName}) is used.
     * For all other card types (equipment, canines, aircraft, crews, etc.)
     * {@code resourceIdentifier} is the authoritative display name —
     * the generalised identifier: dog call sign, apparatus name, tail number, etc.</p>
     */
    private static String effectiveTCardName(TCard card) {
        if (card.getCardType() != TCardType.PERSONNEL) {
            return card.getResourceIdentifier().trim();
        }
        return card.getPersonName().trim();
    }

    /** Returns {@code preferred} if non-blank, otherwise {@code fallback}. */
    private String coalesce(String preferred, String fallback) {
        return (preferred != null && !preferred.isBlank()) ? preferred : safe(fallback);
    }

    /**
     * Returns the existing notes value if non-blank, otherwise the generated label.
     * This prevents overwriting operator-entered notes with role labels on every sync.
     */
    private String notePreserving(String existingNotes, String generatedLabel) {
        return (existingNotes != null && !existingNotes.isBlank()) ? existingNotes : generatedLabel;
    }

    private SarTaskAssignment mergeSarTask(SarTaskAssignment existing, SarTaskAssignment scaffold) {
        existing.setAssignmentId(scaffold.getAssignmentId());
        if (!safe(scaffold.getAssignmentTeamNumber()).isBlank()) {
            existing.setAssignmentTeamNumber(scaffold.getAssignmentTeamNumber());
        }
        if (!safe(scaffold.getResourceType()).isBlank()) {
            existing.setResourceType(scaffold.getResourceType());
        }
        if (!safe(scaffold.getTaskType()).isBlank()) {
            existing.setTaskType(scaffold.getTaskType());
        }
        existing.setIncidentName(scaffold.getIncidentName());
        existing.setResourceIdentifier(scaffold.getResourceIdentifier());
        existing.setLeaderRole(scaffold.getLeaderRole());
        existing.setLeader(scaffold.getLeader());
        if (safe(existing.getAssignment()).isBlank()) {
            existing.setAssignment(scaffold.getAssignment());
        }
        existing.setContact(scaffold.getContact());
        existing.setBranch(scaffold.getBranch());
        existing.setDivision(scaffold.getDivision());
        existing.setGroup(scaffold.getGroup());
        existing.setStagingArea(scaffold.getStagingArea());
        existing.setTaskMap(scaffold.getTaskMap());
        existing.setOperationsSectionChiefName(scaffold.getOperationsSectionChiefName());
        existing.setOperationsSectionChiefContact(scaffold.getOperationsSectionChiefContact());
        existing.setSecondaryManagementRoleLabel(scaffold.getSecondaryManagementRoleLabel());
        existing.setSecondaryManagementName(scaffold.getSecondaryManagementName());
        existing.setSecondaryManagementContact(scaffold.getSecondaryManagementContact());
        if (safe(existing.getTransportationInstructions()).isBlank()) {
            existing.setTransportationInstructions(scaffold.getTransportationInstructions());
        }
        if (safe(existing.getSpecialEquipment()).isBlank()) {
            existing.setSpecialEquipment(scaffold.getSpecialEquipment());
        }
        existing.setCommunications(scaffold.getCommunications());
        if (safe(existing.getPreparedByName()).isBlank()) {
            existing.setPreparedByName(scaffold.getPreparedByName());
        }
        if (safe(existing.getPreparedByPositionTitle()).isBlank()) {
            existing.setPreparedByPositionTitle(scaffold.getPreparedByPositionTitle());
        }
        if (existing.getPreparedDateTime() == null) {
            existing.setPreparedDateTime(scaffold.getPreparedDateTime());
        }
        if (safe(existing.getDebriefPreparedByName()).isBlank()) {
            existing.setDebriefPreparedByName(scaffold.getDebriefPreparedByName());
        }
        if (safe(existing.getDebriefPreparedByPositionTitle()).isBlank()) {
            existing.setDebriefPreparedByPositionTitle(scaffold.getDebriefPreparedByPositionTitle());
        }
        if (existing.getResourcesAssigned().isEmpty()) {
            existing.setResourcesAssigned(scaffold.getResourcesAssigned());
        } else {
            syncLeadResource(existing.getResourcesAssigned(), scaffold.getResourcesAssigned());
        }
        return existing;
    }

    private void syncLeadResource(List<SarTaskResource> existingResources, List<SarTaskResource> scaffoldResources) {
        if (scaffoldResources.isEmpty()) {
            return;
        }
        SarTaskResource lead = scaffoldResources.get(0);
        if (existingResources.isEmpty()) {
            existingResources.add(lead);
            return;
        }
        // Find the existing resource that matches the scaffold leader by UUID or name so that
        // leader-derived fields are only applied to the correct person, not to a canine or
        // equipment resource that happens to be first in the list.
        String leadId   = safe(lead.getResourceId());
        String leadName = safe(lead.getName()).trim().toLowerCase();
        SarTaskResource target = null;
        for (SarTaskResource res : existingResources) {
            if (!leadId.isBlank() && leadId.equals(safe(res.getResourceId()))) {
                target = res;
                break;
            }
            if (leadId.isBlank() && !leadName.isBlank()
                    && leadName.equals(safe(res.getName()).trim().toLowerCase())) {
                target = res;
                break;
            }
        }
        if (target == null) {
            // No matching resource found — do not overwrite unrelated resources.
            return;
        }
        target.setFunction(lead.getFunction());
        target.setIcsPosition(lead.getIcsPosition());
        target.setHomeAgency(lead.getHomeAgency());
        // Only overwrite the name from the ICS 204 scaffold when the existing resource does
        // not already carry a UUID link to a canonical TCard.
        if (target.getResourceId().isBlank()) {
            target.setName(lead.getName());
        }
        // Back-fill the UUID from the existing resource so the scaffold carries it forward.
        if (!target.getResourceId().isBlank() && lead.getResourceId().isBlank()) {
            lead.setResourceId(target.getResourceId());
        }
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
        if (data.getForm201() == null) {
            data.setForm201(new Ics201Form());
        }
        if (data.getForm202() == null) {
            data.setForm202(new org.sarmanagement.icsforms.model.Ics202Form());
        }
        if (data.getForm204() == null) {
            data.setForm204(new Ics204Form());
        }
        if (data.getAdditionalForms204() == null) {
            data.setAdditionalForms204(new ArrayList<>());
        }
        if (data.getActivityLogs() == null) {
            data.setActivityLogs(new ArrayList<>());
        }
        if (data.getActivityEventTypes() == null || data.getActivityEventTypes().isEmpty()) {
            data.setActivityEventTypes(new ArrayList<>(ActivityEventType.defaultTypes()));
        } else {
            // Backfill any built-in types introduced after the initial seed.
            List<String> existing = data.getActivityEventTypes().stream()
                    .map(ActivityEventType::getId).toList();
            for (ActivityEventType builtIn : ActivityEventType.defaultTypes()) {
                if (!existing.contains(builtIn.getId())) {
                    data.getActivityEventTypes().add(builtIn);
                }
            }
        }
        if (data.getSarTaskAssignments() == null) {
            data.setSarTaskAssignments(new ArrayList<>());
        }
        if (data.getClueLogEntries() == null) {
            data.setClueLogEntries(new ArrayList<>());
        }
        if (data.getTCards() == null) {
            data.setTCards(new ArrayList<>());
        }
        if (data.getIncidentMode() == null) {
            data.setIncidentMode(org.sarmanagement.icsforms.model.IncidentMode.SAR);
        }
        if (data.getIapPhase() == null) {
            data.setIapPhase(org.sarmanagement.icsforms.model.IapPhase.PRE_OP);
        }
        if (data.getSchemaVersion() < AppData.CURRENT_SCHEMA_VERSION) {
            data.setSchemaVersion(AppData.CURRENT_SCHEMA_VERSION);
        }
        activeLinkSource = initialLinkSource(data);
        synchronizeLinkedFields(activeLinkSource);
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

    private LinkSource initialLinkSource(AppData data) {
        OrganizationalChart organizationalChart = data.getOrganizationalChart();
        if (organizationalChart != null
                && (!organizationalChart.getIncidentCommanders().isEmpty()
                || !safe(organizationalChart.getOperationsSectionChiefName()).isBlank())) {
            return LinkSource.ORG_CHART;
        }
        return LinkSource.NONE;
    }

    private void updateClueTaskLabels(Map<String, SarTaskAssignment> tasksById) {
        for (ClueLogEntry entry : data.getClueLogEntries()) {
            SarTaskAssignment task = tasksById.get(entry.getAssignmentId());
            if (task != null) {
                entry.setDetectingTask(task.getAssignmentTeamNumber());
            }
        }
    }

    /**
     * Records a clue log entry as an activity log entry in the linked ICS 214 form.
     *
     * <p>When a clue is added via the shared Clue Log panel and an {@code assignmentId} is
     * set on the entry, this method appends a {@link ActivityEventType#ID_CLUE_DETECTED}
     * entry to the matching task-linked ICS 214 form so both logs stay synchronized.</p>
     *
     * @param clue the newly added clue log entry.
     */
    public boolean propagateClueToActivityLog(ClueLogEntry clue) {
        if (clue == null || safe(clue.getAssignmentId()).isBlank()) {
            return false;
        }
        for (Ics214Form log : data.getActivityLogs()) {
            if (clue.getAssignmentId().equals(log.getLinkedSarTaskAssignmentId())) {
                LocalDateTime ts = clue.getDateTimeCollected() != null
                        ? clue.getDateTimeCollected()
                        : LocalDateTime.now().withSecond(0).withNano(0);
                // Avoid duplicating entries already propagated with the same timestamp and clue type.
                boolean alreadyPresent = log.getActivityLog().stream().anyMatch(e ->
                        ActivityEventType.ID_CLUE_DETECTED.equals(e.getEventTypeId())
                        && ts.equals(e.getTimestamp()));
                if (alreadyPresent) {
                    return false;
                }
                ActivityLogEntry entry = new ActivityLogEntry();
                entry.setTimestamp(ts);
                entry.setEventTypeId(ActivityEventType.ID_CLUE_DETECTED);
                entry.setResourceIdentifier(safe(clue.getDetectingTask()));
                String activity = safe(clue.getDescription());
                if (!safe(clue.getLocation()).isBlank()) {
                    activity = safe(clue.getLocation()) + (activity.isBlank() ? "" : ": " + activity);
                }
                entry.setNotableActivity(activity);
                log.getActivityLog().add(entry);
                return true;
            }
        }
        return false;
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
        return normalized.equals("incident commander") || normalized.equals("unified command")
                || normalized.equals("ic");
    }

    private boolean matchesOperationsSectionChiefRole(String value) {
        return normalizeRole(value).equals("operations section chief");
    }

    private boolean matchesPlanningSectionChiefRole(String value) {
        return normalizeRole(value).equals("planning section chief");
    }

    private boolean matchesLogisticsSectionChiefRole(String value) {
        return normalizeRole(value).equals("logistics section chief");
    }

    private boolean matchesFinanceAdminSectionChiefRole(String value) {
        String normalized = normalizeRole(value);
        return normalized.equals("finance admin section chief")
                || normalized.equals("finance administration section chief");
    }

    private boolean matchesDocumentationUnitLeaderRole(String value) {
        return normalizeRole(value).equals("documentation unit leader");
    }

    private boolean matchesSafetyOfficerRole(String value) {
        return normalizeRole(value).equals("safety officer");
    }

    private String normalizeRole(String value) {
        return safe(value).toLowerCase().replaceAll("[^a-z0-9]+", " ").trim().replaceAll("\\s+", " ");
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * Returns {@code true} when {@code value} looks like a phone number rather than a radio
     * channel name or frequency.
     *
     * <p>A value is treated as a phone number when — after stripping common phone-number
     * formatting characters ({@code - . ( ) + space}) — the remaining string contains at
     * least 7 digit characters and no other non-digit characters.  Values that contain
     * letters, or that have fewer than 7 digits after stripping (e.g. short radio channel
     * codes like "Ch 5" or "UHF-3"), are treated as radio channel identifiers.
     * VHF/UHF frequencies with a decimal point (e.g. "155.340") are also rejected because
     * they contain a decimal separator that survives stripping and they have fewer than 7
     * digits total.</p>
     *
     * @param value string to classify; {@code null} or blank returns {@code false}.
     * @return {@code true} if the value looks like a phone number.
     */
    static boolean looksLikePhoneNumber(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        // Strip characters that appear in formatted phone numbers but not channel names.
        String digits = value.replaceAll("[\\-. ()+\\s]", "");
        // Must be all digits after stripping and have at least 7 of them.
        return digits.length() >= 7 && digits.chars().allMatch(Character::isDigit);
    }

    /**
     * Adds the given T-card as a resource entry to the specified SAR task assignment.
     *
     * <p>If the task's {@code resourcesAssigned} list already contains an entry with the
     * same name (case-insensitive) the call is a no-op to avoid creating duplicates.</p>
     *
     * @param card T-card to add.
     * @param task target SAR task assignment.
     * @param asLeader when {@code true}, sets the card as the task's leader instead of
     *                 appending to the resources list.
     */
    public void addTCardToSarTask(TCard card, SarTaskAssignment task, boolean asLeader) {
        if (card == null || task == null) {
            return;
        }
        String name = effectiveTCardName(card);
        if (name.isBlank()) {
            return;
        }
        if (asLeader) {
            task.setLeader(name);
            if (card.getPhoneNumber() != null && !card.getPhoneNumber().isBlank()) {
                task.setContact(card.getPhoneNumber());
            } else if (card.getRadioChannel() != null && !card.getRadioChannel().isBlank()) {
                task.setContact(card.getRadioChannel());
            }
        } else {
            List<SarTaskResource> resources = task.getResourcesAssigned();
            if (resources == null) {
                resources = new ArrayList<>();
                task.setResourcesAssigned(resources);
            }
            String cardId = card.getResourceId();
            // Prefer UUID-based duplicate check; fall back to name for legacy entries.
            boolean alreadyPresent = resources.stream().anyMatch(r -> {
                if (!cardId.isBlank() && !r.getResourceId().isBlank()) {
                    return cardId.equals(r.getResourceId());
                }
                String nameLower = name.trim().toLowerCase();
                return nameLower.equals(r.getName() == null ? "" : r.getName().trim().toLowerCase());
            });
            if (!alreadyPresent) {
                SarTaskResource res = new SarTaskResource();
                res.setName(name);
                res.setCardType(card.getCardType());
                res.setResourceId(cardId);
                if (card.getCardType() == TCardType.PERSONNEL) {
                    res.setHomeAgency(card.getHomeAgency() == null ? "" : card.getHomeAgency());
                }
                resources.add(res);
            }
        }
    }

    /**
     * Sets the name field for an ICS org chart role.
     *
     * <p>The {@code roleLabel} must match one of the values returned by
     * {@link #getOrgChartRoleLabels()}.  If the label is not recognised the call is a no-op.</p>
     *
     * @param roleLabel human-readable role label (e.g. {@code "Safety Officer"}).
     * @param personName name to assign to the role.
     */
    public void setOrgChartRoleName(String roleLabel, String personName) {
        OrganizationalChart chart = data.getOrganizationalChart();
        if (chart == null || roleLabel == null) {
            return;
        }
        String name = personName == null ? "" : personName;
        switch (roleLabel) {
            case "Incident Commander"        -> { List<String> ics = chart.getIncidentCommanders(); if (!ics.contains(name)) { ics.add(name); chart.setIncidentCommanders(ics); } }
            case "Safety Officer"            -> chart.setSafetyOfficerName(name);
            case "Public Information Officer"-> chart.setPublicInformationOfficerName(name);
            case "Liaison Officer"           -> chart.setLiaisonOfficerName(name);
            case "Operations Section Chief"  -> chart.setOperationsSectionChiefName(name);
            case "Planning Section Chief"    -> chart.setPlanningSectionChiefName(name);
            case "Logistics Section Chief"   -> chart.setLogisticsSectionChiefName(name);
            case "Finance/Admin Section Chief"-> chart.setFinanceAdminSectionChiefName(name);
            case "Documentation Unit Leader" -> chart.setDocumentationUnitLeaderName(name);
            case "Communications Unit Leader"-> chart.setCommunicationsUnitLeaderName(name);
            case "Communications Technician" -> chart.setCommunicationsTechnicianName(name);
            case "Staging Area Manager"      -> chart.setStagingAreaManagerName(name);
            case "Resources Unit Leader"     -> chart.setResourcesUnitLeaderName(name);
            case "Situation Unit Leader"     -> chart.setSituationUnitLeaderName(name);
            case "Demobilization Unit Leader"-> chart.setDemobilizationUnitLeaderName(name);
            case "Supply Unit Leader"        -> chart.setSupplyUnitLeaderName(name);
            default                          -> { /* unrecognised role — no-op */ }
        }
    }

    /**
     * Returns the ordered list of ICS org chart role labels supported by
     * {@link #setOrgChartRoleName}.
     *
     * @return unmodifiable list of role label strings.
     */
    public static List<String> getOrgChartRoleLabels() {
        return List.of(
                "Incident Commander",
                "Safety Officer",
                "Public Information Officer",
                "Liaison Officer",
                "Operations Section Chief",
                "Planning Section Chief",
                "Logistics Section Chief",
                "Finance/Admin Section Chief",
                "Documentation Unit Leader",
                "Communications Unit Leader",
                "Communications Technician",
                "Staging Area Manager",
                "Resources Unit Leader",
                "Situation Unit Leader",
                "Demobilization Unit Leader",
                "Supply Unit Leader"
        );
    }
}
