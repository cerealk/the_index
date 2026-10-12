# cbz extraction: ComicInfo, filename parser, StoryArc → Reading Lists

Type: implementation
Status: ready-for-agent
Blocked by: 22

## What to build

Spec: [§3 Reading List](../spec.md#3-domain-model), [§5 stage 3](../spec.md#5-import-pipeline).

- ComicInfo.xml: Series, Number, Volume, Title, credits (Writer, Penciller, Inker, Colorist, Letterer, CoverArtist, Editor), Summary, Publisher, LanguageISO, Year/Month/Day, Genre → Tags, StoryArc/StoryArcNumber.
- A filename parser for comics without ComicInfo (series, number, volume/year, title).
- Page count = image entries in the archive (ComicInfo's PageCount is not trusted).
- Schema: Reading List with ordered entries. StoryArc finds or creates a Reading List by name and places the issue by number, or appends it.

## Acceptance criteria

- [ ] Fixture cbz files with ComicInfo (including two StoryArcs) and without import with the expected Series, position text and sort key, and Credits.
- [ ] `Saga (2012)` and `Saga (2018)` become two Series.
- [ ] StoryArc issues land in the right Reading Lists in number order.
