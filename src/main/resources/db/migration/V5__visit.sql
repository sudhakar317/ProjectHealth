CREATE TABLE visit (
    id              BIGSERIAL     PRIMARY KEY,
    patient_id      BIGINT        NOT NULL REFERENCES patient (id),
    -- The doctor who recorded it, kept separately from patient.doctor_id so that an admin
    -- reassigning the patient never rewrites the authorship of past visits.
    doctor_id       BIGINT        NOT NULL REFERENCES app_user (id),
    visit_date      DATE          NOT NULL,
    notes           VARCHAR(4000) NOT NULL,
    next_visit_date DATE,
    -- Soft delete only: clinical records are never removed, just marked inactive.
    deleted_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by      VARCHAR(320)
);

CREATE INDEX idx_visit_patient ON visit (patient_id, visit_date DESC);

-- Partial index: keeps the follow-ups list cheap as the table grows.
CREATE INDEX idx_visit_followup ON visit (doctor_id, next_visit_date)
    WHERE deleted_at IS NULL AND next_visit_date IS NOT NULL;
