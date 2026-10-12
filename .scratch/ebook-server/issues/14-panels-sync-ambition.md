# How far should Panels progress sync go in iteration 1?

Type: grilling
Status: resolved
Labels: wayfinder:grilling

## Question

Given [Can Panels sync progress to our server?](13-panels-progress-sync.md), choose a level for iteration 1:
(a) none: Panels progress stays in Panels' cloud;
(b) two-way, by emulating Kavita's Panels API or Komga's read-progress endpoint, which needs the traffic-capture spike first and stays brittle across Panels releases.

Note: the one-way `pse:lastRead` option was removed when [Is OPDS-PSE page streaming in iteration 1?](12-page-streaming-scope.md) ruled page streaming out. Also, the research found Panels writes progress back only for streamed comics, so check whether emulation helps at all with download-only comics.

## Answer

**(a) None** (user's decision, 2026-10-12). Panels downloads comics over OPDS and keeps its progress in its own cloud. The server neither emulates Komga or Kavita nor receives progress from Panels in iteration 1. The traffic-capture spike is therefore not needed, and Reading Progress only has to serve Readest (kosync).
