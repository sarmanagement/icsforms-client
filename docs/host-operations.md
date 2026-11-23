# Host-mode operations runbook

This pilot runbook assumes one trusted operator controls the host application
and its files. Host mode is intended for an isolated incident network and does
not require internet access.

## Start and network

1. Save and close any other editor using the incident. Open the incident in the
   host application and use **Sharing → Host this incident…**.
2. Set an address that remote workstations can reach. Allow inbound TCP only
   from the incident network to the data port (default `8443`) and enrollment
   and status port (default `8444`). Do not port-forward either listener to the
   public internet. The host binds a wildcard or loopback address; the firewall
   is the network boundary.
3. Use **Start sharing**. A failed editor flush, file lock, port bind or storage
   check means the incident was not shared. Keep the host awake and connected
   to reliable power. Remote workstations connect to this host, not to the
   incident files.

## Admission and revocation

1. From the host's **Sharing** dialog, create an invitation. It is single-use,
   short-lived and scoped to the current incident. Send the invitation and its
   displayed host fingerprint through a trusted channel; verify the fingerprint
   out of band.
2. The device operator pairs the workstation. Confirm the new device appears
   in the host's paired-device list before treating it as admitted.
3. If a device is lost or compromised, select it and choose **Revoke selected
   device**. The host rejects that identity immediately and after restart.
   Never attempt to make a revoked key valid again.

## Re-authorization

Re-authorization is not automatic: the host administrator reviews and decides
each request. While waiting, the client's status says **Waiting for
administrator approval**; the durable local outbox remains unchanged and is
not replayed. Keep the client open until it reports the decision.

- **Expired credential:** the client requests renewal with the same device ID
  and key pair, proving key possession with a signed certificate request. The
  host checks that the identity is not revoked. In **Review
  re-authorization requests**, inspect the complete pending outbox before
  approving or denying.
- **Revoked credential:** do not reuse its key. Issue a new invitation. The
  client creates a new key and queues a repair request that identifies the
  revoked device and includes its pending outbox for review. Approval admits a
  new identity; replay occurs only after approval. Accepted record provenance
  identifies both the submitting device and the revoked device on which the
  work was queued.
- A denial does not delete the client's outbox. The operator can export it from
  **Review outbox…** for recovery. Resolve the cause and submit a new request
  only after the pending request has been decided.

Accepted work from a repaired device carries both the replacement and revoked
device IDs only for operations the administrator saw queued in the approved
request; later work names only the new device. Operation IDs are asserted by the
client, so the administrator's review of the queued operations bounds this
attribution, and a rejected edit the operator replaces after the request is a
new operation that is not attributed to the old device.

Enrollment and re-authorization do not require a device certificate. They are
rate-limited to three submissions per source address and 20 total per minute;
the durable approval queue holds at most 64 pending requests. Re-authorization
bodies are limited to 1 MiB and pending outboxes to 900,000 characters. These
The client sends a bounded description of its outbox (record content, then record
identities, then only operation IDs and counts as the size grows), so a large
outbox still produces a request the host accepts. These are abuse controls, not
a way to increase incident capacity. A rejected or
oversized request leaves the client's local outbox in place.

## Backup, restore and shutdown

Use **Back up host data…** regularly and after device changes. A host backup is
one integrity-checked archive containing the incident, record history, CA
private key, server key and device registry, including revocations and
re-authorization decisions. Approval requests can also contain pending
incident contents. Treat the archive as a high-sensitivity credential: keep it
owner-only on trusted storage, limit copies, never attach it to a support
ticket, and never log or email it.

To restore, stop sharing and close other editors first. Select
**Restore host data…** and verify the host reports success before admitting
devices. Restore the complete archive; never restore a CA key, registry and
incident from different backup generations. A restore must retain device
admissions and revocations from that archive. Perform a restore drill on an
isolated test copy before field use.

Use **Stop sharing** before shutting down or moving the incident. The host warns
about connected devices, reports the stopping state, drains in-flight
operations and then closes both listeners. Do not terminate the process or
remove host files during this drain.

## Troubleshooting without exposing incident contents

- **Host unreachable:** check host power, address, incident Wi-Fi and both
  firewall rules. A host stop or network interruption does not imply a save
  succeeded; the client outbox and last-confirmed status are the recovery
  source.
- **Host identity changed:** stop and verify the host and backup generation
  with the operator. Do not bypass the fingerprint warning.
- **Expired credential:** submit renewal and wait for an administrator
  decision; do not delete the outbox.
- **Revoked credential:** ask the administrator for a new invitation and submit
  a repair request; the old identity remains revoked.
- **Waiting or denied:** ask an available administrator to inspect the queue.
  A denial leaves the local outbox available for export; do not send its
  contents through an untrusted channel.
- **Host storage or disk failure:** stop sharing if safe, preserve the complete
  backup and local client outboxes, and restore only to a verified writable
  location. Do not reset the registry or delete files to make the host start.

Share only the displayed error category, host version, time and non-sensitive
network facts when seeking help. Never send invitation text, status tokens,
private keys, backup archives, incident records, or raw request/response
bodies. Host errors intentionally avoid returning incident contents.

Host identity rotation remains out of scope and requires field experience
before a safe procedure can be specified.
