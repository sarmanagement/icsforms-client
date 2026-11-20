# Stage 0 decision inventory

Status: **open**, 2026-11-17. This inventory maps the Stage 0 exit criteria to
existing material and the decisions still needed. The targets in
[`DraftWorkPlan.md`, Part III](../DraftWorkPlan.md#part-iii--target-architecture-and-deployment-topologies)
are not evidence that these operational choices have been agreed. Stage 2
implementation remains blocked until Stage 0 exits.

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
- **Record identity and changes (issue #57):** persisted records, nested rows,
  history events and singleton forms have immutable stable IDs serialized as
  `id`; legacy names are read as aliases. Stored legacy data receives missing
  IDs once during load migration, which is persisted before returning the
  document. Copies that become new records receive new IDs, and copied internal
  references are remapped. History is retained rather than physically deleting
  or striking-out records. Record-level submissions use per-record versions,
  three-way comparison of net field values, atomic multi-record batches,
  indefinitely retained version history and operation receipts, and a durable
  client outbox. This records the issue #57 design boundary; it does not
  implement the later host/service, version admission or offline-sync work.

## Existing targets and remaining decisions

| Area | Existing target or constraint | Decisions or evidence still needed |
| --- | --- | --- |
| Incident authority and transfer | One authoritative host per incident; planned freeze, export, validated import and retirement or read-only conversion of the old host. Standalone use remains available. | Who authorizes a transfer; how authority is verified, clients redirected and outstanding edits handled; recovery when the outgoing host is lost. |
| Data classification and retention | Protect incident and personal information; minimize sensitive logs; data-at-rest encryption is not required at this stage. | Classifications and permitted pilot data; record owner, retention and deletion rules; credential protection, key custody and recovery; archive and export policy. |
| Offline edits and conflicts | Users can add information while disconnected; no silent overwrites or discarded drafts. Stage 2 owns the durable outbox; Stage 4 expands it into robust replay and conflict recovery. | How duplicate events are identified and resolved, distinct from retry deduplication; conflict recovery for concurrent edits and closed periods; synchronization and attachment behavior. |
| Current information | Views update affected content without flicker or lost drafts; disconnected or stale information is labelled with its last confirmed time. | Define confirmation and staleness semantics, polling or notification expectations and measurable responsiveness; specify behavior when a refresh or host connection fails. |
| Devices and networks | Devices able to run Java; Java application packaging; incident Wi-Fi with no internet required; no direct client database access. | Minimum Java runtime, platform and hardware requirements, client count, network and connectivity assumptions, host discovery and address changes, and realistic incident/data sizes for the pilot. |
| Admission, roles and recovery | Invitation-based device admission and host identity verification; no shared incident password. Stage 2 includes basic admission; Stage 3 expands lifecycle, operator claims and roles. | Stage 2 invitation and credential lifecycle, secure credential storage, expiry and revocation, administrator bootstrap and recovery, lost-device handling, and which permissions are actually enforced in the pilot. Resolve the Stage 2/Stage 3 boundary without weakening the device trust boundary. |
| Host operations and recovery | Local host without internet; backups and restore are required for later field packaging. | Pilot installation and startup, health and diagnostics, backup frequency and location, restore procedure and drill, host/disk/power failure handling, acceptable data loss and recovery time, and responsible operators. |
| Versions and technology | Java application packaging with an embedded host service; host and connected UIs use the service boundary. Versioned JSON API and explicit transfer objects; host owns authorization, validation and storage. Later topologies are opt-in. | Select persistence and identity technologies after constraints are agreed; define supported client/API versions, compatibility, upgrades, rollback and incompatible-client behavior. |
| Threat assumptions | Data in transit must be encrypted against network monitoring. Same-network outsiders must be refused; incident boundaries enforced on the host; host identity pinned and device credentials individually revocable in the target design. | Document other attacker capabilities and physical assumptions, sensitive assets, invitation interception, lost devices, host impersonation, credential compromise and residual risks; decide mitigations and recovery appropriate to the pilot. |
| First API/domain boundary | Clients use an incident-scoped API, not the database; host validates and authorizes requests. | Specify the initial operations and DTO/domain ownership, incident isolation, version negotiation, revision and conflict semantics, errors and update/freshness mechanism. Do not assume file-store revisions or advisory locks are universally available. |
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
