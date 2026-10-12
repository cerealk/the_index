# Trigram search and OpenSearch

Type: implementation
Status: ready-for-agent
Blocked by: 22, 24, 21

## What to build

Spec: [§10](../spec.md#10-search).

- Primary and secondary search-document columns with trigram GIN indexes, normalized with `unaccent` + lowercase + punctuation stripped.
- A query service: every word must match (`word_similarity` ≥ threshold), ranked primary over secondary, Trash excluded.
- Written by import stage 8. A rebuild helper used by later edit tickets, plus `POST /api/v1/admin/reindex`.
- OPDS OpenSearch descriptor and results feed (paginated).

## Acceptance criteria

- [ ] "uncanny xmen 2013" finds *Uncanny X-Men (2013)* issues, and "tolkein" finds Tolkien.
- [ ] A description-only word doesn't match.
- [ ] Reindex rebuilds every document.
