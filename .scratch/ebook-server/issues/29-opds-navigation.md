# Full OPDS navigation

Type: implementation
Status: ready-for-agent
Blocked by: 21, 22, 24

## What to build

Spec: [§7](../spec.md#7-opds-catalog).

- Prose: By author → Contributor → Publications (grouped by Series, in order), By series, All.
- Comics: By series → issues in position order, All.
- Tags → Tag → Publications. Reading Lists → Reading List → Publications in list order.
- Entries carry the summary (sanitized description). Continue reading comes with [kosync endpoints, Reading Progress and read state](30-kosync-reading-progress.md), and Search with [Trigram search and OpenSearch](31-search.md).

## Acceptance criteria

- [ ] Feed tests cover every navigation path with fixture data, including ordering by position sort key.
- [ ] Manual check in Panels and Readest.
