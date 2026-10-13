# epub metadata extraction, Contributors, Series and Tags

Type: implementation
Status: resolved
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

- [x] Fixture epubs (EPUB 2 and EPUB 3, with and without series) import with the expected fields, Credits and Series position.
- [x] Two epubs by "J. R. R. Tolkien" and "Tolkien, J.R.R." share one Contributor.
- [x] A corrupt epub yields a `failed` job.

## Comments

- 2026-10-13 (agent): Done. Migration `V3__publication_metadata.sql`: `contributor`, `series` and `tag` with a unique `match_key`; `credit` (ordered, role check); `publication_tag`; `identifier`; new Publication columns (description, publisher, language, partial `publication_date`, Series + position text + numeric sort key); `import_job.metadata` (JSONB snapshot, exposed in `GET /api/v1/imports`). Code: `theindex.metadata` (`EpubExtractor`, `ExtractedMetadata`, normalizers), `theindex.publications` (`Names`, `Role`, `Library.insertPublication` / `layoutNames`), `theindex.imports.Drafts`. Folder layout per spec §4 and §15: an existing Contributor's or Series' spelling names the folder, and positions are padded to two digits. Tests: 53/53 pass (`EpubExtractorTest`, `NamesTest`, `MetadataImportTest`, updated `StorageTest`).
- Checked against real epubs (calibre's quick-start guides, Italian retail and calibre-converted novels) in a throwaway Compose project. Two fixes came from it: (1) calibre writes a row number *or* a uuid under the `calibre` scheme, and sometimes junk in `uuid_id`, so **files never produce the `calibre` identifier type**. The Calibre identity comes from `metadata.db` only ([Calibre Import](38-calibre-import.md)), and `uuid` values must be well-formed. (2) "Unknown" (calibre's placeholder) and similar names are not Contributors. A bare, valid ISBN-13 is recognised as `isbn`.
- Decisions inside the ticket: the **prose filename parser is minimal**: title = file name with underscores read as spaces, because "Author - Title" and calibre's "Title - Author" can't be told apart. The pattern-based parser comes with [comic metadata](24-comic-metadata-cbz.md). Sort titles move leading articles for en/it/fr/es/de, and use English rules when the language is unknown. A `dc:contributor` is kept only in a role we map (calibre lists itself as `bkp`), while a `dc:creator` with an unmapped role gets the Kind's default role. DRM (`rights.xml`, or `encryption.xml` other than font obfuscation) → failed "epub is encrypted (DRM)".
- Beyond scope: OPDS entries now carry `<author>` from author/writer Credits.
