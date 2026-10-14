-- Synthetic development zones (dev profile, LOCAL/CI only). Not a launch-city decision (O01 open).
INSERT INTO service_zone (id, code, name_ar, name_en, city_ar, city_en, active, delivery_fee_minor)
VALUES ('00000000-0000-4000-8000-00000000a001', 'test-zone-a', 'منطقة تجريبية أ', 'Test zone A', 'مدينة تجريبية', 'Test city', true, 3000),
       ('00000000-0000-4000-8000-00000000a002', 'test-zone-b', 'منطقة تجريبية ب', 'Test zone B', 'مدينة تجريبية', 'Test city', true, 4500),
       ('00000000-0000-4000-8000-00000000a003', 'test-zone-off', 'منطقة تجريبية متوقفة', 'Test zone (inactive)', 'مدينة تجريبية', 'Test city', false, 3000)
ON CONFLICT (id) DO NOTHING;
