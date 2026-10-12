# Possible Duplicates: list, merge, dismiss

Type: implementation
Status: ready-for-agent
Blocked by: 32, 27

## What to build

Spec: [§12](../spec.md#12-management-api), [§5 stage 5](../spec.md#5-import-pipeline).

- List flagged Publications with their candidate.
- Merge: move the Files into the target Publication folder, fill empty fields only, and fold Credits, Tags and Reading List entries. The merged Publication goes away.
- Dismiss: clear the flag.

## Acceptance criteria

- [ ] After a merge, the target holds both Files, the folders are consistent, and the search document is rebuilt.
- [ ] Dismiss removes the flag permanently.
