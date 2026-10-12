# Upload endpoint and Cover replacement

Type: implementation
Status: ready-for-agent
Blocked by: 32, 28

## What to build

Spec: [§12](../spec.md#12-management-api), [§11](../spec.md#11-covers-and-thumbnails).

- `POST /api/v1/imports`: multipart, one or more `file` parts, optional `kind`, streamed to staging (never buffered in memory), configurable size limit, `202` with job ids.
- `PUT /publications/{id}/cover`: replace `cover.<ext>` and regenerate the thumbnail.

## Acceptance criteria

- [ ] `curl -F file=@a.epub -F file=@b.cbz` creates two jobs that complete.
- [ ] An oversized upload returns Problem Details 413.
- [ ] A replaced Cover shows up in OPDS.
