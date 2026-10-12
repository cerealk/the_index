# How are covers and thumbnails produced and stored?

Type: grilling
Status: resolved
Labels: wayfinder:grilling

## Question

OPDS entries carry a cover and a thumbnail link ([Which OPDS version, and how is the catalog structured?](07-opds-catalog-design.md)). Extraction rules per Format are known ([What metadata can be extracted from each Format?](03-file-metadata-extraction.md)): epub cover-image, the comic's FrontCover or first image, pdf page 1 rendered with PDFBox. Decide: when they're generated (at import vs. lazily), sizes and image format, where they're stored (inside the Publication folder vs. a separate cache, given that Originals are never modified), which File's cover wins when a Publication has several, and whether the user can replace a cover.

## Answer

Grilled with the user on 2026-10-12. New glossary term in `/CONTEXT.md`: **Cover**.

1. **One Cover per Publication.** Source order: Calibre's `cover.jpg`, otherwise the first imported File's extracted cover (epub cover-image, the comic's FrontCover or first image, pdf page 1 rendered with PDFBox). A later attached File only fills a missing Cover (consistent with "fill empty fields only" in [What metadata does a Publication carry?](11-publication-metadata-fields.md)).
2. **Replaceable** by upload through the management API. Picking among the Files' extracted covers is deferred.
3. **Storage:** `cover.<ext>` inside the Publication folder, kept as found with no re-encoding. It moves with the folder on metadata edits and goes to the Trash with it. A Cover is not a File, so Originals stay untouched.
4. **Thumbnail:** generated **at import**, about 300 px tall, JPEG q≈85, stored in `cache/thumbs/<publicationId>.jpg`. Regenerated when the Cover is replaced.
5. **Cache safety:** a missing thumbnail is regenerated on request, so `cache/` can be wiped and is excluded from backups.
6. **No Cover:** the OPDS entry omits the `image`/`thumbnail` links. A missing Cover is an import warning ([How does the import pipeline work?](08-import-pipeline.md)).
