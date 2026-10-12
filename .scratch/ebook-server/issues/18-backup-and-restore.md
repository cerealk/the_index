# How are the library and database backed up and restored?

Type: grilling
Status: resolved
Labels: wayfinder:grilling

## Question

With Docker Compose ([Docker container or plain JVM process?](09-runtime-packaging.md)), the state is the storage volume (Files, Covers, `.trash/`, `failed/`, staging; `cache/` is excluded) plus the Postgres database. Decide: is backup part of the server (e.g. a scheduled `pg_dump` and an admin endpoint) or left to host tooling (a documented script, restic or borg, NAS snapshots); how storage and the database are kept consistent with each other (dump and file copy taken at different moments, while imports and file moves are in flight); and what a restore looks like, including the startup reconciliation from [How are Files stored and identified?](05-storage-layout-and-file-identity.md).

## Answer

**Ruled out of scope for iteration 1** (user's decision, 2026-10-12): backup and restore move to a later iteration and are tracked in [deferred.md](../deferred.md). Closed without a decision on the route.
