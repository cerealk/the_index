# Can Panels sync progress to our server by emulating part of Komga's API?

Type: research
Status: resolved
Labels: wayfinder:research

## Question

Panels writes Reading Progress back only to Komga and Kavita, using their own APIs. How does Panels detect a Komga (or Kavita) server, which endpoints does it call to read and write progress, and how small is the subset our server would have to emulate? Start from Komga's open-source API, and add any public evidence of Panels' traffic. If that isn't enough, say which hands-on spike (recording Panels' traffic against a real Komga) would answer it. Also judge the fallback of inferring progress from the pages Panels streams.

## Answer

Partly settled from public sources; the rest needs a short traffic-capture spike.

- **Connection:** Panels adds every server through the generic OPDS form, with no server-type picker. For Komga you enter the host and port plus username and password, and Panels finds `/opds/v1.2/catalog` by itself. For Kavita you paste the `/api/opds/{apiKey}` URL with no credentials. How Panels decides a server is Komga or Kavita is **not public**. Likely signals: the feed `<author>Komga</author>`, the URL layout, and PSE href shapes.
- **Komga (verified):** Panels writes with `PATCH {base}/api/v1/books/{id}/read-progress` (`{page (1-based), completed}`, HTTP Basic) when the reader closes or the app is backgrounded. It is fire-and-forget and covers streamed comics only. On open, it reads Komga's progress and keeps whichever side is newer. The read endpoint is unnamed and probably `GET /api/v1/books/{id}`.
- **Kavita (verified in Kavita source):** Kavita has a Panels-only API, `POST /api/panels/save-progress?apiKey=` and `GET /api/panels/get-progress?chapterId=&apiKey=`, with `ProgressDto` and `lastModifiedUtc`. Kavita also skips OPDS-PSE progress saving for `User-Agent: Panels*`.
- **Minimal emulation:** for Komga, the OPDS feed under `/opds/v1.2`, `GET /api/v1/books/{id}` and PATCH/DELETE read-progress. For Kavita, the `/api/opds/{apiKey}` layout plus the two Panels endpoints. Both are candidates until the spike confirms the detection rule and the required response fields.
- **Server→Panels without emulation:** emit OPDS-PSE 1.2 `pse:lastRead` and `pse:lastReadDate`. Panels supports both.
- **Inferring progress from page streams:** unreliable, because Panels pre-loads pages. Kavita tried it and abandoned it. Use it only as a best-effort hint.
- **Spike:** put mitmproxy in reverse mode in front of real Komga and Kavita over LAN HTTP, then strip one detection signal or field at a time.

Details: [../research/panels-progress-sync.md](../research/panels-progress-sync.md)
