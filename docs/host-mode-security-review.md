# Host-mode transport and authorization review

**Status:** implementation review for the #62 slice; not an independent
penetration test or a Stage 2 release approval.

## Controls verified in code

- Data operations use TLS with a pinned host authority and an admitted device
  client certificate. Device and incident scope are checked before record
  access. Administrator endpoints additionally require the host's local
  administrator credential and a loopback connection.
- Enrollment and re-authorization use a separate TLS surface. Renewal validates
  the old certificate fingerprint, its device identity, expiry and a signed
  request made by the same public key. Repair requires a valid invitation,
  identifies a revoked source device and uses a newly generated key. Approval
  is an explicit host-administrator action; pending work is not replayed by
  either endpoint.
- Pending requests and administrator decisions are persisted with the host
  registry and included in the integrity-checked host backup. Renewal retains
  the device ID. Repair creates a different ID and stores the revoked source
  identity; the host writes these verified IDs into record provenance on
  accepted submissions.
- Re-authorization requests are limited to three per source address and 20
  globally per minute. At most 64 may be pending. Request bodies are capped at
  1 MiB; outbox strings at 900,000 characters. Failed authorization responses
  do not include incident data. Request tokens are random, only their hashes
  are persisted, and diagnostic string forms redact tokens, keys and queued
  contents.
- The admin review endpoint exposes pending outbox contents only on the
  administrator-authorized loopback API. The client keeps its durable outbox
  through waiting and denial. Polling returns a certificate only to the
  request's bearer-token holder over the pinned TLS connection.
- Backup restore validates a single archive containing the incident,
  authority keys and registry before replacing state. The backup is owner-only
  and must be treated as a credential capable of issuing device certificates.

## Findings and residual risks

1. **Client credentials and approval tickets are session-memory-only.** The
   existing connection flow does not persist credentials across application
   restarts, and a pending re-authorization ticket holds its status token and
   new private key in memory. Closing the client does not delete its durable
   outbox, but it can lose the ability to poll a pending request or use a
   replacement key issued later. Keep the requesting client open until a
   decision. Durable ticket/key recovery is a release blocker if operators
   require restart-safe approval recovery; OS-protected key storage remains
   out of scope.
2. **Approval queue and audit are bounded.** Pending requests cap at 64; the
   registry retains at most 500 administrator decisions. This bounds abuse and
   local storage but is not a permanent audit archive. Export/retention policy
   for longer incidents remains an operational decision.
3. **Backups carry both authority material and queued incident content.**
   Registry/backup file access is therefore equivalent to host credential
   access. The pilot depends on trusted owner-only storage and controlled
   physical access; encryption at rest and key rotation are not provided.
4. **Host identity rotation remains unresolved.** A changed pin is rejected,
   not silently accepted. Recovery needs a field-tested authority-transfer
   procedure.
5. **No independent network penetration test has been performed.** Rate limits
   are in-memory and source-address based; a distributed attacker can still
   consume the global request budget or occupy the 64 pending slots using
   otherwise valid requests. Operators must review and decide requests
   promptly. The host must not be exposed to untrusted networks.

No credentials, outbox contents or backup data should be included in logs,
issue reports or support bundles. This review records implementation checks
and open risks; it does not claim that the system is ready for production.
