-- Dates are stored as ISO-8601 TEXT (sortable, readable); money as INTEGER minor units.

CREATE TABLE country (
    code          TEXT PRIMARY KEY,          -- ISO 3166-1 alpha-2
    name          TEXT NOT NULL,
    currency_code TEXT NOT NULL              -- ISO 4217
);

CREATE TABLE fx_rate (
    currency_code TEXT PRIMARY KEY,
    usd_per_unit  TEXT NOT NULL,             -- decimal string, e.g. '0.012'
    as_of_date    TEXT NOT NULL
);

CREATE TABLE department (
    id   INTEGER PRIMARY KEY,
    name TEXT NOT NULL UNIQUE
);

CREATE TABLE employee (
    id                      INTEGER PRIMARY KEY AUTOINCREMENT,
    employee_code           TEXT    NOT NULL UNIQUE,
    first_name              TEXT    NOT NULL,
    last_name               TEXT    NOT NULL,
    email                   TEXT    NOT NULL UNIQUE,
    country_code            TEXT    NOT NULL REFERENCES country (code),
    department_id           INTEGER NOT NULL REFERENCES department (id),
    job_title               TEXT    NOT NULL,
    level                   TEXT    NOT NULL,
    hire_date               TEXT    NOT NULL,
    status                  TEXT    NOT NULL DEFAULT 'ACTIVE',
    -- Denormalised from the latest effective salary_record for fast aggregates;
    -- always updated in the same transaction as a salary change.
    current_salary_minor    INTEGER NOT NULL,
    current_salary_currency TEXT    NOT NULL,
    created_at              TEXT    NOT NULL,
    updated_at              TEXT    NOT NULL
);

CREATE INDEX idx_employee_country    ON employee (country_code, status);
CREATE INDEX idx_employee_department ON employee (department_id);
CREATE INDEX idx_employee_title      ON employee (job_title);
CREATE INDEX idx_employee_name       ON employee (last_name, first_name);

CREATE TABLE salary_record (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    employee_id    INTEGER NOT NULL REFERENCES employee (id),
    amount_minor   INTEGER NOT NULL CHECK (amount_minor > 0),
    currency_code  TEXT    NOT NULL,
    effective_date TEXT    NOT NULL,
    reason         TEXT    NOT NULL,
    note           TEXT,
    created_at     TEXT    NOT NULL
);

CREATE INDEX idx_salary_record_employee ON salary_record (employee_id, effective_date DESC);
