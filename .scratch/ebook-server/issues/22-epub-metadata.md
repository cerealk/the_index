# epub metadata extraction, Contributors, Series and Tags

Type: implementation
Status: ready-for-agent
Blocked by: 20

## What to build

Extract real metadata from epub and introduce the shared people/series/tag model. Spec: [§3](../spec.md#3-domain-model), [§5 stage 3](../spec.md#5-import-pipeline).

- Schema: Contributor, Credit (ordered, fixed role set), Series (name + volume identity), Series position (text + sort key), Tags, identifiers, description, publisher, language, partial publication date, sort title.
- epub OPF extraction (zip + jsoup XML): DC fields, EPUB 3 refines (`file-as`, `role`) and EPUB 2 attributes, series from `belongs-to-collection` then `calibre:series`, `dc:subject` → Tags, HTML description sanitized.
- Contributor matching: normalized exact match, otherwise create. Sort name from `file-as`, otherwise derived. Role mapping from MARC relators, with a missing role defaulting to author/writer.
- Fallbacks: filename parser, then file name as title, with warnings on the job. A file that can't be opened → failed.
- Raw metadata snapshot stored on the Import job.
- The layout path now uses the first author and Series ([§15](../spec.md#15-spec-level-defaults-accepted)).

## Acceptance criteria

- [ ] Fixture epubs (EPUB 2 and EPUB 3, with and without series) import with the expected fields, Credits and Series position.
- [ ] Two epubs by "J. R. R. Tolkien" and "Tolkien, J.R.R." share one Contributor.
- [ ] A corrupt epub yields a `failed` job.
