# Host mode implementation plan

Status: **proposed implementation plan**, 2026-11-17. No host or network
implementation is delivered by this document. This expands
[Stage 2](../DraftWorkPlan.md#stage-2--incident-api-and-single-local-host-delivers-t1);
the remaining [Stage 0 decisions](stage-0-decisions.md) still apply.

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
version they read, and check for updates. The host validates every submitted
change and either accepts it or reports a clear conflict or failure; newer work
must never be silently overwritten. This agrees the behavior, not a particular
whole-document or per-record protocol.

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
- Begin with whole-document conditional saves through the existing
  `IncidentStore` seam. Detect stale writes atomically with opaque tokens,
  preserve both versions and require explicit recovery; never silently use
  last-write-wins. Per-record concurrency follows in Stage 3.
- Deliver connected sharing first without claiming full offline synchronization.
  Disconnected information entry is required by the agreed direction, but its
  delivery stage must be settled before implementation scope is finalized.
  Stage 4 currently owns durable outbox and synchronization work.

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

Specify successful and ambiguous save outcomes. A save request needs an
operation identifier and a host-side durable receipt or equivalent recovery
mechanism, committed atomically with its effect, so retry after a lost response
cannot duplicate a successful operation. This is distinct from identifying two
independently entered reports of the same event.

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
it after restart. Each pending operation must have stable identity and be visibly
pending until accepted by the host; reconnection must not erase or blindly
overwrite it. Finalize this either as an explicit addition to Stage 2 scope or as
the later Stage 4 delivery; until then Stage 2 alone must not be described as the
complete disconnected workflow.

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
