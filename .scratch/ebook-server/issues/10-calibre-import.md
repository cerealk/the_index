# How does the one-time Calibre import work?

Type: grilling
Status: resolved
Labels: wayfinder:grilling
Blocked by: 05, 08

## Question

Using the findings in [What does a Calibre library look like?](02-calibre-library-structure.md): how is a Calibre library migrated through the import pipeline? Decide how Kind is assigned, what happens to formats we don't support (MOBI, AZW3…), which extra metadata is kept (authors and their sort strings, publisher, identifiers, rating, description, cover, custom columns), whether Calibre's last-read positions become Reading Progress, and whether a re-import (keyed on Calibre's uuid) updates or skips.

Note from [How does the import pipeline work?](08-import-pipeline.md): the pipeline is persisted Import jobs, run by one worker through Format detection, hashing, extraction, Kind, match, store, convert and index. Decide whether Calibre's metadata overrides or supplements the extraction step, and whether unsupported formats become `failed` Import jobs or are skipped before any job is created.

## Answer

Grilled with the user on 2026-10-12. New glossary term: **Calibre Import** (in `/CONTEXT.md`).

1. **Trigger:** a folder containing `metadata.db` in the inbox (`/import`) is recognized as a Calibre library. Its files are never treated as loose inbox files. In Docker, bind-mount the library into the inbox (read-only is fine) rather than symlinking: a link to a target outside the container won't resolve.
2. **Non-destructive:** `metadata.db` is copied to a temp location and opened read-only (base tables only, per [What does a Calibre library look like?](02-calibre-library-structure.md)). Book files are **copied** into staging. The library folder is never drained, moved or deleted.
3. **Shape:** one **Calibre Import** record (Calibre `library_id`, path, timestamps, counts) groups **one ordinary Import job per supported file**. Each job carries its book's Calibre record (uuid plus metadata). Siblings join one Publication through the existing strong match on Calibre uuid.
4. **Metadata:** Calibre is authoritative. Its values win on every field it has, the cover included (`cover.jpg`). Stage 3 extraction only fills fields Calibre leaves empty. Which fields exist follows [What metadata does a Publication carry?](11-publication-metadata-fields.md). Sort strings are read as stored, and author order follows the link-row id. **Custom columns are dropped.**
5. **Kind:** decided once per Calibre book, from the set of its Formats (any cbz or cbr makes it a comic, otherwise the normal inference), and stamped on every job of that book so siblings can't split on Kind.
6. **Unsupported Formats** (MOBI, AZW3, …): each becomes a `failed` Import job with reason "unsupported Format: X" and its path in the Calibre library. **Nothing is copied** to `failed/`.
7. **Reading Progress:** Calibre's `last_read_positions` are **not** imported.
8. **Re-import:** the Calibre Import record, keyed on `library_id`, makes later inbox scans skip the library. No marker file is written into it. A management-API **retry** action on the Calibre Import (e.g. `POST /imports/calibre/{libraryId}/retry`) re-runs it. On a re-run, exact duplicates are skipped and new files attach by strong match, but **metadata of existing Publications is never overwritten**, because after the first import the server is the source of truth.
