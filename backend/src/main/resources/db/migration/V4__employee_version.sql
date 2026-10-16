-- Optimistic locking: every update bumps the version; stale writes are rejected with 409.
ALTER TABLE employee ADD COLUMN version INTEGER NOT NULL DEFAULT 0;
