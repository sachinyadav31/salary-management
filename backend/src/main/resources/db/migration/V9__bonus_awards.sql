-- One-off bonus awards (performance payout, spot, sign-on, retention). Append-only; never changes base salary.
CREATE TABLE bonus_award (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    employee_id         INTEGER NOT NULL REFERENCES employee (id),
    type                TEXT    NOT NULL,
    amount_minor        INTEGER NOT NULL CHECK (amount_minor > 0),
    currency_code       TEXT    NOT NULL,
    awarded_on          TEXT    NOT NULL,
    note                TEXT,
    recorded_by_user_id INTEGER REFERENCES app_user (id),
    created_at          TEXT    NOT NULL
);

CREATE INDEX idx_bonus_award_employee ON bonus_award (employee_id, awarded_on DESC);
CREATE INDEX idx_bonus_award_date ON bonus_award (awarded_on);
