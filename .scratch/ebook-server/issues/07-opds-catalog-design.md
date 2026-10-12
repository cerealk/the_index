# Which OPDS version, and how is the catalog structured?

Type: grilling
Status: resolved
Labels: wayfinder:grilling
Blocked by: 01

## Question

Which OPDS version(s) does the server expose, which extensions does it implement, and how is the catalog navigation structured (by Kind, Series, author, tags, collections, recently added, in progress)? How are Publications with multiple Files exposed as acquisition links?

Research says OPDS 1.2 is the version both apps share, with OpenSearch search and HTTP Basic auth. OPDS-PSE is out of scope ([Is OPDS-PSE page streaming in iteration 1?](12-page-streaming-scope.md)). Each Publication may expose several acquisition links, e.g. both the original cbr and its converted cbz.

## Answer

- **OPDS 1.2 only** (Atom XML) in iteration 1. Catalog building is kept separate from the XML serialization, so OPDS 2.0 can be added later as a second output format.
- **Navigation tree:**
  ```
  Root
  ├── Continue reading      (Publications with Reading Progress, not finished; most recent first)
  ├── Recently added
  ├── Prose
  │   ├── By author → author → Publications (Series grouped, in order)
  │   ├── By series → series → Publications in Series order
  │   └── All (A–Z by title sort)
  ├── Comics
  │   ├── By series → series → issues in number order
  │   └── All (A–Z)
  ├── Tags → tag → Publications
  ├── Collections → collection → Publications
  └── Search (OpenSearch: title, author, series)
  ```
  Long feeds are paginated (`next`/`previous`, about 50 entries per page). Entries carry a cover thumbnail link and a summary.
- **Acquisition links:** every File of a Publication is offered, with its MIME type (`application/epub+zip`, `application/pdf`, `application/vnd.comicbook+zip`, `application/vnd.comicbook-rar`). The **preferred File comes first**: cbz before cbr, epub before pdf. Files are served byte-identical.
- **No authentication.** The catalog (and kosync) are open on the LAN, and kosync accepts any credentials. Authentication, authorization and multi-tenancy are a **future effort** (user decision).

## Comments

- Superseded in part by [What metadata does a Publication carry?](11-publication-metadata-fields.md): Collections are dropped, and the root's "Collections" entry becomes **"Reading Lists"** (Reading List → Publications in list order).
