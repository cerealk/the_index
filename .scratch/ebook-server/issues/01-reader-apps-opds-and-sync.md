# How do Readest and Panels consume OPDS and sync reading progress?

Type: research
Status: resolved
Labels: wayfinder:research

## Question

For **Readest** and **Panels** (the user's target reading apps): which OPDS versions (1.2 / 2.0) and extensions (e.g. OPDS-PSE page streaming, facets, search, authentication) does each support? How does each sync Reading Progress (KOReader kosync, Komga/Kavita APIs, a proprietary cloud, nothing)? What would a self-built server need to implement so both can browse, download, and sync progress? Note which Formats (epub, pdf, cbz, cbr) each app reads.

## Answer

- **Readest** reads OPDS 1.x (Atom) and 2.0 (JSON), OpenSearch and templated search, facets, Basic/Digest auth and OPDS-PSE streaming. It reads epub, pdf and cbz, but **not cbr**. It syncs Reading Progress via its own cloud or via **KOReader kosync** to any server URL. kosync is the only route from Readest to a self-built server, and the document key is KOReader's partial MD5 of the file bytes.
- **Panels** reads OPDS **1.2 only** (2.0 "soon"), OpenSearch search, facets and sort, Basic/Digest auth and **OPDS-PSE** streaming. It reads cbz, cbr, cb7, pdf and *comic* epub only. It writes progress back only to **Komga and Kavita via their REST APIs**. With any other OPDS server, progress goes to Panels' own cloud and never reaches the server.
- **Server minimum:** an OPDS 1.2 Atom catalog with correct MIME types, an OpenSearch descriptor, Basic auth, OPDS-PSE 1.1 stream links for comics (optionally `pse:lastRead`), and a kosync API keyed by the partial MD5 of each File. Files must be served byte-identical so the hashes match.
- **Open risk:** getting Panels progress onto the server would need Komga API emulation (undocumented; needs a traffic-capture spike) or inferring progress from PSE page fetches.

Details and sources: [../research/reader-apps-opds-and-sync.md](../research/reader-apps-opds-and-sync.md)
