# Draft Work Plan

## Live local role panels — issue #33

Tracking issue: https://github.com/sarmanagement/icsforms-client/issues/33

Implementation PR: https://github.com/sarmanagement/icsforms-client/pull/32

This section records the agreed ten-phase delivery plan. It supersedes the older
role-tab-ordering proposal below, not the separate future multi-user plan.
All ten phases belong in **one draft PR**, with phase-sized commits and recorded
exit evidence. Do not merge automatically. An unchecked phase is not delivered.
Issue #33 now exists; the earlier drafted tracking issue was not published by
this agent.

### Phase 1 inspection and baseline evidence

- Inspected the repository instructions and current source before editing.
- The supplied implementation branch is `copilot/implement-live-role-panels`.
  Its initial empty planning commit directly follows the current merged baseline,
  PR #31 merge `8bc0f2bcdf3b3798335aa9dde9b67f134039ee5d`.
  The execution environment already created draft PR #32 against `master`;
  no additional PR or branch is needed.
- PR #28's separate human/canine models, shared Medical Unit Leader assignment,
  medical history copies, and veterinary directory/routing tools are present.
- PR #31's ICS 205 model, tab, imports, PDF, typed workspace destinations and
  immutable `ChannelLinkSource` snapshots are present. They are prerequisites
  to reuse, not abstractions to replace.
- On the unmodified Java baseline, executed
  `xvfb-run -a mvn -B test spotless:check checkstyle:check`.
  Result: **BUILD SUCCESS; 600 tests, 0 failures, 0 errors, 1 skipped;
  0 Checkstyle violations; Spotless passed**. This is baseline evidence, not
  evidence that any new panel has been implemented or validated.
- GitHub Actions master style run
  https://github.com/sarmanagement/icsforms-client/actions/runs/40158253134
  succeeded. Queried PR #31 run
  https://github.com/sarmanagement/icsforms-client/actions/runs/40157545974
  has no failed jobs. Local full-suite verification above independently closes
  the outstanding baseline-verification question; no baseline failure was found.

### Actual contracts and prerequisite gaps

The source references in this section describe the inspected baseline.

| Area | Existing contract | Required reconciliation |
| --- | --- | --- |
| Shared editing | `/home/runner/work/icsforms-client/icsforms-client/src/main/java/org/sarmanagement/icsforms/ui/MainFrame.java:1103-1119` flushes every editor; `:1178-1233` reloads editors and rebuilds activity-log tabs. `/home/runner/work/icsforms-client/icsforms-client/src/main/java/org/sarmanagement/icsforms/ui/AppController.java:363-372` synchronizes linked values and schedules autosave, without committed-change listeners. | Do not attach broad refresh callbacks to `markDirty`: that would risk partial text loss and stale hidden-editor writes. Establish validated edit ownership, committed notifications and presentation-state retention before concurrent role windows. |
| Incident lifecycle | `/home/runner/work/icsforms-client/icsforms-client/src/main/java/org/sarmanagement/icsforms/ui/MainFrame.java:1557-1597` resolves outstanding editors and saves before switching. | Preserve these safeguards; bind actions and asynchronous completions to incident and period generation, invalidate pending actions on switch/rollover, and unregister listeners on disposal. A window must never flush old input into a replacement incident. |
| Medical roles | `/home/runner/work/icsforms-client/icsforms-client/src/main/java/org/sarmanagement/icsforms/model/AppData.java:686-733` provides the canonical Medical Unit Leader assignment and legacy reconciliation. `/home/runner/work/icsforms-client/icsforms-client/src/main/java/org/sarmanagement/icsforms/ui/Ics206Panel.java:264-277` binds the relevant editable name inputs. | Reuse shared role operations and preserve legacy conflicts. Do not make one form the mandatory editing location or infer an electronic signature from a role assignment. |
| Objectives | `/home/runner/work/icsforms-client/icsforms-client/src/main/java/org/sarmanagement/icsforms/model/Ics202Form.java:12-43` stores mutable strings; `/home/runner/work/icsforms-client/icsforms-client/src/main/java/org/sarmanagement/icsforms/ui/Ics202Panel.java:137-169` replaces them from multiline text. | Add stable objective entities and migration, ordered/focused editing, explicit assessments and one authoritative assignment relationship; text/index matching cannot provide identity. |
| Period history | `/home/runner/work/icsforms-client/icsforms-client/src/main/java/org/sarmanagement/icsforms/model/AppData.java:35-152` archives period dates, commanders and medical/comms plans. `:327-357` clears ICS 202, ICS 204, SAR tasks and resources without archiving their graph. | Historical objective wording, links and assessments require an additive independent period-end snapshot of the objective/assignment context before clearing. Old files cannot recover already-discarded history. Keep one active period; do not introduce simultaneous next-period planning or a cross-period panel. Report a scope adjustment before any broader lifecycle redesign. |
| Communications copies | `/home/runner/work/icsforms-client/icsforms-client/src/main/java/org/sarmanagement/icsforms/model/Ics205Form.java:193-222` deep-copies inventory, retaining channel identities; new-period copying resets form identity, dates, signature and page. | Preserve explicit channel-link semantics and independent archives. New review assertions must reset for the new period. |
| Review | `/home/runner/work/icsforms-client/icsforms-client/src/main/java/org/sarmanagement/icsforms/model/MedicalPlan.java:29-35` has preparer/reviewer strings and printed signatures, not a reviewed-content boundary. | Add separate persisted assertion metadata/content boundaries for safety, human, canine and comms. Never interpret a legacy Reviewed/Approved string as evidence of review of current content. |
| Preview | `/home/runner/work/icsforms-client/icsforms-client/src/main/java/org/sarmanagement/icsforms/ui/FormPreviewPanel.java:52-80` renders all PDF pages synchronously from the supplied document. | Render detached snapshots asynchronously; cache by relevant content, reject obsolete results, support cancellation and label updating/older/error states without disabling structured reference. |
| Medical modularity | `/home/runner/work/icsforms-client/icsforms-client/src/main/java/org/sarmanagement/icsforms/ui/CanineMedicalPanel.java` owns plan, directory, regional-file, routing and shortlist controls. | Extract reusable components/operations without duplicating incident state or reparenting the same Swing tree into two windows. Keep the adjacent plan/directory tabs. |

The history and editing gaps are substantive prerequisites, not completed features.
The intended bounded solution remains local multi-view editing and additive
period-end archives. If that boundary cannot satisfy the implementation, report
the specific lifecycle/period scope issue rather than silently expanding it.

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

### Ten phases and exit evidence

| Phase | Scope | Required exit evidence | Current status |
| --- | --- | --- | --- |
| 1 — Baseline/design | Inspect instructions/current master/merged prerequisites; record ownership, contracts, layouts and scenarios; reconcile gaps. | Baseline results and technical contract in this document and PR; prerequisite reconciliation decided. | Inspection and baseline recorded; gaps identified, implementation reconciliation pending. |
| 2 — Live multi-view | Committed notifications/shared operations, validation/commit lifecycle, partial text/focus/selection retention, stale-write prevention, listener disposal and incident isolation. | Cross-view tests pass before adding windows; invalid input blocks action without loss; stale callbacks rejected. | Not implemented. |
| 3 — Objectives/linkage | Entities, legacy migration, ordered/focused ICS 202 editor, compact assignment picker, assessments, authoritative links, history, deduplicated totals and debrief context. | Reorder/edit/delete-resolution, multi-objective assignment, migration/save/reload and independent archival tests. | Not implemented. |
| 4 — Review tracking | Content boundaries, substantive/shared-change invalidation, explicit re-review, legacy treatment and fresh-period review. | Navigation/page/preview do not invalidate; content/shared edits do; persistence/rollover copies remain independent. | Not implemented. |
| 5 — Reference vertical slice | Modular medical tools, distinct nonmodal references, cached async snapshot previews, facility presentation, veterinary links, task context and confirmed logging. | Both windows open with no cross-type leakage; lookup has no effects; failure/cancellation/staleness leaves structured data usable. Validate before role expansion. | Not implemented. |
| 6 — Safety/MUL | Responsibility-filtered worklists, detail/preview, focused edits/review and full-editor links. | Preparation/review workflows work without tab hunting or access restrictions, including simple plans. | Not implemented. |
| 7 — Operations | Task board, every agreed direct action, recorded resources, objective/member/hazard/comms/results context. | Representative task creation/status/log/clue/debrief/resource scenarios preserve existing lifecycle rules and selection. | Not implemented. |
| 8 — IC | Agreed objective-centered layout, explicit assessments, linked effort, results and observational attention. | Normal prelinked assignment creation/linking works; no calculated achievement, request queue or duplicate allocation count. | Not implemented. |
| 9 — Communications | Full workflow on PR31's model/accessor/editor/import/PDF. | Resolved/unresolved/broken-link cases preserve recorded semantics; focused edits, review and full tools work live. | Not implemented. |
| 10 — Consolidation/hardening | Discoverable Panels entry points, stable full workspace/log instances, accessibility/keyboard/resizing/second-screen/offline behavior, lifecycle robustness and docs. | All migrations, save/reload, standard exports, full tests, Spotless and Checkstyle pass; manual desktop scenarios recorded. | Not implemented. |

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

### Exclusions and scope boundary

No permissions/authentication/e-signatures, automatic events, clinical support,
suitable-facility choice, computed achievement, formal resource requirements/
request/approval/ordering queue, structured meeting workflow, mobile application,
remote persistence/sync/concurrency, combined-role widget builder, cross-period
panel, simultaneous next-period planning, or blanket legacy-frequency replacement.
Do not reinterpret assignments as resource requests. The future multi-user
proposal below does not become a prerequisite or part of issue #33.

---

## Future multi-user, hybrid deployment, and SAR form completion

## Summary and recommendation

`icsforms-client` is currently a single-user Java Swing application. It saves an incident workspace as local JSON and can export forms and an IAP bundle as PDFs. It does not yet provide shared, multi-user editing or a mobile client.

The recommended sequence is to finish the core SAR document set already tracked in issues #13–18 before starting client/server implementation. This gives responders a coherent set of documents in the existing standalone application and avoids building network infrastructure around an incomplete workflow. Issue #16 is already closed; the remaining scope includes ICS 203, ICS 205, ICS 215A/208, ICS 206 and its canine variant, and the ICS 233-CG clue-log adaptation. Defer resource-request planning; it is a separate workflow and should not hold up this work.

After the forms are complete, establish explicit decisions for identity, persistence, synchronization, deployment, and data ownership. Then build a versioned domain and API boundary, prove it first in the existing desktop client, and add shared-host and mobile capabilities in small increments. The schema need not be permanently final before client/server work; the migration and compatibility process must be dependable.

The system should support three operating modes without removing today's standalone workflow:

1. **Standalone:** one client uses local persistence and works without a network.
2. **Field host:** a computer at an incident hosts the authoritative incident service on a local network, even without internet access.
3. **Hosted:** a cloud instance, for example on Amazon EC2, hosts the authoritative incident service for remote access.

The central design rule is that clients use an incident-scoped API and never connect directly to the database.

## Target architecture

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

## Authority, ownership, and transfer

Each incident has one authoritative host at any moment. This avoids split-brain edits when a field deployment loses internet connectivity or a cloud link fails. Devices may create offline edits, but those edits are pending client changes until accepted by the current authority.

Moving an incident between field and hosted modes is a planned operation, not automatic multi-master replication:

1. The outgoing host stops accepting edits or enters a clearly marked transfer/freeze state.
2. Connected clients sync; outstanding offline edits are identified and retained for resolution.
3. The host creates a complete, integrity-checked incident export, including attachments and schema version.
4. The receiving host imports and validates the incident and confirms the new authoritative identity.
5. Clients are directed to the new authority; the old host becomes read-only or is securely retired.

Remote mirrors should be explicitly described as read-only, and show their last successful synchronization time. Define how stale data is presented so no responder mistakes a mirror for the current authoritative record.

## Engineering challenges and design requirements

### Concurrent editing and auditability

Several responders may edit separate assignments or forms at once. Avoid whole-incident locks. Give independently editable records stable identifiers and versions, and use optimistic concurrency so stale updates are detected instead of silently overwriting newer work. Prefer append-oriented records for activity logs, clues, and similar events. Keep an audit history of changes, including the attributed responder, device, time, and operation.

For genuine conflicts in narrative fields, start with a visible conflict-resolution workflow and retained prior values. Do not introduce complex text-merging algorithms until field experience demonstrates a need. Conflict handling must be usable on a small screen and understandable under incident pressure.

Cross-form values are shared facts, not separate copies. Define which form or entity owns each value (for example, resource identity, operational period, assignment, and personnel) and derive other views and PDFs from it. Record the source and time for derived values, and make corrections flow back to the authoritative source rather than creating inconsistent duplicates.

### Offline synchronization

Clients need a durable local cache and outbox. Queue idempotent, uniquely identified operations so retrying after a lost connection does not duplicate an assignment, clue, or log entry. Synchronize with an ordered host change cursor rather than trusting device wall clocks. Preserve local timestamps for display and audit, but do not use them as the sole ordering mechanism.

Specify behavior for stale clients, deleted or struck-out records, schema changes, and edits made while an operational period is closed. Do not silently discard queued work. Show pending, accepted, rejected, and conflicting changes clearly, with a way to recover/export unsynced work.

### Attachments, performance, and reliability

Photos, maps, and sketches can be large and are important in SAR. Store attachment metadata separately from form records; transfer metadata first and upload/download bytes on demand when possible. Use content hashes to detect corruption and avoid duplicate transfers. Exports and backups must include attachment bytes or clearly report missing items.

Expect constrained devices, slow local Wi-Fi, intermittent internet, and incidents with large resource lists or many log entries. Paginate or filter large collections, avoid repeatedly transferring entire incidents, and measure sync and PDF-generation performance with realistic fixtures. Make autosave and queued edits durable across app crashes, battery loss, and host restarts.

### Access control for responders without existing accounts

Incident responders need fast access without individually provisioned system accounts, but they must not receive broad database or service access. A proposed low-friction flow is:

1. A small group of trusted incident administrators signs in using an appropriate durable account mechanism.
2. An administrator issues a join invitation scoped to one incident, role, expiry, and use limit. It can be presented as a QR code or short code and include the host address and a way to verify its identity.
3. The responder enters their name, agency/unit, and incident position. Mark this identity as self-asserted unless independently verified.
4. The device creates a key pair and redeems the invitation for an individually revocable, device-bound credential scoped to that incident and role.
5. The host provides role-based permissions, records changes against the device/responder, and expires or revokes access at demobilization, incident close, or administrator action.

Define practical roles (for example, observer, field responder, section lead, planner, and incident administrator) in terms of specific allowed operations. Provide a roster of active devices, expiry and last-seen status, a lost-device revocation action, rate limits, and a way to revoke invitations. Never treat a shared incident password or an invitation code as a long-lived credential. A redeemed token must not grant database access.

### Offline transports and data security

Use incident Wi-Fi as the primary local transport. Bluetooth may be evaluated as a secondary option for pairing or deliberate transfer, but it must not become an unencrypted API channel. If no network is available, support deliberate exchange of encrypted, authenticated incident bundles on removable media where operationally appropriate. Do not send incident data in cleartext over radio.

Protect transport and stored credentials, verify the host identity (especially on an ad hoc field network), and minimize sensitive data in logs and notifications. Establish encryption, key recovery, lost-device handling, retention, and secure deletion requirements before deployments contain real incident or personal information. Document what remains available if a device or host is lost.

### Schema and API change management

Version local documents and API contracts. Use forward migrations that are never edited after release, test upgrades from supported prior versions, and keep backups before migration. Prefer additive/expand-and-contract changes so older clients can continue operating during a gradual rollout. The server must identify incompatible clients clearly rather than accept edits it cannot interpret.

Maintain representative fixture incidents for every supported format and verify load/save round trips, migration, exports, attachments, and unknown-field handling. Test both migration from an empty store and upgrades from released fixtures. Keep form-specific payloads flexible where requirements are still evolving, but model shared identities, incident/operational-period boundaries, and security-relevant fields explicitly.

### Operational and product concerns

- **Recovery and continuity:** document host backup schedules, restore drills, disk-full behavior, power-loss recovery, and recovery time expectations. A backup that has not been restored in a test is not a proven recovery plan.
- **Deployment and updates:** make field-host installation, upgrade, rollback, and health status understandable to a small team. Plan for delayed client upgrades and avoid requiring internet for local operations.
- **Privacy and records:** classify personal and sensitive incident data, set retention and export policy, control logs, and make audit history tamper-evident enough for operational review. Establish who owns the official record and how an incident is archived.
- **Time and provenance:** preserve unambiguous timestamps and the incident's display time zone; record when data was entered, by which device, and when the host accepted it. Treat device clocks as potentially wrong.
- **Accessibility and usability:** join, sync, conflict, and revocation flows must be usable during stressful operations, with clear status and minimal typing. Avoid hiding authority, stale-data, or pending-sync state.
- **Testing and support:** test API contract compatibility, offline/reconnect paths, simultaneous edits, transfer and recovery, and security boundaries. Provide diagnostics that aid support without leaking incident contents or credentials.

## Repository organization

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

## Staged work plan

Estimates are rough person-weeks for a small experienced team, excluding external review, field trials, and delays. They should be refined after each stage; they are not release commitments. Parallel form work can reduce calendar time but should still share a definition of done.

### Stage A — Complete the core SAR forms first

**Scope:** Finish the core SAR document set in the existing standalone application, tracked by issues #13–18. This includes ICS 203 PDF generation from the organization chart; ICS 205 with radio links, frequency import/export, and landscape PDF support; ICS 215A and ICS 208 safety planning; ICS 206 and canine medical planning; and the ICS 233-CG clue-log adaptation. Issue #16's training usability fixes are already closed and should not be reopened as unfinished form work.

Defer resource-request planning. Keep this stage focused on the listed core forms and their existing incident workflow.

**Exit criteria:**

- Remaining agreed issue #13–18 work is complete, reviewed, and usable in standalone mode.
- Form data survives save/reopen and is reflected correctly in PDF and combined IAP export.
- Cross-form data sources and unresolved resource-request requirements are documented.
- The current local workflow remains usable without a host or network.

**Rough size:** 4–8 person-weeks, depending on PDF layout and integration complexity.

### Stage 0 — Decisions and operational constraints (no client/server implementation yet)

Once Stage A is complete, agree on incident authority and transfer, data classification and retention, offline conflict semantics, supported device/network assumptions, join-token roles and recovery, host operations, and the minimum supported client versions. Choose a first deployment target and a technology stack only after those constraints are explicit. This stage is planning and small technical spikes; it does not begin building shared-client/server functionality before the form set is complete.

**Exit criteria:** written decisions, threat and recovery assumptions, a first API/domain boundary, success measures, and a deliberately limited first shared-host pilot.

**Rough size:** 1–2 person-weeks.

### Stage 1 — Versioned domain and local persistence

Introduce stable identities for records, explicit document versions, and a tested migration path for existing local JSON incidents. Separate shared domain behavior from Swing and PDF presentation as needed. Preserve backward-compatible loading and keep the standalone application as the reference client.

**Exit criteria:** released-format fixtures load and round-trip; migrations preserve data; exports remain stable; data identity does not depend on list position.

**Rough size:** 3–6 person-weeks.

### Stage 2 — Incident API and single-host service

Implement an incident-scoped API, host-side validation, persistence, and a local development/test deployment. Start with one authoritative host and basic incident reads and writes. Do not expose database credentials or ports to clients. Add explicit host identity and health status before remote deployment.

**Exit criteria:** two authenticated client sessions can read and update an incident through the API; authorization tests prove one incident cannot access another; the existing standalone path still works.

**Rough size:** 4–8 person-weeks.

### Stage 3 — Multi-user editing, roles, and audit

Add invitation redemption, device credentials, role-based permissions, expiry/revocation, optimistic concurrency, and change history. Build clear conflict and stale-update UX. Keep the first role set small and validate it with incident users.

**Exit criteria:** a responder can join quickly with a scoped invitation; administrators can see and revoke access; simultaneous edits do not silently overwrite one another; changes are attributable.

**Rough size:** 4–7 person-weeks.

### Stage 4 — Offline sync and attachment handling

Add a durable local outbox, idempotent operations, ordered change feed, reconnect and conflict workflows, attachment transfer, and closed-operational-period behavior. Ensure unsynced work survives client restarts and can be recovered.

**Exit criteria:** airplane-mode edits sync correctly after reconnect; replay does not duplicate work; conflicts and rejected edits are visible and recoverable; attachments can be transferred reliably on constrained links.

**Rough size:** 5–9 person-weeks.

### Stage 5 — Field-host deployment and transfer

Package and document a field host that can run on a computer at an incident without internet. Support a local Wi-Fi network, safe host identity, backup/restore, encrypted incident export/import, and planned authority transfer. Evaluate Bluetooth or removable-media workflows only against tested security and operational requirements.

**Exit criteria:** a field exercise can start, operate, back up, transfer, and restore an incident without internet; no database service is exposed to clients; host loss and recovery steps are rehearsed.

**Rough size:** 4–8 person-weeks.

### Stage 6 — Hosted deployment and remote mirrors

Deploy the same service in a managed cloud environment, such as EC2 if selected. Add secure remote access, monitoring, backups, tested restore, and clearly read-only mirrors for remote viewing where needed. Define the process for moving authority between field and hosted deployments.

**Exit criteria:** remote users can access only permitted incidents and functions; restores and transfer are tested; mirror staleness and authority are visible; operations do not depend on a developer manually editing production data.

**Rough size:** 4–8 person-weeks.

### Stage 7 — Mobile client and operational hardening

Build or integrate a mobile client against the versioned API, with offline-first capture, rapid invitation joining, attachment workflows, and clear sync status. Conduct field usability, performance, security, and supportability testing before broad deployment. Add forms or specialized workflows beyond the core set based on operational feedback; revisit deferred resource-request planning separately.

**Exit criteria:** mobile and desktop clients pass API compatibility, offline/reconnect, access-control, and transfer tests; representative users complete a field exercise; release, support, and incident-record procedures are documented.

**Rough size:** 6–12 person-weeks, with substantial variation by mobile platform and offline UX scope.

## Working rules

- Keep pull requests small, focused, and reviewable; land schema/API changes with their migration and tests.
- Preserve a usable standalone application and deliver a usable, recoverable state after each stage.
- Prefer a tested simple mechanism over speculative infrastructure; avoid distributed consensus, peer-to-peer master writes, and sophisticated merge algorithms until a demonstrated need justifies them.
- Treat offline, stale data, host loss, and responder turnover as normal operating conditions, not exceptional cases.
- Make authority, sync status, role, and data freshness visible to users.
- Test with fixture incidents and realistic field constraints; do not use real sensitive incident data in public test fixtures.
- Revisit estimates and assumptions after each stage and field exercise. Defer work that is not necessary to prove the current stage's exit criteria.

## Issue #17 — Medical plans and role-oriented workspaces

Issue #17 is a separate, seven-phase desktop workflow. It adds a standard human ICS 206 and an explicitly adapted canine ICS 206CANINE, plus role-oriented views over shared incident editors. The canine tab is normally eligible when the incident is SAR mode **or** at least one canine T-card exists; hiding it must not remove its saved data. A saved canine workspace must remain openable from View navigation even when normal eligibility is false.

The canine directory is stored as an incident-owned copy. One or more reusable regional JSON/CSV files may be merged into an incident, edited there, and explicitly exported later. The accepted shortlist is a separate snapshot, so saving eight selected vets must not truncate the regional directory or require source files to remain present. Do not write edits to source files implicitly.

Address geocoding and driving routing are independent provider interfaces. ORS is the default: resolve address candidates for operator confirmation, then calculate ground driving distance from the staging/base to every eligible resolved facility before ranking. Use distance as the primary order and display route duration only when provided by the routing service. A straight-line fallback is an explicitly labeled estimate, not road distance or travel time. Do not imply that routing verifies a facility's availability or capability. ORS receives addresses/coordinates; never send clinical or personal details, and do not store the key in incident data. OSRM is a possible route alternative, but requires a separate geocoder and coordinates. Check the providers' current terms, quotas, and request limits before production use.

These seven implementation phases are tracked in one pull request, not seven separate PRs:

- [ ] **Phase 1 —** Additive plan/directory/base models, migrations, safe legacy defaults, and period rollover archives.
- [ ] **Phase 2 —** Standard ICS 206 numbered blocks, shared PDF frame/footer, measured pagination, and regression tests.
- [ ] **Phase 3 —** Human plan editor, simple/detailed retention, role suggestions, dirty-state/lifecycle behavior.
- [ ] **Phase 4 —** Local JSON/CSV directory merge/export, address geocoding, default ORS routes, provenance, offline/error handling.
- [ ] **Phase 5 —** Independent canine editor and adapted PDF, nearest-vet proposal review, dynamic eligibility, saved-data access.
- [ ] **Phase 6 —** IC/ICP, Safety Officer, Operations, and Communications dashboards over shared editors; show safety, org chart, and medical workspaces to Operations and Communications.
- [ ] **Phase 7 —** IAP attachment/page-number integration, documentation, full tests, and migration/export regression evidence.

Issue #14 remains separate: it owns the ICS 205 model/editor, radio plan, frequency-file import, landscape PDF, and radio-to-resource linking. This issue provides a Communications dashboard using existing contacts/resources/logs and a future extension point only; it does not implement ICS 205 or invent an ICS 205A editor.
