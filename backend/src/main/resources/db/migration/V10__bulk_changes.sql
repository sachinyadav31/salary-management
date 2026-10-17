-- Idempotency record for bulk changes: the request id is the primary key, so a double submit or a retry
-- cannot apply the same bulk change twice.
CREATE TABLE bulk_change (
    request_id         TEXT PRIMARY KEY,
    kind               TEXT    NOT NULL,
    applied            INTEGER NOT NULL,
    skipped            INTEGER NOT NULL,
    created_at         TEXT    NOT NULL,
    created_by_user_id INTEGER REFERENCES app_user (id)
);
