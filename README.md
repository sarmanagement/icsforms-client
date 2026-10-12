# icsforms-client

`icsforms-client` is a Java Swing desktop application for authoring Incident Command System (ICS) incident forms with local JSON persistence and PDF export. Development is ongoing and the application is still evolving as additional forms and functionality are added.

This project is intended to support the **SAR** (Search and Rescue) community, but it is also useful for generic ICS incident planning and documentation. The application currently supports ICS 201, ICS 202, ICS 203, ICS 204, ICS 205A, ICS 206, an adapted canine ICS 206, ICS 207, ICS 208, ICS 214, and ICS 215A PDF forms, along with SAR task assignment forms, a clue log, T-cards, and an organizational chart.  I got tired of copying the same header information from one form to another by hand, and having to subject people to my handwriting in SAR Task Assignment Form Debriefing sections, so this tool is intended to make it easier to author ICS forms and share them with others in a clean, legible format.

Currently, this is a desktop application with a Swing UI. It is not a web application, and it does not support multi-user collaboration. Each incident is persisted locally at `~/.icsforms/incidents/<incidentId>.json`, with its own `.json.bak` backup; the install's node identity is kept in `~/.icsforms/node.json`. On first launch, an existing `~/.icsforms/incident.json` is imported as a new incident if no incidents exist yet. The original is left untouched and `incident.json.migrated` prevents repeat imports. File → Import JSON can also import an external workspace as a separate incident. PDF export is supported for each form, as well as bulk export of all forms and a merged IAP bundle PDF. An H2-backed store and offline-to-remote synchronization are planned, but not yet implemented.

This project is open source under the Apache 2.0 license. Contributions are welcome.

## Current UI workflow

1. At launch a startup dialog appears asking you to select an action:
   - **New incident — Pre-Operational (planning):** start a workspace before the incident response begins.
   - **New incident — Initial Incident Response:** an incident has started; ICS 201 is the primary capture tool.
   - **New incident — Subsequent Operational Period:** open a fresh workspace for a new operational period, with ICS 201 read-only.
   - **Open existing incident:** continue working on a previously saved incident.
   - **Open existing incident and add a new operational period:** load an existing workspace and advance it to the next operational period, preserving the org chart and ICS 201 history while clearing the operational-period forms.
2. Select the incident mode: **SAR** (default) or **Generic**.
   - SAR mode enables SAR Task Assignment forms that provide further detail for each Assignment listed on a ICS 204 and a clue log.
   - SAR Task Assignment forms support debriefing, including structured capture of POD factors for SAR operations.
3. Enter shared incident context values on the **Shared** tab:
   - incident name
   - operational period
   - preparer identity
4. Maintain the current organization on **Org Chart**. The **Agency/Organization Representatives** section immediately before **Additional Positions** lets you add, edit, or remove free-text agency and representative names with optional radio and phone contacts; no T-card is required. Use **ICS 203 details…** for deputies, Operations branches/divisions, and assignment-list preparer information; its agency editor stays synchronized with the main tab. Names may include agencies, slash-separated shift assignments, and `(T)` for trainees.
5. Complete the appropriate forms based on the current phase (see below).
6. Save locally or allow autosave to persist the incident workspace.
7. Export individual PDFs, all PDFs as separate files, or the merged **Export IAP Bundle** PDF.

File → New always creates a separate incident. File → Duplicate Incident copies the current document under a new ID; File → Import JSON and Export JSON move portable copies between stores. The picker lists active incidents, and detects another editor's advisory lock rather than silently overwriting its work. A stale revision is rejected, with the option to duplicate unsaved work or reload.

## Current features

- Shared incident context for incident name, operational period, and preparer identity.
- Startup dialog to choose incident phase and mode before the main window opens.
- Incident mode toggle for **SAR** and **Generic** workflows.
- Three IAP phases: **Pre-Operational**, **Initial Incident Response**, and **Subsequent Operational Period**.
- Tabbed Swing UI for:
  - Shared context
  - Org Chart
  - ICS 201
  - ICS 202
  - ICS 204
  - Medical Plan (ICS 206)
  - Canine Medical Plan (ICS 206CANINE, adapted; eligible in SAR mode or when a canine T-card exists)
  - Role-oriented dashboard ordering for IC/ICP, Safety Officer, Operations, and Communications
  - SAR Tasks (SAR mode only)
  - Clue Log (SAR mode only)
  - Optional Incident Open Action Tracker (ICS 233-CG), available in both SAR and Generic modes as a separate tab.
  - T-Cards
  - Activity Logs (ICS 214)
- SAR Task Assignment forms with debriefing and POD factoring support.
- Safety workspace for ICS 215A hazard analysis and editable ICS 208 safety messages/plans. Hazard libraries can be loaded/saved as UTF-8 CSV; imported entries can be stored separately from incident analysis. SAR debrief hazard reports are reviewable from the Safety tab. Safety forms are available for individual export and are excluded from IAP bundles by default until selected.
- ICS 208 authoring includes Safety Officer and Hazardous Materials Group Supervisor role metadata to support later role-specific workflows, including ICS 208 HM.
- Local JSON persistence at `~/.icsforms/incidents/<incidentId>.json`.
- Backup-assisted recovery from each incident's `.json.bak`.
- PDF export for each supported form.
- ICS 206 medical plans use the shared incident/operational-period context. Human plans support a simple emergency-instruction mode and detailed records. Human and adapted canine plans share the **Medical** tab; the canine subtab appears for eligible SAR/canine incidents and can also be opened from **View** for saved plans. Canine plans are explicitly adapted, not an official FEMA variant. IAP inclusion is selected only in ICS 202; medical-plan tabs do not have IAP inclusion toggles. Medical-plan signature lines are printed for signing and are not edited in the application.
- Canine veterinary directories are incident-owned copies, not live links to source files. JSON regional files use schema version 1 (`schemaVersion` and `facilities`); CSV uses UTF-8, quoted fields and this exact header: `id,name,town,address,contactNumbers,available24Hours,availabilityNotes,capabilities,verifiedAt,latitude,longitude`. Multiple contacts are semicolon-separated. Blank availability is unknown, not confirmation of 24-hour service. Directory edits require explicit export and do not overwrite sources implicitly.
- Address geocoding and driving routes are separate services. OpenRouteService is the default provider; requests send the entered base/veterinary addresses or coordinates to that third party. Configure the key in **Configuration > Medical Routing (OpenRouteService)…**, set `ORS_API_KEY`, or place the key in `~/.icsforms/ors-api-key`; the environment variable takes precedence, and credentials are not stored in incident JSON or regional files. Verify current terms, quotas, and service limits before operational use. Unresolved/unreachable locations remain visible for manual handling; any straight-line estimate is explicitly labeled and is not a driving time.
- Role dashboards reorder the same editors; they do not duplicate incident data. Choose **View > Dashboard** (IC/ICP is the default). ICS 205A remains available. The separate ICS 205 radio communications-plan work tracked by issue #14 is not implemented by the dashboard work.
- **Export All PDFs** for individual per-form PDF files.
- **Export IAP Bundle** for a single merged PDF bundle of selected forms with a cover page.
- **ICS 203 – Organization Assignment List** PDF from the Org Chart, alongside the existing ICS 207 chart. Select ICS 203 in **Export forms as PDF…** for an individual PDF or a combined IAP. Its optional **Include ICS 203 in IAP** setting defaults on, but a bundle includes it only when the Org Chart has entries, immediately after ICS 202. Unused positions remain blank labeled rows. When no Operations branches are entered, division/group rows come from ICS 204 and SAR task assignments. Additional entries continue on further pages; paper size, margins, and display timezone follow the shared PDF settings.
- Validation that blocks export when required fields are missing.

### Open actions and clue follow-up

Enable the tracker under **Configuration → Open Action Tracker** to track incident
actions independently of SAR tasks and clues. Disabling the tab does not delete
its entries; incident-level actions also carry forward to subsequent operational
periods. POC means *Point of Contact*: the person or section responsible for
the action. Mark Briefed once that person has been informed of and acknowledged
the assignment. Completed actions remain available in the Completed/All views;
target dates highlight overdue actions in red and actions due within a day in yellow.
The tracker is disabled by default for new incidents; startup only selects phase
and mode.

The clue details editor retains follow-up POC assignment history without changing
the existing clue fields, duplicate detection, or activity-log synchronization.
The standard clue-log PDF remains the default. In the **Export forms as PDF**
dialog, select **Clue Log** and check **Export SAR clue log as adapted ICS 233**
for a table containing every clue, ordered open, assigned, then followed
up on. Its Item column uses Description, Location, and Found by headings, and its
POC column includes assignment history. The regular tracker prints only the current POC.

ICS 233-CG output supports portrait (default) or landscape on the configured paper
size. Choose **ICS 233 orientation** in the PDF generation dialog for either
individual PDFs or the combined IAP bundle. Each dialog starts with portrait and
standard clue output, regardless of legacy saved export flags. These print-time
choices are not saved with the incident. Uncheck **Clue Log** to omit clues
entirely; the enabled incident tracker remains a separate export selection.

## Incident phases

The application models three phases of an incident lifecycle, selectable at startup and changeable from the **Configuration → IAP Phase** menu:

### (a) Pre-Operational (planning)

The team is preparing for a potential or anticipated incident before any response has begun. ICS 201 is available to pre-stage information but is not yet in active use. Resources may be placed in an "Ordered" status on the T-card rack.

### (b) Initial Incident Response

An incident has started. ICS 201 – Incident Briefing is the **primary capture tool** for the incident commander: map/sketch, situation summary, health and safety briefing, objectives, current actions, and the organizational chart and resource summary. All ICS 201 fields are fully editable. ICS 202 and ICS 204 are also available for concurrent planning.

### (c) Subsequent Operational Periods

The incident has progressed beyond the initial response. ICS 201 is **locked (read-only)** and attached to the IAP bundle as the historical initial-response record. ICS 202 (Incident Objectives) and ICS 204 (Assignment List) become the primary forms for each subsequent operational period. When advancing to a new operational period the operational-period forms are cleared while the organizational chart and ICS 201 are preserved as historical context.

## Repository structure

All production source lives under `src/main/java/org/sarmanagement/icsforms/`, with unit tests mirroring that layout under `src/test/java/`. The Maven build is defined in `pom.xml`.

**`App.java`** is the application entry point. It initialises the Swing look-and-feel, constructs the top-level `AppController`, and opens the `MainFrame` window.

**`model/`** holds the plain-Java data model. `AppData` is the root object serialised to each incident's JSON document; it owns `IncidentContext` (shared header fields, mode, and IAP phase) alongside one instance of each form model class (`Ics201Form`, `Ics202Form`, `Ics204Form`, `Ics214Form`, etc.). Supporting value types for SAR task assignments, T-cards, clue log entries, activity log entries, and the organizational chart also live here.

**`ui/`** contains the Swing presentation layer. `MainFrame` builds the top-level tabbed pane and houses all the per-form panels. `AppController` wires the model to the UI and drives save, load, and export actions. Each form has a dedicated panel class (e.g. `Ics201Panel`, `Ics202Panel`) that owns its own sub-tabs or sections. `UiSupport` provides shared Swing helper utilities used across panels.

**`pdf/`** is responsible for turning the in-memory model into PDF files. `AbstractPdfRenderer` and `PdfFormRenderer` provide the shared drawing infrastructure (Apache PDFBox). Each form has a dedicated renderer (e.g. `Ics201PdfRenderer`, `Ics204PdfRenderer`). `CoverPageRenderer` produces the IAP bundle cover page. `PdfExportService` coordinates single-form export, bulk export, and the merged IAP bundle export.

**`persistence/`** defines the store-independent `IncidentStore` contract. `FileIncidentStore` stores each incident as JSON with a separate backup, revision checks, and advisory locks; `LocalRepository` remains for compatibility with older integrations.

**`validation/`** contains pre-export checks. `IncidentValidator` walks the model and collects `ValidationMessage` instances for any required fields that are missing or invalid, blocking PDF export until the workspace is clean.

## Build

```bash
mvn package
```

## Run

```bash
java -jar target/icsforms-client-0.1.1-SNAPSHOT.jar
```

## Notes

- PDF output is structured for field use and printing, not a pixel-perfect facsimile of government forms.
- Date/time entry currently uses validated text or spinner inputs in `yyyy-MM-dd`, `HH:mm`, or `yyyy-MM-dd HH:mm` formats depending on the form.
- Development is ongoing; workflows, form coverage, and export behavior will continue to expand.
- ORS official references: [services](https://openrouteservice.org/services/), [geocoder endpoint](https://giscience.github.io/openrouteservice/api-reference/endpoints/geocoder/), and [matrix endpoint](https://github.com/GIScience/openrouteservice/blob/main/docs/api-reference/endpoints/matrix/index.md). OSRM's table API is an alternative routing service but needs a separate geocoder and coordinates: [OSRM API](https://project-osrm.org/docs/v5.5.1/api/).

## Generative AI

This codebase has been developed with the assistance of generative AI tools, particularly GitHub Copilot.
