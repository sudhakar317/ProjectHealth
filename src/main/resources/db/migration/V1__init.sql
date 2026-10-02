CREATE TABLE app_user (
    id          BIGSERIAL PRIMARY KEY,
    email       VARCHAR(320) NOT NULL,
    role        VARCHAR(20)  NOT NULL CHECK (role IN ('ADMIN', 'DOCTOR')),
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    google_sub  VARCHAR(255),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_app_user_email UNIQUE (email),
    CONSTRAINT uq_app_user_google_sub UNIQUE (google_sub),
    CONSTRAINT ck_app_user_email_lower CHECK (email = lower(email))
);

CREATE TABLE audit_log (
    id          BIGSERIAL PRIMARY KEY,
    event_type  VARCHAR(50)  NOT NULL,
    actor_email VARCHAR(320),
    details     TEXT,
    ip_address  VARCHAR(64),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_log_created_at ON audit_log (created_at);

-- audit_log is append-only: reject UPDATE and DELETE (TRUNCATE is not affected)
CREATE FUNCTION audit_log_reject_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_log is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_log_no_update_delete
    BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION audit_log_reject_mutation();
