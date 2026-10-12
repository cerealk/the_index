# Minimal OPDS catalog with downloads

Type: implementation
Status: ready-for-agent
Blocked by: 20

## What to build

Make the library visible in Readest and Panels as early as possible. Spec: [§7](../spec.md#7-opds-catalog).

- `/opds/` root (OPDS 1.2 Atom) with **Recently added** and **All** (A–Z by sort title), paginated about 50 per page with `next`/`previous`.
- Catalog building kept separate from the Atom serializer.
- Acquisition links for every File with the correct MIME types, preferred File first (cbz > cbr, epub > pdf).
- File download serves bytes unchanged, with a `Content-Disposition` filename independent of the stored path.
- Trashed Publications never appear.

## Acceptance criteria

- [ ] The feed validates as OPDS 1.2 Atom (tested against fixture XML).
- [ ] A downloaded File's SHA-256 equals the stored one.
- [ ] Manual check: Readest and Panels can browse the catalog and download a book (record the result in the ticket).
