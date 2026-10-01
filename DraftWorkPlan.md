# Draft Work Plan: Multi-User, Hybrid Deployment, and SAR Form Completion

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
