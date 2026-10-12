# File metadata extraction per Format, and JVM libraries

Research for ticket `issues/03-file-metadata-extraction.md`. Researched 2026-10-11.

Legend: **[V]** = verified directly in a primary source (link given). **[I]** = inference / my reading / common experience, not stated outright by a primary source.

Primary sources used:

- EPUB 3.3 (W3C Rec): https://www.w3.org/TR/epub-33/ (sections 5.5 metadata, 5.6.2.1 / D.6.1 `cover-image`, 5.9 legacy features, 8.2.2.1 `rendition:layout`, D.3.3/D.3.4/D.3.7 collection properties)
- OPF 2.0.1: https://idpf.org/epub/20/spec/OPF_2.0.1_draft.htm
- calibre OPF3 reader/writer: https://github.com/kovidgoyal/calibre/blob/master/src/calibre/ebooks/metadata/opf3.py ; OPF2: https://github.com/kovidgoyal/calibre/blob/master/src/calibre/ebooks/metadata/opf2.py ; PDF XMP: https://github.com/kovidgoyal/calibre/blob/master/src/calibre/ebooks/metadata/xmp.py
- PDF 2.0 Info-dict deprecation: https://www.pdflib.com/pdf-knowledge-base/pdf-20/deprecated-features/ (secondary summary of ISO 32000-2, which is paywalled)
- Apache PDFBox: https://pdfbox.apache.org/ , migration: https://pdfbox.apache.org/3.0/migration.html
- ComicInfo schema (Anansi project): https://github.com/anansi-project/comicinfo , docs: https://anansi-project.github.io/docs/comicinfo/documentation
- ComicBookInfo: https://github.com/iComics/comicbookinfo (export of code.google.com/p/comicbookinfo), reference implementation https://github.com/davide-romanini/comicapi/blob/master/comicbookinfo.py
- comicbox schema overview: https://comicbox.readthedocs.io/SCHEMAS/
- junrar: https://github.com/junrar/junrar (README, LICENSE, releases)
- 7-Zip-JBinding: https://github.com/borisbrodski/sevenzipjbinding
- Apache Commons Compress: https://commons.apache.org/proper/commons-compress/
- RAR format technote: https://www.rarlab.com/technote.htm
- libarchive release notes: https://github.com/libarchive/libarchive/wiki/ReleaseNotes
- epub4j: https://github.com/documentnode/epub4j ; epublib: https://github.com/psiegman/epublib
- Komga (Kotlin/JVM comics+ebook server, MIT) as a reference implementation: https://github.com/gotson/komga — `komga/build.gradle.kts`, `infrastructure/mediacontainer/{epub,pdf,divina}/*`, `infrastructure/metadata/epub/EpubMetadataProvider.kt`
- Kindle Comic Converter OPF output: https://github.com/ciromattia/kcc/blob/master/kindlecomicconverter/comic2ebook.py

---

## 1. EPUB

### 1.1 What is embedded

The OPF package document (located via `META-INF/container.xml`) carries a `<metadata>` block.

- Required in EPUB 3: `dc:identifier`, `dc:title`, `dc:language`. Optional DCMES: `dc:contributor`, `dc:coverage`, `dc:creator`, `dc:date`, `dc:description`, `dc:format`, `dc:publisher`, `dc:relation`, `dc:rights`, `dc:source`, `dc:subject`, `dc:type`. **[V EPUB 3.3 §5.5.3]**
- EPUB 3 refines DC elements with `<meta property="…" refines="#id">`: `file-as` (sort name), `role` (MARC relator, e.g. `aut`, `ill`), `title-type`, `display-seq`, `identifier-type`. **[V EPUB 3.3 §D.3]**
- EPUB 2 instead uses attributes on the DC elements: `opf:role`, `opf:file-as`, `opf:scheme`, and generic `<meta name content>`. **[V OPF 2.0.1]** EPUB 3.3 keeps the EPUB 2 `meta` as a legacy feature. **[V EPUB 3.3 §5.9.3]**
- `dc:type`: free text marking a "specialized type (e.g., annotations or a dictionary)". No controlled vocabulary for "comic". **[V EPUB 3.3 §5.5.3.2.6]**

**Series.** There are two conventions and real files use both:

1. EPUB 3 standard: `<meta property="belongs-to-collection" id="c1">Name</meta>` refined by `collection-type` (`series` = "a sequence of related works…", or `set`) and `group-position` (the publication's numeric position). The cardinality is "zero or more", so a book may sit in several collections. **[V EPUB 3.3 §D.3.3, D.3.4, D.3.7]**
2. calibre legacy: `<meta name="calibre:series" content="…"/>` + `<meta name="calibre:series_index" content="2.0"/>`. **[V calibre opf2.py L1583-1585]**

calibre's own reader tries `belongs-to-collection` with `collection-type == series` first, then falls back to `calibre:series` / `calibre:series_index`. The index is parsed as a float and defaults to 1.0. **[V calibre opf3.py `read_series` L805-827]** On EPUB 3 writes, calibre removes the `calibre:series*` metas and writes `belongs-to-collection` instead. **[V opf3.py `set_series` L838-845]** Komga reads only `belongs-to-collection` and `group-position` (not `calibre:series`) and does not check `collection-type`. **[V Komga EpubMetadataProvider.kt]** **Recommendation [I]:** follow calibre's precedence: `belongs-to-collection` with `collection-type=series`, then `calibre:series`. Store the index as a decimal or string, because indices like 1.5 occur.

### 1.2 Cover

Try these in order (this is Komga's order **[V EpubExtractor.getCover]**):

1. EPUB 3: the manifest `<item properties="cover-image">`. Setting it is "recommended… but OPTIONAL". **[V EPUB 3.3 §5.6.2.1]**
2. EPUB 2: `<meta name="cover" content="{manifest-id}"/>`. EPUB 3.3 confirms the OPF 2 meta "allows EPUB creators to identify a cover image for EPUB 2 reading systems". **[V EPUB 3.3 §5.9.3]**
3. A heuristic: a manifest item with id `cover-image`/`cover` (Komga), or the `<guide><reference type="cover">` href, which calibre also uses. The guide entry often points to an XHTML page, so you then have to extract its `<img>`/`<svg><image>`. **[V calibre opf2.py L598, L1246]**
4. Fallback **[I]:** the first image referenced from the first spine item.

### 1.3 Reliability [I]

Retail and calibre-processed EPUBs usually have a good title, creator(s), language, identifier (often an ISBN) and publisher. Series is the weak point: publishers rarely set it, and it shows up mostly in files that went through calibre. Watch for messy data:

- `dc:creator` written as "Last, First"
- several creators without roles
- `dc:date` in any ISO-8601 granularity (Komga tries ISO_DATE, ISO_LOCAL_DATE, ISO_DATE_TIME)
- `dc:description` containing HTML (Komga sanitises it with `Jsoup.clean`)
- `dc:subject` used as free-form tags

### 1.4 Libraries

| Option | License | Status | Notes |
|---|---|---|---|
| **Plain `java.util.zip` / Commons Compress + an XML parser (jsoup in XML mode, or StAX/DOM)** | JDK / Apache-2.0 / jsoup MIT | Commons Compress 1.28.0 released 2025-07-26 **[V]**; jsoup 1.23.2 used by Komga **[V build.gradle.kts]** | Komga does exactly this: it parses `container.xml`, then the OPF, with jsoup's `Parser.xmlParser()` and namespace-agnostic `*|metadata > *|title` selectors. **[V]** It is small, and gives full control over EPUB 2 and 3 differences. |
| epub4j (fork of epublib) | Apache-2.0 **[V]** | Pushed 2026-08-31, tags v4.2.x **[V GitHub API]** | Full read/write object model. Its model descends from epublib's EPUB 2 design **[I]**, so you would still need the raw OPF for `refines`/`belongs-to-collection`. |
| epublib | no SPDX license detected on GitHub (LGPL per its README) **[I]** | Last push 2024-01-19 **[V]** | Effectively unmaintained. Avoid. |
| Readium kotlin-toolkit | BSD-3 **[I]** | Active | An Android library (AAR) **[I]**, not a good fit for a plain JVM server. |

**Recommendation [I]:** zip + XML, as Komga does. Reading metadata only needs `container.xml` → OPF → a few XPath/CSS selects.

---

## 2. PDF

### 2.1 What is embedded

- **Document Information dictionary** (trailer `/Info`): `Title`, `Author`, `Subject`, `Keywords`, `Creator`, `Producer`, `CreationDate`, `ModDate`, `Trapped`, plus arbitrary custom keys. **[I, from ISO 32000-1 §14.3.3; PDFBox `PDDocumentInformation` exposes exactly these getters]**
- **XMP metadata stream** (catalog `/Metadata`): Dublin Core (`dc:title`, `dc:creator` as an ordered sequence, `dc:description`, `dc:subject`), `xmp:CreateDate`, `pdf:Producer`, `pdf:Keywords`, `prism:*` (e.g. ISBN/DOI), and arbitrary namespaces.
- In PDF 2.0, all Info-dict entries except `CreationDate` and `ModDate` are deprecated in favour of XMP, because keeping the two in sync was a problem. **[V pdflib.com, summarising ISO 32000-2]**
- calibre writes series into PDF XMP in its own namespaces: `calibre:series` (`http://calibre-ebook.com/xmp-namespace`) with `rdf:value`, and `calibreSI:series_index` (`http://calibre-ebook.com/xmp-namespace-series-index`). It also reads/writes ISBN etc. from `prism`/`pdfx`. **[V calibre xmp.py NS_MAP L24-42, read_series L158-172]**
- comicbox notes that PDF metadata "can embed ComicInfo.xml / MetronInfo.xml". This is a comicbox-specific convention. **[V comicbox README]**

### 2.2 Cover

PDFs have no cover concept. Render page 1 to an image. Komga does this with `PDFRenderer(pdf).renderImage(pageIndex, scale, ImageType.RGB)`, where scale = target resolution / min(cropBox w, h). **[V Komga PdfExtractor.kt]** Komga also adds `org.apache.pdfbox:jbig2-imageio` at runtime **[V build.gradle.kts]**. JBIG2 is common in scanned, bitonal PDFs, and without the plugin those pages render blank or fail **[I]**.

### 2.3 Reliability [I]

PDF metadata is the least reliable of the four Formats. `Title` is often missing or junk: the source filename, "Microsoft Word - foo.docx", or "untitled". `Author` is often a username. `Creator`/`Producer` describe software, not people. There is no standard series field outside calibre's XMP namespace. Plan for a filename-derived fallback and for manual editing.

### 2.4 Libraries

| Option | License | Status |
|---|---|---|
| **Apache PDFBox 3.0.x** (`org.apache.pdfbox:pdfbox`, plus `xmpbox` for XMP parsing) | Apache-2.0 **[V]** | 3.0.8 released 2026-07-11, 2.0.37 on 2026-07-15. Both lines are maintained. **[V pdfbox.apache.org]** Java 8 baseline; 3.0 loads with `Loader.loadPDF(...)` instead of `PDDocument.load`. **[V migration guide]** Komga uses 3.0.8. **[V]** |
| `jbig2-imageio` 3.0.5 | Apache-2.0 | Released 2026-05-20. **[V pdfbox.apache.org]** |

Alternatives such as iText (AGPL) or a PDFium/MuPDF binding (MuPDF is AGPL) are heavier or have licensing problems. PDFBox is the default choice **[I]**.

---

## 3. CBZ / CBR (comic archives)

### 3.1 What is embedded

- **ComicInfo.xml** at the archive root. It comes from ComicRack ("not developed anymore") and is now governed by the Anansi project, which Komga, Kavita and Codex support. Stable schemas are v1.0 and v2.0; v2.1 is a draft. **[V anansi-project/comicinfo README + repo tree]** comicbox calls it "the de facto standard for comic book metadata". **[V comicbox SCHEMAS]**
  - v2.0 fields **[V schema/v2.0/ComicInfo.xsd]**:
    - `Title`, `Series`, `Number` (string), `Count` (int, −1 = unknown), `Volume` (int)
    - `AlternateSeries`, `AlternateNumber`, `AlternateCount`, `SeriesGroup`, `StoryArc`
    - `Summary`, `Notes`, `Year`, `Month`, `Day`
    - Credits: `Writer`, `Penciller`, `Inker`, `Colorist`, `Letterer`, `CoverArtist`, `Editor`
    - `Publisher`, `Imprint`, `Genre`, `Web`, `PageCount`, `LanguageISO`, `Format`
    - `BlackAndWhite` (Unknown/No/Yes), `Manga` (Unknown/No/Yes/YesAndRightToLeft)
    - `Characters`, `Teams`, `Locations`, `MainCharacterOrTeam`, `ScanInformation`, `AgeRating`, `CommunityRating`, `Review`
    - `Pages/Page[@Image @Type @DoublePage @ImageSize @Key @Bookmark @ImageWidth @ImageHeight]`, where `Type` ∈ FrontCover, InnerCover, Roundup, Story, Advertisement, Editorial, Letters, Preview, BackCover, Other, Deleted.
  - The v2.1 draft adds `Translator`, `Tags`, `GTIN`, `StoryArcNumber`. **[V drafts/v2.1/ComicInfo.xsd]**
  - Semantics: `Number` is "Number of the book in the series". For `Volume`, the docs say "Volume is a notion that is specific to US Comics". `Manga=YesAndRightToLeft` also sets RTL reading direction. **[V anansi docs]**
- **MetronInfo.xml**: a newer XML schema (v1.1) meant to fix ComicInfo's deficiencies. **[V comicbox SCHEMAS]** It is niche today **[I]**.
- **ComicBookInfo** (ComicBookLover): JSON in the **ZIP (or RAR) archive comment**, shaped `{"appID":…, "lastModified":…, "ComicBookInfo/1.0":{series, title, issue, volume, numberOfIssues, publisher, credits[], genre, …}}`, at most 65535 bytes. **[V iComics/comicbookinfo; comicapi comicbookinfo.py L39-139]** comicbox: "It probably only survives because Comictagger supports writing it." **[V]**
- **CoMet** (`comet.xml`, from the defunct Comic Viewer): "old and extremely rare". **[V comicbox SCHEMAS]**
- **Filename**: in practice the most common source of series and number. comicbox ships a dedicated filename parser for this. **[V comicbox SCHEMAS]**

### 3.2 Reliability [I]

ComicInfo.xml is present only if a tagger (ComicTagger, Mylar, Komga/Kavita users, comicbox) processed the file. Retail DRM-free CBZs sometimes include it; scene scans often don't. When present, Series/Number/Year/credits are usually good. There is no Kind field, because the format assumes "comic". Expect many archives with no metadata at all, so a filename parser (`Series Name 012 (2019).cbz`, `Series v03 c021`) is a must-have fallback.

### 3.3 Cover

1. The `Pages/Page[@Type="FrontCover"]` image index from ComicInfo, if present. **[V schema has FrontCover type; I for using it to pick the cover]**
2. Otherwise the first image entry in **natural sort order** of filenames (`page2` < `page10`). Komga sorts entries with `CaseInsensitiveSimpleNaturalComparator`, and its docs say "the first page will be used for poster/thumbnail". **[V RarExtractor.kt; komga.org scan-analysis docs]**

Ignore non-image entries (ComicInfo.xml, `.nfo`, `__MACOSX/`, `Thumbs.db`) **[I]**. Komga detects each entry's media type by content (Apache Tika `tika-core`), not by extension. **[V build.gradle.kts, RarExtractor]**

### 3.4 Archive libraries

**CBZ** is plain ZIP. Use `java.util.zip.ZipFile` or Commons Compress `ZipFile`, which also reads the archive comment you need for ComicBookInfo. Commons Compress supports "ar, arj, cpio, dump, tar, 7z and zip" and **not RAR**. **[V commons.apache.org]** Some ".cbz" files are actually RAR (and vice versa), so sniff magic bytes instead of trusting the extension **[I]**:

- ZIP: `PK\x03\x04`
- RAR4: `52 61 72 21 1A 07 00`
- RAR5: `52 61 72 21 1A 07 01 00` **[V rarlab technote]**

**CBR / RAR5. The key finding:**

| Option | License | RAR5 | Status |
|---|---|---|---|
| **junrar** (`com.github.junrar:junrar`) | UnRAR license (freeware: free to use, but the code "cannot be used to re-create the RAR compression algorithm"). GitHub reports `NOASSERTION`. **[V LICENSE]** | **Yes, since v8.0.0 (2026-07-23).** README: "RAR up to v7". The 8.0.0 notes: "RAR5 archives now open and extract instead of throwing UnsupportedRarV5Exception". It adds RAR5 headers, Unpack5, filters, encryption (KDF, header + data decryption), multi-volume and RAR7 extended distances. **[V releases v8.0.0]** | Very active: 8.1.0 (2026-08-03), 8.1.1 (2026-08-31), **8.1.2 (2026-10-09)**. Recent fixes are RAR5 correctness (e.g. "extract RAR5 entries of unknown size instead of an empty file") and path-traversal/symlink hardening. **[V]** Pure Java. Komga depends on `junrar:8.1.0` and declares a `RAR_5` media type. **[V]** |
| 7-Zip-JBinding (`net.sf.sevenzipjbinding`) | LGPL-2.1, plus the unRAR restriction for RAR code **[V README badge; I for unRAR clause]** | Yes ("RAR / RAR5 ✅ extract"). **[V README]** | Revived: 23.01-2.2 released 2026-09-21, on the 7-Zip 23.01 engine, Java 8. The `-all-platforms` jar covers macOS universal, Linux glibc/musl, ARM. **[V release]** Known issue: CVE-2024-11477 (Zstandard) is not fixed in the bundled engine. **[V release notes]** Native JNI, so a crash takes down the JVM **[I]**. |
| Shell out to `unrar` | Proprietary freeware **[I]** | Yes | Reference implementation. Adds an external dependency and process management. |
| Shell out to `7z` / `bsdtar` (libarchive) | LGPL+unRAR / BSD | 7z yes. libarchive has "Read support for RAR 5.0 archives" since 3.4.0 (2019-06-11). **[V libarchive release notes]** libarchive's RAR5 reader has no encryption support **[I, not verified]**. | Mature, but also an external process. |

**Recommendation [I]:** use junrar 8.1.x as the pure-JVM CBR reader. The old "junrar can't do RAR5" limitation no longer holds as of July 2026. Its RAR5 code is about 3 months old, though, so:

1. Wrap it behind an `ArchiveReader` seam.
2. Catch `RarException` / `UnsupportedRarVersionException` and fall back to shelling out to `7z`/`bsdtar` if installed. Otherwise mark the File as "unreadable" instead of failing the import.
3. Build a small corpus of real CBRs (RAR4 + RAR5, solid + non-solid) for tests.

Komga rejects encrypted and multi-volume RARs outright **[V RarExtractor.kt]**. That is a sensible policy for a comics library.

---

## 4. Signals for inferring Kind (prose vs comic) independently of Format

There is no single authoritative flag in any Format. Candidate signals, strongest first **[I unless marked]**:

| Format | Signal | Strength |
|---|---|---|
| cbz/cbr | The Format itself (image archive) | Very strong. Practically always comic (or an art book/photo book). |
| cbz/cbr | `ComicInfo.xml` / `MetronInfo.xml` / ComicBookInfo comment present | Confirms comic. `Manga` ≠ Unknown hints manga plus reading direction. **[V field exists]** |
| epub | `<meta name="book-type" content="comic"/>` (Amazon KF8 convention). Kindle Comic Converter writes this together with `fixed-layout=true`, `original-resolution`, `primary-writing-mode`, and `rendition:layout=pre-paginated`. **[V KCC comic2ebook.py L343-370]** | Strong when present. |
| epub | `rendition:layout = pre-paginated` (globally, or on most spine items via `properties="rendition:layout-pre-paginated"`). This means "one page per spine itemref". **[V EPUB 3.3 §8.2.2.1]** | Medium. Children's picture books, cookbooks and textbooks are also fixed-layout. |
| epub | **Image-per-page structure:** every spine item is an image or an XHTML page whose body text is under a small letter threshold and which holds exactly one `<img>`/`<svg><image>`. Komga implements this as "Divina detection" with a configurable `epubDivinaLetterCountThreshold`. **[V Komga EpubExtractor.getDivinaPages]** | Strong, and the best Format-independent heuristic for EPUB. |
| epub | `spine@page-progression-direction="rtl"` | Weak by itself. It suggests manga when combined with the above; Komga maps it to reading direction. **[V]** |
| epub/pdf | `dc:subject` / XMP keywords containing "Comics & Graphic Novels" (BISAC `CGN…`), "manga" | Weak to medium, and depends on the publisher. |
| pdf | **Image-only pages:** sample N pages; PDFBox `PDFTextStripper` returns (almost) no text, and each page's resources hold one image XObject covering ≈ the full MediaBox | Medium. Also true of scanned prose books without OCR, so combine it with the next row. |
| pdf | Page aspect ratio and size consistent with comics (≈ 6.6×10.2 in US comic, B5/A5 manga), color images, high image-to-text ratio | Weak. |
| all | Library folder or user hint (e.g. imported from `/comics`), and series/filename patterns like `#012`, `v03 c021` | Practical and cheap. |

**Implication [I]:** treat Kind as a *suggested* value with a confidence, editable by the user. Default Kind = comic for cbz/cbr and prose for epub/pdf. Flip the default when the EPUB is pre-paginated + image-per-page (or `book-type=comic`), or when the PDF is image-only and comic-shaped.

---

## 5. Summary for the import pipeline [I]

1. **Sniff the format by magic bytes**, not by extension: ZIP (+ `mimetype` = `application/epub+zip` → epub), RAR4/RAR5, `%PDF-`.
2. **Extract per Format**, with this precedence:
   - epub: OPF (EPUB 3 refines, then EPUB 2 attributes, then `calibre:*` metas)
   - pdf: XMP (incl. `calibre:series`), then the Info dict
   - cbz/cbr: ComicInfo.xml, then MetronInfo, then the ComicBookInfo comment, then the filename parser
   - all: the filename/folder parser as a last resort
3. **Cover**:
   - epub: `cover-image`, then `meta name=cover`, then guide/first image
   - pdf: render page 1 with PDFBox
   - comics: ComicInfo FrontCover, then the first image in natural sort
   - Then generate thumbnails once and cache them.
4. **Libraries**: zip + jsoup/StAX for EPUB; PDFBox 3.0.x (+ xmpbox, jbig2-imageio); `java.util.zip`/Commons Compress for CBZ; junrar 8.1.x for CBR, with an optional external `7z`/`bsdtar` fallback. All are pure JVM except the optional fallback. Licenses are Apache-2.0/MIT, plus UnRAR freeware for junrar, which is fine for a personal server.
5. **Kind** is inferred (section 4) and stored as editable data, not derived from Format at read time.
6. Komga (MIT, Kotlin) has already made these exact choices and is a good code reference for edge cases.
