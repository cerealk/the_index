# Reading Lists endpoints

Type: implementation
Status: ready-for-agent
Blocked by: 32, 24

## What to build

Spec: [§12](../spec.md#12-management-api).

- Create, list, get, rename, delete. Add and remove entries. `POST …/reorder` with the full ordered list of ids.
- A rename rebuilds search documents.

## Acceptance criteria

- [ ] A Publication can sit in two Reading Lists at different positions.
- [ ] Reorder with a mismatched id set is rejected.
