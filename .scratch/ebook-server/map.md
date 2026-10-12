# Map: Ebook library server

Labels: wayfinder:map

## Destination

An implementation-ready spec for **iteration 1 of the library server**: scope, domain model, API shape and stack all decided, with nothing left to decide before building starts.

Reached: [spec.md](spec.md) (accepted, 2026-10-12).

## Notes

- Kotlin/Gradle **learning project**: building it is the point. Prefer choices that teach, but borrow proven protocols (OPDS).
- **Single user**, **LAN-only** deployment.
- Server **owns storage**: files enter via an inbox folder, API upload, and a one-time Calibre import. Library size: thousands of files.
- Reading happens in existing apps (**Readest**, **Panels**) via OPDS. The server stores Reading Progress.
- **Originals are never lost**: the server never modifies or deletes an imported original File. Derived Files (e.g. cbr → cbz) are added alongside it.
- Organize scope: metadata editing, automatic metadata from files, tags and Reading Lists, search, duplicate detection.
- Use the vocabulary in `/CONTEXT.md` (Publication, Kind, Format, File, Series, Reading Progress).
- Ask the user **one question at a time**. Grilling tickets: call the `grilling` and `domain-modeling` skills.

## Decisions so far

<!-- one line per resolved ticket: [title](issues/NN-slug.md): gist -->

- [How do Readest and Panels consume OPDS and sync reading progress?](issues/01-reader-apps-opds-and-sync.md): OPDS 1.2 is the version both apps share, and both use OPDS-PSE page streaming. Readest syncs progress over kosync, keyed by a fingerprint of the file's bytes, so files must be served unchanged. Panels only writes progress back to Komga or Kavita. Readest can't open cbr.
- [What metadata can be extracted from each Format, and with which JVM libraries?](issues/03-file-metadata-extraction.md): Pure-JVM stack: junrar 8.x (RAR5 support is new, so keep a 7z/bsdtar fallback), PDFBox, zip plus XML. Metadata quality from best to worst: epub OPF, ComicInfo.xml (often missing, so a filename parser is needed), pdf. Kind is inferred from Format plus signals and stays editable. Detect Format by magic bytes.
- [Can Panels sync progress to our server by emulating part of Komga's API?](issues/13-panels-progress-sync.md): Partly. `pse:lastRead` on page-streaming links gives server → Panels resume cheaply. For Panels → server, the options are Komga's `PATCH read-progress` or Kavita's 2-endpoint Panels API. Panels' detection rule isn't public and would need a half-day traffic-capture spike. Inferring progress from streamed pages is unreliable.
- [Which server stack: framework and database?](issues/04-stack-choice.md): Ktor, PostgreSQL, Exposed DSL + Flyway, Koin, kotlinx.serialization. Tests use JUnit 5 + Kotest assertions, with a shared, reused Testcontainers Postgres.
- [Is OPDS-PSE page streaming in iteration 1 after all?](issues/12-page-streaming-scope.md): No, comics are download-only. cbr is converted to cbz on import (for Readest), and the original cbr is kept as a second File.
- [How are Files stored and identified, and how are duplicates detected?](issues/05-storage-layout-and-file-identity.md): Human-readable layout per Kind (prose by author, comics by series), with the Publication id in the folder name. Files move immediately and crash-safely on metadata edits. Identity is SHA-256 plus the kosync partial MD5. Exact duplicates are skipped, strong matches are attached, weak matches are flagged. Deletes go to a Trash.
- [Which OPDS version, and how is the catalog structured?](issues/07-opds-catalog-design.md): OPDS 1.2 only, with a root of Continue reading, Recently added, Prose (author/series/all), Comics (series/all), Tags, Collections (now Reading Lists, see [What metadata does a Publication carry?](issues/11-publication-metadata-fields.md)) and OpenSearch. All Files are offered, preferred first (cbz > cbr, epub > pdf). No auth.
- [What does a Calibre library look like, and what maps onto our model?](issues/02-calibre-library-structure.md): Most of it maps cleanly (books → Publication via uuid, data → File, series, tags, per-format read positions). Calibre has no Kind, so it must be inferred. Read a copy of metadata.db read-only, resolve paths from the db, and copy the files into our storage.
- [How does the import pipeline work?](issues/08-import-pipeline.md): Every inbox file or upload becomes a persisted Import job, processed by a single worker. The file is staged first, then goes through Format detection, hashing (exact duplicates skipped), extraction, Kind (inferred or forced by an inbox-folder or upload hint), matching, storing and cbr→cbz conversion. The inbox is polled every 10s. Bad files go to `failed/`, metadata gaps import with warnings, and job history is kept forever.
- [Docker container or plain JVM process?](issues/09-runtime-packaging.md): Docker Compose runs both the app and PostgreSQL, with storage, the inbox and the database on mounted volumes. Any Docker host will do.
- [How does the one-time Calibre import work?](issues/10-calibre-import.md): A Calibre library dropped in the inbox is detected by its `metadata.db`. It's copied (never consumed) as one Calibre Import made of one Import job per file. Calibre metadata wins. Kind is decided per book from its Formats. Unsupported Formats become failed jobs. Custom columns and reading positions are dropped. A retry action re-runs the import without overwriting metadata.
- [What metadata does a Publication carry?](issues/11-publication-metadata-fields.md): Shared Contributors with ordered, role-typed Credits (fixed role set, exact normalized matching, manual merge). Series position is text plus a sort key, and Volume is part of Series identity. New **Reading Lists** (ordered, cross-Series, auto-filled from ComicInfo StoryArc) replace Collections. Fields: title, Credits, Series, Tags, description, publisher, language, date, identifiers; Files add page count. An attached File fills empty fields only.
- [How far should Panels progress sync go in iteration 1?](issues/14-panels-sync-ambition.md): None. Panels keeps its own progress; Reading Progress comes only from Readest via kosync.
- [What is the Reading Progress model?](issues/06-reading-progress-model.md): One kosync-shaped record per partial MD5 (opaque position, percentage, device, timestamp), latest only. Orphans are kept and survive the Trash. Last write wins. A File is read at ≥ 98%, and a Publication is read if any File is. kosync login always succeeds.
- [How is search implemented?](issues/15-search-design.md): Trigram (`pg_trgm` + `unaccent`), not full-text. Searches titles, Contributors, Series, Tags, Reading Lists, publisher and ISBN, but not descriptions. Every word must match, and title/Series/Contributor hits rank first. A stored search document is rebuilt in the edit transaction, plus a `reindex` action.
- [How are covers and thumbnails produced and stored?](issues/16-covers-and-thumbnails.md): One Cover per Publication (Calibre first, then the first File, replaceable by upload), stored as `cover.<ext>` in the Publication folder. A ~300px JPEG thumbnail is made at import into a wipeable `cache/`.
- [What is the management API shape?](issues/17-management-api-shape.md): REST + JSON under `/api/v1`, with named operations as `POST /resource/{id}/verb`. Problem Details errors, offset/limit pagination, code-first OpenAPI. References accept an id or a name. Covers Publications, Contributors, Series, Tags, Reading Lists, Possible Duplicates, Trash, Imports, Calibre Imports and reindex.

## Not yet specified

_Nothing left in the fog._

## Out of scope

Deferred features are tracked in [deferred.md](deferred.md).

- Web reader: a separate future map.
- Android/iOS apps: a separate future map.
- Server-side page streaming or rendering (OPDS-PSE): belongs with the client map. Confirmed out of scope by [Is OPDS-PSE page streaming in iteration 1 after all?](issues/12-page-streaming-scope.md).
- Multiple users, authentication, authorization and multi-tenancy: a future effort. Iteration 1 has no auth ([Which OPDS version, and how is the catalog structured?](issues/07-opds-catalog-design.md)).
- Panels progress sync (emulating Komga/Kavita, and the traffic-capture spike it needs): not in iteration 1, per [How far should Panels progress sync go in iteration 1?](issues/14-panels-sync-ambition.md).
- Online metadata lookup (Open Library, Google Books, Comic Vine): not in iteration 1; tracked in [deferred.md](deferred.md).
- [How are the library and database backed up and restored?](issues/18-backup-and-restore.md): moved to a later iteration; tracked in [deferred.md](deferred.md).

## Implementation tickets

The spec is broken into build tickets `issues/19`–`38` (`Type: implementation`, `Status: ready-for-agent`), wired with `Blocked by:`. The first one to pick is [Walking skeleton: Ktor + Postgres in Docker Compose](issues/19-walking-skeleton.md).
