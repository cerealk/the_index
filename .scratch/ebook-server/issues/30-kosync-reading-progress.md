# kosync endpoints, Reading Progress and read state

Type: implementation
Status: ready-for-agent
Blocked by: 21

## What to build

Spec: [§8](../spec.md#8-reading-progress-and-kosync).

- `/kosync/users/create`, `/users/auth` (always succeed), `GET /syncs/progress/{document}`, `PUT /syncs/progress`, `/healthcheck`, with KOReader JSON shapes and media type.
- A Reading Progress row keyed by partial MD5 (latest only, last write wins on server time). Orphans are kept and joined to Files by hash.
- Read state per File (threshold configurable, default 0.98) and per Publication (read / in progress / unread).
- OPDS **Continue reading** feed (in progress, newest first).

## Acceptance criteria

- [ ] kosync contract tests reproduce the reference server's request/response pairs.
- [ ] A PUT for an unknown hash is returned by GET and attaches when a matching File is imported later.
- [ ] Manual check: Readest syncs progress across two devices through the server.
