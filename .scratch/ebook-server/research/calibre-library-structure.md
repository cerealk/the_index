# Calibre library structure and `metadata.db` schema

Research for ticket `issues/02-calibre-library-structure.md`. Researched 2026-10-11 against calibre `master` on GitHub (schema `user_version` 28 in the bundled SQL).

Legend: **[V]** = verified directly in a primary source (link given). **[I]** = inference / my reading, not stated outright by a primary source.

Primary sources used:

- Schema DDL: https://github.com/kovidgoyal/calibre/blob/master/resources/metadata_sqlite.sql
- DB backend (connection setup, custom functions, paths, custom columns): https://github.com/kovidgoyal/calibre/blob/master/src/calibre/db/backend.py
- Schema upgrades: https://github.com/kovidgoyal/calibre/blob/master/src/calibre/db/schema_upgrades.py
- In-memory tables (ordering semantics): https://github.com/kovidgoyal/calibre/blob/master/src/calibre/db/tables.py
- Constants (file/dir names): https://github.com/kovidgoyal/calibre/blob/master/src/calibre/db/constants.py
- Filename sanitising: https://github.com/kovidgoyal/calibre/blob/master/src/calibre/utils/filenames.py
- OPF writer: https://github.com/kovidgoyal/calibre/blob/master/src/calibre/ebooks/metadata/opf2.py
- Undefined date: https://github.com/kovidgoyal/calibre/blob/master/src/calibre/utils/iso8601.py
- Manual FAQ, "Library management": https://manual.calibre-ebook.com/faq.html#library-management (source: https://github.com/kovidgoyal/calibre/blob/master/manual/faq.rst)
- SQLite URI parameters: https://www.sqlite.org/uri.html

---

## 1. On-disk layout

```
<Library>/
  metadata.db                     SQLite DB, all metadata                         [V faq.rst]
  metadata_db_prefs_backup.json   backup of the `preferences` table               [V backend.py]
  full-text-search.db             FTS index (separate SQLite DB)                  [V backend.py]
  .calnotes/notes.db (+ files)    notes attached to authors/tags/series etc.     [V constants.py]
  .caltrash/                      calibre's recycle bin for deleted books/formats [V constants.py]
  <Author>/
    <Title> (<book_id>)/
      <Title> - <Author>.epub      one file per Format
      <Title> - <Author>.pdf
      cover.jpg                    the cover (always this name)                   [V constants.py]
      metadata.opf                 OPF backup of the book's metadata              [V constants.py]
      data/                        arbitrary "extra files" attached to the book   [V constants.py]
```

- **Folder name** = `construct_path_name`: `"{author}/{title} ({id})"` where author is the *first* author (in practice `authors[0]`), both run through `ascii_filename`, truncated. [V backend.py `construct_path_name`, `BOOK_ID_PATH_TEMPLATE = ' ({})'`]
- **File name** = `construct_file_name`: `"{title} - {author}"`, also ASCII-fied and truncated; extension is `'.' + format.lower()`. [V backend.py]
- `ascii_filename` transliterates to ASCII (Unidecode), replaces `?` and control chars with `_`, then strips characters illegal on any of Windows/macOS/Linux. [V filenames.py, calibre/__init__.py `sanitize_file_name`]
- Length limit: `PATH_LIMIT = 100` per component on non-Windows, 40 on Windows. [V backend.py]
- Windows-reserved author names (`CON`, `NUL`, ...) get a `w` appended. [V backend.py]
- The book id in the folder name exists so that calibre can rebuild `metadata.db` from the `metadata.opf` files and so duplicate title/author pairs don't collide. [V faq.rst]
- The manual says the folder is managed by calibre and users must not add files to it by hand; calibre renames/moves folders and files when title/author change. [V faq.rst; `update_path` in backend.py]

## 2. `metadata.db` schema (relevant tables)

All from `resources/metadata_sqlite.sql` unless noted. [V]

### Core

| Table | Columns that matter | Notes |
|---|---|---|
| `books` | `id` (AUTOINCREMENT), `title`, `sort` (title sort), `timestamp` (date added), `pubdate`, `series_index REAL NOT NULL DEFAULT 1.0`, `author_sort`, `path` (relative, `/`-separated), `uuid`, `has_cover BOOL`, `last_modified` | Older libraries may still have `isbn`, `lccn`, `flags` columns; upgrade 26 drops them. [V schema_upgrades.py] |
| `data` | `book`, `format` (e.g. `EPUB`, NOCASE), `uncompressed_size`, `name` | One row per (book, format) — `UNIQUE(book, format)`. `name` is the filename **without extension**. |
| `comments` | `book`, `text` | `UNIQUE(book)` — one description per book. |
| `identifiers` | `book`, `type` (default `'isbn'`), `val` | `UNIQUE(book, type)` — one value per scheme per book (isbn, amazon, goodreads, google, ...). |

### Entity + link tables

| Entity table | Link table | Cardinality per book |
|---|---|---|
| `authors(id, name, sort, link)` | `books_authors_link(book, author)` | many; **order = link row `id`** ([V tables.py `ManyToManyTable.selectq ... ORDER BY id`]) |
| `series(id, name, sort, link)` | `books_series_link(book, series)` | **at most one** (`UNIQUE(book)`); the index lives on `books.series_index` |
| `tags(id, name, link)` | `books_tags_link(book, tag)` | many |
| `publishers(id, name, sort, link)` | `books_publishers_link(book, publisher)` | at most one (`UNIQUE(book)`) |
| `ratings(id, rating 0..10)` | `books_ratings_link(book, rating)` | effectively one; scale is 0–10 (half-stars) |
| `languages(id, lang_code)` | `books_languages_link(book, lang_code, item_order)` | many, ordered by `item_order` |

### Reading state and other per-book data

| Table | What |
|---|---|
| `last_read_positions(book, format, user, device, cfi, epoch, pos_frac)` | calibre viewer / content-server reading position, **per format**, per user+device. `pos_frac` is 0..1. |
| `annotations(book, format, user_type, user, annot_type, annot_data JSON, ...)` + FTS5 virtual tables | highlights/bookmarks. |
| `books_pages_link(book, pages, algorithm, format, ...)` | page-count estimate (new in upgrade 26). |
| `book_storage(book, format, user_type, user, data JSON)` | viewer per-book localStorage (upgrade 27). |
| `books_plugin_data`, `conversion_options`, `feeds`, `preferences`, `library_id`, `metadata_dirtied`, `annotations_dirtied`, `custom_columns` | calibre-internal. |

### Custom columns

- Defined in `custom_columns(id, label, name, datatype, is_multiple, normalized, display JSON, ...)`. [V]
- Datatypes: `rating, text, comments, series, enumeration, datetime, int, float, bool, composite`. [V backend.py `CUSTOM_DATA_TYPES`]
- Storage is **dynamic tables** named from the column id `N`: [V backend.py `create_custom_column`, `custom_table_names`]
  - **normalized** (`text, series, enumeration, rating`): `custom_column_N(id, value, link)` + `books_custom_column_N_link(book, value[, extra REAL])`. For `series` columns, `extra` holds that column's series index.
  - **not normalized** (`datetime, comments, int, bool, float, composite`): `custom_column_N(id, book, value)` with `UNIQUE(book)`.
  - `composite` columns are templates computed at runtime; nothing useful is stored. [I, from `CompositeTable` in tables.py]
- In the UI / OPF they're addressed as `#label`. [V field_metadata.py `custom_field_prefix = '#'`]

### Triggers, views and custom SQL functions (the plain-SQLite trap)

calibre's `Connection.__init__` registers, on every connection: [V backend.py lines ~449-482]

- scalar: `title_sort`, `author_to_author_sort`, `uuid4`, `books_list_filter`
- aggregates: `sortconcat`, `sortconcat_bar`, `sortconcat_amper`, `identifiers_concat`, `concat`, `aum_sortconcat`
- collations: `PYNOCASE`, `icucollate`
- a native `sqlite_extension` and `PRAGMA foreign_keys=ON`, busy timeout 10 s.

The schema uses them as follows: [V metadata_sqlite.sql]

- `books_insert_trg` → `title_sort(NEW.title)` and `uuid4()`; `books_update_trg` → `title_sort`; `series_insert_trg`/`series_update_trg` → `title_sort`.
- View `meta` → `sortconcat`, `concat`; `tag_browser_*` views → `title_sort`.
- No table column uses the `PYNOCASE`/`icucollate` collations (only built-in `NOCASE`). [V grep of metadata_sqlite.sql]

Consequence [I, follows from SQLite semantics]: with a stock SQLite driver, **`SELECT` from base tables works fine**; selecting from `meta` or `tag_browser_*` views fails with "no such function"; any `INSERT`/`UPDATE` on `books` or `series` fails because triggers call `title_sort`. A read-only importer that only queries base tables is unaffected.

### Other format details

- Dates: undefined `pubdate` is year 101 (`0101-01-01`), i.e. "unknown". [V iso8601.py `UNDEFINED_DATE = datetime(101,1,1)`, date.py `is_date_undefined`] Stored as text timestamps with UTC offset. [I — observed convention; parse leniently]
- `comments.text` is HTML (from calibre's rich-text editor) or plain text. [I]
- `data.format` is stored uppercase (`EPUB`, `PDF`, `CBZ`, `CBR`, also `MOBI`, `AZW3`, `ORIGINAL_EPUB`, `ZIP`, ...). [I for the uppercase convention; `ORIGINAL_EPUB` is [V] referenced in `construct_file_name`]

## 3. Mapping onto our model (`CONTEXT.md`)

| Calibre | Our model | Fit |
|---|---|---|
| `books` row | **Publication** | Direct. `books.uuid` is a good stable external key; `books.id` is library-local. |
| `data` row (book, format) | **File** | Direct, one Calibre format = one File. Absolute path = `<lib>/<books.path>/<data.name>.<lower(format)>`. |
| `data.format` | **Format** | Only `EPUB`, `PDF`, `CBZ`, `CBR` map. Others (MOBI, AZW3, ORIGINAL_EPUB, TXT, ZIP, ...) have no Format. |
| *(nothing)* | **Kind** (prose/comic) | **No mapping.** Must be inferred (e.g. CBZ/CBR present ⇒ comic) or taken from a tag/custom column. [I] |
| `series` + `books_series_link` + `books.series_index` | **Series** + position | Direct. A Publication has ≤1 built-in series; index is REAL (e.g. 1.5). Custom `series`-type columns give extra series with their own index in `extra`. |
| `tags` + `books_tags_link` | tags | Direct (CONTEXT.md doesn't define Tag yet). |
| `last_read_positions` (per book **and format**) | **Reading Progress** (per File) | Shape matches (per File). `pos_frac` usable; `cfi` is EPUB-specific. Only populated if the user read in calibre's viewer/content server. |
| `authors` (+ order, `sort`) | — | Not in CONTEXT.md yet; needs a term (Author/Creator). |
| `identifiers`, `languages`, `publishers`, `ratings`, `comments`, `pubdate`, `timestamp` | — | Plain metadata; no domain term yet. |
| `cover.jpg` / `has_cover` | — | No cover concept yet. |
| custom columns | — | No mapping; open-ended user schema. |
| `annotations`, `book_storage`, `books_pages_link`, `.calnotes`, `data/` extra files, `conversion_options` | — | No mapping (out of scope). |

## 4. Gotchas

1. **Title/author sort are stored, not derived.** `books.sort`, `books.author_sort`, `authors.sort`, `series.sort` are computed by calibre with configurable tweaks (`author_sort_copy_method`, title-sort articles per language) and may be hand-edited. Read them as-is; don't try to recompute. [V faq.rst "How does calibre manage author names and sorting?"; metadata/__init__.py tweaks]
2. **`books.author_sort` ≠ `authors.sort`.** The book's author_sort is a single string for all its authors; each author also has its own sort. [V faq.rst]
3. **Author order** is the order of `books_authors_link.id`, not alphabetical. [V tables.py]
4. **Multiple formats per book** are normal; one row per format in `data`, at most one file per format. [V `UNIQUE(book, format)`]
5. **Don't reconstruct paths from title/author.** Folder/file names are ASCII-transliterated and truncated, and can lag behind metadata. Always use `books.path` + `data.name`. If the exact file is missing, calibre itself falls back to *any* file in the book folder ending in `.<fmt>` (ignoring `._*` AppleDouble files) — a robust importer should do the same. [V backend.py `format_abspath`]
6. **`books.path` uses `/`** regardless of OS. [I — `construct_path_name` returns `f'{author}/{title}{book_id}'`]
7. **`metadata.opf` sidecars are backups, possibly stale.** calibre writes them lazily from a "dirtied" queue (`metadata_dirtied` table). They contain title, creators (with `opf:file-as` = book author_sort), dc:subject tags, identifiers, `calibre:series`/`calibre:series_index`, custom columns as `calibre:user_metadata`. Treat `metadata.db` as authoritative; OPFs are a fallback (e.g. if the DB is corrupt). [V opf2.py `metadata_to_opf`; cache.py dirtied logic]
8. **Cover** is only `cover.jpg` in the book folder; `has_cover` flag tells you whether to expect it. [V constants.py, backend.py `cover_abspath`]
9. **Custom functions** break writes and views under plain SQLite (see §2). [V + I]
10. **Schema drift:** older libraries have extra/missing columns (e.g. `books.isbn`, no `books_pages_link`). Query columns explicitly and tolerate absence; check `PRAGMA user_version` and `PRAGMA application_id = 0x63616c69` ("cali") to confirm it's a calibre DB. [V schema_upgrades.py, metadata_sqlite.sql]
11. **Don't share the live library over a network FS / with concurrent access** — the manual is explicit that calibre is single-user and network drives cause corruption. [V faq.rst "I am getting errors with my calibre library on a networked drive/NAS?"]
12. **Untrusted libraries:** the manual warns never to open a calibre library from an untrusted source (security risk). Relevant if import is exposed over the LAN UI. [V faq.rst]
13. **Paths with `..`**: [I] since `books.path`/`data.name` come from a DB, validate the resolved path stays inside the library root before reading.

## 5. Safest way to read a library without calibre

What calibre itself does for read-only access: **copy `metadata.db` to a temp file and open the copy** (`read_only=True` in `DB.__init__`; also `clone_for_readonly_access`). [V backend.py]

Recommended approach for our importer [I, built on the above + SQLite docs]:

1. Prefer importing when calibre is **not running**. calibre uses the default rollback journal (no WAL setting found in backend.py), so a consistent snapshot is just the `metadata.db` file when no `metadata.db-journal` is present.
2. **Copy `metadata.db` to a temp dir** (as calibre does), then open the copy. This avoids taking any lock on the user's DB and avoids a hot-journal rollback being attempted against the original.
3. Open the copy **read-only**: SQLite URI `file:/tmp/x/metadata.db?mode=ro` (or `immutable=1` since it's a private copy). [V sqlite.org/uri.html for `mode=ro`, `immutable=1`]. With xerial `sqlite-jdbc`: `jdbc:sqlite:file:...?mode=ro` or `SQLiteConfig.setReadOnly(true)`. [I — verify against sqlite-jdbc docs at implementation time]
4. Query **base tables only** (`books`, `data`, `authors`, link tables, `series`, `tags`, `identifiers`, `comments`, `last_read_positions`, `custom_columns`/`custom_column_N`). Never use `meta` or `tag_browser_*` views. If ever needed, `title_sort`/`concat` etc. can be registered as stub functions in sqlite-jdbc (`org.sqlite.Function.create`). [I]
5. Read files from the **original library folder** using `books.path` + `data.name` + extension, read-only; copy them into our own storage rather than referencing calibre's tree (calibre renames/moves them). [I, motivated by V `update_path`]
6. Use `books.uuid` (plus `library_id.uuid`) as the dedupe key for re-imports. [I]

## 6. Open questions for the design (not answered by research)

- How to decide **Kind** for a Calibre book (format-based heuristic vs. a tag/custom column chosen by the user)?
- Import non-mapped formats (MOBI/AZW3) or skip them? (They'd need a Format value.)
- Keep authors/publisher/language/identifiers/description/cover? CONTEXT.md has no terms for them yet.
- Import `last_read_positions` into Reading Progress, or start fresh?
