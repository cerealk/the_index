# Minimal OPDS catalog with downloads

Type: implementation
Status: ready-for-human
Blocked by: 20

## What to build

Make the library visible in Readest and Panels as early as possible. Spec: [§7](../spec.md#7-opds-catalog).

- `/opds/` root (OPDS 1.2 Atom) with **Recently added** and **All** (A–Z by sort title), paginated about 50 per page with `next`/`previous`.
- Catalog building kept separate from the Atom serializer.
- Acquisition links for every File with the correct MIME types, preferred File first (cbz > cbr, epub > pdf).
- File download serves bytes unchanged, with a `Content-Disposition` filename independent of the stored path.
- Trashed Publications never appear.

## Acceptance criteria

- [x] The feed validates as OPDS 1.2 Atom (tested against fixture XML).
- [x] A downloaded File's SHA-256 equals the stored one.
- [ ] Manual check: Readest and Panels can browse the catalog and download a book (record the result in the ticket).

## Comments

- 2026-10-13 (agent): Implemented; only the manual reader check is left, hence `ready-for-human`. Code in `theindex.opds`: `Catalog.kt` (the catalog as plain data), `OpdsCatalog` (builds feeds from the database, plus download lookup), `AtomSerializer` (OPDS 1.2 Atom only), `OpdsRoutes`. Feeds: `/opds/` (navigation: Recently added with `rel=http://opds-spec.org/sort/new`, All), `/opds/recent` and `/opds/all` (acquisition, 50 per page, `?page=N`, `next`/`previous`/`up`/`start`). All sorts by lower-cased sort title, then id. Acquisition links: `/opds/files/{fileId}/{Title}.{ext}`; the name in the URL is cosmetic and is there for readers that ignore Content-Disposition. Downloads send `Content-Disposition: attachment` with an ASCII fallback plus `filename*` (RFC 6266). Format preference is the `Format` enum order (cbz, cbr, epub, pdf). Trashed Publications and Trashed Files are excluded from feeds and downloads. Tests: 34/34 pass. `AtomSerializerTest` and the root-feed test compare against fixture XML in `app/src/test/resources/opds/`, and every feed in `OpdsRoutesTest` is parsed and checked for the Atom/OPDS required elements. Docker E2E: `/opds/all` lists The Hobbit, and the downloaded file's SHA-256 equals the stored file and the `file.sha256` row.
- Manual check to do: add `http://<server>:8080/opds/` in Readest and Panels, browse Recently added / All, download a book. Record the result here and resolve.
