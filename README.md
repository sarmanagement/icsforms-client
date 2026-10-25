# icsforms-client

`icsforms-client` is a Java Swing desktop application for authoring Incident Command System (ICS) incident forms with local JSON persistence and PDF export. Development is ongoing and the application is still evolving as additional forms and functionality are added.

This project is intended to support the **SAR** (Search and Rescue) community, but it is also useful for generic ICS incident planning and documentation. The application currently supports ICS 201, ICS 202, ICS 203, ICS 204, ICS 205, ICS 205A, ICS 206, an adapted canine ICS 206, ICS 207, ICS 208, ICS 214, and ICS 215A PDF forms, along with SAR task assignment forms, a clue log, T-cards, and an organizational chart.  I got tired of copying the same header information from one form to another by hand, and having to subject people to my handwriting in SAR Task Assignment Form Debriefing sections, so this tool is intended to make it easier to author ICS forms and share them with others in a clean, legible format.

Currently, this is a desktop application with a Swing UI. It is not a web application, and it does not support multi-user collaboration. Each incident is persisted locally at `~/.icsforms/incidents/<incidentId>.json`, with its own `.json.bak` backup; the install's node identity is kept in `~/.icsforms/node.json`. On first launch, an existing `~/.icsforms/incident.json` is imported as a new incident if no incidents exist yet. The original is left untouched and `incident.json.migrated` prevents repeat imports. File → Import JSON can also import an external workspace as a separate incident. PDF export is supported for each form, as well as bulk export of all forms and a merged IAP bundle PDF. An H2-backed store and offline-to-remote synchronization are planned, but not yet implemented.

This project is open source under the Apache 2.0 license. Contributions are welcome.

## Current UI workflow

1. At launch a startup dialog appears asking you to select an action:
   - **New incident — Pre-Operational (planning):** start a workspace before the incident response begins.
   - **New incident — Initial Incident Response:** an incident has started; ICS 201 is the primary capture tool.
   - **New incident — Subsequent Operational Period:** open a fresh workspace for a new operational period, with ICS 201 read-only.
   - **Open existing incident:** continue working on a previously saved incident.
   - **Open existing incident and add a new operational period:** load an existing workspace and advance it to the next operational period, preserving the org chart and ICS 201 history while clearing the operational-period forms. ICS 205 can optionally be brought forward using the explicit checkbox.
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
  - Optional ICS 205 Incident Radio Communications Plan (SAR and Generic modes)
  - Medical Plan (ICS 206)
  - Canine Medical Plan (ICS 206CANINE, adapted; eligible in SAR mode or when a canine T-card exists)
  - Role-oriented dashboard ordering for IC/ICP, Safety Officer, Operations, and Communications
  - Live responsibility-focused windows for Incident Command, Safety Officer, Medical Unit Leader,
    Operations, and Communications Unit under **View → Role Panels**
  - SAR Tasks (SAR mode only)
  - Clue Log (SAR mode only)
  - Optional Incident Open Action Tracker (ICS 233-CG), available in both SAR and Generic modes as a separate tab.
  - T-Cards
  - Activity Logs (ICS 214)
- SAR Task Assignment forms with debriefing and POD factoring support.
- Assignment editors save and establish the linked SAR task before navigating to it. Save failures keep the entered values available for correction or retry.
- ICS 202 objectives have stable identities, explicit order and assessment state, and may be linked
  to multiple ICS 204 assignments. Deleting a linked objective requires an explicit unlink or
  replacement choice. Incident Command approval is bound to the current objective wording and
  order, so subsequent changes display the approval as stale rather than silently retaining it.
  Edit objective wording directly in the ICS 202 table; use the objective editor for assessment
  and notes. **Apply to assignments…** selects supporting assignments grouped by branch/division/group.
  Assignment dialogs keep **Supporting Incident Objective(s)** at the bottom, empty until linked.
- Safety, human medical, canine medical, and communications reviews are independent assertions
  over substantive content. Generated page numbers do not invalidate review, but relevant plan
  changes do.
- **Add log entry…** is available from assignments, SAR tasks, and T-cards, including their editors. The shared entry dialog defaults to the ICP log and selected resource/task, but lets the operator change that context. New assignments must be saved before logging; cancelling the subsequent entry does not undo that save.
- Detached ICS 214 windows remain usable while main-window operational editors are open. Incident switching or closing requires resolving outstanding editors; entries already logged are retained.
- Safety workspace for ICS 215A hazard analysis and editable ICS 208 safety messages/plans. Hazard libraries can be loaded/saved as UTF-8 CSV; imported entries can be stored separately from incident analysis. SAR debrief hazard reports are reviewable from the Safety tab. Safety forms are available for individual export and are excluded from IAP bundles by default until selected.
- ICS 208 authoring includes Safety Officer and Hazardous Materials Group Supervisor role metadata to support later role-specific workflows, including ICS 208 HM.
- Local JSON persistence at `~/.icsforms/incidents/<incidentId>.json`.
- Backup-assisted recovery from each incident's `.json.bak`.
- PDF export for each supported form.
- ICS 206 medical plans use the shared incident/operational-period context. Human plans support a simple emergency-instruction mode and detailed records. Human and adapted canine plans share the **Medical** tab, with aligned aid-station, transportation, hospital, and special-procedure sections; aviation coordination is under Transportation. Canine transportation uses editable structured service rows, can copy ambulance entries from the human Medical Plan, and can be explicitly imported/exported as a separate CSV (`service,location,contactNumbers,frequency,airOrGround,levelOfService`). Copied ambulance entries do not imply canine transport capability; confirm that capability before use. Existing free-text canine transport notes remain supported. Confirmed 24-hour veterinary contact numbers are bold red in the canine PDF. The canine subtab appears for eligible SAR/canine incidents and can also be opened from **View** for saved plans. Canine plans are explicitly adapted, not an official FEMA variant. IAP inclusion is selected only in ICS 202; medical-plan tabs do not have IAP inclusion toggles. Medical-plan signature lines are printed for signing and are not edited in the application.
- The **Shared** tab stores incident-owned ICP, staging-area, base, and other addresses. The canine veterinary routing workspace can select one of those locations as its routing origin; address geocoding and coordinate confirmation remain available there.
- **Safety Overview** includes a readiness summary and read-only PDF-like previews of the current ICS 208, ICS 206, and adapted ICS 206CANINE forms. The summary reports the shared Safety Officer and Medical Unit Leader assignments and recorded aid stations.
- **Human Medical Reference** and **Canine Medical Reference** are separate nonmodal windows.
  They automatically render the latest plan PDF, show recorded completion/review status, and never
  mark a plan reviewed merely by opening it. Medical-event logging accepts optional task and
  location context. Canine directory candidates remain distinct from facilities explicitly
  accepted into the incident plan.
- Canine veterinary directories are incident-owned copies, not live links to source files. JSON regional files use schema version 1 (`schemaVersion` and `facilities`). CSV imports accept the app's extended headers or a minimal hospital file with `Hospital Name,Address,City,State,Zip,Contact Number,Fax,Notes`; column names are case- and whitespace-insensitive. Imported street address, city, state, ZIP, contact number, and fax remain separate fields. `Notes` is retained as descriptive capabilities/notes and does not imply confirmed 24-hour availability. The app's UTF-8 CSV export uses `id,name,town,state,postalCode,address,contactNumbers,fax,available24Hours,availabilityNotes,capabilities,verifiedAt,latitude,longitude`; multiple contacts are semicolon-separated. Blank availability is unknown, not confirmation of 24-hour service. Directory edits require explicit export and do not overwrite sources implicitly.
- Address geocoding and driving routes are separate services. HeiGIT-hosted openrouteservice is the default provider: geocoding uses `https://api.heigit.org/pelias/v1/search`, and driving matrices use `https://api.heigit.org/openrouteservice/v2/matrix/driving-car`. The veterinary routing tab guides the operator through loading a directory, confirming coordinates for the base and facilities, calculating road routes, then reviewing and adding the closest confirmed 24-hour facilities. Route calculation remains disabled until the base and at least one facility have valid coordinates; shortlist review remains disabled until a current road route exists for a confirmed 24-hour facility. Requests send entered addresses or coordinates to that third party. Configure the key in **Configuration > Medical Routing (OpenRouteService)…**, set `ORS_API_KEY`, or place the key in `~/.icsforms/ors-api-key`; the environment variable takes precedence, and credentials are not stored in incident JSON or regional files. Verify current [HeiGIT terms and restrictions](https://account.heigit.org/info/tos), quotas, and service limits before operational use. The tab displays **© openrouteservice by HeiGIT | Data from [OpenStreetMap](https://openstreetmap.org/copyright)**. Unresolved/unreachable locations remain visible for manual handling; any straight-line estimate is explicitly labeled and is not a driving time.
- Role dashboards reorder the same editors; they do not duplicate incident data. Choose **View > Dashboard** (IC/ICP is the default). ICS 205 is the radio communications plan; ICS 205A remains a distinct communications/contact list.
- **Configuration > Descriptive tab labels** optionally names the tabs **ICS-202 Objectives**,
  **ICS-204 Assignments**, **ICS-205 Comms**, and **ICS-214 Log** followed by its existing owner/scope.
  The choice is saved locally and does not change workspace navigation or incident data.
- Role panels are focused live views of the same incident data, not new authorization boundaries.
  Incident Command records an explicit IAP planning-cycle stage and objective approval; Operations
  delegates assignment, task status, clue, log, and debrief actions to their established editors;
  Safety and Medical Unit Leader use the shared role assignments and review records. Recorded
  resource status is displayed as recorded and is not treated as a readiness or radio-check
  certification.
  The IC resource overview can break down recorded statuses by resource type. Plan-state badges
  distinguish recorded content from explicit review/approval; counts are not sufficiency scores.
  Its summary bar is red for missing required content, yellow when content is recorded but
  approvals are pending or stale, and green when required content has current approvals.
  Canine/equine medical content is required only when animal resources are recorded.
  Objective rows summarize linked assignment lifecycle states; canine ICS 219-7 equipment
  is counted separately from other equipment. Role/reference windows use distinct themed icons.
- **Export All PDFs** for individual per-form PDF files.
- **Export IAP Bundle** for a single merged PDF bundle of selected forms with a cover page.
- PDF export handles multiline text and tabs, and reports unwanted controls or unsupported characters with the affected form/field and correction guidance. Failed renders do not replace existing good PDFs or leave partial bundle files.
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

ICS 233-CG output supports **Portrait** (default), **Landscape**, or **Portrait-fit**
on the configured paper size. Choose **ICS 233 orientation** in the PDF generation dialog for either
individual PDFs or the combined IAP bundle. Each dialog starts with portrait and
standard clue output, regardless of legacy saved export flags. These print-time
choices are not saved with the incident. Uncheck **Clue Log** to omit clues
entirely; the enabled incident tracker remains a separate export selection.
ICS 201 starts unchecked unless it contains briefing-specific content, such as an
incident number, map, summary, objectives, actions, or resources. Shared incident
identity and automatically populated names/dates alone do not select the briefing.

### Radio communications plan (ICS 205; issue #14)

The ICS 205 summarizes subscriber radio frequency and trunked talkgroup assignments
for the operational period. The Communications Unit Leader prepares it for the
IAP; use the relevant assignments on ICS 204. Incident name and operational period
come from **Shared**. Review all imported or carried-forward settings before use.
Selecting or suggesting a preparer's name is not a signature.
The tab has one optional preparation date/time control for both printed date
blocks. Editing that control updates both dates; unrelated edits retain any
distinct dates in older plans. Signature lines and IAP pagination belong to the
PDF, not the tab; previously saved signature and page data are retained.

The optional communications-plan workspace is available in both incident modes;
show or hide it under **View → Communications Plan (ICS 205)**. The Communications
dashboard places it first. Hiding it does not delete the plan or prevent
explicitly selecting its PDF. CSV actions are also available from the main
Import/Export menus. To import CHIRP's generic CSV, use **Import CHIRP CSV…** on
the tab or **Import → Import communications plan CHIRP CSV…**. This preselects
the CHIRP input format; review and select rows in the preview before accepting.
The general **Import CSV…** action also supports CHIRP through its input-format selector.
Double-click a channel to edit it, or right-click for channel actions. Row actions
are enabled only for a selected channel; movement also respects list boundaries.
The preparer area shows the selected person's known role and offers the
Communications Unit Leader shortcut only when that position is assigned.
Use the main PDF export dialog to print ICS 205.
The channel editor keeps RX and TX frequencies, bandwidths, and tones/NAC separate.
Its compact RX, simplex/duplex, and TX columns support explicit simplex frequency/bandwidth copying
and duplex frequency/offset entry with exact decimal arithmetic. Unknown transmit
settings remain unknown until explicitly supplied. RX and TX tones stay independently
editable in every mode; simplex never copies or synchronizes tones. Bandwidth and mode choices
use readable labels while retaining the native N/W and A/D/M codes.
**Included in plan** controls whether a channel prints. Included rows are green
and excluded rows gray; choose **Included in plan only** to hide reusable channels
without deleting them. The compact table omits internal channel IDs.
Channel numbers and functions allow free text. Digital protocol is separate from
the form's A/D/M mode; a generic D or M is not a programming specification.
Trunked system information is not a conventional transmit frequency.

Workspace navigation uses the existing typed `WorkspaceId.ICS_205` destination,
not the tab's displayed title. Menus and role dashboards reuse the same editor
and incident data, including the existing edit-flush lifecycle.

Channel linkage has a central read-only projection for assignments, tasks,
resources, medical/safety references, and plan-derived assignments. A unique
explicit channel ID takes display precedence over saved free text. Missing,
deleted, ambiguous, or absent links fall back to the legacy value; blank fallback
means unknown/unset, not a usable frequency. Matching labels or frequencies never
establishes a link automatically. This preserves older incidents and duplicate
channel names without destructive rename-by-text replacement. Legacy fields are
not all rewritten into references by this change.

Use **File > Bring Forward…**, or the action in a communications, safety, or medical
plan panel, to reuse selected plans from an archived operational period. The same
dialog can import plans from another saved incident without switching incidents.
Only selected plans are replaced, after confirmation; incident identity and the
active period remain unchanged. Copied plans require new review and do not retain
signatures or approval claims. Communications copies include channel order,
assignments, special instructions, and coverage/relay guidance, but clear
per-channel radio-check assertions.

Direct standalone ICS 205 exports default to native landscape. The PDF generation
dialog offers matching **Portrait**, **Landscape**, and **Portrait-fit** options
for ICS 205 and ICS 233, in that order before the adapted clue-log checkbox.
ICS 205 defaults to **Portrait-fit**, which rotates landscape **content** 90 degrees
counterclockwise inside portrait-sized pages, with upright bundle page numbers.
**Portrait** uses an upright portrait layout; **Landscape** uses landscape paper.
Both choices apply to individual PDFs and IAP bundles and remain independent
between the two forms. Long content continues
on additional pages with identifying context rather than being silently dropped.
Configured paper size and minimum margins apply.

Native plan CSV is the lossless interchange format; CHIRP CSV is not. The
Communications Unit role panel adds sortable/filterable channel access, stable
current-period usage links, coverage/relay guidance, and explicit per-channel
radio-check results. Frequencies
are stored as text without reducing programming precision to the printed form's
four decimal places. Tone/NAC leading zeros, unknown values, and unsupported
programming information must survive native interchange. Unknown TX is not
automatically simplex or receive-only. Use **Copy RX → TX** only when simplex
transmission is actually intended.

The versioned native CSV uses `schemaVersion=2`, a `recordType` column, and a
`formMetadata` column. There is exactly one `form` record, even for an empty plan;
its metadata is a JSON object containing the plan's non-channel fields. Subsequent
`channel` records retain list order. Their named columns cover channel identity,
all form fields, TX policy, digital protocol, system, print selection, and
programming metadata, plus coverage/relay guidance and explicit radio-check
details. Schema 1 native files remain importable and produce a **Not recorded**
radio-check state rather than an inferred result. Channel cell values are JSON-encoded inside normal CSV
quoting: for example a frequency string is `"155.1600125"` as a JSON value,
not a spreadsheet number. This preserves leading zeros, exact strings, booleans,
and metadata maps. Use the application's export as a template rather than
converting these cells to spreadsheet numbers. Files are UTF-8; imports accept
an initial BOM and map headers rather than relying on column position.

Import parses the whole file before offering a selectable-row preview. Appending
is the default; replacement and explicit identity-based update are separate
choices. Names and frequencies are not reliable identities and are never used
to guess updates. Appending a colliding identity must create a distinct channel,
not overwrite the existing one. Cancelling preview or rejecting malformed input
leaves the incident unchanged. A native CSV is a full plan, not an unversioned
generic frequency library; CHIRP CSV has its own separate import/export action.

CSV files may contain spreadsheet formulas as literal field values. Native export
does not prefix or rewrite them, since that would corrupt a round trip. Import
untrusted CSV into a spreadsheet as **text**, with formula evaluation disabled;
do not rely on quoting alone to prevent spreadsheet formula execution.

CHIRP's generic CSV is a channel interchange boundary, not direct radio
programming. Radio-specific `.img` files can contain settings beyond generic CSV
and are interpreted through CHIRP's supported image formats/drivers; they are not
universally unreadable. This application does not implement image drivers, radio
uploads, or a runtime CHIRP/Python dependency. Always review CSV in CHIRP against
the intended radio's capabilities and a downloaded image before writing a radio.

Digital export requires more than recognition of a mode label. DV/D-STAR and
DN/System Fusion require verified routing/settings and representative image
workflows; those hardware/image checks are not claimed here. DMR, P25, NXDN,
generic D/M, and trunked talkgroups must not be presented as programmable analog
channels. Color code, slot, talkgroup, NAC, or routing information in comments is
not a substitute for programming settings. Unsupported records remain available
in native JSON/CSV, and excluding records from CHIRP output requires explicit
confirmation.

#### CHIRP support and verification boundary

CSV semantics are pinned to upstream CHIRP commit
[`21e42e4518c167548af90ed7f9edf75e516c491a`](https://github.com/kk7ds/chirp/tree/21e42e4518c167548af90ed7f9edf75e516c491a),
particularly [`generic_csv.py`](https://github.com/kk7ds/chirp/blob/21e42e4518c167548af90ed7f9edf75e516c491a/chirp/drivers/generic_csv.py)
and [`chirp_common.py`](https://github.com/kk7ds/chirp/blob/21e42e4518c167548af90ed7f9edf75e516c491a/chirp/chirp_common.py).
Recognition in `MODES` is not proof that generic CSV carries every required setting.
The checked-in conventional CSV fixture is hand-authored from these source
semantics, with its commit and scope recorded in the accompanying properties
file. Java conformance tests do not constitute a run of upstream CHIRP, a
radio-image import/export test, or a hardware programming test.

| Channel/settings | Interchange policy |
| --- | --- |
| Conventional analog FM/NFM | Supported CSV projection with explicit RX/TX settings; review target-radio capabilities. |
| Simplex, positive/negative offset, split, receive-only | Separate subscriber RX/TX mapping; unknown TX is excluded, never guessed. |
| CTCSS, DTCS and independent cross tones/polarities | Mapped independently in the TX and RX directions. Unsupported squelch variants require native preservation. |
| DV / D-STAR | Export deferred: upstream recognizes DV and constructs a `DVMemory`, but the generic driver's `ATTR_MAP` does not map its routing/call fields. No verified image workflow. |
| DN / System Fusion | Export deferred: mode recognition alone does not verify required settings or a representative image workflow. |
| DMR / P25 / NXDN, generic D/M, trunked systems | Not exported as programmable channels; native records retained with exclusion diagnostics. |
| Radio images, uploads, hardware validation | Not implemented or claimed tested. Use CHIRP and the intended radio's supported driver. |

CHIRP itself can substitute a default power when CSV power is absent (50 W in the
pinned generic driver). Consequently this application's CHIRP projection requires
explicit power entered in **Edit radio channel → CHIRP power (watts)** (for
example `5` or `5W`, saved as `chirp.Power` metadata) and an explicit nonnegative numeric channel
number. It excludes rows without those settings rather than inventing power or
silently renumbering channels. Imported CHIRP settings retain their metadata.
The power field starts blank when unknown; clearing it removes the power setting.
An edited power field overrides the JSON power entry; otherwise JSON edits are
retained. Verify the chosen power against the target radio and permitted operation.
For verified analog tone conversion, use a blank value for no tone, a numeric
CTCSS value or `CTCSS:88.5`, and `DTCS:023:N` / `DTCS:023:R` for DTCS code and
polarity. Other tone/NAC text remains valid native form content but may be
excluded from CHIRP conversion rather than guessed.
Review power, channel numbering, tuning steps, supported tones, and band limits in
the target radio image; generic CSV acceptance does not certify that a radio can
or should transmit those settings. Comments do not set radio programming
parameters.

## Incident phases

The application models three phases of an incident lifecycle, selectable at startup and changeable from the **Configuration → IAP Phase** menu:

### (a) Pre-Operational (planning)

The team is preparing for a potential or anticipated incident before any response has begun. ICS 201 is available to pre-stage information but is not yet in active use. Resources may be placed in an "Ordered" status on the T-card rack.

### (b) Initial Incident Response

An incident has started. ICS 201 – Incident Briefing is the **primary capture tool** for the incident commander: map/sketch, situation summary, health and safety briefing, objectives, current actions, and the organizational chart and resource summary. All ICS 201 fields are fully editable. ICS 202 and ICS 204 are also available for concurrent planning.

### (c) Subsequent Operational Periods

The incident has progressed beyond the initial response. ICS 201 is **locked (read-only)** and attached to the IAP bundle as the historical initial-response record. ICS 202 (Incident Objectives) and ICS 204 (Assignment List) become the primary forms for each subsequent operational period. When advancing to a new operational period the operational-period forms are cleared while the organizational chart and ICS 201 are preserved as historical context. Completed-period communications, safety, and medical plans are archived for selective reuse through **File > Bring Forward…**; there is no startup carry-forward checkbox. Older archives only offer the plan snapshots they contain.

## Repository structure

All production source lives under `src/main/java/org/sarmanagement/icsforms/`, with unit tests mirroring that layout under `src/test/java/`. The Maven build is defined in `pom.xml`.

**`App.java`** is the application entry point. It initialises the Swing look-and-feel, constructs the top-level `AppController`, and opens the `MainFrame` window.

**`model/`** holds the plain-Java data model. `AppData` is the root object serialised to each incident's JSON document; it owns `IncidentContext` (shared header fields, mode, and IAP phase) alongside one instance of each form model class (`Ics201Form`, `Ics202Form`, `Ics204Form`, `Ics214Form`, etc.). Supporting value types for SAR task assignments, T-cards, clue log entries, activity log entries, and the organizational chart also live here.

**`ui/`** contains the Swing presentation layer. `MainFrame` builds the top-level tabbed pane and houses all the per-form panels. `AppController` wires the model to the UI and drives save, load, and export actions. Each form has a dedicated panel class (e.g. `Ics201Panel`, `Ics202Panel`) that owns its own sub-tabs or sections. `UiSupport` provides shared Swing helper utilities used across panels.

**`pdf/`** is responsible for turning the in-memory model into PDF files. `AbstractPdfRenderer` and `PdfFormRenderer` provide the shared drawing infrastructure (Apache PDFBox). Each form has a dedicated renderer (e.g. `Ics201PdfRenderer`, `Ics204PdfRenderer`). `CoverPageRenderer` produces the IAP bundle cover page. `PdfExportService` coordinates single-form export, bulk export, and the merged IAP bundle export.

**`comms/`** owns native/CHIRP interchange and the UI-independent `ChannelLinkSource` read API. `links(AppData)` returns immutable, typed source/reference snapshots; `resolve(AppData, channelId, legacyFallback)` applies linked-channel-first display precedence; `legacyChannelNames(AppData)` supplies centralized presentation suggestions. `LINKED` requires one exact ID match, `UNLINKED` means no explicit ID, `MISSING` means no target, and `AMBIGUOUS` means duplicate target IDs. All non-linked outcomes retain the original fallback (null becomes empty). Labels never establish links. Structural row locations are documented as locations, not new persisted identities. Reads do not generate IDs or reconcile medical/role state.

**`persistence/`** defines the store-independent `IncidentStore` contract. `FileIncidentStore` stores each incident as JSON with a separate backup, revision checks, and advisory locks; `LocalRepository` remains for compatibility with older integrations.

**`validation/`** contains pre-export checks. `IncidentValidator` walks the model and collects `ValidationMessage` instances for any required fields that are missing or invalid, blocking PDF export until the workspace is clean.

## Build

```bash
mvn package
```

### Desktop integration test

Normal builds retain the modality and window-ownership assertions in
`UiSupportModalityTest`, but skip the workstation-dependent native mouse-input
check. To explicitly enable that check on an unobstructed desktop where Java
`Robot` can inject mouse input:

```sh
mvn -Dicsforms.desktopTests=true -Djava.awt.headless=false -Dtest=UiSupportDesktopIntegrationTest test
```

On Linux, a dedicated virtual display can isolate the test from other windows
and user input:

```sh
xvfb-run -a -s '-screen 0 1280x800x24' mvn -Dicsforms.desktopTests=true -Djava.awt.headless=false -Dtest=UiSupportDesktopIntegrationTest test
```

A headless skipped run does not validate native input. This opt-in test still
depends on desktop input delivery; it does not resolve the unconfirmed cause of
workstation-specific failures.

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
