-- MB-033 native follow-up (delivery checkout): one saved address in the synthetic "Test zone A" for the synthetic
-- customer whose phone is :phone. Synthetic values only; inserted directly as test setup, not as the tested behaviour.
INSERT INTO address (id, user_id, label, recipient_name, phone_e164, governorate, city, zone_id, street, building)
SELECT gen_random_uuid(), u.id, 'Home', 'Synthetic A', :'phone', 'Cairo', 'Test city', '00000000-0000-4000-8000-00000000a001',
       'Synthetic street', '1'
FROM app_user u WHERE u.phone_e164 = :'phone';
