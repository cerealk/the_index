# Inbox → Import job → stored Original

Type: implementation
Status: ready-for-agent
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

- [ ] Dropping an epub into the inbox yields a `done` job, a Publication and a File at the expected path, byte-identical to the input.
- [ ] Dropping the same bytes again yields a `skipped` job, and the inbox copy is gone.
- [ ] A non-ebook file yields a `failed` job and ends up in `failed/<jobId>/`.
- [ ] Killing the app mid-job and restarting completes the job without duplicates (test at the worker level).
- [ ] The partial MD5 matches KOReader's algorithm on a reference file.
