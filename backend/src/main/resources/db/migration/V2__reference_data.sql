INSERT INTO country (code, name, currency_code) VALUES
    ('US', 'United States',  'USD'),
    ('IN', 'India',          'INR'),
    ('GB', 'United Kingdom', 'GBP'),
    ('DE', 'Germany',        'EUR'),
    ('CA', 'Canada',         'CAD'),
    ('SG', 'Singapore',      'SGD'),
    ('AU', 'Australia',      'AUD'),
    ('JP', 'Japan',          'JPY');

-- Fixed, illustrative rates. Reports are reproducible because rates never change at runtime.
INSERT INTO fx_rate (currency_code, usd_per_unit, as_of_date) VALUES
    ('USD', '1',      '2026-10-01'),
    ('INR', '0.012',  '2026-10-01'),
    ('GBP', '1.27',   '2026-10-01'),
    ('EUR', '1.08',   '2026-10-01'),
    ('CAD', '0.73',   '2026-10-01'),
    ('SGD', '0.74',   '2026-10-01'),
    ('AUD', '0.66',   '2026-10-01'),
    ('JPY', '0.0067', '2026-10-01');

INSERT INTO department (id, name) VALUES
    (1, 'Engineering'),
    (2, 'Product'),
    (3, 'Design'),
    (4, 'Sales'),
    (5, 'Marketing'),
    (6, 'Finance'),
    (7, 'People'),
    (8, 'Operations');
