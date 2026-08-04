# icsforms-client

Java Swing desktop application for first-cut ICS incident form authoring with local JSON persistence and PDF export.

## Build

```bash
mvn package
```

## Run

```bash
java -jar target/icsforms-client-0.1.0-SNAPSHOT.jar
```

## Features in this first cut

- Shared incident context for incident name, operational period, and preparer identity
- Tabbed editors for ICS 202, ICS 204, and linked SAR task scaffolding
- Local JSON workspace persistence at `~/.icsforms/incident.json`
- Backup-assisted recovery via `~/.icsforms/incident.json.bak`
- Menu actions for new/open/save/save-as/export
- PDF export for ICS 202 and ICS 204 to a chosen directory
- Validation that blocks export when required fields are missing

## Manual export flow

1. Launch the shaded jar.
2. Enter shared incident context values.
3. Fill in ICS 202 and ICS 204 required fields.
4. Add at least one ICS 204 resource row and, if branch/division/group is set, provide supervisor details.
5. Use **File → Save** or wait for autosave.
6. Use **Export → Export ICS 202 PDF…**, **Export ICS 204 PDF…**, or **Export All PDFs…**.

## Current limitations

- PDF output is structured and printable, but not a pixel-perfect government facsimile.
- SAR scaffolding is derived from ICS 204 assignments and displayed read-only in this first cut.
- Date/time entry currently uses validated text fields in `yyyy-MM-dd HH:mm` format.
- Validation feedback is summarized in the status bar and blocking dialogs rather than rich per-field decorations.
