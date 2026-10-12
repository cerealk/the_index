# Spec: Ebook library server, iteration 1

Status: accepted (2026-10-12)
Source: the map [map.md](map.md). Every section links to the ticket that holds the decision and its reasoning. Terms in **bold capitals** are glossary terms from [/CONTEXT.md](../../CONTEXT.md).

## 1. Purpose and scope

A personal, single-user server on the LAN that **owns** a collection of ebooks and comics, organizes it, serves it to existing reader apps over **OPDS 1.2**, and stores **Reading Progress** synced from Readest over **kosync**.

- Files enter the library through the **inbox** folder, through API upload, and through a one-time **Calibre Import**.
- Size: thousands of Files.
- Readers: **Readest** (epub, pdf, cbz, plus progress sync) and **Panels** (comics, download only, no progress sync).
- **Originals are never lost.** The server never modifies or deletes an imported **Original**. Derived Files (**Conversions**) are added alongside it.

Out of scope for iteration 1: see [§14](#14-out-of-scope) and [deferred.md](deferred.md).

## 2. Runtime and stack

From [Which server stack?](issues/04-stack-choice.md) and [Docker container or plain JVM process?](issues/09-runtime-packaging.md).

| Concern | Choice |
|---|---|
| HTTP | Ktor |
| Database | PostgreSQL, with the `pg_trgm` and `unaccent` extensions |
| Data access | Exposed DSL, raw SQL where Postgres-specific |
| Migrations | Flyway |
| DI | Koin |
| Serialization | kotlinx.serialization |
| Tests | JUnit 5 + Kotest assertions, Ktor `testApplication`, Testcontainers Postgres (singleton, reused between runs, per-test cleanup) |
| Archive/PDF | junrar 8.x (behind a seam, optional `7z`/`bsdtar` fallback), PDFBox 3 (+ xmpbox, jbig2-imageio), zip + jsoup in XML mode |
| Deployment | Docker Compose: an `app` service (image built from the Gradle project) and a `postgres` service (official image). Storage, inbox and Postgres data are on mounted volumes. Any Docker host. |

Logging, config-file format, the image build method (Dockerfile or Jib), the volume type and the port mapping are left to the implementer.

## 3. Domain model

Glossary definitions are in `/CONTEXT.md`. This section adds the fields and rules. Decisions: [What metadata does a Publication carry?](issues/11-publication-metadata-fields.md), [How are Files stored and identified?](issues/05-storage-layout-and-file-identity.md).

### Publication
- `id` (surrogate; appears in folder names)
- `kind`: prose | comic. Inferred at import, editable.
- `title`, `sortTitle`. The sort title comes from Calibre or `file-as`, otherwise it's derived by moving a leading article.
- `credits`: ordered list of **Credit** (`contributorId`, `role`)
- `series`: optional (`seriesId`, `positionText`, `positionSortKey`)
- `tags`: set of **Tag** names
- `description`: HTML sanitized to a safe subset
- `publisher`: text
- `language`: one ISO 639 code
- `publicationDate`: partial date (year, year-month or full)
- `identifiers`: list of (`type`, `value`), e.g. `isbn`, `calibre`, `comicvine`. Used by strong matching.
- `cover`: optional **Cover** (see [§11](#11-covers-and-thumbnails))
- `possibleDuplicateOf`: optional flag (see [§5](#5-import-pipeline))
- trash state

Not kept: rating, and the ComicInfo extras (Characters, Teams, Locations, AgeRating, Imprint, Web). Every field and `kind` is editable. File fields are read-only.

### File
- `id`, `publicationId`, `format` (epub | pdf | cbz | cbr), `size`
- `sha256`: content identity and integrity
- `partialMd5`: KOReader partial MD5, the kosync document key
- `role`: Original | Conversion. A Conversion has `derivedFrom` (the Original's file id).
- `pageCount`: comics are counted from the archive, pdf from the document, epub has none
- `storedPath`, `importedAt`, trash state

Embedded metadata is not kept per File. It fills the Publication once, and a raw snapshot is stored on the Import record.

### Contributor and Credit
- Contributor: `id`, `name`, `sortName`. Shared across the library.
- Credit roles (fixed set): author, writer, penciller, inker, colorist, letterer, cover artist, illustrator, translator, editor. A missing source role defaults to **author** (prose) or **writer** (comic).
- **Matching** (import and API by-name references): normalized exact match (case-fold, strip accents, collapse spaces and dots, "Last, First" → "First Last"). Otherwise a new Contributor is created. No fuzzy matching. A manual merge moves Credits.
- The sort name comes from Calibre or `file-as` when present, otherwise it's derived as "Last, First".

### Series
- `id`, `name`, `volume` (optional). **Volume is part of the identity**: *Saga (2012)* and *Saga (2018)* are two Series.
- Position on the Publication: `positionText` as found ("1", "1.5", "Annual 1") plus a derived decimal `positionSortKey` (numeric prefix, non-numeric sorts last). Both are editable.

### Reading List
- `id`, `name`, an ordered list of Publications. A Publication can be in any number of Reading Lists. Lists may cut across Series.
- ComicInfo `StoryArc` / `StoryArcNumber` (comma-separated, possibly several) find or create a Reading List by name at import, placing the issue by number or appending it.

### Tag
- A free label (by name). Also filled from epub `dc:subject` and ComicInfo `Genre`. There are no Collections.

### Reading Progress
See [§8](#8-reading-progress-and-kosync).

### Import, Calibre Import
See [§5](#5-import-pipeline) and [§6](#6-calibre-import).

### Trash
Deleting a Publication or File is a soft delete. Files move to `.trash/` and rows are marked deleted. They can be restored until the reader explicitly empties the Trash. The server never permanently deletes on its own. Deleting an Original also deletes its Conversions, which can be regenerated.

## 4. Storage layout and file identity

From [How are Files stored and identified?](issues/05-storage-layout-and-file-identity.md).

- **Per-Kind, human-readable layout.** All Files of a Publication share one folder whose name ends with the Publication id:
  - prose: `prose/<Author>/<Series>/<NN> - <Title> (<id>)/<Title>.<ext>`, or `prose/<Author>/<Title> (<id>)/…` without a Series
  - comic: `comics/<Series>/<Series> #<Number> - <Title> (<id>)/….<ext>`, or `comics/<Title> (<id>)/…` without a Series
  - `<Author>` is the first Credit with role author. Path segments are sanitized (illegal characters, length, case collisions).
  - The download filename is set by a header and is independent of the stored path.
- The Publication folder also holds `cover.<ext>` ([§11](#11-covers-and-thumbnails)).
- **Metadata edits move the folder immediately**, crash-safely: copy, fsync, update the database, delete the old path. A startup check reconciles interrupted moves. Moves never change bytes.
- **Other top-level areas** of the storage volume: `.staging/<jobId>/`, `failed/<jobId>/`, `.trash/`, `cache/thumbs/`. The inbox is a separate mount.

## 5. Import pipeline

From [How does the import pipeline work?](issues/08-import-pipeline.md), [What metadata can be extracted from each Format?](issues/03-file-metadata-extraction.md), [What metadata does a Publication carry?](issues/11-publication-metadata-fields.md).

- **Every arrival is a persisted Import job**: `pending → processing → done | skipped | failed`, with a reason, warnings, the resulting Publication and File ids, and a raw snapshot of the extracted metadata. **One background worker** drains the queue (`SELECT … FOR UPDATE SKIP LOCKED`). Jobs are kept forever.
- **Sources:**
  - **Inbox**, polled every 10 s (configurable) plus once at startup. Polling is recursive, and a file is moved to `.staging/<jobId>/` once its size and mtime have been stable for a few seconds. `inbox/prose/` and `inbox/comics/` force the Kind, and the root infers it. Hidden files are ignored. Other unrecognized files become `failed` jobs. Drained empty subfolders are removed, but never the root or the hint folders. Folder names are never used as metadata. A folder containing `metadata.db` is a Calibre library ([§6](#6-calibre-import)).
  - **Upload**: `POST /api/v1/imports` ([§12](#12-management-api)).
- **Stages**, one job at a time:
  1. **Detect Format** by magic bytes. Unknown → failed.
  2. **Hash**: SHA-256 + partial MD5 in one pass. Exact duplicate (SHA-256 matches any stored File, including Trashed ones and the Original of a converted cbr) → skipped, and the staged copy is deleted.
  3. **Extract** metadata, the cover and the page count. A file that can't be opened (corrupt or encrypted) → failed, moved to `failed/<jobId>/`. Missing metadata → a warning, falling back to the filename parser, then the file name as the title. epub: OPF Dublin Core, with series from EPUB 3 `belongs-to-collection` and then `calibre:series`. pdf: XMP, then the Info dict. cbz/cbr: ComicInfo.xml, then the filename parser.
  4. **Decide Kind**: the hint, otherwise inference. cbz/cbr default to comic and epub/pdf to prose. Flip on epub `book-type=comic`, on pre-paginated epubs with one image per page, or on image-only PDFs.
  5. **Match**:
     - **Strong** (same identifier such as ISBN or Calibre uuid, or the same normalized title + author for prose / Series + number for comics, with the same Kind): attach as a new File to the existing Publication. Attaching **fills empty Publication fields only** and never overwrites.
     - **Weak** (fuzzy title/author): a new Publication flagged as a **Possible Duplicate**.
     - Otherwise: a new Publication.
     - Contributors, Series, Tags and Reading Lists resolve as in [§3](#3-domain-model).
  6. **Store the Original**: a crash-safe move into the layout, with the database rows in one transaction. Write the Cover and generate the thumbnail ([§11](#11-covers-and-thumbnails)).
  7. **Convert** every cbr Original to cbz (lossless repack), stored as a Conversion with `derivedFrom`. A failed conversion is a warning, and the cbr stays the only File.
  8. **Index** the search document ([§10](#10-search)) → done.
- **Crash recovery:** at startup, `processing` jobs go back to `pending`. The file stays in staging until stage 6 commits. Stages 6 and 7 check by hash, so rerunning is idempotent.
- No retry API for failed jobs: fix the file and drop it in the inbox again.

## 6. Calibre Import

From [How does the one-time Calibre import work?](issues/10-calibre-import.md) and [What does a Calibre library look like?](issues/02-calibre-library-structure.md).

- **Trigger:** a folder containing `metadata.db` inside the inbox. Bind-mount the library into the inbox (read-only is fine). Don't use a symlink, because it won't resolve inside the container. Its files are never treated as loose inbox files.
- **Non-destructive:** `metadata.db` is copied to a temp location and opened read-only, querying base tables only (`books`, `data`, `authors` + links, `series` + links, `tags` + links, `publishers`, `languages`, `identifiers`, `comments`) and resolving paths via `books.path` + `data.name` + `.<lower(format)>`. Book files are **copied** into staging. The library is never drained, moved or written to.
- **Shape:** one **Calibre Import** record (Calibre `library_id`, path, timestamps, counts) groups **one ordinary Import job per supported file**. Each job carries its book's Calibre record (uuid plus metadata). Siblings join one Publication through the strong match on Calibre uuid.
- **Metadata:** Calibre is authoritative. Its values win on every field it has, the Cover included (`cover.jpg`). Extraction only fills gaps. Sort strings are read as stored, and author order follows the link-row id. Custom columns, rating and `last_read_positions` are **not** imported.
- **Kind:** decided once per Calibre book (any cbz or cbr makes it a comic, otherwise the normal inference) and stamped on all of its jobs.
- **Unsupported Formats** (MOBI, AZW3, …): each becomes a `failed` Import job with reason "unsupported Format: X" and its path in the Calibre library. Nothing is copied.
- **Re-import:** the Calibre Import record makes later scans skip the library. `POST /api/v1/calibre-imports/{id}/retry` re-runs it. Exact duplicates are skipped and new files attach by strong match, but metadata of existing Publications is never overwritten.

## 7. OPDS catalog

From [Which OPDS version, and how is the catalog structured?](issues/07-opds-catalog-design.md), as amended by [What metadata does a Publication carry?](issues/11-publication-metadata-fields.md).

- **OPDS 1.2 (Atom) only**, under `/opds/`. Catalog building is kept separate from XML serialization so OPDS 2.0 can be added later. No OPDS-PSE. No auth.
- **Navigation:**
  ```
  Root
  ├── Continue reading   (in-progress Publications, most recently updated first)
  ├── Recently added
  ├── Prose
  │   ├── By author → Contributor → Publications (grouped by Series, in order)
  │   ├── By series → Series → Publications in Series order
  │   └── All (A–Z by sort title)
  ├── Comics
  │   ├── By series → Series → issues in position order
  │   └── All (A–Z)
  ├── Tags → Tag → Publications
  ├── Reading Lists → Reading List → Publications in list order
  └── Search (OpenSearch, see §10)
  ```
- Pagination: `next`/`previous`, about 50 entries per page. Publications in the Trash never appear.
- **Entries:** summary (description), Cover and thumbnail links (`http://opds-spec.org/image`, `…/image/thumbnail`), omitted when there's no Cover. Every File is an acquisition link with its MIME type (`application/epub+zip`, `application/pdf`, `application/vnd.comicbook+zip`, `application/vnd.comicbook-rar`). The **preferred File comes first**: cbz before cbr, epub before pdf.
- Files are served **byte-identical**, because kosync hashes depend on it.

## 8. Reading Progress and kosync

From [What is the Reading Progress model?](issues/06-reading-progress-model.md), [How do Readest and Panels consume OPDS and sync reading progress?](issues/01-reader-apps-opds-and-sync.md).

- **Endpoints** under `/kosync/`: `POST /users/create`, `GET /users/auth`, `GET /syncs/progress/{document}`, `PUT /syncs/progress`, `GET /healthcheck`. Accept `application/vnd.koreader.v1+json`.
- **Auth:** none. Register and auth always succeed, and `X-Auth-User`/`X-Auth-Key` are ignored.
- **Record (latest only):** keyed by `document` = partial MD5, holding the opaque `progress` string (never interpreted), `percentage`, `device`, `device_id` and a server `updated_at`. PUT overwrites it and returns `{document, timestamp}`. GET returns `{percentage, progress, device, device_id, timestamp}`.
- **Conflicts:** last write wins, by the server's receive time.
- **Joined to File by hash.** Progress for an unknown hash is stored as an orphan and attaches when a matching File is imported. Progress survives Trash and restore, and is deleted when the Trash is emptied.
- **Read state:** a File is read when `percentage` ≥ the read threshold (default 0.98, configurable). There's no manual mark. A Publication is *read* if any File is read, *in progress* if any File has progress below the threshold, otherwise *unread*.
- No Panels sync. Calibre positions aren't imported.

## 9. Metadata editing rules

- Every Publication field and `kind` is editable ([§12](#12-management-api)).
- Edits that change a layout path segment (title, Kind, first author, Series, position) move the folder crash-safely ([§4](#4-storage-layout-and-file-identity)).
- Edits that change searchable text rebuild the affected search documents in the same transaction ([§10](#10-search)).

## 10. Search

From [How is search implemented?](issues/15-search-design.md).

- **Trigram** (`pg_trgm` + `unaccent`). No full-text search.
- **A stored search document per Publication**, as two normalized (unaccented, lowercased, punctuation-stripped) text columns with trigram GIN indexes:
  - **primary**: title, sort title, Contributor names, Series name + position text
  - **secondary**: Tags, Reading List names, publisher, identifiers
  - Not the description.
- **Query:** normalized the same way. **Every word must match** (`word_similarity` ≥ threshold). Results are ranked by combined similarity, with primary above secondary.
- **Maintenance:** rebuilt in Kotlin in the same transaction as any affecting edit (Publication edits; Contributor, Series, Tag or Reading List rename or merge rebuilds all linked Publications). Written by import stage 8. `POST /api/v1/admin/reindex` rebuilds everything.
- The Trash is excluded. OPDS OpenSearch returns both Kinds, paginated.

## 11. Covers and thumbnails

From [How are covers and thumbnails produced and stored?](issues/16-covers-and-thumbnails.md).

- **One Cover per Publication.** Source order: Calibre `cover.jpg`, otherwise the first File's extracted cover (epub cover-image → EPUB 2 `meta cover` → guide or first image; comic FrontCover page → first image in natural order; pdf page 1 rendered with PDFBox). A later File only fills a missing Cover. Replaceable by upload.
- **Stored** as `cover.<ext>` in the Publication folder, as found (no re-encoding). It moves and goes to the Trash with the folder.
- **Thumbnail:** generated at import, about 300 px tall, JPEG q≈85, at `cache/thumbs/<publicationId>.jpg`. Regenerated on Cover replacement, and on request if missing. `cache/` is wipeable.
- No Cover → a warning on the Import, and the OPDS image links are omitted.

## 12. Management API

From [What is the management API shape?](issues/17-management-api-shape.md).

- **REST + JSON under `/api/v1/`.** `PATCH` and `PUT` handle edits. **Named operations** use `POST /resource/{id}/verb` for side effects beyond the row. No auth.
- **Errors:** RFC 9457 Problem Details, with an `errors: [{field, message}]` extension for validation.
- **Pagination:** `offset`/`limit` (default 50, max 200) → `{items, total}`.
- **Docs:** code-first OpenAPI generated from the Ktor routes, served with Swagger UI.
- **References:** Contributors and Series are given by `id` or `name` (names resolve with the import matching and are created if missing). Tags are given by name. Responses embed related resources in full.
- **Endpoints:**

| Resource | Endpoints |
|---|---|
| Publications | `GET /publications?q=&kind=&tag=&series=&readState=&offset=&limit=` · `GET/PATCH /publications/{id}` · `PUT /publications/{id}/credits` · `PUT …/series` · `PUT …/tags` · `PUT …/identifiers` · `PUT …/cover` (upload) · `POST …/trash` |
| Contributors | `GET /contributors` · `GET/PATCH /contributors/{id}` · `POST /contributors/{id}/merge {into}` |
| Series | `GET /series` · `GET/PATCH /series/{id}` |
| Tags | `GET /tags` · `PATCH /tags/{name}` · `POST /tags/{name}/merge {into}` |
| Reading Lists | `POST /reading-lists` · `GET /reading-lists` · `GET/PATCH/DELETE /reading-lists/{id}` · `POST /reading-lists/{id}/entries` · `DELETE …/entries/{publicationId}` · `POST …/reorder` |
| Possible Duplicates | `GET /possible-duplicates` · `POST /possible-duplicates/{id}/merge` · `POST …/dismiss` |
| Trash | `GET /trash` · `POST /trash/{id}/restore` · `POST /trash/empty` |
| Imports | `POST /imports` (multipart `file` parts + optional `kind`, streamed to staging, size limit, `202` + job ids) · `GET /imports?state=` · `GET /imports/{id}` |
| Calibre Imports | `GET /calibre-imports` · `GET /calibre-imports/{id}` · `POST /calibre-imports/{id}/retry` |
| Admin | `POST /admin/reindex` |

Files, Reading Progress and read state are read-only, embedded in Publication responses.

## 13. Configuration

Settings with the defaults decided so far:

| Setting | Default |
|---|---|
| Inbox poll interval | 10 s |
| Inbox stability window | a few seconds (implementer's choice) |
| Read threshold | 0.98 |
| Upload size limit | implementer's choice |
| Thumbnail height / JPEG quality | 300 px / 85 |
| Search similarity threshold | to be tuned in implementation |
| Storage, inbox and cache paths, database URL | from the environment (Compose) |

## 14. Out of scope

From the map's Out of scope section. Details are in [deferred.md](deferred.md).

- Web reader, mobile apps, OPDS-PSE page streaming (separate future maps)
- Multiple users and auth
- Panels progress sync
- Online metadata lookup
- Backup and restore
- Reading history, full-text search on descriptions, picking a Cover from a File, ComicInfo extras

## 15. Spec-level defaults (accepted)

Small choices made while writing this spec that no ticket settled explicitly. The user accepted them on 2026-10-12.

1. The prose folder `<Author>` is the **first Credit with role author** (falling back to the first Credit of any role, then "Unknown").
2. A comic Series with a volume is named `<Series> (<Volume>)` in folder names.
3. The exact-duplicate check (stage 2) also matches Files sitting in the Trash, so a trashed book dropped in the inbox again is skipped. Restore it from the Trash instead.
