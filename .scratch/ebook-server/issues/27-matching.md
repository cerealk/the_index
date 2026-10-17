# Strong and weak matching, Possible Duplicates

Type: implementation
Status: resolved
Blocked by: 22

## What to build

Spec: [§5 stage 5](../spec.md#5-import-pipeline), [§3](../spec.md#3-domain-model).

- Strong match: same identifier (ISBN, Calibre uuid), or normalized title + author (prose) / Series + number (comic), with the same Kind → attach the File, **filling empty Publication fields only**.
- Weak match (fuzzy title/author) → a new Publication flagged Possible Duplicate of the other.
- The exact-duplicate check also matches Trashed Files ([§15](../spec.md#15-spec-level-defaults-accepted)).

## Acceptance criteria

- [x] The epub and pdf of the same book (same ISBN) end up as one Publication with two Files. The pdf's publisher fills an empty field but never overwrites.
- [x] A near-identical title creates a flagged Possible Duplicate.

## Comments

- 2026-10-17 (agent): Done. Migration `V5__possible_duplicates.sql` adds `publication.possible_duplicate_of`. Code: `theindex.publications.Matcher` (stage 5: `StrongMatch` / `WeakMatch`); `Library.attachOriginal` (fill empty fields only) and `publicationFolder`, with `insertPublication` split into per-relation helpers; `ImportWorker` stage 5 (attach, or store flagged), and the extractor is now injectable (`Extractor`, default `Extractors::extract`). Tests: 97/97 pass (`MatchingImportTest`, 9 tests).
- **pdf in the acceptance test:** pdf extraction is [ticket 23](23-pdf-metadata.md), not built yet, so `MatchingImportTest` injects the pdf's metadata (ISBN, publisher, language) through the extractor. Matching itself works on extracted metadata whatever the Format. Worth re-running the scenario with real pdf extraction once 23 lands.
- Decisions inside the ticket:
  - **Strong**, in order: (1) any identifier in common (`isbn`, `uuid`, …); (2) prose: the same title, folded like names, and a shared author Contributor (or any Credit when there are no authors); (3) comics: the same Series identity (name and volume) and number. Always the same Kind, never a Trashed Publication. The job is `done` with `reason` `attached to Publication N: same isbn …` / `same title and author` / `same Series and number`.
  - **Weak:** `pg_trgm` `similarity()` ≥ 0.6 on `lower(unaccent(…))`. Prose and comics without a Series need a similar title, and a similar author when both sides have Credits. Comics in a Series need a similar Series name, the same number and a compatible volume (equal, or one side without), so *Saga (2012) #1* and *Saga (2018) #1* are not flagged. The new Publication gets `possible_duplicate_of`, and the job the warning `possible duplicate of Publication N`. `Dune` vs `Dune Messiah` (0.38) and the same title by another author are not flagged. No trigram index: `unaccent()` isn't `IMMUTABLE`, and a scan is fine for thousands of rows.
  - **Attached Files** go into the Publication's folder, named like its first File; when that path is taken, `<name> (2).<ext>`, `(3)`…. Stage 7 uses the same rule, so an attached cbr's Conversion never overwrites a cbz already there (this is tested: the first version did overwrite it).
  - **Fill empty only:** description, publisher, language and date when null; Series and position when there is none; Credits, Tags, identifiers and Reading Lists only when the Publication has none at all. Title, sort title and Kind never change.
  - **Exact duplicates** already matched Trashed Files (`fileIdWithSha256` ignores the Trash); now covered by a test.
- **Open for [Publication editing](32-management-api-publications.md):** attaching can fill Credits or a Series on a Publication that had none, which changes its layout path (spec §4), but the folder isn't moved yet. Moves and startup reconciliation come with ticket 32, which should also reconcile such Publications.
