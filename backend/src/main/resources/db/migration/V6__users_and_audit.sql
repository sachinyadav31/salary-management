-- HR users who can sign in. Passwords are BCrypt hashes (never plain text).
CREATE TABLE app_user (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    email         TEXT    NOT NULL UNIQUE,
    full_name     TEXT    NOT NULL,
    password_hash TEXT    NOT NULL,
    enabled       INTEGER NOT NULL DEFAULT 1,
    created_at    TEXT    NOT NULL
);

-- Demo accounts for reviewers (password shown on the login page: PayAtlas!2026).
-- A real deployment would use company SSO or an invite flow, never seeded known passwords.
INSERT INTO app_user (email, full_name, password_hash, created_at) VALUES
    ('hr.admin@acme.example', 'Priya Sharma', '$2y$10$ZZUVxWKPkt9OjWFQLwyFWubBAWhHHNoJHUQRHgUJRqH2SzJqisnpi', '2026-10-17T00:00:00Z'),
    ('alex.morgan@acme.example', 'Alex Morgan', '$2y$10$QvoaCAvi.P6zcO8fnnpTS.6FPQeJ/w2xMEPjFUuKRy0gsN2gYVPKu', '2026-10-17T00:00:00Z');

-- Audit: who recorded each salary change and who last edited each employee. NULL = system (seed, rollover).
ALTER TABLE salary_record ADD COLUMN recorded_by_user_id INTEGER REFERENCES app_user (id);
ALTER TABLE employee ADD COLUMN updated_by_user_id INTEGER REFERENCES app_user (id);
