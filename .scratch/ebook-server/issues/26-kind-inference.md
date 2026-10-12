# Kind inference and inbox hint folders

Type: implementation
Status: ready-for-agent
Blocked by: 22, 23, 24

## What to build

Spec: [§5 stage 4](../spec.md#5-import-pipeline).

- Hints: `inbox/prose/` and `inbox/comics/` force Kind (by top-level folder name). Upload hints come with [Upload endpoint and Cover replacement](37-api-upload-and-cover.md).
- Inference signals: epub `book-type=comic`, pre-paginated epub with one image per page, image-only pdf.

## Acceptance criteria

- [ ] A comic epub fixture and an image-only pdf are inferred as comics.
- [ ] A cbz dropped in `inbox/prose/` becomes prose.
