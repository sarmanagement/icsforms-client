# Draft Work Plan

## Big picture

### What the system is for

`icsforms-client` supports the command post of a search and rescue (SAR)
incident run under the Incident Command System. It lets a small team produce
and keep current the documents an incident actually runs on — ICS 201–208,
214, 215/215A, SAR task assignments and debriefs, T-cards, clue log, medical and
communications plans — and print them individually or as a combined Incident
Action Plan. Incidents are often rural, intermittent-connectivity events staffed
by volunteers who arrive at different times, work shifts, share laptops, and
carry their own phones.

### Where it is today

A single-user Java Swing desktop application. One operator, one laptop, one
incident at a time, saved as local JSON files with revision checks, backups and
an advisory edit lock. The core SAR form set is complete. Focused role panels
(Safety, Medical Unit Leader, Operations, Incident Command, Communications) and
separate human/canine medical reference windows exist alongside the full editor
workspace, though some of that agreed scope is still outstanding (Part I).

### Where it is going

The same application, unchanged for the solo user, grows into a system that can
run in four topologies without ever removing the earlier ones:

```text
T0  Standalone        one laptop, local files                         (today)
T1  Local host        several workstations on incident Wi-Fi; one of
                      them hosts the incident service and store        (Stage 2)
T2  Networked store   a field or site server; single-user phones and
                      multi-user shared workstations as clients         (Stage 3-4)
T3  Hosted            cloud service, remote access, read-only mirrors,
                      planned authority transfer with the field         (Stage 5-7)
```

Three ideas carry the whole design:

1. **One authority per incident.** A single host owns the record at any moment.
   Clients keep a working copy and an outbox; they never write to a database
   directly and never act as independent masters.
2. **A device trust boundary, not a shared password.** A device is admitted once,
   in person, by redeeming an administrator-issued, short-lived invitation
   (QR/short code) for an individually revocable device credential. Switching
   operators at a shared workstation is then a frictionless, attributed *claim*
   from the ICS 211 roster, not a login. Outsiders on the same Wi-Fi get nothing;
   responders join in under a minute; lost devices are revoked individually.
3. **Stable identity and provenance everywhere.** Every record has a durable
   UUID; every append-style entry records who entered it, from which station,
   and when (UTC), with room for when the host accepted it. This is what makes
   per-record concurrency, sync and audit possible later without a rewrite.

### How we get there

Work proceeds in two independent streams, then converges:

- **Role-panel completion** (Part I): finish the outstanding #33 scope in the
  standalone application — no network, no authentication.
- **Persistence foundation** (Part IV, Stage 1): make the existing `IncidentStore`
  seam backend-neutral and asynchronous, with contract tests, strict imports,
  durable identity migration, UTC time and a provenance seam — still no network
  code. Tracked by #37, #39–#44. Its exit test is that a remote store stub drops
  into the controller with no UI change.
- **Then** Stage 2 adds the incident API and the first local host (T1); Stage 3
  adds the trust boundary, operator claim, roles and audit (T2); Stages 4–7 add
  offline sync, field-host packaging, hosted deployment and a mobile client (T3).

Every stage ends with a usable, recoverable standalone application. Nothing in
the later stages is allowed to make the solo-laptop case harder.

### How to read this document

**Status date:** 2026-10-31. Status entries are taken from the merged source
tree, not from issue or PR closure state.

- **Part I — Current status:** what is delivered, what remains of the role-panel
  plan, and where each open issue sits.
- **Part II — Design contracts:** binding invariants for all role-panel and
  persistence work.
- **Part III — Architecture and topologies:** target architecture, the device
  trust boundary, and engineering requirements for the shared system.
- **Part IV — Staged work plan:** gates, dependencies, exit criteria and sizes.

## Part I — Current status

### Delivered work

| Work | Tracking | Evidence | Status |
| --- | --- | --- | --- |
| Core SAR form set (ICS 203, 205, 215A/208, 206 + canine 206, ICS 233-CG clue log), training fixes | #13, #14, #15, #16, #17, #18 — all closed | Models, panels and PDF renderers for each form are in `src/main/java/org/sarmanagement/icsforms/{model,ui,pdf}`; IAP bundle numbering in `pdf/PdfExportService.java` | **Complete.** Former "Stage A" is no longer a prerequisite. |
| ICS 215 planning worksheet and next-period tactics | #35 — closed | `model/Ics215Form.java`, `ui/Ics215Panel.java` | **Complete.** |
| SAR task status ↔ ICS 214 integration, ICS 204 PDF layout | #19, #20 — closed | `ui/SarTaskPanel.java`, `pdf/Ics204PdfRenderer.java` | **Complete.** |
| File-backed `IncidentStore` with UUID identity, revision checks, per-incident edit locks and backups | PR #34 and predecessors | `persistence/IncidentStore.java`, `persistence/FileIncidentStore.java`, `persistence/IncidentDocumentReader.java` | **Complete for standalone use;** contract hardening tracked in Part IV. |
| Live local role panels (ten-phase plan) | #33 — closed; PR #32 merged 2026-10-26 | See phase table below | **Partially delivered.** PR #32's own description states the agreed scope remains incomplete. |

### Live local role panels — delivery status by phase

The ten-phase plan from issue #33 is retained below as the authority on *what*
was agreed. Statuses were determined by inspecting the merged tree.

| Phase | Scope | Status | Evidence / gap |
| --- | --- | --- | --- |
| 1 — Baseline/design | Contracts, layouts, scenarios | Delivered | Part II of this document. |
| 2 — Live multi-view | Incident/period-bound change notifications, stale-action guards, disposable listeners, partial-text retention | **Delivered, one gap** | `ui/AppController.java:110-150` (bindings, generations, change kinds, subscriptions), `:705` (publish); `ui/Ics202Panel.java:225-240` (partial retention). **Gap:** PR #32 reports stale full-editor overwrites are not yet fully prevented and live propagation is incomplete for some editors. |
| 3 — Objectives/linkage | `IncidentObjective` entities, migration, ordered editor, authoritative links, archives | **Partial** | `model/Ics202Form.java:20-115`, `model/ResourceAssignment.java`, `ui/Ics202Panel.java:230-405`; archived objective form in `model/AppData.java:220-240`. **Gap:** no explicit period-end objective/assignment-context archive workflow found. |
| 4 — Review tracking | Persisted reviewed-content boundary; Not yet reviewed / Reviewed / Changed since review | Delivered | `model/PlanReviewAssertion.java`, `model/PlanReviewStatus.java`, `model/AppData.java:1750-1800`, `ui/PlanReviewStrip.java`. |
| 5 — Reference vertical slice | Two nonmodal medical reference windows, cached async previews | Delivered | `ui/HumanMedicalReferenceWindow.java`, `ui/CanineMedicalReferenceWindow.java`, `ui/MedicalReferencePanel.java`, `ui/AsyncFormPreviewPanel.java:35-140`. |
| 6 — Safety / MUL | Worklist, detail, preview, focused edits | Delivered | `ui/SafetyRolePanel.java`, `ui/MedicalUnitLeaderRolePanel.java`. |
| 7 — Operations | Live task board with status/log/clue/debrief actions | Delivered | `ui/OperationsRolePanel.java`. |
| 8 — Incident Command | Objective-centred **editable** layout with supporting assignments, results, attention list | **Partial** | `ui/IncidentCommandRolePanel.java:310-430` is a read-only overview with attention/review indicators. **Gap:** agreed editable objective / supporting-assignment / results layout not delivered; editing still only in `Ics202Panel`. |
| 9 — Communications | Role panel over ICS 205 / `ChannelLinkSource` | Delivered | `ui/CommunicationsRolePanel.java:330-450`. |
| 10 — Consolidation/hardening | Panel entry points, retire role-driven tab ordering, canine-tool extraction, accessibility/offline evidence, docs | **Partial** | "Role Panels" menu at `ui/MainFrame.java:369-390`. **Gaps:** `View > Dashboard` role-ordered tab sets still exist (`ui/MainFrame.java:301-325, 1492-1500, 2557-2620`); `CanineMedicalPanel` not extracted into reusable components (`ui/MedicalWorkspacePanel.java:20-40`); no role-panel documentation under `docs/`; integration acceptance and final security verification not recorded. |

### Remaining role-panel work and where it is tracked

**No open issue currently tracks the remaining #33 scope.** Issue #33 was
closed when PR #32 merged. The following items must be filed as a single
follow-up issue (proposed title: *Complete live role panels: stale-write
prevention, editable IC layout, canine-tool extraction, dashboard retirement*)
before further role-panel work is started:

1. Prevent stale full-editor overwrites and complete live propagation for every
   editor that participates in a role panel (Phase 2 gap).
2. Period-end archive of objective/assignment context (Phase 3 gap).
3. Editable Incident Command layout: focused objective editor, supporting
   assignments with Link existing / Create supporting / Open assignment / Open
   debrief, and reported results (Phase 8 gap).
4. Extract reusable canine medical components from `CanineMedicalPanel` without
   duplicating state or reparenting one Swing tree into two windows (Phase 10).
5. Retire `View > Dashboard` role-ordered tab sets once panel entry points and
   stable full-workspace navigation are confirmed discoverable (Phase 10). Dashboard
   ordering must continue to use `WorkspaceId`, never display labels, until removal.
6. Reconcile scope added during PR #32 that was not in the agreed plan:
   planning-stage, objective-approval, radio-check and CSV-version additions.
   Each must either be accepted into Part II contracts or removed.
7. Integration acceptance against the regression matrix in Part II, including
   keyboard, resizing, second-screen and offline evidence, and role-panel
   documentation under `docs/`.

This work is independent of the persistence roadmap in Part IV and must not be
blocked on it; conversely, it must not introduce network, authentication or
remote persistence features.

### Open issues and their place in the plan

| Issue | Title (short) | Plan location |
| --- | --- | --- |
| #37 | Timestamps are not consistent (PR #38 open) | Part IV, Stage 1, gate T1 — time/provenance |
| #39 | Persistence contract coverage; fake `IncidentStore` | Part IV, Stage 1, gate P1 |
| #40 | External validation vs. tolerant load; durable identity migration; entity ID audit | Part IV, Stage 1, gate P3 |
| #41 | Backend-neutral `IncidentStore` concurrency/failure semantics | Part IV, Stage 1, gate P2 |
| #42 | Asynchronous snapshot-based persistence | Part IV, Stage 1, gate P4 |
| #43 | Deterministic Swing test lifecycle; displayed-test CI | Part IV, Stage 1, gate P0 |
| #44 | Provenance seam and injected operator context | Part IV, Stage 1, gate T2 |

---

## Part II — Design contracts (binding)

These contracts were agreed for issue #33 and remain binding for all role-panel
and persistence work. They are invariants, not a status report.

### Ownership and behavioral contracts

- Incident facts, objectives, relationships and review assertions belong in the
  shared persisted model. Panels own only selection, filters, split positions,
  focus, draft input and preview presentation.
- A role panel is a view preference, separate from Current user position and not
  authorization. All comprehensive editors remain reachable through existing
  typed destinations, including nested medical destinations and individual
  activity-log instances. Remove superseded role-driven tab reordering only
  once discoverable panel entry points and stable full-workspace navigation work.
- All windows show one explicit incident/operational period. Validate before
  committing; rejected table input stays editable and blocks its operation.
  Do not refresh away partial text, flush unchanged stale fields, or allow
  delayed callbacks to apply to another incident/period.
- Planning and deployment are concurrent. Missing or unreviewed plans do not
  gate assignment creation or task status changes. A simple medical plan is
  legitimate; missing fields are not a sufficiency judgment.
- Review is a person's explicit assertion about current content at a time,
  not authenticated approval or an electronic signature. Preserve printed
  signature fields and semantics independently.
- Opening a reference or preview has no incident effects: no log creation,
  response declaration, task change or automatic event. No network sync is added.

### Objective, assignment and review contracts

Objectives have stable IDs, text and order, plus explicit **Not assessed /
Ongoing / Achieved / No longer applicable** assessment, note and update metadata.
Migrate legacy strings additively without guessed links. Assignments support
zero/multiple objective IDs, normally one; objectives support multiple assignments.
Keep one authoritative relationship shared by ICS 204 and the linked SAR task.
Reordering and text edits preserve links. Removing a linked objective requires
explicit unlink/reassignment resolution. Creating supporting work creates a
normal immediately editable assignment prelinked to that objective, never a
proposed/request/approval object.

Preserve period-end wording, relationships and assessments in independent deep
copies. Objective carry-forward is optional; if implemented, create new-period
identity with predecessor provenance and reset assessment. Do not carry
assignments automatically. Deduplicate resource totals by stable identity:
multiple objective links do not create separate allocations. Task completion and
debrief never calculate or change objective assessment. Assessment metadata does
not enter standard ICS PDFs.

Persist the reviewed content/revision boundary for safety, human medical, canine
medical and communications. Display **Not yet reviewed / Reviewed-approved by
person at time / Changed since review**, retaining earlier assertion metadata.
Meaningful plan/shared-value changes invalidate review; navigation, generated
page numbers, preview work and review metadata do not. Explicit re-review
acknowledges current content. New periods need fresh review. There is no mandatory
Draft/submission workflow; readiness is optional and must not impede small
incidents.

### Layouts and workflow contracts

**Two medical references, never a combined window**

Separate launch actions and nonmodal windows:
**Human Medical Reference — ICS 206** and
**Canine Medical Reference — adapted ICS 206CANINE**.
Both may remain open; neither has a type toggle.

Each contains incident/period/review header; instructions and personnel,
aid-station and transportation column; facility list with selected details;
optional task context; complete standard PDF preview; explicit log/editor
actions. Show latest working content even incomplete. Instructions, transportation,
facilities, previews and logging defaults never cross types or silently fall back.
Shared transport appears only when explicitly recorded for that plan.

Sort by current recorded road distance where available without hiding unrouted
facilities. Show origin, distance/duration provenance and age, stale/unknown/
unrouted state, contacts, availability, capabilities and verification. Distinguish
accepted plan snapshots from directory candidates. The operator chooses; there
is no suitability algorithm or new human routing service.

Link zero/multiple veterinary personnel T-cards, retaining free text and missing
link information. Display recorded assignment, status and location without
implying availability or an aid-station posting. Task context includes assignment/
team, members/resources and communications, never determines reference type.
Only explicit confirmed **Log medical event…** creates a log entry, using
**Responder medical event** or **Response canine medical event** in the existing
mechanism. No automatic duplicates across people's logs or unnecessary clinical
detail.

**Safety Officer and Medical Unit Leader**

Responsible-item worklist left, selected detail and on-demand PDF right; optional
Show all is not an access control. Safety covers safety planning, reported hazards
and medical review. MUL covers human/canine development, personnel, aid stations,
transport, facilities and review handoff. Provide focused shared edits and
re-review inline, plus full-editor links for extensive changes.

**Operations**

Incident/period header with New assignment and separate Human/Canine reference
actions. Task board left, selected task detail/actions right, resources below.
Actions: see statuses, change status, add log entry, **Record clue**, create
assignment, **Debrief task**, select recorded available resources for new work.
Reuse existing lifecycle/clue/log/debrief operations and status vocabulary.
Show members, objectives, hazards, resolved communications/legacy fallback,
reported results and debrief. Preserve task/resource selection on live updates.
Recorded location/availability is not inferred actual location/readiness.

**Incident Command**

Header: Add objective / Open ICS 202 / Full workspace. Left: ordered objectives
(number/text/assessment); focused full-text/assessment/note editor; supporting
assignments (status/resources/result/debrief); Link existing / Create supporting /
Open assignment / Open debrief; reported results with sources.

Right: observational attention for objectives with no linked assignment,
assignments without allocated resources, assignments without objectives and plans
changed since review; Safety/Human/Canine/Comms plan status; deduplicated resource
totals; Open Operations. This is not a formal command-request queue. Not every
objective needs field work, and planned assignments without resources differ
from no planned assignment.

**Communications**

First-class responsible-item worklist, review state, channel overview and PDF,
unresolved links, linked tasks/resources, focused shared edits and full
editor/import access. Use immutable `ChannelLinkSource` snapshots: unique explicit
resolved IDs take precedence; absent/missing/ambiguous links retain legacy text
and visible unresolved state. Channel assignment is not a radio check.

### Acceptance and regression matrix

- Shared laptop operator changes do not conflate role view and current position.
- Small incidents deploy while plans are incomplete or changing; larger incidents
  use several local role/reference windows concurrently.
- Concurrent edits retain partial narrative text, active cells, focus and
  selection; old hidden controls cannot overwrite newer facts.
- Invalid table values remain editable. Closing/reopening windows disposes
  listeners/workers. Incident switch and period advance cannot mix data.
- Objective text/reorder preserves identity and links; linked deletion requires
  resolution; assessments round-trip; archived copies remain unchanged.
- Carry-forward has fresh review and, if objectives are carried, new identities/
  provenance/reset assessments; assignments do not carry automatically.
- Substantive/shared changes stale review; navigation, preview and generated page
  metadata do not. Legacy review strings make no current-review assertion.
- Both medical windows show only their own instructions/transport/facilities/
  preview/default category, including absent data and mixed-team task context.
- Unrouted/stale facilities remain accessible; directory candidates and accepted
  snapshots are distinct; reference lookup never triggers routing or logs.
- Async rendering handles cancellation, failure, out-of-order completion and
  relevant-revision cache invalidation; structured reference remains usable.
- Multiple veterinary personnel may be assigned elsewhere; missing T-card/channel
  links remain visible and preserve available legacy information.
- All Operations actions invoke established operations; no inferred readiness,
  radio checks or objective achievement.
- Typed full-workspace navigation and separate activity-log instances remain
  stable; all standard forms and exports remain backward compatible.
- Keyboard access, accessible names, resizing, second screen, offline use and
  modal/nonmodal desktop tests receive explicit evidence before release.

Existing test infrastructure is Maven/JUnit, Swing desktop tests under Xvfb,
PDFBox PDF regressions and persistence round trips. Extend those suites rather
than introducing test frameworks. Baseline success is not final acceptance.

### Exclusions for role-panel work

No permissions/authentication/e-signatures, automatic events, clinical support,
suitable-facility choice, computed achievement, formal resource requirements/
request/approval/ordering queue, structured meeting workflow, mobile application,
remote persistence/sync/concurrency, combined-role widget builder, cross-period
panel, simultaneous next-period planning, or blanket legacy-frequency replacement.
Do not reinterpret assignments as resource requests. Role-panel work does not
depend on, and must not pre-empt, the persistence roadmap in Part IV.

---

## Part III — Target architecture and deployment topologies

### Deployment topologies

The system must grow through four topologies without ever removing the previous
one. Each later topology is opt-in; no account or server is required to open and
edit a local incident.

| Topology | Description | Persistence | Identity and trust |
| --- | --- | --- | --- |
| **T0 Standalone** | One device, one operator, no network. Today's application. | `FileIncidentStore` (local JSON) | Operating-system user; optional locally entered operator name (#44). |
| **T1 Local host** | Several workstations on incident Wi-Fi; one of them runs the authoritative incident service (embedded in the desktop application or as a small local process) and houses the store. No internet required. | Host: file or embedded database. Clients: `RemoteIncidentStore` over an incident-scoped API, with a durable local cache/outbox. | Device admission by invitation (QR/short code) issued by an incident administrator; device-bound revocable credential; operator claim per workstation. |
| **T2 Networked store, mixed clients** | A networked persistence service (field host or site server) with multiple client devices: single-user phones/tablets and multi-user shared workstations. | Same API as T1; service may be backed by a database. | Same device trust boundary as T1. Shared workstations distinguish *device admission* (once) from *operator claim* (per shift). |
| **T3 Hosted** | Cloud-hosted authoritative service with remote access and read-only mirrors; planned authority transfer between field and hosted. | Managed database; encrypted export/import for transfer. | Same boundary plus durable administrator accounts and TLS host identity. |

Stage 1 (Part IV) prepares T0 for T1 without implementing any network code.
Stage 2 delivers T1. Stage 3 delivers the trust boundary needed for T2. Stages 4–7
deliver offline sync, field-host packaging, T3 and the mobile client.

### Device trust boundary and low-friction joining

The requirements "new responders join with minimal friction", "people share
workstations with minimal friction" and "outsiders cannot connect or see
incident data" conflict only if one credential has to serve all three. The
resolution is two layers:

**Layer 1 — Device admission (the trust boundary; once per device).**
A device is admitted only by redeeming a short-lived join invitation issued by an
incident administrator: a QR code or short code scoped to one incident, a role
ceiling, an expiry and a use limit, embedding the host address and the host's
certificate fingerprint so the client pins the host. The device generates a key
pair and receives an individually revocable, device-bound credential. The API
accepts nothing without such a credential, so being on the incident Wi-Fi grants
nothing. Physical possession of the invitation at check-in *is* the vetting step
and piggybacks on ICS 211 check-in rather than adding a new process.

**Layer 2 — Operator claim (low friction; many times per shift).**
On an admitted workstation, changing operators is a *claim*, not a login: the
operator is selected from the ICS 211 roster / personnel T-cards, optionally with
a short self-set PIN. The device credential still authorizes every request; the
operator claim is attribution recorded in provenance (#44) and audit history.
Effective role is the lesser of the device's role ceiling and the operator's
assigned position. Only checked-in people can be claimed.

Controls this yields: expired/used invitations defeat photographed QR codes;
host-certificate pinning defeats evil-twin hotspots; lost devices are revoked
individually; demobilised devices expire at period close or demobilisation; a
bystander at an unlocked workstation can at most mis-attribute, mitigated by
screen-lock timeout and the physically controlled ICP. **Never use a shared
incident password**: it is low friction on day one and unrevocable on day two.

Roles (minimal set): `observer` (read), `field` (own task's ICS 214, debrief,
clue reports — the phone default), `section` (edit assigned forms), `planner`
(all forms, period rollover), `admin` (invitations, roster, revocation, authority
transfer). Enforced server-side; clients only hide what they cannot do.

### Target architecture

```text
Swing desktop ─┐
Mobile client ─┼── incident-scoped API ── authoritative incident host ── database
Future clients ┘           │
                            └── controlled read mirrors / exports
```

- **No direct database access from any client.** Database credentials, migrations, authorization, and validation remain on the host. A client receives only the data and operations permitted for its incident and role.
- **Incident-scoped API.** Each request is authorized against an incident and a responder's role. Incident boundaries are enforced in the service layer, not left to client-side filtering.
- **Offline-first clients.** Clients keep a local working copy and queue edits while disconnected. Connectivity is an optimization, not a prerequisite for recording field work.
- **One authority per incident.** A field computer or cloud service is the single authoritative host at a time. Remote mirrors can serve permitted reads and backups, but are not independent writable masters.
- **Preserve standalone mode.** Existing local-only use remains supported and usable. Shared-host features should be opt-in; no account or server should be required to open and edit a local incident.
- **Keep a clean contract boundary.** Use a versioned JSON API and explicit transfer objects rather than exposing database entities. HTTP request/response operations are sufficient initially; add a live-update channel only when polling or change feeds no longer meet operational needs.
- **Avoid premature technology commitments.** Select the database, host packaging, and identity mechanism in Stage 0 based on offline operation, maintainability by a small team, recovery, and upgrade constraints. PostgreSQL is a reasonable hosted candidate, but a field host must be supportable without internet and without exposing database ports to the incident LAN.

### Authority, ownership, and transfer

Each incident has one authoritative host at any moment. This avoids split-brain edits when a field deployment loses internet connectivity or a cloud link fails. Devices may create offline edits, but those edits are pending client changes until accepted by the current authority.

Moving an incident between field and hosted modes is a planned operation, not automatic multi-master replication:

1. The outgoing host stops accepting edits or enters a clearly marked transfer/freeze state.
2. Connected clients sync; outstanding offline edits are identified and retained for resolution.
3. The host creates a complete, integrity-checked incident export, including attachments and schema version.
4. The receiving host imports and validates the incident and confirms the new authoritative identity.
5. Clients are directed to the new authority; the old host becomes read-only or is securely retired.

Remote mirrors should be explicitly described as read-only, and show their last successful synchronization time. Define how stale data is presented so no responder mistakes a mirror for the current authoritative record.

### Engineering challenges and design requirements

#### Concurrent editing and auditability

Several responders may edit separate assignments or forms at once. Avoid whole-incident locks. Give independently editable records stable identifiers and versions, and use optimistic concurrency so stale updates are detected instead of silently overwriting newer work. Prefer append-oriented records for activity logs, clues, and similar events. Keep an audit history of changes, including the attributed responder, device, time, and operation.

For genuine conflicts in narrative fields, start with a visible conflict-resolution workflow and retained prior values. Do not introduce complex text-merging algorithms until field experience demonstrates a need. Conflict handling must be usable on a small screen and understandable under incident pressure.

Cross-form values are shared facts, not separate copies. Define which form or entity owns each value (for example, resource identity, operational period, assignment, and personnel) and derive other views and PDFs from it. Record the source and time for derived values, and make corrections flow back to the authoritative source rather than creating inconsistent duplicates.

#### Offline synchronization

Clients need a durable local cache and outbox. Queue idempotent, uniquely identified operations so retrying after a lost connection does not duplicate an assignment, clue, or log entry. Synchronize with an ordered host change cursor rather than trusting device wall clocks. Preserve local timestamps for display and audit, but do not use them as the sole ordering mechanism.

Specify behavior for stale clients, deleted or struck-out records, schema changes, and edits made while an operational period is closed. Do not silently discard queued work. Show pending, accepted, rejected, and conflicting changes clearly, with a way to recover/export unsynced work.

#### Attachments, performance, and reliability

Photos, maps, and sketches can be large and are important in SAR. Store attachment metadata separately from form records; transfer metadata first and upload/download bytes on demand when possible. Use content hashes to detect corruption and avoid duplicate transfers. Exports and backups must include attachment bytes or clearly report missing items.

Expect constrained devices, slow local Wi-Fi, intermittent internet, and incidents with large resource lists or many log entries. Paginate or filter large collections, avoid repeatedly transferring entire incidents, and measure sync and PDF-generation performance with realistic fixtures. Make autosave and queued edits durable across app crashes, battery loss, and host restarts.

#### Access control for responders without existing accounts

The device trust boundary in Part III is the governing design. In summary:

1. A small group of trusted incident administrators signs in using a durable account mechanism (T3) or local administrator credential (T1/T2).
2. An administrator issues a join invitation scoped to one incident, role ceiling, expiry, and use limit, presented as a QR code or short code including host address and host identity fingerprint.
3. The responder enters name, agency/unit, and incident position, or claims an existing ICS 211 roster entry. Identity is self-asserted unless independently verified.
4. The device creates a key pair and redeems the invitation for an individually revocable, device-bound credential scoped to that incident.
5. The host enforces role-based permissions, records changes against device and operator, and expires or revokes access at demobilisation, incident close, or administrator action.

Provide a roster of active devices with expiry and last-seen status, lost-device revocation, rate limits, and invitation revocation. Never treat a shared incident password or an invitation code as a long-lived credential. A redeemed token must not grant database access.

#### Offline transports and data security

Use incident Wi-Fi as the primary local transport. Bluetooth may be evaluated as a secondary option for pairing or deliberate transfer, but it must not become an unencrypted API channel. If no network is available, support deliberate exchange of encrypted, authenticated incident bundles on removable media where operationally appropriate. Do not send incident data in cleartext over radio.

Protect transport and stored credentials, verify the host identity (especially on an ad hoc field network), and minimize sensitive data in logs and notifications. Establish encryption, key recovery, lost-device handling, retention, and secure deletion requirements before deployments contain real incident or personal information. Document what remains available if a device or host is lost.

#### Schema and API change management

Version local documents and API contracts. Use forward migrations that are never edited after release, test upgrades from supported prior versions, and keep backups before migration. Prefer additive/expand-and-contract changes so older clients can continue operating during a gradual rollout. The server must identify incompatible clients clearly rather than accept edits it cannot interpret.

Maintain representative fixture incidents for every supported format and verify load/save round trips, migration, exports, attachments, and unknown-field handling. Test both migration from an empty store and upgrades from released fixtures. Keep form-specific payloads flexible where requirements are still evolving, but model shared identities, incident/operational-period boundaries, and security-relevant fields explicitly.

#### Operational and product concerns

- **Recovery and continuity:** document host backup schedules, restore drills, disk-full behavior, power-loss recovery, and recovery time expectations. A backup that has not been restored in a test is not a proven recovery plan.
- **Deployment and updates:** make field-host installation, upgrade, rollback, and health status understandable to a small team. Plan for delayed client upgrades and avoid requiring internet for local operations.
- **Privacy and records:** classify personal and sensitive incident data, set retention and export policy, control logs, and make audit history tamper-evident enough for operational review. Establish who owns the official record and how an incident is archived.
- **Time and provenance:** preserve unambiguous timestamps and the incident's display time zone; record when data was entered, by which device, and when the host accepted it. Treat device clocks as potentially wrong.
- **Accessibility and usability:** join, sync, conflict, and revocation flows must be usable during stressful operations, with clear status and minimal typing. Avoid hiding authority, stale-data, or pending-sync state.
- **Testing and support:** test API contract compatibility, offline/reconnect paths, simultaneous edits, transfer and recovery, and security boundaries. Provide diagnostics that aid support without leaking incident contents or credentials.

### Repository organization

Keep the work in one repository while the team and product are small. Atomic changes to the model, API, Swing client, server, and tests are more valuable initially than independently versioned repositories. Organize boundaries so a later split remains possible:

```text
icsforms-client/
  model/ or icsforms-model/       shared incident domain and validation
  api/ or icsforms-api/           versioned API contract and DTOs
  client-core/                    persistence, offline outbox, sync, HTTP client
  swing/                          current desktop UI and standalone packaging
  server/                         authorization, incident API, host deployment
  testkit/                        versioned fixtures and contract/transfer tests
  mobile/                         mobile client if its toolchain fits the repository
```

These are logical boundaries first; introduce Maven modules only when they reduce coupling rather than create ceremony. Keep PDF rendering dependent on the shared model, not Swing. A separate mobile repository is reasonable if its platform toolchain or release process makes a shared build impractical; publish and test against the API contract rather than copying DTO definitions. Split repositories only when ownership, release cadence, or tooling makes the single-repository approach a demonstrated obstacle.


## Part IV — Staged work plan

Estimates are rough person-weeks for a small experienced team, excluding review,
field trials and delays; refine after each stage. Each stage leaves a usable,
recoverable standalone application.

### Stage A — Core SAR forms — **complete**

Issues #13–#18 and #35 are closed and their forms are in the merged tree. This
stage is recorded for history only; nothing from it remains in the forward plan.
Resource-request planning remains deferred and separate.

### Stage 0 — Decisions and operational constraints

Agree, in writing, on incident authority and transfer, data classification and
retention, offline conflict semantics, supported device/network assumptions,
join-invitation roles and recovery, host operations, and minimum supported client
versions. Choose the first deployment target (T1 is recommended) and technology
only after those constraints are explicit. This stage may run in parallel with
Stage 1; it must finish before Stage 2 begins.

**Exit criteria:** written decisions, threat and recovery assumptions, first
API/domain boundary, success measures, and a deliberately limited T1 pilot plan.
**Rough size:** 1–2 person-weeks.

### Stage 1 — Local persistence foundation (prerequisite for any remote store)

Stage 1 makes the existing standalone application ready to accept a remote
`IncidentStore` without controller or UI change. It adds **no network code, no
authentication and no server**. The gates below are ordered by dependency; each
is an open issue and must be complete, with its tests, before the next begins
except where noted.

| Gate | Issue | Scope | Depends on | Exit evidence |
| --- | --- | --- | --- | --- |
| **P0** | #43 | Deterministic Swing test lifecycle: explicit controller shutdown, disposed windows/listeners/timers, bounded modal tests, separate headless and Xvfb CI lanes. | — | Full and isolated runs repeatable; displayed lane reports skipped tests; unexpected persistence errors fail tests rather than block. |
| **P1** | #39 | Contract test suite for `IncidentStore`; controllable fake store with deterministic failures and delays; controller/UI tests moved off deprecated `LocalRepository`. | P0 | Contract suite covers durability, detached loads, identity, stale writes, deletion conflicts, failures; fake store used by controller tests. |
| **P2** | #41 | Backend-neutral `IncidentStore` semantics: opaque revision token, optional edit-lock capability (UI never requires a lock to edit), revision-protected delete, path-free `IncidentSummary`, distinct conflict / validation / not-found / duplicate / unavailable / failed outcomes. No `instanceof FileIncidentStore` in UI. | P1 | A lock-less stub store with server-issued tokens substitutes into `AppController` without controller/UI changes; contract tests pass for both. |
| **P3** | #40 | Strict external import vs. tolerant stored load; durable, repeatable identity migration; **entity ID audit** confirming every list-held record has a stable UUID and no form identifies rows by index/text. | P1 | Fixtures for missing IDs, legacy aliases, unknown fields, malformed versions; repeated loads yield identical IDs; invalid imports leave the store unchanged. |
| **T1** | #37 (PR #38) | All timestamps generated and stored in UTC through an injectable clock; displayed and printed in the configured zone. | — (may run any time) | No path generates local-time values; PDFs and UI honour the selected zone. |
| **T2** | #44 | `Provenance` (operator name, station ID, entered-at UTC, reserved accepted-at) on append-style records; injected `OperatorContext` in `AppController`; one schema bump with fixtures. | P3, T1 | Legacy documents load unchanged; new entries carry provenance from context; ICS 214 "prepared by" prefilled only when blank. |
| **P4** | #42 | Asynchronous persistence coordinator on detached snapshots with an abstract unit of work; "replace active document from store" path; two-dimension status (local durability vs. future sync state); failure retention and retry policy. | P0, P1, P2, P3 | Deterministic tests for out-of-order completion, incident switch during save, edits during save, failure→retry, unavailable→available; second unit type substitutes without coordinator changes. |

Parallelism: T1 can proceed immediately. P2 and P3 can proceed in parallel once
P1 lands. T2 follows P3 and T1. P4 is last and is the gate for Stage 2.

**Stage exit criteria:** all gates closed; released-format fixtures load and
round-trip; migrations preserve data and identity; exports unchanged; the
standalone application behaves as before; a `RemoteIncidentStore` stub passes the
contract suite in a spike branch (not merged).
**Rough size:** 4–7 person-weeks.

### Stage 2 — Incident API and single local host (delivers T1)

Implement the incident-scoped API, host-side validation and persistence, and a
`RemoteIncidentStore` built on the Stage 1 contract. The first host is the
desktop application itself (or a small co-installed process) serving other
workstations on incident Wi-Fi with no internet. Add explicit host identity
(self-signed certificate with fingerprint shown for QR embedding) and health
status. Device admission by invitation is included here in its simplest form so
that the API is never reachable without a credential, even in a pilot.

**Exit criteria:** two admitted workstations read and update one incident through
the API; a non-admitted device on the same network is refused; one incident cannot
access another; the standalone path is unchanged; the Stage 1 contract suite passes
against the remote store.
**Rough size:** 4–8 person-weeks.

### Stage 3 — Multi-user editing, operator claim, roles and audit (delivers T2)

Add full invitation lifecycle (issue, expiry, use limit, revocation), device roster,
operator claim from the ICS 211 roster with optional PIN, role-based permissions,
per-record optimistic concurrency, host-populated `acceptedAt`, and audit history
keyed by device and operator. Build clear conflict and stale-update UX. Keep the
first role set to the five roles in Part III.

**Exit criteria:** a responder joins with a scoped invitation in under a minute;
shared-workstation operator switch is one action; administrators see and revoke
devices; simultaneous edits do not silently overwrite; every change is attributable.
**Rough size:** 4–7 person-weeks.

### Stage 4 — Offline sync and attachment handling

Durable local outbox of idempotent operations, ordered host change cursor,
reconnect and conflict workflows, attachment transfer, and closed-operational-
period behaviour. Unsynced work survives client restarts and can be exported.

**Exit criteria:** airplane-mode edits sync correctly after reconnect; replay does
not duplicate work; conflicts and rejected edits are visible and recoverable;
attachments transfer reliably on constrained links.
**Rough size:** 5–9 person-weeks.

### Stage 5 — Field-host packaging and authority transfer

Package and document a field host that runs without internet: installation,
upgrade, rollback, backup/restore, encrypted incident export/import, and planned
authority transfer between hosts. Evaluate Bluetooth or removable-media workflows
only against tested security and operational requirements.

**Exit criteria:** a field exercise starts, operates, backs up, transfers and
restores an incident without internet; no database service is exposed to clients;
host loss and recovery are rehearsed.
**Rough size:** 4–8 person-weeks.

### Stage 6 — Hosted deployment and remote mirrors (delivers T3)

Deploy the same service in a managed cloud environment. Add durable administrator
accounts, secure remote access, monitoring, backups, tested restore, and clearly
read-only mirrors with visible staleness. Define the process for moving authority
between field and hosted deployments.

**Exit criteria:** remote users access only permitted incidents and functions;
restores and transfer are tested; mirror staleness and authority are visible.
**Rough size:** 4–8 person-weeks.

### Stage 7 — Mobile client and operational hardening

Build or integrate a mobile client against the versioned API with offline-first
capture, rapid invitation joining, `field` role by default, attachment workflows
and clear sync status. Conduct field usability, performance, security and
supportability testing before broad deployment.

**Exit criteria:** mobile and desktop clients pass API compatibility,
offline/reconnect, access-control and transfer tests; representative users
complete a field exercise; release, support and incident-record procedures are
documented.
**Rough size:** 6–12 person-weeks.

### Time and provenance roadmap

| Step | Where | What |
| --- | --- | --- |
| 1 | Stage 1 gate T1 (#37) | UTC generation and storage; zone-aware display and print. |
| 2 | Stage 1 gate T2 (#44) | `Provenance` on append-style records; injected `OperatorContext`; `acceptedAt` reserved and unused locally. |
| 3 | Stage 2 | Host records receive time; device clocks treated as untrusted for ordering. |
| 4 | Stage 3 | Host populates `acceptedAt`; operator claim fills `operatorName`/`stationId`; audit history keyed by device and operator. |
| 5 | Stage 4 | Ordered host change cursor replaces wall-clock ordering for sync; local timestamps retained for display and audit. |

## Working rules

- Keep pull requests small, focused, and reviewable; land schema/API changes with their migration and tests.
- Preserve a usable standalone application and deliver a usable, recoverable state after each stage.
- Prefer a tested simple mechanism over speculative infrastructure; avoid distributed consensus, peer-to-peer master writes, and sophisticated merge algorithms until a demonstrated need justifies them.
- Treat offline, stale data, host loss, and responder turnover as normal operating conditions, not exceptional cases.
- Make authority, sync status, role, and data freshness visible to users.
- Test with fixture incidents and realistic field constraints; do not use real sensitive incident data in public test fixtures.
- Revisit estimates and assumptions after each stage and field exercise. Defer work that is not necessary to prove the current stage's exit criteria.

