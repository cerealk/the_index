# Is OPDS-PSE page streaming in iteration 1 after all?

Type: grilling
Status: resolved
Labels: wayfinder:grilling

## Question

Page streaming was ruled out of scope (map → Out of scope). But [How do Readest and Panels consume OPDS and sync reading progress?](01-reader-apps-opds-and-sync.md) found both apps use OPDS-PSE to read comics without downloading them, and streaming is one way around Readest's lack of cbr support. Should iteration 1 implement OPDS-PSE (server-side page extraction from cbz/cbr/pdf), or does it stay out of scope and comics are download-only?

## Answer

- **No page streaming in iteration 1.** OPDS-PSE stays out of scope, and comics are download-only.
- **cbr is converted to cbz on import** so Readest (which can't open cbr) can read every comic. The conversion is lossless: the same images repacked from RAR into zip.
- **The original cbr is kept**: it and its cbz become two Files of the same Publication.
- **General rule (user):** "I don't want to lose the original files." The server never modifies or deletes an imported original. Converted Files are added alongside it.
- Consequence: Panels gets no server → Panels resume point (`pse:lastRead` needs PSE), so [How far should Panels progress sync go?](14-panels-sync-ambition.md) is down to none vs. emulation.
