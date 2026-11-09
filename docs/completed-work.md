# Completed work record

This document holds the detailed record of work that has been delivered, moved
out of [`DraftWorkPlan.md`](../DraftWorkPlan.md) so the plan can focus on what
remains. Content here is historical: statuses, test counts and commit
references were accurate when recorded (most at 2026-11-08, before PR #56) and
are not updated as the code evolves. The binding design contracts that govern
this work remain in the work plan, Part II.

Contents:

- Core forms and foundation issues
- Live local role panels (issues #33, #46, #49): phase status, exit evidence,
  acceptance record and PR #32 reconciliation
- Stage 1 local persistence foundation (issues #37 and #39-#44): gates, entity
  ID audit, T-card history rules and exit assessment
- Live refresh and flicker work (PR #56)

Note: the Stage 1 "T1" and "T2" gates are time gates (UTC and provenance) and
are unrelated to the deployment topologies of the same names.

## Part I — Current status

## Delivered work

| Work | Tracking | Evidence | Status |
| --- | --- | --- | --- |
| Core SAR form set (ICS 203, 205, 215A/208, 206 + canine 206, ICS 233-CG clue log), training fixes | #13, #14, #15, #16, #17, #18 — all closed | Models, panels and PDF renderers for each form are in `src/main/java/org/sarmanagement/icsforms/{model,ui,pdf}`; IAP bundle numbering in `pdf/PdfExportService.java` | **Complete.** Former "Stage A" is no longer a prerequisite. |
| ICS 215 planning worksheet and next-period tactics | #35 — closed | `model/Ics215Form.java`, `ui/Ics215Panel.java` | **Complete.** |
| SAR task status ↔ ICS 214 integration, ICS 204 PDF layout | #19, #20 — closed | `ui/SarTaskPanel.java`, `pdf/Ics204PdfRenderer.java` | **Complete.** |
| File-backed `IncidentStore` with UUID identity, revision checks, per-incident edit locks and backups | PR #34 and predecessors; hardening in #39–#42 | `persistence/IncidentStore.java`, `persistence/FileIncidentStore.java`, `persistence/IncidentDocumentReader.java` | **Complete for standalone use;** backend-neutral contract and asynchronous saving are delivered. |
| Live local role panels (ten-phase plan) | #33 and #46 — closed; PRs #32 and #48 merged | See phase table and acceptance record below | **Implementation delivered.** Automated coverage and user-confirmed desktop checks are recorded separately below. |

## Live local role panels — delivery status by phase

The ten-phase plan from issue #33 is retained below as the authority on *what*
was agreed. Statuses were determined by inspecting the merged tree.

| Phase | Scope | Status | Evidence / gap |
| --- | --- | --- | --- |
| 1 — Baseline/design | Contracts, layouts, scenarios | Delivered | Part II of this document. |
| 2 — Live multi-view | Incident/period-bound change notifications, stale-action guards, disposable listeners, partial-text retention | **Delivered; automated regression coverage** | `ui/AppController.java` binds guarded commits to incident, period, and revision; `ui/MainFrame.java` rejects stale flushes and retains drafts, active table edits, selection, and focus on refresh. `MainFrameDataRetentionTest.staleFullEditorCannotOverwriteSharedCommitAndLiveRefreshRetainsDraftSelection` covers the stale-write and retention path. No implementation gap is identified. |
| 3 — Objectives/linkage | `IncidentObjective` entities, migration, ordered editor, authoritative links, archives | **Delivered; archive independence and round-trip covered** | Rollover in `model/AppData.java` snapshots ICS 202 objectives and assessments, ICS 204 assignments, and linked SAR tasks into detached period history. `IncidentObjectiveTest.periodAdvanceArchivesIndependentObjectiveAssignmentGraphAndRoundTrips` checks identity/link preservation, independent copies, task snapshots, and JSON round-trip. Archive behavior predates PR #48; PR #48 strengthens its regression evidence. |
| 4 — Review tracking | Persisted reviewed-content boundary; Not yet reviewed / Reviewed / Changed since review | Delivered | `model/PlanReviewAssertion.java`, `model/PlanReviewStatus.java`, `model/AppData.java:1750-1800`, `ui/PlanReviewStrip.java`; content-bound lifecycle regressions are in `PlanReviewTest`. |
| 5 — Reference vertical slice | Two nonmodal medical reference windows, cached async previews | Delivered | `ui/HumanMedicalReferenceWindow.java`, `ui/CanineMedicalReferenceWindow.java`, `ui/MedicalReferencePanel.java`, `ui/CanineMedicalFacilityTools.java`, `ui/AsyncFormPreviewPanel.java:35-140`; concurrent canine workspace/reference coverage is in `MedicalReferencePanelTest.canineWorkspaceAndReferenceCanRunConcurrentlyFromSharedDirectory`. |
| 6 — Safety / MUL | Worklist, detail, preview, focused edits | Delivered | `ui/SafetyRolePanel.java`, `ui/MedicalUnitLeaderRolePanel.java`; role-panel and reference regressions cover the current implementation. |
| 7 — Operations | Live task board with status/log/clue/debrief actions | Delivered | `ui/OperationsRolePanel.java`; lifecycle actions are covered in `OperationsRolePanelTest` and `SarTaskPanelTest`. |
| 8 — Incident Command | Objective-centred **editable** layout with supporting assignments and results | **Delivered; automated regression coverage** | `ui/IncidentCommandRolePanel.java` supports objective wording and assessment edits, command emphasis and situational awareness, existing assignment links, normal prelinked assignment creation, and reported results with stated sources. Missing links are indicated in the objectives table and coverage badges; a separate observational-attention panel is not required. Automated coverage is in `IncidentCommandRolePanelTest` and `IncidentCommandPlanningProgressTest`; no achievement/readiness is computed. |
| 9 — Communications | ICS 205 Plan and communications Console modes / `ChannelLinkSource` | **Delivered; #49 console extension** | `ui/CommunicationsRolePanel.java` retains Plan editing and switches to `ui/CommunicationsConsolePanel.java` for task-scoped resource/contact/channel lookup, recent ICP activity, contextual shared-editor logging, and explicit atomic check-plus-log operations through `AppController` / `IncidentStore`. Assignment, availability, and problem logs do not imply checks or readiness; unresolved links remain visible. The reusable `ui/IncidentClockPanel.java` serves Console, timeline, IC, and Operations. `CommunicationsRolePanelTest`, `CommunicationsConsolePanelTest`, `AppControllerCommunicationsActivityTest`, `ActivityEntryDialogTest`, `IncidentClockPanelTest`, and `IcpActivityTimelineDialogTest` cover retained drafts/selections, stable/legacy associations, live refresh, cleanup, cancellation, and rollback (including a backend-neutral rejecting store). See [`docs/role-panels.md`](docs/role-panels.md). |
| 10 — Consolidation/hardening | Panel entry points, retire role-driven tab ordering, canine-tool extraction, accessibility evidence, docs | **Implementation delivered** | Role Panels entry points and stable `WorkspaceId` navigation are in place; `View > Dashboard` ordering is retired. Shared canine directory lookup, facility presentation, routing, and shortlist operations are used by independent workspace/reference Swing instances over the same incident model; concurrent-use/isolation coverage is in `MedicalReferencePanelTest.canineWorkspaceAndReferenceCanRunConcurrentlyFromSharedDirectory`. Role-panel documentation is in [`docs/role-panels.md`](docs/role-panels.md). Resizing and second-screen use are user-confirmed; manual assistive-technology review is not recorded (see acceptance matrix). Offline workflow testing is not applicable to this scope. |

## Issue #46 implementation status and integration acceptance

Issue #33 was closed when PR #32 merged. Issue #46 implementation is delivered:

1. Revision-bound stale-editor protection and live draft/table-state retention
   are implemented and covered by automated regression tests (Phase 2).
2. Period rollover archives detached ICS 202 objectives and assessments, ICS 204
   assignments, and linked SAR tasks. Independence and JSON round-trip are covered.
   The archive behavior predates PR #48; PR #48 strengthens its regression evidence
   (Phase 3).
3. The editable Incident Command objective, ICS 202 context, assignment-linking/
   creation, and sourced-results workflows are delivered and tested. Missing links
   appear in the objectives table and coverage badges; a separate attention panel
   is not required (Phase 8).
4. Shared canine directory/facility lookup and presentation, routing, and shortlist
   operations are used by the workspace and read-only reference, with independent
   Swing instances and automated concurrent-use/isolation coverage (Phase 10).
5. Dashboard retirement, stable `WorkspaceId` navigation, scope decisions, and
   [`docs/role-panels.md`](docs/role-panels.md) are delivered.
6. Remaining acceptance evidence is limited to the specific items in the matrix
   below. No implementation gap is currently identified.

This work is independent of the persistence roadmap in Part IV and must not be
blocked on it; conversely, it must not introduce network, authentication or
remote persistence features.

## Completed foundation issues and their place in the plan

| Issue | Delivered by | Plan location |
| --- | --- | --- |
| #37 — UTC timestamps and display timezone | PR #38 | Part IV, Stage 1, time gate T1 |
| #39 — persistence contract coverage and controllable store | PR #51 | Part IV, Stage 1, P1 |
| #40 — import validation and durable identity migration | PR #52 | Part IV, Stage 1, P3 |
| #41 — backend-neutral concurrency and failure semantics | PR #53 | Part IV, Stage 1, P2 |
| #42 — asynchronous snapshot-based persistence | PR #55 | Part IV, Stage 1, P4 |
| #43 — deterministic Swing lifecycle and displayed-test CI | PR #47 | Part IV, Stage 1, P0 |
| #44 — provenance seam and operator context | PR #54 | Part IV, Stage 1, time gate T2 |
| #46 — complete live role panels | PR #48 | Part I — independent role-panel stream |
| #49 — communications console mode | PR #50 | Part I — communications role-panel extension |

These issues are closed and their PRs merged. Their closure does not by itself
establish that every stage-level exit criterion below has been demonstrated.

---

## Ten phases and exit evidence

| Phase | Scope | Required exit evidence | Current status |
| --- | --- | --- | --- |
| 1 — Baseline/design | Inspect instructions/current master/merged prerequisites; record ownership, contracts, layouts and scenarios; reconcile gaps. | Baseline results and technical contract in this document and PR; prerequisite reconciliation decided. | Baseline and contracts recorded; remaining implementation gaps identified. |
| 2 — Live multi-view | Committed notifications/shared operations, validation/commit lifecycle, partial text/focus/selection retention, stale-write prevention, listener disposal and incident isolation. | Cross-view tests pass before adding windows; invalid input blocks action without loss; stale callbacks rejected. | **Delivered:** incident/period/revision-bound guards, stale-write rejection, live refresh, draft and active-cell/focus retention, and listener lifecycle coverage. `MainFrameDataRetentionTest` contains the key stale-editor regression. No remaining implementation gap is identified. |
| 3 — Objectives/linkage | Entities, legacy migration, ordered/focused ICS 202 editor, compact assignment picker, assessments, authoritative links, history, deduplicated totals and debrief context. | Reorder/edit/delete-resolution, multi-objective assignment, migration/save/reload and independent archival tests. | **Delivered:** stable objectives, assessments, authoritative links, role-panel assignment workflow, and period-end snapshots of ICS 202 objectives/assessments, ICS 204 assignments, and linked SAR tasks. `IncidentObjectiveTest.periodAdvanceArchivesIndependentObjectiveAssignmentGraphAndRoundTrips` checks detached copies and JSON round-trip. Archive behavior predates PR #48; PR #48 strengthens regression evidence. |
| 4 — Review tracking | Content boundaries, substantive/shared-change invalidation, explicit re-review, legacy treatment and fresh-period review. | Navigation/page/preview do not invalidate; content/shared edits do; persistence/rollover copies remain independent. | Review assertions, content-bound invalidation, and rollover independence are covered by model/UI regressions; no separate manual review workflow is claimed. |
| 5 — Reference vertical slice | Modular medical tools, distinct nonmodal references, cached async snapshot previews, facility presentation, veterinary links, task context and confirmed logging. | Both windows open with no cross-type leakage; lookup has no effects; failure/cancellation/staleness leaves structured data usable. Validate before role expansion. | Separate human/canine references and medical workspace exist. Shared canine facility operations and a passing concurrent canine workspace/reference regression are delivered. `AsyncFormPreviewPanelTest` covers worker/cache lifecycle; no specific implementation gap is identified. |
| 6 — Safety/MUL | Responsibility-filtered worklists, detail/preview, focused edits/review and full-editor links. | Preparation/review workflows work without tab hunting or access restrictions, including simple plans. | Panels are delivered; `SafetyPanelTest`, `MedicalReferencePanelTest`, and the displayed suite cover current role-panel workflows. No specific Issue #46 implementation gap is identified. |
| 7 — Operations | Task board, every agreed direct action, recorded resources, objective/member/hazard/comms/results context. | Representative task creation/status/log/clue/debrief/resource scenarios preserve existing lifecycle rules and selection. | Panel is delivered; `OperationsRolePanelTest` and `SarTaskPanelTest` cover representative lifecycle actions. No specific Issue #46 implementation gap is identified. |
| 8 — IC | Agreed objective-centered layout, explicit assessments, linked effort, and results. | Objective wording/assessment edits preserve identity and links; existing assignment links and normal prelinked assignment creation work; results retain stated sources; no calculated achievement, request queue or duplicate allocation count. | **Delivered and tested:** focused objective editing/assessment, visible/editable ICS 202 context, linked and newly created supporting assignments, operator-reported results with sources, and missing-link markers in the table/badges. No separate attention panel or computed achievement/readiness is required. |
| 9 — Communications | Full workflow on PR31's model/accessor/editor/import/PDF. | Resolved/unresolved/broken-link cases preserve recorded semantics; focused edits, review and full tools work live. | Panel, explicit radio-check data, channel links, CSV import/export, and PDF workflows are delivered and covered by existing communications tests. No specific Issue #46 implementation gap is identified. |
| 10 — Consolidation/hardening | Discoverable Panels entry points, stable full workspace/log instances, accessibility evidence, lifecycle robustness and docs. | All migrations, save/reload, standard exports, full tests, Spotless and Checkstyle pass; relevant manual desktop scenarios recorded. | **Delivered:** Role Panels entry points, typed `WorkspaceId` navigation, Dashboard retirement, shared canine operations and [`docs/role-panels.md`](docs/role-panels.md). Resizing and second-screen use are user-confirmed; no online/offline workflow was introduced or required. Automated accessible-name checks exist; a manual assistive-technology review has not been recorded and is not a keyboard-only gate. |

## Acceptance and regression matrix

### Integration acceptance record (2026-11-04)

Automated results and user-confirmed desktop checks are recorded separately.
GitHub Actions run `41762515365` (Tests) and run `41762515527` (Style) completed
successfully for PR revision `5665350`. The earlier Tests run `41757701865`
reported zero jobs and no failed-job logs, so it provides no failure evidence.
Local displayed verification of the current worktree passed 879 tests with no
failures, errors, or skips; Spotless and Checkstyle passed. The archive test
class includes assertions for linked SAR-task snapshots, independence, and JSON
round-trip. Resizing and second-screen use were confirmed in the PR context on
2026-11-04; no device-specific details were recorded.

| Scenario | Automated evidence | Manual acceptance |
| --- | --- | --- |
| Accessible names and assistive-technology behavior in role/full-editor workflows | **Automated:** accessible-name and displayed-layout assertions cover selected components; this is not a complete screen-reader audit. | **Not manually verified.** A manual assistive-technology review is an optional outstanding check; keyboard-only navigation is not a required gate. |
| Resizing role/reference windows and split panes at narrow and large sizes | **Automated:** Xvfb layout tests cover selected role/reference layouts and overflow. | **User-confirmed:** resizing was exercised; no display-scaling details were recorded. |
| Opening role/reference windows across two physical displays and moving them between screens | Not automated. | **User-confirmed:** second-screen use was exercised; no hardware/display details were recorded. |
| Online/offline desktop workflow | No online/offline workflow was introduced or required by Issue #46. Reference open/selection tests verify that no geocoder or routing provider is called implicitly. | **Not applicable** to this scope; not a pending acceptance item. |
| Concurrent canine workspace and reference window with human/canine content isolation | **Automated:** `MedicalReferencePanelTest.canineWorkspaceAndReferenceCanRunConcurrentlyFromSharedDirectory` opens both windows, checks shared directory data and distinct human/canine content, and verifies no provider requests on open/refresh. | **Covered by automated regression.** No manual-only gap is identified. |
| Objective-centered IC editing, objective linking, supporting assignment creation, and sourced results | **Automated:** `IncidentCommandRolePanelTest` and `IncidentCommandPlanningProgressTest` cover editing, links, assignment actions, results, missing-link indicators, and explicit boundaries. | **Automated coverage recorded.** No manual desktop session is claimed. |
| Stale hidden-editor navigation/save after a newer role-panel commit | **Automated:** `MainFrameDataRetentionTest.staleFullEditorCannotOverwriteSharedCommitAndLiveRefreshRetainsDraftSelection` verifies stale-write rejection and retention of partial text, active cell, selection, and focus. | **Covered by automated regression.** No hands-on view/focus gap is identified. |

| Additional acceptance behavior | Status / evidence |
| --- | --- |
| Shared-laptop operator changes do not conflate role view and current position | **Automated:** role/workspace navigation and preference tests cover the single-user view model; multi-operator behavior is outside Issue #46. |
| Small incidents with incomplete plans and concurrent local role/reference windows | **Automated:** role-window and reference-concurrency tests cover incomplete fixtures and independent concurrent canine views. |
| Partial narrative, active table cell, focus and selection survive concurrent edits; hidden controls cannot overwrite newer facts | **Automated:** `MainFrameDataRetentionTest.staleFullEditorCannotOverwriteSharedCommitAndLiveRefreshRetainsDraftSelection` verifies stale-write rejection and retention of partial text, active cell, selection, and focus. |
| Invalid table values remain editable; window disposal releases listeners/workers; incident/period switches do not mix data | **Automated:** existing table-validation, lifecycle-disposal, and incident/period-binding tests cover these invariants. No specific failing or uncovered Issue #46 path is identified. |
| Objective edit/reorder preserves identity and links; linked deletion resolves explicitly; assessments and archived copies round-trip | **Automated:** objective, link, archive, and JSON round-trip regressions exist, including detached linked SAR-task snapshots. |
| Carry-forward refreshes review and resets/carried objective identity/provenance/assessment appropriately; assignments do not carry automatically | **Automated:** plan-reuse and archive regressions cover the current carry-forward and reset behavior. |
| Substantive/shared changes stale review while navigation/preview/page metadata do not; legacy review strings assert no current review | **Automated:** review lifecycle/model regressions cover content-bound staleness and legacy state. |
| Human/canine references preserve type-specific instructions, transport, facilities, preview and mixed-team task context | **Automated:** `MedicalReferencePanelTest` covers human/canine separation, concurrent canine use, and read-only refresh. |
| Unrouted/stale facilities remain accessible, accepted snapshots remain distinct from candidates, and reference lookup has no routing/log side effects | **Automated:** canine facility and reference tests cover stable facility data and no provider calls on reference open/selection. No online/offline workflow was introduced or required. |
| Async preview cancellation, failure, out-of-order completion, cache invalidation and structured-reference availability | **Automated:** `AsyncFormPreviewPanelTest` covers worker lifecycle and cache behavior. |
| Veterinary personnel assignments elsewhere and missing T-card/channel links retain visible legacy information | **Automated:** medical-reference and canine-plan regressions cover linked/missing data and retained reference content. |
| Operations actions use existing workflows without inferring readiness, radio checks or objective achievement | **Automated:** Operations/SAR-task and IC boundary tests cover representative actions and preserve the explicit status boundaries. |
| Typed workspace navigation, separate activity-log instances, standard forms and exports remain stable | **Automated:** navigation, export, persistence, and full Maven regressions pass. |
| Assistive-technology/accessibility inspection beyond automated accessible-name assertions | **Not manually verified.** This is the only recorded optional desktop follow-up; keyboard-only navigation is not a required gate. |

Existing test infrastructure is Maven/JUnit, Swing desktop tests under Xvfb,
PDFBox PDF regressions and persistence round trips. Extend those suites rather
than introducing test frameworks. Baseline success is not final acceptance.

## Reconciliation of PR #32 additions

| Addition | Decision and rationale |
| --- | --- |
| Planning stage | Accepted as an explicitly recorded planning-cycle fact; it does not gate assignment creation, status changes, or deployment. |
| Objective approval | Accepted as a content-bound human assertion separate from plan review; it is not authentication, an electronic signature, or a readiness gate. |
| Radio-check status | Accepted only as an explicit operator-recorded check with person/time; channel assignment, availability, and task status never imply a check. |
| Versioned native CSV | Accepted as a lossless, validated interchange format for channel records; CSV schema version is not a radio check or a new workflow gate. |

These are retained in code for backward compatibility and because they represent
distinct recorded facts or safe interchange behavior. The binding boundaries
remain: channel assignment is not a radio check, and review is not approval.

## Stage A — Core SAR forms — **complete**

Issues #13–#18 and #35 are closed and their forms are in the merged tree. This
stage is recorded for history only; nothing from it remains in the forward plan.
Resource-request planning remains deferred and separate.

## Stage 1 — Local persistence foundation (prerequisite for any remote store)

Stage 1 makes the existing standalone application ready to accept a remote
`IncidentStore` without controller or UI change. It adds **no network code, no
authentication and no server**. The gates below are ordered by dependency; all
tracked issues are closed and their implementations merged. Stage-level exit
evidence is assessed separately after the table. The time gates T1 and T2 below
are not the similarly named deployment topologies.

| Gate | Issue | Scope | Depends on | Exit evidence |
| --- | --- | --- | --- | --- |
| **P0** | #43 — complete (PR #47) | Deterministic Swing test lifecycle: explicit controller shutdown, disposed windows/listeners/timers, bounded modal tests, separate headless and Xvfb CI lanes. | — | Full and isolated runs repeatable; displayed lane reports skipped tests; unexpected persistence errors fail tests rather than block. |
| **P1** | #39 — complete (PR #51) | Contract test suite for `IncidentStore`; controllable fake store with deterministic failures and delays; controller/UI tests moved off deprecated `LocalRepository`. | P0 | `AbstractIncidentStoreContractTest` covers file-store compatibility; `AbstractSnapshotIncidentStoreContractTest` covers the shared snapshot/token contract. `ControllableIncidentStore` supports one-shot failures and latch gates. General controller/UI tests use `IncidentStoreTestFixtures`; `LocalRepository` remains in compatibility tests. |
| **P2** | #41 — complete (PR #53) | Backend-neutral `IncidentStore` semantics: opaque revision token, optional edit-lock capability (UI never requires a lock to edit), revision-protected delete, path-free `IncidentSummary`, distinct conflict / validation / not-found / duplicate / unavailable / failed outcomes. No `instanceof FileIncidentStore` in UI. | P1 | A lockless, pathless in-memory `RemoteIncidentStore` test double uses opaque server-style tokens in controller tests; file and test-double implementations run the shared snapshot contract. |
| **P3** | #40 — complete (PR #52) | Strict external import vs. tolerant stored load; durable, repeatable identity migration; **entity ID audit** confirming every list-held record has a stable UUID and no form identifies rows by index/text. | P1 | Fixtures for missing IDs, legacy aliases, unknown fields, malformed versions; repeated loads yield identical IDs; invalid imports leave the store unchanged. |
| **T1** | #37 — **complete** (PR #38) | All timestamps generated and stored in UTC through `model/UtcTimestamp` injectable clock; displayed and printed in the configured zone. | — | Delivered 2026-10-31; `UtcTimestamp` is the only timestamp source. |
| **T2** | #44 — complete (PR #54) | `Provenance` (operator name, station ID, entered-at UTC, reserved accepted-at) on append-style records; injected `OperatorContext` in `AppController`; one schema bump with fixtures. | P3, T1 | Legacy documents load unchanged; new entries carry provenance from a nonempty injected context; ICS 214 "prepared by" is prefilled only when blank. |
| **P4** | #42 — complete (PR #55) | Asynchronous persistence coordinator on detached snapshots with an abstract unit of work; "replace active document from store" path; two-dimension status (local durability vs. future sync state); failure retention and retry policy. | P0, P1, P2, P3 | Deterministic tests for out-of-order completion, incident switch during save, edits during save, failure→retry, unavailable→available; second unit type substitutes without coordinator changes. |

T-card check-in history is appended only after an accepted edit changes the check-in
time; corrections retain prior events and record the newly selected effective time.
The explicit **Demobilized** status means formal release from the incident and is
the only status transition that records check-out. Cancelled edits, unrelated status
changes, and card removal do not create events. Legacy cards remain without
historical events until a new check-in or demobilization occurs.

Confirmed duplicate T-card merges retain card A's event order, then append card B's
non-shared events in their original order. New events carry stable occurrence IDs;
only matching IDs with identical event contents are deduplicated, so separate events
with the same action and effective time remain distinct. For legacy events without
IDs, exact event contents are treated as shared and the larger repeated-event count
from either card is retained. Skipped or cancelled merges do not change either
history, and a merge itself does not create an event.

**Issue #40 entity-ID audit:** list-held model records now have stable UUID
identities and legacy IDs are persisted during load migration. Coverage includes
AppData operational-period snapshots, additional ICS 204 forms, activity logs,
SAR tasks and resources, T-cards, clue/follow-up records, open actions, archived
hazard reports, safety-analysis rows, and incident locations; nested ICS 201
action/resource rows, ICS 202 objectives/results, ICS 203 positions/branches,
ICS 204 assignments/communications, ICS 205 channels, ICS 208 message blocks,
ICS 214 logs/activity entries/resources, ICS 215 worksheets/headings/work rows,
medical-plan facilities/transports, veterinary directory sources/facilities, and
SAR communications/POD ratings are included. `ActivityEventType` retains its
stable symbolic key for compatibility; ordinal fields such as action numbers and
objective display order are not record identities. String-valued content lists
are values, not records. Legacy activity resource labels remain unchanged for
display; migration promotes only a uniquely resolved link to the stable
assignment ID and leaves ambiguous links unlinked. UI selection now carries the
record ID rather than resolving records by duplicate display text, list position,
or object-reference equality; JTable row indices remain presentation mechanics
only. Repeated-load/restart fixtures cover absent IDs across representative
nested records and verify both legacy-link preservation and ambiguity handling.
External imports without `schemaVersion` remain supported for historical
compatibility and emit a warning directing users to re-export with the client;
present malformed, null, fractional, or future versions are rejected. Persistent
record UUID fields in the import identity registry are covered for malformed and
duplicate values, while foreign-key and legacy display-text references are not
treated as record identities. Stored JSON saves preserve unknown properties by
stable ID when IDs exist. Legacy arrays without IDs are matched only by unique
known record values; if unknown data cannot be associated uniquely, saving fails
with a path-specific error instead of guessing by array position or transferring
properties to a different record.
The import inventory is independently enumerated in
`ImportValidationTest.AUDITED_RECORD_IDENTITIES` and compared against the
reader's registry. `IncidentResource` and its six polymorphic subclasses are
listed in both inventories, but are documented model-only exclusions from
AppData traversal: no current incident document field or collection stores those
types; persisted T-cards are separately validated by `resourceId`.

Duplicate persistent IDs are rejected only within the containing JSON record
array. The root incident ID is a single document identity, not part of a global
child-ID namespace. Reuse across distinct arrays is allowed, including an
operational-period snapshot that retains IDs of records also present in the
current plan; tests assert both same-array rejection and cross-array reuse.

Sanitized synthetic historical JSON fixtures live in
`src/test/resources/incidents/historical/`; the directory README indexes schema,
purpose, and expected migration. When a supported schema or migration changes,
contributors should add or update a representative fixture and its assertions.

**Stage exit criteria:** all gates closed; released-format fixtures load and
round-trip; migrations preserve data and identity; exports unchanged; the
standalone application behaves as before; a `RemoteIncidentStore` stub passes the
contract suite in a spike branch (not merged).

**Exit assessment, 2026-11-08:** all tracked gates are closed and merged.
Historical fixture, migration, export and standalone regression coverage is in
the merged tree. PR #55 CI at `c64edc1` passed the Java 17 headless suite
(1,073 tests, 140 skipped), displayed Xvfb suite (1,073 tests, none skipped),
and Spotless/Checkstyle. This is automated evidence, not a new field exercise.
The merged test-only, lockless `RemoteIncidentStore` double passes the shared
snapshot/token contract alongside the file store and is exercised through
`AppController`; no separate unmerged spike branch was found. That provides
substantial substitute-store evidence but does not literally satisfy the stated
spike-branch criterion or demonstrate a network-backed store. Either complete
the specified spike or explicitly accept the merged double as equivalent
evidence before marking the stage exit complete.

**Topology T0 / T1 readiness:** standalone T0 and the tracked foundation work
are delivered; T1 local-host functionality has not been built. Planning and
Stage 0 decisions can proceed now. Stage 2 implementation is not yet a cleared
start under this plan: Stage 0 decisions must be recorded and the remaining
Stage 1 exit-evidence decision above must be resolved. This does not require
building an API, authentication or an outbox in Stage 1.

**Rough size:** 4–7 person-weeks.

## Live refresh and flicker work (PR #56)

Delivered on `fix/tab-switch-flicker`. Goal: open views reflect the current
model without visible flicker, and never rebuild or reset content that has not
changed.

Causes found and fixed:

1. Every tab change committed every editor, publishing `MODEL_COMMITTED` even
   when nothing changed. No-op editor flushes now restore the snapshot and
   publish nothing.
2. `MainFrame.refreshFromModel()` removed and re-added tabs on every change.
   The tab strip is now reconciled in place; full and light refreshes are
   separate; the initial tab set is built before `pack()` and display
   (`MainFrameDashboardTest.showingFrameKeepsInitialTabsBuiltBeforeDisplay`).
3. Role panels rebuilt worklists and cleared previews. They now skip rebuilds
   when their serialized inputs (`UiRefreshState`) are unchanged and keep the
   current preview visible while a replacement renders.
4. `OrganizationalChartPanel` created new entries with random IDs on every
   flush, so unchanged pushes looked like model changes. Entry identities are
   now retained.
5. ICS 206 and canine medical panels reset a recorded review status outside the
   fixed choice list to "Not reviewed" on refresh. `UiSupport.selectOrAddItem`
   retains the stored value.
6. Plan-reuse availability listed the incident store on every refresh, and the
   persistence wait disables all incident windows, flickering open role panels
   after commits such as adding an ICS 214 log entry. The listing is now cached
   until a controller store operation invalidates it.

Evidence: `MainFrameDashboardTest.unchangedTabSwitchRetainsComponentsWithoutCommitting`,
`MainFrameDashboardTest.repeatedPlanReuseChecksDoNotPauseIncidentWindows`,
`OrganizationalChartPanelTest.unchangedPushKeepsEntryIdentitiesStable`,
`Ics206PanelTest.pushRetainsCustomRecordedReviewStatus`,
`MainFrameNavigationChurnTest`, `MainFrameDataRetentionTest`. Full Maven,
Spotless and Checkstyle passed at `ff349fc` (1120 tests, 14 skipped). CodeQL
had not been run. The maintainer checked tab switches and ICS 214 log-entry
adds with role panels open against the Maple Ridge example
(`docs/examples/maple-ridge-dementia-op1.json`) by hand on 2026-11-08 and
confirmed they work as expected.

Known limits: the plan-reuse cache is invalidated only by this controller's
own store operations, so an incident added by another process is seen after the
next such operation. Windows are still disabled during genuine persistence
waits (saves, loads, imports).

## Role-panel usability tweaks (PR #56)

- Shared `SarTaskStage` supplies the SAR lifecycle stage and colour to the SAR
  Tasks board and table, the Operations task list, and the new compact board.
- Communications Console: "Show all log entries" moved to the right of the log
  bar; the left shows the selected task (truncated) or the all-entries scope.
- Communications panel: Plan/Console buttons became one toggle button.
- Incident Command: resource count columns sized to content; in SAR mode a
  `SarTaskMiniBoard` can replace the table and only rebuilds when its content
  changes.
- Operations and Communications Console task lists colour-coded by stage with
  a selection border (`SarTaskStageListRenderer`); the Operations "Selected
  task" heading names the selected task.

Evidence: `SarTaskStageViewsTest`, `CommunicationsRolePanelTest`,
`CommunicationsConsolePanelTest`.
