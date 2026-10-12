# What is the management API shape?

Type: grilling
Status: resolved
Labels: wayfinder:grilling
Blocked by: 06, 16

## Question

Design the management (non-OPDS) HTTP API for iteration 1: resource layout, URL scheme, request and response shapes, error format, pagination, and how it is documented (e.g. OpenAPI). It must cover:

- Publications: list, filter, get, edit every field from [What metadata does a Publication carry?](11-publication-metadata-fields.md) (Credits, Series + position, Tags, identifiers, Kind…), and delete to the Trash.
- Contributors (edit, merge), Series, Tags, and Reading Lists (create, rename, add and remove entries, reorder).
- Possible Duplicates: list, merge, dismiss.
- The Trash: list, restore, empty.
- Imports: `POST` upload → `202` + job id, `GET /imports`, `GET /imports/{id}` ([How does the import pipeline work?](08-import-pipeline.md)), and Calibre Imports with their `retry` action ([How does the one-time Calibre import work?](10-calibre-import.md)).
- Reading Progress and cover replacement, as decided by [What is the Reading Progress model?](06-reading-progress-model.md) and [How are covers and thumbnails produced and stored?](16-covers-and-thumbnails.md).

Note from [What is the Reading Progress model?](06-reading-progress-model.md): there's no manual mark read/unread, so the management API only needs to expose Reading Progress and read state for viewing. The kosync endpoints are a separate surface with their own fixed shape.

Note from [How is search implemented?](15-search-design.md): Publication listing includes free-text search plus filters (Kind, Tag, Series, read state), and there's an admin `reindex` action.

Note from [How are covers and thumbnails produced and stored?](16-covers-and-thumbnails.md): Cover upload (replace) per Publication, which regenerates the thumbnail. There's no "pick a File's cover" action.

## Answer

Grilled with the user on 2026-10-12.

1. **Surfaces:** `/api/v1/…` (management), `/opds/…` (OPDS 1.2), `/kosync/…` (kosync, the URL configured in Readest), all on one port. No auth.
2. **Style:** resource-oriented REST + JSON. `PATCH` and `PUT` handle field and list edits. **Named operations** use `POST /resource/{id}/verb` for anything with side effects beyond the row (trash, restore, merge, dismiss, reorder, retry, reindex). Nested collections stay nouns (`/publications/{id}/files`). Folder moves caused by metadata edits stay internal to `PATCH`.
3. **Errors:** RFC 9457 Problem Details (`application/problem+json`) through Ktor `StatusPages`, with an `errors: [{field, message}]` extension for validation.
4. **Pagination:** `offset`/`limit` (default 50, max 200). List responses are `{items, total}`.
5. **Documentation:** **code-first** OpenAPI generated from the Ktor routes, served with Swagger UI.
6. **References in edits:** Contributors and Series are given by `id` **or** `name`. Names resolve with the same normalized exact matching as import ([What metadata does a Publication carry?](11-publication-metadata-fields.md)) and are created if missing. Tags are given by name. Responses embed related resources in full: Credits `{contributor: {id, name, sortName}, role}`, Series `{id, name, volume, position}`, Tags as names, Files, Cover/thumbnail URLs, Reading Progress and read state.
7. **Resources and operations:**
   - **Publications:** `GET /publications?q=&kind=&tag=&series=&readState=&offset=&limit=` (search per [How is search implemented?](15-search-design.md)), `GET/PATCH /publications/{id}`, `PUT …/credits`, `PUT …/series`, `PUT …/tags`, `PUT …/identifiers`, `PUT …/cover` (upload, regenerates the thumbnail), `POST …/trash`. Files, Reading Progress and read state are read-only.
   - **Contributors:** list, `GET/PATCH /contributors/{id}`, `POST /contributors/{id}/merge {into}`.
   - **Series:** list, `GET/PATCH /series/{id}` (name, volume).
   - **Tags:** list, `PATCH /tags/{name}` (rename), `POST /tags/{name}/merge {into}`.
   - **Reading Lists:** `POST /reading-lists`, `GET` list and one, `PATCH` (rename), `DELETE`, `POST /reading-lists/{id}/entries`, `DELETE …/entries/{publicationId}`, `POST …/reorder` (the full ordered list of Publication ids).
   - **Possible Duplicates:** list, `POST /possible-duplicates/{id}/merge`, `POST …/{id}/dismiss`.
   - **Trash:** list, `POST /trash/{id}/restore`, `POST /trash/empty`.
   - **Imports:** `POST /imports` (multipart: one or more `file` parts, optional `kind`, streamed to staging, configurable size limit, `202` + job ids), `GET /imports?state=`, `GET /imports/{id}`.
   - **Calibre Imports:** list, `GET /calibre-imports/{id}`, `POST /calibre-imports/{id}/retry`.
   - **Admin:** `POST /admin/reindex`.
