# Host-mode validation exercise

This is the proposed release exercise for the #62 series. It combines automated
host/API, persistence, outbox and displayed-window tests; a completed CI run and
an operator restore drill are required before declaring Stage 2 complete.

## Automated coverage map

| Exercise | Automated evidence |
| --- | --- |
| One host with two remote clients, independent saves and displayed refresh | `ui/ConnectedWorkflowAcceptanceTest.twoDisplayedClientsMergeClueEditsAndRefreshTheirTables` and `twoClientsMergeActivityClueResourcePlanningAndRootChanges`; run in the displayed lane |
| Invalid credentials, no incident-content leak, expiry and revocation | `host/HostSessionTest.preAuthStatusRevealsNoIncidentContents`, `expiredCredentialIsDistinguishedFromUnreachable`, `revokedDeviceIsRejectedAndDistinguished` |
| Request flood, approval and denial, pending-outbox review, old-key rejection, new/old identity attribution | `host/ReauthorizationTest`; the client flow against a real host and durable outbox is `host/ReauthorizationFlowTest`; the bounded outbox description is `persistence/ReauthorizationOutboxTest` |
| All of the above in one multi-instance run | `host/HostReleaseExerciseTest`: one host, two clients with durable caches, spent invitation, concurrent saves, lost response, host stop/backup/restore/restart, revoked re-pairing with attribution, expired renewal |
| Interrupted response and exact retry across restart | `persistence/DisconnectedRemoteIncidentStoreTest.lostResponseRetainsExactBatchAcrossRestartAndIsIdempotent` and `ambiguousAcceptanceFollowedByAccessFailureCannotBeDiscarded` |
| Local write/storage failure and recovery | `host/HostSessionTest.storageFailureLeavesIncidentUsable`, `persistence/DisconnectedStateFileTest`, and `persistence/RemoteIncidentStoreRejectionTest` |
| Concurrent admission/saves and host restart | `host/HostSessionTest.concurrentRedemptionSucceedsOnlyOnce`, `secondWriterCannotUseSharedIncidentWhileHosting`, `revocationSurvivesRestart`, and `persistence/DisconnectedRemoteIncidentStoreTest.rejectDifferentAuthorityAndConcurrentWritersAndProtectRecovery` |
| Combined backup restore, continued admission and revocation | `host/HostBackupTest.restoreKeepsDevicesAdmittedAndRevocationsInForce` and `host/ReauthorizationTest.revokedRepairNeedsApprovalAndKeepsBothDeviceIdentities` |

`HostReleaseExerciseTest` is one automated multi-instance scenario, but it runs
the host and both clients in one JVM and uses the simulated host clock. Its
limits: a certificate issued after a simulated clock jump is dated in the
future for TLS, so the expiry step verifies the renewal exchange, identity and
retained outbox rather than a replay over the renewed certificate (renewal
attribution is in `ReauthorizationTest`). Displayed workflows still use several
windows in one process. Real deployment networks, real disk exhaustion (only a
simulated write failure is automated) and a new process polling an outstanding
approval still require field-like validation.

Run the headless and displayed CI lanes:

```text
mvn verify -Djava.awt.headless=true
./xvfb_build.sh
```

The displayed lane must produce its baseline report with zero skipped tests.
Run focused regressions during development:

```text
mvn -Dtest=ReauthorizationTest,ReauthorizationFlowTest,ReauthorizationOutboxTest,HostReleaseExerciseTest,HostSessionTest,HostBackupTest test
mvn -Dtest=DisconnectedRemoteIncidentStoreTest,RemoteIncidentStoreRejectionTest,DisconnectedStateFileTest test
mvn -Dtest=ConnectedWorkflowAcceptanceTest test
```

## Operator restore drill

On an isolated test host with no internet, admit two clients, make and back up a
small test incident, revoke one device, restore that exact backup, restart the
host, then verify the admitted credential still works and the revoked
credential is still refused. Make a new edit from the admitted client and
confirm the host continues accepting records. Never use live incident data for
this drill.

## Proposed acceptance measures (not yet agreed)

- Representative pilot fixture: **proposed** 200 records, up to 5 MiB of
  incident JSON, and a pending outbox no larger than the implemented 900,000
  character request limit. Larger data requires a new measured limit.
- On the isolated LAN, **proposed** p95 initial read and a one-record save
  below 2 seconds at that fixture size; visible refresh within 4 seconds with
  the current 2-second poll interval.
- After an administrator decision, **proposed** client approval detection and
  replay start within 4 seconds while the host and client remain available.
  There is intentionally no approval deadline: pending work is retained until
  a human decides.
- **Proposed** backup-and-restore drill completes within 5 minutes for the
  representative fixture, with no admitted-device loss and no re-admission of
  a revoked identity.
- Required safety result: no pending operation is discarded on timeout,
  denial, host restart or interrupted response; replay begins only after an
  approved credential is received.

These values are proposals for the Stage 0 pilot decision, not observed
performance or approved thresholds.
