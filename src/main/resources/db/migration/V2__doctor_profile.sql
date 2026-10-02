ALTER TABLE app_user
    ADD COLUMN full_name           VARCHAR(120),
    ADD COLUMN phone               VARCHAR(20),
    ADD COLUMN specialization      VARCHAR(100),
    ADD COLUMN registration_number VARCHAR(50),
    ADD COLUMN created_by          VARCHAR(320);
