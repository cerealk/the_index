# Strong and weak matching, Possible Duplicates

Type: implementation
Status: ready-for-agent
Blocked by: 22

## What to build

Spec: [§5 stage 5](../spec.md#5-import-pipeline), [§3](../spec.md#3-domain-model).

- Strong match: same identifier (ISBN, Calibre uuid), or normalized title + author (prose) / Series + number (comic), with the same Kind → attach the File, **filling empty Publication fields only**.
- Weak match (fuzzy title/author) → a new Publication flagged Possible Duplicate of the other.
- The exact-duplicate check also matches Trashed Files ([§15](../spec.md#15-spec-level-defaults-accepted)).

## Acceptance criteria

- [ ] The epub and pdf of the same book (same ISBN) end up as one Publication with two Files. The pdf's publisher fills an empty field but never overwrites.
- [ ] A near-identical title creates a flagged Possible Duplicate.
