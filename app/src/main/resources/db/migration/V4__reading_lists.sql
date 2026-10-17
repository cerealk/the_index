-- Reading Lists: named, ordered sequences of Publications that may cut across Series (spec §3).

CREATE TABLE reading_list (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name       TEXT NOT NULL,
    match_key  TEXT NOT NULL UNIQUE
);

-- position is the list order (0-based, dense). number_sort is the numeric StoryArcNumber the
-- entry arrived with, used to place later issues by number; NULL for unnumbered entries.
CREATE TABLE reading_list_entry (
    reading_list_id  BIGINT NOT NULL REFERENCES reading_list (id),
    publication_id   BIGINT NOT NULL REFERENCES publication (id),
    position         INT    NOT NULL,
    number_sort      NUMERIC,
    PRIMARY KEY (reading_list_id, publication_id)
);

CREATE INDEX reading_list_entry_order_idx ON reading_list_entry (reading_list_id, position);
CREATE INDEX reading_list_entry_publication_idx ON reading_list_entry (publication_id);
