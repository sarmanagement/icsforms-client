# icsforms-client

`icsforms-client` is a Java Swing desktop application for authoring Incident Command System (ICS) incident forms with local JSON persistence and PDF export. Development is ongoing and the application is still evolving as additional forms and workflows are added.

## Current UI workflow

1. Launch the desktop application.
2. Enter shared incident context values on the **Shared** tab:
   - incident name
   - operational period
   - preparer identity
   - incident mode (**SAR** or **Generic**)
   - IAP phase (**PRE_OP** or **DURING_OP**)
3. Maintain the current organization on **Org Chart**.
4. Use **ICS 201** during the initial operational period to capture the initial briefing, objectives, current organization, and resource summary.
5. Complete the operational-period forms on the remaining tabs:
   - **ICS 202**
   - **ICS 204**
   - **SAR Tasks** (SAR mode only)
   - **Clue Log** (SAR mode only)
   - **T-Cards**
   - **Activity Logs (ICS 214)**
6. Save locally or allow autosave to persist the incident workspace.
7. Export individual PDFs, all PDFs as separate files, or the merged **Export IAP Bundle** PDF.

## Current features

- Shared incident context for incident name, operational period, and preparer identity.
- Incident mode toggle for **SAR** and **Generic** workflows.
- IAP phase toggle for **PRE_OP** and **DURING_OP** workflows.
- Tabbed Swing UI for:
  - Shared context
  - Org Chart
  - ICS 201
  - ICS 202
  - ICS 204
  - SAR Tasks (SAR mode only)
  - Clue Log (SAR mode only)
  - T-Cards
  - Activity Logs (ICS 214)
- SAR Task Assignment forms with debriefing and POD factoring support.
- Local JSON persistence at `~/.icsforms/incident.json`.
- Backup-assisted recovery from `~/.icsforms/incident.json.bak`.
- PDF export for each supported form.
- **Export All PDFs** for individual per-form PDF files.
- **Export IAP Bundle** for a single merged PDF bundle with a cover page.
- Validation that blocks export when required fields are missing.

## ICS 201 operational behavior

ICS 201 is available in both IAP phases:

- In **PRE_OP**, ICS 201 is the primary place to capture the initial response picture, including objectives, organization, and resources.
- In **DURING_OP**, that operational data flows into the current-period forms such as ICS 202 and ICS 204, while ICS 201 remains available as the historical initial briefing record attached to the IAP.

## Repository structure

```text
src/main/java/org/sarmanagement/icsforms/
├── App.java                       # application entry point
├── model/
│   ├── AppData.java
│   ├── IncidentContext.java
│   ├── Ics201Form.java
│   ├── Ics202Form.java
│   ├── Ics204Form.java
│   ├── Ics214Form.java
│   ├── OrganizationalChart.java
│   ├── SarTaskAssignment.java
│   ├── TCard.java
│   ├── ClueLogEntry.java
│   ├── ActivityEventType.java
│   ├── ActivityLogEntry.java
│   ├── ActivityLogScope.java
│   ├── CommunicationEntry.java
│   ├── IncidentMode.java
│   ├── IapPhase.java
│   ├── PodFactorRating.java
│   ├── ResourceAssignment.java
│   ├── SarTaskResource.java
│   ├── SarTaskSupport.java
│   ├── TCardType.java
│   └── package-info.java
├── ui/
│   ├── MainFrame.java
│   ├── AppController.java
│   ├── IncidentContextPanel.java
│   ├── OrganizationalChartPanel.java
│   ├── Ics201Panel.java
│   ├── Ics202Panel.java
│   ├── Ics204Panel.java
│   ├── Ics214Panel.java
│   ├── SarTaskPanel.java
│   ├── ClueLogPanel.java
│   ├── TCardPanel.java
│   ├── UiSupport.java
│   └── package-info.java
├── pdf/
│   ├── AbstractPdfRenderer.java
│   ├── PdfFormRenderer.java
│   ├── PdfExportService.java
│   ├── CoverPageRenderer.java
│   ├── Ics201PdfRenderer.java
│   ├── Ics202PdfRenderer.java
│   ├── Ics204PdfRenderer.java
│   ├── Ics214PdfRenderer.java
│   ├── SarTaskAssignmentPdfRenderer.java
│   ├── ClueLogPdfRenderer.java
│   └── package-info.java
├── persistence/
│   ├── LocalRepository.java
│   └── package-info.java
└── validation/
    ├── IncidentValidator.java
    ├── ValidationMessage.java
    └── package-info.java

src/test/java/                    # unit tests mirroring the main source structure
pom.xml                           # Maven build definition
```

## Build

```bash
mvn package
```

## Run

```bash
java -jar target/icsforms-client-0.1.0-SNAPSHOT.jar
```

## Notes

- PDF output is structured for field use and printing, not a pixel-perfect facsimile of government forms.
- Date/time entry currently uses validated text or spinner inputs in `yyyy-MM-dd`, `HH:mm`, or `yyyy-MM-dd HH:mm` formats depending on the form.
- Development is ongoing; workflows, form coverage, and export behavior will continue to expand.
