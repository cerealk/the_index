# Library

A personal server that stores, organizes and serves one person's collection of ebooks and comics, and tracks their reading.

## Language

**Publication**:
A single readable work in the library, whether prose or comic.
_Avoid_: Book (for the general concept), Title, Item, Entry

**Kind**:
What a Publication is: prose or comic. Independent of its Format.
_Avoid_: Type, Category

**File**:
One stored file belonging to a Publication, in one Format. A Publication may have several Files.
_Avoid_: Edition, Copy, Asset

**Original**:
A File exactly as it was imported. Never modified or deleted by the server.
_Avoid_: Source file, Raw file

**Conversion**:
A File the server derived from an Original in another Format (e.g. cbz from cbr), stored alongside it in the same Publication.
_Avoid_: Copy, Derivative

**Possible Duplicate**:
A flag on a Publication that weakly matches another one, awaiting the reader's decision to merge or dismiss.

**Import**:
One incoming file's journey into the library, from arrival (inbox or upload) to an outcome: done, skipped as an exact duplicate, or failed. Every Import is kept as history.
_Avoid_: Ingest, Upload (for the general concept)

**Calibre Import**:
The one-time migration of a Calibre library found in the inbox. It groups one Import per supported file and leaves the Calibre library untouched.
_Avoid_: Migration, Sync

**Trash**:
Where deleted Publications and Files wait, restorable, until the reader explicitly empties it.
_Avoid_: Recycle bin, Archive

**Cover**:
The one image that represents a Publication, taken from Calibre or a File at import, or uploaded by the reader. Not a File.
_Avoid_: Thumbnail (a smaller rendition of the Cover), Artwork

**Contributor**:
A person credited on Publications, with one name and one sort name, shared across the library.
_Avoid_: Author (for the general concept), Person, Creator

**Credit**:
A Contributor's part in one Publication, in a given role. A Publication's Credits are ordered.
_Avoid_: Authorship, Byline

**Format**:
How a File is encoded: epub, pdf, cbz or cbr.
_Avoid_: Kind, Type, Extension

**Series**:
An ordered grouping of Publications, of either Kind.

**Tag**:
A free label on a Publication, unordered; a Publication can carry any number.
_Avoid_: Collection, Category, Genre (as a separate concept), Shelf

**Reading List**:
A named, ordered sequence of Publications to read in that order, which may cut across Series (e.g. a crossover event or a story arc). A Publication can be in any number of Reading Lists.
_Avoid_: Story Arc (it's one use of a Reading List), Playlist, Reading Order

**Reading Progress**:
How far the reader has got in a Publication, tracked per File content: it can exist before the File is imported and outlives the File while it sits in the Trash. A File is read once its progress reaches the read threshold.
_Avoid_: Bookmark, Position
