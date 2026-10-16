-- Pay bands per country x level: midpoint = country L1 base x level multiplier; min/max = midpoint -/+ 20%.
-- Bands are reference data managed by migration in v1 (an edit UI would need its own history/approvals).

CREATE TABLE pay_band (
    country_code  TEXT    NOT NULL REFERENCES country (code),
    level         TEXT    NOT NULL,
    currency_code TEXT    NOT NULL,
    min_minor     INTEGER NOT NULL,
    mid_minor     INTEGER NOT NULL,
    max_minor     INTEGER NOT NULL,
    PRIMARY KEY (country_code, level),
    CHECK (0 < min_minor AND min_minor <= mid_minor AND mid_minor <= max_minor)
);

WITH base (country_code, currency_code, l1_major, minor_per_major) AS (
    VALUES ('US', 'USD', 70000, 100), ('IN', 'INR', 800000, 100), ('GB', 'GBP', 35000, 100),
           ('DE', 'EUR', 48000, 100), ('CA', 'CAD', 65000, 100), ('SG', 'SGD', 55000, 100),
           ('AU', 'AUD', 75000, 100), ('JP', 'JPY', 4500000, 1)
),
level_multiplier (level, multiplier_pct) AS (
    VALUES ('L1', 100), ('L2', 130), ('L3', 170), ('L4', 220), ('L5', 290), ('L6', 380)
),
mid AS (
    SELECT b.country_code, l.level, b.currency_code,
           b.l1_major * l.multiplier_pct / 100 * b.minor_per_major AS mid_minor
    FROM base b CROSS JOIN level_multiplier l
)
INSERT INTO pay_band (country_code, level, currency_code, min_minor, mid_minor, max_minor)
SELECT country_code, level, currency_code, mid_minor * 80 / 100, mid_minor, mid_minor * 120 / 100
FROM mid;

-- One definition of compa-ratio and band position, shared by search, sorting, filters and insights.
-- compa_ratio_bp is in basis points (9700 = 0.97), integer maths rounded half-up; no floating point.
CREATE VIEW employee_pay AS
SELECT e.id AS employee_id,
       b.min_minor AS band_min_minor,
       b.mid_minor AS band_mid_minor,
       b.max_minor AS band_max_minor,
       (e.current_salary_minor * 10000 + b.mid_minor / 2) / b.mid_minor AS compa_ratio_bp,
       CASE WHEN e.current_salary_minor < b.min_minor THEN 'BELOW'
            WHEN e.current_salary_minor > b.max_minor THEN 'ABOVE'
            ELSE 'WITHIN' END AS band_position
FROM employee e
JOIN pay_band b ON b.country_code = e.country_code AND b.level = e.level;
