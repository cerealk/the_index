# Readest and Panels: OPDS support, Reading Progress sync, Formats

Research for ticket `issues/01-reader-apps-opds-and-sync.md`. Researched 2026-10-11.

Legend: **[V]** = verified directly in a primary source (link given). **[I]** = inference / my reading, not stated outright by a primary source.

Primary sources used:

- Readest source, `readest/readest` at commit `99e8168ab0d4f47edbcccd1061c1930518fd8307` (2026-10-11): https://github.com/readest/readest (paths below are relative to `apps/readest-app/src/`)
- Readest wiki, "Sync with Koreader devices": https://github.com/readest/readest/wiki/Sync-with-Koreader-devices
- KOReader sync server, `koreader/koreader-sync-server` at commit `46ef6b8`: https://github.com/koreader/koreader-sync-server (`config/routes.lua`, `app/controllers/1/syncs_controller.lua`)
- OPDS-PSE 1.1 spec: https://anansi-project.github.io/docs/opds-pse/specs/v1.1 (1.0: https://github.com/anansi-project/opds-pse/blob/master/v1.0.md)
- Panels (closed source, so only vendor statements are available): https://www.panels.app/, https://www.panels.app/opds-client, https://www.panels.app/komga-ios-app, https://www.panels.app/kavita-ios-app, App Store listing https://apps.apple.com/us/app/panels-comic-reader/id1236567663, and developer ("dani") posts on https://community.panels.app

---

## 1. Readest (open source; Tauri/Next.js; desktop, iOS, Android, web)

### OPDS

| Capability | Status | Evidence |
|---|---|---|
| OPDS 1.x (Atom XML) | yes **[V]** | `app/opds/utils/opdsUtils.ts` MIME `application/atom+xml`; parsing via `foliate-js/opds.js` (`getFeed`, `getPublication`) in `app/opds/page.tsx` |
| OPDS 2.0 (JSON, `application/opds+json`) | yes **[V]** | `opdsUtils.ts` MIME `OPDS2`, JSON feed parsing (~line 343) |
| Search: OpenSearch description (`rel="search"`, `application/opensearchdescription+xml`, `{searchTerms}`) | yes **[V]** | `opdsUtils.ts` `isSearchLink`, `normalizeOpenSearchTemplates` |
| Search: OPDS 2 templated link (RFC 6570, var `query`/`searchTerms`/`q`) | yes **[V]** | `opdsUtils.ts` `expandOPDSSearchTemplate` |
| Facets (`http://opds-spec.org/facet`) | yes **[V]** | `types/opds.ts` `REL.FACET`; facets sidebar in `app/opds/components/FeedView.tsx` |
| Groups, pagination (`first`/`previous`/`next`/`last`) | yes **[V]** | `REL.GROUP`; `FeedView.tsx` ~line 110 |
| Covers/thumbnails (`opds-spec.org/image`, `/image/thumbnail`) | yes **[V]** | `types/opds.ts` `REL.COVER`, `REL.THUMBNAIL` |
| Acquisition incl. `/open-access` | yes **[V]** | `PublicationView.tsx` ~line 208 |
| Auth: HTTP Basic (sent preemptively) and Digest (on 401 challenge) | yes **[V]** | `app/opds/utils/opdsReq.ts` `fetchWithAuth`, `createBasicAuth`, `createDigestAuth` |
| Custom headers per catalog | yes **[V]** | `OPDSCatalog.customHeaders` in `types/opds.ts` |
| OPDS Authentication for OPDS 1.x/2.0 (auth document) | not found **[I]** (no grep hits for `opds-authentication`) |
| OPDS-PSE page streaming (`http://vaemendis.net/opds-pse/stream`) | yes **[V]**: substitutes `{pageNumber}` and `{maxWidth}` (fixed at 2000) and reads `pse:count` | `services/opds/pseStream.ts`; `PublicationView.tsx` ~line 466 |
| PSE `pse:lastRead` / `lastReadDate` | declared in types, not used **[V]** (no uses outside `types/opds.ts`) |

Format detection for OPDS links uses the link `type`, with a fallback to the href extension or title (`services/opds/formats.ts`). A server should still emit correct MIME types **[V]**.

### Formats

- `SUPPORTED_BOOK_EXTS` (in `services/constants.ts`) contains: epub, mobi, azw, azw3, fb2, zip, **cbz**, **pdf**, txt, md, html, htm, mhtml, mht **[V]**.
- README: "EPUB, PDF, MOBI, KF8 (AZW3), FB2, CBZ, TXT, HTML, MD" **[V]** (https://github.com/readest/readest#features).
- **CBR is not supported** **[V]**: it is absent from the extension list and the OPDS format inference rules.

### Reading Progress sync

Readest has three independent mechanisms:

1. **Readest's own cloud (account-based)** **[V]**. It syncs progress, notes, bookmarks and book files (README "Sync across Platforms"; wiki "Option 2"). It is proprietary, and progress does not reach a self-hosted server.
2. **File-sync providers** (WebDAV, S3, Google Drive, OneDrive, iCloud) **[V]** (`services/sync/providers/`). These are library/data replication, not a progress API a server can consume. **[I]**
3. **KOReader Sync (kosync) client** **[V]** (`services/sync/KOSyncClient.ts`, `app/reader/hooks/useKOSync.ts`). The user configures any server URL. This is the only route by which Readest progress reaches a self-built server.

kosync details as implemented by Readest:

- Endpoints: `GET /users/auth`, `POST /users/create` (body `{username, password: md5(password)}`), `GET /syncs/progress/{document}`, `PUT /syncs/progress` **[V]**.
- Headers: `X-Auth-User` / `X-Auth-Key` (key = md5 of password), or HTTP Basic when "uses HTTP auth" is set. `Accept: application/vnd.koreader.v1+json` **[V]**.
- Connect logic: a 2xx response from `/users/auth` must have a JSON body, or Readest rejects the URL as "Not a KOReader Sync server". A 401 triggers auto-registration via `/users/create` **[V]**.
- PUT body: `{document, progress, percentage, device, device_id}` plus an optional `metadata: {filename, title, authors}` (KOReader 2026.05+ extension, sent only when enabled) **[V]**.
- **`document` = Readest's `book.hash` = KOReader-style partial MD5 of the file bytes** (`utils/md5.ts` `partialMD5`: 1 KiB samples at offsets `1024 << 2i` for i = -1..10). The "filename" checksum method is deprecated and falls back to the content hash **[V]**. For books opened by **PSE streaming**, the hash is `md5()` of a synthetic `pse://…` JSON string, not of a file (`services/bookService.ts` ~line 623) **[V]**, so a server cannot map it back to a File **[I]**.
- `progress` string: for fixed-layout formats (pdf, cbz) it is a page number. For reflowable epub it is a KOReader CREngine XPointer (`/body/DocFragment[N]/…`). Readest converts between CFI and XPointer. If the remote `progress` is not an XPointer (it names Kavita as an example), Readest falls back to `percentage` alone **[V]** (`useKOSync.ts`, `kosyncProgress.ts`).
- Reference server (`koreader-sync-server`) routes: `POST /users/create`, `GET /users/auth`, `PUT /syncs/progress`, `GET /syncs/progress/:document`, `GET /healthcheck` (plus `DELETE /users/me`, `PUT /users/password`). PUT stores `percentage, progress, device, device_id` and a server `timestamp`, and returns `{document, timestamp}`. GET returns `{percentage, progress, device, device_id, timestamp}` **[V]**.
- Readest wiki: kosync syncs **progress only**, not notes or files. Readest's matching method must be "File Content", which pairs with KOReader's "Binary" **[V]**.

Readest does not write progress back to Komga or Kavita APIs, and does not use OPDS-PSE `lastRead` **[V]** (no Komga/Kavita client code. The only mentions are comments about Kavita's kosync endpoint and a Komga auth quirk in `opdsReq.ts`).

---

## 2. Panels (closed source; iPhone, iPad, Mac)

There is no source code, so every claim below comes from vendor pages or developer posts.

### OPDS

| Capability | Status | Evidence |
|---|---|---|
| OPDS 1.x / 1.2 | yes **[V]** | "Panels supports all OPDS 1.2 features and soon OPDS 2.0" (panels.app); "works with any OPDS 1.x server" (panels.app/opds-client) |
| OPDS 2.0 | **not yet** **[V]** ("soon"). No release note found announcing it |
| OPDS-PSE streaming | yes **[V]** | dev "dani", 2021-12-02: "We do support [OPDS PSE]" (https://community.panels.app/t/support-for-komga-opds-pse/1200). Listed as a client implementation in the PSE 1.1 spec |
| Search | OpenSearch only **[V]**: follows `rel="search"` with `type="application/opensearchdescription+xml"`, reads the `Url` template and replaces `{searchTerms}` (dani, 2025-09-08, https://community.panels.app/t/opds-search-shows-no-query/2612) |
| Facets and sort | yes **[V]** | App Store version history: 3.12.4, "OPDS catalogs gain facet and sort option support" |
| Auth | "HTTPS, basic auth, digest auth and self signed certificates" **[V]** (panels.app/opds-client). Kavita works through an API key embedded in the URL **[V]** |
| Download for offline | yes **[V]** (series, volume or single book) |
| Tested servers | "Komga, Kavita, Calibre Content Server and Ubooquity" **[V]** (opds-client page) |

### Formats

- App Store: "CBR, CBZ, CB7, PDF and comic ePUB (not text eBook)". Version 3.11.0 adds TAR/CBT **[V]**.
- So Panels **does not read prose epub** **[V]**. Prose Publications are effectively invisible or unusable in Panels **[I]**.

### Reading Progress sync

- **Komga**: writes progress through Komga's REST progress API (not OPDS), using the Komga username and password **[V]** (panels.app/komga-ios-app). On opening a comic, Panels "fetches the reading session from Komga and compares the last update Date" (dani, 2023-02-26, https://community.panels.app/t/komga-reading-progress-sync/1714). Komga's old server-side PSE `markread=true` mechanism was explicitly *not* used (dani, 2021-12-02).
- **Kavita**: "Panels uses Kavita's progress API" **[V]** (panels.app/kavita-ios-app). In October 2023 the developer said only Komga was implemented and that other servers were "a low priority" because "Reading progress sync is not part of the OPDS spec" (https://community.panels.app/t/tracking-progress-in-opds-stream-library/1610). The Kavita support therefore came later **[I]**.
- **Any other OPDS server** (e.g. Calibre): "Calibre has no progress API, so Panels syncs progress across your Apple devices through its own servers with a free account" **[V]** (panels.app/opds-client). Progress stays in Panels' cloud and **never reaches the OPDS server** **[V]**.
- Timing: progress is sent when the reader closes or the app goes to the background, not on every page (dani, thread 1610). This contradicts the marketing claim of "every page turn" **[V for both statements; actual behaviour unverified]**.
- dani proposed (2023-03-05) adding a `lastReadUpdate` attribute to OPDS-PSE so that list views could show progress without per-book API calls. PSE 1.1 contains `lastRead` and `lastReadDate` **[V spec]**. Whether Panels uses `pse:lastRead` from a feed is **unknown**.
- **How Panels decides a server is Komga or Kavita is not documented** **[I]**. Possibilities include URL shape (`/opds/v1.2`, Kavita's `/api/opds/{apiKey}`) or feed contents.

---

## 3. What a self-built server must implement

### Both apps: browse and download (required)

- **OPDS 1.2 Atom** catalog. This is the only version Panels speaks. Readest reads it too, so OPDS 2.0 is optional **[V]**.
  - Navigation feeds, acquisition feeds with `http://opds-spec.org/acquisition` links and **correct MIME types**: `application/epub+zip`, `application/pdf`, `application/vnd.comicbook+zip` (cbz), `application/vnd.comicbook-rar` (cbr).
  - `opds-spec.org/image` and `/image/thumbnail` covers; `next`/`previous` pagination.
  - **OpenSearch description document** linked with `rel="search"`, `type="application/opensearchdescription+xml"` and a `{searchTerms}` template. This is the one search form both apps understand **[V]**.
  - Facets (`rel="http://opds-spec.org/facet"`) are optional and used by both.
- **HTTP Basic auth** over HTTPS (or plain HTTP on the LAN). Both apps support it; Readest sends it preemptively. Digest is unnecessary. Note that Panels and Readest accept self-signed certificates **[V]**.

### Comics streaming (recommended for Panels; usable in Readest)

- **OPDS-PSE 1.1** link per cbz/cbr/pdf entry: `rel="http://vaemendis.net/opds-pse/stream"`, `type="image/jpeg"` (or png/gif), `href` with `{pageNumber}` (0-based) and optional `{maxWidth}`, and `pse:count`. Optionally add `pse:lastRead` and `pse:lastReadDate`, filled from stored Reading Progress.
- Requires server-side page extraction from cbz, cbr (RAR) and pdf (rasterising) **[I]**.

### Reading Progress sync

- **Readest → implement the kosync API** (`/users/auth`, `/users/create` (can return 402 or a no-op for a single user), `GET/PUT /syncs/progress`, `/healthcheck`) with `X-Auth-User`/`X-Auth-Key` auth. Compute the **KOReader partial MD5 of every stored File** at ingest, so that `document` maps to a File (Reading Progress is per File, which matches) **[I, derived from V facts]**.
  - Consequences: the server must serve byte-identical files (no on-the-fly metadata rewriting of epubs), or the hash will not match. Progress for PSE-streamed books in Readest cannot be mapped **[I]**.
  - Treat `progress` as an opaque string (XPointer for epub, page number for pdf/cbz) and keep `percentage` as the portable value. The same endpoint also serves KOReader devices for free **[I]**.
- **Panels → no standard route.** The options are:
  1. Accept that Panels syncs progress only through its own cloud. The server then never sees Panels progress **[V]**.
  2. Emulate enough of the **Komga** (or Kavita) REST API for Panels to treat the server as Komga and write progress back. This is undocumented by Panels, so it needs a spike that captures Panels' traffic against a real Komga instance, and it carries a brittleness risk **[I]**.
  3. Infer progress server-side from PSE page requests (e.g. highest page fetched). Panels does not report progress this way, but it is possible because the server serves every page **[I]**. This works for streamed reading only, not for downloaded files.
- Cross-app progress (e.g. a comic read in Readest and then in Panels) is only possible if the server is the source of truth for both. That requires option 2 or 3 for Panels **[I]**.

### Format coverage summary

| Format | Readest | Panels |
|---|---|---|
| epub (prose) | yes | no (comic epub only) |
| pdf | yes | yes |
| cbz | yes | yes |
| cbr | **no** | yes |

So a cbr-only comic cannot be read in Readest. Consider serving PSE pages (format-agnostic) or offering cbr→cbz conversion as an extra File **[I]**.

## Open questions

- Which endpoints and detection heuristics Panels uses for Komga/Kavita progress write-back (needs a traffic-capture spike).
- Whether Panels reads `pse:lastRead` from feeds.
- Whether Readest's kosync works with PSE-streamed comics in any meaningful way (the evidence says no).
