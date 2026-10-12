# What metadata does a Publication carry?

Type: grilling
Status: resolved
Labels: wayfinder:grilling
Blocked by: 03

## Question

Beyond title, Kind, Series and tags: which fields does a Publication (and a File) carry in iteration 1? For example authors (ordered, with roles like writer/artist for comics, and sort names), publisher, language, identifiers (ISBN…), description, rating, cover, publication date. Which are editable, and which are glossary terms? Informed by the Calibre findings and the format-metadata research.

Note from [How does the one-time Calibre import work?](10-calibre-import.md): Calibre values fill every field decided here (Calibre wins over extraction). Its custom columns are dropped, so no free-form key/value bag is needed for them.

## Answer

Grilled with the user on 2026-10-12. New glossary terms in `/CONTEXT.md`: **Contributor**, **Credit**, **Tag**, **Reading List**.

1. **Contributor** is a shared entity (name + sort name). A Publication has **ordered Credits** (Contributor + role).
2. **Roles** are a fixed set: author, writer, penciller, inker, colorist, letterer, cover artist, illustrator, translator, editor. Source roles (MARC relators, ComicInfo credits) map onto it. A missing role defaults to author (prose) or writer (comic).
3. **Contributor matching on import:** normalized exact match (case-fold, strip accents, collapse spaces and dots, "Last, First" → "First Last"). Otherwise a new Contributor is created. No fuzzy matching. A manual **merge Contributor** action (management API) moves Credits. The sort name comes from Calibre or `file-as` when present, otherwise it's derived as "Last, First".
4. **Series position** is display text as found ("1", "1.5", "Annual 1") plus a derived decimal sort key (numeric prefix, non-numeric sorts last). Both are editable. **Volume is part of Series identity**: *Saga (2012)* and *Saga (2018)* are two Series.
5. **Reading List:** a named, ordered list of Publications that may cut across Series, with a Publication in any number of them (e.g. *Uncanny X-Men (2013)* issues in both "AvX" and "X-Men chronological"). ComicInfo `StoryArc`/`StoryArcNumber` (comma-separated, possibly several) find or create a Reading List by name at import and place the issue by number, or append it. They're editable afterwards.
6. **Collections are dropped.** Tags (unordered labels) and Reading Lists (ordered) cover them. This changes the root of [Which OPDS version, and how is the catalog structured?](07-opds-catalog-design.md): its "Collections" entry becomes **"Reading Lists"** (Reading List → Publications in list order).
7. **Publication fields:** title + sort title (from Calibre or `file-as`, otherwise derived by moving a leading article), Credits, Series + position, Tags (also from epub `dc:subject` and ComicInfo `Genre`), description (HTML sanitized to a safe subset), publisher (plain text), language (one ISO 639 code), publication date (partial: year, year-month or full), identifiers (list of type + value: isbn, calibre uuid, comicvine…, used by strong matching). **Not kept:** rating, and the ComicInfo extras (Characters, Teams, Locations, AgeRating, Imprint, Web).
8. **File fields:** Format, size, SHA-256, KOReader partial MD5, Original or Conversion (+ `derivedFrom`), **page count** (comics: counted from the archive, not trusted from ComicInfo; pdf: from the document; epub: none), stored path, import date. Embedded metadata is not kept per File. It fills the Publication once, and a raw snapshot is stored on the Import record for audit.
9. **Attaching a File** to an existing Publication (strong match) **fills empty Publication fields only** and never overwrites. Known edge case, accepted: a deliberately cleared field can be refilled by a later File.
10. **Editable:** every Publication field and Kind, through the management API. File fields are read-only.
