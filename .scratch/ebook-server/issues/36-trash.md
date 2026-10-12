# Trash: trash, restore, empty

Type: implementation
Status: ready-for-agent
Blocked by: 32, 30

## What to build

Spec: [§3 Trash](../spec.md#3-domain-model), [§12](../spec.md#12-management-api).

- `POST /publications/{id}/trash` moves the folder to `.trash/` and marks the rows. Deleting an Original also trashes its Conversions.
- `GET /trash`, `POST /trash/{id}/restore` (back to the layout path), `POST /trash/empty` (permanent delete, including the Reading Progress of those Files).
- Trashed items are excluded from OPDS and search.

## Acceptance criteria

- [ ] Trash then restore brings back the same bytes, paths, Reading Progress and search hits.
- [ ] Emptying the Trash deletes the files and their progress rows.
