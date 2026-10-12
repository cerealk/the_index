# pdf metadata extraction and page count

Type: implementation
Status: ready-for-agent
Blocked by: 22

## What to build

Spec: [§5 stage 3](../spec.md#5-import-pipeline), [§3 File](../spec.md#3-domain-model).

- PDFBox 3 + xmpbox: XMP first (DC, `calibre:series`/`series_index`, `prism` ISBN), then the Info dict. Discard junk titles ("Microsoft Word - …", "untitled") in favour of the filename parser.
- Page count from the document.
- Encrypted or corrupt → failed.

## Acceptance criteria

- [ ] Fixture pdfs with XMP, Info-only and no metadata import with the expected fields and warnings.
- [ ] An encrypted pdf yields a `failed` job.
