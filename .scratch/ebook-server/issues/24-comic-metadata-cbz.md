# cbz extraction: ComicInfo, filename parser, StoryArc → Reading Lists

Type: implementation
Status: resolved
Blocked by: 22

## What to build

Spec: [§3 Reading List](../spec.md#3-domain-model), [§5 stage 3](../spec.md#5-import-pipeline).

- ComicInfo.xml: Series, Number, Volume, Title, credits (Writer, Penciller, Inker, Colorist, Letterer, CoverArtist, Editor), Summary, Publisher, LanguageISO, Year/Month/Day, Genre → Tags, StoryArc/StoryArcNumber.
- A filename parser for comics without ComicInfo (series, number, volume/year, title).
- Page count = image entries in the archive (ComicInfo's PageCount is not trusted).
- Schema: Reading List with ordered entries. StoryArc finds or creates a Reading List by name and places the issue by number, or appends it.

## Acceptance criteria

- [x] Fixture cbz files with ComicInfo (including two StoryArcs) and without import with the expected Series, position text and sort key, and Credits.
- [x] `Saga (2012)` and `Saga (2018)` become two Series.
- [x] StoryArc issues land in the right Reading Lists in number order.

## Comments

- 2026-10-17 (agent): Done. Migration `V4__reading_lists.sql`: `reading_list` (unique `match_key`) and `reading_list_entry` (dense 0-based `position`, plus `number_sort`, the numeric StoryArcNumber it arrived with). Code in `theindex.metadata`: `ComicArchive` (the archive seam, with `ZipComicArchive`; cbr plugs in here in [cbr support](25-cbr-and-conversion.md)), `ComicExtractor` (ComicInfo.xml), `ComicFileName` (the filename parser). `Extractors` now returns an `Extraction` (metadata + page count), and the page count lands in `file.page_count`. `Library` joins Reading Lists, and `FormatDetector` reuses `ComicArchive.isPage`. Tests: 73/73 pass (`ComicFileNameTest`, `ComicExtractorTest`, `ComicImportTest`, plus `StorageTest` and `ImportPipelineTest` updates). Not checked against real cbz files in Compose yet.
- Decisions inside the ticket:
  - **Placing by number:** a numbered issue goes before the first entry with a higher number, otherwise at the end. Unnumbered issues are appended and stay where they are. This keeps working after a manual reorder ([Reading Lists endpoints](34-api-reading-lists.md)).
  - **Filename parser:** `[...]` groups are ignored, and a `(19xx/20xx)` year becomes the Series volume unless `v2`/`Vol. 2` is given. `Series Vol. 3` with no issue number is a collected volume with no position. Without a number, the cleaned name is the title. Numbers are normalized (`007` → `7`).
  - **ComicInfo gaps:** when ComicInfo has no Series, the Series (with its number and volume) comes from the file name; so does the title, but only when the file name gave no Series. `-1` (unset) Volume/Year are ignored. Characters, Teams and the other extras are dropped (spec §3).
  - **Untitled issues:** a comic with a Series but no title is titled `Saga #7`, with no warning, and its folder drops the repeated ` - <Title>`: `comics/Saga (2012)/Saga (2012) #7 (<id>)/`. A metadata edit that moves the folder ([Publication editing](32-management-api-publications.md)) must use the same rule: `Names.issueTitle` + `Storage.Layout.untitledIssue`.
  - Warning `no ComicInfo.xml, the file name was parsed` when only the file name was available.
