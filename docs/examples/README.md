# Fictional incident examples

## Maple Ridge: missing adult with dementia, operational period 1

[`maple-ridge-dementia-op1.json`](maple-ridge-dementia-op1.json) is a portable
**fictional, illustrative exercise**, not an operational plan. Every person,
agency, place, contact, medical arrangement and approval is invented. Do not
call its example numbers or use its radio settings as authorization to transmit.
Actual searches require qualified local command, current intelligence, verified
resources, permissions and a locally approved safety/medical/communications plan.

### Open and explore

1. Use **File → Import → Import Incident JSON…** and select the example. Import creates a separate
   incident; it does not replace the current incident.
   External imports are checked strictly. A rejected file opens an errors/warnings
   report with JSON property paths and a **Copy Report** action; the active incident
   remains unchanged. This example is tested to import without errors or warnings.
2. The incident opens in **SAR**, **DURING_OP**, with
   **Operational period active** recorded. The summer operational period is
   July 18, 2026, **0700–1900 America/New_York**. Entries represent an **0800
   exercise snapshot**: later work and reminders are planned, not accomplished.
   JSON timestamps follow the application's UTC storage convention (the
   operational period is stored as 1100–2300 UTC); configured display-timezone
   conversion keeps the forms aligned with the local-time objectives/reminders.
3. Review ICS 202 objectives, the flat organization, primary ICS 204, linked SAR
   tasks, T-cards, ICS 205/205A, ICS 206, ICS 215A/208, ICS 214 and ICS 233-CG.
   Export individual PDFs or the IAP bundle to inspect their relationships.

### Scenario and deliberate limits

The fictional missing adult has dementia and wandered from a residence beside
the community pavilion and trail junction. That residence is the point last
seen; the adjacent pavilion is staging. A Koster **hub/spokes/rim** approach
protects that residence/last-known-point hub, rapidly checks three travel
spokes, observes two likely rim exits, and
follows with bounded canine areas and familiar-attractor checks. It does not
claim an impermeable perimeter, a guaranteed detection probability or a real map.

There are **25 human field searchers, including four canine handlers**, plus
**seven IMT overhead** (IC, Ops, Planning, Logistics, Safety, COML and MEDL).
Exactly **four canine equipment T-cards** link to named handlers; dogs are not
counted as people. Three four-person spoke teams, a five-person rim team and
four two-person handler/flanker teams account for all 25 field people. Teams
report directly to Ops; there are no branches, divisions or groups and no
additional ICS 204s. Two canine launches are deferred to 1030; Ops must reassess
active span, conditions and relief before launch. The point assignment reuses
Spoke 1 only after its route task ends.

Measurable objectives address the required responder/public safety wording,
spoke completion by 1000, paired rim observation **through 1900** with relief
from returning spoke teams, four 15-hectare canine zones in the high-probability
inner-ring **25% zone**, prioritizing assessed downwind segments (a 60-hectare
target, not a coverage claim), and three attractor/hazard/water-point checks
within **1 km of the residence/IPP** by 1200. Task timing, actual coverage and
canine welfare require reassessment. The single illustrative **155.160 MHz VHF
simplex analog FM** channel is linked by identity to T-cards, ICS 204/SAR contacts
and the generated ICS 205A directory. No aircraft are assigned.

ICS 215A and ICS 208 carry linked tick, fall and heat mitigations. The fictional
ground-only medical plan includes an aid post, ambulance service and hospital.
The enabled ICS 233-CG action tracker uses actual item/POC/briefing/start/target/
status fields for **first tick checks for ALL teams by 1000 (Safety), hourly
hydration/electrolyte checks and ICP water resupply by 0900 (Logistics/Safety),
mandatory shaded rest and heat-illness checks at 1300 (Safety/MEDL), and
end-shift tick/hydration demobilization preparations from 1830 (Safety)**;
rim posts remain staffed through 1900. Blank completion dates intentionally
mean these future reminders are still open.

### Rebuild and verify

The reproducible builder is retained in
`src/test/java/org/sarmanagement/icsforms/examples/MapleRidgeExampleTest.java`.
It constructs model objects and exports through `FileIncidentStore.exportFile`;
the JSON was not hand-authored.

```sh
# Regenerate deliberately after changing the builder:
mvn -Dtest=MapleRidgeExampleTest#fixtureIsTheReproducibleStoreExport \
    -DmapleRidge.regenerate=true test

# Strict store import, content/link/count assertions, reproducibility, individual
# included-form PDFs and merged IAP rendering:
mvn -Dtest=MapleRidgeExampleTest test
```

Tests create and clean their work directories under `target/`, not the user's
incident store. The checked-in example retains deterministic incident, channel,
assignment, objective and resource identities; import gives it a new local
incident identity.
