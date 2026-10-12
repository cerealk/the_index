# What metadata can be extracted from each Format, and with which JVM libraries?

Type: research
Status: resolved
Labels: wayfinder:research

## Question

For each Format (epub, pdf, cbz, cbr): what metadata is typically embedded (epub OPF/Dublin Core, calibre: meta tags, pdf Info dictionary/XMP, ComicInfo.xml in comic archives), how reliable is it, and how is a cover obtained? Which maintained JVM/Kotlin libraries read each (e.g. PDFBox, an epub parser, a RAR library that handles **RAR5**, since junrar has limits)? Include license and maintenance status. Are there signals that help infer **Kind** (prose vs comic) independently of Format?

## Answer

- **epub**: The OPF Dublin Core metadata (title, creator plus role and file-as, language, identifier/ISBN, publisher, date, description, subject) is generally good. Series is the weak spot. Read EPUB 3 `belongs-to-collection` (with `collection-type=series` and `group-position`) first, then calibre's `calibre:series`/`calibre:series_index`. Cover comes from `properties="cover-image"`, then EPUB 2 `<meta name="cover">`, then a guide or first-image heuristic. Library: plain zip + an XML parser (jsoup in XML mode, which is what Komga does). epublib is dead; epub4j works but is overkill.
- **pdf**: The Info dict and XMP metadata are the least reliable source. PDF 2.0 deprecates the Info dict in favour of XMP. calibre stores series in its own XMP namespace. Cover means rendering page 1. Library: Apache PDFBox 3.0.8 (Apache-2.0, active), plus xmpbox and jbig2-imageio.
- **cbz/cbr**: ComicInfo.xml (Anansi schema v2.0, v2.1 draft) is the de facto standard: Series/Number/Volume/Count, credits, Manga, and Pages with FrontCover. ComicBookInfo (JSON in the archive comment), MetronInfo and CoMet are rare. Many files have no metadata, so a filename parser is required. Cover is the FrontCover page, otherwise the first image in natural sort order.
- **RAR5**: junrar has supported RAR5 (and RAR7) since **v8.0.0 (2026-07-23)**. The current release is 8.1.2 (2026-10-09). It is pure Java under the UnRAR freeware license, and Komga uses it. The RAR5 code is new, so put it behind a seam with an optional `7z`/`bsdtar` fallback. 7-Zip-JBinding 23.01-2.2 (LGPL, native) is an alternative. Commons Compress has no RAR support.
- **Kind**: Default to comic for cbz/cbr and prose for epub/pdf. Flip the default when an EPUB declares `book-type=comic` (KCC), is `rendition:layout=pre-paginated` plus one image per page (Komga's "Divina" detection), or when a PDF is image-only. Store Kind as an editable, inferred value.

Details and sources: [../research/file-metadata-extraction.md](../research/file-metadata-extraction.md)
