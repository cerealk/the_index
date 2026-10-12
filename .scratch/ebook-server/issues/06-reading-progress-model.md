# What is the Reading Progress model?

Type: grilling
Status: resolved
Labels: wayfinder:grilling
Blocked by: 01, 13, 14

## Question

Given how Readest and Panels sync, what does Reading Progress record per File (position representation per Format: epub CFI/percentage, pdf page, comic page), when is a Publication "read", how are conflicts between devices resolved, and how is per-File progress shown at the Publication level?

Constraints from the research: Readest syncs over kosync, keyed by a partial-MD5 fingerprint per File, so files must be served byte-for-byte unchanged. Panels' route depends on [Can Panels sync progress to our server?](13-panels-progress-sync.md).

Note from [How does the one-time Calibre import work?](10-calibre-import.md): Calibre's last-read positions are not imported, so Reading Progress starts empty for every imported Publication.

Note from [How far should Panels progress sync go in iteration 1?](14-panels-sync-ambition.md): no Panels sync. Reading Progress comes only from Readest over kosync in iteration 1.

## Answer

Grilled with the user on 2026-10-12. The **Reading Progress** entry in `/CONTEXT.md` was updated.

1. **Source:** only Readest (and any KOReader device) through a kosync-compatible endpoint: `POST /users/create`, `GET /users/auth`, `GET /syncs/progress/{document}`, `PUT /syncs/progress`, `GET /healthcheck`. No Panels ([How far should Panels progress sync go in iteration 1?](14-panels-sync-ambition.md)). Calibre positions are not imported.
2. **Record (latest only):** one row per document holding the opaque `progress` string (an XPointer for epub, a page number for pdf/cbz, never interpreted by the server), `percentage`, `device`, `device_id` and a server `updated_at`. Each PUT overwrites it. No history log (it could be added later for reading stats).
3. **Keyed by KOReader partial MD5**, joined to File by hash rather than through a foreign key. A PUT for an unknown hash is stored as an orphan and attaches automatically when a File with that hash is imported. Progress stays with a File through the Trash and a restore, and is deleted only when the Trash is emptied.
4. **Conflicts:** last write wins, by the server's receive time (the reference kosync server's behaviour). Clients handle "remote is ahead or behind".
5. **Read state of a File:** read when `percentage` ≥ the read threshold (default 98%, configurable). No manual mark read/unread.
6. **Publication level:** *read* if any File is read, *in progress* if any File has progress below the threshold, otherwise *unread*. OPDS "Continue reading" lists in-progress Publications, newest `updated_at` first.
7. **Auth:** none. Register and auth always succeed, and `X-Auth-User`/`X-Auth-Key` are ignored, consistent with no auth in iteration 1.
