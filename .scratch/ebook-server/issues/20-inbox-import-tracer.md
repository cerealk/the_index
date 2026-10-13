# Inbox → Import job → stored Original

Type: implementation
Status: resolved
Blocked by: 19

## What to build

The thinnest import path: a file dropped in the inbox ends up stored as an Original of a new Publication. Spec: [§4](../spec.md#4-storage-layout-and-file-identity), [§5](../spec.md#5-import-pipeline) (stages 1, 2, 6), [§3 File and Publication](../spec.md#3-domain-model).

- Schema: Publication (id, kind, title, sortTitle, trash state), File (all fields from §3), Import job (state, reason, warnings, result ids, metadata snapshot).
- Inbox polling (interval configurable, plus a scan at startup): recursive scan, stability window, hidden files ignored, drained subfolders removed (never the root or `prose/`/`comics/`), move to `.staging/<jobId>/`.
- A single worker with `SELECT … FOR UPDATE SKIP LOCKED`.
- Stage 1: Format by magic bytes (epub, pdf, cbz, cbr). Unknown → failed, moved to `failed/<jobId>/`.
- Stage 2: SHA-256 + KOReader partial MD5 in one pass. Exact duplicate → skipped, staged copy deleted.
- Kind defaults to Format (cbz/cbr → comic, else prose). Title = file name for now.
- Stage 6: the layout path from §4 (no Series or Credits yet), a crash-safe move, rows in one transaction.
- Crash recovery: `processing` → `pending` at startup, idempotent store by hash.
- `GET /api/v1/imports?state=` and `GET /api/v1/imports/{id}` (plain JSON; Problem Details and pagination arrive with [Management API foundations and Publication editing](32-management-api-publications.md)).

## Acceptance criteria

- [x] Dropping an epub into the inbox yields a `done` job, a Publication and a File at the expected path, byte-identical to the input.
- [x] Dropping the same bytes again yields a `skipped` job, and the inbox copy is gone.
- [x] A non-ebook file yields a `failed` job and ends up in `failed/<jobId>/`.
- [x] Killing the app mid-job and restarting completes the job without duplicates (test at the worker level).
- [x] The partial MD5 matches KOReader's algorithm on a reference file.

## Comments

- 2026-10-12 (agent): Done. Migration `V2__import_publication_file.sql` (publication, file, import_job). Code in `theindex.files` (`FormatDetector`, `Hasher`, `Storage`), `theindex.publications` (`Kind`, `Library`), `theindex.imports` (`Inbox`, `ImportWorker`, `ImportJobs`, routes). Crash safety: the Publication id is reserved and the target path recorded on the job (`store_target`) in a committed transaction, the copy is atomic (temp file + fsync + atomic rename), then the rows and the job outcome commit together. `recover()` deletes copies whose rows never committed and resets `processing` jobs. Tests: 25/25 pass (`ImportPipelineTest` 8, `ImportRoutesTest` 3, Format/Hasher/Storage unit tests). The partial MD5 reference vector was computed independently in Python using 32-bit shift semantics: KOReader's first offset `1024 << -2` wraps to 0. E2E in Docker Compose: an epub and a text file dropped in `./data/inbox` became a `done` job (stored at `prose/Unknown/The Hobbit (1)/The Hobbit.epub`) and a `failed` job (`failed/1/notes.txt`) within one poll.
- Beyond scope: inbox hint folders (`prose/`, `comics/`) are already recorded on the job and applied as the Kind, which overlaps with [Kind inference and inbox hint folders](26-kind-inference.md).
- New settings: `THE_INDEX_INBOX_POLL_SECONDS` (default 10) and `THE_INDEX_INBOX_STABILITY_SECONDS` (default 3).
