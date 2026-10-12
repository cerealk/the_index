# cbr support and cbr → cbz Conversion

Type: implementation
Status: ready-for-agent
Blocked by: 24

## What to build

Spec: [§5 stages 3 and 7](../spec.md#5-import-pipeline), [§3 File](../spec.md#3-domain-model).

- Read cbr through junrar 8.x behind an archive seam. An optional `7z`/`bsdtar` fallback is configurable.
- Same extraction as cbz.
- Stage 7: repack every cbr Original into a cbz Conversion (`derivedFrom`) in the same Publication folder, also when the cbr was attached by a strong match. A failed conversion is a warning.
- Idempotent: a missing Conversion is regenerated on rerun.

## Acceptance criteria

- [ ] A RAR4 and a RAR5 fixture each import as a Publication with two Files (cbr Original, cbz Conversion) containing identical images.
- [ ] The cbr Original's bytes are unchanged.
- [ ] Both Files appear in OPDS with cbz first.
