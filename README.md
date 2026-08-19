# icsforms-client

`icsforms-client` is a Java Swing desktop application for authoring Incident Command System (ICS) incident forms with local JSON persistence and PDF export. Development is ongoing and the application is still evolving as additional forms and functionality are added.

## Current UI workflow

1. At launch a startup dialog appears asking you to select an action:
   - **New incident — Pre-Operational (planning):** start a workspace before the incident response begins.
   - **New incident — Initial Incident Response:** an incident has started; ICS 201 is the primary capture tool.
   - **New incident — Subsequent Operational Period:** open a fresh workspace for a new operational period, with ICS 201 read-only.
   - **Open existing incident:** continue working on a previously saved incident.
   - **Open existing incident and add a new operational period:** load an existing workspace and advance it to the next operational period, preserving the org chart and ICS 201 history while clearing the operational-period forms.
2. Select the incident mode: **SAR** (default) or **Generic**.
3. Enter shared incident context values on the **Shared** tab:
   - incident name
   - operational period
   - preparer identity
4. Maintain the current organization on **Org Chart**.
5. Complete the appropriate forms based on the current phase (see below).
6. Save locally or allow autosave to persist the incident workspace.
7. Export individual PDFs, all PDFs as separate files, or the merged **Export IAP Bundle** PDF.

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
  - SAR Tasks (SAR mode only)
  - Clue Log (SAR mode only)
  - T-Cards
  - Activity Logs (ICS 214)
- SAR Task Assignment forms with debriefing and POD factoring support.
- Local JSON persistence at `~/.icsforms/incident.json`.
- Backup-assisted recovery from `~/.icsforms/incident.json.bak`.
- PDF export for each supported form.
- **Export All PDFs** for individual per-form PDF files.
- **Export IAP Bundle** for a single merged PDF bundle with a cover page.
- Validation that blocks export when required fields are missing.

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

**`model/`** holds the plain-Java data model. `AppData` is the root object that is serialised to and deserialised from `~/.icsforms/incident.json`; it owns `IncidentContext` (shared header fields, mode, and IAP phase) alongside one instance of each form model class (`Ics201Form`, `Ics202Form`, `Ics204Form`, `Ics214Form`, etc.). Supporting value types for SAR task assignments, T-cards, clue log entries, activity log entries, and the organizational chart also live here.

**`ui/`** contains the Swing presentation layer. `MainFrame` builds the top-level tabbed pane and houses all the per-form panels. `AppController` wires the model to the UI and drives save, load, and export actions. Each form has a dedicated panel class (e.g. `Ics201Panel`, `Ics202Panel`) that owns its own sub-tabs or sections. `UiSupport` provides shared Swing helper utilities used across panels.

**`pdf/`** is responsible for turning the in-memory model into PDF files. `AbstractPdfRenderer` and `PdfFormRenderer` provide the shared drawing infrastructure (Apache PDFBox). Each form has a dedicated renderer (e.g. `Ics201PdfRenderer`, `Ics204PdfRenderer`). `CoverPageRenderer` produces the IAP bundle cover page. `PdfExportService` coordinates single-form export, bulk export, and the merged IAP bundle export.

**`persistence/`** handles reading and writing the incident workspace. `LocalRepository` serialises `AppData` to JSON (Jackson) and maintains a rolling backup at `incident.json.bak`.

**`validation/`** contains pre-export checks. `IncidentValidator` walks the model and collects `ValidationMessage` instances for any required fields that are missing or invalid, blocking PDF export until the workspace is clean.

## Build

```bash
mvn package
```

## Run

```bash
java -jar target/icsforms-client-0.1.0-SNAPSHOT.jar
```

## Notes

- PDF output is structured for field use and printing, not a pixel-perfect facsimile of government forms.
- Date/time entry currently uses validated text or spinner inputs in `yyyy-MM-dd`, `HH:mm`, or `yyyy-MM-dd HH:mm` formats depending on the form.
- Development is ongoing; workflows, form coverage, and export behavior will continue to expand.
