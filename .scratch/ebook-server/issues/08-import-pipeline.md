# How does the import pipeline work?

Type: grilling
Status: resolved
Labels: wayfinder:grilling
Blocked by: 03, 05

## Question

Define the single import pipeline shared by the inbox folder and API upload: stages (detect Format, extract metadata, decide Kind, dedupe, store, index), how Kind is decided and overridden, what happens to failed or ambiguous imports, and how the inbox is watched (polling vs filesystem events) and cleaned up.

Includes a **cbr → cbz conversion** stage that adds the cbz as a second File and keeps the original ([Is OPDS-PSE page streaming in iteration 1?](12-page-streaming-scope.md)). Decide what happens if conversion fails.

## Answer

- **Every arrival is a persisted Import job.** Inbox files and API uploads become rows in Postgres with a state: `pending → processing → done | skipped | failed`. A row records a reason, warnings, and the resulting Publication/File ids. A **single background worker** drains the queue (`SELECT … FOR UPDATE SKIP LOCKED`), which avoids duplicate-detection races. An upload returns `202` with the job id.
- **Staging:** a file is moved to `.staging/<jobId>/<name>` when its job is created. Inbox files are moved only once their size and mtime have been stable for a few seconds. Uploads land in the same place. The worker reads only from staging.
- **Fail vs degrade:**
  - **Failed** (the file moves to `failed/<jobId>/`, with a reason on the job): Format not recognized by magic bytes, or an archive or PDF that can't be opened (corrupt or encrypted). To retry, fix the file and drop it into the inbox again. There's no retry API.
  - **Done with warnings:** missing or partial metadata (fall back to the filename parser, then the file name as the title), no cover, or a failed **cbr → cbz conversion** (the cbr Original is kept as the only File).
- **Kind:** inferred as described in [What metadata can be extracted from each Format…?](03-file-metadata-extraction.md), or forced by a hint: the inbox subfolders `inbox/prose/` and `inbox/comics/`, or an optional upload `kind` parameter. Files at the inbox root are inferred.
- **Inbox watching is a polling scan**, every 10s by default (configurable), plus a scan at startup. There's no `WatchService`, because it's unreliable on macOS and network mounts.
  - The scan is **recursive**. Hint folders apply by their top-level name.
  - Hidden files (`.*`) are ignored and left in place. Other unrecognized files become `failed` jobs.
  - Drained empty subfolders are removed. The root and hint folders are never removed.
  - Folder names are never used as metadata.
- **Stage order** (one job at a time):
  1. Detect Format. Unknown → failed.
  2. Hash: SHA-256 plus KOReader partial MD5, in one pass. An exact duplicate is skipped and its staged file deleted.
  3. Extract metadata and cover. A file that can't be opened → failed.
  4. Decide Kind: the hint, otherwise inference.
  5. Match: a strong match attaches to the existing Publication, a weak match creates a new Publication flagged Possible Duplicate, and anything else creates a new Publication.
  6. Store the Original: crash-safe move into the per-Kind layout, plus the database rows in one transaction.
  7. Convert every cbr Original to cbz, including ones attached by a strong match. The cbz is stored as a Conversion with `derivedFrom` set.
  8. Index for search ([How is search implemented?](15-search-design.md)) → done.
- **Crash recovery:** at startup, jobs left in `processing` go back to `pending`. The file stays in staging until step 6 commits. Steps 6 and 7 check by hash whether the file is already stored, so rerunning a job is idempotent. A Conversion that is missing gets regenerated.
- **Reporting:** `GET /imports` (filter by state, newest first) and `GET /imports/{id}` in the management API. Nothing appears in OPDS. **Job rows are kept forever** as the import history.
