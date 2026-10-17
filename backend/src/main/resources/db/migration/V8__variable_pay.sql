-- Variable pay target as basis points of base salary (1000 = 10%). Set for every employee by the seeder and on hire;
-- 0 means no variable pay.
ALTER TABLE employee ADD COLUMN variable_target_bp INTEGER NOT NULL DEFAULT 0
    CHECK (variable_target_bp BETWEEN 0 AND 10000);
