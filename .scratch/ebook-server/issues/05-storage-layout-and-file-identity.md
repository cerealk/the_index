# How are Files stored and identified, and how are duplicates detected?

Type: grilling
Status: resolved
Labels: wayfinder:grilling
Blocked by: 03

## Question

Since the server owns storage: what's the on-disk layout and naming for Files? What identifies a File (content hash, which hash), and what identifies a Publication? How are duplicates detected on import: same File (identical hash), same Publication in another Format, or a probable match by metadata? What happens on a match?

Constraint: originals are never modified or deleted, and a cbr's converted cbz is stored alongside it as a second File ([Is OPDS-PSE page streaming in iteration 1?](12-page-streaming-scope.md)). Decide how a converted File records which File it came from, and that duplicate detection recognizes a re-imported original.

## Answer

- **Layout: human-readable, per Kind.** All Files of one Publication share one folder, and the folder name ends with the Publication id so it's unique:
  - prose: `prose/<Author>/<Series>/<NN> - <Title> (<id>)/<Title>.<ext>`, or `prose/<Author>/<Title> (<id>)/…` without a Series.
  - comic: `comics/<Series>/<Series> #<Number> - <Title> (<id>)/….<ext>`, or `comics/<Title> (<id>)/…` without a Series.
  - Path segments are sanitized (illegal characters, length limits, case collisions). The download filename shown to clients is set by a header and is independent of the stored path.
- **Metadata edits move files immediately**, so the folder always matches the database. Moves never change bytes, so Originals stay byte-identical. Each move is crash-safe: copy, fsync, update the database, delete the old path. A startup check reconciles any interrupted move.
- **File identity:** a surrogate `fileId`, plus **SHA-256** of the bytes (content identity and integrity) and **KOReader partial MD5** (kosync lookup for Readest). Both are computed in one pass at import.
- **Publication identity:** a surrogate id (it appears in the folder name).
- **Conversion → Original:** a Conversion records `derivedFrom` (the Original's fileId). Deleting an Original also deletes its Conversions, since they can be regenerated.
- **Duplicate detection on import:**
  - **Exact** (SHA-256 matches any stored File, including the Original of a converted cbr): skip it, report "already in library", and delete the inbox copy.
  - **Strong** (different bytes, but the same identifier such as ISBN or Calibre uuid, or the same normalized title + author for prose / series + number for comics, with the same Kind): attach automatically as a new File of the existing Publication, and log it in the import report.
  - **Weak** (fuzzy title/author): create a new Publication flagged *possible duplicate* for the user to merge or dismiss.
- **Deletion is a soft delete into a Trash.** Files move to `.trash/` and rows are marked deleted. They can be restored until the user explicitly empties the Trash. The server never permanently deletes on its own.
