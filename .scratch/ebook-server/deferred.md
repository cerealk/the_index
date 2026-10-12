# Deferred features: Ebook library server

Features ruled out of iteration 1 but worth tracking for a later iteration or a separate map. Each line says where it was deferred. Not a backlog: nothing here is decided or specified.

## Separate future maps

- **Web reader**: reading in the browser. (Map: Out of scope)
- **Android/iOS apps**: own clients. (Map: Out of scope)
- **OPDS-PSE page streaming / server-side page rendering**: belongs with the client map. ([Is OPDS-PSE page streaming in iteration 1 after all?](issues/12-page-streaming-scope.md))
- **Multiple users, authentication, authorization**: iteration 1 has no auth, and kosync login always succeeds. ([Which OPDS version, and how is the catalog structured?](issues/07-opds-catalog-design.md), [What is the Reading Progress model?](issues/06-reading-progress-model.md))

## Later iterations

- **Backup and restore** of the storage volume and the Postgres database: built-in vs host tooling, consistency between file and database snapshots, restore procedure. `cache/` is regenerable and can be excluded. ([How are the library and database backed up and restored?](issues/18-backup-and-restore.md), ruled out of scope on 2026-10-12)
- **Online metadata lookup** (Open Library, Google Books, Comic Vine): fetch or enrich Publication metadata from online sources. (Ruled out of scope on 2026-10-12)
- **Panels progress sync**: emulate Komga's or Kavita's progress API, which first needs a traffic-capture spike (iOS, Panels Premium, mitmproxy). ([How far should Panels progress sync go in iteration 1?](issues/14-panels-sync-ambition.md), [Can Panels sync progress to our server?](issues/13-panels-progress-sync.md))
- **Reading history log**: an append-only log of every progress update, for reading stats. ([What is the Reading Progress model?](issues/06-reading-progress-model.md))
- **Full-text search on descriptions** (tsvector), added next to trigram search. ([How is search implemented?](issues/15-search-design.md))
- **Pick the Cover from a File's extracted cover** through the API (upload only in iteration 1). ([How are covers and thumbnails produced and stored?](issues/16-covers-and-thumbnails.md))
- **ComicInfo extras** (Characters, Teams, Locations…) as Tags, if missed. ([What metadata does a Publication carry?](issues/11-publication-metadata-fields.md))
