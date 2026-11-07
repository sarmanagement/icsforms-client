# Historical incident fixtures

These sanitized fixtures contain synthetic data only. Keep one fixture per
representative legacy shape rather than exhaustively recording every released
schema version. Names use `schema-<version>-<purpose>.json`; `schema-unknown`
is reserved for documents that omit `schemaVersion`.

| Fixture | Schema | Purpose | Expected stored-load migration |
| --- | --- | --- | --- |
| `schema-5-legacy-aliases.json` | 5 | Historical field aliases and legacy T-card labels | Aliases bind to current fields; missing record IDs are assigned once and persisted. |
| `schema-5-missing-identities.json` | 5 | Missing child IDs, nested unknown properties, and a linked activity record | IDs are stable across reload/restart; unknown root and record properties remain; legacy text stays available. |

When a supported schema or migration changes, add or update a representative
fixture and its assertions in `FileIncidentStoreTest`. Keep narrow inline
documents for edge cases that are clearer when shown next to the assertion.
