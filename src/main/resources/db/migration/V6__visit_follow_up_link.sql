-- Links a visit to the earlier visit whose follow-up it answers.
--
-- Without this, next_visit_date is a loose date that nothing ever closes: a follow-up stayed
-- "due" forever even after the patient had been seen. The link is also what makes "came early
-- or late" computable, and it survives a visit being inserted between two others, which
-- inferring the pair from date order does not.
ALTER TABLE visit ADD COLUMN previous_visit_id BIGINT REFERENCES visit (id);

-- A booking is answered by exactly one live visit. Soft-deleted rows are excluded, so deleting
-- the visit that answered a follow-up reopens that follow-up on its own.
CREATE UNIQUE INDEX uq_visit_answers_one_follow_up ON visit (previous_visit_id)
    WHERE previous_visit_id IS NOT NULL AND deleted_at IS NULL;

-- Walking a patient's chain, and the "is this follow-up still open" check behind the dashboard.
CREATE INDEX idx_visit_previous ON visit (previous_visit_id);

-- Visits recorded before this column existed stay unlinked, each starting its own chain.
-- Pairing them up by date order now would be guesswork and would state, as fact, follow-ups
-- that may never have happened.
