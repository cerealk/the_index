# What does a Calibre library look like, and what maps onto our model?

Type: research
Status: resolved
Labels: wayfinder:research

## Question

Document the Calibre library layout on disk and the `metadata.db` SQLite schema (books, authors, series and series_index, tags, identifiers, formats/data, covers, custom columns, comments). How does each map onto Publication, File, Format, Series and tags in `/CONTEXT.md`? What has no mapping? Are there gotchas (author sort, title sort, multiple formats per book, `metadata.opf` sidecar files) and what's the safest way to read a library without Calibre running?

## Answer

- Layout: `<lib>/metadata.db` (SQLite) + `<Author>/<Title> (<id>)/` per book, holding one file per format (`<Title> - <Author>.<ext>`), `cover.jpg`, `metadata.opf` (lazy backup) and an optional `data/` dir. Folder/file names are ASCII-transliterated and truncated, so always resolve via `books.path` + `data.name` + `.<lower(format)>`.
- Mapping: `books` → Publication (`books.uuid` as stable key); `data` (UNIQUE book+format) → File; `data.format` → Format (only EPUB/PDF/CBZ/CBR map); `series` + `books_series_link` + `books.series_index` (REAL, max one series per book) → Series; `tags` → tags; `last_read_positions` (per book+format, `pos_frac`) → Reading Progress per File.
- No mapping: Kind (calibre has no prose/comic concept; must be inferred), non-supported formats (MOBI, AZW3, ...), authors/sorts, publisher, languages, identifiers, rating, comments (HTML), cover, custom columns (dynamic `custom_column_N` tables), annotations, notes, extra files.
- Gotchas: sort fields are stored and hand-editable (read, don't recompute); author order = link-row id; triggers and views call calibre-registered SQL functions (`title_sort`, `uuid4`, `sortconcat`, `concat`), so plain SQLite can read base tables but not the `meta`/`tag_browser_*` views and cannot write; schema varies by version (`PRAGMA user_version`).
- Safe read: do what calibre does for read-only access, which is to copy `metadata.db` to a temp dir and open the copy with `mode=ro`, preferably while calibre is closed. Then query base tables only and copy the book files into our own storage.

Details and sources: [../research/calibre-library-structure.md](../research/calibre-library-structure.md)
