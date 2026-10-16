-- USD equivalent of the current salary, computed by CurrencyConverter on every salary write.
-- Enables cross-country sorting and USD aggregates as exact integer SQL.
-- Trade-off: if FX rates change, a re-rating job must recompute this column.
ALTER TABLE employee ADD COLUMN current_salary_usd_minor INTEGER NOT NULL DEFAULT 0;

CREATE INDEX idx_employee_salary_usd ON employee (current_salary_usd_minor);
