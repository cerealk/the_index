# How is search implemented?

Type: grilling
Status: resolved
Labels: wayfinder:grilling
Blocked by: 11

## Question

OPDS search (OpenSearch) covers title, author and series ([Which OPDS version, and how is the catalog structured?](07-opds-catalog-design.md)), and the stack is PostgreSQL ([Which server stack?](04-stack-choice.md)). Decide: Postgres full-text search (tsvector plus a GIN index, and which language configuration for multilingual titles) vs. trigram similarity (pg_trgm) vs. both; which further fields are searchable (tags, description, publisher); how accents and case are handled; and how the index is kept up to date on metadata edits.

Note from [What metadata does a Publication carry?](11-publication-metadata-fields.md): searchable candidates are title/sort title, Contributor names, Series name, Tags, description (sanitized HTML), publisher and identifiers (ISBN). Language is one ISO 639 code per Publication, which may help pick a text-search configuration.

## Answer

Grilled with the user on 2026-10-12.

1. **Technique:** `pg_trgm` + `unaccent` trigram similarity. No full-text search (tsvector) in iteration 1, because queries are names and titles in mixed languages. FTS on descriptions can be added later.
2. **Searchable fields:** title + sort title, Contributor names, Series name + position text, Tags, Reading List names, publisher, identifiers (ISBN, exact match). **Not** the description (too noisy under trigram matching).
3. **Matching and ranking:** the query is normalized (unaccented, lowercased, punctuation stripped), and **every query word must match** the document (`word_similarity` ≥ threshold). Results are ranked by combined similarity, with a **primary** tier (title, Series, Contributors) above a **secondary** tier (Tags, Reading Lists, publisher, identifiers).
4. **Index maintenance:** a stored search document per Publication, as two normalized text columns (primary, secondary) with trigram GIN indexes, rebuilt in Kotlin **in the same transaction** as any edit that affects it: Publication edits, Contributor rename or merge, and Series, Tag and Reading List renames (which rebuild all linked Publications). The import pipeline's "index" stage writes it for new Publications. An admin **`reindex`** action rebuilds every document.
5. **Scope:** Publications in the Trash are excluded. OPDS OpenSearch returns both Kinds, paginated like other feeds.
6. **Management API:** the same search, plus optional filters (Kind, Tag, Series, read state).
