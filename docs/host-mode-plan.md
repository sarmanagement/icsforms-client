# Host mode implementation plan

Status: **issue #58 in-process service delivered**, 2026-11-20. The service
contract and versioned record service are implemented without a listener,
authentication, remote store, outbox, or UI change. This does not deliver host
mode or complete Stage 2/T1. The remaining work expands
[Stage 2](../DraftWorkPlan.md#stage-2--incident-api-and-single-local-host-delivers-t1);
the remaining [Stage 0 decisions](stage-0-decisions.md) still apply.
See the [host API contract](host-api-contract.md) for version 1 DTOs and
semantics.

## Agreed direction

One application instance can run in host mode and authorize one or more other
instances to use its backing service. The service is a separate logical layer,
initially embedded in the host Java application rather than a separate required
installation. Both the host's UI and connected UIs use that service boundary;
the host UI has no direct-write bypass. The first host-mode implementation shares
one selected incident at a time; it does not permanently restrict the service
design to one incident.

```text
Host workstation UI -------+
                           |
Connected workstation UI --+--> Incident service --> Host-owned storage
                           |
Additional workstation UI -+
```

Existing standalone operation remains available without starting a network
listener. Packaging stays Java-based. Network traffic is encrypted; encrypting
incident data at rest is not required at this stage.

### Service contract and lifecycle decisions

Authorized instances can read the shared incident, submit changes against the
record versions they read, and check for updates. A record is the unit of
submission and versioning. The host validates each atomic batch and either
accepts it or reports a clear conflict or failure; newer work must never be
silently overwritten.

For each submitted record, compare the client's base values, current host
values, and proposed values field by field. Accept a client-only change, retain
a host-only change, and report a conflict when both sides changed the same field
to different net values. Equal net values are unchanged, including an
A-to-B-to-A edit cycle. A record's base is identified by its version number.
Keep record-version history and successful operation receipts indefinitely.
Multi-record operations are atomic: the host applies every record in a batch or
none of them. A durable client outbox stores pending operations and their
identities so retry cannot duplicate an accepted batch.

Both host and connected workstations can generate PDFs and export files.
Output is generated and saved locally on the workstation requesting it, not to
an arbitrary path on the host. Input files are likewise selected and read on
the workstation performing an import; whether and how an import may change the
shared incident remains to be specified separately.

The host operator explicitly starts sharing, and connecting instances are
admitted individually. Warn before stopping while others are connected.
Connection loss must be visible on both sides: the affected client shows loss
of contact with the host, and the host shows loss of contact with the client
once detected. Entered work must not be silently discarded.

A deliberate host shutdown must be made clear to reachable clients before
transport closes. If notification cannot be delivered, clients must still
detect loss of contact; they cannot reliably distinguish a network failure from
a host crash. Detection deadlines and shutdown acknowledgment/draining behavior
remain protocol details to specify.

## Proposed first delivery

These are implementation proposals, not additional agreed Stage 0 decisions:

- Offer **Standalone**, **Host an incident** and **Connect to a host** modes.
- Keep the existing file store behind the service initially. Only the service
  accesses the shared incident's managed files; clients never use a network
  filesystem or receive host file paths.
- Use a versioned HTTPS/JSON API with explicit request/response DTOs. Use the
  same HTTP adapter for the host UI over loopback and remote UIs over the LAN,
  exercising identical authorization, validation and conflict behavior.
- Keep application packages in this repository; do not introduce separate
  repositories or Maven modules merely to create the service boundary.
- Begin with record-level submissions through the existing `IncidentStore` seam.
  Use per-record version numbers and three-way net-field comparison; never
  silently use last-write-wins. Retain record-version history and operation
  receipts indefinitely, and commit multi-record batches atomically.
- Include the durable local outbox in Stage 2 so pending operations survive
  client restart and can be retried idempotently. Stage 4 expands this foundation
  into robust offline replay, ordered change cursors, conflict recovery and
  attachment synchronization.

## Implementation sequence

Each phase depends on the preceding foundation unless stated otherwise. These
are implementation slices of one Stage 2 delivery, not separate releases or a
requirement for separate pull requests.

### 1. Specify the service contract and lifecycle

Define incident selection, host identity, supported API/client versions,
request limits, structured errors and the mapping to existing store outcomes.
Initially negotiate compatible client versions explicitly rather than
promising untested cross-version compatibility.

Separate host administration (share, invite, revoke, stop) from incident
read/write operations. Specify which operations connecting devices may invoke;
do not expose the entire local store-management API by default. Local import,
export and PDF generation on either workstation must not become arbitrary
host-filesystem operations.

Specify successful and ambiguous batch outcomes. A request needs an operation
identifier and a host-side durable receipt, committed atomically with its effect,
so retry after a lost response cannot duplicate an accepted batch. Keep receipts
indefinitely. This is distinct from identifying two independently entered
reports of the same event.

Exit evidence: reviewed protocol examples for read, save, conflict, unavailable,
invalid, unauthorized, revoked and incompatible-client responses; restart and
lost-response behavior defined.

### 2. Establish exclusive host ownership and safe lifecycle

Create a service that owns storage access, validation, conditional writes and
shutdown. Reuse file-store revision checks and supported ownership mechanisms
inside that backend, not as requirements on every client or future backend.
Prevent a standalone editor or second host process from bypassing the service
and concurrently writing the shared files. Verify existing file locks cover the
needed paths before relying on them.

Starting host mode must safely flush existing editors and establish exclusive
ownership before admitting clients. Failed startup leaves the existing incident
usable; it must not leave a partially shared session. Stopping must finish or
explicitly fail outstanding operations before releasing storage ownership.
Closing a remote client must not stop the host. Warn before stopping with
connected clients and notify reachable clients that sharing is stopping.
Specify what closing the host window does and the notification/draining
sequence; do not silently terminate other workstations or discard entered work.

Exit evidence: start/stop/restart, port-in-use and storage failures, competing
local processes and client disconnects leave no silent loss or duplicate writer.

### 3. Add encrypted transport and device admission

Persist host identity and present a certificate fingerprint. A connecting
instance pins the host using an invitation delivered by the host operator;
never disable TLS verification or trust a changed certificate silently.

Proposed initial flow: the host operator issues a short-lived, single-use
invitation for the shared incident; the other instance enters connection details
and redeems it for a device-bound credential. Use established cryptographic
libraries/protocols rather than custom cryptography. Choose the concrete
credential mechanism and storage before implementation.

The host UI receives a local credential through trusted local bootstrap, not a
public unauthenticated administrator endpoint. Its incident requests use the same
authorization path as remote requests. Pairing is the narrowly scoped
unauthenticated exception: it permits invitation redemption, never incident
access. Rate-limit redemption and avoid credentials or invitation secrets in logs.

Provide a connected-device list and revocation for the first shared delivery.
Persist revocations across restarts. Proposed remote permission is read/edit of
the invited incident only; host administration stays local. Full operator claims
and the five-role authorization model remain Stage 3 work. This pulls the
minimum invitation expiry/use limit and device revocation forward from Stage 3;
advanced lifecycle management remains there.

Exit evidence: valid pairing succeeds; expired/reused invitations, unadmitted
devices, revoked credentials, wrong incidents and wrong host identity are
rejected. No incident information leaks through failed authorization responses.

### 4. Implement the production remote store and wire both UIs

Implement a production `RemoteIncidentStore`, separate from or clearly
distinguished from the existing test-only class of that name. Map protocol
outcomes into existing store exceptions without collapsing conflicts into
generic failures. Preserve detached snapshots and snapshot-specific tokens.
Keep optional edit locks optional and file-path details out of clients.

Run the shared snapshot/token contract against the real service. Route the
host UI through the loopback remote store; retain the current local store path
for standalone mode. Keep network I/O off Swing's event-dispatch thread.

Exit evidence: host and multiple connected instances can open and save the same
incident, produce local PDFs, and recover from failed saves without losing edits;
standalone workflows remain unchanged.

**Delivered (issue #60).** `persistence/RemoteIncidentStore` (production; the
former test-only class is now `InMemoryServerIncidentStore`) talks to the host
through `RemoteIncidentGateway`; `host/HostGateway` is the HTTPS/mTLS adapter
and `HostSession.loopbackStore()` gives the host window its own loopback store
with the administrator credential, so the host UI has no direct-write bypass
while sharing. Snapshots are rebuilt from records (`RecordDocumentBridge`);
saves diff against the base snapshot with `RecordDiff` and submit records with
base versions and a deterministic operation id, so retrying an identical save
after a failure after commit is idempotent. Tokens are opaque
(`remote/<cursor>/<digest>`). Field conflicts raise `StoreConflictException`
with expected and current tokens and are never retried automatically.
Create, delete and import are host-administrative and throw
`UnsupportedOperationException` remotely; edit locks are absent and `localFile`
is empty.

*Polling and staleness.* `ConnectionMonitor` polls the committed cursor on a
daemon thread (never the EDT). Interval: system property
`icsforms.remote.pollIntervalMillis` (default 5000). The view becomes **stale**
when no poll has succeeded for `icsforms.remote.staleAfterMillis` (default
3 × interval). "Last confirmed" is the local time of the last successful poll;
it is display-only and never compared with host time. A loss of contact is
detected within one interval plus the request timeout (10 s); staleness within
`staleAfter` plus one check period (`maximumStaleDetectionDelay()`). A reply
for a host that announced its stop is shown as **host stopped**; any other
failure is **lost contact**, because a network failure and a host crash cannot
be told apart. A changed cursor calls `AppController.refreshFromRemote()`:
clean views refresh in place; dirty views keep the draft and its *old* token,
so the draft can only conflict, never overwrite newer data. The host dialog's
device table marks active devices that lost contact.

*Connected operation.* *Sharing → Connect to a host…* redeems an invitation,
pins the host, and opens the shared incident in a new window. PDFs and exports
are produced locally. If the host is unreachable, edits stay in the open editors
and unsaved state stays visible (the durable outbox is part 5). Credentials are
session-only; reconnecting after a restart needs a new invitation (persistence
is part 6).

*Known limits.* Root-level document scalars that are not records (IAP phase,
incident mode, time zone) are not synchronised; records cannot be removed
remotely (strike-out is used); the legacy numeric-revision/edit-lock suite
`AbstractIncidentStoreContractTest` does not apply to an opaque, lockless store,
while `AbstractSnapshotIncidentStoreContractTest` runs against the real host in
`RealHostIncidentStoreContractTest`. If the host's file projection fails after
the durable commit the client sees a persistence failure, and retrying the same
save succeeds without double application.

### 5. Add cross-instance refresh and explicit conflict recovery

Start with conditional polling of an opaque committed revision, not device-clock
comparisons. Fetch and apply a matching snapshot only when changed. Define
polling interval, last-confirmed semantics and a maximum stale-detection delay
before accepting this phase. A newer remote revision must not advance the token
associated with an older draft and thereby permit it to overwrite newer data.

Apply remote state through existing draft-preserving refresh mechanisms. Clean
views refresh without flicker; dirty views preserve edits and expose conflicts.
Show authority, connection state and last confirmed time, including on the host
UI if its service fails. The host also shows which clients have lost contact.
Clients distinguish a notified host stop from an unexplained loss of contact.
Offer review/reload and recovery export or save-as-new
where appropriate; never resolve conflict by blind resubmission.

Exit evidence: edits propagate between host and clients; concurrent edits cannot
silently overwrite; disconnection, failed projections and reconnection have
visible states; selections, focus and drafts survive refresh.

### 6. Prove operation and recovery

Document host startup, firewall/network requirements, device admission/revocation,
backup, restore, shutdown and troubleshooting without exposing incident contents
or credentials. Agree backup location/frequency and recovery objectives, then
exercise restore and host restart without internet. A backup is not proven until
restored. No automatic host promotion or multiple writable authorities.

Run store/API integration coverage, headless and displayed Swing regressions,
Spotless and Checkstyle. Exercise at least one host UI and two remote instances,
including invalid credentials, disk-full/write failure, interrupted responses,
concurrent saves, host restart and revoked-client reconnect. Review transport and
authorization before release.

Agree representative data sizes, response/refresh thresholds and a bounded
operational exercise with clear acceptance criteria. These measures remain open,
not silently chosen defaults.

## Disconnected entry and duplicate events

The final workflow must let users add information while disconnected and recover
it after restart. Stage 2 owns the durable outbox and stable operation identity;
Stage 4 owns robust offline replay, ordered synchronization, and conflict
recovery. Each pending operation is visibly pending until accepted by the host;
reconnection must not erase or blindly overwrite it.

Treat three cases separately:

- **Retry of one operation:** the host recognizes its operation identity and
  does not apply it twice.
- **Two records describing one event:** distinct operation/record identities do
  not prove distinct real-world events. Define a review and resolution workflow
  for suspected duplicates in the same log; do not automatically discard one
  because its text or time resembles another.
- **Conflicting edits to existing information:** preserve the draft and accepted
  version for explicit resolution. Later per-record versions reduce unnecessary
  conflicts but do not eliminate this requirement.

Detection rules and how confirmed duplicates are represented remain decisions,
not prescribed algorithms. Preserve provenance and existing strike-out/history
semantics rather than deleting evidence merely to make entries look unique.

## Operations (issue #59)

**Firewall and ports.** Allow inbound TCP on the data port (default 8443) and
the pairing/status port (default 8444) on the incident network only. Set the
"address clients use" to a name or IP clients can reach. Sharing binds a
wildcard or loopback address only.

**Starting.** Use *Sharing → Host this incident…* with the incident saved in the
local file store. Starting saves open editors, takes exclusive ownership of the
incident files, then opens the listeners. Failures (port in use, storage
failure, unsaved edits) leave the incident usable and nothing shared. While
hosting, a standalone editor or second host process cannot write the files:
the exclusive lock is an operating-system file lock, and a test spawns a
separate JVM that is refused while hosting and succeeds after stopping. Locks
are advisory to processes that do not use the application's store, so other
tools can still modify the files directly.

**Period close.** On start, and every two seconds while hosting, the dialog
reads the incident's operational period end and applies it as the credential
cutoff: new credentials last `min(now + 72h, period end)`, existing device
credentials stop working once the period end has passed (and resume if the end
is moved later), and invitations are refused after it. With no period end the
72-hour default applies. *Open item:* the incident model has no demobilised
indicator, so demobilisation does not yet set a cutoff; until it does, set the
operational period end to demobilise credentials.

**Admitting.** Press *Create invitation* (copied to the clipboard) and send the
text to the device operator over a trusted channel. It works once and expires
quickly. Confirm the fingerprint shown in the dialog when the client pins it.

**Revoking.** Select a device in the list and *Revoke*. Revocation persists
across restarts; a revoked device is told so by the pre-auth status check.

**Stopping.** *Stop sharing* warns when devices are connected, reports
`stopping` to clients for a grace period, finishes or explicitly fails in-flight
requests, then closes. Closing the window stops sharing only after confirmation;
closing a client never stops the host.

**Backup and restore.** *Sharing → Host this incident…* offers *Back up host
data…* and *Restore host data…*. Both first show the sensitivity warning. Restore
is refused while sharing and reloads the open incident afterwards; failures are
reported in a dialog. `HostBackup` writes one archive containing the incident
file, record history, device registry (with revocations) and the CA/server keys,
with per-entry checksums, so versions cannot be mixed. The archive is
sensitive (it can mint device credentials): it is created owner-only and must be
stored accordingly and never logged. Restore only while not sharing; it
verifies everything before writing anything.

**Threats considered.** *Invitation interception*: single-use, short-lived and
incident-scoped; an intercepted-and-used invitation is visible as an unexpected
device, which can be revoked. *Host impersonation*: the client pins the CA
fingerprint and refuses a changed certificate. *Credential compromise / lost
device*: revoke the device; credentials expire after 72 hours regardless.
*Evil-twin hotspot*: without the pinned fingerprint an impostor cannot complete
the handshake, but an attacker who also captures the invitation before use could
pair a rogue host, so deliver the fingerprint together with the invitation out of band (for
example read aloud, printed or in person) rather than over the same
network the clients are joining.

**Known limits.** Host identity rotation is deferred. Host-editor whole-document
saves may report a revision conflict after remote writes because refresh is out
of scope here. The window then says remote devices changed the incident, that
nothing was lost and the edit is still in the window, and advises exporting
unsaved changes; the edit is never discarded.
