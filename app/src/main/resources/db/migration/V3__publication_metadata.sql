-- Publication metadata: Contributors and Credits, Series, Tags, identifiers (spec §3),
-- and the raw extracted metadata kept on each Import job (spec §5).

-- match_key holds the normalized form used for exact matching at import (spec §3).
CREATE TABLE contributor (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name       TEXT NOT NULL,
    sort_name  TEXT NOT NULL,
    match_key  TEXT NOT NULL UNIQUE
);

-- Volume is part of a Series' identity, so it is part of its match key.
CREATE TABLE series (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name       TEXT NOT NULL,
    volume     INT,
    match_key  TEXT NOT NULL UNIQUE
);

CREATE TABLE tag (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name       TEXT NOT NULL,
    match_key  TEXT NOT NULL UNIQUE
);

ALTER TABLE publication
    ADD COLUMN description           TEXT,
    ADD COLUMN publisher             TEXT,
    ADD COLUMN language              TEXT,
    ADD COLUMN publication_date      TEXT CHECK (publication_date ~ '^\d{4}(-\d{2}(-\d{2})?)?$'),
    ADD COLUMN series_id             BIGINT REFERENCES series (id),
    ADD COLUMN series_position_text  TEXT,
    -- Numeric prefix of the position text; NULL (sorted last) when it has none.
    ADD COLUMN series_position_sort  NUMERIC;

CREATE INDEX publication_series_idx ON publication (series_id, series_position_sort);

CREATE TABLE credit (
    publication_id  BIGINT NOT NULL REFERENCES publication (id),
    position        INT    NOT NULL,
    contributor_id  BIGINT NOT NULL REFERENCES contributor (id),
    role            TEXT   NOT NULL CHECK (role IN (
        'author', 'writer', 'penciller', 'inker', 'colorist', 'letterer',
        'cover artist', 'illustrator', 'translator', 'editor')),
    PRIMARY KEY (publication_id, position)
);

CREATE INDEX credit_contributor_idx ON credit (contributor_id);

CREATE TABLE publication_tag (
    publication_id  BIGINT NOT NULL REFERENCES publication (id),
    tag_id          BIGINT NOT NULL REFERENCES tag (id),
    PRIMARY KEY (publication_id, tag_id)
);

CREATE INDEX publication_tag_tag_idx ON publication_tag (tag_id);

CREATE TABLE identifier (
    publication_id  BIGINT NOT NULL REFERENCES publication (id),
    type            TEXT   NOT NULL,
    value           TEXT   NOT NULL,
    PRIMARY KEY (publication_id, type, value)
);

CREATE INDEX identifier_value_idx ON identifier (type, value);

ALTER TABLE import_job ADD COLUMN metadata JSONB;
