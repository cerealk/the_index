# Panels progress sync: what a server must emulate

Research for ticket `issues/13-panels-progress-sync.md`. Researched 2026-10-11. Builds on `reader-apps-opds-and-sync.md`.

Legend: **[V]** = verified in a primary source (link given). **[I]** = inference, not stated by a primary source.

## Primary sources

- Komga source, `gotson/komga` @ `519e352a` (2026-10-08): https://github.com/gotson/komga. Paths below are relative to `komga/src/main/kotlin/org/gotson/komga/`.
- Kavita source, `Kareadita/Kavita` @ `3233c26d` (2026-10-10): https://github.com/Kareadita/Kavita
- Kavita PRs that added a Panels-only API: #2362 "Prep for Hotfix" (merged 2023-10-29), https://github.com/Kareadita/Kavita/pull/2362, and #2367 "Pipeline + Panels Tweaks" ("Changed the Panels API per Dani's requests"), https://github.com/Kareadita/Kavita/pull/2367
- Panels developer ("dani") posts on the Panels forum:
  - https://community.panels.app/t/komga-reading-progress-sync/1714 (thread 1714)
  - https://community.panels.app/t/tracking-progress-in-opds-stream-library/1610 (thread 1610)
  - https://community.panels.app/t/support-for-komga-opds-pse/1200 (thread 1200)
  - https://community.panels.app/t/cant-resume-a-book-in-panels-that-was-started-in-komga/1677 (thread 1677)
  - https://community.panels.app/t/reading-progress-not-tracking-opds-streams/1265 (thread 1265)
- Panels guides: https://guides.panels.app/opds/komga, https://guides.panels.app/opds/kavita, https://guides.panels.app/opds/connecting-a-server
- Panels marketing pages: https://www.panels.app/komga-ios-app, https://www.panels.app/kavita-ios-app
- OPDS-PSE 1.2 spec: https://anansi-project.github.io/docs/opds-pse/specs/v1.2. The `lastReadDate` proposal by Panels: https://github.com/anansi-project/opds-pse/pull/6
- Komga docs: https://komga.org/docs/guides/opds. Komga issues #1221 and #1098: https://github.com/gotson/komga/issues/1221, https://github.com/gotson/komga/issues/1098

No public packet capture of Panels' traffic was found. The closest evidence is a user's server-log observation in thread 1714 and the Kavita code written for Panels.

---

## 1. How Panels connects and detects the server

- Every server, Komga and Kavita included, is added through the same generic form: **Library → Connect Service → OPDS**, with fields for server address, port, username and password **[V]** (guides.panels.app/opds/connecting-a-server). There is no "server type" selector **[V]**, since none of the guides mention one.
- **Komga:** you enter the bare host and port, plus your Komga username and password. "Panels finds Komga's OPDS feed on its own, so you don't need to add `/opds/v1.2/catalog`" **[V]** (guides.panels.app/opds/komga). So Panels probes known catalog paths, at least Komga's `/opds/v1.2/catalog` **[I]**.
- **Kavita:** you paste Kavita's "authorized" OPDS URL (it has the shape `…/api/opds/{apiKey}`) into Host and **leave username and password empty**. "filling these in will stop the connection working" **[V]** (guides.panels.app/opds/kavita). The route shape is verified in Kavita's `OPDSController.cs` (`[HttpGet("{apiKey}")]` under `api/[controller]`) **[V]**.
- In 2023 dani said Panels' OPDS layer "is a pure OPDS frontend. It knows nothing about the type of server or APIs. We can only work with the information in the OPDS feed" (thread 1714, 2023-02-26) **[V]**. Komga sync worked anyway, so the decision "this is Komga, call its API" has to be made from feed or URL contents, or from a probe **[I]**.
- **How Panels actually decides "Komga" or "Kavita" is not documented anywhere public** **[V: absence]**. These are the candidate signals a real server exposes:
  - Komga: every feed carries `<author><name>Komga</name><uri>https://github.com/gotson/komga</uri></author>` (`interfaces/api/opds/v1/OpdsController.kt`, `komgaAuthor`) **[V]**. Routes live under `/opds/v1.2/`, and the PSE href is `{base}/opds/v1.2/books/{bookId}/pages/{pageNumber}` **[V]**. Entry `<id>` is the raw Komga book id **[V]** (`BookDto.toOpdsEntry`).
  - Kavita: the URL path is `/api/opds/{apiKey}`. The PSE href is `{prefix}{apiKey}/image?libraryId=…&seriesId=…&volumeId=…&chapterId=…&pageNumber={pageNumber}` **[V]** (`Kavita.Services/OpdsService.cs` ~line 1281). This href carries every id that Kavita's progress DTO needs, so Panels can probably parse them from it **[I]**.
- **Base-URL derivation (Komga):** Panels once called `/api/v1/books/{id}/read-progress` on a Komga that was served under `/komga`. A user saw the PATCH requests fail in his logs, and dani shipped a fix the same day (thread 1714, 2023-03-04) **[V]**. So the API base is derived from the configured address or feed URL, and today it keeps the path prefix that comes before `/opds/` **[I]**.

## 2. Komga: endpoints Panels uses

Verified from Panels sources:
- Write: **`PATCH {base}/api/v1/books/{bookId}/read-progress`** (path and verb seen in a user's server log and acknowledged by dani; thread 1714, 2023-03-04) **[V]**.
- Panels writes "whenever the reading session is saved", which happens when the reader closes or the app is backgrounded, not on every page. The write is fire-and-forget with no retry (thread 1610, 2023-01-25; thread 1714, 2023-02-26) **[V]**.
- Read: "When you open the comic, Panels fetches the reading session from Komga and compares the last update Date. If Komga's reading session is newer…, it updates the local session" (thread 1714) **[V]**. The endpoint is not named **[V: absence]**.
- Mark read and mark unread are available from the OPDS library and are "compatible with Komga reading progress sync" (thread 1714, 2023-02-18) **[V]**.
- Auth: the same Komga username and password, over HTTP Basic **[V for credentials (panels.app/komga-ios-app); I for Basic on the API calls]**. Komga accepts HTTP Basic on `/api/**` and `/opds/**` alike (`infrastructure/security/SecurityConfiguration.kt`, `.httpBasic {}`) **[V]**.
- Sync only covers **streamed** comics. Downloaded or imported files become "just a local comic" without a server id (thread 1610, 2023-01-19) **[V as of 2023]**. Current status is unknown.

Komga's side, verified in source (`interfaces/api/rest/BookController.kt`):

| Endpoint | Body / response | Notes |
|---|---|---|
| `PATCH /api/v1/books/{bookId}/read-progress` → 204 | `{"page": Int?, "completed": Boolean?}` | `page` is **1-based** (`@Positive`). Must be present unless `completed=true`. If `completed` is omitted, Komga derives it from page vs page count **[V]** (`ReadProgressUpdateDto.kt`) |
| `DELETE /api/v1/books/{bookId}/read-progress` → 204 | none | mark unread **[V]** |
| `GET /api/v1/books/{bookId}` | `BookDto` with `readProgress: {page, completed, readDate, created, lastModified, deviceId, deviceName}`, dates formatted `yyyy-MM-dd'T'HH:mm:ss'Z'` | the only per-book REST read of progress **[V]** (`BookDto.kt`). That Panels calls this endpoint is **[I]** |
| `GET /opds/v1.2/books/{bookId}/pages/{pageNumber}` | image | PSE pages are **0-based**; Komga adds 1 internally **[V]** |
| PSE link attributes | `pse:count`, `pse:lastRead` (= Komga page, 1-based), `pse:lastReadDate` | **[V]** (`OpdsLinkPageStreaming`) |

Komga has **no Panels-specific code** **[V]** (grep for "panels" in the Komga source returns nothing).

## 3. Kavita: endpoints Panels uses (designed for Panels)

Kavita added a controller explicitly "For the Panels app". The original PR text reads: "Added 2 APIs for Panels so they can use a dedicated API for syncing progress. … Panels will no longer sync progress via OPDS-PS" (#2362). The shape was then "Changed … per Dani's requests" (#2367) **[V]**. Current code is in `Kavita.Server/Controllers/PanelsController.cs` **[V]**:

| Endpoint | Body / response |
|---|---|
| `POST /api/panels/save-progress?apiKey={key}` | JSON `ProgressDto {volumeId, chapterId, pageNum, seriesId, libraryId, bookScrollId?, lastModifiedUtc}` → 200 |
| `GET /api/panels/get-progress?chapterId={id}&apiKey={key}` | `ProgressDto`. If there is no progress, `pageNum=0` and zero ids. #2367 changed this from a bare int to the DTO at Dani's request, presumably to get `lastModifiedUtc` for the newer-wins comparison **[I]** |

- Auth is the API key from the OPDS URL, passed as a **query parameter**. Kavita's `AuthKeyAuthenticationHandler` accepts `apiKey` from the query, a header or a route value **[V]**.
- Field names are camelCase in JSON (ASP.NET Core default) **[I]**.
- `pageNum` means "pages read", 0..N **[V]** (doc comment "The number of pages read, 0 if none read", and the OPDS image handler's comment "Kavita expects 0-N").
- **Panels' User-Agent starts with `Panels`.** Kavita's OPDS page handler skips server-side progress saving when `User-Agent` starts with "Panels" (case-insensitive) **[V]** (`OPDSController.cs` `GetPageStreamedImage`). The reason is that Panels "pre-loads pages", which corrupted Kavita's page-based progress (dani, thread 1610) **[V]**.

## 4. Server → Panels without any proprietary API (standard OPDS-PSE 1.2)

- Panels proposed `lastReadDate` (merged as OPDS-PSE 1.2). Panels 2.14.2+ is listed as its client, and dani confirmed it works against Komga 0.163 (thread 1714, 2023-03-08) **[V]**.
- `lastRead` is 1-based. Pages are 0..N-1 **[V]** (PSE 1.2 spec).
- In Feb 2023, before `lastReadDate` existed, `lastRead` was used "when you open a comic on Panels for the first time, or when no API exists for the OPDS Server (like Calibre or Mylar)" (thread 1714) **[V]**. Panels finds the PSE prefix by namespace, so emit `xmlns:pse="http://vaemendis.net/opds-pse/ns"` (thread 1677) **[V]**.
- Implication: **emitting `pse:lastRead` + `pse:lastReadDate` from our stored Reading Progress gives server→Panels resume with no emulation at all** **[I, from V facts]**. It does not get Panels' progress back to us.
- Caveat: Panels' current guide says "*Continue reading* doesn't work while streaming over OPDS yet. It's waiting on OPDS 2.0" **[V]** (connecting-a-server). That refers to Panels' own Continue-reading shelf, not to per-book resume **[I]**.

## 5. Minimal emulation subset (candidate, unverified until the spike)

**Option K: look like Komga** (credentials-based, which fits a single user with HTTP Basic):
1. OPDS 1.2 catalog at `{base}/opds/v1.2/catalog`, probably with the Komga `<author>` element **[I: likely detection signal]**.
2. Book entries whose `<id>` is our File id, with a PSE link `{base}/opds/v1.2/books/{id}/pages/{pageNumber}` (0-based) plus `pse:count`/`lastRead`/`lastReadDate`.
3. `GET {base}/api/v1/books/{id}` returning enough of `BookDto` for Panels to decode `readProgress`. Which fields Panels' decoder requires is **unknown**. Swift `Codable` fails on missing non-optional keys, so the safe choice is to return the full Komga shape **[I]**.
4. `PATCH {base}/api/v1/books/{id}/read-progress` (`{page (1-based), completed}`), returning 204. Also `DELETE` the same path for unread.
5. HTTP Basic on all of these.

**Option V: look like Kavita** (API key in URL, which suits a LAN single user):
1. OPDS at `{base}/api/opds/{apiKey}`, with PSE hrefs `{base}/api/opds/{apiKey}/image?libraryId=…&seriesId=…&volumeId=…&chapterId=…&pageNumber={pageNumber}`. Integer ids are required (`int` in `ProgressDto`).
2. `POST /api/panels/save-progress?apiKey=`, which stores `pageNum` and `lastModifiedUtc` for `chapterId`.
3. `GET /api/panels/get-progress?chapterId=&apiKey=`, which returns `ProgressDto`.

This is two tiny endpoints, purpose-built for Panels and stable since 2023 **[V]**. But whether Panels detects "Kavita" from the `/api/opds/` URL, the feed, or a probe (for example `/api/plugin/version` or `/api/health`) is **unknown**.

Either way, our "chapter" or "book" maps to a **File** (CONTEXT.md: Reading Progress is per File). This only works for comics, because Panels doesn't read prose epub.

Risks **[I]**: the detection rules are private and can change in any Panels release ("We track Komga's OPDS and progress APIs as they evolve", panels.app/komga-ios-app). Emulating also means imitating another product's URL layout.

## 6. Fallback: infer progress from streamed PSE page requests

- Mechanism: the server records the highest 0-based page fetched per File and session, and derives the Reading Progress page from it.
- The evidence argues against relying on it:
  - Panels **pre-loads adjacent pages** (dani, thread 1200, 2021). Kavita did exactly this inference and found "the reading progress breaks", then special-cased Panels' User-Agent and built a dedicated API instead (thread 1610; Kavita #2362) **[V]**. Komga also removed its server-side `markread=true` PSE mode in 0.135 (thread 1200) **[V]**.
  - The prefetch depth is unknown and not configurable server-side (Komga #1098) **[V]**. Skimming or jumping looks like reading **[I]**.
  - Downloaded comics, and pages already in Panels' cache, produce no requests, so progress is undercounted or missed **[I]**.
- Possible mitigations **[I]**: subtract an estimated prefetch window, require the page to stay "current" over time (request spacing), and only mark completion when the last page plus quiet time occurs. Also identify Panels by the `Panels*` User-Agent so the heuristic only applies to it.
- Verdict: **acceptable as a best-effort "last seen around page N" hint, or as a completed flag. It is not reliable as Reading Progress.** It is still useful when emulation is not built.

## 7. The spike that settles it

Goal: record exactly what Panels sends and which response fields it needs.

1. Run real Komga (`gotson/komga` Docker) and Kavita (`jvmilazz0/kavita`) on the LAN over **plain HTTP**, so no TLS interception or CA trust on iOS is needed. Load a few cbz files.
2. Put `mitmproxy` in reverse-proxy mode in front of each, for example `mitmweb --mode reverse:http://komga:25600 -p 8081`, and point Panels at the proxy port. For a base-path test, run a second Komga with `server.servlet.context-path=/komga`.
3. Record, per server: adding the server (probe requests: which paths, any non-OPDS calls); browsing; opening a streamed comic (expect GET of progress); turning pages (prefetch depth: how many pages ahead are requested); closing the reader and backgrounding the app (expect PATCH or POST, with body); mark read/unread from the list; download a comic and read it offline, then reconnect (does progress go out?); open the same comic on a second device (newer-wins logic: which timestamp field is compared). Capture `User-Agent`, `Authorization` and every request and response body.
4. Detection and minimum-fields tests: write a mitmproxy addon or a small stub that serves a recorded Komga feed and API and strips one signal at a time. Signals to remove: the `<author>Komga`, the `/opds/v1.2` path, the `<id>` format, and `BookDto` fields one by one. Do the same for Kavita (the `/api/opds/` path, query ids). Re-add the server in Panels each time and check whether a PATCH or POST still occurs and whether resume still works. The output is the minimal emulation subset and the detection rule.
5. Effort: about half a day, with one iPhone/iPad and Panels Premium (OPDS needs Premium) **[V: Premium requirement, guides]**.

## Open questions

- The exact detection heuristic for Komga and Kavita, and the endpoint Panels uses to *read* Komga progress (spike step 3/4).
- Whether downloaded (non-streamed) comics sync today. In 2023 they did not.
- Whether Panels uses `lastReadDate` for list badges in builds after 2023.
