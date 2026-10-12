# Covers and thumbnails

Type: implementation
Status: ready-for-agent
Blocked by: 23, 24

## What to build

Spec: [§11](../spec.md#11-covers-and-thumbnails).

- Extraction per Format (epub cover-image → EPUB 2 meta → guide or first image; comic FrontCover → first image; pdf page 1 rendered with PDFBox).
- `cover.<ext>` in the Publication folder, unchanged bytes. A later File fills only a missing Cover.
- Thumbnail at import: about 300 px tall, JPEG q≈85, `cache/thumbs/<publicationId>.jpg`. Regenerated on request if missing.
- No Cover → an Import warning.
- OPDS image and thumbnail links (omitted without a Cover).

## Acceptance criteria

- [ ] Fixtures of each Format produce a Cover and a thumbnail of the expected size.
- [ ] Deleting `cache/` and requesting a thumbnail regenerates it.
- [ ] OPDS entries carry both links.
