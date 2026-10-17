-- Career history: level, title, department and status changes, append-only, with who made them.
CREATE TABLE employee_event (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    employee_id         INTEGER NOT NULL REFERENCES employee (id),
    type                TEXT    NOT NULL,
    from_value          TEXT,
    to_value            TEXT,
    occurred_at         TEXT    NOT NULL,
    recorded_by_user_id INTEGER REFERENCES app_user (id)
);

CREATE INDEX idx_employee_event_employee ON employee_event (employee_id, occurred_at DESC);
