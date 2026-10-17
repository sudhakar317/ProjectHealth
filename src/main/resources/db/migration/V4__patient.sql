CREATE TABLE patient (
    id            BIGSERIAL PRIMARY KEY,
    full_name     VARCHAR(120) NOT NULL,
    phone         VARCHAR(20)  NOT NULL,
    sex           VARCHAR(10)  NOT NULL CHECK (sex IN ('MALE', 'FEMALE', 'OTHER')),
    date_of_birth DATE         NOT NULL,
    address       VARCHAR(300),
    notes         VARCHAR(1000),
    -- NOT NULL is the guarantee that a patient always keeps a doctor: no request can blank it.
    doctor_id     BIGINT       NOT NULL REFERENCES app_user (id),
    -- Soft delete only: medical records are never removed, just marked inactive.
    deleted_at    TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by    VARCHAR(320)
);

CREATE INDEX idx_patient_doctor ON patient (doctor_id);
CREATE INDEX idx_patient_active ON patient (deleted_at);
