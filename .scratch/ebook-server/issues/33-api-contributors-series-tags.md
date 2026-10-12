# Contributors, Series and Tags endpoints with merges

Type: implementation
Status: ready-for-agent
Blocked by: 32

## What to build

Spec: [§12](../spec.md#12-management-api).

- Contributors: list, get, patch (name, sortName), `POST …/merge {into}`.
- Series: list, get, patch (name, volume).
- Tags: list, `PATCH /tags/{name}` (rename), `POST /tags/{name}/merge {into}`.
- Every change rebuilds the affected search documents and moves affected folders.

## Acceptance criteria

- [ ] Merging two Contributors moves all Credits, leaves no orphan, and updates folders and search.
- [ ] Renaming a Tag to an existing name is rejected with a hint to merge.
