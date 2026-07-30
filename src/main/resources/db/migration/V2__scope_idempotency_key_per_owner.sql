ALTER TABLE jobs DROP CONSTRAINT uq_idempotency_key;
ALTER TABLE jobs ADD CONSTRAINT uq_owner_idempotency_key UNIQUE (owner_id, idempotency_key);