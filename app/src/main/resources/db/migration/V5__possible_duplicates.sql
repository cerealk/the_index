-- A weak match at import flags the new Publication as a Possible Duplicate of an existing one (spec §5 stage 5).
ALTER TABLE publication ADD COLUMN possible_duplicate_of BIGINT REFERENCES publication (id);

CREATE INDEX publication_possible_duplicate_idx ON publication (possible_duplicate_of) WHERE possible_duplicate_of IS NOT NULL;
