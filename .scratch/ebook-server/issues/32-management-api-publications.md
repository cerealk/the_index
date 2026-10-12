# Management API foundations and Publication editing

Type: implementation
Status: ready-for-agent
Blocked by: 22, 31

## What to build

Spec: [§12](../spec.md#12-management-api), [§9](../spec.md#9-metadata-editing-rules).

- Foundations: RFC 9457 Problem Details via `StatusPages` (with field errors), `offset`/`limit` → `{items, total}`, code-first OpenAPI + Swagger UI. Retrofit `/imports` onto them.
- `GET /publications` (q + filters: kind, tag, series, readState), `GET/PATCH /publications/{id}`, `PUT …/credits`, `…/series`, `…/tags`, `…/identifiers`. References accept an id or a name.
- Edits that change a layout segment move the folder crash-safely. Startup reconciliation of interrupted moves.
- Edits rebuild the search document in the same transaction.

## Acceptance criteria

- [ ] Changing a title or Series moves the folder, and the File bytes are unchanged.
- [ ] A simulated crash between copy and delete is reconciled at startup.
- [ ] Validation errors return Problem Details with field errors.
- [ ] The OpenAPI document lists every route.
