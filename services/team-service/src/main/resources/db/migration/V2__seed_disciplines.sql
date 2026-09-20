-- Справочник профессиональных ролей: фиксированный набор для продукта.
-- FE / BE / QA / SA. Velocity — SP на эталонный спринт (10 раб. дней).

INSERT INTO discipline (id, code, name, velocity_sp_per_sprint) VALUES
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001', 'BE', 'Backend', 20.00),
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0002', 'FE', 'Frontend', 20.00),
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0003', 'QA', 'QA', 15.00),
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0004', 'SA', 'System Analyst', 15.00)
ON CONFLICT (code) DO NOTHING;
