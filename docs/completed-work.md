# Completed work record

This document holds the detailed record of work that has been delivered, moved
out of [`DraftWorkPlan.md`](../DraftWorkPlan.md) so the plan can focus on what
remains. Entries are dated and describe the evidence available when recorded;
later additions are noted separately. The binding design contracts that govern
this work remain in the work plan, Part II.

Contents:

- Core forms and foundation issues
- Live local role panels (issues #33, #46, #49): phase status, exit evidence,
  acceptance record and PR #32 reconciliation
- Stage 1 local persistence foundation (issues #37 and #39-#44): gates, entity
  ID audit, T-card history rules and exit assessment
- Live refresh and flicker work (PR #56)
- Host-mode series Part 1 — stable record identity and record diff (issue #57)
- Sharing reliability and data safety (PR #75 for issue #74)

Note: the Stage 1 "T1" and "T2" gates are time gates (UTC and provenance) and
are unrelated to the deployment topologies of the same names.

## Work in progress — issue #61 disconnected workflow

This is a status note, **not a delivery or stage-completion record**. A local
per-authority, per-incident journal, stable queue ordering, replay, and conflict
and clue-duplicate review are under development. The intended behavior and
current limitations are tracked in the
[host mode plan](host-mode-plan.md#issue-61-work-in-progress). Review identified
dependent-edit and ambiguous-authorization replay defects; the current code
and targeted regression tests address those specific cases as detailed in that
plan. Pre-submit rejection and stale-refresh/cursor retry have targeted fixes
and regression tests. The journal now restricts storage to its owner on POSIX
(mode bits) and Windows-style (owner-only ACL) file systems and still fails
closed elsewhere (`PrivateFiles`, `PrivateFilesTest`); on an unsecurable volume
such as FAT or exFAT the journal fails over to another protected location
(`RemoteIncidentStore.connect`), and only when none exists does the window
continue connected-only with a notice. A rejected queue head can
be replaced, from Outbox review, by one edit built from the corrected local
draft after the rejected operations are archived exactly
(`RemoteIncidentStore.replaceRejected`, `RemoteIncidentStoreRejectionTest`).
Tabs label edits the host accepted in the current window
(`MainFrame.acceptedSnapshot`, `MainFrameSyncIndicatorsTest`).
This is not a claim of overall replay safety or acceptance. Still open: the
ACL branch has been exercised only through unit tests on Linux, not on a real
Windows host; an oversized edit cannot yet be corrected: records can now be removed (`REMOVE`,
see "Sharing reliability and data safety") but `replaceRejected` does not use
that and batches are not split; per-view acceptance is not persisted across
restarts; the acceptance scenarios rebuild stores and windows in one JVM (only
the journal commit is exercised across real process kills); and CodeQL on the
final commit is outstanding.
Neither Stage 2 nor Stage 4 exit criteria are satisfied by this work-in-progress;
any future Stage 4 narrowing depends on #61 acceptance. Earlier entries below
describe the scope delivered at the time, not the current status of #61.

## Host-mode series — issue #62 re-authorization and operations

The #62 implementation adds a persisted, capacity-limited host approval queue
and bounded decision audit. Expired non-revoked devices can request a
same-key/same-identity certificate renewal with signed proof; revoked devices
must use a fresh invitation and key pair and receive a new identity. The host
administrator reviews the pending outbox before approval, and the client keeps
its durable work while waiting or after denial. Replay is initiated only after
approval. Accepted repaired work carries both the replacement and revoked
source device IDs in provenance, but only for operations the administrator saw
queued in the approved request.

The unauthenticated request/status routes have per-address/global rate limits,
body caps and generic authorization errors. Approval state is included with the
CA key and registry in the complete host backup. Targeted
`ReauthorizationTest` coverage passed for renewal, denial, repair, rejected old
credentials, provenance, rate limiting and restore continuity. This does not
close Stage 2. Follow-up work on this PR: the client flow moved to
`ReauthorizationFlow` and is tested against a real host and outbox
(`ReauthorizationFlowTest`); the outbox is described to the administrator within
a bounded size (`ReauthorizationOutbox`); `originalDeviceId` is stamped only on
operations the administrator saw queued; and `HostReleaseExerciseTest` runs one
multi-instance scenario. Client credentials and approval tickets remain
session-memory-only. The latter is a release risk if a
client process closes while approval is pending.

Operational procedures, the self-review findings, validation map and proposed
(not yet agreed) response/data-size measures are in
[host operations](host-operations.md),
[security review](host-mode-security-review.md), and
[validation exercise](host-mode-validation.md). Stage 0 and Stage 2 remain
open; host identity rotation is deferred pending real-world use.

## Part I — Current status

## Delivered work

**Planning update, 2026-11-17:** recorded the agreed embedded host-service
direction in the [Stage 0 inventory](stage-0-decisions.md) and added a
[host mode implementation plan](host-mode-plan.md). Subsequent decisions cover
one shared incident per host, version-checked read/save/update operations, local
PDF/export on both host and clients, explicit sharing, individual admission,
stop warnings and connection-loss visibility on both sides. Remaining detailed
implementation choices are proposals; Stage 0 remains open.

**Host-mode foundation update, 2026-11-18:** issue #57 delivers the stable
identity, migration, index and diff foundation described below. It does not
implement the host service or issues #58–#62.

**Host-mode service update, 2026-11-20:** issue #58 delivers the
[version 1 service contract](host-api-contract.md), an in-process
`IncidentService`, and a per-incident file-backed record history with atomic
receipts, deterministic change cursors, three-way field merge, and standalone
save synchronization. The service does not add transport, authentication,
remote-store wiring, UI, or outbox behavior; Stage 2/T1 is not complete.

**Service review follow-up, 2026-11-20:** per-incident operations serialize
across service and file-store instances without relying on advisory edit locks.
The versioned state file is authoritative and a service read repairs a failed
document projection. Regression coverage includes the merge matrix, unknown
bases, commit failure atomicity, backup recovery, lost-response replay,
concurrent clients, standalone create/update/strike-out history, and rollback
when history synchronization fails.

| Work | Tracking | Evidence | Status |
| --- | --- | --- | --- |
| Core SAR form set (ICS 203, 205, 215A/208, 206 + canine 206, ICS 233-CG clue log), training fixes | #13, #14, #15, #16, #17, #18 — all closed | Models, panels and PDF renderers for each form are in `src/main/java/org/sarmanagement/icsforms/{model,ui,pdf}`; IAP bundle numbering in `pdf/PdfExportService.java` | **Complete.** Former "Stage A" is no longer a prerequisite. |
| ICS 215 planning worksheet and next-period tactics | #35 — closed | `model/Ics215Form.java`, `ui/Ics215Panel.java` | **Complete.** |
| SAR task status ↔ ICS 214 integration, ICS 204 PDF layout | #19, #20 — closed | `ui/SarTaskPanel.java`, `pdf/Ics204PdfRenderer.java` | **Complete.** |
| File-backed `IncidentStore` with UUID identity, revision checks, per-incident edit locks and backups | PR #34 and predecessors; hardening in #39–#42 | `persistence/IncidentStore.java`, `persistence/FileIncidentStore.java`, `persistence/IncidentDocumentReader.java` | **Complete for standalone use;** backend-neutral contract and asynchronous saving are delivered. |
| Host-mode Part 1: stable record IDs, load migration, record index/diff | #57 | `model/Identified.java`, `model/IdentityMigration.java`, `model/RecordIndex.java`, `model/RecordDiff.java`, `docs/record-identity.md` | **Delivered in this PR.** Local persistence only; no host/network behavior. |
| Host-mode Part 2: service contract and in-process versioned records | #58 | `docs/host-api-contract.md`, `persistence/IncidentService.java`, `persistence/RecordVersionStore.java`, `persistence/FileRecordVersionStore.java` | **Delivered in this PR.** No network transport, authentication, UI, remote store, or outbox. |
| Host-mode Part 4: production remote store, polling and connected operation | #60 | `persistence/RemoteIncidentStore.java`, `persistence/ConnectionMonitor.java`, `host/HostGateway.java`, `ui/AppController.java`, `ui/MainFrame.java` | **Delivered in this PR.** No durable outbox, re-authorization, roles or operator claim. |
| Live local role panels (ten-phase plan) | #33 and #46 — closed; PRs #32 and #48 merged | See phase table and acceptance record below | **Implementation delivered.** Automated coverage and user-confirmed desktop checks are recorded separately below. |

## Host-mode series — issue #57: stable record identity and record diff

This first series issue gives persisted records, nested collection rows, history
events and singleton forms a common immutable identity contract. JSON writes
canonical `id` properties and reads the old field-specific identity names as
aliases. Schema 18 load migration assigns missing IDs and deterministically
repairs duplicate stored IDs while preserving the first occurrence; migration is
persisted before the load returns. Local create and save also normalize missing
or duplicate identities before committing. Schema-18 external imports reject
blank or duplicate IDs; schema-17 imports preserve the former within-collection
duplicate check and repair cross-collection duplicates with a warning. Stored
files remain tolerant of unknown properties and log warnings.

New-period rollover gives the copied ICS 205 form and channels new identities.
Plan reuse, channel import, facility snapshots, incident duplication, and
period-local record copies mint fresh IDs and remap internal references where
the referenced record is copied.
History remains append-only; this work does not change strike-out behavior.
`RecordIndex` provides deterministic snapshots and parent links, and
`RecordDiff` compares net field values and groups atomic created, changed,
removed and struck-out operations. These are local model primitives only: host
transport, service admission, record-version processing and outbox behavior
remain later series issues.

**CI follow-up, 2026-11-18:** writes assign missing identities without applying
the duplicate-repair rules used for stored-document migration; read-only
projections leave legacy T-card and SAR-task identities unassigned until
explicitly requested. The native radio-channel CSV retains its
`formId`/`channelId` field names while JSON uses canonical `id` properties, and
the checked-in Maple Ridge fixture is the current schema-18 store export.

**Displayed-test CI follow-up, 2026-11-18:** new-period ICS 205 channels and
copied facility/communications records receive independent identities, while
legacy T-card history entries without IDs remain eligible for content-based
overlap reconciliation. The rollover, copy, persistence-retention and duplicate
history regressions cover these identity boundaries.

**Identity review follow-up, 2026-11-18:** task and T-card identity accessors
are now pure; constructors assign IDs to new records, while deserialization
leaves missing IDs for migration. Schema-17 imports repair duplicate IDs shared
across collections with a warning; schema-18 imports still reject document-wide
per-type duplicates. Regression coverage verifies duplicate repair on stored
schema-18 loads and import save/reload stability. The canonical `id` property
and its compatibility warning for older releases are documented.

**Identity review follow-up, 2026-11-19:** rollover archives planned ICS 215
with IDs distinct from a retained live worksheet. Copy-path coverage now checks
incident duplication and internal worksheet links, channel import modes,
facility snapshots, all plan-reuse areas and imported ICS 215. Dedicated
`RecordIndex`/`RecordDiff`, legacy-alias/reference, and schema-17 load/import
tests cover the record foundation and compatibility behavior. Legacy schema-17
unknown properties are ignored with warnings; the fixture's known content is
compared after the established identity and local-only provenance normalization.
`IdentitySetterArchitectureTest` ensures production code outside model and
persistence does not reassign record identities through setters. Mutable
identity setters reject replacement after assignment; migration and intentional
copy operations use explicit identity helpers. Public worksheet row/heading
identity fields remain documented exceptions. The Maple Ridge JSON example was
intentionally added in commit `dcd4fe9` as a fictional schema-18 example and is
used by the stable-identity reflection regression; it contains no operational
incident data.

The identity inventory and the value objects intentionally without IDs are
listed in [`record-identity.md`](record-identity.md).

**Identity and close review follow-up, 2026-11-20:** Public ID-taking
constructors are documented as the supported way to create records with a
known identity. No production identity-setter call sites remain outside
`model` and `persistence`; `IdentitySetterArchitectureTest` now also checks a
synthetic violating `Identified.setId` call to prove the rule detects it.
Existing test-fixture edits replace setter-based identity setup with
`TestIdentitySupport.assign` or ID constructors; no assertions were weakened.
Intentional assertion changes are limited to identity behavior: `Ics205FormTest`
expects archived ICS 205 records to retain their IDs while new-period copies
receive fresh IDs; `ImportValidationTest` checks schema-specific duplicate
handling and every expected duplicate path; `ChannelLinkSourceTest` and
`RadioChannelReferenceTest` verify old channel references do not resolve to
freshly identified carried channels; and `ActivityLogEntryTest` checks the
canonical serialized `id` property. Other listed test edits only update
fixtures, accessors, or schema expectations.

The controller shutdown correction is adjacent lifecycle hardening outside
issue #57. `AppController.close()` restores its open state and prior autosave
timer state when draining issued saves fails, allowing the caller to retry;
listeners, locks, and pending saves remain intact. A regression test covers a
failed drain with an issued write and verifies that the write can settle before
a later successful close.

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
5. Dashboard retirement (the former **View > Dashboard** role-ordered tabs, replaced by one stable workspace order), stable `WorkspaceId` navigation, scope decisions, and
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
`AppController`; no separate unmerged spike branch was found.

**Exit decision, 2026-11-17:** the user accepted the merged test double
as equivalent evidence for the original unmerged-spike criterion. Stage 1 exit
is complete. This is substitute-store evidence, not a demonstration of a
network-backed store; the real remote store remains Stage 2 work.

**Topology T0 / T1 readiness:** standalone T0 and the tracked foundation work
are delivered; T1 local-host functionality has not been built. Planning and
Stage 0 decisions can proceed now. Stage 2 implementation is not yet a cleared
start under this plan: the [Stage 0 decisions](stage-0-decisions.md) must be
recorded and agreed. Stage 1 does not require building an API, authentication or
an outbox.

**Rough size:** 4–7 person-weeks.

## Live refresh and flicker work (PR #56)

Delivered on `fix/tab-switch-flicker` and merged 2026-11-09. Goal: open views reflect the current
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

## Safer "new operational period" opening (PR #56)

The startup dialog previously had separate "Open existing incident" and "Open
existing incident and add a new operational period" buttons, and the second
archived and cleared the plan with no confirmation. There is now one open
button; the incident list offers **Open** and **Open and add new operational
period…**, and the latter asks for confirmation (with the ICS 215 promotion
option). Evidence: `IncidentManagerDialogTest.openingWithNewOperationalPeriodRequiresConfirmation`,
`MainFrameIcs205WorkspaceTest.startupOffersSingleOpenActionWithoutNewPeriodShortcut`.

## Host mode 3/6: secure transport, admission and lifecycle (issue #59)

Delivered the `org.sarmanagement.icsforms.host` package: mutual-TLS HTTPS host
server over the part-2 `IncidentService`, a host-run CA with device-generated
keys, single-use invitations with atomic redemption, a persistent revocable
device registry (72-hour default lifetime), a pre-auth status/credential signal,
rate limits, version negotiation and size limits, loopback-only host
administration, exclusive file ownership with safe start/stop, a combined
host backup/restore including CA keys and registry, and a *Sharing* menu with
`HostSharingDialog`. Standalone mode starts no listener. Documentation lives in
the [API contract](host-api-contract.md), [plan](host-mode-plan.md#operations-issue-59)
and [Stage 0 decisions](stage-0-decisions.md). Connect mode, refresh, outbox,
roles and identity rotation remain out of scope.

Review follow-up: operational period end now caps and enforces credential
expiry (demobilisation has no source yet; open item in the plan); backup and
restore actions in the sharing dialog; clear non-destructive save-conflict
message; cross-process exclusivity test; UI tests for the dialog and the
close-while-hosting confirmation.

Evidence: `HostSessionTest`, `HostBackupTest`, `HostProtocolTest`,
`HostSharingDialogTest`, `MainFrameHostSharingTest`.

## Host mode 4/6: production remote store and connected operation (issue #60)

Delivered the production `RemoteIncidentStore` over `RemoteIncidentGateway`
(`HostGateway` for HTTPS/mTLS), renamed the test-only class to
`InMemoryServerIncidentStore`, added `ConnectionMonitor` (conditional polling,
last-confirmed, stale, lost-contact versus notified host stop; configurable and
documented in the [plan](host-mode-plan.md#4-implement-the-production-remote-store-and-wire-both-uis)),
`AppController.switchStore`/`refreshFromRemote` (draft-preserving), loopback
host mode, *Connect to a host…*, a connection/authority/last-confirmed status
label, and a lost-contact marker in the host device table. Additive
`parent`/`parentField` on read records support document rebuilding.

Evidence: `RealHostIncidentStoreContractTest` (shared snapshot contract against
the real service), `RemoteIncidentStoreHostTest`, `ConnectionMonitorTest`,
`AppControllerRemoteRefreshTest`.

### Follow-up review items (issue #60)

- Contract suites: `AbstractIncidentStoreContractTest` is split by capability and
  runs against the real host in `RemoteIncidentStoreLegacyContractTest`; the
  skipped legacy tests and reasons are listed in the
  [plan](host-mode-plan.md#4-implement-the-production-remote-store-and-wire-both-uis).
- Capability-driven disabling of New, Duplicate, Import and the incident
  manager's New/Load/Delete (`IncidentStore.supportsCreate/Delete/Import`).
- `HostSharingDialog.startSharing()` runs the host start on a background worker.
- Evidence: `SlowGatewayEventThreadTest` (slow gateway under the real store,
  controller and monitor: save, refresh, switch store and polling keep the event
  thread serviced with no gateway call on it), `ConnectedOperationEndToEndTest`
  (host plus two clients, polling, failed-save recovery, local PDFs, root-field
  clean and dirty refresh), `MainFrameConnectedModeTest` (displayed lane: menu
  capabilities, slow connect flow, status line, notified stop versus lost
  contact, draft-kept message, host/standalone switching, clean refresh keeps the
  tab), `DeviceConnectionTextTest` (lost-contact marker).
- Editor drafts: `AppController.setEditorDraftProbe` lets `MainFrame` report
  uncommitted editor input (edited text, uncommitted spinner text, a table cell
  being edited, a retained conflicting draft, open editor windows). A remote
  refresh then returns `DRAFT_PRESERVED` without flushing or replacing anything;
  the monitor offers the snapshot again on its next poll. Covered by
  `RemoteRefreshEditorDraftTest` (displayed lane).
- Issue #69: clean remote refresh retains text focus, caret, selection and
  viewport positions. Activity-log editors are retained by stable log identity;
  location and ICS 214 resource selections use stable IDs; other table selections
  are cleared rather than transferred to a potentially different row.
  `UiRefreshStateTest` covers displayed view state
  and log identity, and `AppControllerLiveChangeTest` checks stale bindings.

**Displayed-test CI follow-up:** the connected-operation test establishes the
saved controller-default baseline used by host sharing before opening clients.
It exercises stale, independent client edits before polling and applies test
edits and polling assertions on the Swing event thread.
The host-stop displayed test also waits for the completion callback and checks
that standalone status is not shown while the local-store load is pending.

**Remote-refresh and displayed-window follow-up:** `AppControllerLiveChangeTest`
checks that refresh during and after a failed save keeps the model draft and its
original token and exposes a conflict.
`ConnectedOperationEndToEndTest` exercises a host loopback window and two
connected windows, polling, failed-save recovery and local PDFs. The sharing
tests arm slow transport before connecting and assert their bounded start wait.
The [host plan](host-mode-plan.md#4-implement-the-production-remote-store-and-wire-both-uis)
clarifies the numeric revision placeholder in remote JSON exports.

## In-person pairing — issue #74

Replaces copying a roughly 560-character invitation with a short number the
device shows and the host operator types in (PR #75, in progress).

* `host/pairing` holds the swappable `SasSuite` (`ecdh-p256-sha256-sas`: ECDH
  P-256 with a hash commitment, giving a 6-character number of 4 digits and 2
  letters, I and O excluded, about 22.5 bits) and `ChannelSuite` (HKDF-SHA-256
  with AES-256-GCM) behind `PairingSuites`, plus `PairingCode`,
  `PairingPayload` and `PairingTranscript`. All cryptography is the Bouncy
  Castle FIPS module (`bc-fips`, `bcpkix-fips`) via `crypto/Fips`; nothing is
  hand-written. The finite-field J-PAKE first draft was replaced because the
  FIPS module has no PAKE and the exchange is in person.
* `ShortPairing` is the single in-memory window (expiry, one awaiting device at
  a time, burned after three failures). `EnrollmentHandler` serves
  `/v1/pairing/short/begin`, `/open`, `/status` and `/finish`; `HostApiHandler`
  serves `/v1/admin/pairing` (open, `GET` state), `:enter`, `:cancel` and
  `:close`. Admission reuses the invitation path.
* `HostClient.pairInPerson` supports typed entry (capturing trust) and a QR
  payload (pinned fingerprint). `HostGateway.pair` accepts a long invitation,
  `ICSFORMS-PAIR-3` QR text or `ICSFORMS-PAIR-1:host[:port]` typed entry, and a
  `PairingListener` shows the number.
* UI: **Start adding a device** in the Sharing dialog (address, fingerprint
  QR, number entry); one connect form (address, advanced paste, find hosts)
  and a `PairingCodeWindow` showing the number. mDNS discovery
  (`HostDiscovery`, JmDNS with `slf4j-jdk14`) advertises only while a window is
  open.
* Packaging: the FIPS module checks its own jar, so it is not merged into the
  shaded jar. It is embedded unmodified under `fips/`, and `Launcher` (the jar's
  main class) extracts it to `~/.icsforms/lib/` and starts `App` through a
  class loader over the extracted jars, so the release stays a single jar.
* Tests: `PairingCoreTest`, `ShortPairingTest`, `QrImagesTest`.
* Camera scanning, in-person re-pairing of revoked devices and approved-only FIPS mode and
  bctls-fips are out of scope.

The host dialog now fits the usable screen and scrolls on smaller displays.
Host start no longer fails when a standalone edit replaces a single-valued
record, such as importing a vet directory over the default one. The record
history (`<incident>.records.json`) now marks a removed or replaced record as
retired (`retiredAtCursor`) instead of leaving it live. Retired histories keep
their versions and receipts for audit but are not projected into the document
or served in reads and change feeds. Existing histories that already hold a
stale record are repaired on the next read: a live history whose single-valued
slot is occupied by a newer record is retired, along with its descendants.
Remote reconstruction through the change feed does not yet receive explicit
retirement entries.

Entered data is saved without an explicit save action. An edited text field
schedules the autosave when it loses focus and on each key release, and
combo-box and check-box actions do the same, so remote devices see edits
promptly and a pending edit no longer blocks remote refresh. Multi-field record
dialogs (for example a T-card) still save when the dialog completes.

The connect and host dialogs were reworked. `ConnectDialog` replaces the old
form and the separate pairing-number window: it finds hosts on the network,
offers advanced options (typed address, pasted invitation), shows the networks
this device is on, reports progress, and shows the pairing number in large print
with instructions in the same dialog. A device is identified by its host name
and user name. The host dialog is wider, lists the networks the host is on and
detected addresses, labels the address and ports clearly, formats times to the
minute, labels the host certificate fingerprint as advanced, and has a Hide
button that keeps sharing running. `NetworkInfo` supplies the network details.

## Host dialog workflow polish and quiet autosave

- Autosave failures are logged and retried quietly instead of opening a modal; remote refreshes keep table row selections by content.
- The Host this incident dialog is now laid out as numbered steps (1 set up, 2 start sharing, 3 add a device), left-aligned, with the firewall remark beside the ports.
- Detected addresses are de-duplicated (`NetworkInfo.distinct`): all IPv4 addresses plus one global IPv6 address per interface.
- A "waiting device" list shows the device (host and user) wanting to join; sharing opens the pairing window automatically.
- Revoke is enabled only when a paired device is selected, and is also in a right-click menu.

## Sharing reliability and data safety (PR #75 for issue #74)

Work on the `short-code-pairing` branch while making host sharing and two-machine
LAN synchronisation dependable. The governing rules: every entry is saved
immediately, silently and in the background; nothing a user types is lost or
blocked; and one instance making a sequence of edits to a data object is not a
conflict, nor are two instances filling separate empty fields.

- **Typed input is never lost.** Autosave is deferred while a field is being
  edited, a recovery copy of unsaved drafts is kept, and an editor flush
  failure no longer stops saving (`AppController.captureSave`). Nothing takes
  focus unless it is an explicit workflow step.
- **Legacy objective text** no longer invents phantom objective records.
- **Stable default identity.** Default T-card rack headers have stable IDs and
  the rack shows one column per label (`AppController.defaultHeaderId`). The
  ICS 203 assignment list (`Ics203Form`) is now `Identified`, following the
  `MedicalPlan` pattern, because its agency representatives and divisions
  could not be created through the host (a record nested under a non-record
  parent is a data-model defect). Legacy files get a random id until saved.
- **History-preserving removal.** `IncidentService.Operation.REMOVE` retires a
  record at a new cursor and keeps every version for audit; a record changed
  since the submitter's base version becomes a `$record` conflict. Retired
  histories are not projected (`RecordDocumentBridge.remove`). The client
  (`RemoteIncidentStore`) emits REMOVE for removed records, no longer rejects
  removals, and handles it in replay and conflict review. Documented in
  `docs/host-api-contract.md`; `HostApiHandler` maps it to the delete
  permission.
- **Independent creates merge.** Two writers creating the same stable-ID
  record merge field by field (`IncidentService.mergeIndependentCreates`); only
  a field both filled with different non-empty values conflicts. The same merge
  applies in `RemoteIncidentStore.dependentConflicts` and `resolvePending`.
- **Editor revision gate limited to remote-backed stores.** A local store has
  one writer, so `AppController.revisionCurrent` never rejects a commit there;
  against a remote store a full editor commit after a newer shared commit is
  still rejected. Binding-identity checks continue to protect replaced models.
- **Event-thread interruption.** `AppController.awaitOwner` keeps waiting for
  the persistence worker when the event thread is interrupted; previously
  `workerIo` returned null and the controller threw a `NullPointerException`
  (regression test in `AppControllerAsyncPersistenceTest`). What interrupted
  the thread in the original local failure is unknown.
- **Tests and CI speed.** Real mDNS registration cost seconds per host session;
  surefire now sets `icsforms.discovery=none` (`HostDiscovery.create`), cutting
  `HostSharingDialogTest` from about 76 s to 4 s and `ShortPairingTest` from
  45 s to 10 s. The dialog tests use a 50 ms stop grace
  (`HostSharingDialog.setStopGrace`) and a shared `@BeforeEach` harness, and
  `stopKeepsHostingWhenFlushOrLocalSwitchFails` no longer depends on an exact
  message count (the host start also requests an invitation that can report
  its own failure). A full displayed local build runs in about 6.5 minutes,
  down from 8.5.
- **Stable identity for default children.** A child stored inline by an earlier
  build (the organizational chart's ICS 203 assignment list) and any default
  single-valued child the host holds no record for now get an identity derived
  from the parent and field (`RecordDocumentBridge.adoptInlineChild`,
  `stabilizeDefaults`, called after a full client projection in
  `RemoteIncidentStore.build`). Previously every rebuild minted a random id,
  which looked like a replaced child and produced a self-conflict in the host's
  own journal. Merging duplicate T-cards now repoints SAR task and ICS 215
  links to the retained card (`AppController.repointTCardLinks`).
- **Retired single-valued children.** When the host retires a record held in a
  single-valued field (for example the ICS 203 assignment list replaced during
  conflict resolution) and the stored document still holds it, projection now
  empties the field before attaching the live replacement
  (`RecordDocumentBridge.clearRetiredOccupant`). Previously the host failed to
  read its own incident ("Parent field already contains a record"), so the
  loopback journal could not acknowledge the accepted batch and its queue never
  drained. A stale conflict now refreshes itself instead of failing.
- **Outbox review wording.** Choices read "Keep my edit" / "Keep the stored
  copy", with keep-all buttons, and the list no longer shows the internal
  `offline-` operation prefix.
- **Host dialog pairing flow.** The pairing button sits beside the
  "This Host: address:port until hh:mm" and waiting lines, reads "Devices can
  join" (disabled) while the window is open and "Allow devices to join" once it
  closes; the start button reads "Sharing" while hosting. Revoked devices are
  hidden unless "Show revoked devices" is ticked. The long invitation and the
  certificate fingerprint are in a collapsed "Advanced" section, with a copy
  button once an invitation exists. The window opens larger so the first view
  needs no scroll bars on a screen of 800 px height or more.
- **Dialogs fit the screen.** `ScreenFit` makes every dialog resizable, clamps
  it to the usable screen area (so buttons stay visible at `uiScale=2`), and
  scrolls content that cannot fit. It is installed at startup for all dialogs
  and used by the `UiSupport` resizable option and confirm dialogs.
- **Plan reviews reach every view.** Role panels re-bind to the latest incident
  on each change event (they ignored events after the first remote refresh), and
  the review strips embedded in the ICS 205, ICS 206, canine medical and safety
  tabs refresh with their panel. Review assertions are covered by the
  host-and-client sync tests.

Still open: the disconnected-entry follow-ups in issue #72 (including using
`REMOVE` for oversized-edit recovery), the stuck-tab report whose cause was
never pinned down (diagnostic logging was added in `MainFrame.pushToModel`),
auditing other panels for direct deletes and random-id defaults in shared mode,
whether the displayed CI lane still needs sharding, an in-app font scale
setting, and CodeQL on the final commit.

## Concurrent creation conflicts

- Two devices that each create the child of a single-valued field (for example
  the organizational chart's assignment list) no longer make the host fail its
  projection with "Parent field already contains a record". The record whose
  history began later is kept and the earlier one, with its descendants, is
  retired with all versions kept.
- Two devices that give different SAR tasks the same Assignment/Team number:
  the task editor refuses a number another task already uses and suggests the
  next free one, and `IncidentValidator` flags the later of any duplicates that
  still arrive through sync so a person can renumber one. Neither task is
  discarded.

## Parallel-entry conflicts, continued

- A task editor that is open when another device saves the same Assignment/Team
  number now shows the conflict live and offers the next free number in the same
  pattern (`G1` becomes `G2`).
- Objectives added at the same position by two devices are settled silently and
  identically on every device (`Ics202Form`).
- ICS 204 rows linked to a SAR task show the task's number read-only, since the
  task already overwrote it on sync; unlinked 204 rows reusing a task number are
  flagged by the validator.
- Radio channel numbers repeated within a zone are flagged on the ICS 205 tab
  and by the validator, and saving one prompts to use the next free number, keep
  both, or keep editing (`RadioChannelNumbers`).
- Closing, switching or stopping sharing now saves open detached editors first
  and warns only when one cannot be saved automatically (for example a modal
  dialog).

## Host dialog tuning and connection troubleshooting

- While devices can join, the host dialog shows "Devices can join this Host:
  address:port until hh:mm" in place of the disabled button, and the button
  returns when the window closes. The QR code moved to Advanced, with guidance.
- The long invitation is a secret: only its `ICSFORMS-INVITE-n:` marker is shown
  and the rest is masked. "Copy to clipboard" copies the full text. Advanced
  explains when to use it instead of the QR / join window.
- Paired devices have a Details button and context-menu item. The host records
  the address of each device's last authenticated request (in memory only,
  `DeviceInfo.lastAddress`).
- The networks list marks each interface wireless or wired where the operating
  system says so (`NetworkInfo.kindOf`).
- The Connect dialog has Troubleshoot, which checks name resolution, ping and the
  pairing and data ports (`HostReachability`).
- Canine medical plan: linked veterinary personnel lists only the linked people,
  with an Add T-cards picker and Remove; links to missing cards are kept.
