# cbr support and cbr → cbz Conversion

Type: implementation
Status: resolved
Blocked by: 24

## What to build

Spec: [§5 stages 3 and 7](../spec.md#5-import-pipeline), [§3 File](../spec.md#3-domain-model).

- Read cbr through junrar 8.x behind an archive seam. ~~An optional `7z`/`bsdtar` fallback is configurable.~~ Dropped: no external runtime dependencies (user's decision, 2026-10-17).
- Same extraction as cbz.
- Stage 7: repack every cbr Original into a cbz Conversion (`derivedFrom`) in the same Publication folder, also when the cbr was attached by a strong match. A failed conversion is a warning.
- Idempotent: a missing Conversion is regenerated on rerun.

## Acceptance criteria

- [x] A RAR4 and a RAR5 fixture each import as a Publication with two Files (cbr Original, cbz Conversion) containing identical images.
- [x] The cbr Original's bytes are unchanged.
- [x] Both Files appear in OPDS with cbz first.

## Comments

- 2026-10-17 (user): No external runtime dependencies: junrar only, no `7z`/`bsdtar` fallback. Test with junrar's own test archives rather than generated ones.
- 2026-10-17 (agent): Done. Dependency `com.github.junrar:junrar:8.1.2` (pure Java, one jar; its only dependency is `slf4j-api`). Code in `theindex.metadata`: `RarComicArchive` (the `ComicArchive` seam for cbr), `CbzRepack` (the Conversion). `Extractors` routes cbr through `ComicExtractor`, the same as cbz. `ImportWorker` stage 7 builds the cbz in `.staging/`, hashes it, moves it next to the Original with `copyAtomically`, and records it with `Library.insertConversion` (`role = conversion`, `derivedFrom`, the Original's page count). The `Converter` is injected (default `CbzRepack::repack`) so tests can make it fail. Tests: 88/88 pass (`RarComicArchiveTest`, `CbzRepackTest`, `ConversionImportTest`).
- Fixtures: `app/src/test/resources/rar/` holds `rar4.rar`, `rar5.rar`, `rar4-solid.rar`, `rar5-solid.rar` and `rar5-encrypted-junrar.rar`, copied unchanged from junrar (commit `c831d1e`). Provenance, license and SHA-256 are in the README next to them. They contain small text files, not images, so the acceptance tests compare every entry byte for byte instead of "identical images", and the page count is 0.
- Decisions inside the ticket:
  - **Idempotent stage 7:** `ImportWorker.recover()` (startup) converts every cbr Original outside the Trash that has no Conversion. That covers a crash between stages 6 and 7 and a conversion that failed earlier. A Conversion that was copied but not recorded is overwritten, because `copyAtomically` replaces the target.
  - **Deterministic Conversions:** zip entries get a fixed 1980-01-01 timestamp, so the same cbr always gives the same cbz bytes.
  - **Conversion already in the library:** two different cbrs with the same entries give the same cbz. The second one is then skipped with the warning `cbz Conversion is already in the library as File N` (`file.sha256` is unique).
  - **Unreadable cbr → failed Import:** encrypted (`cannot open cbr: the archive is encrypted`), damaged headers, or no file entries. Without a fallback there's no "unreadable but imported" state.
  - **Entry names:** `\` becomes `/`, and absolute paths and `..` segments are dropped when writing the zip.
  - **Strong-match attach** ([Matching](27-matching.md)) isn't built yet. When it is, an attached cbr must also get its Conversion; startup recovery already catches it, but ticket 27 should call stage 7 directly.
