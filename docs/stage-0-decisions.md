# Stage 0 decision inventory

Status: **open**, 2026-11-17. This inventory maps the Stage 0 exit criteria to
existing material and the decisions still needed. The targets in
[`DraftWorkPlan.md`, Part III](../DraftWorkPlan.md#part-iii--target-architecture-and-deployment-topologies)
are not evidence that these operational choices have been agreed. Stage 2
production host deployment remains blocked until Stage 0 exits. The explicit
contract and local service slice in issue #58 may proceed against its recorded
design decisions.

## Recorded decisions

The following decisions were provided on 2026-11-17:

- **Devices and packaging:** support devices able to run Java; package the
  application as Java applications. Exact runtime and platform requirements
  remain to be specified.
- **Host architecture:** one application can run in host mode and authorize
  other instances to connect to its backing service. Embed the logically
  separate service initially; both its own UI and connected UIs use the service
  boundary without a direct-write bypass. The first implementation shares one
  selected incident at a time. Standalone mode remains available.
  The [host mode implementation plan](host-mode-plan.md) records the proposed
  delivery sequence and distinguishes agreed decisions from remaining proposals.
- **Service operations:** authorized instances can read, submit changes against
  their observed version, and check for updates. The host validates submissions
  and explicitly accepts or reports conflicts/failures, without silent overwrite.
- **Workstation-local files:** both host and connected workstations can generate
  PDFs and export files locally. File selection and reading for imports are
  local too; permission and semantics for importing into shared data remain open.
- **Sharing lifecycle:** the host operator explicitly starts sharing; connecting
  instances are admitted individually. Warn before stopping with clients
  connected and make a deliberate stop clear to reachable clients. Connection
  loss is visible on both sides once detected; entered work is not silently
  discarded. An unexplained disconnect cannot establish whether the host
  stopped or the network failed.
- **Encryption and threat:** encrypt data in transit against an attacker
  monitoring network traffic. Data-at-rest encryption is not required at this
  stage. Other threats and credential protection remain to be considered.
- **Disconnected work:** users can add information while disconnected. A
  deduplication and conflict-resolution process is needed; a likely case is the
  same event being logged twice in the same log. The process and the stage in
  which disconnected entry becomes available remain to be specified.
- **Record identity and changes (issues #57 and #58):** persisted records, nested rows,
  history events and singleton forms have immutable stable IDs serialized as
  `id`; legacy names are read as aliases. Stored legacy data receives missing
  IDs once during load migration, which is persisted before returning the
  document. Copies that become new records receive new IDs, and copied internal
  references are remapped. History is retained rather than physically deleting
  or striking-out records. Record-level submissions use per-record versions,
  three-way comparison of net field values, atomic multi-record batches,
  indefinitely retained version history and operation receipts, and a durable
  client outbox.   Issue #58 now adds an in-process service contract, retained per-record
  versions, ordered cursors, field-level merge results, atomic batches, and
  durable receipts over managed incident files. Its
  [API contract](host-api-contract.md) defines DTOs and error mappings. This
  does not implement transport, credentials, the remote store, UI, or outbox;
  Stage 0's deployment, threat, recovery, and success-measure decisions remain
  open.

## Existing targets and remaining decisions

| Area | Existing target or constraint | Decisions or evidence still needed |
| --- | --- | --- |
| Incident authority and transfer | One authoritative host per incident; planned freeze, export, validated import and retirement or read-only conversion of the old host. Standalone use remains available. | Who authorizes a transfer; how authority is verified, clients redirected and outstanding edits handled; recovery when the outgoing host is lost. |
| Data classification and retention | Protect incident and personal information; minimize sensitive logs; data-at-rest encryption is not required at this stage. | Classifications and permitted pilot data; record owner, retention and deletion rules; credential protection, key custody and recovery; archive and export policy. |
| Offline edits and conflicts | Users can add information while disconnected; no silent overwrites or discarded drafts. Stage 2 owns the durable outbox; Stage 4 expands it into robust replay and conflict recovery. | Issue #61 is [work in progress](host-mode-plan.md#issue-61-work-in-progress): targeted fixes and regression tests address the named dependent-edit, ambiguous-authorization, stale-refresh/cursor retry and pre-submit rejection defects, not full acceptance. Oversized rejected work and rejected-head corrective recovery remain open. Verify recovery and duplicate-event review separately from retry deduplication; resolve closed-period behavior, synchronization and attachments. Windows journal verification remains open. |
| Current information | Views update affected content without flicker or lost drafts; disconnected or stale information is labelled with its last confirmed time. | Define confirmation and staleness semantics, polling or notification expectations and measurable responsiveness; specify behavior when a refresh or host connection fails. |
| Devices and networks | Devices able to run Java; Java application packaging; incident Wi-Fi with no internet required; no direct client database access. | Minimum Java runtime, platform and hardware requirements, client count, network and connectivity assumptions, host discovery and address changes, and realistic incident/data sizes for the pilot. |
| Admission, roles and recovery | Invitation-based device admission and host identity verification; no shared incident password. Stage 2 includes basic admission; Stage 3 expands lifecycle, operator claims and roles. | Stage 2 invitation and credential lifecycle, secure credential storage, expiry and revocation, administrator bootstrap and recovery, lost-device handling, and which permissions are actually enforced in the pilot. Resolve the Stage 2/Stage 3 boundary without weakening the device trust boundary. |
| Host operations and recovery | Local host without internet; backups and restore are required for later field packaging. | Pilot installation and startup, health and diagnostics, backup frequency and location, restore procedure and drill, host/disk/power failure handling, acceptable data loss and recovery time, and responsible operators. |
| Versions and technology | Java application packaging with an embedded host service; host and connected UIs use the service boundary. Versioned JSON API and explicit transfer objects; host owns authorization, validation and storage. Later topologies are opt-in. | Select persistence and identity technologies after constraints are agreed; define supported client/API versions, compatibility, upgrades, rollback and incompatible-client behavior. |
| Threat assumptions | Data in transit must be encrypted against network monitoring. Same-network outsiders must be refused; incident boundaries enforced on the host; host identity pinned and device credentials individually revocable in the target design. | Document other attacker capabilities and physical assumptions, sensitive assets, invitation interception, lost devices, host impersonation, credential compromise and residual risks; decide mitigations and recovery appropriate to the pilot. |
| First API/domain boundary | Issue #58 specifies record operations and DTOs, incident isolation, per-record versions, merge/conflict semantics, cursors, errors, compatibility, and request limits in the [host API contract](host-api-contract.md). | Implement and verify the transport and authorization boundary; decide refresh/freshness behavior and operational compatibility policy. Do not assume file-store revisions or advisory locks are universally available. |
| Success measures and pilot | Stage 2 exit criteria require two admitted workstations, isolation, unchanged standalone operation, remote-store contract coverage, visible staleness and draft-preserving refresh. | Agree measurable performance, join, reliability, recovery and usability thresholds and how they will be tested; choose participants, environment, duration, permitted data, scope, stop conditions and decision owner. |

## Exit checklist

- [ ] Record and agree the operational decisions and constraints above,
      including first deployment target and technology selection.
- [ ] Record threat and recovery assumptions, including residual risks.
- [ ] Specify the first API/domain boundary.
- [ ] Agree measurable success criteria and how evidence will be collected.
- [ ] Agree a deliberately limited T1 pilot plan and its approval to proceed.

The Stage 1 substitute-store evidence was accepted separately; it does not
demonstrate a network-backed store. See the
[Stage 1 exit assessment](completed-work.md#stage-1--local-persistence-foundation-prerequisite-for-any-remote-store).

## Credential and admission decisions (issue #59)

- Mutual TLS with a host-run CA; the device generates its key pair and the host signs its certificate. The JDK TLS/HTTP server and Jackson are used. BouncyCastle (`bcpkix`/`bcprov` 1.87) is added solely because the JDK has no public API to build X.509 certificates or parse PKCS#10 requests; no custom cryptography is written.
- Invitations are short-lived, single-use, scoped to the shared incident, and carry host address plus the CA fingerprint so the client pins the host.
- Credentials last 72 hours by default and also expire at period close/demobilisation when a cutoff is set. They are individually revocable and revocations persist across restarts.
- Pilot permissions: connect, read, write, update, delete at incident granularity only. No roles or operator claim.
- The host application is the pilot administrator, using an in-memory per-start credential over loopback; remote devices are never administrators.
- The CA key, device registry and revocations live in host-owned storage (`host/` beside the incident store, owner-only) and are included in the host backup. The client private key is held in an owner-only Java keystore.
- Host identity rotation is **deferred** (open item). A changed host certificate is never trusted silently.

## Remote store and connected operation decisions (issue #60)

- The host UI uses a loopback `RemoteIncidentStore` with its administrator credential while sharing; standalone keeps `FileIncidentStore`. Stopping sharing switches the window back to the local store (unsaved work must be saved first).
- Production `RemoteIncidentStore` is distinct from the test double `InMemoryServerIncidentStore`. Tokens are snapshot-specific and opaque; edit locks are not offered; file paths never reach clients.
- Refresh is conditional polling of the committed cursor. Interval and stale threshold are configurable (`icsforms.remote.pollIntervalMillis`, `icsforms.remote.staleAfterMillis`); "last confirmed" is the local time of the last successful poll, with no device-clock comparison with the host.
- A notified host stop is shown differently from an unexplained loss of contact.
- Newer remote revisions never advance the token of an older draft.
- UI actions that a store cannot perform (create, delete, import) are disabled from the store's capabilities (`supportsCreate/Delete/Import`), not from a mode flag.
- Create, delete and import remain host-administrative. Issue #60 used session-only
  credentials; re-authorization and credential persistence remain outstanding.
  The part 5 outbox is work in progress, not delivered; its journal uses
  owner-only permissions (POSIX) or ACLs (Windows-style) and fails closed
  otherwise.
