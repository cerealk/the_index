# Calibre Import

Type: implementation
Status: ready-for-agent
Blocked by: 26, 27, 28, 25

## What to build

Spec: [§6](../spec.md#6-calibre-import).

- Detect a `metadata.db` folder in the inbox and exclude it from the loose-file scan.
- Copy `metadata.db` to temp and open it read-only (SQLite JDBC). Read base tables only, and resolve paths from the database.
- Calibre Import record (keyed on `library_id`). One Import job per supported file, carrying the Calibre record. Calibre metadata wins, extraction fills gaps. Kind per book. Unsupported Formats → failed jobs with no copy.
- Later scans skip a recorded library. `GET /calibre-imports`, `GET …/{id}`, `POST …/{id}/retry` (no metadata overwrite).

## Acceptance criteria

- [ ] A fixture Calibre library (epub+pdf book, cbz comic, MOBI-only book, custom column) imports as expected: siblings share one Publication, the MOBI becomes a failed job, and the custom column is ignored.
- [ ] The library folder is byte-for-byte unchanged afterwards.
- [ ] A second scan creates nothing. Retry after adding a format in Calibre attaches the new File without touching edited metadata.
