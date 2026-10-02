CREATE TABLE specialization (
    id          BIGSERIAL PRIMARY KEY,
    title       VARCHAR(100) NOT NULL,
    description VARCHAR(500) NOT NULL DEFAULT '',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  VARCHAR(320)
);

CREATE UNIQUE INDEX uq_specialization_title ON specialization (lower(title));

-- carry over free-text specializations entered before this table existed
INSERT INTO specialization (title, created_by)
SELECT DISTINCT ON (lower(specialization)) specialization, 'migration'
FROM app_user
WHERE specialization IS NOT NULL;

ALTER TABLE app_user ADD COLUMN specialization_id BIGINT REFERENCES specialization (id);

UPDATE app_user u
SET specialization_id = s.id
FROM specialization s
WHERE lower(s.title) = lower(u.specialization);

ALTER TABLE app_user DROP COLUMN specialization;
