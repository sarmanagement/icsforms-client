# Persisted record identity

Schema 18 establishes a uniform identity contract for every persisted record,
nested record, and singleton form. The canonical JSON property is `id`.
Existing Java getters and setters remain available; old JSON identity names are
accepted as aliases and are written back as `id`.

`Identified.setId` is reserved for Jackson deserialization and persistence
migration. Application code treats an assigned ID as immutable. Constructors
and creation actions assign IDs to new records; `IdentityMigration` assigns
missing IDs and repairs duplicate stored IDs during load and before local create
or save commits. A stored duplicate is replaced deterministically after keeping
its first occurrence, with a warning. External imports reject blank or duplicate
identities. Legacy IDs are persisted before the migrated document is returned.

Copies that become new records receive new IDs. References within a copied graph
are remapped when the reference target is also copied. Archival snapshots keep
the identity of the completed record while the continuing/new-period copy gets
fresh identities. ICS 205 channel identities are retained across operational
periods so existing external channel references continue to resolve.

## Persisted identity inventory

“Legacy JSON name” is the old identity property accepted on read; a dash means
the type already used canonical `id`. Parent names identify the owning form or
collection. Singleton IDs are persisted independently in the current document
and in each operational-period snapshot.

| Persisted type | Identity property | Kind | Parent |
| --- | --- | --- | --- |
| `AppData` | `incidentId` (document key, not `Identified`) | document root | none |
| `ActivityEventType` | `id` | record | `AppData.activityEventTypes` |
| `ActivityLogEntry` | `id` (legacy `entryId`) | nested record | `Ics214Form.activityLog` |
| `ActivityLogEntry.EventTypeChange` | `id` | history record | `ActivityLogEntry.eventTypeChangeHistory` |
| `AppData.OperationalPeriodRecord` | `id` (legacy `recordId`) | nested record | `AppData.operationalPeriodHistory` |
| `ClueLogEntry` | `id` (legacy `entryId`) | record | `AppData.clueLogEntries` |
| `CommunicationEntry` | `id` | nested record | `Ics204Form.communications` or `SarTaskAssignment.communications` |
| `Ics201Form` | `id` | singleton form | `AppData.form201` |
| `Ics201Form.ActionEntry` | `id` | nested record | `Ics201Form.currentActions` |
| `Ics201Form.ResourceSummaryEntry` | `id` | nested record | `Ics201Form.resourceSummary` |
| `Ics202Form` | `id` | singleton form | `AppData.form202` |
| `Ics202ReportedResult` | `id` (legacy `resultId`) | nested record | `Ics202Form.reportedResults` |
| `IncidentObjective` | `id` (legacy `objectiveId`) | nested record | `Ics202Form.structuredObjectives` |
| `Ics203Form.Branch` | `id` | nested record | `Ics203Form.branches` |
| `OrgChartEntry` | `id` | nested record | `OrganizationalChart` positions and commander entries |
| `OrganizationalChart` | `id` | singleton form | `AppData.organizationalChart` |
| `Ics204Form` | `id` (legacy `formId`) | record | `AppData.form204`, `additionalForms204`, or period `forms204` |
| `ResourceAssignment` | `id` (legacy `assignmentId`) | nested record | `Ics204Form.resourcesAssigned` |
| `Ics205Form` | `id` (legacy `formId`) | record | `AppData.form205` or period `form205` |
| `RadioChannel` | `id` (legacy `channelId`) | nested record | `Ics205Form.channels` |
| `Ics207Form` | `id` | singleton form | `AppData.form207` |
| `Ics208Form` | `id` | singleton form | `AppData.form208` and period `form208` |
| `Ics214Form` | `id` (legacy `logId`) | record | `AppData.activityLogs` |
| `Ics215Form` | `id` (legacy `worksheetId`) | form record | `AppData.form215`, `nextPeriodForm215`, or period worksheets |
| `Ics215Form.ResourceHeading` | `id` | nested record | `Ics215Form.resourceHeadings` |
| `Ics215Form.WorkRow` | `id` | nested record | `Ics215Form.workRows` |
| `Ics215aForm` | `id` | singleton form | `AppData.form215a` and period `form215a` |
| `IncidentContext` | `id` | singleton form | `AppData.incidentContext` |
| `IncidentLocation` | `id` | record | `AppData.incidentLocations` |
| `IncidentResource` and its resource subclasses | `id` (legacy `resourceId`) | record | resource-bearing records, including T-cards |
| `MedicalPlan` | `id` | singleton form | `AppData.medicalPlan` and period `medicalPlan` |
| `MedicalPlan.AidStation` | `id` | nested record | `MedicalPlan.aidStations` |
| `MedicalPlan.TransportService` | `id` | nested record | `MedicalPlan.transportServices` |
| `MedicalPlan.Hospital` | `id` | nested record | `MedicalPlan.hospitals` |
| `CanineMedicalPlan` | `id` | singleton form | `AppData.canineMedicalPlan` and period `canineMedicalPlan` |
| `OpenActionItem` | `id` | record | `AppData.openActionItems` |
| `PocAssignment` | `id` | nested record | `ClueLogEntry.followUpAssignments` |
| `PodFactorRating` | `id` | nested record | `SarTaskAssignment.qualitativePodFactors` |
| `SafetyMessageBlock` | `id` | nested record | `Ics208Form.hazardBlocks` |
| `SafetyAnalysisEntry` | `id` (legacy `entryId`) | nested record | `Ics215aForm.entries` |
| `SafetyHazardReport` | `id` | record | `AppData.archivedSafetyHazardReports` |
| `SarTaskAssignment` | `id` (legacy `taskId`) | record | `AppData.sarTaskAssignments` or period `sarTaskAssignments` |
| `SarTaskAssignment.LifecycleStatusChange` | `id` | history record | `SarTaskAssignment.lifecycleStatusChanges` |
| `SarTaskResource` | `id` | nested record | `SarTaskAssignment.resourcesAssigned` |
| `TCard` | `id` (legacy `resourceId`) | record | `AppData.tCards` |
| `TCard.CheckInOutEvent` | `id` (legacy `eventId`) | history record | `TCard.checkInOutHistory` |
| `VetDirectory` | `id` | singleton form | `AppData.vetDirectory` |
| `VetDirectory.Source` | `id` | nested record | `VetDirectory.sources` |
| `VetFacility` | `id` | record or plan snapshot | `VetDirectory.facilities` or `CanineMedicalPlan.selectedFacilities` |
| `StagingLocation` | `id` | singleton form | `AppData.stagingLocation` |
| `PdfLayoutSettings` | `id` | singleton form | `AppData.pdfLayoutSettings` |

`IncidentResource` subclasses (`AircraftResource`, `CanineResource`,
`EquipmentResource`, `CrewResource`, `GenericResource`, and
`PersonnelResource`) inherit the `resourceId` identity exposed canonically as
`id`. The `id` on each `VetFacility` plan snapshot is distinct from the source
directory facility; `sourceFacilityId` is a reference used to recognize the
source, not the snapshot identity.

The following persisted values intentionally do not have identities:

| Persisted type | Kind | Parent | Reason no ID |
| --- | --- | --- | --- |
| `PlanReviewAssertion` | value object | AppData and operational-period review fields | One atomic reviewer/time/content-digest assertion owned by its field |
| `Ics215Form.CountPair` | value object | worksheet heading or allocation | Numeric pair has no independent lifecycle |
| `Ics215Form.ResourceAllocation` | value object | `Ics215Form.WorkRow.resourceAllocations` | Map value is addressed by its resource-heading ID key |
| `Ics215Form.ResourceLink` | value object | worksheet resource links | Link metadata belongs to its parent heading |
| `Provenance` | value object | record or history event | Attribution is part of the owning record/event |
| `MedicalUnitLeaderAssignment` | value object | `AppData.medicalUnitLeaderAssignment` | Singleton assignment fields are one owned value, not separately addressable records |
| `Ics203Form` | form projection | rendered ICS 203 output | The persisted organizational chart and its branch/position records carry identity; the rendered projection is not a second persisted record |

Enums, strings, timestamps, preference values, and other scalar fields are
likewise not records. `SarTaskAssignment.assignmentId`,
`Ics202ReportedResult.objectiveId`, `SafetyHazardReport.taskId` and
`analysisEntryId`, `SarTaskResource.resourceId`, and the `linked*Id` fields on
`Ics214Form` are references, not identities; they remain references and are not
renamed to `id`.

## Record indexing and diffs

`RecordIndex.of(AppData)` orders records by concrete model type and ID, stores
each record's own serialized values, replaces child-record subtrees in parent
snapshots with child IDs, and exposes each record's immediate parent. Child
collection order does not affect a parent's snapshot unless the collection is
part of the record's own value semantics.

`RecordDiff.between(base, edited)` compares those snapshots by net field value.
It reports created, changed (field names), removed, and newly struck-out
records, with a default single atomic batch and an overload for caller-defined
batch groups. It does not apply changes or define service/version semantics.
