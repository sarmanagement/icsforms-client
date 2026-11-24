# Host-mode record service contract

Status: **version 1 contract for issue #58**, 2026-11-20. The in-process
`IncidentService` implements record reads, ordered changes, merge, durable
receipts, and file-backed history. This document also defines the transport
envelope for a later issue; issue #58 itself did not deliver a listener,
authentication mechanism, remote store, UI, or outbox. Issues #59–#60 later
added transport and connected operation; the #61 outbox remains WIP.

## Resource and version model

An incident is addressed by its stable incident UUID. Its records are keyed by
the pair `{type, id}`, where `type` is the fully qualified model class name and
`id` is the immutable stable record ID serialized as `id`. Nested rows are
independent records. Singleton forms and context objects are records too.
Record versions start at 1 and increase monotonically for each record. Version
content is retained indefinitely in the managed history file.

The host assigns a single monotonically increasing incident cursor. It advances
once for each record version committed and orders changes within an atomic
batch. Cursors are opaque to clients except for ordering and resume. A changes
response includes all retained changes strictly after the requested cursor;
there is no expiry in this version. Device timestamps are never used for
ordering. An optional client timestamp is audit/display data only; the host
assigns `receivedAt`.

The file backend stores history, changes, and operation receipts together in
`incidents/{incidentId}.records.json`, with a sibling `.bak` recovery file and
atomic replacement via a temporary file. This versioned state file is
authoritative. The ordinary `{incidentId}.json` document remains the
standalone import/export and whole-document store, but is a projection of the
authoritative record state. Reads and receipt replays repair a stale projection
from version history. A successful standalone create or save synchronizes
changed `RecordIndex` snapshots into record history. Advisory file locks are
not a correctness requirement for this service. The service and whole-document
save path share an `IncidentStore.withIncidentLock` scope per incident across
store and service instances in the process; a backend may override it with
stronger coordination.

## Operations and DTOs

The following JSON shapes define the transport-neutral DTOs. The in-process
service uses the same identities, values, and results.

### Read

`GET /v1/incidents/{incidentId}` returns current records and versions.

```json
{
  "incidentId": "4b5a3f16-d723-48cb-b247-81b3595b06fc",
  "cursor": 42,
  "records": [
    {
      "key": {
        "type": "org.sarmanagement.icsforms.model.IncidentContext",
        "id": "c07be7b4-9e3e-4ab2-9cab-1b9b1a0b2a1d"
      },
      "version": 3,
      "cursor": 39,
      "receivedAt": "2026-11-20T15:40:00Z",
      "content": {
        "id": "c07be7b4-9e3e-4ab2-9cab-1b9b1a0b2a1d",
        "incidentName": "North Ridge"
      }
    }
  ]
}
```

### Changes since a cursor

`GET /v1/incidents/{incidentId}/changes?after=39` returns ordered changes
strictly after cursor 39 and the current cursor. A client with the current
cursor receives an empty list.

```json
{
  "cursor": 42,
  "changes": [
    {
      "cursor": 40,
      "key": {
        "type": "org.sarmanagement.icsforms.model.OpenActionItem",
        "id": "62582556-8e53-4bfc-8dc6-419db70309cb"
      },
      "version": 1,
      "receivedAt": "2026-11-20T15:40:01Z",
      "content": {
        "id": "62582556-8e53-4bfc-8dc6-419db70309cb",
        "description": "Check access"
      }
    }
  ]
}
```

### Incident-wide settings record

Incident-wide values that belong to no identified record (IAP phase,
planning stage, incident mode, time-zone settings, the ICS 205/215/233 and
tracker switches, the plan-review assertions, the medical unit leader, and the
canine routing base) travel as one virtual singleton record of type
`org.sarmanagement.icsforms.model.RootSettings` with the fixed id
`incident-root-settings`, no parent and no parent field. It is read, versioned,
three-way merged and conflicted per field like any other record and is
projected back onto the incident file. It is never stored as a field of the
document. The host adds it from the stored document the first time it reads a
state that predates it, so older hosts' data keep working; clients only ever
`UPDATE` it. The change is additive: clients that ignore it simply do not see
those values.

### Create, update, and strike-out

`POST /v1/incidents/{incidentId}/records:submit` accepts an atomic batch.
`CREATE` uses `baseVersion: 0`; `UPDATE` and `STRIKE_OUT` use a version
previously returned by read. `STRIKE_OUT` submits the retained record with its
application strike-out field set, not a physical delete. `parent` is null for
a top-level record and otherwise identifies the containing record.

```json
{
  "operationId": "e27957fc-0b92-4fdc-b076-71590501c4b8",
  "clientTimestamp": "2026-11-20T15:39:58Z",
  "records": [
    {
      "operation": "UPDATE",
      "key": {
        "type": "org.sarmanagement.icsforms.model.IncidentContext",
        "id": "c07be7b4-9e3e-4ab2-9cab-1b9b1a0b2a1d"
      },
      "parent": null,
      "parentField": null,
      "baseVersion": 2,
      "content": {
        "id": "c07be7b4-9e3e-4ab2-9cab-1b9b1a0b2a1d",
        "incidentName": "North Ridge"
      }
    }
  ]
}
```

`parentField` is the containing Java model field name and is required for a
create. Create payloads contain the complete record snapshot, including
default-valued fields. Updates and strike-outs can omit both parent properties.
For example, creating a top-level action item uses
`"operation": "CREATE"`, `"parent": null`, `"parentField": "openActionItems"`,
and `"baseVersion": 0`. A strike-out uses `"operation": "STRIKE_OUT"` and a
full record `content` with `"struckOut": true` (or the model's
`"isStruckOut": true` field).

All records are validated and merged before persistence. The service commits
the resulting versions, ordered changes, and operation receipt by atomically
replacing one incident state file. A rejected batch changes no records. Each
operation ID identifies exactly one attempt; it must not be reused for a
different logical operation.

## Three-way merge

The base is the retained content at the submitted record version; clients do
not send a base copy. For each field, an unchanged client value keeps the host
value; a host value unchanged from base accepts the client value; equal values
are accepted; different changes to the same field conflict. If the base content
equals the current host content, the submitted content is accepted directly.
Comparison is by net JSON value, so a host A-to-B-to-A cycle is unchanged.
Record IDs cannot change. Unknown records, invalid record types, nonexistent
parents, malformed content, and unknown base versions are rejected as invalid
requests and map to `StoreValidationException`. The host checks parent
references for submitted records, without changing application-specific
cross-reference validation.

### Merge success

```json
{
  "operationId": "e27957fc-0b92-4fdc-b076-71590501c4b8",
  "applied": true,
  "cursor": 43,
  "receivedAt": "2026-11-20T15:40:02Z",
  "versions": [
    {
      "key": {
        "type": "org.sarmanagement.icsforms.model.IncidentContext",
        "id": "c07be7b4-9e3e-4ab2-9cab-1b9b1a0b2a1d"
      },
      "version": 4
    }
  ],
  "conflicts": []
}
```

### Field conflict

HTTP status: `409 Conflict`.

```json
{
  "operationId": "e27957fc-0b92-4fdc-b076-71590501c4b8",
  "applied": false,
  "cursor": 42,
  "receivedAt": "2026-11-20T15:40:02Z",
  "versions": [],
  "conflicts": [
    {
      "key": {
        "type": "org.sarmanagement.icsforms.model.IncidentContext",
        "id": "c07be7b4-9e3e-4ab2-9cab-1b9b1a0b2a1d"
      },
      "field": "incidentName",
      "base": "North Ridge",
      "mine": "North Ridge West",
      "theirs": "North Ridge East"
    }
  ]
}
```

## Idempotency and ambiguous outcomes

The host stores receipts indefinitely, including rejected field-conflict
results. A receipt is committed in the same atomic state-file replacement as
the accepted record effect. Retrying an operation ID returns the original
result and does not apply the batch again. Therefore, after a timeout or lost
response the client retries with the same operation ID; it must not infer
failure from a missing response or create a replacement ID. A crash before
replacement leaves neither the proposed effect nor its receipt; a crash after
replacement leaves both. This retry rule does not deduplicate independently
created reports describing the same real-world event.

For the client-side #61 workflow under development, the journal retains pending
work in stable per-incident queue order; device timestamps do not determine
replay order. Once submitted, a batch's operation ID **and exact payload** must
remain immutable, including after an ambiguous result. A new ID is appropriate
only for a new, explicitly resolved operation, not a retry of the old one.
Conflict and duplicate-event review are separate from receipt replay. These are
client requirements, not a claim of overall replay safety: the current code
and targeted regression tests address the named dependent-edit and
ambiguous-authorization defects. The stale-refresh guard and monitor cursor
retry also have targeted regression tests. Pre-submit validation now
retains oversized work as rejected rather than leaving it pending. A rejected
head can be replaced by a new operation built from the corrected local draft
after the rejected operations are archived; oversized edits cannot be
corrected, and end-to-end acceptance remains open. See the
[disconnected workflow status](host-mode-plan.md#issue-61-work-in-progress).

## Version negotiation, limits, and errors

Requests identify `apiVersion: 1` and the client application version in the
transport envelope. A server advertises its supported API range and minimum
client version. It rejects a missing/unsupported API version or client version
outside its declared range rather than silently downgrading. Authentication
and exact header names are transport work for issue #59.

Version 1 limits a request body to 1 MiB, at most 100 records in a batch, and
1 MiB for an individual serialized record. The transport must enforce these
limits before deserializing a request. The in-process API has no network body
to limit.

Errors use this shape:

```json
{
  "error": {
    "code": "invalid_request",
    "message": "A record ID cannot be changed.",
    "retryable": false,
    "details": {}
  }
}
```

| Service outcome | HTTP status | Existing client exception |
| --- | --- | --- |
| Revision or field conflict | `409` | `StoreConflictException` |
| Advisory edit-lock conflict | `423` | `StoreLockConflictException` |
| Invalid arguments, content, cursor, or record identity | `400` | `StoreValidationException` |
| Incident absent or retired | `404` | `IncidentNotFoundException` |
| Host unavailable | `503` | `StoreUnavailableException` |
| Host persistence failure | `500` | `StorePersistenceException` |
| Submission committed, host file projection failed | `500`, code `committed_not_projected` | `StoreCommittedException` (a `StorePersistenceException`); the change is durable and retrying the identical save returns the original receipt |
| Missing/invalid credential | `401` | Transport authorization error; no matching store exception |
| Expired credential | `401` | Transport authorization error; no matching store exception |
| Revoked credential | `403` | Transport authorization error; no matching store exception |
| Incompatible client or API version | `426` | Client compatibility error; no matching store exception |

### Unavailable

```json
{
  "error": {
    "code": "service_unavailable",
    "message": "The incident service is temporarily unavailable.",
    "retryable": true,
    "details": {}
  }
}
```

### Invalid request

```json
{
  "error": {
    "code": "invalid_request",
    "message": "Record content ID does not match its key.",
    "retryable": false,
    "details": {
      "recordId": "c07be7b4-9e3e-4ab2-9cab-1b9b1a0b2a1d"
    }
  }
}
```

### Unauthorized

```json
{
  "error": {
    "code": "unauthorized",
    "message": "A valid device credential is required.",
    "retryable": false,
    "details": {}
  }
}
```

### Expired credential

```json
{
  "error": {
    "code": "credential_expired",
    "message": "The device credential has expired.",
    "retryable": false,
    "details": {}
  }
}
```

### Revoked credential

```json
{
  "error": {
    "code": "credential_revoked",
    "message": "The device credential was revoked.",
    "retryable": false,
    "details": {}
  }
}
```

### Incompatible client

```json
{
  "error": {
    "code": "incompatible_client",
    "message": "Client version 0.9 is not supported.",
    "retryable": false,
    "details": {
      "minimumClientVersion": "1.0",
      "supportedApiVersions": [1]
    }
  }
}
```

## Transport, pairing and pre-auth status (issue #59)

The host runs two HTTPS listeners (JDK `HttpsServer`), both using a server
certificate signed by the host-run certificate authority (CA):

| Listener | Client certificate | Purpose |
| --- | --- | --- |
| Data port (default 8443) | Required (mutual TLS, trust store holds only the host CA) | Incident read, changes, submit and host administration |
| Enrollment port (default 8444) | Not requested | Pre-auth status and pairing only |

Requests carry `X-ICS-Api-Version` and `X-ICS-Client-Version`. A missing or
unsupported API version, or a client older than the host minimum, receives
`426 incompatible_client`. Bodies over 1 MiB (16 KiB for pairing) receive
`413 payload_too_large`; batches over 100 records are also rejected with `413`.

### Data port endpoints

| Method and path | Purpose |
| --- | --- |
| `GET /v1/incidents/{id}` | Read the shared incident |
| `GET /v1/incidents/{id}/changes?after={cursor}` | Changes since a cursor |
| `POST /v1/incidents/{id}/records:submit` | Submit a batch |
| `POST /v1/admin/invitations`, `GET /v1/admin/devices`, `POST /v1/admin/devices/{id}:revoke` | Administration; only the host's own local credential over a loopback connection is accepted, remote devices always receive `403` |

A device credential grants connect, read, write, update and delete (strike-out)
on **the single invited incident**. Any other incident id returns `404`, the
same as an absent incident. The credential state is checked on every request:
expired → `401 credential_expired`, revoked → `403 credential_revoked`.

### Pre-auth status (enrollment port)

Mutual TLS fails at the handshake, so a deliberate pre-auth signal exists:

- `GET /v1/status` returns `{state, apiVersions, minimumClientVersion, hostFingerprint}` and nothing about incidents. `503` is returned unless the host is running; state `stopping` lets clients see a controlled shutdown.
- `POST /v1/status/credential` with `{deviceId}` returns `{state}` (`active`, `expired`, `revoked` or `unknown`). It reveals nothing about incident content and is rate limited.

A client therefore distinguishes *credential expired/revoked* (credential status),
*host unreachable* (no response from either port) and *host identity changed*
(the presented CA/host certificate does not match the pinned fingerprint; the
connection is refused and never silently trusted).

### Pairing

`POST /v1/pairing/redeem` is the only unauthenticated mutating endpoint. The
body is `{invitation, incidentId, deviceName, certificateRequest}` where the
request is a PKCS#10 PEM generated on the device (its private key never leaves
it). The host verifies the proof of possession, atomically consumes the
invitation use, and returns `{deviceId, incidentId, certificate, caCertificate,
expiresAt}`. Expired, reused, unknown and wrong-incident invitations all
produce the same `403 invitation_invalid` so nothing is revealed. Pairing is
rate limited per address and globally (`429 rate_limited`). Pairing never
grants incident access; the issued certificate is needed for the data port.
Invitation secrets and credentials are never logged.

### In-person pairing

A host operator opens one pairing window. A device connects, shows a 6-character
number (4 digits and 2 letters, I and O excluded) and the operator types it on
the host. No secret is exchanged in advance; the QR code carries only the host
address and authority fingerprint. The long invitation remains available.

* `POST /v1/admin/pairing` (administrator, loopback) opens the window and
  returns `{expiresAt, host, port, enrollmentPort, fingerprint}`; it returns
  `409 period_closed` after the operational period closes.
  `GET /v1/admin/pairing` returns `{open, expiresAt, awaitingDevice, busy}`.
  `POST /v1/admin/pairing:enter` takes `{code}` and returns `{result}`
  (`confirmed`, `mismatch`, `burned` or `none`). `:cancel` drops the exchange in
  progress and `:close` closes the window.
* `GET /v1/status` also lists `pairingSuites` (`sas`, `channel` identifiers).
* `POST /v1/pairing/short/begin` takes `{suite, channel, commitment,
  deviceName}` and returns `{sessionId, suite, channel, share}`. Only one
  exchange runs at a time (`409 busy` otherwise).
* `POST /v1/pairing/short/open` takes `{sessionId, opening}`; the host verifies
  the commitment and awaits the operator's entry.
* `POST /v1/pairing/short/status` takes `{sessionId}` and returns `{state}`
  (`awaiting_entry` or `confirmed`); the device polls it while showing the number.
* `POST /v1/pairing/short/finish` takes `{sessionId, confirmation, sealed}`
  once confirmed. `sealed` carries `{certificateRequest, deviceName}` encrypted
  under the agreed key. The response carries the host's key confirmation and a
  sealed result with the credential, authority certificate, mutual-TLS port and
  incident id.

The agreement is ECDH P-256 with a hash commitment (`ecdh-p256-sha256-sas`) and
the keyed channel is HKDF-SHA-256 with AES-256-GCM (`hkdf-sha256-aes256gcm`),
both from the Bouncy Castle FIPS module. They are behind `SasSuite` and
`ChannelSuite` and chosen by identifier, so either can be replaced without
changing the endpoints. The number is derived from the transcript and the host
authority fingerprint the device observed, so a man in the middle presenting a
different authority produces a different number. A device that read the
fingerprint from the QR code additionally pins TLS to it from the first
connection. The QR/clipboard text is
`ICSFORMS-PAIR-3:HOST:PORT:ENROLLPORT:FINGERPRINT`; typed entry is
`ICSFORMS-PAIR-1:host[:enrollmentPort]`.
